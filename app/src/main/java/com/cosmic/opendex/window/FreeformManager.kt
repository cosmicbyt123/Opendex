package com.cosmic.opendex.window

import android.app.ActivityOptions
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.util.DisplayMetrics
import android.util.Log
import android.view.Display
import android.view.WindowManager
import com.cosmic.opendex.data.AppModel
import java.lang.reflect.Method

/**
 * Orchestrator for launching applications into AOSP Freeform Floating Windows
 * and computing grid snap geometry (tiling, halves, quadrants, fullscreen).
 */
object FreeformManager {

    private const val TAG = "OpenDeX:Freeform"
    // AOSP Platform Constant: WINDOWING_MODE_FREEFORM = 5
    const val WINDOWING_MODE_FREEFORM = 5

    /**
     * Launches an app into a floating freeform window with calculated or custom bounds.
     */
    fun launchAppInFreeform(
        context: Context,
        app: AppModel,
        targetDisplayId: Int? = null,
        customBounds: Rect? = null
    ): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                component = ComponentName(app.packageName, app.activityName)
                addCategory(Intent.CATEGORY_LAUNCHER)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_MULTIPLE_TASK
            }

            val options = ActivityOptions.makeBasic()

            // 1. Hook into AOSP Platform setLaunchWindowingMode(5)
            setFreeformWindowingMode(options)

            // 2. Set Window Size and Geometry
            val bounds = customBounds ?: calculateDefaultWindowBounds(context, targetDisplayId)
            options.launchBounds = bounds

            // 3. Target Display routing (if external display is connected)
            if (targetDisplayId != null && targetDisplayId != Display.DEFAULT_DISPLAY) {
                try {
                    val setLaunchDisplayIdMethod: Method = ActivityOptions::class.java.getMethod(
                        "setLaunchDisplayId",
                        Int::class.javaPrimitiveType
                    )
                    setLaunchDisplayIdMethod.invoke(options, targetDisplayId)
                } catch (e: Exception) {
                    Log.w(TAG, "setLaunchDisplayId reflection failed: ${e.message}")
                }
            }

            context.startActivity(intent, options.toBundle())
            Log.i(TAG, "Launched ${app.label} in Freeform mode at $bounds on display $targetDisplayId")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch ${app.label} in freeform: ${e.message}, falling back to standard launch", e)
            fallbackStandardLaunch(context, app)
            false
        }
    }

    /**
     * Snaps an app to a specific desktop grid layout (Left Half, Right Half, Fullscreen, etc.)
     */
    fun snapApp(
        context: Context,
        app: AppModel,
        snap: SnapPosition,
        taskbarHeightPx: Int = 0,
        targetDisplayId: Int? = null
    ): Rect {
        val bounds = calculateSnapBounds(context, snap, taskbarHeightPx, targetDisplayId)
        launchAppInFreeform(context, app, targetDisplayId, bounds)
        return bounds
    }

    /**
     * Calculates the bounding Rect for a specific snap position.
     */
    fun calculateSnapBounds(
        context: Context,
        snap: SnapPosition,
        taskbarHeightPx: Int = 0,
        targetDisplayId: Int? = null
    ): Rect {
        val display = getTargetDisplay(context, targetDisplayId)
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        display.getRealMetrics(metrics)

        val screenWidth = metrics.widthPixels
        val screenHeight = metrics.heightPixels
        val availableHeight = (screenHeight - taskbarHeightPx).coerceAtLeast(300)

        return when (snap) {
            SnapPosition.LEFT_HALF -> {
                Rect(0, 0, screenWidth / 2, availableHeight)
            }
            SnapPosition.RIGHT_HALF -> {
                Rect(screenWidth / 2, 0, screenWidth, availableHeight)
            }
            SnapPosition.FULLSCREEN -> {
                Rect(0, 0, screenWidth, availableHeight)
            }
            SnapPosition.TOP_LEFT -> {
                Rect(0, 0, screenWidth / 2, availableHeight / 2)
            }
            SnapPosition.TOP_RIGHT -> {
                Rect(screenWidth / 2, 0, screenWidth, availableHeight / 2)
            }
            SnapPosition.BOTTOM_LEFT -> {
                Rect(0, availableHeight / 2, screenWidth / 2, availableHeight)
            }
            SnapPosition.BOTTOM_RIGHT -> {
                Rect(screenWidth / 2, availableHeight / 2, screenWidth, availableHeight)
            }
            SnapPosition.CENTER_FLOAT, SnapPosition.NONE -> {
                calculateDefaultWindowBounds(context, targetDisplayId, taskbarHeightPx)
            }
        }
    }

    /**
     * Calculates a centered floating window Rect.
     */
    fun calculateDefaultWindowBounds(
        context: Context,
        targetDisplayId: Int? = null,
        taskbarHeightPx: Int = 0
    ): Rect {
        val display = getTargetDisplay(context, targetDisplayId)
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        display.getRealMetrics(metrics)

        val screenWidth = metrics.widthPixels
        val screenHeight = metrics.heightPixels
        val availableHeight = (screenHeight - taskbarHeightPx).coerceAtLeast(300)

        // Centered window: 65% width, 72% available height
        val windowWidth = (screenWidth * 0.65).toInt().coerceIn(600, screenWidth)
        val windowHeight = (availableHeight * 0.72).toInt().coerceIn(400, availableHeight)

        val left = (screenWidth - windowWidth) / 2
        val top = (availableHeight - windowHeight) / 3 // Slightly higher for visual balance
        val right = left + windowWidth
        val bottom = top + windowHeight

        return Rect(left, top, right, bottom)
    }

    private fun getTargetDisplay(context: Context, targetDisplayId: Int?): Display {
        val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        if (targetDisplayId != null && targetDisplayId != Display.DEFAULT_DISPLAY) {
            val customDisplay = displayManager.getDisplay(targetDisplayId)
            if (customDisplay != null) return customDisplay
        }
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        @Suppress("DEPRECATION")
        return wm.defaultDisplay
    }

    private fun setFreeformWindowingMode(options: ActivityOptions) {
        try {
            val setWindowingModeMethod: Method = ActivityOptions::class.java.getMethod(
                "setLaunchWindowingMode",
                Int::class.javaPrimitiveType
            )
            setWindowingModeMethod.invoke(options, WINDOWING_MODE_FREEFORM)
        } catch (e: Exception) {
            Log.w(TAG, "setLaunchWindowingMode reflection failed: ${e.message}")
        }
    }

    private fun fallbackStandardLaunch(context: Context, app: AppModel) {
        try {
            val intent = context.packageManager.getLaunchIntentForPackage(app.packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fallback launch also failed: ${e.message}")
        }
    }
}
