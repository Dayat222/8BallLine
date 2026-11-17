package com.dayat.eightballline.util

import com.dayat.eightballline.model.Pocket
import com.dayat.eightballline.model.PointF2D
import com.dayat.eightballline.model.TableConfig
import com.dayat.eightballline.model.TableOrientation
import kotlin.math.abs

data class ReferenceTrajectory(
    val start: PointF2D,
    val cushionHit: PointF2D,
    val targetPocket: PointF2D,
    val pocketType: Pocket,
    val isSecondary: Boolean
)

object DiamondSystemCalculator {

    private enum class RailEdge {
        TOP, BOTTOM, LEFT, RIGHT
    }

    /**
     * Menghitung posisi semua diamond utama pada rel meja biliar.
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

    /**
     * Menghitung titik tengah (half-diamond: 1.5, 2.5, 3.5, 4.5, 5.5, 6.5) pada long rail.
     */
    private fun getLongRailHalfPoints(railPoints: List<PointF2D>): List<PointF2D> {
        val midPoints = mutableListOf<PointF2D>()
        for (i in 1 until railPoints.size - 2) {
            val p1 = railPoints[i]
            val p2 = railPoints[i + 1]
            midPoints.add(PointF2D((p1.x + p2.x) / 2f, (p1.y + p2.y) / 2f))
        }
        return midPoints
    }

    /**
     * Menghitung posisi 6 lubang (4 sudut + 2 tengah)
     */
    fun getPocketPositions(config: TableConfig): Map<Pocket, PointF2D> {
        val l = config.left
        val t = config.top
        val r = config.right
        val b = config.bottom

        return if (config.orientation == TableOrientation.LANDSCAPE) {
            mapOf(
                Pocket.TOP_LEFT to PointF2D(l, t),
                Pocket.TOP_RIGHT to PointF2D(r, t),
                Pocket.BOTTOM_LEFT to PointF2D(l, b),
                Pocket.BOTTOM_RIGHT to PointF2D(r, b),
                Pocket.MIDDLE_LEFT to PointF2D(l + config.width / 2f, t),   // Tengah Atas
                Pocket.MIDDLE_RIGHT to PointF2D(l + config.width / 2f, b)   // Tengah Bawah
            )
        } else {
            mapOf(
                Pocket.TOP_LEFT to PointF2D(l, t),
                Pocket.TOP_RIGHT to PointF2D(r, t),
                Pocket.BOTTOM_LEFT to PointF2D(l, b),
                Pocket.BOTTOM_RIGHT to PointF2D(r, b),
                Pocket.MIDDLE_LEFT to PointF2D(l, t + config.height / 2f),  // Tengah Kiri
                Pocket.MIDDLE_RIGHT to PointF2D(r, t + config.height / 2f)  // Tengah Kanan
            )
        }
    }

    /**
     * Garis tengah lurus yang menghubungkan kedua lubang tengah (seperti pada foto referensi).
     */
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
     * Menghasilkan garis panduan persis sesuai foto referensi:
     * - Garis utama (Primary): pantulan titik diamond 1..7 (termasuk titik tengah 4) di long rail menuju 4 lubang sudut.
     * - Garis sekunder (Teal / Secondary):
     *   1) Pantulan titik tengah 1.5, 2.5, 3.5, 4.5, 5.5, 6.5 di long rail.
     *   2) Pantulan titik diamond 1, 2, 3 di short rail.
     */
    fun calculateReferenceTrajectories(config: TableConfig): List<ReferenceTrajectory> {
        val result = mutableListOf<ReferenceTrajectory>()
        val pockets = getPocketPositions(config)
        val diamonds = getDiamondPoints(config)
        val activeFilter = config.activePocket

        val cornerPockets = listOf(
            Pocket.TOP_LEFT,
            Pocket.TOP_RIGHT,
            Pocket.BOTTOM_LEFT,
            Pocket.BOTTOM_RIGHT
        )

        for (pocketType in cornerPockets) {
            if (activeFilter != Pocket.ALL && activeFilter != pocketType) {
                continue
            }
            val pocketPos = pockets[pocketType] ?: continue

            if (config.orientation == TableOrientation.LANDSCAPE) {
                // LANDSCAPE: Long rails = TOP & BOTTOM, Short rails = LEFT & RIGHT
                val oppositeLongEdge = if (pocketType == Pocket.TOP_LEFT || pocketType == Pocket.TOP_RIGHT) {
                    RailEdge.BOTTOM
                } else {
                    RailEdge.TOP
                }
                val longRailPoints = diamonds[if (oppositeLongEdge == RailEdge.TOP) "top" else "bottom"]

                if (longRailPoints != null) {
                    // Utama: titik 1..7 (termasuk titik tengah 4 -> sudut ke tengah ke sudut)
                    addRailBounces(
                        out = result,
                        config = config,
                        pocketType = pocketType,
                        pocketPos = pocketPos,
                        hitPoints = longRailPoints.subList(1, longRailPoints.size - 1),
                        railEdge = oppositeLongEdge,
                        isSecondary = false
                    )
                    // Sekunder: half-diamonds 1.5, 2.5, 3.5, 4.5, 5.5, 6.5
                    if (config.showSecondaryLines) {
                        addRailBounces(
                            out = result,
                            config = config,
                            pocketType = pocketType,
                            pocketPos = pocketPos,
                            hitPoints = getLongRailHalfPoints(longRailPoints),
                            railEdge = oppositeLongEdge,
                            isSecondary = true
                        )
                    }
                }

                // Short rail bounces (titik 1, 2, 3 -> sekunder/teal)
                if (config.showSecondaryLines) {
                    val oppositeShortEdge = if (pocketType == Pocket.TOP_LEFT || pocketType == Pocket.BOTTOM_LEFT) {
                        RailEdge.RIGHT
                    } else {
                        RailEdge.LEFT
                    }
                    val shortRailPoints = diamonds[if (oppositeShortEdge == RailEdge.LEFT) "left" else "right"]
                    if (shortRailPoints != null) {
                        addRailBounces(
                            out = result,
                            config = config,
                            pocketType = pocketType,
                            pocketPos = pocketPos,
                            hitPoints = shortRailPoints.subList(1, shortRailPoints.size - 1),
                            railEdge = oppositeShortEdge,
                            isSecondary = true
                        )
                    }
                }
            } else {
                // PORTRAIT (orientasi sama persis dengan foto referensi):
                // Long rails = LEFT & RIGHT, Short rails = TOP & BOTTOM
                val oppositeLongEdge = if (pocketType == Pocket.TOP_LEFT || pocketType == Pocket.BOTTOM_LEFT) {
                    RailEdge.RIGHT
                } else {
                    RailEdge.LEFT
                }
                val longRailPoints = diamonds[if (oppositeLongEdge == RailEdge.LEFT) "left" else "right"]

                if (longRailPoints != null) {
                    addRailBounces(
                        out = result,
                        config = config,
                        pocketType = pocketType,
                        pocketPos = pocketPos,
                        hitPoints = longRailPoints.subList(1, longRailPoints.size - 1),
                        railEdge = oppositeLongEdge,
                        isSecondary = false
                    )
                    if (config.showSecondaryLines) {
                        addRailBounces(
                            out = result,
                            config = config,
                            pocketType = pocketType,
                            pocketPos = pocketPos,
                            hitPoints = getLongRailHalfPoints(longRailPoints),
                            railEdge = oppositeLongEdge,
                            isSecondary = true
                        )
                    }
                }

                if (config.showSecondaryLines) {
                    val oppositeShortEdge = if (pocketType == Pocket.TOP_LEFT || pocketType == Pocket.TOP_RIGHT) {
                        RailEdge.BOTTOM
                    } else {
                        RailEdge.TOP
                    }
                    val shortRailPoints = diamonds[if (oppositeShortEdge == RailEdge.TOP) "top" else "bottom"]
                    if (shortRailPoints != null) {
                        addRailBounces(
                            out = result,
                            config = config,
                            pocketType = pocketType,
                            pocketPos = pocketPos,
                            hitPoints = shortRailPoints.subList(1, shortRailPoints.size - 1),
                            railEdge = oppositeShortEdge,
                            isSecondary = true
                        )
                    }
                }
            }
        }

        return result
    }

    private fun addRailBounces(
        out: MutableList<ReferenceTrajectory>,
        config: TableConfig,
        pocketType: Pocket,
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

            out.add(
                ReferenceTrajectory(
                    start = startPos,
                    cushionHit = hitPt,
                    targetPocket = pocketPos,
                    pocketType = pocketType,
                    isSecondary = isSecondary
                )
            )
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
