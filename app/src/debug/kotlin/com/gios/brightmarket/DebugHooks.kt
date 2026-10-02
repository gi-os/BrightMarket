package com.gios.brightmarket

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.gios.brightmarket.data.Focus
import com.gios.brightmarket.data.InstalledVersions
import com.gios.brightmarket.data.Pulse
import com.gios.brightmarket.install.Installer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.gios.brightmarket.update.AutoUpdate
import com.gios.brightmarket.update.AutoUpdateWorker

/**
 * Test hooks for the emulator run. Debug builds only (src/debug).
 *
 *   CONFIGURE --es index_url <url> --ez auto <bool>
 *   RUN       runs the real AutoUpdateWorker once, now, without its constraints
 *   STATE     logs what the app believes
 *   INSTALL   --es url <apk> --es pkg <id> --es sha <hex>: an ordinary, attended install
 *
 * CONFIGURE also takes --ez pulse <bool> (keep the test phone out of the real counts)
 * and --ez onboard true (skip the first-run screen, so the UI test lands on the tabs).
 */
class DebugHooks : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        when (intent.action) {
            "com.gios.brightmarket.debug.CONFIGURE" -> {
                intent.getStringExtra("index_url")?.let { AutoUpdate.setIndexOverride(ctx, it) }
                // Before "auto": setting the switch counts as answering the question.
                if (intent.getBooleanExtra("prompt_reset", false)) AutoUpdate.resetAsked(ctx)
                if (intent.hasExtra("auto")) AutoUpdate.setEnabled(ctx, intent.getBooleanExtra("auto", false))
                if (intent.hasExtra("pulse")) Pulse.setEnabled(ctx, intent.getBooleanExtra("pulse", true))
                if (intent.getBooleanExtra("onboard", false)) Focus.choose(ctx, false)
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
            "com.gios.brightmarket.debug.INSTALL" -> {
                val done = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    val r = Installer.install(
                        ctx,
                        apkUrl = intent.getStringExtra("url").orEmpty(),
                        expectedSha256 = intent.getStringExtra("sha"),
                        pkg = intent.getStringExtra("pkg").orEmpty(),
                    )
                    Log.i(AutoUpdate.TAG, "attended install handed over: ${r.isSuccess}")
                    done.finish()
                }
            }
            "com.gios.brightmarket.debug.STATE" -> {
                val done = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    val periodic = runCatching {
                        WorkManager.getInstance(ctx).getWorkInfosForUniqueWork("auto-update").get()
                            .lastOrNull()?.state?.name ?: "NONE"
                    }.getOrElse { "ERROR" }
                    Log.i(
                        AutoUpdate.TAG,
                        "state auto=${AutoUpdate.enabled(ctx)} periodic=$periodic " +
                            "recorded=${InstalledVersions.get(ctx, ctx.packageName)} " +
                            "summary=${AutoUpdate.summary(ctx)?.line()}",
                    )
                    done.finish()
                }
            }
        }
    }
}
