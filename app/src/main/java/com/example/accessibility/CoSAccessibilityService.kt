package com.example.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Path
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import org.json.JSONArray
import org.json.JSONObject

/**
 * The CoS "eyes and hands" layer.
 *
 * This is the only mechanism that can read and drive *any* app's UI:
 *   - **Eyes**: [getScreen] walks the current window's accessibility tree
 *     (getRootInActiveWindow) into a text outline — "what's on screen right
 *     now" — without OCR.
 *   - **Hands**: [tap], [type], [global] synthesize gestures / global actions
 *     so CoS can drive cross-app flows (e.g. confirm a native-app send)
 *     without per-app wiring.
 *
 * The service holds no per-app knowledge: it exposes primitives only, and the
 * capability policy layer decides what any inbound command may touch.
 *
 * User-granted via Settings (Settings.ACTION_ACCESSIBILITY_SETTINGS) — there is
 * no programmatic grant, by design (same trust posture as DND/overlay).
 */
class CoSAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "CoSAccessibility"
        private const val MAX_SCREEN_NODES = 400
        private const val MAX_SCREEN_DEPTH = 20

        @Volatile
        private var instance: CoSAccessibilityService? = null

        /** True while the service is bound (user has granted access). */
        @Volatile
        var isBound: Boolean = false
            private set

        @Volatile
        var lastEventPkg: String? = null
            private set

        @Volatile
        var lastEventText: String? = null
            private set

        fun isEnabled(context: Context): Boolean {
            val expected = android.content.ComponentName(
                context, CoSAccessibilityService::class.java
            ).flattenToString()
            val enabled = Settings.Secure.getString(
                context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
        }

        /** Post a command to the service's main-thread handler. */
        private fun post(block: () -> Unit): Boolean {
            val s = instance ?: return false
            val h = s.handler ?: return false
            h.post(block)
            return true
        }

        /** Request a screen dump; returns the most recent capture (async). */
        fun screenDump(): JSONObject {
            val s = instance ?: return err("accessibility service not bound")
            s.requestDump()
            return s.lastDump ?: err("no screen captured yet")
        }

        fun tap(x: Float, y: Float): JSONObject {
            val s = instance ?: return err("accessibility service not bound")
            if (!post { s.tap(x, y) }) return err("service busy")
            return ok()
        }

        fun type(text: String): JSONObject {
            val s = instance ?: return err("accessibility service not bound")
            if (!post { s.type(text) }) return err("service busy")
            return ok()
        }

        fun global(action: Int): JSONObject {
            val s = instance ?: return err("accessibility service not bound")
            val ok = post {
                when (action) {
                    GLOBAL_ACTION_BACK -> s.performGlobalAction(GLOBAL_ACTION_BACK)
                    GLOBAL_ACTION_HOME -> s.performGlobalAction(GLOBAL_ACTION_HOME)
                    GLOBAL_ACTION_RECENTS -> s.performGlobalAction(GLOBAL_ACTION_RECENTS)
                    GLOBAL_ACTION_NOTIFICATIONS -> s.performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
                    GLOBAL_ACTION_QUICK_SETTINGS -> s.performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
                    else -> false
                }
            }
            return if (ok) ok() else err("global action rejected")
        }

        private fun ok(): JSONObject =
            JSONObject().put("ok", true)

        private fun err(message: String): JSONObject =
            JSONObject().put("ok", false).put("error", message)
    }

    private var handler: Handler? = null
    @Volatile
    private var lastDump: JSONObject? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        handler = Handler(Looper.getMainLooper())
        instance = this
        isBound = true
        Log.i(TAG, "accessibility bound")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        try {
            val pkg = event.packageName?.toString()
            val text = event.text.joinToString(" ") { it.toString() }.take(120)
            if (!text.isBlank() && pkg != null && pkg != "com.aistudio.autotask.svcqx") {
                lastEventPkg = pkg
                lastEventText = text
            }
        } catch (_: Exception) {}
    }

    override fun onInterrupt() {}

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        isBound = false
        instance = null
        return super.onUnbind(intent)
    }

    // ── Eyes ────────────────────────────────────────────────────────────

    private fun requestDump() {
        handler?.post {
            val root = rootInActiveWindow ?: run {
                lastDump = JSONObject().put("ok", false).put("error", "no active window")
                return@post
            }
            val obj = JSONObject()
            obj.put("ok", true)
            obj.put("package", root.packageName)
            obj.put("className", root.className)
            obj.put("text", walkTree(root, JSONArray(), 0))
            lastDump = obj
        }
    }

    private fun walkTree(node: AccessibilityNodeInfo, out: JSONArray, depth: Int): JSONArray {
        if (depth > MAX_SCREEN_DEPTH || out.length() > MAX_SCREEN_NODES) return out
        val text = node.text?.toString()?.trim().orEmpty()
        val desc = node.contentDescription?.toString()?.trim().orEmpty()
        val viewId = try { node.viewIdResourceName } catch (_: Exception) { null }
        if (text.isNotEmpty() || desc.isNotEmpty()) {
            val item = JSONObject()
            item.put("text", text)
            if (desc.isNotEmpty()) item.put("desc", desc)
            if (viewId != null) item.put("id", viewId)
            item.put("class", node.className?.toString() ?: "")
            item.put("clickable", node.isClickable)
            val r = Rect()
            node.getBoundsInScreen(r)
            item.put("bounds", JSONArray(listOf(r.left, r.top, r.right, r.bottom)))
            item.put("depth", depth)
            out.put(item)
        }
        for (i in 0 until node.childCount) {
            try {
                val child = node.getChild(i) ?: continue
                walkTree(child, out, depth + 1)
                child.recycle()
            } catch (_: Exception) {}
        }
        return out
    }

    // ── Hands ───────────────────────────────────────────────────────────

    private fun tap(x: Float, y: Float): Boolean {
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 80))
            .build()
        return dispatchGesture(gesture, null, null)
    }

    private fun type(text: String): Boolean {
        // Find the focused editable node and paste/insert text.
        val root = rootInActiveWindow ?: return false
        val editable = findEditable(root)
        if (editable == null) {
            Log.w(TAG, "no editable node found")
            return false
        }
        editable.refresh()
        // Use ACTION_SET_TEXT with the full string (avoids one keystroke per
        // char; works for most contenteditable/EditText implementations).
        val args = android.os.Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return editable.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    private fun findEditable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isEditable || node.className?.toString()?.contains("EditText") == true) return node
        for (i in 0 until node.childCount) {
            try {
                val child = node.getChild(i) ?: continue
                val found = findEditable(child)
                if (found != null) { child.recycle(); return found }
                child.recycle()
            } catch (_: Exception) {}
        }
        return null
    }
}
