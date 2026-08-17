package com.example.security

class AccessGuard(
    private val credentials: CredentialStore,
    private val brainToken: () -> String,
    private val lanEnabled: () -> Boolean,
    private val debugBuild: Boolean,
    private val clock: () -> Long = { System.currentTimeMillis() }
) {
    fun authenticate(
        remoteHost: String,
        authorization: String?,
        path: String = ""
    ): AccessDecision {
        val loopback = RoutePolicy.isLoopbackHost(remoteHost)
        val token = bearerToken(authorization)
        val brain = brainToken()

        if (!loopback && !lanEnabled()) {
            return AccessDecision.deny(
                AccessPrincipal.ANONYMOUS, 403, "LAN_DISABLED",
                "LAN access is disabled; pair a client and enable LAN mode"
            )
        }

        if (!token.isNullOrBlank() && token == brain) {
            return if (loopback) {
                AccessDecision.allow(
                    AccessPrincipal(
                        kind = PrincipalKind.INTERNAL_BRAIN,
                        id = "internal-brain",
                        scopes = AccessScope.entries.toSet(),
                        name = "internal-brain"
                    )
                )
            } else {
                AccessDecision.deny(
                    AccessPrincipal.ANONYMOUS, 401, "INTERNAL_TOKEN_ON_LAN",
                    "internal brain token cannot authorize LAN requests"
                )
            }
        }

        if (!token.isNullOrBlank()) {
            val credential = credentials.findByToken(token)
            if (credential == null || credential.revoked) {
                return AccessDecision.deny(
                    AccessPrincipal.ANONYMOUS, 401, "INVALID_TOKEN",
                    "missing or invalid Bearer token"
                )
            }
            if (!loopback && credential.scopes.isEmpty()) {
                return AccessDecision.deny(
                    credential.toPrincipal(), 403, "UNSCOPED",
                    "credential has no scopes"
                )
            }
            credentials.markUsed(credential.id, clock())
            return AccessDecision.allow(credential.toPrincipal())
        }

        if (loopback && debugBuild && !path.startsWith("/mcp")) {
            return AccessDecision.allow(AccessPrincipal.DEBUG_LOOPBACK)
        }
        if (loopback && debugBuild && path.startsWith("/mcp")) {
            return AccessDecision.deny(
                AccessPrincipal.ANONYMOUS, 401, "UNAUTHORIZED",
                "MCP requires a bearer token"
            )
        }

        return AccessDecision.deny(
            AccessPrincipal.ANONYMOUS, 401, "UNAUTHORIZED",
            "missing or invalid Bearer token"
        )
    }

    fun authorize(principal: AccessPrincipal, operation: AccessOperation, loopback: Boolean): AccessDecision {
        if (operation.loopbackOnly && !loopback) {
            return AccessDecision.deny(
                principal, 403, "LOOPBACK_ONLY",
                "pairing administration is only allowed from loopback"
            )
        }
        val required = operation.scope
        if (required != null && !principal.has(required)) {
            return AccessDecision.deny(
                principal, 403, "INSUFFICIENT_SCOPE",
                "scope ${required.name} is required"
            )
        }
        return AccessDecision.allow(principal)
    }

    fun authorizeMcp(principal: AccessPrincipal, toolName: String, loopback: Boolean): AccessDecision =
        authorize(principal, RoutePolicy.operationForMcpTool(toolName), loopback)

    private fun bearerToken(authorization: String?): String? {
        val raw = authorization?.trim().orEmpty()
        if (raw.isEmpty()) return null
        return if (raw.startsWith("Bearer ", ignoreCase = true)) raw.substring(7).trim() else raw
    }

    private fun PairedCredential.toPrincipal() = AccessPrincipal(
        kind = PrincipalKind.PAIRED_CLIENT,
        id = id,
        scopes = scopes,
        approvedActions = approvedActions,
        name = name
    )
}
