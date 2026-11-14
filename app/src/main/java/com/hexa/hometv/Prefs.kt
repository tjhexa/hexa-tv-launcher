package com.hexa.hometv

import android.content.Context

class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("home", Context.MODE_PRIVATE)

    var clock24: Boolean?
        get() = if (sp.contains("clock24")) sp.getBoolean("clock24", true) else null
        set(v) {
            if (v == null) sp.edit().remove("clock24").apply()
            else sp.edit().putBoolean("clock24", v).apply()
        }

    val hidden: Set<String>
        get() = sp.getStringSet("hidden", emptySet()) ?: emptySet()

    fun hide(pkg: String) {
        sp.edit().putStringSet("hidden", hidden + pkg).apply()
    }

    fun unhideAll() {
        sp.edit().remove("hidden").apply()
    }

    var bgPath: String?
        get() = sp.getString("bgPath", null)
        set(v) {
            if (v == null) sp.edit().remove("bgPath").apply()
            else sp.edit().putString("bgPath", v).apply()
        }

    var bgCredit: String?
        get() = sp.getString("bgCredit", null)
        set(v) {
            if (v == null) sp.edit().remove("bgCredit").apply()
            else sp.edit().putString("bgCredit", v).apply()
        }

    var lastRotate: Long
        get() = sp.getLong("lastRotate", 0L)
        set(v) = sp.edit().putLong("lastRotate", v).apply()

    var wallIndex: Int
        get() = sp.getInt("wallIndex", 0)
        set(v) = sp.edit().putInt("wallIndex", v).apply()

    /** null = alphabetical; otherwise the user's arranged order */
    var appOrder: List<String>?
        get() {
            val raw = sp.getString("appOrder", null) ?: return null
            return raw.split("|").filter { it.isNotBlank() }
        }
        set(v) {
            if (v == null) sp.edit().remove("appOrder").apply()
            else sp.edit().putString("appOrder", v.joinToString("|")).apply()
        }

    var favorites: Set<String>
        get() = sp.getStringSet("favorites", emptySet()) ?: emptySet()
        set(v) = sp.edit().putStringSet("favorites", v).apply()

    fun toggleFavorite(pkg: String) {
        val f = favorites.toMutableSet()
        if (!f.add(pkg)) f.remove(pkg)
        favorites = f
    }

    /** 30 = half hour, 60 = hour, 180, 360, 1440, 0 = never */
    var wallIntervalMin: Int
        get() = sp.getInt("wallInterval", 60)
        set(v) = sp.edit().putInt("wallInterval", v).apply()

    var skippedWalls: Set<Int>
        get() = (sp.getStringSet("skippedWalls", emptySet()) ?: emptySet())
            .mapNotNull { it.toIntOrNull() }
            .toSet()
        set(v) = sp.edit().putStringSet("skippedWalls", v.map { it.toString() }.toSet()).apply()

    fun toggleSkipWall(index: Int) {
        val s = skippedWalls.toMutableSet()
        if (!s.add(index)) s.remove(index)
        skippedWalls = s
    }

    var showDate: Boolean
        get() = sp.getBoolean("showDate", true)
        set(v) = sp.edit().putBoolean("showDate", v).apply()

    /** 0 = small, 1 = medium, 2 = large */
    var clockSize: Int
        get() = sp.getInt("clockSize", 1)
        set(v) = sp.edit().putInt("clockSize", v.coerceIn(0, 2)).apply()

    var wordmarkOn: Boolean
        get() = sp.getBoolean("wordmarkOn", true)
        set(v) = sp.edit().putBoolean("wordmarkOn", v).apply()

    /** 0 = gold, 1 = ivory, 2 = silver */
    var wordmarkColor: Int
        get() = sp.getInt("wordmarkColor", 0)
        set(v) = sp.edit().putInt("wordmarkColor", v.coerceIn(0, 2)).apply()

    /** 0 = manual, 1 = A-Z, 2 = most launched, 3 = recently used */
    var sortMode: Int
        get() = sp.getInt("sortMode", 0)
        set(v) = sp.edit().putInt("sortMode", v.coerceIn(0, 3)).apply()

    val launchCounts: Map<String, Int>
        get() = sp.getString("launchCounts", "")
            ?.split(";")
            ?.filter { it.contains("=") }
            ?.associate {
                it.substringBefore("=") to (it.substringAfter("=").toIntOrNull() ?: 0)
            } ?: emptyMap()

    val lastUsed: Map<String, Long>
        get() = sp.getString("lastUsed", "")
            ?.split(";")
            ?.filter { it.contains("=") }
            ?.associate {
                it.substringBefore("=") to (it.substringAfter("=").toLongOrNull() ?: 0L)
            } ?: emptyMap()

    fun recordLaunch(pkg: String) {
        val counts = launchCounts.toMutableMap()
        counts[pkg] = (counts[pkg] ?: 0) + 1
        val recents = lastUsed.toMutableMap()
        recents[pkg] = System.currentTimeMillis()
        sp.edit()
            .putString("launchCounts", counts.entries.joinToString(";") { "${it.key}=${it.value}" })
            .putString("lastUsed", recents.entries.joinToString(";") { "${it.key}=${it.value}" })
            .apply()
    }
}
