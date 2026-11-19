package com.dayat.eightballline.view

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import com.dayat.eightballline.model.PointF2D
import com.dayat.eightballline.model.TableConfig
import com.dayat.eightballline.util.DiamondSystemCalculator
import com.dayat.eightballline.util.LineCategory
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

    var windowOffsetX: Float = 0f
    var windowOffsetY: Float = 0f

    var onCalibrationChanged: ((TableConfig) -> Unit)? = null

    private val bluePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val redPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.4f
    }

    private val cushionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = 0xAAFFFFFF.toInt()
        pathEffect = DashPathEffect(floatArrayOf(15f, 15f), 0f)
    }

    private val diamondPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xFFFFFFFF.toInt()
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 22f
        color = 0xFFFFFFFF.toInt()
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        setShadowLayer(4f, 1f, 1f, 0xFF000000.toInt())
    }

    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xFFFF3366.toInt()
    }

    private val handleBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = 0xFFFFFFFF.toInt()
    }

    private val guideTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 30f
        color = 0xFF00FFCC.toInt()
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        setShadowLayer(6f, 2f, 2f, 0xFF000000.toInt())
    }

    private val centerHandlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xFF00E5FF.toInt()
    }

    private val centerTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 20f
        color = 0xFF000000.toInt()
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private var activeHandle = -1
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private val handleRadius = 40f
    private val centerHandleRadius = 35f

    init {
        updatePaints()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        invalidate()
    }

    private fun updatePaints() {
        bluePaint.color = config.blueLineColor
        bluePaint.alpha = config.opacity
        bluePaint.strokeWidth = config.lineWidth

        redPaint.color = config.redLineColor
        redPaint.alpha = config.opacity
        redPaint.strokeWidth = config.lineWidth

        framePaint.color = config.blueLineColor
        framePaint.alpha = (config.opacity * 0.40f).toInt().coerceIn(20, 140)
        framePaint.strokeWidth = (config.lineWidth * 0.75f).coerceAtLeast(1.0f)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        updatePaints()

        if (config.width <= 20f || config.height <= 20f) return

        canvas.save()
        canvas.translate(-windowOffsetX, -windowOffsetY)

        // 1. Garis Panduan 62-segmen dengan tepat 2 warna (Biru & Merah) persis foto referensi
        if (config.showBankShotLines) {
            canvas.drawRect(config.left, config.top, config.right, config.bottom, framePaint)

            val segments = DiamondSystemCalculator.calculateReferenceSegments(config)
            for (seg in segments) {
                val paint = if (seg.category == LineCategory.BLUE) bluePaint else redPaint
                canvas.drawLine(seg.p1.x, seg.p1.y, seg.p2.x, seg.p2.y, paint)
            }
        }

        // 2. Elemen Kalibrasi (hanya tampil saat Mode Kalibrasi aktif)
        if (isCalibrationMode) {
            canvas.drawRect(config.left, config.top, config.right, config.bottom, cushionPaint)

            val diamonds = DiamondSystemCalculator.getDiamondPoints(config)
            for ((railName, points) in diamonds) {
                for ((idx, pt) in points.withIndex()) {
                    if (idx == 0 || idx == points.size - 1) continue
                    canvas.drawCircle(pt.x, pt.y, 5f, diamondPaint)
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

            val midX = (config.left + config.right) / 2f
            val midY = (config.top + config.bottom) / 2f

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

            canvas.drawCircle(midX, midY, centerHandleRadius, centerHandlePaint)
            canvas.drawCircle(midX, midY, centerHandleRadius, handleBorderPaint)
            canvas.drawText("GESER", midX, midY + 7f, centerTextPaint)

            canvas.drawText(
                "MODE KALIBRASI UKURAN MEJA AKTIF",
                midX, midY - 60f, guideTextPaint
            )
            canvas.drawText(
                "Tarik 4 sudut merah untuk ukuran, atau tombol tengah untuk geser",
                midX, midY + 65f, textPaint
            )
        }

        canvas.restore()
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

                if (hypot((x - midX).toDouble(), (y - midY).toDouble()) <= centerHandleRadius * 1.8f) {
                    activeHandle = 4
                    return true
                }

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
                        0 -> {
                            config.left = clampedX.coerceAtMost(config.right - 80f)
                            config.top = clampedY.coerceAtMost(config.bottom - 80f)
                        }
                        1 -> {
                            config.right = clampedX.coerceAtLeast(config.left + 80f)
                            config.top = clampedY.coerceAtMost(config.bottom - 80f)
                        }
                        2 -> {
                            config.right = clampedX.coerceAtLeast(config.left + 80f)
                            config.bottom = clampedY.coerceAtLeast(config.top + 80f)
                        }
                        3 -> {
                            config.left = clampedX.coerceAtMost(config.right - 80f)
                            config.bottom = clampedY.coerceAtLeast(config.top + 80f)
                        }
                        4 -> {
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
