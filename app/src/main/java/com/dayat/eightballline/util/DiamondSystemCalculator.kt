package com.dayat.eightballline.util

import com.dayat.eightballline.model.PointF2D
import com.dayat.eightballline.model.TableConfig
import com.dayat.eightballline.model.TableOrientation
import kotlin.math.abs
import kotlin.math.roundToInt

data class ReferenceSegment(
    val p1: PointF2D,
    val p2: PointF2D,
    val isSecondary: Boolean
)

object DiamondSystemCalculator {

    private enum class RailEdge {
        TOP, BOTTOM, LEFT, RIGHT
    }

    /**
     * Titik diamond utama pada rel meja:
     * Long rail: 8 segmen (0..8 -> titik 1..3, lubang tengah = 4, titik 5..7).
     * Short rail: 4 segmen (0..4 -> titik 1..3).
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

    private fun getLongRailHalfPoints(railPoints: List<PointF2D>): List<PointF2D> {
        val midPoints = mutableListOf<PointF2D>()
        for (i in 1 until railPoints.size - 2) {
            val p1 = railPoints[i]
            val p2 = railPoints[i + 1]
            midPoints.add(PointF2D((p1.x + p2.x) / 2f, (p1.y + p2.y) / 2f))
        }
        return midPoints
    }

    fun getCenterCrossLine(config: TableConfig): Pair<PointF2D, PointF2D> {
        return if (config.orientation == TableOrientation.LANDSCAPE) {
            val midX = config.left + config.width / 2f
            PointF2D(midX, config.top) to PointF2D(midX, config.bottom)
        } else {
            val midY = config.top + config.height / 2f
            PointF2D(config.left, midY) to PointF2D(config.right, midY)
        }
    }

    /**
     * Menghasilkan seluruh segmen garis persis seperti foto referensi (tanpa duplikasi garis simetris):
     * - Utama (Hitam): garis tengah lurus + pantulan titik 1..7 long rail ke 4 lubang sudut.
     * - Sekunder (Teal): pantulan titik tengah 1.5..6.5 long rail + pantulan titik 1..3 short rail ke 4 lubang sudut.
     */
    fun calculateReferenceSegments(config: TableConfig): List<ReferenceSegment> {
        val segments = mutableListOf<ReferenceSegment>()
        val seenKeys = HashSet<String>()
        val diamonds = getDiamondPoints(config)

        val l = config.left
        val t = config.top
        val r = config.right
        val b = config.bottom

        val topLeft = PointF2D(l, t)
        val topRight = PointF2D(r, t)
        val bottomLeft = PointF2D(l, b)
        val bottomRight = PointF2D(r, b)

        if (config.orientation == TableOrientation.LANDSCAPE) {
            val topRail = diamonds["top"] ?: return segments
            val bottomRail = diamonds["bottom"] ?: return segments
            val leftRail = diamonds["left"] ?: return segments
            val rightRail = diamonds["right"] ?: return segments

            // Garis utama: long rail (1..7)
            for (pocketPos in listOf(topLeft, topRight)) {
                addRailBounces(segments, seenKeys, config, pocketPos, bottomRail.subList(1, 8), RailEdge.BOTTOM, isSecondary = false)
            }
            for (pocketPos in listOf(bottomLeft, bottomRight)) {
                addRailBounces(segments, seenKeys, config, pocketPos, topRail.subList(1, 8), RailEdge.TOP, isSecondary = false)
            }

            // Garis sekunder: long rail half-diamonds (1.5..6.5) & short rail (1..3)
            for (pocketPos in listOf(topLeft, topRight)) {
                addRailBounces(segments, seenKeys, config, pocketPos, getLongRailHalfPoints(bottomRail), RailEdge.BOTTOM, isSecondary = true)
            }
            for (pocketPos in listOf(bottomLeft, bottomRight)) {
                addRailBounces(segments, seenKeys, config, pocketPos, getLongRailHalfPoints(topRail), RailEdge.TOP, isSecondary = true)
            }
            for (pocketPos in listOf(topLeft, bottomLeft)) {
                addRailBounces(segments, seenKeys, config, pocketPos, rightRail.subList(1, 4), RailEdge.RIGHT, isSecondary = true)
            }
            for (pocketPos in listOf(topRight, bottomRight)) {
                addRailBounces(segments, seenKeys, config, pocketPos, leftRail.subList(1, 4), RailEdge.LEFT, isSecondary = true)
            }
        } else {
            val leftRail = diamonds["left"] ?: return segments
            val rightRail = diamonds["right"] ?: return segments
            val topRail = diamonds["top"] ?: return segments
            val bottomRail = diamonds["bottom"] ?: return segments

            // Garis utama: long rail (1..7)
            for (pocketPos in listOf(topLeft, bottomLeft)) {
                addRailBounces(segments, seenKeys, config, pocketPos, rightRail.subList(1, 8), RailEdge.RIGHT, isSecondary = false)
            }
            for (pocketPos in listOf(topRight, bottomRight)) {
                addRailBounces(segments, seenKeys, config, pocketPos, leftRail.subList(1, 8), RailEdge.LEFT, isSecondary = false)
            }

            // Garis sekunder: long rail half-diamonds (1.5..6.5) & short rail (1..3)
            for (pocketPos in listOf(topLeft, bottomLeft)) {
                addRailBounces(segments, seenKeys, config, pocketPos, getLongRailHalfPoints(rightRail), RailEdge.RIGHT, isSecondary = true)
            }
            for (pocketPos in listOf(topRight, bottomRight)) {
                addRailBounces(segments, seenKeys, config, pocketPos, getLongRailHalfPoints(leftRail), RailEdge.LEFT, isSecondary = true)
            }
            for (pocketPos in listOf(topLeft, topRight)) {
                addRailBounces(segments, seenKeys, config, pocketPos, bottomRail.subList(1, 4), RailEdge.BOTTOM, isSecondary = true)
            }
            for (pocketPos in listOf(bottomLeft, bottomRight)) {
                addRailBounces(segments, seenKeys, config, pocketPos, topRail.subList(1, 4), RailEdge.TOP, isSecondary = true)
            }
        }

        return segments
    }

    private fun addUniqueSegment(
        out: MutableList<ReferenceSegment>,
        seenKeys: HashSet<String>,
        a: PointF2D,
        b: PointF2D,
        isSecondary: Boolean
    ) {
        val ax = a.x.roundToInt()
        val ay = a.y.roundToInt()
        val bx = b.x.roundToInt()
        val by = b.y.roundToInt()
        val key = if (ax < bx || (ax == bx && ay <= by)) {
            "$ax,$ay-$bx,$by-$isSecondary"
        } else {
            "$bx,$by-$ax,$ay-$isSecondary"
        }
        if (seenKeys.add(key)) {
            out.add(ReferenceSegment(a, b, isSecondary))
        }
    }

    private fun addRailBounces(
        out: MutableList<ReferenceSegment>,
        seenKeys: HashSet<String>,
        config: TableConfig,
        pocketPos: PointF2D,
        hitPoints: List<PointF2D>,
        railEdge: RailEdge,
        isSecondary: Boolean
    ) {
        val virtualPocket = when (railEdge) {
            RailEdge.TOP -> PointF2D(pocketPos.x, 2f * config.top - pocketPos.y)
            RailEdge.BOTTOM -> PointF2D(pocketPos.x, 2f * config.bottom - pocketPos.y)
            RailEdge.LEFT -> PointF2D(2f * config.left - pocketPos.x, pocketPos.y)
            RailEdge.RIGHT -> PointF2D(2f * config.right - pocketPos.x, pocketPos.y)
        }

        for (hitPt in hitPoints) {
            val startPos = rayIntersectionWithTableBounds(
                origin = hitPt,
                virtualPocket = virtualPocket,
                config = config,
                excludeEdge = railEdge
            ) ?: continue

            addUniqueSegment(out, seenKeys, startPos, hitPt, isSecondary)
            addUniqueSegment(out, seenKeys, hitPt, pocketPos, isSecondary)
        }
    }

    private fun rayIntersectionWithTableBounds(
        origin: PointF2D,
        virtualPocket: PointF2D,
        config: TableConfig,
        excludeEdge: RailEdge
    ): PointF2D? {
        val dx = origin.x - virtualPocket.x
        val dy = origin.y - virtualPocket.y
        val eps = 0.0001f
        val boundsTol = 0.5f

        var bestT = Float.MAX_VALUE
        var bestPoint: PointF2D? = null

        if (excludeEdge != RailEdge.LEFT && abs(dx) > eps) {
            val t = (config.left - origin.x) / dx
            if (t > 0.001f && t < bestT) {
                val y = origin.y + t * dy
                if (y in (config.top - boundsTol)..(config.bottom + boundsTol)) {
                    bestT = t
                    bestPoint = PointF2D(config.left, y.coerceIn(config.top, config.bottom))
                }
            }
        }

        if (excludeEdge != RailEdge.RIGHT && abs(dx) > eps) {
            val t = (config.right - origin.x) / dx
            if (t > 0.001f && t < bestT) {
                val y = origin.y + t * dy
                if (y in (config.top - boundsTol)..(config.bottom + boundsTol)) {
                    bestT = t
                    bestPoint = PointF2D(config.right, y.coerceIn(config.top, config.bottom))
                }
            }
        }

        if (excludeEdge != RailEdge.TOP && abs(dy) > eps) {
            val t = (config.top - origin.y) / dy
            if (t > 0.001f && t < bestT) {
                val x = origin.x + t * dx
                if (x in (config.left - boundsTol)..(config.right + boundsTol)) {
                    bestT = t
                    bestPoint = PointF2D(x.coerceIn(config.left, config.right), config.top)
                }
            }
        }

        if (excludeEdge != RailEdge.BOTTOM && abs(dy) > eps) {
            val t = (config.bottom - origin.y) / dy
            if (t > 0.001f && t < bestT) {
                val x = origin.x + t * dx
                if (x in (config.left - boundsTol)..(config.right + boundsTol)) {
                    bestT = t
                    bestPoint = PointF2D(x.coerceIn(config.left, config.right), config.bottom)
                }
            }
        }

        return bestPoint
    }
}
