package com.dayat.eightballline.view

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.util.DisplayMetrics
import android.view.*
import android.widget.*
import androidx.appcompat.view.ContextThemeWrapper
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import com.dayat.eightballline.R
import com.dayat.eightballline.model.PocketFilter
import com.dayat.eightballline.util.PreferencesManager
import kotlin.math.abs

class FloatingMenuManager(
    context: Context,
    private val windowManager: WindowManager,
    private val overlayView: TableOverlayView,
    private val prefs: PreferencesManager,
    private val onStopServiceRequested: () -> Unit,
    private val onCalibrationModeChanged: (Boolean) -> Unit,
    private val onTableBoundsChanged: () -> Unit = {}
) {

    private data class ColorThemePreset(
        val view: View,
        val blueColor: Int,
        val redColor: Int
    )

    private val themedContext: Context = ContextThemeWrapper(context, R.style.Theme_EightBallLine)
    private val menuView: View = LayoutInflater.from(themedContext).inflate(R.layout.view_floating_menu, null)
    private lateinit var layoutParams: WindowManager.LayoutParams

    private val layoutBubble: View = menuView.findViewById(R.id.layoutBubble)
    private val layoutExpandedPanel: View = menuView.findViewById(R.id.layoutExpandedPanel)
    private val btnMinimize: ImageView = menuView.findViewById(R.id.btnMinimize)
    private val switchLines: SwitchCompat = menuView.findViewById(R.id.switchLines)

    private val btnFilterAll: TextView = menuView.findViewById(R.id.btnFilterAll)
    private val btnFilterBlue: TextView = menuView.findViewById(R.id.btnFilterBlue)
    private val btnFilterRed: TextView = menuView.findViewById(R.id.btnFilterRed)

    private val tvOpacityLabel: TextView = menuView.findViewById(R.id.tvOpacityLabel)
    private val seekBarOpacity: SeekBar = menuView.findViewById(R.id.seekBarOpacity)
    private val tvLineWidthLabel: TextView = menuView.findViewById(R.id.tvLineWidthLabel)
    private val seekBarLineWidth: SeekBar = menuView.findViewById(R.id.seekBarLineWidth)

    private val btnThemeCyanRed: View = menuView.findViewById(R.id.btnThemeCyanRed)
    private val btnThemeDeepBlueRed: View = menuView.findViewById(R.id.btnThemeDeepBlueRed)
    private val btnThemeCyanOrange: View = menuView.findViewById(R.id.btnThemeCyanOrange)
    private val btnThemeGreenRed: View = menuView.findViewById(R.id.btnThemeGreenRed)
    private val btnThemeWhiteYellow: View = menuView.findViewById(R.id.btnThemeWhiteYellow)

    private val btnToggleCalibration: Button = menuView.findViewById(R.id.btnToggleCalibration)
    private val btnResetTableInOverlay: Button = menuView.findViewById(R.id.btnResetTableInOverlay)
    private val btnCloseOverlay: Button = menuView.findViewById(R.id.btnCloseOverlay)

    private val filterButtons by lazy {
        listOf(
            btnFilterAll to PocketFilter.ALL,
            btnFilterBlue to PocketFilter.BLUE_ONLY,
            btnFilterRed to PocketFilter.RED_ONLY
        )
    }

    private val colorThemes by lazy {
        listOf(
            // 1. Biru Cyan & Merah (default - persis foto referensi)
            ColorThemePreset(btnThemeCyanRed, 0xFF00D8FF.toInt(), 0xFFEF4444.toInt()),
            // 2. Biru & Merah pekat
            ColorThemePreset(btnThemeDeepBlueRed, 0xFF2563EB.toInt(), 0xFFDC2626.toInt()),
            // 3. Cyan & Oranye
            ColorThemePreset(btnThemeCyanOrange, 0xFF00E5FF.toInt(), 0xFFF97316.toInt()),
            // 4. Hijau & Merah
            ColorThemePreset(btnThemeGreenRed, 0xFF10B981.toInt(), 0xFFEF4444.toInt()),
            // 5. Putih & Kuning
            ColorThemePreset(btnThemeWhiteYellow, 0xFFFFFFFF.toInt(), 0xFFFACC15.toInt())
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
        setupFilterButtons()
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
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(metrics)
            val w = metrics.widthPixels.takeIf { it > 0 } ?: themedContext.resources.displayMetrics.widthPixels
            val h = metrics.heightPixels.takeIf { it > 0 } ?: themedContext.resources.displayMetrics.heightPixels
            val reset = prefs.resetCalibration(w, h, overlayView.config.orientation)
            overlayView.config.left = reset.left
            overlayView.config.top = reset.top
            overlayView.config.right = reset.right
            overlayView.config.bottom = reset.bottom
            overlayView.invalidate()
            onTableBoundsChanged()
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

    private fun setupFilterButtons() {
        updateFilterHighlights()
        for ((btn, filter) in filterButtons) {
            btn.setOnClickListener {
                overlayView.config.pocketFilter = filter
                overlayView.invalidate()
                prefs.saveConfig(overlayView.config)
                updateFilterHighlights()
            }
        }
    }

    private fun updateFilterHighlights() {
        val density = themedContext.resources.displayMetrics.density
        val active = overlayView.config.pocketFilter
        val blueCol = overlayView.config.blueLineColor
        val redCol = overlayView.config.redLineColor

        for ((btn, filter) in filterButtons) {
            val isSelected = filter == active
            val accent = when (filter) {
                PocketFilter.BLUE_ONLY -> blueCol
                PocketFilter.RED_ONLY -> redCol
                PocketFilter.ALL -> 0xFFE2E8F0.toInt()
            }
            btn.setTextColor(accent)
            btn.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 5f * density
                setColor(if (isSelected) 0xFF1F2937.toInt() else 0xFF111827.toInt())
                setStroke(
                    ((if (isSelected) 1.8f else 0.8f) * density).toInt(),
                    if (isSelected) accent else 0x33FFFFFF
                )
            }
        }
    }

    private fun setupThemePicker() {
        updateThemeHighlights()
        for (preset in colorThemes) {
            preset.view.setOnClickListener {
                overlayView.config.blueLineColor = preset.blueColor
                overlayView.config.redLineColor = preset.redColor
                overlayView.invalidate()
                prefs.saveConfig(overlayView.config)
                updateThemeHighlights()
                updateFilterHighlights()
            }
        }
    }

    private fun updateThemeHighlights() {
        val density = themedContext.resources.displayMetrics.density
        val activeBlue = overlayView.config.blueLineColor
        val activeRed = overlayView.config.redLineColor

        for (preset in colorThemes) {
            val isSelected = preset.blueColor == activeBlue && preset.redColor == activeRed
            val borderColor = if (isSelected) Color.WHITE else 0x55FFFFFF
            val strokePx = ((if (isSelected) 2.2f else 1f) * density).toInt()
            preset.view.background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(preset.blueColor, preset.redColor)
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
        val panelWidthPx = (220 * density).toInt()
        val screenH = themedContext.resources.displayMetrics.heightPixels
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
