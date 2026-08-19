package com.example.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StepRetryPolicyTest {

    @Test
    fun retryableFailuresAndNonRetryableValidation() {
        assertTrue(StepRetryPolicy.retryable("FAILED", "step_timeout"))
        assertTrue(StepRetryPolicy.retryable("FAILED", "HTTP GET https://x -> 503"))
        assertFalse(StepRetryPolicy.retryable("FAILED", "SMS number and text required"))
        assertFalse(StepRetryPolicy.retryable("FAILED", "sms_radio_timeout (+1555)"))
        assertFalse(StepRetryPolicy.retryable("FAILED", "sms_send_failed:generic_failure (+1555)"))
        assertFalse(StepRetryPolicy.retryable("FAILED", "Unknown action type FOO"))
        assertFalse(StepRetryPolicy.retryable("FAILED", "capability 'SEND_SMS' blocked: not granted"))
        assertFalse(StepRetryPolicy.retryable("OK", "SMS sent"))
        assertFalse(StepRetryPolicy.retryable("SKIPPED", "denied"))
    }

    @Test
    fun backoffDoublesAndCaps() {
        assertEquals(100L, StepRetryPolicy.backoffMs(1))
        assertEquals(200L, StepRetryPolicy.backoffMs(2))
        assertEquals(400L, StepRetryPolicy.backoffMs(3))
        assertEquals(400L, StepRetryPolicy.backoffMs(8))
    }
}
