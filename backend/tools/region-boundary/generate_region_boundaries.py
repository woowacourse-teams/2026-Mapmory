#!/usr/bin/env python3
"""Generate per-country boundary files that the app uses to filter photos.

The app judges on the phone whether a photo's GPS is inside the region a user
picked, so photo locations never leave the device. This script prepares the
boundary the server hands to the app for each country code in the region table.

Usage (from backend/):
    curl -L --fail -o /tmp/ne_10m_admin_0_map_units.geojson \\
      https://raw.githubusercontent.com/nvkelso/natural-earth-vector/v5.1.2/geojson/ne_10m_admin_0_map_units.geojson
    python3 tools/region-boundary/generate_region_boundaries.py /tmp/ne_10m_admin_0_map_units.geojson

Source: Natural Earth 1:10m Admin 0 map units (public domain). Map units keep
overseas territories (French Guiana, Reunion...) apart from their country.
Only outer rings are kept (holes such as lakes are dropped) and every ring is
simplified with Ramer-Douglas-Peucker, then rounded to 5 decimals (about 1 m).
"""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
from pathlib import Path

SOURCE_VERSION = "natural-earth-vector v5.1.2 ne_10m_admin_0_map_units"
SOURCE_SHA256 = "57da82be755f4afccd8f3b14251bb2752f5df1395f47d2d86f817470c4a48862"
# About 200 m. Small enough to keep coasts and borders for photo matching,
# large enough to drop points that do not change the shape.
TOLERANCE_DEGREES = 0.002
COUNTRY_MIGRATION = Path("src/main/resources/db/migration/V6__insert_country.sql")
OUTPUT = Path("src/main/resources/region-boundaries")


def country_codes(migration: Path) -> set[str]:
    return set(re.findall(r"\('([A-Z]{2})',", migration.read_text()))


def alpha_2(properties: dict) -> str | None:
    # Natural Earth stores -99 (France, Norway) or CN-TW (Taiwan) in ISO_A2 for
    # some countries and keeps the ISO 3166-1 code in ISO_A2_EH.
    for key in ("ISO_A2", "ISO_A2_EH"):
        code = properties.get(key)
        if code and len(code) == 2 and code.isalpha() and code.isupper():
            return code
    return None


def simplify(points: list[tuple[float, float]], tolerance: float) -> list[tuple[float, float]]:
    if len(points) < 3:
        return points
    keep = [False] * len(points)
    keep[0] = keep[-1] = True
    stack = [(0, len(points) - 1)]
    while stack:
        start, end = stack.pop()
        (x1, y1), (x2, y2) = points[start], points[end]
        dx, dy = x2 - x1, y2 - y1
        length = (dx * dx + dy * dy) ** 0.5
        farthest, index = 0.0, -1
        for i in range(start + 1, end):
            x0, y0 = points[i]
            if length:
                distance = abs(dy * x0 - dx * y0 + x2 * y1 - y2 * x1) / length
            else:
                distance = ((x0 - x1) ** 2 + (y0 - y1) ** 2) ** 0.5
            if distance > farthest:
                farthest, index = distance, i
        if farthest > tolerance:
            keep[index] = True
            stack += [(start, index), (index, end)]
    return [point for point, kept in zip(points, keep) if kept]


def outer_rings(geometry: dict) -> list[list[list[float]]]:
    if geometry["type"] == "Polygon":
        return [geometry["coordinates"][0]]
    if geometry["type"] == "MultiPolygon":
        return [polygon[0] for polygon in geometry["coordinates"] if polygon]
    return []


def ring(raw: list[list[float]]) -> list[list[float]]:
    points = [(round(lon, 5), round(lat, 5)) for lon, lat in raw]
    simplified = simplify(points, TOLERANCE_DEGREES)
    # A tiny island can collapse to a line. Keep its original outline instead.
    if len(simplified) < 4:
        simplified = points
    return [[lon, lat] for lon, lat in simplified]


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("source", type=Path)
    args = parser.parse_args()

    digest = hashlib.sha256(args.source.read_bytes()).hexdigest()
    if digest != SOURCE_SHA256:
        sys.exit(f"unexpected source sha256 {digest}; update SOURCE_VERSION and SOURCE_SHA256 together")

    codes = country_codes(COUNTRY_MIGRATION)
    rings_by_code: dict[str, list[list[list[float]]]] = {}
    for feature in json.loads(args.source.read_text())["features"]:
        code = alpha_2(feature["properties"])
        if code in codes:
            rings_by_code.setdefault(code, []).extend(
                ring(raw) for raw in outer_rings(feature["geometry"]) if len(raw) >= 4
            )

    OUTPUT.mkdir(parents=True, exist_ok=True)
    for old in OUTPUT.glob("*.json"):
        old.unlink()
    for code, rings in sorted(rings_by_code.items()):
        (OUTPUT / f"{code}.json").write_text(
            json.dumps({"rings": rings}, separators=(",", ":")) + "\n"
        )
    (OUTPUT / "VERSION").write_text(f"{SOURCE_VERSION} tolerance={TOLERANCE_DEGREES}\n")

    missing = sorted(codes - rings_by_code.keys())
    print(f"wrote {len(rings_by_code)} boundaries; no boundary for {missing}")


if __name__ == "__main__":
    main()
