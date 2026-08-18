package com.example.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StepResumePolicyTest {

    @Test
    fun onlyLogWaitAndToastMayReenter() {
        assertTrue(StepResumePolicy.safeToReenter("LOG"))
        assertTrue(StepResumePolicy.safeToReenter("wait"))
        assertTrue(StepResumePolicy.safeToReenter("TOAST"))
        assertFalse(StepResumePolicy.safeToReenter("SEND_SMS"))
        assertFalse(StepResumePolicy.safeToReenter("CALL"))
        assertFalse(StepResumePolicy.safeToReenter("HTTP"))
        assertFalse(StepResumePolicy.safeToReenter("UI_DRIVE"))
        assertFalse(StepResumePolicy.safeToReenter("WRITE_FILE"))
        assertFalse(StepResumePolicy.safeToReenter("CAMERA"))
        assertFalse(StepResumePolicy.safeToReenter("SPEAK"))
    }
}
