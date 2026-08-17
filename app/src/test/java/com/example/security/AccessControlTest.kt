package com.example.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AccessControlTest {
    private lateinit var store: InMemoryCredentialStore
    private lateinit var pairing: PairingManager
    private lateinit var guard: AccessGuard
    private var lan = false
    private val brain = "cos-internal-secret"

    @Before
    fun setUp() {
        store = InMemoryCredentialStore()
        pairing = PairingManager(store, codeFactory = { "123456" }, tokenFactory = { "atc-paired-token" })
        lan = false
        guard = AccessGuard(
            credentials = store,
            brainToken = { brain },
            lanEnabled = { lan },
            debugBuild = true
        )
    }

    @Test
    fun authorizationMatrix() {
        pairing.start()
        val issued = pairing.complete("123456", "reader", setOf(AccessScope.READ))
        val principal = guard.authenticate("127.0.0.1", "Bearer ${issued.token}").principal
        assertTrue(guard.authorize(principal, AccessOperation.READ_PROFILES, true).allowed)
        assertFalse(guard.authorize(principal, AccessOperation.WRITE_PROFILES, true).allowed)
        assertFalse(guard.authorize(principal, AccessOperation.EXECUTE_RUNS, true).allowed)
        assertFalse(guard.authorize(principal, AccessOperation.UI_CONTROL, true).allowed)
        assertFalse(guard.authorize(principal, AccessOperation.WRITE_OTA, true).allowed)
    }

    @Test
    fun unauthenticatedLanIsRejected() {
        lan = true
        val decision = guard.authenticate("192.168.1.20", null)
        assertFalse(decision.allowed)
        assertEquals(401, decision.status)
        assertEquals("UNAUTHORIZED", decision.code)
    }

    @Test
    fun lanDisabledRejectsNonLoopback() {
        val decision = guard.authenticate("192.168.1.20", "Bearer atc-paired-token")
        assertFalse(decision.allowed)
        assertEquals("LAN_DISABLED", decision.code)
    }

    @Test
    fun brainTokenIsRejectedOnLan() {
        lan = true
        val decision = guard.authenticate("192.168.1.20", "Bearer $brain")
        assertFalse(decision.allowed)
        assertEquals("INTERNAL_TOKEN_ON_LAN", decision.code)
    }

    @Test
    fun brainTokenIsAcceptedOnLoopback() {
        val decision = guard.authenticate("127.0.0.1", "Bearer $brain")
        assertTrue(decision.allowed)
        assertEquals(PrincipalKind.INTERNAL_BRAIN, decision.principal.kind)
    }

    @Test
    fun debugLoopbackAllowsUnauthenticatedRest() {
        val decision = guard.authenticate("127.0.0.1", null, "/v1/status")
        assertTrue(decision.allowed)
        assertEquals(PrincipalKind.DEBUG_LOOPBACK, decision.principal.kind)
    }

    @Test
    fun mcpOnLoopbackStillRequiresAToken() {
        val decision = guard.authenticate("127.0.0.1", null, "/mcp")
        assertFalse(decision.allowed)
        assertEquals(401, decision.status)
    }

    @Test
    fun pairedLanClientNeedsMatchingScope() {
        pairing.start()
        val issued = pairing.complete(
            "123456",
            "exec",
            setOf(AccessScope.EXECUTE)
        )
        lan = true
        val auth = guard.authenticate("10.0.0.8", "Bearer ${issued.token}")
        assertTrue(auth.allowed)
        assertFalse(guard.authorize(auth.principal, AccessOperation.READ_PROFILES, false).allowed)
        assertTrue(guard.authorize(auth.principal, AccessOperation.EXECUTE_RUNS, false).allowed)
    }

    @Test
    fun pairingAdminIsLoopbackOnly() {
        pairing.start()
        val issued = pairing.complete("123456", "admin", AccessScope.entries.toSet())
        lan = true
        val auth = guard.authenticate("10.0.0.8", "Bearer ${issued.token}")
        val denied = guard.authorize(auth.principal, AccessOperation.PAIRING_ADMIN, loopback = false)
        assertFalse(denied.allowed)
        assertEquals("LOOPBACK_ONLY", denied.code)
    }

    @Test
    fun invalidTokenDoesNotLeakSecret() {
        lan = true
        pairing.start()
        pairing.complete("123456", "x", setOf(AccessScope.READ))
        val decision = guard.authenticate("10.0.0.8", "Bearer totally-wrong")
        assertFalse(decision.allowed)
        assertFalse(decision.message.contains("atc-paired-token"))
    }

    @Test
    fun pairingMintsOnceAndHashesToken() {
        pairing.start()
        val issued = pairing.complete("123456", "mac", setOf(AccessScope.READ, AccessScope.EXECUTE))
        assertTrue(issued.token.startsWith("atc-"))
        assertNotEquals(issued.token, issued.credential.tokenHash)
        assertEquals(issued.credential.tokenHash, TokenHasher.hash(issued.token))
        assertNull(store.findByToken("wrong"))
        assertEquals(issued.credential.id, store.findByToken(issued.token)?.id)
    }
}

class RateLimitAndIdempotencyTest {
    @Test
    fun rateLimiterTripsAfterWindowBudget() {
        var now = 1_000L
        val limiter = RateLimiter(maxPerWindow = 3, windowMs = 1_000L, clock = { now })
        assertTrue(limiter.allow("a").allowed)
        assertTrue(limiter.allow("a").allowed)
        assertTrue(limiter.allow("a").allowed)
        val blocked = limiter.allow("a")
        assertFalse(blocked.allowed)
        assertTrue(blocked.retryAfterMs > 0)
        now = 2_100L
        assertTrue(limiter.allow("a").allowed)
    }

    @Test
    fun idempotencyReplaysCachedResponse() {
        val store = IdempotencyStore(ttlMs = 10_000L, clock = { 5L })
        assertNull(store.get("k"))
        store.put("k", 201, "{\"ok\":true}")
        val cached = store.get("k")!!
        assertEquals(201, cached.status)
        assertEquals("{\"ok\":true}", cached.body)
    }
}

class OriginAndRedactionTest {
    @Test
    fun originFromRandomSiteIsDeniedOnLoopback() {
        assertFalse(RoutePolicy.originAllowed("https://evil.example", loopback = true, lanEnabled = false))
        assertTrue(RoutePolicy.originAllowed("http://127.0.0.1:8788", loopback = true, lanEnabled = false))
        assertTrue(RoutePolicy.originAllowed(null, loopback = false, lanEnabled = true))
        assertTrue(RoutePolicy.originAllowed("http://192.168.1.8:8788", loopback = false, lanEnabled = true))
    }

    @Test
    fun tokensAndBodiesAreRedacted() {
        assertEquals("Bearer ***", Redaction.redact("Bearer cos-abc123"))
        assertEquals("***", Redaction.redact("atc-deadbeef"))
        assertTrue(Redaction.isSensitiveKey("Authorization"))
        assertTrue(Redaction.isSensitiveKey("smsBody"))
        assertEquals("***", Redaction.redactMap(mapOf("token" to "secret")).getValue("token"))
    }
}

class HighRiskApprovalTest {
    @Test
    fun smsRequiresStoredApproval() {
        assertTrue(HighRiskPolicy.requiresApproval("SEND_SMS"))
        assertTrue(HighRiskPolicy.requiresApproval("HTTP"))
        assertFalse(HighRiskPolicy.requiresApproval("LOG"))
        assertEquals(listOf("SEND_SMS"), HighRiskPolicy.missingApprovals(listOf("SEND_SMS"), emptySet()))
        assertTrue(HighRiskPolicy.missingApprovals(listOf("SEND_SMS"), setOf("SEND_SMS")).isEmpty())
    }
}
