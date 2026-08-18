package com.example.engine

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.AutomationProfile
import com.example.data.AutoTaskRepository
import com.example.domain.StepStatuses
import com.example.engine.actions.ActionHandler
import com.example.engine.actions.ActionRegistry
import com.example.engine.actions.ActionRequest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ActionExecutorEffectTest {

    @Test
    fun committedEffectIdSkipsTheSecondSend() = runBlocking {
        val handler = CountingSmsHandler()
        val store = InMemoryRunStore()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val executor = ActionExecutor(
            context = context,
            repository = AutoTaskRepository(context),
            registry = ActionRegistry(listOf(handler)),
            ledger = store
        )
        val profile = AutomationProfile(
            id = "p",
            name = "P",
            triggerType = "MANUAL",
            triggerConfigJson = "{}",
            actionsJson = "[]"
        )
        val event = AutomationEvent(type = "MANUAL")
        val first = executor.executeStep(0, "SEND_SMS", org.json.JSONObject(), profile, event, "effect-1")
        val second = executor.executeStep(0, "SEND_SMS", org.json.JSONObject(), profile, event, "effect-1")
        assertEquals(StepStatuses.OK, first.status)
        assertEquals(StepStatuses.OK, second.status)
        assertEquals(1, handler.sends)
        assertEquals("sent", second.detail)
    }

    private class CountingSmsHandler : ActionHandler {
        override val type: String = "SEND_SMS"
        var sends: Int = 0
        override suspend fun execute(request: ActionRequest): StepResult {
            sends += 1
            return StepResult(request.stepIndex, type, StepStatuses.OK, "sent")
        }
    }
}
