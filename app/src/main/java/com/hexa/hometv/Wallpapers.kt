package com.hexa.hometv

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory

/** Wallpapers baked into the APK, rotated hourly. */
object Wallpapers {
    private const val DIR = "walls"
    private const val VIDEO_DIR = "video"
    private const val VIDEO_FILE = "jatayu_vimana_bg.mp4"
    const val VIDEO_POSTER_PATH = "$VIDEO_DIR/jatayu_vimana_poster.jpg"

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

    /** True when the looping Jatayu/Vimana clip is bundled with the APK. */
    fun hasVideo(ctx: Context): Boolean = try {
        ctx.assets.open("$VIDEO_DIR/$VIDEO_FILE").close()
        true
    } catch (e: Exception) {
        false
    }

    fun videoAssetPath(): String = "$VIDEO_DIR/$VIDEO_FILE"

    fun videoPoster(ctx: Context): Bitmap? = try {
        ctx.assets.open(VIDEO_POSTER_PATH).use { BitmapFactory.decodeStream(it) }
    } catch (e: Exception) {
        null
    }

    /** The video is the extra item after the stills (index == count). */
    fun isVideo(ctx: Context, index: Int): Boolean =
        hasVideo(ctx) && index == count(ctx)
}
