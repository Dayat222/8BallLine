package com.dayat.eightballline.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.FileProvider
import com.dayat.eightballline.BuildConfig
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

sealed class UpdateCheckResult {
    data class UpdateAvailable(
        val latestVersion: String,
        val changelog: String,
        val downloadUrl: String,
        val fileName: String,
        val fileSizeBytes: Long
    ) : UpdateCheckResult()

    data class UpToDate(val currentVersion: String) : UpdateCheckResult()
    data class Error(val message: String) : UpdateCheckResult()
}

class OtaUpdateManager(private val context: Context) {

    companion object {
        private const val GITHUB_LATEST_RELEASE_API =
            "https://api.github.com/repos/Dayat222/8BallLine/releases/latest"
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val READ_TIMEOUT_MS = 15_000
    }

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    fun checkForUpdate(onResult: (UpdateCheckResult) -> Unit) {
        executor.execute {
            try {
                val url = URL(GITHUB_LATEST_RELEASE_API)
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = CONNECT_TIMEOUT_MS
                    readTimeout = READ_TIMEOUT_MS
                    setRequestProperty("Accept", "application/vnd.github+json")
                    setRequestProperty("User-Agent", "GridRuler-OTA/${BuildConfig.VERSION_NAME}")
                }

                val responseCode = conn.responseCode
                if (responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
                    postResult(onResult, UpdateCheckResult.UpToDate(BuildConfig.VERSION_NAME))
                    return@execute
                }
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    postResult(
                        onResult,
                        UpdateCheckResult.Error("Gagal cek update (HTTP $responseCode)")
                    )
                    return@execute
                }

                val body = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()

                val json = JSONObject(body)
                val tagName = json.optString("tag_name", "").trim()
                val remoteVersion = tagName.removePrefix("v").removePrefix("V")
                val changelog = json.optString("body", "Pembaruan tersedia.")
                    .ifBlank { "Pembaruan tersedia." }

                if (remoteVersion.isBlank()) {
                    postResult(onResult, UpdateCheckResult.Error("Format versi rilis tidak valid"))
                    return@execute
                }

                if (!isNewerVersion(remoteVersion, BuildConfig.VERSION_NAME)) {
                    postResult(onResult, UpdateCheckResult.UpToDate(BuildConfig.VERSION_NAME))
                    return@execute
                }

                val assets = json.optJSONArray("assets")
                var apkUrl: String? = null
                var apkName = "8BallLine-$remoteVersion.apk"
                var apkSize = 0L

                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        val name = asset.optString("name", "")
                        if (name.endsWith(".apk", ignoreCase = true)) {
                            apkUrl = asset.optString("browser_download_url", "")
                            apkName = name
                            apkSize = asset.optLong("size", 0L)
                            break
                        }
                    }
                }

                if (apkUrl.isNullOrBlank()) {
                    postResult(
                        onResult,
                        UpdateCheckResult.Error("File APK tidak ditemukan pada rilis v$remoteVersion")
                    )
                    return@execute
                }

                postResult(
                    onResult,
                    UpdateCheckResult.UpdateAvailable(
                        latestVersion = remoteVersion,
                        changelog = changelog,
                        downloadUrl = apkUrl,
                        fileName = apkName,
                        fileSizeBytes = apkSize
                    )
                )
            } catch (e: Exception) {
                postResult(
                    onResult,
                    UpdateCheckResult.Error(e.localizedMessage ?: "Gagal terhubung ke server update")
                )
            }
        }
    }

    fun downloadAndInstall(
        downloadUrl: String,
        fileName: String,
        onProgress: (Int) -> Unit,
        onReadyToInstall: (File) -> Unit,
        onError: (String) -> Unit
    ) {
        executor.execute {
            try {
                val updatesDir = File(context.cacheDir, "updates").apply {
                    if (!exists()) mkdirs()
                }
                // Hapus file APK lama agar cache tetap bersih
                updatesDir.listFiles()?.forEach { it.delete() }

                val outFile = File(updatesDir, fileName)
                val conn = openConnectionFollowingRedirects(downloadUrl)
                val totalBytes = conn.contentLengthLong

                BufferedInputStream(conn.inputStream).use { input ->
                    FileOutputStream(outFile).use { output ->
                        val buffer = ByteArray(8 * 1024)
                        var downloaded = 0L
                        var lastReportedProgress = -1

                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            downloaded += read

                            if (totalBytes > 0) {
                                val progress = ((downloaded * 100L) / totalBytes).toInt().coerceIn(0, 100)
                                if (progress != lastReportedProgress) {
                                    lastReportedProgress = progress
                                    mainHandler.post { onProgress(progress) }
                                }
                            }
                        }
                        output.flush()
                    }
                }
                conn.disconnect()

                mainHandler.post { onReadyToInstall(outFile) }
            } catch (e: Exception) {
                mainHandler.post {
                    onError(e.localizedMessage ?: "Gagal mengunduh file update")
                }
            }
        }
    }

    fun launchApkInstaller(apkFile: File) {
        val apkUri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun canInstallPackages(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    private fun openConnectionFollowingRedirects(urlString: String): HttpURLConnection {
        var currentUrl = urlString
        repeat(5) {
            val conn = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = 30_000
                setRequestProperty("User-Agent", "GridRuler-OTA/${BuildConfig.VERSION_NAME}")
            }
            val code = conn.responseCode
            if (code in 300..399) {
                val location = conn.getHeaderField("Location")
                conn.disconnect()
                if (location.isNullOrBlank()) {
                    throw IllegalStateException("Redirect tanpa header Location")
                }
                currentUrl = location
            } else if (code == HttpURLConnection.HTTP_OK) {
                return conn
            } else {
                conn.disconnect()
                throw IllegalStateException("Download gagal (HTTP $code)")
            }
        }
        throw IllegalStateException("Terlalu banyak redirect saat mengunduh APK")
    }

    private fun isNewerVersion(remote: String, local: String): Boolean {
        val remoteParts = remote.split(".").map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
        val localParts = local.split(".").map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
        val maxLen = maxOf(remoteParts.size, localParts.size)
        for (i in 0 until maxLen) {
            val r = remoteParts.getOrElse(i) { 0 }
            val l = localParts.getOrElse(i) { 0 }
            if (r > l) return true
            if (r < l) return false
        }
        return false
    }

    private fun postResult(onResult: (UpdateCheckResult) -> Unit, result: UpdateCheckResult) {
        mainHandler.post { onResult(result) }
    }
}
