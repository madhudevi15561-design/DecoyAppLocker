package com.example.applocker

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: LockPreferences
    private lateinit var btnStatus: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = LockPreferences(this)

        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        btnStatus = Button(this).apply {
            setOnClickListener { checkAndRequestPermissions() }
        }

        val listView = ListView(this)
        rootLayout.addView(btnStatus)
        rootLayout.addView(listView)
        setContentView(rootLayout)

        loadInstalledApps(listView)
    }

    override fun onResume() {
        super.onResume()
        updateButtonLabel()
    }

    private fun updateButtonLabel() {
        btnStatus.text = when {
            !Settings.canDrawOverlays(this) -> "Step 1: Allow 'Display Over Other Apps'"
            !isAccessibilityServiceEnabled() -> "Step 2: Enable 'AppLocker' in Accessibility"
            else -> "Protection Active (No Notification Mode)"
        }
    }

    private fun loadInstalledApps(listView: ListView) {
        val pm = packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val apps = pm.queryIntentActivities(mainIntent, 0)
            .map { it.activityInfo.applicationInfo }
            .distinctBy { it.packageName }
            .filter { it.packageName != packageName }
            .sortedBy { pm.getApplicationLabel(it).toString() }

        val appNames = apps.map { pm.getApplicationLabel(it).toString() }
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_multiple_choice, appNames)

        listView.choiceMode = ListView.CHOICE_MODE_MULTIPLE
        listView.adapter = adapter

        val lockedSet = prefs.getLockedPackages()
        apps.forEachIndexed { index, appInfo ->
            if (lockedSet.contains(appInfo.packageName)) {
                listView.setItemChecked(index, true)
            }
        }

        listView.setOnItemClickListener { _, _, position, _ ->
            val pkg = apps[position].packageName
            val isChecked = listView.isItemChecked(position)
            prefs.toggleAppLock(pkg, isChecked)
        }
    }

    private fun checkAndRequestPermissions() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Turn ON 'Display over other apps' for AppLocker", Toast.LENGTH_LONG).show()
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
            return
        }

        if (!isAccessibilityServiceEnabled()) {
            Toast.makeText(this, "Find 'AppLocker' under Downloaded/Installed Services and turn it ON", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            return
        }

        Toast.makeText(this, "Stealth AppLock is already running!", Toast.LENGTH_SHORT).show()
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expectedComponent = ComponentName(this, AppLockService::class.java).flattenToString()
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabledServices)
        while (splitter.hasNext()) {
            if (splitter.next().equals(expectedComponent, ignoreCase = true)) {
                return true
            }
        }
        return false
    }
}
