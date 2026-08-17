package com.example.security

class RateLimiter(
    private val maxPerWindow: Int = DEFAULT_MAX_PER_WINDOW,
    private val windowMs: Long = DEFAULT_WINDOW_MS,
    private val clock: () -> Long = { System.currentTimeMillis() }
) {
    private val hits = linkedMapOf<String, MutableList<Long>>()

    @Synchronized
    fun allow(key: String): RateLimitResult {
        val now = clock()
        val windowStart = now - windowMs
        val timestamps = hits.getOrPut(key) { mutableListOf() }
        timestamps.removeAll { it < windowStart }
        if (timestamps.size >= maxPerWindow) {
            val retryAfterMs = (timestamps.first() + windowMs - now).coerceAtLeast(1L)
            return RateLimitResult(false, retryAfterMs)
        }
        timestamps += now
        return RateLimitResult(true, 0L)
    }

    @Synchronized
    fun reset() {
        hits.clear()
    }

    data class RateLimitResult(val allowed: Boolean, val retryAfterMs: Long)

    companion object {
        const val DEFAULT_MAX_PER_WINDOW = 60
        const val DEFAULT_WINDOW_MS = 60_000L
    }
}
