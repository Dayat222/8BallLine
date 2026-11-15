package com.dayat.eightballline.view

import android.content.Context
import android.graphics.PixelFormat
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
    private val btnToggleCalibration: Button = menuView.findViewById(R.id.btnToggleCalibration)
    private val btnResetTableInOverlay: Button = menuView.findViewById(R.id.btnResetTableInOverlay)
    private val btnPrevDirection: TextView = menuView.findViewById(R.id.btnPrevDirection)
    private val tvSelectedDirection: TextView = menuView.findViewById(R.id.tvSelectedDirection)
    private val btnNextDirection: TextView = menuView.findViewById(R.id.btnNextDirection)
    private val btnPrevPocket: TextView = menuView.findViewById(R.id.btnPrevPocket)
    private val tvSelectedPocket: TextView = menuView.findViewById(R.id.tvSelectedPocket)
    private val btnNextPocket: TextView = menuView.findViewById(R.id.btnNextPocket)
    private val tvOpacityLabel: TextView = menuView.findViewById(R.id.tvOpacityLabel)
    private val seekBarOpacity: SeekBar = menuView.findViewById(R.id.seekBarOpacity)
    private val btnCloseOverlay: Button = menuView.findViewById(R.id.btnCloseOverlay)

    private val pockets = Pocket.values()
    private var currentPocketIndex = pockets.indexOf(overlayView.config.activePocket).coerceAtLeast(0)

    private val directions = BankDirection.values()
    private var currentDirectionIndex = directions.indexOf(overlayView.config.bankDirection).coerceAtLeast(0)

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
        setupDirectionSelector()
        setupPocketSelector()
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

        // Mode Kalibrasi vs Lock Meja
        btnToggleCalibration.setOnClickListener {
            val newMode = !overlayView.isCalibrationMode
            overlayView.isCalibrationMode = newMode
            onCalibrationModeChanged(newMode)

            if (newMode) {
                btnToggleCalibration.text = "Kunci Meja (Mode Main)"
                btnToggleCalibration.setBackgroundColor(ContextCompat.getColor(themedContext, R.color.accent_green))
            } else {
                btnToggleCalibration.text = "Masuk Mode Kalibrasi"
                btnToggleCalibration.setBackgroundColor(ContextCompat.getColor(themedContext, R.color.accent_red))
                prefs.saveConfig(overlayView.config)
            }
        }

        // Reset posisi meja agar pas di layar aktif (mis. saat sudah di dalam game landscape)
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
        tvOpacityLabel.text = "Opasitas Garis (${(overlayView.config.opacity * 100) / 255}%)"
        seekBarOpacity.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val clamped = progress.coerceIn(30, 255)
                overlayView.config.opacity = clamped
                overlayView.invalidate()
                tvOpacityLabel.text = "Opasitas Garis (${(clamped * 100) / 255}%)"
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

    private fun setupDirectionSelector() {
        tvSelectedDirection.text = directions[currentDirectionIndex].displayName

        btnPrevDirection.setOnClickListener {
            currentDirectionIndex = (currentDirectionIndex - 1 + directions.size) % directions.size
            applyDirectionSelection()
        }

        btnNextDirection.setOnClickListener {
            currentDirectionIndex = (currentDirectionIndex + 1) % directions.size
            applyDirectionSelection()
        }

        tvSelectedDirection.setOnClickListener {
            currentDirectionIndex = (currentDirectionIndex + 1) % directions.size
            applyDirectionSelection()
        }
    }

    private fun applyDirectionSelection() {
        val selectedDirection = directions[currentDirectionIndex]
        tvSelectedDirection.text = selectedDirection.displayName
        overlayView.config.bankDirection = selectedDirection
        overlayView.invalidate()
        prefs.saveConfig(overlayView.config)
    }

    private fun setupPocketSelector() {
        tvSelectedPocket.text = pockets[currentPocketIndex].displayName

        btnPrevPocket.setOnClickListener {
            currentPocketIndex = (currentPocketIndex - 1 + pockets.size) % pockets.size
            applyPocketSelection()
        }

        btnNextPocket.setOnClickListener {
            currentPocketIndex = (currentPocketIndex + 1) % pockets.size
            applyPocketSelection()
        }

        tvSelectedPocket.setOnClickListener {
            currentPocketIndex = (currentPocketIndex + 1) % pockets.size
            applyPocketSelection()
        }
    }

    private fun applyPocketSelection() {
        val selectedPocket = pockets[currentPocketIndex]
        tvSelectedPocket.text = selectedPocket.displayName
        overlayView.config.activePocket = selectedPocket
        overlayView.invalidate()
        prefs.saveConfig(overlayView.config)
    }

    private fun expandMenu() {
        layoutBubble.visibility = View.GONE
        layoutExpandedPanel.visibility = View.VISIBLE
        val density = themedContext.resources.displayMetrics.density
        val panelWidthPx = (220 * density).toInt()
        val screenH = overlayView.height.takeIf { it > 0 } ?: themedContext.resources.displayMetrics.heightPixels
        val maxPanelHeight = (screenH * 0.80f).toInt()
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
