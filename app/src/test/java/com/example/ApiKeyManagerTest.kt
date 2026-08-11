package com.example

import com.example.server.remote.ApiKeyManager
import com.example.server.remote.ApiKeyScope
import com.example.server.remote.ApiKeyValidation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Hermetic unit tests for the opt-in remote API-key scaffold (#23).
 *
 * Pure JVM: no Android Context, no Robolectric, no I/O. Covers the generate -> validate ->
 * revoke lifecycle plus the scope and expiry rules from docs/REMOTE_COS_THREAT_MODEL.md T7.
 */
class ApiKeyManagerTest {

  private val now = 1_700_000_000_000L

  @Test
  fun generatedKeyValidatesAndCarriesItsScopes() {
    val manager = ApiKeyManager()
    val issued = manager.issueKey("laptop-agent", setOf(ApiKeyScope.READ), now = now)

    val validation = manager.validate(issued.plaintextKey, now = now)

    assertTrue(validation is ApiKeyValidation.Valid)
    assertEquals(issued.record.keyId, (validation as ApiKeyValidation.Valid).record.keyId)
    assertEquals("laptop-agent", validation.record.label)
    assertEquals(setOf(ApiKeyScope.READ), validation.record.scopes)
  }

  @Test
  fun revokedKeyNoLongerValidates() {
    val manager = ApiKeyManager()
    val issued = manager.issueKey("laptop-agent", setOf(ApiKeyScope.READ), now = now)
    assertTrue(manager.validate(issued.plaintextKey, now = now) is ApiKeyValidation.Valid)

    assertTrue(manager.revoke(issued.record.keyId))

    val validation = manager.validate(issued.plaintextKey, now = now)
    assertTrue(validation is ApiKeyValidation.Revoked)
    assertEquals(issued.record.keyId, (validation as ApiKeyValidation.Revoked).keyId)
    assertEquals(0, manager.activeKeyCount(now))
  }

  @Test
  fun revokingTwiceReportsNoSecondChangeButRetainsRecord() {
    val manager = ApiKeyManager()
    val issued = manager.issueKey("laptop-agent", now = now)

    assertTrue(manager.revoke(issued.record.keyId))
    assertFalse(manager.revoke(issued.record.keyId))
    assertFalse(manager.revoke("atk_does_not_exist"))

    // Record is retained after revocation so audit history stays attributable (R7.7).
    assertEquals(1, manager.listKeys().size)
    assertTrue(manager.findKey(issued.record.keyId)?.revoked == true)
  }

  @Test
  fun plaintextSecretIsNeverStoredInTheRecord() {
    val manager = ApiKeyManager()
    val issued = manager.issueKey("laptop-agent", now = now)

    val secret = issued.plaintextKey.substringAfter(ApiKeyManager.KEY_SEPARATOR)

    assertNotEquals(secret, issued.record.verifier)
    assertFalse(issued.record.verifier.contains(secret))
    assertTrue(issued.plaintextKey.startsWith(ApiKeyManager.KEY_ID_PREFIX))
  }

  @Test
  fun eachIssuedKeyIsUnique() {
    val manager = ApiKeyManager()

    val first = manager.issueKey("agent-one", now = now)
    val second = manager.issueKey("agent-two", now = now)

    assertNotEquals(first.record.keyId, second.record.keyId)
    assertNotEquals(first.plaintextKey, second.plaintextKey)
    assertNotEquals(first.record.verifier, second.record.verifier)
    assertEquals(2, manager.activeKeyCount(now))
  }

  @Test
  fun tamperedOrMalformedKeysAreRejected() {
    val manager = ApiKeyManager()
    val issued = manager.issueKey("laptop-agent", now = now)
    val keyId = issued.record.keyId

    assertTrue(manager.validate("", now = now) is ApiKeyValidation.Invalid)
    assertTrue(manager.validate(keyId, now = now) is ApiKeyValidation.Invalid)
    assertTrue(manager.validate("$keyId.", now = now) is ApiKeyValidation.Invalid)
    assertTrue(manager.validate("$keyId.deadbeef", now = now) is ApiKeyValidation.Invalid)
    assertTrue(manager.validate("atk_unknown.deadbeef", now = now) is ApiKeyValidation.Invalid)
  }

  @Test
  fun keysFromOneManagerDoNotValidateAgainstAnother() {
    val issuer = ApiKeyManager()
    val other = ApiKeyManager()
    val issued = issuer.issueKey("laptop-agent", now = now)

    // Different pepper, so the verifier cannot match even for a well-formed key.
    assertTrue(other.validate(issued.plaintextKey, now = now) is ApiKeyValidation.Invalid)
  }

  @Test
  fun expiredKeyIsRejectedAtValidation() {
    val manager = ApiKeyManager()
    val ttl = 1_000L
    val issued = manager.issueKey("short-lived", now = now, ttlMillis = ttl)

    assertTrue(manager.validate(issued.plaintextKey, now = now + ttl - 1) is ApiKeyValidation.Valid)

    val expired = manager.validate(issued.plaintextKey, now = now + ttl)
    assertTrue(expired is ApiKeyValidation.Expired)
    assertEquals(0, manager.activeKeyCount(now + ttl))
  }

  @Test
  fun executeScopeIsNotImpliedByWrite() {
    val manager = ApiKeyManager()
    val issued = manager.issueKey(
      "writer-agent",
      setOf(ApiKeyScope.READ, ApiKeyScope.WRITE),
      now = now
    )

    assertTrue(manager.isAuthorized(issued.plaintextKey, ApiKeyScope.READ, now))
    assertTrue(manager.isAuthorized(issued.plaintextKey, ApiKeyScope.WRITE, now))
    // R5.2: execute must be granted explicitly.
    assertFalse(manager.isAuthorized(issued.plaintextKey, ApiKeyScope.EXECUTE, now))
  }

  @Test
  fun defaultScopeIsReadOnly() {
    val manager = ApiKeyManager()
    val issued = manager.issueKey("default-agent", now = now)

    assertEquals(setOf(ApiKeyScope.READ), issued.record.scopes)
    assertFalse(manager.isAuthorized(issued.plaintextKey, ApiKeyScope.WRITE, now))
  }

  @Test
  fun revokedKeyIsNotAuthorizedForAnyScope() {
    val manager = ApiKeyManager()
    val issued = manager.issueKey("full-agent", ApiKeyScope.entries.toSet(), now = now)
    manager.revoke(issued.record.keyId)

    ApiKeyScope.entries.forEach { scope ->
      assertFalse(manager.isAuthorized(issued.plaintextKey, scope, now))
    }
  }

  @Test
  fun revokeAllDisablesEveryLiveKey() {
    val manager = ApiKeyManager()
    val first = manager.issueKey("agent-one", now = now)
    val second = manager.issueKey("agent-two", now = now)

    assertEquals(2, manager.revokeAll())
    assertEquals(0, manager.activeKeyCount(now))
    assertTrue(manager.validate(first.plaintextKey, now = now) is ApiKeyValidation.Revoked)
    assertTrue(manager.validate(second.plaintextKey, now = now) is ApiKeyValidation.Revoked)
    // Idempotent: nothing left to revoke.
    assertEquals(0, manager.revokeAll())
  }

  @Test
  fun blankLabelAndEmptyScopesAreRejected() {
    val manager = ApiKeyManager()

    assertThrows { manager.issueKey("  ", now = now) }
    assertThrows { manager.issueKey("agent", emptySet(), now = now) }
  }

  @Test
  fun scopeLabelsRoundTrip() {
    ApiKeyScope.entries.forEach { scope ->
      assertEquals(scope, ApiKeyScope.fromLabel(scope.label))
    }
    assertEquals(ApiKeyScope.READ, ApiKeyScope.fromLabel("READ"))
    assertEquals(null, ApiKeyScope.fromLabel("admin"))
  }

  private fun assertThrows(block: () -> Unit) {
    try {
      block()
      throw AssertionError("Expected IllegalArgumentException")
    } catch (expected: IllegalArgumentException) {
      // expected
    }
  }
}
