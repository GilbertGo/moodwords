package com.example.moodwords

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkManager
import androidx.work.WorkerParameters

/**
 * 백그라운드 자동 설치는 꺼둠 (사용자 요청): 업데이트는 앱을 열 때 안내 창에서만.
 * 예전 버전이 예약해 둔 6시간 주기 작업이 남아 있을 수 있어서, 앱을 열 때 취소함.
 */
object AutoUpdate {
    private const val WORK = "moodwords-auto-update"

    fun cancelBackground(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK)
    }
}

/** 예전 예약 작업이 취소 전에 한 번 더 실행돼도 아무것도 하지 않음 */
class UpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = Result.success()
}
