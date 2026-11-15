package com.dayat.eightballline.view

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.*
import android.widget.*
import androidx.appcompat.view.ContextThemeWrapper
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import com.dayat.eightballline.R
import com.dayat.eightballline.model.BankDirection
import com.dayat.eightballline.model.Pocket
import com.dayat.eightballline.util.PreferencesManager
import kotlin.math.abs

class FloatingMenuManager(
    context: Context,
    private val windowManager: WindowManager,
    private val overlayView: TableOverlayView,
    private val prefs: PreferencesManager,
    private val onStopServiceRequested: () -> Unit,
    private val onCalibrationModeChanged: (Boolean) -> Unit
) {

    private val themedContext: Context = ContextThemeWrapper(context, R.style.Theme_EightBallLine)
    private val menuView: View = LayoutInflater.from(themedContext).inflate(R.layout.view_floating_menu, null)
    private lateinit var layoutParams: WindowManager.LayoutParams

    private val layoutBubble: View = menuView.findViewById(R.id.layoutBubble)
    private val layoutExpandedPanel: View = menuView.findViewById(R.id.layoutExpandedPanel)
    private val btnMinimize: ImageView = menuView.findViewById(R.id.btnMinimize)
    private val switchLines: SwitchCompat = menuView.findViewById(R.id.switchLines)
    private val switchHalfDiamonds: SwitchCompat = menuView.findViewById(R.id.switchHalfDiamonds)
    private val switchColorByPocket: SwitchCompat = menuView.findViewById(R.id.switchColorByPocket)

    private val btnPocketTL: TextView = menuView.findViewById(R.id.btnPocketTL)
    private val btnPocketML: TextView = menuView.findViewById(R.id.btnPocketML)
    private val btnPocketTR: TextView = menuView.findViewById(R.id.btnPocketTR)
    private val btnPocketBL: TextView = menuView.findViewById(R.id.btnPocketBL)
    private val btnPocketMR: TextView = menuView.findViewById(R.id.btnPocketMR)
    private val btnPocketBR: TextView = menuView.findViewById(R.id.btnPocketBR)
    private val btnPocketAll: TextView = menuView.findViewById(R.id.btnPocketAll)

    private val btnDirAll: TextView = menuView.findViewById(R.id.btnDirAll)
    private val btnDirTB: TextView = menuView.findViewById(R.id.btnDirTB)
    private val btnDirLR: TextView = menuView.findViewById(R.id.btnDirLR)

    private val btnToggleCalibration: Button = menuView.findViewById(R.id.btnToggleCalibration)
    private val btnResetTableInOverlay: Button = menuView.findViewById(R.id.btnResetTableInOverlay)
    private val tvOpacityLabel: TextView = menuView.findViewById(R.id.tvOpacityLabel)
    private val seekBarOpacity: SeekBar = menuView.findViewById(R.id.seekBarOpacity)
    private val tvLineWidthLabel: TextView = menuView.findViewById(R.id.tvLineWidthLabel)
    private val seekBarLineWidth: SeekBar = menuView.findViewById(R.id.seekBarLineWidth)
    private val btnFloatColorCyan: View = menuView.findViewById(R.id.btnFloatColorCyan)
    private val btnFloatColorGreen: View = menuView.findViewById(R.id.btnFloatColorGreen)
    private val btnFloatColorYellow: View = menuView.findViewById(R.id.btnFloatColorYellow)
    private val btnFloatColorRed: View = menuView.findViewById(R.id.btnFloatColorRed)
    private val btnFloatColorWhite: View = menuView.findViewById(R.id.btnFloatColorWhite)
    private val btnFloatColorBlack: View = menuView.findViewById(R.id.btnFloatColorBlack)
    private val btnCloseOverlay: Button = menuView.findViewById(R.id.btnCloseOverlay)

    private val pocketButtons by lazy {
        listOf(
            btnPocketTL to Pocket.TOP_LEFT,
            btnPocketML to Pocket.MIDDLE_LEFT,
            btnPocketTR to Pocket.TOP_RIGHT,
            btnPocketBL to Pocket.BOTTOM_LEFT,
            btnPocketMR to Pocket.MIDDLE_RIGHT,
            btnPocketBR to Pocket.BOTTOM_RIGHT,
            btnPocketAll to Pocket.ALL
        )
    }

    private val directionButtons by lazy {
        listOf(
            btnDirAll to BankDirection.ALL,
            btnDirTB to BankDirection.TOP_BOTTOM,
            btnDirLR to BankDirection.LEFT_RIGHT
        )
    }

    private val colorSwatches by lazy {
        listOf(
            btnFloatColorCyan to 0xFF00E5FF.toInt(),
            btnFloatColorGreen to 0xFF10B981.toInt(),
            btnFloatColorYellow to 0xFFF59E0B.toInt(),
            btnFloatColorRed to 0xFFEF4444.toInt(),
            btnFloatColorWhite to 0xFFFFFFFF.toInt(),
            btnFloatColorBlack to 0xFF111827.toInt()
        )
    }

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isDragging = false

    fun attach() {
        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 50
            y = 200
        }

        setupListeners()
        setupPocketGrid()
        setupDirectionGrid()
        setupColorPicker()
        windowManager.addView(menuView, layoutParams)
    }

    private fun setupListeners() {
        // Dragging & clicking bubble
        layoutBubble.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = layoutParams.x
                    initialY = layoutParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (abs(dx) > 10 || abs(dy) > 10) {
                        isDragging = true
                        layoutParams.x = initialX + dx
                        layoutParams.y = initialY + dy
                        safeUpdateViewLayout()
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) {
                        expandMenu()
                    }
                    true
                }
                else -> false
            }
        }

        btnMinimize.setOnClickListener {
            collapseMenu()
        }

        // Toggle Garis Pantul
        switchLines.isChecked = overlayView.config.showBankShotLines
        switchLines.setOnCheckedChangeListener { _, isChecked ->
            overlayView.config.showBankShotLines = isChecked
            overlayView.invalidate()
            prefs.saveConfig(overlayView.config)
        }

        // Toggle Garis Titik Tengah (Half-Diamonds)
        switchHalfDiamonds.isChecked = overlayView.config.showHalfDiamonds
        switchHalfDiamonds.setOnCheckedChangeListener { _, isChecked ->
            overlayView.config.showHalfDiamonds = isChecked
            overlayView.invalidate()
            prefs.saveConfig(overlayView.config)
        }

        // Toggle Warna per Lubang (Auto Color-Coding)
        switchColorByPocket.isChecked = overlayView.config.colorByPocket
        switchColorByPocket.setOnCheckedChangeListener { _, isChecked ->
            overlayView.config.colorByPocket = isChecked
            overlayView.invalidate()
            prefs.saveConfig(overlayView.config)
            updateColorSwatchHighlights(overlayView.config.lineColor)
            updatePocketHighlights()
        }

        // Mode Kalibrasi vs Lock Meja
        btnToggleCalibration.setOnClickListener {
            val newMode = !overlayView.isCalibrationMode
            overlayView.isCalibrationMode = newMode
            onCalibrationModeChanged(newMode)

            if (newMode) {
                btnToggleCalibration.text = "Kunci Meja"
                btnToggleCalibration.setBackgroundColor(ContextCompat.getColor(themedContext, R.color.accent_green))
            } else {
                btnToggleCalibration.text = "Kalibrasi"
                btnToggleCalibration.setBackgroundColor(ContextCompat.getColor(themedContext, R.color.accent_red))
                prefs.saveConfig(overlayView.config)
            }
        }

        // Reset posisi meja agar pas di layar aktif
        btnResetTableInOverlay.setOnClickListener {
            val w = overlayView.width.takeIf { it > 0 } ?: 1920
            val h = overlayView.height.takeIf { it > 0 } ?: 1080
            val reset = prefs.resetCalibration(w, h, overlayView.config.orientation)
            overlayView.config.left = reset.left
            overlayView.config.top = reset.top
            overlayView.config.right = reset.right
            overlayView.config.bottom = reset.bottom
            overlayView.invalidate()
        }

        // Opacity Slider
        seekBarOpacity.progress = overlayView.config.opacity
        tvOpacityLabel.text = "Kecerahan / Opasitas (${(overlayView.config.opacity * 100) / 255}%)"
        seekBarOpacity.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val clamped = progress.coerceIn(40, 255)
                overlayView.config.opacity = clamped
                overlayView.invalidate()
                tvOpacityLabel.text = "Kecerahan / Opasitas (${(clamped * 100) / 255}%)"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                prefs.saveConfig(overlayView.config)
            }
        })

        // Line Width Slider (1.0px - 6.0px)
        val initialWidthProgress = ((overlayView.config.lineWidth - 1.0f) * 10f).toInt().coerceIn(0, 50)
        seekBarLineWidth.progress = initialWidthProgress
        tvLineWidthLabel.text = String.format("Ketebalan Garis (%.1fpx)", overlayView.config.lineWidth)
        seekBarLineWidth.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val widthPx = 1.0f + (progress * 0.1f)
                overlayView.config.lineWidth = widthPx
                overlayView.invalidate()
                tvLineWidthLabel.text = String.format("Ketebalan Garis (%.1fpx)", widthPx)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                prefs.saveConfig(overlayView.config)
            }
        })

        // Close Overlay Service
        btnCloseOverlay.setOnClickListener {
            onStopServiceRequested()
        }
    }

    private fun setupPocketGrid() {
        updatePocketHighlights()
        for ((btn, pocket) in pocketButtons) {
            btn.setOnClickListener {
                overlayView.config.activePocket = pocket
                overlayView.invalidate()
                prefs.saveConfig(overlayView.config)
                updatePocketHighlights()
            }
        }
    }

    private fun updatePocketHighlights() {
        val density = themedContext.resources.displayMetrics.density
        val active = overlayView.config.activePocket
        val usePocketColors = overlayView.config.colorByPocket

        for ((btn, pocket) in pocketButtons) {
            val isSelected = pocket == active
            val pocketColor = TableOverlayView.getPocketColor(pocket)
            val strokeColor = if (isSelected) {
                if (usePocketColors && pocket != Pocket.ALL) pocketColor else 0xFF00E5FF.toInt()
            } else {
                0x33FFFFFF
            }
            val bgColor = if (isSelected) 0xFF1F2937.toInt() else 0xFF111827.toInt()

            btn.setTextColor(
                if (usePocketColors && pocket != Pocket.ALL) pocketColor
                else if (isSelected) 0xFF00E5FF.toInt()
                else 0xFFE2E8F0.toInt()
            )
            btn.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 6f * density
                setColor(bgColor)
                setStroke(((if (isSelected) 1.8f else 0.8f) * density).toInt(), strokeColor)
            }
        }
    }

    private fun setupDirectionGrid() {
        updateDirectionHighlights()
        for ((btn, direction) in directionButtons) {
            btn.setOnClickListener {
                overlayView.config.bankDirection = direction
                overlayView.invalidate()
                prefs.saveConfig(overlayView.config)
                updateDirectionHighlights()
            }
        }
    }

    private fun updateDirectionHighlights() {
        val density = themedContext.resources.displayMetrics.density
        val active = overlayView.config.bankDirection

        for ((btn, direction) in directionButtons) {
            val isSelected = direction == active
            btn.setTextColor(if (isSelected) 0xFF00E5FF.toInt() else 0xFFCBD5E1.toInt())
            btn.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 6f * density
                setColor(if (isSelected) 0xFF1F2937.toInt() else 0xFF111827.toInt())
                setStroke(
                    ((if (isSelected) 1.8f else 0.8f) * density).toInt(),
                    if (isSelected) 0xFF00E5FF.toInt() else 0x33FFFFFF
                )
            }
        }
    }

    private fun setupColorPicker() {
        updateColorSwatchHighlights(overlayView.config.lineColor)
        for ((view, color) in colorSwatches) {
            view.setOnClickListener {
                overlayView.config.colorByPocket = false
                switchColorByPocket.isChecked = false
                overlayView.config.lineColor = color
                overlayView.invalidate()
                prefs.saveConfig(overlayView.config)
                updateColorSwatchHighlights(color)
                updatePocketHighlights()
            }
        }
    }

    private fun updateColorSwatchHighlights(activeColor: Int) {
        val density = themedContext.resources.displayMetrics.density
        val isMonoMode = !overlayView.config.colorByPocket
        for ((view, color) in colorSwatches) {
            val isSelected = isMonoMode && color == activeColor
            val borderColor = if (isSelected) {
                if (color == Color.WHITE) 0xFF00E5FF.toInt() else Color.WHITE
            } else {
                0x55FFFFFF
            }
            val strokePx = ((if (isSelected) 2.5f else 1f) * density).toInt()
            view.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 6f * density
                setColor(color)
                setStroke(strokePx, borderColor)
            }
        }
    }

    private fun expandMenu() {
        layoutBubble.visibility = View.GONE
        layoutExpandedPanel.visibility = View.VISIBLE
        val density = themedContext.resources.displayMetrics.density
        val panelWidthPx = (224 * density).toInt()
        val screenH = overlayView.height.takeIf { it > 0 } ?: themedContext.resources.displayMetrics.heightPixels
        val maxPanelHeight = (screenH * 0.82f).toInt()
        layoutExpandedPanel.measure(
            View.MeasureSpec.makeMeasureSpec(panelWidthPx, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(maxPanelHeight, View.MeasureSpec.AT_MOST)
        )
        layoutParams.height = layoutExpandedPanel.measuredHeight.coerceAtMost(maxPanelHeight)
        safeUpdateViewLayout()
    }

    private fun collapseMenu() {
        layoutExpandedPanel.visibility = View.GONE
        layoutBubble.visibility = View.VISIBLE
        layoutParams.height = WindowManager.LayoutParams.WRAP_CONTENT
        safeUpdateViewLayout()
    }

    private fun safeUpdateViewLayout() {
        try {
            if (menuView.isAttachedToWindow) {
                windowManager.updateViewLayout(menuView, layoutParams)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun detach() {
        try {
            if (menuView.isAttachedToWindow) {
                windowManager.removeView(menuView)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
