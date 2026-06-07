package com.helmlink.companion.model

import android.content.Context

class OrcaSettings(context: Context) {
    private val prefs = context.getSharedPreferences("orca_settings", Context.MODE_PRIVATE)

    var autoDiscover: Boolean
        get() = prefs.getBoolean("autoDiscover", true)
        set(value) = prefs.edit().putBoolean("autoDiscover", value).apply()

    var host: String
        get() = prefs.getString("host", "10.11.12.1") ?: "10.11.12.1"
        set(value) = prefs.edit().putString("host", value).apply()

    var autopilotId: Int
        get() = prefs.getInt("autopilotId", -1)
        set(value) = prefs.edit().putInt("autopilotId", value).apply()

    var testMode: Boolean
        get() = prefs.getBoolean("testMode", false)
        set(value) = prefs.edit().putBoolean("testMode", value).apply()

    val httpPort: Int get() = 8088
    val wsPort: Int get() = 8089
}
