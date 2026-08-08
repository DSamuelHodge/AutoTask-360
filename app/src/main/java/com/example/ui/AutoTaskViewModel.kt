package com.example.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AutomationProfile
import com.example.data.AutoTaskRepository
import com.example.data.ExecutionLog
import com.example.engine.AutoTaskEngine
import com.example.engine.AutomationEvent
import com.example.service.AutoTaskService
import kotlinx.coroutines.Dispatchers
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

    private val repository = AutoTaskRepository(application)
    private val engine = AutoTaskEngine.getInstance(application)

    val profiles: StateFlow<List<AutomationProfile>> = repository.allProfilesFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val logs: StateFlow<List<ExecutionLog>> = repository.logsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _isServiceRunning = MutableStateFlow(true)
    val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

    private val _apiTestResponse = MutableStateFlow<String>("")
    val apiTestResponse: StateFlow<String> = _apiTestResponse.asStateFlow()

    private val _isApiTestLoading = MutableStateFlow(false)
    val isApiTestLoading: StateFlow<Boolean> = _isApiTestLoading.asStateFlow()

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()
    }

    init {
        // Ensure default recipes exist on startup
        viewModelScope.launch {
            repository.seedDefaultRecipesIfNeeded()
            AutoTaskService.startService(application)
            _isServiceRunning.value = true
        }
    }

    fun toggleService(enable: Boolean) {
        val app = getApplication<Application>()
        if (enable) {
            AutoTaskService.startService(app)
            engine.setRunningState(true)
            _isServiceRunning.value = true
        } else {
            AutoTaskService.stopService(app)
            engine.setRunningState(false)
            _isServiceRunning.value = false
        }
    }

    fun toggleProfileEnabled(profileId: String, currentEnabled: Boolean) {
        viewModelScope.launch {
            repository.setProfileEnabled(profileId, !currentEnabled)
        }
    }

    fun upsertProfile(profile: AutomationProfile) {
        viewModelScope.launch {
            repository.upsertProfile(profile)
        }
    }

    fun deleteProfile(profileId: String) {
        viewModelScope.launch {
            repository.deleteProfileById(profileId)
        }
    }

    fun fireManualEvent(profileId: String? = null) {
        viewModelScope.launch {
            val payload = mutableMapOf<String, Any?>("manualTriggeredAt" to System.currentTimeMillis())
            if (!profileId.isNull_or_Empty()) {
                payload["profileId"] = profileId
            }
            engine.processEvent(AutomationEvent(type = "MANUAL", payload = payload))
        }
    }

    fun clearLogs() {
        viewModelScope.launch {
            repository.clearLogs()
        }
    }

    fun executeApiTest(method: String, endpoint: String, body: String = "") {
        viewModelScope.launch {
            _isApiTestLoading.value = true
            _apiTestResponse.value = "Sending $method http://127.0.0.1:8788$endpoint ..."

            withContext(Dispatchers.IO) {
                try {
                    val url = "http://127.0.0.1:8788$endpoint"
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
                }
            }
        }
    }
}

private fun String?.isNull_or_Empty(): Boolean = this == null || this.trim().isEmpty()
