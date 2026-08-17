package com.example.engine.actions

import com.example.engine.actions.handlers.AudioActionHandler
import com.example.engine.actions.handlers.BroadcastActionHandler
import com.example.engine.actions.handlers.BrightnessActionHandler
import com.example.engine.actions.handlers.CallActionHandler
import com.example.engine.actions.handlers.CameraActionHandler
import com.example.engine.actions.handlers.ClipboardActionHandler
import com.example.engine.actions.handlers.DndActionHandler
import com.example.engine.actions.handlers.FlashlightActionHandler
import com.example.engine.actions.handlers.HttpActionHandler
import com.example.engine.actions.handlers.LaunchAppActionHandler
import com.example.engine.actions.handlers.LogActionHandler
import com.example.engine.actions.handlers.NotificationActionHandler
import com.example.engine.actions.handlers.OpenSettingsActionHandler
import com.example.engine.actions.handlers.OpenUrlActionHandler
import com.example.engine.actions.handlers.PolicyStubActionHandler
import com.example.engine.actions.handlers.ProfileActionHandler
import com.example.engine.actions.handlers.ReadFileActionHandler
import com.example.engine.actions.handlers.RotationActionHandler
import com.example.engine.actions.handlers.ScreenTimeoutActionHandler
import com.example.engine.actions.handlers.SendIntentActionHandler
import com.example.engine.actions.handlers.SendSmsActionHandler
import com.example.engine.actions.handlers.SpeakActionHandler
import com.example.engine.actions.handlers.ToastActionHandler
import com.example.engine.actions.handlers.VibrateActionHandler
import com.example.engine.actions.handlers.WaitActionHandler
import com.example.engine.actions.handlers.WriteFileActionHandler

/**
 * Type → handler map. Adding an action means implementing [ActionHandler]
 * and appending it to [standardHandlers].
 */
class ActionRegistry(handlers: List<ActionHandler>) {
    private val byType: Map<String, ActionHandler> =
        handlers.associateBy { it.type.uppercase() }

    fun handler(type: String): ActionHandler? = byType[type.uppercase()]

    fun types(): Set<String> = byType.keys

    fun metadata(type: String): ActionMetadata? = handler(type)?.metadata()

    companion object {
        fun standard(): ActionRegistry = ActionRegistry(standardHandlers())

        fun standardHandlers(): List<ActionHandler> = listOf(
            AudioActionHandler(),
            DndActionHandler(),
            BrightnessActionHandler(),
            ScreenTimeoutActionHandler(),
            RotationActionHandler(),
            PolicyStubActionHandler("POWER_SAVE"),
            PolicyStubActionHandler("WIFI_ACTION"),
            PolicyStubActionHandler("BLUETOOTH_ACTION"),
            PolicyStubActionHandler("AIRPLANE_MODE_ACTION"),
            PolicyStubActionHandler("HOTSPOT"),
            PolicyStubActionHandler("NFC_ACTION"),
            NotificationActionHandler(),
            SpeakActionHandler(),
            ToastActionHandler(),
            VibrateActionHandler(),
            SendSmsActionHandler(),
            CallActionHandler(),
            OpenUrlActionHandler(),
            SendIntentActionHandler(),
            LaunchAppActionHandler(),
            PolicyStubActionHandler("KILL_APP"),
            OpenSettingsActionHandler(),
            FlashlightActionHandler(),
            ClipboardActionHandler(),
            CameraActionHandler(),
            HttpActionHandler(),
            WriteFileActionHandler(),
            ReadFileActionHandler(),
            BroadcastActionHandler(),
            ProfileActionHandler(),
            WaitActionHandler(),
            LogActionHandler()
        )
    }
}
