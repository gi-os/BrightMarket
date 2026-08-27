package com.gios.brightmarket.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Collections

/**
 * The app's mark, in a list row or at the top of its page.
 *
 * Icons are 192px PNGs served from brightmarket.gzl.dev/icons, one per package,
 * built by the index repo's `extract_icons.py` from either the app's own repo or
 * its APK. The index carries the URL; an app with no icon simply has no `icon`
 * key, and that is not an error -- eighteen of the fifty-nine declare no icon
 * anywhere, because LightOS never asked an SDK tool for one. Those get their
 * first letter instead, which is a deliberate look rather than a gap in the row.
 *
 * No image library, for the same reason [ScreenshotStrip] doesn't use one: Coil
 * would add more to the APK than the whole rest of the app.
 *
 * Unlike the screenshot strip, this one caches to disk. A screenshot is looked
 * at once; icons are every row of a fifty-nine app list, on a phone that is
 * often on no network at all, and re-downloading them on each cold start is
 * both the slow way and the wrong way to spend someone's data.
 */
private object IconCache {

    /**
     * Decoded icons, kept for the process lifetime. 192px ARGB is ~147KB each,
     * so this is a real budget rather than a formality -- 32 covers three
     * screens of scrolling in both directions.
     */
    private const val MAX_ENTRIES = 32

    /** Re-fetched after this long, so a redrawn mark eventually arrives. */
    private const val MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000

    private val cache: MutableMap<String, Bitmap> = Collections.synchronizedMap(
        object : LinkedHashMap<String, Bitmap>(0, 0.75f, true) {
            override fun removeEldestEntry(eldest: Map.Entry<String, Bitmap>) = size > MAX_ENTRIES
        }
    )

    /** URLs that failed, so a broken link isn't retried on every scroll. */
    private val failed = Collections.synchronizedSet(mutableSetOf<String>())

    fun cached(url: String): Bitmap? = cache[url]

    private fun fileFor(context: Context, url: String): File {
        val dir = File(context.cacheDir, "icons").apply { mkdirs() }
        // Hashed, because a package name is a legal filename right up until
        // someone submits one that isn't.
        val digest = MessageDigest.getInstance("SHA-1").digest(url.toByteArray())
        return File(dir, digest.joinToString("") { "%02x".format(it) } + ".png")
    }

    suspend fun load(context: Context, url: String): Bitmap? {
        cache[url]?.let { return it }
        if (url in failed) return null

        return withContext(Dispatchers.IO) {
            val file = fileFor(context, url)
            val fresh = file.exists() && System.currentTimeMillis() - file.lastModified() < MAX_AGE_MS

            // The cached copy is used even when it is stale, if the fetch that
            // was meant to replace it fails. An old icon beats no icon, and on
            // this phone "no network" is the normal case rather than the
            // exception.
            val disk = if (file.exists()) runCatching {
                BitmapFactory.decodeFile(file.absolutePath)
            }.getOrNull() else null

            if (fresh && disk != null) {
                cache[url] = disk
                return@withContext disk
            }

            val downloaded = runCatching {
                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 10_000
                    readTimeout = 20_000
                    instanceFollowRedirects = true
                }
                try {
                    if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode}")
                    conn.inputStream.readBytes()
                } finally {
                    conn.disconnect()
                }
            }.getOrNull()

            val bitmap = downloaded?.let {
                BitmapFactory.decodeByteArray(it, 0, it.size)
            }

            if (bitmap != null && downloaded != null) {
                // Written through a temporary file: a half-written PNG in the
                // cache would be decoded as a failure on every launch after,
                // and nothing would ever repair it.
                runCatching {
                    val tmp = File(file.parentFile, file.name + ".part")
                    tmp.writeBytes(downloaded)
                    if (!tmp.renameTo(file)) tmp.delete()
                }
                cache[url] = bitmap
                return@withContext bitmap
            }

            if (disk != null) {
                cache[url] = disk
                return@withContext disk
            }
            failed.add(url)
            null
        }
    }
}

/**
 * @param url the index's icon URL, or blank for an app that has none.
 * @param name the app's name, for the lettered fallback.
 * @param size the box the icon fills. Grid units, never fixed dp.
 */
@Composable
fun AppIcon(url: String, name: String, size: Dp) {
    val context = LocalContext.current
    // Seeded from the cache so a row scrolled back into view doesn't flash
    // empty and then pop.
    var bitmap by remember(url) { mutableStateOf(if (url.isBlank()) null else IconCache.cached(url)) }

    LaunchedEffect(url) {
        if (url.isNotBlank() && bitmap == null) bitmap = IconCache.load(context, url)
    }

    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        val bmp = bitmap
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.size(size),
                contentScale = ContentScale.Fit,
            )
        } else {
            // The first letter, and nothing else. LightOS draws no boxes, no
            // tinted tiles and no placeholder art anywhere, so an outline here
            // would be the only one in the whole system.
            //
            // Also the loading state: an icon arrives in a few hundred
            // milliseconds and swapping a letter for a mark reads as the image
            // loading, whereas a blank square reads as a broken row.
            Text(
                text = name.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "·",
                style = MaterialTheme.typography.titleMedium,
                color = Light.ContentSecondary,
            )
        }
    }
}
