package io.github.zichen0116.pointa

import android.util.Base64
import android.app.UiAutomation
import android.content.res.Configuration
import android.view.accessibility.AccessibilityEvent
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import io.github.zichen0116.pointa.ui.Margins
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
            model.changePreferences(model.preferences.value.copy(onboardingDone = true, language = "zh", autoReconnect = true,
                mode = "one", leftHand = false, rightMargins = Margins(), theme = 1))
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
        fun screenshot(name: String, waitForCompose: Boolean = true) {
            if (waitForCompose) compose.waitForIdle()
            Thread.sleep(if (waitForCompose) 300 else 700) // Allow the system Toast's entrance animation after replacing the previous one.
            val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            java.io.File(output, name).outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
        screenshot("one-hand-zh.png")
        val workspace = compose.onNodeWithTag("control-workspace").getUnclippedBoundsInRoot()
        compose.onNodeWithContentDescription("编辑触控区域").performClick()
        assertEquals(workspace, compose.onNodeWithTag("control-workspace").getUnclippedBoundsInRoot())
        compose.onNodeWithContentDescription("调整左边").performTouchInput { down(center); moveBy(androidx.compose.ui.geometry.Offset(60f,0f)); up() }
        screenshot("edit-in-place-zh.png")
        compose.onNodeWithContentDescription("完成编辑").performClick()
        compose.waitUntil(5000) { PreferenceStore(context).load().rightMargins.left > 0 }
        assertEquals(workspace, compose.onNodeWithTag("control-workspace").getUnclippedBoundsInRoot())

        val notices = mutableListOf<String>()
        lateinit var noticeJob: Job
        compose.runOnUiThread {
            noticeJob = compose.activity.lifecycleScope.launch(start = CoroutineStart.UNDISPATCHED) { model.notices.collect { notices.add(it) } }
            model.onActionResult("toast-test-1", false, "presentation_not_focused")
        }
        compose.waitUntil(3000) { notices.size == 1 }
        compose.runOnIdle { assertNull(model.ui.value.statusError); assertTrue(notices.single().contains("翻页未执行")) }
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val toastEvent = automation.executeAndWaitForEvent({
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                model.onActionResult("toast-test-2", false, "presentation_not_focused")
            }
        }, { event ->
            event.eventType == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED &&
                event.text.any { it.contains("翻页未执行") }
        }, 5000)
        assertTrue(toastEvent.text.any { it.contains("翻页未执行") })
        // Espresso can wait until a platform Toast expires; capture directly after its accessibility event.
        screenshot("action-toast-zh.png", waitForCompose = false)
        compose.waitUntil(3000) { notices.size == 2 }
        compose.runOnUiThread { noticeJob.cancel() }
        Thread.sleep(2500) // Wait for the platform's short Toast to expire before checking the clean control screen.
        compose.runOnIdle { assertNull(model.ui.value.statusError) }
        screenshot("after-toast-zh.png")

        for (mode in listOf("full", "slides", "air")) {
            compose.runOnUiThread { model.changePreferences(model.preferences.value.copy(mode = mode)) }
            compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)).assertCountEquals(0)
            compose.onNodeWithText("下一页").assertIsDisplayed()
        }
        screenshot("air-pointer-zh.png")
        try {
            assertTrue(automation.setRotation(UiAutomation.ROTATION_FREEZE_90))
            compose.waitUntil(5000) { runCatching { compose.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }.getOrDefault(false) }
            compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)).assertCountEquals(0)
            compose.onNodeWithText("下一页").assertIsDisplayed()
            screenshot("air-landscape-zh.png")
        } finally { automation.setRotation(UiAutomation.ROTATION_FREEZE_0) }
        compose.waitUntil(5000) { runCatching { compose.activity.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT }.getOrDefault(false) }
        compose.runOnUiThread { model.changePreferences(model.preferences.value.copy(mode = "one")) }
        compose.onNodeWithContentDescription("个性化设置").performClick()
        compose.onNodeWithText("主题颜色").assertExists()
        screenshot("settings-zh.png")
        compose.onNodeWithText("欢迎提交 PR 和 issue").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("GitHub · Pointa").performScrollTo().assertIsDisplayed().assertHasClickAction()
        compose.onNodeWithText("触觉反馈").assertDoesNotExist()
        compose.onNodeWithText("激光颜色").assertDoesNotExist()
        compose.onNodeWithText("开源许可").assertDoesNotExist()
        screenshot("settings-footer-zh.png")
        compose.onNodeWithContentDescription("返回演示").performScrollTo()
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
