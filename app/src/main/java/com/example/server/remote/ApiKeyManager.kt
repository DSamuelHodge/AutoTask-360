package com.example.server.remote

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Opt-in remote API-key manager — ALPHA scaffold (#23).
 *
 * Pure Kotlin/JDK: no Android [android.content.Context], no Keystore, no I/O. That keeps it
 * unit-testable on the JVM and keeps the trust-critical logic (issue / validate / revoke)
 * independent of storage. Storage is deliberately a separate, later concern.
 *
 * Design (see docs/REMOTE_COS_THREAT_MODEL.md T7):
 *  - A key is `<keyId>.<secret>`; only an HMAC-SHA256 verifier over the secret is retained,
 *    so a dump of manager state does not yield usable keys (R7.3).
 *  - Secrets come from [SecureRandom] with 256 bits of entropy, never from device identifiers
 *    (R7.2).
 *  - Verification is constant-time via [MessageDigest.isEqual] (R7.9).
 *  - Revoked key ids are retained and never reissued, so audit entries stay attributable (R7.7).
 *  - Revocation is local and immediate — no network round-trip (R7.6).
 *
 * TODO(#2): persist [ApiKeyRecord]s and the HMAC pepper in Android Keystore /
 *  EncryptedSharedPreferences (R7.10). This scaffold holds them in memory only, so keys do not
 *  survive process death. That is intentional for an alpha: nothing durable is created before
 *  the storage story is settled.
 * TODO(#2): enforce [ApiKeyRecord.expiresAt] against a real clock source and surface
 *  `lastUsedAt` in the UI.
 * TODO(#24): emit an audit event on every validate/revoke with the key id (never the key).
 */

/** Capability scopes carried by a key. Mirrors `McpScope` in the MCP tool schema (#22). */
enum class ApiKeyScope(val label: String) {
    READ("read"),
    WRITE("write"),
    EXECUTE("execute");

    companion object {
        fun fromLabel(label: String): ApiKeyScope? =
            entries.firstOrNull { it.label.equals(label, ignoreCase = true) }
    }
}

/**
 * Stored metadata for one issued key. Contains a [verifier], never the secret itself.
 *
 * @param keyId Public, non-secret identifier. Safe to log and to show in audit entries.
 * @param label Human-readable client name, e.g. "laptop-agent".
 * @param scopes Scopes bound at issuance. Not widenable through the API (R5.4).
 * @param verifier Base16 HMAC-SHA256 of the secret under the manager pepper.
 * @param createdAt Issuance time, epoch millis.
 * @param expiresAt Expiry, epoch millis; null means no expiry (discouraged, see R7.5).
 * @param revoked Whether the key has been revoked. Records are kept after revocation (R7.7).
 */
data class ApiKeyRecord(
    val keyId: String,
    val label: String,
    val scopes: Set<ApiKeyScope>,
    val verifier: String,
    val createdAt: Long,
    val expiresAt: Long? = null,
    val revoked: Boolean = false
)

/** Result of issuing a key. [plaintextKey] is returned exactly once and never stored (R7.3). */
data class IssuedApiKey(
    val record: ApiKeyRecord,
    val plaintextKey: String
)

/** Outcome of validating a presented key. */
sealed class ApiKeyValidation {
    data class Valid(val record: ApiKeyRecord) : ApiKeyValidation()
    data class Revoked(val keyId: String) : ApiKeyValidation()
    data class Expired(val keyId: String) : ApiKeyValidation()

    /** Malformed, unknown key id, or verifier mismatch — deliberately not distinguished. */
    object Invalid : ApiKeyValidation()
}

class ApiKeyManager(
    /** HMAC pepper. TODO(#2): source from Android Keystore rather than a generated default. */
    pepper: ByteArray = generatePepper(),
    private val random: SecureRandom = SecureRandom()
) {
    private val pepper: ByteArray = pepper.copyOf()
    private val records = LinkedHashMap<String, ApiKeyRecord>()

    /**
     * Issue a new key.
     *
     * @param label Human-readable client name.
     * @param scopes Scopes to bind. Defaults to [ApiKeyScope.READ] only — `execute` is never
     *   implied and must be requested explicitly (R5.2).
     * @param now Clock injection point for tests.
     * @param ttlMillis Lifetime; defaults to [DEFAULT_TTL_MILLIS] (90 days, R7.5).
     * @return the record plus the one-time plaintext key.
     */
    fun issueKey(
        label: String,
        scopes: Set<ApiKeyScope> = setOf(ApiKeyScope.READ),
        now: Long = System.currentTimeMillis(),
        ttlMillis: Long? = DEFAULT_TTL_MILLIS
    ): IssuedApiKey {
        require(label.isNotBlank()) { "label must not be blank" }
        require(scopes.isNotEmpty()) { "at least one scope is required" }

        val keyId = newKeyId()
        val secret = ByteArray(SECRET_BYTES).also { random.nextBytes(it) }
        val secretHex = secret.encodeHex()

        val record = ApiKeyRecord(
            keyId = keyId,
            label = label,
            scopes = scopes.toSet(),
            verifier = verifierFor(secretHex),
            createdAt = now,
            expiresAt = ttlMillis?.let { now + it }
        )
        records[keyId] = record

        return IssuedApiKey(record = record, plaintextKey = "$keyId$KEY_SEPARATOR$secretHex")
    }

    /**
     * Validate a presented key. The verifier comparison is constant-time (R7.9), and an unknown
     * key id is reported identically to a wrong secret so the response body is not an
     * enumeration oracle.
     *
     * TODO(#24): the early return on an unknown key id skips the HMAC, so *timing* still
     *  distinguishes "unknown key id" from "known id, wrong secret". Key ids are 64 bits of
     *  CSPRNG output so this is a weak oracle, but the remote path should compute a dummy HMAC
     *  on the miss branch to equalise timing before this is exposed to a network attacker.
     */
    fun validate(presentedKey: String, now: Long = System.currentTimeMillis()): ApiKeyValidation {
        val separator = presentedKey.indexOf(KEY_SEPARATOR)
        if (separator <= 0 || separator == presentedKey.length - 1) return ApiKeyValidation.Invalid

        val keyId = presentedKey.substring(0, separator)
        val secretHex = presentedKey.substring(separator + 1)
        val record = records[keyId] ?: return ApiKeyValidation.Invalid

        val expected = record.verifier.toByteArray(Charsets.US_ASCII)
        val actual = verifierFor(secretHex).toByteArray(Charsets.US_ASCII)
        if (!MessageDigest.isEqual(expected, actual)) return ApiKeyValidation.Invalid

        // Only report revoked/expired once the secret is proven, so the states are not probeable.
        if (record.revoked) return ApiKeyValidation.Revoked(keyId)
        val expiresAt = record.expiresAt
        if (expiresAt != null && now >= expiresAt) return ApiKeyValidation.Expired(keyId)

        return ApiKeyValidation.Valid(record)
    }

    /** True when the key is valid *and* carries [scope]. Scope failure is not a validity failure. */
    fun isAuthorized(
        presentedKey: String,
        scope: ApiKeyScope,
        now: Long = System.currentTimeMillis()
    ): Boolean {
        val validation = validate(presentedKey, now)
        return validation is ApiKeyValidation.Valid && scope in validation.record.scopes
    }

    /**
     * Revoke by key id. Immediate and local (R7.6). The record is retained, not deleted, so the
     * id is never reissued and audit history stays attributable (R7.7).
     *
     * @return true if a live key was revoked; false if unknown or already revoked.
     */
    fun revoke(keyId: String): Boolean {
        val record = records[keyId] ?: return false
        if (record.revoked) return false
        records[keyId] = record.copy(revoked = true)
        return true
    }

    /** Revoke every live key — the "disable remote mode" path (R7.11). @return count revoked. */
    fun revokeAll(): Int = records.keys.toList().count { revoke(it) }

    /** Metadata for all keys, live and revoked. Never includes secrets. */
    fun listKeys(): List<ApiKeyRecord> = records.values.toList()

    fun findKey(keyId: String): ApiKeyRecord? = records[keyId]

    /** Count of keys that are neither revoked nor expired. */
    fun activeKeyCount(now: Long = System.currentTimeMillis()): Int =
        records.values.count { record ->
            !record.revoked && (record.expiresAt == null || now < record.expiresAt)
        }

    private fun newKeyId(): String {
        val bytes = ByteArray(KEY_ID_BYTES).also { random.nextBytes(it) }
        return KEY_ID_PREFIX + bytes.encodeHex()
    }

    private fun verifierFor(secretHex: String): String {
        val mac = Mac.getInstance(HMAC_ALGORITHM)
        mac.init(SecretKeySpec(pepper, HMAC_ALGORITHM))
        return mac.doFinal(secretHex.toByteArray(Charsets.US_ASCII)).encodeHex()
    }

    companion object {
        /** Separates the public key id from the secret in a presented key. */
        const val KEY_SEPARATOR = '.'

        /** Prefix so keys are recognisable in logs and bug reports. */
        const val KEY_ID_PREFIX = "atk_"

        /** 256 bits of secret entropy (R7.2). */
        const val SECRET_BYTES = 32

        const val KEY_ID_BYTES = 8

        const val HMAC_ALGORITHM = "HmacSHA256"

        const val PEPPER_BYTES = 32

        /** Default key lifetime: 90 days (R7.5). */
        const val DEFAULT_TTL_MILLIS: Long = 90L * 24 * 60 * 60 * 1000

        /** TODO(#2): replace with a Keystore-backed pepper that survives process death. */
        fun generatePepper(random: SecureRandom = SecureRandom()): ByteArray =
            ByteArray(PEPPER_BYTES).also { random.nextBytes(it) }
    }
}

private fun ByteArray.encodeHex(): String {
    val out = StringBuilder(size * 2)
    for (byte in this) {
        val value = byte.toInt() and 0xFF
        out.append(HEX_DIGITS[value ushr 4])
        out.append(HEX_DIGITS[value and 0x0F])
    }
    return out.toString()
}

private const val HEX_DIGITS = "0123456789abcdef"
