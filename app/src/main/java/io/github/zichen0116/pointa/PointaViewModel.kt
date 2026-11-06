package io.github.zichen0116.pointa

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.zichen0116.pointa.protocol.*
import io.github.zichen0116.pointa.ui.Device
import io.github.zichen0116.pointa.ui.Preferences
import io.github.zichen0116.pointa.ui.UiState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.DateFormat
import java.util.Date
import java.util.Locale

class PointaViewModel(application: Application) : AndroidViewModel(application), PointaListener {
    private val main = Handler(Looper.getMainLooper())
    private val preferenceStore = PreferenceStore(application)
    private val trust = SecureDeviceStore(application)
    private val mutablePreferences = MutableStateFlow(Preferences())
    val preferences = mutablePreferences.asStateFlow()
    private val mutableUi = MutableStateFlow(UiState())
    val ui = mutableUi.asStateFlow()
    private val client = PointaClient((Build.MANUFACTURER + " " + Build.MODEL).take(64), this)
    private val air = AirPointerController(application,
        move = { x, y -> if (canControl() && preferences.value.mode == "air") client.move(x, y) },
        hide = client::hide, changed = { mutableUi.value = mutableUi.value.copy(airActive = it) },
        unavailable = { localError("sensor_unavailable") })
    private var devices = emptyList<KnownComputer>()
    private var selectedId: String? = null
    private var currentComputer: KnownComputer? = null
    private var discoveredComputers = emptyList<DiscoveredComputer>()
    private var foreground = false
    private var wantsConnection = true
    private var controlActive = false
    private var initialized = false
    internal val ready: Boolean get() = initialized
    private var storageHealthy = true
    private var preferenceHealthy = true
    private var disposed = false
    private var retry: Job? = null
    private var retryCount = 0
    private var stateRevision = 0L
    private var persistPairing: Job? = null
    private var preferenceSave: Job? = null
    private var effectUpdate: Job? = null
    private val connectivity = application.getSystemService(ConnectivityManager::class.java)
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = post { if (initialized) resumeIfNeeded() }
    }

    init {
        mutableUi.value = mutableUi.value.copy(airAvailable = air.available)
        viewModelScope.launch {
            try { mutablePreferences.value = withContext(Dispatchers.IO) { preferenceStore.load() } }
            catch (_: Exception) { preferenceHealthy = false; localError("preferences_corrupt") }
            try { devices = withContext(Dispatchers.IO) { trust.load() }; updateDevices() }
            catch (_: Exception) { storageHealthy = false; localError("storage_corrupt") }
            initialized = true
            resumeIfNeeded()
        }
        connectivity.registerDefaultNetworkCallback(networkCallback)
    }

    fun t(chinese: String, english: String): String {
        val language = mutablePreferences.value.language
        return if (language == "zh" || (language == "system" && Locale.getDefault().language == "zh")) chinese else english
    }

    fun setForeground(active: Boolean) {
        foreground = active
        if (active) resumeIfNeeded()
        else { retry?.cancel(); hide(); client.disconnect(); mutableUi.value = mutableUi.value.copy(connected = false, connecting = false) }
    }

    fun setControlActive(active: Boolean) { controlActive = active; if (!active) hide() }
    fun setDisplayRotation(rotation: Int) { air.rotation = rotation }
    fun airHold(held: Boolean) {
        if (held && canControl() && preferences.value.mode == "air") { air.sensitivity = preferences.value.sensitivity; air.start() }
        else air.stop()
    }
    fun calibrateAir() = air.calibrate()
    fun setScanning(scanning: Boolean) {
        if (scanning) hide()
        mutableUi.value = mutableUi.value.copy(scanning = scanning)
    }

    fun connectQr(raw: String) {
        if (!storageHealthy) { localError("storage_corrupt"); return }
        try {
            val invitation = QrInvitation.parse(raw)
            retry?.cancel(); retryCount = 0; selectedId = null; wantsConnection = true
            mutableUi.value = mutableUi.value.copy(scanning = false, statusError = null)
            client.connectQr(invitation)
        } catch (_: IllegalArgumentException) { setScanning(false); localError("invalid_qr") }
    }

    fun pairCode(host: String, code: String) {
        if (!storageHealthy) { localError("storage_corrupt"); return }
        retry?.cancel(); retryCount = 0; selectedId = null; wantsConnection = true
        mutableUi.value = mutableUi.value.copy(statusError = null)
        try {
            val discovered = discoveredComputers.firstOrNull { it.host == host.trim() }
            if (discovered == null) client.pairCode(host.trim(), code) else client.pairCode(discovered, code)
        }
        catch (_: IllegalArgumentException) { localError("invalid_pairing_input") }
    }

    fun reconnect(id: String) {
        val device = devices.firstOrNull { it.serverId == id } ?: return
        retry?.cancel(); retryCount = 0; selectedId = id; wantsConnection = true
        currentComputer = device
        mutableUi.value = mutableUi.value.copy(statusError = null)
        client.reconnect(device)
    }

    fun disconnect() {
        wantsConnection = false; retry?.cancel(); hide(); client.disconnect()
        mutableUi.value = mutableUi.value.copy(connected = false, connecting = false)
    }

    fun move(x: Float, y: Float) { if (canControl()) client.move(x.toDouble(), y.toDouble()) }
    fun hide() { air.stop(); client.hide() }
    fun page(next: Boolean): Boolean {
        if (!canControl()) return false
        client.page(next)
        return true
    }

    fun volumeAction(key: String): Boolean? {
        val p = preferences.value
        if (!p.volumeEnabled || !canControl()) return null
        return when (key) { p.nextVolume -> true; p.prevVolume -> false; else -> null }
    }

    private fun canControl() = foreground && controlActive && mutableUi.value.connected && !mutableUi.value.scanning

    fun discover() {
        if (mutableUi.value.discovering) return
        mutableUi.value = mutableUi.value.copy(discovering = true, statusError = null)
        viewModelScope.launch {
            try {
                val found = withContext(Dispatchers.IO) { discoverLan() }
                discoveredComputers = found
                mutableUi.value = mutableUi.value.copy(discoveredDevices = found.map { Device(it.serverId, it.name, it.host) },
                    statusError = if (found.isEmpty()) t("未发现电脑，可手动输入局域网 IP。", "No computer found. Enter its local IP address.") else null)
            } catch (_: Exception) { localError("discovery_failed") }
            finally { mutableUi.value = mutableUi.value.copy(discovering = false) }
        }
    }

    private fun discoverLan(): List<DiscoveredComputer> {
        val wifi = getApplication<Application>().getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val lock = wifi?.createMulticastLock("pointa-discovery")?.apply { setReferenceCounted(false); acquire() }
        return try { LanDiscovery.discover() } finally { if (lock?.isHeld == true) lock.release() }
    }

    fun forget(id: String) {
        if (selectedId == id) {
            air.stop()
            wantsConnection = false; retry?.cancel()
            if (ui.value.connected) client.forget() else client.disconnect()
            selectedId = null
            mutableUi.value = mutableUi.value.copy(connected = false, connecting = false)
        }
        viewModelScope.launch {
            try {
                devices = withContext(Dispatchers.IO) { trust.forget(id) }; updateDevices()
                localMessage(t("已在手机移除。若电脑未连接，请在电脑端撤销授权。", "Forgotten on this phone. If offline, revoke access on the computer too."))
            } catch (_: Exception) { localError("storage_failed") }
        }
    }

    fun changePreferences(next: Preferences) {
        if (!preferenceHealthy) { localError("preferences_corrupt"); return }
        val old = mutablePreferences.value
        if (old.mode != next.mode) air.stop()
        mutablePreferences.value = next
        preferenceSave?.cancel()
        preferenceSave = viewModelScope.launch {
            delay(200)
            try { withContext(Dispatchers.IO) { preferenceStore.save(mutablePreferences.value) } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { localError("preferences_failed") }
        }
        if (!next.autoReconnect) retry?.cancel()
        if (effect(old) != effect(next) && ui.value.connected) {
            effectUpdate?.cancel()
            effectUpdate = viewModelScope.launch { delay(300); if (ui.value.connected) client.updateEffect(effect(preferences.value)) }
        }
    }

    private fun effect(p: Preferences) = LaserEffect(p.laserColor, when (p.laserSize) { "small" -> 10; "large" -> 24; else -> 16 },
        if (p.trailEnabled) p.trailMs else 0, p.smoothing)

    override fun onPaired(computer: KnownComputer) = post {
        persistPairing = viewModelScope.launch {
            try { devices = withContext(Dispatchers.IO) { trust.remember(computer) }; selectedId = computer.serverId; currentComputer = computer; updateDevices() }
            catch (_: Exception) { storageHealthy = false; wantsConnection = false; client.disconnect(); localError("storage_failed") }
        }
    }

    override fun onState(state: ConnectionState) = post {
        val revision = ++stateRevision
        if (state.phase != ConnectionPhase.CONNECTED) air.stop()
        if (!storageHealthy) { mutableUi.value = mutableUi.value.copy(connected = false, connecting = false); return@post }
        if (state.phase in setOf(ConnectionPhase.CONNECTING, ConnectionPhase.PAIRING, ConnectionPhase.WAITING_APPROVAL)) retry?.cancel()
        if (state.phase == ConnectionPhase.CONNECTED) {
            viewModelScope.launch {
                persistPairing?.join()
                if (revision != stateRevision) return@launch
                if (!storageHealthy || !foreground || !wantsConnection) { client.disconnect(); return@launch }
                retry?.cancel(); retryCount = 0
                mutableUi.value = mutableUi.value.copy(connected = true, connecting = false, deviceName = state.name, connectedDeviceId = selectedId,
                    statusText = t("已连接", "Connected"), statusError = null)
                val computer = currentComputer?.takeIf { it.serverId == selectedId }
                if (computer != null) try {
                    devices = withContext(Dispatchers.IO) { trust.touch(computer.copy(name = state.name, lastConnectedAt = System.currentTimeMillis())) }; updateDevices()
                } catch (_: Exception) { localError("storage_failed") }
            }
        } else {
            mutableUi.value = mutableUi.value.copy(connected = false,
                connecting = state.phase in setOf(ConnectionPhase.CONNECTING, ConnectionPhase.PAIRING, ConnectionPhase.WAITING_APPROVAL),
                deviceName = state.name.ifBlank { ui.value.deviceName },
                statusText = when (state.phase) {
                    ConnectionPhase.CONNECTING -> t("正在连接电脑…", "Connecting…")
                    ConnectionPhase.PAIRING -> t("正在安全配对…", "Securing connection…")
                    ConnectionPhase.WAITING_APPROVAL -> t("请在电脑上允许配对", "Allow pairing on your computer")
                    else -> t("未连接", "Disconnected")
                }, statusError = state.error?.let(::errorText))
            if (state.phase in setOf(ConnectionPhase.FAILED, ConnectionPhase.DISCONNECTED)) scheduleRetry(state.error)
        }
    }

    override fun onActionResult(id: String, success: Boolean, error: String?) = post {
        if (!success) localError(error ?: "action_failed")
        else mutableUi.value = mutableUi.value.copy(statusError = null)
    }

    override fun onEffect(effect: LaserEffect) = post {
        val p = preferences.value
        val next = p.copy(laserColor = effect.color, laserSize = when { effect.size <= 12 -> "small"; effect.size >= 22 -> "large"; else -> "medium" },
            trailEnabled = effect.trailMs > 0, trailMs = if (effect.trailMs > 0) effect.trailMs.coerceIn(100, 1200) else p.trailMs, smoothing = effect.smoothing)
        mutablePreferences.value = next
        preferenceSave?.cancel()
        preferenceSave = viewModelScope.launch {
            delay(200)
            try { if (preferenceHealthy) withContext(Dispatchers.IO) { preferenceStore.save(preferences.value) } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { localError("preferences_failed") }
        }
    }
    override fun onDisplay(display: DisplayInfo?) = post {
        mutableUi.value = mutableUi.value.copy(displayAspect = display?.takeIf { it.width > 0 && it.height > 0 }?.let { it.width.toFloat() / it.height })
    }
    override fun onEffectRejected(error: String) = post { localError(error) }

    private fun resumeIfNeeded() {
        if (!initialized || !storageHealthy || !foreground || !wantsConnection || !preferences.value.autoReconnect || ui.value.connected || ui.value.connecting || retry?.isActive == true) return
        val computer = devices.firstOrNull { it.serverId == selectedId } ?: devices.firstOrNull() ?: return
        selectedId = computer.serverId
        currentComputer = computer
        client.reconnect(computer)
    }

    private fun scheduleRetry(error: String?) {
        val recoverable = error == null || error in setOf("network_error", "network_closed", "network_timeout", "network_slow")
        if (!recoverable) { wantsConnection = false; return }
        if (!foreground || !wantsConnection || !preferences.value.autoReconnect || selectedId == null || retry?.isActive == true) return
        retry = viewModelScope.launch {
            delay((1L shl retryCount.coerceAtMost(4)).coerceAtMost(15) * 1000)
            if (ui.value.connected || ui.value.connecting) return@launch
            retryCount++
            val computer = devices.firstOrNull { it.serverId == selectedId } ?: return@launch
            val discovered = try { withContext(Dispatchers.IO) { discoverLan().firstOrNull { it.serverId == computer.serverId } } }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { null }
            if (foreground && wantsConnection && preferences.value.autoReconnect && selectedId == computer.serverId && !ui.value.connected && !ui.value.connecting) {
                val updated = if (discovered != null) computer.copy(host = discovered.host, port = discovered.port) else computer
                currentComputer = updated
                client.reconnect(updated)
            }
        }
    }

    private fun updateDevices() {
        mutableUi.value = mutableUi.value.copy(recentDevices = devices.map {
            Device(it.serverId, it.name, it.host, DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it.lastConnectedAt)))
        })
    }

    fun localError(code: String) = localMessage(errorText(code))
    private fun localMessage(message: String) { mutableUi.value = mutableUi.value.copy(statusError = message) }
    private fun errorText(code: String): String = when {
        code == "sensor_unavailable" -> t("无法读取旋转传感器。请检查传感器开关，或使用触控模式。", "Rotation sensor unavailable. Check sensor access or use touch mode.")
        code in setOf("invalid_qr", "invalid_pairing_input") -> t("配对信息无效，请扫描映点二维码或输入局域网 IP 和六位码。", "Invalid pairing details. Use a Pointa QR or local IP and six-digit code.")
        code.contains("certificate") || code.contains("identity") || code.contains("pin") -> t("电脑身份验证失败，请在电脑上刷新二维码并重新配对。", "Computer identity could not be verified. Pair again with its new QR code.")
        code in setOf("pairing_expired", "invalid_proof", "invalid_pairing", "pairing_denied", "code_rate_limited") -> t("配对未通过。请刷新电脑端设备码，核对后重新连接并允许配对。", "Pairing failed. Refresh the computer's code and approve a new pairing.")
        code in setOf("invalid_credential", "credential_revoked") -> t("电脑已撤销授权，请重新配对。", "Access was revoked. Pair with the computer again.")
        code == "controller_busy" -> t("电脑正由另一台手机控制，请先在电脑端断开它。", "Another phone is controlling this computer. Disconnect it on Windows first.")
        code.contains("storage") -> t("设备记录无法安全读取或保存，原记录已保留。请检查存储；密钥损坏时在系统清除应用数据后重新配对。", "Trusted devices cannot be read or saved securely. Records were preserved. Check storage; reset app data and pair again if the key is lost.")
        code.contains("preferences") -> t("设置无法读取或保存，原文件已保留。请检查存储或清除应用数据后重试。", "Settings could not be read or saved. The original was preserved. Check storage or reset app data.")
        code.contains("focus") || code.contains("target") || code == "no_foreground_window" || code == "input_blocked" -> t("翻页未执行：请将演示置前台；浏览器/PDF 需在电脑托盘菜单中授权窗口。", "Slide change was blocked. Focus your presentation; allow browser/PDF windows from the Windows tray.")
        code.contains("cooldown") || code == "keyboard_busy" -> t("操作过快或电脑按键正被占用，请稍后再试。", "Please slow down or release the computer's keys.")
        code == "camera_denied" -> t("未获得相机权限，仍可用设备码连接。", "Camera access was denied. You can still connect with a pairing code.")
        code == "camera_failed" -> t("相机暂不可用，请重试或使用设备码。", "Camera unavailable. Retry or use a pairing code.")
        else -> t("连接中断或不可达。请检查同一局域网、防火墙与电脑网络设置。", "Connection lost or unavailable. Check the local network, firewall and Windows network settings.")
    }

    private fun post(action: () -> Unit) { if (!disposed) main.post { if (!disposed) action() } }
    override fun onCleared() {
        disposed = true
        air.stop()
        connectivity.unregisterNetworkCallback(networkCallback)
        client.close()
        super.onCleared()
    }
}
