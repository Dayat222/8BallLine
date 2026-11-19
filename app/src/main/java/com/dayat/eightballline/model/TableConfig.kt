package com.dayat.eightballline.model

enum class PocketFilter(val id: Int, val displayName: String) {
    ALL(0, "Semua"),
    BLUE_ONLY(1, "Biru"),
    RED_ONLY(2, "Merah")
}

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
    var pocketFilter: PocketFilter = PocketFilter.ALL,
    var blueLineColor: Int = 0xFF00D8FF.toInt(), // Biru/Cyan (sesuai foto referensi)
    var redLineColor: Int = 0xFFEF4444.toInt(),  // Merah (sesuai foto referensi)
    var lineWidth: Float = 1.6f,
    var opacity: Int = 225, // 0 - 255
    var showBankShotLines: Boolean = true
) {
    val width: Float get() = (right - left).coerceAtLeast(10f)
    val height: Float get() = (bottom - top).coerceAtLeast(10f)
}
