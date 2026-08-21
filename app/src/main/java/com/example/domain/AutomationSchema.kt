package com.example.domain

/**
 * Versioned catalog of supported triggers, actions, conditions, and parameter
 * types. [com.example.engine.SchemaProvider] renders this catalog; the
 * compiler validates definitions against it.
 */
object AutomationSchema {
    /** Catalog 2 dropped unused trigger keys. Extra keys on persisted v1 rows fail compile and skip as invalid_definition. */
    const val CURRENT_VERSION = 2

    enum class ParamKind {
        STRING,
        INT,
        LONG,
        FLOAT,
        BOOLEAN,
        STRING_ARRAY,
        LONG_ARRAY,
        OBJECT
    }

    data class ParamSpec(
        val name: String,
        val kind: ParamKind,
        val description: String,
        val min: Double? = null,
        val max: Double? = null,
        val allowed: Set<String>? = null,
        val allowedIgnoreCase: Boolean = true
    )

    data class TriggerDescriptor(
        val type: String,
        val source: String,
        val description: String,
        val state: String,
        val config: List<ParamSpec>,
        val templateVars: List<String>
    ) {
        val configByName: Map<String, ParamSpec> = config.associateBy { it.name }
    }

    data class ActionDescriptor(
        val type: String,
        val description: String,
        val params: List<ParamSpec>,
        val notes: String = "",
        val requirements: List<String> = emptyList(),
        val risk: String = "low",
        val autonomy: String = "autonomous_allowed",
        val state: String = "delivery-ready"
    ) {
        val paramsByName: Map<String, ParamSpec> = params.associateBy { it.name }
    }

    private val WEEKDAYS = setOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN")
    private val CALL_STATES = setOf("RINGING", "OFFHOOK", "IDLE")
    private val SETTINGS_SCREENS = setOf("wifi", "bluetooth", "accessibility", "battery", "display")

    val conditionParams: List<ParamSpec> = listOf(
        bool("isScreenOn", "Boolean"),
        bool("isCharging", "Boolean"),
        int("batteryAbove", "Int 0-100", 0.0, 100.0),
        enumString("ringerMode", "String 'silent', 'vibrate', or 'normal'", setOf("silent", "vibrate", "normal"))
    )
    val conditionByName: Map<String, ParamSpec> = conditionParams.associateBy { it.name }

    val triggers: Map<String, TriggerDescriptor> = linkedMapOf<String, TriggerDescriptor>().apply {
        add(
            "TIME", "AlarmManager exact alarm", "Fires at the next matching local clock time", "delivery-ready",
            listOf(
                int("hour", "Int 0-23", 0.0, 23.0),
                int("minute", "Int 0-59", 0.0, 59.0),
                stringArray("days", "Array<String> ['MON','TUE']; omit for every day", WEEKDAYS),
                string("timezone", "IANA timezone; defaults to the device zone")
            ),
            listOf("{{hour}}", "{{minute}}")
        )
        add(
            "SCHEDULE",
            "Next-occurrence scheduler; cron uses AlarmManager, interval uses WorkManager",
            "Fires on a 5-field cron or a flexible interval. Only the next occurrence is registered.",
            "delivery-ready",
            listOf(
                string(
                    "cronExpression",
                    "5-field cron: minute hour day-of-month month day-of-week. Tokens: *, N, N-M, A,B, */S, N-M/S. Day-of-week 0-6 or SUN-SAT (0 and 7 are Sunday). When both day fields are restricted, either may match."
                ),
                long("intervalMs", "Flexible WorkManager interval in milliseconds; used when cronExpression is absent"),
                string("timezone", "IANA timezone; defaults to the device zone")
            ),
            listOf("{{cronExpression}}")
        )
        add(
            "SUNRISE_SUNSET", "Calculated location + AlarmManager", "Fires at the next sunrise or sunset",
            "delivery-ready",
            listOf(
                enumString("event", "sunrise/sunset", setOf("sunrise", "sunset")),
                int("offsetMinutes", "Int offset applied to the calculated solar time"),
                float("latitude", "Decimal degrees; falls back to last known location"),
                float("longitude", "Decimal degrees; falls back to last known location"),
                string("timezone", "IANA timezone; defaults to the device zone")
            ),
            listOf("{{event}}")
        )
        add(
            "BATTERY", "BroadcastReceiver ACTION_BATTERY_CHANGED", "Fires on battery level or status change",
            "delivery-ready",
            listOf(
                int("levelBelow", "Int 1-100", 1.0, 100.0),
                int("levelAbove", "Int 1-100", 1.0, 100.0),
                bool("isCharging", "Boolean"),
                bool("isLow", "Boolean")
            ),
            listOf("{{levelPercent}}", "{{isCharging}}")
        )
        add(
            "POWER", "BroadcastReceiver ACTION_POWER_CONNECTED / DISCONNECTED",
            "Fires when charger is connected or disconnected", "delivery-ready",
            listOf(bool("connected", "Boolean")),
            listOf("{{connected}}")
        )
        add(
            "POWER_SAVE", "BroadcastReceiver ACTION_POWER_SAVE_MODE_CHANGED",
            "Fires when Battery Saver mode changes", "delivery-ready",
            listOf(bool("enabled", "Boolean")),
            listOf("{{enabled}}")
        )
        add(
            "WIFI", "BroadcastReceiver NETWORK_STATE_CHANGED",
            "Fires on WiFi state or SSID change", "delivery-ready",
            listOf(
                string("ssid", "String SSID"),
                bool("connected", "Boolean")
            ),
            listOf("{{ssid}}", "{{connected}}")
        )
        add(
            "AIRPLANE_MODE", "BroadcastReceiver ACTION_AIRPLANE_MODE_CHANGED",
            "Fires when Airplane Mode toggles", "delivery-ready",
            listOf(bool("enabled", "Boolean")),
            listOf("{{enabled}}")
        )
        add(
            "MOBILE_DATA", "TelephonyManager callback", "Fires on mobile data connectivity change", "runtime",
            listOf(
                bool("connected", "Boolean"),
                string("networkType", "String 4g/5g")
            ),
            listOf("{{connected}}", "{{networkType}}")
        )
        add(
            "BLUETOOTH", "BroadcastReceiver ACL_CONNECTED / ACL_DISCONNECTED",
            "Fires on Bluetooth device connect/disconnect", "delivery-ready",
            listOf(
                string("deviceName", "String filter"),
                string("deviceAddress", "String MAC"),
                bool("connected", "Boolean")
            ),
            listOf("{{deviceName}}", "{{connected}}")
        )
        add(
            "BLUETOOTH_STATE", "BroadcastReceiver ACTION_STATE_CHANGED",
            "Fires when Bluetooth adapter turns ON/OFF", "delivery-ready",
            listOf(enumString("state", "String ON/OFF", setOf("ON", "OFF"))),
            listOf("{{state}}")
        )
        add(
            "SCREEN", "BroadcastReceiver SCREEN_ON / SCREEN_OFF",
            "Fires when display turns ON or OFF", "delivery-ready",
            listOf(enumString("state", "String 'ON' or 'OFF'", setOf("ON", "OFF"))),
            listOf("{{screenState}}")
        )
        add(
            "DEVICE_UNLOCKED", "BroadcastReceiver ACTION_USER_PRESENT",
            "Fires when device is unlocked by user", "delivery-ready",
            emptyList(),
            listOf("{{timestamp}}")
        )
        add(
            "DOZE", "BroadcastReceiver ACTION_DEVICE_IDLE_MODE_CHANGED",
            "Fires when device enters or exits Doze mode", "delivery-ready",
            listOf(bool("entering", "Boolean")),
            listOf("{{entering}}")
        )
        add(
            "DREAMING", "BroadcastReceiver ACTION_DREAMING_STARTED / STOPPED",
            "Fires when ambient screen saver starts or stops", "policy-ready",
            listOf(bool("active", "Boolean")),
            listOf("{{active}}")
        )
        add(
            "APP_LAUNCH", "AccessibilityService event TYPE_WINDOW_STATE_CHANGED",
            "Fires when specific app window opens", "policy-ready",
            listOf(
                string("packageName", "String app pkg"),
                string("className", "String activity class")
            ),
            listOf("{{packageName}}", "{{className}}")
        )
        add(
            "PACKAGE_CHANGED", "BroadcastReceiver PACKAGE_ADDED / REMOVED / REPLACED",
            "Fires when app package is installed, removed, or updated", "policy-ready",
            listOf(
                string("packageName", "String"),
                enumString("event", "installed/removed/updated", setOf("installed", "removed", "updated"))
            ),
            listOf("{{packageName}}", "{{event}}")
        )
        add(
            "FOREGROUND_APP", "UsageStatsManager poll or AccessibilityService",
            "Fires on active foreground application change", "policy-ready",
            listOf(
                string("packageName", "String"),
                long("durationMs", "Long")
            ),
            listOf("{{packageName}}")
        )
        add(
            "INCOMING_CALL", "BroadcastReceiver PHONE_STATE",
            "Fires on incoming call state change", "delivery-ready",
            listOf(
                string("numberContains", "String number filter"),
                enumString("state", "String 'RINGING', 'OFFHOOK', 'IDLE'", CALL_STATES)
            ),
            listOf("{{number}}", "{{callState}}")
        )
        add(
            "OUTGOING_CALL", "BroadcastReceiver NEW_OUTGOING_CALL",
            "Fires when outgoing phone call is initiated", "policy-ready",
            listOf(string("numberContains", "String number filter")),
            listOf("{{number}}")
        )
        add(
            "SMS", "BroadcastReceiver SMS_RECEIVED", "Fires when SMS text is received", "delivery-ready",
            listOf(
                string("senderContains", "String sender filter"),
                string("bodyContains", "String body filter")
            ),
            listOf("{{sender}}", "{{smsBody}}")
        )
        add(
            "SIGNAL_STRENGTH", "TelephonyManager SignalStrength callback",
            "Fires on cellular signal strength change", "runtime",
            listOf(
                int("belowDbm", "Int dbm threshold"),
                string("networkType", "String")
            ),
            listOf("{{belowDbm}}")
        )
        add(
            "NOTIFICATION", "NotificationListenerService onNotificationPosted",
            "Fires when status bar notification is posted", "delivery-ready",
            listOf(
                string("packageName", "String app pkg"),
                string("titleContains", "String title filter"),
                string("textContains", "String text filter")
            ),
            listOf("{{packageName}}", "{{title}}", "{{text}}")
        )
        add(
            "NOTIFICATION_REMOVED", "NotificationListenerService onNotificationRemoved",
            "Fires when notification is dismissed", "policy-ready",
            listOf(
                string("packageName", "String app pkg"),
                string("reason", "String reason")
            ),
            listOf("{{packageName}}", "{{reason}}")
        )
        add(
            "LOCATION", "Geofencing API / FusedLocationProvider",
            "Fires on entering, exiting, or dwelling in geofence", "policy-ready",
            listOf(
                float("latitude", "Double"),
                float("longitude", "Double"),
                float("radiusMeters", "Float"),
                enumString("event", "enter/exit/dwell", setOf("enter", "exit", "dwell"))
            ),
            listOf("{{event}}")
        )
        add(
            "ACTIVITY_RECOGNITION", "ActivityRecognitionClient",
            "Fires when user activity changes", "policy-ready",
            listOf(
                enumString("activity", "still/walking/running/driving/cycling", setOf("still", "walking", "running", "driving", "cycling")),
                int("confidence", "Int 0-100", 0.0, 100.0)
            ),
            listOf("{{activity}}", "{{confidence}}")
        )
        add(
            "HEADSET", "BroadcastReceiver ACTION_HEADSET_PLUG",
            "Fires when wired audio headset is plugged/unplugged", "delivery-ready",
            listOf(
                bool("connected", "Boolean")
            ),
            listOf("{{connected}}")
        )
        add(
            "USB", "BroadcastReceiver ACTION_USB_DEVICE_ATTACHED / DETACHED",
            "Fires when USB device or accessory is attached/detached", "delivery-ready",
            listOf(
                bool("connected", "Boolean")
            ),
            listOf("{{connected}}")
        )
        add(
            "VOLUME_BUTTON", "AccessibilityService or MediaSession",
            "Fires on hardware volume button press", "policy-ready",
            listOf(
                enumString("direction", "up/down", setOf("up", "down")),
                enumString("stream", "ring/media", setOf("ring", "media"))
            ),
            listOf("{{direction}}")
        )
        add(
            "CAMERA_BUTTON", "KeyEvent via AccessibilityService",
            "Fires on hardware camera button press", "policy-ready",
            emptyList(),
            listOf("{{timestamp}}")
        )
        add(
            "NFC", "NfcAdapter foreground dispatch or NDEF intent",
            "Fires when NFC tag is scanned", "policy-ready",
            listOf(
                string("tagId", "String tag hex ID"),
                string("ndefRecord", "String NDEF payload"),
                string("mimeType", "String")
            ),
            listOf("{{tagId}}", "{{ndefRecord}}")
        )
        add(
            "SHAKE", "SensorManager TYPE_ACCELEROMETER", "Fires when device is shaken",
            "policy-ready",
            listOf(
                float("threshold", "Float sensitivity"),
                long("durationMs", "Long cooldown")
            ),
            listOf("{{threshold}}")
        )
        add(
            "PROXIMITY", "SensorManager TYPE_PROXIMITY",
            "Fires when object gets near or far from proximity sensor", "policy-ready",
            listOf(bool("near", "Boolean")),
            listOf("{{near}}")
        )
        add(
            "LIGHT", "SensorManager TYPE_LIGHT", "Fires on ambient light level changes", "policy-ready",
            listOf(
                float("belowLux", "Float lux threshold"),
                float("aboveLux", "Float lux threshold")
            ),
            listOf("{{belowLux}}")
        )
        add(
            "STEP", "SensorManager TYPE_STEP_COUNTER / STEP_DETECTOR",
            "Fires on step detector or count threshold", "policy-ready",
            listOf(
                int("count", "Int step target"),
                bool("detected", "Boolean")
            ),
            listOf("{{count}}")
        )
        add(
            "PRESSURE", "SensorManager TYPE_PRESSURE", "Fires on barometric pressure change", "policy-ready",
            listOf(
                float("belowHpa", "Float"),
                float("aboveHpa", "Float")
            ),
            listOf("{{belowHpa}}")
        )
        add(
            "TEMPERATURE", "SensorManager TYPE_AMBIENT_TEMPERATURE",
            "Fires on ambient temperature change", "policy-ready",
            listOf(
                float("belowC", "Float"),
                float("aboveC", "Float")
            ),
            listOf("{{belowC}}")
        )
        add(
            "CALENDAR_EVENT", "CalendarContract ContentObserver or poller",
            "Fires near upcoming calendar events", "policy-ready",
            listOf(
                string("titleContains", "String title filter"),
                string("calendarName", "String calendar filter"),
                int("minutesBefore", "Int lead time")
            ),
            listOf("{{title}}")
        )
        add(
            "MEETING", "CalendarContract meeting-specific filter",
            "Fires near scheduled meeting events", "policy-ready",
            listOf(
                int("attendeeCount", "Int min attendees"),
                bool("isRecurring", "Boolean"),
                int("minutesBefore", "Int lead time")
            ),
            listOf("{{attendeeCount}}")
        )
        add(
            "BOOT", "BroadcastReceiver BOOT_COMPLETED + MY_PACKAGE_REPLACED",
            "Fires on device startup or after this app is replaced", "delivery-ready",
            emptyList(),
            listOf("{{timestamp}}")
        )
        add(
            "TIMEZONE", "BroadcastReceiver ACTION_TIMEZONE_CHANGED",
            "Fires when device timezone changes", "delivery-ready",
            listOf(string("timezone", "String timezone ID")),
            listOf("{{timezone}}")
        )
        add(
            "LOCALE", "BroadcastReceiver ACTION_LOCALE_CHANGED",
            "Fires when system language/locale changes", "delivery-ready",
            listOf(string("locale", "String locale ID")),
            listOf("{{locale}}")
        )
        add(
            "CUSTOM_INTENT", "BroadcastReceiver with dynamic intent filter",
            "Fires on custom intent action broadcast", "policy-ready",
            listOf(
                string("action", "String intent action"),
                obj("extras", "JSONObject extras filter")
            ),
            listOf("{{action}}")
        )
        add(
            "MANUAL", "POST /v1/events (facade); ContentProvider /events is in-process",
            "Direct execution triggered by AI agent or user", "delivery-ready",
            listOf(string("profileId", "String target profile ID")),
            listOf("{{payload.*}}")
        )
        add(
            "CALL", "BroadcastReceiver PHONE_STATE",
            "Fires on incoming or active phone calls", "delivery-ready",
            listOf(
                enumString("state", "String 'RINGING', 'OFFHOOK', 'IDLE'", CALL_STATES),
                string("numberContains", "String number filter")
            ),
            listOf("{{number}}", "{{callState}}")
        )
    }

    val actions: Map<String, ActionDescriptor> = linkedMapOf<String, ActionDescriptor>().apply {
        add(
            "AUDIO", "Changes system ringer mode and volume streams",
            listOf(
                enumString("ringerMode", "String 'normal', 'vibrate', 'silent'", setOf("normal", "vibrate", "silent")),
                enumString("stream", "String 'ring', 'media', 'alarm', 'notification'", setOf("ring", "media", "alarm", "notification")),
                int("volume", "Int 0-15", 0.0, 15.0)
            ),
            "ringerMode=silent may require Do Not Disturb access on Android 13+",
            listOf("runtime:notification_policy_access_when_ringerMode_silent"),
            "medium",
            "confirm_or_policy_allowed"
        )
        add(
            "DND", "Enables or disables Do Not Disturb mode",
            listOf(
                bool("enabled", "Boolean"),
                enumString("policy", "String 'all', 'priority', 'none', 'alarms'", setOf("all", "priority", "none", "alarms"))
            ),
            "Requires Do Not Disturb access",
            listOf("manifest:android.permission.ACCESS_NOTIFICATION_POLICY", "runtime:notification_policy_access"),
            "elevated",
            "confirm_or_policy_allowed"
        )
        add(
            "BRIGHTNESS", "Sets screen brightness level",
            listOf(int("level", "Int 0-255", 0.0, 255.0), bool("auto", "Boolean")),
            "Requires WRITE_SETTINGS permission",
            listOf("manifest:android.permission.WRITE_SETTINGS", "appop:android:write_settings"),
            "medium",
            "policy_allowed"
        )
        add(
            "SCREEN_TIMEOUT", "Sets screen turn-off timeout in seconds",
            listOf(int("seconds", "Int timeout in seconds")),
            "Requires WRITE_SETTINGS permission",
            listOf("manifest:android.permission.WRITE_SETTINGS", "appop:android:write_settings"),
            "medium",
            "policy_allowed"
        )
        add(
            "ROTATION", "Toggles auto-rotation or forces orientation",
            listOf(
                bool("auto", "Boolean"),
                enumString("orientation", "String 'portrait', 'landscape'", setOf("portrait", "landscape"))
            ),
            "Requires WRITE_SETTINGS permission",
            listOf("manifest:android.permission.WRITE_SETTINGS", "appop:android:write_settings"),
            "medium",
            "policy_allowed"
        )
        add("POWER_SAVE", "Toggles Power Saver battery mode", listOf(bool("enabled", "Boolean")), state = "policy-ready")
        add("WIFI_ACTION", "Enables or disables WiFi", listOf(bool("enabled", "Boolean")), state = "policy-ready")
        add("BLUETOOTH_ACTION", "Enables or disables Bluetooth adapter", listOf(bool("enabled", "Boolean")), state = "policy-ready")
        add("AIRPLANE_MODE_ACTION", "Toggles Airplane mode state", listOf(bool("enabled", "Boolean")), state = "policy-ready")
        add("HOTSPOT", "Toggles Local-Only Wi-Fi Hotspot", listOf(bool("enabled", "Boolean")), state = "policy-ready")
        add("NFC_ACTION", "Toggles NFC adapter state", listOf(bool("enabled", "Boolean")), state = "policy-ready")
        add(
            "NOTIFICATION", "Posts a system notification",
            listOf(
                string("title", "String (supports templates)"),
                string("text", "String (supports templates)"),
                enumString("priority", "String 'low', 'normal', 'high'", setOf("low", "normal", "high")),
                string("channelId", "String"),
                bool("ongoing", "Boolean")
            )
        )
        add(
            "SPEAK", "Speaks text aloud using TextToSpeech",
            listOf(
                string("text", "String (supports templates)"),
                float("rate", "Float pitch/rate"),
                float("pitch", "Float"),
                string("stream", "String")
            )
        )
        add(
            "TOAST", "Displays a short or long screen Toast popup",
            listOf(
                string("text", "String (supports templates)"),
                enumString("duration", "String 'short' or 'long'", setOf("short", "long"))
            )
        )
        add(
            "VIBRATE", "Vibrates device using haptic engine",
            listOf(
                longArray("pattern", "Array<Long> ms timings"),
                long("durationMs", "Long duration")
            )
        )
        add(
            "SEND_SMS", "Sends an outgoing SMS text message",
            listOf(
                string("number", "String target phone number"),
                string("text", "String SMS text body")
            ),
            "Requires SEND_SMS permission",
            listOf("runtime:android.permission.SEND_SMS"),
            "high",
            "confirm_required"
        )
        add(
            "CALL", "Initiates outgoing phone dialer call",
            listOf(string("number", "String phone number")),
            "Requires CALL_PHONE permission"
        )
        add("OPEN_URL", "Opens web URL in default web browser", listOf(string("url", "String web address")))
        add(
            "SEND_INTENT",
            "Sends a resolve-and-actuate intent. Default VIEW+data; SEND/SENDTO can pin a package (e.g. Google Voice share).",
            listOf(
                string("data", "String full URI (e.g. whatsapp://send?phone=...)"),
                string("scheme", "String scheme (e.g. whatsapp, mailto, geo, tel)"),
                string("target", "String target for scheme://target form"),
                string("action", "Intent action; default android.intent.action.VIEW"),
                string("package", "Optional target package (e.g. com.google.android.apps.googlevoice)"),
                string("mimeType", "Optional MIME (text/plain for ACTION_SEND)"),
                string("extraText", "Optional EXTRA_TEXT / sms_body"),
                string("extraPhone", "Optional EXTRA_PHONE_NUMBER / address")
            )
        )
        add(
            "LAUNCH_APP", "Launches an installed Android application",
            listOf(
                string("packageName", "String package name"),
                string("activity", "String optional target activity")
            )
        )
        add("KILL_APP", "Terminates background process for package", listOf(string("packageName", "String package name")), state = "policy-ready")
        add(
            "OPEN_SETTINGS", "Opens system settings menu directly",
            listOf(enumString("screen", "String 'wifi', 'bluetooth', 'accessibility', 'battery', 'display'", SETTINGS_SCREENS))
        )
        add(
            "FLASHLIGHT", "Toggles camera LED flashlight torch",
            listOf(bool("on", "Boolean"), bool("toggle", "Boolean"))
        )
        add(
            "CLIPBOARD", "Copies or retrieves text to system clipboard",
            listOf(
                enumString("operation", "String 'set' or 'get'", setOf("set", "get")),
                string("text", "String content")
            )
        )
        add(
            "CAMERA", "Triggers camera photo capture or recording",
            listOf(enumString("action", "String 'photo' or 'video'", setOf("photo", "video"))),
            risk = "high",
            autonomy = "confirm_required",
            state = "policy-ready"
        )
        add(
            "HTTP", "Performs an HTTP client request",
            listOf(
                string("url", "String endpoint URL"),
                enumString("method", "String 'GET', 'POST', 'PUT', 'PATCH', 'DELETE'", setOf("GET", "POST", "PUT", "PATCH", "DELETE")),
                obj("headers", "JSONObject key-values"),
                string("body", "String request body"),
                string("contentType", "String"),
                long("timeoutMs", "Long")
            )
        )
        add(
            "WRITE_FILE", "Writes text content to local storage file",
            listOf(
                string("path", "String target file path"),
                string("content", "String text content"),
                bool("append", "Boolean")
            )
        )
        add("READ_FILE", "Reads local file text into context variable", listOf(string("path", "String target file path")))
        add(
            "BROADCAST", "Sends custom broadcast intent",
            listOf(
                string("action", "String intent action"),
                obj("extras", "JSONObject extras key-values")
            )
        )
        add(
            "PROFILE", "Enables, disables, or fires another profile",
            listOf(
                string("profileId", "String target profile ID"),
                enumString("action", "String 'enable', 'disable', 'fire'", setOf("enable", "disable", "fire", "toggle"))
            )
        )
        add("WAIT", "Pauses sequence execution for duration", listOf(long("durationMs", "Long pause in milliseconds")))
        add(
            "LOG", "Inserts custom execution log message",
            listOf(
                string("message", "String log message"),
                enumString("level", "String 'info', 'warn', 'error'", setOf("info", "warn", "error"))
            )
        )
    }

    val universalTemplateVariables: List<String> = listOf(
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
    )

    val riskRank: Map<String, Int> = mapOf(
        "low" to 0,
        "medium" to 1,
        "elevated" to 2,
        "high" to 3
    )

    fun trigger(type: String): TriggerDescriptor? = triggers[type.uppercase()]

    fun action(type: String): ActionDescriptor? = actions[type.uppercase()]

    fun deriveRiskPolicy(steps: List<ActionStep>): RiskPolicy {
        var maxRank = 0
        var maxRisk = "low"
        var requireConfirmation = false
        for (step in steps) {
            val descriptor = action(step.type) ?: continue
            val rank = riskRank[descriptor.risk] ?: 0
            if (rank > maxRank) {
                maxRank = rank
                maxRisk = descriptor.risk
            }
            if (descriptor.autonomy == "confirm_required") {
                requireConfirmation = true
            }
        }
        return RiskPolicy(maxRisk = maxRisk, requireConfirmation = requireConfirmation)
    }

    private fun MutableMap<String, TriggerDescriptor>.add(
        type: String,
        source: String,
        description: String,
        state: String,
        config: List<ParamSpec>,
        templateVars: List<String>
    ) {
        put(type, TriggerDescriptor(type, source, description, state, config, templateVars))
    }

    private fun MutableMap<String, ActionDescriptor>.add(
        type: String,
        description: String,
        params: List<ParamSpec>,
        notes: String = "",
        requirements: List<String> = emptyList(),
        risk: String = "low",
        autonomy: String = "autonomous_allowed",
        state: String = "delivery-ready"
    ) {
        put(type, ActionDescriptor(type, description, params, notes, requirements, risk, autonomy, state))
    }

    private fun string(name: String, description: String) =
        ParamSpec(name, ParamKind.STRING, description)

    private fun enumString(name: String, description: String, allowed: Set<String>) =
        ParamSpec(name, ParamKind.STRING, description, allowed = allowed)

    private fun int(name: String, description: String, min: Double? = null, max: Double? = null) =
        ParamSpec(name, ParamKind.INT, description, min, max)

    private fun long(name: String, description: String) =
        ParamSpec(name, ParamKind.LONG, description)

    private fun float(name: String, description: String) =
        ParamSpec(name, ParamKind.FLOAT, description)

    private fun bool(name: String, description: String) =
        ParamSpec(name, ParamKind.BOOLEAN, description)

    private fun stringArray(name: String, description: String, allowed: Set<String>? = null) =
        ParamSpec(name, ParamKind.STRING_ARRAY, description, allowed = allowed)

    private fun longArray(name: String, description: String) =
        ParamSpec(name, ParamKind.LONG_ARRAY, description)

    private fun obj(name: String, description: String) =
        ParamSpec(name, ParamKind.OBJECT, description)
}
