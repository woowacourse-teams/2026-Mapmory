#!/usr/bin/env python3
"""Build the pinned Daegu/Gyeongbuk overview correction from matching KOSTAT sources.

Requires Shapely at development time only. Use the same 2018 source revision for
both inputs; apply the transfer before simplifying so shared borders remain aligned.
"""
import argparse
import json
from pathlib import Path

from shapely.geometry import mapping, shape
from generate_korea_map import (
    DEFAULT_PROVINCE_OVERRIDE_TOLERANCE,
    PROVINCE_NAMES,
    outer_rings,
    province_ring_tolerance,
    simplify_ring,
)


def corrected_features(provinces, districts):
    gunwi = shape(next(f['geometry'] for f in districts['features'] if str(f['properties']['code']) == '37310'))
    result = []
    for source_code, code in [('22', 'KR-27'), ('37', 'KR-47')]:
        original = shape(next(f['geometry'] for f in provinces['features'] if str(f['properties']['code']) == source_code))
        corrected = original.union(gunwi) if code == 'KR-27' else original.difference(gunwi)
        if not corrected.is_valid:
            raise ValueError(f'Invalid corrected boundary: {code}')
        # Difference can reorder untouched island rings and change RDP results.
        # Preserve those source rings exactly (including Dokdo's fine outline).
        if code == 'KR-47':
            source_geometry = next(f['geometry'] for f in provinces['features'] if str(f['properties']['code']) == source_code)
            polygons = source_geometry['coordinates'] if source_geometry['type'] == 'MultiPolygon' else [source_geometry['coordinates']]
            source_rings = []
            for polygon in polygons:
                geometry = {'type': 'Polygon', 'coordinates': polygon}
                part = shape(geometry)
                source_rings.extend(outer_rings(mapping(part.difference(gunwi))) if part.intersects(gunwi) else outer_rings(geometry))
        else:
            source_rings = outer_rings(mapping(corrected))
        rings = [simplify_ring(ring, province_ring_tolerance(code, ring, DEFAULT_PROVINCE_OVERRIDE_TOLERANCE))
                 for ring in source_rings]
        rings = [[[round(x, 5), round(y, 5)] for x, y in ring] for ring in rings if len(ring) >= 4]
        result.append({'type': 'Feature', 'properties': {'shapeISO': code, 'name': PROVINCE_NAMES[code]},
                       'geometry': {'type': 'MultiPolygon', 'coordinates': [[ring] for ring in rings]}})
    return {'type': 'FeatureCollection', 'features': result}


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('provinces', type=Path)
    parser.add_argument('districts', type=Path)
    parser.add_argument('--output', type=Path, default=Path(__file__).with_name('data') / 'gunwi-province-overrides.geojson')
    args = parser.parse_args()
    args.output.write_text(json.dumps(corrected_features(json.loads(args.provinces.read_text()),
                                                        json.loads(args.districts.read_text())),
                                     ensure_ascii=False, separators=(',', ':')) + '\n')
