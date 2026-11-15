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

    var onCalibrationChanged: ((TableConfig) -> Unit)? = null

    // Paints
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
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

    // Dragging state for calibration
    private var activeCorner = -1 // 0: Top-Left, 1: Top-Right, 2: Bottom-Right, 3: Bottom-Left
    private val handleRadius = 45f

    init {
        updatePaints()
    }

    private fun updatePaints() {
        linePaint.color = config.lineColor
        linePaint.alpha = config.opacity
        linePaint.strokeWidth = config.lineWidth
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (config.width <= 20f || config.height <= 20f) return

        // 1. Gambar Batas Meja (Cushions)
        canvas.drawRect(config.left, config.top, config.right, config.bottom, cushionPaint)

        // 2. Gambar Lubang (6 Pockets)
        val pockets = DiamondSystemCalculator.getPocketPositions(config)
        for ((_, pos) in pockets) {
            canvas.drawCircle(pos.x, pos.y, 22f, pocketPaint)
        }

        // 3. Gambar Garis Pantulan (Bank Shot Trajectories)
        if (config.showBankShotLines) {
            val trajectories = DiamondSystemCalculator.calculateTrajectories(config)
            for (traj in trajectories) {
                // Sinar datang (incoming ray)
                canvas.drawLine(
                    traj.start.x, traj.start.y,
                    traj.cushionHit1.x, traj.cushionHit1.y,
                    linePaint
                )
                // Sinar pantul (reflected ray)
                canvas.drawLine(
                    traj.cushionHit1.x, traj.cushionHit1.y,
                    traj.targetPocket.x, traj.targetPocket.y,
                    linePaint
                )
            }
        }

        // 4. Gambar Diamond Points & Label Titik
        if (config.showDiamonds) {
            val diamonds = DiamondSystemCalculator.getDiamondPoints(config)
            for ((railName, points) in diamonds) {
                for ((idx, pt) in points.withIndex()) {
                    if (idx == 0 || idx == points.size - 1) continue // Pocket corners

                    // Lingkaran diamond
                    canvas.drawCircle(pt.x, pt.y, 6f, diamondPaint)

                    // Label nomor titik (Titik 1, 2, 3, 4, 5, 6)
                    val label = if (idx == 4 && points.size > 5) "T4 (Tengah)" else "T$idx"
                    val offsetY = when (railName) {
                        "top" -> -14f
                        "bottom" -> 30f
                        else -> 10f
                    }
                    val offsetX = when (railName) {
                        "left" -> -20f
                        "right" -> 20f
                        else -> 0f
                    }
                    canvas.drawText(label, pt.x + offsetX, pt.y + offsetY, textPaint)
                }
            }
        }

        // 5. Gambar Handle Kalibrasi (jika dalam Mode Kalibrasi)
        if (isCalibrationMode) {
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

            // Panduan di tengah layar
            val midX = (config.left + config.right) / 2f
            val midY = (config.top + config.bottom) / 2f
            canvas.drawText(
                "MODE KALIBRASI AKTIF",
                midX, midY - 30f, guideTextPaint
            )
            canvas.drawText(
                "Geser 4 titik merah sudut meja agar pas dengan 8 Ball Pool",
                midX, midY + 25f, textPaint
            )
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isCalibrationMode) {
            // Ketika mode game / bukan kalibrasi, jangan sentuh event ini (biarkan pass-through)
            return false
        }

        val x = event.x
        val y = event.y

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val corners = arrayOf(
                    PointF2D(config.left, config.top),
                    PointF2D(config.right, config.top),
                    PointF2D(config.right, config.bottom),
                    PointF2D(config.left, config.bottom)
                )

                activeCorner = -1
                for (i in corners.indices) {
                    val dist = hypot((x - corners[i].x).toDouble(), (y - corners[i].y).toDouble())
                    if (dist <= handleRadius * 1.8f) {
                        activeCorner = i
                        break
                    }
                }
                return activeCorner != -1
            }

            MotionEvent.ACTION_MOVE -> {
                if (activeCorner != -1) {
                    when (activeCorner) {
                        0 -> { // Top-Left
                            config.left = x.coerceAtMost(config.right - 80f)
                            config.top = y.coerceAtMost(config.bottom - 80f)
                        }
                        1 -> { // Top-Right
                            config.right = x.coerceAtLeast(config.left + 80f)
                            config.top = y.coerceAtMost(config.bottom - 80f)
                        }
                        2 -> { // Bottom-Right
                            config.right = x.coerceAtLeast(config.left + 80f)
                            config.bottom = y.coerceAtLeast(config.top + 80f)
                        }
                        3 -> { // Bottom-Left
                            config.left = x.coerceAtMost(config.right - 80f)
                            config.bottom = y.coerceAtLeast(config.top + 80f)
                        }
                    }
                    invalidate()
                    onCalibrationChanged?.invoke(config)
                    return true
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                activeCorner = -1
                return true
            }
        }

        return super.onTouchEvent(event)
    }
}
