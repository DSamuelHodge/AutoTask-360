package com.example

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import com.example.engine.CapabilityProvider
import com.example.engine.SchemaProvider
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CapabilityProviderTest {

  @Test
  fun manifestDeclaresAutomationPermissionSurface() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val packageInfo = context.packageManager.getPackageInfo(
      context.packageName,
      PackageManager.GET_PERMISSIONS
    )
    val declared = packageInfo.requestedPermissions?.toSet().orEmpty()

    val expected = setOf(
      Manifest.permission.ACCESS_NETWORK_STATE,
      Manifest.permission.FOREGROUND_SERVICE,
      Manifest.permission.RECEIVE_BOOT_COMPLETED,
      Manifest.permission.WAKE_LOCK,
      Manifest.permission.SCHEDULE_EXACT_ALARM,
      Manifest.permission.USE_EXACT_ALARM,
      Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
      Manifest.permission.POST_NOTIFICATIONS,
      Manifest.permission.ACCESS_NOTIFICATION_POLICY,
      Manifest.permission.RECEIVE_SMS,
      Manifest.permission.READ_SMS,
      Manifest.permission.SEND_SMS,
      Manifest.permission.READ_PHONE_STATE,
      Manifest.permission.READ_PHONE_NUMBERS,
      Manifest.permission.CALL_PHONE,
      Manifest.permission.READ_CALL_LOG,
      Manifest.permission.READ_CONTACTS,
      Manifest.permission.READ_CALENDAR,
      Manifest.permission.WRITE_CALENDAR,
      Manifest.permission.ACCESS_FINE_LOCATION,
      Manifest.permission.ACCESS_COARSE_LOCATION,
      Manifest.permission.ACCESS_BACKGROUND_LOCATION,
      Manifest.permission.ACTIVITY_RECOGNITION,
      Manifest.permission.ACCESS_WIFI_STATE,
      Manifest.permission.CHANGE_WIFI_STATE,
      Manifest.permission.CHANGE_NETWORK_STATE,
      Manifest.permission.BLUETOOTH_CONNECT,
      Manifest.permission.BLUETOOTH_SCAN,
      Manifest.permission.BLUETOOTH_ADVERTISE,
      Manifest.permission.NFC,
      Manifest.permission.CAMERA,
      Manifest.permission.RECORD_AUDIO,
      Manifest.permission.MODIFY_AUDIO_SETTINGS,
      Manifest.permission.SYSTEM_ALERT_WINDOW,
      Manifest.permission.WRITE_SETTINGS,
      Manifest.permission.PACKAGE_USAGE_STATS,
      Manifest.permission.MANAGE_EXTERNAL_STORAGE
    )

    expected.forEach { permission ->
      assertTrue("$permission should be declared", permission in declared)
    }
  }

  @Test
  fun schemaPointsAgentsToCapabilitiesEndpointAndPermissionRequirements() {
    val schema = JSONObject(SchemaProvider.getSchemaJson())
    assertTrue(schema.getString("capabilitiesEndpoint") == "/v1/capabilities")

    val actions = schema.getJSONObject("actionTypes")
    assertTrue(actions.getJSONObject("DND").getJSONArray("requirements").length() > 0)
    assertTrue(actions.getJSONObject("AUDIO").getString("notes").contains("ringerMode=silent"))
    assertTrue(actions.getJSONObject("SEND_SMS").getString("autonomy") == "confirm_required")
  }

  @Test
  fun capabilitiesExposePermissionGroupsAndAgentPolicy() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val capabilities = JSONObject(CapabilityProvider.getCapabilitiesJson(context))

    assertTrue(capabilities.has("declaredPermissions"))
    assertTrue(capabilities.has("specialAccess"))
    assertTrue(capabilities.has("runtimePermissions"))
    assertTrue(capabilities.has("triggerRequirements"))
    assertTrue(capabilities.has("provisioningHints"))
    assertTrue(capabilities.getJSONObject("actions").has("DND"))
    val safeReadEndpoints = capabilities.getJSONObject("agentPolicy").getJSONArray("safeReadEndpoints")
    val hasCapabilitiesEndpoint = (0 until safeReadEndpoints.length()).any { i ->
      safeReadEndpoints.getString(i) == "/v1/capabilities"
    }
    assertTrue(hasCapabilitiesEndpoint)
  }
}
