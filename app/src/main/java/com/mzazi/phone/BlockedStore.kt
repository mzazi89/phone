package com.mzazi.phone

import android.content.Context

/** Local block list: numbers here are rejected before they ring. */
class BlockedStore(context: Context) {

    private val prefs = context.getSharedPreferences("blocked", Context.MODE_PRIVATE)

    fun all(): List<String> =
        prefs.getStringSet(KEY, emptySet())?.sorted().orEmpty()

    fun contains(number: String): Boolean {
        val normalized = T9.normalizeNumber(number).trimStart('+')
        if (normalized.isEmpty()) return false
        return all().any { blocked ->
            val candidate = T9.normalizeNumber(blocked).trimStart('+')
            candidate.isNotEmpty() &&
                (candidate == normalized ||
                    candidate.endsWith(normalized) ||
                    normalized.endsWith(candidate))
        }
    }

    fun add(number: String) {
        val value = number.trim()
        if (value.isEmpty()) return
        val current = all().toMutableSet()
        current.add(value)
        prefs.edit().putStringSet(KEY, current).apply()
    }

    fun remove(number: String) {
        val current = all().toMutableSet()
        current.remove(number)
        prefs.edit().putStringSet(KEY, current).apply()
    }

    private companion object {
        const val KEY = "numbers"
    }
}
