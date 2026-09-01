package com.mapmory.shared.domain.model

import com.mapmory.shared.presentation.map.data.GeneratedWorldMapData
import kotlin.test.Test
import kotlin.test.assertTrue

class KoreanCountryNamesTest {
    @Test
    fun `세계_지도_모든_국가에_한글_이름이_있다`() {
        val missingCodes = GeneratedWorldMapData.countries
            .map { it.code }
            .filterNot(KoreanCountryNames.byCode::containsKey)

        assertTrue(
            missingCodes.isEmpty(),
            "한국어 국가명이 없는 코드: $missingCodes",
        )
    }
}
