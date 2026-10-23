# Codebase Map: DashGuard-Pro

Comprehensive architectural inventory and relational index of the **DashGuard-Pro** codebase. Every semantically meaningful implementation unit is indexed with a globally unique, stable canonical tag `[repository-relative/path/FileName.ext::EntityName]`.

---

## 1. System Scope & Build Manifest

- **Application / Artifact ID:** `io.github.roaxio.dashguardpro`
- **Minimum SDK:** 24 (Android 7.0 Nougat)
- **Target SDK:** 36 (Android 15)
- **Compile SDK:** 36.1
- **Language / Runtime:** Kotlin 2.2+ / JVM 11 / Android Gradle Plugin 9.1+ / Jetpack Compose BOM
- **Core Ecosystem Libraries:**
  - **Camera & Video Capture:** AndroidX CameraX 1.5+ (`camera-core`, `camera-camera2`, `camera-lifecycle`, `camera-video`, `camera-view`)
  - **Media Processing & Transcoding:** AndroidX Media3 1.3+ (`media3-transformer`, `media3-effect`, `media3-common`)
  - **Persistence:** Room 2.7 (`room-runtime`, `room-ktx`, `ksp` compiler)
  - **Location & Sensors:** Android Hardware `LocationManager` (`GPS_PROVIDER` & `NETWORK_PROVIDER`), Android Hardware Sensors (`SensorManager`)
  - **Audio Recording:** Android AudioRecord PCM 16kHz
  - **Serialization:** Moshi 1.15 (`moshi-kotlin`, `moshi-kotlin-codegen`)
  - **Networking / Air-Gap Invariant:** Strict offline air-gap (zero network permissions, zero HTTP/cloud dependencies - AGENTS.md Rule 18)
- **Primary Package / Root Namespace:** `io.github.roaxio.dashguardpro`
- **Root Entry Points:**
  - **Application Bootstrap / DI Root:** `[app/src/main/kotlin/DashGuardApplication.kt::DashGuardApplication]`
  - **Primary UI Host Activity:** `[app/src/main/kotlin/MainActivity.kt::MainActivity]`
  - **Background Daemon / Service:** `[app/src/main/kotlin/service/DashcamForegroundService.kt::DashcamForegroundService]`
- **Declared System Permissions & Platform Capabilities:**
  - **Camera & Audio:** `android.permission.CAMERA`, `android.permission.RECORD_AUDIO`
  - **Storage & Media:** `android.permission.MANAGE_EXTERNAL_STORAGE`, `android.permission.READ_EXTERNAL_STORAGE`, `android.permission.WRITE_EXTERNAL_STORAGE`, `android.permission.READ_MEDIA_VIDEO`, `android.permission.READ_MEDIA_IMAGES`, `android.permission.READ_MEDIA_AUDIO`
  - **Location & Telemetry:** `android.permission.ACCESS_FINE_LOCATION`, `android.permission.ACCESS_COARSE_LOCATION`
  - **Foreground Execution:** `android.permission.FOREGROUND_SERVICE`, `android.permission.FOREGROUND_SERVICE_CAMERA`, `android.permission.FOREGROUND_SERVICE_LOCATION`, `android.permission.FOREGROUND_SERVICE_DATA_SYNC`, `android.permission.FOREGROUND_SERVICE_SPECIAL_USE`
  - **System Controls & Power:** `android.permission.WAKE_LOCK`, `android.permission.POST_NOTIFICATIONS`, `android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, `android.permission.VIBRATE`, `android.permission.ACCESS_NOTIFICATION_POLICY`

---

## 2. Canonical Tagging Syntax & Invariants

Every code entity cataloged in this map is identified by a unique, immutable tag:
`[repository-relative/path/FileName.ext::EntityName]`

### Tagging Rules:
1. **Repository-Relative Paths:** Paths are always relative to the repository root without leading slashes or `.` (e.g., `app/src/main/kotlin/MainActivity.kt::MainActivity`).
2. **Deterministic Entity Names:** Use the formal language identifier (e.g., `Class`, `Class.method`, `EnumClass`, `DataClass`).
3. **No Aliasing:** Never shorten, abbreviate, or alias tags. If an entity moves or is renamed, its tag is updated across all architectural documents.
4. **1:1 Uniqueness:** Every tag uniquely resolves to exactly one code definition.

---

## 3. Exhaustive Entity Inventory & Relational Graph

### Category A: Core Bootstrap & Application Lifecycle Entry Points

#### `app/src/main/kotlin/DashGuardApplication.kt`

##### `[app/src/main/kotlin/DashGuardApplication.kt::DashGuardApplication]`
- **Purpose:** Primary Android `Application` class serving as the dependency injection container and service registry for application singletons.
- **Role:** Canonical Manual Service Locator / DI Container.
- **Inputs:** Android OS Application Lifecycle.
- **Outputs:** Lazy singleton instances of persistence, hardware monitors, and engines.
- **Creates:** `AppDatabase`, `SettingsRepository`, `LoopStorageManager`, `CameraXRecordingManager`, `SensorMonitor`, `BatteryMonitor`, `LocationTracker`, `AlertManager`, `AcousticNoiseMonitor`, `SentryController`, `VideoPlaybackCoordinator`, `SystemDiagnosticsManager`.
- **Called By:** Android OS runtime bootstrap.

##### `[app/src/main/kotlin/DashGuardApplication.kt::DashGuardApplication.database]`
- **Purpose:** Lazy singleton accessor for Room database instance.
- **Calls:** `[app/src/main/kotlin/data/AppDatabase.kt::AppDatabase.getDatabase]`
- **Called By:** `[app/src/main/kotlin/DashGuardApplication.kt::DashGuardApplication.storageManager]`, `[app/src/main/kotlin/viewmodel/DashcamViewModel.kt::DashcamViewModel]`

##### `[app/src/main/kotlin/DashGuardApplication.kt::DashGuardApplication.settingsRepository]`
- **Purpose:** Lazy singleton accessor for persistent SharedPreferences settings repository.
- **Creates:** `[app/src/main/kotlin/data/SettingsRepository.kt::SettingsRepository]`
- **Called By:** `[app/src/main/kotlin/DashGuardApplication.kt::DashGuardApplication.storageManager]`, `[app/src/main/kotlin/viewmodel/DashcamViewModel.kt::DashcamViewModel]`, `[app/src/main/kotlin/ui/components/hud/CameraPreviewView.kt::CameraPreviewView]`

##### `[app/src/main/kotlin/DashGuardApplication.kt::DashGuardApplication.storageManager]`
- **Purpose:** Lazy singleton accessor for loop storage retention and cleanup engine.
- **Creates:** `[app/src/main/kotlin/storage/LoopStorageManager.kt::LoopStorageManager]`
- **Called By:** `[app/src/main/kotlin/viewmodel/DashcamViewModel.kt::DashcamViewModel]`

##### `[app/src/main/kotlin/DashGuardApplication.kt::DashGuardApplication.recordingManager]`
- **Purpose:** Lazy singleton accessor for CameraX video recording session coordinator.
- **Creates:** `[app/src/main/kotlin/camera/CameraXRecordingManager.kt::CameraXRecordingManager]`
- **Called By:** `[app/src/main/kotlin/viewmodel/DashcamViewModel.kt::DashcamViewModel]`, `[app/src/main/kotlin/ui/components/hud/CameraPreviewView.kt::CameraPreviewView]`

##### `[app/src/main/kotlin/DashGuardApplication.kt::DashGuardApplication.sensorMonitor]`
- **Purpose:** Lazy singleton accessor for accelerometer and hardware G-force monitor.
- **Creates:** `[app/src/main/kotlin/sensor/SensorMonitor.kt::SensorMonitor]`
- **Called By:** `[app/src/main/kotlin/viewmodel/DashcamViewModel.kt::DashcamViewModel]`

##### `[app/src/main/kotlin/DashGuardApplication.kt::DashGuardApplication.batteryMonitor]`
- **Purpose:** Lazy singleton accessor for battery telemetry and thermal broadcast receiver.
- **Creates:** `[app/src/main/kotlin/sensor/BatteryMonitor.kt::BatteryMonitor]`
- **Called By:** `[app/src/main/kotlin/viewmodel/DashcamViewModel.kt::DashcamViewModel]`

##### `[app/src/main/kotlin/DashGuardApplication.kt::DashGuardApplication.locationTracker]`
- **Purpose:** Lazy singleton accessor for GPS location and hardware orientation sensor listener.
- **Creates:** `[app/src/main/kotlin/sensor/LocationTracker.kt::LocationTracker]`
- **Called By:** `[app/src/main/kotlin/viewmodel/DashcamViewModel.kt::DashcamViewModel]`

##### `[app/src/main/kotlin/DashGuardApplication.kt::DashGuardApplication.alertManager]`
- **Purpose:** Lazy singleton accessor for audio siren, LED strobe, haptics, and TTS voice alarms.
- **Creates:** `[app/src/main/kotlin/feedback/AlertManager.kt::AlertManager]`
- **Called By:** `[app/src/main/kotlin/viewmodel/DashcamViewModel.kt::DashcamViewModel]`, `[app/src/main/kotlin/ui/components/hud/CameraPreviewView.kt::CameraPreviewView]`

##### `[app/src/main/kotlin/DashGuardApplication.kt::DashGuardApplication.acousticNoiseMonitor]`
- **Purpose:** Lazy singleton accessor for AudioRecord background decibel monitor.
- **Creates:** `[app/src/main/kotlin/sensor/AcousticNoiseMonitor.kt::AcousticNoiseMonitor]`
- **Called By:** `[app/src/main/kotlin/viewmodel/DashcamViewModel.kt::DashcamViewModel]`

##### `[app/src/main/kotlin/DashGuardApplication.kt::DashGuardApplication.sentryController]`
- **Purpose:** Lazy singleton accessor for Sentry mode surveillance coordinator.
- **Creates:** `[app/src/main/kotlin/sentry/SentryController.kt::SentryController]`
- **Called By:** `[app/src/main/kotlin/viewmodel/DashcamViewModel.kt::DashcamViewModel]`

##### `[app/src/main/kotlin/DashGuardApplication.kt::DashGuardApplication.playbackCoordinator]`
- **Purpose:** Lazy singleton accessor for video playback and synchronized telemetry subtitle coordinator.
- **Creates:** `[app/src/main/kotlin/playback/VideoPlaybackCoordinator.kt::VideoPlaybackCoordinator]`
- **Called By:** `[app/src/main/kotlin/viewmodel/DashcamViewModel.kt::DashcamViewModel]`

##### `[app/src/main/kotlin/DashGuardApplication.kt::DashGuardApplication.systemDiagnosticsManager]`
- **Purpose:** Lazy singleton accessor for OS permissions, battery optimizations, and Auto-DND lifecycle manager.
- **Creates:** `[app/src/main/kotlin/system/SystemDiagnosticsManager.kt::SystemDiagnosticsManager]`
- **Called By:** `[app/src/main/kotlin/viewmodel/DashcamViewModel.kt::DashcamViewModel]`

---

#### `app/src/main/kotlin/MainActivity.kt`

##### `[app/src/main/kotlin/MainActivity.kt::NavigationTab]`
- **Purpose:** Enum defining primary navigation items (`CAMERA`, `GALLERY`, `OPTIMIZATION`, `SETTINGS`) with automotive title and Material vector icon.
- **Consumers:** `[app/src/main/kotlin/MainActivity.kt::DashGuardApp]`

##### `[app/src/main/kotlin/MainActivity.kt::MainActivity]`
- **Purpose:** Primary Android `ComponentActivity` hosting Jetpack Compose root, managing window flags, immersive cutout expansion, orientation locks, and impact simulation receiver.
- **Lifecycle Invariants:**
  - Pins `FLAG_KEEP_SCREEN_ON` on window.
  - Dynamically forces display cutout short edges mode on Android 9+.
  - Dynamically pins orientation during active recording to prevent CameraX surface destruction.

##### `[app/src/main/kotlin/MainActivity.kt::MainActivity.onCreate]`
- **Purpose:** Bootstraps Compose content, registers `repeatOnLifecycle` orientation lock observer, and registers the `io.github.roaxio.dashguardpro.ACTION_SIMULATE_IMPACT` debug broadcast receiver (`adb shell am broadcast -a io.github.roaxio.dashguardpro.ACTION_SIMULATE_IMPACT`).
- **Calls:** `[app/src/main/kotlin/MainActivity.kt::MainActivity.hideSystemBars]`, `[app/src/main/kotlin/MainActivity.kt::DashGuardApp]`, `[app/src/main/kotlin/viewmodel/DashcamViewModel.kt::DashcamViewModel]` (`handleImpactDetected`).

##### `[app/src/main/kotlin/MainActivity.kt::MainActivity.onResume]`
- **Purpose:** Re-hides system bars, refreshes system optimization diagnostics, and re-engages GPS telemetry.
- **Calls:** `[app/src/main/kotlin/viewmodel/DashcamViewModel.kt::DashcamViewModel]` (`refreshSystemDiagnostics`, `onActivityResumed`), `[app/src/main/kotlin/MainActivity.kt::Activity.lockOrientationToCurrent]`.

##### `[app/src/main/kotlin/MainActivity.kt::MainActivity.onKeyDown]`
- **Purpose:** Intercepts hardware physical volume buttons (`KEYCODE_VOLUME_DOWN`, `KEYCODE_VOLUME_UP`) during active recording to trigger instant clip emergency lock with haptic vibration.
- **Calls:** `[app/src/main/kotlin/viewmodel/DashcamViewModel.kt::DashcamViewModel]` (`toggleCurrentClipLock`).

##### `[app/src/main/kotlin/MainActivity.kt::MainActivity.hideSystemBars]`
- **Purpose:** Immersive mode utility configuring `WindowInsetsControllerCompat` to `BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE` and hiding system status/navigation bars.

##### `[app/src/main/kotlin/MainActivity.kt::DashGuardApp]`
- **Purpose:** Root `@Composable` application router. Implements adaptive layout switching:
  - Landscape: Displays `NavigationRail` alongside full-height screen content.
  - Portrait: Displays Material 3 `Scaffold` with `NavigationBar`.
  - Speed Safety Barrier: Enforces `SpeedSafetyLockoutOverlay` when moving > 16 km/h outside Camera HUD.
  - Centralized BackHandler: Intercepts back gestures across video playback, speed lockout, and sub-tabs.
- **Subscribes To:** `DashcamViewModel.uiState`, `settings`, `telemetry`, `allClips`, `trips`, `playbackState`.
- **Calls:** `CameraHudScreen`, `GalleryScreen`, `OptimizationScreen`, `SettingsScreen`, `VideoPlayerScreen`.

##### `[app/src/main/kotlin/MainActivity.kt::AppScreenContent]`
- **Purpose:** Single, authoritative screen navigation router shared between landscape NavigationRail and portrait Scaffold branches, eliminating duplicate when (currentTab) blocks.
- **Consumers:** `[app/src/main/kotlin/MainActivity.kt::DashGuardApp]`.

##### `[app/src/main/kotlin/MainActivity.kt::Context.findActivity]`
- **Purpose:** Tail-recursive Context unwrapper to resolve host `Activity` from arbitrary Compose contexts.

##### `[app/src/main/kotlin/MainActivity.kt::Activity.lockOrientationToCurrent]`
- **Purpose:** Autorotation suppression utility. Reads display rotation (`0`, `90`, `180`, `270`) and configuration, locking `requestedOrientation` to exact physical orientation (`LANDSCAPE`, `REVERSE_LANDSCAPE`, `PORTRAIT`, `REVERSE_PORTRAIT`) during active recording.

##### `[app/src/main/kotlin/MainActivity.kt::Activity.unlockOrientation]`
- **Purpose:** Restores `ActivityInfo.SCREEN_ORIENTATION_FULL_USER` when recording stops cleanly.

---

### Category B: Persistence, Storage & Data Models

#### `app/src/main/kotlin/data/AppDatabase.kt`

##### `[app/src/main/kotlin/data/AppDatabase.kt::AppDatabase]`
- **Purpose:** Room database abstract container storing `VideoClip` records (version 5 with `MIGRATION_4_5` enforcing unique `filePath` index and destructive migration fallback).
- **Role:** Persistence Root / Unit of Work.
- **Canonical Status:** Single source of truth for persistent video metadata.
- **Exports:** `[app/src/main/kotlin/data/ClipDao.kt::ClipDao]`

##### `[app/src/main/kotlin/data/AppDatabase.kt::AppDatabase.getDatabase]`
- **Purpose:** Thread-safe synchronized singleton builder constructing `dashguard_database`.

---

#### `app/src/main/kotlin/data/ClipDao.kt`

##### `[app/src/main/kotlin/data/ClipDao.kt::ClipDao]`
- **Purpose:** Data Access Object for Room database queries and mutations on `video_clips`.
- **Methods:**
  - `getAllClips()`: Reactive `Flow<List<VideoClip>>` sorted newest first.
  - `getLockedClips()`: Reactive `Flow<List<VideoClip>>` filtered by `isLocked = 1`.
  - `getSentryClips()`: Reactive `Flow<List<VideoClip>>` filtered by `isSentryEvent = 1`.
  - `getClipById(id)`, `getClipByPath(filePath)`, `getLatestClip()`: Direct entity lookups.
  - `getUnlockedClipsOldestFirst()`: FIFO loop retention candidate selector.
  - `getTotalVideoBytes()`: Storage aggregation query.
  - `insertClip(clip)`, `updateClip(clip)`, `deleteClip(clip)`, `deleteClipById(id)`, `setLockStatus(id, isLocked)`, `updateClipIncidentLocation(id, lat, lng)`, `healSentryNormalLoopReasons()`, `deduplicateClipsByPath()`.
- **Consumers:** `ClipRepository`, `LoopStorageManager`, `DashcamViewModel`, `StorageMigrationManager`.

---

#### `app/src/main/kotlin/data/settings/DashGuardSettings.kt`

##### `[app/src/main/kotlin/data/settings/DashGuardSettings.kt::AppMode]`
- **Purpose:** Enum defining primary system operating mode: `DASHCAM` (continuous rolling driving loop) vs `SENTRY` (parked security surveillance).

##### `[app/src/main/kotlin/data/settings/DashGuardSettings.kt::VideoAspectRatio]`
- **Purpose:** Enum defining camera frame geometry: `RATIO_16_9` (widescreen road angle) vs `RATIO_4_3` (tall sensor field of view).

##### `[app/src/main/kotlin/data/settings/DashGuardSettings.kt::VideoQuality]`
- **Purpose:** Enum defining resolution profiles: `SD_480P`, `HD_720P`, `FHD_1080P`, `UHD_4K`.

##### `[app/src/main/kotlin/data/settings/DashGuardSettings.kt::SpeedUnit]`
- **Purpose:** Speed units: `KMH` (multiplier 3.6f) vs `MPH` (multiplier 2.23694f).

##### `[app/src/main/kotlin/data/settings/DashGuardSettings.kt::Sensitivity]`
- **Purpose:** Calibrated thresholds for G-force shocks and optical motion: `OFF`, `VERY_LOW`, `LOW`, `MEDIUM`, `HIGH`, `VERY_HIGH`.

##### `[app/src/main/kotlin/data/settings/DashGuardSettings.kt::StorageLocationType]`
- **Purpose:** Enum: `DEFAULT_APP_DATA` (isolated app movies) vs `CUSTOM_DIRECTORY` (user-selected folder / SD card).

##### `[app/src/main/kotlin/data/settings/DashGuardSettings.kt::AutoDimTimeout]`
- **Purpose:** HUD inactivity timeouts: `DISABLED`, `SEC_5`, `SEC_10`, `SEC_15`, `SEC_30`.

##### `[app/src/main/kotlin/data/settings/DashGuardSettings.kt::HudDisplayMode]`
- **Purpose:** HUD density: `FULL` (complete telemetry pod) vs `COMPACT` (minimal road obstruction).

##### `[app/src/main/kotlin/data/settings/DashGuardSettings.kt::SentryArmingTrigger]`
- **Purpose:** Enum defining auto-arming condition for Sentry mode: `ON_POWER_LOSS`, `ON_RECORDING_STOP`, `MANUAL_ONLY`.

##### `[app/src/main/kotlin/data/settings/DashGuardSettings.kt::SentryGuardType]`
- **Purpose:** Sentry power tiers: `SHOCK_STANDBY` (Tier 1, ultra low power, zero camera draw) vs `OPTICAL_MOTION` (Tier 2, 4 FPS motion analysis with auto-demote).

##### `[app/src/main/kotlin/data/settings/DashGuardSettings.kt::SentryInterruptedClipAction]`
- **Purpose:** Enum defining behavior when recording is interrupted in Sentry mode: `KEEP_WITH_BADGE`, `AUTO_DELETE`.

##### `[app/src/main/kotlin/data/settings/DashGuardSettings.kt::SentryDeterrentMode]`
- **Purpose:** Security responses: `NONE`, `TORCH_STROBE`, `SIREN_CHIME`, `BOTH`.

##### `[app/src/main/kotlin/data/settings/DashGuardSettings.kt::DashGuardSettings]`
- **Purpose:** Immutable data class holding all 40+ user preferences, system invariants, and calibrated thresholds.

---

#### `app/src/main/kotlin/data/SettingsRepository.kt`

##### `[app/src/main/kotlin/data/SettingsRepository.kt::SettingsRepository]`
- **Purpose:** SharedPreferences persistence repository managing `DashGuardSettings` state (`dashguard_settings`).
- **Key Invariants Enforced in `updateSettings`:**
  - Sentry configured guard type preserved across temporary demotions (`sentryConfiguredGuardType`).
  - Configurable Tier 2 auto-demote protection toggle (`sentryAutoDemoteEnabled`).
  - Sentry battery demote floor clamped `>= lowBatterySafetyLimitPct + 5%`.
  - Sentry temp demote ceiling clamped `<= maxTempThresholdC - 4°C`.
  - Thermal recovery target clamped `<= maxTempThresholdC - 2°C`.
  - Warm auto-dim threshold clamped `<= maxTempThresholdC - 4°C`.
- **Emits:** `StateFlow<DashGuardSettings>`.

---

#### `app/src/main/kotlin/model/VideoClip.kt`

##### `[app/src/main/kotlin/model/VideoClip.kt::VideoClip]`
- **Purpose:** Room entity (`tableName = "video_clips"`, `@Index(value = ["filePath"], unique = true)`) representing an individual recorded video loop chunk with strict database-level path uniqueness enforced in schema v5 (`MIGRATION_4_5`).
- **Attributes:** `id`, `filePath`, `fileName`, `timestamp`, `durationMs`, `fileSizeBytes`, `isLocked`, `isSentryEvent`, `triggerReason`, `speedKmh`, `avgSpeedKmh`, `distanceMeters`, `speedSource`, coordinates (`latitude`, `longitude`, `startLatitude`, `startLongitude`, `endLatitude`, `endLongitude`, `incidentLatitude`, `incidentLongitude`), `incidentVideoOffsetSeconds`, `incidentGForce`, `batteryTempC`.
- **Canonical Properties:** `isTelemetryFused` (identifies burned-in overlay clips), `isOriginalRawClip` (identifies original camera footage).

---

#### `app/src/main/kotlin/model/TripSession.kt`

##### `[app/src/main/kotlin/model/TripSession.kt::TripSession]`
- **Purpose:** Domain entity representing an aggregated continuous driving trip or sentry session.
- **Properties:** Contiguous list of `VideoClip` items, total duration, distance, average/max speed, locked count, incident count, starting/ending coordinates.

---

#### `app/src/main/kotlin/model/GalleryFilter.kt`

##### `[app/src/main/kotlin/model/GalleryFilter.kt::GalleryViewMode]`
- **Purpose:** Enum defining gallery grouping: `TRIP_LOOPS` (continuous drives) vs `ALL_CLIPS` (flat loop list).

##### `[app/src/main/kotlin/model/GalleryFilter.kt::GalleryFilter]`
- **Purpose:** Enum defining gallery filter criteria: `ALL`, `LOCKED`, `DASHCAM`, `SENTRY`, `FUSED_TELEMETRY`.
- **Filter Invariant:** `GalleryFilter.LOCKED` strictly includes all locked video items (both original raw camera loops and derived telemetry-fused exports).

##### `[app/src/main/kotlin/model/GalleryFilter.kt::GalleryNavState]`
- **Purpose:** Hoisted navigation and scroll memory state model. Preserves active `GalleryViewMode` (`TRIP_LOOPS` vs `ALL_CLIPS`), `GalleryFilter`, expanded trip session IDs (`expandedTripIds`), focused clip path (`lastPlayedClipFilePath`), one-time scroll target (`pendingScrollToClip`), and decoupled scroll positions (`tripScrollIndex`, `tripScrollOffset`, `allClipsScrollIndex`, `allClipsScrollOffset`) across video playback transitions and configuration changes.

---

### Category C: Hardware, Camera & Sensors (Ingress Tier)

#### `app/src/main/kotlin/camera/MotionDetectionAnalyzer.kt`

##### `[app/src/main/kotlin/camera/MotionDetectionAnalyzer.kt::MotionDetectionAnalyzer]`
- **Purpose:** ImageAnalysis.Analyzer performing 4 FPS low-power computer vision motion detection on YUV image buffers.
- **Algorithms Implemented:**
  1. Spatial Block Pooling: 32x24 grid (768 blocks) with 4x4 sub-sampling.
  2. Global Illumination Shift Compensation: Subtracts average luma shift to prevent false alarms from clouds or headlights.
  3. Dynamic Noise Floor: Elevates delta threshold in dark scenes (< 50 DN).
  4. 8-Neighbor Spatial Clustering: Requires neighboring blocks to eliminate sensor shot noise.
  5. Temporal Multi-Frame Persistence: Requires N consecutive frames of clustered motion.
  6. Auto-Exposure Hardware Warm-up & Temporal Reset: Automatically flushes stale grid history on `isEnabled` transitions and skips initial 3 warm-up frames to allow camera AE/AWB to settle.
- **Calls:** `onMotionDetected(motionScore, avgLuminance)`.
- **Consumers:** `[app/src/main/kotlin/ui/components/hud/CameraPreviewView.kt::CameraPreviewView]`.

---

#### `app/src/main/kotlin/sensor/AcousticNoiseMonitor.kt`

##### `[app/src/main/kotlin/sensor/AcousticNoiseMonitor.kt::AcousticNoiseMonitor]`
- **Purpose:** AudioRecord background listener measuring ambient sound pressure level in decibels (dB).
- **Lifecycle:** Starts 16 kHz 16-bit PCM AudioRecord on `Dispatchers.IO`; calculates RMS and converts to dB.
- **Emits:** `currentDb: StateFlow<Float>`, invokes `onAcousticAlert(db)`.
- **Called By:** `[app/src/main/kotlin/DashGuardApplication.kt::DashGuardApplication.acousticNoiseMonitor]`, `[app/src/main/kotlin/viewmodel/DashcamViewModel.kt::DashcamViewModel]`, `[app/src/main/kotlin/sentry/SentryController.kt::SentryController]`, `[app/src/main/kotlin/camera/RecordingSessionCoordinator.kt::RecordingSessionCoordinator]`, `[app/src/main/kotlin/system/PowerAndThermalCoordinator.kt::PowerAndThermalCoordinator]`.

---

#### `app/src/main/kotlin/feedback/AlertManager.kt`

##### `[app/src/main/kotlin/feedback/AlertManager.kt::AlertManager]`
- **Purpose:** Hardware deterrent, haptic, audio, and speech synthesizer coordinator.
- **Capabilities:**
  - Haptics: Short vibration (`vibrateShort`), multi-pulse impact pattern (`vibrateImpactPattern`).
  - Audio: Distinct audio confirmation cues (`playRecordStartSound`, `playRecordStopSound`, `playLockSound`, `playImpactBeep`, `playChime`, `startSirenDeterrent`).
  - Thermal / Low Battery Audio Alarms: Plays custom raw audio alerts with fallback to alarm tone.
  - Camera Strobe: Strobe LED flashes using Camera2 `setTorchMode` or CameraX `CameraControl.enableTorch`.
  - Voice Prompts: Android `TextToSpeech` engine announcing Sentry arming, disarming, and cooling recovery.
- **Called By:** `[app/src/main/kotlin/DashGuardApplication.kt::DashGuardApplication.alertManager]`, `[app/src/main/kotlin/viewmodel/DashcamViewModel.kt::DashcamViewModel]`, `[app/src/main/kotlin/sentry/SentryController.kt::SentryController]`, `[app/src/main/kotlin/camera/RecordingSessionCoordinator.kt::RecordingSessionCoordinator]`, `[app/src/main/kotlin/system/PowerAndThermalCoordinator.kt::PowerAndThermalCoordinator]`, `[app/src/main/kotlin/ui/components/hud/CameraPreviewView.kt::CameraPreviewView]`.

---

#### `app/src/main/kotlin/sensor/BatteryMonitor.kt`

##### `[app/src/main/kotlin/sensor/BatteryMonitor.kt::PowerSource]`
- **Purpose:** Enum: `AC`, `USB`, `WIRELESS`, `BATTERY`.

##### `[app/src/main/kotlin/sensor/BatteryMonitor.kt::TemperatureStatus]`
- **Purpose:** Centralized domain enum for thermal classification tiers: `COOL` ("Cool / Safe"), `NORMAL` ("Normal"), `WARM` ("Warm"), `HOT` ("High Temp (Hot)"), `CRITICAL` ("Critical Overheating").
- **Consumers:** `[app/src/main/kotlin/ui/components/hud/TelemetryHud.kt::TelemetryHud]`, `[app/src/main/kotlin/ui/components/optimization/HardwareDiagnosticsSection.kt::HardwareDiagnosticsSection]`.

##### `[app/src/main/kotlin/sensor/BatteryMonitor.kt::BatteryInfo]`
- **Purpose:** Telemetry model holding battery level %, charging status, power source, temperature °C, voltage mV, overheating flag, and computed `temperatureStatus: TemperatureStatus`.

##### `[app/src/main/kotlin/sensor/BatteryMonitor.kt::BatteryMonitor]`
- **Purpose:** BroadcastReceiver monitoring `ACTION_BATTERY_CHANGED`, `ACTION_POWER_CONNECTED`, and `ACTION_POWER_DISCONNECTED`.
- **Callbacks:** `onPowerConnected`, `onPowerDisconnected`, `onThermalWarning`, `onWarmTrigger`, `onLowBatteryCutoff`.
- **Emits:** `batteryState: StateFlow<BatteryInfo>`.

---

#### `app/src/main/kotlin/sensor/LocationTracker.kt`

##### `[app/src/main/kotlin/sensor/LocationTracker.kt::TelemetryData]`
- **Purpose:** Telemetry model capturing speed, speed display, canonical `speedUnit: SpeedUnit`, coordinates, altitude, bearing, cardinal heading, GPS fix flag, cumulative distance, `lastFixTimestamp: Long`, and `isSignalLost: Boolean`.

##### `[app/src/main/kotlin/sensor/LocationTracker.kt::LocationTracker]`
- **Purpose:** Fuses Android `LocationManager` (GPS/Network) and hardware `Sensor.TYPE_ROTATION_VECTOR` (Compass).
- **Invariants:**
  - Symmetrical listener hygiene: Unconditionally unregisters previous updates before re-registering to flush stale/orphaned OS callbacks.
  - Dual provider registration: Simultaneously requests `GPS_PROVIDER` (1s / 1m) and `NETWORK_PROVIDER` for immediate fix and robust system-level engagement.
  - Prefers GPS bearing when vehicle speed > 3 km/h.
  - Falls back to hardware magnetometer rotation matrix azimuth when stationary.
  - Filters out anomalous GPS teleportation jumps (> 150m in 0.5s).
- **Emits:** `telemetryState: StateFlow<TelemetryData>`.

---

#### `app/src/main/kotlin/sensor/SensorMonitor.kt`

##### `[app/src/main/kotlin/sensor/SensorMonitor.kt::GForceData]`
- **Purpose:** Model capturing normalized X, Y, Z G-forces, total G magnitude, and impact detection flag.

##### `[app/src/main/kotlin/sensor/SensorMonitor.kt::SensorMonitor]`
- **Purpose:** Hardware `Sensor.TYPE_ACCELEROMETER` listener. Computes total G-force magnitude `sqrt(x^2 + y^2 + z^2) / 9.80665`.
- **Performance Invariant:** Throttles UI StateFlow emissions (emits on impact, on > 0.2G delta, or 2Hz rate-limited) to prevent 60Hz main-thread recomposition spam.
- **Callbacks:** `onImpactDetected(gValue)`.
- **Emits:** `gForceState: StateFlow<GForceData>`.

---

#### `app/src/main/kotlin/sensor/VehicleTelemetryPipeline.kt`

##### `[app/src/main/kotlin/sensor/VehicleTelemetryPipeline.kt::VehicleTelemetryPipeline]`
- **Purpose:** Fuses GPS velocity and IMU accelerometer G-forces into higher-level driving safety assertions and interaction lockouts.
- **Key Invariants:**
  - 16 km/h speed threshold for UI driving interaction lockout.
  - 5-second deadband hysteresis window with accelerometer stillness corroboration (`abs(totalG - 1.0f) < 0.35f`).
- **Emits:** `isDrivingLockoutActive: StateFlow<Boolean>`.
- **Called By:** `[app/src/main/kotlin/viewmodel/DashcamViewModel.kt::DashcamViewModel]`, `[app/src/main/kotlin/camera/RecordingSessionCoordinator.kt::RecordingSessionCoordinator]`.

---

### Category D: Core Recording Engine & Storage (Engine Tier)

#### `app/src/main/kotlin/camera/CameraHardwareSpecManager.kt`

##### `[app/src/main/kotlin/camera/CameraHardwareSpecManager.kt::CameraHardwareProfile]`
- **Purpose:** Comprehensive hardware characteristics container (lens facing, sensor orientation, physical size, supported FPS ranges, aspect ratios, and resolution profiles).

##### `[app/src/main/kotlin/camera/CameraHardwareSpecManager.kt::SupportedResolutionInfo]`
- **Purpose:** Data model describing a hardware-verified video resolution (width, height, label, quality tier, verified FPS).

##### `[app/src/main/kotlin/camera/CameraHardwareSpecManager.kt::SupportedAspectRatioInfo]`
- **Purpose:** Data model cataloging supported aspect ratios (16:9, 4:3) with list of available resolutions.

##### `[app/src/main/kotlin/camera/CameraHardwareSpecManager.kt::CameraHardwareSpecManager]`
- **Purpose:** Hardware capability introspection service querying Camera2 `CameraCharacteristics` and `StreamConfigurationMap`.
- **Capabilities:**
  - Discovers physical sensor megapixels and active pixel array.
  - Maps supported resolutions into `VideoQuality` tiers for both 16:9 and 4:3 aspect ratios.
  - Queries actual AE target FPS ranges and `CamcorderProfile` to determine verified 30 FPS / 60 FPS support.
  - Provides fallback profiles for headless test / Robolectric sandboxes.
- **Consumers:** `[app/src/main/kotlin/viewmodel/DashcamViewModel.kt::DashcamViewModel]`, `[app/src/main/kotlin/ui/components/settings/DashcamSettingsSection.kt::DashcamSettingsSection]`.

---

#### `app/src/main/kotlin/camera/CameraXRecordingManager.kt`

##### `[app/src/main/kotlin/camera/CameraXRecordingManager.kt::CameraBindingState]`
- **Purpose:** Enum defining CameraX lifecycle binding state: `UNBOUND`, `BOUND`, `FAILED`.

##### `[app/src/main/kotlin/camera/CameraXRecordingManager.kt::CameraXRecordingManager]`
- **Purpose:** CameraX `VideoCapture<Recorder>` coordinator managing video recording lifecycle and rolling chunk rotation.
- **Core Invariants & Lifecycle Guarantees:**
  1. Stream Closure Guarantee: During chunk rotation, calls `stop()` on active recording and strictly awaits `VideoRecordEvent.Finalize` before creating the subsequent output file.
  2. Startup Interruption Protection: If teardown is requested before `VideoRecordEvent.Start` is emitted, calls `close()` immediately to trigger clean terminal `Finalize` without hanging.
  3. Dynamic Rotation Alignment: Aligns `VideoCapture.targetRotation` with device display orientation prior to recording, preventing 180° inverted videos.
  4. Mid-Recording Protection: Refuses mid-recording use-case destruction or orientation flips while a stream is actively writing.
- **Callbacks:** `onSegmentFinalized`, `onStartNextChunk`, `onRecordEvent`.
- **Emits:** `cameraBindingState: StateFlow<CameraBindingState>`.

---

#### `app/src/main/kotlin/camera/RecordingSessionCoordinator.kt`

##### `[app/src/main/kotlin/camera/RecordingSessionCoordinator.kt::RecordingSessionCoordinator]`
- **Purpose:** Domain coordinator managing active CameraX video recording sessions, rolling loop chunk timer and rotation, pre-flight storage admission checks, Room DB persistence, synchronized .srt subtitle telemetry sidecars, and emergency clip locking.
- **Role:** Autonomous Recording Session & Video Pipeline Coordinator.
- **Inputs:** UI intents, sensor interrupts (impact/motion/acoustic), battery/thermal state transitions, settings changes.
- **Outputs:** State updates, file management, Room clip insertions, subtitle sidecars.
- **Key Methods:** `initialize()`, `startRecording()`, `stopRecording()`, `toggleRecording()`, `rotateSegment()`, `toggleCurrentClipLock()`, `lockCurrentClip()`, `guaranteePostIncidentBuffer()`, `handleSentryOwnerReturnInterruption()`, `launchGpsBackfill()`, `release()`.
- **Called By:** `[app/src/main/kotlin/viewmodel/DashcamViewModel.kt::DashcamViewModel]`.

---

#### `app/src/main/kotlin/storage/ClipRepository.kt`

##### `[app/src/main/kotlin/storage/ClipRepository.kt::ClipRepository]`
- **Purpose:** Decoupled persistence repository abstracting Room Database queries, video clip CRUD, trip grouping synchronization, custom trip titles, and lock mutations from the presentation ViewModel tier.
- **Key Invariants:**
  - Encapsulates Room database I/O on background threads (`Dispatchers.IO`), returning reactive `StateFlow` streams (`allClips`, `lockedClips`, `trips`) to consumers (AGENTS.md Rule 7 & Rule 11).
  - Serializes all directory synchronization and reconciliation passes via `syncMutex: Mutex` and executes `clipDao.deduplicateClipsByPath()` prior to reconciliation, preventing concurrent sync race conditions and duplicate SQLite records.
  - Enforces `File.isDashGuardRecordingFile` signature validation (`VID_(DASH|SENTRY|LOOP|MANUAL|INCIDENT)_<YYYYMMDD>_<HHMMSS>`) before admitting any `.mp4` file into the database.
  - Owns `dashguard_trip_titles` persistence via dedicated SharedPreferences container, exposing reactive `tripCustomTitles: StateFlow<Map<String, String>>`, `saveTripCustomTitle()`, and `getTripCustomTitle()`, unifying custom trip titles directly with the `trips` flow.
  - Synchronizes clip metadata updates and fused video registration deterministically without exposing raw DAO transactions to UI components.
- **Called By:** `[app/src/main/kotlin/viewmodel/DashcamViewModel.kt::DashcamViewModel]`, `[app/src/main/kotlin/camera/RecordingSessionCoordinator.kt::RecordingSessionCoordinator]`.

##### `[app/src/main/kotlin/storage/ClipRepository.kt::ClipRepository.syncExistingClips]`
- **Purpose:** Scans physical clip and sidecar files on disk to synchronize and heal byte size changes and companion artifacts into Room DB metadata without touching uncorrupted clip properties, while intentionally preserving existing DB records when external storage or SD cards are temporarily unmounted so unattached clips remain visible until re-inserted or manually deleted.

##### `[app/src/main/kotlin/storage/ClipRepository.kt::ClipRepository.syncClipsFromDirectory]`
- **Purpose:** Mutex-serialized (`syncMutex`) directory reconciliation engine that runs `deduplicateClipsByPath()`, invokes `syncExistingClips()`, and synchronizes both existing raw video clips and derived telemetry-fused MP4 exports (strictly filtered by `File.isDashGuardRecordingFile`) from physical storage into the Room database.

---

#### `app/src/main/kotlin/sentry/SentryController.kt`

##### `[app/src/main/kotlin/sentry/SentryController.kt::SentryController]`
- **Purpose:** Decoupled coordinator governing Sentry surveillance mode, arming countdowns, cancellation, optical/acoustic sensitivity evaluation, auto-demote/auto-promote policies, and deterrent dispatch.
- **Key Invariants:**
  - Dynamic Auto-Demote & Auto-Promotion Protection: Demotes Tier 2 optical surveillance to Tier 1 shock standby under battery/thermal stress (when `sentryAutoDemoteEnabled` is true), and automatically restores Tier 2 when conditions recover with calibrated deadband hysteresis (5% battery margin or charging, 2.0°C thermal margin) to prevent rapid oscillation (AGENTS.md Rule 10 & 16).
  - Post-Incident Settling Cooldown: Enforces a 4000ms cooldown window via `canTriggerIncident()` and `markIncidentCompleted()` after incident recordings stop, preventing endless back-to-back re-trigger loops from pipeline rebind transients and camera AE settling (AGENTS.md Rule 4 & Rule 16).
  - Evaluates sensitivity thresholds strictly against canonical authorities: optical motion against `Sensitivity.motionDiffThreshold` and acoustic noise against `AcousticNoiseMonitor.getThresholdForSensitivity`, eliminating dead/conflicting tables (AGENTS.md Rule 11 & Rule 17).
  - Symmetrical Cancellation: Aborts in-progress arming countdowns cleanly without leaving the system in an ambiguous arming state (AGENTS.md Rule 9).
- **Called By:** `[app/src/main/kotlin/DashGuardApplication.kt::DashGuardApplication.sentryController]`, `[app/src/main/kotlin/viewmodel/DashcamViewModel.kt::DashcamViewModel]`, `[app/src/main/kotlin/camera/RecordingSessionCoordinator.kt::RecordingSessionCoordinator]`, `[app/src/main/kotlin/system/PowerAndThermalCoordinator.kt::PowerAndThermalCoordinator]`.

##### `[app/src/main/kotlin/sentry/SentryController.kt::SentryState]`
- **Purpose:** Reactive state data model tracking armed status, countdown seconds, arming flag, and detected event counts.

---

#### `app/src/main/kotlin/playback/VideoPlaybackCoordinator.kt`

##### `[app/src/main/kotlin/playback/VideoPlaybackCoordinator.kt::VideoPlaybackCoordinator]`
- **Purpose:** Decoupled video playback coordinator managing playback state transitions, synchronized `.srt` telemetry subtitle loading/caching, binary timestamp searching, and trip-contiguous clip navigation.
- **Key Invariants:**
  - Subtitle Caching & Binary Search: Caches parsed `.srt` sidecars and uses binary search for $O(\log n)$ telemetry HUD watermark synchronization during video scrubbing and playback.
  - Playlist Traversal: Supports seamless continuous playback auto-advance and trip-scoped adjacent clip navigation.
- **Called By:** `[app/src/main/kotlin/DashGuardApplication.kt::DashGuardApplication.playbackCoordinator]`, `[app/src/main/kotlin/viewmodel/DashcamViewModel.kt::DashcamViewModel]`.

##### `[app/src/main/kotlin/playback/VideoPlaybackCoordinator.kt::VideoPlaybackState]`
- **Purpose:** State model managing video playback screen, active clip, synchronized telemetry watermark text, continuous auto-advance, and loop adjacency.

---

#### `app/src/main/kotlin/system/SystemDiagnosticsManager.kt`

##### `[app/src/main/kotlin/system/SystemDiagnosticsManager.kt::SystemOptimizationState]`
- **Purpose:** Immutable data state encapsulating battery whitelist exemption, kiosk mode pinning, DND policy access, and storage availability.

##### `[app/src/main/kotlin/system/SystemDiagnosticsManager.kt::SystemDiagnosticsManager]`
- **Purpose:** Authoritative coordinator for OS-level permissions, battery optimization whitelisting, kiosk pinning, auto-DND lifecycle, and storage availability discovery.
- **Key Methods:** `refreshSystemDiagnostics()`, `applyAutoDndIfEnabled()`, `restoreAutoDndIfSet()`, `updateStorageOptions()`.
- **Called By:** `[app/src/main/kotlin/DashGuardApplication.kt::DashGuardApplication.systemDiagnosticsManager]`, `[app/src/main/kotlin/viewmodel/DashcamViewModel.kt::DashcamViewModel]`, `[app/src/main/kotlin/camera/RecordingSessionCoordinator.kt::RecordingSessionCoordinator]`.

---

#### `app/src/main/kotlin/system/PowerAndThermalCoordinator.kt`

##### `[app/src/main/kotlin/system/PowerAndThermalCoordinator.kt::PowerAndThermalCoordinator]`
- **Purpose:** Dedicated coordinator for power supply transitions, AC unplug countdown shutdown, low-battery safety cutoff, thermal warning/auto-dim, hysteretic sleep recovery, and Sentry auto-demote/promote lifecycle management.
- **Role:** Device Power & Thermal Safety Coordinator.
- **Inputs:** `BatteryMonitor` events (charging state, level, temperature), user settings, device operational state.
- **Outputs:** Power & thermal UI state transformations, sleep mode activations, recording triggers/stops.
- **Key Methods:** `initialize()`, `startUnplugCountdown()`, `cancelUnplugShutdown()`, `triggerThermalCoolingSleep()`, `cancelThermalCooling()`, `release()`.
- **Called By:** `[app/src/main/kotlin/viewmodel/DashcamViewModel.kt::DashcamViewModel]`.

##### `[app/src/main/kotlin/system/PowerAndThermalCoordinator.kt::PendingPostLockAction]`
- **Purpose:** Lifecycle state enumeration queuing deferred state transitions (power-loss shutdown, sentry arming on power loss, thermal cooling sleep, low-battery sleep) while an emergency locked clip is actively recording to disk, preventing premature pipeline teardown until clip finalization (AGENTS.md Rule 9 & Rule 12). Queried and executed upon segment finalization via `hasPendingPostLockAction` and `executePendingPostLockAction` functional callbacks wired by `DashcamViewModel`.
- **Consumers:** `[app/src/main/kotlin/system/PowerAndThermalCoordinator.kt::PowerAndThermalCoordinator]`.

---

#### `app/src/main/kotlin/storage/LoopStorageManager.kt`

##### `[app/src/main/kotlin/storage/LoopStorageManager.kt::StorageStats]`
- **Purpose:** Data model capturing app video bytes, locked video bytes, loop buffer bytes, free device bytes, total device bytes, and maximum quota limit.

##### `[app/src/main/kotlin/storage/LoopStorageManager.kt::LoopStorageManager]`
- **Purpose:** Manages video and snapshot file creation, storage quota accounting, automated FIFO retention cleanup, and canonical clip sidecar eviction.
- **Key Functions:**
  - `createNewVideoFileWithTimestamp(prefix)`: Atomically generates monotonic, collision-free `.mp4` file references.
  - `createNewSnapshotFile(prefix)`: Generates `.jpg` snapshot files.
  - `defaultAppVideosDir`: Routes directly to `StorageDirectoryManager.getDefaultAppStorageDirectory(context)` as the single source of truth for filesystem geometry.
  - `getStorageStats(maxAllowedGb)`: Computes exact disk usage scoped to the active storage directory (filtering physical files strictly via `File.isDashGuardRecordingFile`), querying `StorageDirectoryManager` for filesystem geometry.
  - `deleteClipAndSidecars(clip, forceDeleteLocked)`: Canonical method for deleting an individual video clip, its companion `.srt` subtitle sidecar, and any `-telemetry-fused.mp4` derivatives while purging the Room DB record. Strictly preserves locked clips unless explicitly forced.
  - `performLoopCleanupIfNeeded(maxAllowedGb)`: Multi-pass retention engine:
    - Pass 1: Evicts oldest unlocked clips strictly scoped to the active storage directory (via `StorageMigrationManager.getClipsInDirectory`) along with companion `.srt` sidecars and `-telemetry-fused.mp4` derivatives via `deleteClipAndSidecars`, safely preserving historical footage left in older directories.
    - Pass 2: Purges untracked orphaned DashGuard recording files (`File.isDashGuardRecordingFile`) older than 120 seconds while strictly ignoring non-app foreign media files.
    - Strict Invariant: Never deletes locked files, foreign non-app files, files in historical storage directories, or files modified in the last 120 seconds. Preserves dynamic OS safety buffer (`clamp(500 MB, totalPartitionBytes * 0.02, 5 GB)`).

##### `[app/src/main/kotlin/storage/LoopStorageManager.kt::LoopStorageManager.deleteClipAndSidecars]`
- **Purpose:** Canonical single-point eviction method for a video clip, deleting its primary `.mp4` file, paired `.srt` subtitle sidecar, and any `-telemetry-fused.mp4` derivatives, and removing its record from Room DB.
- **Invariants:** Strictly preserves locked clips unless `forceDeleteLocked == true` is explicitly passed.
- **Called By:** `[app/src/main/kotlin/storage/LoopStorageManager.kt::LoopStorageManager]`, `[app/src/main/kotlin/viewmodel/DashcamViewModel.kt::DashcamViewModel]`.

---

#### `app/src/main/kotlin/storage/StorageDirectoryManager.kt`

##### `[app/src/main/kotlin/storage/StorageDirectoryManager.kt::StorageOptionInfo]`
- **Purpose:** Data model describing a candidate storage volume or directory (type, title, description, resolved path, free bytes, total bytes, isWritable, isAvailable).

##### `[app/src/main/kotlin/storage/StorageDirectoryManager.kt::StorageMetrics]`
- **Purpose:** Data model reporting volume-wide storage metrics (free bytes, total bytes, app used bytes, OS safety buffer, max assignable GB).

##### `[app/src/main/kotlin/storage/StorageDirectoryManager.kt::StorageDirectoryManager]`
- **Purpose:** Authoritative storage directory resolver, permissions inspector, capacity calculator, and in-app folder browser (Single Source of Truth for storage geometry).
- **Key Methods & Invariants:**
  - `getAvailableBytes(directory)`: Canonical query for available free space on the physical volume hosting the specified directory.
  - `getTotalBytes(directory)`: Canonical query for total capacity on the physical volume hosting the specified directory.
  - `getOsSafetyBufferBytes(directory)`: Dynamic proportional safety buffer: `clamp(500 MB, totalPartitionBytes * 0.02, 5 GB)` (AGENTS.md Rule 10).
  - Stable Pool Capacity: `totalPool = physicalFreeBytes + appUsedBytes`.
  - Max Assignable Quota: `(totalPool - osSafetyBuffer) / 1GB`. Older app footage is recycled, so current app footage is included in available quota.
  - Dynamic Quota Step Generation: Generates step increments up to directory ceiling.
  - SAF Tree URI resolver converting `content://` tree URIs to canonical filesystem paths.

---

#### `app/src/main/kotlin/storage/StorageMigrationManager.kt`

##### `[app/src/main/kotlin/storage/StorageMigrationManager.kt::MigrationProgress]`
- **Purpose:** Progress model for active migration (file index, total files, bytes transferred, percentage, errors).

##### `[app/src/main/kotlin/storage/StorageMigrationManager.kt::StorageMigrationManager]`
- **Purpose:** Engine for atomic cross-directory and cross-partition footage migration.
- **Key Invariants:**
  - Pre-flight capacity gate: Verifies destination directory can accommodate required bytes plus dynamic OS safety buffer.
  - Federated query scoping: Scopes clips by directory canonical paths.
  - Idempotent self-moves (`isSelfMove`): Instant zero-cost success if source and destination canonical identities match (AGENTS.md Rule 15).
  - Two-Phase Commit: Streams data, verifies destination byte count and readability, updates Room database (including companion `-telemetry-fused.mp4` `VideoClip` records without orphaning paths), and only unlinks source file upon verified completion.

##### `[app/src/main/kotlin/storage/StorageMigrationManager.kt::StorageMigrationManager.calculateClipTotalBytes]`
- **Purpose:** Authoritative calculation of physical clip disk consumption, aggregating the raw video loop (`.mp4`), companion SubRip sidecar (`.srt`), and derived telemetry-fused export (`-telemetry-fused.mp4` via `includeFusedCompanion`, preventing double-counting when the fused export is also registered as its own `VideoClip` row). Guarantees 100% capacity calculation parity across migration dialogs and gallery quota meters (AGENTS.md Rule 11).

---

#### `app/src/main/kotlin/media/TelemetrySubtitleManager.kt`

##### `[app/src/main/kotlin/media/TelemetrySubtitleManager.kt::TelemetryPoint]`
- **Purpose:** 1-second sampled telemetry data record (speed, coordinates, altitude, heading, G-force, lock status).

##### `[app/src/main/kotlin/media/TelemetrySubtitleManager.kt::TelemetrySubtitleEntry]`
- **Purpose:** Parsed SubRip `.srt` entry containing start/end millisecond timestamps and formatted text line.

##### `[app/src/main/kotlin/media/TelemetrySubtitleManager.kt::TelemetrySubtitleManager]`
- **Purpose:** Watermark subtitle formatting, standard `.srt` sidecar file generator, and synchronized `.srt` parser.
- **Key Functions:**
  - `formatTelemetryLine`: Formats canonical dashcam watermark string.
  - `writeSubtitleFile`: Serializes `List<TelemetryPoint>` to `.srt` format with millisecond timestamps.
  - `parseSubtitleFile`: Parses `.srt` file into `TelemetrySubtitleEntry` list for synchronized playback HUD.
  - `getTelemetryForPosition`: Binary/linear lookup of telemetry string for given playback millisecond.

---

#### `app/src/main/kotlin/media/TelemetryVideoFusionEngine.kt`

##### `[app/src/main/kotlin/media/TelemetryVideoFusionEngine.kt::VideoSourceMetadata]`
- **Purpose:** Container holding source video width, height, rotation degrees, duration, and frame rate.

##### `[app/src/main/kotlin/media/TelemetryVideoFusionEngine.kt::TelemetryVideoFusionEngine]`
- **Purpose:** Hardware-accelerated video transcoding engine fusing telemetry watermarks directly into video pixel streams using AndroidX Media3 Transformer.
- **Key Invariants:**
  - Preserves source bitrate and orientation.
  - Generates `-telemetry-fused.mp4` derivative files with custom Canvas overlay.
  - Adapts watermark text between single-line and two-line layouts based on video dimensions.

---

#### `app/src/main/kotlin/analytics/TripGroupingEngine.kt`

##### `[app/src/main/kotlin/analytics/TripGroupingEngine.kt::TripGroupingEngine]`
- **Purpose:** Domain engine clustering discrete video loops into continuous `TripSession` entities and computing playback adjacency.
- **Key Algorithms:**
  - Clusters loops separated by `<= tripGroupingGapMinutes` into a unified Trip.
  - Distinguishes Sentry events from driving loops.
  - Computes weighted average speed, total distance, duration, and incident count.
  - Determines immediate playback predecessor/successor within `loopAdjacencyThresholdSeconds`.

---

### Category E: Service & State Orchestration (Orchestration Tier)

#### `app/src/main/kotlin/service/DashcamForegroundService.kt`

##### `[app/src/main/kotlin/service/DashcamForegroundService.kt::DashcamForegroundService]`
- **Purpose:** Android Foreground Service providing process survival, partial wake lock, and ongoing notification with emergency lock action.
- **Foreground Lifecycle Invariant:** Promotes to foreground immediately in `onCreate()` with an initial notification, fulfilling the Android OS contract before any asynchronous transition or stop action can execute (eliminating `ForegroundServiceDidNotStartInTimeException`).
- **Foreground Service Types:** Consolidates permission checks and service bitmask calculation into `computeForegroundServiceType()`, dynamically requesting `camera | location | dataSync | specialUse` without duplicate runtime checks.
- **Actions:** `ACTION_START`, `ACTION_STOP`, `ACTION_EMERGENCY_LOCK`. Emergency lock is relayed to `DashcamViewModel` as the in-app broadcast `ACTION_EMERGENCY_LOCK_TRIGGER` (`io.github.roaxio.dashguardpro.EMERGENCY_LOCK_TRIGGER`).
- **Called By:** `[app/src/main/kotlin/viewmodel/DashcamViewModel.kt::DashcamViewModel]`, `[app/src/main/kotlin/camera/RecordingSessionCoordinator.kt::RecordingSessionCoordinator]`.

---

#### `app/src/main/kotlin/viewmodel/DashcamAlertPresenter.kt`

##### `[app/src/main/kotlin/viewmodel/DashcamAlertPresenter.kt::DashcamAlertPresenter]`
- **Purpose:** Presenter coordinating transient UI notification timers, countdown loops (`unplugCountdownSeconds`), snapshot flash durations, impact alert auto-dismissals, and speed lockout visibility states.
- **Invariants:** Strictly symmetric coroutine lifecycle teardown (`resetAllAlerts()`) adhering to AGENTS.md Rule 7 & Rule 9.
- **Called By:** `[app/src/main/kotlin/viewmodel/DashcamViewModel.kt::DashcamViewModel]`, `[app/src/main/kotlin/camera/RecordingSessionCoordinator.kt::RecordingSessionCoordinator]`, `[app/src/main/kotlin/system/PowerAndThermalCoordinator.kt::PowerAndThermalCoordinator]`.

---

#### `app/src/main/kotlin/viewmodel/DashcamViewModel.kt`

##### `[app/src/main/kotlin/viewmodel/DashcamViewModel.kt::DashcamViewModel]`
- **Purpose:** Central architectural ViewModel orchestrating hardware monitors, recording lifecycle, Sentry state machine, thermal/battery sleep recovery, storage retention, and UI state flows.
- **Core State Flows:** `uiState`, `settings`, `telemetry`, `gForce`, `battery`, `allClips`, `lockedClips`, `trips`, `playbackState`, `systemOptState`, `cameraProfile`, `migrationState`, `isDrivingLockoutActive`.
- **Lifecycle Invariants:**
  - `recordingLifecycleMutex: Mutex`: Serializes start/stop cycles deterministically, eliminating heuristic polling loops (AGENTS.md Rule 4 & Rule 9).
  - `Standby Override Isolation & Thermal Symmetrical Recovery`: Tracks `recordingOriginState` to ensure discrete emergency overrides woken from Standby safely return to Standby upon segment finalization rather than rolling over into continuous driving loops. Defers thermal cooling sleep during in-flight emergency locks via `pendingThermalSleep` and proactive HUD dimming, cleanly transitioning to Thermal Cooling Sleep once evidence is committed (AGENTS.md Rule 9).
  - Hysteretic Speed Lockout: Employs a 5-second deadband hysteresis and accelerometer stillness check to prevent premature UI unlocking during tunnel GPS drops.
  - Symmetrical GPS Telemetry Lifecycle: Re-asserts location tracking on recording start and activity foreground resumption (`onActivityResumed()`), flushing stale listener state to prevent Android 14+ background disconnect purgatory (AGENTS.md Rule 9 & Rule 16.1).
- **Primary Operations:**
  - `startRecording(reason)`: Pre-flight storage/permission check, opens CameraX recording, acquires wake lock, applies Auto-DND, initiates segment timer.
  - `stopRecording(autoArmSentry)`: Stops CameraX, finalizes Room record, restores DND, evaluates Sentry auto-arm triggers.
  - `rotateSegment()`: Triggers seamless CameraX chunk rotation at interval boundaries.
  - `lockCurrentClip(reason)`: Marks current recording locked, guarantees post-incident buffer, vibrates haptics.
  - `handleImpactDetected(gValue)`: Evaluates G-force against threshold; triggers emergency lock or Sentry wake.
  - `handleAcousticAlert(db)`: Evaluates decibel spikes; wakes Sentry camera in Tier 1 standby.
  - `handleMotionDetected(score, luma)`: Optical motion trigger in Tier 2 Sentry mode; initiates incident capture.
  - `armSentryMode(delaySeconds)` / `disarmSentryMode(isManual)`: Sentry mode state transitions.
  - `migrateFootageToNewDirectory(targetDir)`: Executes atomic storage migration.

##### `[app/src/main/kotlin/viewmodel/DashcamViewModel.kt::DashcamViewModel.galleryNavState]`
- **Purpose:** Public `StateFlow<GalleryNavState>` broadcasting active view mode, filter category, expanded trip IDs, and scroll/focus state across playback transitions and configuration changes; explicitly reset to default (`GalleryNavState()`) via `resetGalleryNavigationMemory()` when exiting the Footage screen (`NavigationTab.GALLERY`) via back navigation.

##### `[app/src/main/kotlin/viewmodel/DashcamViewModel.kt::DashcamViewModel.syncClipsFromStorageDirect]`
- **Purpose:** Synchronous IO dispatch invoking `clipRepository.syncClipsFromDirectory(storageManager.videosDir)` directly (which internally executes `deduplicateClipsByPath()`, `syncExistingClips()`, and directory reconciliation under `syncMutex`) to reconcile Room clip byte counts and companion sidecars with actual disk usage prior to migrations or quota checks.

---

#### `app/src/main/kotlin/viewmodel/DashcamIntent.kt`

##### `[app/src/main/kotlin/viewmodel/DashcamIntent.kt::DashcamIntent]`
- **Purpose:** Sealed interface hierarchy defining unidirectional MVI user interactions dispatched from Compose UI to `DashcamViewModel.onIntent(intent)`.
- **Variants:** `ToggleRecording`, `ToggleCurrentClipLock`, `LockCurrentClip`, `SetMode`, `ToggleOledMode`, `SetOledMode`, `ToggleNightExposure`, `SetScreenBrightness`, `ToggleTorch`, `ToggleAudio`, `ToggleCameraLens`, `CycleHudDisplayMode`, `ArmSentryMode`, `DisarmSentryMode`, `CancelSentryArming`, `ConfirmStartDashcamAfterDisarm`, `DismissStartDashcamPrompt`, `CancelThermalCooling`, `CancelUnplugShutdown`, `CaptureSnapshot`, `ClearIncidentMessage`, `DismissSpeedLockout`, `UpdateSettings`, `MigrateFootageToNewDirectory`, `DismissMigrationProgress`, `ResetStorageMigrationState`, `RefreshStorageStats`, `RefreshSystemDiagnostics`, `SetGalleryViewMode`, `SetGalleryFilter`, `ToggleTripExpanded`, `SetTripExpanded`, `ResetGalleryNavigationMemory`, `SaveTripCustomTitle`, `DeleteClip`, `DeleteTrip`, `ToggleClipLock`, `LockTripClips`, `UnlockTripClips`, `OpenClipForPlayback`, `ClosePlayback`, `ToggleContinuousPlayback`, `UpdatePlaybackState`.
- **Called By:** UI composables via `DashcamViewModel.onIntent()`.

---

#### `app/src/main/kotlin/viewmodel/state/DashcamUiState.kt`

##### `[app/src/main/kotlin/viewmodel/state/DashcamUiState.kt::RecordingOriginState]`
- **Purpose:** Enum defining the operational baseline state (`STANDBY`, `DRIVING_LOOP`, `SENTRY`) from which a recording was initiated.
- **Role:** Enforces Standby Override Isolation & Symmetrical Recovery Hygiene (AGENTS.md Rule 9) to guarantee that temporary high-priority overrides do not leak continuous state into background modes.
- **Values:** `STANDBY`, `DRIVING_LOOP`, `SENTRY`.
- **Called By:** `[app/src/main/kotlin/viewmodel/DashcamViewModel.kt::DashcamViewModel]`, `[app/src/main/kotlin/camera/RecordingSessionCoordinator.kt::RecordingSessionCoordinator]`.

##### `[app/src/main/kotlin/viewmodel/state/DashcamUiState.kt::TripStats]`
- **Purpose:** Telemetry aggregation state model tracking current session drive time, total distance, max speed, average speed, and lock counts.

##### `[app/src/main/kotlin/viewmodel/state/DashcamUiState.kt::DashcamUiState]`
- **Purpose:** Primary reactive presentation state data class holding recording flags, segment timer, armed state, OLED stealth mode, alert messages, incident locks, and transient notifications.
- **De-Shadowed Sleep Properties:** Backs `isThermalCoolingActive`, `isLowBatterySleepActive`, and `isSleeping` as computed properties derived directly from `deviceSleepMode`, eliminating multi-boolean state desynchronization (AGENTS.md Rule 1 & Rule 8).
- **Reactive Sentry Synchronization:** Arming flags and countdowns are reactively bound to `SentryController.sentryState` at ViewModel initialization, eliminating duplicate collection jobs.

---

#### `app/src/main/kotlin/viewmodel/state/DeviceSleepState.kt`

##### `[app/src/main/kotlin/viewmodel/state/DeviceSleepState.kt::DeviceSleepMode]`
- **Purpose:** Formal state machine enum governing low-power and protection sleep modes, eliminating single-boolean overload and UI state conflation.
- **Values:** `NONE` (normal operation), `THERMAL_COOLING` (high-temp cutoff), `LOW_BATTERY_STANDBY` (depleted battery cutoff).
- **Called By:** `[app/src/main/kotlin/viewmodel/DashcamViewModel.kt::DashcamViewModel]`, `[app/src/main/kotlin/system/PowerAndThermalCoordinator.kt::PowerAndThermalCoordinator]`, `[app/src/main/kotlin/viewmodel/state/DashcamUiState.kt::DashcamUiState]`, `[app/src/main/kotlin/ui/screens/CameraHudScreen.kt::CameraHudScreen]`, `[app/src/main/kotlin/ui/components/hud/OledPowerSaverOverlay.kt::OledPowerSaverOverlay]`.

##### `[app/src/main/kotlin/viewmodel/state/DeviceSleepState.kt::PreSleepOperatingState]`
- **Purpose:** Origin state tracking enum that records the exact operational duty (`STANDBY`, `DRIVING_LOOP`, `SENTRY_TIER1_SHOCK`, `SENTRY_TIER2_OPTICAL`) active prior to sleep admission.
- **Role:** Guarantees truthful symmetrical recovery so that waking from sleep returns to the precise prior state without false rollover into unintended modes.
- **Called By:** `[app/src/main/kotlin/viewmodel/DashcamViewModel.kt::DashcamViewModel]`, `[app/src/main/kotlin/system/PowerAndThermalCoordinator.kt::PowerAndThermalCoordinator]`, `[app/src/main/kotlin/viewmodel/state/DashcamUiState.kt::DashcamUiState]`, `[app/src/main/kotlin/ui/components/hud/OledPowerSaverOverlay.kt::OledPowerSaverOverlay]`.

##### `[app/src/main/kotlin/viewmodel/state/DeviceSleepState.kt::SleepTriggerCause]`
- **Purpose:** Enum recording the specific event causing sleep admission: `THERMAL_OVERHEAT`, `LOW_BATTERY`.

---

#### `app/src/main/kotlin/viewmodel/state/ActiveSegmentData.kt`

##### `[app/src/main/kotlin/viewmodel/state/ActiveSegmentData.kt::ActiveSegmentData]`
- **Purpose:** Thread-safe data class tracking the in-flight recording segment (file, startTimeMs, startLat, startLng, maxGForce, maxSpeed), utilizing concurrent `CopyOnWriteArrayList` collections for `speedSamples` and `telemetryPoints` to guarantee safe lock-free snapshots during rollover and concurrent timer sampling (AGENTS.md Rule 4 & Rule 9).

---

### Category F: Presentation & UI Layer (Surface Tier)

#### Root Components (`app/src/main/kotlin/ui/components/`)

##### `[app/src/main/kotlin/ui/components/SpeedSafetyLockoutOverlay.kt::SpeedSafetyLockoutOverlay]`
- **Purpose:** Driving safety barrier active when speed > 16 km/h, dynamically adapting viewport padding and card constraints via `rememberScreenMetrics` (AGENTS.md Rule 22).

##### `[app/src/main/kotlin/ui/components/ResponsiveLayout.kt::WindowSizeBucket]`
- **Purpose:** Adaptive window classification enum: `COMPACT`, `STANDARD`, `EXPANDED`.

##### `[app/src/main/kotlin/ui/components/ResponsiveLayout.kt::ScreenMetrics]`
- **Purpose:** Immutable data class holding window width/height dp, size bucket, orientation flags, and dynamic padding scalars.

##### `[app/src/main/kotlin/ui/components/ResponsiveLayout.kt::rememberScreenMetrics]`
- **Purpose:** Reactive window size classifier computing `ScreenMetrics`.

##### `[app/src/main/kotlin/ui/components/ResponsiveLayout.kt::rememberFluidFontSize]`
- **Purpose:** Derives clamped fluid typography scaled proportionally from runtime viewport geometry (AGENTS.md Rule 22.2).

---

#### HUD Components (`app/src/main/kotlin/ui/components/hud/`)

##### `[app/src/main/kotlin/ui/components/hud/CameraPreviewView.kt::CameraPreviewView]`
- **Purpose:** SurfaceView/TextureView Compose bridge displaying live CameraX viewfinder and binding `MotionDetectionAnalyzer`. Manages surface detachment during OLED dimmed states to save GPU/thermal power.

##### `[app/src/main/kotlin/ui/components/hud/TelemetryHud.kt::TelemetryHud]`
- **Purpose:** HUD overlay rendering speed, compass heading, G-force radar, battery, GPS fix, and dashcam timestamp in FULL and COMPACT modes. Smoked glass aesthetic with 100% opaque indicators, adapting padding proportionally via `rememberScreenMetrics` (AGENTS.md Rule 22).

##### `[app/src/main/kotlin/ui/components/hud/OledPowerSaverOverlay.kt::OledPowerSaverOverlay]`
- **Purpose:** True-black (#000000) OLED stealth overlay with pulsing deterrent alarm ring, 3-second long-press unlock barrier, and incident flash deterrent. Distinct rendering branches for `THERMAL_COOLING` (cyan theme) vs `LOW_BATTERY_STANDBY` (amber theme), with truthful origin-based recovery subtitles derived from `PreSleepOperatingState`.

##### `[app/src/main/kotlin/ui/components/hud/HudControlDock.kt::LandscapeLeftCockpitDeck]`
- **Purpose:** Ergonomic left-hand control dock for landscape cockpit HUD, housing fast-access, PIP/density, emergency lock, and primary shutter controls.
- **Consumers:** `[app/src/main/kotlin/ui/screens/CameraHudScreen.kt::CameraHudScreen]`.

##### `[app/src/main/kotlin/ui/components/hud/HudControlDock.kt::PortraitCockpitDeck]`
- **Purpose:** Ergonomic bottom control dock for portrait orientation HUD.
- **Consumers:** `[app/src/main/kotlin/ui/screens/CameraHudScreen.kt::CameraHudScreen]`.

##### `[app/src/main/kotlin/ui/components/hud/HudControlDock.kt::PermissionsGuardCard]`
- **Purpose:** Pre-flight permissions barrier card guiding driver to grant Camera and Audio permissions.
- **Consumers:** `[app/src/main/kotlin/ui/screens/CameraHudScreen.kt::CameraHudScreen]`.

---

#### Components: Common (`app/src/main/kotlin/ui/components/common/`)

#### `app/src/main/kotlin/ui/components/common/NumericThresholdDialog.kt`

##### `[app/src/main/kotlin/ui/components/common/NumericThresholdDialog.kt::NumericThresholdDialog]`
- **Purpose:** Authoritative parameterized cyber-styled numeric threshold input dialog providing `-`/`+` stepper adjustment buttons, direct keyboard input (`BasicTextField` with headless Robolectric fallback per AGENTS.md Rule 3), digit filtering, bounds validation, error display, and high-contrast confirm/dismiss action buttons.
- **Consumers:** `[app/src/main/kotlin/ui/components/settings/StorageSettingsSection.kt::StorageSettingsSection]`, `[app/src/main/kotlin/ui/components/settings/CustomThresholdDialogs.kt::CustomTripGapDialog]`, `[app/src/main/kotlin/ui/components/settings/CustomThresholdDialogs.kt::CustomSentryArmingDelayDialog]`, `[app/src/main/kotlin/ui/components/settings/CustomThresholdDialogs.kt::CustomSentryIncidentDurationDialog]`, `[app/src/main/kotlin/ui/components/settings/CustomThresholdDialogs.kt::CustomSentryDemoteTempDialog]`, `[app/src/main/kotlin/ui/components/settings/CustomThresholdDialogs.kt::CustomIncidentBufferDialog]`, `[app/src/main/kotlin/ui/components/optimization/OptimizationThresholdDialogs.kt::CustomLowBatteryDialog]`, `[app/src/main/kotlin/ui/components/optimization/OptimizationThresholdDialogs.kt::CustomThermalTempDialog]`, `[app/src/main/kotlin/ui/components/optimization/OptimizationThresholdDialogs.kt::CustomSleepEmergencyDurationDialog]`.

---

#### `app/src/main/kotlin/ui/components/common/DashboardPrimitives.kt`

##### `[app/src/main/kotlin/ui/components/common/DashboardPrimitives.kt::DashboardSectionHeader]`
- **Purpose:** Common section header with cyber-styled uppercase letter spacing.
- **Consumers:** `[app/src/main/kotlin/ui/components/settings/SettingsPrimitives.kt::SectionHeader]`.

##### `[app/src/main/kotlin/ui/components/common/DashboardPrimitives.kt::DashboardCard]`
- **Purpose:** Common Card container with responsive horizontal padding and subtle dark border.
- **Consumers:** `[app/src/main/kotlin/ui/components/settings/SettingsPrimitives.kt::SettingsCard]`.

##### `[app/src/main/kotlin/ui/components/common/DashboardPrimitives.kt::DashboardToggleRow]`
- **Purpose:** Unified, responsive Toggle Row supporting two-tier full-width layout for narrow screens and customizable accent track colors (CyberCyan, NeonGreen).
- **Consumers:** `[app/src/main/kotlin/ui/components/settings/SettingsPrimitives.kt::SettingsToggleRow]`, `[app/src/main/kotlin/ui/components/optimization/OptimizationPrimitives.kt::OptimizationToggleRow]`.

##### `[app/src/main/kotlin/ui/components/common/DashboardPrimitives.kt::DashboardDropdownRow]`
- **Purpose:** Unified, responsive Dropdown Row that automatically adapts layout: stacks on compact screens (<420dp) and renders side-by-side row on expanded screens.
- **Consumers:** `[app/src/main/kotlin/ui/components/settings/SettingsPrimitives.kt::SettingsDropdownRow]`, `[app/src/main/kotlin/ui/components/optimization/OptimizationPrimitives.kt::OptimizationDropdownRow]`.

##### `[app/src/main/kotlin/ui/components/common/DashboardPrimitives.kt::DashboardTopAppBar]`
- **Purpose:** Authoritative compact automotive top bar reclaiming bloated framework padding with integrated status bar insets and accessible 48dp touch bounds.
- **Consumers:** `[app/src/main/kotlin/ui/screens/SettingsScreen.kt::SettingsScreen]`, `[app/src/main/kotlin/ui/screens/OptimizationScreen.kt::OptimizationScreen]`, `[app/src/main/kotlin/ui/screens/GalleryScreen.kt::GalleryScreen]`, `[app/src/main/kotlin/ui/components/optimization/SystemProtectionSubMenuScreen.kt::SystemProtectionSubMenuScreen]`, `[app/src/main/kotlin/ui/components/settings/SentrySubMenuScreen.kt::SentrySubMenuScreen]`, `[app/src/main/kotlin/ui/components/settings/SmartPlaybackSubMenuScreen.kt::SmartPlaybackSubMenuScreen]`.

---

#### Components: Gallery (`app/src/main/kotlin/ui/components/gallery/`)

##### `[app/src/main/kotlin/ui/components/gallery/StorageBar.kt::StorageBar]`
- **Purpose:** Interactive segmented storage bar showing app video bytes, locked video bytes, loop buffer, and free space percentage.
- **Consumers:** `[app/src/main/kotlin/ui/screens/GalleryScreen.kt::GalleryScreen]`.

##### `[app/src/main/kotlin/ui/components/gallery/GalleryFilterBar.kt::GalleryFilterBar]`
- **Purpose:** Segmented filter chips row for switching between footage categories (All, Protected, Dashcam, Sentry, Telemetry Fused) with count badges and test tags.
- **Consumers:** `[app/src/main/kotlin/ui/screens/GalleryScreen.kt::GalleryScreen]`.

##### `[app/src/main/kotlin/ui/components/gallery/TripRenameDialog.kt::TripRenameDialog]`
- **Purpose:** Modal dialog for renaming trip sessions with title preview and reset-to-default actions.
- **Consumers:** `[app/src/main/kotlin/ui/screens/GalleryScreen.kt::GalleryScreen]`.

##### `[app/src/main/kotlin/ui/components/gallery/ClipItemRow.kt::ClipItemRow]`
- **Purpose:** Modular video clip card displaying thumbnail, timestamp, duration, telemetry badges, lock state, and action buttons (play, lock/unlock, share, delete).
- **Consumers:** `[app/src/main/kotlin/ui/screens/GalleryScreen.kt::GalleryScreen]`, `[app/src/main/kotlin/ui/components/gallery/TripSessionCard.kt::TripSessionCard]`.

##### `[app/src/main/kotlin/ui/components/gallery/TripSessionCard.kt::TripSessionCard]`
- **Purpose:** Collapsible trip session container displaying trip overview, continuity timeline strip, metrics (duration, distance, avg speed, incidents), and expandable loop segments drawer.
- **Consumers:** `[app/src/main/kotlin/ui/screens/GalleryScreen.kt::GalleryScreen]`.

##### `[app/src/main/kotlin/ui/components/gallery/ShareChoiceDialog.kt::ShareChoiceDialog]`
- **Purpose:** Modal dialog presenting video sharing choices: sharing raw MP4 with companion SRT subtitles or burned-in fused telemetry video.
- **Consumers:** `[app/src/main/kotlin/ui/screens/GalleryScreen.kt::GalleryScreen]`, `[app/src/main/kotlin/ui/components/playback/VideoPlayerDialogs.kt::VideoPlayerDialogs]`.

##### `[app/src/main/kotlin/ui/components/gallery/VideoThumbnailView.kt::VideoThumbnailView]`
- **Purpose:** Asynchronous video frame thumbnail loader with in-memory caching.
- **Consumers:** `[app/src/main/kotlin/ui/components/gallery/ClipItemRow.kt::ClipItemRow]`.

##### `[app/src/main/kotlin/ui/components/gallery/VideoThumbnailView.kt::VideoThumbnailCache]`
- **Purpose:** In-memory LRU bitmap and aspect-ratio cache for video thumbnails.
- **Consumers:** `[app/src/main/kotlin/ui/components/gallery/VideoThumbnailView.kt::VideoThumbnailView]`, `[app/src/main/kotlin/ui/components/gallery/ClipItemRow.kt::ClipItemRow]`.

---

#### Components: Settings (`app/src/main/kotlin/ui/components/settings/`)

##### `[app/src/main/kotlin/ui/components/settings/SettingsPrimitives.kt::SectionHeader]`
- **Purpose:** Consistent section header text displaying uppercase teal title with custom letter spacing.
- **Consumers:** `[app/src/main/kotlin/ui/screens/SettingsScreen.kt::SettingsScreen]`, `[app/src/main/kotlin/ui/components/settings/StorageSettingsSection.kt::StorageSettingsSection]`, `[app/src/main/kotlin/ui/components/settings/DashcamSettingsSection.kt::DashcamSettingsSection]`, `[app/src/main/kotlin/ui/components/settings/SentrySubMenuScreen.kt::SentrySubMenuScreen]`, `[app/src/main/kotlin/ui/components/settings/SmartPlaybackSubMenuScreen.kt::SmartPlaybackSubMenuScreen]`.

##### `[app/src/main/kotlin/ui/components/settings/SettingsPrimitives.kt::SettingsCard]`
- **Purpose:** Automotive dark surface container card with dynamic responsive horizontal padding and slate borders.
- **Consumers:** `[app/src/main/kotlin/ui/screens/SettingsScreen.kt::SettingsScreen]`, `[app/src/main/kotlin/ui/components/settings/StorageSettingsSection.kt::StorageSettingsSection]`, `[app/src/main/kotlin/ui/components/settings/DashcamSettingsSection.kt::DashcamSettingsSection]`, `[app/src/main/kotlin/ui/components/settings/SentrySubMenuScreen.kt::SentrySubMenuScreen]`, `[app/src/main/kotlin/ui/components/settings/SmartPlaybackSubMenuScreen.kt::SmartPlaybackSubMenuScreen]`.

##### `[app/src/main/kotlin/ui/components/settings/SettingsPrimitives.kt::SettingsDropdownRow]`
- **Purpose:** Adaptive dropdown selector row supporting compact stacked layout on small phones and side-by-side row on expanded screens.
- **Consumers:** `[app/src/main/kotlin/ui/components/settings/DashcamSettingsSection.kt::DashcamSettingsSection]`, `[app/src/main/kotlin/ui/components/settings/SentrySubMenuScreen.kt::SentrySubMenuScreen]`, `[app/src/main/kotlin/ui/components/settings/SmartPlaybackSubMenuScreen.kt::SmartPlaybackSubMenuScreen]`.

##### `[app/src/main/kotlin/ui/components/settings/SettingsPrimitives.kt::SettingsToggleRow]`
- **Purpose:** High-contrast toggle switch row with full-width wrapping title and secondary explanation text.
- **Consumers:** `[app/src/main/kotlin/ui/components/settings/DashcamSettingsSection.kt::DashcamSettingsSection]`, `[app/src/main/kotlin/ui/components/settings/SentrySubMenuScreen.kt::SentrySubMenuScreen]`, `[app/src/main/kotlin/ui/components/settings/SmartPlaybackSubMenuScreen.kt::SmartPlaybackSubMenuScreen]`.

##### `[app/src/main/kotlin/ui/components/settings/StorageMigrationDialogs.kt::PendingStorageChange]`
- **Purpose:** Transient data class holding pending storage location change during user confirmation and migration prompt.
- **Consumers:** `[app/src/main/kotlin/ui/screens/SettingsScreen.kt::SettingsScreen]`.

##### `[app/src/main/kotlin/ui/components/settings/StorageMigrationDialogs.kt::StorageMigrationPromptDialog]`
- **Purpose:** Modal confirmation dialog presenting file migration options (consolidate all footage, move current folder, or keep files in place).
- **Consumers:** `[app/src/main/kotlin/ui/screens/SettingsScreen.kt::SettingsScreen]`.

##### `[app/src/main/kotlin/ui/components/settings/StorageMigrationDialogs.kt::InsufficientStorageMigrationDialog]`
- **Purpose:** Warning alert dialog triggered when target volume lacks capacity for migration plus dynamic OS safety buffer.
- **Consumers:** `[app/src/main/kotlin/ui/screens/SettingsScreen.kt::SettingsScreen]`.

##### `[app/src/main/kotlin/ui/components/settings/StorageMigrationDialogs.kt::MigrationProgressDialog]`
- **Purpose:** Full-screen modal progress dialog displaying active file transfer index, byte counters, progress bar, and completion/error actions.
- **Consumers:** `[app/src/main/kotlin/ui/screens/SettingsScreen.kt::SettingsScreen]`.

##### `[app/src/main/kotlin/ui/components/settings/CustomThresholdDialogs.kt::CustomTripGapDialog]`
- **Purpose:** Numeric threshold input dialog for setting maximum stopped/paused minutes between recorded driving loops before triggering a new Trip session.
- **Consumers:** `[app/src/main/kotlin/ui/screens/SettingsScreen.kt::SettingsScreen]`.

##### `[app/src/main/kotlin/ui/components/settings/CustomThresholdDialogs.kt::CustomSentryArmingDelayDialog]`
- **Purpose:** Countdown delay dialog allowing driver to configure exit delay seconds before Sentry sensors arm.
- **Consumers:** `[app/src/main/kotlin/ui/screens/SettingsScreen.kt::SettingsScreen]`.

##### `[app/src/main/kotlin/ui/components/settings/CustomThresholdDialogs.kt::CustomSentryIncidentDurationDialog]`
- **Purpose:** Incident capture duration dialog specifying post-trigger recording length (seconds) after shock, noise, or motion events.
- **Consumers:** `[app/src/main/kotlin/ui/screens/SettingsScreen.kt::SettingsScreen]`.

##### `[app/src/main/kotlin/ui/components/settings/CustomThresholdDialogs.kt::CustomSentryDemoteTempDialog]`
- **Purpose:** Thermal protection threshold dialog defining custom temperature cutoff for optical motion demotion to shock standby.
- **Consumers:** `[app/src/main/kotlin/ui/screens/SettingsScreen.kt::SettingsScreen]`.

##### `[app/src/main/kotlin/ui/components/settings/CustomThresholdDialogs.kt::CustomIncidentBufferDialog]`
- **Purpose:** Custom duration dialog enabling users to input arbitrary incident guarantee buffer windows (15 to 300 seconds / 5 minutes) via `NumericThresholdDialog`.
- **Consumers:** `[app/src/main/kotlin/ui/components/settings/DashcamSettingsSection.kt::DashcamSettingsSection]`.

##### `[app/src/main/kotlin/ui/components/settings/StorageLocationDialogs.kt::StorageLocationSubMenuDialog]`
- **Purpose:** Comprehensive storage location picker dialog offering Internal Scoped Storage, Removable SD Card, and Custom Directory with direct shortcuts to Viewfinder and SAF Picker.
- **Consumers:** `[app/src/main/kotlin/ui/screens/SettingsScreen.kt::SettingsScreen]`.

##### `[app/src/main/kotlin/ui/components/settings/StorageLocationDialogs.kt::StorageQuotaInfoDialog]`
- **Purpose:** Technical education dialog explaining dynamic 2% OS safety buffers, reclaimable loop buffer pools, and integer whole-GB quota flooring.
- **Consumers:** `[app/src/main/kotlin/ui/screens/SettingsScreen.kt::SettingsScreen]`.

##### `[app/src/main/kotlin/ui/components/settings/StorageLocationDialogs.kt::StorageLocationItemRow]`
- **Purpose:** Interactive radio row card displaying storage volume title, available disk space, mount path, and custom configuration action button.
- **Consumers:** `[app/src/main/kotlin/ui/components/settings/StorageLocationDialogs.kt::StorageLocationSubMenuDialog]`.

##### `[app/src/main/kotlin/ui/components/settings/SmartPlaybackSubMenuScreen.kt::SmartPlaybackSubMenuScreen]`
- **Purpose:** Full sub-menu screen for configuring Trip Continuity & Playback parameters (inactivity gap, loop adjacency window, auto-advance toggle, minimum trip duration, gap inclusion).
- **Consumers:** `[app/src/main/kotlin/ui/screens/SettingsScreen.kt::SettingsScreen]`.

##### `[app/src/main/kotlin/ui/components/settings/SentrySubMenuScreen.kt::SentrySubMenuScreen]`
- **Purpose:** Full sub-menu screen for configuring parked surveillance parameters (sentry toggle, arming triggers, exit delays, guard architectures, motion sensitivity/persistence, auto-demotion floors, acoustic thresholds, incident clip durations, deterrents, and voice prompts).
- **Consumers:** `[app/src/main/kotlin/ui/screens/SettingsScreen.kt::SettingsScreen]`.

##### `[app/src/main/kotlin/ui/components/settings/StorageSettingsSection.kt::StorageSettingsSection]`
- **Purpose:** Storage directory and dynamic quota configuration card managing permission banners, active directory path badges, interactive custom quota input via `NumericThresholdDialog`, and capacity sliders.
- **Consumers:** `[app/src/main/kotlin/ui/screens/SettingsScreen.kt::SettingsScreen]`.

##### `[app/src/main/kotlin/ui/components/settings/DashcamSettingsSection.kt::DashcamSettingsSection]`
- **Purpose:** Dashcam loop buffer and camera hardware configuration card managing segment duration, incident safety window, hardware sensor banners, aspect ratios, resolutions, and target FPS.
- **Consumers:** `[app/src/main/kotlin/ui/screens/SettingsScreen.kt::SettingsScreen]`.

##### `[app/src/main/kotlin/ui/components/settings/DashcamSettingsSection.kt::SensorsSettingsSection]`
- **Purpose:** Intelligent sensors and driving triggers configuration card managing G-force impact sensitivity thresholds and speedometer units.
- **Consumers:** `[app/src/main/kotlin/ui/screens/SettingsScreen.kt::SettingsScreen]`.

##### `[app/src/main/kotlin/ui/components/settings/DashcamSettingsSection.kt::RecordingMediaSettingsSection]`
- **Purpose:** Audio recording, telemetry watermark overlay, and debug orientation configuration cards.
- **Consumers:** `[app/src/main/kotlin/ui/screens/SettingsScreen.kt::SettingsScreen]`.

##### `[app/src/main/kotlin/ui/components/settings/DirectoryViewfinderDialog.kt::DirectoryViewfinderDialog]`
- **Purpose:** In-app folder browser and directory selector dialog for configuring storage destinations.
- **Consumers:** `[app/src/main/kotlin/ui/screens/SettingsScreen.kt::SettingsScreen]`.

---

#### Optimization Components (`app/src/main/kotlin/ui/components/optimization/`)

##### `[app/src/main/kotlin/ui/components/optimization/OptimizationPrimitives.kt::GuideStepRow]`
- **Purpose:** Numbered instruction row primitive with circular cyber-cyan badge for step-by-step OEM and Kiosk configuration dialogs.
- **Consumers:** `[app/src/main/kotlin/ui/components/optimization/OemGuideDialogs.kt::KioskGuideDialog]`, `[app/src/main/kotlin/ui/components/optimization/OemGuideDialogs.kt::OemGuideDialog]`.

##### `[app/src/main/kotlin/ui/components/optimization/OptimizationPrimitives.kt::DiagRow]`
- **Purpose:** Diagnostic telemetry row displaying metric label, status indicator dot, monospace value, and optional quick-fix action button.
- **Consumers:** `[app/src/main/kotlin/ui/components/optimization/HardwareDiagnosticsSection.kt::HardwareDiagnosticsSection]`.

##### `[app/src/main/kotlin/ui/components/optimization/OptimizationPrimitives.kt::OptimizationStepCard]`
- **Purpose:** Reusable step container card with numbered badges, cyber border styling, and primary/secondary action buttons.
- **Consumers:** `[app/src/main/kotlin/ui/components/optimization/SystemProtectionStepsSection.kt::BatteryWhitelistStepCard]`, `[app/src/main/kotlin/ui/components/optimization/SystemProtectionStepsSection.kt::KioskAppPinningStepCard]`, `[app/src/main/kotlin/ui/components/optimization/SystemProtectionStepsSection.kt::SilentCockpitDndCard]`, `[app/src/main/kotlin/ui/components/optimization/SystemProtectionStepsSection.kt::OemBackgroundProtectionCard]`.

##### `[app/src/main/kotlin/ui/components/optimization/OptimizationPrimitives.kt::OptimizationToggleRow]`
- **Purpose:** High-contrast toggle switch row styled with `NeonGreen` active track for device optimization and vehicle power automation controls, delegating to `DashboardToggleRow`.
- **Consumers:** `[app/src/main/kotlin/ui/components/optimization/VehiclePowerAutomationSection.kt::VehiclePowerAutomationSection]`.

##### `[app/src/main/kotlin/ui/components/optimization/OptimizationPrimitives.kt::OptimizationDropdownRow]`
- **Purpose:** Responsive dropdown selector row for optimization and power/thermal threshold options, delegating to `DashboardDropdownRow`.
- **Consumers:** `[app/src/main/kotlin/ui/components/optimization/VehiclePowerAutomationSection.kt::VehiclePowerAutomationSection]`.

##### `[app/src/main/kotlin/ui/components/optimization/HardwareDiagnosticsSection.kt::HardwareDiagnosticsSection]`
- **Purpose:** Live hardware and OS diagnostic grid displaying battery temperature, charging status, storage partition health, GPS telemetry fix, and background policy states.
- **Consumers:** `[app/src/main/kotlin/ui/screens/OptimizationScreen.kt::OptimizationScreen]`.

##### `[app/src/main/kotlin/ui/components/optimization/VehiclePowerAutomationSection.kt::VehiclePowerAutomationSection]`
- **Purpose:** Consolidated vehicle power & thermal automation card configuring ignition detection, shutdown countdowns, screen auto-dimming, battery cutoff protection, and thermal recovery loops.
- **Consumers:** `[app/src/main/kotlin/ui/screens/OptimizationScreen.kt::OptimizationScreen]`.

##### `[app/src/main/kotlin/ui/components/optimization/SystemProtectionStepsSection.kt::BatteryWhitelistStepCard]`
- **Purpose:** Step 1 protection card guiding the user to exempt DashGuard from Android Doze / battery optimization.
- **Consumers:** `[app/src/main/kotlin/ui/components/optimization/SystemProtectionSubMenuScreen.kt::SystemProtectionSubMenuScreen]`.

##### `[app/src/main/kotlin/ui/components/optimization/SystemProtectionStepsSection.kt::KioskAppPinningStepCard]`
- **Purpose:** Step 2 protection card configuring Android Lock Task / App Pinning (Kiosk Mode) and launching the Kiosk setup guide.
- **Consumers:** `[app/src/main/kotlin/ui/components/optimization/SystemProtectionSubMenuScreen.kt::SystemProtectionSubMenuScreen]`.

##### `[app/src/main/kotlin/ui/components/optimization/SystemProtectionStepsSection.kt::SilentCockpitDndCard]`
- **Purpose:** Step 3 protection card managing Do Not Disturb policy access, auto-silence while recording, and OS DND priority rule configuration.
- **Consumers:** `[app/src/main/kotlin/ui/components/optimization/SystemProtectionSubMenuScreen.kt::SystemProtectionSubMenuScreen]`.

##### `[app/src/main/kotlin/ui/components/optimization/SystemProtectionStepsSection.kt::OemBackgroundProtectionCard]`
- **Purpose:** Step 4 protection card providing manufacturer-aware background restriction guidance, App Info deep-linking, and user completion tracking.
- **Consumers:** `[app/src/main/kotlin/ui/components/optimization/SystemProtectionSubMenuScreen.kt::SystemProtectionSubMenuScreen]`.

##### `[app/src/main/kotlin/ui/components/optimization/SystemProtectionStepsSection.kt::WindshieldThermalGuideCard]`
- **Purpose:** Step 5 informational card detailing windshield solar load management, sunshade recommendations, and Li-ion thermal safety practices.
- **Consumers:** `[app/src/main/kotlin/ui/screens/OptimizationScreen.kt::OptimizationScreen]`.

##### `[app/src/main/kotlin/ui/components/optimization/OemGuideDialogs.kt::KioskGuideDialog]`
- **Purpose:** Modal guide explaining how to enable Android App Pinning in Security Settings, pin DashGuard to the screen, and unpin via Back + Overview.
- **Consumers:** `[app/src/main/kotlin/ui/screens/OptimizationScreen.kt::OptimizationScreen]`, `[app/src/main/kotlin/ui/components/optimization/SystemProtectionSubMenuScreen.kt::SystemProtectionSubMenuScreen]`.

##### `[app/src/main/kotlin/ui/components/optimization/OemGuideDialogs.kt::OemGuideDialog]`
- **Purpose:** Manufacturer-specific guidance modal dialog detailing exact autostart and background protection settings for Xiaomi, Samsung, Huawei, Oppo/Realme/OnePlus, and generic Android devices.
- **Consumers:** `[app/src/main/kotlin/ui/components/optimization/SystemProtectionSubMenuScreen.kt::SystemProtectionSubMenuScreen]`.

##### `[app/src/main/kotlin/ui/components/optimization/OptimizationThresholdDialogs.kt::CustomLowBatteryDialog]`
- **Purpose:** Numeric threshold input dialog for configuring custom low-battery standby cutoff percentage (1% to 50%) via `NumericThresholdDialog`.
- **Consumers:** `[app/src/main/kotlin/ui/screens/OptimizationScreen.kt::OptimizationScreen]`.

##### `[app/src/main/kotlin/ui/components/optimization/OptimizationThresholdDialogs.kt::CustomThermalTempDialog]`
- **Purpose:** Parameterized numeric threshold dialog for configuring custom warm auto-dim warning temperatures and cool-off recovery targets (`minTempC` to `maxTempC`) via `NumericThresholdDialog`.
- **Consumers:** `[app/src/main/kotlin/ui/screens/OptimizationScreen.kt::OptimizationScreen]`.

##### `[app/src/main/kotlin/ui/components/optimization/OptimizationThresholdDialogs.kt::CustomSleepEmergencyDurationDialog]`
- **Purpose:** Numeric threshold dialog for configuring custom emergency clip recording length (10s to 180s) when triggered during thermal cooling or low-battery standby via `NumericThresholdDialog`.
- **Consumers:** `[app/src/main/kotlin/ui/screens/OptimizationScreen.kt::OptimizationScreen]`.

##### `[app/src/main/kotlin/ui/components/optimization/SystemProtectionSubMenuScreen.kt::SystemProtectionSubMenuScreen]`
- **Purpose:** Dedicated sub-menu screen hosting the Android & OEM protection workflow (Doze bypass, Kiosk app pinning, Silent Cockpit DND, and OEM background guides).
- **Consumers:** `[app/src/main/kotlin/ui/screens/OptimizationScreen.kt::OptimizationScreen]`.

---

#### Components: Playback (`app/src/main/kotlin/ui/components/playback/`)

##### `[app/src/main/kotlin/ui/components/playback/VideoPlaybackSurface.kt::VideoPlaybackSurface]`
- **Purpose:** Video rendering surface hosting native Android `VideoView` via Compose `AndroidView`, stateful playback lifecycle management, transparent touch overlay handling, and interactive fallback telemetry HUD simulation.
- **Consumers:** `[app/src/main/kotlin/ui/screens/VideoPlayerScreen.kt::VideoPlayerScreen]`.

##### `[app/src/main/kotlin/ui/components/playback/TelemetryWatermarkOverlay.kt::TelemetryWatermarkOverlay]`
- **Purpose:** Real-time telemetry watermark overlay banner with responsive dynamic text scaling, displaying synchronized speed, timestamp, GPS coordinates, and lock status over video playback.
- **Consumers:** `[app/src/main/kotlin/ui/screens/VideoPlayerScreen.kt::VideoPlayerScreen]`.

##### `[app/src/main/kotlin/ui/components/playback/TelemetryWatermarkOverlay.kt::TelemetryWatermarkHelper]`
- **Purpose:** Authoritative telemetry watermark text generator resolving frame positions against companion `.srt` subtitles or falling back to interpolated clip metadata.
- **Consumers:** `[app/src/main/kotlin/ui/screens/VideoPlayerScreen.kt::VideoPlayerScreen]`.

##### `[app/src/main/kotlin/ui/components/playback/PlaybackControlsOverlay.kt::PlaybackControlsOverlay]`
- **Purpose:** Animated auto-hiding controls overlay for VideoPlayerScreen hosting status badges, center transport controls (skip previous, play/pause, replay, skip next, auto-advance toggle), driving break resume card, trip loop strip, and timeline scrubber.
- **Consumers:** `[app/src/main/kotlin/ui/screens/VideoPlayerScreen.kt::VideoPlayerScreen]`.

##### `[app/src/main/kotlin/ui/components/playback/TripMetricsCard.kt::TripMetricsCard]`
- **Purpose:** Bottom scrollable telemetry record and actions card for non-fullscreen mode, housing trip continuous loop timeline, incident telemetry table, external map navigation shortcuts, and lock/share controls.
- **Consumers:** `[app/src/main/kotlin/ui/screens/VideoPlayerScreen.kt::VideoPlayerScreen]`.

##### `[app/src/main/kotlin/ui/components/playback/TripMetricsCard.kt::TelemetryRow]`
- **Purpose:** Clean key-value telemetry presentation row pairing labeled sensor indicators with monospace values.
- **Consumers:** `[app/src/main/kotlin/ui/components/playback/TripMetricsCard.kt::TripMetricsCard]`.

##### `[app/src/main/kotlin/ui/components/playback/VideoPlayerDialogs.kt::VideoPlayerDialogs]`
- **Purpose:** Presentation dialog coordinator encapsulating video sharing mode selection (`ShareChoiceDialog`) and protected clip unlock confirmation alerts.
- **Consumers:** `[app/src/main/kotlin/ui/screens/VideoPlayerScreen.kt::VideoPlayerScreen]`.

##### `[app/src/main/kotlin/ui/components/playback/PlaybackZoomState.kt::PlaybackZoomState]`
- **Purpose:** State coordinator managing photo-gallery style pinch-to-zoom, bounded panning, double-tap zoom/reset, and smooth zoom clamping for video playback surfaces in full screen mode.
- **Consumers:** `[app/src/main/kotlin/ui/components/playback/VideoPlaybackSurface.kt::VideoPlaybackSurface]`, `[app/src/main/kotlin/ui/screens/VideoPlayerScreen.kt::VideoPlayerScreen]`.

##### `[app/src/main/kotlin/ui/components/playback/PlaybackZoomState.kt::rememberPlaybackZoomState]`
- **Purpose:** Composable factory creating and remembering a `PlaybackZoomState` instance.
- **Consumers:** `[app/src/main/kotlin/ui/screens/VideoPlayerScreen.kt::VideoPlayerScreen]`.

##### `[app/src/main/kotlin/ui/components/playback/PlaybackZoomState.kt::PlaybackZoomBadge]`
- **Purpose:** Floating HUD badge displaying active magnification level (e.g. 2.5x) with a one-touch reset control.
- **Consumers:** `[app/src/main/kotlin/ui/screens/VideoPlayerScreen.kt::VideoPlayerScreen]`.

---

#### Screens (`app/src/main/kotlin/ui/screens/`)

##### `[app/src/main/kotlin/ui/screens/CameraHudScreen.kt::CameraHudScreen]`
- **Purpose:** Primary driving HUD screen. Coordinates camera viewfinder, telemetry overlay, auto-dimming timers, shutter button, emergency lock, and cockpit control docks (`HudControlDock`).

##### `[app/src/main/kotlin/ui/screens/GalleryScreen.kt::GalleryScreen]`
- **Purpose:** Video gallery screen supporting Trip-grouped loops vs all-clips view, category filters (Locked, Sentry, Fused), trip renaming, locking, and deletion.

##### `[app/src/main/kotlin/ui/screens/OptimizationScreen.kt::OptimizationScreen]`
- **Purpose:** System optimization screen managing battery optimization exemption, app pinning (kiosk mode), DND permissions, thermal thresholds, and storage capacity metrics, with hoisted `LazyListState` preserving scroll location across `SystemProtectionSubMenuScreen` navigation.

##### `[app/src/main/kotlin/ui/screens/SettingsScreen.kt::SettingsScreen]`
- **Purpose:** System configuration screen managing video quality, aspect ratios, loop segment duration, storage quota, sensitivities, Sentry triggers, and storage migrations, with hoisted `LazyListState` preserving scroll location across `SmartPlaybackSubMenuScreen` and `SentrySubMenuScreen` navigation.

##### `[app/src/main/kotlin/ui/screens/VideoPlayerScreen.kt::VideoPlayerScreen]`
- **Purpose:** Video player screen supporting synchronized telemetry subtitle watermarks, continuous auto-advance, manual trip-scoped skip, and lock toggling.

---

#### Theme (`app/src/main/kotlin/ui/theme/`)

##### `[app/src/main/kotlin/ui/theme/Color.kt::HudBlack]`
- **Purpose:** Primary tactical OLED dark background surface color (`0xFF06090E`).

##### `[app/src/main/kotlin/ui/theme/Color.kt::HudSurface]`
- **Purpose:** Elevated HUD and dashboard card surface color (`0xFF0D131F`).

##### `[app/src/main/kotlin/ui/theme/Color.kt::HudSurfaceVariant]`
- **Purpose:** Secondary elevated HUD surface variant color (`0xFF161F32`).

##### `[app/src/main/kotlin/ui/theme/Color.kt::HudCardBorder]`
- **Purpose:** Subtle slate card outline and divider border color (`0xFF1E293B`).

##### `[app/src/main/kotlin/ui/theme/Color.kt::AutomotiveDark]`
- **Purpose:** Standard automotive dark scaffold surface color (`0xFF121212`).

##### `[app/src/main/kotlin/ui/theme/Color.kt::AutomotivePitchBlack]`
- **Purpose:** True-black (`0xFF000000`) OLED power-saver and safety lockout background color.

##### `[app/src/main/kotlin/ui/theme/Color.kt::CockpitPanel]`
- **Purpose:** Cockpit navigation dock and control deck panel fill color (`0xFF0F141C`).

##### `[app/src/main/kotlin/ui/theme/Color.kt::CockpitBorder]`
- **Purpose:** Cockpit control deck and panel border stroke color (`0xFF222F3E`).

##### `[app/src/main/kotlin/ui/theme/Color.kt::SafetyAmber]`
- **Purpose:** High-visibility automotive warning and standby amber accent (`0xFFFF9500`).

##### `[app/src/main/kotlin/ui/theme/Color.kt::SafetyYellow]`
- **Purpose:** High-contrast caution and lock indicator yellow accent (`0xFFFFCC00`).

##### `[app/src/main/kotlin/ui/theme/Color.kt::SafetyRed]`
- **Purpose:** Active recording shutter, critical alert, and emergency lock red accent (`0xFFFF3B30`).

##### `[app/src/main/kotlin/ui/theme/Color.kt::CyberCyan]`
- **Purpose:** Primary interactive telemetry, active tab, and cyber HUD cyan accent (`0xFF00E5FF`).

##### `[app/src/main/kotlin/ui/theme/Color.kt::CyberCyanDim]`
- **Purpose:** Dimmed secondary cyan accent for subdued borders and inactive states (`0xFF0091A8`).

##### `[app/src/main/kotlin/ui/theme/Color.kt::NeonGreen]`
- **Purpose:** Nominal status, active toggle track, GPS fix, and healthy diagnostic green (`0xFF10B981`).

##### `[app/src/main/kotlin/ui/theme/Color.kt::NeonAmber]`
- **Purpose:** Elevated thermal, protected badge, and Sentry event amber indicator (`0xFFF59E0B`).

##### `[app/src/main/kotlin/ui/theme/Color.kt::EmergencyRed]`
- **Purpose:** Destructive action, impact alert, and critical threshold violation red (`0xFFEF4444`).

##### `[app/src/main/kotlin/ui/theme/Color.kt::EmergencyRedDark]`
- **Purpose:** Deep crimson error container background color (`0xFF991B1B`).

##### `[app/src/main/kotlin/ui/theme/Color.kt::ElectricViolet]`
- **Purpose:** Telemetry-fused video badge and export indicator violet accent (`0xFF8B5CF6`).

##### `[app/src/main/kotlin/ui/theme/Color.kt::TextPrimary]`
- **Purpose:** High-contrast primary off-white typography color (`0xFFF8FAFC`).

##### `[app/src/main/kotlin/ui/theme/Color.kt::TextSecondary]`
- **Purpose:** Muted slate-gray secondary subtitle and label typography color (`0xFF94A3B8`).

##### `[app/src/main/kotlin/ui/theme/Color.kt::TextMuted]`
- **Purpose:** Low-emphasis caption and disabled control text color (`0xFF64748B`).

##### `[app/src/main/kotlin/ui/theme/Color.kt::SpeedometerGlow]`
- **Purpose:** Translucent cyan ambient glow color for speedometer HUD rings (`0x3300E5FF`).

##### `[app/src/main/kotlin/ui/theme/Color.kt::SentryAlertGlow]`
- **Purpose:** Translucent red ambient glow color for Sentry alarm pulses (`0x44EF4444`).

##### `[app/src/main/kotlin/ui/theme/Theme.kt::DashGuardProTheme]`
- **Purpose:** Material 3 dark color scheme enforcing dark status bars and automotive dark containers.

##### `[app/src/main/kotlin/ui/theme/Type.kt::Typography]`
- **Purpose:** Typography scale for dashcam text and monospace telemetry indicators.

---

### Category G: Foundation & Cross-Cutting Utilities (Utility Tier)

#### `app/src/main/kotlin/util/`

##### `[app/src/main/kotlin/util/FileExtensions.kt::FileExtensions]`
- **Purpose:** Marker namespace object for canonical sidecar file resolution and coordinate evaluation extensions in `FileExtensions.kt`.

##### `[app/src/main/kotlin/util/FileExtensions.kt::VideoClip.videoFile]`
- **Purpose:** Resolves a `VideoClip`'s `filePath` string into a physical `java.io.File` reference.
- **Consumers:** `[app/src/main/kotlin/storage/LoopStorageManager.kt::LoopStorageManager]`, `[app/src/main/kotlin/storage/StorageMigrationManager.kt::StorageMigrationManager]`, `[app/src/main/kotlin/ui/components/gallery/ShareChoiceDialog.kt::ShareChoiceDialog]`, `[app/src/main/kotlin/ui/screens/GalleryScreen.kt::GalleryScreen]`.

##### `[app/src/main/kotlin/util/FileExtensions.kt::VideoClip.srtSidecarFile]`
- **Purpose:** Resolves the companion `.srt` SubRip telemetry sidecar `File` for a `VideoClip` by replacing its extension with `.srt`.
- **Consumers:** `[app/src/main/kotlin/storage/LoopStorageManager.kt::LoopStorageManager]`, `[app/src/main/kotlin/storage/StorageMigrationManager.kt::StorageMigrationManager]`, `[app/src/main/kotlin/ui/components/gallery/ShareChoiceDialog.kt::ShareChoiceDialog]`, `[app/src/main/kotlin/ui/screens/VideoPlayerScreen.kt::VideoPlayerScreen]`.

##### `[app/src/main/kotlin/util/FileExtensions.kt::VideoClip.fusedVideoFile]`
- **Purpose:** Resolves the companion `-telemetry-fused.mp4` export `File` for a `VideoClip` via `TelemetryVideoFusionEngine.getFusedVideoFile(videoFile)`.
- **Consumers:** `[app/src/main/kotlin/storage/LoopStorageManager.kt::LoopStorageManager]`.

##### `[app/src/main/kotlin/util/FileExtensions.kt::VideoClip.hasValidCoordinates]`
- **Purpose:** Evaluates whether a `VideoClip` contains non-zero primary (`latitude`/`longitude`) or segment start (`startLatitude`/`startLongitude`) GPS coordinates.

##### `[app/src/main/kotlin/util/FileExtensions.kt::VideoClip.effectiveCoordinates]`
- **Purpose:** Resolves the best available `(latitude, longitude)` pair for a `VideoClip` using priority fallback: primary coordinates → start coordinates → incident coordinates → `null`.
- **Consumers:** `[app/src/main/kotlin/ui/components/gallery/ClipItemRow.kt::ClipItemRow]`.

##### `[app/src/main/kotlin/util/FileExtensions.kt::VideoClip.effectiveStartCoordinates]`
- **Purpose:** Resolves the starting `(latitude, longitude)` pair for a `VideoClip`, preferring `startLatitude`/`startLongitude` and falling back to `effectiveCoordinates`.
- **Consumers:** `[app/src/main/kotlin/model/TripSession.kt::TripSession]`, `[app/src/main/kotlin/ui/components/playback/TripMetricsCard.kt::TripMetricsCard]`.

##### `[app/src/main/kotlin/util/FileExtensions.kt::VideoClip.effectiveEndCoordinates]`
- **Purpose:** Resolves the ending `(latitude, longitude)` pair for a `VideoClip`, preferring `endLatitude`/`endLongitude` and falling back to `effectiveCoordinates`.
- **Consumers:** `[app/src/main/kotlin/model/TripSession.kt::TripSession]`, `[app/src/main/kotlin/ui/components/playback/TripMetricsCard.kt::TripMetricsCard]`.

##### `[app/src/main/kotlin/util/FileExtensions.kt::File.srtSidecarFile]`
- **Purpose:** Resolves the canonical `.srt` sidecar `File` for any raw or `-telemetry-fused` video `File` by stripping `-telemetry-fused` from the base name.
- **Consumers:** `[app/src/main/kotlin/camera/RecordingSessionCoordinator.kt::RecordingSessionCoordinator]`, `[app/src/main/kotlin/storage/ClipRepository.kt::ClipRepository]`, `[app/src/main/kotlin/media/TelemetryVideoFusionEngine.kt::TelemetryVideoFusionEngine]`, `[app/src/main/kotlin/playback/VideoPlaybackCoordinator.kt::VideoPlaybackCoordinator]`.

##### `[app/src/main/kotlin/util/FileExtensions.kt::File.fusedVideoFile]`
- **Purpose:** Resolves the derived `-telemetry-fused.mp4` companion `File` for a physical video `File` via `TelemetryVideoFusionEngine.getFusedVideoFile(this)`.
- **Consumers:** `[app/src/main/kotlin/storage/LoopStorageManager.kt::LoopStorageManager]`, `[app/src/main/kotlin/storage/StorageMigrationManager.kt::StorageMigrationManager]`.

##### `[app/src/main/kotlin/util/FileExtensions.kt::File.isFusedVideo]`
- **Purpose:** Returns `true` if the `File` name contains the `-telemetry-fused` derivative suffix.

##### `[app/src/main/kotlin/util/FileExtensions.kt::File.rawSourceVideoFile]`
- **Purpose:** Maps a `-telemetry-fused` video `File` back to its original raw camera `.mp4` `File` (or returns `this` if already raw).
- **Consumers:** `[app/src/main/kotlin/storage/ClipRepository.kt::ClipRepository]`, `[app/src/main/kotlin/storage/LoopStorageManager.kt::LoopStorageManager]`.

##### `[app/src/main/kotlin/util/FileExtensions.kt::DASHGUARD_RECORDING_PATTERN]`
- **Purpose:** Case-insensitive canonical `Regex` (`^VID_(?:DASH|SENTRY|LOOP|MANUAL|INCIDENT)_\d{8}_\d{6}(?:_\d+)?(?:-telemetry-fused)?\.(mp4|srt)$`) backing `File.isDashGuardRecordingFile`.

##### `[app/src/main/kotlin/util/FileExtensions.kt::File.isDashGuardRecordingFile]`
- **Purpose:** Canonical regex validator verifying that media and sidecar files conform strictly to DashGuard naming conventions (`VID_<TYPE>_<YYYYMMDD_HHMMSS>(_<counter>)?(-telemetry-fused)?.(mp4|srt)` where `<TYPE>` is strictly `DASH|SENTRY|LOOP|MANUAL|INCIDENT`). Shields non-app/foreign media files in shared or custom directories from false database ingestion, misleading quota inflation, and FIFO loop retention deletion.
- **Consumers:** `[app/src/main/kotlin/storage/ClipRepository.kt::ClipRepository]`, `[app/src/main/kotlin/storage/LoopStorageManager.kt::LoopStorageManager]`.

##### `[app/src/main/kotlin/util/FormatUtils.kt::FormatUtils]`
- **Purpose:** Authoritative formatting utility for storage bytes, durations, distances, speed, and GPS coordinates. Eliminates ad-hoc division and inconsistent string formatting across all UI and domain tiers.
- **Consumers:** `[app/src/main/kotlin/ui/screens/GalleryScreen.kt::GalleryScreen]`, `[app/src/main/kotlin/ui/components/hud/TelemetryHud.kt::TelemetryHud]`, `[app/src/main/kotlin/ui/components/SpeedSafetyLockoutOverlay.kt::SpeedSafetyLockoutOverlay]`, `[app/src/main/kotlin/ui/components/gallery/StorageBar.kt::StorageBar]`, `[app/src/main/kotlin/ui/components/gallery/ClipItemRow.kt::ClipItemRow]`, `[app/src/main/kotlin/ui/components/gallery/TripSessionCard.kt::TripSessionCard]`, `[app/src/main/kotlin/ui/components/playback/TripMetricsCard.kt::TripMetricsCard]`, `[app/src/main/kotlin/ui/components/playback/PlaybackControlsOverlay.kt::PlaybackControlsOverlay]`, `[app/src/main/kotlin/ui/components/playback/VideoPlaybackSurface.kt::VideoPlaybackSurface]`, `[app/src/main/kotlin/ui/components/playback/TelemetryWatermarkOverlay.kt::TelemetryWatermarkHelper]`, `[app/src/main/kotlin/ui/components/settings/StorageMigrationDialogs.kt::InsufficientStorageMigrationDialog]`, `[app/src/main/kotlin/storage/StorageDirectoryManager.kt::StorageOptionInfo]`, `[app/src/main/kotlin/storage/StorageMigrationManager.kt::StorageMigrationManager]`.

##### `[app/src/main/kotlin/util/IntentUtils.kt::IntentUtils]`
- **Purpose:** Centralized intent factory and launcher utility for system settings (battery whitelist, DND policy, app details, security/pinning), external map navigation (`geo:` and directions), and MIME-compliant video/subtitle sharing.
- **Consumers:** `[app/src/main/kotlin/ui/screens/GalleryScreen.kt::GalleryScreen]`, `[app/src/main/kotlin/ui/screens/VideoPlayerScreen.kt::VideoPlayerScreen]`, `[app/src/main/kotlin/ui/screens/OptimizationScreen.kt::OptimizationScreen]`, `[app/src/main/kotlin/ui/components/optimization/SystemProtectionSubMenuScreen.kt::SystemProtectionSubMenuScreen]`, `[app/src/main/kotlin/ui/components/gallery/ClipItemRow.kt::ClipItemRow]`, `[app/src/main/kotlin/ui/components/gallery/TripSessionCard.kt::TripSessionCard]`, `[app/src/main/kotlin/ui/components/playback/TripMetricsCard.kt::TripMetricsCard]`.

---

## 4. Verification & Consistency Guarantees

This codebase map is validated by automated CI/tooling ([validate_audit.py](validate_audit.py)) to ensure:
1. Every tag referenced in [ARCHITECTURE_FLOW.md](ARCHITECTURE_FLOW.md) exists verbatim in this map.
2. Every file path declared in tags corresponds to an existing physical file on disk.

