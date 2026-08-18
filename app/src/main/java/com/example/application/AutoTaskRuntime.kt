package com.example.application

import android.content.Context
import com.example.engine.AutoTaskEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Application-scoped startup. Construction of [AutoTaskEngine] is lazy and
 * side-effect free; [start] is the only place that seeds, recovers, reconciles,
 * and prunes.
 */
object AutoTaskRuntime {
    private val started = AtomicBoolean(false)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun start(context: Context) {
        val app = context.applicationContext
        val engine = AutoTaskEngine.getInstance(app)
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            engine.start()
        }
    }

    fun isStarted(): Boolean = started.get()

    internal fun resetForTests() {
        started.set(false)
    }
}
