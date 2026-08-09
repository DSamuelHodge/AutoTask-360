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
import io.ktor.server.application.call
import io.ktor.server.cio.CIO
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

        serverEngine = embeddedServer(CIO, host = "127.0.0.1", port = port) {
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

    private suspend fun ApplicationCall.respondError(status: HttpStatusCode, message: String) {
        val err = JSONObject()
        err.put("error", status.description)
        err.put("code", status.value)
        err.put("message", message)
        respondText(err.toString(2), ContentType.Application.Json, status)
    }
}
