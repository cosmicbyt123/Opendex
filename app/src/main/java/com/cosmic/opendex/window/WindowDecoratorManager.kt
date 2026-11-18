package com.cosmic.opendex.window

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.provider.Settings
import android.util.DisplayMetrics
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import com.cosmic.opendex.R

/**
 * Manages floating window title bar decorators on top of AOSP Freeform windows.
 * Provides Minimize, Maximize/Restore, Close, Snap Left/Right, and drag repositioning.
 */
class WindowDecoratorManager(
    private val context: Context,
    private val onSnap: (ActiveTask, SnapPosition) -> Unit,
    private val onMinimize: (ActiveTask) -> Unit,
    private val onMaximizeToggle: (ActiveTask) -> Unit,
    private val onClose: (ActiveTask) -> Unit,
    private val onBoundsMoved: (ActiveTask, Rect) -> Unit
) {
    private val TAG = "OpenDeX:Decorator"
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val activeViews = mutableMapOf<String, View>()
    private val titleBarHeightPx = (38 * context.resources.displayMetrics.density).toInt()

    fun canDrawOverlays(): Boolean {
        return Settings.canDrawOverlays(context)
    }

    /**
     * Shows or updates the floating title bar for an active task.
     */
    @SuppressLint("ClickableViewAccessibility")
    fun showOrUpdateDecorator(task: ActiveTask) {
        if (!canDrawOverlays()) {
            Log.d(TAG, "Overlay permission not granted; titlebar decorator suppressed")
            return
        }

        if (task.isMinimized) {
            hideDecorator(task.id)
            return
        }

        val existingView = activeViews[task.id]
        if (existingView != null) {
            updateViewPosition(existingView, task)
            updateDecoratorButtons(existingView, task)
            return
        }

        // Inflate new title bar
        val view = LayoutInflater.from(context).inflate(R.layout.view_window_decorator, null)
        val iconView = view.findViewById<ImageView>(R.id.windowAppIcon)
        val titleView = view.findViewById<TextView>(R.id.windowTitleText)
        val snapLeftBtn = view.findViewById<ImageView>(R.id.btnSnapLeft)
        val snapRightBtn = view.findViewById<ImageView>(R.id.btnSnapRight)
        val minimizeBtn = view.findViewById<ImageView>(R.id.btnMinimize)
        val maxRestoreBtn = view.findViewById<ImageView>(R.id.btnMaximizeRestore)
        val closeBtn = view.findViewById<ImageView>(R.id.btnClose)

        iconView.setImageDrawable(task.app.icon)
        titleView.text = task.app.label
        updateDecoratorButtons(view, task)

        // Button Click Listeners
        snapLeftBtn.setOnClickListener { onSnap(task, SnapPosition.LEFT_HALF) }
        snapRightBtn.setOnClickListener { onSnap(task, SnapPosition.RIGHT_HALF) }
        minimizeBtn.setOnClickListener { onMinimize(task) }
        maxRestoreBtn.setOnClickListener { onMaximizeToggle(task) }
        closeBtn.setOnClickListener { onClose(task) }

        // Layout Parameters
        val params = createWindowLayoutParams(task.bounds)

        // Drag handling for moving window and snapping
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        titleView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    params.x = (initialX + dx).coerceAtLeast(0)
                    params.y = (initialY + dy).coerceAtLeast(0)
                    try {
                        windowManager.updateViewLayout(view, params)
                    } catch (e: Exception) {
                        Log.w(TAG, "Error updating dragged decorator: ${e.message}")
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    // Check edge snapping
                    val metrics = DisplayMetrics()
                    @Suppress("DEPRECATION")
                    windowManager.defaultDisplay.getRealMetrics(metrics)
                    val screenWidth = metrics.widthPixels

                    when {
                        event.rawY < 60 -> {
                            // Dragged to top edge -> Snap Fullscreen
                            onSnap(task, SnapPosition.FULLSCREEN)
                        }
                        event.rawX < 80 -> {
                            // Dragged to left edge -> Snap Left Half
                            onSnap(task, SnapPosition.LEFT_HALF)
                        }
                        event.rawX > screenWidth - 80 -> {
                            // Dragged to right edge -> Snap Right Half
                            onSnap(task, SnapPosition.RIGHT_HALF)
                        }
                        else -> {
                            // Moved to custom position
                            val newLeft = params.x
                            val newTop = params.y + titleBarHeightPx
                            val newRight = newLeft + task.bounds.width()
                            val newBottom = newTop + task.bounds.height()
                            val newBounds = Rect(newLeft, newTop, newRight, newBottom)
                            task.bounds = newBounds
                            onBoundsMoved(task, newBounds)
                        }
                    }
                    true
                }
                else -> false
            }
        }

        try {
            windowManager.addView(view, params)
            activeViews[task.id] = view
            Log.d(TAG, "Added titlebar decorator for ${task.app.label} at (${params.x}, ${params.y})")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add window decorator overlay: ${e.message}")
        }
    }

    private fun updateDecoratorButtons(view: View, task: ActiveTask) {
        val maxRestoreBtn = view.findViewById<ImageView>(R.id.btnMaximizeRestore)
        if (task.isMaximized) {
            maxRestoreBtn.setImageResource(R.drawable.ic_window_restore)
            maxRestoreBtn.contentDescription = context.getString(R.string.restore)
        } else {
            maxRestoreBtn.setImageResource(R.drawable.ic_window_maximize)
            maxRestoreBtn.contentDescription = context.getString(R.string.maximize)
        }
    }

    private fun updateViewPosition(view: View, task: ActiveTask) {
        val params = view.layoutParams as? WindowManager.LayoutParams ?: return
        params.x = task.bounds.left.coerceAtLeast(0)
        params.y = (task.bounds.top - titleBarHeightPx).coerceAtLeast(0)
        params.width = task.bounds.width().coerceAtLeast(300)
        params.height = titleBarHeightPx
        try {
            windowManager.updateViewLayout(view, params)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to update decorator layout: ${e.message}")
        }
    }

    fun hideDecorator(taskId: String) {
        val view = activeViews[taskId] ?: return
        view.visibility = View.GONE
    }

    fun removeDecorator(taskId: String) {
        val view = activeViews.remove(taskId) ?: return
        try {
            windowManager.removeView(view)
            Log.d(TAG, "Removed titlebar decorator for task $taskId")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to remove decorator view: ${e.message}")
        }
    }

    fun removeAllDecorators() {
        for ((_, view) in activeViews) {
            try {
                windowManager.removeView(view)
            } catch (e: Exception) {
                // ignore
            }
        }
        activeViews.clear()
    }

    private fun createWindowLayoutParams(bounds: Rect): WindowManager.LayoutParams {
        val type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY

        return WindowManager.LayoutParams(
            bounds.width().coerceAtLeast(300),
            titleBarHeightPx,
            bounds.left.coerceAtLeast(0),
            (bounds.top - titleBarHeightPx).coerceAtLeast(0),
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }
    }
}
