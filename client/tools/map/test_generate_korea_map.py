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
    def test_시도별_번들_리소스는_정규_코드를_사용한다(self):
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
                self.assertTrue(district["rings"])
                self.assertTrue(all(len(ring) >= 4 for ring in district["rings"]))

        gangwon = json.loads((resource_dir / "korea-districts-42.json").read_text())
        seoul = json.loads((resource_dir / "korea-districts-11.json").read_text())
        incheon = json.loads((resource_dir / "korea-districts-28.json").read_text())
        self.assertEqual("51760", next(item["code"] for item in gangwon["districts"] if item["name"] == "평창군"))
        self.assertEqual("11680", next(item["code"] for item in seoul["districts"] if item["name"] == "강남구"))
        self.assertEqual(
            {"28125", "28155", "28177", "28275", "28290"},
            {item["code"] for item in incheon["districts"] if item["code"] in {"28125", "28155", "28177", "28275", "28290"}},
        )
        self.assertEqual(11, len(incheon["districts"]))

    def test_외곽_링은_폴리곤_구멍을_제외한다(self):
        geometry = {
            "type": "Polygon",
            "coordinates": [
                [[0, 0], [4, 0], [4, 4], [0, 4], [0, 0]],
                [[1, 1], [2, 1], [2, 2], [1, 2], [1, 1]],
            ],
        }

        self.assertEqual(1, len(MODULE.outer_rings(geometry)))

    def test_외곽_링은_멀티폴리곤의_각_외곽을_유지한다(self):
        geometry = {
            "type": "MultiPolygon",
            "coordinates": [
                [[[0, 0], [1, 0], [1, 1], [0, 0]]],
                [[[2, 2], [3, 2], [3, 3], [2, 2]], [[2.2, 2.2], [2.4, 2.2], [2.4, 2.4], [2.2, 2.2]]],
            ],
        }

        self.assertEqual(2, len(MODULE.outer_rings(geometry)))

    def test_링_단순화는_닫힌_링을_유지한다(self):
        ring = [[0, 0], [1, 0.001], [2, 0], [2, 2], [0, 2], [0, 0]]

        simplified = MODULE.simplify_ring(ring, 0.01)

        self.assertEqual(simplified[0], simplified[-1])
        self.assertLess(len(simplified), len(ring))
        self.assertIn([2, 2], simplified)

    def test_주요_시도_재정의는_대상_코드만_교체한다(self):
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

    def test_개요_재정의는_모든_시도에_하나의_소스를_사용한다(self):
        self.assertEqual(
            frozenset(MODULE.PROVINCE_NAMES),
            MODULE.PROVINCE_OVERRIDE_CODES,
        )

    def test_시도_조각은_좌표_개수에_따라_분할된다(self):
        feature = lambda code, count: (code, code, [[[index, 0] for index in range(count)]])

        parts = MODULE.partition_province_features(
            [feature("A", 4), feature("B", 3), feature("C", 5)],
            max_points=7,
        )

        self.assertEqual([["A", "B"], ["C"]], [[feature[0] for feature in part] for part in parts])

    def test_큰_시도의_링은_시도_코드를_유지한_채_여러_조각으로_나뉜다(self):
        feature = ("KR-41", "경기도", [
            [[index, 0] for index in range(4)],
            [[index, 1] for index in range(4)],
        ])

        parts = MODULE.partition_province_features([feature], max_points=5)

        self.assertEqual(2, len(parts))
        self.assertEqual(["KR-41", "KR-41"], [part[0][0] for part in parts])

    def test_기존_평창_코드를_앱_코드로_정규화한다(self):
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

    def test_정규화된_재정의_경계의_시도_코드를_사용한다(self):
        feature = {
            "type": "Feature",
            "properties": {"code": "28125", "name": "제물포구", "provinceCode": "KR-28"},
            "geometry": {"type": "Polygon", "coordinates": [[[126, 37], [127, 37], [126, 38], [126, 37]]]},
        }
        with tempfile.TemporaryDirectory() as directory:
            directory = Path(directory)
            source = directory / "districts.json"
            locations = directory / "KoreanDistrictCode.kt"
            source.write_text(json.dumps({"features": [feature]}, ensure_ascii=False))
            locations.write_text('KoreanDistrictCode("28125", "인천광역시 제물포구", "KR-28")')

            boundaries, skipped = MODULE.canonicalize_districts(source, locations)

        self.assertEqual([], skipped)
        self.assertEqual(["28125"], [boundary.code for boundary in boundaries])

    def test_재정의_경계를_기존_경계에_추가한다(self):
        base = [MODULE.Boundary("KR-28", "28177", "미추홀구", [[[0, 0], [1, 0], [0, 1]]])]
        overrides = [MODULE.Boundary("KR-28", "28125", "제물포구", [[[2, 2], [3, 2], [2, 3]]])]

        merged = MODULE.merge_district_boundaries(base, overrides)

        self.assertEqual(["28125", "28177"], [boundary.code for boundary in merged])


if __name__ == "__main__":
    unittest.main()
