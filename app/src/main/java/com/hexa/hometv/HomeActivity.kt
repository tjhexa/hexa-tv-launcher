package com.hexa.hometv

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioAttributes
import android.media.SoundPool
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.os.Handler
import android.os.Looper
import android.text.format.DateFormat
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HomeActivity : Activity() {

    companion object {
        private const val ALL_PKG = "__all__"
    }

    private lateinit var root: FrameLayout
    private lateinit var videoSurface: SurfaceView
    private var videoBg: VideoBackground? = null
    private var videoMode = false
    private var videoFrameShown = false
    private var stillsVisible = false
    private lateinit var bgA: ImageView
    private lateinit var bgB: ImageView
    private var activeBg = 0
    private val bgBitmaps = arrayOfNulls<Bitmap>(2)
    private lateinit var topScrim: View
    private lateinit var bottomScrim: View
    private lateinit var wordmark: ShimmerTextView
    private lateinit var clockWrap: LinearLayout
    private lateinit var clock: TextView
    private lateinit var date: TextView
    private lateinit var perimeter: PerimeterView
    private var allApps: List<AppEntry> = emptyList()
    private var allAppsOverlay: AllAppsOverlay? = null
    private var wallpaperPicker: WallpaperPicker? = null
    private var quickMenu: View? = null
    private lateinit var moveHint: TextView
    private lateinit var sounds: SoundPool
    private var navSoundId = 0
    private var launchSoundId = 0
    private lateinit var prefs: Prefs

    private var settingsPanel: View? = null

    private val handler = Handler(Looper.getMainLooper())
    private val clockTick = object : Runnable {
        override fun run() {
            updateClock()
            val now = System.currentTimeMillis()
            handler.postDelayed(this, 60_000L - (now % 60_000L) + 200L)
        }
    }

    private val bgTick = object : Runnable {
        override fun run() {
            nextWallpaper()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)

        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        applyImmersive()

        val d = resources.displayMetrics.density

        root = FrameLayout(this)
        root.setBackgroundColor(Color.BLACK)

        // The video surface renders behind the window's views. It stays gone
        // while a still wallpaper is active so no decoder is allocated.
        videoSurface = SurfaceView(this).apply {
            visibility = View.GONE
        }
        root.addView(videoSurface, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))

        bgA = ImageView(this).apply {
            // no backing colour: an empty bgA must stay transparent so the
            // video surface can show through when the video wallpaper is used
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
        bgB = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            alpha = 0f
        }
        root.addView(bgA, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))
        root.addView(bgB, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))

        // soft scrims so white text stays readable on bright wallpapers
        topScrim = View(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0x7A000000, 0x00000000)
            )
        }
        root.addView(
            topScrim,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (170 * d).toInt(), Gravity.TOP
            )
        )

        bottomScrim = View(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0x00000000, 0xA8000000.toInt())
            )
        }
        root.addView(
            bottomScrim,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (150 * d).toInt(), Gravity.BOTTOM
            )
        )

        clockWrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }

        clock = TextView(this).apply {
            typeface = Typeface.create("serif", Typeface.NORMAL)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 44f)
            setTextColor(0xFFF6ECD8.toInt())
            letterSpacing = 0.07f
            gravity = Gravity.CENTER
            includeFontPadding = false
            setShadowLayer(14f, 0f, 0f, 0x66FFD9A0)
        }
        date = TextView(this).apply {
            typeface = Typeface.create("serif", Typeface.NORMAL)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(0xFFF6ECD8.toInt())
            letterSpacing = 0.40f
            gravity = Gravity.CENTER
            setPadding(0, (6 * d).toInt(), 0, 0)
            setShadowLayer(8f, 0f, 0f, 0xAA000000.toInt())
        }
        clockWrap.addView(clock)
        clockWrap.addView(date)

        val clockLp = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.CENTER_HORIZONTAL
        )
        clockLp.topMargin = (22 * d).toInt()
        root.addView(clockWrap, clockLp)

        sounds = SoundPool.Builder()
            .setMaxStreams(3)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .build()
        navSoundId = sounds.load(this, R.raw.nav_shimmer, 1)
        launchSoundId = sounds.load(this, R.raw.launch_pluck, 1)

        perimeter = PerimeterView(this).apply {
            isSoundEffectsEnabled = false
            onLaunch = { handleLaunch(it) }
            onHide = { prefs.hide(it.pkg) }
            onNavSound = { playSound(navSoundId, 0.40f) }
            onSelectSound = { playSound(launchSoundId, 0.50f) }
            onOrderChanged = { order ->
                prefs.appOrder = order
                prefs.sortMode = 0
            }
            onMoveMode = { on ->
                moveHint.visibility = if (on) View.VISIBLE else View.GONE
            }
            onQuickMenu = { showQuickMenu() }
        }
        root.addView(
            perimeter,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        moveHint = TextView(this).apply {
            text = "MOVE  ·  ←/→ to place  ·  OK to finish  ·  MENU to hide"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(0xFFF6ECD8.toInt())
            letterSpacing = 0.12f
            gravity = Gravity.CENTER
            setBackgroundColor(0xAA0B0A12.toInt())
            setPadding((24 * d).toInt(), (10 * d).toInt(), (24 * d).toInt(), (10 * d).toInt())
            visibility = View.GONE
        }
        val hintLp = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        )
        hintLp.bottomMargin = (176 * d).toInt()
        root.addView(moveHint, hintLp)

        val wordmarkFont = try {
            Typeface.createFromAsset(assets, "fonts/cinzel.ttf")
        } catch (e: Exception) {
            Typeface.create("serif", Typeface.NORMAL)
        }
        wordmark = ShimmerTextView(this).apply {
            text = "TJHEXA"
            typeface = wordmarkFont
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            letterSpacing = 0.42f
            setTextColor(0xFFE9C887.toInt())
            gravity = Gravity.END
            includeFontPadding = false
        }
        val wordmarkLp = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.END
        )
        wordmarkLp.topMargin = (26 * d).toInt()
        wordmarkLp.rightMargin = (38 * d).toInt()
        root.addView(wordmark, wordmarkLp)

        val gearRow = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        val gear = ImageView(this).apply {
            setImageBitmap(settingsIcon((26 * d).toInt()))
        }
        val gearLabel = TextView(this).apply {
            text = "SETTINGS"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 8f)
            setTextColor(0xFFE9C887.toInt())
            letterSpacing = 0.18f
        }
        gearRow.addView(gear)
        gearRow.addView(gearLabel)
        val gearLp = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.START
        )
        gearLp.topMargin = (16 * d).toInt()
        gearLp.leftMargin = (30 * d).toInt()
        root.addView(gearRow, gearLp)

        setContentView(root)

        updateClock()
        handler.post(clockTick)
        loadApps()
        applyClockSize()
        applyWordmark()
        showWallpaper(prefs.wallIndex)
        scheduleWallpaper()
    }

    private fun applyImmersive() {
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applyImmersive()
    }

    override fun onResume() {
        super.onResume()
        handler.removeCallbacks(clockTick)
        handler.post(clockTick)
        val interval = prefs.wallIntervalMin * 60_000L
        if (interval > 0 && prefs.lastRotate > 0L &&
            System.currentTimeMillis() - prefs.lastRotate >= interval
        ) {
            nextWallpaper()
        } else {
            scheduleWallpaper()
        }
        if (videoMode) videoBg?.play()
    }

    override fun onPause() {
        handler.removeCallbacks(clockTick)
        videoBg?.pause()
        super.onPause()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        videoBg?.release()
        videoBg = null
        sounds.release()
        bgBitmaps.forEach { it?.recycle() }
        bgBitmaps.fill(null)
        super.onDestroy()
    }

    private fun showWallpaper(index: Int) {
        if (Wallpapers.isVideo(this, index)) {
            showVideoWallpaper()
        } else {
            videoMode = false
            Thread {
                applyStill(Wallpapers.decode(this, index))
            }.start()
        }
    }

    private fun nextWallpaper() {
        val total = Wallpapers.count(this) +
                if (Wallpapers.hasVideo(this)) 1 else 0
        if (total <= 0) return
        val skipped = prefs.skippedWalls
        var idx = prefs.wallIndex.coerceIn(0, total - 1)
        var tries = 0
        do {
            idx = (idx + 1) % total
            tries++
        } while (idx in skipped && tries <= total)
        prefs.wallIndex = idx
        prefs.lastRotate = System.currentTimeMillis()
        showWallpaper(idx)
        scheduleWallpaper()
    }

    private fun scheduleWallpaper() {
        handler.removeCallbacks(bgTick)
        val mins = prefs.wallIntervalMin
        if (mins <= 0) return
        val elapsed = System.currentTimeMillis() - prefs.lastRotate
        val delay = (mins * 60_000L - elapsed).coerceAtLeast(1_000L)
        handler.postDelayed(bgTick, delay)
    }

    private fun pickWallpaper(index: Int) {
        prefs.wallIndex = index
        prefs.lastRotate = System.currentTimeMillis()
        showWallpaper(index)
        scheduleWallpaper()
    }

    private fun showVideoWallpaper() {
        videoMode = true
        videoFrameShown = false
        if (videoBg == null) {
            videoBg = VideoBackground(this, videoSurface, Wallpapers.videoAssetPath()) {
                onVideoFirstFrame()
            }
        }
        if (videoSurface.visibility != View.VISIBLE) videoSurface.visibility = View.VISIBLE
        videoBg?.play()

        // If nothing is on screen yet (cold start on the video), put the poster
        // up so the launcher never opens on a black frame.
        if (!stillsVisible) {
            Thread {
                val poster = Wallpapers.videoPoster(this)
                if (poster != null) {
                    runOnUiThread {
                        if (videoMode && !videoFrameShown) applyStill(poster) else poster.recycle()
                    }
                }
            }.start()
        }
    }

    private fun onVideoFirstFrame() {
        runOnUiThread {
            if (!videoMode || videoFrameShown) return@runOnUiThread
            videoFrameShown = true
            if (!stillsVisible) return@runOnUiThread
            // crossfade from the still/poster down to the moving video
            bgA.animate().cancel()
            bgB.animate().cancel()
            bgA.animate().alpha(0f).setDuration(450).withEndAction { clearStills() }.start()
            bgB.animate().alpha(0f).setDuration(450).start()
        }
    }

    private fun clearStills() {
        if (!videoMode) return
        bgA.setImageDrawable(null)
        bgB.setImageDrawable(null)
        bgBitmaps.forEach { it?.recycle() }
        bgBitmaps.fill(null)
        bgA.alpha = 1f
        bgB.alpha = 0f
        // Hide both layers while the video is active; the video surface sits
        // behind the window and must not be covered by stale views.
        bgA.visibility = View.INVISIBLE
        bgB.visibility = View.INVISIBLE
        activeBg = 0
        stillsVisible = false
    }

    private fun stopVideo() {
        videoBg?.release()
        videoBg = null
        videoSurface.visibility = View.GONE
        videoFrameShown = false
    }

    private fun applyStill(bmp: Bitmap?) {
        if (bmp == null) return
        runOnUiThread {
            if (isFinishing || isDestroyed) {
                bmp.recycle()
                return@runOnUiThread
            }
            val wasHidden = !stillsVisible
            bgA.animate().cancel()
            bgB.animate().cancel()
            bgA.visibility = View.VISIBLE
            bgB.visibility = View.VISIBLE
            val slot = if (wasHidden) 1 else 1 - activeBg
            val target = if (slot == 0) bgA else bgB
            val old = bgBitmaps[slot]
            bgBitmaps[slot] = bmp
            target.setImageBitmap(bmp)
            if (wasHidden) {
                // fading back in over the video surface (or at cold start)
                target.alpha = 0f
                target.animate().alpha(1f).setDuration(450).withEndAction {
                    if (!videoMode) stopVideo()
                }.start()
            } else if (slot == 1) {
                // bgB is the top layer: fade it in
                target.alpha = 0f
                target.animate().alpha(1f).setDuration(450).start()
            } else {
                // bgA is underneath: reveal it by fading the top layer out
                bgA.alpha = 1f
                bgB.animate().alpha(0f).setDuration(450).start()
            }
            activeBg = slot
            if (old != null && old !== bmp) old.recycle()
            stillsVisible = true
            applyScrims(bmp)
        }
    }

    private fun applyScrims(bmp: Bitmap) {
        val small = Bitmap.createScaledBitmap(bmp, 32, 18, true)
        var topSum = 0L
        var botSum = 0L
        for (y in 0 until 18) {
            for (x in 0 until 32) {
                val c = small.getPixel(x, y)
                val l = (Color.red(c) * 299 + Color.green(c) * 587 + Color.blue(c) * 114) / 1000
                if (y < 5) topSum += l else if (y >= 13) botSum += l
            }
        }
        small.recycle()
        val topAvg = topSum / (32.0 * 5.0) / 255.0
        val botAvg = botSum / (32.0 * 5.0) / 255.0
        val topAlpha = (0x28 + topAvg * 0x68).toInt().coerceIn(0x28, 0x90)
        val botAlpha = (0x70 + botAvg * 0x60).toInt().coerceIn(0x70, 0xD0)
        topScrim.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf((topAlpha shl 24), 0)
        )
        bottomScrim.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(0, (botAlpha shl 24))
        )
    }

    private fun applyClockSize() {
        val sp = when (prefs.clockSize) {
            0 -> 38f
            2 -> 52f
            else -> 44f
        }
        clock.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
    }

    private fun applyWordmark() {
        wordmark.visibility = if (prefs.wordmarkOn) View.VISIBLE else View.GONE
        val colors = when (prefs.wordmarkColor) {
            1 -> intArrayOf(0xFFF3EAD8.toInt(), 0xFFFFFFFF.toInt(), 0xFFF3EAD8.toInt())
            2 -> intArrayOf(0xFFC9D2DC.toInt(), 0xFFFFFFFF.toInt(), 0xFFC9D2DC.toInt())
            else -> intArrayOf(0xFFE9C887.toInt(), 0xFFFFF4D6.toInt(), 0xFFE9C887.toInt())
        }
        wordmark.setShimmerColors(colors[0], colors[1], colors[2])
    }

    private fun updateClock() {
        val now = Date()
        val h24 = prefs.clock24 ?: DateFormat.is24HourFormat(this)
        val pattern = if (h24) "HH:mm" else "h:mm"
        clock.text = SimpleDateFormat(pattern, Locale.getDefault()).format(now)
        date.text = SimpleDateFormat("EEEE · d MMMM", Locale.getDefault())
            .format(now)
            .uppercase(Locale.getDefault())
        date.visibility = if (prefs.showDate) View.VISIBLE else View.GONE

        val d = resources.displayMetrics.density
        clock.animate().cancel()
        date.animate().cancel()
        clock.alpha = 0.45f
        clock.translationY = 7f * d
        clock.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(460)
            .setInterpolator(DecelerateInterpolator())
            .start()
        date.alpha = 0.30f
        date.animate()
            .alpha(1f)
            .setDuration(520)
            .setStartDelay(70)
            .start()
    }

    private fun loadApps() {
        Thread {
            val iconPx = (64 * resources.displayMetrics.density).toInt()
            val list = AppRepo.load(
                this,
                prefs.hidden,
                iconPx,
                prefs.appOrder,
                prefs.sortMode,
                prefs.launchCounts,
                prefs.lastUsed
            )
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                allApps = list
                val favs = list.filter { prefs.favorites.contains(it.pkg) }
                val ring = (if (favs.isEmpty()) list else favs) + allAppsEntry(iconPx)
                perimeter.setApps(ring)
                if (settingsPanel == null && allAppsOverlay == null && wallpaperPicker == null) {
                    perimeter.requestFocus()
                }
            }
        }.start()
    }

    private fun handleLaunch(e: AppEntry) {
        if (e.pkg == ALL_PKG) {
            showAllApps()
            return
        }
        prefs.recordLaunch(e.pkg)
        launch(e)
    }

    private fun allAppsEntry(iconPx: Int): AppEntry = AppEntry(
        ALL_PKG,
        ComponentName(this, HomeActivity::class.java),
        "All apps",
        allAppsIcon(iconPx)
    )

    private fun settingsIcon(size: Int): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = 0xFFE9C887.toInt()
        p.strokeWidth = size * 0.085f
        p.style = Paint.Style.STROKE
        val cx = size / 2f
        val cy = size / 2f
        c.drawCircle(cx, cy, size * 0.25f, p)
        p.style = Paint.Style.FILL
        for (i in 0 until 8) {
            c.save()
            c.rotate(i * 45f, cx, cy)
            c.drawRoundRect(
                cx - size * 0.055f, cy - size * 0.44f,
                cx + size * 0.055f, cy - size * 0.30f,
                size * 0.025f, size * 0.025f, p
            )
            c.restore()
        }
        return bmp
    }

    private fun allAppsIcon(size: Int): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = 0xFFE9C887.toInt()
        val pad = size * 0.14f
        val gap = size * 0.07f
        val cell = (size - 2 * pad - 2 * gap) / 3f
        for (row in 0..2) {
            for (col in 0..2) {
                val left = pad + col * (cell + gap)
                val top = pad + row * (cell + gap)
                c.drawRoundRect(left, top, left + cell, top + cell,
                    size * 0.04f, size * 0.04f, p)
            }
        }
        return bmp
    }

    private fun showAllApps() {
        if (allAppsOverlay != null) return
        val overlay = AllAppsOverlay(
            this,
            allApps,
            prefs.favorites,
            onLaunch = { app ->
                closeAllApps()
                prefs.recordLaunch(app.pkg)
                launch(app)
            },
            onToggleFavorite = { app, nowFav ->
                val f = prefs.favorites.toMutableSet()
                if (nowFav) f.add(app.pkg) else f.remove(app.pkg)
                prefs.favorites = f
            }
        )
        allAppsOverlay = overlay
        root.addView(overlay, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))
        overlay.focusFirst()
    }

    private fun closeAllApps() {
        allAppsOverlay?.let { root.removeView(it) }
        allAppsOverlay = null
        loadApps()
        perimeter.post { perimeter.requestFocus() }
    }

    private fun launch(e: AppEntry) {
        try {
            val i = Intent(Intent.ACTION_MAIN).apply {
                component = e.component
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            }
            startActivity(i)
        } catch (ex: Exception) {
            Toast.makeText(this, "Cannot launch ${e.label}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun playSound(id: Int, vol: Float) {
        if (id != 0) sounds.play(id, vol, vol, 1, 0, 1f)
    }

    private fun intervalLabel(): String = when (prefs.wallIntervalMin) {
        30 -> "30 minutes"
        60 -> "1 hour"
        180 -> "3 hours"
        360 -> "6 hours"
        1440 -> "Daily"
        else -> "Never"
    }

    private fun clockSizeLabel(): String = when (prefs.clockSize) {
        0 -> "Small"
        2 -> "Large"
        else -> "Medium"
    }

    private fun wordmarkColorLabel(): String = when (prefs.wordmarkColor) {
        1 -> "Ivory"
        2 -> "Silver"
        else -> "Gold"
    }

    private fun sortLabel(): String = when (prefs.sortMode) {
        1 -> "A–Z"
        2 -> "Most used"
        3 -> "Recent"
        else -> "Manual"
    }

    private fun showQuickMenu() {
        if (quickMenu != null || settingsPanel != null || allAppsOverlay != null ||
            wallpaperPicker != null
        ) return
        val d = resources.displayMetrics.density

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xF20B0A12.toInt())
            setPadding((24 * d).toInt(), (18 * d).toInt(), (24 * d).toInt(), (18 * d).toInt())
            isFocusable = true
        }

        val title = TextView(this).apply {
            text = "QUICK MENU"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTextColor(0xFFE9C887.toInt())
            letterSpacing = 0.28f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, (14 * d).toInt())
        }
        panel.addView(title)

        fun item(label: () -> String, action: () -> Unit) {
            val tv = TextView(this).apply {
                text = label()
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                setTextColor(0xFFF3EAD8.toInt())
                gravity = Gravity.CENTER
                isFocusable = true
                isFocusableInTouchMode = true
                setBackgroundResource(R.drawable.option_bg)
                setPadding((18 * d).toInt(), (10 * d).toInt(), (18 * d).toInt(), (10 * d).toInt())
            }
            val lp = LinearLayout.LayoutParams(
                (330 * d).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            lp.bottomMargin = (8 * d).toInt()
            tv.layoutParams = lp
            tv.setOnClickListener { action() }
            panel.addView(tv)
        }

        item({ "Settings" }) {
            closeQuickMenu()
            showSettings()
        }
        item({ "TV settings" }) {
            closeQuickMenu()
            openIntent(Intent(Settings.ACTION_SETTINGS))
        }
        item({ "Network settings" }) {
            closeQuickMenu()
            try {
                openIntent(Intent(Settings.ACTION_WIFI_SETTINGS))
            } catch (e: Exception) {
                openIntent(Intent(Settings.ACTION_WIRELESS_SETTINGS))
            }
        }
        item({ "Live TV" }) {
            closeQuickMenu()
            val i = packageManager.getLaunchIntentForPackage("com.mediatek.wwtv.tvcenter")
            if (i != null) openIntent(i)
            else Toast.makeText(this, "Live TV not available", Toast.LENGTH_SHORT).show()
        }
        val current = perimeter.currentApp()
        item({
            "App info: ${if (current == null || current.pkg == ALL_PKG) "—" else current.label}"
        }) {
            closeQuickMenu()
            val pkg = current?.pkg
            if (pkg != null && pkg != ALL_PKG) {
                openIntent(Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:$pkg")
                ))
            }
        }
        item({ "Close" }) { closeQuickMenu() }

        quickMenu = panel
        root.addView(panel, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.START
        ).apply {
            topMargin = (72 * d).toInt()
            leftMargin = (24 * d).toInt()
        })
        panel.post { panel.getChildAt(1)?.requestFocus() }
    }

    private fun closeQuickMenu() {
        quickMenu?.let { root.removeView(it) }
        quickMenu = null
        perimeter.post { perimeter.requestFocus() }
    }

    private fun showSettings() {
        if (settingsPanel != null) return
        val d = resources.displayMetrics.density

        val overlay = FrameLayout(this).apply {
            setBackgroundColor(0x99000000.toInt())
            isFocusable = true
        }
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = 18 * d
                setColor(0xF70B0A12.toInt())
                setStroke((1 * d).toInt(), 0x33E9C887)
            }
            setPadding((24 * d).toInt(), (20 * d).toInt(), (24 * d).toInt(), (20 * d).toInt())
            isFocusable = false
        }
        val title = TextView(this).apply {
            text = "SETTINGS"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(0xFFE9C887.toInt())
            letterSpacing = 0.32f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, (14 * d).toInt())
        }
        card.addView(title)

        val scroll = ScrollView(this).apply {
            isFillViewport = false
            isFocusable = false
            setBackgroundColor(Color.TRANSPARENT)
        }
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            isFocusable = false
        }
        scroll.addView(panel, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        fun option(label: () -> String, onClick: () -> Unit) {
            val tv = TextView(this).apply {
                text = label()
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
                setTextColor(0xFFF3EAD8.toInt())
                gravity = Gravity.CENTER
                isFocusable = true
                isFocusableInTouchMode = true
                setBackgroundResource(R.drawable.option_bg)
                setPadding((24 * d).toInt(), (12 * d).toInt(), (24 * d).toInt(), (12 * d).toInt())
            }
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            lp.bottomMargin = (10 * d).toInt()
            tv.layoutParams = lp
            tv.setOnClickListener {
                onClick()
                tv.text = label()
            }
            tv.setOnFocusChangeListener { v, hasFocus ->
                if (hasFocus) {
                    scroll.smoothScrollTo(0, (v.top - scroll.height / 3).coerceAtLeast(0))
                }
            }
            panel.addView(tv)
        }

        option({ "New background now" }) { nextWallpaper() }
        option({ "Wallpaper: ${intervalLabel()}" }) {
            val values = intArrayOf(30, 60, 180, 360, 1440, 0)
            val i = values.indexOf(prefs.wallIntervalMin)
            prefs.wallIntervalMin = values[(i + 1 + values.size) % values.size]
            scheduleWallpaper()
        }
        option({ "Pick wallpaper" }) { showWallpaperPicker() }
        option({ "Skip current wallpaper" }) {
            prefs.toggleSkipWall(prefs.wallIndex)
            if (prefs.skippedWalls.contains(prefs.wallIndex)) nextWallpaper()
        }
        option({ "Reset skipped wallpapers" }) {
            prefs.skippedWalls = emptySet()
        }
        option({
            val h24 = prefs.clock24 ?: DateFormat.is24HourFormat(this)
            "Clock: ${if (h24) "24-hour" else "12-hour"}"
        }) {
            val h24 = prefs.clock24 ?: DateFormat.is24HourFormat(this)
            prefs.clock24 = !h24
            updateClock()
        }
        option({ "Clock size: ${clockSizeLabel()}" }) {
            prefs.clockSize = (prefs.clockSize + 1) % 3
            applyClockSize()
        }
        option({ "Date: ${if (prefs.showDate) "On" else "Off"}" }) {
            prefs.showDate = !prefs.showDate
            updateClock()
        }
        option({ "Wordmark: ${if (prefs.wordmarkOn) "On" else "Off"}" }) {
            prefs.wordmarkOn = !prefs.wordmarkOn
            applyWordmark()
        }
        option({ "Wordmark colour: ${wordmarkColorLabel()}" }) {
            prefs.wordmarkColor = (prefs.wordmarkColor + 1) % 3
            applyWordmark()
        }
        option({ "Sort: ${sortLabel()}" }) {
            prefs.sortMode = (prefs.sortMode + 1) % 4
            loadApps()
        }
        option({ "Unhide all apps" }) {
            prefs.unhideAll()
            loadApps()
        }
        option({ "Clear favourites" }) {
            prefs.favorites = emptySet()
            loadApps()
        }
        option({ "TV settings" }) {
            openIntent(Intent(Settings.ACTION_SETTINGS))
        }
        option({ "Network settings" }) {
            try {
                openIntent(Intent(Settings.ACTION_WIFI_SETTINGS))
            } catch (e: Exception) {
                openIntent(Intent(Settings.ACTION_WIRELESS_SETTINGS))
            }
        }
        option({ "Live TV" }) {
            val i = packageManager.getLaunchIntentForPackage("com.mediatek.wwtv.tvcenter")
            if (i != null) openIntent(i)
            else Toast.makeText(this, "Live TV not available", Toast.LENGTH_SHORT).show()
        }
        val current = perimeter.currentApp()
        option({
            "App info: ${if (current == null || current.pkg == ALL_PKG) "—" else current.label}"
        }) {
            val pkg = current?.pkg
            if (pkg != null && pkg != ALL_PKG) {
                openIntent(Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:$pkg")
                ))
            }
        }
        option({ "Close" }) { hideSettings() }

        val scrollHeight = (resources.displayMetrics.heightPixels * 0.56f).toInt()
        card.addView(scroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, scrollHeight
        ))
        overlay.addView(card, FrameLayout.LayoutParams(
            (560 * d).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER
        ))
        settingsPanel = overlay
        root.addView(overlay, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))
        scroll.post { panel.getChildAt(0)?.requestFocus() }
    }

    private fun openIntent(intent: Intent) {
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Not available on this TV", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showWallpaperPicker() {
        if (wallpaperPicker != null) return
        val n = Wallpapers.count(this)
        val videoPos = if (Wallpapers.hasVideo(this)) n else -1
        if (n <= 0 && videoPos < 0) return
        val picker = WallpaperPicker(
            this,
            n,
            videoPos,
            prefs.skippedWalls,
            prefs.wallIndex,
            onPick = { idx ->
                pickWallpaper(idx)
                closeWallpaperPicker()
                hideSettings()
            },
            onToggleSkip = { idx, skipped ->
                val s = prefs.skippedWalls.toMutableSet()
                if (skipped) s.add(idx) else s.remove(idx)
                prefs.skippedWalls = s
            }
        )
        wallpaperPicker = picker
        root.addView(picker, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))
        picker.focusFirst()
    }

    private fun closeWallpaperPicker() {
        wallpaperPicker?.let { root.removeView(it) }
        wallpaperPicker = null
        perimeter.post { perimeter.requestFocus() }
    }

    private fun hideSettings() {
        settingsPanel?.let { root.removeView(it) }
        settingsPanel = null
        perimeter.post { perimeter.requestFocus() }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (quickMenu != null) {
            if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_MENU) {
                closeQuickMenu()
                return true
            }
        }
        if (allAppsOverlay != null) {
            if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_MENU) {
                closeAllApps()
                return true
            }
        }
        if (wallpaperPicker != null) {
            if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_MENU) {
                closeWallpaperPicker()
                return true
            }
        }
        if (keyCode == KeyEvent.KEYCODE_MENU || keyCode == KeyEvent.KEYCODE_SETTINGS) {
            if (settingsPanel == null) showSettings() else hideSettings()
            return true
        }
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (settingsPanel != null) {
                hideSettings()
                return true
            }
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    @Deprecated("Launcher stays on home")
    override fun onBackPressed() {
        // intentionally do nothing
    }
}
