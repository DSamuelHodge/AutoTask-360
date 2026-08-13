package com.example.wa

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebSettings
import com.example.engine.AutoTaskEngine
import com.example.engine.AutomationEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * WhatsApp Web bridge: hosts web.whatsapp.com in an off-screen WebView inside
 * the AutoTask app and wires it to the automation engine.
 *
 * - Inbound: injected JS observes new incoming messages and calls back into
 *   [BridgeJs.onIncoming], which fires a `NOTIFICATION` event with
 *   `packageName = com.whatsapp` — the existing `cos-aware-whatsapp` profile
 *   matches and the CoS daemon resolves/logs/notifies exactly like the native
 *   WhatsApp notifications. The real WhatsApp app's notifications are not
 *   relied on, so the bridge works even when WhatsApp's own notifications are
 *   silenced.
 * - Outbound: [sendMessage] evaluates JS to open a chat and type/send. Exposed
 *   to AutoTask's loopback server via `POST /v1/wa/send`.
 *
 * The WebView lives in a foreground service so it survives backgrounding and
 * keeps the WhatsApp Web session (localStorage/cookies) alive across app
 * restarts.
 */
object WhatsAppBridgeManager {

    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    var webView: WebView? = null
        private set

    @Volatile
    var isPaired: Boolean = false
        private set

    @Volatile
    var lastError: String? = null

    @Volatile
    var lastSendResult: String? = null
        private set

    /** Called on the main thread when a new inbound message arrives. */
    var onInbound: ((sender: String, text: String) -> Unit)? = null

    /** Holds the engine instance for firing events (set once at init). */
    private var engineRef: AutoTaskEngine? = null

    fun initialize(context: Context) {
        engineRef = AutoTaskEngine.getInstance(context)
    }

    /**
     * Create (or reuse) the WebView on the main thread and point it at
     * web.whatsapp.com with a desktop user agent.
     */
    fun ensureWebView(context: Context): WebView? {
        if (webView != null) return webView

        val webView = WebView(context.applicationContext)
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            setSupportMultipleWindows(false)
            javaScriptCanOpenWindowsAutomatically = false
            cacheMode = WebSettings.LOAD_DEFAULT
            userAgentString =
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                if (url?.contains("web.whatsapp.com") == true) {
                    isPaired = false
                    lastError = null
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                // Check pairing + install the bridge. Injection runs on every
                // finished page load so the listener survives WA's SPA reloads.
                view?.evaluateJavascript(BRIDGE_JS, null)
                detectState(view)
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                val code = error?.errorCode ?: -1
                val desc = error?.description?.toString() ?: "unknown"
                lastError = "web error $code: $desc"
            }
        }

        webView.addJavascriptInterface(BridgeJs(), "CoSWaBridge")

        this.webView = webView
        webView.loadUrl("https://web.whatsapp.com/")
        return webView
    }

    /** Run a block on the main thread (WebView must be touched from main). */
    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post(block)
        }
    }

    /** Attach the managed WebView to a container (e.g. the pairing Activity). */
    fun attachTo(container: android.view.ViewGroup): WebView? {
        val view = ensureWebView(container.context) ?: return null
        onMain {
            if (view.parent != null) {
                (view.parent as? android.view.ViewGroup)?.removeView(view)
            }
            container.addView(view)
        }
        return view
    }

    /** Detach the WebView from any parent but keep it alive in the manager. */
    fun detach() {
        val view = webView ?: return
        onMain {
            (view.parent as? android.view.ViewGroup)?.removeView(view)
        }
    }

    /** Inspect the current DOM to decide paired vs QR-not-visible. */
    private fun detectState(view: WebView?) {
        view?.evaluateJavascript(
            "window.__cosWaState ? window.__cosWaState() : 'null'"
        ) { result ->
            val clean = result.trim().trim('"')
            when {
                clean == "paired" -> { isPaired = true; lastError = null }
                clean == "qrcode" -> { isPaired = false }
                else -> { /* still loading */ }
            }
        }
    }

    /** Send a WhatsApp message to a full international number (e.g. +1614...). */
    fun sendMessage(phone: String, text: String): Boolean {
        val view = webView ?: return false
        if (!isPaired) return false
        val safePhone = phone.replace(Regex("[^+0-9]"), "")
        val safeText = text.replace("\\", "\\\\").replace("\"", "\\\"")
        val js = "window.__cosWaSend ? window.__cosWaSend(\"$safePhone\", \"$safeText\") : false"
        onMain {
            view.evaluateJavascript(js, null)
        }
        return true
    }

    /** The JS bridge object injected as `window.CoSWaBridge`. */
    class BridgeJs {
        @JavascriptInterface
        fun onIncoming(json: String) {
            try {
                val obj = JSONObject(json)
                val sender = obj.optString("sender", "Unknown")
                val text = obj.optString("text", "")
                if (text.isBlank()) return

                val engine = engineRef ?: return
                val scope = CoroutineScope(Dispatchers.IO)
                scope.launch {
                    engine.processEvent(
                        AutomationEvent(
                            type = "NOTIFICATION",
                            payload = mapOf(
                                "packageName" to "com.whatsapp",
                                "title" to sender,
                                "text" to text
                            )
                        )
                    )
                }
                onInbound?.invoke(sender, text)
            } catch (_: Exception) {
                // Malformed payload from the page — ignore.
            }
        }

        @JavascriptInterface
        fun onState(state: String) {
            when (state) {
                "paired" -> { isPaired = true; lastError = null }
                "qrcode" -> isPaired = false
            }
        }

        @JavascriptInterface
        fun onSendResult(status: String, detail: String) {
            lastSendResult = "$status: $detail"
        }
    }

    /** Injected once into every finished page load. */
    private val BRIDGE_JS = """
        (function () {
          if (window.__cosWaInstalled) return;
          window.__cosWaInstalled = true;

          window.__cosWaState = function () {
            // WhatsApp Web shows a QR on the login screen.
            var canvas = document.querySelector('canvas[aria-label*="Scan"]') ||
                         document.querySelector('canvas');
            if (canvas && canvas.style.display !== 'none') return 'qrcode';
            return 'paired';
          };

          window.__cosWaSend = function (phone, text) {
            try {
              // The WebView disallows window.open (single-window), so navigate
              // in-tab. WhatsApp Web's /send?phone= deep link opens the chat
              // and pre-fills the composer from the `text` param natively, so
              // we don't have to fake keystrokes into a React contenteditable.
              // Stash the payload too: if the prefill doesn't land, the bridge
              // re-injection (onPageFinished) types it via execCommand.
              sessionStorage.setItem('cosWaPending', JSON.stringify({ phone: phone, text: text }));
              var encoded = encodeURIComponent(text);
              window.location.href = 'https://web.whatsapp.com/send?phone=' + phone + '&text=' + encoded;
              return true;
            } catch (e) { return false; }
          };

          // Flush any pending send after a page load (deep-link navigation).
          (function flushPending() {
            try {
              var raw = sessionStorage.getItem('cosWaPending');
              if (!raw) return;
              var p = JSON.parse(raw);
              var tries = 0;
              var sending = false;
              var iv = setInterval(function () {
                if (sending) return;          // exactly one send attempt per message
                var input = document.querySelector(
                  'div[contenteditable="true"][data-tab="10"], div[contenteditable="true"][data-tab="6"], div[contenteditable="true"][role="textbox"]');
                if (input) {
                  sending = true;
                  clearInterval(iv);          // stop re-arming the composer watcher
                  var cur = input.textContent || '';
                  if (!cur || cur.indexOf(p.text) === -1) {
                    input.focus();
                    document.execCommand('selectAll', false, null);
                    document.execCommand('insertText', false, p.text);
                    input.dispatchEvent(new Event('input', { bubbles: true }));
                  }
                  // Wait for the send affordance (it appears once the composer
                  // has text). Try several selectors, then Enter as a fallback.
                  var sendTries = 0;
                  var iv2 = setInterval(function () {
                    sendTries++;
                    var btn = document.querySelector('span[data-icon="send"], button[data-testid="compose-btn-send"], [aria-label="Send"]');
                    if (btn) {
                      clearInterval(iv2);
                      btn.click();
                      sessionStorage.removeItem('cosWaPending');
                      try { window.CoSWaBridge.onSendResult('sent', p.phone); } catch (e) {}
                    } else if (sendTries >= 12) {
                      // Fallback: Enter usually sends in WA Web.
                      clearInterval(iv2);
                      input.focus();
                      input.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', code: 'Enter', keyCode: 13, which: 13, bubbles: true }));
                      input.dispatchEvent(new KeyboardEvent('keyup', { key: 'Enter', code: 'Enter', keyCode: 13, which: 13, bubbles: true }));
                      sessionStorage.removeItem('cosWaPending');
                      try { window.CoSWaBridge.onSendResult('sent_via_enter', p.phone); } catch (e) {}
                    }
                  }, 500);
                  return;
                }
                if (++tries > 40) {
                  clearInterval(iv);
                  sessionStorage.removeItem('cosWaPending');
                  try { window.CoSWaBridge.onSendResult('timeout', p.phone); } catch (e) {}
                }
              }, 500);
            } catch (e) {}
          })();

          // Observe the message list and report new *incoming* messages.
          var known = new Set();
          var pending = {};
          var timer = null;

          function digest() {
            var items = document.querySelectorAll('div.message-in');
            for (var i = 0; i < items.length; i++) {
              var el = items[i];
              var id = el.getAttribute('data-id');
              if (!id || known.has(id)) continue;
              known.add(id);
              // Sender: from the message tail's preceding name element if present,
              // else fall back to the current chat title.
              var textEl = el.querySelector('span.selectable-text');
              var text = textEl ? textEl.textContent : '';
              if (!text) continue;
              var senderEl = el.querySelector('span[data-testid="msg-meta"] span, span[title]');
              var sender = senderEl ? senderEl.getAttribute('title') || senderEl.textContent : '';
              var chatEl = document.querySelector('header span[dir="auto"]');
              var chat = chatEl ? chatEl.textContent : '';
              var payload = { sender: sender || chat || 'Unknown', text: text };
              try { window.CoSWaBridge.onIncoming(JSON.stringify(payload)); } catch (e) {}
            }
          }

          // Re-scan whenever the DOM mutates (new messages, chat switches).
          var observer = new MutationObserver(function () {
            clearTimeout(timer);
            timer = setTimeout(digest, 400);
          });
          observer.observe(document.body, { childList: true, subtree: true });
          setTimeout(digest, 2000);

          // Push state to the native side.
          function pushState() {
            try { window.CoSWaBridge.onState(window.__cosWaState()); } catch (e) {}
          }
          setInterval(pushState, 5000);
          setTimeout(pushState, 1500);
        })();
    """.trimIndent()
}
