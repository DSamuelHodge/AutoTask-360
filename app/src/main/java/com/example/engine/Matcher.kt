package com.example.engine

import com.example.data.AutomationProfile
import org.json.JSONObject

data class MatchResult(
    val isMatch: Boolean,
    val skippedReason: String = ""
)

object Matcher {

    fun evaluate(
        profile: AutomationProfile,
        event: AutomationEvent,
        deviceState: Map<String, Any?> = emptyMap(),
        nowMs: Long = System.currentTimeMillis()
    ): MatchResult {
        // 1. Check if enabled
        if (!profile.isEnabled) {
            return MatchResult(false, "profile_disabled")
        }

        // 2. Check Cooldown
        if (profile.cooldownMs > 0 && (nowMs - profile.lastTriggeredAt) < profile.cooldownMs) {
            return MatchResult(false, "cooldown_active")
        }

        // 3. Evaluate trigger_config_json against event.payload
        val configMatchReason = evaluateTriggerConfig(profile.triggerType, profile.triggerConfigJson, event.payload)
        if (configMatchReason != null) {
            return MatchResult(false, configMatchReason)
        }

        // 4. Evaluate conditions_json against runtime deviceState
        val conditionMatchReason = evaluateConditions(profile.conditionsJson, deviceState)
        if (conditionMatchReason != null) {
            return MatchResult(false, conditionMatchReason)
        }

        return MatchResult(true, "")
    }

    private fun evaluateTriggerConfig(
        triggerType: String,
        configJsonStr: String,
        payload: Map<String, Any?>
    ): String? {
        if (configJsonStr.isBlank() || configJsonStr.trim() == "{}") return null

        try {
            val json = JSONObject(configJsonStr)

            when (triggerType.uppercase()) {
                "SMS" -> {
                    if (json.has("senderContains")) {
                        val senderContains = json.getString("senderContains")
                        val sender = payload["sender"]?.toString() ?: ""
                        if (!sender.contains(senderContains, ignoreCase = true)) return "config_mismatch"
                    }
                    if (json.has("bodyContains")) {
                        val bodyContains = json.getString("bodyContains")
                        val smsBody = payload["smsBody"]?.toString() ?: ""
                        if (!smsBody.contains(bodyContains, ignoreCase = true)) return "config_mismatch"
                    }
                }
                "BATTERY" -> {
                    val level = (payload["level"] as? Number)?.toInt()
                        ?: (payload["levelPercent"] as? Number)?.toInt() ?: 100
                    if (json.has("levelBelow")) {
                        val levelBelow = json.getInt("levelBelow")
                        if (level >= levelBelow) return "config_mismatch"
                    }
                    if (json.has("levelAbove")) {
                        val levelAbove = json.getInt("levelAbove")
                        if (level <= levelAbove) return "config_mismatch"
                    }
                    if (json.has("isCharging")) {
                        val requiredCharging = json.getBoolean("isCharging")
                        val actualCharging = (payload["isCharging"] as? Boolean) ?: false
                        if (requiredCharging != actualCharging) return "config_mismatch"
                    }
                }
                "WIFI" -> {
                    if (json.has("ssid")) {
                        val requiredSsid = json.getString("ssid")
                        val actualSsid = payload["ssid"]?.toString() ?: ""
                        if (!actualSsid.equals(requiredSsid, ignoreCase = true) && !actualSsid.contains(requiredSsid, ignoreCase = true)) {
                            return "config_mismatch"
                        }
                    }
                    if (json.has("connected")) {
                        val requiredConn = json.getBoolean("connected")
                        val actualConn = (payload["connected"] as? Boolean) ?: false
                        if (requiredConn != actualConn) return "config_mismatch"
                    }
                }
                "SCREEN" -> {
                    if (json.has("state")) {
                        val requiredState = json.getString("state")
                        val actualState = payload["state"]?.toString() ?: ""
                        if (!actualState.equals(requiredState, ignoreCase = true)) return "config_mismatch"
                    }
                }
                "BLUETOOTH" -> {
                    if (json.has("deviceName")) {
                        val requiredDevice = json.getString("deviceName")
                        val actualDevice = payload["deviceName"]?.toString() ?: ""
                        if (!actualDevice.contains(requiredDevice, ignoreCase = true)) return "config_mismatch"
                    }
                    if (json.has("connected")) {
                        val requiredConn = json.getBoolean("connected")
                        val actualConn = (payload["connected"] as? Boolean) ?: false
                        if (requiredConn != actualConn) return "config_mismatch"
                    }
                }
                "NOTIFICATION" -> {
                    if (json.has("packageName")) {
                        val reqPkg = json.getString("packageName")
                        val actPkg = payload["packageName"]?.toString() ?: ""
                        if (!actPkg.equals(reqPkg, ignoreCase = true)) return "config_mismatch"
                    }
                    if (json.has("titleContains")) {
                        val reqTitle = json.getString("titleContains")
                        val actTitle = payload["title"]?.toString() ?: ""
                        if (!actTitle.contains(reqTitle, ignoreCase = true)) return "config_mismatch"
                    }
                    if (json.has("textContains")) {
                        val reqText = json.getString("textContains")
                        val actText = payload["text"]?.toString() ?: ""
                        if (!actText.contains(reqText, ignoreCase = true)) return "config_mismatch"
                    }
                }
                "TIME" -> {
                    if (json.has("hour")) {
                        val reqHour = json.getInt("hour")
                        val actHour = (payload["hour"] as? Number)?.toInt() ?: -1
                        if (actHour != -1 && actHour != reqHour) return "config_mismatch"
                    }
                    if (json.has("minute")) {
                        val reqMin = json.getInt("minute")
                        val actMin = (payload["minute"] as? Number)?.toInt() ?: -1
                        if (actMin != -1 && actMin != reqMin) return "config_mismatch"
                    }
                }
                "CALL" -> {
                    if (json.has("state")) {
                        val reqState = json.getString("state")
                        val actState = payload["state"]?.toString() ?: ""
                        if (!actState.equals(reqState, ignoreCase = true)) return "config_mismatch"
                    }
                    if (json.has("numberContains")) {
                        val reqNum = json.getString("numberContains")
                        val actNum = payload["number"]?.toString() ?: ""
                        if (!actNum.contains(reqNum)) return "config_mismatch"
                    }
                }
            }
        } catch (e: Exception) {
            return "config_invalid_json"
        }
        return null
    }

    private fun evaluateConditions(
        conditionsJsonStr: String,
        deviceState: Map<String, Any?>
    ): String? {
        if (conditionsJsonStr.isBlank() || conditionsJsonStr.trim() == "{}") return null

        try {
            val json = JSONObject(conditionsJsonStr)

            if (json.has("isScreenOn")) {
                val reqScreen = json.getBoolean("isScreenOn")
                val actScreen = (deviceState["isScreenOn"] as? Boolean) ?: false
                if (reqScreen != actScreen) return "condition_not_met"
            }
            if (json.has("isCharging")) {
                val reqChg = json.getBoolean("isCharging")
                val actChg = (deviceState["isCharging"] as? Boolean) ?: false
                if (reqChg != actChg) return "condition_not_met"
            }
            if (json.has("batteryAbove")) {
                val reqBat = json.getInt("batteryAbove")
                val actBat = (deviceState["batteryLevel"] as? Number)?.toInt() ?: 100
                if (actBat <= reqBat) return "condition_not_met"
            }
            if (json.has("ringerMode")) {
                val reqRinger = json.getString("ringerMode")
                val actRinger = deviceState["ringerMode"]?.toString() ?: ""
                if (!actRinger.equals(reqRinger, ignoreCase = true)) return "condition_not_met"
            }
        } catch (e: Exception) {
            return "condition_invalid_json"
        }
        return null
    }
}
