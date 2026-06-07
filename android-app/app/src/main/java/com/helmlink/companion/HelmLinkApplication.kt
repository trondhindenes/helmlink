package com.helmlink.companion

import android.app.Application
import android.util.Log
import com.helmlink.companion.model.AutopilotCommand
import com.helmlink.companion.model.AutopilotState
import com.helmlink.companion.model.ConnectionState
import com.helmlink.companion.model.OrcaModes
import com.helmlink.companion.model.OrcaSettings
import com.helmlink.companion.service.GarminConnectionService
import com.helmlink.companion.service.OrcaApiClient
import com.helmlink.companion.service.OrcaDiscoveryService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class HelmLinkApplication : Application() {

    companion object {
        private const val TAG = "AutopilotApp"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    val garminService by lazy { GarminConnectionService(this) }
    val orcaClient by lazy { OrcaApiClient() }
    val orcaDiscovery by lazy { OrcaDiscoveryService(this) }
    val settings by lazy { OrcaSettings(this) }

    private val _commandLog = MutableStateFlow<List<AutopilotCommand>>(emptyList())
    val commandLog: StateFlow<List<AutopilotCommand>> = _commandLog

    private val _testState = MutableStateFlow(AutopilotState())
    private val _isTestMode = MutableStateFlow(false)
    val isTestMode: StateFlow<Boolean> = _isTestMode

    private val _activeAutopilotState = MutableStateFlow(AutopilotState())
    val autopilotState: StateFlow<AutopilotState> = _activeAutopilotState

    val watchConnectionState: StateFlow<ConnectionState>
        get() = garminService.connectionState

    private val _orcaConnectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val orcaConnectionState: StateFlow<ConnectionState> = _orcaConnectionState

    var isBridgeInitialized = false
        private set

    @Volatile
    private var commandedHeading: Int = 270
    @Volatile
    private var adjustsInFlight: Int = 0

    private var stateCollectorJob: Job? = null
    private var orcaStateJob: Job? = null

    fun initializeBridge() {
        if (isBridgeInitialized) return
        isBridgeInitialized = true

        garminService.onCommandReceived = { command ->
            processCommand(command)
        }
        garminService.onPingReceived = {
            sendCurrentStateToWatch()
        }
        garminService.initialize()

        _isTestMode.value = settings.testMode
        applyMode()
    }

    fun shutdownBridge() {
        if (!isBridgeInitialized) return
        isBridgeInitialized = false

        stateCollectorJob?.cancel()
        orcaStateJob?.cancel()
        garminService.onCommandReceived = null
        garminService.onPingReceived = null
        garminService.shutdown()
        orcaDiscovery.stopDiscovery()
        orcaClient.disconnect()
    }

    fun connectOrca() {
        _isTestMode.value = settings.testMode
        applyMode()
    }

    private fun applyMode() {
        stateCollectorJob?.cancel()
        orcaStateJob?.cancel()

        if (_isTestMode.value) {
            orcaClient.disconnect()
            orcaDiscovery.stopDiscovery()
            _orcaConnectionState.value = ConnectionState.DISCONNECTED
            _activeAutopilotState.value = _testState.value

            stateCollectorJob = scope.launch {
                _testState.collect { state ->
                    _activeAutopilotState.value = state
                    if (adjustsInFlight == 0) commandedHeading = state.heading
                    garminService.sendStatusToWatch(
                        state.heading,
                        if (state.engaged) state.mode else OrcaModes.STANDBY,
                        state.engaged
                    )
                }
            }
        } else {
            if (settings.autopilotId >= 0) {
                orcaClient.setAutopilotId(settings.autopilotId)
            }

            orcaStateJob = scope.launch {
                orcaClient.connectionState.collect { _orcaConnectionState.value = it }
            }

            stateCollectorJob = scope.launch {
                orcaClient.autopilotState.collect { state ->
                    _activeAutopilotState.value = state
                    if (adjustsInFlight == 0) commandedHeading = state.heading
                    garminService.sendStatusToWatch(
                        state.heading,
                        if (state.engaged) state.mode else OrcaModes.STANDBY,
                        state.engaged
                    )
                }
            }

            if (settings.autoDiscover) {
                Log.d(TAG, "Starting autodiscovery...")
                orcaDiscovery.startDiscovery()
                scope.launch {
                    orcaDiscovery.discoveredHost.collect { host ->
                        if (host != null) {
                            Log.d(TAG, "Discovered Orca at $host")
                            orcaClient.configure(host, settings.httpPort, settings.wsPort)
                            orcaClient.connect()
                        }
                    }
                }
            } else {
                Log.d(TAG, "Using manual host: ${settings.host}")
                orcaDiscovery.stopDiscovery()
                orcaClient.configure(settings.host, settings.httpPort, settings.wsPort)
                orcaClient.connect()
            }
        }
    }

    fun simulateCommand(type: String, detail: String = "") {
        if (type == "ADJUST") {
            val deg = detail.toIntOrNull() ?: 0
            val desired = (_activeAutopilotState.value.heading + deg + 360) % 360
            processCommand(AutopilotCommand(type = type, detail = desired.toString()))
        } else {
            processCommand(AutopilotCommand(type = type, detail = detail))
        }
    }

    private fun processCommand(command: AutopilotCommand) {
        _commandLog.value = listOf(command) + _commandLog.value.take(99)

        if (_isTestMode.value) {
            processTestCommand(command)
            return
        }

        when (command.type) {
            "ENGAGE" -> {
                val mode = command.detail.ifEmpty { OrcaModes.AUTO }
                val heading = _activeAutopilotState.value.heading
                orcaClient.setMode(mode) { success ->
                    garminService.ackSeq(command.seq)
                    garminService.sendStatusToWatch(heading, mode, engaged = true, confirmed = true)
                }
            }
            "DISENGAGE" -> {
                val heading = _activeAutopilotState.value.heading
                orcaClient.disengage { success ->
                    garminService.ackSeq(command.seq)
                    garminService.sendStatusToWatch(heading, OrcaModes.STANDBY, engaged = false, confirmed = true)
                }
            }
            "ADJUST" -> {
                val desiredHeading = command.detail.toIntOrNull() ?: 0
                val delta = headingDelta(commandedHeading, desiredHeading)
                commandedHeading = desiredHeading
                adjustsInFlight++
                orcaClient.adjustCourse(delta) { success ->
                    adjustsInFlight--
                    garminService.ackSeq(command.seq)
                    sendCurrentStateToWatch()
                }
            }
            "MODE" -> {
                val currentState = _activeAutopilotState.value
                if (currentState.engaged) {
                    orcaClient.setMode(command.detail) { success ->
                        garminService.ackSeq(command.seq)
                        garminService.sendStatusToWatch(currentState.heading, command.detail, engaged = true, confirmed = true)
                    }
                }
            }
        }
    }

    private fun headingDelta(from: Int, to: Int): Int {
        return ((to - from) + 540) % 360 - 180
    }

    private fun sendCurrentStateToWatch(confirmed: Boolean = false) {
        val state = _activeAutopilotState.value
        garminService.sendStatusToWatch(
            state.heading,
            if (state.engaged) state.mode else OrcaModes.STANDBY,
            state.engaged,
            confirmed
        )
    }

    private fun processTestCommand(command: AutopilotCommand) {
        garminService.ackSeq(command.seq)
        val current = _testState.value
        val newState = when (command.type) {
            "ENGAGE" -> current.copy(engaged = true, mode = command.detail.ifEmpty { "AUTO" })
            "DISENGAGE" -> current.copy(engaged = false)
            "ADJUST" -> {
                val desiredHeading = command.detail.toIntOrNull() ?: 0
                commandedHeading = desiredHeading
                current.copy(heading = desiredHeading)
            }
            "MODE" -> current.copy(mode = command.detail)
            else -> current
        }
        _testState.value = newState
        _activeAutopilotState.value = newState
        garminService.sendStatusToWatch(
            newState.heading,
            if (newState.engaged) newState.mode else OrcaModes.STANDBY,
            newState.engaged,
            confirmed = true
        )
    }
}
