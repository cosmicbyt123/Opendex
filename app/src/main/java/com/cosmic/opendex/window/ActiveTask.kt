package com.cosmic.opendex.window

import android.graphics.Rect
import com.cosmic.opendex.data.AppModel

/**
 * Tracks the state and geometry of an active freeform window.
 */
data class ActiveTask(
    val app: AppModel,
    var bounds: Rect = Rect(),
    var snapPosition: SnapPosition = SnapPosition.NONE,
    var isMinimized: Boolean = false,
    var isMaximized: Boolean = false,
    var isPinnedOnly: Boolean = false,
    var lastRestoredBounds: Rect? = null,
    var lastActiveTime: Long = System.currentTimeMillis()
) {
    val id: String
        get() = app.componentNameString
}
