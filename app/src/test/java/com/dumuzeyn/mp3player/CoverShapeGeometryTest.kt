package com.dumuzeyn.mp3player

import kotlin.math.hypot
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverShapeGeometryTest {
    @Test fun hexagonHasSixEqualSidesOnNonSquareArtwork() {
        val vertices = CoverShapeGeometry.vertices("hexagon", 140f, 100f)
        assertEquals(6, vertices.size)
        val sides = vertices.indices.map { index ->
            val first = vertices[index]
            val next = vertices[(index + 1) % vertices.size]
            hypot(first.first - next.first, first.second - next.second)
        }
        assertTrue(sides.maxOrNull()!! - sides.minOrNull()!! < 0.001f)
    }

    @Test fun starHasFiveSymmetricOuterPoints() {
        val vertices = CoverShapeGeometry.vertices("star", 100f, 100f)
        assertEquals(10, vertices.size)
        val outer = vertices.filterIndexed { index, _ -> index % 2 == 0 }
        val radii = outer.map { hypot(it.first - 50f, it.second - 50f) }
        assertTrue(radii.maxOrNull()!! - radii.minOrNull()!! < 0.001f)
        assertTrue(vertices.any { it.second == 0f })
    }

    @Test fun triangleIsWideAndVerticallyCentered() {
        val vertices = CoverShapeGeometry.vertices("triangle", 100f, 100f)
        assertEquals(3, vertices.size)
        assertTrue(vertices[1].first - vertices[0].first > vertices[2].second - vertices[0].second)
        assertEquals(50f, vertices[1].second, 0.001f)
        assertEquals(100f, vertices[0].second + vertices[2].second, 0.001f)
    }

    @Test fun rotatedSquareAlwaysFitsItsOriginalBounds() {
        for (degree in 0..90) {
            val scale = CoverShapeGeometry.rotationFitScale("rounded", degree.toFloat())
            val radians = degree * PI / 180.0
            val transformedExtent = scale * (abs(cos(radians)) + abs(sin(radians)))
            assertTrue(transformedExtent <= 1.0001)
        }
        assertEquals(1f, CoverShapeGeometry.rotationFitScale("circle", 45f), 0f)
    }
}
