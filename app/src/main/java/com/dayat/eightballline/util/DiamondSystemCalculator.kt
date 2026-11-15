package com.dayat.eightballline.util

import com.dayat.eightballline.model.Pocket
import com.dayat.eightballline.model.PointF2D
import com.dayat.eightballline.model.TableConfig
import com.dayat.eightballline.model.TableOrientation

data class BankTrajectory(
    val start: PointF2D,
    val cushionHit1: PointF2D,
    val cushionHit2: PointF2D? = null,
    val targetPocket: PointF2D,
    val label: String,
    val pocketType: Pocket
)

object DiamondSystemCalculator {

    /**
     * Menghitung posisi semua diamond (titik pantul) pada rel meja biliar.
     * Long rail: dibagi 8 segmen (3 diamond di paruh pertama, lubang tengah = titik 4, 3 diamond di paruh kedua).
     * Short rail: dibagi 4 segmen (3 diamond).
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
            // Rel Panjang: Top & Bottom (8 segmen)
            val topRail = (0..8).map { i -> PointF2D(l + (w / 8f) * i, t) }
            val bottomRail = (0..8).map { i -> PointF2D(l + (w / 8f) * i, b) }
            // Rel Pendek: Left & Right (4 segmen)
            val leftRail = (0..4).map { i -> PointF2D(l, t + (h / 4f) * i) }
            val rightRail = (0..4).map { i -> PointF2D(r, t + (h / 4f) * i) }

            result["top"] = topRail
            result["bottom"] = bottomRail
            result["left"] = leftRail
            result["right"] = rightRail
        } else {
            // PORTRAIT: Rel Panjang: Left & Right (8 segmen), Rel Pendek: Top & Bottom (4 segmen)
            val leftRail = (0..8).map { i -> PointF2D(l, t + (h / 8f) * i) }
            val rightRail = (0..8).map { i -> PointF2D(r, t + (h / 8f) * i) }
            val topRail = (0..4).map { i -> PointF2D(l + (w / 4f) * i, t) }
            val bottomRail = (0..4).map { i -> PointF2D(l + (w / 4f) * i, b) }

            result["top"] = topRail
            result["bottom"] = bottomRail
            result["left"] = leftRail
            result["right"] = rightRail
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
                Pocket.MIDDLE_LEFT to PointF2D(l + config.width / 2f, t), // Middle Top
                Pocket.MIDDLE_RIGHT to PointF2D(l + config.width / 2f, b)  // Middle Bottom
            )
        } else {
            mapOf(
                Pocket.TOP_LEFT to PointF2D(l, t),
                Pocket.TOP_RIGHT to PointF2D(r, t),
                Pocket.BOTTOM_LEFT to PointF2D(l, b),
                Pocket.BOTTOM_RIGHT to PointF2D(r, b),
                Pocket.MIDDLE_LEFT to PointF2D(l, t + config.height / 2f),
                Pocket.MIDDLE_RIGHT to PointF2D(r, t + config.height / 2f)
            )
        }
    }

    /**
     * Menghasilkan garis pantulan (bank shot trajectories) sesuai Diamond System
     * seperti yang ditunjukkan di tutorial video rikisenjaya8bp.
     */
    fun calculateTrajectories(config: TableConfig): List<BankTrajectory> {
        val trajectories = mutableListOf<BankTrajectory>()
        val pockets = getPocketPositions(config)
        val diamonds = getDiamondPoints(config)

        val activeFilter = config.activePocket

        // Hitung garis pantul 1-cushion untuk lubang yang aktif
        for ((pocketType, targetPocketPos) in pockets) {
            if (activeFilter != Pocket.ALL && activeFilter != pocketType) {
                continue
            }

            // Cushion pantul seberang (opposite rails)
            if (config.orientation == TableOrientation.LANDSCAPE) {
                // Untuk lubang di bawah (BOTTOM_LEFT, BOTTOM_RIGHT), pantulan via Top Rail
                if (pocketType == Pocket.BOTTOM_LEFT || pocketType == Pocket.BOTTOM_RIGHT) {
                    val topRail = diamonds["top"] ?: continue
                    val virtualPocket = PointF2D(targetPocketPos.x, 2 * config.top - targetPocketPos.y)

                    for (idx in 1..7) {
                        if (idx == 4) continue // Titik 4 adalah lubang tengah
                        val hitDiamond = topRail[idx]
                        // Garis datang dari rel bawah atau seberang melalui titik pantul
                        val startPos = lineIntersectionWithRail(
                            p1 = virtualPocket,
                            p2 = hitDiamond,
                            railY = config.bottom,
                            minX = config.left,
                            maxX = config.right
                        ) ?: continue

                        trajectories.add(
                            BankTrajectory(
                                start = startPos,
                                cushionHit1 = hitDiamond,
                                targetPocket = targetPocketPos,
                                label = "Titik $idx",
                                pocketType = pocketType
                            )
                        )
                    }
                }

                // Untuk lubang di atas (TOP_LEFT, TOP_RIGHT), pantulan via Bottom Rail
                if (pocketType == Pocket.TOP_LEFT || pocketType == Pocket.TOP_RIGHT) {
                    val bottomRail = diamonds["bottom"] ?: continue
                    val virtualPocket = PointF2D(targetPocketPos.x, 2 * config.bottom - targetPocketPos.y)

                    for (idx in 1..7) {
                        if (idx == 4) continue
                        val hitDiamond = bottomRail[idx]
                        val startPos = lineIntersectionWithRail(
                            p1 = virtualPocket,
                            p2 = hitDiamond,
                            railY = config.top,
                            minX = config.left,
                            maxX = config.right
                        ) ?: continue

                        trajectories.add(
                            BankTrajectory(
                                start = startPos,
                                cushionHit1 = hitDiamond,
                                targetPocket = targetPocketPos,
                                label = "Titik $idx",
                                pocketType = pocketType
                            )
                        )
                    }
                }
            } else {
                // PORTRAIT (Vertikal - sesuai video TikTok):
                // Pantulan menuju Lubang Kiri Bawah (BOTTOM_LEFT) via Right Rail (Rel Kanan)
                if (pocketType == Pocket.BOTTOM_LEFT || pocketType == Pocket.TOP_LEFT) {
                    val rightRail = diamonds["right"] ?: continue
                    val virtualPocket = PointF2D(2 * config.right - targetPocketPos.x, targetPocketPos.y)

                    for (idx in 1..7) {
                        val hitDiamond = rightRail[idx]
                        val startPos = lineIntersectionWithVerticalRail(
                            p1 = virtualPocket,
                            p2 = hitDiamond,
                            railX = config.left,
                            minY = config.top,
                            maxY = config.bottom
                        ) ?: continue

                        trajectories.add(
                            BankTrajectory(
                                start = startPos,
                                cushionHit1 = hitDiamond,
                                targetPocket = targetPocketPos,
                                label = "Titik $idx",
                                pocketType = pocketType
                            )
                        )
                    }
                }

                // Pantulan menuju Lubang Kanan Bawah / Kanan Atas via Left Rail (Rel Kiri)
                if (pocketType == Pocket.BOTTOM_RIGHT || pocketType == Pocket.TOP_RIGHT) {
                    val leftRail = diamonds["left"] ?: continue
                    val virtualPocket = PointF2D(2 * config.left - targetPocketPos.x, targetPocketPos.y)

                    for (idx in 1..7) {
                        val hitDiamond = leftRail[idx]
                        val startPos = lineIntersectionWithVerticalRail(
                            p1 = virtualPocket,
                            p2 = hitDiamond,
                            railX = config.right,
                            minY = config.top,
                            maxY = config.bottom
                        ) ?: continue

                        trajectories.add(
                            BankTrajectory(
                                start = startPos,
                                cushionHit1 = hitDiamond,
                                targetPocket = targetPocketPos,
                                label = "Titik $idx",
                                pocketType = pocketType
                            )
                        )
                    }
                }
            }
        }

        return trajectories
    }

    private fun lineIntersectionWithRail(
        p1: PointF2D,
        p2: PointF2D,
        railY: Float,
        minX: Float,
        maxX: Float
    ): PointF2D? {
        val dy = p2.y - p1.y
        if (Math.abs(dy) < 0.0001f) return null
        val t = (railY - p1.y) / dy
        val x = p1.x + t * (p2.x - p1.x)
        return if (x in minX..maxX) PointF2D(x, railY) else null
    }

    private fun lineIntersectionWithVerticalRail(
        p1: PointF2D,
        p2: PointF2D,
        railX: Float,
        minY: Float,
        maxY: Float
    ): PointF2D? {
        val dx = p2.x - p1.x
        if (Math.abs(dx) < 0.0001f) return null
        val t = (railX - p1.x) / dx
        val y = p1.y + t * (p2.y - p1.y)
        return if (y in minY..maxY) PointF2D(railX, y) else null
    }
}
