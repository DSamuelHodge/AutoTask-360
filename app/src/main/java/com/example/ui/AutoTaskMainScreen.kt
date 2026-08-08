package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SettingsSuggest
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AutomationProfile
import com.example.data.ExecutionLog
import com.example.ui.theme.HighDensityErrorContainer
import com.example.ui.theme.HighDensityErrorRed
import com.example.ui.theme.HighDensityOnErrorContainer
import com.example.ui.theme.HighDensityOnPrimaryContainer
import com.example.ui.theme.HighDensityOnSurface
import com.example.ui.theme.HighDensityOnSurfaceVariant
import com.example.ui.theme.HighDensityPrimary
import com.example.ui.theme.HighDensityPrimaryContainer
import com.example.ui.theme.HighDensitySuccessGreen
import com.example.ui.theme.HighDensitySurface
import com.example.ui.theme.HighDensitySurfaceVariant
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AutoTaskMainScreen(viewModel: AutoTaskViewModel) {
    val profiles by viewModel.profiles.collectAsState()
    val logs by viewModel.logs.collectAsState()
    val isServiceRunning by viewModel.isServiceRunning.collectAsState()
    val apiTestResponse by viewModel.apiTestResponse.collectAsState()
    val isApiTestLoading by viewModel.isApiTestLoading.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) } // 0: Policies, 1: Logs, 2: Events, 3: Status/API
    var showAddDialog by remember { mutableStateOf(false) }
    var selectedJsonProfile by remember { mutableStateOf<AutomationProfile?>(null) }

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = Color.White,
                tonalElevation = 4.dp,
                modifier = Modifier
                    .height(72.dp)
                    .border(1.dp, HighDensitySurfaceVariant)
            ) {
                val tabs = listOf("POLICIES", "LOGS", "EVENTS", "STATUS")
                val icons = listOf(
                    Icons.Default.SettingsSuggest,
                    Icons.Default.History,
                    Icons.Default.Terminal,
                    Icons.Default.Info
                )

                tabs.forEachIndexed { index, label ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = { Icon(icons[index], contentDescription = label) },
                        label = {
                            Text(
                                text = label,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = HighDensityOnPrimaryContainer,
                            selectedTextColor = HighDensityOnPrimaryContainer,
                            indicatorColor = HighDensityPrimaryContainer,
                            unselectedIconColor = HighDensityOnSurfaceVariant,
                            unselectedTextColor = HighDensityOnSurfaceVariant
                        ),
                        modifier = Modifier.testTag("nav_tab_$index")
                    )
                }
            }
        },
        containerColor = Color(0xFFFDFBFF)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Header
            HeaderSection(
                isRunning = isServiceRunning,
                onToggleService = { viewModel.toggleService(it) }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Metric Overview Cards
            MetricsSection(
                activeProfilesCount = profiles.count { it.isEnabled },
                totalProfilesCount = profiles.size,
                logsCount = logs.size
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Main Content Card Container
            Card(
                colors = CardDefaults.cardColors(containerColor = HighDensitySurface),
                shape = RoundedCornerShape(24.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, HighDensitySurfaceVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    when (selectedTab) {
                        0 -> PoliciesTab(
                            profiles = profiles,
                            onToggleProfile = { id, enabled -> viewModel.toggleProfileEnabled(id, enabled) },
                            onFireManual = { id -> viewModel.fireManualEvent(id) },
                            onDeleteProfile = { id -> viewModel.deleteProfile(id) },
                            onShowJson = { profile -> selectedJsonProfile = profile },
                            onOpenAddDialog = { showAddDialog = true }
                        )

                        1 -> LogsTab(
                            logs = logs,
                            onClearLogs = { viewModel.clearLogs() }
                        )

                        2 -> EventsTab(
                            onFireEvent = { type -> viewModel.fireManualEvent() },
                            viewModel = viewModel
                        )

                        3 -> StatusTab(
                            isServiceRunning = isServiceRunning,
                            logs = logs,
                            apiTestResponse = apiTestResponse,
                            isApiTestLoading = isApiTestLoading,
                            onExecuteApiTest = { method, endpoint, body ->
                                viewModel.executeApiTest(method, endpoint, body)
                            }
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddProfileDialog(
            onDismiss = { showAddDialog = false },
            onSave = { newProf ->
                viewModel.upsertProfile(newProf)
                showAddDialog = false
            }
        )
    }

    if (selectedJsonProfile != null) {
        ProfileJsonDialog(
            profile = selectedJsonProfile!!,
            onDismiss = { selectedJsonProfile = null }
        )
    }
}

@Composable
private fun HeaderSection(
    isRunning: Boolean,
    onToggleService: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "AutoTask",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = HighDensityOnPrimaryContainer
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(top = 2.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (isRunning) HighDensitySuccessGreen else Color.Gray)
                )
                Text(
                    text = if (isRunning) "ENGINE RUNNING" else "ENGINE STOPPED",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = HighDensityOnSurfaceVariant
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(HighDensityPrimaryContainer)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "127.0.0.1:8788",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = HighDensityOnPrimaryContainer
                )
            }

            Switch(
                checked = isRunning,
                onCheckedChange = onToggleService,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = HighDensityPrimary,
                    uncheckedThumbColor = Color.White,
                    uncheckedTrackColor = HighDensitySurfaceVariant
                ),
                modifier = Modifier.testTag("engine_switch")
            )
        }
    }
}

@Composable
private fun MetricsSection(
    activeProfilesCount: Int,
    totalProfilesCount: Int,
    logsCount: Int
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = HighDensitySurfaceVariant),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .weight(1f)
                .height(72.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(10.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "ACTIVE POLICIES",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = HighDensityOnSurfaceVariant
                )
                Text(
                    text = "$activeProfilesCount / $totalProfilesCount",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = HighDensityOnPrimaryContainer
                )
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = HighDensityErrorContainer),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .weight(1f)
                .height(72.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(10.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "EXECUTION LOGS",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = HighDensityOnSurfaceVariant
                )
                Text(
                    text = "$logsCount",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = HighDensityOnErrorContainer
                )
            }
        }
    }
}

@Composable
private fun PoliciesTab(
    profiles: List<AutomationProfile>,
    onToggleProfile: (String, Boolean) -> Unit,
    onFireManual: (String?) -> Unit,
    onDeleteProfile: (String) -> Unit,
    onShowJson: (AutomationProfile) -> Unit,
    onOpenAddDialog: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "AUTOMATION POLICIES",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = HighDensityOnSurface
            )

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(
                    onClick = { onFireManual(null) },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                    modifier = Modifier.height(32.dp).testTag("fire_all_button")
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("FIRE ALL", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = onOpenAddDialog,
                    colors = ButtonDefaults.buttonColors(containerColor = HighDensityPrimary),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                    modifier = Modifier.height(32.dp).testTag("add_policy_button")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("NEW", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (profiles.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("No automation policies configured", color = HighDensityOnSurfaceVariant, fontSize = 12.sp)
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(profiles, key = { it.id }) { profile ->
                    PolicyCard(
                        profile = profile,
                        onToggle = { onToggleProfile(profile.id, profile.isEnabled) },
                        onRun = { onFireManual(profile.id) },
                        onDelete = { onDeleteProfile(profile.id) },
                        onShowJson = { onShowJson(profile) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PolicyCard(
    profile: AutomationProfile,
    onToggle: () -> Unit,
    onRun: () -> Unit,
    onDelete: () -> Unit,
    onShowJson: () -> Unit
) {
    val triggerIcon: ImageVector = when (profile.triggerType) {
        "BATTERY" -> Icons.Default.BatteryChargingFull
        "WIFI" -> Icons.Default.Wifi
        "SMS" -> Icons.Default.Sms
        else -> Icons.Default.SettingsSuggest
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (profile.isEnabled) HighDensityPrimaryContainer else Color.Transparent
        ),
        modifier = Modifier.fillMaxWidth().testTag("policy_card_${profile.id}")
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (profile.isEnabled) HighDensityPrimaryContainer else HighDensitySurfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = triggerIcon,
                        contentDescription = null,
                        tint = if (profile.isEnabled) HighDensityOnPrimaryContainer else HighDensityOnSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = profile.name,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = HighDensityOnPrimaryContainer
                    )
                    Text(
                        text = "${profile.triggerType} • ${profile.description.ifEmpty { "No description" }}",
                        fontSize = 10.sp,
                        color = HighDensityOnSurfaceVariant,
                        maxLines = 1
                    )
                }

                Switch(
                    checked = profile.isEnabled,
                    onCheckedChange = { onToggle() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = HighDensityPrimary,
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = HighDensitySurfaceVariant
                    ),
                    modifier = Modifier.height(28.dp)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ID: ${profile.id}",
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    color = HighDensityOnSurfaceVariant
                )

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = onRun, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Run", tint = HighDensityPrimary, modifier = Modifier.size(16.dp))
                    }
                    IconButton(onClick = onShowJson, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Code, contentDescription = "JSON", tint = HighDensityOnSurfaceVariant, modifier = Modifier.size(16.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = HighDensityErrorRed, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun LogsTab(
    logs: List<ExecutionLog>,
    onClearLogs: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "EXECUTION LOGS (${logs.size})",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = HighDensityOnSurface
            )

            TextButton(
                onClick = onClearLogs,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                modifier = Modifier.height(28.dp).testTag("clear_logs_button")
            ) {
                Text("CLEAR ALL", fontSize = 10.sp, color = HighDensityErrorRed, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (logs.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No execution logs available", color = HighDensityOnSurfaceVariant, fontSize = 12.sp)
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(logs, key = { it.id }) { log ->
                    LogCard(log = log)
                }
            }
        }
    }
}

@Composable
private fun LogCard(log: ExecutionLog) {
    val statusColor = when (log.status) {
        "SUCCESS" -> HighDensitySuccessGreen
        "FAILED" -> HighDensityErrorRed
        else -> HighDensityOnSurfaceVariant
    }

    val df = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val timeStr = remember(log.timestamp) { df.format(Date(log.timestamp)) }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(statusColor)
            )

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "${log.profileName} • ${log.triggerType}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = HighDensityOnPrimaryContainer
                    )
                    Text(
                        text = timeStr,
                        fontSize = 9.sp,
                        color = HighDensityOnSurfaceVariant
                    )
                }

                if (!log.skippedReason.isNullOrEmpty()) {
                    Text(
                        text = "Reason: ${log.skippedReason}",
                        fontSize = 10.sp,
                        color = HighDensityOnSurfaceVariant
                    )
                } else if (!log.actionsResultJson.isNullOrEmpty()) {
                    Text(
                        text = "Actions: ${log.actionsResultJson}",
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        color = HighDensityOnSurfaceVariant,
                        maxLines = 1
                    )
                }
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(statusColor.copy(alpha = 0.15f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = log.status,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = statusColor
                )
            }
        }
    }
}

@Composable
private fun EventsTab(
    onFireEvent: (String) -> Unit,
    viewModel: AutoTaskViewModel
) {
    var selectedTrigger by remember { mutableStateOf("BATTERY") }
    var customPayloadJson by remember { mutableStateOf("{\n  \"level\": 12,\n  \"isCharging\": false\n}") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "EVENT DISPATCHER & SIMULATION",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = HighDensityOnSurface
        )

        Text(
            text = "Simulate broadcast events or fire manual triggers directly into AutoTaskEngine:",
            fontSize = 10.sp,
            color = HighDensityOnSurfaceVariant
        )

        val triggerTypes = listOf("BATTERY", "WIFI", "SMS", "SCREEN", "CALL", "TIME", "BOOT", "MANUAL")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            triggerTypes.take(4).forEach { type ->
                OutlinedButton(
                    onClick = {
                        selectedTrigger = type
                        customPayloadJson = when (type) {
                            "BATTERY" -> "{\n  \"level\": 12,\n  \"isCharging\": false\n}"
                            "WIFI" -> "{\n  \"ssid\": \"HomeNetwork\",\n  \"connected\": true\n}"
                            "SMS" -> "{\n  \"sender\": \"+15550199\",\n  \"smsBody\": \"URGENT alert test\"\n}"
                            "SCREEN" -> "{\n  \"state\": \"ON\"\n}"
                            else -> "{}"
                        }
                    },
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (selectedTrigger == type) HighDensityPrimaryContainer else Color.Transparent
                    ),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(4.dp),
                    modifier = Modifier.weight(1f).height(32.dp)
                ) {
                    Text(type, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            triggerTypes.drop(4).forEach { type ->
                OutlinedButton(
                    onClick = {
                        selectedTrigger = type
                        customPayloadJson = when (type) {
                            "CALL" -> "{\n  \"state\": \"RINGING\",\n  \"number\": \"+15550122\"\n}"
                            "TIME" -> "{\n  \"hour\": 22,\n  \"minute\": 0\n}"
                            "BOOT" -> "{\n  \"bootTimestamp\": ${System.currentTimeMillis()}\n}"
                            else -> "{}"
                        }
                    },
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (selectedTrigger == type) HighDensityPrimaryContainer else Color.Transparent
                    ),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(4.dp),
                    modifier = Modifier.weight(1f).height(32.dp)
                ) {
                    Text(type, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        OutlinedTextField(
            value = customPayloadJson,
            onValueChange = { customPayloadJson = it },
            label = { Text("Payload JSON ($selectedTrigger)", fontSize = 10.sp) },
            textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
            modifier = Modifier.fillMaxWidth().height(120.dp)
        )

        Button(
            onClick = {
                viewModel.executeApiTest(
                    method = "POST",
                    endpoint = "/v1/events",
                    body = "{\n  \"type\": \"$selectedTrigger\",\n  \"payload\": $customPayloadJson\n}"
                )
            },
            colors = ButtonDefaults.buttonColors(containerColor = HighDensityPrimary),
            modifier = Modifier.fillMaxWidth().testTag("dispatch_event_button")
        ) {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("DISPATCH $selectedTrigger EVENT", fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun StatusTab(
    isServiceRunning: Boolean,
    logs: List<ExecutionLog>,
    apiTestResponse: String,
    isApiTestLoading: Boolean,
    onExecuteApiTest: (String, String, String) -> Unit
) {
    var selectedMethod by remember { mutableStateOf("GET") }
    var selectedEndpoint by remember { mutableStateOf("/v1/status") }
    var requestBody by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text(
                text = "ON-DEVICE SERVER & PROVIDER STATUS",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = HighDensityOnSurface
            )
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Ktor Server: http://127.0.0.1:8788", fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    Text("ContentProvider: content://com.example.autotask.provider", fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    Text("Service Status: ${if (isServiceRunning) "Active (Foreground)" else "Stopped"}", fontSize = 10.sp)
                }
            }
        }

        item {
            Text(
                text = "INTERACTIVE REST API TESTER",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = HighDensityOnSurface
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf("GET", "POST", "PATCH", "DELETE").forEach { m ->
                    OutlinedButton(
                        onClick = { selectedMethod = m },
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (selectedMethod == m) HighDensityPrimaryContainer else Color.Transparent
                        ),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(2.dp),
                        modifier = Modifier.weight(1f).height(30.dp)
                    ) {
                        Text(m, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf("/v1/status", "/v1/profiles", "/v1/events", "/v1/logs", "/v1/schema").forEach { ep ->
                    OutlinedButton(
                        onClick = { selectedEndpoint = ep },
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (selectedEndpoint == ep) HighDensityPrimaryContainer else Color.Transparent
                        ),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(2.dp),
                        modifier = Modifier.weight(1f).height(30.dp)
                    ) {
                        Text(ep.removePrefix("/v1/"), fontSize = 9.sp)
                    }
                }
            }
        }

        if (selectedMethod == "POST" || selectedMethod == "PATCH") {
            item {
                OutlinedTextField(
                    value = requestBody,
                    onValueChange = { requestBody = it },
                    label = { Text("Request Body JSON", fontSize = 10.sp) },
                    textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 10.sp),
                    modifier = Modifier.fillMaxWidth().height(80.dp)
                )
            }
        }

        item {
            Button(
                onClick = { onExecuteApiTest(selectedMethod, selectedEndpoint, requestBody) },
                colors = ButtonDefaults.buttonColors(containerColor = HighDensityPrimary),
                modifier = Modifier.fillMaxWidth().testTag("execute_api_test_button")
            ) {
                if (isApiTestLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White)
                } else {
                    Text("SEND HTTP REQUEST", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (apiTestResponse.isNotEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("API Response Output:", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = apiTestResponse,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = HighDensityOnSurface
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AddProfileDialog(
    onDismiss: () -> Unit,
    onSave: (AutomationProfile) -> Unit
) {
    var id by remember { mutableStateOf("profile_${System.currentTimeMillis() % 10000}") }
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var triggerType by remember { mutableStateOf("BATTERY") }
    var triggerConfig by remember { mutableStateOf("{\n  \"level\": 15,\n  \"operator\": \"LESS_THAN\"\n}") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Automation Policy", fontSize = 14.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Policy Name") })
                OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Description") })
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("BATTERY", "WIFI", "SMS", "MANUAL").forEach { type ->
                        OutlinedButton(
                            onClick = { triggerType = type },
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (triggerType == type) HighDensityPrimaryContainer else Color.Transparent
                            ),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(2.dp),
                            modifier = Modifier.weight(1f).height(28.dp)
                        ) {
                            Text(type, fontSize = 8.sp)
                        }
                    }
                }
                OutlinedTextField(
                    value = triggerConfig,
                    onValueChange = { triggerConfig = it },
                    label = { Text("Trigger Config JSON") },
                    textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        val newProfile = AutomationProfile(
                            id = id,
                            name = name,
                            description = description,
                            isEnabled = true,
                            triggerType = triggerType,
                            triggerConfigJson = triggerConfig,
                            conditionsJson = "{}",
                            actionsJson = "[\n  {\n    \"type\": \"NOTIFICATION\",\n    \"params\": {\n      \"title\": \"AutoTask Alert\",\n      \"text\": \"Policy $name triggered!\"\n    }\n  }\n]",
                            createdAt = System.currentTimeMillis(),
                            updatedAt = System.currentTimeMillis()
                        )
                        onSave(newProfile)
                    }
                }
            ) {
                Text("SAVE POLICY")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("CANCEL") }
        }
    )
}

@Composable
private fun ProfileJsonDialog(
    profile: AutomationProfile,
    onDismiss: () -> Unit
) {
    val jsonStr = remember(profile) {
        val obj = org.json.JSONObject()
        obj.put("id", profile.id)
        obj.put("name", profile.name)
        obj.put("triggerType", profile.triggerType)
        obj.put("triggerConfig", try { org.json.JSONObject(profile.triggerConfigJson) } catch (e: Exception) { profile.triggerConfigJson })
        obj.put("actions", try { org.json.JSONArray(profile.actionsJson) } catch (e: Exception) { profile.actionsJson })
        obj.toString(2)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Policy Schema JSON", fontSize = 14.sp, fontWeight = FontWeight.Bold) },
        text = {
            Text(
                text = jsonStr,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                color = HighDensityOnSurface
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("CLOSE") }
        }
    )
}
