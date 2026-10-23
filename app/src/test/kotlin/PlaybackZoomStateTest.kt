package io.github.roaxio.dashguardpro

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import io.github.roaxio.dashguardpro.ui.components.playback.PlaybackZoomState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PlaybackZoomStateTest {

    private lateinit var zoomState: PlaybackZoomState

    @Before
    fun setup() {
        zoomState = PlaybackZoomState()
        zoomState.viewportSize = IntSize(1080, 1920)
    }

    @Test
    fun testInitialState_isNotZoomed() {
        assertEquals(1.0f, zoomState.scale, 0.001f)
        assertEquals(Offset.Zero, zoomState.offset)
        assertFalse(zoomState.isZoomed)
    }

    @Test
    fun testPinchZoom_scalesAndClampsWithinBounds() {
        // Zoom in by 2x
        zoomState.onTransform(zoomChange = 2.0f, panChange = Offset.Zero, centroid = Offset(540f, 960f))
        assertEquals(2.0f, zoomState.scale, 0.001f)
        assertTrue(zoomState.isZoomed)

        // Zoom beyond max (e.g. 10x) -> must clamp to maxScale (5.0f)
        zoomState.onTransform(zoomChange = 10.0f, panChange = Offset.Zero, centroid = Offset(540f, 960f))
        assertEquals(5.0f, zoomState.scale, 0.001f)

        // Zoom out below 1x -> must reset to 1.0f and Offset.Zero
        zoomState.onTransform(zoomChange = 0.1f, panChange = Offset.Zero, centroid = Offset(540f, 960f))
        assertEquals(1.0f, zoomState.scale, 0.001f)
        assertEquals(Offset.Zero, zoomState.offset)
        assertFalse(zoomState.isZoomed)
    }

    @Test
    fun testDoubleTap_togglesBetweenDefaultAndMagnified() {
        // First double tap: quick zooms to doubleTapScale (2.5x)
        zoomState.onDoubleTap(Offset(540f, 960f))
        assertEquals(2.5f, zoomState.scale, 0.001f)
        assertTrue(zoomState.isZoomed)

        // Second double tap: resets cleanly back to 1.0x (fit)
        zoomState.onDoubleTap(Offset(540f, 960f))
        assertEquals(1.0f, zoomState.scale, 0.001f)
        assertEquals(Offset.Zero, zoomState.offset)
        assertFalse(zoomState.isZoomed)
    }

    @Test
    fun testDrag_onlyActiveWhenZoomedAndBoundedToViewport() {
        // At 1.0x: drag has no effect (video is fit to screen)
        zoomState.onDrag(Offset(100f, 100f))
        assertEquals(Offset.Zero, zoomState.offset)

        // Zoom in to 2.0x
        zoomState.onTransform(zoomChange = 2.0f, panChange = Offset.Zero, centroid = Offset(540f, 960f))
        // Max pan: width*(2-1)/2 = 1080*1/2 = 540, height = 1920*1/2 = 960
        zoomState.onDrag(Offset(200f, 300f))
        assertEquals(200f, zoomState.offset.x, 1f)
        assertEquals(300f, zoomState.offset.y, 1f)

        // Drag far beyond boundaries -> must clamp
        zoomState.onDrag(Offset(5000f, 5000f))
        assertEquals(540f, zoomState.offset.x, 1f)
        assertEquals(960f, zoomState.offset.y, 1f)

        zoomState.onDrag(Offset(-10000f, -10000f))
        assertEquals(-540f, zoomState.offset.x, 1f)
        assertEquals(-960f, zoomState.offset.y, 1f)
    }

    @Test
    fun testReset_restoresScaleAndOffset() {
        zoomState.onTransform(zoomChange = 3.0f, panChange = Offset(50f, 50f), centroid = Offset(540f, 960f))
        assertTrue(zoomState.isZoomed)

        zoomState.reset()
        assertEquals(1.0f, zoomState.scale, 0.001f)
        assertEquals(Offset.Zero, zoomState.offset)
        assertFalse(zoomState.isZoomed)
    }
}
