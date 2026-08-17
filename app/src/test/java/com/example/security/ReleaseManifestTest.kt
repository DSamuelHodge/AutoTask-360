package com.example.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ReleaseManifestTest {
    private val manifest = File("src/main/AndroidManifest.xml").takeIf { it.exists() }
        ?: File("app/src/main/AndroidManifest.xml")
    private val networkConfig = File("src/main/res/xml/network_security_config.xml").takeIf { it.exists() }
        ?: File("app/src/main/res/xml/network_security_config.xml")

    @Test
    fun releaseManifestDoesNotExportInternalServicesOrProvider() {
        val xml = manifest.readText()
        assertTrue(xml.contains("android:usesCleartextTraffic=\"false\""))
        assertTrue(xml.contains("android:networkSecurityConfig=\"@xml/network_security_config\""))
        assertFalse(componentExported(xml, ".service.AutoTaskService"))
        assertFalse(componentExported(xml, ".wa.BrainService"))
        assertFalse(componentExported(xml, ".wa.WhatsAppBridgeService"))
        assertFalse(componentExported(xml, ".wa.WhatsAppBridgeActivity"))
        assertFalse(componentExported(xml, ".provider.AutoTaskContentProvider"))
        assertTrue(componentExported(xml, ".MainActivity"))
        assertTrue(componentExported(xml, ".service.BootReceiver"))
    }

    @Test
    fun releaseNetworkConfigDisablesBroadCleartext() {
        val xml = networkConfig.readText()
        assertTrue(xml.contains("cleartextTrafficPermitted=\"false\""))
        assertTrue(xml.contains("127.0.0.1"))
        assertTrue(xml.contains("localhost"))
    }

    private fun componentExported(xml: String, name: String): Boolean {
        val start = xml.indexOf(name)
        require(start >= 0) { "component $name missing from manifest" }
        val window = xml.substring(start, (start + 400).coerceAtMost(xml.length))
        return window.contains("android:exported=\"true\"")
    }
}
