package com.dayat.eightballline.util

import android.content.Context
import android.content.SharedPreferences
import com.dayat.eightballline.model.Pocket
import com.dayat.eightballline.model.TableConfig
import com.dayat.eightballline.model.TableOrientation

class PreferencesManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("eight_ball_line_prefs", Context.MODE_PRIVATE)

    fun saveConfig(config: TableConfig) {
        prefs.edit().apply {
            putFloat("table_left", config.left)
            putFloat("table_top", config.top)
            putFloat("table_right", config.right)
            putFloat("table_bottom", config.bottom)
            putString("table_orientation", config.orientation.name)
            putInt("active_pocket", config.activePocket.id)
            putInt("line_color", config.lineColor)
            putFloat("line_width", config.lineWidth)
            putInt("line_opacity", config.opacity)
            putBoolean("show_diamonds", config.showDiamonds)
            putBoolean("show_half_diamonds", config.showHalfDiamonds)
            putBoolean("show_lines", config.showBankShotLines)
            apply()
        }
    }

    fun loadConfig(screenWidth: Int, screenHeight: Int): TableConfig {
        val orientationStr = prefs.getString("table_orientation", TableOrientation.LANDSCAPE.name)
        val orientation = try {
            TableOrientation.valueOf(orientationStr ?: TableOrientation.LANDSCAPE.name)
        } catch (e: Exception) {
            TableOrientation.LANDSCAPE
        }

        val defaultMarginX = screenWidth * 0.1f
        val defaultMarginY = screenHeight * 0.15f

        val left = prefs.getFloat("table_left", defaultMarginX)
        val top = prefs.getFloat("table_top", defaultMarginY)
        val right = prefs.getFloat("table_right", screenWidth - defaultMarginX)
        val bottom = prefs.getFloat("table_bottom", screenHeight - defaultMarginY)

        val pocketId = prefs.getInt("active_pocket", Pocket.ALL.id)
        val activePocket = Pocket.values().firstOrNull { it.id == pocketId } ?: Pocket.ALL

        val lineColor = prefs.getInt("line_color", 0xFF00E5FF.toInt())
        val lineWidth = prefs.getFloat("line_width", 3f)
        val opacity = prefs.getInt("line_opacity", 210)

        val showDiamonds = prefs.getBoolean("show_diamonds", true)
        val showHalfDiamonds = prefs.getBoolean("show_half_diamonds", true)
        val showLines = prefs.getBoolean("show_lines", true)

        return TableConfig(
            left = left,
            top = top,
            right = right,
            bottom = bottom,
            orientation = orientation,
            activePocket = activePocket,
            lineColor = lineColor,
            lineWidth = lineWidth,
            opacity = opacity,
            showDiamonds = showDiamonds,
            showHalfDiamonds = showHalfDiamonds,
            showBankShotLines = showLines
        )
    }

    fun resetCalibration(screenWidth: Int, screenHeight: Int, orientation: TableOrientation): TableConfig {
        val marginX = screenWidth * 0.08f
        val marginY = screenHeight * 0.12f

        val config = TableConfig(
            left = marginX,
            top = marginY,
            right = screenWidth - marginX,
            bottom = screenHeight - marginY,
            orientation = orientation
        )
        saveConfig(config)
        return config
    }
}
