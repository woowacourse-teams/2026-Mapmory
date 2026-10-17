package com.mapmory.shared.presentation.map

import com.mapmory.shared.presentation.map.data.GeneratedKoreaMapData
import com.mapmory.shared.presentation.map.domain.GeoPoint
import com.mapmory.shared.presentation.map.domain.isWithinRegions
import com.mapmory.shared.presentation.map.math.Quaternion
import com.mapmory.shared.presentation.map.math.Vec3
import com.mapmory.shared.presentation.map.math.globeOrientationToCenter
import com.mapmory.shared.presentation.map.math.toSphere
import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CurrentLocationMapTest {
    @Test
    fun currentLocationSelectsKoreaOnlyInsideItsBoundary() {
        assertTrue(isWithinRegions(GeoPoint(126.9780f, 37.5665f), GeneratedKoreaMapData.provinces))
        assertFalse(isWithinRegions(GeoPoint(139.6917f, 35.6895f), GeneratedKoreaMapData.provinces))
    }

    @Test
    fun globeOrientationPlacesRequestedLocationAtTheCenter() {
        val point = GeoPoint(139.6917f, 35.6895f)
        val orientation = point.globeOrientationToCenter()
        val rotation = (
            Quaternion.fromAxisAngle(Vec3(1f, 0f, 0f), orientation.latitude) *
                Quaternion.fromAxisAngle(Vec3(0f, 1f, 0f), orientation.longitude)
            ).normalized()

        val centered = rotation.rotate(point.toSphere())
        assertTrue(centered.x in -0.001f..0.001f)
        assertTrue(centered.y in -0.001f..0.001f)
        assertTrue(centered.z > 0.999f)
    }
}
