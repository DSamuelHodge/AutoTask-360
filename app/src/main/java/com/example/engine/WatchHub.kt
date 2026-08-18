package com.example.engine

import org.json.JSONObject
import java.util.UUID

data class WatchFact(
    val id: String = UUID.randomUUID().toString(),
    val kind: String,
    val occurredAt: Long,
    val body: JSONObject
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("kind", kind)
        .put("occurredAt", occurredAt)
        .put("body", body)
}

class WatchHub(private val capacity: Int = 100) {
    private val lock = Any()
    private val buffer = ArrayDeque<WatchFact>(capacity)
    private val listeners = mutableListOf<(WatchFact) -> Unit>()

    fun publish(fact: WatchFact) {
        val snapshot: List<(WatchFact) -> Unit>
        synchronized(lock) {
            if (buffer.size >= capacity) buffer.removeFirst()
            buffer.addLast(fact)
            snapshot = listeners.toList()
        }
        snapshot.forEach { listener -> listener(fact) }
    }

    fun recent(limit: Int = 50): List<WatchFact> = synchronized(lock) {
        buffer.toList().takeLast(limit.coerceIn(1, capacity))
    }

    fun size(): Int = synchronized(lock) { buffer.size }

    fun subscribe(listener: (WatchFact) -> Unit): () -> Unit {
        synchronized(lock) { listeners.add(listener) }
        return { synchronized(lock) { listeners.remove(listener) } }
    }

    fun clear() {
        synchronized(lock) { buffer.clear() }
    }
}

object WatchBus {
    val hub = WatchHub()
}
