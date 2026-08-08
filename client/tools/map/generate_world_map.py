#!/usr/bin/env python3
"""Generate compact Kotlin country rings from a Natural Earth GeoJSON file.

Usage:
    python3 tools/map/generate_world_map.py /path/to/ne_110m_admin_0_countries.geojson
"""

from __future__ import annotations

import argparse
import json
import math
from pathlib import Path

PART_COUNT = 10


def number(value: float) -> str:
    value = round(float(value), 4)
    if abs(value) < 0.00005:
        value = 0.0
    result = f"{value:.4f}".rstrip("0").rstrip(".")
    return (result if result not in ("", "-0") else "0") + "f"


def ring_expression(ring: list[list[float]]) -> str:
    points = ", ".join(f"p({number(lon)}, {number(lat)})" for lon, lat in ring)
    return f"listOf({points})"


def read_features(source: Path) -> list[tuple[str, str, list[list[list[float]]]]]:
    features = []
    for feature in json.loads(source.read_text())["features"]:
        properties = feature["properties"]
        code = properties.get("ISO_A3")
        if not code or code == "-99":
            code = properties["ADM0_A3"]
        name = properties.get("NAME") or code
        geometry = feature["geometry"]
        if geometry["type"] == "Polygon":
            rings = [geometry["coordinates"][0]]
        elif geometry["type"] == "MultiPolygon":
            rings = [polygon[0] for polygon in geometry["coordinates"] if polygon]
        else:
            continue
        rings = [ring for ring in rings if len(ring) >= 3]
        if rings:
            features.append((code, name, rings))
    return features


def write_part(output: Path, index: int, features: list[tuple[str, str, list[list[list[float]]]]]) -> str:
    object_name = f"GeneratedWorldMapDataPart{index:02d}"
    lines = [
        "package com.mapmory.shared.presentation.map.data",
        "",
        "import com.mapmory.shared.presentation.map.domain.CountryPolygon",
        "import com.mapmory.shared.presentation.map.domain.GeoPoint",
        "",
        f"internal object {object_name} {{",
        "    val countries: List<CountryPolygon> = listOf(",
    ]
    for code, name, rings in features:
        lines.append(f"        country({json.dumps(code)}, {json.dumps(name, ensure_ascii=False)}, listOf(")
        for ring_index, ring in enumerate(rings):
            comma = "," if ring_index < len(rings) - 1 else ""
            lines.append(f"            {ring_expression(ring)}{comma}")
        lines.append("        )),")
    lines += [
        "    )",
        "",
        "    private fun country(",
        "        code: String,",
        "        name: String,",
        "        rings: List<List<GeoPoint>>,",
        "    ): CountryPolygon = CountryPolygon(code = code, name = name, rings = rings)",
        "",
        "    private fun p(longitude: Float, latitude: Float): GeoPoint =",
        "        GeoPoint(longitude = longitude, latitude = latitude)",
        "}",
    ]
    (output / f"{object_name}.kt").write_text("\n".join(lines) + "\n")
    return object_name


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("source", type=Path)
    parser.add_argument(
        "--output",
        type=Path,
        default=Path("shared/src/commonMain/kotlin/com/mapmory/shared/presentation/map/data"),
    )
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)

    for old in args.output.glob("GeneratedWorldMapDataPart*.kt"):
        old.unlink()

    features = read_features(args.source)
    chunk_size = math.ceil(len(features) / PART_COUNT)
    names = [
        write_part(args.output, index, features[index * chunk_size:(index + 1) * chunk_size])
        for index in range(PART_COUNT)
        if features[index * chunk_size:(index + 1) * chunk_size]
    ]

    parts = ",\n".join(f"        {name}.countries" for name in names)
    aggregator = f"""package com.mapmory.shared.presentation.map.data

import com.mapmory.shared.presentation.map.domain.CountryPolygon

/** Generated from Natural Earth Admin 0 Countries (110m). */
internal object GeneratedWorldMapData {{
    val countries: List<CountryPolygon> = listOf(
{parts},
    ).flatten()
}}
"""
    (args.output / "GeneratedWorldMapData.kt").write_text(aggregator)
    print(f"generated {len(features)} features in {len(names)} parts")


if __name__ == "__main__":
    main()
