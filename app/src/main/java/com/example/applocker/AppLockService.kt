package com.example.applocker

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.core.app.NotificationCompat

class AppLockService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var prefs: LockPreferences
    private var overlayView: View? = null

    private val handler = Handler(Looper.getMainLooper())
    private var lastUnlockedPackage: String? = null
    private var currentForegroundPackage: String? = null

    private val checkRunnable = object : Runnable {
        override fun run() {
            checkForegroundApp()
            handler.postDelayed(this, 350)
        }
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        prefs = LockPreferences(this)
        startForegroundNotification()
        handler.post(checkRunnable)
    }

    private fun startForegroundNotification() {
        val channelId = "app_lock_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "App Lock Protection",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("App Lock Active")
            .setContentText("Monitoring protected apps")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .build()

        startForeground(101, notification)
    }

    private fun checkForegroundApp() {
        val foregroundApp = getForegroundPackageName() ?: return

        if (foregroundApp != currentForegroundPackage) {
            if (currentForegroundPackage == lastUnlockedPackage && foregroundApp != packageName) {
                lastUnlockedPackage = null
            }
            currentForegroundPackage = foregroundApp
        }

        val lockedApps = prefs.getLockedPackages()

        if (lockedApps.contains(foregroundApp) && foregroundApp != lastUnlockedPackage) {
            if (overlayView == null) {
                showLockOverlay(foregroundApp)
            }
        } else if (!lockedApps.contains(foregroundApp) && overlayView != null) {
            removeLockOverlay()
        }
    }

    private fun getForegroundPackageName(): String? {
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val endTime = System.currentTimeMillis()
        val beginTime = endTime - 5000

        val usageEvents = usm.queryEvents(beginTime, endTime)
        val event = UsageEvents.Event()
        var recentPackage: String? = null

        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                recentPackage = event.packageName
            }
        }
        return recentPackage
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showLockOverlay(targetPackage: String) {
        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }

        overlayView = LayoutInflater.from(this).inflate(R.layout.overlay_lock_screen, null)

        val titleText = overlayView!!.findViewById<TextView>(R.id.tvLockedApp)
        val subtitleText = overlayView!!.findViewById<TextView>(R.id.tvSubtitle)
        val secretEnterText = overlayView!!.findViewById<TextView>(R.id.tvSecretEnter)
        val patternView = overlayView!!.findViewById<PatternLockView>(R.id.patternView)
        val exitBtn = overlayView!!.findViewById<Button>(R.id.btnGoHome)

        titleText.text = targetPackage

        patternView.onPatternCompleted = { dots ->
            if (dots.size < 4) {
                subtitleText.text = "Connect at least 4 dots"
            } else {
                subtitleText.text = "Incorrect pattern. Try again."
            }
            subtitleText.setTextColor(Color.parseColor("#EF5350"))
            patternView.triggerErrorAndReset()
        }

        var startX = 0f
        secretEnterText.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.rawX
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = event.rawX - startX
                    if (deltaX < 0) {
                        view.translationX = deltaX.coerceAtLeast(-120f)
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val deltaX = event.rawX - startX
                    if (deltaX <= -60f) {
                        lastUnlockedPackage = targetPackage
                        removeLockOverlay()
                    } else {
                        view.animate().translationX(0f).setDuration(150).start()
                    }
                    true
                }
                else -> false
            }
        }

        exitBtn.setOnClickListener {
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(homeIntent)
            removeLockOverlay()
        }

        windowManager.addView(overlayView, layoutParams)
    }

    private fun removeLockOverlay() {
        overlayView?.let {
            windowManager.removeView(it)
            overlayView = null
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(checkRunnable)
        removeLockOverlay()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
