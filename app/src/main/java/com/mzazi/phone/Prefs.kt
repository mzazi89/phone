package com.mzazi.phone

import android.content.Context

/** Small settings store for the toggles that actually change behaviour. */
class Prefs(context: Context) {

    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var whatsappButton: Boolean
        get() = prefs.getBoolean(KEY_WHATSAPP, true)
        set(value) = prefs.edit().putBoolean(KEY_WHATSAPP, value).apply()

    var haptics: Boolean
        get() = prefs.getBoolean(KEY_HAPTICS, true)
        set(value) = prefs.edit().putBoolean(KEY_HAPTICS, value).apply()

    var roleAsked: Boolean
        get() = prefs.getBoolean(KEY_ROLE_ASKED, false)
        set(value) = prefs.edit().putBoolean(KEY_ROLE_ASKED, value).apply()

    private companion object {
        const val KEY_WHATSAPP = "whatsapp_button"
        const val KEY_HAPTICS = "haptics"
        const val KEY_ROLE_ASKED = "role_asked"
    }
}
