package com.example.wa

import android.os.Bundle
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.service.AutoTaskService
import com.example.ui.theme.*

/**
 * Pairing screen for the WhatsApp Web bridge. Shows the managed WebView so the
 * user can scan the QR code once. Once paired, the bridge keeps running via
 * [WhatsAppBridgeService] and this screen can be closed.
 */
class WhatsAppBridgeActivity : ComponentActivity() {

    private val container: FrameLayout by lazy {
        FrameLayout(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WhatsAppBridgeManager.initialize(applicationContext)
        setContent {
            MaterialTheme {
                var paired by remember { mutableStateOf(false) }
                val bridge = WhatsAppBridgeManager
                // Poll pairing state from the manager.
                LaunchedEffect(Unit) {
                    while (true) {
                        paired = bridge.isPaired
                        kotlinx.coroutines.delay(1500)
                    }
                }
                Surface(modifier = Modifier.fillMaxSize()) {
                    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                        Text(
                            "CoS WhatsApp Bridge",
                            style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Text(
                            if (paired) {
                                "Paired ✓ — this screen can be closed; the bridge keeps running in the background."
                            } else {
                                "Scan this QR code with your WhatsApp (Settings → Linked devices) to pair web.whatsapp.com. " +
                                    "The CoS will then read and send WhatsApp messages."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        val err = bridge.lastError
                        if (err != null) {
                            Text(
                                "Bridge note: $err",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                        }
                        androidx.compose.ui.viewinterop.AndroidView(
                            factory = { container },
                            modifier = Modifier.fillMaxWidth().weight(1f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = { WhatsAppBridgeService.stopService(applicationContext) },
                                modifier = Modifier.weight(1f)
                            ) { Text("Stop Bridge") }
                            Button(
                                onClick = { finish() },
                                modifier = Modifier.weight(1f)
                            ) { Text("Close") }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        WhatsAppBridgeManager.attachTo(container)
    }

    override fun onPause() {
        super.onPause()
        // Keep the WebView alive inside the manager; the service holds it.
        WhatsAppBridgeManager.detach()
    }

    override fun onDestroy() {
        super.onDestroy()
        WhatsAppBridgeManager.detach()
    }
}
