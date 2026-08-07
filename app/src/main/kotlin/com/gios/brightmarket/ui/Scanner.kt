package com.gios.brightmarket.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.util.concurrent.Executors

/**
 * QR decoding, carried over from LightQR.
 *
 * ZXing on the luminance plane, not ML Kit: LightOS ships without Play Services,
 * so ML Kit's barcode scanner isn't an option. Pure Java, works offline.
 */
private class QrAnalyzer(private val onResult: (String) -> Unit) : ImageAnalysis.Analyzer {

    private val reader = MultiFormatReader().apply {
        setHints(mapOf(DecodeHintType.TRY_HARDER to true))
    }

    override fun analyze(image: ImageProxy) {
        try {
            val buffer = image.planes[0].buffer
            val bytes = ByteArray(buffer.remaining()).also { buffer.get(it) }
            val source = PlanarYUVLuminanceSource(
                bytes, image.width, image.height,
                0, 0, image.width, image.height, false,
            )
            reader.decodeWithState(BinaryBitmap(HybridBinarizer(source)))
                ?.text?.let(onResult)
        } catch (_: Exception) {
            // No code in this frame. Normal -- most frames have none.
        } finally {
            reader.reset()
            image.close()
        }
    }
}

/**
 * Scan screen.
 *
 * Accepts two things: a `brightmarket://` link from the desktop catalogue, and
 * a plain GitHub repo URL. The second is what makes this useful beyond the
 * store — point it at any repo that publishes APK releases and it gets tracked
 * for updates like anything else.
 */
@Composable
fun ScanScreen(onScanned: (String) -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val ask = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted = it }

    // Asked on arrival rather than behind a button: the screen has exactly one
    // purpose and is useless without it, so a preamble would be a step for its
    // own sake.
    LaunchedEffect(Unit) { if (!granted) ask.launch(Manifest.permission.CAMERA) }

    Column(Modifier.fillMaxSize()) {
        if (!granted) {
            Box(
                Modifier.fillMaxSize().padding(gridUnits(Grid.INSET)),
                contentAlignment = Alignment.Center,
            ) {
                Column {
                    Text(
                        "Scanning needs the camera.",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Spacer(Modifier.height(gridUnits(0.5f)))
                    Text(
                        "GRANT",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.lightClickable {
                            ask.launch(Manifest.permission.CAMERA)
                        },
                    )
                }
            }
            return@Column
        }

        // weight, not fillMaxSize: fillMaxSize asks for the whole screen height
        // even though the bar above has already taken some of it, so the preview
        // ran off the bottom and pushed everything with it.
        Box(Modifier.fillMaxWidth().weight(1f)) {
            CameraPreview(onScanned)
        }

        // A second way out, below the preview, because the first one is a small
        // arrow at the top of a screen filled by a camera. Belt and braces on
        // purpose: being unable to leave a viewfinder is the worst failure this
        // screen has, and it is not worth being clever about.
        Text(
            "CANCEL",
            style = MaterialTheme.typography.labelLarge,
            color = Light.Content,
            modifier = Modifier
                .lightClickable(onClick = onCancel)
                .padding(horizontal = gridUnits(Grid.INSET), vertical = gridUnits(0.8f)),
        )
    }
}

@Composable
private fun CameraPreview(onScanned: (String) -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    // One scan per visit to this screen. Without the latch a held-still code
    // fires on every analysed frame, which would stack dozens of navigations.
    var handled by remember { mutableStateOf(false) }
    DisposableEffect(Unit) { onDispose { executor.shutdown() } }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                // COMPATIBLE is a TextureView. The default, PERFORMANCE, is a
                // SurfaceView composited in its own layer, which on some devices
                // draws over everything around it -- including the top bar with
                // the only way off this screen. Slightly more expensive and
                // correct, which is the right trade for a viewfinder that is on
                // screen for a few seconds.
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            }
            val future = ProcessCameraProvider.getInstance(ctx)
            future.addListener({
                val provider = future.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis.setAnalyzer(executor, QrAnalyzer { text ->
                    if (!handled) {
                        handled = true
                        // Analysis runs on its own executor; hop to the main
                        // thread before touching Compose state.
                        previewView.post { onScanned(text) }
                    }
                })
                try {
                    provider.unbindAll()
                    provider.bindToLifecycle(
                        lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis,
                    )
                } catch (_: Exception) {
                    // Nothing sensible to do if the camera won't bind; the
                    // screen simply shows no preview rather than crashing.
                }
            }, ContextCompat.getMainExecutor(ctx))
            previewView
        },
    )
}
