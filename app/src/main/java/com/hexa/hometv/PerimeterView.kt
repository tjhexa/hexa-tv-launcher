package com.hexa.hometv

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView

/**
 * Apps laid out around the screen edges — left column, bottom row, right
 * column. The app ring rotates under a fixed anchor at the bottom centre, so
 * icons flow left -> bottom -> right. Long-press on OK enters move mode.
 */
class PerimeterView(context: Context) : ViewGroup(context) {

    private class Slot(val x: Float, val y: Float)

    private class Tile(
        val root: FrameLayout,
        val ring: View,
        val icon: ImageView,
        val label: TextView
    ) {
        var app: AppEntry? = null
    }

    private val items = ArrayList<AppEntry>()
    private val slots = ArrayList<Slot>()
    private val tiles = ArrayList<Tile>()

    private var focus = 0
    private var anchor = 0
    private var moving = false
    private var centerDown = false
    private var longFired = false

    private val handler = Handler(Looper.getMainLooper())
    private val longPress = Runnable { startMove() }

    var onLaunch: ((AppEntry) -> Unit)? = null
    var onHide: ((AppEntry) -> Unit)? = null
    var onNavSound: (() -> Unit)? = null
    var onSelectSound: (() -> Unit)? = null
    var onOrderChanged: ((List<String>) -> Unit)? = null
    var onMoveMode: ((Boolean) -> Unit)? = null
    var onQuickMenu: (() -> Unit)? = null

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        isSoundEffectsEnabled = false
        setBackgroundColor(Color.TRANSPARENT)
        setWillNotDraw(true)
    }

    fun setApps(list: List<AppEntry>) {
        items.clear()
        items.addAll(list)
        if (items.isEmpty()) {
            focus = 0
        } else {
            focus = focus.coerceIn(0, items.size - 1)
        }
        rebuildTiles()
        applyPositions(false)
    }

    fun currentApp(): AppEntry? = items.getOrNull(focus)

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        computeSlots(w, h)
        applyPositions(false)
    }

    private fun computeSlots(w: Int, h: Int) {
        slots.clear()
        if (w <= 0 || h <= 0) return
        val d = resources.displayMetrics.density
        val itemW = 56 * d
        val itemH = 68 * d
        val leftX = 18 * d
        val rightX = w - 18 * d - itemW
        val topY = 70 * d
        val bottomY = h - 16 * d - itemH
        val leftSlots = 6
        val rightSlots = 6
        val avail = rightX - leftX
        val bottomPositions = maxOf(3, (avail / (itemW + 10 * d)).toInt() + 1)
        val pitchX = if (bottomPositions > 1) avail / (bottomPositions - 1) else 0f
        val pitchY = (bottomY - topY) / (leftSlots - 1)

        for (i in 0 until leftSlots) {
            slots.add(Slot(leftX, topY + i * pitchY))
        }
        for (j in 1 until bottomPositions - 1) {
            slots.add(Slot(leftX + j * pitchX, bottomY))
        }
        for (k in 0 until rightSlots) {
            slots.add(Slot(rightX, bottomY - k * pitchY))
        }
        anchor = leftSlots + ((bottomPositions - 2) / 2)
    }

    private fun slotOf(index: Int): Int {
        val n = items.size
        if (n == 0 || slots.isEmpty()) return -1
        val raw = anchor - index + focus
        return if (n <= slots.size) {
            ((raw % slots.size) + slots.size) % slots.size
        } else {
            ((raw % n) + n) % n
        }
    }

    private fun rebuildTiles() {
        val d = resources.displayMetrics.density
        val itemW = (56 * d).toInt()
        val itemH = (68 * d).toInt()

        while (tiles.size < items.size) {
            val root = FrameLayout(context)
            root.isSoundEffectsEnabled = false

            val ring = View(context)
            ring.setBackgroundResource(R.drawable.focus_glow)
            ring.alpha = 0f
            root.addView(ring, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ))

            val icon = ImageView(context)
            icon.scaleType = ImageView.ScaleType.FIT_CENTER
            val iconLp = FrameLayout.LayoutParams(
                (32 * d).toInt(), (32 * d).toInt(),
                Gravity.TOP or Gravity.CENTER_HORIZONTAL
            )
            iconLp.topMargin = (6 * d).toInt()
            root.addView(icon, iconLp)

            val label = TextView(context)
            label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f)
            label.gravity = Gravity.CENTER
            label.maxLines = 1
            label.ellipsize = TextUtils.TruncateAt.END
            label.setTextColor(0xFFD8CFBD.toInt())
            label.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            val labelLp = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            )
            labelLp.bottomMargin = (4 * d).toInt()
            root.addView(label, labelLp)

            root.setOnClickListener { launchFocused() }
            root.isLongClickable = true
            root.setOnLongClickListener {
                startMove()
                true
            }
            addView(root, LayoutParams(itemW, itemH))
            tiles.add(Tile(root, ring, icon, label))
        }

        for (i in tiles.indices) {
            if (i < items.size) {
                val t = tiles[i]
                val app = items[i]
                t.app = app
                t.icon.setImageBitmap(app.icon)
                t.label.text = app.label
            }
        }
        requestLayout()
    }

    private fun applyPositions(animate: Boolean) {
        if (slots.isEmpty()) return
        for (i in tiles.indices) {
            val tile = tiles[i]
            if (i >= items.size) {
                tile.root.visibility = View.GONE
                continue
            }
            val s = slotOf(i)
            val slot = slots.getOrNull(s)
            if (slot == null) {
                tile.root.visibility = View.GONE
                continue
            }
            if (tile.root.visibility != View.VISIBLE) {
                tile.root.visibility = View.VISIBLE
                tile.root.translationX = slot.x
                tile.root.translationY = slot.y
            } else if (animate) {
                tile.root.animate()
                    .translationX(slot.x)
                    .translationY(slot.y)
                    .setDuration(150L)
                    .start()
            } else {
                tile.root.animate().cancel()
                tile.root.translationX = slot.x
                tile.root.translationY = slot.y
            }

            val focused = i == focus
            tile.ring.alpha = if (focused) 1f else 0f
            tile.root.scaleX = if (focused) 1.10f else 1f
            tile.root.scaleY = if (focused) 1.10f else 1f
            tile.label.setTextColor(
                if (focused) 0xFFE9C887.toInt() else 0xFFD8CFBD.toInt()
            )
        }
    }

    private fun refocusTiles() {
        for (i in tiles.indices) {
            val tile = tiles[i]
            if (i >= items.size) continue
            val focused = i == focus
            tile.ring.alpha = if (focused) 1f else 0f
            tile.root.scaleX = if (focused) 1.10f else 1f
            tile.root.scaleY = if (focused) 1.10f else 1f
            tile.label.setTextColor(
                if (focused) 0xFFE9C887.toInt() else 0xFFD8CFBD.toInt()
            )
        }
    }

    private fun rebindTile(index: Int) {
        val tile = tiles.getOrNull(index) ?: return
        val app = items.getOrNull(index) ?: return
        tile.app = app
        tile.icon.setImageBitmap(app.icon)
        tile.label.text = app.label
    }

    private fun step(dir: Int) {
        val n = items.size
        if (n == 0) return
        onNavSound?.invoke()

        if (moving) {
            val from = focus
            val to = ((from + dir) % n + n) % n
            val a = items[from]
            items[from] = items[to]
            items[to] = a
            rebindTile(from)
            rebindTile(to)
            focus = to
        } else {
            focus = ((focus + dir) % n + n) % n
        }
        applyPositions(true)
        refocusTiles()
    }

    private fun launchFocused() {
        val app = items.getOrNull(focus) ?: return
        onSelectSound?.invoke()
        onLaunch?.invoke(app)
    }

    private fun startMove() {
        if (items.isEmpty() || moving) return
        moving = true
        longFired = true
        onSelectSound?.invoke()
        onMoveMode?.invoke(true)
    }

    private fun finishMove() {
        if (!moving) return
        moving = false
        onMoveMode?.invoke(false)
        onOrderChanged?.invoke(items.map { it.pkg })
    }

    private fun hideFocused() {
        val app = items.getOrNull(focus) ?: return
        onHide?.invoke(app)
        items.removeAt(focus)
        if (focus >= items.size) focus = (items.size - 1).coerceAtLeast(0)
        rebuildTiles()
        applyPositions(false)
        finishMove()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val d = resources.displayMetrics.density
        val itemW = (56 * d).toInt()
        val itemH = (68 * d).toInt()
        for (tile in tiles) {
            tile.root.measure(
                MeasureSpec.makeMeasureSpec(itemW, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(itemH, MeasureSpec.EXACTLY)
            )
        }
        setMeasuredDimension(
            MeasureSpec.getSize(widthMeasureSpec),
            MeasureSpec.getSize(heightMeasureSpec)
        )
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val d = resources.displayMetrics.density
        val itemW = (56 * d).toInt()
        val itemH = (68 * d).toInt()
        for (tile in tiles) {
            tile.root.layout(0, 0, itemW, itemH)
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (items.isEmpty()) return super.onKeyDown(keyCode, event)

        if (moving && keyCode == KeyEvent.KEYCODE_MENU) {
            hideFocused()
            return true
        }

        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_DOWN -> {
                if (event.repeatCount == 0 || event.repeatCount % 2 == 0) step(1)
                return true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (event.repeatCount == 0 || event.repeatCount % 2 == 0) step(-1)
                return true
            }
            KeyEvent.KEYCODE_DPAD_UP -> {
                if (moving) {
                    if (event.repeatCount == 0 || event.repeatCount % 2 == 0) step(-1)
                } else {
                    onQuickMenu?.invoke()
                }
                return true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                if (event.repeatCount == 0) {
                    centerDown = true
                    longFired = false
                    event.startTracking()
                    handler.postDelayed(longPress, 650L)
                }
                return true
            }
            KeyEvent.KEYCODE_BACK -> {
                if (moving) {
                    finishMove()
                    return true
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyLongPress(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            if (!moving) {
                longFired = true
                startMove()
            }
            return true
        }
        return super.onKeyLongPress(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            handler.removeCallbacks(longPress)
            if (centerDown) {
                centerDown = false
                if (!longFired) {
                    if (moving) finishMove() else launchFocused()
                }
                return true
            }
        }
        return super.onKeyUp(keyCode, event)
    }

    override fun onDetachedFromWindow() {
        handler.removeCallbacks(longPress)
        super.onDetachedFromWindow()
    }
}
