package io.github.zichen0116.pointa

import android.util.Base64
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

class AppFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun mainActivityPairsControlsReconnectsAndForgets() {
        val encoded = InstrumentationRegistry.getArguments().getString("pointa.appFixture")
        assumeTrue("Run device-smoke.ps1 -AppFlow with its isolated Windows host", encoded != null)
        val fixture = JSONObject(String(Base64.decode(encoded, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)))
        lateinit var model: PointaViewModel
        compose.runOnUiThread { model = ViewModelProvider(compose.activity)[PointaViewModel::class.java] }
        compose.waitUntil(5000) { model.ready }
        compose.runOnUiThread {
            model.changePreferences(model.preferences.value.copy(onboardingDone = true, language = "zh", autoReconnect = true))
            model.connectQr(fixture.getString("qr").replace("host=127.0.0.1&", "host=10.0.2.2&"))
        }
        compose.waitUntil(15000) { model.ui.value.connected }
        compose.onNodeWithText("下一页").performClick()
        compose.onNodeWithContentDescription("按住移动").performTouchInput {
            down(center); moveBy(androidx.compose.ui.geometry.Offset(80f, 40f)); up()
        }
        compose.runOnIdle { assertNull(model.ui.value.statusError) }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val output = java.io.File(context.getExternalFilesDir(null), "previews").apply { mkdirs() }
        fun screenshot(name: String) {
            compose.waitForIdle()
            Thread.sleep(300) // Wait for the emulator compositor to present the frame, beyond Compose semantics.
            val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            java.io.File(output, name).outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
        screenshot("one-hand-zh.png")
        compose.onNodeWithContentDescription("个性化设置").performClick()
        compose.onNodeWithText("主题颜色").assertExists()
        screenshot("settings-zh.png")
        compose.onNodeWithContentDescription("返回演示").performClick()
        compose.runOnUiThread { model.setForeground(false) }
        compose.waitUntil(5000) { !model.ui.value.connected }
        compose.runOnUiThread { model.setForeground(true) }
        compose.waitUntil(15000) { model.ui.value.connected }
        val id = model.ui.value.connectedDeviceId!!
        compose.runOnUiThread { model.forget(id) }
        compose.waitUntil(5000) { model.ui.value.recentDevices.none { it.id == id } && !model.ui.value.connected }
        assertTrue(SecureDeviceStore(context).load().none { it.serverId == id })
    }
}
