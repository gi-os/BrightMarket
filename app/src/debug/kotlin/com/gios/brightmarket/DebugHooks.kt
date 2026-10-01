package com.gios.brightmarket

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.gios.brightmarket.data.InstalledVersions
import com.gios.brightmarket.update.AutoUpdate
import com.gios.brightmarket.update.AutoUpdateWorker

/**
 * Test hooks for the emulator run. Debug builds only (src/debug).
 *
 *   CONFIGURE --es index_url <url> --ez auto <bool>
 *   RUN       runs the real AutoUpdateWorker once, now, without its constraints
 *   STATE     logs what the app believes
 */
class DebugHooks : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        when (intent.action) {
            "com.gios.brightmarket.debug.CONFIGURE" -> {
                intent.getStringExtra("index_url")?.let { AutoUpdate.setIndexOverride(ctx, it) }
                if (intent.hasExtra("auto")) AutoUpdate.setEnabled(ctx, intent.getBooleanExtra("auto", false))
                Log.i(AutoUpdate.TAG, "configured auto=${AutoUpdate.enabled(ctx)} index=${AutoUpdate.indexUrl(ctx)}")
            }
            "com.gios.brightmarket.debug.RUN" -> {
                WorkManager.getInstance(ctx).enqueueUniqueWork(
                    "auto-update-now",
                    ExistingWorkPolicy.REPLACE,
                    OneTimeWorkRequestBuilder<AutoUpdateWorker>().build(),
                )
                Log.i(AutoUpdate.TAG, "run enqueued")
            }
            "com.gios.brightmarket.debug.STATE" -> {
                Log.i(
                    AutoUpdate.TAG,
                    "state auto=${AutoUpdate.enabled(ctx)} " +
                        "recorded=${InstalledVersions.get(ctx, ctx.packageName)} " +
                        "summary=${AutoUpdate.summary(ctx)?.line()}",
                )
            }
        }
    }
}
