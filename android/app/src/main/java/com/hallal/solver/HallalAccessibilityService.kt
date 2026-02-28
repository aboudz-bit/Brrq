package com.hallal.solver

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class HallalAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var lastHash = ""
    private var busy = false
    private val mainHandler = Handler(Looper.getMainLooper())

    companion object { var instance: HallalAccessibilityService? = null }

    override fun onServiceConnected() {
        instance = this
        mainHandler.post { OverlayService.instance?.updateStatus("✅ جاهز") }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val pkg = event.packageName?.toString() ?: return
        if (!pkg.contains("barraq", ignoreCase = true) && !pkg.contains("barq", ignoreCase = true)) return
        if (busy) return

        val text = extractText(rootInActiveWindow) 
        if (text.isBlank() || !isMCQ(text)) return

        val hash = text.filter { !it.isWhitespace() }.take(80)
        if (hash == lastHash) return
        lastHash = hash
        busy = true

        mainHandler.post { OverlayService.instance?.updateStatus("🔍 يحلّل...") }
        scope.launch {
            try { solve(text) }
            finally { delay(3000); busy = false; mainHandler.post { OverlayService.instance?.updateStatus("✅ جاهز") } }
        }
    }

    private fun extractText(node: AccessibilityNodeInfo?): String {
        val sb = StringBuilder()
        fun walk(n: AccessibilityNodeInfo?) {
            n ?: return
            n.text?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let { sb.appendLine(it) }
            for (i in 0 until n.childCount) walk(n.getChild(i))
        }
        walk(node)
        return sb.toString().trim()
    }

    private fun isMCQ(text: String): Boolean {
        val opts = text.contains("أ)") || text.contains("ب)") || text.contains("أ-") || text.contains("ب-") ||
                   text.contains("أ.") || Regex("[A-D][.)\\-]").containsMatchIn(text)
        val q = text.contains("؟") || text.contains("?") || text.contains("ما ") || text.contains("هل ") || text.contains("كم ")
        return opts && q && text.length > 20
    }

    private suspend fun solve(screenText: String) {
        try {
            val key = getSharedPreferences("hallal", MODE_PRIVATE).getString("api_key", "").orEmpty()
            if (key.isEmpty()) { mainHandler.post { OverlayService.instance?.updateStatus("⚠️ أدخل مفتاح API") }; return }

            val prompt = "نص شاشة برق:\n\"${screenText.take(1500)}\"\n\nاستخرج سؤال MCQ وأعطني الإجابة.\nJSON فقط:\n{\"question\":\"نص\",\"answer\":\"الحرف\",\"answer_text\":\"نص الإجابة\",\"explain\":\"شرح\",\"confidence\":95}"

            val body = JSONObject().apply {
                put("model", "claude-haiku-4-5-20251001")
                put("max_tokens", 300)
                put("messages", JSONArray().put(JSONObject().apply { put("role","user"); put("content",prompt) }))
            }.toString()

            val conn = (URL("https://api.anthropic.com/v1/messages").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("x-api-key", key)
                setRequestProperty("anthropic-version", "2023-06-01")
                doOutput = true; connectTimeout = 12000; readTimeout = 20000
            }
            OutputStreamWriter(conn.outputStream, "UTF-8").use { it.write(body) }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val raw = BufferedReader(InputStreamReader(stream, "UTF-8")).use { it.readText() }

            if (code !in 200..299) { mainHandler.post { OverlayService.instance?.updateStatus("❌ API $code") }; return }

            var txt = JSONObject(raw).getJSONArray("content").getJSONObject(0).getString("text")
                .trim().replace("```json","").replace("```","").trim()
            val s = txt.indexOf('{'); val e = txt.lastIndexOf('}')
            if (s < 0 || e < s) { mainHandler.post { OverlayService.instance?.updateStatus("❌ تعذّر التحليل") }; return }
            txt = txt.substring(s, e+1)

            val r = JSONObject(txt)
            val answer = r.optString("answer","?")
            val ansText = r.optString("answer_text","")
            val explain = r.optString("explain","")
            val conf = r.optInt("confidence",90)

            mainHandler.post {
                OverlayService.instance?.showAnswer(answer, ansText, explain, conf)
                mainHandler.postDelayed({ tryClick(ansText, answer) }, 500)
            }
        } catch (e: Exception) {
            mainHandler.post { OverlayService.instance?.updateStatus("❌ ${e.message?.take(40)}") }
        }
    }

    private fun tryClick(answerText: String, letter: String) {
        fun click(n: AccessibilityNodeInfo?): Boolean {
            n ?: return false
            val t = (n.text?.toString() ?: n.contentDescription?.toString() ?: "").trim()
            val match = (answerText.length > 4 && t.contains(answerText.take(12), ignoreCase = true)) ||
                        t.startsWith(letter) || t.contains("$letter)") || t.contains("$letter.") || t.contains("$letter-")
            if (match) {
                var cur: AccessibilityNodeInfo? = n
                repeat(4) {
                    if (cur?.isClickable == true) {
                        val b = Rect(); cur.getBoundsInScreen(b)
                        if (b.width() > 10) {
                            dispatchGesture(GestureDescription.Builder()
                                .addStroke(GestureDescription.StrokeDescription(
                                    Path().apply { moveTo(b.centerX().toFloat(), b.centerY().toFloat()) }, 0, 50))
                                .build(), null, null)
                            return true
                        }
                        cur.performAction(AccessibilityNodeInfo.ACTION_CLICK); return true
                    }
                    cur = cur?.parent
                }
            }
            for (i in 0 until n.childCount) if (click(n.getChild(i))) return true
            return false
        }
        click(rootInActiveWindow)
    }

    override fun onInterrupt() { instance = null }
    override fun onDestroy() { scope.cancel(); instance = null; super.onDestroy() }
}
