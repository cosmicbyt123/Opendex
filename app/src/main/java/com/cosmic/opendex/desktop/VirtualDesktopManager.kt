package com.cosmic.opendex.desktop

/**
 * Manages multiple virtual desktop workspaces (Windows Task View / Desktops).
 */
class VirtualDesktopManager {

    private val desktops = mutableListOf<VirtualDesktop>()
    var activeIndex: Int = 0
        private set

    init {
        // Default with Desktop 1 and Desktop 2
        desktops.add(VirtualDesktop(1, "Desktop 1"))
        desktops.add(VirtualDesktop(2, "Desktop 2"))
    }

    fun getDesktops(): List<VirtualDesktop> = desktops.toList()

    fun getCurrentDesktop(): VirtualDesktop = desktops[activeIndex]

    fun switchDesktop(index: Int): VirtualDesktop {
        val safeIndex = index.coerceIn(0, desktops.size - 1)
        activeIndex = safeIndex
        return desktops[safeIndex]
    }

    fun addDesktop(name: String? = null): VirtualDesktop {
        val newId = desktops.size + 1
        val desktopName = name ?: "Desktop $newId"
        val newDesktop = VirtualDesktop(newId, desktopName)
        desktops.add(newDesktop)
        return newDesktop
    }

    fun removeDesktop(index: Int): Boolean {
        if (desktops.size <= 1) return false
        desktops.removeAt(index)
        if (activeIndex >= desktops.size) {
            activeIndex = desktops.size - 1
        }
        return true
    }
}
