package io.github.zichen0116.pointa.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeColorTest {
    @Test fun laserAlwaysUsesSelectedThemeColor() {
        for (theme in ThemeColorHex.indices) assertEquals(ThemeColorHex[theme], Preferences(theme = theme).laserColor)
        assertEquals("#32B894", Preferences(theme = 1).copy(theme = 2).laserColor)
    }
}
