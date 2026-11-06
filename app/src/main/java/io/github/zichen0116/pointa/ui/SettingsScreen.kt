package io.github.zichen0116.pointa.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import io.github.zichen0116.pointa.BuildConfig

@Composable
internal fun SettingsScreen(p: Preferences, update: (Preferences) -> Unit, onBack: () -> Unit, onDevices: () -> Unit) {
    val s = LocalCopy.current
    var reset by remember { mutableStateOf(false) }
    var mappingConflict by remember { mutableStateOf(false) }
    var showLicenses by remember { mutableStateOf(false) }
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        PageHeader(s.t("个性化设置", "Settings"), onBack)
        CardGroup {
            SectionTitle(s.t("主题颜色", "Theme color"))
            val names = listOf(s.t("黑色 · 默认", "Night · Default"), s.t("珊瑚红", "Coral"), s.t("薄荷绿", "Mint"), s.t("天空蓝", "Sky"), s.t("暖金色", "Gold"), s.t("淡紫色", "Lilac"))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                ThemeColors.forEachIndexed { index, color -> ColorSwatch(color, p.theme == index, names[index]) { update(p.copy(theme = index)) } }
            }
            Text(names[p.theme.coerceIn(0, 5)] + s.t(" · 页面强调色，与激光颜色独立", " · Interface accent, separate from the laser"),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Choice(s.t("外观", "Appearance"), p.appearance,
                listOf("dark" to s.t("深色", "Dark"), "light" to s.t("浅色", "Light"), "system" to s.t("跟随系统", "System"))) { update(p.copy(appearance = it)) }
            Choice(s.t("语言", "Language"), p.language,
                listOf("system" to s.t("跟随系统", "System"), "zh" to "简体中文", "en" to "English")) { update(p.copy(language = it)) }
            Toggle(s.t("减少动画", "Reduce motion"), p.reduceMotion, { update(p.copy(reduceMotion = it)) },
                s.t("减少非必要的界面动画，不影响激光效果", "Reduce decorative interface animation; laser effects are separate"))
        }
        CardGroup {
            SectionTitle(s.t("激光效果", "Laser effects"))
            Text(s.t("激光颜色", "Laser color"), style = MaterialTheme.typography.bodyMedium)
            val lasers = listOf("#FF6475", "#32B894", "#4D95ED", "#FFD166", "#B58AE9", "#FFFFFF")
            val laserNames = listOf(s.t("红色", "Red"), s.t("绿色", "Green"), s.t("蓝色", "Blue"), s.t("黄色", "Yellow"), s.t("紫色", "Purple"), s.t("白色", "White"))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                lasers.forEachIndexed { index, color ->
                    ColorSwatch(Color(android.graphics.Color.parseColor(color)), p.laserColor.equals(color, true), laserNames[index]) { update(p.copy(laserColor = color)) }
                }
            }
            Choice(s.t("指针大小", "Pointer size"), p.laserSize,
                listOf("small" to s.t("小", "Small"), "medium" to s.t("中", "Medium"), "large" to s.t("大", "Large"))) { update(p.copy(laserSize = it)) }
            Choice(s.t("平滑级别", "Smoothing"), p.smoothing,
                listOf("low" to s.t("低", "Low"), "standard" to s.t("标准", "Standard"), "high" to s.t("高", "High"))) { update(p.copy(smoothing = it)) }
            Toggle(s.t("渐隐拖尾", "Fading trail"), p.trailEnabled, { update(p.copy(trailEnabled = it)) })
            SettingSlider(s.t("拖尾时间", "Trail duration"), p.trailMs, 100, 1200, 50, "ms", p.trailEnabled) { update(p.copy(trailMs = it)) }
            Text(s.t("连接时实时应用到电脑；拖尾关闭后仍保留时长。", "Applied to the computer while connected. The duration is kept when trails are off."),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        CardGroup {
            SectionTitle(s.t("触控与布局", "Touch and layout"))
            SettingSlider(s.t("控制灵敏度", "Sensitivity"), p.sensitivity, 10, 100, 5, "%") { update(p.copy(sensitivity = it)) }
            Text(s.t("用于相对触控与空中指针；60% 为标准速度。", "Used for relative touch and air pointing. 60% is the standard speed."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Choice(s.t("惯用手", "Preferred hand"), if (p.leftHand) "left" else "right",
                listOf("left" to s.t("左手", "Left"), "right" to s.t("右手", "Right"))) { update(p.copy(leftHand = it == "left")) }
            Choice(s.t("全屏映射", "Full-screen mapping"), if (p.fullRelative) "relative" else "absolute",
                listOf("absolute" to s.t("绝对坐标", "Absolute"), "relative" to s.t("相对位移", "Relative"))) { update(p.copy(fullRelative = it == "relative")) }
            Text(s.t("单手模式始终采用相对位移。返回单手模式，点击铅笔可独立编辑左右手布局。", "One-hand mode always uses relative movement. Use its pencil button to edit and save each hand's layout separately."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        CardGroup {
            Toggle(s.t("音量键翻页", "Volume-key paging"), p.volumeEnabled, { update(p.copy(volumeEnabled = it)) })
            val keys = listOf("0" to s.t("无", "None"), "+" to s.t("音量 +", "Volume +"), "-" to s.t("音量 −", "Volume −"))
            Choice(s.t("上一页", "Previous slide"), p.prevVolume, keys, p.volumeEnabled) { key ->
                mappingConflict = key != "0" && key == p.nextVolume
                update(p.withVolumeMapping(previous = true, key = key))
            }
            Choice(s.t("下一页", "Next slide"), p.nextVolume, keys, p.volumeEnabled) { key ->
                mappingConflict = key != "0" && key == p.prevVolume
                update(p.withVolumeMapping(previous = false, key = key))
            }
            if (mappingConflict) Text(s.t("同一按键不能绑定两个动作，已解除另一项映射。", "A key can only control one action. Its other mapping was cleared."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            Text(s.t("仅在 App 前台的演示页面生效；长按不会连续翻页。", "Works only on the presentation screen while the app is in the foreground. Holding a key does not repeat."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        CardGroup {
            SectionTitle(s.t("演示偏好", "Presentation preferences"))
            Toggle(s.t("保持屏幕常亮", "Keep screen awake"), p.keepScreenOn, { update(p.copy(keepScreenOn = it)) }, s.t("仅在已连接的演示页面生效", "Only during a connected presentation"))
            Toggle(s.t("触觉反馈", "Haptic feedback"), p.haptics, { update(p.copy(haptics = it)) })
            Toggle(s.t("自动重连", "Auto-reconnect"), p.autoReconnect, { update(p.copy(autoReconnect = it)) }, s.t("前台启动时连接最近信任的电脑", "Reconnect to the last trusted computer on foreground launch"))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .35f))
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClick = onDevices), verticalAlignment = Alignment.CenterVertically) {
                Text(s.t("设备与信任管理", "Computers and trust"), Modifier.weight(1f))
                Icon(Icons.Default.ChevronRight, null)
            }
        }
        CardGroup {
            SectionTitle("映点 Pointa · ${BuildConfig.VERSION_NAME}")
            Text(s.t("让手机成为你的演示伙伴。\n指示和翻页相互独立，激光不会移动电脑鼠标。", "Your phone, ready to present.\nLaser pointing and slide navigation are independent; the computer mouse stays where it is."), style = MaterialTheme.typography.bodyMedium)
            Text(s.t("采用 Kotlin、AndroidX、OkHttp、Bouncy Castle 与 ZXing。\n空中指针使用旋转传感器；按住激活，松手隐藏，可随时归零。", "Built with Kotlin, AndroidX, OkHttp, Bouncy Castle and ZXing.\nAir pointer uses a rotation sensor. Hold to activate, release to hide, and recenter anytime."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = { showLicenses = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(s.t("开源许可", "Open-source licenses")) }
            OutlinedButton(onClick = { reset = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(s.t("恢复默认设置", "Reset preferences")) }
        }
        Spacer(Modifier.height(8.dp))
    }
    if (reset) AlertDialog(onDismissRequest = { reset = false }, title = { Text(s.t("恢复默认设置？", "Reset preferences?")) },
        text = { Text(s.t("主题、激光、按键与左右手布局将恢复默认。已配对电脑会保留。", "Theme, laser, keys and both hand layouts will reset. Paired computers will be kept.")) },
        confirmButton = { TextButton(onClick = { update(Preferences(onboardingDone = true)); reset = false }) { Text(s.t("恢复默认", "Reset")) } },
        dismissButton = { TextButton(onClick = { reset = false }) { Text(s.t("取消", "Cancel")) } })
    if (showLicenses) {
        val notices = remember { context.assets.list("licenses").orEmpty().sorted().joinToString("\n\n") { name ->
            val raw = context.assets.open("licenses/$name").bufferedReader().use { it.readText() }
            "$name\n" + if (name.endsWith(".html")) android.text.Html.fromHtml(raw, 0).toString() else raw
        } }
        AlertDialog(onDismissRequest = { showLicenses = false }, title = { Text(s.t("开源许可", "Open-source licenses")) },
            text = { Text(notices, Modifier.heightIn(max = 450.dp).verticalScroll(rememberScrollState()), style = MaterialTheme.typography.bodySmall) },
            confirmButton = { TextButton(onClick = { showLicenses = false }) { Text(s.t("关闭", "Close")) } })
    }
}

@Composable
private fun SectionTitle(text: String) { Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }

@Composable
private fun ColorSwatch(color: Color, chosen: Boolean, label: String, onClick: () -> Unit) {
    Box(Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.RadioButton, onClick = onClick)
        .semantics { contentDescription = label; selected = chosen }, contentAlignment = Alignment.Center) {
        Box(Modifier.size(34.dp).background(color, CircleShape).border(if (chosen) 3.dp else 1.dp,
            if (chosen) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline, CircleShape), contentAlignment = Alignment.Center) {
            if (chosen) Icon(Icons.Default.Check, null, Modifier.size(18.dp), tint = if (color == ThemeColors[0]) Color.White else Color(0xFF111722))
        }
    }
}

@Composable
private fun Toggle(label: String, value: Boolean, change: (Boolean) -> Unit, help: String? = null) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).toggleable(value = value, role = Role.Switch, onValueChange = change),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label)
            if (help != null) Text(help, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = value, onCheckedChange = null)
    }
}

@Composable
private fun Choice(label: String, selected: String, choices: List<Pair<String, String>>, enabled: Boolean = true, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(enabled = enabled) { expanded = true },
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(label, Modifier.weight(1f), color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = .4f))
            Text(choices.firstOrNull { it.first == selected }?.second ?: selected, Modifier.weight(1f),
                color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = .4f))
            Icon(Icons.Default.ChevronRight, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        DropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false }) {
            choices.forEach { (key, text) -> DropdownMenuItem(text = { Text(text) }, onClick = { onSelect(key); expanded = false },
                trailingIcon = if (key == selected) { { Icon(Icons.Default.Check, null) } } else null) }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun SettingSlider(label: String, value: Int, min: Int, max: Int, step: Int, unit: String, enabled: Boolean = true, change: (Int) -> Unit) {
    val active = MaterialTheme.colorScheme.primary.copy(alpha = if (enabled) 1f else .35f)
    val inactive = MaterialTheme.colorScheme.outline.copy(alpha = if (enabled) .8f else .3f)
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Text("$value${if (unit == "%") "" else " "}$unit", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        }
        Slider(value = value.coerceIn(min, max).toFloat(), onValueChange = { change((min + ((it - min) / step).roundToInt() * step).coerceIn(min, max)) },
            valueRange = min.toFloat()..max.toFloat(), steps = (max - min) / step - 1, enabled = enabled,
            thumb = { Box(Modifier.size(16.dp).background(active, CircleShape)) },
            track = {
                Canvas(Modifier.fillMaxWidth().height(5.dp)) {
                    val radius = androidx.compose.ui.geometry.CornerRadius(size.height / 2)
                    drawRoundRect(inactive, cornerRadius = radius)
                    drawRoundRect(active, size = androidx.compose.ui.geometry.Size(size.width * ((value - min).toFloat() / (max - min)).coerceIn(0f, 1f), size.height), cornerRadius = radius)
                }
            },
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = label })
    }
}
