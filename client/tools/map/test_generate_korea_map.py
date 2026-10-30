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
    def test_시도_외곽은_시군구_채움의_공유_변만_제거하고_구멍을_보존한다(self):
        districts = [
            MODULE.Boundary("KR-46", f"{x}{y}", "지역", [
                [[x, y], [x, y + 1], [x + 1, y + 1], [x + 1, y], [x, y]],
            ])
            for x in range(3) for y in range(3) if (x, y) != (1, 1)
        ]

        province, = MODULE.dissolve_overview_provinces(districts)

        self.assertEqual("KR-46", province.code)
        self.assertEqual(2, len(province.rings))
        signed_areas = [sum(a[0] * b[1] - b[0] * a[1] for a, b in zip(ring, ring[1:]))
                        for ring in province.rings]
        self.assertEqual([-18, 2], sorted(signed_areas))
        self.assertEqual(-16, sum(signed_areas))

    def test_꼭짓점만_닿는_섬은_각각_닫힌_시도_외곽으로_남는다(self):
        districts = [
            MODULE.Boundary("KR-28", str(x), "섬", [
                [[x, x], [x, x + 1], [x + 1, x + 1], [x + 1, x], [x, x]],
            ])
            for x in (0, 1)
        ]

        province, = MODULE.dissolve_overview_provinces(districts)

        self.assertEqual(2, len(province.rings))
        self.assertTrue(all(len(ring) == 5 and ring[0] == ring[-1] for ring in province.rings))

    def test_시도_외곽은_열린_링이나_겹친_채움_변을_허용하지_않는다(self):
        ring = [[0, 0], [0, 1], [1, 1], [1, 0], [0, 0]]
        with self.assertRaisesRegex(ValueError, "unclosed overview province source"):
            MODULE.dissolve_overview_provinces([MODULE.Boundary("KR-41", "A", "A", [ring[:-1]])])
        with self.assertRaisesRegex(ValueError, "duplicate directed overview edge"):
            MODULE.dissolve_overview_provinces([
                MODULE.Boundary("KR-41", "A", "A", [ring]),
                MODULE.Boundary("KR-41", "B", "B", [ring]),
            ])

    def test_전국_시도_외곽은_최종_시군구_채움의_변과_정확하게_일치한다(self):
        resource_dir = MODULE_PATH.parents[2] / "shared/src/commonMain/composeResources/files"
        districts = json.loads((resource_dir / "korea-district-overview-polygons.json").read_text())["districts"]
        boundaries = [MODULE.Boundary(d["provinceCode"], d["code"], d["name"], d["rings"])
                      for d in districts]
        bundled = (resource_dir / "korea-province-overview-polygons.json").read_text()
        with tempfile.TemporaryDirectory() as directory:
            MODULE.write_overview_province_polygons_resource(Path(directory), boundaries)
            self.assertEqual(bundled, (Path(directory) / "korea-province-overview-polygons.json").read_text())
        provinces = json.loads(bundled)["districts"]
        self.assertEqual(set(MODULE.PROVINCE_NAMES), {p["code"] for p in provinces})
        self.assertEqual(17, len(provinces))
        for province in provinces:
            source_edges = {
                (tuple(start), tuple(end))
                for district in districts if district["provinceCode"] == province["code"]
                for ring in district["rings"] for start, end in zip(ring, ring[1:])
                if start != end
            }
            expected = {edge for edge in source_edges if tuple(reversed(edge)) not in source_edges}
            actual = [(tuple(start), tuple(end))
                      for ring in province["rings"] for start, end in zip(ring, ring[1:])]
            self.assertEqual(expected, set(actual), province["code"])
            self.assertEqual(len(expected), len(actual), province["code"])
            self.assertTrue(all(ring[0] == ring[-1] for ring in province["rings"]))
            self.assertEqual(province["code"], province["provinceCode"])

    def test_같은_시의_일반구_접합부를_채움에서만_제거한다(self):
        source = MODULE.Boundary("KR-41", "41130", "성남시", [
            [[0, 0], [1, 0], [0.8, 1], [1.2, 2], [1, 3], [0, 3], [0, 0]],
            [[1, 0], [2, 0], [2, 3], [1, 3], [1.2, 2], [0.8, 1], [1, 0]],
        ])
        overview = MODULE.Boundary("KR-41", "41130", "성남시", [
            [[0, 0], [1, 0], [0.8, 1], [1, 3], [0, 3], [0, 0]],
            [[1, 0], [2, 0], [2, 3], [1, 3], [1.2, 2], [1, 0]],
        ])
        self.assertFalse(any(MODULE._point_in_ring((1, 1.5), ring) for ring in overview.rings))

        fill = MODULE.dissolve_overview_fill_rings(source, overview)

        self.assertEqual(1, len(fill.rings))
        self.assertTrue(MODULE._point_in_ring((1, 1.5), fill.rings[0]))
        self.assertFalse(MODULE._point_in_ring((2.1, 1.5), fill.rings[0]))
        self.assertEqual({(0, 0), (1, 0), (2, 0), (2, 3), (1, 3), (0, 3)},
                         set(map(tuple, fill.rings[0])))
        self.assertEqual(2, len(overview.rings))

    def test_분리된_섬은_접합부가_없으면_그대로_유지한다(self):
        boundary = MODULE.Boundary("KR-47", "47940", "울릉군", [
            [[0, 0], [1, 0], [0, 1], [0, 0]],
            [[2, 2], [3, 2], [2, 3], [2, 2]],
        ])
        self.assertIs(boundary, MODULE.dissolve_overview_fill_rings(boundary, boundary))

    def test_전국_채움은_기존_내부_경계선을_빠짐없이_공유한다(self):
        resource_dir = MODULE_PATH.parents[2] / "shared/src/commonMain/composeResources/files"
        outlines = json.loads((resource_dir / "korea-district-overview.json").read_text())
        districts = json.loads((resource_dir / "korea-district-overview-polygons.json").read_text())["districts"]
        owners = {}
        for district in districts:
            for ring in district["rings"]:
                for start, end in zip(ring, ring[1:]):
                    owners.setdefault(MODULE.normalized_edge(tuple(start), tuple(end)), set()).add(district["code"])
        for line in outlines["lines"]:
            for start, end in zip(line, line[1:]):
                edge = MODULE.normalized_edge(tuple(start), tuple(end))
                self.assertIn(edge, owners)
                self.assertTrue(len(owners[edge]) >= 2 or "27720" in owners[edge], edge)
        seongnam = next(d for d in districts if d["code"] == "41130")
        self.assertEqual(1, len(seongnam["rings"]))
        self.assertTrue(MODULE._point_in_ring((127.121, 37.413), seongnam["rings"][0]))

    def test_빈_단순화_링을_포함한_번들에서_전국_채움을_재생성한다(self):
        resource_dir = MODULE_PATH.parents[2] / "shared/src/commonMain/composeResources/files"
        boundaries = []
        for resource in sorted(resource_dir.glob("korea-districts-*.json")):
            payload = json.loads(resource.read_text())
            boundaries.extend(
                MODULE.Boundary(payload["provinceCode"], district["code"], district["name"], district["rings"])
                for district in payload["districts"]
            )
        overview = MODULE.simplify_overview_boundaries(boundaries)
        self.assertTrue(any(len(ring) < 4 for district in overview for ring in district.rings))
        fills = [MODULE.dissolve_overview_fill_rings(source, simplified)
                 for source, simplified in zip(boundaries, overview)]
        with tempfile.TemporaryDirectory() as directory:
            MODULE.write_overview_district_polygons_resource(Path(directory), fills)
            regenerated = json.loads((Path(directory) / "korea-district-overview-polygons.json").read_text())
        bundled = json.loads((resource_dir / "korea-district-overview-polygons.json").read_text())
        self.assertEqual(bundled, regenerated)

    def test_시도_재생성에도_군위_편입_보정을_자동_적용한다(self):
        # Even an old source must not overwrite the pinned transfer correction.
        features = [
            {"type": "Feature", "properties": {"shapeISO": code},
             "geometry": {"type": "Polygon", "coordinates": [[[0, 0], [1, 0], [1, 1], [0, 0]]]}}
            for code in MODULE.PROVINCE_NAMES
        ]
        correction_path = MODULE_PATH.with_name("data") / "gunwi-province-overrides.geojson"
        corrections = MODULE.read_province_features(correction_path)
        gunwi = (128.72472, 36.16710)
        for code, _, rings in corrections:
            self.assertEqual(code == "KR-27", any(MODULE._point_in_ring(gunwi, ring) for ring in rings))
        with tempfile.TemporaryDirectory() as directory:
            directory = Path(directory)
            source = directory / "old-source.json"
            source.write_text(json.dumps({"features": features}))
            MODULE.write_provinces(directory, source)
            generated = "\n".join(path.read_text() for path in directory.glob("GeneratedKoreaMapDataPart*.kt"))
            for _, _, rings in corrections:
                for ring in rings:
                    self.assertIn(MODULE.ring_expression(ring), generated)

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

    def test_전국_개요_채움_폴리곤은_정규_시군구_코드를_유지한다(self):
        client_dir = MODULE_PATH.parents[2]
        resource_dir = client_dir / "shared/src/commonMain/composeResources/files"
        locations_file = client_dir / "shared/src/commonMain/kotlin/com/mapmory/shared/domain/model/KoreanDistrictCode.kt"
        locations_by_code = {
            location.code: location
            for location in MODULE.selectable_locations(MODULE.read_locations(locations_file))
        }

        resource = json.loads((resource_dir / "korea-district-overview-polygons.json").read_text())
        districts = resource["districts"]

        self.assertEqual(230, len(districts))
        self.assertEqual(230, len({district["code"] for district in districts}))
        self.assertEqual(set(locations_by_code), {district["code"] for district in districts})
        for district in districts:
            self.assertEqual(locations_by_code[district["code"]].province_code, district["provinceCode"])
            self.assertTrue(district["rings"])
            self.assertTrue(all(len(ring) >= 4 and ring[0] == ring[-1] for ring in district["rings"]))

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

    def test_대구로_편입된_군위군은_경북과_맞닿는_경계를_유지한다(self):
        gunwi = MODULE.Boundary(
            "KR-27", "27720", "군위군",
            [[[0, 0], [1, 0], [1, 1], [0, 1], [0, 0]]],
        )
        neighbor = MODULE.Boundary(
            "KR-47", "47730", "의성군",
            [[[0, 1], [1, 1], [1, 2], [0, 2], [0, 1]]],
        )
        lines = MODULE.overview_boundary_lines([gunwi, neighbor])
        edges = {
            MODULE.normalized_edge(tuple(start), tuple(end))
            for line in lines
            for start, end in zip(line, line[1:])
        }
        self.assertEqual({
            MODULE.normalized_edge(tuple(start), tuple(end))
            for start, end in zip(gunwi.rings[0], gunwi.rings[0][1:])
        }, edges)

    def test_독도는_전국_개요에서도_주요_섬_윤곽을_유지한다(self):
        ring = [
            [131.86941, 37.2410],
            [131.86991, 37.2407],
            [131.8700, 37.2402],
            [131.86991, 37.2397],
            [131.86941, 37.2394],
            [131.86891, 37.2397],
            [131.86882, 37.2402],
            [131.86891, 37.2407],
            [131.86941, 37.2410],
        ]
        feature = {
            "type": "Feature",
            "properties": {"shapeISO": "KR-47"},
            "geometry": {"type": "Polygon", "coordinates": [ring]},
        }
        with tempfile.TemporaryDirectory() as directory:
            source = Path(directory) / "province.json"
            source.write_text(json.dumps({"features": [feature]}))

            provinces = MODULE.read_province_features(source, simplify_tolerance=0.002)

        self.assertEqual(1, len(provinces))
        self.assertGreater(len(provinces[0][2][0]), len(MODULE.simplify_ring(ring, 0.002)))

    def test_광주는_다른_시도와_같은_단순화_정밀도를_사용한다(self):
        ring = [[126.65, 35.05], [126.75, 35.051], [126.85, 35.05], [126.85, 35.15], [126.65, 35.15]]

        self.assertEqual(0.002, MODULE.province_ring_tolerance("KR-29", ring, 0.002))
        self.assertEqual(0.002, MODULE.province_ring_tolerance("KR-46", ring, 0.002))
        self.assertEqual(0.0, MODULE.province_ring_tolerance("KR-29", ring, 0.0))

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
