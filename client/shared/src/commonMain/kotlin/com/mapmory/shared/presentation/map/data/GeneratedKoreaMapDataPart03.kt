package com.mapmory.shared.presentation.map.data

import com.mapmory.shared.presentation.map.domain.GeoPoint
import com.mapmory.shared.presentation.map.domain.ProvincePolygon

internal object GeneratedKoreaMapDataPart03 {
    val provinces: List<ProvincePolygon> = listOf(
        province("KR-29", "광주광역시", listOf(
            listOf(p(126.98556f, 35.23878f), p(126.93399f, 35.25459f), p(126.88035f, 35.24299f), p(126.84449f, 35.25077f), p(126.82288f, 35.23408f), p(126.85027f, 35.18488f), p(126.90474f, 35.15814f), p(126.96644f, 35.15008f), p(127.00582f, 35.19305f), p(126.98556f, 35.23878f))
        )),
        province("KR-27", "대구광역시", listOf(
            listOf(p(128.74132f, 35.84882f), p(128.75693f, 35.86189f), p(128.7623f, 35.87771f), p(128.75972f, 35.9105f), p(128.74897f, 35.91791f), p(128.74091f, 35.92773f), p(128.7406f, 35.953f), p(128.74545f, 35.95967f), p(128.74597f, 35.96757f), p(128.73326f, 35.9855f), p(128.69316f, 36.00933f), p(128.64034f, 36.00654f), p(128.57668f, 35.99961f), p(128.52965f, 35.96799f), p(128.53596f, 35.92241f), p(128.5188f, 35.90711f), p(128.50805f, 35.88745f), p(128.52635f, 35.88355f), p(128.52449f, 35.87357f), p(128.52025f, 35.86412f), p(128.50413f, 35.86197f), p(128.48914f, 35.86189f), p(128.47529f, 35.84621f), p(128.48128f, 35.82774f), p(128.49679f, 35.82283f), p(128.51136f, 35.8112f), p(128.52325f, 35.79254f), p(128.56242f, 35.76828f), p(128.57006f, 35.77358f), p(128.58433f, 35.79384f), p(128.59859f, 35.80298f), p(128.63094f, 35.80035f), p(128.68871f, 35.78813f), p(128.71104f, 35.79911f), p(128.71404f, 35.80849f), p(128.71414f, 35.81495f), p(128.72354f, 35.83494f), p(128.72437f, 35.84996f), p(128.73378f, 35.85063f), p(128.74132f, 35.84882f))
        )),
    )

    private fun province(
        code: String,
        name: String,
        rings: List<List<GeoPoint>>,
    ): ProvincePolygon = ProvincePolygon(code = code, name = name, rings = rings)

    private fun p(longitude: Float, latitude: Float): GeoPoint =
        GeoPoint(longitude = longitude, latitude = latitude)
}
