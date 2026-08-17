package com.example.security

object Redaction {
    private val SENSITIVE_KEYS = setOf(
        "authorization", "token", "bearer", "password", "secret", "smsbody",
        "body", "text", "message", "content", "number", "phone", "email",
        "screen", "dump", "contacts", "headers"
    )

    fun redact(value: String?): String {
        if (value.isNullOrBlank()) return ""
        return when {
            value.startsWith("Bearer ", ignoreCase = true) -> "Bearer ***"
            value.startsWith("cos-") || value.startsWith("atc-") -> "***"
            value.length > 12 && looksLikeSecret(value) -> "***"
            else -> value
        }
    }

    fun redactMap(input: Map<String, Any?>): Map<String, Any?> {
        return input.mapValues { (key, value) ->
            if (isSensitiveKey(key)) "***" else value?.toString()?.let { redact(it) } ?: value
        }
    }

    fun isSensitiveKey(key: String): Boolean {
        val normalized = key.lowercase().replace("_", "").replace("-", "")
        return SENSITIVE_KEYS.any { it in normalized }
    }

    fun looksLikeSecret(value: String): Boolean {
        val compact = value.trim()
        return compact.startsWith("cos-") ||
            compact.startsWith("atc-") ||
            compact.matches(Regex("(?i)bearer\\s+\\S+"))
    }
}
