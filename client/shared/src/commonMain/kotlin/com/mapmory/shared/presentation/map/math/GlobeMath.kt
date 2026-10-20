package com.mapmory.shared.presentation.map.math

import androidx.compose.ui.geometry.Offset
import com.mapmory.shared.presentation.map.domain.GeoPoint
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

data class Vec3(
    val x: Float,
    val y: Float,
    val z: Float,
) {
    operator fun plus(other: Vec3): Vec3 = Vec3(x + other.x, y + other.y, z + other.z)
    operator fun minus(other: Vec3): Vec3 = Vec3(x - other.x, y - other.y, z - other.z)
    operator fun times(value: Float): Vec3 = Vec3(x * value, y * value, z * value)

    fun dot(other: Vec3): Float = x * other.x + y * other.y + z * other.z

    fun cross(other: Vec3): Vec3 = Vec3(
        x = y * other.z - z * other.y,
        y = z * other.x - x * other.z,
        z = x * other.y - y * other.x,
    )

    fun normalized(): Vec3 {
        val length = sqrt(x * x + y * y + z * z)
        return if (length <= EPSILON) this else this * (1f / length)
    }
}

data class Quaternion(
    val w: Float,
    val x: Float,
    val y: Float,
    val z: Float,
) {
    operator fun times(other: Quaternion): Quaternion = Quaternion(
        w = w * other.w - x * other.x - y * other.y - z * other.z,
        x = w * other.x + x * other.w + y * other.z - z * other.y,
        y = w * other.y - x * other.z + y * other.w + z * other.x,
        z = w * other.z + x * other.y - y * other.x + z * other.w,
    )

    fun conjugate(): Quaternion = Quaternion(w, -x, -y, -z)

    fun normalized(): Quaternion {
        val length = sqrt(w * w + x * x + y * y + z * z)
        return if (length <= EPSILON) Identity else {
            Quaternion(w / length, x / length, y / length, z / length)
        }
    }

    fun rotate(vector: Vec3): Vec3 {
        val point = Quaternion(0f, vector.x, vector.y, vector.z)
        val rotated = this * point * conjugate()
        return Vec3(rotated.x, rotated.y, rotated.z)
    }

    companion object {
        val Identity = Quaternion(1f, 0f, 0f, 0f)

        fun between(from: Vec3, to: Vec3): Quaternion {
            val start = from.normalized()
            val end = to.normalized()
            val dot = start.dot(end).coerceIn(-1f, 1f)

            if (dot < -0.9999f) {
                val axis = if (abs(start.x) < abs(start.z)) {
                    start.cross(Vec3(1f, 0f, 0f)).normalized()
                } else {
                    start.cross(Vec3(0f, 0f, 1f)).normalized()
                }
                return fromAxisAngle(axis, PI.toFloat())
            }

            return Quaternion(
                w = 1f + dot,
                x = start.y * end.z - start.z * end.y,
                y = start.z * end.x - start.x * end.z,
                z = start.x * end.y - start.y * end.x,
            ).normalized()
        }

        fun fromAxisAngle(axis: Vec3, angle: Float): Quaternion {
            val half = angle / 2f
            val sine = sin(half)
            return Quaternion(
                w = cos(half),
                x = axis.x * sine,
                y = axis.y * sine,
                z = axis.z * sine,
            ).normalized()
        }
    }
}

fun GeoPoint.toSphere(): Vec3 {
    val longitude = longitude * PI.toFloat() / 180f
    val latitude = latitude * PI.toFloat() / 180f
    val cosLatitude = cos(latitude)

    return Vec3(
        x = cosLatitude * sin(longitude),
        y = sin(latitude),
        z = cosLatitude * cos(longitude),
    )
}

fun mapToArcball(
    position: Offset,
    center: Offset,
    radius: Float,
): Vec3 {
    if (radius <= EPSILON) return Vec3(0f, 0f, 1f)

    var x = (position.x - center.x) / radius
    var y = -(position.y - center.y) / radius
    val lengthSquared = x * x + y * y

    if (lengthSquared <= 1f) {
        return Vec3(x, y, sqrt(1f - lengthSquared))
    }

    val length = sqrt(lengthSquared)
    x = x / max(length, EPSILON)
    y = y / max(length, EPSILON)
    return Vec3(x, y, 0f)
}

fun clipToFrontHemisphere(points: List<Vec3>): List<Vec3> {
    if (points.isEmpty()) return emptyList()

    val result = mutableListOf<Vec3>()
    var previous = points.last()
    var previousVisible = previous.z >= 0f

    points.forEach { current ->
        val currentVisible = current.z >= 0f

        when {
            previousVisible && currentVisible -> result += current
            previousVisible && !currentVisible -> result += horizonIntersection(previous, current)
            !previousVisible && currentVisible -> {
                result += horizonIntersection(previous, current)
                result += current
            }
        }

        previous = current
        previousVisible = currentVisible
    }

    return result
}

fun projectToScreen(
    point: Vec3,
    center: Offset,
    radius: Float,
): Offset = Offset(
    x = center.x + point.x * radius,
    y = center.y - point.y * radius,
)

private fun horizonIntersection(from: Vec3, to: Vec3): Vec3 {
    val denominator = from.z - to.z
    if (abs(denominator) <= EPSILON) return from

    val t = (from.z / denominator).coerceIn(0f, 1f)
    val point = from + (to - from) * t
    val length = sqrt(point.x * point.x + point.y * point.y)

    return if (length <= EPSILON) {
        Vec3(0f, 0f, 0f)
    } else {
        Vec3(point.x / length, point.y / length, 0f)
    }
}

private const val EPSILON = 0.00001f
