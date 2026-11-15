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
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.dayat.eightballline.databinding.ActivityMainBinding
import com.dayat.eightballline.model.TableOrientation
import com.dayat.eightballline.service.OverlayService
import com.dayat.eightballline.util.OtaUpdateManager
import com.dayat.eightballline.util.PreferencesManager
import com.dayat.eightballline.util.UpdateCheckResult
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: PreferencesManager
    private lateinit var otaManager: OtaUpdateManager
    private var pendingApkFile: File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = PreferencesManager(this)
        otaManager = OtaUpdateManager(this)

        setupUI()
        setupListeners()
        checkOtaUpdate(isManual = false)
    }

    override fun onResume() {
        super.onResume()
        checkOverlayPermission()
        updateServiceStatus()

        pendingApkFile?.let { file ->
            if (file.exists() && otaManager.canInstallPackages()) {
                pendingApkFile = null
                otaManager.launchApkInstaller(file)
            }
        }
    }

    private fun setupUI() {
        binding.tvAppVersion.text = "Versi ${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})"

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
            val orientation = if (checkedId == R.id.rbLandscape) {
                TableOrientation.LANDSCAPE
            } else {
                TableOrientation.PORTRAIT
            }
            prefs.updateOrientation(orientation)
            Toast.makeText(this, "Orientasi diubah: ${orientation.displayName}", Toast.LENGTH_SHORT).show()
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

        // Check OTA update
        binding.btnCheckUpdate.setOnClickListener {
            checkOtaUpdate(isManual = true)
        }
    }

    private fun selectColor(color: Int, name: String) {
        prefs.updateLineColor(color)
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
        Toast.makeText(this, "Overlay GridRuler Aktif!", Toast.LENGTH_LONG).show()
    }

    private fun stopOverlayService() {
        val intent = Intent(this, OverlayService::class.java)
        stopService(intent)
        updateServiceStatus()
        Toast.makeText(this, "Overlay GridRuler Dinonaktifkan", Toast.LENGTH_SHORT).show()
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

    private fun checkOtaUpdate(isManual: Boolean) {
        if (isManual) {
            binding.btnCheckUpdate.isEnabled = false
            binding.tvOtaStatus.text = getString(R.string.ota_checking)
        }

        otaManager.checkForUpdate { result ->
            binding.btnCheckUpdate.isEnabled = true
            when (result) {
                is UpdateCheckResult.UpdateAvailable -> {
                    val sizeMb = if (result.fileSizeBytes > 0) {
                        String.format("%.1f MB", result.fileSizeBytes / (1024f * 1024f))
                    } else {
                        "APK"
                    }
                    binding.tvOtaStatus.text = "Tersedia versi baru: v${result.latestVersion} ($sizeMb)"
                    binding.tvOtaStatus.setTextColor(ContextCompat.getColor(this, R.color.accent_green))
                    showUpdateDialog(result, sizeMb)
                }
                is UpdateCheckResult.UpToDate -> {
                    binding.tvOtaStatus.text = "Aplikasi sudah versi terbaru (v${result.currentVersion})"
                    binding.tvOtaStatus.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
                    if (isManual) {
                        Toast.makeText(this, "Sudah menggunakan versi terbaru!", Toast.LENGTH_SHORT).show()
                    }
                }
                is UpdateCheckResult.Error -> {
                    if (isManual) {
                        binding.tvOtaStatus.text = "Gagal cek update: ${result.message}"
                        binding.tvOtaStatus.setTextColor(ContextCompat.getColor(this, R.color.accent_red))
                        Toast.makeText(this, result.message, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun showUpdateDialog(update: UpdateCheckResult.UpdateAvailable, sizeLabel: String) {
        AlertDialog.Builder(this)
            .setTitle("Update Tersedia: v${update.latestVersion}")
            .setMessage("Ukuran: $sizeLabel\n\nCatatan Rilis:\n${update.changelog}")
            .setPositiveButton("Update Sekarang") { _, _ ->
                startOtaDownload(update)
            }
            .setNegativeButton("Nanti", null)
            .show()
    }

    private fun startOtaDownload(update: UpdateCheckResult.UpdateAvailable) {
        val progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = false
            max = 100
            progress = 0
        }
        val statusText = TextView(this).apply {
            text = "Mengunduh ${update.fileName}... 0%"
            setPadding(0, 0, 0, 24)
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 40, 60, 20)
            addView(statusText)
            addView(progressBar)
        }

        val downloadDialog = AlertDialog.Builder(this)
            .setTitle("Mengunduh Pembaruan")
            .setView(container)
            .setCancelable(false)
            .create()
        downloadDialog.show()

        otaManager.downloadAndInstall(
            downloadUrl = update.downloadUrl,
            fileName = update.fileName,
            onProgress = { progress ->
                progressBar.progress = progress
                statusText.text = "Mengunduh ${update.fileName}... $progress%"
            },
            onReadyToInstall = { apkFile ->
                downloadDialog.dismiss()
                if (otaManager.canInstallPackages()) {
                    otaManager.launchApkInstaller(apkFile)
                } else {
                    pendingApkFile = apkFile
                    Toast.makeText(
                        this,
                        "Izinkan instalasi aplikasi dari sumber ini untuk melanjutkan update",
                        Toast.LENGTH_LONG
                    ).show()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:$packageName")
                        )
                        startActivity(intent)
                    }
                }
            },
            onError = { errorMsg ->
                downloadDialog.dismiss()
                Toast.makeText(this, "Gagal download update: $errorMsg", Toast.LENGTH_LONG).show()
            }
        )
    }
}
