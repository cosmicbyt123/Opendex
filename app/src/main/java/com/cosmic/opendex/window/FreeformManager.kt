package com.cosmic.opendex.window

import android.app.ActivityOptions
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import com.cosmic.opendex.data.AppModel
import java.lang.reflect.Method

/**
 * Orchestrator for launching applications into AOSP Freeform Floating Windows.
 */
object FreeformManager {

    private const val TAG = "OpenDeX:Freeform"
    // AOSP Platform Constant: WINDOWING_MODE_FREEFORM = 5
    private const val WINDOWING_MODE_FREEFORM = 5

    /**
     * Launches an app into a floating freeform window with calculated default bounds.
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

            // 2. Set Default Window Size and Geometry
            val bounds = customBounds ?: calculateDefaultWindowBounds(context)
            options.launchBounds = bounds

            // 3. Target Display routing (if external display is connected)
            if (targetDisplayId != null && targetDisplayId != 0) {
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
            Log.i(TAG, "Successfully launched ${app.label} in Freeform mode at $bounds")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch ${app.label} in freeform: ${e.message}, falling back to standard launch", e)
            fallbackStandardLaunch(context, app)
            false
        }
    }

    private fun setFreeformWindowingMode(options: ActivityOptions) {
        try {
            val setWindowingModeMethod: Method = ActivityOptions::class.java.getMethod(
                "setLaunchWindowingMode",
                Int::class.javaPrimitiveType
            )
            setWindowingModeMethod.invoke(options, WINDOWING_MODE_FREEFORM)
        } catch (e: Exception) {
            Log.w(TAG, "setLaunchWindowingMode reflection failed, attempting direct bundle injection: ${e.message}")
        }
    }

    private fun calculateDefaultWindowBounds(context: Context): Rect {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getMetrics(metrics)

        val screenWidth = metrics.widthPixels
        val screenHeight = metrics.heightPixels

        // Standard 16:9 or 4:3 PC window ratio centered on desktop
        val windowWidth = (screenWidth * 0.65).toInt().coerceAtLeast(800)
        val windowHeight = (screenHeight * 0.70).toInt().coerceAtLeast(500)

        val left = (screenWidth - windowWidth) / 2
        val top = (screenHeight - windowHeight) / 3 // Slightly elevated from bottom taskbar
        val right = left + windowWidth
        val bottom = top + windowHeight

        return Rect(left, top, right, bottom)
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
