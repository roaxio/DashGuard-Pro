package io.github.roaxio.dashguardpro

import io.github.roaxio.dashguardpro.viewmodel.DashcamAlertPresenter
import io.github.roaxio.dashguardpro.viewmodel.state.DashcamUiState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DashcamAlertPresenterTest {

    private lateinit var uiState: MutableStateFlow<DashcamUiState>

    @Before
    fun setup() {
        uiState = MutableStateFlow(DashcamUiState())
    }

    private fun createPresenter(scope: kotlinx.coroutines.CoroutineScope): DashcamAlertPresenter {
        return DashcamAlertPresenter(
            scope = scope,
            getCurrentUiState = { uiState.value },
            updateUiState = { transform -> uiState.update(transform) }
        )
    }

    @Test
    fun testShowTransientMessage_autoDismissesAfterDelay() = runTest {
        val presenter = createPresenter(this)

        presenter.showTransientMessage("Incident Detected", durationMs = 2500L)
        assertEquals("Incident Detected", uiState.value.recentIncidentMessage)

        advanceTimeBy(1000L)
        assertEquals("Incident Detected", uiState.value.recentIncidentMessage)

        advanceTimeBy(1600L)
        assertNull(uiState.value.recentIncidentMessage)
    }

    @Test
    fun testClearIncidentMessage_cancelsJobAndClearsImmediately() = runTest {
        val presenter = createPresenter(this)

        presenter.showTransientMessage("Incident Detected", durationMs = 2500L)
        assertEquals("Incident Detected", uiState.value.recentIncidentMessage)

        presenter.clearIncidentMessage()
        assertNull(uiState.value.recentIncidentMessage)

        advanceTimeBy(3000L)
        assertNull(uiState.value.recentIncidentMessage)
    }

    @Test
    fun testUnplugCountdown_decrementsAndTriggersStop() = runTest {
        val presenter = createPresenter(this)
        var stopCalled = false

        presenter.startUnplugCountdown(
            seconds = 3,
            isRecording = { true },
            onStopRecording = { stopCalled = true }
        )

        assertTrue(presenter.isUnplugCountdownActive)
        assertEquals(3, uiState.value.unplugCountdownSeconds)

        testScheduler.runCurrent()
        assertEquals(3, uiState.value.unplugCountdownSeconds)

        advanceTimeBy(1000L)
        testScheduler.runCurrent()
        assertEquals(2, uiState.value.unplugCountdownSeconds)

        advanceTimeBy(1000L)
        testScheduler.runCurrent()
        assertEquals(1, uiState.value.unplugCountdownSeconds)

        advanceTimeBy(1000L)
        testScheduler.runCurrent()
        assertNull(uiState.value.unplugCountdownSeconds)
        assertTrue(stopCalled)
        assertFalse(presenter.isUnplugCountdownActive)
    }

    @Test
    fun testUnplugCountdown_cancelledWhenRecordingStopsEarly() = runTest {
        val presenter = createPresenter(this)
        var recording = true
        var stopCalled = false

        presenter.startUnplugCountdown(
            seconds = 5,
            isRecording = { recording },
            onStopRecording = { stopCalled = true }
        )

        testScheduler.runCurrent()
        assertEquals(5, uiState.value.unplugCountdownSeconds)

        advanceTimeBy(1000L)
        testScheduler.runCurrent()
        assertEquals(4, uiState.value.unplugCountdownSeconds)

        recording = false
        advanceTimeBy(1000L)
        testScheduler.runCurrent()
        assertNull(uiState.value.unplugCountdownSeconds)
        assertFalse(stopCalled)
    }

    @Test
    fun testCancelUnplugCountdown_abortsActiveCountdown() = runTest {
        val presenter = createPresenter(this)
        var stopCalled = false

        presenter.startUnplugCountdown(
            seconds = 5,
            isRecording = { true },
            onStopRecording = { stopCalled = true }
        )

        presenter.cancelUnplugCountdown()
        assertFalse(presenter.isUnplugCountdownActive)
        assertNull(uiState.value.unplugCountdownSeconds)

        advanceTimeBy(6000L)
        assertFalse(stopCalled)
    }

    @Test
    fun testTriggerSnapshotFlash_flashesTemporarily() = runTest {
        val presenter = createPresenter(this)

        presenter.triggerSnapshotFlash(durationMs = 150L)
        assertTrue(uiState.value.isSnapshotFlashActive)

        advanceTimeBy(160L)
        assertFalse(uiState.value.isSnapshotFlashActive)
    }

    @Test
    fun testSpeedLockoutAlert_showAndDismiss() = runTest {
        val presenter = createPresenter(this)

        assertFalse(uiState.value.isSpeedLockoutAlertVisible)
        presenter.showSpeedLockoutAlert()
        assertTrue(uiState.value.isSpeedLockoutAlertVisible)

        presenter.dismissSpeedLockoutAlert()
        assertFalse(uiState.value.isSpeedLockoutAlertVisible)
    }

    @Test
    fun testImpactAlert_showAndAutoDismiss() = runTest {
        val presenter = createPresenter(this)

        presenter.showImpactAlert(gValue = 3.2f, isSentry = false, autoDismissMs = 4000L)
        assertTrue(uiState.value.showImpactAlert)
        assertEquals(3.2f, uiState.value.impactG)

        advanceTimeBy(2000L)
        assertTrue(uiState.value.showImpactAlert)

        advanceTimeBy(2100L)
        assertFalse(uiState.value.showImpactAlert)
    }

    @Test
    fun testImpactAlert_sentryModeDoesNotShowAlert() = runTest {
        val presenter = createPresenter(this)

        presenter.showImpactAlert(gValue = 4.0f, isSentry = true, autoDismissMs = 4000L)
        assertFalse(uiState.value.showImpactAlert)
        assertEquals(4.0f, uiState.value.impactG)
    }

    @Test
    fun testResetAllAlerts_cleansUpAllJobs() = runTest {
        val presenter = createPresenter(this)

        presenter.showTransientMessage("Msg", durationMs = 10000L)
        presenter.startUnplugCountdown(10, { true }, {})
        presenter.triggerSnapshotFlash(10000L)
        presenter.showImpactAlert(2.5f, false, 10000L)

        presenter.resetAllAlerts()
        assertFalse(presenter.isUnplugCountdownActive)

        // Advance time and verify no residual exceptions or triggers
        advanceTimeBy(15000L)
    }
}
