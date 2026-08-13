package com.example.engine

import org.json.JSONArray
import org.json.JSONObject

object SchemaProvider {

    fun getSchemaJson(): String {
        val root = JSONObject()

        root.put("service", "AutoTask Tool Server Engine")
        root.put("version", "2.0.0")
        root.put("architecture", "Policy is Data. Execution is Deterministic.")
        root.put("capabilitiesEndpoint", "/v1/capabilities")
        val endpoints = JSONObject()
        val eventsEndpoint = JSONObject()
        eventsEndpoint.put("method", "POST")
        eventsEndpoint.put("path", "/v1/events")
        eventsEndpoint.put("canonicalBody", JSONObject(mapOf(
            "triggerType" to "MANUAL",
            "profileId" to "optional target profile ID; omit only for broadcast",
            "dryRun" to "Boolean; validates and reports planned profiles without executing actions",
            "payload" to "JSONObject event payload"
        )))
        eventsEndpoint.put("aliasesAccepted", JSONArray(listOf("type", "trigger_type", "profile_id", "dry_run")))
        endpoints.put("events", eventsEndpoint)
        root.put("endpoints", endpoints)

        // Trigger Types
        val triggerTypes = JSONObject()

        fun addTrigger(type: String, source: String, description: String, state: String, config: Map<String, String>, vars: List<String>) {
            val obj = JSONObject()
            obj.put("source", source)
            obj.put("description", description)
            obj.put("state", state)
            val cfgObj = JSONObject()
            config.forEach { (k, v) -> cfgObj.put(k, v) }
            obj.put("configKeys", cfgObj)
            obj.put("templateVars", JSONArray(vars))
            triggerTypes.put(type, obj)
        }

        // Time
        addTrigger("TIME", "AlarmManager exact alarm", "Fires at specific clock time", "delivery-ready", mapOf("hour" to "Int 0-23", "minute" to "Int 0-59", "days" to "Array<String> ['MON','TUE']"), listOf("{{hour}}", "{{minute}}"))
        addTrigger("SCHEDULE", "WorkManager PeriodicWorkRequest", "Fires on periodic cron schedule", "partial", mapOf("cronExpression" to "String", "intervalMs" to "Long"), listOf("{{cronExpression}}"))
        addTrigger("SUNRISE_SUNSET", "Calculated location + AlarmManager", "Fires at calculated sunrise or sunset", "partial", mapOf("event" to "sunrise/sunset", "offsetMinutes" to "Int"), listOf("{{event}}"))

        // Power
        addTrigger("BATTERY", "BroadcastReceiver ACTION_BATTERY_CHANGED", "Fires on battery level or status change", "delivery-ready", mapOf("levelBelow" to "Int 1-100", "levelAbove" to "Int 1-100", "isCharging" to "Boolean", "isLow" to "Boolean"), listOf("{{levelPercent}}", "{{isCharging}}"))
        addTrigger("POWER", "BroadcastReceiver ACTION_POWER_CONNECTED / DISCONNECTED", "Fires when charger is connected or disconnected", "delivery-ready", mapOf("connected" to "Boolean"), listOf("{{connected}}"))
        addTrigger("POWER_SAVE", "BroadcastReceiver ACTION_POWER_SAVE_MODE_CHANGED", "Fires when Battery Saver mode changes", "delivery-ready", mapOf("enabled" to "Boolean"), listOf("{{enabled}}"))

        // Network
        addTrigger("WIFI", "BroadcastReceiver NETWORK_STATE_CHANGED + ConnectivityManager", "Fires on WiFi state or SSID change", "delivery-ready", mapOf("ssid" to "String SSID", "connected" to "Boolean", "signalStrength" to "Int 0-4"), listOf("{{ssid}}", "{{connected}}"))
        addTrigger("AIRPLANE_MODE", "BroadcastReceiver ACTION_AIRPLANE_MODE_CHANGED", "Fires when Airplane Mode toggles", "delivery-ready", mapOf("enabled" to "Boolean"), listOf("{{enabled}}"))
        addTrigger("MOBILE_DATA", "TelephonyManager callback", "Fires on mobile data connectivity change", "runtime", mapOf("connected" to "Boolean", "networkType" to "String 4g/5g"), listOf("{{connected}}", "{{networkType}}"))

        // Bluetooth
        addTrigger("BLUETOOTH", "BroadcastReceiver ACTION_CONNECTION_STATE_CHANGED", "Fires on Bluetooth device connect/disconnect", "delivery-ready", mapOf("deviceName" to "String filter", "deviceAddress" to "String MAC", "connected" to "Boolean"), listOf("{{deviceName}}", "{{connected}}"))
        addTrigger("BLUETOOTH_STATE", "BroadcastReceiver ACTION_STATE_CHANGED", "Fires when Bluetooth adapter turns ON/OFF", "delivery-ready", mapOf("state" to "String ON/OFF"), listOf("{{state}}"))

        // Screen / Device
        addTrigger("SCREEN", "BroadcastReceiver SCREEN_ON / SCREEN_OFF", "Fires when display turns ON or OFF", "delivery-ready", mapOf("state" to "String 'ON' or 'OFF'"), listOf("{{screenState}}"))
        addTrigger("DEVICE_UNLOCKED", "BroadcastReceiver ACTION_USER_PRESENT", "Fires when device is unlocked by user", "delivery-ready", emptyMap(), listOf("{{timestamp}}"))
        addTrigger("DOZE", "BroadcastReceiver ACTION_DEVICE_IDLE_MODE_CHANGED", "Fires when device enters or exits Doze mode", "delivery-ready", mapOf("entering" to "Boolean"), listOf("{{entering}}"))
        addTrigger("DREAMING", "BroadcastReceiver ACTION_DREAMING_STARTED / STOPPED", "Fires when ambient screen saver starts or stops", "delivery-ready", mapOf("active" to "Boolean"), listOf("{{active}}"))

        // App
        addTrigger("APP_LAUNCH", "AccessibilityService event TYPE_WINDOW_STATE_CHANGED", "Fires when specific app window opens", "delivery-ready", mapOf("packageName" to "String app pkg", "className" to "String activity class"), listOf("{{packageName}}", "{{className}}"))
        addTrigger("PACKAGE_CHANGED", "BroadcastReceiver PACKAGE_ADDED / REMOVED / REPLACED", "Fires when app package is installed, removed, or updated", "delivery-ready", mapOf("packageName" to "String", "event" to "installed/removed/updated"), listOf("{{packageName}}", "{{event}}"))
        addTrigger("FOREGROUND_APP", "UsageStatsManager poll or AccessibilityService", "Fires on active foreground application change", "partial", mapOf("packageName" to "String", "durationMs" to "Long"), listOf("{{packageName}}"))

        // Telephony
        addTrigger("INCOMING_CALL", "TelephonyTriggerController / PHONE_STATE broadcast", "Fires on incoming call state change", "delivery-ready", mapOf("numberContains" to "String number filter", "contactName" to "String contact filter", "isUnknown" to "Boolean"), listOf("{{number}}", "{{callState}}"))
        addTrigger("OUTGOING_CALL", "BroadcastReceiver NEW_OUTGOING_CALL", "Fires when outgoing phone call is initiated", "delivery-ready", mapOf("numberContains" to "String number filter"), listOf("{{number}}"))
        addTrigger("SMS", "BroadcastReceiver SMS_RECEIVED", "Fires when SMS text is received", "delivery-ready", mapOf("senderContains" to "String sender filter", "bodyContains" to "String body filter"), listOf("{{sender}}", "{{smsBody}}"))
        addTrigger("SIGNAL_STRENGTH", "TelephonyManager SignalStrength callback", "Fires on cellular signal strength change", "runtime", mapOf("belowDbm" to "Int dbm threshold", "networkType" to "String"), listOf("{{belowDbm}}"))

        // Notifications
        addTrigger("NOTIFICATION", "NotificationListenerService onNotificationPosted", "Fires when status bar notification is posted", "delivery-ready", mapOf("packageName" to "String app pkg", "titleContains" to "String title filter", "textContains" to "String text filter", "priority" to "String"), listOf("{{packageName}}", "{{title}}", "{{text}}"))
        addTrigger("NOTIFICATION_REMOVED", "NotificationListenerService onNotificationRemoved", "Fires when notification is dismissed", "delivery-ready", mapOf("packageName" to "String app pkg", "reason" to "String reason"), listOf("{{packageName}}", "{{reason}}"))

        // Location & Activity
        addTrigger("LOCATION", "Geofencing API / FusedLocationProvider", "Fires on entering, exiting, or dwelling in geofence", "policy-ready", mapOf("latitude" to "Double", "longitude" to "Double", "radiusMeters" to "Float", "event" to "enter/exit/dwell"), listOf("{{event}}"))
        addTrigger("ACTIVITY_RECOGNITION", "ActivityRecognitionClient", "Fires when user activity changes", "policy-ready", mapOf("activity" to "still/walking/running/driving/cycling", "confidence" to "Int 0-100"), listOf("{{activity}}", "{{confidence}}"))

        // Hardware
        addTrigger("HEADSET", "BroadcastReceiver ACTION_HEADSET_PLUG", "Fires when wired audio headset is plugged/unplugged", "delivery-ready", mapOf("connected" to "Boolean", "hasMicrophone" to "Boolean"), listOf("{{connected}}"))
        addTrigger("USB", "BroadcastReceiver ACTION_USB_DEVICE_ATTACHED / DETACHED", "Fires when USB device or accessory is attached/detached", "delivery-ready", mapOf("connected" to "Boolean", "deviceClass" to "String"), listOf("{{connected}}"))
        addTrigger("VOLUME_BUTTON", "AccessibilityService or MediaSession", "Fires on hardware volume button press", "policy-ready", mapOf("direction" to "up/down", "stream" to "ring/media"), listOf("{{direction}}"))
        addTrigger("CAMERA_BUTTON", "KeyEvent via AccessibilityService", "Fires on hardware camera button press", "policy-ready", emptyMap(), listOf("{{timestamp}}"))

        // NFC
        addTrigger("NFC", "NfcAdapter foreground dispatch or NDEF intent", "Fires when NFC tag is scanned", "policy-ready", mapOf("tagId" to "String tag hex ID", "ndefRecord" to "String NDEF payload", "mimeType" to "String"), listOf("{{tagId}}", "{{ndefRecord}}"))

        // Sensors
        addTrigger("SHAKE", "SensorTriggerController TYPE_ACCELEROMETER", "Fires when device is shaken", "delivery-ready", mapOf("threshold" to "Float sensitivity", "durationMs" to "Long cooldown"), listOf("{{threshold}}"))
        addTrigger("PROXIMITY", "SensorManager TYPE_PROXIMITY", "Fires when object gets near or far from proximity sensor", "delivery-ready", mapOf("near" to "Boolean"), listOf("{{near}}"))
        addTrigger("LIGHT", "SensorManager TYPE_LIGHT", "Fires on ambient light level changes", "delivery-ready", mapOf("belowLux" to "Float lux threshold", "aboveLux" to "Float lux threshold"), listOf("{{belowLux}}"))
        addTrigger("STEP", "SensorManager TYPE_STEP_COUNTER / STEP_DETECTOR", "Fires on step detector or count threshold", "delivery-ready", mapOf("count" to "Int step target", "detected" to "Boolean"), listOf("{{count}}"))
        addTrigger("PRESSURE", "SensorManager TYPE_PRESSURE", "Fires on barometric pressure change", "policy-ready", mapOf("belowHpa" to "Float", "aboveHpa" to "Float"), listOf("{{belowHpa}}"))
        addTrigger("TEMPERATURE", "SensorManager TYPE_AMBIENT_TEMPERATURE", "Fires on ambient temperature change", "policy-ready", mapOf("belowC" to "Float", "aboveC" to "Float"), listOf("{{belowC}}"))

        // Calendar
        addTrigger("CALENDAR_EVENT", "CalendarContract ContentObserver or poller", "Fires near upcoming calendar events", "policy-ready", mapOf("titleContains" to "String title filter", "calendarName" to "String calendar filter", "minutesBefore" to "Int lead time"), listOf("{{title}}"))
        addTrigger("MEETING", "CalendarContract meeting-specific filter", "Fires near scheduled meeting events", "policy-ready", mapOf("attendeeCount" to "Int min attendees", "isRecurring" to "Boolean", "minutesBefore" to "Int lead time"), listOf("{{attendeeCount}}"))

        // System
        addTrigger("BOOT", "BroadcastReceiver BOOT_COMPLETED + QUICKBOOT_POWERON", "Fires once upon device startup", "delivery-ready", emptyMap(), listOf("{{timestamp}}"))
        addTrigger("TIMEZONE", "BroadcastReceiver ACTION_TIMEZONE_CHANGED", "Fires when device timezone changes", "delivery-ready", mapOf("timezone" to "String timezone ID"), listOf("{{timezone}}"))
        addTrigger("LOCALE", "BroadcastReceiver ACTION_LOCALE_CHANGED", "Fires when system language/locale changes", "delivery-ready", mapOf("locale" to "String locale ID"), listOf("{{locale}}"))
        addTrigger("CUSTOM_INTENT", "BroadcastReceiver with dynamic intent filter", "Fires on custom intent action broadcast", "delivery-ready", mapOf("action" to "String intent action", "extras" to "JSONObject extras filter"), listOf("{{action}}"))
        addTrigger("MANUAL", "ContentProvider /events insert or POST /v1/events", "Direct execution triggered by AI agent or user", "delivery-ready", mapOf("profileId" to "String target profile ID"), listOf("{{payload.*}}"))
        addTrigger("CALL", "TelephonyTriggerController / PHONE_STATE", "Fires on incoming or active phone calls", "delivery-ready", mapOf("state" to "String 'RINGING', 'OFFHOOK', 'IDLE'", "numberContains" to "String number filter"), listOf("{{number}}", "{{callState}}"))

        root.put("triggerTypes", triggerTypes)

        // Action Types
        val actionTypes = JSONObject()

        fun addAction(
            type: String,
            description: String,
            params: Map<String, String>,
            notes: String = "",
            requirements: List<String> = emptyList(),
            risk: String = "low",
            autonomy: String = "autonomous_allowed"
        ) {
            val obj = JSONObject()
            obj.put("description", description)
            val pObj = JSONObject()
            params.forEach { (k, v) -> pObj.put(k, v) }
            obj.put("params", pObj)
            if (notes.isNotEmpty()) obj.put("notes", notes)
            obj.put("requirements", JSONArray(requirements))
            obj.put("risk", risk)
            obj.put("autonomy", autonomy)
            actionTypes.put(type, obj)
        }

        // Device State
        addAction(
            "AUDIO",
            "Changes system ringer mode and volume streams",
            mapOf("ringerMode" to "String 'normal', 'vibrate', 'silent'", "stream" to "String 'ring', 'media', 'alarm', 'notification'", "volume" to "Int 0-15"),
            "ringerMode=silent may require Do Not Disturb access on Android 13+",
            listOf("runtime:notification_policy_access_when_ringerMode_silent"),
            "medium",
            "confirm_or_policy_allowed"
        )
        addAction(
            "DND",
            "Enables or disables Do Not Disturb mode",
            mapOf("enabled" to "Boolean", "policy" to "String 'all', 'priority', 'none', 'alarms'"),
            "Requires Do Not Disturb access",
            listOf("manifest:android.permission.ACCESS_NOTIFICATION_POLICY", "runtime:notification_policy_access"),
            "elevated",
            "confirm_or_policy_allowed"
        )
        addAction("BRIGHTNESS", "Sets screen brightness level", mapOf("level" to "Int 0-255", "auto" to "Boolean"), "Requires WRITE_SETTINGS permission", listOf("manifest:android.permission.WRITE_SETTINGS", "appop:android:write_settings"), "medium", "policy_allowed")
        addAction("SCREEN_TIMEOUT", "Sets screen turn-off timeout in seconds", mapOf("seconds" to "Int timeout in seconds"), "Requires WRITE_SETTINGS permission", listOf("manifest:android.permission.WRITE_SETTINGS", "appop:android:write_settings"), "medium", "policy_allowed")
        addAction("ROTATION", "Toggles auto-rotation or forces orientation", mapOf("auto" to "Boolean", "orientation" to "String 'portrait', 'landscape'"), "Requires WRITE_SETTINGS permission", listOf("manifest:android.permission.WRITE_SETTINGS", "appop:android:write_settings"), "medium", "policy_allowed")
        addAction("POWER_SAVE", "Toggles Power Saver battery mode", mapOf("enabled" to "Boolean"))

        // Connectivity
        addAction("WIFI_ACTION", "Enables or disables WiFi", mapOf("enabled" to "Boolean"))
        addAction("BLUETOOTH_ACTION", "Enables or disables Bluetooth adapter", mapOf("enabled" to "Boolean"))
        addAction("AIRPLANE_MODE_ACTION", "Toggles Airplane mode state", mapOf("enabled" to "Boolean"))
        addAction("HOTSPOT", "Toggles Local-Only Wi-Fi Hotspot", mapOf("enabled" to "Boolean"))
        addAction("NFC_ACTION", "Toggles NFC adapter state", mapOf("enabled" to "Boolean"))

        // Notifications & Alerts
        addAction("NOTIFICATION", "Posts a system notification", mapOf("title" to "String (supports templates)", "text" to "String (supports templates)", "priority" to "String 'low', 'normal', 'high'", "channelId" to "String", "ongoing" to "Boolean"))
        addAction("SPEAK", "Speaks text aloud using TextToSpeech", mapOf("text" to "String (supports templates)", "rate" to "Float pitch/rate", "pitch" to "Float", "stream" to "String"))
        addAction("TOAST", "Displays a short or long screen Toast popup", mapOf("text" to "String (supports templates)", "duration" to "String 'short' or 'long'"))
        addAction("VIBRATE", "Vibrates device using haptic engine", mapOf("pattern" to "Array<Long> ms timings", "durationMs" to "Long duration"))

        // Communication
        addAction("SEND_SMS", "Sends an outgoing SMS text message", mapOf("number" to "String target phone number", "text" to "String SMS text body"), "Requires SEND_SMS permission", listOf("runtime:android.permission.SEND_SMS"), "high", "confirm_required")
        addAction("CALL", "Initiates outgoing phone dialer call", mapOf("number" to "String phone number"), "Requires CALL_PHONE permission")
        addAction("OPEN_URL", "Opens web URL in default web browser", mapOf("url" to "String web address"))
        addAction("SEND_INTENT", "Sends a resolve-and-actuate VIEW intent to whatever app handles the scheme", mapOf("data" to "String full URI (e.g. whatsapp://send?phone=...)", "scheme" to "String scheme (e.g. whatsapp, mailto, geo, tel)", "target" to "String target for scheme://target form"))

        // App Control
        addAction("LAUNCH_APP", "Launches an installed Android application", mapOf("packageName" to "String package name", "activity" to "String optional target activity"))
        addAction("KILL_APP", "Terminates background process for package", mapOf("packageName" to "String package name"))
        addAction("OPEN_SETTINGS", "Opens system settings menu directly", mapOf("screen" to "String 'wifi', 'bluetooth', 'accessibility', 'battery', 'display'"))

        // Hardware
        addAction("FLASHLIGHT", "Toggles camera LED flashlight torch", mapOf("on" to "Boolean", "toggle" to "Boolean"))
        addAction("CLIPBOARD", "Copies or retrieves text to system clipboard", mapOf("operation" to "String 'set' or 'get'", "text" to "String content"))
        addAction("CAMERA", "Triggers camera photo capture or recording", mapOf("action" to "String 'photo' or 'video'"))

        // Data & Integration
        addAction("HTTP", "Performs an HTTP client request", mapOf("url" to "String endpoint URL", "method" to "String 'GET', 'POST', 'PUT', 'PATCH', 'DELETE'", "headers" to "JSONObject key-values", "body" to "String request body", "contentType" to "String", "timeoutMs" to "Long"))
        addAction("WRITE_FILE", "Writes text content to local storage file", mapOf("path" to "String target file path", "content" to "String text content", "append" to "Boolean"))
        addAction("READ_FILE", "Reads local file text into context variable", mapOf("path" to "String target file path"))
        addAction("BROADCAST", "Sends custom broadcast intent", mapOf("action" to "String intent action", "extras" to "JSONObject extras key-values"))

        // Profile & Flow Control
        addAction("PROFILE", "Enables, disables, or fires another profile", mapOf("profileId" to "String target profile ID", "action" to "String 'enable', 'disable', 'fire'"))
        addAction("WAIT", "Pauses sequence execution for duration", mapOf("durationMs" to "Long pause in milliseconds"))
        addAction("LOG", "Inserts custom execution log message", mapOf("message" to "String log message", "level" to "String 'info', 'warn', 'error'"))

        root.put("actionTypes", actionTypes)

        // Universal Template Variables
        val globalVars = JSONArray(listOf(
            "{{triggerType}}",
            "{{timestamp}}",
            "{{profileId}}",
            "{{profileName}}",
            "{{sender}}",
            "{{smsBody}}",
            "{{number}}",
            "{{callState}}",
            "{{levelPercent}}",
            "{{isCharging}}",
            "{{ssid}}",
            "{{connected}}",
            "{{deviceName}}",
            "{{packageName}}",
            "{{title}}",
            "{{text}}",
            "{{screenState}}",
            "{{hour}}",
            "{{minute}}"
        ))
        root.put("universalTemplateVariables", globalVars)

        return root.toString(2)
    }
}
