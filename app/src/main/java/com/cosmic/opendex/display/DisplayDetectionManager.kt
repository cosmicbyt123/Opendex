package com.cosmic.opendex.display

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.Display
import android.view.WindowManager

/**
 * Monitors physical and virtual display connections using AOSP DisplayManager.DisplayListener.
 * Detects external HDMI, wireless cast, or secondary displays and manages target routing.
 */
class DisplayDetectionManager(
    private val context: Context,
    private val onDisplayConnected: (DisplayInfoModel) -> Unit,
    private val onDisplayDisconnected: (Int) -> Unit,
    private val onDisplayChanged: ((DisplayInfoModel) -> Unit)? = null
) {
    private val TAG = "OpenDeX:Display"
    private val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
    private val mainHandler = Handler(Looper.getMainLooper())

    var activeTargetDisplayId: Int = Display.DEFAULT_DISPLAY
        private set

    init {
        // Automatically adopt the activity's current display if it's already on a secondary display
        val currentDisplay = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                context.display?.displayId ?: Display.DEFAULT_DISPLAY
            } else {
                @Suppress("DEPRECATION")
                (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.displayId
            }
        } catch (e: Exception) {
            Display.DEFAULT_DISPLAY
        }

        if (currentDisplay != Display.DEFAULT_DISPLAY) {
            activeTargetDisplayId = currentDisplay
            Log.i(TAG, "Initialized activeTargetDisplayId to Activity's current display: $currentDisplay")
        } else {
            // Check if there are already secondary displays connected
            val displays = displayManager.displays
            val secondary = displays.firstOrNull { it.displayId != Display.DEFAULT_DISPLAY }
            if (secondary != null) {
                activeTargetDisplayId = secondary.displayId
                Log.i(TAG, "Found pre-existing secondary display: ${secondary.displayId} (${secondary.name})")
            }
        }
    }

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) {
            val display = displayManager.getDisplay(displayId) ?: return
            val info = extractDisplayInfo(display)
            Log.i(TAG, "Secondary Display Connected: ID $displayId -> ${info.name} [${info.resolutionString}]")
            if (displayId != Display.DEFAULT_DISPLAY) {
                activeTargetDisplayId = displayId
            }
            mainHandler.post {
                onDisplayConnected(info)
            }
        }

        override fun onDisplayRemoved(displayId: Int) {
            Log.i(TAG, "Display Disconnected: ID $displayId")
            if (activeTargetDisplayId == displayId) {
                activeTargetDisplayId = Display.DEFAULT_DISPLAY
            }
            mainHandler.post {
                onDisplayDisconnected(displayId)
            }
        }

        override fun onDisplayChanged(displayId: Int) {
            val display = displayManager.getDisplay(displayId) ?: return
            val info = extractDisplayInfo(display)
            mainHandler.post {
                onDisplayChanged?.invoke(info)
            }
        }
    }

    fun startListening() {
        displayManager.registerDisplayListener(displayListener, mainHandler)
        Log.d(TAG, "DisplayListener registered")
    }

    fun stopListening() {
        try {
            displayManager.unregisterDisplayListener(displayListener)
            Log.d(TAG, "DisplayListener unregistered")
        } catch (e: Exception) {
            Log.w(TAG, "Error unregistering display listener: ${e.message}")
        }
    }

    fun setTargetDisplay(displayId: Int) {
        val display = displayManager.getDisplay(displayId)
        if (display != null && display.isValid) {
            activeTargetDisplayId = displayId
            Log.i(TAG, "Active target display set to: $displayId (${display.name})")
        } else {
            activeTargetDisplayId = Display.DEFAULT_DISPLAY
        }
    }

    fun getConnectedDisplays(): List<DisplayInfoModel> {
        val displays = displayManager.displays
        return displays.mapNotNull { display ->
            if (display != null && display.isValid) {
                extractDisplayInfo(display)
            } else null
        }
    }

    fun hasExternalDisplay(): Boolean {
        return getConnectedDisplays().any { it.displayId != Display.DEFAULT_DISPLAY }
    }

    private fun extractDisplayInfo(display: Display): DisplayInfoModel {
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        display.getRealMetrics(metrics)

        return DisplayInfoModel(
            displayId = display.displayId,
            name = display.name ?: "Display #${display.displayId}",
            width = metrics.widthPixels,
            height = metrics.heightPixels,
            refreshRate = display.refreshRate,
            densityDpi = metrics.densityDpi,
            isDefault = display.displayId == Display.DEFAULT_DISPLAY,
            isValid = display.isValid
        )
    }
}
