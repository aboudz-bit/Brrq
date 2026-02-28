package com.hallal.solver

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class HallalAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var lastQuestion = ""
    private var isProcessing = false
    private var overlayService: OverlayService? = null

    // ── Known Barq app package names (add more if needed) ──
    private val BARQ_PACKAGES = setOf(
        "com.barq.app", "com.brq.app", "barq", "com.barq",
        "sa.com.barq", "com.barq.quiz"
    )

    companion object {
        var instance: HallalAccessibilityService? = null
    }

    override fun onServiceConnected() {
        instance = this
        showOverlay("✅ حلّال متصل وجاهز")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return

        // Only react to Barq app events
        val pkg = event.packageName?.toString() ?: return
        if (!BARQ_PACKAGES.any { pkg.contains(it, ignoreCase = true) }) return

        // Throttle: don't process if already working
        if (isProcessing) return

        // Extract all text from the screen
        val screenText = extractScreenText(rootInActiveWindow)
        if (screenText.isBlank()) return

        // Detect if there's a question (has options أ/ب or A/B)
        if (!looksLikeMCQ(screenText)) return

        // Skip if same question
        val questionHash = screenText.take(100)
        if (questionHash == lastQuestion) return
        lastQuestion = questionHash

        // Solve it!
        isProcessing = true
        showOverlay("🔍 اكتشفت سؤال، جاري الحل…")

        scope.launch {
            solveQuestion(screenText)
        }
    }

    // ── Extract all text nodes from screen ──
    private fun extractScreenText(node: AccessibilityNodeInfo?): String {
        node ?: return ""
        val sb = StringBuilder()

        fun traverse(n: AccessibilityNodeInfo?) {
            n ?: return
            val text = n.text?.toString()
            val desc = n.contentDescription?.toString()
            if (!text.isNullOrBlank()) sb.appendLine(text.trim())
            else if (!desc.isNullOrBlank()) sb.appendLine(desc.trim())
            for (i in 0 until n.childCount) traverse(n.getChild(i))
        }

        traverse(node)
        return sb.toString().trim()
    }

    // ── Check if text looks like MCQ ──
    private fun looksLikeMCQ(text: String): Boolean {
        val hasArabicOptions = text.contains("أ)") || text.contains("ب)") ||
                text.contains("أ-") || text.contains("ب-") ||
                text.contains("١-") || text.contains("١.")
        val hasLatinOptions = Regex("[A-D][.)\\-]").containsMatchIn(text)
        val hasQuestion = text.contains("؟") || text.contains("?") ||
                text.contains("ما ") || text.contains("كم ") ||
                text.contains("هل ") || text.contains("أي ")
        return (hasArabicOptions || hasLatinOptions) && hasQuestion
    }

    // ── Call Claude API ──
    private suspend fun solveQuestion(screenText: String) {
        withContext(Dispatchers.IO) {
            try {
                val prefs = getSharedPreferences("hallal", MODE_PRIVATE)
                val apiKey = prefs.getString("api_key", "") ?: ""
                if (apiKey.isEmpty()) {
                    showOverlay("⚠️ أدخل مفتاح API في الإعدادات")
                    isProcessing = false
                    return@withContext
                }

                val prompt = """
محتوى شاشة تطبيق الكويز:
"$screenText"

استخرج سؤال MCQ وأعطني الإجابة.
رد بـ JSON فقط بدون أي نص آخر:
{"question":"نص السؤال","answer":"الحرف (أ أو ب أو ج أو A أو B أو C)","answer_text":"نص الإجابة الكاملة","explain":"شرح قصير جداً","confidence":95}
""".trimIndent()

                val url = URL("https://api.anthropic.com/v1/messages")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("x-api-key", apiKey)
                conn.setRequestProperty("anthropic-version", "2023-06-01")
                conn.doOutput = true
                conn.connectTimeout = 10000
                conn.readTimeout = 15000

                val body = """
{
  "model": "claude-haiku-4-5-20251001",
  "max_tokens": 300,
  "messages": [{"role": "user", "content": ${JSONObject.quote(prompt)}}]
}
""".trimIndent()

                OutputStreamWriter(conn.outputStream).use { it.write(body) }

                val response = BufferedReader(InputStreamReader(conn.inputStream)).use {
                    it.readText()
                }

                val jsonResp = JSONObject(response)
                var raw = jsonResp.getJSONArray("content")
                    .getJSONObject(0).getString("text")
                    .trim().replace("```json", "").replace("```", "").trim()

                // Extract JSON
                val jsonStart = raw.indexOf('{')
                val jsonEnd = raw.lastIndexOf('}')
                if (jsonStart >= 0 && jsonEnd > jsonStart) {
                    raw = raw.substring(jsonStart, jsonEnd + 1)
                }

                val result = JSONObject(raw)
                val answer = result.optString("answer", "?")
                val answerText = result.optString("answer_text", "")
                val explain = result.optString("explain", "")
                val confidence = result.optInt("confidence", 90)

                // Show overlay with answer
                withContext(Dispatchers.Main) {
                    showAnswerOverlay(answer, answerText, explain, confidence)
                    // Try to click the answer button
                    clickAnswer(answerText, answer)
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showOverlay("❌ خطأ: ${e.message?.take(60)}")
                }
            } finally {
                delay(2000)
                isProcessing = false
            }
        }
    }

    // ── Try to click the answer on screen ──
    private fun clickAnswer(answerText: String, answerLetter: String) {
        val root = rootInActiveWindow ?: return

        fun findAndClick(node: AccessibilityNodeInfo?): Boolean {
            node ?: return false

            val nodeText = node.text?.toString() ?: node.contentDescription?.toString() ?: ""

            // Match by text content
            val textMatch = answerText.isNotEmpty() &&
                    answerText.length > 3 &&
                    nodeText.contains(answerText.take(12), ignoreCase = true)

            // Match by letter (أ, ب, A, B etc)
            val letterMatch = nodeText.trim().startsWith(answerLetter) ||
                    nodeText.contains("$answerLetter)") ||
                    nodeText.contains("$answerLetter-")

            if ((textMatch || letterMatch) && node.isClickable) {
                val bounds = Rect()
                node.getBoundsInScreen(bounds)
                if (bounds.width() > 0 && bounds.height() > 0) {
                    // Perform click via gesture (more reliable)
                    val path = Path().apply {
                        moveTo(bounds.centerX().toFloat(), bounds.centerY().toFloat())
                    }
                    val gesture = GestureDescription.Builder()
                        .addStroke(GestureDescription.StrokeDescription(path, 100, 50))
                        .build()
                    dispatchGesture(gesture, null, null)
                    return true
                }
                // Fallback: accessibility click
                node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                return true
            }

            // Try children
            for (i in 0 until node.childCount) {
                if (findAndClick(node.getChild(i))) return true
            }
            return false
        }

        val clicked = findAndClick(root)
        if (!clicked) {
            // Highlight answer visually if can't click
            OverlayService.instance?.showPulse()
        }
    }

    // ── Show simple toast overlay ──
    private fun showOverlay(msg: String) {
        OverlayService.instance?.updateStatus(msg)
    }

    // ── Show full answer overlay ──
    private fun showAnswerOverlay(
        letter: String,
        text: String,
        explain: String,
        confidence: Int
    ) {
        OverlayService.instance?.showAnswer(letter, text, explain, confidence)
    }

    override fun onInterrupt() {
        instance = null
    }

    override fun onDestroy() {
        scope.cancel()
        instance = null
        super.onDestroy()
    }
}
