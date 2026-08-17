package com.example.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.application.AutomationCommandFacade
import com.example.data.AutomationProfile
import com.example.data.ExecutionLog
import com.example.engine.AutomationEvent
import com.example.server.KtorServerConfig
import com.example.server.KtorServerSnapshot
import com.example.service.AutoTaskService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class AutoTaskViewModel(application: Application) : AndroidViewModel(application) {

    private val commands = AutomationCommandFacade.getInstance(application)

    val profiles: StateFlow<List<AutomationProfile>> = commands.profilesFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val logs: StateFlow<List<ExecutionLog>> = commands.logsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _isServiceRunning = MutableStateFlow(false)
    val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

    private val _apiTestResponse = MutableStateFlow<String>("")
    val apiTestResponse: StateFlow<String> = _apiTestResponse.asStateFlow()

    private val _isApiTestLoading = MutableStateFlow(false)
    val isApiTestLoading: StateFlow<Boolean> = _isApiTestLoading.asStateFlow()

    private val _ktorServerConfig = MutableStateFlow(KtorServerConfig.getSnapshot(application))
    val ktorServerConfig: StateFlow<KtorServerSnapshot> = _ktorServerConfig.asStateFlow()

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()
    }

    init {
        // Ensure default recipes exist on startup
        viewModelScope.launch {
            commands.seedDefaults()
            AutoTaskService.startService(application)
            refreshRuntimeStatus()
        }

        viewModelScope.launch {
            while (true) {
                refreshRuntimeStatus()
                delay(1000L)
            }
        }
    }

    fun toggleService(enable: Boolean) {
        val app = getApplication<Application>()
        if (enable) {
            AutoTaskService.startService(app)
            commands.setRunningState(true)
            refreshRuntimeStatus()
        } else {
            AutoTaskService.stopService(app)
            commands.setRunningState(false)
            refreshRuntimeStatus()
        }
    }

    fun setKtorServerEnabled(enabled: Boolean) {
        val app = getApplication<Application>()
        KtorServerConfig.setEnabled(app, enabled)
        AutoTaskService.restartKtorServer(app)
        refreshRuntimeStatus()
    }

    fun setKtorServerPort(portText: String) {
        val app = getApplication<Application>()
        val port = portText.toIntOrNull()
        if (port == null) {
            _apiTestResponse.value = "Ktor Server Config Failed:\nPort must be a number."
            return
        }

        val validationError = KtorServerConfig.savePort(app, port)
        if (validationError != null) {
            _apiTestResponse.value = "Ktor Server Config Failed:\n$validationError"
            refreshRuntimeStatus()
            return
        }

        AutoTaskService.restartKtorServer(app)
        _apiTestResponse.value = "Ktor server restart requested for ${KtorServerConfig.bindHost(app)}:$port."
        refreshRuntimeStatus()
    }

    fun resetKtorServerConfig() {
        val app = getApplication<Application>()
        KtorServerConfig.reset(app)
        AutoTaskService.restartKtorServer(app)
        _apiTestResponse.value = "Ktor server reset to ${KtorServerConfig.bindHost(app)}:${KtorServerConfig.DEFAULT_PORT}."
        refreshRuntimeStatus()
    }

    fun setLanEnabled(enabled: Boolean) {
        val app = getApplication<Application>()
        try {
            com.example.security.ExternalAccess.getInstance(app).setLanEnabled(enabled)
            AutoTaskService.restartKtorServer(app)
            _apiTestResponse.value = if (enabled) {
                "LAN bind enabled at ${KtorServerConfig.bindHost(app)}."
            } else {
                "LAN bind disabled; server is loopback-only."
            }
        } catch (e: com.example.security.PairingRequiredException) {
            _apiTestResponse.value = e.message ?: "Pair a credential before enabling LAN."
        }
        refreshRuntimeStatus()
    }

    fun restartKtorServer() {
        val app = getApplication<Application>()
        AutoTaskService.restartKtorServer(app)
        _apiTestResponse.value = "Ktor server restart requested for ${KtorServerConfig.getSnapshot(app).baseUrl}."
        refreshRuntimeStatus()
    }

    fun toggleProfileEnabled(profileId: String, currentEnabled: Boolean) {
        viewModelScope.launch {
            commands.setProfileEnabled(profileId, !currentEnabled)
        }
    }

    fun upsertProfile(profile: AutomationProfile) {
        viewModelScope.launch {
            commands.upsertProfile(profile)
        }
    }

    fun deleteProfile(profileId: String) {
        viewModelScope.launch {
            commands.deleteProfile(profileId)
        }
    }

    fun fireManualEvent(profileId: String? = null) {
        viewModelScope.launch {
            val payload = mutableMapOf<String, Any?>("manualTriggeredAt" to System.currentTimeMillis())
            if (!profileId.isNull_or_Empty()) {
                payload["profileId"] = profileId
            }
            commands.processEvent(AutomationEvent(type = "MANUAL", payload = payload))
        }
    }

    fun clearLogs() {
        viewModelScope.launch {
            commands.clearLogs()
        }
    }

    fun executeApiTest(method: String, endpoint: String, body: String = "") {
        viewModelScope.launch {
            _isApiTestLoading.value = true
            refreshRuntimeStatus()
            val baseUrl = _ktorServerConfig.value.baseUrl
            _apiTestResponse.value = "Sending $method $baseUrl$endpoint ..."

            withContext(Dispatchers.IO) {
                try {
                    val url = "$baseUrl$endpoint"
                    val requestBuilder = Request.Builder().url(url)

                    when (method.uppercase()) {
                        "GET" -> requestBuilder.get()
                        "POST", "PATCH" -> {
                            val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
                            val reqBody = body.toRequestBody(mediaType)
                            if (method.uppercase() == "POST") {
                                requestBuilder.post(reqBody)
                            } else {
                                requestBuilder.patch(reqBody)
                            }
                        }
                        "DELETE" -> requestBuilder.delete()
                    }

                    val response = httpClient.newCall(requestBuilder.build()).execute()
                    val respBody = response.body?.string() ?: ""
                    val formattedJson = try {
                        if (respBody.startsWith("{")) JSONObject(respBody).toString(2)
                        else if (respBody.startsWith("[")) org.json.JSONArray(respBody).toString(2)
                        else respBody
                    } catch (e: Exception) {
                        respBody
                    }

                    _apiTestResponse.value = "HTTP ${response.code} ${response.message}\n\n$formattedJson"
                } catch (e: Exception) {
                    _apiTestResponse.value = "HTTP Request Failed:\n${e.localizedMessage ?: "Connection refused. Is AutoTask engine running?"}"
                } finally {
                    _isApiTestLoading.value = false
                    refreshRuntimeStatus()
                }
            }
        }
    }

    private fun refreshRuntimeStatus() {
        _ktorServerConfig.value = KtorServerConfig.getSnapshot(getApplication())
        _isServiceRunning.value = AutoTaskService.isForegroundActive && commands.isRunning
    }
}

private fun String?.isNull_or_Empty(): Boolean = this == null || this.trim().isEmpty()
