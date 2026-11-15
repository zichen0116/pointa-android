package io.github.zichen0116.pointa.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.zichen0116.pointa.protocol.isLanAddress

@Composable
internal fun PairingCodeScreen(state: UiState, actions: UiActions, onBack: () -> Unit) {
    val s = LocalCopy.current
    var parts by rememberSaveable { mutableStateOf(listOf("", "", "", "")) }
    var code by rememberSaveable { mutableStateOf("") }
    var submitted by rememberSaveable { mutableStateOf(false) }
    val focus = remember { List(5) { FocusRequester() } }
    val keyboard = LocalSoftwareKeyboardController.current
    val host = parts.joinToString(".")
    val validHost = parts.all { it.isNotEmpty() && it.toIntOrNull() in 0..255 } && isLanAddress(host)
    fun submit(value: String) {
        if (!validHost || value.length != 6 || state.connecting) return
        submitted = true
        keyboard?.hide()
        actions.pairCode(host, value)
    }
    LaunchedEffect(Unit) { focus[0].requestFocus() }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        PageHeader(s.t("设备码连接", "Connect with a code"), onBack)
        if (state.discoveredDevices.isNotEmpty()) {
            Text(s.t("附近电脑", "Nearby computers"), style = MaterialTheme.typography.labelLarge)
            state.discoveredDevices.forEach { device ->
                OutlinedButton(onClick = {
                    parts = device.host.split('.'); code = ""; submitted = false; focus[4].requestFocus()
                }, enabled = !state.connecting, modifier = Modifier.fillMaxWidth()) { Text("${device.name} · ${device.host}") }
            }
        }
        Text(s.t("电脑 IP", "Computer IP"), style = MaterialTheme.typography.titleSmall)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            repeat(4) { index ->
                if (index > 0) Text(".", Modifier.padding(horizontal = 4.dp), style = MaterialTheme.typography.titleLarge)
                OutlinedTextField(value = parts[index], onValueChange = { raw ->
                    val pasted = raw.trim().split('.')
                    if (pasted.size == 4 && pasted.all { it.isNotEmpty() && it.all(Char::isDigit) && it.toIntOrNull() in 0..255 }) {
                        parts = pasted.map { it.toInt().toString() }; code = ""; submitted = false; focus[4].requestFocus()
                    } else {
                        val digits = raw.filter { it in '0'..'9' }.take(3)
                        if (digits.isEmpty() || digits.toInt() <= 255) {
                            parts = parts.toMutableList().also { it[index] = digits }; code = ""; submitted = false
                            if (digits.length == 3 || digits.isNotEmpty() && raw.endsWith('.')) focus[index + 1].requestFocus()
                        }
                    }
                }, enabled = !state.connecting, singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(textAlign = TextAlign.Center),
                    modifier = Modifier.weight(1f).focusRequester(focus[index]).semantics { contentDescription = s.t("IP 第 ${index + 1} 段", "IP part ${index + 1}") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focus[index + 1].requestFocus() }))
            }
        }
        Text(s.t("6 位设备码", "6-digit pairing code"), style = MaterialTheme.typography.titleSmall)
        BasicTextField(value = code, onValueChange = { raw ->
            val next = raw.filter { it in '0'..'9' }.take(6)
            val changed = next != code
            code = next
            if (changed && next.length == 6) submit(next)
        }, enabled = !state.connecting, singleLine = true,
            textStyle = MaterialTheme.typography.titleLarge.copy(color = Color.Transparent), cursorBrush = SolidColor(Color.Transparent),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit(code) }),
            modifier = Modifier.fillMaxWidth().focusRequester(focus[4]).semantics { contentDescription = s.t("6 位设备码", "6-digit pairing code") },
            decorationBox = { input ->
                Box {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        repeat(6) { index ->
                            Box(Modifier.weight(1f).height(56.dp).border(1.dp,
                                if (index == code.length) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                                Text(code.getOrNull(index)?.toString() ?: "", style = MaterialTheme.typography.titleLarge)
                            }
                        }
                    }
                    Box(Modifier.matchParentSize().alpha(0f)) { input() }
                }
            })
        if (state.connecting) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            Text(state.statusText, style = MaterialTheme.typography.bodyMedium)
        } else if (submitted) TextButton(onClick = { submit(code) }, enabled = validHost && code.length == 6) { Text(s.t("重新连接", "Try again")) }
    }
}
