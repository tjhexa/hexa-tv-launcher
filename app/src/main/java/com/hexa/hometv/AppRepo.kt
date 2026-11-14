package com.hexa.hometv

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import java.text.Collator
import java.util.Locale

data class AppEntry(
    val pkg: String,
    val component: ComponentName,
    val label: String,
    var icon: Bitmap? = null
)

object AppRepo {

    fun load(
        ctx: Context,
        hidden: Set<String>,
        iconSizePx: Int,
        order: List<String>? = null,
        sortMode: Int = 0,
        counts: Map<String, Int> = emptyMap(),
        recents: Map<String, Long> = emptyMap()
    ): List<AppEntry> {
        val pm = ctx.packageManager
        val byPkg = LinkedHashMap<String, AppEntry>()

        fun scan(category: String) {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(category)
            val ris = try {
                pm.queryIntentActivities(intent, 0)
            } catch (e: Exception) {
                emptyList()
            }
            for (ri in ris) {
                val pkg = ri.activityInfo.packageName
                if (pkg == ctx.packageName) continue
                if (hidden.contains(pkg)) continue
                val existing = byPkg[pkg]
                if (existing == null || category == Intent.CATEGORY_LEANBACK_LAUNCHER) {
                    val label = try {
                        ri.loadLabel(pm).toString()
                    } catch (e: Exception) {
                        pkg
                    }
                    byPkg[pkg] = AppEntry(
                        pkg,
                        ComponentName(pkg, ri.activityInfo.name),
                        label.ifBlank { pkg }
                    )
                }
            }
        }

        scan(Intent.CATEGORY_LEANBACK_LAUNCHER)
        scan(Intent.CATEGORY_LAUNCHER)

        val collator = Collator.getInstance(Locale.getDefault())
        val alphabetical = byPkg.values.sortedWith(Comparator { a, b -> collator.compare(a.label, b.label) })

        val sorted = when (sortMode) {
            1 -> alphabetical
            2 -> alphabetical.sortedWith(
                compareByDescending<AppEntry> { counts[it.pkg] ?: 0 }
                    .thenBy { it.label }
            )
            3 -> alphabetical.sortedWith(
                compareByDescending<AppEntry> { recents[it.pkg] ?: 0L }
                    .thenBy { it.label }
            )
            else -> if (order.isNullOrEmpty()) {
                alphabetical
            } else {
                val rank = order.withIndex().associate { (i, pkg) -> pkg to i }
                alphabetical.sortedWith(
                    compareBy(
                        { rank[it.pkg] ?: Int.MAX_VALUE },
                        { it.label }
                    )
                )
            }
        }

        // Decode icons once, here, so the UI thread never touches package resources.
        val pmIcons = ctx.packageManager
        for (e in sorted) {
            e.icon = try {
                val dr = pmIcons.getApplicationIcon(e.pkg)
                val bmp = Bitmap.createBitmap(iconSizePx, iconSizePx, Bitmap.Config.ARGB_8888)
                val c = Canvas(bmp)
                dr.setBounds(0, 0, iconSizePx, iconSizePx)
                dr.draw(c)
                bmp
            } catch (ex: Exception) {
                null
            }
        }
        return sorted
    }
}
