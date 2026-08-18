package com.example.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StepResumePolicyTest {

    @Test
    fun onlyLogWaitAndToastAreInherentlySafe() {
        assertTrue(StepResumePolicy.safeToReenter("LOG"))
        assertTrue(StepResumePolicy.safeToReenter("wait"))
        assertTrue(StepResumePolicy.safeToReenter("TOAST"))
        assertFalse(StepResumePolicy.safeToReenter("SEND_SMS"))
        assertFalse(StepResumePolicy.safeToReenter("HTTP"))
        assertFalse(StepResumePolicy.safeToReenter("CAMERA"))
    }

    @Test
    fun dedupeCapableTypesMayReenter() {
        assertTrue(StepResumePolicy.mayReenter("SEND_SMS"))
        assertTrue(StepResumePolicy.mayReenter("HTTP"))
        assertTrue(StepResumePolicy.mayReenter("WRITE_FILE"))
        assertTrue(StepResumePolicy.dedupesByEffectId("send_sms"))
        assertFalse(StepResumePolicy.mayReenter("CAMERA"))
        assertFalse(StepResumePolicy.mayReenter("WIFI_ACTION"))
        assertFalse(StepResumePolicy.dedupesByEffectId("CAMERA"))
    }
}
