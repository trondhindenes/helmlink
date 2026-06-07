package com.helmlink.companion.service

import com.helmlink.companion.model.AutopilotCommand
import com.helmlink.companion.model.AutopilotState
import com.helmlink.companion.model.ConnectionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class AutopilotWebSocketClient {

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    private val _autopilotState = MutableStateFlow(AutopilotState())
    val autopilotState: StateFlow<AutopilotState> = _autopilotState

    fun processCommand(command: AutopilotCommand): AutopilotState {
        val current = _autopilotState.value
        val newState = when (command.type) {
            "ENGAGE" -> current.copy(engaged = true)
            "DISENGAGE" -> current.copy(engaged = false)
            "ADJUST" -> {
                val deg = command.detail.toIntOrNull() ?: 0
                current.copy(heading = (current.heading + deg + 360) % 360)
            }
            "MODE" -> current.copy(mode = command.detail)
            else -> current
        }
        _autopilotState.value = newState
        return newState
    }
}
