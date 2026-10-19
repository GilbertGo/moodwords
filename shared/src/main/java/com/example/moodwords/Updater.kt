package com.example.moodwords

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

/**
 * GitHub Releases에서 새 버전을 확인하고, 기기에서 바로 내려받아 설치.
 * 안드로이드 정책상 마지막에 사용자가 "설치"를 한 번 눌러야 함.
 *
 * 릴리스 규칙: 워치는 태그 build-N / MoodWords.apk,
 *            폰은 태그 phone-N / MoodWords-phone.apk (pre-release)
 */
object Updater {
    private const val RELEASES = "https://api.github.com/repos/GilbertGo/moodwords/releases?per_page=20"

    data class Channel(val tagPrefix: String, val assetName: String)

    val WATCH = Channel("build-", "MoodWords.apk")
    val PHONE = Channel("phone-", "MoodWords-phone.apk")

    /** 패키지 이름으로 채널 결정 (폰 앱은 .phone으로 끝남) */
    fun channelOf(context: Context): Channel =
        if (context.packageName.endsWith(".phone")) PHONE else WATCH

    @Suppress("DEPRECATION")
    fun installedVersion(context: Context): Int {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        return if (Build.VERSION.SDK_INT >= 28) info.longVersionCode.toInt() else info.versionCode
    }

    data class Release(val version: Int, val apkUrl: String)

    /** 지금 설치된 것보다 새 버전이 있으면 반환, 없거나 오프라인이면 null */
    suspend fun checkNewer(channel: Channel, currentVersion: Int): Release? = withContext(Dispatchers.IO) {
        runCatching {
            val conn = (URL(RELEASES).openConnection() as HttpURLConnection).apply {
                setRequestProperty("Accept", "application/vnd.github+json")
                connectTimeout = 10_000
                readTimeout = 10_000
            }
            val arr = conn.inputStream.bufferedReader().use { JSONArray(it.readText()) }
            (0 until arr.length()).asSequence()
                .map { arr.getJSONObject(it) }
                .filter { it.getString("tag_name").startsWith(channel.tagPrefix) }
                .mapNotNull { rel ->
                    val version = rel.getString("tag_name").removePrefix(channel.tagPrefix).toIntOrNull()
                        ?: return@mapNotNull null
                    val assets = rel.getJSONArray("assets")
                    val apk = (0 until assets.length())
                        .map { assets.getJSONObject(it) }
                        .firstOrNull { it.getString("name") == channel.assetName }
                        ?.getString("browser_download_url") ?: return@mapNotNull null
                    Release(version, apk)
                }
                .maxByOrNull { it.version }
        }.getOrNull()?.takeIf { it.version > currentVersion }
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
            // Android 12+: 이 앱이 직접 설치한 이후부터는 확인 없이 조용히 업데이트
            // (첫 번째 자체 업데이트만 '설치' 확인이 한 번 필요)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
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
                // 백그라운드에서는 화면을 띄울 수 없을 수 있음 → 다음에 앱을 열 때 다시 시도
                runCatching { context.startActivity(confirm) }
            }
            PackageInstaller.STATUS_SUCCESS -> Unit
            else -> {
                val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "알 수 없는 오류"
                Toast.makeText(context, "업데이트 실패: $msg", Toast.LENGTH_LONG).show()
            }
        }
    }
}
