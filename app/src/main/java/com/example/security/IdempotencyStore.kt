package com.example.security

class IdempotencyStore(
    private val ttlMs: Long = DEFAULT_TTL_MS,
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
    private val clock: () -> Long = { System.currentTimeMillis() }
) {
    data class CachedResponse(
        val status: Int,
        val body: String,
        val storedAt: Long
    )

    private val entries = linkedMapOf<String, CachedResponse>()

    @Synchronized
    fun get(key: String): CachedResponse? {
        prune()
        val cached = entries[key] ?: return null
        if (clock() - cached.storedAt > ttlMs) {
            entries.remove(key)
            return null
        }
        return cached
    }

    @Synchronized
    fun put(key: String, status: Int, body: String) {
        prune()
        if (entries.size >= maxEntries) {
            val oldest = entries.keys.firstOrNull()
            if (oldest != null) entries.remove(oldest)
        }
        entries[key] = CachedResponse(status, body, clock())
    }

    @Synchronized
    fun reset() {
        entries.clear()
    }

    private fun prune() {
        val now = clock()
        val expired = entries.filterValues { now - it.storedAt > ttlMs }.keys
        expired.forEach { entries.remove(it) }
    }

    companion object {
        const val DEFAULT_TTL_MS = 24L * 60L * 60L * 1000L
        const val DEFAULT_MAX_ENTRIES = 100
    }
}
