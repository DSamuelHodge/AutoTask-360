package com.example.domain

/**
 * Crash-and-resume classification for a step left in [StepStatuses.RUNNING].
 *
 * Safe types may re-enter [com.example.engine.actions.ActionHandler.execute]
 * with the same [StepRun.effectId]. Everything else is fail-closed:
 * the coordinator must not call execute again.
 */
object StepResumePolicy {
    val safeToReenterTypes: Set<String> = setOf("LOG", "WAIT", "TOAST")

    fun safeToReenter(type: String): Boolean = type.uppercase() in safeToReenterTypes
}
