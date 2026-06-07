package com.helmlink.companion

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.helmlink.companion.service.GarminConnectionService
import com.helmlink.companion.service.HelmLinkBridgeService
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.helmlink.companion.model.AutopilotCommand
import com.helmlink.companion.model.AutopilotState
import com.helmlink.companion.model.ConnectionState
import com.helmlink.companion.model.OrcaSettings
import com.helmlink.companion.service.DebugLog
import com.helmlink.companion.ui.theme.AutopilotTheme
import com.helmlink.companion.viewmodel.HelmLinkViewModel
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 0)
        }

        val serviceIntent = Intent(this, HelmLinkBridgeService::class.java)
        startForegroundService(serviceIntent)

        enableEdgeToEdge()
        setContent {
            AutopilotTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    DashboardScreen(
                        modifier = Modifier.padding(innerPadding),
                        onExit = {
                            val stopIntent = Intent(this@MainActivity, HelmLinkBridgeService::class.java).apply {
                                action = HelmLinkBridgeService.ACTION_STOP
                            }
                            startService(stopIntent)
                            finishAndRemoveTask()
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun DashboardScreen(
    modifier: Modifier = Modifier,
    viewModel: HelmLinkViewModel = viewModel(),
    onExit: () -> Unit = {}
) {
    val autopilotState by viewModel.autopilotState.collectAsStateWithLifecycle()
    val watchConnection by viewModel.watchConnectionState.collectAsStateWithLifecycle()
    val orcaConnection by viewModel.orcaConnectionState.collectAsStateWithLifecycle()
    val commandLog by viewModel.commandLog.collectAsStateWithLifecycle()
    val isSearching by viewModel.discoverySearching.collectAsStateWithLifecycle()
    val discoveredHost by viewModel.discoveredHost.collectAsStateWithLifecycle()
    val isTestMode by viewModel.isTestMode.collectAsStateWithLifecycle()
    var showSettings by remember { mutableStateOf(false) }
    var showExitConfirm by remember { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(false) }
    var debugMode by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "HelmLink Companion",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                TextButton(
                    onClick = { showInfo = true },
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Text("Info")
                }
                TextButton(
                    onClick = { showSettings = true },
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Text("Settings")
                }
                TextButton(
                    onClick = { showExitConfirm = true },
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Text("Exit", color = MaterialTheme.colorScheme.error)
                }
            }
        }

        if (isTestMode) {
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFFF6F00)
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "TEST MODE — NOT CONNECTED TO ORCA",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (!isTestMode) {
            ConnectionStatusRow(watchConnection, orcaConnection, isSearching, discoveredHost)
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                ConnectionIndicator("Watch", watchConnection)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        ViewModeToggle(debugMode = debugMode, onChange = { debugMode = it })

        Spacer(modifier = Modifier.height(16.dp))

        if (!debugMode) {
            AutopilotStateCard(autopilotState)

            Spacer(modifier = Modifier.height(16.dp))

            SimulateButtons(viewModel)

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Command Log",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(modifier = Modifier.weight(1f)) {
                items(commandLog) { command ->
                    CommandLogItem(command)
                    HorizontalDivider()
                }
            }
        } else {
            DebugView(viewModel, modifier = Modifier.weight(1f))
        }
    }

    if (showSettings) {
        val detectedIds by viewModel.detectedAutopilotIds.collectAsStateWithLifecycle()
        SettingsDialog(
            settings = viewModel.settings,
            detectedAutopilotIds = detectedIds,
            onDismiss = { showSettings = false },
            onSave = {
                showSettings = false
                viewModel.reconnectOrca()
            }
        )
    }

    if (showExitConfirm) {
        AlertDialog(
            onDismissRequest = { showExitConfirm = false },
            title = { Text("Exit HelmLink?") },
            text = {
                Text(
                    "This stops the bridge and closes the app. The watch will no longer " +
                        "be able to control the autopilot until you reopen it."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showExitConfirm = false
                    onExit()
                }) { Text("Exit", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showExitConfirm = false }) { Text("Cancel") }
            }
        )
    }

    if (showInfo) {
        InfoDialog(onDismiss = { showInfo = false })
    }
}

@Composable
fun InfoDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val versionName = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
        } catch (e: Exception) {
            "?"
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("HelmLink Info") },
        text = {
            SelectionContainer {
                Column {
                    InfoRow("Watch app UUID", GarminConnectionService.WATCH_APP_ID)
                    Spacer(modifier = Modifier.height(12.dp))
                    InfoRow("Package", context.packageName)
                    Spacer(modifier = Modifier.height(12.dp))
                    InfoRow("Version", versionName)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
fun InfoRow(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
fun ConnectionStatusRow(
    watch: ConnectionState,
    orca: ConnectionState,
    isSearching: Boolean,
    discoveredHost: String?
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            ConnectionIndicator("Watch", watch)
            ConnectionIndicator("Orca", orca)
        }

        if (isSearching) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Searching for Orca Core...",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        } else if (discoveredHost != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Orca at $discoveredHost",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
fun ConnectionIndicator(label: String, state: ConnectionState) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(
                    when (state) {
                        ConnectionState.CONNECTED -> Color(0xFF4CAF50)
                        ConnectionState.CONNECTING -> Color(0xFFFFC107)
                        ConnectionState.DISCONNECTED -> Color(0xFFF44336)
                    }
                )
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = "$label: ${state.name.lowercase()}", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun SettingsDialog(
    settings: OrcaSettings,
    detectedAutopilotIds: Set<Int>,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    var testMode by remember { mutableStateOf(settings.testMode) }
    var autoDiscover by remember { mutableStateOf(settings.autoDiscover) }
    var host by remember { mutableStateOf(settings.host) }
    var selectedApId by remember { mutableStateOf(settings.autopilotId) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Orca Connection") },
        text = {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Test mode")
                    Switch(
                        checked = testMode,
                        onCheckedChange = { testMode = it }
                    )
                }

                if (testMode) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "No Orca connection — commands update local state only",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (!testMode) {
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Auto-discover")
                        Switch(
                            checked = autoDiscover,
                            onCheckedChange = { autoDiscover = it }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = host,
                        onValueChange = { host = it },
                        label = { Text("Host address") },
                        placeholder = { Text("10.11.12.1") },
                        enabled = !autoDiscover,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = if (autoDiscover) "Will search for Orca Core via mDNS"
                               else "Connect directly to specified host",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (detectedAutopilotIds.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))

                    Text("Autopilot", style = MaterialTheme.typography.bodyMedium)

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        detectedAutopilotIds.sorted().forEach { id ->
                            OutlinedButton(
                                onClick = { selectedApId = id },
                                modifier = Modifier.weight(1f),
                                colors = if (selectedApId == id)
                                    androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer
                                    )
                                else
                                    androidx.compose.material3.ButtonDefaults.outlinedButtonColors()
                            ) {
                                Text("ID $id")
                            }
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Autopilot ID: auto (first detected)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                settings.testMode = testMode
                settings.autoDiscover = autoDiscover
                settings.host = host
                settings.autopilotId = selectedApId
                onSave()
            }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun AutopilotStateCard(state: AutopilotState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (state.engaged)
                Color(0xFF1B5E20).copy(alpha = 0.3f)
            else
                MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (state.engaged) "ENGAGED" else "STANDBY",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = if (state.engaged) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "%03d".format(state.heading),
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = state.mode,
                style = MaterialTheme.typography.titleMedium,
                color = when (state.mode) {
                    "AUTO" -> Color(0xFF5588FF)
                    "WIND" -> Color(0xFF00CCCC)
                    "NO_DRIFT" -> Color(0xFFFFAA00)
                    else -> MaterialTheme.colorScheme.onSurface
                }
            )
        }
    }
}

@Composable
fun SimulateButtons(viewModel: HelmLinkViewModel) {
    Text(
        text = "Simulate Watch Commands",
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Spacer(modifier = Modifier.height(8.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(
            onClick = { viewModel.simulateWatchCommand("ENGAGE", "AUTO") },
            modifier = Modifier.weight(1f)
        ) { Text("Engage") }

        Button(
            onClick = { viewModel.simulateWatchCommand("DISENGAGE") },
            modifier = Modifier.weight(1f)
        ) { Text("Disengage") }
    }

    Spacer(modifier = Modifier.height(4.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedButton(
            onClick = { viewModel.simulateWatchCommand("ADJUST", "-1") },
            modifier = Modifier.weight(1f)
        ) { Text("Port") }

        OutlinedButton(
            onClick = { viewModel.simulateWatchCommand("ADJUST", "1") },
            modifier = Modifier.weight(1f)
        ) { Text("Stbd") }
    }

    Spacer(modifier = Modifier.height(4.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedButton(
            onClick = { viewModel.simulateWatchCommand("MODE", "AUTO") },
            modifier = Modifier.weight(1f)
        ) { Text("Auto") }

        OutlinedButton(
            onClick = { viewModel.simulateWatchCommand("MODE", "WIND") },
            modifier = Modifier.weight(1f)
        ) { Text("Wind") }

        OutlinedButton(
            onClick = { viewModel.simulateWatchCommand("MODE", "NO_DRIFT") },
            modifier = Modifier.weight(1f)
        ) { Text("No Drift") }
    }
}

private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
private val debugTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
private val fileNameFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")

private fun shareDebugLog(context: Context) {
    val dir = File(context.cacheDir, "debug-logs").apply { mkdirs() }
    val file = File(dir, "helmlink-debug-${LocalDateTime.now().format(fileNameFormatter)}.txt")
    file.writeText(DebugLog.formatAll())

    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, file.name)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share debug log"))
}

@Composable
fun ViewModeToggle(debugMode: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (!debugMode) {
            Button(onClick = { onChange(false) }, modifier = Modifier.weight(1f)) { Text("Simple") }
            OutlinedButton(onClick = { onChange(true) }, modifier = Modifier.weight(1f)) { Text("Debug") }
        } else {
            OutlinedButton(onClick = { onChange(false) }, modifier = Modifier.weight(1f)) { Text("Simple") }
            Button(onClick = { onChange(true) }, modifier = Modifier.weight(1f)) { Text("Debug") }
        }
    }
}

@Composable
fun DebugView(viewModel: HelmLinkViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val entries by viewModel.debugLog.collectAsStateWithLifecycle()
    var showSensorStream by remember { mutableStateOf(false) }
    var showHeartbeat by remember { mutableStateOf(false) }
    val expanded = remember { mutableStateOf(setOf<Long>()) }

    val visible = entries.filter { entry ->
        (showSensorStream || entry.kind != DebugLog.Kind.WS_IN) &&
            (showHeartbeat || !entry.heartbeat)
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Traffic",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Row {
                TextButton(
                    onClick = { shareDebugLog(context) },
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) { Text("Share") }
                TextButton(
                    onClick = { viewModel.clearDebugLog() },
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) { Text("Clear") }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Sensor stream",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(4.dp))
                Switch(checked = showSensorStream, onCheckedChange = { showSensorStream = it })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Heartbeat",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(4.dp))
                Switch(checked = showHeartbeat, onCheckedChange = { showHeartbeat = it })
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (visible.isEmpty()) {
            Text(
                text = "No traffic yet. Watch commands, Orca requests and responses will appear here.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(visible) { entry ->
                DebugLogItem(
                    entry = entry,
                    expanded = expanded.value.contains(entry.id),
                    onToggle = {
                        expanded.value = if (expanded.value.contains(entry.id))
                            expanded.value - entry.id
                        else
                            expanded.value + entry.id
                    }
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
fun DebugLogItem(entry: DebugLog.Entry, expanded: Boolean, onToggle: () -> Unit) {
    val color = when (entry.kind) {
        DebugLog.Kind.HTTP_OUT -> Color(0xFF5588FF)
        DebugLog.Kind.HTTP_IN -> Color(0xFF4CAF50)
        DebugLog.Kind.WS_IN -> Color(0xFF00CCCC)
        DebugLog.Kind.WATCH_IN -> Color(0xFFFFAA00)
        DebugLog.Kind.WATCH_OUT -> Color(0xFFCC88FF)
        DebugLog.Kind.INFO -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val arrow = when (entry.kind) {
        DebugLog.Kind.HTTP_OUT -> "→"
        DebugLog.Kind.HTTP_IN, DebugLog.Kind.WS_IN -> "←"
        DebugLog.Kind.WATCH_IN -> "←⌚"
        DebugLog.Kind.WATCH_OUT -> "→⌚"
        DebugLog.Kind.INFO -> "•"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .padding(vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "$arrow ${entry.label}",
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                color = color,
                modifier = Modifier.weight(1f, fill = false)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = entry.timestamp
                    .atZone(ZoneId.systemDefault())
                    .format(debugTimeFormatter),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (entry.detail.isNotBlank()) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = entry.detail,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (expanded) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun CommandLogItem(command: AutopilotCommand) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "${command.type} ${command.detail}".trim(),
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = command.timestamp
                .atZone(ZoneId.systemDefault())
                .format(timeFormatter),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
