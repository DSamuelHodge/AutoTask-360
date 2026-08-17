package com.example.server

import com.example.security.AccessDeniedException
import com.example.security.AccessOperation
import com.example.security.AccessPrincipal
import com.example.security.ExternalAccess
import com.example.security.RoutePolicy
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.response.respondText
import io.ktor.util.AttributeKey
import org.json.JSONObject

object HttpSecurity {
    const val MAX_BODY_BYTES = 256 * 1024
    val PrincipalKey = AttributeKey<AccessPrincipal>("accessPrincipal")
    val IdempotencyKey = AttributeKey<String>("idempotencyKey")

    suspend fun gate(call: ApplicationCall, access: ExternalAccess): Boolean {
        val path = call.request.path()
        val method = call.request.httpMethod.value
        val remote = call.request.local.remoteHost
        val loopback = RoutePolicy.isLoopbackHost(remote)
        val origin = call.request.headers["Origin"]
        val contentLength = call.request.headers["Content-Length"]?.toLongOrNull()
        if (contentLength != null && contentLength > MAX_BODY_BYTES) {
            deny(call, access, AccessPrincipal.ANONYMOUS, 413, "BODY_TOO_LARGE", "Request body too large", path)
            return false
        }
        if (!RoutePolicy.originAllowed(origin, loopback, access.isLanEnabled())) {
            deny(call, access, AccessPrincipal.ANONYMOUS, 403, "ORIGIN_DENIED", "Origin not allowed", path)
            return false
        }

        val auth = access.guard.authenticate(remote, call.request.headers["Authorization"], path)
        if (!auth.allowed) {
            deny(call, access, auth.principal, auth.status, auth.code, auth.message, path)
            return false
        }

        val operation = RoutePolicy.operationFor(method, path)
        val authorized = access.guard.authorize(auth.principal, operation, loopback)
        if (!authorized.allowed) {
            deny(call, access, authorized.principal, authorized.status, authorized.code, authorized.message, path)
            return false
        }

        val rateKey = "${auth.principal.id}|$remote"
        val rate = access.limiter.allow(rateKey)
        if (!rate.allowed) {
            call.response.headers.append("Retry-After", ((rate.retryAfterMs + 999) / 1000).toString())
            deny(call, access, auth.principal, 429, "RATE_LIMITED", "Too many requests", path)
            return false
        }

        val idem = call.request.headers["Idempotency-Key"]?.trim().orEmpty()
        if (idem.isNotEmpty() && method.uppercase() in setOf("POST", "PATCH", "DELETE")) {
            access.idempotency.get("${auth.principal.id}|$method|$path|$idem")?.let { cached ->
                call.respondText(
                    cached.body,
                    ContentType.Application.Json,
                    HttpStatusCode.fromValue(cached.status)
                )
                return false
            }
            call.attributes.put(IdempotencyKey, "${auth.principal.id}|$method|$path|$idem")
        }

        call.attributes.put(PrincipalKey, auth.principal)
        return true
    }

    fun principalOf(call: ApplicationCall): AccessPrincipal =
        if (call.attributes.contains(PrincipalKey)) call.attributes[PrincipalKey] else AccessPrincipal.ANONYMOUS

    suspend fun recordIdempotent(call: ApplicationCall, access: ExternalAccess, status: Int, body: String) {
        if (!call.attributes.contains(IdempotencyKey)) return
        access.idempotency.put(call.attributes[IdempotencyKey], status, body)
    }

    private suspend fun deny(
        call: ApplicationCall,
        access: ExternalAccess,
        principal: AccessPrincipal,
        status: Int,
        code: String,
        message: String,
        path: String
    ) {
        access.audit.record(principal, "HTTP", path, "DENIED", code, message)
        val err = JSONObject()
            .put("error", HttpStatusCode.fromValue(status).description)
            .put("code", code)
            .put("status", status)
            .put("message", message)
        call.respondText(err.toString(2), ContentType.Application.Json, HttpStatusCode.fromValue(status))
    }

    fun require(call: ApplicationCall, access: ExternalAccess, operation: AccessOperation) {
        val principal = principalOf(call)
        val loopback = RoutePolicy.isLoopbackHost(call.request.local.remoteHost)
        val decision = access.guard.authorize(principal, operation, loopback)
        if (!decision.allowed) {
            throw AccessDeniedException(decision.status, decision.code, decision.message)
        }
    }
}
