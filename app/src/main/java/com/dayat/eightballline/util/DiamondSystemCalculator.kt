package com.dayat.eightballline.util

import com.dayat.eightballline.model.PocketFilter
import com.dayat.eightballline.model.PointF2D
import com.dayat.eightballline.model.TableConfig
import com.dayat.eightballline.model.TableOrientation
import kotlin.math.roundToInt

enum class LineCategory {
    BLUE, // Garis Biru sesuai foto referensi
    RED   // Garis Merah sesuai foto referensi
}

data class ReferenceSegment(
    val p1: PointF2D,
    val p2: PointF2D,
    val category: LineCategory
)

object DiamondSystemCalculator {

    private data class NormSeg(
        val s1: Float,
        val l1: Float,
        val s2: Float,
        val l2: Float,
        val category: LineCategory
    )

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

    // 62 segmen pasti dengan 2 warna persis seperti foto referensi (Biru & Merah)
    private val normalizedSegments: List<NormSeg> by lazy {
        val list = ArrayList<NormSeg>(64)
        val seen = HashSet<String>()

        fun add(s1: Float, l1: Float, s2: Float, l2: Float, cat: LineCategory) {
            if (seen.add(segKey(s1, l1, s2, l2))) {
                list.add(NormSeg(s1, l1, s2, l2, cat))
            }
        }

        // 1. Garis tengah lubang-ke-lubang (L4 - R4) & garis kitchen (L2 - R2): BIRU
        add(0f, 4f, 4f, 4f, LineCategory.BLUE)
        add(0f, 2f, 4f, 2f, LineCategory.BLUE)

        // 2. Pantulan 1-cushion menuju 4 lubang sudut via rel panjang seberang (28 garis)
        //    Memantul dari rel Kanan (s=4) -> MERAH; dari rel Kiri (s=0) -> BIRU
        for (k in 1..7) {
            val d = k * 0.5f
            add(4f, d, 0f, 2f * d, LineCategory.RED)           // TL via R -> MERAH
            add(0f, d, 4f, 2f * d, LineCategory.BLUE)          // TR via L -> BIRU
            add(4f, 8f - d, 0f, 8f - 2f * d, LineCategory.RED) // BL via R -> MERAH
            add(0f, 8f - d, 4f, 8f - 2f * d, LineCategory.BLUE)// BR via L -> BIRU
        }

        // 3. Pantulan 1-cushion menuju 2 lubang tengah via rel panjang seberang (16 garis)
        //    Memantul dari rel Kanan (s=4) -> MERAH; dari rel Kiri (s=0) -> BIRU
        for (k in 1..4) {
            val d = k * 0.5f
            add(4f, 4f - d, 0f, 4f - 2f * d, LineCategory.RED) // ML via R atas -> MERAH
            add(4f, 4f + d, 0f, 4f + 2f * d, LineCategory.RED) // ML via R bawah -> MERAH
            add(0f, 4f - d, 4f, 4f - 2f * d, LineCategory.BLUE)// MR via L atas -> BIRU
            add(0f, 4f + d, 4f, 4f + 2f * d, LineCategory.BLUE)// MR via L bawah -> BIRU
        }

        // 4. Pantulan 1-cushion menuju 4 lubang sudut via rel pendek seberang (16 garis)
        //    Memantul dari rel Atas (l=0) -> MERAH; dari rel Bawah (l=8) -> BIRU
        for (k in 1..4) {
            val d = k * 0.5f
            add(d, 0f, 2f * d, 8f, LineCategory.RED)           // BL via T -> MERAH
            add(4f - d, 0f, 4f - 2f * d, 8f, LineCategory.RED) // BR via T -> MERAH
            add(d, 8f, 2f * d, 0f, LineCategory.BLUE)          // TL via B -> BIRU
            add(4f - d, 8f, 4f - 2f * d, 0f, LineCategory.BLUE)// TR via B -> BIRU
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
            // Rotasi 90° berlawanan arah jarum jam:
            // Garis Biru mengarah ke lubang ATAS (s=4 -> top) & KIRI; Merah mengarah ke lubang BAWAH (s=0 -> bottom) & KANAN
            PointF2D(
                config.left + (l / 8f) * config.width,
                config.bottom - (s / 4f) * config.height
            )
        }
    }

    fun calculateReferenceSegments(config: TableConfig): List<ReferenceSegment> {
        val filter = config.pocketFilter
        val result = ArrayList<ReferenceSegment>(normalizedSegments.size)

        for (seg in normalizedSegments) {
            val include = when (filter) {
                PocketFilter.ALL -> true
                PocketFilter.BLUE_ONLY -> seg.category == LineCategory.BLUE
                PocketFilter.RED_ONLY -> seg.category == LineCategory.RED
            }
            if (!include) continue

            result.add(
                ReferenceSegment(
                    p1 = mapNormPoint(config, seg.s1, seg.l1),
                    p2 = mapNormPoint(config, seg.s2, seg.l2),
                    category = seg.category
                )
            )
        }

        return result
    }
}
