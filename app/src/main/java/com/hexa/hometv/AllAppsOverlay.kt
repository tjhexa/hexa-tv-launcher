package com.hexa.hometv

import android.content.Context
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

/** Focusable tile that reports a long press on OK/Enter. */
private class TileView(context: Context) : FrameLayout(context) {
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

/** Full-screen app grid. OK opens, hold OK pins/unpins a favourite. */
class AllAppsOverlay(
    context: Context,
    apps: List<AppEntry>,
    favorites: Set<String>,
    onLaunch: (AppEntry) -> Unit,
    onToggleFavorite: (AppEntry, Boolean) -> Unit
) : FrameLayout(context) {

    private val adapter = Adapter(apps, favorites.toMutableSet(), onLaunch, onToggleFavorite)
    private lateinit var recycler: RecyclerView

    init {
        setBackgroundColor(0xF20B0A12.toInt())
        isFocusable = true
        val d = resources.displayMetrics.density

        val title = TextView(context).apply {
            text = "ALL APPS"
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
            layoutManager = GridLayoutManager(context, 5)
            adapter = this@AllAppsOverlay.adapter
            setPadding((44 * d).toInt(), (76 * d).toInt(), (44 * d).toInt(), (72 * d).toInt())
            clipToPadding = false
            itemAnimator = null
            setBackgroundColor(Color.TRANSPARENT)
        }
        addView(recycler, LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))

        val hint = TextView(context).apply {
            text = "OK open   ·   hold OK pin / unpin   ·   BACK close"
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
        recycler.post {
            recycler.layoutManager?.findViewByPosition(0)?.requestFocus()
        }
    }

    fun updateFavorites(set: Set<String>) {
        adapter.setFavorites(set)
    }

    private class Adapter(
        private val apps: List<AppEntry>,
        private var favorites: MutableSet<String>,
        private val onLaunch: (AppEntry) -> Unit,
        private val onToggleFavorite: (AppEntry, Boolean) -> Unit
    ) : RecyclerView.Adapter<Adapter.VH>() {

        class VH(v: View, val ring: View, val icon: ImageView, val label: TextView, val star: TextView) :
            RecyclerView.ViewHolder(v)

        fun setFavorites(set: Set<String>) {
            favorites = set.toMutableSet()
            notifyDataSetChanged()
        }

        override fun getItemCount() = apps.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val ctx = parent.context
            val d = ctx.resources.displayMetrics.density
            val root = TileView(ctx)
            root.layoutParams = RecyclerView.LayoutParams((150 * d).toInt(), (148 * d).toInt())
            root.isFocusable = true
            root.isFocusableInTouchMode = true
            root.isSoundEffectsEnabled = false

            val ring = View(ctx)
            ring.setBackgroundResource(R.drawable.focus_glow)
            ring.alpha = 0f
            root.addView(ring, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            ))

            val icon = ImageView(ctx)
            icon.scaleType = ImageView.ScaleType.FIT_CENTER
            val ilp = FrameLayout.LayoutParams(
                (60 * d).toInt(), (60 * d).toInt(),
                Gravity.TOP or Gravity.CENTER_HORIZONTAL
            )
            ilp.topMargin = (16 * d).toInt()
            root.addView(icon, ilp)

            val label = TextView(ctx)
            label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            label.gravity = Gravity.CENTER
            label.maxLines = 1
            label.ellipsize = android.text.TextUtils.TruncateAt.END
            label.setTextColor(0xFFD8CFBD.toInt())
            label.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            val llp = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            )
            llp.bottomMargin = (12 * d).toInt()
            root.addView(label, llp)

            val star = TextView(ctx)
            star.text = "★"
            star.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            star.setTextColor(0xFFE9C887.toInt())
            star.visibility = View.GONE
            root.addView(star, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.END
            ).apply {
                topMargin = (4 * d).toInt()
                rightMargin = (8 * d).toInt()
            })

            return VH(root, ring, icon, label, star)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val app = apps[position]
            holder.icon.setImageBitmap(app.icon)
            holder.label.text = app.label
            holder.star.visibility = if (favorites.contains(app.pkg)) View.VISIBLE else View.GONE

            holder.itemView.setOnClickListener { onLaunch(app) }
            (holder.itemView as TileView).onLongPress = {
                val nowFav = !favorites.contains(app.pkg)
                if (nowFav) favorites.add(app.pkg) else favorites.remove(app.pkg)
                holder.star.visibility = if (nowFav) View.VISIBLE else View.GONE
                onToggleFavorite(app, nowFav)
            }
            holder.itemView.setOnFocusChangeListener { v, hasFocus ->
                holder.ring.alpha = if (hasFocus) 1f else 0f
                holder.label.setTextColor(
                    if (hasFocus) 0xFFE9C887.toInt() else 0xFFD8CFBD.toInt()
                )
                v.animate().scaleX(if (hasFocus) 1.05f else 1f)
                    .scaleY(if (hasFocus) 1.05f else 1f)
                    .setDuration(140L).start()
            }
        }
    }
}
