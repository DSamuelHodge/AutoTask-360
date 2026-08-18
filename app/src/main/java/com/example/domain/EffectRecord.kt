package com.example.domain

/**
 * Committed side-effect for one [StepRun.effectId]. Handlers that
 * [StepResumePolicy.dedupesByEffectId] skip work when a row is OK.
 */
data class EffectRecord(
    val effectId: String,
    val type: String,
    val status: String,
    val detail: String = "",
    val runId: String? = null,
    val stepIndex: Int? = null,
    val completedAt: Long
)
