package com.helmlink.companion.service

import android.util.Log
import com.helmlink.companion.model.AutopilotState
import com.helmlink.companion.model.ConnectionState
import com.helmlink.companion.model.OrcaModes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class OrcaApiClient {

    companion object {
        private const val TAG = "OrcaApi"
        private val JSON_TYPE = "application/json".toMediaType()
    }

    private var httpBase = "http://10.11.12.1:8088"
    private var wsUrl = "ws://10.11.12.1:8089/v1/sensors/full?interval=500"

    fun configure(host: String, httpPort: Int = 8088, wsPort: Int = 8089) {
        httpBase = "http://$host:$httpPort"
        wsUrl = "ws://$host:$wsPort/v1/sensors/full?interval=500"
        Log.d(TAG, "Configured: HTTP=$httpBase WS=$wsUrl")
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    private val wsClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private var autopilotId: Int = -1

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    private val _autopilotState = MutableStateFlow(AutopilotState())
    val autopilotState: StateFlow<AutopilotState> = _autopilotState

    private val _detectedAutopilotIds = MutableStateFlow<Set<Int>>(emptySet())
    val detectedAutopilotIds: StateFlow<Set<Int>> = _detectedAutopilotIds

    fun connect() {
        _connectionState.value = ConnectionState.CONNECTING
        connectWebSocket()
    }

    fun disconnect() {
        webSocket?.close(1000, "Client closing")
        webSocket = null
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    private fun connectWebSocket() {
        val request = Request.Builder().url(wsUrl).build()
        webSocket = wsClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket connected")
                DebugLog.record(DebugLog.Kind.INFO, "WebSocket connected", wsUrl)
                _connectionState.value = ConnectionState.CONNECTED
                enumerateAutopilots()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                DebugLog.record(DebugLog.Kind.WS_IN, "sensors/full", text)
                parseSensorData(text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket failure: ${t.message}")
                DebugLog.record(DebugLog.Kind.INFO, "WebSocket failure", t.message ?: "")
                _connectionState.value = ConnectionState.DISCONNECTED
                scheduleReconnect()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closed: $reason")
                DebugLog.record(DebugLog.Kind.INFO, "WebSocket closed", "$code $reason")
                _connectionState.value = ConnectionState.DISCONNECTED
            }
        })
    }

    private fun scheduleReconnect() {
        Thread {
            Thread.sleep(3000)
            if (_connectionState.value == ConnectionState.DISCONNECTED) {
                Log.d(TAG, "Reconnecting WebSocket...")
                connectWebSocket()
            }
        }.start()
    }

    private fun enumerateAutopilots() {
        DebugLog.record(DebugLog.Kind.HTTP_OUT, "GET /v1/autopilots")
        val request = Request.Builder().url("$httpBase/v1/autopilots").build()
        httpClient.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onResponse(call: okhttp3.Call, response: Response) {
                val body = response.body?.string()
                Log.d(TAG, "GET /v1/autopilots → ${response.code}: $body")
                DebugLog.record(DebugLog.Kind.HTTP_IN, "GET /v1/autopilots → ${response.code}", body ?: "")
                response.close()
                if (response.isSuccessful && body != null) {
                    try {
                        val json = JSONObject(body)
                        val results = json.getJSONArray("results")
                        Log.d(TAG, "Found ${results.length()} autopilot(s): $results")
                        if (results.length() > 0 && autopilotId < 0) {
                            autopilotId = results.getInt(0)
                            Log.d(TAG, "Auto-selected autopilot ID: $autopilotId")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to parse autopilots list", e)
                    }
                }
            }

            override fun onFailure(call: okhttp3.Call, e: IOException) {
                Log.e(TAG, "GET /v1/autopilots error: ${e.message}")
                DebugLog.record(DebugLog.Kind.HTTP_IN, "GET /v1/autopilots failed", e.message ?: "")
            }
        })
    }

    private fun parseSensorData(text: String) {
        try {
            val json = JSONObject(text)
            val values = if (json.has("values")) json.getJSONObject("values") else json
            val prefix = "autopilot.$autopilotId"
            val steeringPrefix = "steering.headingControl.$autopilotId"
            val current = _autopilotState.value

            var heading = current.heading
            var mode = current.mode
            var engaged = current.engaged
            var windAngle = current.windAngle

            if (values.has("$steeringPrefix.headingToSteer")) {
                heading = Math.toDegrees(values.getDouble("$steeringPrefix.headingToSteer")).toInt() % 360
            }
            if (values.has("$prefix.mode")) {
                val modeValue = values.getInt("$prefix.mode")
                mode = OrcaModes.fromValue(modeValue)
                engaged = mode != OrcaModes.STANDBY
            }
            if (values.has("$prefix.windHoldAngle")) {
                windAngle = Math.toDegrees(values.getDouble("$prefix.windHoldAngle")).toInt()
            }

            _autopilotState.value = AutopilotState(
                engaged = engaged,
                heading = heading,
                mode = if (engaged) mode else current.mode,
                windAngle = windAngle
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse sensor data", e)
        }
    }

    fun setMode(mode: String, onResult: ((Boolean) -> Unit)? = null) {
        postJson("/v1/autopilots/$autopilotId/mode", """{"value":${OrcaModes.toValue(mode)}}""", onResult)
    }

    fun setAutopilotId(id: Int) {
        if (id >= 0) autopilotId = id
    }

    fun disengage(onResult: ((Boolean) -> Unit)? = null) {
        postJson("/v1/autopilots/$autopilotId/mode", """{"value":${OrcaModes.toValue(OrcaModes.STANDBY)}}""", onResult)
    }

    fun adjustCourse(degrees: Int, onResult: ((Boolean) -> Unit)? = null) {
        val isWind = _autopilotState.value.mode == OrcaModes.WIND
        val steps = mutableListOf<Int>()
        var remaining = degrees
        while (remaining != 0) {
            val step = when {
                remaining >= 10 -> 10
                remaining > 0 -> 1
                remaining <= -10 -> -10
                else -> -1
            }
            steps.add(step)
            remaining -= step
        }
        if (steps.isEmpty()) {
            onResult?.invoke(true)
            return
        }
        sendCourseSteps(steps, isWind, 0, onResult)
    }

    private fun sendCourseSteps(steps: List<Int>, isWind: Boolean, index: Int, onResult: ((Boolean) -> Unit)?) {
        val step = steps[index]
        val body = if (isWind) {
            """{"value":$step,"wind_value":${-step}}"""
        } else {
            """{"value":$step}"""
        }
        postJson("/v1/autopilots/$autopilotId/course-change", body) { success ->
            if (!success || index + 1 >= steps.size) {
                onResult?.invoke(success)
            } else {
                sendCourseSteps(steps, isWind, index + 1, onResult)
            }
        }
    }

    private fun postJson(path: String, json: String, onResult: ((Boolean) -> Unit)? = null) {
        DebugLog.record(DebugLog.Kind.HTTP_OUT, "POST $path", json)
        val request = Request.Builder()
            .url("$httpBase$path")
            .post(json.toRequestBody(JSON_TYPE))
            .build()

        httpClient.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onResponse(call: okhttp3.Call, response: Response) {
                val success = response.isSuccessful
                val body = response.body?.string()
                Log.d(TAG, "POST $path → ${response.code}: $body")
                DebugLog.record(DebugLog.Kind.HTTP_IN, "POST $path → ${response.code}", body ?: "")
                response.close()
                onResult?.invoke(success)
            }

            override fun onFailure(call: okhttp3.Call, e: IOException) {
                Log.e(TAG, "POST $path error: ${e.message}")
                DebugLog.record(DebugLog.Kind.HTTP_IN, "POST $path failed", e.message ?: "")
                onResult?.invoke(false)
            }
        })
    }
}
