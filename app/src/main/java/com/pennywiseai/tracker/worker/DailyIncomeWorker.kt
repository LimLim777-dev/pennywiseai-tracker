package com.pennywiseai.tracker.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pennywiseai.tracker.domain.usecase.GenerateIncomeAutopayUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class DailyIncomeWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val generateIncomeAutopayUseCase: GenerateIncomeAutopayUseCase
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            generateIncomeAutopayUseCase.execute()
            Log.d(TAG, "Daily income autopay executed")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Daily income autopay failed: ${e.message}", e)
            Result.retry()
        }
    }

    companion object {
        const val TAG = "DailyIncomeWorker"
        const val WORK_NAME_PREFIX = "daily_income_rule_"
    }
}
