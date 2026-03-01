package com.hallal.solver

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.os.*
import android.view.*
import android.widget.*
import androidx.core.app.NotificationCompat

class OverlayService : Service() {

    private lateinit var wm: WindowManager
    private var bubbleView: View? = null
    private var answerView: View? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    companion object {
        var instance: OverlayService? = null
        private const val CHANNEL_ID = "hallal_ch"
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotifChannel()
        startForeground(1, buildNotif())
        showBubble()
    }

    private fun overlayType() =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else WindowManager.LayoutParams.TYPE_PHONE

    private fun makeParams(w: Int, h: Int) = WindowManager.LayoutParams(
        w, h, overlayType(),
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
        PixelFormat.TRANSLUCENT
    )

    private fun showBubble() {
        val bubble = TextView(this).apply {
            text = "⚡"; textSize = 20f; gravity = Gravity.CENTER
            setTextColor(Color.BLACK)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#CC00e5ff"))
            }
        }
        val dp = resources.displayMetrics.density
        val bubbleSize = (56 * dp).toInt()
        val screenWidth = resources.displayMetrics.widthPixels
        val params = makeParams(bubbleSize, bubbleSize).apply {
            gravity = Gravity.TOP or Gravity.START
            x = screenWidth - bubbleSize - (16 * dp).toInt(); y = (280 * dp).toInt()
        }
        var dX = 0f; var dY = 0f; var moved = false
        bubble.setOnTouchListener { v, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { dX = e.rawX - params.x; dY = e.rawY - params.y; moved = false; true }
                MotionEvent.ACTION_MOVE -> { params.x = (e.rawX - dX).toInt(); params.y = (e.rawY - dY).toInt(); wm.updateViewLayout(v, params); moved = true; true }
                MotionEvent.ACTION_UP  -> { if (!moved) dismissAnswer(); true }
                else -> false
            }
        }
        wm.addView(bubble, params)
        bubbleView = bubble
    }

    fun updateStatus(msg: String) {
        mainHandler.post {
            (bubbleView as? TextView)?.text = when {
                msg.startsWith("🔍") -> "⏳"
                msg.startsWith("✅") -> "⚡"
                msg.startsWith("❌") -> "❗"
                else -> "⚡"
            }
        }
    }

    fun showAnswer(letter: String, text: String, explain: String, confidence: Int) {
        mainHandler.post {
            dismissAnswer()
            val ctx = this
            val dp = resources.displayMetrics.density

            val card = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                layoutDirection = View.LAYOUT_DIRECTION_RTL
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = (24 * dp)
                    setColor(Color.parseColor("#F2060a14"))
                    setStroke(4, Color.parseColor("#00e5ff"))
                }
                setPadding((36 * dp).toInt(), (28 * dp).toInt(), (36 * dp).toInt(), (22 * dp).toInt())
            }

            // Hero row: letter + answer text
            val hero = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 0, 0, (16 * dp).toInt())
            }
            val ltrBox = TextView(ctx).apply {
                this.text = letter; textSize = 38f
                setTextColor(Color.BLACK); typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE; cornerRadius = (18 * dp)
                    colors = intArrayOf(Color.parseColor("#00e5ff"), Color.parseColor("#0055ff"))
                    orientation = GradientDrawable.Orientation.TL_BR
                    gradientType = GradientDrawable.LINEAR_GRADIENT
                }
                layoutParams = LinearLayout.LayoutParams((80 * dp).toInt(), (80 * dp).toInt()).apply { marginEnd = (14 * dp).toInt() }
            }
            val info = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(ctx).apply {
                    this.text = "الإجابة الصحيحة"; textSize = 10f; setTextColor(Color.parseColor("#5a7099"))
                })
                addView(TextView(ctx).apply {
                    this.text = text; textSize = 17f
                    setTextColor(Color.parseColor("#00ff88")); typeface = Typeface.DEFAULT_BOLD
                    setPadding(0, 6, 0, 0)
                })
            }
            hero.addView(ltrBox); hero.addView(info)

            // Explain
            val explainView = TextView(ctx).apply {
                this.text = explain; textSize = 12f
                setTextColor(Color.parseColor("#5a7099")); setPadding(0, 0, (10 * dp).toInt(), (14 * dp).toInt())
            }

            // Confidence bar
            val confRow = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, 0, 0, 14)
                addView(TextView(ctx).apply {
                    this.text = "الثقة "; textSize = 10f; setTextColor(Color.parseColor("#3a4a6a"))
                })
                val conf = confidence.coerceIn(0, 100)
                val track = FrameLayout(ctx).apply {
                    layoutParams = LinearLayout.LayoutParams(0, (14 * dp).toInt(), 1f).apply { marginEnd = (10 * dp).toInt() }
                    background = GradientDrawable().apply { shape = GradientDrawable.RECTANGLE; cornerRadius = (7 * dp); setColor(Color.parseColor("#161d35")) }
                    addView(View(ctx).apply {
                        background = GradientDrawable().apply {
                            shape = GradientDrawable.RECTANGLE; cornerRadius = (7 * dp)
                            colors = intArrayOf(Color.parseColor("#00e5ff"), Color.parseColor("#00ff88"))
                            orientation = GradientDrawable.Orientation.LEFT_RIGHT
                        }
                        layoutParams = FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.MATCH_PARENT
                        )
                        pivotX = 0f
                        scaleX = conf / 100f
                    })
                }
                addView(track)
                addView(TextView(ctx).apply {
                    this.text = "$conf%"; textSize = 11f; setTextColor(Color.parseColor("#00e5ff")); typeface = Typeface.DEFAULT_BOLD
                })
            }

            // Close button
            val closeBtn = Button(ctx).apply {
                this.text = "✕ إغلاق"; textSize = 13f; setTextColor(Color.parseColor("#5a7099"))
                setBackgroundColor(Color.parseColor("#0c1020")); isAllCaps = false
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                setOnClickListener { dismissAnswer() }
            }

            card.addView(hero); card.addView(explainView); card.addView(confRow); card.addView(closeBtn)

            val screenW = resources.displayMetrics.widthPixels
            val cardW = (screenW * 0.88f).toInt()
            val cp = makeParams(cardW, WindowManager.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL; y = 80
            }
            wm.addView(card, cp)
            answerView = card

            // Vibrate
            val vib = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                vib.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 80, 50, 80), -1))
            else @Suppress("DEPRECATION")
                vib.vibrate(longArrayOf(0, 80, 50, 80), -1)
        }
    }

    private fun dismissAnswer() {
        try { answerView?.let { wm.removeView(it) } } catch (_: Exception) {}
        answerView = null
    }

    private fun createNotifChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID, "حلّال", NotificationManager.IMPORTANCE_LOW)
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(ch)
        }
    }

    private fun buildNotif() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("⚡ حلّال شغّال").setContentText("يراقب برق ويجيب تلقائياً")
        .setSmallIcon(android.R.drawable.ic_dialog_info).setPriority(NotificationCompat.PRIORITY_LOW).build()

    override fun onBind(intent: Intent?) = null

    override fun onDestroy() {
        instance = null
        try { bubbleView?.let { wm.removeView(it) } } catch (_: Exception) {}
        dismissAnswer()
        super.onDestroy()
    }
}
