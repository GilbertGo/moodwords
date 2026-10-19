package com.example.moodwords

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * GitHub Releases에서 새 버전을 확인하고, 워치에서 바로 내려받아 설치.
 * 안드로이드 정책상 마지막에 사용자가 "설치"를 한 번 눌러야 함.
 */
object Updater {
    private const val LATEST = "https://api.github.com/repos/GilbertGo/moodwords/releases/latest"

    data class Release(val version: Int, val apkUrl: String)

    /** 지금 설치된 것보다 새 버전이 있으면 반환, 없거나 오프라인이면 null */
    suspend fun checkNewer(): Release? = withContext(Dispatchers.IO) {
        runCatching {
            val conn = (URL(LATEST).openConnection() as HttpURLConnection).apply {
                setRequestProperty("Accept", "application/vnd.github+json")
                connectTimeout = 10_000
                readTimeout = 10_000
            }
            val json = conn.inputStream.bufferedReader().use { JSONObject(it.readText()) }
            val version = json.getString("tag_name").removePrefix("build-").toInt()
            val assets = json.getJSONArray("assets")
            val apk = (0 until assets.length())
                .map { assets.getJSONObject(it) }
                .first { it.getString("name").endsWith(".apk") }
                .getString("browser_download_url")
            Release(version, apk)
        }.getOrNull()?.takeIf { it.version > BuildConfig.VERSION_CODE }
    }

    /** APK를 받아 시스템 설치 화면을 띄움 */
    suspend fun install(context: Context, release: Release, onProgress: (Float) -> Unit) =
        withContext(Dispatchers.IO) {
            val conn = (URL(release.apkUrl).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                connectTimeout = 15_000
                readTimeout = 30_000
            }
            val total = conn.contentLengthLong
            val installer = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            val sessionId = installer.createSession(params)
            installer.openSession(sessionId).use { session ->
                session.openWrite("update.apk", 0, total).use { out ->
                    conn.inputStream.use { input ->
                        val buf = ByteArray(64 * 1024)
                        var done = 0L
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            done += n
                            if (total > 0) onProgress(done / total.toFloat())
                        }
                    }
                    session.fsync(out)
                }
                val intent = Intent(context, InstallResultReceiver::class.java)
                val pending = PendingIntent.getBroadcast(
                    context, sessionId, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                )
                session.commit(pending.intentSender)
            }
        }
}

/** 설치 진행 결과를 받아, 사용자 확인이 필요하면 확인 화면을 띄움 */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(confirm)
            }
            PackageInstaller.STATUS_SUCCESS -> Unit
            else -> {
                val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "알 수 없는 오류"
                Toast.makeText(context, "업데이트 실패: $msg", Toast.LENGTH_LONG).show()
            }
        }
    }
}
