package com.dayat.eightballline.util

import com.dayat.eightballline.model.BankDirection
import com.dayat.eightballline.model.Pocket
import com.dayat.eightballline.model.PointF2D
import com.dayat.eightballline.model.TableConfig
import com.dayat.eightballline.model.TableOrientation
import kotlin.math.abs
import kotlin.math.hypot

data class BankTrajectory(
    val start: PointF2D,
    val cushionHit1: PointF2D,
    val cushionHit2: PointF2D? = null,
    val targetPocket: PointF2D,
    val label: String,
    val pocketType: Pocket,
    val isHalfDiamond: Boolean = false,
    val points: List<PointF2D> = listOf(start, cushionHit1, targetPocket)
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
     * Menghitung titik tengah (half-diamond) di antara titik-titik diamond yang sudah ada:
     * - Rel panjang (9 titik: 0..8): 1.5, 2.5, 3.5, 4.5, 5.5, 6.5 (tanpa 0.5 & 7.5 di pojok lubang).
     * - Rel pendek (5 titik: 0..4): 1.5, 2.5 (tanpa 0.5 & 3.5 di pojok lubang).
     */
    fun getHalfDiamondPoints(config: TableConfig): Map<String, List<PointF2D>> {
        val mainPoints = getDiamondPoints(config)
        val result = mutableMapOf<String, List<PointF2D>>()

        for ((railName, points) in mainPoints) {
            val midPoints = mutableListOf<PointF2D>()
            // Mulai dari index 1 sampai size - 3 agar hanya di antara titik diamond (1-2, 2-3, dst.)
            for (i in 1 until points.size - 2) {
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

            // Saat mode "Semua Lubang", tampilkan garis utama ke 4 lubang sudut agar tidak terlalu ramai.
            // Garis ke lubang tengah ditampilkan saat lubang tengah dipilih spesifik.
            val isMiddlePocket = pocketType == Pocket.MIDDLE_LEFT || pocketType == Pocket.MIDDLE_RIGHT
            if (activeFilter == Pocket.ALL && isMiddlePocket) {
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

    /**
     * Menghitung jalur bidik langsung (0 pantul) atau 1, 2, 3 pantulan melalui titik bola (ballPos)
     * menuju lubang tujuan aktif dengan metode refleksi cermin + validasi ray-tracing meja.
     */
    fun calculateAimPinTrajectories(config: TableConfig, ballPos: PointF2D): List<BankTrajectory> {
        val result = mutableListOf<BankTrajectory>()
        val pockets = getPocketPositions(config)
        val activeFilter = config.activePocket
        val activeBounces = config.enabledBounces.filter { it in 0..2 }.sorted()
        if (activeBounces.isEmpty()) return result

        val includeTopBottom = config.bankDirection == BankDirection.ALL ||
                config.bankDirection == BankDirection.TOP_BOTTOM
        val includeLeftRight = config.bankDirection == BankDirection.ALL ||
                config.bankDirection == BankDirection.LEFT_RIGHT

        // Clamp sedikit ke dalam meja agar titik pantul tidak degeneratif saat bola menempel rel
        val bx = ballPos.x.coerceIn(config.left + 2f, config.right - 2f)
        val by = ballPos.y.coerceIn(config.top + 2f, config.bottom - 2f)
        val clampedBall = PointF2D(bx, by)

        val allPocketPositions = pockets.values.toList()

        for ((pocketType, pocketPos) in pockets) {
            if (activeFilter != Pocket.ALL && activeFilter != pocketType) {
                continue
            }

            for (bounceCount in activeBounces) {
                if (bounceCount == 0) {
                    result.add(
                        BankTrajectory(
                            start = clampedBall,
                            cushionHit1 = pocketPos,
                            targetPocket = pocketPos,
                            label = "Langsung",
                            pocketType = pocketType,
                            isHalfDiamond = false,
                            points = listOf(clampedBall, pocketPos)
                        )
                    )
                    continue
                }

                val firstEdges = mutableListOf<RailEdge>()
                if (includeTopBottom) {
                    firstEdges.add(RailEdge.TOP)
                    firstEdges.add(RailEdge.BOTTOM)
                }
                if (includeLeftRight) {
                    firstEdges.add(RailEdge.LEFT)
                    firstEdges.add(RailEdge.RIGHT)
                }

                val validLastEdges = getValidLastEdgesForPocket(config, pocketPos)
                val sequences = buildEdgeSequences(firstEdges, validLastEdges, bounceCount)

                for (seq in sequences) {
                    val path = traceMultiBouncePath(
                        config = config,
                        ballPos = clampedBall,
                        pocketPos = pocketPos,
                        edgeSequence = seq,
                        allPockets = allPocketPositions
                    ) ?: continue

                    result.add(
                        BankTrajectory(
                            start = path.first(),
                            cushionHit1 = path[1],
                            cushionHit2 = if (path.size > 3) path[2] else null,
                            targetPocket = path.last(),
                            label = "${bounceCount}P",
                            pocketType = pocketType,
                            isHalfDiamond = false,
                            points = path
                        )
                    )
                }
            }
        }

        return result
    }

    private fun getValidLastEdgesForPocket(config: TableConfig, pocketPos: PointF2D): Set<RailEdge> {
        val valid = mutableSetOf<RailEdge>()
        if (pocketPos.y > config.top + 5f) valid.add(RailEdge.TOP)
        if (pocketPos.y < config.bottom - 5f) valid.add(RailEdge.BOTTOM)
        if (pocketPos.x > config.left + 5f) valid.add(RailEdge.LEFT)
        if (pocketPos.x < config.right - 5f) valid.add(RailEdge.RIGHT)
        return valid
    }

    private fun buildEdgeSequences(
        firstEdges: List<RailEdge>,
        validLastEdges: Set<RailEdge>,
        bounceCount: Int
    ): List<List<RailEdge>> {
        val sequences = mutableListOf<List<RailEdge>>()
        val allEdges = RailEdge.values()

        fun dfs(current: MutableList<RailEdge>) {
            val depth = current.size
            if (depth == bounceCount) {
                if (current.last() in validLastEdges) {
                    sequences.add(current.toList())
                }
                return
            }
            val candidates = if (depth == 0) firstEdges else allEdges.filter { it != current.last() }
            for (edge in candidates) {
                if (depth == bounceCount - 1 && edge !in validLastEdges) continue
                current.add(edge)
                dfs(current)
                current.removeAt(current.size - 1)
            }
        }

        dfs(mutableListOf())
        return sequences
    }

    private fun reflectPointAcrossEdge(p: PointF2D, edge: RailEdge, config: TableConfig): PointF2D {
        return when (edge) {
            RailEdge.TOP -> PointF2D(p.x, 2f * config.top - p.y)
            RailEdge.BOTTOM -> PointF2D(p.x, 2f * config.bottom - p.y)
            RailEdge.LEFT -> PointF2D(2f * config.left - p.x, p.y)
            RailEdge.RIGHT -> PointF2D(2f * config.right - p.x, p.y)
        }
    }

    private fun traceMultiBouncePath(
        config: TableConfig,
        ballPos: PointF2D,
        pocketPos: PointF2D,
        edgeSequence: List<RailEdge>,
        allPockets: List<PointF2D>
    ): List<PointF2D>? {
        // 1. Hitung bayangan virtual lubang mundur dari pantulan terakhir ke pertama
        var virtualTarget = pocketPos
        for (i in edgeSequence.indices.reversed()) {
            virtualTarget = reflectPointAcrossEdge(virtualTarget, edgeSequence[i], config)
        }

        var dirX = virtualTarget.x - ballPos.x
        var dirY = virtualTarget.y - ballPos.y
        if (abs(dirX) < 0.001f && abs(dirY) < 0.001f) return null

        val path = mutableListOf(ballPos)
        var cur = ballPos
        var prevEdge: RailEdge? = null
        val pocketClearance = 14f

        // 2. Ray-trace maju pantulan demi pantulan, pastikan memantul tepat sesuai urutan rel
        for (expectedEdge in edgeSequence) {
            val hit = findFirstWallHit(cur, dirX, dirY, config, prevEdge) ?: return null
            if (hit.first != expectedEdge) {
                return null
            }
            val hitPoint = hit.second

            // Pastikan titik pantul tidak langsung masuk ke salah satu lubang di tengah jalan
            for (p in allPockets) {
                if (hypot((hitPoint.x - p.x).toDouble(), (hitPoint.y - p.y).toDouble()) < pocketClearance) {
                    return null
                }
            }

            path.add(hitPoint)
            cur = hitPoint
            prevEdge = expectedEdge

            when (expectedEdge) {
                RailEdge.TOP, RailEdge.BOTTOM -> dirY = -dirY
                RailEdge.LEFT, RailEdge.RIGHT -> dirX = -dirX
            }
        }

        // 3. Pastikan segmen terakhir benar-benar menuju lubang tanpa membentur rel lain lebih dulu
        val distToPocket = hypot((pocketPos.x - cur.x).toDouble(), (pocketPos.y - cur.y).toDouble()).toFloat()
        if (distToPocket < 5f) return null
        val dirLen = hypot(dirX.toDouble(), dirY.toDouble()).toFloat()
        if (dirLen < 0.001f) return null

        val tPocket = distToPocket / dirLen
        val nextWall = findFirstWallHit(cur, dirX, dirY, config, prevEdge)
        if (nextWall != null) {
            val distToWall = hypot((nextWall.second.x - cur.x).toDouble(), (nextWall.second.y - cur.y).toDouble()).toFloat()
            if (distToWall + 6f < distToPocket) {
                return null
            }
        }
        if (tPocket <= 0f) return null

        path.add(pocketPos)
        return path
    }

    private fun findFirstWallHit(
        origin: PointF2D,
        dx: Float,
        dy: Float,
        config: TableConfig,
        excludeEdge: RailEdge?
    ): Pair<RailEdge, PointF2D>? {
        val eps = 0.0001f
        val tol = 0.5f
        var bestT = Float.MAX_VALUE
        var bestHit: Pair<RailEdge, PointF2D>? = null

        if (excludeEdge != RailEdge.LEFT && abs(dx) > eps) {
            val t = (config.left - origin.x) / dx
            if (t > 0.0005f && t < bestT) {
                val y = origin.y + t * dy
                if (y in (config.top - tol)..(config.bottom + tol)) {
                    bestT = t
                    bestHit = RailEdge.LEFT to PointF2D(config.left, y.coerceIn(config.top, config.bottom))
                }
            }
        }

        if (excludeEdge != RailEdge.RIGHT && abs(dx) > eps) {
            val t = (config.right - origin.x) / dx
            if (t > 0.0005f && t < bestT) {
                val y = origin.y + t * dy
                if (y in (config.top - tol)..(config.bottom + tol)) {
                    bestT = t
                    bestHit = RailEdge.RIGHT to PointF2D(config.right, y.coerceIn(config.top, config.bottom))
                }
            }
        }

        if (excludeEdge != RailEdge.TOP && abs(dy) > eps) {
            val t = (config.top - origin.y) / dy
            if (t > 0.0005f && t < bestT) {
                val x = origin.x + t * dx
                if (x in (config.left - tol)..(config.right + tol)) {
                    bestT = t
                    bestHit = RailEdge.TOP to PointF2D(x.coerceIn(config.left, config.right), config.top)
                }
            }
        }

        if (excludeEdge != RailEdge.BOTTOM && abs(dy) > eps) {
            val t = (config.bottom - origin.y) / dy
            if (t > 0.0005f && t < bestT) {
                val x = origin.x + t * dx
                if (x in (config.left - tol)..(config.right + tol)) {
                    bestT = t
                    bestHit = RailEdge.BOTTOM to PointF2D(x.coerceIn(config.left, config.right), config.bottom)
                }
            }
        }

        return bestHit
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
                        label = "T${idx + 1}.5",
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
