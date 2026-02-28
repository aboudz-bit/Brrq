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
    private var overlayView: View? = null
    private var isExpanded = false

    companion object {
        var instance: OverlayService? = null
        const val CHANNEL_ID = "hallal_channel"
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        startForeground(1, buildNotification())
        showFloatingBubble()
    }

    // ── Floating bubble (always visible) ──
    private fun showFloatingBubble() {
        val bubble = TextView(this).apply {
            text = "⚡"
            textSize = 22f
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#CC00e5ff"))
            setPadding(20, 20, 20, 20)
        }

        // Make it circular
        bubble.post {
            val size = bubble.height
            val shape = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#CC00e5ff"))
            }
            bubble.background = shape
        }

        val params = WindowManager.LayoutParams(
            120, 120,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 16; y = 300
        }

        // Drag support
        var dX = 0f; var dY = 0f; var moved = false
        bubble.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    dX = event.rawX - params.x
                    dY = event.rawY - params.y
                    moved = false; true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = (event.rawX - dX).toInt()
                    params.y = (event.rawY - dY).toInt()
                    wm.updateViewLayout(v, params)
                    moved = true; true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) toggleExpand()
                    true
                }
                else -> false
            }
        }

        wm.addView(bubble, params)
        overlayView = bubble
    }

    private fun toggleExpand() {
        isExpanded = !isExpanded
    }

    // ── Update status text in bubble ──
    fun updateStatus(msg: String) {
        Handler(Looper.getMainLooper()).post {
            (overlayView as? TextView)?.text = "⚡"
        }
    }

    // ── Show answer as expanded overlay ──
    fun showAnswer(letter: String, text: String, explain: String, confidence: Int) {
        Handler(Looper.getMainLooper()).post {
            // Remove old answer view if exists
            dismissAnswerView()

            val ctx = this

            // Build answer card
            val card = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                layoutDirection = View.LAYOUT_DIRECTION_RTL
                setBackgroundColor(Color.parseColor("#F0060a14"))
                setPadding(32, 28, 32, 24)
            }

            // Add round corners via background
            val bg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 40f
                setColor(Color.parseColor("#F0060a14"))
                setStroke(4, Color.parseColor("#00e5ff"))
            }
            card.background = bg

            // ── Top row: big letter + answer text ──
            val topRow = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 0, 0, 16)
            }

            // Letter box
            val letterBox = TextView(ctx).apply {
                this.text = letter
                textSize = 36f
                setTextColor(Color.BLACK)
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                val d = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 20f
                    colors = intArrayOf(Color.parseColor("#00e5ff"), Color.parseColor("#0055ff"))
                    gradientType = GradientDrawable.LINEAR_GRADIENT
                    orientation = GradientDrawable.Orientation.TL_BR
                }
                background = d
                layoutParams = LinearLayout.LayoutParams(120, 120).apply { marginEnd = 20 }
                elevation = 8f
            }

            // Answer text
            val ansText = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL

                addView(TextView(ctx).apply {
                    this.text = "الإجابة الصحيحة"
                    textSize = 10f
                    setTextColor(Color.parseColor("#5a7099"))
                    letterSpacing = 0.1f
                })
                addView(TextView(ctx).apply {
                    this.text = text
                    textSize = 16f
                    setTextColor(Color.parseColor("#00ff88"))
                    typeface = Typeface.DEFAULT_BOLD
                    setPadding(0, 6, 0, 0)
                })
            }

            topRow.addView(letterBox)
            topRow.addView(ansText)

            // ── Explanation ──
            val explainView = TextView(ctx).apply {
                this.text = explain
                textSize = 12f
                setTextColor(Color.parseColor("#5a7099"))
                setPadding(0, 0, 12, 12)
                val leftBorder = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    setColor(Color.TRANSPARENT)
                }
                // Simple right border effect using padding
                background = leftBorder
            }

            // ── Confidence bar ──
            val confRow = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 4, 0, 12)

                addView(TextView(ctx).apply {
                    this.text = "الثقة"
                    textSize = 10f
                    setTextColor(Color.parseColor("#3a4a6a"))
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { marginEnd = 12 }
                })

                val barBg = FrameLayout(ctx).apply {
                    layoutParams = LinearLayout.LayoutParams(0,
                        16, 1f).apply { marginEnd = 12 }
                    val bgD = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = 8f
                        setColor(Color.parseColor("#161d35"))
                    }
                    background = bgD
                }
                val barFill = View(ctx).apply {
                    val fillD = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = 8f
                        colors = intArrayOf(Color.parseColor("#00e5ff"), Color.parseColor("#00ff88"))
                        orientation = GradientDrawable.Orientation.LEFT_RIGHT
                    }
                    background = fillD
                    layoutParams = FrameLayout.LayoutParams(
                        (3 * confidence).coerceAtMost(300), // approx
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                }
                barBg.addView(barFill)
                addView(barBg)

                addView(TextView(ctx).apply {
                    this.text = "$confidence%"
                    textSize = 11f
                    setTextColor(Color.parseColor("#00e5ff"))
                    typeface = Typeface.DEFAULT_BOLD
                })
            }

            // ── Close button ──
            val closeBtn = Button(ctx).apply {
                this.text = "✕ إغلاق"
                textSize = 13f
                setTextColor(Color.parseColor("#5a7099"))
                setBackgroundColor(Color.parseColor("#0c1020"))
                isAllCaps = false
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                setOnClickListener { dismissAnswerView() }
            }

            card.addView(topRow)
            card.addView(explainView)
            card.addView(confRow)
            card.addView(closeBtn)

            // Window params for the card
            val display = wm.defaultDisplay
            val size = Point()
            display.getSize(size)
            val cardWidth = (size.x * 0.88f).toInt()

            val cardParams = WindowManager.LayoutParams(
                cardWidth,
                WindowManager.LayoutParams.WRAP_CONTENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                y = 80
            }

            card.tag = "answer_card"
            wm.addView(card, cardParams)

            // Vibrate
            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 100, 50, 100), -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(longArrayOf(0, 100, 50, 100), -1)
            }
        }
    }

    private var answerCardView: View? = null

    fun dismissAnswerView() {
        try {
            answerCardView?.let { wm.removeView(it) }
        } catch (e: Exception) { /* already removed */ }
        answerCardView = null
    }

    fun showPulse() {
        // Flash the bubble to indicate found but couldn't click
        Handler(Looper.getMainLooper()).post {
            val v = overlayView as? TextView ?: return@post
            v.text = "✓"
            Handler(Looper.getMainLooper()).postDelayed({ v.text = "⚡" }, 1500)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "حلّال",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "خدمة حلّال الذكي" }
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("⚡ حلّال شغّال")
            .setContentText("يراقب تطبيق برق ويجيب تلقائياً")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onBind(intent: Intent?) = null

    override fun onDestroy() {
        instance = null
        try { overlayView?.let { wm.removeView(it) } } catch (e: Exception) {}
        dismissAnswerView()
        super.onDestroy()
    }
}
