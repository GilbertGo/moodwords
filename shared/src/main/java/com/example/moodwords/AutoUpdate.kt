package com.example.moodwords

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * 자동 업데이트: 앱을 열지 않아도 6시간마다 GitHub에서 새 버전을 확인하고 설치.
 * 앱이 스스로 설치한 적이 있으면(두 번째 업데이트부터) 확인 없이 조용히 설치됨.
 */
object AutoUpdate {
    private const val WORK = "moodwords-auto-update"

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<UpdateWorker>(6, TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresBatteryNotLow(true)
                    .build()
            )
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, request)
    }
}

class UpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val release = Updater.checkNewer(Updater.channelOf(ctx), Updater.installedVersion(ctx))
            ?: return Result.success()
        return runCatching { Updater.install(ctx, release) {} }
            .fold({ Result.success() }, { Result.retry() })
    }
}
