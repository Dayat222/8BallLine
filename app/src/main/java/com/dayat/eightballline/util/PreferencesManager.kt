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

    fun saveConfig(config: TableConfig) {
        prefs.edit().apply {
            putFloat("table_left", config.left)
            putFloat("table_top", config.top)
            putFloat("table_right", config.right)
            putFloat("table_bottom", config.bottom)
            putString("table_orientation", config.orientation.name)
            putInt("active_pocket", config.activePocket.id)
            putInt("bank_direction", config.bankDirection.id)
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

        val (effW, effH) = effectiveDimensions(screenWidth, screenHeight, orientation)
        val (defLeft, defTop, defRight, defBottom) = defaultBounds(effW, effH, orientation)

        var left = prefs.getFloat("table_left", defLeft)
        var top = prefs.getFloat("table_top", defTop)
        var right = prefs.getFloat("table_right", defRight)
        var bottom = prefs.getFloat("table_bottom", defBottom)

        // Jika koordinat tersimpan keluar dari batas layar orientasi aktif, sesuaikan agar 4 sudut selalu terlihat
        val margin = 24f
        if (left < margin || top < margin || right > effW - margin || bottom > effH - margin ||
            right - left < 120f || bottom - top < 80f
        ) {
            left = left.coerceIn(margin, (effW - margin - 120f).coerceAtLeast(margin))
            top = top.coerceIn(margin, (effH - margin - 80f).coerceAtLeast(margin))
            right = right.coerceIn((left + 120f).coerceAtMost(effW - margin), effW - margin)
            bottom = bottom.coerceIn((top + 80f).coerceAtMost(effH - margin), effH - margin)
        }

        val pocketId = prefs.getInt("active_pocket", Pocket.ALL.id)
        val activePocket = Pocket.values().firstOrNull { it.id == pocketId } ?: Pocket.ALL

        val directionId = prefs.getInt("bank_direction", BankDirection.ALL.id)
        val bankDirection = BankDirection.values().firstOrNull { it.id == directionId } ?: BankDirection.ALL

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
            bankDirection = bankDirection,
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
