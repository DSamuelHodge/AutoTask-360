package com.example.server

import android.content.Context
import com.example.application.AutomationCommandFacade
import com.example.application.ProfileNotFoundException
import com.example.domain.ProfileListQuery
import com.example.domain.RunNotFoundException
import com.example.domain.ScheduleNotFoundException
import com.example.security.AccessDeniedException
import com.example.security.AccessOperation
import com.example.security.AccessScope
import com.example.security.ApprovalRequiredException
import com.example.security.CommandContext
import com.example.security.ExternalAccess
import com.example.security.PairingException
import com.example.security.PairingRequiredException
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
    private val commands = AutomationCommandFacade.getInstance(context)
    private val access = ExternalAccess.getInstance(context)

    fun start() {
        if (serverEngine != null) return

        serverEngine = embeddedServer(CIO, host = KtorServerConfig.bindHost(context), port = port) {
            intercept(ApplicationCallPipeline.Plugins) {
                if (!HttpSecurity.gate(call, access)) {
                    finish()
                }
            }
            routing {
                post("/v1/pairing/start") {
                    val challenge = access.startPairing()
                    val body = JSONObject()
                        .put("status", "OK")
                        .put("code", challenge.code)
                        .put("expiresAt", challenge.expiresAt)
                    call.respondJson(body.toString(2))
                }

                post("/v1/pairing/complete") {
                    val bodyText = call.receiveText()
                    try {
                        val json = if (bodyText.isBlank()) JSONObject() else JSONObject(bodyText)
                        val scopes = linkedSetOf<AccessScope>()
                        val scopeArr = json.optJSONArray("scopes")
                        if (scopeArr != null) {
                            for (i in 0 until scopeArr.length()) {
                                AccessScope.parse(scopeArr.optString(i))?.let { scopes += it }
                            }
                        }
                        val actions = linkedSetOf<String>()
                        val actionArr = json.optJSONArray("approvedActions")
                        if (actionArr != null) {
                            for (i in 0 until actionArr.length()) actions += actionArr.optString(i)
                        }
                        val issued = access.completePairing(
                            code = json.optString("code"),
                            name = json.optString("name", "paired-client"),
                            scopes = scopes,
                            approvedActions = actions
                        )
                        val body = JSONObject()
                            .put("status", "OK")
                            .put("credentialId", issued.credential.id)
                            .put("name", issued.credential.name)
                            .put("token", issued.token)
                            .put("scopes", JSONArray(issued.credential.scopes.map { it.name }))
                            .put("approvedActions", JSONArray(issued.credential.approvedActions.toList()))
                        call.respondJson(body.toString(2), HttpStatusCode.Created)
                    } catch (e: PairingException) {
                        call.respondError(HttpStatusCode.BadRequest, e.message ?: "Pairing failed")
                    }
                }

                get("/v1/pairing/credentials") {
                    val arr = JSONArray()
                    access.pairing.list().forEach { cred ->
                        arr.put(
                            JSONObject()
                                .put("id", cred.id)
                                .put("name", cred.name)
                                .put("scopes", JSONArray(cred.scopes.map { it.name }))
                                .put("approvedActions", JSONArray(cred.approvedActions.toList()))
                                .put("createdAt", cred.createdAt)
                                .put("lastUsedAt", cred.lastUsedAt)
                                .put("revoked", cred.revoked)
                        )
                    }
                    call.respondJson(JSONObject().put("credentials", arr).put("count", arr.length()).toString(2))
                }

                post("/v1/pairing/revoke") {
                    val bodyText = call.receiveText()
                    val id = try {
                        JSONObject(bodyText).optString("id")
                    } catch (_: Exception) {
                        ""
                    }
                    if (id.isBlank()) {
                        call.respondError(HttpStatusCode.BadRequest, "id is required")
                    } else {
                        val revoked = access.pairing.revoke(id)
                        if (!revoked) call.respondError(HttpStatusCode.NotFound, "Credential not found")
                        else call.respondJson(JSONObject().put("status", "OK").put("revoked", id).toString(2))
                    }
                }

                post("/v1/pairing/lan") {
                    val bodyText = call.receiveText()
                    val enabled = try {
                        JSONObject(bodyText).optBoolean("enabled", false)
                    } catch (_: Exception) {
                        false
                    }
                    try {
                        access.setLanEnabled(enabled)
                        call.respondJson(
                            JSONObject()
                                .put("status", "OK")
                                .put("lanEnabled", access.isLanEnabled())
                                .put("bindHost", KtorServerConfig.bindHost(context))
                                .toString(2)
                        )
                    } catch (_: PairingRequiredException) {
                        call.respondError(HttpStatusCode.Forbidden, "LAN access requires at least one active paired credential")
                    }
                }

                // GET /v1/status
                get("/v1/status") {
                    val statusMap = commands.statusMap()
                    val json = JSONObject()
                    json.put("engine_running", if (commands.isRunning) 1 else 0)
                    json.put("profile_count", statusMap["profile_count"])
                    json.put("log_count", statusMap["log_count"])
                    json.put("command_url", statusMap["command_url"])
                    json.put("ktor_server_enabled", statusMap["ktor_server_enabled"])
                    json.put("ktor_server_host", statusMap["ktor_server_host"])
                    json.put("ktor_server_port", statusMap["ktor_server_port"])
                    json.put("ktor_server_running", statusMap["ktor_server_running"])
                    json.put("listener_port", statusMap["listener_port"])
                    json.put("watch_running", statusMap["watch_running"])
                    json.put("last_server_error", statusMap["last_server_error"])
                    json.put("last_server_result", statusMap["last_server_result"])
                    json.put("notification_policy_declared", statusMap["notification_policy_declared"])
                    json.put("notification_policy_granted", statusMap["notification_policy_granted"])
                    json.put("write_settings_granted", statusMap["write_settings_granted"])
                    json.put("notification_listener_enabled", statusMap["notification_listener_enabled"])
                    json.put("dnd_ready", statusMap["dnd_ready"])
                    json.put("device_settings_ready", statusMap["device_settings_ready"])
                    val ready = JSONObject()
                    ready.put("api", commands.isRunning && statusMap["ktor_server_running"] == true)
                    ready.put("permissions", statusMap["dnd_ready"] == true && statusMap["device_settings_ready"] == true)
                    ready.put("dnd", statusMap["dnd_ready"])
                    ready.put("device_settings", statusMap["device_settings_ready"])
                    ready.put("notification_listener", statusMap["notification_listener_enabled"])
                    json.put("ready", ready)
                    json.put("provider_uri", "content://com.example.autotask.provider")
                    json.put("uptime_ms", commands.uptimeMs())
                    json.put("version", com.example.BuildConfig.VERSION_NAME)

                    call.respondJson(json.toString(2))
                }

                // GET /v1/schema
                get("/v1/schema") {
                    call.respondJson(commands.schemaJson())
                }

                // GET /v1/capabilities
                get("/v1/capabilities") {
                    call.respondJson(commands.capabilitiesJson())
                }

                // GET /v1/profiles
                get("/v1/profiles") {
                    val params = call.request.queryParameters
                    val query = ProfileListQuery(
                        q = params["q"] ?: params["search"],
                        id = params["id"],
                        actionType = params["actionType"] ?: params["action"],
                        triggerType = params["triggerType"] ?: params["trigger"],
                        enabled = params["enabled"]?.toBooleanStrictOrNull(),
                        limit = params["limit"]?.toIntOrNull()
                    )
                    val profiles = commands.listProfiles(query)
                    val arr = JSONArray()
                    profiles.forEach { p ->
                        arr.put(AutomationCommandFacade.profileToJson(p))
                    }
                    call.respondJson(arr.toString(2))
                }

                // GET /v1/profiles/{id}
                get("/v1/profiles/{id}") {
                    val id = call.parameters["id"] ?: ""
                    val p = commands.getProfile(id)
                    if (p != null) {
                        call.respondJson(AutomationCommandFacade.profileToJson(p).toString(2))
                    } else {
                        call.respondError(HttpStatusCode.NotFound, "Profile not found: $id")
                    }
                }

                // POST /v1/profiles/validate
                post("/v1/profiles/validate") {
                    val bodyText = call.receiveText()
                    try {
                        val definition = commands.validateAutomation(JSONObject(bodyText))
                        val resp = JSONObject()
                            .put("status", "OK")
                            .put("valid", true)
                            .put("definition", AutomationCommandFacade.definitionToJson(definition))
                        call.respondJson(resp.toString(2))
                    } catch (e: Exception) {
                        call.respondError(HttpStatusCode.BadRequest, "Invalid automation: ${e.localizedMessage}")
                    }
                }

                // POST /v1/profiles (Create / Upsert)
                post("/v1/profiles") {
                    val bodyText = call.receiveText()
                    try {
                        val profile = commands.upsertProfile(JSONObject(bodyText))

                        val resp = JSONObject()
                        resp.put("status", "OK")
                        resp.put("message", "Profile upserted successfully")
                        resp.put("profile", AutomationCommandFacade.profileToJson(profile))
                        call.respondJson(resp.toString(2), HttpStatusCode.Created)
                    } catch (e: Exception) {
                        call.respondError(HttpStatusCode.BadRequest, "Invalid JSON body: ${e.localizedMessage}")
                    }
                }

                // PATCH /v1/profiles/{id}
                patch("/v1/profiles/{id}") {
                    val id = call.parameters["id"] ?: ""
                    val bodyText = call.receiveText()
                    try {
                        val updated = commands.patchProfile(id, JSONObject(bodyText))

                        val resp = JSONObject()
                        resp.put("status", "OK")
                        resp.put("message", "Profile patched")
                        resp.put("profile", AutomationCommandFacade.profileToJson(updated))
                        call.respondJson(resp.toString(2))
                    } catch (e: ProfileNotFoundException) {
                        call.respondError(HttpStatusCode.NotFound, e.message ?: "Profile not found")
                    } catch (e: Exception) {
                        call.respondError(HttpStatusCode.BadRequest, "Invalid JSON patch: ${e.localizedMessage}")
                    }
                }

                // DELETE /v1/profiles/{id}
                delete("/v1/profiles/{id}") {
                    val id = call.parameters["id"] ?: ""
                    val deleted = commands.deleteProfile(id)
                    if (deleted) {
                        val resp = JSONObject()
                        resp.put("status", "OK")
                        resp.put("deletedProfileId", id)
                        call.respondJson(resp.toString(2))
                    } else {
                        call.respondError(HttpStatusCode.NotFound, "Profile not found: $id")
                    }
                }

                // POST /v1/runs (Enqueue a durable run)
                post("/v1/runs") {
                    val bodyText = call.receiveText()
                    try {
                        val json = if (bodyText.isBlank()) JSONObject() else JSONObject(bodyText)
                        val ctx = CommandContext(HttpSecurity.principalOf(call))
                        call.respondJson(AutomationCommandFacade.eventResultToJson(commands.requestRun(json, ctx)).toString(2))
                    } catch (e: ProfileNotFoundException) {
                        call.respondError(HttpStatusCode.NotFound, e.message ?: "Profile not found")
                    } catch (e: ApprovalRequiredException) {
                        call.respondApprovalRequired(e)
                    } catch (e: AccessDeniedException) {
                        call.respondDenied(e)
                    } catch (e: Exception) {
                        call.respondError(HttpStatusCode.BadRequest, "Invalid run request: ${e.localizedMessage}")
                    }
                }

                get("/v1/runs") {
                    val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 50
                    val profileId = call.request.queryParameters["profileId"]
                    val arr = JSONArray()
                    commands.listRuns(limit, profileId).forEach { arr.put(AutomationCommandFacade.runToJson(it)) }
                    call.respondJson(JSONObject().put("runs", arr).put("count", arr.length()).toString(2))
                }

                get("/v1/runs/{id}") {
                    val id = call.parameters["id"] ?: ""
                    try {
                        call.respondJson(AutomationCommandFacade.runToJson(commands.getRun(id)).toString(2))
                    } catch (e: RunNotFoundException) {
                        call.respondError(HttpStatusCode.NotFound, e.message ?: "Run not found")
                    }
                }

                post("/v1/runs/{id}/cancel") {
                    val id = call.parameters["id"] ?: ""
                    try {
                        call.respondJson(AutomationCommandFacade.runToJson(commands.cancelRun(id)).toString(2))
                    } catch (e: RunNotFoundException) {
                        call.respondError(HttpStatusCode.NotFound, e.message ?: "Run not found")
                    }
                }

                post("/v1/runs/{id}/retry") {
                    val id = call.parameters["id"] ?: ""
                    try {
                        call.respondJson(AutomationCommandFacade.runToJson(commands.retryRun(id)).toString(2))
                    } catch (e: RunNotFoundException) {
                        call.respondError(HttpStatusCode.NotFound, e.message ?: "Run not found")
                    } catch (e: Exception) {
                        call.respondError(HttpStatusCode.BadRequest, e.localizedMessage ?: "Retry failed")
                    }
                }

                post("/v1/runs/{id}/resume") {
                    val id = call.parameters["id"] ?: ""
                    try {
                        call.respondJson(AutomationCommandFacade.runToJson(commands.resumeRun(id)).toString(2))
                    } catch (e: RunNotFoundException) {
                        call.respondError(HttpStatusCode.NotFound, e.message ?: "Run not found")
                    }
                }

                get("/v1/schedules") {
                    val arr = JSONArray()
                    commands.listSchedules().forEach { arr.put(AutomationCommandFacade.scheduleToJson(it)) }
                    call.respondJson(JSONObject().put("schedules", arr).put("count", arr.length()).toString(2))
                }

                get("/v1/schedules/{id}") {
                    val id = call.parameters["id"] ?: ""
                    try {
                        call.respondJson(AutomationCommandFacade.scheduleToJson(commands.getSchedule(id)).toString(2))
                    } catch (e: ScheduleNotFoundException) {
                        call.respondError(HttpStatusCode.NotFound, e.message ?: "Schedule not found")
                    }
                }

                post("/v1/schedules/reconcile") {
                    val bodyText = call.receiveText()
                    val reason = try {
                        if (bodyText.isBlank()) "manual" else JSONObject(bodyText).optString("reason", "manual")
                    } catch (_: Exception) {
                        "manual"
                    }
                    val arr = JSONArray()
                    commands.reconcileSchedules(reason).forEach { arr.put(AutomationCommandFacade.scheduleToJson(it)) }
                    call.respondJson(
                        JSONObject()
                            .put("status", "OK")
                            .put("reason", reason)
                            .put("schedules", arr)
                            .put("count", arr.length())
                            .toString(2)
                    )
                }

                // POST /v1/events (Fire manual / test event)
                post("/v1/events") {
                    val bodyText = call.receiveText()
                    try {
                        val json = if (bodyText.isBlank()) JSONObject() else JSONObject(bodyText)
                        val dryRun = json.optBoolean("dryRun", false) || json.optBoolean("dry_run", false)
                        if (!dryRun) {
                            HttpSecurity.require(call, access, AccessOperation.EXECUTE_RUNS)
                        }
                        val ctx = CommandContext(HttpSecurity.principalOf(call))
                        call.respondJson(AutomationCommandFacade.eventResultToJson(commands.fireEvent(json, ctx)).toString(2))
                    } catch (e: ProfileNotFoundException) {
                        call.respondError(HttpStatusCode.NotFound, e.message ?: "Profile not found")
                    } catch (e: ApprovalRequiredException) {
                        call.respondApprovalRequired(e)
                    } catch (e: AccessDeniedException) {
                        call.respondDenied(e)
                    } catch (e: Exception) {
                        call.respondError(HttpStatusCode.BadRequest, "Invalid event request: ${e.localizedMessage}")
                    }
                }

                // GET /v1/logs?limit=N
                get("/v1/logs") {
                    val limitParam = call.request.queryParameters["limit"]?.toIntOrNull() ?: 100
                    val logs = commands.listLogs(limitParam)

                    val arr = JSONArray()
                    logs.forEach { l ->
                        arr.put(AutomationCommandFacade.logToJson(l))
                    }
                    call.respondJson(arr.toString(2))
                }

                // DELETE /v1/logs
                delete("/v1/logs") {
                    commands.clearLogs()
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
                    val bodyText = call.receiveText()
                    val headers = mutableMapOf<String, String>()
                    for (k in call.request.headers.names()) {
                        headers[k] = call.request.headers[k] ?: ""
                    }
                    val result = com.example.mcp.McpHandler.handle(
                        context,
                        headers,
                        bodyText,
                        HttpSecurity.principalOf(call)
                    )
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

    private suspend fun ApplicationCall.respondJson(text: String, status: HttpStatusCode = HttpStatusCode.OK) {
        HttpSecurity.recordIdempotent(this, access, status.value, text)
        respondText(text, ContentType.Application.Json, status)
    }

    private suspend fun ApplicationCall.respondDenied(error: AccessDeniedException) {
        val body = JSONObject()
            .put("error", error.code)
            .put("code", error.code)
            .put("status", error.status)
            .put("message", error.message)
            .toString(2)
        HttpSecurity.recordIdempotent(this, access, error.status, body)
        respondText(body, ContentType.Application.Json, HttpStatusCode.fromValue(error.status))
    }

    private suspend fun ApplicationCall.respondApprovalRequired(error: ApprovalRequiredException) {
        val body = JSONObject()
            .put("error", "APPROVAL_REQUIRED")
            .put("code", "APPROVAL_REQUIRED")
            .put("status", 403)
            .put("message", error.message)
            .put("actions", JSONArray(error.actions))
            .toString(2)
        HttpSecurity.recordIdempotent(this, access, 403, body)
        respondText(body, ContentType.Application.Json, HttpStatusCode.Forbidden)
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
        val body = err.toString(2)
        HttpSecurity.recordIdempotent(this, access, status.value, body)
        respondText(body, ContentType.Application.Json, status)
    }
}
