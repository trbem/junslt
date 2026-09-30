package com.jev.probe.advisor

import org.junit.Assert.*
import org.junit.Test

class CropMathTest {
    @Test fun mapsLetterboxedPreviewToImagePixels() {
        assertEquals(CropBounds(0, 240, 1000, 1680),
            CropMath.pixels(100f, 130f, 350f, 490f, 100f, 70f, 250f, 500f, 1000, 2000))
    }
    @Test fun clampsOutOfBoundsCropAndPreservesAtLeastOnePixel() {
        assertEquals(CropBounds(0, 0, 100, 200),
            CropMath.pixels(-10f, -10f, 300f, 400f, 0f, 0f, 100f, 200f, 100, 200))
        val tiny = CropMath.pixels(99f, 199f, 99f, 199f, 0f, 0f, 100f, 200f, 100, 200)
        assertEquals(1, tiny.right - tiny.left); assertEquals(1, tiny.bottom - tiny.top)
    }
}
