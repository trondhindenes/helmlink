package com.helmlink.companion.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicLong

/**
 * In-memory ring buffer of traffic between the app, Orca Core, and the watch.
 * Written to by [OrcaApiClient] and [GarminConnectionService], observed by the debug view.
 */
object DebugLog {

    enum class Kind {
        HTTP_OUT,   // request the app sent to Orca
        HTTP_IN,    // response Orca returned
        WS_IN,      // sensor/state frame received over the WebSocket
        WATCH_IN,   // message received from the watch
        WATCH_OUT,  // message sent to the watch
        INFO        // connection lifecycle / errors
    }

    data class Entry(
        val id: Long,
        val timestamp: Instant,
        val kind: Kind,
        val label: String,
        val detail: String,
        val heartbeat: Boolean = false
    )

    private const val MAX_ENTRIES = 500
    private val seq = AtomicLong(0)
    private val exportFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")

    private val _entries = MutableStateFlow<List<Entry>>(emptyList())
    val entries: StateFlow<List<Entry>> = _entries

    fun record(kind: Kind, label: String, detail: String = "", heartbeat: Boolean = false) {
        val entry = Entry(
            id = seq.incrementAndGet(),
            timestamp = Instant.now(),
            kind = kind,
            label = label,
            detail = detail,
            heartbeat = heartbeat
        )
        _entries.value = (listOf(entry) + _entries.value).take(MAX_ENTRIES)
    }

    fun clear() {
        _entries.value = emptyList()
    }

    /** Full buffer as text, oldest first, for export. */
    fun formatAll(): String {
        val sb = StringBuilder()
        sb.append("HelmLink debug log\n")
        sb.append("Exported: ${exportFormatter.format(Instant.now().atZone(ZoneId.systemDefault()))}\n")
        sb.append("Entries: ${_entries.value.size} (max $MAX_ENTRIES, newest kept)\n\n")
        _entries.value.asReversed().forEach { e ->
            val ts = exportFormatter.format(e.timestamp.atZone(ZoneId.systemDefault()))
            sb.append("$ts  ${e.kind.name.padEnd(9)} ${e.label}")
            if (e.detail.isNotBlank()) {
                sb.append("\n    ${e.detail}")
            }
            sb.append('\n')
        }
        return sb.toString()
    }
}
