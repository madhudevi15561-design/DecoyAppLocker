package com.example.applocker

import android.content.Context

class LockPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("app_lock_prefs", Context.MODE_PRIVATE)

    fun getLockedPackages(): MutableSet<String> {
        return prefs.getStringSet("locked_apps", emptySet())?.toMutableSet() ?: mutableSetOf()
    }

    fun toggleAppLock(packageName: String, isLocked: Boolean) {
        val current = getLockedPackages()
        if (isLocked) current.add(packageName) else current.remove(packageName)
        prefs.edit().putStringSet("locked_apps", current).apply()
    }
}
