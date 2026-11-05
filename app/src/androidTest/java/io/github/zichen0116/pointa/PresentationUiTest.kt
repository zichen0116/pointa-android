package io.github.zichen0116.pointa

import androidx.compose.runtime.*
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

    @Test fun controlsHideOnReleaseAndEditCancelPreservesBothHands() {
        var prefs by mutableStateOf(Preferences(language = "en", onboardingDone = true))
        compose.setContent { PointaApp(UiState(connected = true, deviceName = "Test computer"), prefs, { prefs = it }, actions()) }
        compose.onNodeWithText("Next").performClick()
        assertEquals(1, pages)
        compose.onNodeWithContentDescription("Hold to point").performTouchInput { down(center); moveBy(androidx.compose.ui.geometry.Offset(40f, 20f)); up() }
        compose.runOnIdle { assertTrue(moves > 0); assertTrue(hides > 0) }
        compose.onNodeWithContentDescription("Edit touch area").performClick()
        compose.onNodeWithContentDescription("Left margin").performScrollTo().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(.2f) }
        compose.onNodeWithText("Cancel").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(Margins(), prefs.rightMargins); assertEquals(Margins(), prefs.leftMargins) }
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
}
