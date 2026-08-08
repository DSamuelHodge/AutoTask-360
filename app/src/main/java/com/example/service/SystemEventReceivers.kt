package com.example.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.NetworkInfo
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.provider.Telephony
import android.telephony.TelephonyManager
import com.example.engine.AutoTaskEngine
import com.example.engine.AutomationEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SystemEventReceivers(private val context: Context) {

    private var isRegistered = false
    private val engine = AutoTaskEngine.getInstance(context)
    private val scope = CoroutineScope(Dispatchers.IO)

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.action) {
                Telephony.Sms.Intents.SMS_RECEIVED_ACTION -> {
                    val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
                    for (msg in messages) {
                        val sender = msg.originatingAddress ?: "Unknown"
                        val body = msg.messageBody ?: ""
                        dispatch("SMS", mapOf("sender" to sender, "smsBody" to body, "number" to sender))
                    }
                }

                Intent.ACTION_BATTERY_CHANGED -> {
                    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                    val pct = if (level >= 0 && scale > 0) (level * 100 / scale.toFloat()).toInt() else level
                    val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                    val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL

                    dispatch("BATTERY", mapOf("level" to pct, "levelPercent" to pct, "isCharging" to isCharging))
                }

                WifiManager.NETWORK_STATE_CHANGED_ACTION -> {
                    val info = intent.getParcelableExtra<NetworkInfo>(WifiManager.EXTRA_NETWORK_INFO)
                    val isConn = info?.isConnected == true
                    val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                    val ssid = wifiManager?.connectionInfo?.ssid?.replace("\"", "") ?: "Unknown"

                    dispatch("WIFI", mapOf("ssid" to ssid, "connected" to isConn))
                }

                Intent.ACTION_SCREEN_ON -> {
                    dispatch("SCREEN", mapOf("state" to "ON"))
                }

                Intent.ACTION_SCREEN_OFF -> {
                    dispatch("SCREEN", mapOf("state" to "OFF"))
                }

                TelephonyManager.ACTION_PHONE_STATE_CHANGED -> {
                    val stateStr = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: ""
                    val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER) ?: ""
                    val state = when (stateStr) {
                        TelephonyManager.EXTRA_STATE_RINGING -> "RINGING"
                        TelephonyManager.EXTRA_STATE_OFFHOOK -> "OFFHOOK"
                        else -> "IDLE"
                    }
                    dispatch("CALL", mapOf("state" to state, "number" to incomingNumber))
                }
            }
        }
    }

    fun register() {
        if (isRegistered) return
        val filter = IntentFilter().apply {
            addAction(Telephony.Sms.Intents.SMS_RECEIVED_ACTION)
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(TelephonyManager.ACTION_PHONE_STATE_CHANGED)
        }
        try {
            context.registerReceiver(receiver, filter)
            isRegistered = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun unregister() {
        if (!isRegistered) return
        try {
            context.unregisterReceiver(receiver)
            isRegistered = false
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun dispatch(triggerType: String, payload: Map<String, Any?>) {
        scope.launch {
            engine.processEvent(AutomationEvent(type = triggerType, payload = payload))
        }
    }
}
