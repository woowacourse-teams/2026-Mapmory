#!/usr/bin/env python3
"""Generate static Kotlin province and district boundary data.

The source GeoJSON is read only during development. Runtime code consumes the
generated Kotlin files and never contacts the source repository.

Examples:
    python3 tools/map/generate_korea_map.py /tmp/geoBoundaries-KOR-ADM1.geojson
    python3 tools/map/generate_korea_map.py \
        --district-source /tmp/skorea-municipalities.json \
        --locations-source shared/src/commonMain/kotlin/com/mapmory/shared/domain/model/KoreanDistrictCode.kt \
        --district-tolerance 0.0005 \
        --district-override-source /tmp/incheon-reorganized-districts.json

The 2025 Q2 Korean boundary Shapefiles can be converted first with
``convert_korea_shapefile.py``. Both scripts run only during development;
generated Kotlin/resources are the runtime source.
"""

from __future__ import annotations

import argparse
import json
import re
from collections import defaultdict
from dataclasses import dataclass
from math import atan2, tau
from pathlib import Path
from typing import Any

MAX_PROVINCE_POINTS_PER_PART = 3_000
DISTRICT_SOURCE_PROVINCE_CODES = {
    "11": "KR-11", "21": "KR-26", "22": "KR-27", "23": "KR-28",
    "24": "KR-29", "25": "KR-30", "26": "KR-31", "29": "KR-50",
    "31": "KR-41", "32": "KR-42", "33": "KR-43", "34": "KR-44",
    "35": "KR-45", "36": "KR-46", "37": "KR-47", "38": "KR-48",
    "39": "KR-49",
}
# 군위군은 원본 2018 데이터에서 경북에 있었지만 현재 앱의 canonical
# 행정구역에서는 대구광역시 소속이다. 이 예외는 생성 단계에서만 적용한다.
DISTRICT_FEATURE_PROVINCE_OVERRIDES = {"37310": "KR-27"}
PROVINCE_NAMES = {
    "KR-11": "서울특별시", "KR-26": "부산광역시", "KR-27": "대구광역시",
    "KR-28": "인천광역시", "KR-29": "광주광역시", "KR-30": "대전광역시",
    "KR-31": "울산광역시", "KR-41": "경기도", "KR-42": "강원특별자치도",
    "KR-43": "충청북도", "KR-44": "충청남도", "KR-45": "전북특별자치도",
    "KR-46": "전라남도", "KR-47": "경상북도", "KR-48": "경상남도",
    "KR-49": "제주특별자치도", "KR-50": "세종특별자치시",
}
METROPOLITAN_PROVINCES = {
    "KR-11", "KR-26", "KR-27", "KR-28", "KR-29", "KR-30", "KR-31", "KR-50",
}
# Never mix province boundaries from different sources in one overview map.
# Adjacent polygons must use the same coordinate set or narrow gaps can appear
# between them after projection (for example Ulsan/Gyeongnam and Sejong/Chungnam).
PROVINCE_OVERRIDE_CODES = frozenset(PROVINCE_NAMES)
DEFAULT_PROVINCE_OVERRIDE_TOLERANCE = 0.002
# Finer district geometry is subpixel nationwide; detailed resources keep the full geometry.
OVERVIEW_DISTRICT_TOLERANCE = 0.008
DOKDO_PROVINCE_CODE = "KR-47"
DOKDO_REFERENCE_POINT = (131.86941, 37.24006)
DOKDO_RING_RADIUS = 0.05
DOKDO_OVERVIEW_TOLERANCE = 0.00003
PROVINCE_PREFIXES = tuple(PROVINCE_NAMES.values()) + ("강원도", "전라북도")
CITY_DISTRICT_PATTERN = re.compile(r"^(.+시).+구$")
LOCATION_PATTERN = re.compile(
    r'KoreanDistrictCode\("([^"]+)",\s*"([^"]+)",\s*(?:"([^"]+)"|null)\)',
)


@dataclass(frozen=True)
class Location:
    code: str
    name: str
    province_code: str | None


@dataclass(frozen=True)
class Boundary:
    province_code: str
    code: str
    name: str
    rings: list[list[list[float]]]


def number(value: float) -> str:
    value = round(float(value), 5)
    if abs(value) < 0.000005:
        value = 0.0
    result = f"{value:.5f}".rstrip("0").rstrip(".")
    return (result if result not in ("", "-0") else "0") + "f"


def ring_expression(ring: list[list[float]]) -> str:
    points = ", ".join(f"p({number(lon)}, {number(lat)})" for lon, lat in ring)
    return f"listOf({points})"


def outer_rings(geometry: dict[str, Any]) -> list[list[list[float]]]:
    """Return only exterior rings; interior GeoJSON rings are holes."""
    geometry_type = geometry.get("type")
    coordinates = geometry.get("coordinates", [])
    if geometry_type == "Polygon":
        candidates = [coordinates[0]] if coordinates else []
    elif geometry_type == "MultiPolygon":
        candidates = [polygon[0] for polygon in coordinates if polygon]
    else:
        candidates = []
    return [ring for ring in candidates if len(ring) >= 3]


def _squared_segment_distance(point: tuple[float, float], start: tuple[float, float], end: tuple[float, float]) -> float:
    dx = end[0] - start[0]
    dy = end[1] - start[1]
    if dx == 0 and dy == 0:
        return (point[0] - start[0]) ** 2 + (point[1] - start[1]) ** 2
    ratio = ((point[0] - start[0]) * dx + (point[1] - start[1]) * dy) / (dx * dx + dy * dy)
    ratio = max(0.0, min(1.0, ratio))
    projected = (start[0] + ratio * dx, start[1] + ratio * dy)
    return (point[0] - projected[0]) ** 2 + (point[1] - projected[1]) ** 2


def _rdp(points: list[tuple[float, float]], tolerance: float) -> list[tuple[float, float]]:
    if len(points) <= 2:
        return points
    farthest_index = 0
    farthest_distance = 0.0
    for index in range(1, len(points) - 1):
        distance = _squared_segment_distance(points[index], points[0], points[-1])
        if distance > farthest_distance:
            farthest_index = index
            farthest_distance = distance
    if farthest_distance <= tolerance * tolerance:
        return [points[0], points[-1]]
    left = _rdp(points[:farthest_index + 1], tolerance)
    right = _rdp(points[farthest_index:], tolerance)
    return left[:-1] + right


def simplify_ring(ring: list[list[float]], tolerance: float) -> list[list[float]]:
    """Simplify a closed ring while preserving its first/last point."""
    if tolerance <= 0 or len(ring) < 4:
        return ring
    points = [(float(longitude), float(latitude)) for longitude, latitude in ring]
    if points[0] == points[-1]:
        points.pop()
    if len(points) < 3:
        return ring
    simplified = _rdp(points + [points[0]], tolerance)
    if simplified[-1] != simplified[0]:
        simplified.append(simplified[0])
    if len(simplified) < 4:
        return []
    return [[longitude, latitude] for longitude, latitude in simplified]


def province_ring_tolerance(
    code: str,
    ring: list[list[float]],
    tolerance: float,
) -> float:
    if code == DOKDO_PROVINCE_CODE and all(
        abs(longitude - DOKDO_REFERENCE_POINT[0]) <= DOKDO_RING_RADIUS
        and abs(latitude - DOKDO_REFERENCE_POINT[1]) <= DOKDO_RING_RADIUS
        for longitude, latitude in ring
    ):
        return min(tolerance, DOKDO_OVERVIEW_TOLERANCE)
    return tolerance


def read_province_features(
    source: Path,
    simplify_tolerance: float = 0.0,
) -> list[tuple[str, str, list[list[list[float]]]]]:
    features = []
    for feature in json.loads(source.read_text())["features"]:
        properties = feature.get("properties", {})
        raw_code = properties.get("shapeISO") or properties.get("code")
        if not raw_code:
            continue
        if raw_code in PROVINCE_NAMES:
            code = raw_code
        else:
            code = DISTRICT_SOURCE_PROVINCE_CODES.get(str(raw_code))
        if code is None:
            continue
        rings = outer_rings(feature.get("geometry", {}))
        if simplify_tolerance > 0:
            rings = [
                simplify_ring(ring, province_ring_tolerance(code, ring, simplify_tolerance))
                for ring in rings
            ]
            rings = [ring for ring in rings if len(ring) >= 4]
        if rings:
            features.append((code, PROVINCE_NAMES.get(code, properties.get("name", code)), rings))
    return features


def merge_province_features(
    base: list[tuple[str, str, list[list[list[float]]]]],
    overrides: list[tuple[str, str, list[list[list[float]]]]],
    override_codes: frozenset[str],
) -> list[tuple[str, str, list[list[list[float]]]]]:
    override_by_code = {feature[0]: feature for feature in overrides}
    base_codes = {code for code, *_ in base}
    missing = override_codes - override_by_code.keys()
    if missing:
        raise ValueError(f"province override source is missing codes: {sorted(missing)}")
    if not override_codes <= base_codes:
        missing_base = override_codes - base_codes
        raise ValueError(f"base province source is missing codes: {sorted(missing_base)}")
    return [override_by_code[feature[0]] if feature[0] in override_codes else feature for feature in base]


def read_locations(source: Path) -> list[Location]:
    return [Location(code, name, province) for code, name, province in LOCATION_PATTERN.findall(source.read_text())]


def normalize_name(name: str) -> str:
    compact_name = name.replace(" ", "")
    for prefix in PROVINCE_PREFIXES:
        if compact_name.startswith(prefix):
            return compact_name[len(prefix):]
    return compact_name


def selectable_locations(locations: list[Location]) -> list[Location]:
    selectable = []
    for location in locations:
        if location.province_code is None:
            continue
        city = CITY_DISTRICT_PATTERN.fullmatch(normalize_name(location.name))
        if location.province_code in METROPOLITAN_PROVINCES or city is None:
            selectable.append(Location(location.code, normalize_name(location.name), location.province_code))
        else:
            selectable.append(Location(location.code[:-1] + "0", city.group(1), location.province_code))
    unique = {}
    for location in selectable:
        unique.setdefault(location.code, location)
    return list(unique.values())


def canonicalize_districts(
    source: Path,
    locations_source: Path,
    simplify_tolerance: float = 0.0,
) -> tuple[list[Boundary], list[str]]:
    targets = selectable_locations(read_locations(locations_source))
    by_name = {(location.province_code, location.name): location for location in targets}
    grouped: dict[tuple[str, str], Boundary] = {}
    skipped: list[str] = []

    for feature in json.loads(source.read_text())["features"]:
        properties = feature.get("properties", {})
        raw_code = str(properties.get("code", ""))
        province_code = properties.get("provinceCode")
        province_code = province_code or DISTRICT_FEATURE_PROVINCE_OVERRIDES.get(raw_code)
        province_code = province_code or DISTRICT_SOURCE_PROVINCE_CODES.get(raw_code[:2])
        raw_name = properties.get("name")
        if not province_code or not raw_name:
            continue
        display_name = raw_name
        if province_code not in METROPOLITAN_PROVINCES:
            city = CITY_DISTRICT_PATTERN.fullmatch(raw_name)
            if city:
                display_name = city.group(1)
        target = by_name.get((province_code, display_name))
        rings = outer_rings(feature.get("geometry", {}))
        if simplify_tolerance > 0:
            rings = [simplify_ring(ring, simplify_tolerance) for ring in rings]
            rings = [ring for ring in rings if len(ring) >= 4]
        if target is None:
            skipped.append(f"{province_code} {raw_code} {raw_name}")
            continue
        if not rings:
            skipped.append(f"{province_code} {raw_code} {raw_name} (no valid exterior rings)")
            continue
        key = (province_code, target.code)
        if key in grouped:
            grouped[key].rings.extend(rings)
        else:
            grouped[key] = Boundary(province_code, target.code, target.name, list(rings))

    result = list(grouped.values())
    result.sort(key=lambda boundary: (boundary.province_code, boundary.code))
    return result, skipped


def merge_district_boundaries(base: list[Boundary], overrides: list[Boundary]) -> list[Boundary]:
    by_key = {(boundary.province_code, boundary.code): boundary for boundary in base}
    for boundary in overrides:
        by_key[(boundary.province_code, boundary.code)] = boundary
    return sorted(by_key.values(), key=lambda boundary: (boundary.province_code, boundary.code))


def write_province_part(output: Path, index: int, features) -> str:
    object_name = f"GeneratedKoreaMapDataPart{index:02d}"
    lines = [
        "package com.mapmory.shared.presentation.map.data", "",
        "import com.mapmory.shared.presentation.map.domain.GeoPoint",
        "import com.mapmory.shared.presentation.map.domain.ProvincePolygon", "",
        f"internal object {object_name} {{",
        "    val provinces: List<ProvincePolygon> = listOf(",
    ]
    for code, name, rings in features:
        lines.append(f"        province({json.dumps(code)}, {json.dumps(name, ensure_ascii=False)}, listOf(")
        for ring_index, ring in enumerate(rings):
            comma = "," if ring_index < len(rings) - 1 else ""
            lines.append(f"            {ring_expression(ring)}{comma}")
        lines.append("        )),")
    lines += [
        "    )", "", "    private fun province(", "        code: String,", "        name: String,",
        "        rings: List<List<GeoPoint>>,",
        "    ): ProvincePolygon = ProvincePolygon(code = code, name = name, rings = rings)", "",
        "    private fun p(longitude: Float, latitude: Float): GeoPoint =",
        "        GeoPoint(longitude = longitude, latitude = latitude)", "}",
    ]
    (output / f"{object_name}.kt").write_text("\n".join(lines) + "\n")
    return object_name


def partition_province_features(features, max_points: int = MAX_PROVINCE_POINTS_PER_PART):
    """Keep generated JVM static initializers below the method-size limit."""
    parts = []
    current = []
    current_points = 0
    for feature in features:
        code, name, rings = feature
        ring_chunks = []
        current_rings = []
        current_ring_points = 0
        for ring in rings:
            if current_rings and current_ring_points + len(ring) > max_points:
                ring_chunks.append(current_rings)
                current_rings = []
                current_ring_points = 0
            current_rings.append(ring)
            current_ring_points += len(ring)
        if current_rings:
            ring_chunks.append(current_rings)

        for ring_chunk in ring_chunks:
            chunk = (code, name, ring_chunk)
            chunk_points = sum(len(ring) for ring in ring_chunk)
            if current and current_points + chunk_points > max_points:
                parts.append(current)
                current = []
                current_points = 0
            current.append(chunk)
            current_points += chunk_points
    if current:
        parts.append(current)
    return parts


def write_provinces(
    output: Path,
    source: Path,
    override_source: Path | None = None,
    override_tolerance: float = DEFAULT_PROVINCE_OVERRIDE_TOLERANCE,
    simplify_tolerance: float = 0.0,
) -> None:
    for old in output.glob("GeneratedKoreaMapDataPart*.kt"):
        old.unlink()
    features = read_province_features(source, simplify_tolerance)
    if override_source:
        override_features = read_province_features(override_source, override_tolerance)
        features = merge_province_features(features, override_features, PROVINCE_OVERRIDE_CODES)
    # The 2018 province source predates Gunwi's transfer. Always apply this pinned
    # correction after source overrides, just as district canonicalization does.
    corrections = read_province_features(Path(__file__).with_name("data") / "gunwi-province-overrides.geojson")
    features = merge_province_features(features, corrections, frozenset({"KR-27", "KR-47"}))
    feature_parts = partition_province_features(features)
    names = [
        write_province_part(output, index, part)
        for index, part in enumerate(feature_parts)
    ]
    parts = ",\n".join(f"        {name}.provinces" for name in names)
    aggregator = f"""package com.mapmory.shared.presentation.map.data

import com.mapmory.shared.presentation.map.domain.ProvincePolygon

/** Generated from a development-time GeoJSON source. */
internal object GeneratedKoreaMapData {{
    private val fragments: List<ProvincePolygon> = listOf(
{parts},
    ).flatten()

    val provinces: List<ProvincePolygon> = fragments
        .groupBy(ProvincePolygon::code)
        .values
        .map {{ provinceFragments ->
            val first = provinceFragments.first()
            first.copy(rings = provinceFragments.flatMap {{ it.rings }})
        }}
}}
"""
    (output / "GeneratedKoreaMapData.kt").write_text(aggregator)
    print(f"generated {len(features)} provinces in {len(names)} parts")


def write_district_resource(output: Path, province_code: str, boundaries: list[Boundary]) -> None:
    suffix = province_code.removeprefix("KR-")
    payload = {
        "provinceCode": province_code,
        "districts": [
            {
                "code": boundary.code,
                "name": boundary.name,
                "rings": [
                    [[round(longitude, 5), round(latitude, 5)] for longitude, latitude in ring]
                    for ring in boundary.rings
                ],
            }
            for boundary in boundaries
        ],
    }
    (output / f"korea-districts-{suffix}.json").write_text(
        json.dumps(payload, ensure_ascii=False, separators=(",", ":")) + "\n",
    )


def normalized_edge(
    start: tuple[float, float],
    end: tuple[float, float],
) -> tuple[tuple[float, float], tuple[float, float]]:
    return (start, end) if start < end else (end, start)


def district_label_point(rings: list[list[list[float]]]) -> tuple[float, float] | None:
    if not rings:
        return None
    ring = max(rings, key=lambda candidate: abs(_ring_area_twice(candidate)))
    area = _ring_area_twice(ring)
    if abs(area) > 0.000001:
        longitude = 0.0
        latitude = 0.0
        for index, point in enumerate(ring):
            next_point = ring[(index + 1) % len(ring)]
            cross = point[0] * next_point[1] - next_point[0] * point[1]
            longitude += (point[0] + next_point[0]) * cross
            latitude += (point[1] + next_point[1]) * cross
        centroid = (longitude / (3 * area), latitude / (3 * area))
        if _point_in_ring(centroid, ring):
            return centroid

    bounds_center = (
        (min(point[0] for point in ring) + max(point[0] for point in ring)) / 2,
        (min(point[1] for point in ring) + max(point[1] for point in ring)) / 2,
    )
    if _point_in_ring(bounds_center, ring):
        return bounds_center
    return tuple(ring[0])


def _ring_area_twice(ring: list[list[float]]) -> float:
    return sum(
        point[0] * ring[(index + 1) % len(ring)][1]
        - ring[(index + 1) % len(ring)][0] * point[1]
        for index, point in enumerate(ring)
    )


def _point_in_ring(point: tuple[float, float], ring: list[list[float]]) -> bool:
    inside = False
    previous = ring[-1]
    for current in ring:
        cross = (point[1] - previous[1]) * (current[0] - previous[0]) - (
            point[0] - previous[0]
        ) * (current[1] - previous[1])
        if (
            abs(cross) <= 0.000001
            and min(previous[0], current[0]) <= point[0] <= max(previous[0], current[0])
            and min(previous[1], current[1]) <= point[1] <= max(previous[1], current[1])
        ):
            return True
        crosses_y = (current[1] > point[1]) != (previous[1] > point[1])
        if crosses_y:
            intersection = (previous[0] - current[0]) * (point[1] - current[1]) / (
                previous[1] - current[1]
            ) + current[0]
            if point[0] < intersection:
                inside = not inside
        previous = current
    return inside


def overview_district_labels(boundaries: list[Boundary]) -> list[dict[str, Any]]:
    labels = []
    for boundary in boundaries:
        point = district_label_point(boundary.rings)
        if point is not None:
            labels.append({
                "code": boundary.code,
                "provinceCode": boundary.province_code,
                "name": boundary.name,
                "point": [round(point[0], 5), round(point[1], 5)],
            })
    return labels


def overview_shared_edge_paths(
    boundaries: list[Boundary],
) -> list[tuple[tuple[tuple[str, int], ...], list[tuple[float, float]]]]:
    edge_owners: dict[
        tuple[tuple[float, float], tuple[float, float]],
        set[tuple[str, int]],
    ] = defaultdict(set)
    for boundary in boundaries:
        for ring_index, ring in enumerate(boundary.rings):
            points = [(float(longitude), float(latitude)) for longitude, latitude in ring]
            for index, start in enumerate(points):
                end = points[(index + 1) % len(points)]
                if start != end:
                    edge_owners[normalized_edge(start, end)].add((boundary.code, ring_index))

    edges_by_owners: dict[
        tuple[tuple[str, int], ...],
        set[tuple[tuple[float, float], tuple[float, float]]],
    ] = defaultdict(set)
    for edge, ring_owners in edge_owners.items():
        if len({code for code, _ in ring_owners}) > 1:
            edges_by_owners[tuple(sorted(ring_owners))].add(edge)

    paths = []
    for owners, edges in edges_by_owners.items():
        adjacency: dict[tuple[float, float], set[tuple[float, float]]] = defaultdict(set)
        for start, end in edges:
            adjacency[start].add(end)
            adjacency[end].add(start)
        visited = set()

        def append_path(start: tuple[float, float], following: tuple[float, float]) -> None:
            points = [start]
            previous = None
            current = start
            while True:
                edge = normalized_edge(current, following)
                if edge in visited:
                    break
                visited.add(edge)
                points.append(following)
                previous, current = current, following
                if len(adjacency[current]) != 2:
                    break
                following = next(point for point in adjacency[current] if point != previous)
            paths.append((owners, points))

        for start in sorted(adjacency):
            if len(adjacency[start]) == 2:
                continue
            for following in sorted(adjacency[start]):
                if normalized_edge(start, following) not in visited:
                    append_path(start, following)
        for start, end in sorted(edges):
            if normalized_edge(start, end) not in visited:
                append_path(start, end)
    return paths


def overview_shared_district_pairs(boundaries: list[Boundary]) -> set[tuple[str, str]]:
    return {
        tuple(sorted((first[0], second[0])))
        for owners, _ in overview_shared_edge_paths(boundaries)
        for index, first in enumerate(owners)
        for second in owners[index + 1:]
        if first[0] != second[0]
    }


def _canonical_ring(points: list[tuple[float, float]]) -> list[tuple[float, float]]:
    candidates = []
    for direction in (points, list(reversed(points))):
        lowest = min(direction)
        candidates.extend(
            tuple(direction[index:] + direction[:index])
            for index, point in enumerate(direction)
            if point == lowest
        )
    return list(min(candidates))


def _simplify_shared_path(
    points: list[tuple[float, float]],
    tolerance: float,
) -> list[tuple[float, float]]:
    if points[0] == points[-1]:
        canonical = _canonical_ring(points[:-1])
        simplified = _rdp(canonical + [canonical[0]], tolerance)
        if simplified[-1] != simplified[0]:
            simplified.append(simplified[0])
        return simplified if len(simplified) >= 4 else points

    forward = tuple(points)
    reversed_direction = tuple(reversed(points)) < forward
    canonical = list(reversed(points)) if reversed_direction else points
    simplified = _rdp(canonical, tolerance)
    return list(reversed(simplified)) if reversed_direction else simplified


def simplify_ring_with_shared_anchors(
    ring: list[list[float]],
    retained_points: set[tuple[float, float]],
    removed_points: set[tuple[float, float]],
    tolerance: float,
) -> list[list[float]]:
    points = [(float(longitude), float(latitude)) for longitude, latitude in ring]
    if points[0] == points[-1]:
        points.pop()
    points = [point for point in points if point not in removed_points or point in retained_points]
    points = [point for index, point in enumerate(points) if index == 0 or point != points[index - 1]]
    anchors = [index for index, point in enumerate(points) if point in retained_points]
    if len(points) < 3:
        return ring
    if not anchors:
        return simplify_ring(ring, tolerance)

    start = anchors[0]
    points = points[start:] + points[:start]
    anchor_indices = sorted({(index - start) % len(points) for index in anchors})
    cycle = points + [points[0]]
    simplified_ring: list[tuple[float, float]] = []
    for segment_start, segment_end in zip(anchor_indices, anchor_indices[1:] + [len(points)]):
        simplified = _rdp(cycle[segment_start:segment_end + 1], tolerance)
        if simplified_ring:
            simplified_ring.extend(simplified[1:])
        else:
            simplified_ring.extend(simplified)
    if simplified_ring[0] != simplified_ring[-1]:
        simplified_ring.append(simplified_ring[0])
    if len(simplified_ring) < 4:
        return ring
    return [[longitude, latitude] for longitude, latitude in simplified_ring]


def simplify_overview_boundaries(boundaries: list[Boundary]) -> list[Boundary]:
    # Canonical shared paths keep adjacent district fills and overview strokes aligned.
    retained_by_ring: dict[tuple[str, int], set[tuple[float, float]]] = defaultdict(set)
    removed_by_ring: dict[tuple[str, int], set[tuple[float, float]]] = defaultdict(set)
    for ring_owners, points in overview_shared_edge_paths(boundaries):
        simplified = _simplify_shared_path(points, OVERVIEW_DISTRICT_TOLERANCE)
        retained = set(simplified[:-1] if simplified[0] == simplified[-1] else simplified)
        removed = set(points[:-1] if points[0] == points[-1] else points) - retained
        for ring_owner in ring_owners:
            retained_by_ring[ring_owner].update(retained)
            removed_by_ring[ring_owner].update(removed)

    simplified_boundaries = [
        Boundary(
            boundary.province_code,
            boundary.code,
            boundary.name,
            [
                simplify_ring_with_shared_anchors(
                    ring,
                    retained_by_ring[(boundary.code, ring_index)],
                    removed_by_ring[(boundary.code, ring_index)],
                    OVERVIEW_DISTRICT_TOLERANCE,
                )
                for ring_index, ring in enumerate(boundary.rings)
            ],
        )
        for boundary in boundaries
    ]
    if not overview_shared_district_pairs(boundaries) <= overview_shared_district_pairs(simplified_boundaries):
        raise ValueError("overview simplification separated adjacent district fills")
    return simplified_boundaries


def overview_boundary_lines(boundaries: list[Boundary]) -> list[list[tuple[float, float]]]:
    # Province grouping excludes coastlines and inter-province seams from the pale overlay.
    edge_owners: dict[
        tuple[str, tuple[tuple[float, float], tuple[float, float]]],
        set[str],
    ] = defaultdict(set)
    for boundary in boundaries:
        for ring in boundary.rings:
            points = [(float(longitude), float(latitude)) for longitude, latitude in ring]
            for index, start in enumerate(points):
                end = points[(index + 1) % len(points)]
                if start != end:
                    edge_owners[(boundary.province_code, normalized_edge(start, end))].add(boundary.code)

    # Gunwi moved to Daegu after the province geometry source; retain its missing perimeter.
    edges = sorted({
        edge for (_, edge), owners in edge_owners.items()
        if len(owners) > 1 or "27720" in owners
    })
    adjacency: dict[tuple[float, float], set[tuple[float, float]]] = defaultdict(set)
    for start, end in edges:
        adjacency[start].add(end)
        adjacency[end].add(start)

    visited = set()
    lines = []

    def append_line(start: tuple[float, float], next_point: tuple[float, float]) -> None:
        points = [start]
        previous = None
        current = start
        following = next_point
        while True:
            edge = normalized_edge(current, following)
            if edge in visited:
                break
            visited.add(edge)
            points.append(following)
            previous, current = current, following
            if len(adjacency[current]) != 2:
                break
            following = next(point for point in sorted(adjacency[current]) if point != previous)

        if len(points) >= 2:
            lines.append(points)

    for start in sorted(adjacency):
        if len(adjacency[start]) == 2:
            continue
        for next_point in sorted(adjacency[start]):
            if normalized_edge(start, next_point) not in visited:
                append_line(start, next_point)
    for start, end in edges:
        if normalized_edge(start, end) not in visited:
            append_line(start, end)
    if not lines:
        raise ValueError("district data has no shared within-province boundaries")
    return lines


def write_overview_district_resource(
    output: Path,
    boundaries: list[Boundary],
    label_boundaries: list[Boundary] | None = None,
) -> None:
    labels = overview_district_labels(label_boundaries or boundaries)
    if len(labels) != len(boundaries) or len({label["code"] for label in labels}) != len(boundaries):
        raise ValueError("overview district labels must match the canonical district boundaries")
    payload = {
        "lines": [
            [[round(longitude, 5), round(latitude, 5)] for longitude, latitude in line]
            for line in overview_boundary_lines(boundaries)
        ],
        "labels": labels,
    }
    (output / "korea-district-overview.json").write_text(
        json.dumps(payload, ensure_ascii=False, separators=(",", ":")) + "\n",
    )


def dissolve_overview_fill_rings(source: Boundary, overview: Boundary) -> Boundary:
    # Remove constituent-district seams from fills without regenerating visible outlines.
    source_rings = []
    owners = defaultdict(set)
    for ring_index, ring in enumerate(source.rings):
        points = []
        for longitude, latitude in ring:
            point = (round(longitude, 5), round(latitude, 5))
            if not points or points[-1] != point:
                points.append(point)
        if points[0] == points[-1]:
            points.pop()
        source_rings.append(points)
        for start, end in zip(points, points[1:] + points[:1]):
            owners[normalized_edge(start, end)].add(ring_index)
    internal_edges = {edge for edge, ring_owners in owners.items() if len(ring_owners) > 1}
    if not internal_edges:
        return overview

    exterior_edges = set()
    matched_rings = set()
    for ring in overview.rings:
        if len(ring) < 4:
            continue
        simplified = [(round(x, 5), round(y, 5)) for x, y in ring]
        candidates = [
            index for index, points in enumerate(source_rings)
            if set(simplified) <= set(points)
        ]
        if len(candidates) != 1:
            raise ValueError(f"ambiguous overview fill ring: {source.code}")
        ring_index = candidates[0]
        matched_rings.add(ring_index)
        points = source_rings[ring_index]
        indices = {point: index for index, point in enumerate(points)}
        for start, end in zip(simplified, simplified[1:]):
            first, last = indices[start], indices[end]
            arc = points[first:last + 1] if last > first else points[first:] + points[:last + 1]
            edges = list(zip(arc, arc[1:]))
            external = [edge for edge in edges if normalized_edge(*edge) not in internal_edges]
            if len(external) == len(edges):
                exterior_edges.add((start, end))
            elif external:
                exterior_edges.update(external)

    # Tiny constituent rings may have collapsed during overview simplification.
    for ring_index, points in enumerate(source_rings):
        if ring_index in matched_rings:
            continue
        edges = list(zip(points, points[1:] + points[:1]))
        if any(normalized_edge(*edge) in internal_edges for edge in edges):
            exterior_edges.update(edge for edge in edges if normalized_edge(*edge) not in internal_edges)

    rings = trace_overview_exterior_rings(exterior_edges, source.code)
    return Boundary(overview.province_code, overview.code, overview.name, rings)


def trace_overview_exterior_rings(
    exterior_edges: set[tuple[tuple[float, float], tuple[float, float]]],
    code: str,
) -> list[list[list[float]]]:
    following = defaultdict(set)
    incoming = defaultdict(int)
    for start, end in exterior_edges:
        following[start].add(end)
        incoming[end] += 1
    if any(len(following[point]) != incoming[point] for point in following.keys() | incoming.keys()):
        raise ValueError(f"unclosed overview fill exterior: {code}")
    rings = []
    remaining = set(exterior_edges)
    while remaining:
        start, end = min(remaining)
        ring = [start]
        previous, current = start, end
        remaining.remove((previous, current))
        while current != start:
            ring.append(current)
            candidates = [point for point in following[current] if (current, point) in remaining]
            if not candidates:
                raise ValueError(f"disconnected overview fill exterior: {code}")
            reverse_angle = atan2(previous[1] - current[1], previous[0] - current[0])
            # Clockwise source rings keep point-touching islands in separate exterior cycles.
            next_point = min(candidates, key=lambda point: (
                (atan2(point[1] - current[1], point[0] - current[0]) - reverse_angle) % tau,
                point,
            ))
            remaining.remove((current, next_point))
            previous, current = current, next_point
        ring.append(start)
        if len(ring) < 4:
            raise ValueError(f"degenerate overview fill exterior: {code}")
        rings.append([[x, y] for x, y in ring])
    return rings


def dissolve_overview_provinces(boundaries: list[Boundary]) -> list[Boundary]:
    # Cancel exact shared fill edges; preserve winding so enclosed provinces remain holes.
    grouped: dict[str, list[Boundary]] = defaultdict(list)
    for boundary in boundaries:
        grouped[boundary.province_code].append(boundary)
    provinces = []
    for code, districts in sorted(grouped.items()):
        directed_edges = set()
        for boundary in districts:
            for ring in boundary.rings:
                if len(ring) < 4 or ring[0] != ring[-1]:
                    raise ValueError(f"unclosed overview province source: {boundary.code}")
                for first, second in zip(ring, ring[1:]):
                    start = tuple(round(value, 5) for value in first)
                    end = tuple(round(value, 5) for value in second)
                    if start == end:
                        continue
                    if (start, end) in directed_edges:
                        raise ValueError(f"duplicate directed overview edge: {code}")
                    directed_edges.add((start, end))
        exterior_edges = {edge for edge in directed_edges if tuple(reversed(edge)) not in directed_edges}
        rings = trace_overview_exterior_rings(exterior_edges, code)
        if not rings:
            raise ValueError(f"empty overview province: {code}")
        provinces.append(Boundary(code, code, PROVINCE_NAMES[code], rings))
    return provinces


def write_overview_polygons_resource(output: Path, boundaries: list[Boundary], filename: str) -> None:
    payload = {
        "districts": [
            {
                "code": boundary.code,
                "name": boundary.name,
                "provinceCode": boundary.province_code,
                "rings": [
                    [[round(longitude, 5), round(latitude, 5)] for longitude, latitude in ring]
                    for ring in boundary.rings
                    if len(ring) >= 4
                ],
            }
            for boundary in boundaries
        ],
    }
    districts = payload["districts"]
    if len(districts) != len(boundaries) or len({district["code"] for district in districts}) != len(boundaries):
        raise ValueError("overview polygons must match the canonical boundaries")
    (output / filename).write_text(
        json.dumps(payload, ensure_ascii=False, separators=(",", ":")) + "\n",
    )


def write_overview_district_polygons_resource(output: Path, boundaries: list[Boundary]) -> None:
    write_overview_polygons_resource(output, boundaries, "korea-district-overview-polygons.json")


def write_overview_province_polygons_resource(output: Path, boundaries: list[Boundary]) -> None:
    write_overview_polygons_resource(
        output, dissolve_overview_provinces(boundaries), "korea-province-overview-polygons.json",
    )


def write_districts(
    resource_output: Path,
    source: Path,
    locations_source: Path,
    simplify_tolerance: float = 0.0,
    override_source: Path | None = None,
) -> None:
    resource_output.mkdir(parents=True, exist_ok=True)
    for old in resource_output.glob("korea-districts-*.json"):
        old.unlink()
    boundaries, skipped = canonicalize_districts(source, locations_source, simplify_tolerance)
    if override_source:
        overrides, override_skipped = canonicalize_districts(
            override_source,
            locations_source,
            simplify_tolerance,
        )
        boundaries = merge_district_boundaries(boundaries, overrides)
        skipped.extend(override_skipped)
    by_province: dict[str, list[Boundary]] = defaultdict(list)
    for boundary in boundaries:
        by_province[boundary.province_code].append(boundary)
    for province, values in sorted(by_province.items()):
        write_district_resource(resource_output, province, values)
    overview_boundaries = simplify_overview_boundaries(boundaries)
    write_overview_district_resource(resource_output, overview_boundaries, boundaries)
    fill_boundaries = [
        dissolve_overview_fill_rings(source, overview)
        for source, overview in zip(boundaries, overview_boundaries)
    ]
    write_overview_district_polygons_resource(resource_output, fill_boundaries)
    write_overview_province_polygons_resource(resource_output, fill_boundaries)
    print(f"generated {len(boundaries)} display boundaries for {len(by_province)} provinces")
    if skipped:
        print("skipped source features without a canonical app location:")
        print("\n".join(f"  - {entry}" for entry in skipped))


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("province_source", nargs="?", type=Path)
    parser.add_argument("--province-override-source", type=Path)
    parser.add_argument(
        "--province-override-tolerance",
        type=float,
        default=DEFAULT_PROVINCE_OVERRIDE_TOLERANCE,
        help="RDP tolerance in degrees for the topology-compatible province overrides.",
    )
    parser.add_argument(
        "--province-tolerance",
        type=float,
        default=0.0,
        help="RDP tolerance in degrees for the generated province map.",
    )
    parser.add_argument("--district-source", type=Path)
    parser.add_argument("--district-override-source", type=Path)
    parser.add_argument("--locations-source", type=Path)
    parser.add_argument(
        "--district-tolerance",
        type=float,
        default=0.0,
        help="RDP tolerance in degrees for generated district resources.",
    )
    parser.add_argument("--resource-output", type=Path, default=Path("shared/src/commonMain/composeResources/files"))
    parser.add_argument(
        "--output",
        type=Path,
        default=Path("shared/src/commonMain/kotlin/com/mapmory/shared/presentation/map/data"),
    )
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    if args.province_source:
        write_provinces(
            args.output,
            args.province_source,
            args.province_override_source,
            args.province_override_tolerance,
            args.province_tolerance,
        )
    if args.district_source:
        if not args.locations_source:
            parser.error("--locations-source is required with --district-source")
        write_districts(
            args.resource_output,
            args.district_source,
            args.locations_source,
            args.district_tolerance,
            args.district_override_source,
        )
    if not args.province_source and not args.district_source:
        parser.error("province source or --district-source is required")


if __name__ == "__main__":
    main()
