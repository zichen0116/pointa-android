package io.github.zichen0116.pointa.ui

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import kotlin.math.roundToInt

internal class Copy(private val chinese: Boolean) {
    fun t(zh: String, en: String) = if (chinese) zh else en
}
internal val LocalCopy = staticCompositionLocalOf { Copy(true) }
internal val ThemeColors = listOf(0xFF111722, 0xFFFF6475, 0xFF32B894, 0xFF4D95ED, 0xFFD69A47, 0xFFB58AE9).map(::Color)
internal val LocalThemeIndex = staticCompositionLocalOf { 0 }
private class PointerMemory(var point: Point = Point(.5f, .5f))

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun PointaApp(state: UiState, preferences: Preferences, onPreferencesChange: (Preferences) -> Unit, actions: UiActions) {
    val locale = LocalConfiguration.current.locales[0].language
    val copy = Copy(preferences.language == "zh" || preferences.language == "system" && locale == "zh")
    val dark = when (preferences.appearance) { "light" -> false; "system" -> isSystemInDarkTheme(); else -> true }
    val context = LocalContext.current
    val system = if (Build.VERSION.SDK_INT >= 31) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else if (dark) darkColorScheme() else lightColorScheme()
    val theme = preferences.theme.coerceIn(0, 5)
    val accent = if (theme == 0) system.primary else ThemeColors[theme]
    val scheme = if (dark) darkColorScheme(primary = accent, onPrimary = Color(0xFF111722),
        background = Color(0xFF111722), surface = Color(0xFF192332), surfaceVariant = Color(0xFF263246),
        onBackground = Color(0xFFF4F6FB), onSurface = Color(0xFFF4F6FB), onSurfaceVariant = Color(0xFFA7B5CA), outline = Color(0xFF344052))
    else lightColorScheme(primary = if (theme == 0) system.primary else accent, background = Color(0xFFF3F5F9),
        surface = Color.White, surfaceVariant = Color(0xFFE3EAF3), onBackground = Color(0xFF172236),
        onSurface = Color(0xFF172236), onSurfaceVariant = Color(0xFF56657A), outline = Color(0xFFB4C0D0))
    MaterialTheme(colorScheme = scheme) {
        CompositionLocalProvider(LocalCopy provides copy, LocalThemeIndex provides theme) {
            var panel by rememberSaveable { mutableStateOf<String?>(if (state.connected) null else "devices") }
            var locked by rememberSaveable { mutableStateOf(false) }
            var draft by remember { mutableStateOf<Margins?>(null) }
            val pointer = remember { PointerMemory() }
            val currentActions by rememberUpdatedState(actions)
            val controlActive = panel == null && draft == null && preferences.onboardingDone && state.connected && !state.scanning
            LaunchedEffect(controlActive) {
                currentActions.onControlActive(controlActive)
                if (!controlActive) currentActions.hide()
            }
            DisposableEffect(Unit) { onDispose { currentActions.hide(); currentActions.onControlActive(false) } }
            LaunchedEffect(state.connected) { if (state.connected) panel = null }
            BackHandler(panel != null || draft != null || locked) {
                when { panel != null -> panel = null; draft != null -> draft = null; locked -> locked = false }
            }
            Surface(modifier = Modifier.fillMaxSize(), color = scheme.background) {
                Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                    if (!preferences.onboardingDone) Onboarding { onPreferencesChange(preferences.copy(onboardingDone = true)) }
                    else ControlScreen(state, preferences, onPreferencesChange, actions, pointer,
                        locked, { locked = it }, draft, { draft = it },
                        onDevices = { panel = "devices" }, onSettings = { panel = "settings" })
                }
            }
            if (preferences.onboardingDone && panel != null) {
                ModalBottomSheet(onDismissRequest = { panel = null },
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = scheme.background) {
                    Box(Modifier.fillMaxWidth().fillMaxHeight(.94f)) {
                        if (panel == "devices") DeviceScreen(state, actions, onBack = { panel = null }, onSettings = { panel = "settings" })
                        else SettingsScreen(preferences, onPreferencesChange, onBack = { panel = null }, onDevices = { panel = "devices" })
                    }
                }
            }
        }
    }
}

@Composable
private fun Onboarding(onDone: () -> Unit) {
    val s = LocalCopy.current
    var step by rememberSaveable { mutableIntStateOf(0) }
    val titles = listOf(s.t("手机就是翻页笔", "Your phone. Your presenter."), s.t("按住指示，松手隐藏", "Hold to point. Release to hide."), s.t("专注每一次演示", "Stay focused on your presentation"))
    val descriptions = listOf(s.t("让手机和电脑连接同一 Wi-Fi，扫描 Windows 端二维码，或输入设备码完成配对。", "Connect both devices to the same Wi-Fi. Scan the Windows QR code, or use its pairing code."),
        s.t("单手模式像触控板一样移动光点。全屏模式对应电脑屏幕；激光与系统鼠标相互独立。", "One-hand mode works like a trackpad. Full-screen mode maps to the display. The laser stays separate from the system mouse."),
        s.t("大按钮和音量键都能翻页。演示前先在电脑打开幻灯片放映；随时可以调整惯用手、触觉和激光效果。", "Use the large buttons or volume keys to turn slides. Start the slideshow on your PC first. Adjust your preferred hand, haptics and laser anytime."))
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.SpaceBetween) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("映点 Pointa", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = onDone) { Text(s.t("跳过", "Skip")) }
        }
        Column(Modifier.padding(vertical = 48.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Icon(listOf(Icons.Default.QrCodeScanner, Icons.Default.TouchApp, Icons.Default.Slideshow)[step], null,
                Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary)
            Text(titles[step], style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Text(descriptions[step], style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Text("${step + 1} / 3", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = { if (step == 2) onDone() else step++ }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Text(if (step == 2) s.t("开始连接", "Connect a computer") else s.t("下一步", "Next"))
            }
        }
    }
}

@Composable
internal fun PageHeader(title: String, onBack: () -> Unit, trailing: (@Composable () -> Unit)? = null) {
    val s = LocalCopy.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, s.t("返回演示", "Back to presentation"))
        }
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        trailing?.invoke()
    }
}

@Composable
internal fun CardGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .4f))) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
private fun Status(state: UiState) {
    val s = LocalCopy.current
    if (state.connecting || state.statusText.isNotBlank()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (state.connecting) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            Text(state.statusText.ifBlank { s.t("正在连接电脑…", "Connecting…") }, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        }
    }
    state.statusError?.takeIf(String::isNotBlank)?.let { error ->
        Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
            Text(error, Modifier.padding(14.dp).semantics { liveRegion = LiveRegionMode.Polite }, color = MaterialTheme.colorScheme.onErrorContainer)
        }
    }
}

@Composable
private fun DeviceScreen(state: UiState, actions: UiActions, onBack: () -> Unit, onSettings: () -> Unit) {
    val s = LocalCopy.current
    var showCode by rememberSaveable { mutableStateOf(false) }
    var code by rememberSaveable { mutableStateOf("") }
    var host by rememberSaveable { mutableStateOf("") }
    var forgotten by remember { mutableStateOf<Device?>(null) }
    val focus = remember { FocusRequester() }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        PageHeader(s.t("连接设备", "Connect a computer"), onBack) {
            IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, s.t("设置", "Settings")) }
        }
        Text(s.t("同一 Wi-Fi，即刻开始演示", "One Wi-Fi. Ready to present."), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(s.t("请先在电脑上打开映点 Pointa。", "Open Pointa on your Windows computer first."), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Status(state)
        if (state.recentDevices.isNotEmpty()) {
            Text(s.t("最近使用", "Recent computers"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            state.recentDevices.forEach { device ->
                CardGroup {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Default.Computer, null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary)
                        Column(Modifier.weight(1f)) {
                            Text(device.name, fontWeight = FontWeight.SemiBold)
                            Text(device.detail.ifBlank { device.host }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { forgotten = device }, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Default.DeleteOutline, s.t("忘记设备", "Forget computer"))
                        }
                    }
                    Button(onClick = { if (state.connected && device.id == state.connectedDeviceId) onBack() else actions.reconnect(device.id) },
                        enabled = !state.connecting, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text(if (state.connected && device.id == state.connectedDeviceId) s.t("继续演示", "Continue presentation") else s.t("连接电脑", "Connect"))
                    }
                }
            }
        }
        if (state.connected) OutlinedButton(onClick = actions.disconnect, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text(s.t("断开当前连接", "Disconnect"))
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .4f))
        Text(s.t("添加新设备", "Add a computer"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        OutlinedButton(onClick = actions.scan, enabled = !state.connecting && !state.scanning, modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp), shape = RoundedCornerShape(16.dp)) {
            Icon(Icons.Default.QrCodeScanner, null); Spacer(Modifier.width(12.dp)); Text(s.t("扫描二维码", "Scan QR code"))
        }
        OutlinedButton(onClick = { showCode = !showCode; if (showCode) actions.discover() }, enabled = !state.connecting,
            modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp), shape = RoundedCornerShape(16.dp)) {
            Icon(Icons.Default.Password, null); Spacer(Modifier.width(12.dp)); Text(s.t("输入 6 位设备码", "Enter 6-digit code"))
        }
        if (showCode) {
            LaunchedEffect(Unit) { focus.requestFocus() }
            CardGroup {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(s.t("选择电脑", "Choose a computer"), Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                    TextButton(onClick = actions.discover, enabled = !state.discovering) {
                        Text(if (state.discovering) s.t("搜索中…", "Searching…") else s.t("重新搜索", "Refresh"))
                    }
                }
                state.discoveredDevices.forEach { device ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { host = device.host }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = host == device.host, onClick = { host = device.host })
                        Column { Text(device.name); Text(device.host, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
                if (!state.discovering && state.discoveredDevices.isEmpty()) Text(s.t("未发现电脑，可手动输入 Windows 配对窗口中的 IP 地址。", "No computers found. Enter the IP address shown in the Windows pairing window."), style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(value = host, onValueChange = { host = it.trim().take(255) }, label = { Text(s.t("电脑 IP 地址", "Computer IP address")) },
                    placeholder = { Text("192.168.1.100") }, singleLine = true, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
                Text(s.t("6 位设备码", "6-digit pairing code"), style = MaterialTheme.typography.labelLarge)
                BasicTextField(value = code, onValueChange = { code = it.filter { c -> c in '0'..'9' }.take(6) }, singleLine = true,
                    textStyle = MaterialTheme.typography.titleLarge.copy(color = Color.Transparent), cursorBrush = SolidColor(Color.Transparent),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus).semantics { contentDescription = s.t("6 位设备码", "6-digit pairing code") },
                    decorationBox = { input ->
                        Box {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                repeat(6) { index ->
                                    Box(Modifier.weight(1f).heightIn(min = 56.dp).border(1.dp,
                                        if (index == code.length) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                        RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                                        Text(code.getOrNull(index)?.toString() ?: "·", style = MaterialTheme.typography.titleLarge)
                                    }
                                }
                            }
                            Box(Modifier.matchParentSize().alpha(0f)) { input() }
                        }
                    })
                Button(onClick = { actions.pairCode(host.trim(), code) }, enabled = host.isNotBlank() && code.length == 6 && !state.connecting,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(s.t("安全配对", "Pair securely")) }
                Text(s.t("设备码会过期；请使用电脑上当前显示的号码，并在电脑上确认连接。", "Pairing codes expire. Use the current code and approve the connection on your computer."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(s.t("连接不上？确认手机和电脑处于同一局域网。校园或访客 Wi-Fi 可能禁止设备互访，可尝试手机热点；也可在 Windows 端设置中允许映点通过防火墙。", "Can't connect? Both devices must be reachable on the same local network. Campus or guest Wi-Fi may isolate devices; try a phone hotspot, or allow Pointa through the firewall in its Windows settings."),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
    }
    forgotten?.let { device ->
        AlertDialog(onDismissRequest = { forgotten = null }, title = { Text(s.t("忘记这台电脑？", "Forget this computer?")) },
            text = { Text(s.t("将移除本机保存的凭据。若电脑不可达，请另在电脑端设备管理中撤销此手机的授权。", "Saved credentials will be removed. If the computer cannot be reached, revoke this phone separately in its device manager.")) },
            confirmButton = { TextButton(onClick = { actions.forget(device.id); forgotten = null }) { Text(s.t("忘记设备", "Forget")) } },
            dismissButton = { TextButton(onClick = { forgotten = null }) { Text(s.t("取消", "Cancel")) } })
    }
}

@Composable
private fun ControlScreen(state: UiState, p: Preferences, update: (Preferences) -> Unit, actions: UiActions, pointer: PointerMemory,
    locked: Boolean, onLock: (Boolean) -> Unit, draft: Margins?, setDraft: (Margins?) -> Unit, onDevices: () -> Unit, onSettings: () -> Unit) {
    val s = LocalCopy.current
    var modesOpen by remember { mutableStateOf(false) }
    var marginsOpen by remember { mutableStateOf(false) }
    var statusOpen by remember { mutableStateOf(false) }
    val modeName = when (p.mode) {
        "full" -> s.t("全屏触控", "Full-screen")
        "slides" -> s.t("纯翻页", "Slides only")
        "air" -> s.t("空中指针", "Air pointer")
        else -> s.t("单手模式", "One-hand")
    }
    BoxWithConstraints(Modifier.fillMaxSize().testTag("presentation-screen")) {
        val compact = maxHeight < 440.dp
        Column(Modifier.fillMaxSize().padding(horizontal = if (compact) 12.dp else 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).background(if (state.connected) Color(0xFF42D2A6) else MaterialTheme.colorScheme.outline, CircleShape))
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(state.deviceName.ifBlank { s.t("映点 Pointa", "Pointa") }, style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (!compact) Text(if (state.connected) s.t("已连接", "Connected") else s.t("尚未连接", "Disconnected"),
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { onLock(!locked) }, enabled = draft == null) {
                    Icon(if (locked) Icons.Default.Lock else Icons.Default.LockOpen,
                        if (locked) s.t("解锁布局", "Unlock layout") else s.t("锁定布局", "Lock layout"))
                }
                if (!locked) {
                    IconButton(onClick = onDevices, enabled = draft == null) { Icon(Icons.Default.Computer, s.t("连接设备", "Devices")) }
                    IconButton(onClick = onSettings, enabled = draft == null) { Icon(Icons.Default.Settings, s.t("个性化设置", "Settings")) }
                }
            }
            if (state.statusError != null || state.connecting) {
                Text(state.statusError ?: state.statusText, Modifier.fillMaxWidth().clickable { statusOpen = true }
                    .semantics { liveRegion = LiveRegionMode.Polite },
                    maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall,
                    color = if (state.statusError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    TextButton(onClick = { modesOpen = true }, enabled = !locked && draft == null,
                        contentPadding = PaddingValues(horizontal = 0.dp)) {
                        Text(modeName, Modifier.weight(1f, fill = false), fontSize = if (compact) 18.sp else 22.sp,
                            fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onBackground)
                        if (!locked) Icon(Icons.Default.ExpandMore, null)
                    }
                    DropdownMenu(expanded = modesOpen, onDismissRequest = { modesOpen = false }) {
                        listOf("one" to s.t("单手模式", "One-hand"), "full" to s.t("全屏触控", "Full-screen"),
                            "slides" to s.t("纯翻页", "Slides only"), "air" to s.t("空中指针", "Air pointer")).forEach { (mode, label) ->
                            DropdownMenuItem(text = { Text(label) }, onClick = { actions.hide(); update(p.copy(mode = mode)); modesOpen = false })
                        }
                    }
                }
                if (p.mode == "one" && !locked && draft == null) {
                    TextButton(onClick = { update(p.copy(leftHand = !p.leftHand)) }) { Text(if (p.leftHand) s.t("左手", "Left") else s.t("右手", "Right")) }
                    IconButton(onClick = { actions.hide(); setDraft(if (p.leftHand) p.leftMargins else p.rightMargins) }) {
                        Icon(Icons.Default.Edit, s.t("编辑触控区域", "Edit touch area"))
                    }
                }
                if (p.mode != "slides" && draft == null) IconButton(onClick = actions.hide, enabled = state.connected) {
                    Icon(Icons.Default.VisibilityOff, s.t("立即隐藏激光", "Hide laser now"))
                }
            }
            if (draft != null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedButton(onClick = { setDraft(null) }, modifier = Modifier.weight(1f)) { Text(s.t("取消", "Cancel")) }
                    TextButton(onClick = { setDraft(Margins()) }, modifier = Modifier.weight(1f)) { Text(s.t("重置", "Reset")) }
                    Button(onClick = {
                        update(if (p.leftHand) p.copy(leftMargins = draft.normalized()) else p.copy(rightMargins = draft.normalized()))
                        setDraft(null)
                    }, modifier = Modifier.weight(1f)) { Text(s.t("保存", "Save")) }
                    IconButton(onClick = { marginsOpen = true }) { Icon(Icons.Default.Tune, s.t("边距数值", "Margin values")) }
                }
            }
            Box(Modifier.fillMaxWidth().weight(1f).testTag("control-workspace")) {
                when (p.mode) {
                    "slides" -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        PageButton(false, state.connected, { actions.page(false) }, Modifier.fillMaxWidth().weight(.36f))
                        PageButton(true, state.connected, { actions.page(true) }, Modifier.fillMaxWidth().weight(.64f))
                    }
                    "full" -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        LaserPad(state.connected, p.fullRelative, p.sensitivity, state.displayAspect, pointer, actions,
                            Modifier.fillMaxWidth().weight(1f), p.laserColor)
                        Row(Modifier.fillMaxWidth().height(if (compact) 56.dp else 76.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            PageButton(false, state.connected, { actions.page(false) }, Modifier.weight(.4f).fillMaxHeight())
                            PageButton(true, state.connected, { actions.page(true) }, Modifier.weight(.6f).fillMaxHeight())
                        }
                    }
                    "air" -> AirWorkspace(state, actions)
                    else -> OneHandWorkspace(p, state.connected, actions, pointer, draft, setDraft, Modifier.fillMaxSize())
                }
            }
            if (!compact) Text(
                when {
                    draft != null -> s.t("拖动四边圆点 · 保存后生效", "Drag an edge handle · Save to apply")
                    !state.connected -> s.t("请先连接电脑以启用演示控制", "Connect a computer to enable controls")
                    locked -> s.t("布局已锁定 · 点按锁图标解锁", "Layout locked · Tap the lock to unlock")
                    p.mode == "slides" -> s.t("先在电脑上开始幻灯片放映", "Start the slideshow on your computer")
                    p.mode == "air" -> s.t("按住轻转手机 · 松手隐藏 · 可随时归零", "Hold and rotate gently · Release to hide · Recenter anytime")
                    else -> s.t("按住移动 · 松手隐藏", "Hold to move · Release to hide")
                }, modifier = Modifier.fillMaxWidth(), maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (statusOpen) AlertDialog(onDismissRequest = { statusOpen = false }, title = { Text(s.t("连接与控制状态", "Connection and controls")) },
        text = { Text(state.statusError ?: state.statusText) }, confirmButton = { TextButton(onClick = { statusOpen = false }) { Text(s.t("关闭", "Close")) } })
    if (draft != null && marginsOpen) AlertDialog(onDismissRequest = { marginsOpen = false },
        title = { Text(s.t("触控区边距", "Touch area margins")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Edge.entries.forEach { edge ->
                    val value = when (edge) { Edge.LEFT -> draft.left; Edge.RIGHT -> draft.right; Edge.TOP -> draft.top; Edge.BOTTOM -> draft.bottom }
                    val name = when (edge) { Edge.LEFT -> s.t("左边距", "Left margin"); Edge.RIGHT -> s.t("右边距", "Right margin"); Edge.TOP -> s.t("上边距", "Top margin"); Edge.BOTTOM -> s.t("下边距", "Bottom margin") }
                    Text(name + " · " + (value * 100).roundToInt() + "%", style = MaterialTheme.typography.labelLarge)
                    Slider(value = value, onValueChange = { setDraft(draft.withEdge(edge, it)) },
                        valueRange = 0f..if (edge == Edge.LEFT || edge == Edge.RIGHT) .58f else .6f,
                        modifier = Modifier.semantics { contentDescription = name })
                }
            }
        }, confirmButton = { TextButton(onClick = { marginsOpen = false }) { Text(s.t("完成", "Done")) } })
}

@Composable
private fun AirWorkspace(state: UiState, actions: UiActions) {
    val s = LocalCopy.current
    val currentActions by rememberUpdatedState(actions)
    val enabled = state.connected && state.airAvailable
    DisposableEffect(Unit) { onDispose { currentActions.airHold(false) } }
    @Composable fun Pad(modifier: Modifier) {
        Box(modifier.clip(RoundedCornerShape(24.dp))
            .background(if (state.airActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(24.dp))
            .semantics { contentDescription = s.t("按住激活空中指针", "Hold to activate air pointer"); if (!enabled) disabled() }
            .pointerInput(enabled) {
                if (enabled) detectTapGestures(onPress = {
                    currentActions.airHold(true)
                    try { awaitRelease() } finally { currentActions.airHold(false) }
                })
            }, contentAlignment = Alignment.Center) {
            Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Default.ScreenRotation, null, Modifier.size(44.dp),
                    tint = if (state.airActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                Text(when {
                    !state.airAvailable -> s.t("此设备无旋转传感器，请用触控模式", "No rotation sensor. Use touch mode.")
                    state.airActive -> s.t("轻转手机进行指示", "Rotate gently to point")
                    else -> s.t("按住激活空中指针", "Hold to activate air pointer")
                }, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
    @Composable fun Recenter(modifier: Modifier) {
        OutlinedButton(onClick = actions.calibrateAir, enabled = state.airAvailable, modifier = modifier.heightIn(min = 48.dp)) {
            Icon(Icons.Default.GpsFixed, null); Spacer(Modifier.width(8.dp)); Text(s.t("归零校准", "Recenter"))
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxHeight < 240.dp) Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Pad(Modifier.weight(1f).fillMaxHeight())
            Column(Modifier.width(150.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PageButton(false, state.connected, { actions.page(false) }, Modifier.weight(1f).fillMaxHeight())
                    PageButton(true, state.connected, { actions.page(true) }, Modifier.weight(1f).fillMaxHeight())
                }
                Recenter(Modifier.fillMaxWidth())
            }
        } else Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Pad(Modifier.fillMaxWidth().weight(1f))
            Row(Modifier.fillMaxWidth().height(70.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PageButton(false, state.connected, { actions.page(false) }, Modifier.weight(.4f).fillMaxHeight())
                PageButton(true, state.connected, { actions.page(true) }, Modifier.weight(.6f).fillMaxHeight())
            }
            Recenter(Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun PageButton(next: Boolean, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val s = LocalCopy.current
    val accent = LocalThemeIndex.current != 0 && next
    Button(onClick = onClick, enabled = enabled, modifier = modifier.heightIn(min = 48.dp), shape = RoundedCornerShape(22.dp),
        colors = ButtonDefaults.buttonColors(containerColor = if (accent) ThemeColors[LocalThemeIndex.current] else Color(0xFF263246),
            contentColor = if (accent) Color(0xFF111722) else Color(0xFFF4F6FB)),
        border = BorderStroke(1.dp, if (accent) Color.Transparent else Color(0xFF344052)), contentPadding = PaddingValues(8.dp)) {
        val label = if (next) s.t("下一页", "Next") else s.t("上一页", "Previous")
        val arrow = if (next) Icons.Default.ChevronRight else Icons.Default.ChevronLeft
        BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val compact = maxHeight < 72.dp
            val labelStyle = if (maxWidth < 72.dp) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge
            if (maxHeight < 40.dp) Text(label, style = labelStyle, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            else if (compact && maxWidth >= 96.dp) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(arrow, null, Modifier.size(24.dp))
                Text(label, style = labelStyle, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            } else Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(if (compact) 0.dp else 8.dp)) {
                Icon(arrow, null, Modifier.size(if (compact) 20.dp else if (next) 32.dp else 26.dp))
                Text(label, style = labelStyle, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun OneHandWorkspace(p: Preferences, enabled: Boolean, actions: UiActions, pointer: PointerMemory, draft: Margins?, setDraft: (Margins?) -> Unit, modifier: Modifier) {
    val density = LocalDensity.current
    val s = LocalCopy.current
    val margins = draft ?: if (p.leftHand) p.leftMargins else p.rightMargins
    BoxWithConstraints(modifier) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val geometry = oneHandGeometry(w, h, with(density) { 9.dp.toPx() }, p.leftHand, margins)
        fun Rect.modifier() = Modifier.offset { IntOffset(x.roundToInt(), y.roundToInt()) }.size(with(density) { width.toDp() }, with(density) { height.toDp() })
        Column(geometry.rail.modifier(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            PageButton(false, enabled && draft == null, { actions.page(false) }, Modifier.fillMaxWidth().weight(.4f))
            PageButton(true, enabled && draft == null, { actions.page(true) }, Modifier.fillMaxWidth().weight(.6f))
        }
        LaserPad(enabled && draft == null, true, p.sensitivity, null, pointer, actions, geometry.touch.modifier(), p.laserColor, draft != null)
        if (draft != null) {
            Edge.entries.forEach { edge ->
                val point = when (edge) {
                    Edge.LEFT -> Offset(geometry.touch.x, geometry.touch.y + geometry.touch.height / 2)
                    Edge.RIGHT -> Offset(geometry.touch.x + geometry.touch.width, geometry.touch.y + geometry.touch.height / 2)
                    Edge.TOP -> Offset(geometry.touch.x + geometry.touch.width / 2, geometry.touch.y)
                    Edge.BOTTOM -> Offset(geometry.touch.x + geometry.touch.width / 2, geometry.touch.y + geometry.touch.height)
                }
                val half = with(density) { 24.dp.toPx() }
                val name = when (edge) { Edge.LEFT -> s.t("调整左边", "Adjust left edge"); Edge.RIGHT -> s.t("调整右边", "Adjust right edge"); Edge.TOP -> s.t("调整上边", "Adjust top edge"); Edge.BOTTOM -> s.t("调整下边", "Adjust bottom edge") }
                val latestDraft by rememberUpdatedState(draft)
                Box(Modifier.offset { IntOffset((point.x - half).coerceIn(0f, (w - half * 2).coerceAtLeast(0f)).roundToInt(), (point.y - half).coerceIn(0f, (h - half * 2).coerceAtLeast(0f)).roundToInt()) }
                    .size(48.dp).semantics { contentDescription = name }
                    .pointerInput(edge, geometry.containerWidth, h) {
                        detectDragGestures { change, amount ->
                            change.consume()
                            val current = latestDraft ?: return@detectDragGestures
                            val value = when (edge) { Edge.LEFT -> current.left + amount.x / geometry.containerWidth; Edge.RIGHT -> current.right - amount.x / geometry.containerWidth; Edge.TOP -> current.top + amount.y / h; Edge.BOTTOM -> current.bottom - amount.y / h }
                            setDraft(current.withEdge(edge, value))
                        }
                    }, contentAlignment = Alignment.Center) {
                    Box(Modifier.size(18.dp).background(MaterialTheme.colorScheme.primary, CircleShape).border(3.dp, MaterialTheme.colorScheme.surface, CircleShape))
                }
            }
        }
    }
}

@Composable
private fun LaserPad(enabled: Boolean, relative: Boolean, sensitivity: Int, aspect: Float?, memory: PointerMemory,
    actions: UiActions, modifier: Modifier, laserColor: String, editing: Boolean = false) {
    val s = LocalCopy.current
    val currentActions by rememberUpdatedState(actions)
    var visible by remember { mutableStateOf(false) }
    var localPoint by remember { mutableStateOf(Offset.Zero) }
    val color = remember(laserColor) { runCatching { Color(android.graphics.Color.parseColor(laserColor)) }.getOrDefault(Color(0xFFFF6475)) }
    val outline = MaterialTheme.colorScheme.outline
    val text = if (editing) s.t("编辑触控区域", "Edit touch area") else if (enabled) s.t("按住移动", "Hold to point") else s.t("等待连接", "Waiting for connection")
    Box(modifier.clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surface)
        .border(1.dp, outline, RoundedCornerShape(24.dp))
        .semantics { contentDescription = text; if (!enabled) disabled() }
        .pointerInput(enabled, relative, sensitivity, aspect, editing) {
            if (!enabled || editing) return@pointerInput
            val canvas = fitAspect(size.width.toFloat(), size.height.toFloat(), if (relative) null else aspect)
            try {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    var previous = down.position
                    if (!relative) memory.point = absolutePoint(previous.x, previous.y, canvas)
                    localPoint = down.position
                    visible = true
                    currentActions.move(memory.point.x, memory.point.y)
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed || change.isConsumed) break
                            val position = change.position
                            memory.point = if (relative) relativePoint(memory.point, position.x - previous.x, position.y - previous.y, size.width.toFloat(), size.height.toFloat(), sensitivity)
                                else absolutePoint(position.x, position.y, canvas)
                            previous = position
                            localPoint = position
                            currentActions.move(memory.point.x, memory.point.y)
                            change.consume()
                        }
                    } finally { visible = false; currentActions.hide() }
                }
            } finally { visible = false; currentActions.hide() }
        }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val canvas = fitAspect(size.width, size.height, if (relative) null else aspect)
            if (!relative) {
                drawRect(outline.copy(alpha = .12f), Offset(canvas.x, canvas.y), androidx.compose.ui.geometry.Size(canvas.width, canvas.height))
                drawLine(outline.copy(alpha = .45f), Offset(canvas.x, size.height / 2), Offset(canvas.x + canvas.width, size.height / 2), 1.dp.toPx())
                drawLine(outline.copy(alpha = .45f), Offset(size.width / 2, canvas.y), Offset(size.width / 2, canvas.y + canvas.height), 1.dp.toPx())
            }
            if (visible) { drawCircle(color.copy(alpha = .16f), 20.dp.toPx(), localPoint); drawCircle(color, 6.dp.toPx(), localPoint) }
        }
        if (!visible) Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(12.dp)) {
            Icon(if (editing) Icons.Default.OpenWith else Icons.Default.TouchApp, null, Modifier.size(30.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .65f))
            Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!relative && enabled) Text(s.t("绝对坐标", "Absolute position"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .7f))
        }
    }
}
