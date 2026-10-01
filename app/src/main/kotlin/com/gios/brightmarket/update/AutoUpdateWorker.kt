package com.gios.brightmarket.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException

/** One automatic update run. Always succeeds: the next period is the retry. */
class AutoUpdateWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        try {
            AutoUpdate.runOnce(applicationContext)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(AutoUpdate.TAG, "run failed: ${e::class.java.simpleName}: ${e.message}")
        }
        return Result.success()
    }
}

/**
 * MY_PACKAGE_REPLACED: BrightMarket was just updated, by itself or by anyone else.
 * Records a self-update the old process could not, and puts the schedule back.
 */
class SelfReplacedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) AutoUpdate.selfReplaced(context)
    }
}
