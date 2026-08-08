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
        nowMs: Long = System.currentTimeMillis(),
        evaluateCooldown: Boolean = true,
        evaluateTriggerConfig: Boolean = true
    ): MatchResult {
        // 1. Check if enabled
        if (!profile.isEnabled) {
            return MatchResult(false, "profile_disabled")
        }

        // 2. Check Cooldown
        if (evaluateCooldown && profile.cooldownMs > 0 && (nowMs - profile.lastTriggeredAt) < profile.cooldownMs) {
            return MatchResult(false, "cooldown_active")
        }

        // 3. Evaluate trigger_config_json against event.payload
        if (evaluateTriggerConfig) {
            val configMatchReason = evaluateTriggerConfig(profile.triggerType, profile.triggerConfigJson, event.payload)
            if (configMatchReason != null) {
                return MatchResult(false, configMatchReason)
            }
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
            val type = triggerType.uppercase()

            when (type) {
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
                    if (json.has("isLow")) {
                        val reqLow = json.getBoolean("isLow")
                        val actLow = (payload["isLow"] as? Boolean) ?: (level <= 15)
                        if (reqLow != actLow) return "config_mismatch"
                    }
                }
                "POWER" -> {
                    if (json.has("connected")) {
                        val reqConn = json.getBoolean("connected")
                        val actConn = (payload["connected"] as? Boolean) ?: false
                        if (reqConn != actConn) return "config_mismatch"
                    }
                }
                "POWER_SAVE", "AIRPLANE_MODE" -> {
                    if (json.has("enabled")) {
                        val reqEnabled = json.getBoolean("enabled")
                        val actEnabled = (payload["enabled"] as? Boolean) ?: false
                        if (reqEnabled != actEnabled) return "config_mismatch"
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
                "BLUETOOTH" -> {
                    if (json.has("deviceName")) {
                        val requiredDevice = json.getString("deviceName")
                        val actualDevice = payload["deviceName"]?.toString() ?: ""
                        if (!actualDevice.contains(requiredDevice, ignoreCase = true)) return "config_mismatch"
                    }
                    if (json.has("deviceAddress")) {
                        val reqMac = json.getString("deviceAddress")
                        val actMac = payload["deviceAddress"]?.toString() ?: ""
                        if (!actMac.equals(reqMac, ignoreCase = true)) return "config_mismatch"
                    }
                    if (json.has("connected")) {
                        val requiredConn = json.getBoolean("connected")
                        val actualConn = (payload["connected"] as? Boolean) ?: false
                        if (requiredConn != actualConn) return "config_mismatch"
                    }
                }
                "BLUETOOTH_STATE" -> {
                    if (json.has("state")) {
                        val reqState = json.getString("state")
                        val actState = payload["state"]?.toString() ?: ""
                        if (!actState.equals(reqState, ignoreCase = true)) return "config_mismatch"
                    }
                }
                "SCREEN" -> {
                    if (json.has("state")) {
                        val requiredState = json.getString("state")
                        val actualState = payload["state"]?.toString() ?: payload["screenState"]?.toString() ?: ""
                        if (!actualState.equals(requiredState, ignoreCase = true)) return "config_mismatch"
                    }
                }
                "DOZE" -> {
                    if (json.has("entering")) {
                        val req = json.getBoolean("entering")
                        val act = (payload["entering"] as? Boolean) ?: false
                        if (req != act) return "config_mismatch"
                    }
                }
                "DREAMING" -> {
                    if (json.has("active")) {
                        val req = json.getBoolean("active")
                        val act = (payload["active"] as? Boolean) ?: false
                        if (req != act) return "config_mismatch"
                    }
                }
                "APP_LAUNCH", "FOREGROUND_APP", "PACKAGE_CHANGED" -> {
                    if (json.has("packageName")) {
                        val reqPkg = json.getString("packageName")
                        val actPkg = payload["packageName"]?.toString() ?: ""
                        if (!actPkg.equals(reqPkg, ignoreCase = true)) return "config_mismatch"
                    }
                    if (json.has("event")) {
                        val reqEv = json.getString("event")
                        val actEv = payload["event"]?.toString() ?: ""
                        if (!actEv.equals(reqEv, ignoreCase = true)) return "config_mismatch"
                    }
                }
                "NOTIFICATION", "NOTIFICATION_REMOVED" -> {
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
                "INCOMING_CALL", "OUTGOING_CALL", "CALL" -> {
                    if (json.has("state")) {
                        val reqState = json.getString("state")
                        val actState = payload["state"]?.toString() ?: payload["callState"]?.toString() ?: ""
                        if (!actState.equals(reqState, ignoreCase = true)) return "config_mismatch"
                    }
                    if (json.has("numberContains")) {
                        val reqNum = json.getString("numberContains")
                        val actNum = payload["number"]?.toString() ?: ""
                        if (!actNum.contains(reqNum)) return "config_mismatch"
                    }
                }
                "HEADSET", "USB" -> {
                    if (json.has("connected")) {
                        val reqConn = json.getBoolean("connected")
                        val actConn = (payload["connected"] as? Boolean) ?: false
                        if (reqConn != actConn) return "config_mismatch"
                    }
                }
                "PROXIMITY" -> {
                    if (json.has("near")) {
                        val reqNear = json.getBoolean("near")
                        val actNear = (payload["near"] as? Boolean) ?: false
                        if (reqNear != actNear) return "config_mismatch"
                    }
                }
                "LIGHT" -> {
                    val lux = (payload["lux"] as? Number)?.toFloat() ?: (payload["belowLux"] as? Number)?.toFloat() ?: 0f
                    if (json.has("belowLux")) {
                        val thresh = json.getDouble("belowLux").toFloat()
                        if (lux >= thresh) return "config_mismatch"
                    }
                    if (json.has("aboveLux")) {
                        val thresh = json.getDouble("aboveLux").toFloat()
                        if (lux <= thresh) return "config_mismatch"
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
                "CUSTOM_INTENT" -> {
                    if (json.has("action")) {
                        val reqAct = json.getString("action")
                        val actAct = payload["action"]?.toString() ?: ""
                        if (!actAct.equals(reqAct, ignoreCase = true)) return "config_mismatch"
                    }
                }
                "MANUAL" -> {
                    if (json.has("profileId")) {
                        val reqId = json.getString("profileId")
                        val actId = payload["profileId"]?.toString() ?: ""
                        if (actId.isNotEmpty() && !actId.equals(reqId, ignoreCase = true)) return "config_mismatch"
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
