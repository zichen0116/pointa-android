package io.github.zichen0116.pointa.ui

internal val ThemeColorHex = listOf("#111722", "#FF6475", "#32B894", "#4D95ED", "#D69A47", "#B58AE9")

/** Values are persisted by the application layer; UI never reads credentials or performs I/O. */
data class Preferences(
    val theme: Int = 0,
    val appearance: String = "dark", // dark, light, system
    val language: String = "system", // zh, en, system
    val mode: String = "one", // one, full, slides, air
    val leftHand: Boolean = false,
    val leftMargins: Margins = Margins(),
    val rightMargins: Margins = Margins(),
    val fullRelative: Boolean = false,
    val trailEnabled: Boolean = true,
    val trailMs: Int = 500,
    val sensitivity: Int = 60,
    val laserSize: String = "medium", // small, medium, large
    val smoothing: String = "standard", // low, standard, high
    val volumeEnabled: Boolean = true,
    val prevVolume: String = "-", // 0, +, -
    val nextVolume: String = "+",
    val keepScreenOn: Boolean = true,
    val autoReconnect: Boolean = true,
    val reduceMotion: Boolean = false,
    val onboardingDone: Boolean = false,
) {
    val laserColor: String get() = ThemeColorHex[theme.coerceIn(0, ThemeColorHex.lastIndex)]
}

data class Margins(val left: Float = 0f, val right: Float = 0f, val top: Float = 0f, val bottom: Float = 0f)

data class Device(val id: String, val name: String, val host: String, val detail: String = "")

data class UiState(
    val connected: Boolean = false,
    val connecting: Boolean = false,
    val deviceName: String = "",
    val connectedDeviceId: String? = null,
    val statusText: String = "",
    val statusError: String? = null,
    val recentDevices: List<Device> = emptyList(),
    val discoveredDevices: List<Device> = emptyList(),
    val discovering: Boolean = false,
    val scanning: Boolean = false,
    val displayAspect: Float? = null,
    val airAvailable: Boolean = false,
    val airActive: Boolean = false,
    val airUsesTilt: Boolean = false,
)

data class UiActions(
    val scan: () -> Unit,
    val discover: () -> Unit,
    val pairCode: (host: String, code: String) -> Unit,
    val reconnect: (id: String) -> Unit,
    val disconnect: () -> Unit,
    val forget: (id: String) -> Unit,
    val move: (x: Float, y: Float) -> Unit,
    val hide: () -> Unit,
    val page: (next: Boolean) -> Unit,
    val onControlActive: (Boolean) -> Unit,
    val airHold: (Boolean) -> Unit = {},
    val calibrateAir: () -> Unit = {},
)
