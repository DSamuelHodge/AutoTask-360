package com.example.security

import kotlin.random.Random

class PairingManager(
    private val store: CredentialStore,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val codeFactory: () -> String = { "%06d".format(Random.nextInt(0, 1_000_000)) },
    private val tokenFactory: () -> String = { TokenHasher.newToken() },
    private val idFactory: () -> String = { TokenHasher.newId() },
    private val ttlMs: Long = DEFAULT_TTL_MS
) {
    @Volatile
    private var challenge: PairingChallenge? = null

    fun start(): PairingChallenge {
        val created = PairingChallenge(code = codeFactory(), expiresAt = clock() + ttlMs)
        challenge = created
        return created
    }

    fun peek(): PairingChallenge? {
        val current = challenge ?: return null
        if (clock() > current.expiresAt) {
            challenge = null
            return null
        }
        return current
    }

    fun complete(
        code: String,
        name: String,
        scopes: Set<AccessScope>,
        approvedActions: Set<String> = emptySet()
    ): IssuedCredential {
        val current = peek() ?: throw PairingException("no active pairing challenge")
        if (code.trim() != current.code) throw PairingException("pairing code mismatch")
        if (scopes.isEmpty()) throw PairingException("at least one scope is required")
        val now = clock()
        val token = tokenFactory()
        val credential = PairedCredential(
            id = idFactory(),
            name = name.ifBlank { "paired-client" },
            tokenHash = TokenHasher.hash(token),
            scopes = scopes,
            approvedActions = approvedActions.map { it.uppercase() }.toSet(),
            createdAt = now
        )
        store.put(credential)
        challenge = null
        return IssuedCredential(credential, token)
    }

    fun list(): List<PairedCredential> = store.list()

    fun revoke(id: String): Boolean = store.revoke(id)

    fun activeCredentials(): List<PairedCredential> = store.list().filter { !it.revoked }

    companion object {
        const val DEFAULT_TTL_MS = 5L * 60L * 1000L
    }
}
