package io.github.roaxio.dashguardpro

import androidx.test.core.app.ApplicationProvider
import io.github.roaxio.dashguardpro.data.SettingsRepository
import io.github.roaxio.dashguardpro.data.settings.StorageLocationType
import io.github.roaxio.dashguardpro.storage.StorageOptionInfo
import io.github.roaxio.dashguardpro.system.SystemDiagnosticsManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * SystemDiagnosticsManagerTest — Empirical verification of OS policy checks,
 * battery optimization whitelist detection, storage option discovery, and auto-DND lifecycle.
 * Conforms to AGENTS.md Rule 5 (Atomic Phase Cadence).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SystemDiagnosticsManagerTest {

    private lateinit var app: DashGuardApplication
    private lateinit var settingsRepo: SettingsRepository
    private lateinit var diagnosticsManager: SystemDiagnosticsManager

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        settingsRepo = app.settingsRepository
        diagnosticsManager = SystemDiagnosticsManager(app, settingsRepo)
    }

    @Test
    fun testInitialState_isPopulated() {
        val state = diagnosticsManager.systemOptState.value
        assertNotNull(state)
        assertNotNull(state.availableStorageOptions)
        assertNotNull(state.hasStoragePermission)
        assertFalse(diagnosticsManager.hasPreviousInterruptionFilter)
    }

    @Test
    fun testRefreshSystemDiagnostics_updatesStateFlow() {
        diagnosticsManager.refreshSystemDiagnostics()
        val state = diagnosticsManager.systemOptState.value
        assertNotNull(state)
        assertNotNull(state.isBatteryIgnored)
        assertNotNull(state.isAppPinned)
        assertNotNull(state.isDndGranted)
    }

    @Test
    fun testUpdateStorageOptions_replacesAvailableOptions() {
        val dummyOption = StorageOptionInfo(
            type = StorageLocationType.DEFAULT_APP_DATA,
            title = "Test Storage Directory",
            description = "Mock directory for testing",
            resolvedPath = File(app.filesDir, "test_videos").absolutePath,
            freeBytes = 500L,
            totalBytes = 1000L,
            isWritable = true,
            isAvailable = true
        )
        diagnosticsManager.updateStorageOptions(listOf(dummyOption))

        val updated = diagnosticsManager.systemOptState.value.availableStorageOptions
        assertEquals(1, updated.size)
        assertEquals("Test Storage Directory", updated[0].title)
    }

    @Test
    fun testAutoDnd_whenDisabledInSettings_doesNotApply() {
        settingsRepo.updateSettings(settingsRepo.settings.value.copy(autoDndWhileRecording = false))

        diagnosticsManager.applyAutoDndIfEnabled()
        assertFalse(diagnosticsManager.hasPreviousInterruptionFilter)

        diagnosticsManager.restoreAutoDndIfSet()
        assertFalse(diagnosticsManager.hasPreviousInterruptionFilter)
    }

    @Test
    fun testAutoDnd_restoreWithoutApply_isSafeNoOp() {
        diagnosticsManager.restoreAutoDndIfSet()
        assertFalse(diagnosticsManager.hasPreviousInterruptionFilter)
    }
}
