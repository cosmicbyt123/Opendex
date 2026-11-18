package com.cosmic.opendex.window

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import com.cosmic.opendex.MainActivity
import com.cosmic.opendex.R

/**
 * Persistent system overlay taskbar that floats on top of ALL apps (Amazon, YouTube, Chrome, etc.)
 * so the user always has Back Navigation, Start Menu, and Desktop controls visible at all times.
 */
class TaskbarOverlayService : Service() {

    private val TAG = "OpenDeX:OverlayTaskbar"
    private var windowManager: WindowManager? = null
    private var overlayView: View? = null

    companion object {
        const val ACTION_START_OVERLAY = "com.cosmic.opendex.ACTION_START_OVERLAY"
        const val ACTION_STOP_OVERLAY = "com.cosmic.opendex.ACTION_STOP_OVERLAY"
        const val ACTION_UPDATE_ACTIVE_APP = "com.cosmic.opendex.ACTION_UPDATE_ACTIVE_APP"
        const val EXTRA_ACTIVE_APP_LABEL = "extra_active_app_label"

        fun start(context: Context) {
            if (Settings.canDrawOverlays(context)) {
                val intent = Intent(context, TaskbarOverlayService::class.java).apply {
                    action = ACTION_START_OVERLAY
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
                showOverlay()
            }
        }
        return START_STICKY
    }

    private fun showOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            Log.w(TAG, "Cannot show overlay: SYSTEM_ALERT_WINDOW not granted")
            return
        }

        if (overlayView != null) return

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
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

        val inflater = LayoutInflater.from(this)
        val view = inflater.inflate(R.layout.view_taskbar_overlay, null)

        val startBtn = view.findViewById<FrameLayout>(R.id.overlayStartBtn)
        val backBtn = view.findViewById<FrameLayout>(R.id.overlayBackBtn)
        val taskViewBtn = view.findViewById<FrameLayout>(R.id.overlayTaskViewBtn)

        // Back Button: Brings OpenDeX desktop straight back to the top over any app!
        backBtn.setOnClickListener {
            val homeIntent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            }
            startActivity(homeIntent)
        }

        // Start Button: Opens OpenDeX with Start Menu open
        startBtn.setOnClickListener {
            val homeIntent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                putExtra(MainActivity.EXTRA_OPEN_START_MENU, true)
            }
            startActivity(homeIntent)
        }

        // Task View Button: Opens OpenDeX with Virtual Desktops open
        taskViewBtn.setOnClickListener {
            val homeIntent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                putExtra(MainActivity.EXTRA_OPEN_TASK_VIEW, true)
            }
            startActivity(homeIntent)
        }

        try {
            windowManager?.addView(view, params)
            overlayView = view
            Log.i(TAG, "Persistent Floating Taskbar Overlay is now active on top of all apps!")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add floating taskbar overlay: ${e.message}")
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
