package com.cosmic.opendex.display

data class DisplayInfoModel(
    val displayId: Int,
    val name: String,
    val width: Int,
    val height: Int,
    val refreshRate: Float,
    val densityDpi: Int,
    val isDefault: Boolean,
    val isValid: Boolean
) {
    val resolutionString: String
        get() = "${width}x${height} @ ${refreshRate.toInt()}Hz (${densityDpi} DPI)"
}
