package com.example.engine

import android.Manifest
import android.app.AppOpsManager
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.example.service.AutoTaskNotificationListener
import org.json.JSONArray
import org.json.JSONObject

object CapabilityProvider {
    private const val NOTIFICATION_POLICY_ACCESS = "android.permission.ACCESS_NOTIFICATION_POLICY"

    fun getCapabilitiesJson(context: Context): String {
        val root = JSONObject()
        root.put("service", "AutoTask Tool Server Engine")
        root.put("version", com.example.BuildConfig.VERSION_NAME)
        root.put("versionCode", com.example.BuildConfig.VERSION_CODE)
        root.put("packageName", context.packageName)
        root.put("permissionSummary", permissionSummaryJson(context))
        root.put("declaredPermissions", declaredPermissionsJson(context))
        root.put("specialAccess", specialAccessJson(context))
        root.put("runtimePermissions", runtimePermissionsJson(context))
        root.put("triggerRequirements", triggerRequirementsJson())
        root.put("actions", actionCapabilitiesJson(context))
        root.put("provisioningHints", provisioningHintsJson(context))
        root.put("agentPolicy", agentPolicyJson())
        return root.toString(2)
    }

    fun permissionSummary(context: Context): Map<String, Any> {
        val notificationPolicyDeclared = isPermissionDeclared(context, NOTIFICATION_POLICY_ACCESS)
        val notificationPolicyGranted = isNotificationPolicyAccessGranted(context)
        val writeSettingsDeclared = isPermissionDeclared(context, Manifest.permission.WRITE_SETTINGS)
        val writeSettingsGranted = Settings.System.canWrite(context)
        val notificationListenerEnabled = isNotificationListenerEnabled(context)

        return mapOf(
            "notification_policy_declared" to notificationPolicyDeclared,
            "notification_policy_granted" to notificationPolicyGranted,
            "write_settings_declared" to writeSettingsDeclared,
            "write_settings_granted" to writeSettingsGranted,
            "exact_alarm_declared" to isPermissionDeclared(context, Manifest.permission.SCHEDULE_EXACT_ALARM),
            "exact_alarm_granted" to canScheduleExactAlarms(context),
            "usage_stats_declared" to isPermissionDeclared(context, Manifest.permission.PACKAGE_USAGE_STATS),
            "usage_stats_granted" to isUsageStatsAccessGranted(context),
            "manage_external_storage_declared" to isPermissionDeclared(context, Manifest.permission.MANAGE_EXTERNAL_STORAGE),
            "manage_external_storage_granted" to isManageExternalStorageGranted(),
            "ignore_battery_optimizations_declared" to isPermissionDeclared(context, Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS),
            "system_alert_window_declared" to isPermissionDeclared(context, Manifest.permission.SYSTEM_ALERT_WINDOW),
            "system_alert_window_granted" to isSystemAlertWindowGranted(context),
            "post_notifications_granted" to isRuntimePermissionGranted(context, Manifest.permission.POST_NOTIFICATIONS, Build.VERSION_CODES.TIRAMISU),
            "send_sms_granted" to isRuntimePermissionGranted(context, Manifest.permission.SEND_SMS),
            "receive_sms_granted" to isRuntimePermissionGranted(context, Manifest.permission.RECEIVE_SMS),
            "read_sms_granted" to isRuntimePermissionGranted(context, Manifest.permission.READ_SMS),
            "read_phone_state_granted" to isRuntimePermissionGranted(context, Manifest.permission.READ_PHONE_STATE),
            "call_phone_granted" to isRuntimePermissionGranted(context, Manifest.permission.CALL_PHONE),
            "read_contacts_granted" to isRuntimePermissionGranted(context, Manifest.permission.READ_CONTACTS),
            "read_calendar_granted" to isRuntimePermissionGranted(context, Manifest.permission.READ_CALENDAR),
            "write_calendar_granted" to isRuntimePermissionGranted(context, Manifest.permission.WRITE_CALENDAR),
            "camera_granted" to isRuntimePermissionGranted(context, Manifest.permission.CAMERA),
            "record_audio_granted" to isRuntimePermissionGranted(context, Manifest.permission.RECORD_AUDIO),
            "bluetooth_connect_granted" to isRuntimePermissionGranted(context, Manifest.permission.BLUETOOTH_CONNECT, Build.VERSION_CODES.S),
            "bluetooth_scan_granted" to isRuntimePermissionGranted(context, Manifest.permission.BLUETOOTH_SCAN, Build.VERSION_CODES.S),
            "bluetooth_advertise_granted" to isRuntimePermissionGranted(context, Manifest.permission.BLUETOOTH_ADVERTISE, Build.VERSION_CODES.S),
            "fine_location_granted" to isRuntimePermissionGranted(context, Manifest.permission.ACCESS_FINE_LOCATION),
            "coarse_location_granted" to isRuntimePermissionGranted(context, Manifest.permission.ACCESS_COARSE_LOCATION),
            "background_location_granted" to isRuntimePermissionGranted(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION, Build.VERSION_CODES.Q),
            "activity_recognition_granted" to isRuntimePermissionGranted(context, Manifest.permission.ACTIVITY_RECOGNITION, Build.VERSION_CODES.Q),
            "notification_listener_enabled" to notificationListenerEnabled,
            "accessibility_enabled" to isAccessibilityEnabled(context),
            "dnd_ready" to (notificationPolicyDeclared && notificationPolicyGranted),
            "device_settings_ready" to (writeSettingsDeclared && writeSettingsGranted)
        )
    }

    fun isNotificationPolicyAccessGranted(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.isNotificationPolicyAccessGranted
        } else {
            true
        }
    }

    fun isPermissionDeclared(context: Context, permission: String): Boolean {
        return try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
            }
            packageInfo.requestedPermissions?.contains(permission) == true
        } catch (_: Exception) {
            false
        }
    }

    private fun permissionSummaryJson(context: Context): JSONObject {
        val obj = JSONObject()
        permissionSummary(context).forEach { (key, value) -> obj.put(key, value) }
        return obj
    }

    private fun actionCapabilitiesJson(context: Context): JSONObject {
        val summary = permissionSummary(context)
        val actions = JSONObject()

        fun addAction(type: String, risk: String, autonomy: String, ready: Boolean, requirements: List<String>, notes: String) {
            val obj = JSONObject()
            obj.put("risk", risk)
            obj.put("autonomy", autonomy)
            obj.put("ready", ready)
            obj.put("requirements", JSONArray(requirements))
            obj.put("notes", notes)
            actions.put(type, obj)
        }

        val dndReady = summary["dnd_ready"] == true
        val deviceSettingsReady = summary["device_settings_ready"] == true

        addAction(
            "DND",
            "elevated",
            "confirm_or_policy_allowed",
            dndReady,
            listOf("manifest:android.permission.ACCESS_NOTIFICATION_POLICY", "runtime:notification_policy_access"),
            "Controls Do Not Disturb interruption filter. Android requires Notification Policy Access; app-op/settings entries alone may not be sufficient on hardened devices."
        )
        addAction(
            "AUDIO",
            "medium",
            "confirm_or_policy_allowed",
            dndReady,
            listOf("runtime:notification_policy_access_when_ringerMode_silent"),
            "Volume changes are generally available, but ringerMode=silent can require DND access on Android 13+."
        )
        addAction(
            "BRIGHTNESS",
            "medium",
            "policy_allowed",
            deviceSettingsReady,
            listOf("manifest:android.permission.WRITE_SETTINGS", "appop:android:write_settings"),
            "Requires WRITE_SETTINGS for system brightness changes."
        )
        addAction(
            "SCREEN_TIMEOUT",
            "medium",
            "policy_allowed",
            deviceSettingsReady,
            listOf("manifest:android.permission.WRITE_SETTINGS", "appop:android:write_settings"),
            "Requires WRITE_SETTINGS for system display timeout changes."
        )
        addAction(
            "ROTATION",
            "medium",
            "policy_allowed",
            deviceSettingsReady,
            listOf("manifest:android.permission.WRITE_SETTINGS", "appop:android:write_settings"),
            "Requires WRITE_SETTINGS for rotation state changes."
        )
        addAction(
            "SEND_SMS",
            "high",
            "confirm_required",
            summary["send_sms_granted"] == true,
            listOf("runtime:android.permission.SEND_SMS"),
            "Sends billable or carrier-mediated messages; agents should request confirmation unless a device-owner policy explicitly allows it."
        )
        addAction(
            "CALL",
            "high",
            "confirm_required",
            true,
            listOf("intent:android.intent.action.DIAL", "runtime:android.permission.CALL_PHONE_for_direct_call_future"),
            "Current implementation opens the dialer. Direct call execution would require CALL_PHONE and explicit confirmation."
        )
        addAction(
            "NOTIFICATION",
            "low",
            "autonomous_allowed",
            summary["post_notifications_granted"] == true,
            listOf("runtime:android.permission.POST_NOTIFICATIONS"),
            "Posts local status notifications."
        )
        addAction(
            "FLASHLIGHT",
            "medium",
            "policy_allowed",
            summary["camera_granted"] == true,
            listOf("runtime:android.permission.CAMERA"),
            "Controls the device torch through CameraManager."
        )
        addAction(
            "CAMERA",
            "high",
            "confirm_required",
            summary["camera_granted"] == true,
            listOf("runtime:android.permission.CAMERA", "runtime:android.permission.RECORD_AUDIO_for_video"),
            "Camera capture actions should require confirmation unless a device-owner policy explicitly allows them."
        )
        addAction(
            "READ_FILE",
            "medium",
            "policy_allowed",
            true,
            listOf("app-private-storage", "special:MANAGE_EXTERNAL_STORAGE_for_shared_storage_future"),
            "Current implementation reads app-private files or direct paths available to the app sandbox."
        )
        addAction(
            "WRITE_FILE",
            "medium",
            "policy_allowed",
            true,
            listOf("app-private-storage", "special:MANAGE_EXTERNAL_STORAGE_for_shared_storage_future"),
            "Current implementation writes app-private files or direct paths available to the app sandbox."
        )

        return actions
    }

    private fun declaredPermissionsJson(context: Context): JSONObject {
        val groups = linkedMapOf(
            "core" to listOf(
                Manifest.permission.INTERNET,
                Manifest.permission.ACCESS_NETWORK_STATE,
                Manifest.permission.FOREGROUND_SERVICE,
                Manifest.permission.RECEIVE_BOOT_COMPLETED,
                Manifest.permission.WAKE_LOCK
            ),
            "scheduling" to listOf(
                Manifest.permission.SCHEDULE_EXACT_ALARM,
                Manifest.permission.USE_EXACT_ALARM,
                Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
            ),
            "notifications" to listOf(
                Manifest.permission.POST_NOTIFICATIONS,
                NOTIFICATION_POLICY_ACCESS
            ),
            "telephony" to listOf(
                Manifest.permission.RECEIVE_SMS,
                Manifest.permission.READ_SMS,
                Manifest.permission.SEND_SMS,
                Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.READ_PHONE_NUMBERS,
                Manifest.permission.CALL_PHONE,
                Manifest.permission.READ_CALL_LOG
            ),
            "people_calendar" to listOf(
                Manifest.permission.READ_CONTACTS,
                Manifest.permission.READ_CALENDAR,
                Manifest.permission.WRITE_CALENDAR
            ),
            "location_activity" to listOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION,
                Manifest.permission.ACTIVITY_RECOGNITION
            ),
            "connectivity" to listOf(
                Manifest.permission.ACCESS_WIFI_STATE,
                Manifest.permission.CHANGE_WIFI_STATE,
                Manifest.permission.CHANGE_NETWORK_STATE,
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.NFC
            ),
            "media_storage" to listOf(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.MODIFY_AUDIO_SETTINGS,
                Manifest.permission.MANAGE_EXTERNAL_STORAGE,
                Manifest.permission.SYSTEM_ALERT_WINDOW
            ),
            "special_settings" to listOf(
                Manifest.permission.WRITE_SETTINGS,
                Manifest.permission.PACKAGE_USAGE_STATS
            )
        )

        val root = JSONObject()
        groups.forEach { (group, permissions) ->
            val arr = JSONArray()
            permissions.forEach { permission ->
                val obj = JSONObject()
                obj.put("name", permission)
                obj.put("declared", isPermissionDeclared(context, permission))
                arr.put(obj)
            }
            root.put(group, arr)
        }
        return root
    }

    private fun specialAccessJson(context: Context): JSONObject {
        val summary = permissionSummary(context)
        val obj = JSONObject()
        obj.put("notification_policy_access", specialAccessEntry(summary["notification_policy_granted"] == true, "Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS", "Required for DND and silent ringer changes."))
        obj.put("write_settings", specialAccessEntry(summary["write_settings_granted"] == true, "Settings.ACTION_MANAGE_WRITE_SETTINGS", "Required for brightness, screen timeout, and rotation."))
        obj.put("usage_stats", specialAccessEntry(summary["usage_stats_granted"] == true, "Settings.ACTION_USAGE_ACCESS_SETTINGS", "Required for foreground app polling."))
        obj.put("manage_external_storage", specialAccessEntry(summary["manage_external_storage_granted"] == true, "Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION", "Required only for broad shared-storage file operations."))
        obj.put("exact_alarm", specialAccessEntry(summary["exact_alarm_granted"] == true, "Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM", "Required for exact time alarms on Android 12+."))
        obj.put("notification_listener", specialAccessEntry(summary["notification_listener_enabled"] == true, "Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS", "Required for notification trigger ingestion."))
        obj.put("accessibility", specialAccessEntry(summary["accessibility_enabled"] == true, "Settings.ACTION_ACCESSIBILITY_SETTINGS", "The 'eyes and hands' layer: screen reads, taps, typing, global actions for cross-app awareness and driving."))
        obj.put("draw_over_apps", specialAccessEntry(summary["system_alert_window_granted"] == true, "Settings.ACTION_MANAGE_OVERLAY_PERMISSION", "Reserved for future overlay/assistive UI surfaces."))
        return obj
    }

    private fun specialAccessEntry(granted: Boolean, settingsAction: String, notes: String): JSONObject {
        val obj = JSONObject()
        obj.put("granted", granted)
        obj.put("settingsAction", settingsAction)
        obj.put("grantMode", "user_or_device_owner")
        obj.put("notes", notes)
        return obj
    }

    private fun runtimePermissionsJson(context: Context): JSONObject {
        val permissions = listOf(
            Manifest.permission.POST_NOTIFICATIONS,
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
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )
        val root = JSONObject()
        permissions.forEach { permission ->
            val obj = JSONObject()
            obj.put("declared", isPermissionDeclared(context, permission))
            obj.put("granted", isRuntimePermissionGranted(context, permission))
            obj.put("grantMode", "runtime_or_device_owner")
            root.put(permission, obj)
        }
        return root
    }

    private fun triggerRequirementsJson(): JSONObject {
        val root = JSONObject()
        fun add(type: String, requirements: List<String>) {
            root.put(type, JSONArray(requirements))
        }
        add("TIME", listOf("special:SCHEDULE_EXACT_ALARM_or_USE_EXACT_ALARM"))
        add("SCHEDULE", listOf("androidx.work"))
        add("SUNRISE_SUNSET", listOf("runtime:ACCESS_FINE_LOCATION_or_ACCESS_COARSE_LOCATION", "special:SCHEDULE_EXACT_ALARM"))
        add("WIFI", listOf("manifest:ACCESS_WIFI_STATE", "runtime:ACCESS_FINE_LOCATION_for_ssid_on_recent_android"))
        add("BLUETOOTH", listOf("runtime:BLUETOOTH_CONNECT_on_android_12_plus"))
        add("BLUETOOTH_STATE", listOf("runtime:BLUETOOTH_CONNECT_on_android_12_plus"))
        add("APP_LAUNCH", listOf("service:AccessibilityService_or_special:PACKAGE_USAGE_STATS"))
        add("FOREGROUND_APP", listOf("special:PACKAGE_USAGE_STATS_or_service:AccessibilityService"))
        add("INCOMING_CALL", listOf("runtime:READ_PHONE_STATE", "runtime:READ_CALL_LOG_for_number_on_recent_android"))
        add("CALL", listOf("runtime:READ_PHONE_STATE", "runtime:READ_CALL_LOG_for_number_on_recent_android"))
        add("SMS", listOf("runtime:RECEIVE_SMS", "runtime:READ_SMS"))
        add("SIGNAL_STRENGTH", listOf("runtime:READ_PHONE_STATE", "runtime:ACCESS_FINE_LOCATION"))
        add("NOTIFICATION", listOf("service:BIND_NOTIFICATION_LISTENER_SERVICE_user_enabled"))
        add("NOTIFICATION_REMOVED", listOf("service:BIND_NOTIFICATION_LISTENER_SERVICE_user_enabled"))
        add("LOCATION", listOf("runtime:ACCESS_FINE_LOCATION_or_ACCESS_COARSE_LOCATION", "runtime:ACCESS_BACKGROUND_LOCATION_for_background_geofence"))
        add("ACTIVITY_RECOGNITION", listOf("runtime:ACTIVITY_RECOGNITION"))
        add("VOLUME_BUTTON", listOf("service:AccessibilityService_or_media_session_policy"))
        add("CAMERA_BUTTON", listOf("service:AccessibilityService"))
        add("NFC", listOf("manifest:NFC"))
        add("CALENDAR_EVENT", listOf("runtime:READ_CALENDAR"))
        add("MEETING", listOf("runtime:READ_CALENDAR"))
        return root
    }

    private fun provisioningHintsJson(context: Context): JSONObject {
        val pkg = context.packageName
        val listener = "$pkg/com.example.service.AutoTaskNotificationListener"
        val root = JSONObject()
        root.put("packageName", pkg)
        root.put("deviceOwnerDetected", false)
        root.put("adbRuntimeGrantTemplate", "pm grant $pkg <runtime-permission>")
        root.put(
            "adbSpecialAccessCommands",
            JSONArray(
                listOf(
                    "appops set $pkg android:write_settings allow",
                    "appops set $pkg GET_USAGE_STATS allow",
                    "appops set $pkg MANAGE_EXTERNAL_STORAGE allow",
                    "cmd notification allow_dnd $pkg 0",
                    "cmd notification allow_listener $listener 0"
                )
            )
        )
        root.put(
            "runtimePermissions",
            JSONArray(
                listOf(
                    Manifest.permission.POST_NOTIFICATIONS,
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
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_ADVERTISE,
                    Manifest.permission.CAMERA,
                    Manifest.permission.RECORD_AUDIO
                )
            )
        )
        root.put("distributionNote", "Normal installs still require user consent or device-owner provisioning for dangerous and special permissions.")
        return root
    }

    private fun agentPolicyJson(): JSONObject {
        val policy = JSONObject()
        policy.put("dryRunSupported", true)
        policy.put("defaultAutonomy", "read_only")
        policy.put("confirmationRequiredFor", JSONArray(listOf("SEND_SMS", "CALL", "UI_DRIVE", "HTTP", "WRITE_FILE", "CAMERA", "DND", "AUDIO:silent")))
        policy.put("safeReadEndpoints", JSONArray(listOf("/v1/status", "/v1/schema", "/v1/capabilities", "/v1/profiles", "/v1/logs", "/v1/schedules", "/v1/runs")))
        policy.put("writeEndpoints", JSONArray(listOf("/v1/profiles", "/v1/profiles/validate", "/v1/events", "/v1/logs", "/v1/schedules/reconcile", "/v1/runs")))
        policy.put("scopes", JSONArray(listOf("READ", "PROFILE_WRITE", "EXECUTE", "UI_CONTROL", "OTA")))
        policy.put("lanRequiresPairing", true)
        policy.put("defaultBind", "127.0.0.1")
        policy.put("productVersion", com.example.BuildConfig.VERSION_NAME)
        policy.put("mcpProtocolVersion", "2026-07-28")
        return policy
    }

    private fun isRuntimePermissionGranted(context: Context, permission: String, minSdk: Int = 1): Boolean {
        if (Build.VERSION.SDK_INT < minSdk) return true
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun canScheduleExactAlarms(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    private fun isUsageStatsAccessGranted(context: Context): Boolean {
        return try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
            }
            mode == AppOpsManager.MODE_ALLOWED
        } catch (_: Exception) {
            false
        }
    }

    private fun isManageExternalStorageGranted(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                android.os.Environment.isExternalStorageManager()
            } catch (_: Exception) {
                false
            }
        } else {
            true
        }
    }

    private fun isNotificationListenerEnabled(context: Context): Boolean {
        val enabled = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners") ?: return false
        val expected = ComponentName(context, AutoTaskNotificationListener::class.java).flattenToString()
        return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
    }

    private fun isAccessibilityEnabled(context: Context): Boolean {
        return com.example.accessibility.CoSAccessibilityService.isEnabled(context)
    }

    private fun isSystemAlertWindowGranted(context: Context): Boolean {
        return Settings.canDrawOverlays(context)
    }
}
