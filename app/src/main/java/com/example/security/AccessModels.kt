package com.example.security

enum class AccessScope {
    READ,
    PROFILE_WRITE,
    EXECUTE,
    UI_CONTROL,
    OTA;

    companion object {
        fun parse(raw: String): AccessScope? =
            entries.firstOrNull { it.name.equals(raw.trim(), ignoreCase = true) }

        fun parseAll(values: Collection<String>): Set<AccessScope> =
            values.mapNotNull { parse(it) }.toSet()
    }
}

enum class CallerKind {
    LOCAL,
    REMOTE
}

enum class PrincipalKind {
    LOCAL_DEVICE,
    DEBUG_LOOPBACK,
    INTERNAL_BRAIN,
    PAIRED_CLIENT,
    ANONYMOUS
}

data class AccessPrincipal(
    val kind: PrincipalKind,
    val id: String,
    val scopes: Set<AccessScope>,
    val approvedActions: Set<String> = emptySet(),
    val name: String = kind.name
) {
    fun has(scope: AccessScope): Boolean = scope in scopes

    val caller: CallerKind
        get() = if (kind == PrincipalKind.LOCAL_DEVICE) CallerKind.LOCAL else CallerKind.REMOTE

    companion object {
        val LOCAL = AccessPrincipal(
            kind = PrincipalKind.LOCAL_DEVICE,
            id = "local-device",
            scopes = AccessScope.entries.toSet(),
            approvedActions = emptySet(),
            name = "local-device"
        )
        val DEBUG_LOOPBACK = AccessPrincipal(
            kind = PrincipalKind.DEBUG_LOOPBACK,
            id = "debug-loopback",
            scopes = AccessScope.entries.toSet(),
            name = "debug-loopback"
        )
        val ANONYMOUS = AccessPrincipal(
            kind = PrincipalKind.ANONYMOUS,
            id = "anonymous",
            scopes = emptySet(),
            name = "anonymous"
        )
    }
}

data class CommandContext(
    val principal: AccessPrincipal
) {
    val caller: CallerKind get() = principal.caller

    companion object {
        val LOCAL = CommandContext(AccessPrincipal.LOCAL)
    }
}

enum class AccessOperation(val scope: AccessScope?, val loopbackOnly: Boolean = false) {
    READ_STATUS(AccessScope.READ),
    READ_SCHEMA(AccessScope.READ),
    READ_CAPABILITIES(AccessScope.READ),
    READ_PROFILES(AccessScope.READ),
    WRITE_PROFILES(AccessScope.PROFILE_WRITE),
    READ_RUNS(AccessScope.READ),
    EXECUTE_RUNS(AccessScope.EXECUTE),
    READ_SCHEDULES(AccessScope.READ),
    WRITE_SCHEDULES(AccessScope.PROFILE_WRITE),
    READ_LOGS(AccessScope.READ),
    WRITE_LOGS(AccessScope.PROFILE_WRITE),
    READ_BRAIN(AccessScope.READ),
    EXECUTE_BRAIN(AccessScope.EXECUTE),
    EXECUTE_HTTP(AccessScope.EXECUTE),
    READ_CONTACTS(AccessScope.READ),
    READ_LOCATION(AccessScope.READ),
    UI_CONTROL(AccessScope.UI_CONTROL),
    READ_OTA(AccessScope.READ),
    WRITE_OTA(AccessScope.OTA),
    READ_WA(AccessScope.READ),
    EXECUTE_WA(AccessScope.EXECUTE),
    MCP_CALL(AccessScope.READ),
    PAIRING_ADMIN(null, loopbackOnly = true)
}

data class AccessDecision(
    val allowed: Boolean,
    val principal: AccessPrincipal,
    val status: Int,
    val code: String,
    val message: String
) {
    companion object {
        fun allow(principal: AccessPrincipal) =
            AccessDecision(true, principal, 200, "OK", "OK")

        fun deny(
            principal: AccessPrincipal,
            status: Int,
            code: String,
            message: String
        ) = AccessDecision(false, principal, status, code, message)
    }
}

class AccessDeniedException(
    val status: Int,
    val code: String,
    override val message: String
) : RuntimeException(message)

class ApprovalRequiredException(
    val actions: List<String>
) : RuntimeException("approval required for ${actions.joinToString()}")

class PairingRequiredException : RuntimeException("LAN access requires at least one active paired credential")

class PairingException(message: String) : RuntimeException(message)

data class PairedCredential(
    val id: String,
    val name: String,
    val tokenHash: String,
    val scopes: Set<AccessScope>,
    val approvedActions: Set<String>,
    val createdAt: Long,
    val lastUsedAt: Long = 0L,
    val revoked: Boolean = false
)

data class IssuedCredential(
    val credential: PairedCredential,
    val token: String
)

data class PairingChallenge(
    val code: String,
    val expiresAt: Long
)

data class SecurityAuditEvent(
    val timestamp: Long,
    val principalId: String,
    val kind: String,
    val operation: String,
    val path: String,
    val outcome: String,
    val code: String,
    val detail: String
)
