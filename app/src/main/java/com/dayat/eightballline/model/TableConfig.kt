package com.dayat.eightballline.model

enum class Pocket(val id: Int, val displayName: String) {
    ALL(0, "Semua Lubang"),
    BOTTOM_LEFT(1, "Kiri Bawah"),
    TOP_LEFT(2, "Kiri Atas"),
    BOTTOM_RIGHT(3, "Kanan Bawah"),
    TOP_RIGHT(4, "Kanan Atas"),
    MIDDLE_LEFT(5, "Tengah Atas / Kiri"),
    MIDDLE_RIGHT(6, "Tengah Bawah / Kanan")
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
    var activePocket: Pocket = Pocket.ALL,
    var lineColor: Int = 0xFFFFFFFF.toInt(),          // Garis utama (Putih terang)
    var secondaryLineColor: Int = 0xFF14B8A6.toInt(), // Garis sekunder (Teal seperti foto referensi)
    var lineWidth: Float = 1.6f,
    var opacity: Int = 215, // 0 - 255
    var showBankShotLines: Boolean = true,
    var showSecondaryLines: Boolean = true
) {
    val width: Float get() = (right - left).coerceAtLeast(10f)
    val height: Float get() = (bottom - top).coerceAtLeast(10f)
}
