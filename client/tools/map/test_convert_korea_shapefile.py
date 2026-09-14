import importlib.util
import sys
import unittest
from pathlib import Path


MODULE_PATH = Path(__file__).with_name("convert_korea_shapefile.py")
SPEC = importlib.util.spec_from_file_location("convert_korea_shapefile", MODULE_PATH)
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
sys.modules["convert_korea_shapefile"] = MODULE
SPEC.loader.exec_module(MODULE)


class ConvertKoreaShapefileTest(unittest.TestCase):
    def test_epsg5179_중심점은_대한민국_경위도로_변환된다(self):
        longitude, latitude = MODULE.epsg5179_to_wgs84(1_000_000, 2_000_000)

        self.assertAlmostEqual(127.5, longitude, places=4)
        self.assertAlmostEqual(38.0, latitude, places=4)

    def test_시계방향_링만_외곽으로_남기고_반시계방향_링은_구멍으로_제외한다(self):
        clockwise = [(0, 0), (0, 2), (2, 2), (2, 0), (0, 0)]
        counterclockwise = [(0, 0), (2, 0), (2, 2), (0, 2), (0, 0)]

        self.assertEqual([clockwise], MODULE.exterior_rings([clockwise, counterclockwise]))

    def test_구멍만_있는_레코드는_조용히_외곽으로_그리지_않는다(self):
        counterclockwise = [(0, 0), (2, 0), (2, 2), (0, 2), (0, 0)]

        with self.assertRaises(ValueError):
            MODULE.exterior_rings([counterclockwise])

    def test_2025_인천_행정동을_2026_개편_구로_매핑한다(self):
        self.assertEqual("28125", MODULE.reorganized_incheon_district_code("23020510", "만석동"))
        self.assertEqual("28155", MODULE.reorganized_incheon_district_code("23010650", "영종동"))
        self.assertEqual("28275", MODULE.reorganized_incheon_district_code("23080510", "검암경서동"))
        self.assertEqual("28290", MODULE.reorganized_incheon_district_code("23080810", "불로대곡동"))
        self.assertIsNone(MODULE.reorganized_incheon_district_code("11010530", "신포동"))


if __name__ == "__main__":
    unittest.main()
