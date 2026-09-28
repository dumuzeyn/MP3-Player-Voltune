package com.dumuzeyn.mp3player

import kotlin.math.hypot
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
}
