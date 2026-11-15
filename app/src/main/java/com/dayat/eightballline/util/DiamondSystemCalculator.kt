package com.dayat.eightballline.util

import com.dayat.eightballline.model.BankDirection
import com.dayat.eightballline.model.Pocket
import com.dayat.eightballline.model.PointF2D
import com.dayat.eightballline.model.TableConfig
import com.dayat.eightballline.model.TableOrientation
import kotlin.math.abs

data class BankTrajectory(
    val start: PointF2D,
    val cushionHit1: PointF2D,
    val cushionHit2: PointF2D? = null,
    val targetPocket: PointF2D,
    val label: String,
    val pocketType: Pocket,
    val isHalfDiamond: Boolean = false
)

object DiamondSystemCalculator {

    private enum class RailEdge {
        TOP, BOTTOM, LEFT, RIGHT
    }

    /**
     * Menghitung posisi semua diamond utama pada rel meja biliar.
     * Long rail: dibagi 8 segmen (titik 1-3, lubang tengah = titik 4, titik 5-7).
     * Short rail: dibagi 4 segmen (titik 1-3).
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
     * Menghitung titik tengah (half-diamond: 0.5, 1.5, 2.5, ...) di antara setiap dua titik yang ada.
     */
    fun getHalfDiamondPoints(config: TableConfig): Map<String, List<PointF2D>> {
        val mainPoints = getDiamondPoints(config)
        val result = mutableMapOf<String, List<PointF2D>>()

        for ((railName, points) in mainPoints) {
            val midPoints = mutableListOf<PointF2D>()
            for (i in 0 until points.size - 1) {
                val p1 = points[i]
                val p2 = points[i + 1]
                midPoints.add(PointF2D((p1.x + p2.x) / 2f, (p1.y + p2.y) / 2f))
            }
            result[railName] = midPoints
        }

        return result
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
     * Menghasilkan garis pantulan 1-cushion untuk arah Atas/Bawah maupun Kiri/Kanan,
     * termasuk titik tengah (half-diamond) di antara titik-titik utama.
     */
    fun calculateTrajectories(config: TableConfig): List<BankTrajectory> {
        val trajectories = mutableListOf<BankTrajectory>()
        val pockets = getPocketPositions(config)
        val diamonds = getDiamondPoints(config)
        val halfDiamonds = if (config.showHalfDiamonds) getHalfDiamondPoints(config) else emptyMap()

        val activeFilter = config.activePocket
        val includeTopBottom = config.bankDirection == BankDirection.ALL ||
                config.bankDirection == BankDirection.TOP_BOTTOM
        val includeLeftRight = config.bankDirection == BankDirection.ALL ||
                config.bankDirection == BankDirection.LEFT_RIGHT

        for ((pocketType, targetPocketPos) in pockets) {
            if (activeFilter != Pocket.ALL && activeFilter != pocketType) {
                continue
            }

            // 1. Pantulan Atas <-> Bawah (via Top Rail & Bottom Rail)
            if (includeTopBottom) {
                val isBottomPocket = pocketType == Pocket.BOTTOM_LEFT ||
                        pocketType == Pocket.BOTTOM_RIGHT ||
                        (config.orientation == TableOrientation.LANDSCAPE && pocketType == Pocket.MIDDLE_RIGHT)
                val isTopPocket = pocketType == Pocket.TOP_LEFT ||
                        pocketType == Pocket.TOP_RIGHT ||
                        (config.orientation == TableOrientation.LANDSCAPE && pocketType == Pocket.MIDDLE_LEFT)
                val isSideMiddleInPortrait = config.orientation == TableOrientation.PORTRAIT &&
                        (pocketType == Pocket.MIDDLE_LEFT || pocketType == Pocket.MIDDLE_RIGHT)

                if (isBottomPocket || isSideMiddleInPortrait) {
                    addRailTrajectories(
                        trajectories = trajectories,
                        config = config,
                        pocketType = pocketType,
                        targetPocketPos = targetPocketPos,
                        railPoints = diamonds["top"],
                        halfRailPoints = halfDiamonds["top"],
                        railEdge = RailEdge.TOP
                    )
                }

                if (isTopPocket || isSideMiddleInPortrait) {
                    addRailTrajectories(
                        trajectories = trajectories,
                        config = config,
                        pocketType = pocketType,
                        targetPocketPos = targetPocketPos,
                        railPoints = diamonds["bottom"],
                        halfRailPoints = halfDiamonds["bottom"],
                        railEdge = RailEdge.BOTTOM
                    )
                }
            }

            // 2. Pantulan Kiri <-> Kanan (via Left Rail & Right Rail)
            if (includeLeftRight) {
                val isLeftPocket = pocketType == Pocket.TOP_LEFT ||
                        pocketType == Pocket.BOTTOM_LEFT ||
                        (config.orientation == TableOrientation.PORTRAIT && pocketType == Pocket.MIDDLE_LEFT)
                val isRightPocket = pocketType == Pocket.TOP_RIGHT ||
                        pocketType == Pocket.BOTTOM_RIGHT ||
                        (config.orientation == TableOrientation.PORTRAIT && pocketType == Pocket.MIDDLE_RIGHT)
                val isTopBottomMiddleInLandscape = config.orientation == TableOrientation.LANDSCAPE &&
                        (pocketType == Pocket.MIDDLE_LEFT || pocketType == Pocket.MIDDLE_RIGHT)

                if (isLeftPocket || isTopBottomMiddleInLandscape) {
                    addRailTrajectories(
                        trajectories = trajectories,
                        config = config,
                        pocketType = pocketType,
                        targetPocketPos = targetPocketPos,
                        railPoints = diamonds["right"],
                        halfRailPoints = halfDiamonds["right"],
                        railEdge = RailEdge.RIGHT
                    )
                }

                if (isRightPocket || isTopBottomMiddleInLandscape) {
                    addRailTrajectories(
                        trajectories = trajectories,
                        config = config,
                        pocketType = pocketType,
                        targetPocketPos = targetPocketPos,
                        railPoints = diamonds["left"],
                        halfRailPoints = halfDiamonds["left"],
                        railEdge = RailEdge.LEFT
                    )
                }
            }
        }

        return trajectories
    }

    private fun addRailTrajectories(
        trajectories: MutableList<BankTrajectory>,
        config: TableConfig,
        pocketType: Pocket,
        targetPocketPos: PointF2D,
        railPoints: List<PointF2D>?,
        halfRailPoints: List<PointF2D>?,
        railEdge: RailEdge
    ) {
        if (railPoints == null) return

        val virtualPocket = when (railEdge) {
            RailEdge.TOP -> PointF2D(targetPocketPos.x, 2f * config.top - targetPocketPos.y)
            RailEdge.BOTTOM -> PointF2D(targetPocketPos.x, 2f * config.bottom - targetPocketPos.y)
            RailEdge.LEFT -> PointF2D(2f * config.left - targetPocketPos.x, targetPocketPos.y)
            RailEdge.RIGHT -> PointF2D(2f * config.right - targetPocketPos.x, targetPocketPos.y)
        }

        val isLongRail = railPoints.size > 5

        // Titik diamond utama (T1..T3 / T1..T7)
        for (idx in 1 until railPoints.size - 1) {
            if (isLongRail && idx == 4) continue // Titik 4 adalah lubang tengah
            val hitDiamond = railPoints[idx]
            val startPos = rayIntersectionWithTableBounds(
                origin = hitDiamond,
                virtualPocket = virtualPocket,
                config = config,
                excludeEdge = railEdge
            ) ?: continue

            trajectories.add(
                BankTrajectory(
                    start = startPos,
                    cushionHit1 = hitDiamond,
                    targetPocket = targetPocketPos,
                    label = "T$idx",
                    pocketType = pocketType,
                    isHalfDiamond = false
                )
            )
        }

        // Titik tengah (half-diamond: 0.5, 1.5, 2.5, ...)
        if (halfRailPoints != null) {
            for (idx in halfRailPoints.indices) {
                val hitHalf = halfRailPoints[idx]
                val startPos = rayIntersectionWithTableBounds(
                    origin = hitHalf,
                    virtualPocket = virtualPocket,
                    config = config,
                    excludeEdge = railEdge
                ) ?: continue

                trajectories.add(
                    BankTrajectory(
                        start = startPos,
                        cushionHit1 = hitHalf,
                        targetPocket = targetPocketPos,
                        label = "T${idx}.5",
                        pocketType = pocketType,
                        isHalfDiamond = true
                    )
                )
            }
        }
    }

    /**
     * Mencari perpotongan sinar datang (dari origin ke dalam meja, menjauhi virtualPocket)
     * dengan batas bantalan meja lainnya (top, bottom, left, right).
     */
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
