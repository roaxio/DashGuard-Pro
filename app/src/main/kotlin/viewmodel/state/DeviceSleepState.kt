package io.github.roaxio.dashguardpro.viewmodel.state

enum class DeviceSleepMode {
    NONE,
    THERMAL_COOLING,
    LOW_BATTERY_STANDBY
}

enum class PreSleepOperatingState {
    STANDBY,
    DRIVING_LOOP,
    SENTRY_TIER1_SHOCK,
    SENTRY_TIER2_OPTICAL
}

enum class SleepTriggerCause {
    THERMAL_OVERHEAT,
    LOW_BATTERY
}
