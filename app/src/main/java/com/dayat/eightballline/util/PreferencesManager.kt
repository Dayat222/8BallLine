package com.dayat.eightballline.util

import android.content.Context
import android.content.SharedPreferences
import com.dayat.eightballline.model.BankDirection
import com.dayat.eightballline.model.Pocket
import com.dayat.eightballline.model.TableConfig
import com.dayat.eightballline.model.TableOrientation

class PreferencesManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("eight_ball_line_prefs", Context.MODE_PRIVATE)

    fun hasSavedCalibration(): Boolean {
        return prefs.contains("table_left") &&
                prefs.contains("table_top") &&
                prefs.contains("table_right") &&
                prefs.contains("table_bottom")
    }

    fun saveConfig(config: TableConfig) {
        prefs.edit().apply {
            putFloat("table_left", config.left)
            putFloat("table_top", config.top)
            putFloat("table_right", config.right)
            putFloat("table_bottom", config.bottom)
            putBoolean("has_saved_calibration", true)
            putString("table_orientation", config.orientation.name)
            putInt("active_pocket", config.activePocket.id)
            putInt("bank_direction", config.bankDirection.id)
            putString("enabled_bounces_csv", config.enabledBounces.sorted().joinToString(","))
            putBoolean("color_by_pocket", config.colorByPocket)
            putInt("line_color", config.lineColor)
            putFloat("line_width_v3", config.lineWidth)
            putInt("line_opacity_v2", config.opacity)
            putBoolean("show_diamonds", config.showDiamonds)
            putBoolean("show_half_lines_v2", config.showHalfDiamonds)
            putBoolean("show_lines", config.showBankShotLines)
            apply()
        }
    }

    fun updateLineColor(color: Int) {
        prefs.edit().putInt("line_color", color).apply()
    }

    fun updateOrientation(orientation: TableOrientation) {
        prefs.edit().putString("table_orientation", orientation.name).apply()
    }

    fun loadConfig(screenWidth: Int, screenHeight: Int): TableConfig {
        val orientationStr = prefs.getString("table_orientation", TableOrientation.LANDSCAPE.name)
        val orientation = try {
            TableOrientation.valueOf(orientationStr ?: TableOrientation.LANDSCAPE.name)
        } catch (e: Exception) {
            TableOrientation.LANDSCAPE
        }

        val (effW, effH) = effectiveDimensions(screenWidth, screenHeight, orientation)
        val (defLeft, defTop, defRight, defBottom) = defaultBounds(effW, effH, orientation)

        val left: Float
        val top: Float
        val right: Float
        val bottom: Float

        if (hasSavedCalibration()) {
            // Pertahankan hasil kalibrasi user apa adanya tanpa diubah/di-clamp otomatis
            val savedLeft = prefs.getFloat("table_left", defLeft)
            val savedTop = prefs.getFloat("table_top", defTop)
            val savedRight = prefs.getFloat("table_right", defRight)
            val savedBottom = prefs.getFloat("table_bottom", defBottom)

            val isValid = !savedLeft.isNaN() && !savedTop.isNaN() &&
                    !savedRight.isNaN() && !savedBottom.isNaN() &&
                    (savedRight - savedLeft) >= 40f && (savedBottom - savedTop) >= 40f

            if (isValid) {
                left = savedLeft
                top = savedTop
                right = savedRight
                bottom = savedBottom
            } else {
                left = defLeft
                top = defTop
                right = defRight
                bottom = defBottom
            }
        } else {
            left = defLeft
            top = defTop
            right = defRight
            bottom = defBottom
        }

        val pocketId = prefs.getInt("active_pocket", Pocket.ALL.id)
        val activePocket = Pocket.values().firstOrNull { it.id == pocketId } ?: Pocket.ALL

        val directionId = prefs.getInt("bank_direction", BankDirection.ALL.id)
        val bankDirection = BankDirection.values().firstOrNull { it.id == directionId } ?: BankDirection.ALL

        val enabledBounces: MutableSet<Int> = if (prefs.contains("enabled_bounces_csv")) {
            val csv = prefs.getString("enabled_bounces_csv", "1").orEmpty()
            csv.split(",")
                .mapNotNull { it.trim().toIntOrNull() }
                .filter { it in 0..2 }
                .toMutableSet()
                .also { if (it.isEmpty() && csv.contains("3")) it.add(1) }
        } else {
            mutableSetOf(prefs.getInt("bounce_count", 1).coerceIn(0, 2))
        }
        val colorByPocket = prefs.getBoolean("color_by_pocket", true)
        val lineColor = prefs.getInt("line_color", 0xFF00E5FF.toInt())
        val lineWidth = prefs.getFloat("line_width_v3", 2.6f)
        val opacity = prefs.getInt("line_opacity_v2", 240)

        val showDiamonds = prefs.getBoolean("show_diamonds", true)
        val showHalfDiamonds = prefs.getBoolean("show_half_lines_v2", true)
        val showLines = prefs.getBoolean("show_lines", true)

        return TableConfig(
            left = left,
            top = top,
            right = right,
            bottom = bottom,
            orientation = orientation,
            activePocket = activePocket,
            bankDirection = bankDirection,
            enabledBounces = enabledBounces,
            colorByPocket = colorByPocket,
            lineColor = lineColor,
            lineWidth = lineWidth,
            opacity = opacity,
            showDiamonds = showDiamonds,
            showHalfDiamonds = showHalfDiamonds,
            showBankShotLines = showLines
        )
    }

    fun resetCalibration(screenWidth: Int, screenHeight: Int, orientation: TableOrientation): TableConfig {
        val (effW, effH) = effectiveDimensions(screenWidth, screenHeight, orientation)
        val (defLeft, defTop, defRight, defBottom) = defaultBounds(effW, effH, orientation)

        val current = loadConfig(screenWidth, screenHeight)
        current.left = defLeft
        current.top = defTop
        current.right = defRight
        current.bottom = defBottom
        current.orientation = orientation
        saveConfig(current)
        return current
    }

    private fun effectiveDimensions(
        screenWidth: Int,
        screenHeight: Int,
        orientation: TableOrientation
    ): Pair<Float, Float> {
        val w = screenWidth.toFloat().coerceAtLeast(200f)
        val h = screenHeight.toFloat().coerceAtLeast(200f)
        return if (orientation == TableOrientation.LANDSCAPE) {
            maxOf(w, h) to minOf(w, h)
        } else {
            minOf(w, h) to maxOf(w, h)
        }
    }

    private fun defaultBounds(
        effW: Float,
        effH: Float,
        orientation: TableOrientation
    ): FloatArray {
        return if (orientation == TableOrientation.LANDSCAPE) {
            floatArrayOf(effW * 0.16f, effH * 0.14f, effW * 0.84f, effH * 0.88f)
        } else {
            floatArrayOf(effW * 0.10f, effH * 0.18f, effW * 0.90f, effH * 0.82f)
        }
    }
}
