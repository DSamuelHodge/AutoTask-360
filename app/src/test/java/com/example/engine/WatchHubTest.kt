package com.example.engine

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WatchHubTest {

    @Test
    fun buffersNewestAndDropsOldest() {
        val hub = WatchHub(capacity = 2)
        hub.publish(WatchFact(kind = "event", occurredAt = 1, body = JSONObject().put("n", 1)))
        hub.publish(WatchFact(kind = "event", occurredAt = 2, body = JSONObject().put("n", 2)))
        hub.publish(WatchFact(kind = "run", occurredAt = 3, body = JSONObject().put("n", 3)))
        val recent = hub.recent(10)
        assertEquals(2, recent.size)
        assertEquals(2, recent[0].body.getInt("n"))
        assertEquals(3, recent[1].body.getInt("n"))
        assertEquals("run", recent[1].kind)
    }

    @Test
    fun subscribersSeeLiveFacts() {
        val hub = WatchHub()
        val seen = mutableListOf<String>()
        val unsub = hub.subscribe { seen += it.kind }
        hub.publish(WatchFact(kind = "event", occurredAt = 1, body = JSONObject()))
        unsub()
        hub.publish(WatchFact(kind = "run", occurredAt = 2, body = JSONObject()))
        assertEquals(listOf("event"), seen)
    }
}
