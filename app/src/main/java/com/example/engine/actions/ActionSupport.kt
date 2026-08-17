package com.example.engine.actions

import com.example.data.AutomationProfile
import com.example.engine.AutomationEvent

object ActionSupport {
    fun substitute(
        input: String,
        profile: AutomationProfile,
        event: AutomationEvent
    ): String {
        var result = input
        val p = event.payload

        result = result.replace("{{triggerType}}", event.type)
        result = result.replace("{{timestamp}}", event.timestamp.toString())
        result = result.replace("{{profileId}}", profile.id)
        result = result.replace("{{profileName}}", profile.name)

        result = result.replace("{{sender}}", p["sender"]?.toString() ?: "")
        result = result.replace("{{smsBody}}", p["smsBody"]?.toString() ?: "")
        result = result.replace("{{number}}", p["number"]?.toString() ?: "")
        result = result.replace("{{callState}}", p["state"]?.toString() ?: p["callState"]?.toString() ?: "")

        result = result.replace("{{levelPercent}}", p["levelPercent"]?.toString() ?: p["level"]?.toString() ?: "")
        result = result.replace("{{isCharging}}", p["isCharging"]?.toString() ?: "")

        result = result.replace("{{ssid}}", p["ssid"]?.toString() ?: "")
        result = result.replace("{{connected}}", p["connected"]?.toString() ?: "")
        result = result.replace("{{deviceName}}", p["deviceName"]?.toString() ?: "")

        result = result.replace("{{packageName}}", p["packageName"]?.toString() ?: "")
        result = result.replace("{{url}}", p["url"]?.toString() ?: "")
        result = result.replace("{{title}}", p["title"]?.toString() ?: "")
        result = result.replace("{{text}}", p["text"]?.toString() ?: "")
        result = result.replace("{{screenState}}", p["state"]?.toString() ?: p["screenState"]?.toString() ?: "")

        result = result.replace("{{hour}}", p["hour"]?.toString() ?: "")
        result = result.replace("{{minute}}", p["minute"]?.toString() ?: "")

        return result
    }
}
