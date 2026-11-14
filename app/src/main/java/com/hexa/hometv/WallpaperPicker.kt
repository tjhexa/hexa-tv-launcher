package com.hexa.hometv

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

private class WallTile(context: Context) : FrameLayout(context) {
    var onLongPress: (() -> Unit)? = null
    private var longFired = false

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            if (event.repeatCount == 0) event.startTracking()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyLongPress(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            longFired = true
            onLongPress?.invoke()
            return true
        }
        return super.onKeyLongPress(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            if (!longFired) performClick()
            longFired = false
            return true
        }
        return super.onKeyUp(keyCode, event)
    }
}

class WallpaperPicker(
    context: Context,
    stillCount: Int,
    videoPos: Int,
    skipped: Set<Int>,
    current: Int,
    onPick: (Int) -> Unit,
    onToggleSkip: (Int, Boolean) -> Unit
) : FrameLayout(context) {

    private val adapter = Adapter(
        stillCount, videoPos, skipped.toMutableSet(), current, onPick, onToggleSkip
    )
    private lateinit var recycler: RecyclerView

    init {
        setBackgroundColor(0xF20B0A12.toInt())
        isFocusable = true
        val d = resources.displayMetrics.density

        val title = TextView(context).apply {
            text = "WALLPAPERS"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setTextColor(0xFFE9C887.toInt())
            letterSpacing = 0.32f
            gravity = Gravity.CENTER
        }
        addView(title, LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = (30 * d).toInt() })

        recycler = RecyclerView(context).apply {
            layoutManager = GridLayoutManager(context, 6)
            adapter = this@WallpaperPicker.adapter
            setPadding((36 * d).toInt(), (76 * d).toInt(), (36 * d).toInt(), (72 * d).toInt())
            clipToPadding = false
            itemAnimator = null
            setBackgroundColor(Color.TRANSPARENT)
        }
        addView(recycler, LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))

        val hint = TextView(context).apply {
            text = "OK set   ·   hold OK skip / unskip   ·   BACK close"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(0xFFB89A63.toInt())
            letterSpacing = 0.12f
            gravity = Gravity.CENTER
        }
        addView(hint, LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM
        ).apply { bottomMargin = (22 * d).toInt() })
    }

    fun focusFirst() {
        recycler.post { recycler.layoutManager?.findViewByPosition(0)?.requestFocus() }
    }

    private class Adapter(
        private val stillCount: Int,
        private val videoPos: Int,
        private val skipped: MutableSet<Int>,
        private val current: Int,
        private val onPick: (Int) -> Unit,
        private val onToggleSkip: (Int, Boolean) -> Unit
    ) : RecyclerView.Adapter<Adapter.VH>() {

        private val thumbs = HashMap<Int, Bitmap?>()
        private val itemCount = stillCount + if (videoPos >= 0) 1 else 0

        class VH(v: View, val image: ImageView, val label: TextView, val skip: TextView) :
            RecyclerView.ViewHolder(v)

        override fun getItemCount() = itemCount

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val ctx = parent.context
            val d = ctx.resources.displayMetrics.density
            val root = WallTile(ctx)
            root.layoutParams = RecyclerView.LayoutParams((150 * d).toInt(), (112 * d).toInt())
            root.isFocusable = true
            root.isFocusableInTouchMode = true
            root.isSoundEffectsEnabled = false
            root.setBackgroundResource(R.drawable.focus_glow)

            val image = ImageView(ctx)
            image.scaleType = ImageView.ScaleType.CENTER_CROP
            root.addView(image, LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (88 * d).toInt()
            ))

            val label = TextView(ctx)
            label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9.5f)
            label.gravity = Gravity.CENTER
            label.setTextColor(0xFFD8CFBD.toInt())
            label.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            root.addView(label, LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
            ).apply { bottomMargin = (2 * d).toInt() })

            val skip = TextView(ctx)
            skip.text = "SKIPPED"
            skip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f)
            skip.setTextColor(0xFFFFD98A.toInt())
            skip.setBackgroundColor(0x99000000.toInt())
            skip.gravity = Gravity.CENTER
            skip.visibility = View.GONE
            root.addView(skip, LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (20 * d).toInt(),
                Gravity.CENTER
            ))

            return VH(root, image, label, skip)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val ctx = holder.itemView.context
            val isVideo = position == videoPos
            var bmp = thumbs[position]
            if (bmp == null && !thumbs.containsKey(position)) {
                val opts = BitmapFactory.Options().apply { inSampleSize = 8 }
                bmp = try {
                    if (isVideo) {
                        ctx.assets.open(Wallpapers.VIDEO_POSTER_PATH).use {
                            BitmapFactory.decodeStream(it, null, opts)
                        }
                    } else {
                        val name = Wallpapers.nameAt(ctx, position)
                        ctx.assets.open("walls/$name").use {
                            BitmapFactory.decodeStream(it, null, opts)
                        }
                    }
                } catch (e: Exception) {
                    null
                }
                thumbs[position] = bmp
            }
            holder.image.setImageBitmap(bmp)
            holder.image.alpha = if (skipped.contains(position)) 0.35f else 1f
            val isCurrent = position == current
            holder.label.text = when {
                isVideo && isCurrent -> "JATAYU · CURRENT"
                isVideo -> "JATAYU · VIDEO"
                isCurrent -> "Wall ${position + 1} · CURRENT"
                else -> "Wall ${position + 1}"
            }
            holder.label.setTextColor(
                if (isCurrent) 0xFFE9C887.toInt() else 0xFFD8CFBD.toInt()
            )
            holder.skip.visibility = if (skipped.contains(position)) View.VISIBLE else View.GONE

            holder.itemView.setOnClickListener { onPick(position) }
            (holder.itemView as WallTile).onLongPress = {
                val nowSkipped = !skipped.contains(position)
                if (nowSkipped) skipped.add(position) else skipped.remove(position)
                holder.image.alpha = if (nowSkipped) 0.35f else 1f
                holder.skip.visibility = if (nowSkipped) View.VISIBLE else View.GONE
                onToggleSkip(position, nowSkipped)
            }
        }
    }
}
