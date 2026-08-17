package com.example.wa

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.BuildConfig
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * QA instrumented tests for Turso credential storage.
 *
 * [TursoConfig] uses Keystore-backed [androidx.security.crypto.EncryptedSharedPreferences],
 * which cannot run on the host JVM (Robolectric) — so these run on-device where
 * the real Android Keystore is available.
 *
 * Contract under test:
 *  1. explicit values round-trip through encrypted storage and take precedence
 *     over the build-time defaults (`BuildConfig.TURSO_*` from `.env`);
 *  2. when nothing is stored, the build-time defaults are the fallback;
 *  3. [clear] removes the stored override, reverting to build defaults.
 */
@RunWith(AndroidJUnit4::class)
class TursoConfigTest {

  private val context =
    InstrumentationRegistry.getInstrumentation().targetContext

  // Whether this debug build was provisioned with credentials from `.env`.
  private val buildProvisioned =
    BuildConfig.TURSO_URL.isNotEmpty() && BuildConfig.TURSO_TOKEN.isNotEmpty()

  @After
  fun tearDown() {
    TursoConfig.clear(context)
  }

  @Test
  fun explicitValuesRoundTripAndOverrideBuildDefaults() {
    val url = "https://override.example.turso.io"
    val token = "override-token-abc123"

    TursoConfig.setUrl(context, url)
    TursoConfig.setToken(context, token)

    assertEquals(url, TursoConfig.getUrl(context))
    assertEquals(token, TursoConfig.getToken(context))
    assertTrue(TursoConfig.isConfigured(context))
  }

  @Test
  fun isConfiguredMatchesResolvableCredentials() {
    TursoConfig.clear(context)

    // With no stored override, both build defaults (or both absent) decide.
    assertEquals(buildProvisioned, TursoConfig.isConfigured(context))
  }

  @Test
  fun clearRevertsToBuildDefaults() {
    TursoConfig.setUrl(context, "https://override.example.turso.io")
    TursoConfig.setToken(context, "override-token")
    assertTrue(TursoConfig.isConfigured(context))

    TursoConfig.clear(context)

    assertEquals(BuildConfig.TURSO_URL, TursoConfig.getUrl(context))
    assertEquals(BuildConfig.TURSO_TOKEN, TursoConfig.getToken(context))
  }
}
