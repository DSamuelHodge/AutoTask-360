package com.example.engine

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.speech.tts.TextToSpeech
import android.telephony.SmsManager
import androidx.core.app.NotificationCompat
import com.example.data.AutomationProfile
import com.example.data.AutoTaskRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit

data class StepResult(
    val step: Int,
    val type: String,
    val status: String,
    val detail: String = ""
)

class ActionExecutor(
    private val context: Context,
    private val repository: AutoTaskRepository
) {
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    init {
        try {
            tts = TextToSpeech(context.applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    tts?.language = Locale.US
                    ttsReady = true
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun executeActions(
        profile: AutomationProfile,
        event: AutomationEvent
    ): Pair<String, List<StepResult>> = withContext(Dispatchers.IO) {
        val results = mutableListOf<StepResult>()
        var overallSuccess = true
        var hasFailures = false

        if (profile.actionsJson.isBlank() || profile.actionsJson.trim() == "[]") {
            return@withContext Pair("SUCCESS", emptyList())
        }

        try {
            val jsonArray = JSONArray(profile.actionsJson)
            for (i in 0 until jsonArray.length()) {
                val actionObj = jsonArray.getJSONObject(i)
                val type = actionObj.optString("type", "").uppercase()
                val paramsObj = actionObj.optJSONObject("params") ?: JSONObject()

                val stepRes = runSingleAction(i, type, paramsObj, profile, event)
                results.add(stepRes)

                if (stepRes.status != "OK") {
                    hasFailures = true
                }
            }
        } catch (e: Exception) {
            results.add(StepResult(0, "PARSE_ERROR", "FAILED", e.localizedMessage ?: "Invalid actions JSON"))
            return@withContext Pair("FAILED", results)
        }

        val finalStatus = when {
            hasFailures && results.any { it.status == "OK" } -> "PARTIAL"
            hasFailures -> "FAILED"
            else -> "SUCCESS"
        }

        Pair(finalStatus, results)
    }

    private suspend fun runSingleAction(
        stepIndex: Int,
        type: String,
        params: JSONObject,
        profile: AutomationProfile,
        event: AutomationEvent
    ): StepResult {
        return try {
            when (type) {
                "NOTIFICATION" -> {
                    val rawTitle = params.optString("title", "AutoTask Alert")
                    val rawText = params.optString("text", "Automation triggered")
                    val priority = params.optString("priority", "normal")

                    val title = substituteTemplate(rawTitle, profile, event)
                    val text = substituteTemplate(rawText, profile, event)

                    showNotification(title, text, priority)
                    StepResult(stepIndex, type, "OK", "Notification posted: $title")
                }

                "AUDIO" -> {
                    val modeStr = params.optString("ringerMode", "normal").lowercase()
                    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                    when (modeStr) {
                        "silent" -> audioManager.ringerMode = AudioManager.RINGER_MODE_SILENT
                        "vibrate" -> audioManager.ringerMode = AudioManager.RINGER_MODE_VIBRATE
                        else -> audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
                    }
                    StepResult(stepIndex, type, "OK", "Ringer mode set to $modeStr")
                }

                "DND" -> {
                    val enabled = params.optBoolean("enabled", true)
                    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && nm.isNotificationPolicyAccessGranted) {
                        val filter = if (enabled) NotificationManager.INTERRUPTION_FILTER_PRIORITY else NotificationManager.INTERRUPTION_FILTER_ALL
                        nm.setInterruptionFilter(filter)
                        StepResult(stepIndex, type, "OK", "DND set to $enabled")
                    } else {
                        StepResult(stepIndex, type, "SKIPPED", "Notification policy access permission missing for DND")
                    }
                }

                "BRIGHTNESS" -> {
                    val level = params.optInt("level", 128)
                    try {
                        android.provider.Settings.System.putInt(
                            context.contentResolver,
                            android.provider.Settings.System.SCREEN_BRIGHTNESS,
                            level.coerceIn(0, 255)
                        )
                        StepResult(stepIndex, type, "OK", "Brightness set to $level")
                    } catch (e: Exception) {
                        StepResult(stepIndex, type, "FAILED", "Write settings permission missing: ${e.message}")
                    }
                }

                "FLASHLIGHT" -> {
                    val on = params.optBoolean("on", true)
                    val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
                    val cameraId = cameraManager.cameraIdList.firstOrNull()
                    if (cameraId != null) {
                        cameraManager.setTorchMode(cameraId, on)
                        StepResult(stepIndex, type, "OK", "Flashlight set to $on")
                    } else {
                        StepResult(stepIndex, type, "FAILED", "Camera flashlight not found")
                    }
                }

                "SPEAK" -> {
                    val rawText = params.optString("text", "AutoTask automation executed")
                    val textToSpeak = substituteTemplate(rawText, profile, event)

                    if (ttsReady && tts != null) {
                        tts?.speak(textToSpeak, TextToSpeech.QUEUE_FLUSH, null, "AutoTaskTTS")
                        StepResult(stepIndex, type, "OK", "Spoke: $textToSpeak")
                    } else {
                        StepResult(stepIndex, type, "SKIPPED", "TextToSpeech engine initializing or unavailable")
                    }
                }

                "HTTP" -> {
                    val rawUrl = params.optString("url", "")
                    val method = params.optString("method", "GET").uppercase()
                    val rawBody = params.optString("body", "")

                    val url = substituteTemplate(rawUrl, profile, event)
                    val bodyStr = substituteTemplate(rawBody, profile, event)

                    if (url.isBlank()) {
                        StepResult(stepIndex, type, "FAILED", "HTTP URL is required")
                    } else {
                        val requestBuilder = Request.Builder().url(url)
                        if (method == "POST" || method == "PUT" || method == "PATCH") {
                            val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
                            val reqBody = bodyStr.toRequestBody(mediaType)
                            requestBuilder.method(method, reqBody)
                        } else {
                            requestBuilder.get()
                        }

                        val response = httpClient.newCall(requestBuilder.build()).execute()
                        StepResult(stepIndex, type, if (response.isSuccessful) "OK" else "FAILED", "HTTP $method $url -> ${response.code}")
                    }
                }

                "SEND_SMS" -> {
                    val rawNum = params.optString("number", "")
                    val rawText = params.optString("text", "")

                    val number = substituteTemplate(rawNum, profile, event)
                    val text = substituteTemplate(rawText, profile, event)

                    if (number.isBlank() || text.isBlank()) {
                        StepResult(stepIndex, type, "FAILED", "SMS number and text required")
                    } else {
                        try {
                            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                context.getSystemService(SmsManager::class.java)
                            } else {
                                @Suppress("DEPRECATION")
                                SmsManager.getDefault()
                            }
                            smsManager.sendTextMessage(number, null, text, null, null)
                            StepResult(stepIndex, type, "OK", "SMS sent to $number")
                        } catch (e: Exception) {
                            StepResult(stepIndex, type, "FAILED", "SMS send failed: ${e.localizedMessage}")
                        }
                    }
                }

                "LAUNCH_APP" -> {
                    val pkg = params.optString("packageName", "")
                    if (pkg.isBlank()) {
                        StepResult(stepIndex, type, "FAILED", "packageName parameter missing")
                    } else {
                        val launchIntent = context.packageManager.getLaunchIntentForPackage(pkg)
                        if (launchIntent != null) {
                            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(launchIntent)
                            StepResult(stepIndex, type, "OK", "Launched package $pkg")
                        } else {
                            StepResult(stepIndex, type, "FAILED", "App package $pkg not installed")
                        }
                    }
                }

                "CLIPBOARD" -> {
                    val rawText = params.optString("text", "")
                    val textToCopy = substituteTemplate(rawText, profile, event)
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("AutoTask", textToCopy)
                    clipboard.setPrimaryClip(clip)
                    StepResult(stepIndex, type, "OK", "Copied to clipboard: $textToCopy")
                }

                "PROFILE" -> {
                    val targetProfileId = params.optString("profileId", "")
                    val action = params.optString("action", "enable").lowercase()

                    if (targetProfileId.isBlank()) {
                        StepResult(stepIndex, type, "FAILED", "profileId parameter missing")
                    } else {
                        val target = repository.getProfileById(targetProfileId)
                        if (target != null) {
                            val newStatus = when (action) {
                                "enable" -> true
                                "disable" -> false
                                "toggle" -> !target.isEnabled
                                else -> true
                            }
                            repository.setProfileEnabled(targetProfileId, newStatus)
                            StepResult(stepIndex, type, "OK", "Profile $targetProfileId enabled=$newStatus")
                        } else {
                            StepResult(stepIndex, type, "FAILED", "Target profile $targetProfileId not found")
                        }
                    }
                }

                else -> {
                    StepResult(stepIndex, type, "FAILED", "Unknown action type $type")
                }
            }
        } catch (e: Exception) {
            StepResult(stepIndex, type, "FAILED", e.localizedMessage ?: "Action execution exception")
        }
    }

    private fun showNotification(title: String, text: String, priority: String) {
        val channelId = "autotask_execution_channel"
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "AutoTask Action Notifications",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            nm.createNotificationChannel(channel)
        }

        val importance = when (priority.lowercase()) {
            "high" -> NotificationCompat.PRIORITY_HIGH
            "low" -> NotificationCompat.PRIORITY_LOW
            else -> NotificationCompat.PRIORITY_DEFAULT
        }

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(importance)
            .setAutoCancel(true)

        nm.notify((System.currentTimeMillis() % 100000).toInt(), builder.build())
    }

    private fun substituteTemplate(
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

        result = result.replace("{{levelPercent}}", p["levelPercent"]?.toString() ?: p["level"]?.toString() ?: "")
        result = result.replace("{{isCharging}}", p["isCharging"]?.toString() ?: "")

        result = result.replace("{{ssid}}", p["ssid"]?.toString() ?: "")
        result = result.replace("{{connected}}", p["connected"]?.toString() ?: "")

        result = result.replace("{{packageName}}", p["packageName"]?.toString() ?: "")
        result = result.replace("{{title}}", p["title"]?.toString() ?: "")
        result = result.replace("{{text}}", p["text"]?.toString() ?: "")

        return result
    }

    fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
