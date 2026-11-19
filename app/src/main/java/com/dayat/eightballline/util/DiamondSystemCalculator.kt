package com.dayat.eightballline.util

import com.dayat.eightballline.model.PocketFilter
import com.dayat.eightballline.model.PointF2D
import com.dayat.eightballline.model.TableConfig
import com.dayat.eightballline.model.TableOrientation
import kotlin.math.roundToInt

enum class LineCategory {
    TOP_POCKET,     // Lubang atas (TL/TR di Portrait; TL/TM/TR di Landscape)
    BOTTOM_POCKET,  // Lubang bawah (BL/BR di Portrait; BL/BM/BR di Landscape)
    MIDDLE_OR_GRID, // Lubang tengah kiri/kanan (di Portrait) & garis tengah L4-R4
    KITCHEN_LINE    // Garis kitchen/head-string L2-R2 (Cyan sesuai foto referensi)
}

data class ReferenceSegment(
    val p1: PointF2D,
    val p2: PointF2D,
    val category: LineCategory
)

object DiamondSystemCalculator {

    private enum class NormGroup {
        CENTER_LINE,      // L4 - R4
        KITCHEN_LINE,     // L2 - R2
        TOP_CORNER,       // TL & TR (via opposite long or short rail)
        BOTTOM_CORNER,    // BL & BR (via opposite long or short rail)
        SIDE_POCKET_LEFT, // ML (s=0, l=4) -> di Landscape menjadi TM (Lubang Tengah Atas!)
        SIDE_POCKET_RIGHT // MR (s=4, l=4) -> di Landscape menjadi BM (Lubang Tengah Bawah!)
    }

    private data class NormSeg(
        val s1: Float,
        val l1: Float,
        val s2: Float,
        val l2: Float,
        val group: NormGroup
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

    // 62 segmen pasti sesuai kedua foto referensi, terkelompokkan berdasarkan lubang tujuan
    private val normalizedSegments: List<NormSeg> by lazy {
        val list = ArrayList<NormSeg>(64)
        val seen = HashSet<String>()

        fun add(s1: Float, l1: Float, s2: Float, l2: Float, group: NormGroup) {
            if (seen.add(segKey(s1, l1, s2, l2))) {
                list.add(NormSeg(s1, l1, s2, l2, group))
            }
        }

        // 1. Garis tengah lubang-ke-lubang (L4 - R4) & garis kitchen (L2 - R2)
        add(0f, 4f, 4f, 4f, NormGroup.CENTER_LINE)
        add(0f, 2f, 4f, 2f, NormGroup.KITCHEN_LINE)

        // 2. Pantulan 1-cushion menuju 4 lubang sudut via rel panjang seberang (28 garis)
        for (k in 1..7) {
            val d = k * 0.5f
            add(4f, d, 0f, 2f * d, NormGroup.TOP_CORNER)           // TL via R -> L(2d)
            add(0f, d, 4f, 2f * d, NormGroup.TOP_CORNER)           // TR via L -> R(2d)
            add(4f, 8f - d, 0f, 8f - 2f * d, NormGroup.BOTTOM_CORNER) // BL via R -> L(8-2d)
            add(0f, 8f - d, 4f, 8f - 2f * d, NormGroup.BOTTOM_CORNER) // BR via L -> R(8-2d)
        }

        // 3. Pantulan 1-cushion menuju 2 lubang tengah via rel panjang seberang (16 garis)
        for (k in 1..4) {
            val d = k * 0.5f
            add(4f, 4f - d, 0f, 4f - 2f * d, NormGroup.SIDE_POCKET_LEFT)  // ML (s=0, l=4) via R atas
            add(4f, 4f + d, 0f, 4f + 2f * d, NormGroup.SIDE_POCKET_LEFT)  // ML (s=0, l=4) via R bawah
            add(0f, 4f - d, 4f, 4f - 2f * d, NormGroup.SIDE_POCKET_RIGHT) // MR (s=4, l=4) via L atas
            add(0f, 4f + d, 4f, 4f + 2f * d, NormGroup.SIDE_POCKET_RIGHT) // MR (s=4, l=4) via L bawah
        }

        // 4. Pantulan 1-cushion menuju 4 lubang sudut via rel pendek seberang (16 garis)
        for (k in 1..4) {
            val d = k * 0.5f
            add(d, 0f, 2f * d, 8f, NormGroup.BOTTOM_CORNER)           // BL via T -> B(2d)
            add(4f - d, 0f, 4f - 2f * d, 8f, NormGroup.BOTTOM_CORNER) // BR via T -> B(4-2d)
            add(d, 8f, 2f * d, 0f, NormGroup.TOP_CORNER)              // TL via B -> T(2d)
            add(4f - d, 8f, 4f - 2f * d, 0f, NormGroup.TOP_CORNER)    // TR via B -> T(4-2d)
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
            // Rotasi 90 derajat untuk Landscape: l (0..8) di sumbu X, s (0..4) di sumbu Y
            // sehingga s=0 berada di rel ATAS dan s=4 di rel BAWAH
            PointF2D(
                config.left + (l / 8f) * config.width,
                config.top + (s / 4f) * config.height
            )
        }
    }

    private fun resolveCategory(group: NormGroup, orientation: TableOrientation): LineCategory {
        return when (group) {
            NormGroup.TOP_CORNER -> LineCategory.TOP_POCKET
            NormGroup.BOTTOM_CORNER -> LineCategory.BOTTOM_POCKET
            NormGroup.SIDE_POCKET_LEFT -> {
                if (orientation == TableOrientation.LANDSCAPE) LineCategory.TOP_POCKET
                else LineCategory.MIDDLE_OR_GRID
            }
            NormGroup.SIDE_POCKET_RIGHT -> {
                if (orientation == TableOrientation.LANDSCAPE) LineCategory.BOTTOM_POCKET
                else LineCategory.MIDDLE_OR_GRID
            }
            NormGroup.CENTER_LINE -> LineCategory.MIDDLE_OR_GRID
            NormGroup.KITCHEN_LINE -> LineCategory.KITCHEN_LINE
        }
    }

    fun calculateReferenceSegments(config: TableConfig): List<ReferenceSegment> {
        val filter = config.pocketFilter
        val result = ArrayList<ReferenceSegment>(normalizedSegments.size)

        for (seg in normalizedSegments) {
            val cat = resolveCategory(seg.group, config.orientation)
            val include = when (filter) {
                PocketFilter.ALL -> true
                PocketFilter.TOP_ONLY -> cat == LineCategory.TOP_POCKET || cat == LineCategory.KITCHEN_LINE || cat == LineCategory.MIDDLE_OR_GRID
                PocketFilter.BOTTOM_ONLY -> cat == LineCategory.BOTTOM_POCKET || cat == LineCategory.KITCHEN_LINE || cat == LineCategory.MIDDLE_OR_GRID
            }
            if (!include) continue

            result.add(
                ReferenceSegment(
                    p1 = mapNormPoint(config, seg.s1, seg.l1),
                    p2 = mapNormPoint(config, seg.s2, seg.l2),
                    category = cat
                )
            )
        }

        return result
    }
}
