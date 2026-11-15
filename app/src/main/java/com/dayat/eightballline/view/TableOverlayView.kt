package com.dayat.eightballline.view

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import com.dayat.eightballline.model.Pocket
import com.dayat.eightballline.model.PointF2D
import com.dayat.eightballline.model.TableConfig
import com.dayat.eightballline.util.DiamondSystemCalculator
import kotlin.math.hypot

class TableOverlayView(context: Context) : View(context) {

    var config: TableConfig = TableConfig()
        set(value) {
            field = value
            updatePaints()
            invalidate()
        }

    var isCalibrationMode: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    var aimTarget: PointF2D? = null
        set(value) {
            field = value
            invalidate()
        }

    var onCalibrationChanged: ((TableConfig) -> Unit)? = null

    // Paints
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val halfLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        pathEffect = DashPathEffect(floatArrayOf(10f, 8f), 0f)
    }

    private val aimPinLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val bounceDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val gridLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }

    private val cushionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = 0x88FFFFFF.toInt()
        pathEffect = DashPathEffect(floatArrayOf(15f, 15f), 0f)
    }

    private val diamondPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xFFFFFFFF.toInt()
    }

    private val halfDiamondPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xCC00E5FF.toInt()
    }

    private val pocketPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = 0xFFFFD700.toInt() // Gold pocket rings
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 24f
        color = 0xFFFFFFFF.toInt()
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        setShadowLayer(4f, 1f, 1f, 0xFF000000.toInt())
    }

    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xFFFF3366.toInt() // Red handle
    }

    private val handleBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = 0xFFFFFFFF.toInt()
    }

    private val guideTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 32f
        color = 0xFF00FFCC.toInt()
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        setShadowLayer(6f, 2f, 2f, 0xFF000000.toInt())
    }

    private val centerHandlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xFF00E5FF.toInt() // Cyan center
    }

    private val centerTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 20f
        color = 0xFF000000.toInt()
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    // Dragging state for calibration
    private var activeHandle = -1 // 0-3: Corners (TL, TR, BR, BL), 4: Center pan
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private val handleRadius = 40f
    private val centerHandleRadius = 35f

    init {
        updatePaints()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // Jangan mengubah atau melakukan clamp koordinat kalibrasi tersimpan saat orientasi berubah
        invalidate()
    }

    companion object {
        fun getPocketColor(pocket: Pocket): Int = when (pocket) {
            Pocket.TOP_LEFT -> 0xFF00E5FF.toInt()     // Cyan
            Pocket.TOP_RIGHT -> 0xFFF59E0B.toInt()    // Kuning Gold
            Pocket.BOTTOM_LEFT -> 0xFF10B981.toInt()  // Hijau Emerald
            Pocket.BOTTOM_RIGHT -> 0xFFFB7185.toInt() // Merah Rose
            Pocket.MIDDLE_LEFT -> 0xFFA78BFA.toInt()  // Ungu Neon
            Pocket.MIDDLE_RIGHT -> 0xFF38BDF8.toInt() // Biru Langit
            Pocket.ALL -> 0xFF00E5FF.toInt()
        }
    }

    private fun updatePaints() {
        linePaint.color = config.lineColor
        linePaint.alpha = config.opacity
        linePaint.strokeWidth = config.lineWidth

        halfLinePaint.color = config.lineColor
        halfLinePaint.alpha = (config.opacity * 0.68f).toInt().coerceIn(30, 255)
        halfLinePaint.strokeWidth = (config.lineWidth * 0.72f).coerceAtLeast(1.3f)

        gridLinePaint.color = if (config.colorByPocket) 0xFFFFFFFF.toInt() else config.lineColor
        gridLinePaint.alpha = (config.opacity * 0.25f).toInt().coerceIn(18, 95)
        gridLinePaint.strokeWidth = (config.lineWidth * 0.45f).coerceAtLeast(1.1f)

        halfDiamondPaint.color = if (config.colorByPocket) 0xFFFFFFFF.toInt() else config.lineColor
        halfDiamondPaint.alpha = (config.opacity * 0.90f).toInt().coerceIn(50, 255)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        updatePaints()

        if (config.width <= 20f || config.height <= 20f) return

        // 1. Gambar Batas Meja (Cushions) & Grid Penghubung Diamond + Tick Marks (seperti stiker referensi)
        canvas.drawRect(config.left, config.top, config.right, config.bottom, cushionPaint)
        val tickLen = 10f
        val diamonds = DiamondSystemCalculator.getDiamondPoints(config)
        val topPts = diamonds["top"]
        val bottomPts = diamonds["bottom"]
        val leftPts = diamonds["left"]
        val rightPts = diamonds["right"]
        if (topPts != null && bottomPts != null && topPts.size == bottomPts.size) {
            for (i in 1 until topPts.size - 1) {
                canvas.drawLine(
                    topPts[i].x, topPts[i].y - tickLen,
                    bottomPts[i].x, bottomPts[i].y + tickLen,
                    gridLinePaint
                )
            }
        }
        if (leftPts != null && rightPts != null && leftPts.size == rightPts.size) {
            for (i in 1 until leftPts.size - 1) {
                canvas.drawLine(
                    leftPts[i].x - tickLen, leftPts[i].y,
                    rightPts[i].x + tickLen, rightPts[i].y,
                    gridLinePaint
                )
            }
        }

        // 2. Gambar Lubang (6 Pockets) dengan warna penanda
        val pockets = DiamondSystemCalculator.getPocketPositions(config)
        for ((pocketType, pos) in pockets) {
            pocketPaint.color = if (config.colorByPocket) getPocketColor(pocketType) else 0xFFFFD700.toInt()
            canvas.drawCircle(pos.x, pos.y, 22f, pocketPaint)
        }

        // 3. Gambar Garis Pantulan (Bank Shot Trajectories)
        val activeAimTarget = aimTarget
        if (config.showBankShotLines) {
            val trajectories = DiamondSystemCalculator.calculateTrajectories(config)
            // Jika mode Bidik Bola aktif, redupkan garis statis agar jalur bidikan utama sangat menonjol
            val dimFactor = if (activeAimTarget != null) 0.32f else 1.0f
            val mainAlpha = (config.opacity * dimFactor).toInt().coerceIn(15, 255)
            val halfAlpha = (config.opacity * 0.68f * dimFactor).toInt().coerceIn(12, 255)
            val bounceLegWidth = (config.lineWidth * 0.65f).coerceAtLeast(1.2f)

            for (traj in trajectories) {
                val activePaint = if (traj.isHalfDiamond) halfLinePaint else linePaint
                if (config.colorByPocket) {
                    activePaint.color = getPocketColor(traj.pocketType)
                } else {
                    activePaint.color = config.lineColor
                }
                activePaint.alpha = if (traj.isHalfDiamond) halfAlpha else mainAlpha
                activePaint.strokeWidth = if (traj.isHalfDiamond) {
                    (config.lineWidth * 0.72f).coerceAtLeast(1.3f)
                } else {
                    config.lineWidth
                }

                // Sinar datang / arah bidik
                canvas.drawLine(
                    traj.start.x, traj.start.y,
                    traj.cushionHit1.x, traj.cushionHit1.y,
                    activePaint
                )

                // Sinar pantul menuju lubang dibuat sedikit lebih tipis agar arah pantul mudah dibedakan
                if (!traj.isHalfDiamond) {
                    activePaint.strokeWidth = bounceLegWidth
                }
                canvas.drawLine(
                    traj.cushionHit1.x, traj.cushionHit1.y,
                    traj.targetPocket.x, traj.targetPocket.y,
                    activePaint
                )
            }
        }

        // 3b. Gambar Jalur Bidik Tepat melalui Pin Target Bola (jika aktif)
        if (activeAimTarget != null) {
            val aimTrajectories = DiamondSystemCalculator.calculateAimPinTrajectories(config, activeAimTarget)
            for (traj in aimTrajectories) {
                val trajColor = if (config.colorByPocket) getPocketColor(traj.pocketType) else config.lineColor
                aimPinLinePaint.color = trajColor
                aimPinLinePaint.alpha = 255
                aimPinLinePaint.strokeWidth = (config.lineWidth * 1.45f).coerceAtLeast(3.5f)

                // Garis dari bola ke titik pantul rel
                canvas.drawLine(
                    traj.start.x, traj.start.y,
                    traj.cushionHit1.x, traj.cushionHit1.y,
                    aimPinLinePaint
                )
                // Garis pantul dari rel ke lubang
                aimPinLinePaint.strokeWidth = (config.lineWidth * 1.15f).coerceAtLeast(2.6f)
                canvas.drawLine(
                    traj.cushionHit1.x, traj.cushionHit1.y,
                    traj.targetPocket.x, traj.targetPocket.y,
                    aimPinLinePaint
                )

                // Titik pantul terang pada bantalan
                bounceDotPaint.color = 0xFFFFFFFF.toInt()
                canvas.drawCircle(traj.cushionHit1.x, traj.cushionHit1.y, 8f, bounceDotPaint)
                bounceDotPaint.color = trajColor
                canvas.drawCircle(traj.cushionHit1.x, traj.cushionHit1.y, 5f, bounceDotPaint)
            }
        }

        // 4. Gambar Diamond Points & Label Titik
        if (config.showDiamonds) {
            for ((railName, points) in diamonds) {
                for ((idx, pt) in points.withIndex()) {
                    if (idx == 0 || idx == points.size - 1) continue // Pocket corners

                    // Lingkaran diamond
                    canvas.drawCircle(pt.x, pt.y, 6f, diamondPaint)

                    // Label nomor titik (Titik 1, 2, 3, 4, 5, 6, 7)
                    val label = if (idx == 4 && points.size > 5) "T4" else "T$idx"
                    val offsetY = when (railName) {
                        "top" -> -14f
                        "bottom" -> 28f
                        else -> 8f
                    }
                    val offsetX = when (railName) {
                        "left" -> -22f
                        "right" -> 22f
                        else -> 0f
                    }
                    canvas.drawText(label, pt.x + offsetX, pt.y + offsetY, textPaint)
                }
            }

            // Titik tengah (half-diamonds) di antara titik-titik utama tetap ditampilkan di rel sebagai patokan
            val halfDiamonds = DiamondSystemCalculator.getHalfDiamondPoints(config)
            for ((_, points) in halfDiamonds) {
                for (pt in points) {
                    canvas.drawCircle(pt.x, pt.y, 3.8f, halfDiamondPaint)
                }
            }
        }

        // 5. Gambar Handle Kalibrasi (jika dalam Mode Kalibrasi)
        if (isCalibrationMode) {
            val midX = (config.left + config.right) / 2f
            val midY = (config.top + config.bottom) / 2f

            // 4 Sudut (Merah)
            val corners = arrayOf(
                PointF2D(config.left, config.top),
                PointF2D(config.right, config.top),
                PointF2D(config.right, config.bottom),
                PointF2D(config.left, config.bottom)
            )

            for (pt in corners) {
                canvas.drawCircle(pt.x, pt.y, handleRadius, handlePaint)
                canvas.drawCircle(pt.x, pt.y, handleRadius, handleBorderPaint)
            }

            // Handle Geser Tengah (Cyan)
            canvas.drawCircle(midX, midY, centerHandleRadius, centerHandlePaint)
            canvas.drawCircle(midX, midY, centerHandleRadius, handleBorderPaint)
            canvas.drawText("GESER", midX, midY + 7f, centerTextPaint)

            // Panduan di layar
            canvas.drawText(
                "MODE KALIBRASI UKURAN MEJA AKTIF",
                midX, midY - 60f, guideTextPaint
            )
            canvas.drawText(
                "Tarik 4 sudut merah untuk ukuran, atau tombol tengah untuk geser",
                midX, midY + 65f, textPaint
            )
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isCalibrationMode) {
            return false
        }

        val x = event.x
        val y = event.y

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = x
                lastTouchY = y

                val midX = (config.left + config.right) / 2f
                val midY = (config.top + config.bottom) / 2f

                // Cek apakah klik tombol tengah (center pan)
                if (hypot((x - midX).toDouble(), (y - midY).toDouble()) <= centerHandleRadius * 1.8f) {
                    activeHandle = 4
                    return true
                }

                // Cek 4 sudut meja
                val corners = arrayOf(
                    PointF2D(config.left, config.top),
                    PointF2D(config.right, config.top),
                    PointF2D(config.right, config.bottom),
                    PointF2D(config.left, config.bottom)
                )

                activeHandle = -1
                for (i in corners.indices) {
                    val dist = hypot((x - corners[i].x).toDouble(), (y - corners[i].y).toDouble())
                    if (dist <= handleRadius * 1.8f) {
                        activeHandle = i
                        break
                    }
                }
                return activeHandle != -1
            }

            MotionEvent.ACTION_MOVE -> {
                if (activeHandle != -1) {
                    val dx = x - lastTouchX
                    val dy = y - lastTouchY
                    lastTouchX = x
                    lastTouchY = y

                    val margin = 24f
                    val maxX = (width - margin).coerceAtLeast(margin + 100f)
                    val maxY = (height - margin).coerceAtLeast(margin + 100f)
                    val clampedX = x.coerceIn(margin, maxX)
                    val clampedY = y.coerceIn(margin, maxY)

                    when (activeHandle) {
                        0 -> { // Top-Left
                            config.left = clampedX.coerceAtMost(config.right - 80f)
                            config.top = clampedY.coerceAtMost(config.bottom - 80f)
                        }
                        1 -> { // Top-Right
                            config.right = clampedX.coerceAtLeast(config.left + 80f)
                            config.top = clampedY.coerceAtMost(config.bottom - 80f)
                        }
                        2 -> { // Bottom-Right
                            config.right = clampedX.coerceAtLeast(config.left + 80f)
                            config.bottom = clampedY.coerceAtLeast(config.top + 80f)
                        }
                        3 -> { // Bottom-Left
                            config.left = clampedX.coerceAtMost(config.right - 80f)
                            config.bottom = clampedY.coerceAtLeast(config.top + 80f)
                        }
                        4 -> { // Center Pan (Geser seluruh meja, tetap di dalam layar)
                            val clampedDx = dx.coerceIn(margin - config.left, maxX - config.right)
                            val clampedDy = dy.coerceIn(margin - config.top, maxY - config.bottom)
                            config.left += clampedDx
                            config.right += clampedDx
                            config.top += clampedDy
                            config.bottom += clampedDy
                        }
                    }
                    invalidate()
                    onCalibrationChanged?.invoke(config)
                    return true
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (activeHandle != -1) {
                    onCalibrationChanged?.invoke(config)
                }
                activeHandle = -1
                return true
            }
        }

        return super.onTouchEvent(event)
    }
}
