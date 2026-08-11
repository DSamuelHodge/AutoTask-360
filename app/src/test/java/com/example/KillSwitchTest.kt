package com.example

import com.example.engine.ExecutionPolicy
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KillSwitchTest {

  @After
  fun tearDown() {
    ExecutionPolicy.reset()
  }

  @Test
  fun defaultsAllowExecutionAndAgentWrites() {
    ExecutionPolicy.reset()
    assertTrue(ExecutionPolicy.isExecutionAllowed())
    assertTrue(ExecutionPolicy.isAgentWriteAllowed())
    assertTrue(ExecutionPolicy.isHighRiskAllowed())
  }

  @Test
  fun disablingExecutionBlocksHighRisk() {
    ExecutionPolicy.executionEnabled = false
    assertFalse(ExecutionPolicy.isExecutionAllowed())
    assertFalse(ExecutionPolicy.isHighRiskAllowed())
    // agent writes flag is independent of execution flag
    assertTrue(ExecutionPolicy.isAgentWriteAllowed())
  }

  @Test
  fun disablingAgentWritesBlocksHighRiskButNotExecution() {
    ExecutionPolicy.agentWritesEnabled = false
    assertFalse(ExecutionPolicy.isAgentWriteAllowed())
    assertFalse(ExecutionPolicy.isHighRiskAllowed())
    assertTrue(ExecutionPolicy.isExecutionAllowed())
  }

  @Test
  fun resetRestoresBothSwitches() {
    ExecutionPolicy.executionEnabled = false
    ExecutionPolicy.agentWritesEnabled = false
    ExecutionPolicy.reset()
    assertTrue(ExecutionPolicy.isExecutionAllowed())
    assertTrue(ExecutionPolicy.isAgentWriteAllowed())
  }

  @Test
  fun agentWriteDisabledExceptionIsThrownWithClearMessage() {
    ExecutionPolicy.agentWritesEnabled = false
    val ex = ExecutionPolicy.AgentWriteDisabledException()
    assertTrue(ex.message?.contains("disabled", ignoreCase = true) == true)
  }
}
