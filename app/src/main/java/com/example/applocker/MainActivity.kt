package com.example.applocker

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: LockPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = LockPreferences(this)

        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        val btnPermissions = Button(this).apply {
            text = "Grant Permissions & Start Lock Service"
            setOnClickListener { checkAndRequestPermissions() }
        }

        val listView = ListView(this)
        rootLayout.addView(btnPermissions)
        rootLayout.addView(listView)
        setContentView(rootLayout)

        loadInstalledApps(listView)
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
        if (!hasUsageStatsPermission()) {
            Toast.makeText(this, "Enable Usage Access for AppLocker", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            return
        }

        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Enable 'Display over other apps'", Toast.LENGTH_LONG).show()
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
            return
        }

        val serviceIntent = Intent(this, AppLockService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
        Toast.makeText(this, "AppLock Service Started!", Toast.LENGTH_SHORT).show()
    }

    private fun hasUsageStatsPermission(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }
}
