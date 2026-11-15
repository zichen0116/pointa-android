package io.github.zichen0116.pointa

import android.media.AudioManager
import android.media.ToneGenerator
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.camera.view.transform.CoordinateTransform
import androidx.camera.view.transform.ImageProxyTransformFactory
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.core.content.ContextCompat
import androidx.core.view.doOnLayout
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.zxing.*
import io.github.zichen0116.pointa.protocol.QrInvitation
import kotlinx.coroutines.delay
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@Composable
@androidx.annotation.OptIn(androidx.camera.view.TransformExperimental::class)
fun CameraScanner(hint: String, recognizedHint: String, cancel: String, flashLabel: String, reduceMotion: Boolean,
    onResult: (String) -> Unit, onClose: () -> Unit, onError: () -> Unit, onInvalid: () -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    val result by rememberUpdatedState(onResult)
    val error by rememberUpdatedState(onError)
    val invalid by rememberUpdatedState(onInvalid)
    val previewView = remember { PreviewView(context).apply { implementationMode = PreviewView.ImplementationMode.COMPATIBLE } }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var flash by remember { mutableStateOf(false) }
    var detectedText by remember { mutableStateOf<String?>(null) }
    var corners by remember { mutableStateOf(emptyList<Offset>()) }
    val tone = remember { runCatching { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 60) }.getOrNull() }
    val haptics = remember { PageHaptics(context) }
    DisposableEffect(Unit) { onDispose { tone?.release() } }
    LaunchedEffect(detectedText) { detectedText?.let { delay(550); result(it) } }
    DisposableEffect(lifecycle) {
        val stopped = AtomicBoolean(false)
        val detected = AtomicBoolean(false)
        val executor = Executors.newSingleThreadExecutor()
        val main = ContextCompat.getMainExecutor(context)
        val future = ProcessCameraProvider.getInstance(context)
        val preview = Preview.Builder().build().apply { surfaceProvider = previewView.surfaceProvider }
        val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
        val reader = MultiFormatReader().apply { setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE))) }
        val transformFactory = ImageProxyTransformFactory()
        var lastInvalid: String? = null
        fun failOnce() {
            if (!stopped.get() && detected.compareAndSet(false, true)) main.execute { if (!stopped.get()) error() }
        }
        analysis.setAnalyzer(executor) { image ->
            try {
                if (!stopped.get() && !detected.get()) {
                    val plane = image.planes[0]
                    val crop = image.cropRect
                    val decoded = decodeQrFrame(plane.buffer.duplicate(), image.width, image.height, plane.rowStride, plane.pixelStride,
                        crop.left, crop.top, crop.width(), crop.height(), reader)
                    if (decoded != null) {
                        if (runCatching { QrInvitation.parse(decoded.text) }.isFailure) {
                            if (lastInvalid != decoded.text) { lastInvalid = decoded.text; main.execute { if (!stopped.get()) invalid() } }
                        } else if (detected.compareAndSet(false, true)) {
                            val source = transformFactory.getOutputTransform(image)
                            val points = decoded.corners.flatMap { listOf(it.x, it.y) }.toFloatArray()
                            main.execute {
                                if (!stopped.get()) {
                                    previewView.outputTransform?.let { target ->
                                        CoordinateTransform(source, target).mapPoints(points)
                                        corners = points.toList().chunked(2).map { Offset(it[0], it[1]) }
                                    }
                                    detectedText = decoded.text
                                    haptics.click()
                                    if (context.getSystemService(AudioManager::class.java).ringerMode == AudioManager.RINGER_MODE_NORMAL)
                                        tone?.startTone(ToneGenerator.TONE_PROP_ACK, 120)
                                }
                            }
                        }
                    }
                }
            } catch (_: RuntimeException) { failOnce() }
            finally { image.close() }
        }
        previewView.doOnLayout {
            future.addListener({
                if (!stopped.get()) try {
                    val group = UseCaseGroup.Builder().addUseCase(preview).addUseCase(analysis)
                    previewView.viewPort?.let(group::setViewPort)
                    camera = future.get().bindToLifecycle(lifecycle, CameraSelector.DEFAULT_BACK_CAMERA, group.build())
                } catch (_: Exception) { failOnce() }
            }, main)
        }
        onDispose {
            stopped.set(true)
            camera?.cameraControl?.enableTorch(false)
            analysis.clearAnalyzer()
            if (future.isDone) try { future.get().unbind(preview, analysis) } catch (_: Exception) { }
            executor.shutdown()
        }
    }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false,
        decorFitsSystemWindows = false, securePolicy = SecureFlagPolicy.SecureOn)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
            ScannerOverlay(if (detectedText == null) hint else recognizedHint, cancel, flashLabel,
                camera?.cameraInfo?.hasFlashUnit() == true, flash, corners, detectedText != null, reduceMotion, onClose) {
                flash = !flash; camera?.cameraControl?.enableTorch(flash)
            }
        }
    }
}

@Composable
internal fun ScannerOverlay(hint: String, cancel: String, flashLabel: String, hasFlash: Boolean, flash: Boolean,
    corners: List<Offset>, recognized: Boolean, reduceMotion: Boolean, onClose: () -> Unit, onFlash: () -> Unit) {
    val progress = if (reduceMotion || recognized) 0f else {
        val transition = rememberInfiniteTransition(label = "scan")
        val position by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(2200, easing = LinearEasing)), label = "scan-line")
        position
    }
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val green = Color(0xFF4ADFA5)
            if (corners.size == 4) {
                val path = Path().apply { moveTo(corners[0].x, corners[0].y); corners.drop(1).forEach { lineTo(it.x, it.y) }; close() }
                drawPath(path, green.copy(alpha = .12f))
                drawPath(path, green, style = Stroke(3.dp.toPx()))
            } else if (!recognized && !reduceMotion) {
                val top = size.height * .20f; val bottom = size.height * .76f
                val y = top + (bottom - top) * progress
                val left = size.width * .10f; val width = size.width * .80f
                drawRect(Brush.verticalGradient(listOf(Color.Transparent, green.copy(alpha = .2f)), y - 48.dp.toPx(), y),
                    Offset(left, y - 48.dp.toPx()), Size(width, 48.dp.toPx()))
                drawLine(Brush.horizontalGradient(listOf(Color.Transparent, green, Color.Transparent), left, left + width),
                    Offset(left, y), Offset(left + width, y), 2.dp.toPx())
            }
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .4f), Color.Transparent, Color.Black.copy(alpha = .6f)))))
        Row(Modifier.fillMaxWidth().safeDrawingPadding().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            IconButton(onClick = onClose, modifier = Modifier.size(48.dp).background(Color.Black.copy(alpha = .3f), CircleShape)) {
                Icon(Icons.Default.Close, cancel, tint = Color.White)
            }
            if (hasFlash) IconButton(onClick = onFlash, modifier = Modifier.size(48.dp).background(Color.Black.copy(alpha = .3f), CircleShape)) {
                Icon(if (flash) Icons.Default.FlashlightOn else Icons.Default.FlashlightOff, flashLabel, tint = Color.White)
            }
        }
        Text(hint, Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(bottom = 112.dp),
            style = MaterialTheme.typography.bodyMedium, color = Color.White)
    }
}
