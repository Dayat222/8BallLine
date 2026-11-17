package com.dayat.eightballline.model

enum class TableOrientation(val displayName: String) {
    LANDSCAPE("Landscape (Standar 8BP)"),
    PORTRAIT("Portrait (Tegak)")
}

data class PointF2D(val x: Float, val y: Float)

data class TableConfig(
    var left: Float = 0f,
    var top: Float = 0f,
    var right: Float = 0f,
    var bottom: Float = 0f,
    var orientation: TableOrientation = TableOrientation.LANDSCAPE,
    var lineColor: Int = 0xFF111827.toInt(),          // Hitam (sesuai foto referensi)
    var secondaryLineColor: Int = 0xFF0D9488.toInt(), // Teal (sesuai foto referensi)
    var lineWidth: Float = 1.6f,
    var opacity: Int = 230, // 0 - 255
    var showBankShotLines: Boolean = true
) {
    val width: Float get() = (right - left).coerceAtLeast(10f)
    val height: Float get() = (bottom - top).coerceAtLeast(10f)
}
