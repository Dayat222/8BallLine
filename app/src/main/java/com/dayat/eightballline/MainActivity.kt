package com.dayat.eightballline

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.dayat.eightballline.databinding.ActivityMainBinding
import com.dayat.eightballline.model.TableOrientation
import com.dayat.eightballline.service.OverlayService
import com.dayat.eightballline.util.PreferencesManager

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: PreferencesManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = PreferencesManager(this)

        setupUI()
        setupListeners()
    }

    override fun onResume() {
        super.onResume()
        checkOverlayPermission()
        updateServiceStatus()
    }

    private fun setupUI() {
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)

        val config = prefs.loadConfig(metrics.widthPixels, metrics.heightPixels)

        // Set radio orientation
        if (config.orientation == TableOrientation.LANDSCAPE) {
            binding.rbLandscape.isChecked = true
        } else {
            binding.rbPortrait.isChecked = true
        }
    }

    private fun setupListeners() {
        binding.btnGrantPermission.setOnClickListener {
            requestOverlayPermission()
        }

        binding.btnToggleService.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                requestOverlayPermission()
                return@setOnClickListener
            }

            if (isServiceRunning(OverlayService::class.java)) {
                stopOverlayService()
            } else {
                startOverlayService()
            }
        }

        binding.rgOrientation.setOnCheckedChangeListener { _, checkedId ->
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(metrics)
            val config = prefs.loadConfig(metrics.widthPixels, metrics.heightPixels)

            config.orientation = if (checkedId == R.id.rbLandscape) {
                TableOrientation.LANDSCAPE
            } else {
                TableOrientation.PORTRAIT
            }
            prefs.saveConfig(config)
            Toast.makeText(this, "Orientasi diubah: ${config.orientation.displayName}", Toast.LENGTH_SHORT).show()
        }

        // Color buttons
        binding.btnColorCyan.setOnClickListener { selectColor(0xFF00E5FF.toInt(), "Cyan Neon") }
        binding.btnColorGreen.setOnClickListener { selectColor(0xFF10B981.toInt(), "Hijau Emerald") }
        binding.btnColorYellow.setOnClickListener { selectColor(0xFFF59E0B.toInt(), "Kuning Gold") }
        binding.btnColorRed.setOnClickListener { selectColor(0xFFEF4444.toInt(), "Merah Neon") }

        // Reset calibration
        binding.btnResetCalibration.setOnClickListener {
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(metrics)

            val orientation = if (binding.rbLandscape.isChecked) {
                TableOrientation.LANDSCAPE
            } else {
                TableOrientation.PORTRAIT
            }
            prefs.resetCalibration(metrics.widthPixels, metrics.heightPixels, orientation)
            Toast.makeText(this, "Ukuran meja berhasil di-reset ke default", Toast.LENGTH_SHORT).show()
        }
    }

    private fun selectColor(color: Int, name: String) {
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)
        val config = prefs.loadConfig(metrics.widthPixels, metrics.heightPixels)
        config.lineColor = color
        prefs.saveConfig(config)
        Toast.makeText(this, "Warna garis diubah: $name", Toast.LENGTH_SHORT).show()
    }

    private fun checkOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            binding.cardPermission.visibility = View.VISIBLE
        } else {
            binding.cardPermission.visibility = View.GONE
        }
    }

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }
    }

    private fun startOverlayService() {
        val intent = Intent(this, OverlayService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.startForegroundService(this, intent)
        } else {
            startService(intent)
        }
        updateServiceStatus()
        Toast.makeText(this, "Overlay 8BallLine Aktif! Buka game 8 Ball Pool.", Toast.LENGTH_LONG).show()
    }

    private fun stopOverlayService() {
        val intent = Intent(this, OverlayService::class.java)
        stopService(intent)
        updateServiceStatus()
        Toast.makeText(this, "Overlay 8BallLine Dinonaktifkan", Toast.LENGTH_SHORT).show()
    }

    private fun updateServiceStatus() {
        val running = isServiceRunning(OverlayService::class.java)
        if (running) {
            binding.tvStatusText.text = getString(R.string.status_running)
            binding.tvStatusText.setTextColor(ContextCompat.getColor(this, R.color.accent_green))
            binding.btnToggleService.text = getString(R.string.btn_stop_overlay)
            binding.btnToggleService.setBackgroundColor(ContextCompat.getColor(this, R.color.accent_red))
        } else {
            binding.tvStatusText.text = getString(R.string.status_stopped)
            binding.tvStatusText.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
            binding.btnToggleService.text = getString(R.string.btn_start_overlay)
            binding.btnToggleService.setBackgroundColor(ContextCompat.getColor(this, R.color.neon_cyan))
        }
    }

    private fun isServiceRunning(serviceClass: Class<*>): Boolean {
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        @Suppress("DEPRECATION")
        for (service in manager.getRunningServices(Int.MAX_VALUE)) {
            if (serviceClass.name == service.service.className) {
                return true
            }
        }
        return false
    }
}
