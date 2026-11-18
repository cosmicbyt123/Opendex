package com.cosmic.opendex.desktop

import com.cosmic.opendex.window.ActiveTask

/**
 * Represents an isolated virtual desktop workspace (like Windows Desktops / Mac Spaces).
 */
data class VirtualDesktop(
    val id: Int,
    var name: String,
    val activeTasks: MutableList<ActiveTask> = mutableListOf()
)
