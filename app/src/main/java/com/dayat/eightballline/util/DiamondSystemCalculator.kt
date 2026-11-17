package com.dayat.eightballline.util

import com.dayat.eightballline.model.PointF2D
import com.dayat.eightballline.model.TableConfig
import com.dayat.eightballline.model.TableOrientation
import kotlin.math.roundToInt

data class ReferenceSegment(
    val p1: PointF2D,
    val p2: PointF2D,
    val isSecondary: Boolean
)

object DiamondSystemCalculator {

    private data class NormSeg(val s1: Float, val l1: Float, val s2: Float, val l2: Float)

    private fun segKey(s1: Float, l1: Float, s2: Float, l2: Float): String {
        val a1 = (s1 * 2f).roundToInt()
        val b1 = (l1 * 2f).roundToInt()
        val a2 = (s2 * 2f).roundToInt()
        val b2 = (l2 * 2f).roundToInt()
        return if (a1 < a2 || (a1 == a2 && b1 <= b2)) {
            "$a1,$b1-$a2,$b2"
        } else {
            "$a2,$b2-$a1,$b1"
        }
    }

    // 11 segmen yang digambar dengan warna sekunder (teal) pada foto referensi
    private val secondaryKeys: Set<String> = setOf(
        segKey(0f, 2.0f, 4f, 2.0f), // L2.0 - R2.0 (head string)
        segKey(0f, 2.5f, 4f, 1.0f), // L2.5 - R1.0
        segKey(0f, 3.5f, 4f, 3.0f), // L3.5 - R3.0
        segKey(0f, 4.5f, 4f, 5.0f), // L4.5 - R5.0
        segKey(0f, 3.0f, 4f, 6.0f), // L3.0 - R6.0
        segKey(1f, 0.0f, 0.5f, 8f), // T1.0 - B0.5
        segKey(1f, 0.0f, 2.5f, 8f), // T1.0 - B2.5
        segKey(2f, 0.0f, 1.0f, 8f), // T2.0 - B1.0
        segKey(2f, 0.0f, 3.0f, 8f), // T2.0 - B3.0
        segKey(3f, 0.0f, 1.5f, 8f), // T3.0 - B1.5
        segKey(3f, 0.0f, 3.5f, 8f)  // T3.0 - B3.5
    )

    // 62 segmen pasti sesuai hasil ekstraksi langsung dari foto referensi
    private val normalizedSegments: List<NormSeg> by lazy {
        val list = ArrayList<NormSeg>(64)
        val seen = HashSet<String>()

        fun add(s1: Float, l1: Float, s2: Float, l2: Float) {
            if (seen.add(segKey(s1, l1, s2, l2))) {
                list.add(NormSeg(s1, l1, s2, l2))
            }
        }

        // 1. Garis tengah lubang-ke-lubang (L4 - R4) & garis kitchen (L2 - R2)
        add(0f, 4f, 4f, 4f)
        add(0f, 2f, 4f, 2f)

        // 2. Pantulan 1-cushion menuju 4 lubang sudut via rel panjang seberang (28 garis)
        for (k in 1..7) {
            val d = k * 0.5f
            add(4f, d, 0f, 2f * d)             // TL via R -> L(2d)
            add(0f, d, 4f, 2f * d)             // TR via L -> R(2d)
            add(4f, 8f - d, 0f, 8f - 2f * d)   // BL via R -> L(8-2d)
            add(0f, 8f - d, 4f, 8f - 2f * d)   // BR via L -> R(8-2d)
        }

        // 3. Pantulan 1-cushion menuju 2 lubang tengah via rel panjang seberang (16 garis)
        for (k in 1..4) {
            val d = k * 0.5f
            add(4f, 4f - d, 0f, 4f - 2f * d)   // Tengah kiri via R atas -> L
            add(4f, 4f + d, 0f, 4f + 2f * d)   // Tengah kiri via R bawah -> L
            add(0f, 4f - d, 4f, 4f - 2f * d)   // Tengah kanan via L atas -> R
            add(0f, 4f + d, 4f, 4f + 2f * d)   // Tengah kanan via L bawah -> R
        }

        // 4. Pantulan 1-cushion menuju 4 lubang sudut via rel pendek seberang (16 garis)
        for (k in 1..4) {
            val d = k * 0.5f
            add(d, 0f, 2f * d, 8f)             // BL via T -> B(2d)
            add(4f - d, 0f, 4f - 2f * d, 8f)   // BR via T -> B(4-2d)
            add(d, 8f, 2f * d, 0f)             // TL via B -> T(2d)
            add(4f - d, 8f, 4f - 2f * d, 0f)   // TR via B -> T(4-2d)
        }

        list
    }

    /**
     * Titik diamond utama pada rel meja (digunakan saat Mode Kalibrasi).
     */
    fun getDiamondPoints(config: TableConfig): Map<String, List<PointF2D>> {
        val l = config.left
        val t = config.top
        val r = config.right
        val b = config.bottom
        val w = config.width
        val h = config.height

        val result = mutableMapOf<String, List<PointF2D>>()

        if (config.orientation == TableOrientation.LANDSCAPE) {
            result["top"] = (0..8).map { i -> PointF2D(l + (w / 8f) * i, t) }
            result["bottom"] = (0..8).map { i -> PointF2D(l + (w / 8f) * i, b) }
            result["left"] = (0..4).map { i -> PointF2D(l, t + (h / 4f) * i) }
            result["right"] = (0..4).map { i -> PointF2D(r, t + (h / 4f) * i) }
        } else {
            result["left"] = (0..8).map { i -> PointF2D(l, t + (h / 8f) * i) }
            result["right"] = (0..8).map { i -> PointF2D(r, t + (h / 8f) * i) }
            result["top"] = (0..4).map { i -> PointF2D(l + (w / 4f) * i, t) }
            result["bottom"] = (0..4).map { i -> PointF2D(l + (w / 4f) * i, b) }
        }

        return result
    }

    private fun mapNormPoint(config: TableConfig, s: Float, l: Float): PointF2D {
        return if (config.orientation == TableOrientation.PORTRAIT) {
            PointF2D(
                config.left + (s / 4f) * config.width,
                config.top + (l / 8f) * config.height
            )
        } else {
            PointF2D(
                config.left + (l / 8f) * config.width,
                config.top + (s / 4f) * config.height
            )
        }
    }

    fun calculateReferenceSegments(config: TableConfig): List<ReferenceSegment> {
        return normalizedSegments.map { seg ->
            val isSec = secondaryKeys.contains(segKey(seg.s1, seg.l1, seg.s2, seg.l2))
            ReferenceSegment(
                p1 = mapNormPoint(config, seg.s1, seg.l1),
                p2 = mapNormPoint(config, seg.s2, seg.l2),
                isSecondary = isSec
            )
        }
    }
}
