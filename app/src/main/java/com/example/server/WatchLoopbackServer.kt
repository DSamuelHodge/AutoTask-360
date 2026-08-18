package com.example.server

import com.example.engine.WatchBus
import com.example.engine.WatchFact
import com.example.security.RoutePolicy
import io.ktor.http.CacheControl
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.cio.CIO
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.cacheControl
import io.ktor.server.response.respondText
import io.ktor.server.response.respondTextWriter
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.coroutines.channels.Channel
import org.json.JSONArray
import org.json.JSONObject

/**
 * Loopback-only watch stream on [KtorServerConfig.LISTENER_PORT].
 * Command traffic stays on 8788. Termux / CoS subscribe here.
 */
class WatchLoopbackServer(
    private val port: Int = KtorServerConfig.LISTENER_PORT
) {
    private var serverEngine: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null

    @Volatile
    var lastError: String = ""
        private set

    val running: Boolean get() = isRunning

    fun start() {
        if (serverEngine != null) return
        serverEngine = embeddedServer(CIO, host = KtorServerConfig.LOOPBACK_HOST, port = port) {
            intercept(ApplicationCallPipeline.Plugins) {
                val remote = call.request.local.remoteHost
                if (!RoutePolicy.isLoopbackHost(remote) && !KtorServerConfig.isLoopbackHost(remote)) {
                    call.respondText(
                        JSONObject().put("error", "LOOPBACK_ONLY").toString(),
                        ContentType.Application.Json,
                        HttpStatusCode.Forbidden
                    )
                    finish()
                }
            }
            routing {
                get("/") { call.respondText(statusJson(), ContentType.Application.Json) }
                get("/v1/status") { call.respondText(statusJson(), ContentType.Application.Json) }
                get("/v1/watch") {
                    val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 50
                    call.respondText(recentJson(limit), ContentType.Application.Json)
                }
                get("/v1/watch/stream") {
                    call.response.cacheControl(CacheControl.NoCache(null))
                    call.respondTextWriter(ContentType.Text.EventStream) {
                        WatchBus.hub.recent(20).forEach { fact ->
                            write(sse(fact))
                            flush()
                        }
                        val gate = Channel<WatchFact>(Channel.UNLIMITED)
                        val unsub = WatchBus.hub.subscribe { fact -> gate.trySend(fact) }
                        try {
                            for (fact in gate) {
                                write(sse(fact))
                                flush()
                            }
                        } finally {
                            unsub()
                            gate.close()
                        }
                    }
                }
            }
        }
        serverEngine?.start(wait = false)
        isRunning = true
        lastError = ""
    }

    fun stop() {
        try {
            serverEngine?.stop(gracePeriodMillis = 200, timeoutMillis = 500)
        } catch (_: Exception) {
        }
        serverEngine = null
        isRunning = false
    }

    companion object {
        @Volatile
        var isRunning: Boolean = false
            private set
    }

    private fun sse(fact: WatchFact): String = "data: ${fact.toJson()}\n\n"

    private fun statusJson(): String = JSONObject()
        .put("service", "AutoTask watch")
        .put("port", port)
        .put("host", KtorServerConfig.LOOPBACK_HOST)
        .put("running", running)
        .put("buffered", WatchBus.hub.size())
        .put("commandPort", KtorServerConfig.DEFAULT_PORT)
        .toString(2)

    private fun recentJson(limit: Int): String {
        val facts = JSONArray()
        WatchBus.hub.recent(limit).forEach { facts.put(it.toJson()) }
        return JSONObject()
            .put("status", "OK")
            .put("count", facts.length())
            .put("facts", facts)
            .toString(2)
    }
}
