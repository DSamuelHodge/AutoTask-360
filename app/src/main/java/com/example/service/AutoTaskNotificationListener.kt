package com.example.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.example.application.AutomationCommandFacade
import com.example.engine.AutomationEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AutoTaskNotificationListener : NotificationListenerService() {

    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        val pkg = sbn.packageName ?: return

        // Ignore AutoTask's own service notifications to prevent feedback loops
        if (pkg == packageName) return

        val extras = sbn.notification.extras
        val title = extras.getCharSequence("android.title")?.toString() ?: ""
        val text = extras.getCharSequence("android.text")?.toString() ?: ""

        if (title.isBlank() && text.isBlank()) return

        scope.launch {
            val commands = AutomationCommandFacade.getInstance(applicationContext)
            commands.processEvent(
                AutomationEvent(
                    type = "NOTIFICATION",
                    payload = mapOf(
                        "packageName" to pkg,
                        "title" to title,
                        "text" to text
                    )
                )
            )
        }
    }
}
