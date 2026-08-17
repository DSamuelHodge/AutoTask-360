package com.example.engine.actions.handlers

import android.content.Intent
import com.example.data.ExecutionLog
import com.example.engine.StepResult
import com.example.engine.actions.ActionHandler
import com.example.engine.actions.ActionRequest
import kotlinx.coroutines.delay
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File

class HttpActionHandler : ActionHandler {
    override val type: String = "HTTP"
    override val timeoutMs: Long = 15_000L

    override suspend fun execute(request: ActionRequest): StepResult {
        val url = request.substitute(request.params.optString("url", ""))
        val method = request.params.optString("method", "GET").uppercase()
        val bodyStr = request.substitute(request.params.optString("body", ""))
        if (url.isBlank()) {
            return StepResult(request.stepIndex, type, "FAILED", "HTTP URL is required")
        }
        val requestBuilder = Request.Builder().url(url)
        if (method == "POST" || method == "PUT" || method == "PATCH") {
            val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
            requestBuilder.method(method, bodyStr.toRequestBody(mediaType))
        } else {
            requestBuilder.get()
        }
        val response = request.services.httpClient.value.newCall(requestBuilder.build()).execute()
        return StepResult(
            request.stepIndex,
            type,
            if (response.isSuccessful) "OK" else "FAILED",
            "HTTP $method $url -> ${response.code}"
        )
    }
}

class WriteFileActionHandler : ActionHandler {
    override val type: String = "WRITE_FILE"

    override suspend fun execute(request: ActionRequest): StepResult {
        val path = request.substitute(request.params.optString("path", "autotask_output.txt"))
        val content = request.substitute(request.params.optString("content", ""))
        val append = request.params.optBoolean("append", true)
        val targetFile = if (path.startsWith("/")) File(path) else File(request.context.filesDir, path)
        targetFile.parentFile?.mkdirs()
        if (append) targetFile.appendText(content + "\n") else targetFile.writeText(content + "\n")
        return StepResult(request.stepIndex, type, "OK", "Wrote to ${targetFile.name}")
    }
}

class ReadFileActionHandler : ActionHandler {
    override val type: String = "READ_FILE"

    override suspend fun execute(request: ActionRequest): StepResult {
        val path = request.substitute(request.params.optString("path", "autotask_output.txt"))
        val targetFile = if (path.startsWith("/")) File(path) else File(request.context.filesDir, path)
        return if (targetFile.exists()) {
            val text = targetFile.readText()
            StepResult(request.stepIndex, type, "OK", "Read ${text.length} chars from ${targetFile.name}")
        } else {
            StepResult(request.stepIndex, type, "FAILED", "File $path not found")
        }
    }
}

class BroadcastActionHandler : ActionHandler {
    override val type: String = "BROADCAST"

    override suspend fun execute(request: ActionRequest): StepResult {
        val action = request.substitute(request.params.optString("action", "com.example.autotask.CUSTOM_ACTION"))
        val bIntent = Intent(action)
        val extras = request.params.optJSONObject("extras")
        extras?.keys()?.forEach { k ->
            bIntent.putExtra(k, extras.optString(k))
        }
        request.context.sendBroadcast(bIntent)
        return StepResult(request.stepIndex, type, "OK", "Broadcast sent for $action")
    }
}

class ProfileActionHandler : ActionHandler {
    override val type: String = "PROFILE"

    override suspend fun execute(request: ActionRequest): StepResult {
        val targetProfileId = request.params.optString("profileId", "")
        val action = request.params.optString("action", "enable").lowercase()
        if (targetProfileId.isBlank()) {
            return StepResult(request.stepIndex, type, "FAILED", "profileId parameter missing")
        }
        val target = request.repository.getProfileById(targetProfileId)
            ?: return StepResult(request.stepIndex, type, "FAILED", "Target profile $targetProfileId not found")
        val newStatus = when (action) {
            "enable" -> true
            "disable" -> false
            "toggle" -> !target.isEnabled
            else -> true
        }
        request.repository.setProfileEnabled(targetProfileId, newStatus)
        return StepResult(request.stepIndex, type, "OK", "Profile $targetProfileId enabled=$newStatus")
    }
}

class WaitActionHandler : ActionHandler {
    override val type: String = "WAIT"

    override suspend fun execute(request: ActionRequest): StepResult {
        val durationMs = request.params.optLong("durationMs", 1000L).coerceIn(0L, 30_000L)
        delay(durationMs)
        return StepResult(request.stepIndex, type, "OK", "Waited ${durationMs}ms")
    }
}

class LogActionHandler : ActionHandler {
    override val type: String = "LOG"

    override suspend fun execute(request: ActionRequest): StepResult {
        val msg = request.substitute(request.params.optString("message", "Custom log execution step"))
        val level = request.params.optString("level", "INFO").uppercase()
        request.repository.insertLog(
            ExecutionLog(
                profileId = request.profile.id,
                profileName = request.profile.name,
                triggerType = request.profile.triggerType,
                status = level,
                skippedReason = "",
                actionsResultJson = "[{\"step\":${request.stepIndex},\"type\":\"LOG\",\"status\":\"OK\",\"detail\":${JSONObject.quote("LOG Action: $msg")}}] ",
                durationMs = 0L
            )
        )
        return StepResult(request.stepIndex, type, "OK", "Logged: $msg")
    }
}
