package com.example.domain

/**
 * Crash-and-resume classification for a step left in [StepStatuses.RUNNING].
 *
 * Inherently safe types may re-enter execute with the same [StepRun.effectId].
 * Dedupe-capable types may also re-enter: the executor consults the effect
 * ledger and skips the side effect when that id already committed OK.
 * Everything else stays fail-closed.
 */
object StepResumePolicy {
    val safeToReenterTypes: Set<String> = setOf("LOG", "WAIT", "TOAST")

    val dedupeCapableTypes: Set<String> = setOf(
        "SEND_SMS",
        "HTTP",
        "NOTIFICATION",
        "SPEAK",
        "CLIPBOARD",
        "WRITE_FILE",
        "READ_FILE",
        "OPEN_URL",
        "SEND_INTENT",
        "LAUNCH_APP",
        "VIBRATE",
        "BROADCAST"
    )

    fun safeToReenter(type: String): Boolean = type.uppercase() in safeToReenterTypes

    fun dedupesByEffectId(type: String): Boolean = type.uppercase() in dedupeCapableTypes

    fun mayReenter(type: String): Boolean =
        safeToReenter(type) || dedupesByEffectId(type)
}
