package com.helmlink.companion.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.helmlink.companion.HelmLinkApplication
import com.helmlink.companion.service.DebugLog

class HelmLinkViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as HelmLinkApplication

    val autopilotState = app.autopilotState
    val watchConnectionState = app.watchConnectionState
    val orcaConnectionState = app.orcaConnectionState
    val commandLog = app.commandLog
    val settings = app.settings
    val discoverySearching = app.orcaDiscovery.isSearching
    val discoveredHost = app.orcaDiscovery.discoveredHost
    val detectedAutopilotIds = app.orcaClient.detectedAutopilotIds
    val isTestMode = app.isTestMode
    val debugLog = DebugLog.entries

    fun simulateWatchCommand(type: String, detail: String = "") {
        app.simulateCommand(type, detail)
    }

    fun reconnectOrca() {
        app.connectOrca()
    }

    fun clearDebugLog() {
        DebugLog.clear()
    }
}
