                                 #!/usr/bin/env python3
"""Convert Korean boundary Shapefiles to WGS84 GeoJSON for map generation.

The 2025 Q2 administrative boundary package is a development-time source:
EPSG:5179 Polygon Shapefiles are converted here, then committed as generated
app resources. The app never reads the Shapefile or contacts its source.
"""

from __future__ import annotations

import argparse
import json
import math
import struct
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable

WGS84_A = 6_378_137.0
KOREA_INV_FLATTENING = 298.257222101
KOREA_CENTRAL_MERIDIAN = math.radians(127.5)
KOREA_LATITUDE_OF_ORIGIN = math.radians(38.0)
KOREA_SCALE = 0.9996
KOREA_FALSE_EASTING = 1_000_000.0
KOREA_FALSE_NORTHING = 2_000_000.0


@dataclass(frozen=True)
class ShapeRecord:
    parts: list[list[tuple[float, float]]]


# 2025 Q2 source still contains the pre-2026 Incheon administrative dongs.
# These groups are the source areas for the four new districts introduced on
# 2026-07-01. The source uses the unsplit names 운서동 and 아라동.
INCHEON_DONGS_BY_DISTRICT: dict[str, tuple[str, frozenset[str]]] = {
    "28125": (
        "제물포구",
        frozenset({
            "연안동", "신포동", "신흥동", "도원동", "율목동", "동인천동", "개항동",
            "만석동", "화수1·화평동", "화수2동", "송현1·2동", "송현3동", "송림1동",
            "송림2동", "송림3·5동", "송림4동", "송림6동", "금창동",
        }),
    ),
    "28155": (
        "영종구",
        frozenset({"용유동", "운서동", "영종동", "영종1동", "영종2동"}),
    ),
    "28275": (
        "서해구",
        frozenset({
            "검암경서동", "연희동", "청라1동", "청라2동", "청라3동", "가정1동",
            "가정2동", "가정3동", "신현원창동", "석남1동", "석남2동", "석남3동",
            "가좌1동", "가좌2동", "가좌3동", "가좌4동",
        }),
    ),
    "28290": (
        "검단구",
        frozenset({"검단동", "불로대곡동", "원당동", "당하동", "오류왕길동", "마전동", "아라동"}),
    ),
}


def reorganized_incheon_district_code(adm_code: str, adm_name: str) -> str | None:
    """Return a 2026 Incheon district code for a 2025 source dong."""
    if not adm_code.startswith("23"):
        return None
    return next(
        (
            district_code
            for district_code, (_, dong_names) in INCHEON_DONGS_BY_DISTRICT.items()
            if adm_name in dong_names
        ),
        None,
    )


def _meridional_arc(latitude: float, eccentricity_squared: float) -> float:
    a = WGS84_A
    return a * (
        (1 - eccentricity_squared / 4 - 3 * eccentricity_squared**2 / 64 - 5 * eccentricity_squared**3 / 256) * latitude
        - (3 * eccentricity_squared / 8 + 3 * eccentricity_squared**2 / 32 + 45 * eccentricity_squared**3 / 1024) * math.sin(2 * latitude)
        + (15 * eccentricity_squared**2 / 256 + 45 * eccentricity_squared**3 / 1024) * math.sin(4 * latitude)
        - (35 * eccentricity_squared**3 / 3072) * math.sin(6 * latitude)
    )


def epsg5179_to_wgs84(easting: float, northing: float) -> tuple[float, float]:
    """Convert Korea 2000 / Unified CS (EPSG:5179) to lon/lat WGS84."""
    flattening = 1 / KOREA_INV_FLATTENING
    eccentricity_squared = flattening * (2 - flattening)
    second_eccentricity_squared = eccentricity_squared / (1 - eccentricity_squared)

    meridional_origin = _meridional_arc(
        KOREA_LATITUDE_OF_ORIGIN,
        eccentricity_squared,
    )
    x = (easting - KOREA_FALSE_EASTING) / KOREA_SCALE
    y = (northing - KOREA_FALSE_NORTHING) / KOREA_SCALE
    mu = (meridional_origin + y) / (
        WGS84_A * (1 - eccentricity_squared / 4 - 3 * eccentricity_squared**2 / 64 - 5 * eccentricity_squared**3 / 256)
    )

    eccentricity_one = (1 - math.sqrt(1 - eccentricity_squared)) / (
        1 + math.sqrt(1 - eccentricity_squared)
    )
    footpoint_latitude = mu
    footpoint_latitude += (
        3 * eccentricity_one / 2 - 27 * eccentricity_one**3 / 32
    ) * math.sin(2 * mu)
    footpoint_latitude += (
        21 * eccentricity_one**2 / 16 - 55 * eccentricity_one**4 / 32
    ) * math.sin(4 * mu)
    footpoint_latitude += 151 * eccentricity_one**3 / 96 * math.sin(6 * mu)
    footpoint_latitude += 1097 * eccentricity_one**4 / 512 * math.sin(8 * mu)

    sin_footpoint = math.sin(footpoint_latitude)
    cos_footpoint = math.cos(footpoint_latitude)
    tangent_squared = math.tan(footpoint_latitude) ** 2
    curvature_prime = second_eccentricity_squared * cos_footpoint**2
    radius_prime = WGS84_A / math.sqrt(1 - eccentricity_squared * sin_footpoint**2)
    radius_meridian = WGS84_A * (1 - eccentricity_squared) / (
        1 - eccentricity_squared * sin_footpoint**2
    ) ** 1.5
    distance = x / radius_prime

    latitude = footpoint_latitude - (radius_prime * math.tan(footpoint_latitude) / radius_meridian) * (
        distance**2 / 2
        - (5 + 3 * tangent_squared + 10 * curvature_prime - 4 * curvature_prime**2 - 9 * second_eccentricity_squared)
        * distance**4 / 24
        + (61 + 90 * tangent_squared + 298 * curvature_prime + 45 * tangent_squared**2
           - 252 * second_eccentricity_squared - 3 * curvature_prime**2)
        * distance**6 / 720
    )
    longitude = KOREA_CENTRAL_MERIDIAN + (
        distance
        - (1 + 2 * tangent_squared + curvature_prime) * distance**3 / 6
        + (5 - 2 * curvature_prime + 28 * tangent_squared - 3 * curvature_prime**2
           + 8 * second_eccentricity_squared + 24 * tangent_squared**2)
        * distance**5 / 120
    ) / cos_footpoint
    return math.degrees(longitude), math.degrees(latitude)


def read_dbf(path: Path) -> list[dict[str, str]]:
    raw = path.read_bytes()
    if len(raw) < 32:
        raise ValueError(f"DBF header is truncated: {path}")

    record_count = struct.unpack_from("<I", raw, 4)[0]
    header_length = struct.unpack_from("<H", raw, 8)[0]
    record_length = struct.unpack_from("<H", raw, 10)[0]
    encoding_path = path.with_suffix(".cpg")
    encoding = encoding_path.read_text().strip() if encoding_path.exists() else "cp949"
    fields: list[tuple[str, int]] = []
    offset = 32
    while offset < header_length and raw[offset] != 0x0D:
        field_name = raw[offset:offset + 11].split(b"\0", 1)[0].decode("ascii")
        field_length = raw[offset + 16]
        fields.append((field_name, field_length))
        offset += 32

    rows: list[dict[str, str]] = []
    for index in range(record_count):
        start = header_length + index * record_length
        row = raw[start:start + record_length]
        if len(row) < record_length or row[:1] == b"*":
            continue
        cursor = 1
        values: dict[str, str] = {}
        for field_name, field_length in fields:
            values[field_name] = row[cursor:cursor + field_length].decode(
                encoding,
                errors="replace",
            ).strip()
            cursor += field_length
        rows.append(values)
    return rows


def read_polygon_shapefile(path: Path) -> list[ShapeRecord]:
    raw = path.read_bytes()
    if len(raw) < 100 or struct.unpack_from(">I", raw, 0)[0] != 9994:
        raise ValueError(f"Not a Shapefile: {path}")

    records: list[ShapeRecord] = []
    offset = 100
    while offset < len(raw):
        if offset + 8 > len(raw):
            raise ValueError(f"Truncated Shapefile record header: {path}")
        _, content_words = struct.unpack_from(">2i", raw, offset)
        offset += 8
        content_size = content_words * 2
        content = raw[offset:offset + content_size]
        offset += content_size
        if len(content) != content_size:
            raise ValueError(f"Truncated Shapefile record: {path}")

        shape_type = struct.unpack_from("<i", content, 0)[0]
        if shape_type == 0:
            continue
        if shape_type != 5:
            raise ValueError(f"Only Polygon Shapefiles are supported, got {shape_type}")

        part_count, point_count = struct.unpack_from("<2i", content, 36)
        parts = struct.unpack_from(f"<{part_count}i", content, 44)
        points_offset = 44 + part_count * 4
        points = [
            struct.unpack_from("<2d", content, points_offset + point_index * 16)
            for point_index in range(point_count)
        ]
        rings = []
        for part_index, start in enumerate(parts):
            end = parts[part_index + 1] if part_index + 1 < part_count else point_count
            ring = points[start:end]
            if len(ring) >= 4:
                rings.append(ring)
        records.append(ShapeRecord(parts=rings))
    return records


def signed_area_twice(ring: Iterable[tuple[float, float]]) -> float:
    points = list(ring)
    return sum(
        first[0] * second[1] - second[0] * first[1]
        for first, second in zip(points, points[1:] + points[:1])
    )


def exterior_rings(parts: list[list[tuple[float, float]]]) -> list[list[tuple[float, float]]]:
    """Keep Shapefile clockwise exteriors and discard counter-clockwise holes."""
    exteriors = [part for part in parts if signed_area_twice(part) < 0]
    if not exteriors:
        raise ValueError("Polygon record has no clockwise exterior ring")
    return exteriors


def _geojson_ring(ring: list[tuple[float, float]]) -> list[list[float]]:
    return [list(epsg5179_to_wgs84(easting, northing)) for easting, northing in ring]


def convert_shapefile(
    source: Path,
    output: Path,
    code_field: str,
    name_field: str,
) -> tuple[int, int, int]:
    rows = read_dbf(source.with_suffix(".dbf"))
    shapes = read_polygon_shapefile(source)
    if len(rows) != len(shapes):
        raise ValueError(f"DBF/SHP record count differs: {len(rows)} != {len(shapes)}")

    features = []
    hole_count = 0
    point_count = 0
    for row, shape in zip(rows, shapes):
        if code_field not in row or name_field not in row:
            raise ValueError(f"Missing fields {code_field}/{name_field} in {source}")
        exteriors = exterior_rings(shape.parts)
        hole_count += len(shape.parts) - len(exteriors)
        converted_rings = [_geojson_ring(ring) for ring in exteriors]
        point_count += sum(len(ring) for ring in converted_rings)
        if len(converted_rings) == 1:
            geometry = {"type": "Polygon", "coordinates": [converted_rings[0]]}
        else:
            geometry = {
                "type": "MultiPolygon",
                "coordinates": [[[point for point in ring]] for ring in converted_rings],
            }
        features.append({
            "type": "Feature",
            "properties": {
                "code": row[code_field],
                "name": row[name_field],
                "baseDate": row.get("BASE_DATE", ""),
            },
            "geometry": geometry,
        })

    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps({"type": "FeatureCollection", "features": features}, separators=(",", ":")))
    return len(features), hole_count, point_count


def convert_incheon_reorganized_districts(source: Path, output: Path) -> tuple[int, int, int]:
    """Aggregate 2025 Incheon dong shapes into the four 2026 district shapes."""
    rows = read_dbf(source.with_suffix(".dbf"))
    shapes = read_polygon_shapefile(source)
    if len(rows) != len(shapes):
        raise ValueError(f"DBF/SHP record count differs: {len(rows)} != {len(shapes)}")

    grouped: dict[str, list[list[list[float]]]] = {
        district_code: [] for district_code in INCHEON_DONGS_BY_DISTRICT
    }
    seen_dongs: set[str] = set()
    hole_count = 0
    point_count = 0
    for row, shape in zip(rows, shapes):
        adm_code = row.get("ADM_CD", "")
        adm_name = row.get("ADM_NM", "")
        district_code = reorganized_incheon_district_code(adm_code, adm_name)
        if district_code is None:
            continue
        exteriors = exterior_rings(shape.parts)
        hole_count += len(shape.parts) - len(exteriors)
        converted_rings = [_geojson_ring(ring) for ring in exteriors]
        grouped[district_code].extend(converted_rings)
        seen_dongs.add(adm_name)
        point_count += sum(len(ring) for ring in converted_rings)

    expected_dongs = {
        dong_name
        for _, (_, dong_names) in INCHEON_DONGS_BY_DISTRICT.items()
        for dong_name in dong_names
    }
    missing_dongs = expected_dongs - seen_dongs
    if missing_dongs:
        raise ValueError(f"Incheon source is missing dongs: {sorted(missing_dongs)}")

    features = []
    for district_code, (district_name, _) in INCHEON_DONGS_BY_DISTRICT.items():
        rings = grouped[district_code]
        if not rings:
            raise ValueError(f"Incheon district has no geometry: {district_code}")
        if len(rings) == 1:
            geometry = {"type": "Polygon", "coordinates": [rings[0]]}
        else:
            geometry = {
                "type": "MultiPolygon",
                "coordinates": [[[point for point in ring]] for ring in rings],
            }
        features.append({
            "type": "Feature",
            "properties": {
                "code": district_code,
                "name": district_name,
                "provinceCode": "KR-28",
            },
            "geometry": geometry,
        })

    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps({"type": "FeatureCollection", "features": features}, separators=(",", ":")))
    return len(features), hole_count, point_count


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("source", type=Path, help="Polygon .shp file with adjacent .dbf/.cpg files")
    parser.add_argument("--code-field")
    parser.add_argument("--name-field")
    parser.add_argument(
        "--aggregate-incheon-districts",
        action="store_true",
        help="Aggregate the 2025 Incheon dong source into 2026 district boundaries.",
    )
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    if args.aggregate_incheon_districts:
        if args.code_field or args.name_field:
            parser.error("--aggregate-incheon-districts cannot be combined with field options")
        feature_count, hole_count, point_count = convert_incheon_reorganized_districts(
            args.source,
            args.output,
        )
    else:
        if not args.code_field or not args.name_field:
            parser.error("--code-field and --name-field are required without --aggregate-incheon-districts")
        feature_count, hole_count, point_count = convert_shapefile(
            args.source,
            args.output,
            args.code_field,
            args.name_field,
        )
    print(f"converted {feature_count} features, discarded {hole_count} holes, kept {point_count} points")


if __name__ == "__main__":
    main()
