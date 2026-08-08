package com.example.service

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import android.net.NetworkInfo
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.PowerManager
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
                    val isLow = pct <= 15

                    dispatch("BATTERY", mapOf("level" to pct, "levelPercent" to pct, "isCharging" to isCharging, "isLow" to isLow))
                }

                Intent.ACTION_POWER_CONNECTED -> {
                    dispatch("POWER", mapOf("connected" to true))
                }

                Intent.ACTION_POWER_DISCONNECTED -> {
                    dispatch("POWER", mapOf("connected" to false))
                }

                PowerManager.ACTION_POWER_SAVE_MODE_CHANGED -> {
                    val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                    val enabled = pm?.isPowerSaveMode ?: false
                    dispatch("POWER_SAVE", mapOf("enabled" to enabled))
                }

                Intent.ACTION_AIRPLANE_MODE_CHANGED -> {
                    val enabled = intent.getBooleanExtra("state", false)
                    dispatch("AIRPLANE_MODE", mapOf("enabled" to enabled))
                }

                WifiManager.NETWORK_STATE_CHANGED_ACTION -> {
                    val info = intent.getParcelableExtra<NetworkInfo>(WifiManager.EXTRA_NETWORK_INFO)
                    val isConn = info?.isConnected == true
                    val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                    val ssid = wifiManager?.connectionInfo?.ssid?.replace("\"", "") ?: "Unknown"

                    dispatch("WIFI", mapOf("ssid" to ssid, "connected" to isConn))
                }

                BluetoothDevice.ACTION_ACL_CONNECTED -> {
                    val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                    dispatch("BLUETOOTH", mapOf("deviceName" to (device?.name ?: "Unknown"), "deviceAddress" to (device?.address ?: ""), "connected" to true))
                }

                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                    dispatch("BLUETOOTH", mapOf("deviceName" to (device?.name ?: "Unknown"), "deviceAddress" to (device?.address ?: ""), "connected" to false))
                }

                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                    val stateStr = if (state == BluetoothAdapter.STATE_ON) "ON" else "OFF"
                    dispatch("BLUETOOTH_STATE", mapOf("state" to stateStr))
                }

                Intent.ACTION_SCREEN_ON -> {
                    dispatch("SCREEN", mapOf("state" to "ON", "screenState" to "ON"))
                }

                Intent.ACTION_SCREEN_OFF -> {
                    dispatch("SCREEN", mapOf("state" to "OFF", "screenState" to "OFF"))
                }

                Intent.ACTION_USER_PRESENT -> {
                    dispatch("DEVICE_UNLOCKED", mapOf("unlocked" to true))
                }

                PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED -> {
                    val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                    val entering = pm?.isDeviceIdleMode ?: false
                    dispatch("DOZE", mapOf("entering" to entering))
                }

                Intent.ACTION_HEADSET_PLUG -> {
                    val state = intent.getIntExtra("state", -1)
                    val microphone = intent.getIntExtra("microphone", -1)
                    dispatch("HEADSET", mapOf("connected" to (state == 1), "hasMicrophone" to (microphone == 1)))
                }

                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    dispatch("USB", mapOf("connected" to true))
                }

                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    dispatch("USB", mapOf("connected" to false))
                }

                Intent.ACTION_TIMEZONE_CHANGED -> {
                    val tz = intent.getStringExtra("time-zone") ?: ""
                    dispatch("TIMEZONE", mapOf("timezone" to tz))
                }

                Intent.ACTION_LOCALE_CHANGED -> {
                    val loc = context.resources.configuration.locales.get(0)?.displayName ?: ""
                    dispatch("LOCALE", mapOf("locale" to loc))
                }

                TelephonyManager.ACTION_PHONE_STATE_CHANGED -> {
                    val stateStr = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: ""
                    val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER) ?: ""
                    val state = when (stateStr) {
                        TelephonyManager.EXTRA_STATE_RINGING -> "RINGING"
                        TelephonyManager.EXTRA_STATE_OFFHOOK -> "OFFHOOK"
                        else -> "IDLE"
                    }
                    dispatch("INCOMING_CALL", mapOf("state" to state, "number" to incomingNumber, "callState" to state))
                    dispatch("CALL", mapOf("state" to state, "number" to incomingNumber, "callState" to state))
                }
            }
        }
    }

    fun register() {
        if (isRegistered) return
        val filter = IntentFilter().apply {
            addAction(Telephony.Sms.Intents.SMS_RECEIVED_ACTION)
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
            addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED)
            addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION)
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED)
            addAction(Intent.ACTION_HEADSET_PLUG)
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(Intent.ACTION_LOCALE_CHANGED)
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

