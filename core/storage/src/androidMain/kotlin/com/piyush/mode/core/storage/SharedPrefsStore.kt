package com.piyush.mode.core.storage

import android.content.Context
import android.content.SharedPreferences

/** Settings in one SharedPreferences file; Mode saves only text values. */
public class SharedPrefsStore(context: Context, fileName: String = "mode") : KeyValueStore {
    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(fileName, Context.MODE_PRIVATE)

    override fun getString(name: String): String? = prefs.getString(name, null)

    override fun putString(name: String, value: String) {
        prefs.edit().putString(name, value).apply()
    }

    override fun names(): Set<String> = prefs.all.filterValues { it is String }.keys

    override fun clear() {
        prefs.edit().clear().commit()
    }
}
