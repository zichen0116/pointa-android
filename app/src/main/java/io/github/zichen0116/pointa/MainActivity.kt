package io.github.zichen0116.pointa

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.HapticFeedbackConstants
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
import io.github.zichen0116.pointa.ui.PointaApp
import io.github.zichen0116.pointa.ui.UiActions

class MainActivity : ComponentActivity() {
    private val model: PointaViewModel by viewModels()
    private val consumedVolumeKeys = mutableSetOf<Int>()
    private val cameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) model.setScanning(true) else model.localError("camera_denied")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val state by model.ui.collectAsStateWithLifecycle()
            val preferences by model.preferences.collectAsStateWithLifecycle()
            val systemDark = isSystemInDarkTheme()
            SideEffect {
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
                    page = ::page, onControlActive = model::setControlActive)
            }
            PointaApp(state, preferences, model::changePreferences, actions)
            if (state.scanning) CameraScanner(
                title = model.t("扫描电脑上的映点二维码", "Scan the Pointa QR on your computer"),
                hint = model.t("无需互联网。请让二维码完整出现在画面内。", "Works offline. Keep the entire QR in view."),
                cancel = model.t("取消", "Cancel"), onResult = model::connectQr,
                onClose = { model.setScanning(false) }, onError = { model.setScanning(false); model.localError("camera_failed") })
        }
    }

    private fun scan() {
        model.hide()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) model.setScanning(true)
        else cameraPermission.launch(Manifest.permission.CAMERA)
    }

    private fun page(next: Boolean) {
        if (model.page(next) && model.preferences.value.haptics) window.decorView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
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
        consumedVolumeKeys.clear()
        if (!isChangingConfigurations) model.setForeground(false)
        super.onStop()
    }
}
