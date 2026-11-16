package io.github.zichen0116.pointa

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.zichen0116.pointa.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PresentationUiTest {
    @get:Rule val compose = createComposeRule()
    private var pages = 0
    private var moves = 0
    private var hides = 0
    private fun actions() = UiActions({}, {}, { _, _ -> }, {}, {}, {}, { _, _ -> moves++ }, { hides++ }, { pages++ }, {})

    @Test fun codeEntryIsSeparateAndAutomaticallyPairsOnceWhenComplete() {
        val requests = mutableListOf<Pair<String,String>>()
        val actions = actions().copy(pairCode = { host, code -> requests.add(host to code) })
        compose.setContent { PointaApp(UiState(), Preferences(language = "en", onboardingDone = true), {}, actions) }
        compose.onNodeWithText("Enter 6-digit code").performClick()
        compose.onNodeWithText("Scan QR code").assertDoesNotExist()
        listOf("192", "168", "1", "187").forEachIndexed { index, part ->
            compose.onNodeWithContentDescription("IP part ${index + 1}").performScrollTo().performTextInput(part)
        }
        compose.onNodeWithContentDescription("6-digit pairing code").performScrollTo().performTextInput("12345")
        compose.runOnIdle { assertTrue(requests.isEmpty()) }
        compose.onNodeWithContentDescription("6-digit pairing code").performTextInput("6")
        compose.runOnIdle { assertEquals(listOf("192.168.1.187" to "123456"), requests) }
        compose.onNodeWithText("Try again").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(2, requests.size) }
    }

    @Test fun settingsPageStaysFullScreenAndKeepsBackVisibleAtTheBottom() {
        compose.setContent { PointaApp(UiState(connected = true), Preferences(language = "en", onboardingDone = true), {}, actions()) }
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("GitHub · Pointa").performScrollTo()
        val panel = compose.onNodeWithTag("settings-page")
        val bounds = panel.getUnclippedBoundsInRoot()
        repeat(6) { compose.onNodeWithTag("settings-scroll").performTouchInput { swipeUp() } }
        assertEquals(bounds, panel.getUnclippedBoundsInRoot())
        compose.onNodeWithText("GitHub · Pointa").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back to presentation").assertIsDisplayed()
        compose.onNodeWithTag("presentation-screen").assertDoesNotExist()
        compose.onNodeWithText("Auto-reconnect").assertDoesNotExist()
    }

    @Test fun devicePanelHasNoPageScrollAndDismissesByDraggingDown() {
        val computers = (1..20).map { Device("pc-$it", "Computer $it", "192.168.1.$it") }
        compose.setContent { PointaApp(UiState(recentDevices = computers), Preferences(language = "en", onboardingDone = true), {}, actions()) }
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)).assertCountEquals(0)
        compose.onNodeWithText("Choose computer").performClick()
        compose.onNodeWithText("Computer 2").performScrollTo().performClick()
        compose.onNodeWithText("Computer 2").assertIsDisplayed()
        compose.onNodeWithTag("device-sheet-handle", useUnmergedTree = true).performTouchInput { swipe(center, center + androidx.compose.ui.geometry.Offset(0f, 600f)) }
        compose.waitForIdle()
        compose.onNodeWithTag("device-sheet").assertDoesNotExist()
        compose.onNodeWithText("Full-screen").assertIsDisplayed()
    }

    @Test fun fullScreenStartsRelativeAndOneHandUsesAbsoluteCoordinates() {
        var preferences by mutableStateOf(Preferences(language = "en", onboardingDone = true))
        val positions = mutableListOf<Pair<Float,Float>>()
        compose.setContent { PointaApp(UiState(connected = true), preferences, { preferences = it }, actions().copy(move = { x,y -> positions.add(x to y) })) }
        compose.onNodeWithText("Full-screen").assertIsDisplayed()
        compose.onNodeWithContentDescription("Hold to point").performTouchInput {
            down(androidx.compose.ui.geometry.Offset(width * .2f, height * .3f)); up()
        }
        compose.runOnIdle { assertEquals(.5f, positions.last().first, .01f); assertEquals(.5f, positions.last().second, .01f); preferences = preferences.copy(mode = "one") }
        compose.onNodeWithContentDescription("Hold to point").performTouchInput {
            down(androidx.compose.ui.geometry.Offset(width * .2f, height * .3f)); up()
        }
        compose.runOnIdle { assertEquals(.2f, positions.last().first, .01f); assertEquals(.3f, positions.last().second, .01f) }
    }

    @Test fun controlsHideOnReleaseAndEditingStaysInPlaceAndAutosaves() {
        var prefs by mutableStateOf(Preferences(language = "en", mode = "one", onboardingDone = true))
        compose.setContent { PointaApp(UiState(connected = true, deviceName = "Test computer"), prefs, { prefs = it }, actions()) }
        compose.onNodeWithText("Next").performClick()
        assertEquals(1, pages)
        compose.onNodeWithContentDescription("Hold to point").performTouchInput { down(center); moveBy(androidx.compose.ui.geometry.Offset(40f, 20f)); up() }
        compose.runOnIdle { assertTrue(moves > 0); assertTrue(hides > 0) }
        val workspace = compose.onNodeWithTag("control-workspace").getUnclippedBoundsInRoot()
        compose.onNodeWithContentDescription("Edit touch area").performClick()
        assertEquals(workspace, compose.onNodeWithTag("control-workspace").getUnclippedBoundsInRoot())
        compose.onNodeWithContentDescription("Margin values").assertDoesNotExist()
        compose.onNodeWithText("Save").assertDoesNotExist()
        compose.onNodeWithContentDescription("Adjust left edge").performTouchInput { down(center); moveBy(androidx.compose.ui.geometry.Offset(60f,0f)); up() }
        compose.runOnIdle { assertTrue(prefs.rightMargins.left > Preferences().rightMargins.left); assertEquals(Preferences().leftMargins, prefs.leftMargins) }
        compose.onNodeWithContentDescription("Finish editing").performClick()
        compose.runOnIdle { assertTrue(prefs.rightMargins.left > 0) }
        assertEquals(workspace, compose.onNodeWithTag("control-workspace").getUnclippedBoundsInRoot())
        compose.onNodeWithContentDescription("Edit touch area").performClick()
        compose.onNodeWithText("Reset").performClick()
        compose.runOnIdle { assertEquals(Preferences().rightMargins, prefs.rightMargins); assertEquals(Preferences().leftMargins, prefs.leftMargins) }
        compose.onNodeWithContentDescription("Finish editing").performClick()
    }

    @Test fun disconnectedControlsNeverSendActionsAndEnglishSettingsAreReachable() {
        var prefs by mutableStateOf(Preferences(language = "en", onboardingDone = true))
        compose.setContent { PointaApp(UiState(), prefs, { prefs = it }, actions()) }
        compose.onNodeWithTag("device-sheet-handle", useUnmergedTree = true).performTouchInput { swipe(center, center + androidx.compose.ui.geometry.Offset(0f, 600f)) }
        compose.waitForIdle()
        compose.onNodeWithText("Next").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("Theme color").assertExists()
        compose.runOnIdle { assertEquals(0, pages); assertEquals(0, moves) }
    }

    @Test fun editingUndoesWholeDragsAndResetAndStartsFreshEachSession() {
        var prefs by mutableStateOf(Preferences(language = "en", mode = "one", onboardingDone = true))
        compose.setContent { PointaApp(UiState(connected = true), prefs, { prefs = it }, actions()) }
        compose.onNodeWithContentDescription("Hide laser now").assertDoesNotExist()
        compose.onNodeWithContentDescription("Edit touch area").performClick()
        val undo = compose.onNodeWithContentDescription("Undo adjustment")
        undo.assertIsNotEnabled()
        val defaults = prefs.rightMargins
        fun dragEdge(name: String) {
            compose.onNodeWithContentDescription(name).performTouchInput {
                down(center)
                repeat(4) { moveBy(androidx.compose.ui.geometry.Offset(18f, 18f)); advanceEventTime(24) }
                up()
            }
        }
        dragEdge("Adjust left edge")
        val first = compose.runOnIdle { prefs.rightMargins.also { assertNotEquals(defaults, it) } }
        dragEdge("Adjust top edge")
        compose.runOnIdle { assertNotEquals(first, prefs.rightMargins) }
        undo.assertIsEnabled()
        undo.performClick()
        compose.runOnIdle { assertEquals(first, prefs.rightMargins) }
        compose.onNodeWithText("Reset").performClick()
        compose.runOnIdle { assertEquals(defaults, prefs.rightMargins) }
        undo.performClick()
        compose.runOnIdle { assertEquals(first, prefs.rightMargins) }
        undo.performClick()
        compose.runOnIdle { assertEquals(defaults, prefs.rightMargins); assertEquals(Preferences().leftMargins, prefs.leftMargins) }
        undo.assertIsNotEnabled()
        dragEdge("Adjust left edge")
        val saved = compose.runOnIdle { prefs.rightMargins }
        compose.onNodeWithContentDescription("Exit editing").performClick()
        compose.runOnIdle { assertEquals(saved, prefs.rightMargins) }
        compose.onNodeWithContentDescription("Undo adjustment").assertDoesNotExist()
        compose.onNodeWithContentDescription("Edit touch area").performClick()
        undo.assertIsNotEnabled()
    }

    @Test fun allControlModesFitSmallScreensWithoutVerticalScrolling() {
        var prefs by mutableStateOf(Preferences(language = "en", onboardingDone = true))
        var height by mutableStateOf(480.dp)
        compose.setContent {
            Box(Modifier.requiredSize(320.dp, height)) {
                PointaApp(UiState(connected = true, deviceName = "A long computer name that must fit", airAvailable = true), prefs, { prefs = it }, actions())
            }
        }
        for (size in listOf(480.dp, 640.dp)) for (mode in listOf("one", "full", "slides", "air")) {
            compose.runOnIdle { height = size; prefs = prefs.copy(mode = mode) }
            compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)).assertCountEquals(0)
            val screen = compose.onNodeWithTag("presentation-screen").getUnclippedBoundsInRoot()
            val workspace = compose.onNodeWithTag("control-workspace").getUnclippedBoundsInRoot()
            assertTrue("Workspace must fit " + mode, workspace.top >= screen.top && workspace.bottom <= screen.bottom)
            val next = compose.onNodeWithText("Next").getUnclippedBoundsInRoot()
            assertTrue("Next button must fit " + mode, next.bottom <= screen.bottom)
            for (label in listOf("Next", "Previous")) {
                val layouts = mutableListOf<TextLayoutResult>()
                compose.onNodeWithText(label, useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                assertTrue("$label must not clip in $mode at $size: " + layouts.map { "size=${it.size}, height=${it.didOverflowHeight}, ellipsis=${it.isLineEllipsized(0)}" },
                    layouts.isNotEmpty() && layouts.none { it.didOverflowHeight || (0 until it.lineCount).any(it::isLineEllipsized) })
            }
        }
    }

    @Test fun airPointerNeedsNoHoldAndSupportsCalibrationAndDoubleTap() {
        var calibrated = false
        var state by mutableStateOf(UiState(connected = true, airAvailable = true, airActive = true))
        val callbacks = actions().copy(calibrateAir = { calibrated = true })
        compose.setContent { PointaApp(state, Preferences(language = "en", mode = "air", onboardingDone = true), {}, callbacks) }
        compose.onNodeWithText("Press a volume key to turn slides").assertIsDisplayed()
        compose.onNodeWithContentDescription("Air pointer area").performTouchInput { doubleClick() }
        compose.runOnIdle { assertEquals(1, pages) }
        compose.onNodeWithText("Recenter").performClick()
        compose.runOnIdle { assertTrue(calibrated); state = state.copy(airAvailable = false) }
        compose.onNodeWithContentDescription("Air pointer area").assertIsNotEnabled()
    }

    @Test fun doubleTapPagesOnceButSingleTapsAndDragsDoNot() {
        var preferences by mutableStateOf(Preferences(language = "en", onboardingDone = true))
        compose.setContent { PointaApp(UiState(connected = true), preferences, { preferences = it }, actions()) }
        for (mode in listOf("full", "one")) {
            compose.runOnIdle { preferences = preferences.copy(mode = mode); pages = 0 }
            val pad = compose.onNodeWithContentDescription("Hold to point")
            pad.performTouchInput { click() }
            compose.runOnIdle { assertEquals(0, pages) }
            pad.performTouchInput { advanceEventTime(600); doubleClick() }
            compose.runOnIdle { assertEquals(1, pages) }
            pad.performTouchInput {
                advanceEventTime(600)
                down(center); moveBy(androidx.compose.ui.geometry.Offset(80f, 60f)); up()
                advanceEventTime(100); click()
            }
            compose.runOnIdle { assertEquals(1, pages) }
        }
    }
}
