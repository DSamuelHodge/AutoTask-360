package com.example.domain

/**
 * Temporal-style activity retry: same [StepRun.effectId], bounded attempts,
 * short backoff. Validation and capability denials are not retried.
 */
object StepRetryPolicy {
    const val DEFAULT_MAX_ATTEMPTS = 3
    const val DEFAULT_INITIAL_BACKOFF_MS = 100L
    const val DEFAULT_MAX_BACKOFF_MS = 400L

    fun retryable(status: String, detail: String): Boolean {
        if (status != StepStatuses.FAILED) return false
        val d = detail.lowercase()
        if (d.contains("required")) return false
        if (d.contains("unknown action")) return false
        if (d.contains("not granted")) return false
        if (d.contains("capability") && d.contains("blocked")) return false
        if (d.contains("invalid")) return false
        return true
    }

    fun backoffMs(
        failedAttempt: Int,
        initialMs: Long = DEFAULT_INITIAL_BACKOFF_MS,
        maxMs: Long = DEFAULT_MAX_BACKOFF_MS
    ): Long {
        if (failedAttempt <= 1) return initialMs.coerceAtMost(maxMs)
        var value = initialMs
        repeat(failedAttempt - 1) {
            val next = value * 2L
            if (next > maxMs || next < value) return maxMs
            value = next
        }
        return value.coerceAtMost(maxMs)
    }
}
