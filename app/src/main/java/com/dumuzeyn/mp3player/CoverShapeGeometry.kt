package com.dumuzeyn.mp3player

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Regular polygons fitted inside the artwork bounds without stretching their sides. */
internal object CoverShapeGeometry {
    fun vertices(shape: String, width: Float, height: Float): List<Pair<Float, Float>> {
        val centerX = width / 2f
        val centerY = height / 2f
        val radius = min(width / 2f, height / 2f)
        return when (shape) {
            "triangle" -> listOf(
                Pair(width * 0.04f, height * 0.08f),
                Pair(width * 0.96f, height * 0.5f),
                Pair(width * 0.04f, height * 0.92f),
            )
            "hexagon" -> {
                val r = min(width / 2f, height / sqrt(3f))
                List(6) { index ->
                    val angle = PI * index / 3.0
                    Pair(centerX + (cos(angle) * r).toFloat(),
                        centerY + (sin(angle) * r).toFloat())
                }
            }
            "star" -> List(10) { index ->
                val angle = -PI / 2 + PI * index / 5.0
                val pointRadius = if (index % 2 == 0) radius else radius * 0.381966f
                Pair(centerX + (cos(angle) * pointRadius).toFloat(),
                    centerY + (sin(angle) * pointRadius).toFloat())
            }
            else -> emptyList()
        }
    }

    fun rotationFitScale(shape: String, degrees: Float): Float {
        if (shape == "circle") return 1f
        val radians = degrees * PI / 180.0
        val extent = abs(cos(radians)) + abs(sin(radians))
        return min(1.0, 0.995 / extent).toFloat()
    }
}
