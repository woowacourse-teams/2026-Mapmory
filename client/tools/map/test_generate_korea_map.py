import importlib.util
import json
import sys
import tempfile
import unittest
from pathlib import Path


MODULE_PATH = Path(__file__).with_name("generate_korea_map.py")
SPEC = importlib.util.spec_from_file_location("generate_korea_map", MODULE_PATH)
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
sys.modules["generate_korea_map"] = MODULE
SPEC.loader.exec_module(MODULE)


class GenerateKoreaMapTest(unittest.TestCase):
    def test_bundled_resources_have_canonical_codes_by_province(self):
        client_dir = MODULE_PATH.parents[2]
        resource_dir = client_dir / "shared/src/commonMain/composeResources/files"
        locations_file = client_dir / "shared/src/commonMain/kotlin/com/mapmory/shared/domain/model/KoreanDistrictCode.kt"
        locations = MODULE.selectable_locations(MODULE.read_locations(locations_file))
        locations_by_code = {location.code: location for location in locations}
        resources = sorted(resource_dir.glob("korea-districts-*.json"))

        self.assertEqual(17, len(resources))
        for resource in resources:
            payload = json.loads(resource.read_text())
            province_code = payload["provinceCode"]
            self.assertTrue(payload["districts"])
            for district in payload["districts"]:
                self.assertEqual(province_code, locations_by_code[district["code"]].province_code)

        gangwon = json.loads((resource_dir / "korea-districts-42.json").read_text())
        seoul = json.loads((resource_dir / "korea-districts-11.json").read_text())
        self.assertEqual("51760", next(item["code"] for item in gangwon["districts"] if item["name"] == "평창군"))
        self.assertEqual("11680", next(item["code"] for item in seoul["districts"] if item["name"] == "강남구"))

    def test_outer_rings_discards_polygon_holes(self):
        geometry = {
            "type": "Polygon",
            "coordinates": [
                [[0, 0], [4, 0], [4, 4], [0, 4], [0, 0]],
                [[1, 1], [2, 1], [2, 2], [1, 2], [1, 1]],
            ],
        }

        self.assertEqual(1, len(MODULE.outer_rings(geometry)))

    def test_outer_rings_keeps_each_multipolygon_exterior(self):
        geometry = {
            "type": "MultiPolygon",
            "coordinates": [
                [[[0, 0], [1, 0], [1, 1], [0, 0]]],
                [[[2, 2], [3, 2], [3, 3], [2, 2]], [[2.2, 2.2], [2.4, 2.2], [2.4, 2.4], [2.2, 2.2]]],
            ],
        }

        self.assertEqual(2, len(MODULE.outer_rings(geometry)))

    def test_simplify_ring_preserves_closed_ring(self):
        ring = [[0, 0], [1, 0.001], [2, 0], [2, 2], [0, 2], [0, 0]]

        simplified = MODULE.simplify_ring(ring, 0.01)

        self.assertEqual(simplified[0], simplified[-1])
        self.assertLess(len(simplified), len(ring))
        self.assertIn([2, 2], simplified)

    def test_major_province_override_replaces_only_target_codes(self):
        base = [
            ("KR-11", "서울특별시", [[[0, 0], [1, 0], [0, 1]]]),
            ("KR-41", "경기도", [[[2, 2], [3, 2], [2, 3]]]),
        ]
        overrides = [
            ("KR-11", "서울특별시", [[[10, 10], [11, 10], [10, 11]]]),
        ]

        merged = MODULE.merge_province_features(base, overrides, frozenset({"KR-11"}))

        self.assertEqual(10, merged[0][2][0][0][0])
        self.assertEqual(base[1], merged[1])

    def test_overview_override_uses_one_source_for_every_province(self):
        self.assertEqual(
            frozenset(MODULE.PROVINCE_NAMES),
            MODULE.PROVINCE_OVERRIDE_CODES,
        )

    def test_province_parts_are_split_by_coordinate_count(self):
        feature = lambda code, count: (code, code, [[[index, 0] for index in range(count)]])

        parts = MODULE.partition_province_features(
            [feature("A", 4), feature("B", 3), feature("C", 5)],
            max_points=7,
        )

        self.assertEqual([["A", "B"], ["C"]], [[feature[0] for feature in part] for part in parts])

    def test_canonicalizes_legacy_pyeongchang_code_to_app_code(self):
        feature = {
            "type": "Feature",
            "properties": {"code": "32340", "name": "평창군"},
            "geometry": {"type": "Polygon", "coordinates": [[[127, 37], [128, 37], [128, 38], [127, 37]]]},
        }
        with tempfile.TemporaryDirectory() as directory:
            directory = Path(directory)
            source = directory / "districts.json"
            locations = directory / "KoreanDistrictCode.kt"
            source.write_text(json.dumps({"features": [feature]}, ensure_ascii=False))
            locations.write_text('KoreanDistrictCode("51760", "강원특별자치도 평창군", "KR-42")')

            boundaries, skipped = MODULE.canonicalize_districts(source, locations)

        self.assertEqual([], skipped)
        self.assertEqual(["51760"], [boundary.code for boundary in boundaries])


if __name__ == "__main__":
    unittest.main()
