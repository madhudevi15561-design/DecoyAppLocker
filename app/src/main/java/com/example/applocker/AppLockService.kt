package com.example.applocker

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.TextView

class AppLockService : AccessibilityService() {

    private lateinit var windowManager: WindowManager
    private lateinit var prefs: LockPreferences
    private var overlayView: View? = null

    private val handler = Handler(Looper.getMainLooper())
    private var lastUnlockedPackage: String? = null
    private var currentForegroundPackage: String? = null

    private val ignoredPackages = setOf(
        "com.android.systemui",
        "com.google.android.inputmethod.latin",
        "com.samsung.android.honeyboard",
        "com.touchtype.swiftkey"
    )

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        prefs = LockPreferences(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return

        if (ignoredPackages.contains(pkg)) return

        if (pkg != currentForegroundPackage) {
            if (currentForegroundPackage == lastUnlockedPackage) {
                lastUnlockedPackage = null
            }
            currentForegroundPackage = pkg
        }

        val lockedApps = prefs.getLockedPackages()
        val shouldLock = lockedApps.contains(pkg) || pkg == packageName

        if (shouldLock && pkg != lastUnlockedPackage) {
            if (overlayView == null) {
                showLockOverlay(pkg)
            }
        } else if (!shouldLock && overlayView != null) {
            removeLockOverlay()
        }
    }

    override fun onInterrupt() {
        removeLockOverlay()
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
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS,
            PixelFormat.OPAQUE
        ).apply {
            gravity = Gravity.CENTER
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        overlayView = LayoutInflater.from(this).inflate(R.layout.overlay_lock_screen, null)

        val subtitleText = overlayView!!.findViewById<TextView>(R.id.tvSubtitle)
        val secretEnterText = overlayView!!.findViewById<TextView>(R.id.tvSecretEnter)
        val patternView = overlayView!!.findViewById<PatternLockView>(R.id.patternView)
        val exitBtn = overlayView!!.findViewById<Button>(R.id.btnGoHome)

        // Decoy Pattern: Rejects every pattern drawn
        patternView.onPatternCompleted = { dots ->
            if (dots.size < 4) {
                subtitleText.text = "Connect at least 4 dots"
            } else {
                subtitleText.text = "Incorrect pattern. Try again."
            }
            subtitleText.setTextColor(Color.parseColor("#EF5350"))
            patternView.triggerErrorAndReset()
        }

        // Secret Unlock: Swipe "Enter" left (at least 60px) AND hold it there for 750ms
        var startX = 0f
        var isTimerRunning = false

        val unlockRunnable = Runnable {
            isTimerRunning = false
            lastUnlockedPackage = targetPackage
            removeLockOverlay()
        }

        secretEnterText.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.rawX
                    isTimerRunning = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = event.rawX - startX
                    if (deltaX < 0) {
                        view.translationX = deltaX.coerceAtLeast(-120f)
                    } else {
                        view.translationX = 0f
                    }

                    // Start 750ms timer once dragged at least 60px to the left
                    if (deltaX <= -60f) {
                        if (!isTimerRunning) {
                            isTimerRunning = true
                            handler.postDelayed(unlockRunnable, 750L)
                        }
                    } else {
                        // Cancel timer if user slides back right before 750ms finishes
                        if (isTimerRunning) {
                            isTimerRunning = false
                            handler.removeCallbacks(unlockRunnable)
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    // Cancel unlock if released before 750ms completes and snap "Enter" back
                    if (isTimerRunning) {
                        isTimerRunning = false
                        handler.removeCallbacks(unlockRunnable)
                    }
                    view.animate().translationX(0f).setDuration(150).start()
                    true
                }
                else -> false
            }
        }

        exitBtn.setOnClickListener {
            handler.removeCallbacks(unlockRunnable)
            performGlobalAction(GLOBAL_ACTION_HOME)
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
        removeLockOverlay()
        super.onDestroy()
    }
}
