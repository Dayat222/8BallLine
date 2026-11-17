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
    private val switchSecondaryLines: SwitchCompat = menuView.findViewById(R.id.switchSecondaryLines)

    private val btnPocketTL: TextView = menuView.findViewById(R.id.btnPocketTL)
    private val btnPocketTR: TextView = menuView.findViewById(R.id.btnPocketTR)
    private val btnPocketBL: TextView = menuView.findViewById(R.id.btnPocketBL)
    private val btnPocketBR: TextView = menuView.findViewById(R.id.btnPocketBR)
    private val btnPocketAll: TextView = menuView.findViewById(R.id.btnPocketAll)

    private val tvOpacityLabel: TextView = menuView.findViewById(R.id.tvOpacityLabel)
    private val seekBarOpacity: SeekBar = menuView.findViewById(R.id.seekBarOpacity)
    private val tvLineWidthLabel: TextView = menuView.findViewById(R.id.tvLineWidthLabel)
    private val seekBarLineWidth: SeekBar = menuView.findViewById(R.id.seekBarLineWidth)

    private val btnThemeBlackTeal: View = menuView.findViewById(R.id.btnThemeBlackTeal)
    private val btnThemeWhiteTeal: View = menuView.findViewById(R.id.btnThemeWhiteTeal)
    private val btnThemeCyanGold: View = menuView.findViewById(R.id.btnThemeCyanGold)
    private val btnThemeYellowGreen: View = menuView.findViewById(R.id.btnThemeYellowGreen)
    private val btnThemeRedCyan: View = menuView.findViewById(R.id.btnThemeRedCyan)

    private val btnToggleCalibration: Button = menuView.findViewById(R.id.btnToggleCalibration)
    private val btnResetTableInOverlay: Button = menuView.findViewById(R.id.btnResetTableInOverlay)
    private val btnCloseOverlay: Button = menuView.findViewById(R.id.btnCloseOverlay)

    private val pocketButtons by lazy {
        listOf(
            btnPocketTL to Pocket.TOP_LEFT,
            btnPocketTR to Pocket.TOP_RIGHT,
            btnPocketBL to Pocket.BOTTOM_LEFT,
            btnPocketBR to Pocket.BOTTOM_RIGHT,
            btnPocketAll to Pocket.ALL
        )
    }

    // Pasangan warna (Garis Utama, Garis Sekunder)
    private val colorThemes by lazy {
        listOf(
            Triple(btnThemeBlackTeal, 0xFF111827.toInt(), 0xFF0F766E.toInt()),   // Hitam + Teal (persis foto)
            Triple(btnThemeWhiteTeal, 0xFFFFFFFF.toInt(), 0xFF14B8A6.toInt()),   // Putih + Teal
            Triple(btnThemeCyanGold, 0xFF00E5FF.toInt(), 0xFFF59E0B.toInt()),    // Cyan + Kuning
            Triple(btnThemeYellowGreen, 0xFFFACC15.toInt(), 0xFF10B981.toInt()), // Kuning + Hijau
            Triple(btnThemeRedCyan, 0xFFFB7185.toInt(), 0xFF38BDF8.toInt())      // Merah Rose + Biru Langit
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
            x = 24
            y = 140
        }

        setupListeners()
        setupPocketGrid()
        setupThemePicker()
        windowManager.addView(menuView, layoutParams)
    }

    private fun setupListeners() {
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

        switchLines.isChecked = overlayView.config.showBankShotLines
        switchLines.setOnCheckedChangeListener { _, isChecked ->
            overlayView.config.showBankShotLines = isChecked
            overlayView.invalidate()
            prefs.saveConfig(overlayView.config)
        }

        switchSecondaryLines.isChecked = overlayView.config.showSecondaryLines
        switchSecondaryLines.setOnCheckedChangeListener { _, isChecked ->
            overlayView.config.showSecondaryLines = isChecked
            overlayView.invalidate()
            prefs.saveConfig(overlayView.config)
        }

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

        for ((btn, pocket) in pocketButtons) {
            val isSelected = pocket == active
            btn.setTextColor(if (isSelected) 0xFF00E5FF.toInt() else 0xFFE2E8F0.toInt())
            btn.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 5f * density
                setColor(if (isSelected) 0xFF1F2937.toInt() else 0xFF111827.toInt())
                setStroke(
                    ((if (isSelected) 1.8f else 0.8f) * density).toInt(),
                    if (isSelected) 0xFF00E5FF.toInt() else 0x33FFFFFF
                )
            }
        }
    }

    private fun setupThemePicker() {
        updateThemeHighlights()
        for ((view, primary, secondary) in colorThemes) {
            view.setOnClickListener {
                overlayView.config.lineColor = primary
                overlayView.config.secondaryLineColor = secondary
                overlayView.invalidate()
                prefs.saveConfig(overlayView.config)
                updateThemeHighlights()
            }
        }
    }

    private fun updateThemeHighlights() {
        val density = themedContext.resources.displayMetrics.density
        val activePrimary = overlayView.config.lineColor
        val activeSecondary = overlayView.config.secondaryLineColor

        for ((view, primary, secondary) in colorThemes) {
            val isSelected = primary == activePrimary && secondary == activeSecondary
            val borderColor = if (isSelected) Color.WHITE else 0x55FFFFFF
            val strokePx = ((if (isSelected) 2.2f else 1f) * density).toInt()
            view.background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(primary, secondary)
            ).apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 6f * density
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
