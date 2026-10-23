package io.github.roaxio.dashguardpro.system

import android.app.ActivityManager
import android.app.NotificationManager
import android.content.Context
import android.os.PowerManager
import android.util.Log
import io.github.roaxio.dashguardpro.data.SettingsRepository
import io.github.roaxio.dashguardpro.storage.StorageDirectoryManager
import io.github.roaxio.dashguardpro.storage.StorageOptionInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Encapsulates system health, optimization status, and OS policy configurations
 * such as battery optimization whitelisting, app pinning / kiosk mode, DND access,
 * and storage permissions.
 */
data class SystemOptimizationState(
    val isBatteryIgnored: Boolean = false,
    val isAppPinned: Boolean = false,
    val isDndGranted: Boolean = false,
    val hasStoragePermission: Boolean = false,
    val availableStorageOptions: List<StorageOptionInfo> = emptyList()
)

/**
 * SystemDiagnosticsManager — Authoritative manager for OS-level permissions,
 * system optimization checks, auto-DND lifecycle, and storage availability discovery.
 * Extracted from DashcamViewModel to preserve Single Responsibility and modularity.
 */
class SystemDiagnosticsManager(
    private val context: Context,
    private val settingsRepository: SettingsRepository
) {
    private val _systemOptState = MutableStateFlow(
        SystemOptimizationState(
            isBatteryIgnored = isBatteryIgnored(),
            isAppPinned = isAppPinned(),
            isDndGranted = isDndGranted(),
            hasStoragePermission = hasStoragePermission(),
            availableStorageOptions = getAvailableStorageOptions()
        )
    )
    val systemOptState: StateFlow<SystemOptimizationState> = _systemOptState.asStateFlow()

    private var previousInterruptionFilter: Int? = null

    val hasPreviousInterruptionFilter: Boolean
        get() = previousInterruptionFilter != null

    fun isBatteryIgnored(): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        return powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true
    }

    fun isAppPinned(): Boolean {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        return activityManager?.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE
    }

    fun isDndGranted(): Boolean {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        return notificationManager?.isNotificationPolicyAccessGranted == true
    }

    fun hasStoragePermission(): Boolean {
        return StorageDirectoryManager.hasStoragePermission(context)
    }

    fun getAvailableStorageOptions(
        customPath: String = settingsRepository.settings.value.customStoragePath,
        customUri: String = settingsRepository.settings.value.customStorageUri
    ): List<StorageOptionInfo> {
        return StorageDirectoryManager.getAvailableStorageOptions(context, customPath, customUri)
    }

    fun refreshSystemDiagnostics() {
        val isBattery = isBatteryIgnored()
        val isPinned = isAppPinned()
        val isDnd = isDndGranted()
        val hasStorage = hasStoragePermission()
        val storageOptions = getAvailableStorageOptions()
        _systemOptState.value = SystemOptimizationState(
            isBatteryIgnored = isBattery,
            isAppPinned = isPinned,
            isDndGranted = isDnd,
            hasStoragePermission = hasStorage,
            availableStorageOptions = storageOptions
        )
    }

    fun updateStorageOptions(options: List<StorageOptionInfo>) {
        _systemOptState.value = _systemOptState.value.copy(availableStorageOptions = options)
    }

    fun applyAutoDndIfEnabled() {
        if (!settingsRepository.settings.value.autoDndWhileRecording) return
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            if (notificationManager?.isNotificationPolicyAccessGranted == true) {
                if (previousInterruptionFilter == null) {
                    previousInterruptionFilter = notificationManager.currentInterruptionFilter
                }
                notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
                Log.d(TAG, "Auto-DND applied (interruption filter set to Priority)")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to apply auto-DND: ${e.message}")
        }
    }

    fun restoreAutoDndIfSet() {
        val filterToRestore = previousInterruptionFilter ?: return
        previousInterruptionFilter = null
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            if (notificationManager?.isNotificationPolicyAccessGranted == true) {
                notificationManager.setInterruptionFilter(filterToRestore)
                Log.d(TAG, "Auto-DND restored (interruption filter reset to $filterToRestore)")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to restore auto-DND: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "SystemDiagnosticsManager"
    }
}
