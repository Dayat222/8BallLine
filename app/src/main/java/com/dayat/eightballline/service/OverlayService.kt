package com.dayat.eightballline.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.dayat.eightballline.MainActivity
import com.dayat.eightballline.R
import com.dayat.eightballline.model.TableConfig
import com.dayat.eightballline.util.PreferencesManager
import com.dayat.eightballline.view.FloatingMenuManager
import com.dayat.eightballline.view.TableOverlayView
import kotlin.math.ceil
import kotlin.math.floor

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: TableOverlayView
    private lateinit var tableLayoutParams: WindowManager.LayoutParams
    private lateinit var prefs: PreferencesManager
    private var floatingMenuManager: FloatingMenuManager? = null

    companion object {
        const val CHANNEL_ID = "channel_grid_ruler_overlay"
        const val NOTIFICATION_ID = 8888
        const val ACTION_STOP_SERVICE = "com.dayat.screenruler.STOP_SERVICE"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        prefs = PreferencesManager(this)

        startForegroundNotification()
        setupTableOverlay()
        setupFloatingMenu()
    }

    private fun startForegroundNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "GridRuler Overlay Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Status layanan penggaris layar mengambang"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val openAppIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("GridRuler Aktif")
            .setContentText("Penggaris proyeksi sudut sedang berjalan")
            .setSmallIcon(R.drawable.ic_ball8)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun setupTableOverlay() {
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)

        val config = prefs.loadConfig(metrics.widthPixels, metrics.heightPixels)
        overlayView = TableOverlayView(this).apply {
            this.config = config
            this.onCalibrationChanged = { updatedConfig ->
                prefs.saveConfig(updatedConfig)
            }
        }

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        // Mode bermain: batasi ukuran window hanya seluas area area terkalibrasi (bukan MATCH_PARENT penuh layar)
        // dan patuhi batas opasitas untrusted touch Android 12+ (<= 0.80f).
        tableLayoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            alpha = 0.80f
        }
        applyWindowBounds(config, isCalibration = false)

        windowManager.addView(overlayView, tableLayoutParams)
    }

    private fun setupFloatingMenu() {
        floatingMenuManager = FloatingMenuManager(
            context = this,
            windowManager = windowManager,
            overlayView = overlayView,
            prefs = prefs,
            onStopServiceRequested = {
                stopSelf()
            },
            onCalibrationModeChanged = { isCalibration ->
                updateTableTouchability(isCalibration)
            },
            onTableBoundsChanged = {
                if (!overlayView.isCalibrationMode) {
                    applyWindowBounds(overlayView.config, isCalibration = false)
                    windowManager.updateViewLayout(overlayView, tableLayoutParams)
                }
            }
        ).also {
            it.attach()
        }
    }

    private fun applyWindowBounds(config: TableConfig, isCalibration: Boolean) {
        if (isCalibration) {
            tableLayoutParams.x = 0
            tableLayoutParams.y = 0
            tableLayoutParams.width = WindowManager.LayoutParams.MATCH_PARENT
            tableLayoutParams.height = WindowManager.LayoutParams.MATCH_PARENT
            tableLayoutParams.alpha = 1.0f
            overlayView.windowOffsetX = 0f
            overlayView.windowOffsetY = 0f
        } else {
            val pad = 3
            val winX = floor(config.left.toDouble()).toInt() - pad
            val winY = floor(config.top.toDouble()).toInt() - pad
            val winW = (ceil(config.right.toDouble()).toInt() + pad - winX).coerceAtLeast(24)
            val winH = (ceil(config.bottom.toDouble()).toInt() + pad - winY).coerceAtLeast(24)

            tableLayoutParams.x = winX
            tableLayoutParams.y = winY
            tableLayoutParams.width = winW
            tableLayoutParams.height = winH
            tableLayoutParams.alpha = 0.80f
            overlayView.windowOffsetX = winX.toFloat()
            overlayView.windowOffsetY = winY.toFloat()
        }
    }

    private fun updateTableTouchability(isCalibration: Boolean) {
        if (isCalibration) {
            // Ketika kalibrasi aktif: perluas ke layar penuh & buang FLAG_NOT_TOUCHABLE agar 4 sudut bisa digeser
            tableLayoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        } else {
            // Ketika bermain: batasi window hanya seluas area terkalibrasi + FLAG_NOT_TOUCHABLE
            tableLayoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        }
        applyWindowBounds(overlayView.config, isCalibration)
        windowManager.updateViewLayout(overlayView, tableLayoutParams)
    }

    override fun onDestroy() {
        super.onDestroy()
        floatingMenuManager?.detach()
        try {
            windowManager.removeView(overlayView)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
