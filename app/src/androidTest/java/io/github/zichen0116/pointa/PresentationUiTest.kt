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

    @Test fun controlsHideOnReleaseAndEditingStaysInPlaceAndAutosaves() {
        var prefs by mutableStateOf(Preferences(language = "en", onboardingDone = true))
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
        compose.runOnIdle { assertTrue(prefs.rightMargins.left > 0); assertEquals(Margins(), prefs.leftMargins) }
        compose.onNodeWithContentDescription("Finish editing").performClick()
        compose.runOnIdle { assertTrue(prefs.rightMargins.left > 0) }
        assertEquals(workspace, compose.onNodeWithTag("control-workspace").getUnclippedBoundsInRoot())
        compose.onNodeWithContentDescription("Edit touch area").performClick()
        compose.onNodeWithText("Reset").performClick()
        compose.runOnIdle { assertEquals(Margins(), prefs.rightMargins); assertEquals(Margins(), prefs.leftMargins) }
        compose.onNodeWithContentDescription("Finish editing").performClick()
    }

    @Test fun disconnectedControlsNeverSendActionsAndEnglishSettingsAreReachable() {
        var prefs by mutableStateOf(Preferences(language = "en", onboardingDone = true))
        compose.setContent { PointaApp(UiState(), prefs, { prefs = it }, actions()) }
        compose.onNodeWithContentDescription("Back to presentation").performClick()
        compose.onNodeWithText("Next").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("Theme color").assertExists()
        compose.runOnIdle { assertEquals(0, pages); assertEquals(0, moves) }
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

    @Test fun airPointerUsesHoldReleaseAndHasARealCalibrationAction() {
        val holds = mutableListOf<Boolean>()
        var calibrated = false
        var state by mutableStateOf(UiState(connected = true, airAvailable = true))
        val callbacks = actions().copy(airHold = { holds.add(it); state = state.copy(airActive = it) }, calibrateAir = { calibrated = true })
        compose.setContent { PointaApp(state, Preferences(language = "en", mode = "air", onboardingDone = true), {}, callbacks) }
        compose.onNodeWithContentDescription("Hold to activate air pointer").performTouchInput { down(center); advanceEventTime(100); up() }
        compose.runOnIdle { assertEquals(listOf(true, false), holds) }
        compose.onNodeWithText("Recenter").performClick()
        compose.runOnIdle { assertTrue(calibrated); state = state.copy(airAvailable = false) }
        compose.onNodeWithContentDescription("Hold to activate air pointer").assertIsNotEnabled()
    }
}
