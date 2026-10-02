package com.example

import com.example.domain.profileCrop
import org.junit.Assert.*
import org.junit.Test

class ProfileCropTest {
    @Test fun portraitUsesCenteredSquareAndZoomHalvesTheSourceArea() {
        assertEquals(1000, profileCrop(1000, 2000, 280f, 1f, 0f, 0f).side)
        assertEquals(500, profileCrop(1000, 2000, 280f, 1f, 0f, 0f).top)
        assertEquals(500, profileCrop(1000, 2000, 280f, 2f, 0f, 0f).side)
    }
    @Test fun extremeDragNeverExportsOutsideImage() {
        val crop = profileCrop(1200, 800, 280f, 4f, 100000f, -100000f)
        assertEquals(0, crop.left)
        assertEquals(800, crop.top + crop.side)
    }
}
