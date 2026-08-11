package com.example.engine

/**
 * Local trust contract: kill switches (#21) and high-risk gate.
 *
 * CANONICAL source of truth for execution + agent-write kill switches. Other modules
 * (AutoTaskEngine, AutoTaskContentProvider, KtorLoopbackServer) must read these flags
 * rather than keeping their own copies. Flags are process-wide @Volatile booleans so a
 * user/operator control can flip them at runtime; they are reversible.
 */
object ExecutionPolicy {
    /** Master execution switch. When false, the engine refuses to run any automation. */
    @Volatile
    var executionEnabled: Boolean = true

    /** Agent policy-write switch. When false, agent/API surfaces reject profile writes. */
    @Volatile
    var agentWritesEnabled: Boolean = true

    /** True only when both execution and agent writes are permitted (high-risk gate). */
    fun isHighRiskAllowed(): Boolean = agentWritesEnabled && executionEnabled

    fun isExecutionAllowed(): Boolean = executionEnabled

    fun isAgentWriteAllowed(): Boolean = agentWritesEnabled

    /** Reversible: restore both switches to their default (enabled) state. */
    fun reset() {
        executionEnabled = true
        agentWritesEnabled = true
    }

    /**
     * Thrown when an agent/API write is attempted while [agentWritesEnabled] is false.
     * Surfaces a clear, machine-readable rejection to the caller.
     */
    class AgentWriteDisabledException(message: String = "Agent policy writes are disabled") :
        IllegalStateException(message)
}
