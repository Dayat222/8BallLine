package com.dayat.eightballline.util

import android.content.Context
import android.content.SharedPreferences
import com.dayat.eightballline.model.Pocket
import com.dayat.eightballline.model.TableConfig
import com.dayat.eightballline.model.TableOrientation

class PreferencesManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("grid_ruler_prefs", Context.MODE_PRIVATE)

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
            putInt("ref_line_color", config.lineColor)
            putInt("ref_secondary_color", config.secondaryLineColor)
            putFloat("ref_line_width", config.lineWidth)
            putInt("ref_line_opacity", config.opacity)
            putBoolean("show_lines", config.showBankShotLines)
            putBoolean("show_secondary_lines", config.showSecondaryLines)
            apply()
        }
    }

    fun updateLineColor(color: Int) {
        prefs.edit().putInt("ref_line_color", color).apply()
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

        val lineColor = prefs.getInt("ref_line_color", 0xFFFFFFFF.toInt())
        val secondaryLineColor = prefs.getInt("ref_secondary_color", 0xFF14B8A6.toInt())
        val lineWidth = prefs.getFloat("ref_line_width", 1.6f)
        val opacity = prefs.getInt("ref_line_opacity", 215)

        val showLines = prefs.getBoolean("show_lines", true)
        val showSecondaryLines = prefs.getBoolean("show_secondary_lines", true)

        return TableConfig(
            left = left,
            top = top,
            right = right,
            bottom = bottom,
            orientation = orientation,
            activePocket = activePocket,
            lineColor = lineColor,
            secondaryLineColor = secondaryLineColor,
            lineWidth = lineWidth,
            opacity = opacity,
            showBankShotLines = showLines,
            showSecondaryLines = showSecondaryLines
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
