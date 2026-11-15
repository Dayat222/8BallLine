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
import com.dayat.eightballline.model.PointF2D
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

    // Aim Pin (Bidik Bola) window — aktif langsung sebagai mode utama
    private val aimPinView: View = LayoutInflater.from(themedContext).inflate(R.layout.view_aim_pin, null)
    private lateinit var aimPinParams: WindowManager.LayoutParams
    private var isAimPinActive = true

    // Mini 1-Tap Quick Dock (Slim Capsule)
    private val layoutBubble: LinearLayout = menuView.findViewById(R.id.layoutBubble)
    private val btnMiniMenu: View = menuView.findViewById(R.id.btnMiniMenu)
    private val btnQuickTL: TextView = menuView.findViewById(R.id.btnQuickTL)
    private val btnQuickML: TextView = menuView.findViewById(R.id.btnQuickML)
    private val btnQuickTR: TextView = menuView.findViewById(R.id.btnQuickTR)
    private val btnQuickAimPin: TextView = menuView.findViewById(R.id.btnQuickAimPin)
    private val btnQuickAll: TextView = menuView.findViewById(R.id.btnQuickAll)
    private val btnQuickBL: TextView = menuView.findViewById(R.id.btnQuickBL)
    private val btnQuickMR: TextView = menuView.findViewById(R.id.btnQuickMR)
    private val btnQuickBR: TextView = menuView.findViewById(R.id.btnQuickBR)
    private val btnQuickBounce0: TextView = menuView.findViewById(R.id.btnQuickBounce0)
    private val btnQuickBounce1: TextView = menuView.findViewById(R.id.btnQuickBounce1)
    private val btnQuickBounce2: TextView = menuView.findViewById(R.id.btnQuickBounce2)
    private val btnQuickBounce3: TextView = menuView.findViewById(R.id.btnQuickBounce3)
    private val btnQuickDir: TextView = menuView.findViewById(R.id.btnQuickDir)
    private val btnQuickRotate: TextView = menuView.findViewById(R.id.btnQuickRotate)
    private var isDockVertical = false

    // Expanded Settings Panel
    private val layoutExpandedPanel: View = menuView.findViewById(R.id.layoutExpandedPanel)
    private val btnMinimize: ImageView = menuView.findViewById(R.id.btnMinimize)
    private val switchLines: SwitchCompat = menuView.findViewById(R.id.switchLines)
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

    private val btnBounce0: TextView = menuView.findViewById(R.id.btnBounce0)
    private val btnBounce1: TextView = menuView.findViewById(R.id.btnBounce1)
    private val btnBounce2: TextView = menuView.findViewById(R.id.btnBounce2)
    private val btnBounce3: TextView = menuView.findViewById(R.id.btnBounce3)

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

    private val quickPocketButtons by lazy {
        listOf(
            btnQuickTL to Pocket.TOP_LEFT,
            btnQuickML to Pocket.MIDDLE_LEFT,
            btnQuickTR to Pocket.TOP_RIGHT,
            btnQuickBL to Pocket.BOTTOM_LEFT,
            btnQuickMR to Pocket.MIDDLE_RIGHT,
            btnQuickBR to Pocket.BOTTOM_RIGHT,
            btnQuickAll to Pocket.ALL
        )
    }

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

    private val quickBounceButtons by lazy {
        listOf(
            btnQuickBounce0 to 0,
            btnQuickBounce1 to 1,
            btnQuickBounce2 to 2,
            btnQuickBounce3 to 3
        )
    }

    private val bounceButtons by lazy {
        listOf(
            btnBounce0 to 0,
            btnBounce1 to 1,
            btnBounce2 to 2,
            btnBounce3 to 3
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
            x = 120
            y = 12
        }

        val pinSizePx = (40 * themedContext.resources.displayMetrics.density).toInt()
        val initCenter = getDefaultAimCenter()
        aimPinParams = WindowManager.LayoutParams(
            pinSizePx,
            pinSizePx,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (initCenter.x - pinSizePx / 2f).toInt()
            y = (initCenter.y - pinSizePx / 2f).toInt()
        }
        aimPinView.visibility = if (isAimPinActive) View.VISIBLE else View.GONE

        setupMiniDock()
        setupAimPinDrag()
        setupListeners()
        setupPocketGrid()
        setupBounceGrid()
        setupDirectionGrid()
        setupColorPicker()

        windowManager.addView(menuView, layoutParams)
        windowManager.addView(aimPinView, aimPinParams)

        overlayView.onViewSizeChanged = { w, h ->
            if (isAimPinActive) {
                if (aimPinParams.x < 0 || aimPinParams.x > w - pinSizePx ||
                    aimPinParams.y < 0 || aimPinParams.y > h - pinSizePx
                ) {
                    centerAimPinInView(w, h)
                } else {
                    aimPinView.post { syncAimTargetFromPin() }
                }
            }
        }

        if (isAimPinActive) {
            overlayView.aimTarget = initCenter
            aimPinView.post { syncAimTargetFromPin() }
        }
    }

    private fun getDefaultAimCenter(): PointF2D {
        val w = overlayView.width.takeIf { it > 0 } ?: themedContext.resources.displayMetrics.widthPixels
        val h = overlayView.height.takeIf { it > 0 } ?: themedContext.resources.displayMetrics.heightPixels
        val cx = ((overlayView.config.left + overlayView.config.right) / 2f).coerceIn(40f, (w - 40f).coerceAtLeast(40f))
        val cy = ((overlayView.config.top + overlayView.config.bottom) / 2f).coerceIn(40f, (h - 40f).coerceAtLeast(40f))
        return PointF2D(cx, cy)
    }

    private fun centerAimPinInView(viewW: Int, viewH: Int) {
        val pinW = aimPinParams.width
        val pinH = aimPinParams.height
        val cx = ((overlayView.config.left + overlayView.config.right) / 2f).coerceIn(40f, (viewW - 40f).coerceAtLeast(40f))
        val cy = ((overlayView.config.top + overlayView.config.bottom) / 2f).coerceIn(40f, (viewH - 40f).coerceAtLeast(40f))
        aimPinParams.x = (cx - pinW / 2f).toInt()
        aimPinParams.y = (cy - pinH / 2f).toInt()
        safeUpdateAimPinLayout()
        overlayView.aimTarget = PointF2D(cx, cy)
        aimPinView.post { syncAimTargetFromPin() }
    }

    private fun attachDragOrClick(target: View, onClick: () -> Unit) {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false

        target.setOnTouchListener { _, event ->
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
                        onClick()
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun setupMiniDock() {
        attachDragOrClick(btnMiniMenu) {
            expandMenu()
        }

        for ((btn, pocket) in quickPocketButtons) {
            attachDragOrClick(btn) {
                selectPocket(pocket)
            }
        }

        for ((btn, bounce) in quickBounceButtons) {
            attachDragOrClick(btn) {
                selectBounceCount(bounce)
            }
        }

        attachDragOrClick(btnQuickAimPin) {
            toggleAimPin()
        }

        attachDragOrClick(btnQuickDir) {
            val dirs = BankDirection.values()
            val nextIdx = (dirs.indexOf(overlayView.config.bankDirection) + 1) % dirs.size
            selectDirection(dirs[nextIdx])
        }

        attachDragOrClick(btnQuickRotate) {
            isDockVertical = !isDockVertical
            layoutBubble.orientation = if (isDockVertical) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
            safeUpdateViewLayout()
        }
    }

    private fun setupAimPinDrag() {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        aimPinView.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = aimPinParams.x
                    initialY = aimPinParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    aimPinParams.x = initialX + dx
                    aimPinParams.y = initialY + dy
                    safeUpdateAimPinLayout()
                    syncAimTargetFromPin()
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    aimPinView.post { syncAimTargetFromPin() }
                    true
                }
                else -> false
            }
        }
    }

    private fun toggleAimPin() {
        isAimPinActive = !isAimPinActive
        if (isAimPinActive) {
            val w = overlayView.width.takeIf { it > 0 } ?: themedContext.resources.displayMetrics.widthPixels
            val h = overlayView.height.takeIf { it > 0 } ?: themedContext.resources.displayMetrics.heightPixels
            aimPinView.visibility = View.VISIBLE
            centerAimPinInView(w, h)
        } else {
            aimPinView.visibility = View.GONE
            overlayView.aimTarget = null
        }
        updateQuickAimPinStyle()
    }

    private fun syncAimTargetFromPin() {
        if (!isAimPinActive || !aimPinView.isAttachedToWindow) return
        val pinLoc = IntArray(2)
        val overlayLoc = IntArray(2)
        aimPinView.getLocationOnScreen(pinLoc)
        overlayView.getLocationOnScreen(overlayLoc)

        val cx = (pinLoc[0] - overlayLoc[0]) + (aimPinView.width.takeIf { it > 0 } ?: aimPinParams.width) / 2f
        val cy = (pinLoc[1] - overlayLoc[1]) + (aimPinView.height.takeIf { it > 0 } ?: aimPinParams.height) / 2f
        overlayView.aimTarget = PointF2D(cx, cy)
    }

    private fun updateQuickAimPinStyle() {
        val density = themedContext.resources.displayMetrics.density
        btnQuickAimPin.setTextColor(if (isAimPinActive) 0xFF000000.toInt() else 0xFF00E5FF.toInt())
        btnQuickAimPin.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 5f * density
            setColor(if (isAimPinActive) 0xFF00E5FF.toInt() else 0xFF111827.toInt())
            setStroke((1.2f * density).toInt(), 0xFF00E5FF.toInt())
        }
    }

    private fun setupListeners() {
        btnMinimize.setOnClickListener {
            collapseMenu()
        }

        // Toggle Garis Bidik Pin
        switchLines.isChecked = overlayView.config.showBankShotLines
        switchLines.setOnCheckedChangeListener { _, isChecked ->
            overlayView.config.showBankShotLines = isChecked
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

        updateQuickAimPinStyle()
    }

    private fun selectPocket(pocket: Pocket) {
        overlayView.config.activePocket = pocket
        overlayView.invalidate()
        prefs.saveConfig(overlayView.config)
        updatePocketHighlights()
    }

    private fun setupPocketGrid() {
        updatePocketHighlights()
        for ((btn, pocket) in pocketButtons) {
            btn.setOnClickListener {
                selectPocket(pocket)
            }
        }
    }

    private fun updatePocketHighlights() {
        val density = themedContext.resources.displayMetrics.density
        val active = overlayView.config.activePocket
        val usePocketColors = overlayView.config.colorByPocket

        val allButtons = pocketButtons + quickPocketButtons
        for ((btn, pocket) in allButtons) {
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
                cornerRadius = 5f * density
                setColor(bgColor)
                setStroke(((if (isSelected) 1.8f else 0.8f) * density).toInt(), strokeColor)
            }
        }
    }

    private fun selectBounceCount(bounce: Int) {
        overlayView.config.bounceCount = bounce.coerceIn(0, 3)
        overlayView.invalidate()
        prefs.saveConfig(overlayView.config)
        updateBounceHighlights()
    }

    private fun setupBounceGrid() {
        updateBounceHighlights()
        for ((btn, bounce) in bounceButtons) {
            btn.setOnClickListener {
                selectBounceCount(bounce)
            }
        }
    }

    private fun updateBounceHighlights() {
        val density = themedContext.resources.displayMetrics.density
        val active = overlayView.config.bounceCount

        for ((btn, bounce) in bounceButtons + quickBounceButtons) {
            val isSelected = bounce == active
            btn.setTextColor(if (isSelected) 0xFF000000.toInt() else 0xFFE2E8F0.toInt())
            btn.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 5f * density
                setColor(if (isSelected) 0xFFF59E0B.toInt() else 0xFF111827.toInt())
                setStroke(
                    ((if (isSelected) 1.5f else 0.8f) * density).toInt(),
                    if (isSelected) 0xFFF59E0B.toInt() else 0x33FFFFFF
                )
            }
        }
    }

    private fun selectDirection(direction: BankDirection) {
        overlayView.config.bankDirection = direction
        overlayView.invalidate()
        prefs.saveConfig(overlayView.config)
        updateDirectionHighlights()
    }

    private fun setupDirectionGrid() {
        updateDirectionHighlights()
        for ((btn, direction) in directionButtons) {
            btn.setOnClickListener {
                selectDirection(direction)
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

        btnQuickDir.text = when (active) {
            BankDirection.ALL -> "✛"
            BankDirection.TOP_BOTTOM -> "↕"
            BankDirection.LEFT_RIGHT -> "↔"
        }
        btnQuickDir.setTextColor(if (active == BankDirection.ALL) 0xFFE2E8F0.toInt() else 0xFF00E5FF.toInt())
        btnQuickDir.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 5f * density
            setColor(if (active == BankDirection.ALL) 0xFF111827.toInt() else 0xFF1F2937.toInt())
            setStroke(
                (1.0f * density).toInt(),
                if (active == BankDirection.ALL) 0x33FFFFFF else 0xFF00E5FF.toInt()
            )
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

    private fun safeUpdateAimPinLayout() {
        try {
            if (aimPinView.isAttachedToWindow) {
                windowManager.updateViewLayout(aimPinView, aimPinParams)
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
        try {
            if (aimPinView.isAttachedToWindow) {
                windowManager.removeView(aimPinView)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
