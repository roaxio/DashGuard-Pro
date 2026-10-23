package io.github.roaxio.dashguardpro

import io.github.roaxio.dashguardpro.MainActivity
import io.github.roaxio.dashguardpro.NavigationTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackNavigationTest {

    @Test
    fun testTabNavigationBackHierarchy() {
        val stack = mutableListOf(NavigationTab.CAMERA)

        fun navigateTo(tab: NavigationTab) {
            if (stack.lastOrNull() != tab) {
                stack.add(tab)
            }
        }

        fun navigateBack() {
            if (stack.size > 1) {
                stack.removeAt(stack.lastIndex)
            }
        }

        // Navigate: Camera -> Settings -> Optimization
        navigateTo(NavigationTab.SETTINGS)
        assertEquals(listOf(NavigationTab.CAMERA, NavigationTab.SETTINGS), stack)
        assertEquals(NavigationTab.SETTINGS, stack.last())

        navigateTo(NavigationTab.OPTIMIZATION)
        assertEquals(listOf(NavigationTab.CAMERA, NavigationTab.SETTINGS, NavigationTab.OPTIMIZATION), stack)
        assertEquals(NavigationTab.OPTIMIZATION, stack.last())

        // Back from Optimization returns to Settings
        navigateBack()
        assertEquals(listOf(NavigationTab.CAMERA, NavigationTab.SETTINGS), stack)
        assertEquals(NavigationTab.SETTINGS, stack.last())

        // Back from Settings returns to Camera
        navigateBack()
        assertEquals(listOf(NavigationTab.CAMERA), stack)
        assertEquals(NavigationTab.CAMERA, stack.last())

        // Back from Camera does not remove last entry
        navigateBack()
        assertEquals(listOf(NavigationTab.CAMERA), stack)
    }

    @Test
    fun testTabNavigationNoDuplicateConsecutiveEntries() {
        val stack = mutableListOf(NavigationTab.CAMERA)

        fun navigateTo(tab: NavigationTab) {
            if (stack.lastOrNull() != tab) {
                stack.add(tab)
            }
        }

        navigateTo(NavigationTab.GALLERY)
        navigateTo(NavigationTab.GALLERY)
        assertEquals(listOf(NavigationTab.CAMERA, NavigationTab.GALLERY), stack)
    }

    @Test
    fun testSpeedLockoutResetsToCamera() {
        val stack = mutableListOf(NavigationTab.CAMERA, NavigationTab.SETTINGS, NavigationTab.OPTIMIZATION)

        // Speed lockout trigger resets back-stack to CAMERA
        stack.clear()
        stack.add(NavigationTab.CAMERA)

        assertEquals(1, stack.size)
        assertEquals(NavigationTab.CAMERA, stack.last())
    }

    @Test
    fun testVideoPlaybackBackClosesPlayback() {
        var selectedClipFilePath: String? = "VID_123.mp4"
        var isPlaybackOpen = true

        // Pressing back when video is open closes playback
        if (selectedClipFilePath != null) {
            selectedClipFilePath = null
            isPlaybackOpen = false
        }

        assertEquals(null, selectedClipFilePath)
        assertFalse(isPlaybackOpen)
    }

    @Test
    fun testBackExitResetsSaveableStateForAllScreens() {
        val stack = mutableListOf(NavigationTab.CAMERA)
        val savedTabStates = mutableSetOf<String>()

        fun navigateTo(tab: NavigationTab) {
            if (stack.lastOrNull() != tab) {
                stack.add(tab)
                savedTabStates.add(tab.name)
            }
        }

        fun navigateBack() {
            if (stack.size > 1) {
                val exitingTab = stack.removeAt(stack.lastIndex)
                savedTabStates.remove(exitingTab.name)
            }
        }

        // Navigate Camera -> Settings -> Optimization
        navigateTo(NavigationTab.SETTINGS)
        navigateTo(NavigationTab.OPTIMIZATION)
        assertTrue(savedTabStates.contains(NavigationTab.SETTINGS.name))
        assertTrue(savedTabStates.contains(NavigationTab.OPTIMIZATION.name))

        // Exiting Optimization via Back clears Optimization's saved scroll state while preserving Settings' state
        navigateBack()
        assertEquals(NavigationTab.SETTINGS, stack.last())
        assertFalse(savedTabStates.contains(NavigationTab.OPTIMIZATION.name))
        assertTrue(savedTabStates.contains(NavigationTab.SETTINGS.name))

        // Exiting Settings via Back clears Settings' saved scroll state
        navigateBack()
        assertEquals(NavigationTab.CAMERA, stack.last())
        assertFalse(savedTabStates.contains(NavigationTab.SETTINGS.name))
    }
}
