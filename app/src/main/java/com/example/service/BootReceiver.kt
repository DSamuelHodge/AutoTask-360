package com.example.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.engine.AutoTaskEngine
import com.example.engine.AutomationEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            // Start AutoTask foreground service
            AutoTaskService.startService(context)
            // Start the CoS brain (supervised) + passive health monitor
            com.example.wa.BrainService.startService(context)
            com.example.wa.HealthMonitor.startService(context)

            // Dispatch BOOT automation event
            CoroutineScope(Dispatchers.IO).launch {
                val engine = AutoTaskEngine.getInstance(context)
                engine.processEvent(
                    AutomationEvent(
                        type = "BOOT",
                        payload = mapOf(
                            "action" to intent.action,
                            "bootTimestamp" to System.currentTimeMillis()
                        )
                    )
                )
            }
        }
    }
}
