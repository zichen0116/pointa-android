package io.github.zichen0116.pointa

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.*
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import io.github.zichen0116.pointa.ui.PointaApp
import io.github.zichen0116.pointa.ui.UiActions

class MainActivity : ComponentActivity() {
    private val model: PointaViewModel by viewModels()
    private val consumedVolumeKeys = mutableSetOf<Int>()
    private val pageHaptics by lazy { PageHaptics(this) }
    private var noticeToast: Toast? = null
    private val cameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) model.setScanning(true) else model.localError("camera_denied")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                model.notices.collect { message ->
                    noticeToast?.cancel()
                    noticeToast = Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).also { it.show() }
                }
            }
        }
        setContent {
            val state by model.ui.collectAsStateWithLifecycle()
            val preferences by model.preferences.collectAsStateWithLifecycle()
            val systemDark = isSystemInDarkTheme()
            SideEffect {
                @Suppress("DEPRECATION")
                model.setDisplayRotation(windowManager.defaultDisplay.rotation)
                val dark = preferences.appearance == "dark" || (preferences.appearance == "system" && systemDark)
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }
            DisposableEffect(preferences.keepScreenOn, state.connected) {
                if (preferences.keepScreenOn && state.connected) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                onDispose { window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
            }
            val actions = remember {
                UiActions(scan = ::scan, discover = model::discover, pairCode = model::pairCode, reconnect = model::reconnect,
                    disconnect = model::disconnect, forget = model::forget, move = model::move, hide = model::hide,
                    page = ::page, onControlActive = model::setControlActive, airHold = model::airHold, calibrateAir = model::calibrateAir)
            }
            PointaApp(state, preferences, model::changePreferences, actions)
            if (state.scanning) CameraScanner(
                hint = model.t("对准二维码自动识别", "Align the QR code to scan"),
                recognizedHint = model.t("已识别，正在连接…", "Recognized. Connecting…"),
                cancel = model.t("关闭扫码", "Close scanner"), flashLabel = model.t("手电筒", "Flashlight"),
                reduceMotion = preferences.reduceMotion, onResult = model::connectQr,
                onInvalid = { model.localError("invalid_qr") },
                onClose = { model.setScanning(false) }, onError = { model.setScanning(false); model.localError("camera_failed") })
        }
    }

    private fun scan() {
        model.hide()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) model.setScanning(true)
        else cameraPermission.launch(Manifest.permission.CAMERA)
    }

    private fun page(next: Boolean) {
        if (model.page(next)) pageHaptics.click()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode != KeyEvent.KEYCODE_VOLUME_UP && keyCode != KeyEvent.KEYCODE_VOLUME_DOWN) return super.onKeyDown(keyCode, event)
        val key = if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) "+" else "-"
        val action = model.volumeAction(key) ?: return super.onKeyDown(keyCode, event)
        consumedVolumeKeys.add(keyCode)
        if (event.repeatCount == 0) page(action)
        return true
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean =
        if (consumedVolumeKeys.remove(keyCode)) true else super.onKeyUp(keyCode, event)

    override fun onStart() { super.onStart(); model.setForeground(true) }
    override fun onPause() { model.hide(); super.onPause() }
    override fun onStop() {
        noticeToast?.cancel()
        consumedVolumeKeys.clear()
        if (!isChangingConfigurations) model.setForeground(false)
        super.onStop()
    }
}
