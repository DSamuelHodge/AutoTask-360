package com.example.provider

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import com.example.application.AutomationCommandFacade
import com.example.data.AutomationProfile
import com.example.engine.AutomationEvent
import com.example.server.KtorServerConfig
import kotlinx.coroutines.runBlocking
import org.json.JSONObject

class AutoTaskContentProvider : ContentProvider() {

    companion object {
        const val AUTHORITY = "com.example.autotask.provider"
        val CONTENT_URI_STATUS: Uri = Uri.parse("content://$AUTHORITY/status")
        val CONTENT_URI_PROFILES: Uri = Uri.parse("content://$AUTHORITY/profiles")
        val CONTENT_URI_EVENTS: Uri = Uri.parse("content://$AUTHORITY/events")
        val CONTENT_URI_LOGS: Uri = Uri.parse("content://$AUTHORITY/logs")

        private const val CODE_STATUS = 1
        private const val CODE_PROFILES = 2
        private const val CODE_PROFILE_ID = 3
        private const val CODE_EVENTS = 4
        private const val CODE_LOGS = 5

        private val uriMatcher = UriMatcher(UriMatcher.NO_MATCH).apply {
            addURI(AUTHORITY, "status", CODE_STATUS)
            addURI(AUTHORITY, "profiles", CODE_PROFILES)
            addURI(AUTHORITY, "profiles/*", CODE_PROFILE_ID)
            addURI(AUTHORITY, "events", CODE_EVENTS)
            addURI(AUTHORITY, "logs", CODE_LOGS)
        }
    }

    private lateinit var commands: AutomationCommandFacade

    override fun onCreate(): Boolean {
        val ctx = context ?: return false
        commands = AutomationCommandFacade.getInstance(ctx)
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        return when (uriMatcher.match(uri)) {
            CODE_STATUS -> {
                val cursor = MatrixCursor(arrayOf(
                    "engine_running",
                    "profile_count",
                    "log_count",
                    "command_url",
                    "ktor_server_enabled",
                    "ktor_server_host",
                    "ktor_server_port",
                    "ktor_server_running",
                    "listener_port",
                    "last_server_error",
                    "last_server_result",
                    "notification_policy_declared",
                    "notification_policy_granted",
                    "write_settings_granted",
                    "notification_listener_enabled",
                    "dnd_ready",
                    "device_settings_ready",
                    "uptime_ms",
                    "version"
                ))
                runBlocking {
                    val status = commands.statusMap()
                    val profileCount = status["profile_count"]
                    val logCount = status["log_count"]
                    val isRunning = if (commands.isRunning) 1 else 0
                    val serverConfig = KtorServerConfig.getSnapshot(requireNotNull(context))
                    cursor.addRow(arrayOf(
                        isRunning,
                        profileCount,
                        logCount,
                        serverConfig.baseUrl,
                        serverConfig.enabled,
                        serverConfig.host,
                        serverConfig.port,
                        serverConfig.isRunning,
                        serverConfig.listenerPort,
                        serverConfig.lastError,
                        serverConfig.lastResult,
                        status["notification_policy_declared"],
                        status["notification_policy_granted"],
                        status["write_settings_granted"],
                        status["notification_listener_enabled"],
                        status["dnd_ready"],
                        status["device_settings_ready"],
                        commands.uptimeMs(),
                        status["version"] ?: com.example.BuildConfig.VERSION_NAME
                    ))
                }
                cursor
            }

            CODE_PROFILES -> {
                val cursor = MatrixCursor(arrayOf(
                    "id", "name", "description", "isEnabled", "triggerType",
                    "triggerConfigJson", "conditionsJson", "actionsJson",
                    "cooldownMs", "priority", "createdAt", "updatedAt", "lastTriggeredAt",
                    "schemaVersion", "revision"
                ))
                runBlocking {
                    val profiles = commands.listProfiles()
                    profiles.forEach { p ->
                        cursor.addRow(arrayOf(
                            p.id, p.name, p.description, if (p.isEnabled) 1 else 0, p.triggerType,
                            p.triggerConfigJson, p.conditionsJson, p.actionsJson,
                            p.cooldownMs, p.priority, p.createdAt, p.updatedAt, p.lastTriggeredAt,
                            p.schemaVersion, p.revision
                        ))
                    }
                }
                cursor
            }

            CODE_PROFILE_ID -> {
                val id = uri.lastPathSegment ?: return null
                val cursor = MatrixCursor(arrayOf(
                    "id", "name", "description", "isEnabled", "triggerType",
                    "triggerConfigJson", "conditionsJson", "actionsJson",
                    "cooldownMs", "priority", "createdAt", "updatedAt", "lastTriggeredAt",
                    "schemaVersion", "revision"
                ))
                runBlocking {
                    val p = commands.getProfile(id)
                    if (p != null) {
                        cursor.addRow(arrayOf(
                            p.id, p.name, p.description, if (p.isEnabled) 1 else 0, p.triggerType,
                            p.triggerConfigJson, p.conditionsJson, p.actionsJson,
                            p.cooldownMs, p.priority, p.createdAt, p.updatedAt, p.lastTriggeredAt,
                            p.schemaVersion, p.revision
                        ))
                    }
                }
                cursor
            }

            CODE_LOGS -> {
                val limit = selectionArgs?.firstOrNull()?.toIntOrNull() ?: 100
                val cursor = MatrixCursor(arrayOf(
                    "id", "profileId", "profileName", "triggerType",
                    "status", "skippedReason", "actionsResultJson", "durationMs", "timestamp"
                ))
                runBlocking {
                    val logs = commands.listLogs(limit)
                    logs.forEach { l ->
                        cursor.addRow(arrayOf(
                            l.id, l.profileId, l.profileName, l.triggerType,
                            l.status, l.skippedReason, l.actionsResultJson, l.durationMs, l.timestamp
                        ))
                    }
                }
                cursor
            }

            else -> null
        }
    }

    override fun getType(uri: Uri): String? {
        return when (uriMatcher.match(uri)) {
            CODE_STATUS -> "vnd.android.cursor.item/vnd.$AUTHORITY.status"
            CODE_PROFILES -> "vnd.android.cursor.dir/vnd.$AUTHORITY.profiles"
            CODE_PROFILE_ID -> "vnd.android.cursor.item/vnd.$AUTHORITY.profiles"
            CODE_EVENTS -> "vnd.android.cursor.item/vnd.$AUTHORITY.events"
            CODE_LOGS -> "vnd.android.cursor.dir/vnd.$AUTHORITY.logs"
            else -> null
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        if (values == null) return null
        return when (uriMatcher.match(uri)) {
            CODE_PROFILES -> {
                val id = values.getAsString("id") ?: "profile_${System.currentTimeMillis()}"
                val now = System.currentTimeMillis()
                val profile = AutomationProfile(
                    id = id,
                    name = values.getAsString("name") ?: "New Automation Profile",
                    description = values.getAsString("description") ?: "",
                    isEnabled = values.getAsBoolean("isEnabled") ?: false,
                    triggerType = values.getAsString("triggerType") ?: "MANUAL",
                    triggerConfigJson = values.getAsString("triggerConfigJson") ?: "{}",
                    conditionsJson = values.getAsString("conditionsJson") ?: "{}",
                    actionsJson = values.getAsString("actionsJson") ?: "[]",
                    cooldownMs = values.getAsLong("cooldownMs") ?: 0L,
                    priority = values.getAsInteger("priority") ?: 0,
                    createdAt = values.getAsLong("createdAt") ?: now,
                    updatedAt = now
                )
                runBlocking {
                    commands.upsertProfile(profile)
                }
                Uri.withAppendedPath(CONTENT_URI_PROFILES, id)
            }

            CODE_EVENTS -> {
                val triggerType = values.getAsString("triggerType") ?: "MANUAL"
                val payloadJsonStr = values.getAsString("payloadJson") ?: "{}"
                val payloadMap = try {
                    val json = JSONObject(payloadJsonStr)
                    val map = mutableMapOf<String, Any?>()
                    json.keys().forEach { k ->
                        val normalizedKey = if (k == "profile_id") "profileId" else k
                        map[normalizedKey] = json.get(k)
                    }
                    map
                } catch (e: Exception) {
                    mutableMapOf<String, Any?>()
                }.toMutableMap()
                val targetProfileId = values.getAsString("profileId")
                    ?: values.getAsString("profile_id")
                    ?: payloadMap["profileId"]?.toString()
                if (!targetProfileId.isNullOrBlank()) {
                    payloadMap["profileId"] = targetProfileId
                }

                val event = AutomationEvent(type = triggerType, payload = payloadMap)
                val logs = runBlocking {
                    commands.processEvent(event)
                }
                val firstLogId = logs.firstOrNull()?.id ?: 0L
                Uri.withAppendedPath(CONTENT_URI_EVENTS, firstLogId.toString())
            }

            else -> null
        }
    }

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int {
        if (values == null) return 0
        return when (uriMatcher.match(uri)) {
            CODE_PROFILE_ID -> {
                val id = uri.lastPathSegment ?: return 0
                runBlocking {
                    val existing = commands.getProfile(id) ?: return@runBlocking 0
                    val updated = existing.copy(
                        name = values.getAsString("name") ?: existing.name,
                        description = values.getAsString("description") ?: existing.description,
                        isEnabled = values.getAsBoolean("isEnabled") ?: existing.isEnabled,
                        triggerType = values.getAsString("triggerType") ?: existing.triggerType,
                        triggerConfigJson = values.getAsString("triggerConfigJson") ?: existing.triggerConfigJson,
                        conditionsJson = values.getAsString("conditionsJson") ?: existing.conditionsJson,
                        actionsJson = values.getAsString("actionsJson") ?: existing.actionsJson,
                        cooldownMs = values.getAsLong("cooldownMs") ?: existing.cooldownMs,
                        priority = values.getAsInteger("priority") ?: existing.priority,
                        updatedAt = System.currentTimeMillis()
                    )
                    commands.updateProfile(updated)
                    1
                }
            }
            else -> 0
        }
    }

    override fun delete(
        uri: Uri,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int {
        return when (uriMatcher.match(uri)) {
            CODE_PROFILE_ID -> {
                val id = uri.lastPathSegment ?: return 0
                runBlocking {
                    if (commands.deleteProfile(id)) 1 else 0
                }
            }
            CODE_LOGS -> {
                runBlocking {
                    commands.clearLogs()
                }
            }
            else -> 0
        }
    }
}
