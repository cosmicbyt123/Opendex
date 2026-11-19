package com.cosmic.opendex.window

import android.app.ActivityOptions
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.Display
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import com.cosmic.opendex.MainActivity
import com.cosmic.opendex.R

/**
 * Persistent system overlay taskbar that floats on top of ALL apps
 * so the user always has Back Navigation, Start Menu, Virtual Desktops, and Notifications.
 */
class TaskbarOverlayService : Service() {

    private val TAG = "OpenDeX:OverlayTaskbar"
    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var currentDisplayId: Int = Display.DEFAULT_DISPLAY

    companion object {
        const val ACTION_START_OVERLAY = "com.cosmic.opendex.ACTION_START_OVERLAY"
        const val ACTION_STOP_OVERLAY = "com.cosmic.opendex.ACTION_STOP_OVERLAY"
        const val ACTION_UPDATE_ACTIVE_APP = "com.cosmic.opendex.ACTION_UPDATE_ACTIVE_APP"
        const val EXTRA_ACTIVE_APP_LABEL = "extra_active_app_label"
        const val EXTRA_TARGET_DISPLAY_ID = "extra_target_display_id"

        fun start(context: Context, displayId: Int = -1) {
            if (Settings.canDrawOverlays(context)) {
                val intent = Intent(context, TaskbarOverlayService::class.java).apply {
                    action = ACTION_START_OVERLAY
                    putExtra(EXTRA_TARGET_DISPLAY_ID, displayId)
                }
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, TaskbarOverlayService::class.java).apply {
                action = ACTION_STOP_OVERLAY
            }
            context.startService(intent)
        }

        fun updateActiveApp(context: Context, appLabel: String) {
            if (Settings.canDrawOverlays(context)) {
                val intent = Intent(context, TaskbarOverlayService::class.java).apply {
                    action = ACTION_UPDATE_ACTIVE_APP
                    putExtra(EXTRA_ACTIVE_APP_LABEL, appLabel)
                }
                context.startService(intent)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val displayId = intent?.getIntExtra(EXTRA_TARGET_DISPLAY_ID, -1) ?: -1
        if (displayId != -1) {
            currentDisplayId = displayId
        }

        when (intent?.action) {
            ACTION_STOP_OVERLAY -> {
                removeOverlay()
                stopSelf()
            }
            ACTION_UPDATE_ACTIVE_APP -> {
                val label = intent.getStringExtra(EXTRA_ACTIVE_APP_LABEL) ?: "OpenDeX Desktop"
                overlayView?.findViewById<TextView>(R.id.overlayActiveLabel)?.text = label
            }
            else -> {
                showOverlay(currentDisplayId)
            }
        }
        return START_STICKY
    }

    private fun showOverlay(displayId: Int) {
        if (!Settings.canDrawOverlays(this)) {
            Log.w(TAG, "Cannot show overlay: SYSTEM_ALERT_WINDOW not granted")
            return
        }

        if (overlayView != null) return

        val displayManager = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val targetDisplay = if (displayId != -1 && displayId != Display.DEFAULT_DISPLAY) {
            displayManager.getDisplay(displayId)
        } else {
            null
        }

        val targetContext = if (targetDisplay != null) {
            createDisplayContext(targetDisplay)
        } else {
            this
        }

        windowManager = targetContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val barHeightPx = (52 * resources.displayMetrics.density).toInt()

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            barHeightPx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.START
        }

        val inflater = LayoutInflater.from(targetContext)
        val view = inflater.inflate(R.layout.view_taskbar_overlay, null)

        val startBtn = view.findViewById<FrameLayout>(R.id.overlayStartBtn)
        val backBtn = view.findViewById<FrameLayout>(R.id.overlayBackBtn)
        val taskViewBtn = view.findViewById<FrameLayout>(R.id.overlayTaskViewBtn)
        val notificationBtn = view.findViewById<FrameLayout>(R.id.overlayNotificationBtn)

        fun launchHome(extraFlag: String? = null) {
            val homeIntent = Intent(targetContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                if (extraFlag != null) {
                    putExtra(extraFlag, true)
                }
            }
            if (targetDisplay != null) {
                try {
                    val opt = ActivityOptions.makeBasic()
                    val m = ActivityOptions::class.java.getMethod("setLaunchDisplayId", Int::class.javaPrimitiveType)
                    m.invoke(opt, targetDisplay.displayId)
                    targetContext.startActivity(homeIntent, opt.toBundle())
                    return
                } catch (e: Exception) {
                    Log.w(TAG, "setLaunchDisplayId reflection failed: ${e.message}")
                }
            }
            targetContext.startActivity(homeIntent)
        }

        // Back Navigation Button
        backBtn.setOnClickListener {
            launchHome()
        }

        // Start / App Drawer Button
        startBtn.setOnClickListener {
            launchHome(MainActivity.EXTRA_OPEN_START_MENU)
        }

        // Task View Button
        taskViewBtn.setOnClickListener {
            launchHome(MainActivity.EXTRA_OPEN_TASK_VIEW)
        }

        // Notification Button
        notificationBtn?.setOnClickListener {
            expandNotifications()
        }

        try {
            windowManager?.addView(view, params)
            overlayView = view
            Log.i(TAG, "Persistent Floating Taskbar Overlay is now active on display $displayId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add floating taskbar overlay: ${e.message}", e)
        }
    }

    private fun expandNotifications() {
        try {
            val statusBarService = getSystemService("statusbar")
            val method = statusBarService.javaClass.getMethod("expandNotificationsPanel")
            method.invoke(statusBarService)
        } catch (e: Exception) {
            Log.w(TAG, "Cannot expand notification panel via reflection: ${e.message}")
            try {
                val intent = Intent("android.settings.NOTIFICATION_SETTINGS").apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                startActivity(intent)
            } catch (ex: Exception) {
                Log.e(TAG, "Cannot open notification settings: ${ex.message}")
            }
        }
    }

    private fun removeOverlay() {
        overlayView?.let { view ->
            try {
                windowManager?.removeView(view)
            } catch (e: Exception) {
                Log.w(TAG, "Error removing overlay view: ${e.message}")
            }
        }
        overlayView = null
    }

    override fun onDestroy() {
        super.onDestroy()
        removeOverlay()
    }
}
