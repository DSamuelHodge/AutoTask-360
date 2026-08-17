package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SettingsSuggest
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.LaunchedEffect
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
import com.example.engine.SchemaProvider
import com.example.server.KtorServerSnapshot
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
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class CatalogueTriggerItem(
    val type: String,
    val source: String,
    val description: String,
    val state: String,
    val configKeysMap: Map<String, String>,
    val templateVars: List<String>,
    val category: String
)

data class CatalogueActionItem(
    val type: String,
    val description: String,
    val paramsMap: Map<String, String>,
    val notes: String,
    val category: String
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AutoTaskMainScreen(viewModel: AutoTaskViewModel) {
    val profiles by viewModel.profiles.collectAsState()
    val logs by viewModel.logs.collectAsState()
    val isServiceRunning by viewModel.isServiceRunning.collectAsState()
    val apiTestResponse by viewModel.apiTestResponse.collectAsState()
    val isApiTestLoading by viewModel.isApiTestLoading.collectAsState()
    val ktorServerConfig by viewModel.ktorServerConfig.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) } // 0: Policies, 1: Catalogue, 2: Logs, 3: Events, 4: Status
    var showAddDialog by remember { mutableStateOf(false) }
    var editingProfile by remember { mutableStateOf<AutomationProfile?>(null) }
    var preselectedTriggerForNewPolicy by remember { mutableStateOf<String?>(null) }
    var preselectedActionForNewPolicy by remember { mutableStateOf<String?>(null) }
    var selectedJsonProfile by remember { mutableStateOf<AutomationProfile?>(null) }

    // Parse Schema
    val (catalogueTriggers, catalogueActions) = remember { parseSchemaData() }

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = Color.White,
                tonalElevation = 4.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .height(72.dp)
                    .border(1.dp, HighDensitySurfaceVariant)
            ) {
                val tabs = listOf("POLICIES", "CATALOGUE", "LOGS", "EVENTS", "STATUS")
                val icons = listOf(
                    Icons.Default.SettingsSuggest,
                    Icons.Default.Category,
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
                                fontSize = 9.sp,
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
                serverBaseUrl = ktorServerConfig.baseUrl,
                onToggleService = { viewModel.toggleService(it) }
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Metric Overview Cards
            MetricsSection(
                activeProfilesCount = profiles.count { it.isEnabled },
                totalProfilesCount = profiles.size,
                triggersCount = catalogueTriggers.size,
                actionsCount = catalogueActions.size,
                logsCount = logs.size
            )

            Spacer(modifier = Modifier.height(10.dp))

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
                            onEditProfile = { profile -> editingProfile = profile },
                            onDeleteProfile = { id -> viewModel.deleteProfile(id) },
                            onShowJson = { profile -> selectedJsonProfile = profile },
                            onOpenAddDialog = {
                                preselectedTriggerForNewPolicy = null
                                preselectedActionForNewPolicy = null
                                editingProfile = null
                                showAddDialog = true
                            }
                        )

                        1 -> CatalogueTab(
                            triggers = catalogueTriggers,
                            actions = catalogueActions,
                            onCreatePolicyWithTrigger = { triggerType ->
                                preselectedTriggerForNewPolicy = triggerType
                                preselectedActionForNewPolicy = null
                                editingProfile = null
                                showAddDialog = true
                            },
                            onCreatePolicyWithAction = { actionType ->
                                preselectedTriggerForNewPolicy = null
                                preselectedActionForNewPolicy = actionType
                                editingProfile = null
                                showAddDialog = true
                            }
                        )

                        2 -> LogsTab(
                            logs = logs,
                            onClearLogs = { viewModel.clearLogs() }
                        )

                        3 -> EventsTab(
                            triggers = catalogueTriggers,
                            viewModel = viewModel
                        )

                        4 -> StatusTab(
                            isServiceRunning = isServiceRunning,
                            logs = logs,
                            serverConfig = ktorServerConfig,
                            apiTestResponse = apiTestResponse,
                            isApiTestLoading = isApiTestLoading,
                            onSetServerEnabled = { enabled -> viewModel.setKtorServerEnabled(enabled) },
                            onSetServerPort = { port -> viewModel.setKtorServerPort(port) },
                            onRestartServer = { viewModel.restartKtorServer() },
                            onResetServer = { viewModel.resetKtorServerConfig() },
                            onExecuteApiTest = { method, endpoint, body ->
                                viewModel.executeApiTest(method, endpoint, body)
                            }
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog || editingProfile != null) {
        PolicyEditorDialog(
            existingProfile = editingProfile,
            preselectedTrigger = preselectedTriggerForNewPolicy,
            preselectedAction = preselectedActionForNewPolicy,
            triggers = catalogueTriggers,
            actions = catalogueActions,
            onDismiss = {
                showAddDialog = false
                editingProfile = null
            },
            onSave = { updatedProf ->
                viewModel.upsertProfile(updatedProf)
                showAddDialog = false
                editingProfile = null
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

private fun parseSchemaData(): Pair<List<CatalogueTriggerItem>, List<CatalogueActionItem>> {
    val triggers = mutableListOf<CatalogueTriggerItem>()
    val actions = mutableListOf<CatalogueActionItem>()

    try {
        val root = JSONObject(SchemaProvider.getSchemaJson())

        val triggerObj = root.optJSONObject("triggerTypes") ?: JSONObject()
        val triggerKeys = triggerObj.keys()
        while (triggerKeys.hasNext()) {
            val key = triggerKeys.next()
            val t = triggerObj.getJSONObject(key)
            val source = t.optString("source", "System")
            val desc = t.optString("description", "")
            val state = t.optString("state", "delivery-ready")

            val cfgObj = t.optJSONObject("configKeys") ?: JSONObject()
            val cfgMap = mutableMapOf<String, String>()
            cfgObj.keys().forEach { k -> cfgMap[k] = cfgObj.optString(k) }

            val varsArr = t.optJSONArray("templateVars") ?: JSONArray()
            val varsList = mutableListOf<String>()
            for (i in 0 until varsArr.length()) {
                varsList.add(varsArr.getString(i))
            }

            val category = when (key) {
                "TIME", "SCHEDULE", "SUNRISE_SUNSET" -> "TIME/SCHEDULE"
                "BATTERY", "POWER", "POWER_SAVE" -> "POWER"
                "WIFI", "AIRPLANE_MODE", "MOBILE_DATA" -> "NETWORK"
                "BLUETOOTH", "BLUETOOTH_STATE" -> "BLUETOOTH"
                "SCREEN", "DEVICE_UNLOCKED", "DOZE", "DREAMING" -> "DEVICE"
                "APP_LAUNCH", "PACKAGE_CHANGED", "FOREGROUND_APP" -> "APP"
                "INCOMING_CALL", "OUTGOING_CALL", "SMS", "SIGNAL_STRENGTH", "CALL" -> "TELEPHONY"
                "NOTIFICATION", "NOTIFICATION_REMOVED" -> "NOTIFICATIONS"
                "LOCATION", "ACTIVITY_RECOGNITION" -> "LOCATION"
                "HEADSET", "USB", "VOLUME_BUTTON", "CAMERA_BUTTON", "NFC" -> "HARDWARE"
                "SHAKE", "PROXIMITY", "LIGHT", "STEP", "PRESSURE", "TEMPERATURE" -> "SENSORS"
                "CALENDAR_EVENT", "MEETING" -> "CALENDAR"
                else -> "SYSTEM"
            }

            triggers.add(CatalogueTriggerItem(key, source, desc, state, cfgMap, varsList, category))
        }

        val actionObj = root.optJSONObject("actionTypes") ?: JSONObject()
        val actionKeys = actionObj.keys()
        while (actionKeys.hasNext()) {
            val key = actionKeys.next()
            val a = actionObj.getJSONObject(key)
            val desc = a.optString("description", "")
            val notes = a.optString("notes", "")

            val pObj = a.optJSONObject("params") ?: JSONObject()
            val pMap = mutableMapOf<String, String>()
            pObj.keys().forEach { k -> pMap[k] = pObj.optString(k) }

            val category = when (key) {
                "AUDIO", "DND", "BRIGHTNESS", "SCREEN_TIMEOUT", "ROTATION", "POWER_SAVE" -> "DEVICE SETTINGS"
                "WIFI_ACTION", "BLUETOOTH_ACTION", "AIRPLANE_MODE_ACTION", "HOTSPOT", "NFC_ACTION" -> "CONNECTIVITY"
                "NOTIFICATION", "SPEAK", "TOAST", "VIBRATE" -> "ALERTS/MEDIA"
                "SEND_SMS", "CALL", "OPEN_URL" -> "COMMUNICATION"
                "LAUNCH_APP", "KILL_APP", "OPEN_SETTINGS" -> "APP CONTROL"
                "FLASHLIGHT", "CLIPBOARD", "CAMERA" -> "HARDWARE"
                "HTTP", "WRITE_FILE", "READ_FILE", "BROADCAST" -> "DATA/INTEGRATION"
                else -> "AUTOMATION"
            }

            actions.add(CatalogueActionItem(key, desc, pMap, notes, category))
        }

    } catch (e: Exception) {
        e.printStackTrace()
    }

    return Pair(triggers, actions)
}

@Composable
private fun HeaderSection(
    isRunning: Boolean,
    serverBaseUrl: String,
    onToggleService: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
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
                modifier = Modifier.padding(top = 1.dp)
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
                    text = serverBaseUrl.removePrefix("http://"),
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
    triggersCount: Int,
    actionsCount: Int,
    logsCount: Int
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = HighDensitySurfaceVariant),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .weight(1f)
                .height(68.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "ACTIVE POLICIES",
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = HighDensityOnSurfaceVariant
                )
                Text(
                    text = "$activeProfilesCount / $totalProfilesCount",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = HighDensityOnPrimaryContainer
                )
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = HighDensityPrimaryContainer),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .weight(1.2f)
                .height(68.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "CATALOGUE SCOPE",
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = HighDensityOnPrimaryContainer
                )
                Text(
                    text = "$triggersCount Triggers • $actionsCount Actions",
                    fontSize = 11.sp,
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
                .height(68.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "EXECUTION LOGS",
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = HighDensityOnSurfaceVariant
                )
                Text(
                    text = "$logsCount",
                    fontSize = 18.sp,
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
    onEditProfile: (AutomationProfile) -> Unit,
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
            Column {
                Text(
                    text = "AUTOMATION POLICIES",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = HighDensityOnSurface
                )
                Text(
                    text = "${profiles.size} configured policy profiles",
                    fontSize = 9.sp,
                    color = HighDensityOnSurfaceVariant
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(
                    onClick = { onFireManual(null) },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                    modifier = Modifier.height(32.dp).testTag("fire_all_button")
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("FIRE ALL", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = onOpenAddDialog,
                    colors = ButtonDefaults.buttonColors(containerColor = HighDensityPrimary),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                    modifier = Modifier.height(32.dp).testTag("add_policy_button")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("NEW POLICY", fontSize = 10.sp, fontWeight = FontWeight.Bold)
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
                        onEdit = { onEditProfile(profile) },
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
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onShowJson: () -> Unit
) {
    val triggerIcon: ImageVector = when (profile.triggerType) {
        "BATTERY", "POWER", "POWER_SAVE" -> Icons.Default.BatteryChargingFull
        "WIFI", "AIRPLANE_MODE" -> Icons.Default.Wifi
        "BLUETOOTH", "BLUETOOTH_STATE" -> Icons.Default.Bluetooth
        "SMS" -> Icons.Default.Sms
        "INCOMING_CALL", "CALL" -> Icons.Default.Call
        "SCREEN", "DEVICE_UNLOCKED" -> Icons.Default.Smartphone
        "HEADSET" -> Icons.Default.Headset
        "LIGHT" -> Icons.Default.Lightbulb
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
                    IconButton(onClick = onEdit, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit", tint = HighDensityPrimary, modifier = Modifier.size(16.dp))
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
private fun CatalogueTab(
    triggers: List<CatalogueTriggerItem>,
    actions: List<CatalogueActionItem>,
    onCreatePolicyWithTrigger: (String) -> Unit,
    onCreatePolicyWithAction: (String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedTypeFilter by remember { mutableStateOf("ALL") } // ALL, TRIGGERS, ACTIONS
    var selectedCategory by remember { mutableStateOf("ALL") }

    val categories = remember(triggers, actions) {
        val set = mutableSetOf("ALL")
        triggers.forEach { set.add(it.category) }
        actions.forEach { set.add(it.category) }
        set.toList().sorted()
    }

    val filteredTriggers = remember(triggers, searchQuery, selectedTypeFilter, selectedCategory) {
        if (selectedTypeFilter == "ACTIONS") emptyList()
        else triggers.filter {
            (selectedCategory == "ALL" || it.category == selectedCategory) &&
                    (searchQuery.isBlank() || it.type.contains(searchQuery, ignoreCase = true) ||
                            it.description.contains(searchQuery, ignoreCase = true) ||
                            it.source.contains(searchQuery, ignoreCase = true))
        }
    }

    val filteredActions = remember(actions, searchQuery, selectedTypeFilter, selectedCategory) {
        if (selectedTypeFilter == "TRIGGERS") emptyList()
        else actions.filter {
            (selectedCategory == "ALL" || it.category == selectedCategory) &&
                    (searchQuery.isBlank() || it.type.contains(searchQuery, ignoreCase = true) ||
                            it.description.contains(searchQuery, ignoreCase = true))
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "TRIGGER & ACTION CATALOGUE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = HighDensityOnSurface
                )
                Text(
                    text = "44 Trigger Types • 31 Action Types across System APIs",
                    fontSize = 9.sp,
                    color = HighDensityOnSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search triggers & actions...", fontSize = 11.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp)) },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp)
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Type Filter Pills
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            listOf("ALL (${triggers.size + actions.size})", "TRIGGERS (${triggers.size})", "ACTIONS (${actions.size})").forEach { label ->
                val typeKey = when {
                    label.startsWith("TRIGGERS") -> "TRIGGERS"
                    label.startsWith("ACTIONS") -> "ACTIONS"
                    else -> "ALL"
                }
                OutlinedButton(
                    onClick = { selectedTypeFilter = typeKey },
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (selectedTypeFilter == typeKey) HighDensityPrimaryContainer else Color.Transparent
                    ),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Text(label, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Category Filter Horizontal Row
        LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            items(categories) { cat ->
                OutlinedButton(
                    onClick = { selectedCategory = cat },
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (selectedCategory == cat) HighDensityPrimaryContainer else Color.Transparent
                    ),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.height(26.dp)
                ) {
                    Text(cat, fontSize = 8.5.sp, fontWeight = if (selectedCategory == cat) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            if (filteredTriggers.isNotEmpty()) {
                item {
                    Text(
                        text = "TRIGGERS (${filteredTriggers.size})",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = HighDensityPrimary
                    )
                }
                items(filteredTriggers, key = { "trig_${it.type}" }) { item ->
                    CatalogueTriggerCard(item = item, onCreatePolicy = { onCreatePolicyWithTrigger(item.type) })
                }
            }

            if (filteredActions.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "ACTIONS (${filteredActions.size})",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = HighDensityPrimary
                    )
                }
                items(filteredActions, key = { "act_${it.type}" }) { item ->
                    CatalogueActionCard(item = item, onCreatePolicy = { onCreatePolicyWithAction(item.type) })
                }
            }

            if (filteredTriggers.isEmpty() && filteredActions.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                        Text("No matching triggers or actions found", fontSize = 11.sp, color = HighDensityOnSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun CatalogueTriggerCard(
    item: CatalogueTriggerItem,
    onCreatePolicy: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, HighDensitySurfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(HighDensityPrimaryContainer)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text("TRIGGER", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = HighDensityOnPrimaryContainer)
                    }

                    Text(
                        text = item.type,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = HighDensityOnPrimaryContainer
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            when (item.state) {
                                "delivery-ready" -> HighDensitySuccessGreen.copy(alpha = 0.15f)
                                "policy-ready" -> HighDensityPrimaryContainer
                                else -> HighDensitySurfaceVariant
                            }
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = item.state,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = when (item.state) {
                            "delivery-ready" -> HighDensitySuccessGreen
                            else -> HighDensityOnSurfaceVariant
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(text = item.description, fontSize = 10.sp, color = HighDensityOnSurface)
            Text(text = "Source: ${item.source}", fontSize = 9.sp, color = HighDensityOnSurfaceVariant)

            if (item.configKeysMap.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Config Keys: ${item.configKeysMap.entries.joinToString { "${it.key}: ${it.value}" }}",
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    color = HighDensityOnSurfaceVariant
                )
            }

            if (item.templateVars.isNotEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Vars: ${item.templateVars.joinToString(", ")}",
                    fontSize = 8.5.sp,
                    fontFamily = FontFamily.Monospace,
                    color = HighDensityPrimary
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            OutlinedButton(
                onClick = onCreatePolicy,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                modifier = Modifier.height(26.dp).align(Alignment.End)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(12.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("CREATE POLICY WITH THIS", fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun CatalogueActionCard(
    item: CatalogueActionItem,
    onCreatePolicy: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, HighDensitySurfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFFE1BEE7))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text("ACTION", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4A148C))
                    }

                    Text(
                        text = item.type,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = HighDensityOnPrimaryContainer
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(HighDensitySurfaceVariant)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = item.category,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = HighDensityOnSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(text = item.description, fontSize = 10.sp, color = HighDensityOnSurface)

            if (item.paramsMap.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Params: ${item.paramsMap.entries.joinToString { "${it.key}: ${it.value}" }}",
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    color = HighDensityOnSurfaceVariant
                )
            }

            if (item.notes.isNotEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = "Note: ${item.notes}", fontSize = 8.5.sp, color = HighDensityErrorRed)
            }

            Spacer(modifier = Modifier.height(6.dp))

            OutlinedButton(
                onClick = onCreatePolicy,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                modifier = Modifier.height(26.dp).align(Alignment.End)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(12.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("CREATE POLICY WITH THIS", fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
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
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
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
        "SUCCESS", "OK", "INFO" -> HighDensitySuccessGreen
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
    triggers: List<CatalogueTriggerItem>,
    viewModel: AutoTaskViewModel
) {
    var selectedTrigger by remember { mutableStateOf("BATTERY") }
    var customPayloadJson by remember { mutableStateOf("{\n  \"level\": 12,\n  \"isCharging\": false\n}") }
    var showAllTriggersDropdown by remember { mutableStateOf(false) }

    val quickTriggers = listOf("BATTERY", "POWER_SAVE", "WIFI", "BLUETOOTH", "SCREEN", "HEADSET", "INCOMING_CALL", "SMS")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "EVENT DISPATCHER & SIMULATION",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = HighDensityOnSurface
        )

        Text(
            text = "Simulate broadcast events or fire manual triggers directly into the automation runtime for all 44 trigger types:",
            fontSize = 10.sp,
            color = HighDensityOnSurfaceVariant
        )

        // Quick Trigger Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            quickTriggers.take(4).forEach { type ->
                OutlinedButton(
                    onClick = {
                        selectedTrigger = type
                        customPayloadJson = getSamplePayloadForTrigger(type)
                    },
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (selectedTrigger == type) HighDensityPrimaryContainer else Color.Transparent
                    ),
                    contentPadding = PaddingValues(2.dp),
                    modifier = Modifier.weight(1f).height(30.dp)
                ) {
                    Text(type, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            quickTriggers.drop(4).forEach { type ->
                OutlinedButton(
                    onClick = {
                        selectedTrigger = type
                        customPayloadJson = getSamplePayloadForTrigger(type)
                    },
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (selectedTrigger == type) HighDensityPrimaryContainer else Color.Transparent
                    ),
                    contentPadding = PaddingValues(2.dp),
                    modifier = Modifier.weight(1f).height(30.dp)
                ) {
                    Text(type, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Dropdown for all 44 Triggers
        Box {
            OutlinedButton(
                onClick = { showAllTriggersDropdown = true },
                modifier = Modifier.fillMaxWidth().height(36.dp)
            ) {
                Text("SELECT FROM ALL 44 TRIGGERS (Current: $selectedTrigger)", fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }

            DropdownMenu(
                expanded = showAllTriggersDropdown,
                onDismissRequest = { showAllTriggersDropdown = false },
                modifier = Modifier.height(280.dp)
            ) {
                triggers.forEach { trig ->
                    DropdownMenuItem(
                        text = { Text("${trig.type} • ${trig.category}", fontSize = 10.sp) },
                        onClick = {
                            selectedTrigger = trig.type
                            customPayloadJson = getSamplePayloadForTrigger(trig.type)
                            showAllTriggersDropdown = false
                        }
                    )
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

private fun getSamplePayloadForTrigger(type: String): String {
    return when (type) {
        "BATTERY" -> "{\n  \"level\": 12,\n  \"isCharging\": false,\n  \"isLow\": true\n}"
        "POWER" -> "{\n  \"connected\": true\n}"
        "POWER_SAVE" -> "{\n  \"enabled\": true\n}"
        "WIFI" -> "{\n  \"ssid\": \"HomeNetwork\",\n  \"connected\": true\n}"
        "BLUETOOTH" -> "{\n  \"deviceName\": \"AirPods Pro\",\n  \"connected\": true\n}"
        "SMS" -> "{\n  \"sender\": \"+15550199\",\n  \"smsBody\": \"URGENT status report requested\"\n}"
        "INCOMING_CALL", "CALL" -> "{\n  \"state\": \"RINGING\",\n  \"number\": \"+15550122\"\n}"
        "SCREEN" -> "{\n  \"state\": \"ON\"\n}"
        "DEVICE_UNLOCKED" -> "{\n  \"timestamp\": ${System.currentTimeMillis()}\n}"
        "HEADSET" -> "{\n  \"connected\": true,\n  \"hasMicrophone\": true\n}"
        "LIGHT" -> "{\n  \"lux\": 8.5,\n  \"belowLux\": 10\n}"
        "TIME" -> "{\n  \"hour\": 22,\n  \"minute\": 0\n}"
        "BOOT" -> "{\n  \"timestamp\": ${System.currentTimeMillis()}\n}"
        else -> "{\n  \"active\": true,\n  \"state\": \"ON\"\n}"
    }
}

@Composable
private fun StatusTab(
    isServiceRunning: Boolean,
    logs: List<ExecutionLog>,
    serverConfig: KtorServerSnapshot,
    apiTestResponse: String,
    isApiTestLoading: Boolean,
    onSetServerEnabled: (Boolean) -> Unit,
    onSetServerPort: (String) -> Unit,
    onRestartServer: () -> Unit,
    onResetServer: () -> Unit,
    onExecuteApiTest: (String, String, String) -> Unit
) {
    var selectedMethod by remember { mutableStateOf("GET") }
    var selectedEndpoint by remember { mutableStateOf("/v1/status") }
    var requestBody by remember { mutableStateOf("") }
    var serverPortText by remember { mutableStateOf(serverConfig.port.toString()) }

    LaunchedEffect(serverConfig.port) {
        serverPortText = serverConfig.port.toString()
    }

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
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
                            Text("Ktor Server", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text(serverConfig.baseUrl, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        }
                        Switch(
                            checked = serverConfig.enabled,
                            onCheckedChange = onSetServerEnabled,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = HighDensityPrimary,
                                checkedTrackColor = HighDensityPrimaryContainer
                            )
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = serverPortText,
                            onValueChange = { value ->
                                serverPortText = value.filter { it.isDigit() }.take(5)
                            },
                            label = { Text("Port", fontSize = 10.sp) },
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = { onSetServerPort(serverPortText) },
                            colors = ButtonDefaults.buttonColors(containerColor = HighDensityPrimary),
                            contentPadding = PaddingValues(horizontal = 10.dp),
                            modifier = Modifier.height(48.dp)
                        ) {
                            Text("SET", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedButton(
                            onClick = onRestartServer,
                            modifier = Modifier.weight(1f).height(34.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Restart", modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("RESTART", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = onResetServer,
                            modifier = Modifier.weight(1f).height(34.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            Text("RESET 8788", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Text("Service Status: ${if (isServiceRunning) "Active (Foreground)" else "Stopped"}", fontSize = 10.sp)
                    Text("Ktor HTTP Server: ${if (serverConfig.isRunning) "Running" else "Stopped"}", fontSize = 10.sp)
                    Text("Bind: ${serverConfig.host} • LAN ${if (serverConfig.lanEnabled) "on" else "off"}", fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    Text("Listener Port: ${serverConfig.listenerPort} reserved", fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    Text("ContentProvider: content://com.example.autotask.provider", fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    Text("Last Result: ${serverConfig.lastResult}", fontSize = 10.sp)
                    if (serverConfig.lastError.isNotBlank()) {
                        Text("Last Error: ${serverConfig.lastError}", fontSize = 10.sp, color = HighDensityErrorRed)
                    }
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
                        contentPadding = PaddingValues(2.dp),
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
                listOf("/v1/status", "/v1/profiles", "/v1/events", "/v1/logs", "/v1/schema", "/v1/capabilities").forEach { ep ->
                    val label = if (ep == "/v1/capabilities") "caps" else ep.removePrefix("/v1/")
                    OutlinedButton(
                        onClick = { selectedEndpoint = ep },
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (selectedEndpoint == ep) HighDensityPrimaryContainer else Color.Transparent
                        ),
                        contentPadding = PaddingValues(2.dp),
                        modifier = Modifier.weight(1f).height(30.dp)
                    ) {
                        Text(label, fontSize = 9.sp, maxLines = 1)
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
private fun PolicyEditorDialog(
    existingProfile: AutomationProfile?,
    preselectedTrigger: String?,
    preselectedAction: String?,
    triggers: List<CatalogueTriggerItem>,
    actions: List<CatalogueActionItem>,
    onDismiss: () -> Unit,
    onSave: (AutomationProfile) -> Unit
) {
    var id by remember { mutableStateOf(existingProfile?.id ?: "policy_${System.currentTimeMillis() % 100000}") }
    var name by remember { mutableStateOf(existingProfile?.name ?: "") }
    var description by remember { mutableStateOf(existingProfile?.description ?: "") }
    var triggerType by remember { mutableStateOf(existingProfile?.triggerType ?: preselectedTrigger ?: "BATTERY") }
    var triggerConfig by remember {
        mutableStateOf(
            existingProfile?.triggerConfigJson ?: getSamplePayloadForTrigger(triggerType)
        )
    }

    var selectedActionType by remember { mutableStateOf(preselectedAction ?: "NOTIFICATION") }
    var actionsJson by remember {
        mutableStateOf(
            existingProfile?.actionsJson ?: getDefaultActionsJsonForAction(selectedActionType)
        )
    }

    var showTriggerDropdown by remember { mutableStateOf(false) }
    var showActionDropdown by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (existingProfile != null) "Edit Automation Policy" else "New Automation Policy",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.height(380.dp)) {
                item {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Policy Name", fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Description", fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    Text("TRIGGER TYPE (44 Options)", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = HighDensityPrimary)
                    Box {
                        OutlinedButton(
                            onClick = { showTriggerDropdown = true },
                            modifier = Modifier.fillMaxWidth().height(36.dp)
                        ) {
                            Text("Trigger: $triggerType", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }

                        DropdownMenu(
                            expanded = showTriggerDropdown,
                            onDismissRequest = { showTriggerDropdown = false },
                            modifier = Modifier.height(240.dp)
                        ) {
                            triggers.forEach { t ->
                                DropdownMenuItem(
                                    text = { Text("${t.type} (${t.category})", fontSize = 10.sp) },
                                    onClick = {
                                        triggerType = t.type
                                        triggerConfig = getSamplePayloadForTrigger(t.type)
                                        showTriggerDropdown = false
                                    }
                                )
                            }
                        }
                    }
                }

                item {
                    OutlinedTextField(
                        value = triggerConfig,
                        onValueChange = { triggerConfig = it },
                        label = { Text("Trigger Config JSON", fontSize = 10.sp) },
                        textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 10.sp),
                        modifier = Modifier.fillMaxWidth().height(90.dp)
                    )
                }

                item {
                    Text("ADD / SELECT ACTION TYPE (31 Options)", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = HighDensityPrimary)
                    Box {
                        OutlinedButton(
                            onClick = { showActionDropdown = true },
                            modifier = Modifier.fillMaxWidth().height(36.dp)
                        ) {
                            Text("Add Action Type: $selectedActionType", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }

                        DropdownMenu(
                            expanded = showActionDropdown,
                            onDismissRequest = { showActionDropdown = false },
                            modifier = Modifier.height(240.dp)
                        ) {
                            actions.forEach { a ->
                                DropdownMenuItem(
                                    text = { Text("${a.type} • ${a.description}", fontSize = 10.sp) },
                                    onClick = {
                                        selectedActionType = a.type
                                        actionsJson = getDefaultActionsJsonForAction(a.type)
                                        showActionDropdown = false
                                    }
                                )
                            }
                        }
                    }
                }

                item {
                    OutlinedTextField(
                        value = actionsJson,
                        onValueChange = { actionsJson = it },
                        label = { Text("Actions Array JSON", fontSize = 10.sp) },
                        textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 10.sp),
                        modifier = Modifier.fillMaxWidth().height(100.dp)
                    )
                }
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
                            isEnabled = existingProfile?.isEnabled ?: true,
                            triggerType = triggerType,
                            triggerConfigJson = triggerConfig,
                            conditionsJson = "{}",
                            actionsJson = actionsJson,
                            createdAt = existingProfile?.createdAt ?: System.currentTimeMillis(),
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

private fun getDefaultActionsJsonForAction(actionType: String): String {
    return when (actionType) {
        "SPEAK" -> "[\n  {\"type\":\"SPEAK\",\"params\":{\"text\":\"Alert: Policy triggered for {{triggerType}}.\"}}\n]"
        "TOAST" -> "[\n  {\"type\":\"TOAST\",\"params\":{\"text\":\"AutoTask: Policy triggered\",\"duration\":\"short\"}}\n]"
        "VIBRATE" -> "[\n  {\"type\":\"VIBRATE\",\"params\":{\"durationMs\":300}}\n]"
        "AUDIO" -> "[\n  {\"type\":\"AUDIO\",\"params\":{\"ringerMode\":\"silent\",\"stream\":\"ring\",\"volume\":0}}\n]"
        "DND" -> "[\n  {\"type\":\"DND\",\"params\":{\"enabled\":true,\"policy\":\"priority\"}}\n]"
        "BRIGHTNESS" -> "[\n  {\"type\":\"BRIGHTNESS\",\"params\":{\"level\":128,\"auto\":false}}\n]"
        "FLASHLIGHT" -> "[\n  {\"type\":\"FLASHLIGHT\",\"params\":{\"on\":true}}\n]"
        "HTTP" -> "[\n  {\"type\":\"HTTP\",\"params\":{\"url\":\"http://127.0.0.1:8788/v1/status\",\"method\":\"GET\"}}\n]"
        "LAUNCH_APP" -> "[\n  {\"type\":\"LAUNCH_APP\",\"params\":{\"packageName\":\"com.android.settings\"}}\n]"
        "OPEN_URL" -> "[\n  {\"type\":\"OPEN_URL\",\"params\":{\"url\":\"https://ai.studio\"}}\n]"
        "WRITE_FILE" -> "[\n  {\"type\":\"WRITE_FILE\",\"params\":{\"path\":\"autotask_log.txt\",\"content\":\"Event {{triggerType}} fired at {{timestamp}}\"}}\n]"
        "LOG" -> "[\n  {\"type\":\"LOG\",\"params\":{\"message\":\"Execution triggered for {{triggerType}}\",\"level\":\"INFO\"}}\n]"
        else -> "[\n  {\"type\":\"$actionType\",\"params\":{\"title\":\"AutoTask Alert\",\"text\":\"Policy triggered for {{triggerType}}\"}}\n]"
    }
}

@Composable
private fun ProfileJsonDialog(
    profile: AutomationProfile,
    onDismiss: () -> Unit
) {
    val jsonStr = remember(profile) {
        val obj = JSONObject()
        obj.put("id", profile.id)
        obj.put("name", profile.name)
        obj.put("triggerType", profile.triggerType)
        obj.put("triggerConfig", try { JSONObject(profile.triggerConfigJson) } catch (e: Exception) { profile.triggerConfigJson })
        obj.put("actions", try { JSONArray(profile.actionsJson) } catch (e: Exception) { profile.actionsJson })
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
