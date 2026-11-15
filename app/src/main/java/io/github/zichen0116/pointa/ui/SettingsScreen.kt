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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
internal fun SettingsScreen(p: Preferences, update: (Preferences) -> Unit, onBack: () -> Unit, onDevices: () -> Unit) {
    val s = LocalCopy.current
    var reset by remember { mutableStateOf(false) }
    var mappingConflict by remember { mutableStateOf(false) }
    val links = LocalUriHandler.current
    Column(Modifier.fillMaxSize().testTag("settings-page").padding(horizontal = 16.dp)) {
        PageHeader(s.t("个性化设置", "Settings"), onBack)
        Column(Modifier.weight(1f).fillMaxWidth().testTag("settings-scroll").verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            CardGroup {
                SectionTitle(s.t("主题颜色", "Theme color"))
                val names = listOf(s.t("黑色 · 默认", "Night · Default"), s.t("珊瑚红", "Coral"), s.t("薄荷绿", "Mint"), s.t("天空蓝", "Sky"), s.t("暖金色", "Gold"), s.t("淡紫色", "Lilac"))
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    ThemeColors.forEachIndexed { index, color -> ColorSwatch(color, p.theme == index, names[index]) { update(p.copy(theme = index)) } }
                }
                Choice(s.t("外观", "Appearance"), p.appearance,
                    listOf("dark" to s.t("深色", "Dark"), "light" to s.t("浅色", "Light"), "system" to s.t("跟随系统", "System"))) { update(p.copy(appearance = it)) }
                Choice(s.t("语言", "Language"), p.language,
                    listOf("system" to s.t("跟随系统", "System"), "zh" to "简体中文", "en" to "English")) { update(p.copy(language = it)) }
                Toggle(s.t("减少动画", "Reduce motion"), p.reduceMotion, { update(p.copy(reduceMotion = it)) })
            }
            CardGroup {
                SectionTitle(s.t("激光效果", "Laser effects"))
                Choice(s.t("指针大小", "Pointer size"), p.laserSize,
                    listOf("small" to s.t("小", "Small"), "medium" to s.t("中", "Medium"), "large" to s.t("大", "Large"))) { update(p.copy(laserSize = it)) }
                Choice(s.t("平滑级别", "Smoothing"), p.smoothing,
                    listOf("low" to s.t("低", "Low"), "standard" to s.t("标准", "Standard"), "high" to s.t("高", "High"))) { update(p.copy(smoothing = it)) }
                Toggle(s.t("渐隐拖尾", "Fading trail"), p.trailEnabled, { update(p.copy(trailEnabled = it)) })
                SettingSlider(s.t("拖尾时间", "Trail duration"), p.trailMs, 100, 1200, 50, "ms", p.trailEnabled) { update(p.copy(trailMs = it)) }
            }
            CardGroup {
                SectionTitle(s.t("触控与布局", "Touch and layout"))
                SettingSlider(s.t("控制灵敏度", "Sensitivity"), p.sensitivity, 10, 100, 5, "%") { update(p.copy(sensitivity = it)) }
                Choice(s.t("惯用手", "Preferred hand"), if (p.leftHand) "left" else "right",
                    listOf("left" to s.t("左手", "Left"), "right" to s.t("右手", "Right"))) { update(p.copy(leftHand = it == "left")) }
                Choice(s.t("全屏映射", "Full-screen mapping"), if (p.fullRelative) "relative" else "absolute",
                    listOf("absolute" to s.t("绝对坐标", "Absolute"), "relative" to s.t("相对位移", "Relative"))) { update(p.copy(fullRelative = it == "relative")) }
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
            }
            CardGroup {
                SectionTitle(s.t("演示偏好", "Presentation preferences"))
                Toggle(s.t("保持屏幕常亮", "Keep screen awake"), p.keepScreenOn, { update(p.copy(keepScreenOn = it)) })
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .35f))
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClick = onDevices), verticalAlignment = Alignment.CenterVertically) {
                    Text(s.t("设备与信任管理", "Computers and trust"), Modifier.weight(1f))
                    Icon(Icons.Default.ChevronRight, null)
                }
                OutlinedButton(onClick = { reset = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(s.t("恢复默认设置", "Reset preferences")) }
            }
            CardGroup {
                Text(s.t("欢迎提交 PR 和 issue", "PRs and issues are welcome"))
                TextButton(onClick = { links.openUri("https://github.com/zichen0116/pointa-android") }) { Text("GitHub · Pointa") }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
    if (reset) AlertDialog(onDismissRequest = { reset = false }, title = { Text(s.t("恢复默认设置？", "Reset preferences?")) },
        text = { Text(s.t("主题、激光、按键与左右手布局将恢复默认。已配对电脑会保留。", "Theme, laser, keys and both hand layouts will reset. Paired computers will be kept.")) },
        confirmButton = { TextButton(onClick = { update(Preferences(onboardingDone = true)); reset = false }) { Text(s.t("恢复默认", "Reset")) } },
        dismissButton = { TextButton(onClick = { reset = false }) { Text(s.t("取消", "Cancel")) } })
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
private fun Toggle(label: String, value: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).toggleable(value = value, role = Role.Switch, onValueChange = change),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, Modifier.weight(1f))
        Switch(checked = value, onCheckedChange = null)
    }
}

@Composable
private fun Choice(label: String, selected: String, choices: List<Pair<String, String>>, enabled: Boolean = true, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(enabled = enabled) { expanded = true },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, Modifier.weight(1f), color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = .4f))
        Box(Modifier.weight(1f).wrapContentWidth(Alignment.End)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(choices.firstOrNull { it.first == selected }?.second ?: selected, textAlign = TextAlign.End,
                    color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = .4f))
                Icon(Icons.Default.ChevronRight, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            DropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false }, modifier = Modifier.widthIn(min = 180.dp)) {
                choices.forEach { (key, text) -> DropdownMenuItem(text = { Text(text) }, onClick = { onSelect(key); expanded = false },
                    trailingIcon = if (key == selected) { { Icon(Icons.Default.Check, null) } } else null) }
            }
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
