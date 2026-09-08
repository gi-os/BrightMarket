package com.gios.brightmarket.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
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
fun ScreenshotStrip(urls: List<String>, onOpen: ((Int) -> Unit)? = null) {
    // Most apps have none. Render nothing rather than an empty gap or a spinner
    // that never resolves.
    if (urls.isEmpty()) return

    Column {
        // The heading and the affordance on one baseline. A 9-unit thumbnail on
        // this panel is about a third of the width, which is enough to tell two
        // screens apart and not enough to read one -- so the strip has to say
        // that there is more to see, in the only place anyone is looking when
        // they wonder.
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                "Screenshots",
                style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                color = Light.ContentSecondary,
            )
            if (onOpen != null) {
                Text(
                    "TAP TO ENLARGE",
                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                    color = Light.ContentSecondary,
                )
            }
        }
        Spacer(Modifier.height(gridUnits(0.4f)))

        LazyRow(horizontalArrangement = Arrangement.spacedBy(gridUnits(0.6f))) {
            items(urls.size, key = { urls[it] }) { i ->
                // Declared, not inferred: a lambda literal handed back out of
                // `let` has no expected type to be inferred against.
                val open: (() -> Unit)? = if (onOpen == null) null else ({ onOpen(i) })
                Shot(urls[i], open)
            }
        }
    }
}

/**
 * One screenshot, filling the screen.
 *
 * Opened from the strip and closed by a tap anywhere -- there is no chrome to
 * put a close button in, and a phone with a hardware back key does not need
 * one. The counter says which of the set is on screen, because at full size
 * there is nothing else left to say where you are.
 *
 * Deliberately not a pager: swiping between images means animating between
 * them, and nothing in LightOS animates. Back to the strip, then in again.
 */
@Composable
fun ScreenshotZoom(urls: List<String>, index: Int, onClose: () -> Unit) {
    val url = urls.getOrNull(index) ?: return
    var bitmap by remember(url) { mutableStateOf(ShotCache.cached(url)) }

    LaunchedEffect(url) {
        if (bitmap == null) bitmap = ShotCache.load(url)
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Light.Background)
            .lightClickable(onClick = onClose),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(gridUnits(Grid.TOP_BAR))
                .padding(horizontal = gridUnits(Grid.INSET)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "${index + 1}/${urls.size}",
                style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
            )
            Spacer(Modifier.weight(1f))
            Text(
                "TAP TO CLOSE",
                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                color = Light.ContentSecondary,
            )
        }

        Box(
            Modifier
                .fillMaxSize()
                .padding(start = gridUnits(Grid.INSET), end = gridUnits(Grid.INSET), bottom = gridUnits(Grid.INSET)),
            contentAlignment = Alignment.Center,
        ) {
            val bmp = bitmap
            if (bmp != null) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            }
            // No spinner and no placeholder while it loads: the strip only
            // offers a tap once the thumbnail is decoded, so by the time this
            // opens the bitmap is nearly always already in the cache.
        }
    }
}

@Composable
private fun Shot(url: String, onOpen: (() -> Unit)? = null) {
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
    val bmp = bitmap
    Box(
        Modifier
            .width(gridUnits(9f))
            .height(gridUnits(16f))
            // Tappable only once there is something to enlarge. A frame that
            // opens a blank full-screen view reads as a broken image rather
            // than as one that hasn't arrived.
            .then(
                if (bmp != null && onOpen != null) Modifier.lightClickable(onClick = onOpen)
                else Modifier
            ),
        contentAlignment = Alignment.Center,
    ) {
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
