package com.example.applocker

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
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

    private var lastUnlockedPackage: String? = null
    private var currentForegroundPackage: String? = null

    // System UI & keyboard packages to ignore so typing or pulling down shade doesn't reset unlock
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

        // Reset temporary unlock state when switching to a different app or Home screen
        if (pkg != currentForegroundPackage) {
            if (currentForegroundPackage == lastUnlockedPackage) {
                lastUnlockedPackage = null
            }
            currentForegroundPackage = pkg
        }

        val lockedApps = prefs.getLockedPackages()
        // Lock checked apps AND lock AppLocker itself so nobody can uncheck your apps
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

        titleText.text = if (targetPackage == packageName) "AppLocker Settings" else targetPackage

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

        // Secret Unlock: Swipe the word "Enter" to the left
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

        // Send user to Home screen if they tap Cancel
        exitBtn.setOnClickListener {
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
