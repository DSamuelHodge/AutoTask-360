package com.example.security

object RoutePolicy {
    fun operationFor(method: String, path: String): AccessOperation {
        val normalized = normalize(path)
        val verb = method.uppercase()
        return when {
            normalized.startsWith("/v1/pairing") -> AccessOperation.PAIRING_ADMIN
            normalized == "/mcp" -> AccessOperation.MCP_CALL
            normalized == "/v1/status" -> AccessOperation.READ_STATUS
            normalized == "/v1/schema" -> AccessOperation.READ_SCHEMA
            normalized == "/v1/capabilities" -> AccessOperation.READ_CAPABILITIES
            normalized.startsWith("/v1/profiles") -> if (verb == "GET") AccessOperation.READ_PROFILES else AccessOperation.WRITE_PROFILES
            normalized.startsWith("/v1/runs") -> if (verb == "GET") AccessOperation.READ_RUNS else AccessOperation.EXECUTE_RUNS
            normalized.startsWith("/v1/schedules") -> if (verb == "GET") AccessOperation.READ_SCHEDULES else AccessOperation.WRITE_SCHEDULES
            normalized.startsWith("/v1/logs") -> if (verb == "GET") AccessOperation.READ_LOGS else AccessOperation.WRITE_LOGS
            normalized == "/v1/brain/status" -> AccessOperation.READ_BRAIN
            normalized == "/v1/brain" -> AccessOperation.EXECUTE_BRAIN
            normalized == "/v1/http" -> AccessOperation.EXECUTE_HTTP
            normalized == "/v1/contacts" -> AccessOperation.READ_CONTACTS
            normalized == "/v1/location" -> AccessOperation.READ_LOCATION
            normalized == "/v1/screen" || normalized.startsWith("/v1/ui/") -> AccessOperation.UI_CONTROL
            normalized.startsWith("/v1/ota") -> if (verb == "GET") AccessOperation.READ_OTA else AccessOperation.WRITE_OTA
            normalized.startsWith("/v1/wa") -> if (verb == "GET") AccessOperation.READ_WA else AccessOperation.EXECUTE_WA
            normalized == "/v1/events" -> AccessOperation.READ_PROFILES
            else -> AccessOperation.READ_STATUS
        }
    }

    fun operationForMcpTool(name: String): AccessOperation {
        return when (name) {
            "autotask.schema", "autotask.capabilities",
            "autotask.profiles.list", "autotask.profiles.get",
            "autotask.runs.get", "autotask.runs.list",
            "autotask.schedules.list", "autotask.schedules.get",
            "autotask.logs.list" -> AccessOperation.READ_PROFILES
            "autotask.profiles.validate", "autotask.profiles.upsert",
            "autotask.profiles.patch", "autotask.profiles.delete",
            "autotask.schedules.reconcile" -> AccessOperation.WRITE_PROFILES
            "autotask.events.fire" -> AccessOperation.READ_PROFILES
            "autotask.runs.request",
            "autotask.runs.cancel", "autotask.runs.retry",
            "autotask.runs.resume" -> AccessOperation.EXECUTE_RUNS
            else -> AccessOperation.EXECUTE_RUNS
        }
    }

    fun originAllowed(origin: String?, loopback: Boolean, lanEnabled: Boolean): Boolean {
        if (origin.isNullOrBlank()) return true
        val host = hostOf(origin) ?: return false
        if (isLoopbackHost(host)) return true
        return lanEnabled && !loopback
    }

    fun isLoopbackHost(host: String): Boolean =
        host == "127.0.0.1" || host == "::1" || host.equals("localhost", ignoreCase = true) ||
            host == "::ffff:127.0.0.1"

    private fun hostOf(origin: String): String? {
        return try {
            val trimmed = origin.trim()
            val withoutScheme = trimmed.substringAfter("://", trimmed)
            withoutScheme.substringBefore("/").substringBefore(":").ifBlank { null }
        } catch (_: Exception) {
            null
        }
    }

    private fun normalize(path: String): String {
        val cut = path.substringBefore("?").trim().ifBlank { "/" }
        return if (cut.length > 1 && cut.endsWith("/")) cut.dropLast(1) else cut
    }
}
