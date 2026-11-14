package com.hexa.hometv

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory

/** Twelve wallpapers baked into the APK, rotated hourly. */
object Wallpapers {
    private const val DIR = "walls"

    private fun names(ctx: Context): List<String> = try {
        ctx.assets.list(DIR)?.filter { it.endsWith(".jpg", true) }?.sorted() ?: emptyList()
    } catch (e: Exception) {
        emptyList()
    }

    fun count(ctx: Context): Int = names(ctx).size

    fun nameAt(ctx: Context, index: Int): String? {
        val list = names(ctx)
        if (list.isEmpty()) return null
        return list[((index % list.size) + list.size) % list.size]
    }

    fun decode(ctx: Context, index: Int): Bitmap? = try {
        val list = names(ctx)
        if (list.isEmpty()) {
            null
        } else {
            val name = list[((index % list.size) + list.size) % list.size]
            ctx.assets.open("$DIR/$name").use { BitmapFactory.decodeStream(it) }
        }
    } catch (e: Exception) {
        null
    }
}
