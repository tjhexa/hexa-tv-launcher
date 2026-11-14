package com.hexa.hometv

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.service.dreams.DreamService
import android.text.format.DateFormat
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Ambient screensaver: current wallpaper, large clock, date and wordmark. */
class HomeDreamService : DreamService() {

    private val handler = Handler(Looper.getMainLooper())
    private var clock: TextView? = null
    private var date: TextView? = null
    private var image: ImageView? = null

    private val tick = object : Runnable {
        override fun run() {
            updateClock()
            val now = System.currentTimeMillis()
            handler.postDelayed(this, 60_000L - (now % 60_000L) + 200L)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true

        val prefs = Prefs(this)
        val root = FrameLayout(this)
        root.setBackgroundColor(Color.BLACK)

        image = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
        root.addView(image, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))

        val topScrim = View(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0x66000000, 0x00000000)
            )
        }
        root.addView(topScrim, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, (180 * dp()).toInt(), Gravity.TOP
        ))
        val bottomScrim = View(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0x00000000, 0x88000000.toInt())
            )
        }
        root.addView(bottomScrim, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, (160 * dp()).toInt(), Gravity.BOTTOM
        ))

        val wrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        clock = TextView(this).apply {
            typeface = Typeface.create("serif", Typeface.NORMAL)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 68f)
            setTextColor(0xFFF6ECD8.toInt())
            letterSpacing = 0.07f
            gravity = Gravity.CENTER
            includeFontPadding = false
            setShadowLayer(16f, 0f, 0f, 0x66FFD9A0)
        }
        date = TextView(this).apply {
            typeface = Typeface.create("serif", Typeface.NORMAL)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(0xFFF6ECD8.toInt())
            letterSpacing = 0.40f
            gravity = Gravity.CENTER
            setPadding(0, (8 * dp()).toInt(), 0, 0)
        }
        wrap.addView(clock)
        wrap.addView(date)
        val wrapLp = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.CENTER_HORIZONTAL
        )
        wrapLp.topMargin = (90 * dp()).toInt()
        root.addView(wrap, wrapLp)

        val word = TextView(this).apply {
            text = "TJHEXA"
            typeface = Typeface.create("serif", Typeface.NORMAL)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            letterSpacing = 0.42f
            setTextColor(0xFFE9C887.toInt())
        }
        root.addView(word, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.END
        ).apply {
            topMargin = (34 * dp()).toInt()
            rightMargin = (42 * dp()).toInt()
        })

        setContentView(root)

        Thread {
            val bmp = Wallpapers.decode(this, prefs.wallIndex)
            handler.post { image?.setImageBitmap(bmp) }
        }.start()

        updateClock()
        handler.post(tick)
    }

    private fun updateClock() {
        val now = Date()
        val h24 = DateFormat.is24HourFormat(this)
        clock?.text = SimpleDateFormat(if (h24) "HH:mm" else "h:mm", Locale.getDefault()).format(now)
        date?.text = SimpleDateFormat("EEEE · d MMMM", Locale.getDefault())
            .format(now)
            .uppercase(Locale.getDefault())
    }

    private fun dp(): Float = resources.displayMetrics.density

    override fun onDreamingStopped() {
        handler.removeCallbacksAndMessages(null)
        image?.setImageDrawable(null)
        super.onDreamingStopped()
    }
}
