package com.helmlink.companion.model

import java.time.Instant

data class AutopilotCommand(
    val type: String,
    val detail: String,
    val seq: Int = 0,
    val timestamp: Instant = Instant.now()
)

data class AutopilotState(
    val engaged: Boolean = false,
    val heading: Int = 270,
    val mode: String = "AUTO",
    val windAngle: Int = 0
)

enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED
}

object OrcaModes {
    const val STANDBY = "STANDBY"
    const val AUTO = "AUTO"
    const val NO_DRIFT = "NO_DRIFT"
    const val WIND = "WIND"
    // Route/waypoint following. The autopilot can be put in this mode from the
    // chartplotter; HelmLink does not let the watch engage it, but it must be
    // surfaced rather than shown as STANDBY.
    const val NAVIGATION = "NAVIGATION"

    fun fromValue(value: Int): String = when (value) {
        0 -> STANDBY
        1 -> AUTO
        2 -> NO_DRIFT
        4 -> NAVIGATION
        7 -> WIND
        else -> STANDBY
    }

    fun toValue(mode: String): Int = when (mode) {
        STANDBY -> 0
        AUTO -> 1
        NO_DRIFT -> 2
        NAVIGATION -> 4
        WIND -> 7
        else -> 0
    }
}
