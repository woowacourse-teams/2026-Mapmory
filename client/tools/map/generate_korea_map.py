#!/usr/bin/env python3
"""Generate Kotlin province rings from a geoBoundaries KOR ADM1 GeoJSON file."""

from __future__ import annotations

import argparse
import json
import math
from pathlib import Path

PART_COUNT = 4
NAMES = {
    "KR-11": "서울특별시", "KR-26": "부산광역시", "KR-27": "대구광역시",
    "KR-28": "인천광역시", "KR-29": "광주광역시", "KR-30": "대전광역시",
    "KR-31": "울산광역시", "KR-41": "경기도", "KR-42": "강원특별자치도",
    "KR-43": "충청북도", "KR-44": "충청남도", "KR-45": "전북특별자치도",
    "KR-46": "전라남도", "KR-47": "경상북도", "KR-48": "경상남도",
    "KR-49": "제주특별자치도", "KR-50": "세종특별자치시",
}


def number(value: float) -> str:
    value = round(float(value), 5)
    if abs(value) < 0.000005:
        value = 0.0
    result = f"{value:.5f}".rstrip("0").rstrip(".")
    return (result if result not in ("", "-0") else "0") + "f"


def ring_expression(ring: list[list[float]]) -> str:
    points = ", ".join(f"p({number(lon)}, {number(lat)})" for lon, lat in ring)
    return f"listOf({points})"


def read_features(source: Path):
    result = []
    for feature in json.loads(source.read_text())["features"]:
        properties = feature["properties"]
        code = properties["shapeISO"]
        geometry = feature["geometry"]
        if geometry["type"] == "Polygon":
            rings = [geometry["coordinates"][0]]
        elif geometry["type"] == "MultiPolygon":
            rings = [polygon[0] for polygon in geometry["coordinates"] if polygon]
        else:
            continue
        result.append((code, NAMES.get(code, properties["shapeName"]), [r for r in rings if len(r) >= 3]))
    return result


def write_part(output: Path, index: int, features) -> str:
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


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("source", type=Path)
    parser.add_argument("--output", type=Path, default=Path("shared/src/commonMain/kotlin/com/mapmory/shared/presentation/map/data"))
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    for old in args.output.glob("GeneratedKoreaMapDataPart*.kt"):
        old.unlink()
    features = read_features(args.source)
    chunk_size = math.ceil(len(features) / PART_COUNT)
    names = [
        write_part(args.output, index, features[index * chunk_size:(index + 1) * chunk_size])
        for index in range(PART_COUNT)
        if features[index * chunk_size:(index + 1) * chunk_size]
    ]
    parts = ",\n".join(f"        {name}.provinces" for name in names)
    aggregator = f"""package com.mapmory.shared.presentation.map.data

import com.mapmory.shared.presentation.map.domain.ProvincePolygon

/** Generated from geoBoundaries KOR ADM1 simplified geometry. */
internal object GeneratedKoreaMapData {{
    val provinces: List<ProvincePolygon> = listOf(
{parts},
    ).flatten()
}}
"""
    (args.output / "GeneratedKoreaMapData.kt").write_text(aggregator)
    print(f"generated {len(features)} provinces in {len(names)} parts")


if __name__ == "__main__":
    main()
