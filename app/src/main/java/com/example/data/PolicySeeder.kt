package com.example.data

object PolicySeeder {
    fun getStarterProfiles(): List<AutomationProfile> {
        val now = System.currentTimeMillis()
        return listOf(
            AutomationProfile(
                id = "cos-battery-advisory",
                name = "Low Battery Advisory",
                description = "Speaks alert and posts notification when battery drops below 20%",
                isEnabled = false,
                triggerType = "BATTERY",
                triggerConfigJson = """{"levelBelow":20,"isCharging":false}""",
                conditionsJson = "{}",
                actionsJson = """[
                    {"type":"NOTIFICATION","params":{"title":"Low Battery Alert","text":"Battery level is {{levelPercent}}%","priority":"high"}},
                    {"type":"SPEAK","params":{"text":"Warning: Battery level is at {{levelPercent}} percent."}}
                ]""".trimIndent(),
                cooldownMs = 300000L, // 5 min cooldown
                priority = 10,
                createdAt = now,
                updatedAt = now
            ),
            AutomationProfile(
                id = "cos-wifi-welcome",
                name = "Wi-Fi Connection Notifier",
                description = "Notifies when connected to a Wi-Fi network",
                isEnabled = false,
                triggerType = "WIFI",
                triggerConfigJson = """{"connected":true}""",
                conditionsJson = "{}",
                actionsJson = """[
                    {"type":"NOTIFICATION","params":{"title":"Wi-Fi Connected","text":"Connected to {{ssid}}","priority":"normal"}}
                ]""".trimIndent(),
                cooldownMs = 60000L,
                priority = 5,
                createdAt = now,
                updatedAt = now
            ),
            AutomationProfile(
                id = "cos-quiet-night",
                name = "Night Mode Silence",
                description = "Sets ringer mode to silent at 10:00 PM",
                isEnabled = false,
                triggerType = "TIME",
                triggerConfigJson = """{"hour":22,"minute":0}""",
                conditionsJson = "{}",
                actionsJson = """[
                    {"type":"AUDIO","params":{"ringerMode":"silent"}},
                    {"type":"NOTIFICATION","params":{"title":"AutoTask","text":"Silent mode activated for night.","priority":"low"}}
                ]""".trimIndent(),
                cooldownMs = 3600000L,
                priority = 8,
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
                id = "cos-boot-check",
                name = "System Boot Health Check",
                description = "Executes on system startup to verify AutoTask engine readiness",
                isEnabled = false,
                triggerType = "BOOT",
                triggerConfigJson = "{}",
                conditionsJson = "{}",
                actionsJson = """[
                    {"type":"NOTIFICATION","params":{"title":"AutoTask Ready","text":"Tool Server engine initialized and ready on boot.","priority":"normal"}}
                ]""".trimIndent(),
                cooldownMs = 0L,
                priority = 10,
                createdAt = now,
                updatedAt = now
            )
        )
    }
}
