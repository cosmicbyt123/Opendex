package com.cosmic.opendex.data

import android.graphics.drawable.Drawable

/**
 * Represents an installed Android application in OpenDeX.
 */
data class AppModel(
    val packageName: String,
    val activityName: String,
    val label: String,
    val icon: Drawable,
    val isSystemApp: Boolean = false,
    var isPinnedToTaskbar: Boolean = false
) {
    val componentNameString: String
        get() = "$packageName/$activityName"
}
