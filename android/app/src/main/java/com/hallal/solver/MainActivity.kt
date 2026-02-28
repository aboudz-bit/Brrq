package com.hallal.solver

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.graphics.Color
import android.graphics.Typeface
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUI()
    }

    override fun onResume() {
        super.onResume()
        buildUI() // Refresh status on return from settings
    }

    private fun buildUI() {
        val prefs = getSharedPreferences("hallal", MODE_PRIVATE)

        val scroll = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#04050a"))
            setPadding(48, 100, 48, 60)
        }

        // Logo
        root.addView(TextView(this).apply {
            text = "⚡"; textSize = 56f; gravity = Gravity.CENTER; setPadding(0, 0, 0, 8)
        })
        root.addView(TextView(this).apply {
            text = "حلّال ذكي"; textSize = 26f; setTextColor(Color.parseColor("#00e5ff"))
            typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        })
        root.addView(TextView(this).apply {
            text = "يقرأ برق ويجيب تلقائياً ⚡"; textSize = 13f
            setTextColor(Color.parseColor("#3a4a6a")); gravity = Gravity.CENTER
            layoutDirection = View.LAYOUT_DIRECTION_RTL; setPadding(0, 6, 0, 36)
        })

        // API Key label
        root.addView(TextView(this).apply {
            text = "🔑 مفتاح Anthropic API"; textSize = 12f
            setTextColor(Color.parseColor("#5a7099")); typeface = Typeface.DEFAULT_BOLD
            layoutDirection = View.LAYOUT_DIRECTION_RTL; gravity = Gravity.RIGHT; setPadding(0, 0, 0, 8)
        })

        // API Key input
        val keyInput = EditText(this).apply {
            hint = "sk-ant-..."
            textSize = 14f; setTextColor(Color.WHITE); setHintTextColor(Color.parseColor("#3a4a6a"))
            setBackgroundColor(Color.parseColor("#0c0f1a")); setPadding(32, 28, 32, 28)
            inputType = android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD or android.text.InputType.TYPE_CLASS_TEXT
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 28 }
            setText(prefs.getString("api_key", ""))
        }
        root.addView(keyInput)

        // Status checks
        val overlayOk = Settings.canDrawOverlays(this)
        val accessOk = isAccessibilityEnabled()
        root.addView(statusRow("Overlay — نافذة فوق التطبيقات", overlayOk))
        root.addView(statusRow("Accessibility — قراءة شاشة برق", accessOk))
        root.addView(spacer(24))

        // Buttons
        if (!overlayOk) {
            root.addView(makeButton("⚙️ منح تصريح Overlay", "#00e5ff") {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            })
        }
        if (!accessOk) {
            root.addView(makeButton("♿ تفعيل Accessibility Service", "#00ff88") {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                toast("ابحث عن 'حلّال' وفعّله")
            })
        }

        root.addView(makeButton("⚡ حفظ وتشغيل", "#6c63ff") {
            val k = keyInput.text.toString().trim()
            if (k.isEmpty()) { toast("أدخل مفتاح API أولاً"); return@makeButton }
            if (!Settings.canDrawOverlays(this)) { toast("فعّل تصريح Overlay أولاً"); return@makeButton }
            if (!isAccessibilityEnabled()) { toast("فعّل Accessibility Service أولاً"); return@makeButton }
            prefs.edit().putString("api_key", k).apply()
            stopService(Intent(this, OverlayService::class.java))
            startService(Intent(this, OverlayService::class.java))
            toast("✅ حلّال شغّال! افتح تطبيق برق الآن")
        })

        root.addView(makeButton("⏹ إيقاف", "#ff3355") {
            stopService(Intent(this, OverlayService::class.java))
            toast("تم الإيقاف")
        })

        root.addView(TextView(this).apply {
            text = "احصل على مفتاح مجاني:\nconsole.anthropic.com ← API Keys"
            textSize = 11f; setTextColor(Color.parseColor("#3a4a6a"))
            gravity = Gravity.CENTER; setPadding(0, 24, 0, 0)
        })

        scroll.addView(root)
        setContentView(scroll)
    }

    private fun isAccessibilityEnabled(): Boolean {
        val service = "$packageName/${HallalAccessibilityService::class.java.canonicalName}"
        return try {
            Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
                ?.contains(service) == true
        } catch (e: Exception) { false }
    }

    private fun statusRow(label: String, ok: Boolean): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(0, 8, 0, 8)
            addView(TextView(this@MainActivity).apply {
                text = if (ok) "✅" else "⭕"; textSize = 16f; setPadding(0, 0, 14, 0)
            })
            addView(TextView(this@MainActivity).apply {
                text = label; textSize = 13f
                setTextColor(if (ok) Color.parseColor("#00ff88") else Color.parseColor("#8a9bbf"))
                layoutDirection = View.LAYOUT_DIRECTION_RTL
            })
        }
    }

    private fun spacer(h: Int) = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, h)
    }

    private fun makeButton(label: String, color: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            text = label; textSize = 15f
            setTextColor(if (color == "#ff3355") Color.WHITE else Color.BLACK)
            setBackgroundColor(Color.parseColor(color))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 14 }
            setPadding(0, 22, 0, 22); isAllCaps = false
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setOnClickListener { onClick() }
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
}
