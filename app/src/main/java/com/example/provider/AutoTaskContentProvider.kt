package com.example.provider

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import com.example.data.AutomationProfile
import com.example.data.AutoTaskDatabase
import com.example.engine.AutoTaskEngine
import com.example.engine.AutomationEvent
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

    private lateinit var db: AutoTaskDatabase
    private lateinit var engine: AutoTaskEngine

    override fun onCreate(): Boolean {
        val ctx = context ?: return false
        db = AutoTaskDatabase.getInstance(ctx)
        engine = AutoTaskEngine.getInstance(ctx)
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
                    "relay_target",
                    "uptime_ms",
                    "version"
                ))
                runBlocking {
                    val profileCount = db.profileDao().getProfileCount()
                    val logCount = db.logDao().getLogCount()
                    val isRunning = if (engine.isRunning) 1 else 0
                    cursor.addRow(arrayOf(
                        isRunning,
                        profileCount,
                        logCount,
                        "http://127.0.0.1:8788",
                        engine.getUptimeMs(),
                        "1.0.0"
                    ))
                }
                cursor
            }

            CODE_PROFILES -> {
                val cursor = MatrixCursor(arrayOf(
                    "id", "name", "description", "isEnabled", "triggerType",
                    "triggerConfigJson", "conditionsJson", "actionsJson",
                    "cooldownMs", "priority", "createdAt", "updatedAt", "lastTriggeredAt"
                ))
                runBlocking {
                    val profiles = db.profileDao().getAllProfiles()
                    profiles.forEach { p ->
                        cursor.addRow(arrayOf(
                            p.id, p.name, p.description, if (p.isEnabled) 1 else 0, p.triggerType,
                            p.triggerConfigJson, p.conditionsJson, p.actionsJson,
                            p.cooldownMs, p.priority, p.createdAt, p.updatedAt, p.lastTriggeredAt
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
                    "cooldownMs", "priority", "createdAt", "updatedAt", "lastTriggeredAt"
                ))
                runBlocking {
                    val p = db.profileDao().getProfileById(id)
                    if (p != null) {
                        cursor.addRow(arrayOf(
                            p.id, p.name, p.description, if (p.isEnabled) 1 else 0, p.triggerType,
                            p.triggerConfigJson, p.conditionsJson, p.actionsJson,
                            p.cooldownMs, p.priority, p.createdAt, p.updatedAt, p.lastTriggeredAt
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
                    val logs = db.logDao().getLogs(limit)
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
                    db.profileDao().upsertProfile(profile)
                }
                Uri.withAppendedPath(CONTENT_URI_PROFILES, id)
            }

            CODE_EVENTS -> {
                val triggerType = values.getAsString("triggerType") ?: "MANUAL"
                val payloadJsonStr = values.getAsString("payloadJson") ?: "{}"
                val payloadMap = try {
                    val json = JSONObject(payloadJsonStr)
                    val map = mutableMapOf<String, Any?>()
                    json.keys().forEach { k -> map[k] = json.get(k) }
                    map
                } catch (e: Exception) {
                    emptyMap<String, Any?>()
                }

                val event = AutomationEvent(type = triggerType, payload = payloadMap)
                val logs = runBlocking {
                    engine.processEvent(event)
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
                    val existing = db.profileDao().getProfileById(id) ?: return@runBlocking 0
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
                    db.profileDao().updateProfile(updated)
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
                    db.profileDao().deleteProfileById(id)
                }
            }
            CODE_LOGS -> {
                runBlocking {
                    db.logDao().clearLogs()
                }
            }
            else -> 0
        }
    }
}
