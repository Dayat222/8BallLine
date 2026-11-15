package com.dayat.eightballline.view

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.*
import android.widget.*
import androidx.appcompat.widget.SwitchCompat
import com.dayat.eightballline.R
import com.dayat.eightballline.model.Pocket
import com.dayat.eightballline.model.TableConfig
import com.dayat.eightballline.util.PreferencesManager
import kotlin.math.abs

class FloatingMenuManager(
    private val context: Context,
    private val windowManager: WindowManager,
    private val overlayView: TableOverlayView,
    private val prefs: PreferencesManager,
    private val onStopServiceRequested: () -> Unit,
    private val onCalibrationModeChanged: (Boolean) -> Unit
) {

    private val menuView: View = LayoutInflater.from(context).inflate(R.layout.view_floating_menu, null)
    private lateinit var layoutParams: WindowManager.LayoutParams

    private val layoutBubble: View = menuView.findViewById(R.id.layoutBubble)
    private val layoutExpandedPanel: View = menuView.findViewById(R.id.layoutExpandedPanel)
    private val btnMinimize: ImageView = menuView.findViewById(R.id.btnMinimize)
    private val switchLines: SwitchCompat = menuView.findViewById(R.id.switchLines)
    private val btnToggleCalibration: Button = menuView.findViewById(R.id.btnToggleCalibration)
    private val spinnerPockets: Spinner = menuView.findViewById(R.id.spinnerPockets)
    private val tvOpacityLabel: TextView = menuView.findViewById(R.id.tvOpacityLabel)
    private val seekBarOpacity: SeekBar = menuView.findViewById(R.id.seekBarOpacity)
    private val btnCloseOverlay: Button = menuView.findViewById(R.id.btnCloseOverlay)

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
        setupSpinner()
        windowManager.addView(menuView, layoutParams)
    }

    private fun setupListeners() {
        // Dragging & clicking bubble
        layoutBubble.setOnTouchListener { _, event ->
            when (event.action) {
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
                        windowManager.updateViewLayout(menuView, layoutParams)
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

        // Mode Kalibrasi vs Lock Meja
        btnToggleCalibration.setOnClickListener {
            val newMode = !overlayView.isCalibrationMode
            overlayView.isCalibrationMode = newMode
            onCalibrationModeChanged(newMode)

            if (newMode) {
                btnToggleCalibration.text = "Kunci Meja (Mode Main)"
                btnToggleCalibration.setBackgroundColor(context.getColor(R.color.accent_green))
            } else {
                btnToggleCalibration.text = "Masuk Mode Kalibrasi"
                btnToggleCalibration.setBackgroundColor(context.getColor(R.color.accent_red))
                prefs.saveConfig(overlayView.config)
            }
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

    private fun setupSpinner() {
        val pocketList = Pocket.values().map { it.displayName }
        val adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, pocketList)
        spinnerPockets.adapter = adapter

        val currentIdx = Pocket.values().indexOf(overlayView.config.activePocket)
        if (currentIdx >= 0) {
            spinnerPockets.setSelection(currentIdx)
        }

        spinnerPockets.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedPocket = Pocket.values()[position]
                overlayView.config.activePocket = selectedPocket
                overlayView.invalidate()
                prefs.saveConfig(overlayView.config)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun expandMenu() {
        layoutBubble.visibility = View.GONE
        layoutExpandedPanel.visibility = View.VISIBLE
    }

    private fun collapseMenu() {
        layoutExpandedPanel.visibility = View.GONE
        layoutBubble.visibility = View.VISIBLE
    }

    fun detach() {
        try {
            windowManager.removeView(menuView)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
