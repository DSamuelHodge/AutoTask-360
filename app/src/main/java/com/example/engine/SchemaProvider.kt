package com.example.engine

import org.json.JSONArray
import org.json.JSONObject

object SchemaProvider {

    fun getSchemaJson(): String {
        val root = JSONObject()

        root.put("service", "AutoTask Tool Server Engine")
        root.put("version", "1.0.0")
        root.put("architecture", "Policy is Data. Execution is Deterministic.")

        // Trigger Types
        val triggerTypes = JSONObject()

        val sms = JSONObject()
        sms.put("description", "Fires when SMS is received")
        sms.put("source", "BroadcastReceiver SMS_RECEIVED")
        val smsConfig = JSONObject()
        smsConfig.put("senderContains", "String (e.g. '+1555')")
        smsConfig.put("bodyContains", "String (e.g. 'ALERT')")
        sms.put("configKeys", smsConfig)
        sms.put("templateVars", JSONArray(listOf("{{sender}}", "{{smsBody}}")))
        triggerTypes.put("SMS", sms)

        val battery = JSONObject()
        battery.put("description", "Fires on battery level or status change")
        battery.put("source", "BroadcastReceiver ACTION_BATTERY_CHANGED")
        val batConfig = JSONObject()
        batConfig.put("levelBelow", "Int 1-100")
        batConfig.put("levelAbove", "Int 1-100")
        batConfig.put("isCharging", "Boolean")
        battery.put("configKeys", batConfig)
        battery.put("templateVars", JSONArray(listOf("{{levelPercent}}", "{{isCharging}}")))
        triggerTypes.put("BATTERY", battery)

        val wifi = JSONObject()
        wifi.put("description", "Fires on network state or SSID connection change")
        wifi.put("source", "BroadcastReceiver NETWORK_STATE_CHANGED")
        val wifiConfig = JSONObject()
        wifiConfig.put("ssid", "String SSID name")
        wifiConfig.put("connected", "Boolean")
        wifi.put("configKeys", wifiConfig)
        wifi.put("templateVars", JSONArray(listOf("{{ssid}}", "{{connected}}")))
        triggerTypes.put("WIFI", wifi)

        val screen = JSONObject()
        screen.put("description", "Fires when display turns ON or OFF")
        screen.put("source", "BroadcastReceiver SCREEN_ON / SCREEN_OFF")
        val screenConfig = JSONObject()
        screenConfig.put("state", "String 'ON' or 'OFF'")
        screen.put("configKeys", screenConfig)
        screen.put("templateVars", JSONArray(listOf("{{state}}")))
        triggerTypes.put("SCREEN", screen)

        val bluetooth = JSONObject()
        bluetooth.put("description", "Fires on Bluetooth connection state change")
        bluetooth.put("source", "BroadcastReceiver ACTION_CONNECTION_STATE_CHANGED")
        val btConfig = JSONObject()
        btConfig.put("deviceName", "String device name filter")
        btConfig.put("connected", "Boolean")
        bluetooth.put("configKeys", btConfig)
        bluetooth.put("templateVars", JSONArray(listOf("{{deviceName}}", "{{connected}}")))
        triggerTypes.put("BLUETOOTH", bluetooth)

        val notification = JSONObject()
        notification.put("description", "Fires when status bar notification is posted")
        notification.put("source", "NotificationListenerService")
        val notifConfig = JSONObject()
        notifConfig.put("packageName", "String app package (e.g. com.whatsapp)")
        notifConfig.put("titleContains", "String filter text")
        notifConfig.put("textContains", "String filter text")
        notification.put("configKeys", notifConfig)
        notification.put("templateVars", JSONArray(listOf("{{packageName}}", "{{title}}", "{{text}}")))
        triggerTypes.put("NOTIFICATION", notification)

        val time = JSONObject()
        time.put("description", "Fires on scheduled clock or periodic timer")
        time.put("source", "WorkManager PeriodicWorkRequest")
        val timeConfig = JSONObject()
        timeConfig.put("hour", "Int 0-23")
        timeConfig.put("minute", "Int 0-59")
        timeConfig.put("days", "Array of Strings ['MON', 'TUE', ...]")
        time.put("configKeys", timeConfig)
        time.put("templateVars", JSONArray(listOf("{{hour}}", "{{minute}}")))
        triggerTypes.put("TIME", time)

        val manual = JSONObject()
        manual.put("description", "Direct execution triggered by AI agent or user")
        manual.put("source", "ContentProvider /events or POST /v1/events")
        manual.put("configKeys", JSONObject())
        manual.put("templateVars", JSONArray(listOf("{{payload.*}}")))
        triggerTypes.put("MANUAL", manual)

        val boot = JSONObject()
        boot.put("description", "Fires once upon device startup")
        boot.put("source", "BroadcastReceiver BOOT_COMPLETED")
        boot.put("configKeys", JSONObject())
        boot.put("templateVars", JSONArray(listOf("{{timestamp}}")))
        triggerTypes.put("BOOT", boot)

        val call = JSONObject()
        call.put("description", "Fires on phone ringing or state change")
        call.put("source", "BroadcastReceiver PHONE_STATE")
        val callConfig = JSONObject()
        callConfig.put("state", "String 'RINGING', 'OFFHOOK', 'IDLE'")
        callConfig.put("numberContains", "String phone number filter")
        call.put("configKeys", callConfig)
        call.put("templateVars", JSONArray(listOf("{{number}}", "{{state}}")))
        triggerTypes.put("CALL", call)

        root.put("triggerTypes", triggerTypes)

        // Action Types
        val actionTypes = JSONObject()

        val notifAct = JSONObject()
        notifAct.put("description", "Posts a system notification")
        val notifParams = JSONObject()
        notifParams.put("title", "String (supports templates)")
        notifParams.put("text", "String (supports templates)")
        notifParams.put("priority", "String 'low', 'normal', 'high'")
        notifAct.put("params", notifParams)
        actionTypes.put("NOTIFICATION", notifAct)

        val audioAct = JSONObject()
        audioAct.put("description", "Changes system ringer mode")
        val audioParams = JSONObject()
        audioParams.put("ringerMode", "String 'normal', 'vibrate', 'silent'")
        audioAct.put("params", audioParams)
        actionTypes.put("AUDIO", audioAct)

        val dndAct = JSONObject()
        dndAct.put("description", "Enables or disables Do Not Disturb mode")
        val dndParams = JSONObject()
        dndParams.put("enabled", "Boolean")
        dndAct.put("params", dndParams)
        actionTypes.put("DND", dndAct)

        val brightAct = JSONObject()
        brightAct.put("description", "Sets screen brightness level")
        val brightParams = JSONObject()
        brightParams.put("level", "Int 0-255")
        brightAct.put("params", brightParams)
        actionTypes.put("BRIGHTNESS", brightAct)

        val flashAct = JSONObject()
        flashAct.put("description", "Toggles camera LED flashlight")
        val flashParams = JSONObject()
        flashParams.put("on", "Boolean")
        flashAct.put("params", flashParams)
        actionTypes.put("FLASHLIGHT", flashAct)

        val speakAct = JSONObject()
        speakAct.put("description", "Speaks text aloud using TextToSpeech")
        val speakParams = JSONObject()
        speakParams.put("text", "String (supports templates)")
        speakAct.put("params", speakParams)
        actionTypes.put("SPEAK", speakAct)

        val httpAct = JSONObject()
        httpAct.put("description", "Performs an HTTP client request")
        val httpParams = JSONObject()
        httpParams.put("url", "String URL endpoint")
        httpParams.put("method", "String 'GET', 'POST', 'PUT', 'PATCH', 'DELETE'")
        httpParams.put("body", "String request body")
        httpAct.put("params", httpParams)
        actionTypes.put("HTTP", httpAct)

        val smsAct = JSONObject()
        smsAct.put("description", "Sends an outgoing SMS text message")
        val smsParams = JSONObject()
        smsParams.put("number", "String target phone number")
        smsParams.put("text", "String SMS message body")
        smsAct.put("params", smsParams)
        actionTypes.put("SEND_SMS", smsAct)

        val appAct = JSONObject()
        appAct.put("description", "Launches an installed Android app")
        val appParams = JSONObject()
        appParams.put("packageName", "String target package name")
        appAct.put("params", appParams)
        actionTypes.put("LAUNCH_APP", appAct)

        val clipAct = JSONObject()
        clipAct.put("description", "Copies text to system clipboard")
        val clipParams = JSONObject()
        clipParams.put("text", "String content to copy")
        clipAct.put("params", clipParams)
        actionTypes.put("CLIPBOARD", clipAct)

        val profileAct = JSONObject()
        profileAct.put("description", "Enables, disables, or toggles another profile")
        val profileParams = JSONObject()
        profileParams.put("profileId", "String target profile ID")
        profileParams.put("action", "String 'enable', 'disable', 'toggle'")
        profileAct.put("params", profileParams)
        actionTypes.put("PROFILE", profileAct)

        root.put("actionTypes", actionTypes)

        // Universal Template Variables
        val globalVars = JSONArray(listOf(
            "{{triggerType}}",
            "{{timestamp}}",
            "{{profileId}}",
            "{{profileName}}"
        ))
        root.put("universalTemplateVariables", globalVars)

        return root.toString(2)
    }
}
