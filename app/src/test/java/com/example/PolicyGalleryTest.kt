package com.example

import com.example.data.PolicyGalleryEntry
import com.example.data.PolicyGalleryStore
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PolicyGalleryTest {

    @Test
    fun galleryIdsAreUnique() {
        val entries = PolicyGalleryStore.getGallery()
        assertTrue("Expected gallery entries", entries.isNotEmpty())
        val ids = entries.map { it.id }
        assertEquals("Gallery entry ids must be unique", ids.size, ids.toSet().size)
    }

    @Test
    fun everyEntryIsDisabledByDefault() {
        PolicyGalleryStore.getGallery().forEach { entry ->
            assertTrue("${entry.id} must be disabledByDefault", entry.disabledByDefault)
        }
    }

    @Test
    fun everyEntryHasRequiredCapabilitiesAndRiskClass() {
        PolicyGalleryStore.getGallery().forEach { entry ->
            assertTrue("${entry.id} requiredCapabilities must be non-empty", entry.requiredCapabilities.isNotEmpty())
            assertTrue("${entry.id} riskClass must be non-empty", entry.riskClass.isNotBlank())
            assertTrue("${entry.id} category must be non-empty", entry.category.isNotBlank())
        }
    }

    @Test
    fun cloningCreatesDistinctEnabledProfileWithoutClobbering() {
        val existing = AutomationProfileFactory.existing()
        val existingIds = setOf(existing.id)

        val cloned = PolicyGalleryStore.cloneEntry("gallery-meeting-shield", existingIds)
        assertNotNull("Clone should succeed for a new id", cloned)
        cloned!!
        assertEquals("gallery-meeting-shield", cloned.id)
        assertFalse("Cloned profile must start disabled", cloned.isEnabled)

        val duplicate = PolicyGalleryStore.cloneEntry(existing.id, existingIds)
        assertNull("Clone must NOT clobber an existing profile with same id", duplicate)
    }

    @Test
    fun enablingDoesNotMutateGallerySource() {
        val before = PolicyGalleryStore.getEntry("gallery-deep-work-fortress")!!
        val snapshot = before.toJson().toString()

        PolicyGalleryStore.cloneEntry("gallery-deep-work-fortress", emptySet())

        val after = PolicyGalleryStore.getEntry("gallery-deep-work-fortress")!!
        assertEquals("Gallery source must be unchanged after clone", snapshot, after.toJson().toString())
        assertTrue("Gallery source must remain disabled after clone", after.disabledByDefault)
    }
}

private object AutomationProfileFactory {
    fun existing(): com.example.data.AutomationProfile {
        val now = System.currentTimeMillis()
        return com.example.data.AutomationProfile(
            id = "gallery-deep-work-fortress",
            name = "Existing",
            description = "User-edited existing profile",
            isEnabled = true,
            triggerType = "TIME",
            triggerConfigJson = "{}",
            conditionsJson = "{}",
            actionsJson = JSONArray().toString(),
            cooldownMs = 0L,
            priority = 0,
            createdAt = now,
            updatedAt = now
        )
    }
}
