package com.apx.mouse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FpsAimProcessorTest {
    @Test fun convertsRelativeDeltaAroundConfiguredCenter() {
        val processor = FpsAimProcessor(centerX = 500f, centerY = 400f, radius = 100f)
        val point = processor.target(20f, -10f)!!
        assertEquals(520f, point.x, 0.001f)
        assertEquals(390f, point.y, 0.001f)
    }

    @Test fun appliesIndependentSensitivityAndAxisInversion() {
        val processor = FpsAimProcessor(
            centerX = 100f, centerY = 100f, radius = 100f,
            sensitivityX = 2f, sensitivityY = 0.5f,
            invertX = true, invertY = true
        )
        val point = processor.target(10f, 20f)!!
        assertEquals(80f, point.x, 0.001f)
        assertEquals(90f, point.y, 0.001f)
    }

    @Test fun clampsVectorToCameraRadius() {
        val processor = FpsAimProcessor(centerX = 0f, centerY = 0f, radius = 10f)
        val point = processor.target(30f, 40f)!!
        assertEquals(6f, point.x, 0.001f)
        assertEquals(8f, point.y, 0.001f)
    }

    @Test fun ignoresZeroAndNonFiniteDeltas() {
        val processor = FpsAimProcessor(centerX = 10f, centerY = 10f, radius = 5f)
        assertNull(processor.target(0f, 0f))
        assertNull(processor.target(Float.NaN, 1f))
        assertNull(processor.target(1f, Float.POSITIVE_INFINITY))
    }
}
