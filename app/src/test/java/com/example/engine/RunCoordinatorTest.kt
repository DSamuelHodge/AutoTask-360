package com.example.engine

import com.example.data.AutomationProfile
import com.example.domain.DefinitionCompiler
import com.example.domain.EventEnvelope
import com.example.domain.RunStatuses
import com.example.domain.StepRun
import com.example.domain.StepStatuses
import org.junit.Assert.assertNotNull
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RunCoordinatorTest {

    @Before
    fun resetCache() {
        DefinitionCompiler.resetForTests()
    }

    @Test
    fun stepsExecuteInOrderAndCheckpoint() = runBlocking {
        val env = harness(
            actions = """[{"type":"LOG","params":{"message":"one"}},{"type":"TOAST","params":{"text":"two"}}]"""
        )
        val result = env.dispatch()

        assertEquals(1, result.runs.size)
        assertEquals(RunStatuses.SUCCESS, result.runs[0].run.status)
        assertEquals(listOf("0:LOG", "1:TOAST"), env.calls)
        assertEquals(listOf(StepStatuses.OK, StepStatuses.OK), result.runs[0].steps.map { it.status })
        assertEquals(1, env.watched.size)
    }

    @Test
    fun partialFailureRecordsCompletedAndFailedSteps() = runBlocking {
        val env = harness(
            actions = """[{"type":"LOG","params":{"message":"one"}},{"type":"HTTP","params":{"url":"https://example.invalid"}},{"type":"TOAST","params":{"text":"skip"}}]"""
        )
        val result = env.dispatch()

        assertEquals(RunStatuses.PARTIAL, result.runs[0].run.status)
        assertEquals(listOf(StepStatuses.OK, StepStatuses.FAILED), result.runs[0].steps.map { it.status })
        assertEquals(listOf("0:LOG", "1:HTTP"), env.calls)
    }

    @Test
    fun waitPersistsContinuationInsteadOfBlocking() = runBlocking {
        var now = 10_000L
        val scheduled = mutableListOf<Pair<String, Long>>()
        val env = harness(
            actions = """[{"type":"WAIT","params":{"durationMs":5000}},{"type":"LOG","params":{"message":"after"}}]""",
            clock = { now },
            scheduler = object : WakeScheduler {
                override fun schedule(runId: String, wakeAtEpochMs: Long) {
                    scheduled.add(runId to wakeAtEpochMs)
                }
                override fun cancel(runId: String) = Unit
            }
        )

        val first = env.dispatch()
        assertEquals(RunStatuses.WAITING, first.runs[0].run.status)
        assertEquals(15_000L, first.runs[0].run.wakeAt)
        assertEquals(StepStatuses.WAITING, first.runs[0].steps.single().status)
        assertTrue(env.calls.isEmpty())
        assertEquals(1, scheduled.size)

        now = 15_000L
        val resumed = env.coordinator.execute(first.runs[0].run.runId)
        assertEquals(RunStatuses.SUCCESS, resumed.run.status)
        assertEquals(listOf("1:LOG"), env.calls)
        assertEquals(StepStatuses.OK, resumed.steps[0].status)
        assertEquals(StepStatuses.OK, resumed.steps[1].status)
    }

    @Test
    fun firstAdmissionStampsAnEffectId() = runBlocking {
        val env = harness(actions = """[{"type":"LOG","params":{"message":"one"}}]""")
        val result = env.dispatch()
        val step = result.runs[0].steps.single()
        assertEquals(StepStatuses.OK, step.status)
        assertNotNull(step.effectId)
        assertEquals(step.effectId, env.effectIds.single())
    }

    @Test
    fun interruptedRunningStepResumesWithoutManualRepair() = runBlocking {
        val env = harness(
            actions = """[{"type":"LOG","params":{"message":"one"}},{"type":"TOAST","params":{"text":"two"}}]"""
        )
        val queued = env.dispatch(executeNow = false)
        env.store.updateRun(queued.runs[0].run.copy(status = RunStatuses.RUNNING, currentStepIndex = 0))

        val recovered = env.coordinator.recoverIncomplete()
        assertEquals(1, recovered.size)
        assertEquals(RunStatuses.SUCCESS, recovered[0].run.status)
        assertEquals(listOf("0:LOG", "1:TOAST"), env.calls)
    }

    @Test
    fun interruptedSafeStepReentersWithTheSameEffectId() = runBlocking {
        val env = harness(
            actions = """[{"type":"LOG","params":{"message":"one"}},{"type":"TOAST","params":{"text":"two"}}]"""
        )
        val queued = env.dispatch(executeNow = false)
        val run = queued.runs[0].run.copy(status = RunStatuses.RUNNING, currentStepIndex = 0)
        env.store.updateRun(run)
        env.store.upsertStep(
            StepRun(
                stepRunId = "step-0",
                runId = run.runId,
                stepIndex = 0,
                type = "LOG",
                status = StepStatuses.RUNNING,
                effectId = "effect-log-1",
                startedAt = run.createdAt
            )
        )

        val recovered = env.coordinator.recoverIncomplete().single()
        assertEquals(RunStatuses.SUCCESS, recovered.run.status)
        assertEquals("effect-log-1", recovered.steps[0].effectId)
        assertEquals("effect-log-1", env.effectIds[0])
        assertEquals(listOf("0:LOG", "1:TOAST"), env.calls)
    }

    @Test
    fun interruptedNonIdempotentStepDoesNotReexecute() = runBlocking {
        val env = harness(
            actions = """[{"type":"SEND_SMS","params":{"number":"+15551212","text":"hi"}},{"type":"LOG","params":{"message":"after"}}]"""
        )
        val queued = env.dispatch(executeNow = false)
        val run = queued.runs[0].run.copy(status = RunStatuses.RUNNING, currentStepIndex = 0)
        env.store.updateRun(run)
        env.store.upsertStep(
            StepRun(
                stepRunId = "step-sms",
                runId = run.runId,
                stepIndex = 0,
                type = "SEND_SMS",
                status = StepStatuses.RUNNING,
                effectId = "effect-sms-1",
                startedAt = run.createdAt
            )
        )

        val recovered = env.coordinator.recoverIncomplete().single()
        assertEquals(RunStatuses.INDETERMINATE, recovered.run.status)
        assertEquals("resume_indeterminate", recovered.run.error)
        assertEquals(StepStatuses.INDETERMINATE, recovered.steps.single().status)
        assertEquals("effect-sms-1", recovered.steps.single().effectId)
        assertTrue(env.calls.isEmpty())
    }

    @Test
    fun retryAfterIndeterminateStartsAFreshRun() = runBlocking {
        val env = harness(actions = """[{"type":"SEND_SMS","params":{"number":"+15551212","text":"hi"}}]""")
        val queued = env.dispatch(executeNow = false)
        val run = queued.runs[0].run.copy(status = RunStatuses.RUNNING, currentStepIndex = 0)
        env.store.updateRun(run)
        env.store.upsertStep(
            StepRun(
                stepRunId = "step-sms",
                runId = run.runId,
                stepIndex = 0,
                type = "SEND_SMS",
                status = StepStatuses.RUNNING,
                effectId = "effect-sms-1",
                startedAt = run.createdAt
            )
        )
        val crashed = env.coordinator.recoverIncomplete().single()
        assertEquals(RunStatuses.INDETERMINATE, crashed.run.status)

        val retry = env.coordinator.retry(crashed.run.runId)
        assertNotEquals(crashed.run.runId, retry.runId)
        val executed = env.coordinator.execute(retry)
        assertEquals(RunStatuses.SUCCESS, executed.run.status)
        assertEquals(listOf("0:SEND_SMS"), env.calls)
        assertNotEquals("effect-sms-1", executed.steps.single().effectId)
    }

    @Test
    fun cancelStopsAWaitingRun() = runBlocking {
        val env = harness(
            actions = """[{"type":"WAIT","params":{"durationMs":8000}},{"type":"LOG","params":{"message":"nope"}}]"""
        )
        val first = env.dispatch()
        val cancelled = env.coordinator.cancel(first.runs[0].run.runId)

        assertEquals(RunStatuses.CANCELLED, cancelled.run.status)
        assertEquals(StepStatuses.CANCELLED, cancelled.steps.single().status)
    }

    @Test
    fun retryCreatesANewAttemptFromAFailedRun() = runBlocking {
        val env = harness(actions = """[{"type":"HTTP","params":{"url":"https://example.invalid"}}]""")
        val first = env.dispatch()
        assertEquals(RunStatuses.FAILED, first.runs[0].run.status)

        val retry = env.coordinator.retry(first.runs[0].run.runId)
        assertNotEquals(first.runs[0].run.runId, retry.runId)
        assertEquals(2, retry.attempt)
        assertEquals(first.runs[0].run.runId, retry.retryOfRunId)

        val executed = env.coordinator.execute(retry)
        assertEquals(RunStatuses.FAILED, executed.run.status)
        assertEquals(2, env.calls.size)
    }

    @Test
    fun duplicateEventIdAndDedupeKeyDoNotRerun() = runBlocking {
        val env = harness(actions = """[{"type":"LOG","params":{"message":"once"}}]""")
        val event = EventEnvelope.create(
            type = "MANUAL",
            payload = mapOf("profileId" to env.profileId),
            eventId = "evt-1",
            dedupeKey = "same-key"
        )
        val first = env.dispatcher.dispatch(event, targetProfileId = env.profileId)
        val byId = env.dispatcher.dispatch(event.copy(correlationId = "other"), targetProfileId = env.profileId)
        val byDedupe = env.dispatcher.dispatch(
            EventEnvelope.create(
                type = "MANUAL",
                payload = mapOf("profileId" to env.profileId),
                dedupeKey = "same-key"
            ),
            targetProfileId = env.profileId
        )

        assertEquals(false, first.deduplicated)
        assertTrue(byId.deduplicated)
        assertTrue(byDedupe.deduplicated)
        assertEquals(first.runs[0].run.runId, byId.runs[0].run.runId)
        assertEquals(1, env.calls.size)
    }

    @Test
    fun idempotencyKeyReusesTheFirstEvent() = runBlocking {
        val env = harness(actions = """[{"type":"LOG","params":{"message":"once"}}]""")
        val first = env.dispatcher.dispatch(
            EventEnvelope.create(
                type = "MANUAL",
                payload = mapOf("profileId" to env.profileId),
                idempotencyKey = "job-9"
            ),
            targetProfileId = env.profileId
        )
        val second = env.dispatcher.dispatch(
            EventEnvelope.create(
                type = "MANUAL",
                payload = mapOf("profileId" to env.profileId),
                idempotencyKey = "job-9"
            ),
            targetProfileId = env.profileId
        )
        assertTrue(second.deduplicated)
        assertEquals(first.event.eventId, second.event.eventId)
        assertEquals(1, env.calls.size)
    }

    @Test
    fun concurrentDistinctEventsAllComplete() = runBlocking {
        val env = harness(actions = """[{"type":"LOG","params":{"message":"go"}}]""")
        val results = (0 until 20).map { index ->
            async {
                env.dispatcher.dispatch(
                    EventEnvelope.create(
                        type = "MANUAL",
                        payload = mapOf("profileId" to env.profileId, "n" to index),
                        eventId = "evt-$index"
                    ),
                    targetProfileId = env.profileId
                )
            }
        }.awaitAll()

        assertEquals(20, results.size)
        assertEquals(20, results.map { it.event.eventId }.toSet().size)
        assertEquals(20, results.map { it.runs.single().run.runId }.toSet().size)
        assertTrue(results.all { it.runs.single().run.status == RunStatuses.SUCCESS })
        assertEquals(20, env.calls.size)
    }

    private fun harness(
        actions: String,
        clock: () -> Long = { System.currentTimeMillis() },
        scheduler: WakeScheduler = NoOpWakeScheduler
    ): TestEnv {
        val store = InMemoryRunStore()
        val calls = mutableListOf<String>()
        val effectIds = mutableListOf<String>()
        val profileId = "profile-${System.nanoTime()}"
        val profile = AutomationProfile(
            id = profileId,
            name = "Test",
            isEnabled = true,
            triggerType = "MANUAL",
            triggerConfigJson = "{}",
            actionsJson = actions
        )
        val coordinator = RunCoordinator(
            store = store,
            runner = StepRunner { _, _, stepIndex, type, _, effectId ->
                calls.add("$stepIndex:$type")
                effectIds.add(effectId)
                if (type == "HTTP") StepResult(stepIndex, type, "FAILED", "boom")
                else StepResult(stepIndex, type, "OK", "ok")
            },
            wakeScheduler = scheduler,
            loadProfile = { if (it == profile.id) profile else null },
            clock = clock
        )
        val watched = mutableListOf<String>()
        val dispatcher = EventDispatcher(
            store = store,
            coordinator = coordinator,
            loadEnabled = { if (it == "MANUAL") listOf(profile) else emptyList() },
            loadProfile = { if (it == profile.id) profile else null },
            clock = clock,
            onDispatch = { watched += it.event.eventId }
        )
        return TestEnv(profileId, store, coordinator, dispatcher, calls, effectIds, watched)
    }

    private data class TestEnv(
        val profileId: String,
        val store: InMemoryRunStore,
        val coordinator: RunCoordinator,
        val dispatcher: EventDispatcher,
        val calls: MutableList<String>,
        val effectIds: MutableList<String>,
        val watched: MutableList<String>
    ) {
        suspend fun dispatch(executeNow: Boolean = true) = dispatcher.dispatch(
            EventEnvelope.create(type = "MANUAL", payload = mapOf("profileId" to profileId)),
            targetProfileId = profileId,
            executeNow = executeNow
        )
    }
}
