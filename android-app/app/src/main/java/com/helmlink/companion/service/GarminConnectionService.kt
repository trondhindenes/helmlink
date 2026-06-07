package com.helmlink.companion.service

import android.content.Context
import android.util.Log
import com.helmlink.companion.model.AutopilotCommand
import com.helmlink.companion.model.ConnectionState
import com.garmin.android.connectiq.ConnectIQ
import com.garmin.android.connectiq.IQApp
import com.garmin.android.connectiq.IQDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class GarminConnectionService(private val context: Context) {

    companion object {
        private const val TAG = "GarminConnection"
        const val WATCH_APP_ID = "15a3b6d3-c6a6-4452-ab21-ad992ef28dc6"

        // The watch widget pings every 5s while open; if we haven't heard from it
        // in this long, it's closed and ConnectIQ sends would fail with
        // FAILURE_DURING_TRANSFER, so we skip them.
        private const val WATCH_ACTIVE_TIMEOUT_MS = 15_000L
    }

    @Volatile
    private var lastWatchMessageAt = 0L

    private var connectIQ: ConnectIQ? = null
    private var connectedDevice: IQDevice? = null
    private var watchApp: IQApp? = null

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    var onCommandReceived: ((AutopilotCommand) -> Unit)? = null
    var onPingReceived: (() -> Unit)? = null

    @Volatile
    var processedSeq: Int = 0
        private set

    fun ackSeq(seq: Int) {
        if (seq > processedSeq) processedSeq = seq
    }

    fun initialize() {
        _connectionState.value = ConnectionState.CONNECTING
        try {
            connectIQ = ConnectIQ.getInstance(context, ConnectIQ.IQConnectType.WIRELESS)
            connectIQ?.initialize(context, true, object : ConnectIQ.ConnectIQListener {
                override fun onSdkReady() {
                    Log.d(TAG, "ConnectIQ SDK ready")
                    DebugLog.record(DebugLog.Kind.INFO, "ConnectIQ SDK ready")
                    discoverDevices()
                }

                override fun onInitializeError(status: ConnectIQ.IQSdkErrorStatus) {
                    Log.e(TAG, "ConnectIQ init error: $status")
                    DebugLog.record(DebugLog.Kind.INFO, "ConnectIQ init error", status.toString())
                    _connectionState.value = ConnectionState.DISCONNECTED
                }

                override fun onSdkShutDown() {
                    Log.d(TAG, "ConnectIQ SDK shut down")
                    DebugLog.record(DebugLog.Kind.INFO, "ConnectIQ SDK shut down")
                    _connectionState.value = ConnectionState.DISCONNECTED
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize ConnectIQ", e)
            _connectionState.value = ConnectionState.DISCONNECTED
        }
    }

    private fun discoverDevices() {
        try {
            val devices = connectIQ?.knownDevices ?: emptyList()
            Log.d(TAG, "Known devices: ${devices.size}")

            for (device in devices) {
                connectIQ?.registerForDeviceEvents(device) { _, status ->
                    Log.d(TAG, "Device ${device.friendlyName}: $status")
                    DebugLog.record(DebugLog.Kind.INFO, "Watch ${device.friendlyName}", status.toString())
                    if (status == IQDevice.IQDeviceStatus.CONNECTED) {
                        registerWatchApp(device)
                    } else if (device == connectedDevice) {
                        _connectionState.value = ConnectionState.DISCONNECTED
                        connectedDevice = null
                    }
                }

                if (device.status == IQDevice.IQDeviceStatus.CONNECTED) {
                    registerWatchApp(device)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to discover devices", e)
        }
    }

    private fun registerWatchApp(device: IQDevice) {
        try {
            watchApp = IQApp(WATCH_APP_ID)
            connectIQ?.registerForAppEvents(device, watchApp) { _, _, messages, _ ->
                messages?.forEach { msg ->
                    handleWatchMessage(msg)
                }
            }
            connectedDevice = device
            _connectionState.value = ConnectionState.CONNECTED
            Log.d(TAG, "Registered for watch app events on ${device.friendlyName}")
            DebugLog.record(DebugLog.Kind.INFO, "Registered watch app", device.friendlyName ?: "")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register watch app", e)
            DebugLog.record(DebugLog.Kind.INFO, "Failed to register watch app", e.message ?: "")
        }
    }

    private fun handleWatchMessage(msg: Any?) {
        if (msg is Map<*, *>) {
            lastWatchMessageAt = System.currentTimeMillis()
            val cmd = msg["cmd"] as? String
            DebugLog.record(
                DebugLog.Kind.WATCH_IN,
                cmd ?: "unknown",
                msg.toString(),
                heartbeat = cmd == "PING"
            )
            if (cmd == null) return

            if (cmd == "PING") {
                onPingReceived?.invoke()
                return
            }

            val seq = (msg["seq"] as? Number)?.toInt() ?: 0

            val command = when (cmd) {
                "ENGAGE" -> {
                    val mode = msg["mode"] as? String ?: "AUTO"
                    AutopilotCommand(type = "ENGAGE", detail = mode, seq = seq)
                }
                "DISENGAGE" -> AutopilotCommand(type = "DISENGAGE", detail = "", seq = seq)
                "ADJUST" -> {
                    val heading = (msg["heading"] as? Number)?.toInt() ?: 0
                    AutopilotCommand(type = "ADJUST", detail = heading.toString(), seq = seq)
                }
                "MODE" -> {
                    val mode = msg["mode"] as? String ?: ""
                    AutopilotCommand(type = "MODE", detail = mode, seq = seq)
                }
                else -> null
            }
            command?.let { onCommandReceived?.invoke(it) }
        }
    }


    private data class StatusMessage(
        val heading: Int,
        val mode: String,
        val engaged: Boolean,
        val confirmed: Boolean
    )

    // ConnectIQ transfers go over BLE and take ~1s+; starting a new one while
    // another is in flight causes FAILURE_DURING_TRANSFER. Send one at a time
    // and coalesce: a status message is a full-state snapshot, so the latest
    // queued one supersedes anything before it.
    private var sendInFlight = false
    private var sendStartedAt = 0L
    private var queuedStatus: StatusMessage? = null

    fun sendStatusToWatch(heading: Int, mode: String, engaged: Boolean, confirmed: Boolean = false) {
        if (connectedDevice == null || watchApp == null) return

        // Widget closed on the watch: messages can't be delivered, don't try.
        if (System.currentTimeMillis() - lastWatchMessageAt > WATCH_ACTIVE_TIMEOUT_MS) {
            Log.d(TAG, "Skipping status send - watch widget not active")
            return
        }

        val status = StatusMessage(heading, mode, engaged, confirmed)
        synchronized(this) {
            // Safety valve: if a completion callback never arrived, don't stay stuck.
            if (sendInFlight && System.currentTimeMillis() - sendStartedAt > 10_000) {
                Log.w(TAG, "Send stuck in flight >10s, resetting")
                sendInFlight = false
            }
            if (sendInFlight) {
                queuedStatus = status
                return
            }
            sendInFlight = true
            sendStartedAt = System.currentTimeMillis()
        }
        transmit(status)
    }

    private fun transmit(status: StatusMessage) {
        val device = connectedDevice
        val app = watchApp
        if (device == null || app == null) {
            synchronized(this) {
                sendInFlight = false
                queuedStatus = null
            }
            return
        }

        val message = mutableMapOf<String, Any>(
            "pong" to true,
            "heading" to status.heading,
            "mode" to status.mode,
            "engaged" to status.engaged,
            "ack_seq" to processedSeq
        )
        if (status.confirmed) {
            message["confirmed"] = true
        }

        DebugLog.record(
            DebugLog.Kind.WATCH_OUT,
            if (status.confirmed) "status (confirmed)" else "status",
            message.toString(),
            heartbeat = !status.confirmed
        )

        try {
            connectIQ?.sendMessage(device, app, message) { _, _, sendStatus ->
                Log.d(TAG, "Send status: $sendStatus")
                if (sendStatus != ConnectIQ.IQMessageStatus.SUCCESS) {
                    DebugLog.record(DebugLog.Kind.INFO, "Send to watch failed", sendStatus.toString())
                }
                val next: StatusMessage?
                synchronized(this) {
                    next = queuedStatus
                    queuedStatus = null
                    if (next == null) {
                        sendInFlight = false
                    } else {
                        sendStartedAt = System.currentTimeMillis()
                    }
                }
                if (next != null) transmit(next)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send status to watch", e)
            DebugLog.record(DebugLog.Kind.INFO, "Send to watch failed", e.message ?: "")
            synchronized(this) {
                sendInFlight = false
                queuedStatus = null
            }
        }
    }

    fun shutdown() {
        try {
            connectIQ?.shutdown(context)
        } catch (e: Exception) {
            Log.e(TAG, "Error shutting down ConnectIQ", e)
        }
        connectIQ = null
        connectedDevice = null
        _connectionState.value = ConnectionState.DISCONNECTED
    }
}
