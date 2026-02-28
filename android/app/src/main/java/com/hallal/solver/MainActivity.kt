package com.hallal.solver

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import android.graphics.Color
import android.view.*
import android.widget.LinearLayout

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("hallal", MODE_PRIVATE)

        // ── Build UI programmatically (no XML needed) ──
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#04050a"))
            setPadding(48, 80, 48, 48)
        }

        // Logo
        val logo = TextView(this).apply {
            text = "⚡"
            textSize = 64f
            gravity = android.view.Gravity.CENTER
        }

        // Title
        val title = TextView(this).apply {
            text = "حلّال ذكي"
            textSize = 28f
            setTextColor(Color.parseColor("#00e5ff"))
            gravity = android.view.Gravity.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }

        // Subtitle
        val sub = TextView(this).apply {
            text = "يقرأ سؤال برق ويجيب تلقائياً"
            textSize = 14f
            setTextColor(Color.parseColor("#3a4a6a"))
            gravity = android.view.Gravity.CENTER
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(0, 8, 0, 40)
        }

        // API Key label
        val keyLabel = TextView(this).apply {
            text = "🔑 مفتاح Anthropic API"
            textSize = 12f
            setTextColor(Color.parseColor("#3a4a6a"))
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            gravity = android.view.Gravity.RIGHT
            setPadding(0, 0, 0, 8)
        }

        // API Key input
        val keyInput = EditText(this).apply {
            hint = "sk-ant-..."
            textSize = 14f
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#3a4a6a"))
            setBackgroundColor(Color.parseColor("#0c0f1a"))
            setPadding(32, 28, 32, 28)
            inputType = android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD or
                    android.text.InputType.TYPE_CLASS_TEXT
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 24 }
            // Load saved key
            setText(prefs.getString("api_key", ""))
        }

        // Status card
        val statusCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0c0f1a"))
            setPadding(32, 24, 32, 24)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 24 }
        }

        val step1 = makeStepView("1", "تصريح Overlay (نافذة فوق التطبيقات)", prefs.getBoolean("overlay_granted", false))
        val step2 = makeStepView("2", "تصريح Accessibility (قراءة الشاشة)", isAccessibilityEnabled())

        statusCard.addView(step1)
        statusCard.addView(step2)

        // Button: Grant Overlay
        val btnOverlay = makeButton("منح تصريح الـ Overlay ➜", "#00e5ff") {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName"))
            startActivity(intent)
        }

        // Button: Grant Accessibility
        val btnAccess = makeButton("تفعيل Accessibility Service ➜", "#00ff88") {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
            Toast.makeText(this,
                "ابحث عن '⚡ حلّال' وفعّله",
                Toast.LENGTH_LONG).show()
        }

        // Button: Save & Start
        val btnStart = makeButton("💾 حفظ وتشغيل ⚡", "#6c63ff") {
            val key = keyInput.text.toString().trim()
            if (key.isEmpty()) {
                Toast.makeText(this, "أدخل المفتاح أولاً", Toast.LENGTH_SHORT).show()
                return@makeButton
            }
            prefs.edit().putString("api_key", key).apply()

            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "فعّل تصريح Overlay أولاً", Toast.LENGTH_SHORT).show()
                return@makeButton
            }
            if (!isAccessibilityEnabled()) {
                Toast.makeText(this, "فعّل Accessibility Service أولاً", Toast.LENGTH_SHORT).show()
                return@makeButton
            }

            // Start overlay service
            startService(Intent(this, OverlayService::class.java))
            Toast.makeText(this, "✅ حلّال شغّال! افتح برق الآن", Toast.LENGTH_LONG).show()
        }

        // Button: Stop
        val btnStop = makeButton("⏹ إيقاف التطبيق", "#ff3355") {
            stopService(Intent(this, OverlayService::class.java))
            Toast.makeText(this, "تم الإيقاف", Toast.LENGTH_SHORT).show()
        }

        // Hint
        val hint = TextView(this).apply {
            text = "للحصول على مفتاح مجاني:\nconsole.anthropic.com ← API Keys"
            textSize = 12f
            setTextColor(Color.parseColor("#3a4a6a"))
            gravity = android.view.Gravity.CENTER
            setPadding(0, 16, 0, 0)
        }

        // Add all views
        root.addView(logo)
        root.addView(title)
        root.addView(sub)
        root.addView(keyLabel)
        root.addView(keyInput)
        root.addView(statusCard)
        root.addView(btnOverlay)
        root.addView(btnAccess)
        root.addView(btnStart)
        root.addView(btnStop)
        root.addView(hint)

        val scroll = ScrollView(this)
        scroll.addView(root)
        setContentView(scroll)
    }

    override fun onResume() {
        super.onResume()
        // Refresh UI when returning from settings
        recreate()
    }

    private fun isAccessibilityEnabled(): Boolean {
        val service = "$packageName/${HallalAccessibilityService::class.java.canonicalName}"
        return try {
            val enabled = Settings.Secure.getString(
                contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
            enabled?.contains(service) == true
        } catch (e: Exception) { false }
    }

    private fun makeStepView(num: String, text: String, done: Boolean): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 8, 0, 8)
            layoutDirection = View.LAYOUT_DIRECTION_RTL

            addView(TextView(this@MainActivity).apply {
                this.text = if (done) "✅" else "⭕"
                textSize = 18f
                setPadding(0, 0, 16, 0)
            })
            addView(TextView(this@MainActivity).apply {
                this.text = "$num. $text"
                textSize = 13f
                setTextColor(if (done) Color.parseColor("#00ff88") else Color.parseColor("#8a9bbf"))
                layoutDirection = View.LAYOUT_DIRECTION_RTL
            })
        }
    }

    private fun makeButton(label: String, color: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            text = label
            textSize = 15f
            setTextColor(if (color == "#ff3355") Color.WHITE else Color.BLACK)
            setBackgroundColor(Color.parseColor(color))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 16 }
            setPadding(0, 24, 0, 24)
            isAllCaps = false
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setOnClickListener { onClick() }
        }
    }
}
