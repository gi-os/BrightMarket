package com.gios.brightmarket.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.Collections

/**
 * Screenshot strip for the detail screen.
 *
 * Images come straight from raw.githubusercontent.com — the marketplace hosts
 * nothing itself. No image library is pulled in for this: Coil/Glide would add
 * more to the APK than the whole rest of the app, for one screen that shows a
 * handful of pictures.
 */
private object ShotCache {

    /**
     * Decoded bitmaps, kept for the process lifetime. Bounded because the LP3
     * has real memory limits and an unbounded map here would grow with every
     * app the user browses. Access is synchronised — the loader runs on IO
     * while Compose reads on main.
     */
    private const val MAX_ENTRIES = 24
    private val cache: MutableMap<String, Bitmap> = Collections.synchronizedMap(
        object : LinkedHashMap<String, Bitmap>(0, 0.75f, true) {
            override fun removeEldestEntry(eldest: Map.Entry<String, Bitmap>) = size > MAX_ENTRIES
        }
    )

    /** Marks URLs that failed, so a broken link isn't retried on every scroll. */
    private val failed = Collections.synchronizedSet(mutableSetOf<String>())

    fun cached(url: String): Bitmap? = cache[url]

    suspend fun load(url: String): Bitmap? {
        cache[url]?.let { return it }
        if (url in failed) return null

        return withContext(Dispatchers.IO) {
            runCatching {
                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 10_000
                    readTimeout = 20_000
                    instanceFollowRedirects = true
                }
                try {
                    if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode}")
                    // The panel is ~480dp wide; a full-resolution PNG is far more
                    // than it can show. inSampleSize halves in powers of two, so
                    // this trades a little sharpness for a lot of heap.
                    val bytes = conn.inputStream.readBytes()
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                    var sample = 1
                    while (bounds.outWidth / sample > 1080) sample *= 2

                    BitmapFactory.decodeByteArray(
                        bytes, 0, bytes.size,
                        BitmapFactory.Options().apply { inSampleSize = sample }
                    )
                } finally {
                    conn.disconnect()
                }
            }.onSuccess { bmp ->
                if (bmp != null) cache[url] = bmp else failed.add(url)
            }.onFailure {
                failed.add(url)
            }.getOrNull()
        }
    }
}

@Composable
fun ScreenshotStrip(urls: List<String>) {
    // Most apps have none. Render nothing rather than an empty gap or a spinner
    // that never resolves.
    if (urls.isEmpty()) return

    Column {
        Text(
            "Screenshots",
            style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
            color = Light.ContentSecondary,
        )
        Spacer(Modifier.height(gridUnits(0.4f)))

        LazyRow(horizontalArrangement = Arrangement.spacedBy(gridUnits(0.6f))) {
            items(urls, key = { it }) { url -> Shot(url) }
        }
    }
}

@Composable
private fun Shot(url: String) {
    // Seed from cache so a screenshot already decoded doesn't flash empty when
    // the row is scrolled back into view.
    var bitmap by remember(url) { mutableStateOf(ShotCache.cached(url)) }
    var failed by remember(url) { mutableStateOf(false) }

    LaunchedEffect(url) {
        if (bitmap == null) {
            val loaded = ShotCache.load(url)
            if (loaded != null) bitmap = loaded else failed = true
        }
    }

    // Fixed frame so the row doesn't reflow as images arrive one by one.
    // 9 grid units wide is roughly a third of the panel: enough to read a
    // screenshot, narrow enough that a second one is visibly there to swipe to.
    Box(
        Modifier
            .width(gridUnits(9f))
            .height(gridUnits(16f)),
        contentAlignment = Alignment.Center,
    ) {
        val bmp = bitmap
        when {
            bmp != null -> Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
            failed -> Text(
                "—",
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                color = Light.ContentSecondary,
            )
            // Loading state is deliberately blank, not a spinner: LightOS has no
            // spinner anywhere, and the frame already holds the space.
        }
    }
}
