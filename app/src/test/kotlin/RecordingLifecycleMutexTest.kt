package io.github.roaxio.dashguardpro

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.roaxio.dashguardpro.viewmodel.DashcamViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RecordingLifecycleMutexTest {

    private lateinit var context: Context
    private lateinit var app: DashGuardApplication
    private lateinit var viewModel: DashcamViewModel

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        app = context as DashGuardApplication
        app.settingsRepository.updateSettings(
            app.settingsRepository.settings.value.copy(debugAllowPortraitRecording = true)
        )
        viewModel = DashcamViewModel(app)
    }

    @Test
    fun testLifecycleMutexExistsAndIsUnlockedInitially() {
        assertNotNull(viewModel.recordingLifecycleMutex)
        assertFalse(viewModel.recordingLifecycleMutex.isLocked)
    }

    @Test
    fun testRapidStartStopExecutionSettlesSymmetrically() = runTest {
        // Fast start then immediate stop (simulating rapid user double-tap)
        viewModel.startRecording("Rapid Test Start")
        viewModel.stopRecording(autoArmSentry = false)

        // Invariant (AGENTS.md Rule 7): Must settle cleanly to idle, never stuck in isRecordingStarting purgatory
        assertFalse(viewModel.uiState.value.isRecording)
        assertFalse(viewModel.uiState.value.isRecordingStarting)
    }

    @Test
    fun testManualRecordingStartsUnlocked() = runTest {
        // Invariant (AGENTS.md Rule 1 & Rule 7): Manual recording must start unlocked as a standard loop
        val job = viewModel.startRecording("Manual")
        org.junit.Assert.assertNotNull(job)
        org.junit.Assert.assertTrue(viewModel.uiState.value.isRecording || viewModel.uiState.value.isRecordingStarting)
        assertFalse(viewModel.uiState.value.isSegmentLocked)
        org.junit.Assert.assertEquals(io.github.roaxio.dashguardpro.viewmodel.state.RecordingOriginState.DRIVING_LOOP, viewModel.recordingOriginState)
        viewModel.stopRecording(autoArmSentry = false)
        assertFalse(viewModel.uiState.value.isRecording)
        assertFalse(viewModel.uiState.value.isRecordingStarting)
    }

    @Test
    fun testAutoPowerOnRecordingStartsUnlocked() = runTest {
        val job = viewModel.startRecording("Auto Power-On")
        org.junit.Assert.assertNotNull(job)
        org.junit.Assert.assertTrue(viewModel.uiState.value.isRecording || viewModel.uiState.value.isRecordingStarting)
        assertFalse(viewModel.uiState.value.isSegmentLocked)
        org.junit.Assert.assertEquals(io.github.roaxio.dashguardpro.viewmodel.state.RecordingOriginState.DRIVING_LOOP, viewModel.recordingOriginState)
        viewModel.stopRecording(autoArmSentry = false)
        assertFalse(viewModel.uiState.value.isRecording)
        assertFalse(viewModel.uiState.value.isRecordingStarting)
    }

    @Test
    fun testToggleLockSegmentDuringRecording() {
        viewModel.startRecording("Manual")
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        assertFalse(viewModel.uiState.value.isSegmentLocked)

        viewModel.toggleCurrentClipLock()
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        org.junit.Assert.assertTrue(viewModel.uiState.value.isSegmentLocked)

        viewModel.toggleCurrentClipLock()
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        assertFalse(viewModel.uiState.value.isSegmentLocked)

        viewModel.stopRecording(autoArmSentry = false)
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        assertFalse(viewModel.uiState.value.isRecording)
        assertFalse(viewModel.uiState.value.isRecordingStarting)
    }

    @Test
    fun testAudioRecordingToggle() {
        val initial = app.settingsRepository.settings.value.audioRecordingEnabled
        viewModel.toggleAudio()
        org.junit.Assert.assertEquals(!initial, app.settingsRepository.settings.value.audioRecordingEnabled)
        viewModel.toggleAudio()
        org.junit.Assert.assertEquals(initial, app.settingsRepository.settings.value.audioRecordingEnabled)
    }
}
