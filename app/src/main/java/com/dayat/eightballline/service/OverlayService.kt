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
import com.dayat.eightballline.util.PreferencesManager
import com.dayat.eightballline.view.FloatingMenuManager
import com.dayat.eightballline.view.TableOverlayView

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

        // Default: FLAG_NOT_TOUCHABLE agar semua sentuhan langsung tembus ke game 8 Ball Pool
        tableLayoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

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
            }
        ).also {
            it.attach()
        }
    }

    private fun updateTableTouchability(isCalibration: Boolean) {
        if (isCalibration) {
            // Ketika kalibrasi aktif: buang FLAG_NOT_TOUCHABLE agar 4 titik sudut merah bisa digeser
            tableLayoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        } else {
            // Ketika bermain: pasang FLAG_NOT_TOUCHABLE agar game 8 Ball Pool dapat disentuh secara bebas
            tableLayoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        }
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
