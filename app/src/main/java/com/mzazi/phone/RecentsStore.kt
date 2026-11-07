package com.mzazi.phone

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class RecentCall(
    val number: String,
    val name: String?,
    val timestamp: Long
)

/**
 * Local call history. This app does not read the system call log (that
 * permission is reserved for the default dialer), so it records the calls it
 * places itself.
 */
class RecentsStore(context: Context) {

    private val prefs = context.getSharedPreferences("recents", Context.MODE_PRIVATE)

    fun load(): List<RecentCall> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                val number = item.optString("number")
                if (number.isEmpty()) null
                else RecentCall(number, item.optString("name").ifEmpty { null }, item.optLong("time"))
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun add(number: String, name: String?) {
        val current = load().toMutableList()
        // Collapse an immediate repeat of the same number.
        if (current.firstOrNull()?.number == number) current.removeAt(0)
        current.add(0, RecentCall(number, name, System.currentTimeMillis()))
        while (current.size > MAX_ENTRIES) current.removeAt(current.size - 1)
        save(current)
    }

    fun clear() {
        prefs.edit().remove(KEY).apply()
    }

    private fun save(entries: List<RecentCall>) {
        val array = JSONArray()
        entries.forEach {
            array.put(
                JSONObject()
                    .put("number", it.number)
                    .put("name", it.name ?: "")
                    .put("time", it.timestamp)
            )
        }
        prefs.edit().putString(KEY, array.toString()).apply()
    }

    private companion object {
        const val KEY = "calls"
        const val MAX_ENTRIES = 50
    }
}
