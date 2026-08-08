package com.example.data

object PolicySeeder {
    fun getStarterProfiles(): List<AutomationProfile> {
        val now = System.currentTimeMillis()
        return listOf(
            AutomationProfile(
                id = "cos-battery-advisory",
                name = "Low Battery Advisory",
                description = "Speaks alert and posts notification when battery drops below 20%",
                isEnabled = true,
                triggerType = "BATTERY",
                triggerConfigJson = """{"levelBelow":20,"isCharging":false}""",
                conditionsJson = "{}",
                actionsJson = """[
                    {"type":"NOTIFICATION","params":{"title":"Low Battery Alert","text":"Battery level is {{levelPercent}}%","priority":"high"}},
                    {"type":"SPEAK","params":{"text":"Warning: Battery level is at {{levelPercent}} percent."}}
                ]""".trimIndent(),
                cooldownMs = 300000L,
                priority = 10,
                createdAt = now,
                updatedAt = now
            ),
            AutomationProfile(
                id = "cos-power-save-auto",
                name = "Power Saver Response",
                description = "Triggers toast popup and vibration pattern when Battery Saver mode activates",
                isEnabled = true,
                triggerType = "POWER_SAVE",
                triggerConfigJson = """{"enabled":true}""",
                conditionsJson = "{}",
                actionsJson = """[
                    {"type":"TOAST","params":{"text":"Power Saver Mode Engaged","duration":"short"}},
                    {"type":"VIBRATE","params":{"durationMs":300}}
                ]""".trimIndent(),
                cooldownMs = 60000L,
                priority = 8,
                createdAt = now,
                updatedAt = now
            ),
            AutomationProfile(
                id = "cos-wifi-welcome",
                name = "Wi-Fi Connection Notifier",
                description = "Notifies and logs when connected to a Wi-Fi network",
                isEnabled = true,
                triggerType = "WIFI",
                triggerConfigJson = """{"connected":true}""",
                conditionsJson = "{}",
                actionsJson = """[
                    {"type":"NOTIFICATION","params":{"title":"Wi-Fi Connected","text":"Connected to {{ssid}}","priority":"normal"}},
                    {"type":"LOG","params":{"message":"Auto-connected to Wi-Fi SSID {{ssid}}","level":"INFO"}}
                ]""".trimIndent(),
                cooldownMs = 60000L,
                priority = 5,
                createdAt = now,
                updatedAt = now
            ),
            AutomationProfile(
                id = "cos-bluetooth-audio",
                name = "Bluetooth Audio Connect",
                description = "Speaks connection event and adjusts volume when Bluetooth device connects",
                isEnabled = true,
                triggerType = "BLUETOOTH",
                triggerConfigJson = """{"connected":true}""",
                conditionsJson = "{}",
                actionsJson = """[
                    {"type":"SPEAK","params":{"text":"Connected to Bluetooth device {{deviceName}}."}},
                    {"type":"AUDIO","params":{"ringerMode":"normal","stream":"media","volume":10}}
                ]""".trimIndent(),
                cooldownMs = 30000L,
                priority = 7,
                createdAt = now,
                updatedAt = now
            ),
            AutomationProfile(
                id = "cos-quiet-night",
                name = "Night Mode Silence",
                description = "Sets ringer mode to silent and enables DND at 10:00 PM",
                isEnabled = true,
                triggerType = "TIME",
                triggerConfigJson = """{"hour":22,"minute":0}""",
                conditionsJson = "{}",
                actionsJson = """[
                    {"type":"AUDIO","params":{"ringerMode":"silent"}},
                    {"type":"DND","params":{"enabled":true,"policy":"priority"}},
                    {"type":"NOTIFICATION","params":{"title":"AutoTask","text":"Silent mode activated for night.","priority":"low"}}
                ]""".trimIndent(),
                cooldownMs = 3600000L,
                priority = 8,
                createdAt = now,
                updatedAt = now
            ),
            AutomationProfile(
                id = "cos-headset-jack",
                name = "Wired Headset Auto Adjust",
                description = "Shows toast and logs when wired headset is plugged or unplugged",
                isEnabled = true,
                triggerType = "HEADSET",
                triggerConfigJson = """{"connected":true}""",
                conditionsJson = "{}",
                actionsJson = """[
                    {"type":"TOAST","params":{"text":"Wired headset connected","duration":"short"}},
                    {"type":"VIBRATE","params":{"durationMs":150}}
                ]""".trimIndent(),
                cooldownMs = 10000L,
                priority = 6,
                createdAt = now,
                updatedAt = now
            ),
            AutomationProfile(
                id = "cos-sms-status-reader",
                name = "SMS Status Reader",
                description = "Announces incoming SMS messages containing the keyword 'status'",
                isEnabled = false,
                triggerType = "SMS",
                triggerConfigJson = """{"bodyContains":"status"}""",
                conditionsJson = "{}",
                actionsJson = """[
                    {"type":"SPEAK","params":{"text":"Status request received from {{sender}}."}},
                    {"type":"NOTIFICATION","params":{"title":"SMS Status Request","text":"From {{sender}}: {{smsBody}}","priority":"normal"}}
                ]""".trimIndent(),
                cooldownMs = 30000L,
                priority = 7,
                createdAt = now,
                updatedAt = now
            ),
            AutomationProfile(
                id = "cos-screen-unlock-welcome",
                name = "Device Unlock Greeting",
                description = "Fires a toast popup and vibrates when device is unlocked by user",
                isEnabled = false,
                triggerType = "DEVICE_UNLOCKED",
                triggerConfigJson = "{}",
                conditionsJson = "{}",
                actionsJson = """[
                    {"type":"TOAST","params":{"text":"Welcome back! AutoTask engine active.","duration":"short"}}
                ]""".trimIndent(),
                cooldownMs = 60000L,
                priority = 4,
                createdAt = now,
                updatedAt = now
            ),
            AutomationProfile(
                id = "cos-incoming-call-flash",
                name = "Incoming Call Strobe Alert",
                description = "Toggles flashlight torch and notifies on incoming phone calls",
                isEnabled = false,
                triggerType = "INCOMING_CALL",
                triggerConfigJson = """{"state":"RINGING"}""",
                conditionsJson = "{}",
                actionsJson = """[
                    {"type":"FLASHLIGHT","params":{"on":true}},
                    {"type":"NOTIFICATION","params":{"title":"Incoming Call","text":"Call ringing from {{number}}","priority":"high"}}
                ]""".trimIndent(),
                cooldownMs = 5000L,
                priority = 9,
                createdAt = now,
                updatedAt = now
            ),
            AutomationProfile(
                id = "cos-light-sensor-brightness",
                name = "Low Light Ambient Mode",
                description = "Adjusts display brightness level when ambient light drops below 10 lux",
                isEnabled = false,
                triggerType = "LIGHT",
                triggerConfigJson = """{"belowLux":10}""",
                conditionsJson = "{}",
                actionsJson = """[
                    {"type":"BRIGHTNESS","params":{"level":30,"auto":false}},
                    {"type":"TOAST","params":{"text":"Low light detected. Brightness dimmed.","duration":"short"}}
                ]""".trimIndent(),
                cooldownMs = 120000L,
                priority = 5,
                createdAt = now,
                updatedAt = now
            ),
            AutomationProfile(
                id = "cos-airplane-mode-guard",
                name = "Airplane Mode Logger",
                description = "Logs event and displays toast when Airplane Mode is toggled",
                isEnabled = false,
                triggerType = "AIRPLANE_MODE",
                triggerConfigJson = "{}",
                conditionsJson = "{}",
                actionsJson = """[
                    {"type":"LOG","params":{"message":"Airplane mode state toggled","level":"INFO"}},
                    {"type":"TOAST","params":{"text":"Airplane Mode State Updated","duration":"short"}}
                ]""".trimIndent(),
                cooldownMs = 10000L,
                priority = 3,
                createdAt = now,
                updatedAt = now
            ),
            AutomationProfile(
                id = "cos-boot-check",
                name = "System Boot Health Check",
                description = "Executes on system startup to verify AutoTask engine readiness",
                isEnabled = true,
                triggerType = "BOOT",
                triggerConfigJson = "{}",
                conditionsJson = "{}",
                actionsJson = """[
                    {"type":"NOTIFICATION","params":{"title":"AutoTask Ready","text":"Tool Server engine initialized and ready on boot.","priority":"normal"}},
                    {"type":"LOG","params":{"message":"System startup verified by AutoTask engine","level":"INFO"}}
                ]""".trimIndent(),
                cooldownMs = 0L,
                priority = 10,
                createdAt = now,
                updatedAt = now
            )
        )
    }
}

