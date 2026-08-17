package com.example.security

class SecurityAuditLog(
    private val maxEvents: Int = 200,
    private val clock: () -> Long = { System.currentTimeMillis() }
) {
    private val events = ArrayDeque<SecurityAuditEvent>()

    @Synchronized
    fun record(
        principal: AccessPrincipal,
        operation: String,
        path: String,
        outcome: String,
        code: String,
        detail: String = ""
    ) {
        events.addLast(
            SecurityAuditEvent(
                timestamp = clock(),
                principalId = principal.id,
                kind = principal.kind.name,
                operation = operation,
                path = path,
                outcome = outcome,
                code = code,
                detail = Redaction.redact(detail)
            )
        )
        while (events.size > maxEvents) events.removeFirst()
    }

    @Synchronized
    fun list(limit: Int = 50): List<SecurityAuditEvent> =
        events.toList().takeLast(limit.coerceIn(1, maxEvents)).reversed()

    @Synchronized
    fun reset() {
        events.clear()
    }
}
