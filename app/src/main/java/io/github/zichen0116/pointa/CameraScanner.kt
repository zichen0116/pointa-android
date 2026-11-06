package io.github.zichen0116.pointa

import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.zxing.*
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@Composable
fun CameraScanner(title: String, hint: String, cancel: String, onResult: (String) -> Unit, onClose: () -> Unit, onError: () -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    val result by rememberUpdatedState(onResult)
    val error by rememberUpdatedState(onError)
    val previewView = remember { PreviewView(context).apply { implementationMode = PreviewView.ImplementationMode.COMPATIBLE } }
    DisposableEffect(lifecycle) {
        val stopped = AtomicBoolean(false)
        val detected = AtomicBoolean(false)
        val executor = Executors.newSingleThreadExecutor()
        val main = ContextCompat.getMainExecutor(context)
        val future = ProcessCameraProvider.getInstance(context)
        val preview = Preview.Builder().build().apply { surfaceProvider = previewView.surfaceProvider }
        val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
        val reader = MultiFormatReader().apply { setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE))) }
        fun failOnce() {
            if (!stopped.get() && detected.compareAndSet(false, true)) main.execute { if (!stopped.get()) error() }
        }
        analysis.setAnalyzer(executor) { image ->
            try {
                if (!stopped.get() && !detected.get()) {
                    val plane = image.planes[0]
                    val buffer = plane.buffer.duplicate()
                    val crop = image.cropRect
                    val decoded = decodeQrFrame(buffer, image.width, image.height, plane.rowStride, plane.pixelStride,
                        crop.left, crop.top, crop.width(), crop.height(), reader)
                    if (decoded != null && detected.compareAndSet(false, true)) main.execute { if (!stopped.get()) result(decoded) }
                }
            } catch (_: RuntimeException) { failOnce() }
            finally { image.close() }
        }
        future.addListener({
            if (!stopped.get()) try {
                future.get().bindToLifecycle(lifecycle, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            } catch (_: Exception) { failOnce() }
        }, main)
        onDispose {
            stopped.set(true)
            analysis.clearAnalyzer()
            if (future.isDone) try { future.get().unbind(preview, analysis) } catch (_: Exception) { }
            executor.shutdown()
        }
    }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, securePolicy = SecureFlagPolicy.SecureOn)) {
        Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF111722), contentColor = Color.White) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(title, style = MaterialTheme.typography.headlineSmall)
                Text(hint, style = MaterialTheme.typography.bodyMedium)
                AndroidView(factory = { previewView }, modifier = Modifier.fillMaxWidth().weight(1f).background(Color.Black))
                Button(onClick = onClose, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(cancel) }
            }
        }
    }
}
