package com.example

import com.example.engine.ActionExecutor
import com.example.engine.StepResult
import org.junit.Assert.assertEquals
import org.junit.Test

class ActionExecutorStatusTest {

  @Test
  fun allOkStepsProduceSuccess() {
    val status = ActionExecutor.finalStatusFor(
      listOf(
        StepResult(0, "TOAST", "OK"),
        StepResult(1, "VIBRATE", "OK")
      )
    )

    assertEquals("SUCCESS", status)
  }

  @Test
  fun mixedOkAndFailureStepsProducePartial() {
    val status = ActionExecutor.finalStatusFor(
      listOf(
        StepResult(0, "TOAST", "OK"),
        StepResult(1, "VIBRATE", "FAILED")
      )
    )

    assertEquals("PARTIAL", status)
  }

  @Test
  fun allFailureStepsProduceFailed() {
    val status = ActionExecutor.finalStatusFor(
      listOf(
        StepResult(0, "BRIGHTNESS", "FAILED"),
        StepResult(1, "DND", "SKIPPED")
      )
    )

    assertEquals("FAILED", status)
  }
}
