package com.example

import com.example.server.KtorServerConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KtorServerConfigTest {

  @Test
  fun defaultServerPortIsValid() {
    assertNull(KtorServerConfig.validatePort(KtorServerConfig.DEFAULT_PORT))
  }

  @Test
  fun listenerPortIsReserved() {
    assertEquals(
      "Port ${KtorServerConfig.LISTENER_PORT} is reserved for the listener.",
      KtorServerConfig.validatePort(KtorServerConfig.LISTENER_PORT)
    )
  }

  @Test
  fun privilegedAndOutOfRangePortsAreRejected() {
    assertEquals("Port must be between 1024 and 65535.", KtorServerConfig.validatePort(80))
    assertEquals("Port must be between 1024 and 65535.", KtorServerConfig.validatePort(70_000))
  }
}
