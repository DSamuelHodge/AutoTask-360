package com.example.server

import android.content.Context
import com.example.data.AutomationProfile
import com.example.engine.AutoTaskEngine
import com.example.engine.AutomationEvent
import com.example.engine.CapabilityProvider
import com.example.engine.SchemaProvider
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.cio.CIO
import io.ktor.server.cio.CIOApplicationEngine
import okhttp3.MediaType.Companion.toMediaType
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.path
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import org.json.JSONArray
import org.json.JSONObject

class KtorLoopbackServer(
    private val context: Context,
    private val port: Int = 8788
) {
    private var serverEngine: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null
    private val autoTaskEngine = AutoTaskEngine.getInstance(context)

    fun start() {
        if (serverEngine != null) return

        serverEngine = embeddedServer(CIO, host = KtorServerConfig.HOST, port = port) {
            intercept(ApplicationCallPipeline.Plugins) {
                val path = call.request.path()
                if (path.startsWith("/v1/") && !KtorServerConfig.isLoopbackHost(call.request.local.remoteHost)) {
                    val expected = "Bearer ${com.example.wa.BrainService.getToken(this@KtorLoopbackServer.context)}"
                    if (call.request.headers["Authorization"] != expected) {
                        call.respondError(HttpStatusCode.Unauthorized, "Unauthorized: missing or invalid Bearer token")
                        finish()
                    }
                }
            }
            routing {
                // GET /v1/status
                get("/v1/status") {
                    val statusMap = autoTaskEngine.repository.getStatusMap()
                    val json = JSONObject()
                    json.put("engine_running", if (autoTaskEngine.isRunning) 1 else 0)
                    json.put("profile_count", statusMap["profile_count"])
                    json.put("log_count", statusMap["log_count"])
                    json.put("relay_target", "http://127.0.0.1:$port")
                    json.put("ktor_server_enabled", statusMap["ktor_server_enabled"])
                    json.put("ktor_server_host", statusMap["ktor_server_host"])
                    json.put("ktor_server_port", statusMap["ktor_server_port"])
                    json.put("ktor_server_running", statusMap["ktor_server_running"])
                    json.put("listener_port", statusMap["listener_port"])
                    json.put("last_server_error", statusMap["last_server_error"])
                    json.put("last_server_result", statusMap["last_server_result"])
                    json.put("notification_policy_declared", statusMap["notification_policy_declared"])
                    json.put("notification_policy_granted", statusMap["notification_policy_granted"])
                    json.put("write_settings_granted", statusMap["write_settings_granted"])
                    json.put("notification_listener_enabled", statusMap["notification_listener_enabled"])
                    json.put("dnd_ready", statusMap["dnd_ready"])
                    json.put("device_settings_ready", statusMap["device_settings_ready"])
                    val ready = JSONObject()
                    ready.put("api", autoTaskEngine.isRunning && statusMap["ktor_server_running"] == true)
                    ready.put("permissions", statusMap["dnd_ready"] == true && statusMap["device_settings_ready"] == true)
                    ready.put("dnd", statusMap["dnd_ready"])
                    ready.put("device_settings", statusMap["device_settings_ready"])
                    ready.put("notification_listener", statusMap["notification_listener_enabled"])
                    json.put("ready", ready)
                    json.put("provider_uri", "content://com.example.autotask.provider")
                    json.put("uptime_ms", autoTaskEngine.getUptimeMs())
                    json.put("version", "1.0.0")

                    call.respondJson(json.toString(2))
                }

                // GET /v1/schema
                get("/v1/schema") {
                    call.respondJson(SchemaProvider.getSchemaJson())
                }

                // GET /v1/capabilities
                get("/v1/capabilities") {
                    call.respondJson(CapabilityProvider.getCapabilitiesJson(context))
                }

                // GET /v1/profiles
                get("/v1/profiles") {
                    val profiles = autoTaskEngine.repository.profileDao.getAllProfiles()
                    val arr = JSONArray()
                    profiles.forEach { p ->
                        arr.put(profileToJson(p))
                    }
                    call.respondJson(arr.toString(2))
                }

                // GET /v1/profiles/{id}
                get("/v1/profiles/{id}") {
                    val id = call.parameters["id"] ?: ""
                    val p = autoTaskEngine.repository.getProfileById(id)
                    if (p != null) {
                        call.respondJson(profileToJson(p).toString(2))
                    } else {
                        call.respondError(HttpStatusCode.NotFound, "Profile not found: $id")
                    }
                }

                // POST /v1/profiles (Create / Upsert)
                post("/v1/profiles") {
                    val bodyText = call.receiveText()
                    try {
                        val json = JSONObject(bodyText)
                        val id = json.optString("id", "")
                        val name = json.optString("name", "")
                        val triggerType = json.optString("triggerType", "")

                        // Input validation
                        if (id.isBlank()) {
                            call.respondError(HttpStatusCode.BadRequest, "Field 'id' is required")
                            return@post
                        }
                        if (name.isBlank()) {
                            call.respondError(HttpStatusCode.BadRequest, "Field 'name' is required")
                            return@post
                        }
                        if (triggerType.isBlank()) {
                            call.respondError(HttpStatusCode.BadRequest, "Field 'triggerType' is required")
                            return@post
                        }

                        val now = System.currentTimeMillis()
                        val profile = AutomationProfile(
                            id = id,
                            name = name,
                            description = json.optString("description", ""),
                            isEnabled = json.optBoolean("isEnabled", false),
                            triggerType = triggerType.uppercase(),
                            triggerConfigJson = json.opt("triggerConfigJson")?.toString() ?: "{}",
                            conditionsJson = json.opt("conditionsJson")?.toString() ?: "{}",
                            actionsJson = json.opt("actionsJson")?.toString() ?: "[]",
                            cooldownMs = json.optLong("cooldownMs", 0L),
                            priority = json.optInt("priority", 0),
                            createdAt = json.optLong("createdAt", now),
                            updatedAt = now
                        )

                        autoTaskEngine.repository.upsertProfile(profile)

                        val resp = JSONObject()
                        resp.put("status", "OK")
                        resp.put("message", "Profile upserted successfully")
                        resp.put("profile", profileToJson(profile))
                        call.respondJson(resp.toString(2), HttpStatusCode.Created)
                    } catch (e: Exception) {
                        call.respondError(HttpStatusCode.BadRequest, "Invalid JSON body: ${e.localizedMessage}")
                    }
                }

                // PATCH /v1/profiles/{id}
                patch("/v1/profiles/{id}") {
                    val id = call.parameters["id"] ?: ""
                    val bodyText = call.receiveText()
                    val existing = autoTaskEngine.repository.getProfileById(id)
                    if (existing == null) {
                        call.respondError(HttpStatusCode.NotFound, "Profile not found: $id")
                        return@patch
                    }

                    try {
                        val json = JSONObject(bodyText)
                        val updated = existing.copy(
                            name = if (json.has("name")) json.getString("name") else existing.name,
                            description = if (json.has("description")) json.getString("description") else existing.description,
                            isEnabled = if (json.has("isEnabled")) json.getBoolean("isEnabled") else existing.isEnabled,
                            triggerType = if (json.has("triggerType")) json.getString("triggerType").uppercase() else existing.triggerType,
                            triggerConfigJson = if (json.has("triggerConfigJson")) json.opt("triggerConfigJson")?.toString() ?: "{}" else existing.triggerConfigJson,
                            conditionsJson = if (json.has("conditionsJson")) json.opt("conditionsJson")?.toString() ?: "{}" else existing.conditionsJson,
                            actionsJson = if (json.has("actionsJson")) json.opt("actionsJson")?.toString() ?: "[]" else existing.actionsJson,
                            cooldownMs = if (json.has("cooldownMs")) json.getLong("cooldownMs") else existing.cooldownMs,
                            priority = if (json.has("priority")) json.getInt("priority") else existing.priority,
                            updatedAt = System.currentTimeMillis()
                        )

                        autoTaskEngine.repository.updateProfile(updated)

                        val resp = JSONObject()
                        resp.put("status", "OK")
                        resp.put("message", "Profile patched")
                        resp.put("profile", profileToJson(updated))
                        call.respondJson(resp.toString(2))
                    } catch (e: Exception) {
                        call.respondError(HttpStatusCode.BadRequest, "Invalid JSON patch: ${e.localizedMessage}")
                    }
                }

                // DELETE /v1/profiles/{id}
                delete("/v1/profiles/{id}") {
                    val id = call.parameters["id"] ?: ""
                    val deleted = autoTaskEngine.repository.deleteProfileById(id)
                    if (deleted) {
                        val resp = JSONObject()
                        resp.put("status", "OK")
                        resp.put("deletedProfileId", id)
                        call.respondJson(resp.toString(2))
                    } else {
                        call.respondError(HttpStatusCode.NotFound, "Profile not found: $id")
                    }
                }

                // POST /v1/events (Fire manual / test event)
                post("/v1/events") {
                    val bodyText = call.receiveText()
                    try {
                        val json = if (bodyText.isBlank()) JSONObject() else JSONObject(bodyText)
                        val request = EventRequestParser.parse(json)
                        val triggerType = request.triggerType

                        val targetProfile = if (triggerType == "MANUAL" && request.targetProfileId != null) {
                            autoTaskEngine.repository.getProfileById(request.targetProfileId)
                        } else {
                            null
                        }
                        if (triggerType == "MANUAL" && request.targetProfileId != null && targetProfile == null) {
                            call.respondError(HttpStatusCode.NotFound, "Profile not found: ${request.targetProfileId}")
                            return@post
                        }

                        if (request.dryRun) {
                            val plannedProfiles = if (targetProfile != null) {
                                listOf(targetProfile)
                            } else {
                                autoTaskEngine.repository.profileDao.getEnabledProfilesForTrigger(triggerType)
                            }
                            val resp = JSONObject()
                            resp.put("status", "OK")
                            resp.put("dryRun", true)
                            resp.put("triggerType", triggerType)
                            resp.put("targetProfileId", request.targetProfileId ?: JSONObject.NULL)
                            resp.put("profilesMatched", plannedProfiles.size)
                            resp.put("logsGenerated", 0)
                            val profilesArray = JSONArray()
                            plannedProfiles.forEach { p ->
                                val pObj = JSONObject()
                                pObj.put("id", p.id)
                                pObj.put("name", p.name)
                                pObj.put("triggerType", p.triggerType)
                                pObj.put("isEnabled", p.isEnabled)
                                profilesArray.put(pObj)
                            }
                            resp.put("plannedProfiles", profilesArray)
                            call.respondJson(resp.toString(2))
                            return@post
                        }

                        val event = AutomationEvent(type = triggerType, payload = request.payload)
                        val logs = autoTaskEngine.processEvent(event)

                        val resp = JSONObject()
                        resp.put("status", "OK")
                        resp.put("dryRun", false)
                        resp.put("triggerType", triggerType)
                        resp.put("targetProfileId", request.targetProfileId ?: JSONObject.NULL)
                        resp.put("logsGenerated", logs.size)

                        val logsArray = JSONArray()
                        logs.forEach { l ->
                            val lObj = JSONObject()
                            lObj.put("id", l.id)
                            lObj.put("profileId", l.profileId)
                            lObj.put("profileName", l.profileName)
                            lObj.put("status", l.status)
                            lObj.put("skippedReason", l.skippedReason)
                            lObj.put("durationMs", l.durationMs)
                            logsArray.put(lObj)
                        }
                        resp.put("results", logsArray)

                        call.respondJson(resp.toString(2))
                    } catch (e: Exception) {
                        call.respondError(HttpStatusCode.BadRequest, "Invalid event request: ${e.localizedMessage}")
                    }
                }

                // GET /v1/logs?limit=N
                get("/v1/logs") {
                    val limitParam = call.request.queryParameters["limit"]?.toIntOrNull() ?: 100
                    val logs = autoTaskEngine.repository.logDao.getLogs(limitParam)

                    val arr = JSONArray()
                    logs.forEach { l ->
                        val obj = JSONObject()
                        obj.put("id", l.id)
                        obj.put("profileId", l.profileId)
                        obj.put("profileName", l.profileName)
                        obj.put("triggerType", l.triggerType)
                        obj.put("status", l.status)
                        obj.put("skippedReason", l.skippedReason)
                        obj.put("actionsResultJson", try { JSONArray(l.actionsResultJson) } catch (e: Exception) { l.actionsResultJson })
                        obj.put("durationMs", l.durationMs)
                        obj.put("timestamp", l.timestamp)
                        arr.put(obj)
                    }
                    call.respondJson(arr.toString(2))
                }

                // DELETE /v1/logs
                delete("/v1/logs") {
                    autoTaskEngine.repository.clearLogs()
                    val resp = JSONObject()
                    resp.put("status", "OK")
                    resp.put("message", "Execution logs cleared")
                    call.respondJson(resp.toString(2))
                }

                // GET /v1/brain/status — CoS brain health (UNIX socket).
                get("/v1/brain/status") {
                    val resp = JSONObject()
                    resp.put("brain_running", com.example.wa.BrainClient.ping(context))
                    resp.put("binary", com.example.wa.BrainService.binaryPath(context))
                    resp.put("sock", com.example.wa.BrainService.sockPath(context))
                    resp.put("db", com.example.wa.BrainService.dbPath(context))
                    resp.put("last_error", com.example.wa.BrainService.lastError ?: JSONObject.NULL)
                    resp.put("supervisor", com.example.wa.BrainService.statusJson())
                    resp.put("health", com.example.wa.HealthMonitor.statusJson())
                    call.respondJson(resp.toString(2))
                }

                // POST /v1/brain — proxy an RPC to the brain over its UNIX socket.
                post("/v1/brain") {
                    val bodyText = call.receiveText()
                    try {
                        val body = com.example.wa.BrainClient.call(context, bodyText)
                        call.respondText(body, ContentType.Application.Json)
                    } catch (e: Exception) {
                        val resp = JSONObject()
                        resp.put("ok", false)
                        resp.put("error", "brain unreachable: ${e.message}")
                        call.respondJson(resp.toString(2))
                    }
                }

                // POST /v1/http — outbound HTTP proxy for the brain (it has no
                // network stack/curl; the engine's OkHttp does TLS).
                post("/v1/http") {
                    val bodyText = call.receiveText()
                    try {
                        val req = JSONObject(bodyText)
                        val url = req.optString("url", "")
                        val method = req.optString("method", "GET").uppercase()
                        val headers = req.optJSONObject("headers") ?: JSONObject()
                        if (url.isBlank()) {
                            call.respondError(HttpStatusCode.BadRequest, "url is required")
                            return@post
                        }
                        val bodyData = if (req.has("data")) req.get("data").toString() else null
                        val builder = okhttp3.Request.Builder().url(url)
                        for (k in headers.keys()) builder.header(k, headers.getString(k))
                        val jsonType = "application/json; charset=utf-8".toMediaType()
                        val httpBody = when {
                            bodyData != null -> okhttp3.RequestBody.create(jsonType, bodyData)
                            method == "POST" -> okhttp3.RequestBody.create(jsonType, "{}")
                            else -> null
                        }
                        val request = if (method == "POST" && httpBody != null) {
                            builder.post(httpBody).build()
                        } else {
                            builder.build()
                        }
                        val client = okhttp3.OkHttpClient().newBuilder()
                            .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                            .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                            .build()
                        client.newCall(request).execute().use { resp ->
                            val body = resp.body?.string() ?: ""
                            call.respondText(body, ContentType.Application.Json, HttpStatusCode(resp.code, "response"))
                        }
                    } catch (e: Exception) {
                        val resp = JSONObject()
                        resp.put("ok", false)
                        resp.put("error", "http proxy failed: ${e.message}")
                        call.respondJson(resp.toString(2))
                    }
                }

                // POST /v1/contacts — the device address book for the brain's
                // sync_contacts / mention-resolution (the daemon has no
                // ContactsContract access; the engine does via READ_CONTACTS;
                // the daemon reads it with a POST).
                post("/v1/contacts") {
                    val resp = JSONObject()
                    try {
                        val ok = androidx.core.content.ContextCompat.checkSelfPermission(
                            context, android.Manifest.permission.READ_CONTACTS
                        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                        if (!ok) {
                            resp.put("ok", false)
                            resp.put("error", "READ_CONTACTS not granted")
                            call.respondJson(resp.toString(2))
                            return@post
                        }
                        val arr = JSONArray()
                        val resolver = context.contentResolver
                        val projection = arrayOf(
                            android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                            android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER,
                        )
                        resolver.query(
                            android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                            projection, null, null, null
                        )?.use { cursor ->
                            val nameIdx = cursor.getColumnIndex(android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                            val numIdx = cursor.getColumnIndex(android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER)
                            while (cursor.moveToNext()) {
                                val name = if (nameIdx >= 0) cursor.getString(nameIdx) ?: "" else ""
                                val number = if (numIdx >= 0) cursor.getString(numIdx) ?: "" else ""
                                if (name.isNotBlank() && number.isNotBlank()) {
                                    arr.put(JSONObject().put("name", name).put("number", number))
                                }
                            }
                        }
                        resp.put("ok", true)
                        resp.put("contacts", arr)
                        resp.put("count", arr.length())
                    } catch (e: Exception) {
                        resp.put("ok", false)
                        resp.put("error", e.message)
                    }
                    call.respondJson(resp.toString(2))
                }

                // POST /v1/location — last known GPS fix for the brain's
                // travel/commute inference (the daemon has no location APIs;
                // the engine does via its granted ACCESS_FINE_LOCATION; the
                // daemon reads it with a POST).
                post("/v1/location") {
                    val resp = JSONObject()
                    try {
                        val lm = context.getSystemService(android.content.Context.LOCATION_SERVICE) as android.location.LocationManager
                        val providers = mutableListOf(android.location.LocationManager.NETWORK_PROVIDER)
                        if (lm.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)) {
                            providers.add(0, android.location.LocationManager.GPS_PROVIDER)
                        }
                        val loc = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            requestSingleUpdate(lm, providers)
                        }
                        if (loc != null) {
                            resp.put("ok", true)
                            resp.put("latitude", loc.latitude)
                            resp.put("longitude", loc.longitude)
                            resp.put("accuracy", loc.accuracy)
                            resp.put("time", loc.time)
                            resp.put("provider", loc.provider)
                        } else {
                            resp.put("ok", false)
                            resp.put("error", "no location fix (single update timed out)")
                        }
                    } catch (e: Exception) {
                        resp.put("ok", false)
                        resp.put("error", e.message)
                    }
                    call.respondJson(resp.toString(2))
                }

                // GET /v1/screen — what's on screen right now (accessibility
                // "eyes"). Walks the active window's accessibility tree.
                get("/v1/screen") {
                    val enabled = com.example.accessibility.CoSAccessibilityService.isEnabled(context)
                    val resp = JSONObject()
                    resp.put("enabled", enabled)
                    resp.put("bound", com.example.accessibility.CoSAccessibilityService.isBound)
                    resp.put("last_event_pkg", com.example.accessibility.CoSAccessibilityService.lastEventPkg ?: JSONObject.NULL)
                    resp.put("last_event_text", com.example.accessibility.CoSAccessibilityService.lastEventText ?: JSONObject.NULL)
                    if (!enabled) {
                        resp.put("ok", false)
                        resp.put("error", "Accessibility not granted. Enable CoS Screen Access in Settings.")
                        call.respondJson(resp.toString(2))
                        return@get
                    }
                    resp.put("screen", com.example.accessibility.CoSAccessibilityService.screenDump())
                    call.respondJson(resp.toString(2))
                }

                // POST /v1/ui/tap — synthesize a tap at screen coords (accessibility "hands").
                post("/v1/ui/tap") {
                    val bodyText = call.receiveText()
                    try {
                        val json = JSONObject(bodyText)
                        val x = json.getDouble("x").toFloat()
                        val y = json.getDouble("y").toFloat()
                        call.respondJson(com.example.accessibility.CoSAccessibilityService.tap(x, y).toString(2))
                    } catch (e: Exception) {
                        call.respondError(HttpStatusCode.BadRequest, "tap requires x and y: ${e.message}")
                    }
                }

                // POST /v1/ui/type — set text into the focused editable field.
                post("/v1/ui/type") {
                    val bodyText = call.receiveText()
                    try {
                        val json = JSONObject(bodyText)
                        val text = json.getString("text")
                        call.respondJson(com.example.accessibility.CoSAccessibilityService.type(text).toString(2))
                    } catch (e: Exception) {
                        call.respondError(HttpStatusCode.BadRequest, "type requires text: ${e.message}")
                    }
                }

                // POST /v1/ui/global — back/home/recents/notifications/quick_settings.
                post("/v1/ui/global") {
                    val bodyText = call.receiveText()
                    try {
                        val json = JSONObject(bodyText)
                        val name = json.getString("action").lowercase()
                        val action = when (name) {
                            "back" -> android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK
                            "home" -> android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME
                            "recents" -> android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_RECENTS
                            "notifications" -> android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS
                            "quick_settings" -> android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS
                            else -> { call.respondError(HttpStatusCode.BadRequest, "unknown action: $name"); return@post }
                        }
                        call.respondJson(com.example.accessibility.CoSAccessibilityService.global(action).toString(2))
                    } catch (e: Exception) {
                        call.respondError(HttpStatusCode.BadRequest, "global requires action: ${e.message}")
                    }
                }

                // POST /v1/ota/config — set the update URL (persisted).
                post("/v1/ota/config") {
                    val bodyText = call.receiveText()
                    try {
                        val json = JSONObject(bodyText)
                        val url = json.optString("updateUrl", "")
                        if (url.isBlank()) {
                            call.respondError(HttpStatusCode.BadRequest, "updateUrl is required")
                            return@post
                        }
                        com.example.ota.OtaUpdater.setUpdateUrl(context, url)
                        val resp = JSONObject()
                        resp.put("status", "OK")
                        resp.put("update_url", url)
                        call.respondJson(resp.toString(2))
                    } catch (e: Exception) {
                        call.respondError(HttpStatusCode.BadRequest, "Invalid JSON body: ${e.localizedMessage}")
                    }
                }

                // GET /v1/ota/status — current version + update readiness.
                get("/v1/ota/status") {
                    val resp = JSONObject()
                    resp.put("version_code", com.example.BuildConfig.VERSION_CODE)
                    resp.put("version_name", com.example.BuildConfig.VERSION_NAME)
                    resp.put("update_url", com.example.ota.OtaUpdater.getUpdateUrl(context))
                    resp.put("can_request_install", com.example.ota.OtaUpdater.canRequestInstall(context))
                    call.respondJson(resp.toString(2))
                }

                // POST /v1/ota/check — fetch the manifest and compare versions.
                post("/v1/ota/check") {
                    val bodyText = call.receiveText()
                    val updateUrl = try {
                        JSONObject(bodyText).optString("updateUrl", "").takeIf { it.isNotBlank() }
                    } catch (_: Exception) { null }
                    val resp = com.example.ota.OtaUpdater.check(context, updateUrl)
                    val status = if (resp.optBoolean("ok", false)) HttpStatusCode.OK else HttpStatusCode.BadGateway
                    call.respondJson(resp.toString(2), status)
                }

                // POST /v1/ota/install — download, verify (sha256 + signing cert), install.
                post("/v1/ota/install") {
                    val bodyText = call.receiveText()
                    val updateUrl = try {
                        JSONObject(bodyText).optString("updateUrl", "").takeIf { it.isNotBlank() }
                    } catch (_: Exception) { null }
                    if (!com.example.ota.OtaUpdater.canRequestInstall(context)) {
                        call.respondError(HttpStatusCode.Forbidden, "Unknown-source installs not allowed for this app")
                        return@post
                    }
                    try {
                        val resp = com.example.ota.OtaUpdater.install(context, updateUrl)
                        call.respondJson(resp.toString(2))
                    } catch (e: Exception) {
                        call.respondError(HttpStatusCode.InternalServerError, "OTA install failed: ${e.message}")
                    }
                }

                // POST /mcp — stateless MCP endpoint (protocol 2026-07-28).
                // One JSON-RPC request per HTTP request; no sessions.
                post("/mcp") {
                    // Bearer-token auth mirroring the brain's UNIX socket: the
                    // MCP surface is app-level, so it uses the same shared token.
                    val auth = call.request.headers["Authorization"]
                    val expected = "Bearer ${com.example.wa.BrainService.getToken(context)}"
                    if (auth != expected) {
                        call.respondText(
                            com.example.mcp.McpHandler.error("Unauthorized: missing or invalid Bearer token").toString(),
                            io.ktor.http.ContentType.Application.Json,
                            HttpStatusCode.Unauthorized
                        )
                        return@post
                    }
                    // Origin check against DNS-rebinding (loopback server).
                    val origin = call.request.headers["Origin"]
                    if (origin != null && origin.isNotBlank()) {
                        val okOrigin = origin == "http://127.0.0.1:8788" || origin == "http://localhost:8788"
                        if (!okOrigin) {
                            call.respondText(
                                com.example.mcp.McpHandler.error("Origin not allowed").toString(),
                                io.ktor.http.ContentType.Application.Json,
                                HttpStatusCode.Forbidden
                            )
                            return@post
                        }
                    }
                    val bodyText = call.receiveText()
                    val headers = mutableMapOf<String, String>()
                    for (k in call.request.headers.names()) {
                        headers[k] = call.request.headers[k] ?: ""
                    }
                    val result = com.example.mcp.McpHandler.handle(context, headers, bodyText)
                    val status = when (result.status) {
                        400 -> HttpStatusCode.BadRequest
                        403 -> HttpStatusCode.Forbidden
                        404 -> HttpStatusCode.NotFound
                        else -> HttpStatusCode.OK
                    }
                    call.respondText(
                        result.json.toString(),
                        io.ktor.http.ContentType.Application.Json,
                        status
                    )
                }

                // GET /v1/wa/status — WhatsApp Web bridge state.
                get("/v1/wa/status") {
                    val resp = JSONObject()
                    resp.put("bridge_running", com.example.wa.WhatsAppBridgeManager.webView != null)
                    resp.put("paired", com.example.wa.WhatsAppBridgeManager.isPaired)
                    resp.put("last_error", com.example.wa.WhatsAppBridgeManager.lastError ?: JSONObject.NULL)
                    resp.put("last_send_result", com.example.wa.WhatsAppBridgeManager.lastSendResult ?: JSONObject.NULL)
                    resp.put("last_debug", com.example.wa.WhatsAppBridgeManager.lastDebug ?: JSONObject.NULL)
                    call.respondJson(resp.toString(2))
                }

                // POST /v1/wa/debug — run the DOM probe.
                post("/v1/wa/debug") {
                    com.example.wa.WhatsAppBridgeManager.probeDom()
                    val resp = JSONObject()
                    resp.put("status", "OK")
                    resp.put("message", "probe dispatched")
                    call.respondJson(resp.toString(2))
                }

                // POST /v1/wa/send — send a WhatsApp message via the bridge.
                post("/v1/wa/send") {
                    val bodyText = call.receiveText()
                    try {
                        val json = JSONObject(bodyText)
                        val phone = json.optString("phone", "")
                        val text = json.optString("text", "")
                        if (phone.isBlank() || text.isBlank()) {
                            call.respondError(HttpStatusCode.BadRequest, "phone and text are required")
                            return@post
                        }
                        val ok = com.example.wa.WhatsAppBridgeManager.sendMessage(phone, text)
                        val resp = JSONObject()
                        resp.put("status", if (ok) "OK" else "NOT_READY")
                        resp.put("paired", com.example.wa.WhatsAppBridgeManager.isPaired)
                        resp.put("message", if (ok) "send dispatched" else "bridge not paired")
                        call.respondJson(resp.toString(2))
                    } catch (e: Exception) {
                        call.respondError(HttpStatusCode.BadRequest, "Invalid JSON body: ${e.localizedMessage}")
                    }
                }
            }
        }
        serverEngine?.start(wait = false)
    }

    fun stop() {
        serverEngine?.stop(1000, 2000)
        serverEngine = null
    }

    private fun profileToJson(p: AutomationProfile): JSONObject {
        val obj = JSONObject()
        obj.put("id", p.id)
        obj.put("name", p.name)
        obj.put("description", p.description)
        obj.put("isEnabled", p.isEnabled)
        obj.put("triggerType", p.triggerType)
        obj.put("triggerConfigJson", try { JSONObject(p.triggerConfigJson) } catch (e: Exception) { p.triggerConfigJson })
        obj.put("conditionsJson", try { JSONObject(p.conditionsJson) } catch (e: Exception) { p.conditionsJson })
        obj.put("actionsJson", try { JSONArray(p.actionsJson) } catch (e: Exception) { p.actionsJson })
        obj.put("cooldownMs", p.cooldownMs)
        obj.put("priority", p.priority)
        obj.put("createdAt", p.createdAt)
        obj.put("updatedAt", p.updatedAt)
        obj.put("lastTriggeredAt", p.lastTriggeredAt)
        return obj
    }

    private suspend fun ApplicationCall.respondJson(text: String, status: HttpStatusCode = HttpStatusCode.OK) {
        respondText(text, ContentType.Application.Json, status)
    }

    /**
     * Request one fresh location fix (async, ~4s timeout). Falls back to the
     * last-known fix so a moving or stationary phone both answer quickly.
     */
    private suspend fun requestSingleUpdate(
        lm: android.location.LocationManager,
        providers: List<String>,
    ): android.location.Location? = kotlinx.coroutines.suspendCancellableCoroutine { cont ->
        var done = false
        val listener = object : android.location.LocationListener {
            override fun onLocationChanged(loc: android.location.Location) {
                if (!done) {
                    done = true
                    try { lm.removeUpdates(this) } catch (_: Exception) {}
                    cont.resume(loc)
                }
            }
        }
        val lastKnown = providers.firstNotNullOfOrNull { p ->
            try { lm.getLastKnownLocation(p) } catch (_: Exception) { null }
        }
        // If ANY cached fix exists, use it immediately (age-bounded: 48h).
        // A stationary phone may never produce a fresh network fix; the cached
        // one is still a useful commute estimate for the brain.
        if (lastKnown != null && System.currentTimeMillis() - lastKnown.time < 48L * 3600_000L) {
            done = true
            cont.resume(lastKnown)
            return@suspendCancellableCoroutine
        }
        var requestedAny = false
        for (p in providers) {
            try {
                lm.requestSingleUpdate(p, listener, android.os.Looper.getMainLooper())
                requestedAny = true
            } catch (_: Exception) {}
        }
        if (!requestedAny) {
            if (!done) { done = true; cont.resume(lastKnown) }
            return@suspendCancellableCoroutine
        }
        cont.invokeOnCancellation {
            if (!done) { done = true; try { lm.removeUpdates(listener) } catch (_: Exception) {} }
        }
        // Timeout fallback to last-known (may be null).
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            if (!done) {
                done = true
                try { lm.removeUpdates(listener) } catch (_: Exception) {}
                cont.resume(lastKnown)
            }
        }, 4000L)
    }

    private suspend fun ApplicationCall.respondError(status: HttpStatusCode, message: String) {
        val err = JSONObject()
        err.put("error", status.description)
        err.put("code", status.value)
        err.put("message", message)
        respondText(err.toString(2), ContentType.Application.Json, status)
    }
}
