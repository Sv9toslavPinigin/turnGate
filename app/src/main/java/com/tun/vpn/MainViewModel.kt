package com.tun.vpn

import android.app.Application
import android.util.Base64
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.BufferedReader
import java.io.StringReader

/**
 * ViewModel для главного экрана.
 * Управляет профилями, подключением (proxy + VPN) и капчей.
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    val profileStore = ProfileStore(application)
    private val proxyManager = TurnProxyManager(application)
    private val backend = GoBackend(application)
    private var tunnel: WgTunnel? = null

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _showCaptchaDialog = MutableStateFlow(false)
    val showCaptchaDialog: StateFlow<Boolean> = _showCaptchaDialog.asStateFlow()

    private val _updateInfo = MutableStateFlow<UpdateInfo?>(null)
    val updateInfo: StateFlow<UpdateInfo?> = _updateInfo.asStateFlow()

    /**
     * Порядковый номер текущей manual-captcha в сессии. Показывается юзеру
     * как «Captcha #3» чтобы было понятно что их может быть несколько.
     * Сбрасывается при connect()/disconnect().
     */
    private val _captchaCount = MutableStateFlow(0)
    val captchaCount: StateFlow<Int> = _captchaCount.asStateFlow()

    init {
        refreshProfileState()
        checkForUpdates()

        // Реактивно обновляем status-bar notification (issue #2).
        viewModelScope.launch {
            var lastStatus: ConnectionState? = null
            var lastProfileName: String? = null
            _state.collect { st ->
                val profileName = st.profiles.find { it.id == st.selectedProfileId }?.name
                if (st.status != lastStatus || profileName != lastProfileName) {
                    TunVpnService.updateState(st.status, profileName)
                    lastStatus = st.status
                    lastProfileName = profileName
                }
            }
        }

        // Собираем логи прокси
        viewModelScope.launch {
            proxyManager.logs.collect { logs ->
                _state.value = _state.value.copy(proxyLogs = logs)
            }
        }

        // Собираем события капчи
        viewModelScope.launch {
            proxyManager.captchaEvent.collect { event ->
                when (event) {
                    is CaptchaEvent.ManualCaptchaRequired -> {
                        _captchaCount.value = _captchaCount.value + 1
                        val connected = _state.value.status == ConnectionState.CONNECTED
                        _showCaptchaDialog.value = true
                        if (!connected) {
                            _state.value = _state.value.copy(
                                statusText = Strings.current.stCaptchaWaiting.format(_captchaCount.value)
                            )
                        }
                        LogStore.addFriendlyLog(
                            Strings.current.fVkCaptcha.format(_captchaCount.value),
                            LogLevel.WARNING
                        )
                    }
                    is CaptchaEvent.CaptchaSolved -> {
                        _showCaptchaDialog.value = false
                        if (_state.value.status != ConnectionState.CONNECTED) {
                            _state.value = _state.value.copy(statusText = Strings.current.stCaptchaSolved)
                        }
                        LogStore.addFriendlyLog(Strings.current.fCaptchaPassed)
                    }
                    is CaptchaEvent.CaptchaFailed -> {
                        _showCaptchaDialog.value = false
                    }
                }
            }
        }

        // Собираем stage события — обновляем statusText пока не CONNECTED
        viewModelScope.launch {
            proxyManager.stage.collect { stage ->
                if (_state.value.status == ConnectionState.CONNECTED) return@collect
                val s = Strings.current
                val (text, friendly) = when (stage) {
                    is ProxyStage.Starting ->
                        s.stStartingProxy to s.fStartingTurnGate
                    is ProxyStage.AuthConnecting ->
                        s.stAuthenticating to s.fReachingVk
                    is ProxyStage.SolvingCaptcha ->
                        s.stCaptchaSolving to s.fSolvingCaptcha
                    is ProxyStage.CaptchaSolved ->
                        s.stCaptchaSolved to s.fCaptchaPassedShort
                    is ProxyStage.IdentityRegistered ->
                        s.stRegisteringIdentity.format(stage.current, stage.total) to
                            s.fAuthenticating.format(stage.current, stage.total)
                    is ProxyStage.DtlsEstablished ->
                        s.stDtls to s.fSecureChannel
                    is ProxyStage.TurnAllocated ->
                        s.stTurnAllocated to s.fOpeningTunnel
                }
                _state.value = _state.value.copy(statusText = text)
                LogStore.addFriendlyLog(friendly)
            }
        }
    }

    fun refreshProfileState() {
        _state.value = _state.value.copy(
            profiles = profileStore.profiles,
            selectedProfileId = profileStore.selectedProfileId
        )
    }

    fun selectProfile(id: String) {
        profileStore.selectedProfileId = id
        refreshProfileState()
    }

    fun addEmptyProfile(): VpnProfile {
        val profile = profileStore.addProfile(VpnProfile())
        refreshProfileState()
        return profile
    }

    fun updateProfile(profile: VpnProfile) {
        profileStore.updateProfile(profile)
        refreshProfileState()
    }

    fun deleteProfile(id: String) {
        profileStore.deleteProfile(id)
        refreshProfileState()
    }

    /**
     * Импорт конфига из turnbridge://BASE64 строки.
     * Создаёт новый профиль.
     */
    fun importConfig(link: String): Boolean {
        return try {
            val prefix = "turnbridge://"
            val b64 = if (link.startsWith(prefix)) link.substring(prefix.length) else link
            val json = String(Base64.decode(b64, Base64.DEFAULT), Charsets.UTF_8)
            val obj = JSONObject(json)

            val profile = VpnProfile(
                name = obj.optString("name", "Imported"),
                vkLink = obj.optString("turn", ""),
                peerAddr = obj.optString("peer", ""),
                listenAddr = obj.optString("listen", "127.0.0.1:9000"),
                nValue = obj.optInt("n", 16),
                wgQuickConfig = obj.optString("wg", "").trim()
            )

            val added = profileStore.addProfile(profile)
            profileStore.selectedProfileId = added.id
            refreshProfileState()

            _state.value = _state.value.copy(statusText = Strings.current.stImported.format(added.name))
            Log.i(TAG, "Config imported as profile: ${added.name}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import config", e)
            _state.value = _state.value.copy(
                status = ConnectionState.ERROR,
                statusText = Strings.current.stInvalidLink
            )
            false
        }
    }

    fun dismissCaptcha() {
        _showCaptchaDialog.value = false
    }

    fun checkForUpdates() {
        viewModelScope.launch(Dispatchers.IO) {
            _updateInfo.value = UpdateChecker.check()
        }
    }

    fun setUpdateInfo(info: UpdateInfo?) {
        _updateInfo.value = info
    }

    fun downloadUpdate() {
        _updateInfo.value?.let {
            UpdateChecker.downloadAndInstall(getApplication(), it)
        }
    }

    /**
     * Подключение: запуск proxy, затем WireGuard туннель через GoBackend.
     */
    fun connect() {
        val s = Strings.current
        val profile = profileStore.selectedProfile
        if (profile == null) {
            _state.value = _state.value.copy(
                status = ConnectionState.ERROR,
                statusText = s.stNoProfile
            )
            return
        }

        if (profile.vkLink.isBlank() || profile.peerAddr.isBlank() || profile.wgQuickConfig.isBlank()) {
            _state.value = _state.value.copy(
                status = ConnectionState.ERROR,
                statusText = s.stProfileIncomplete
            )
            return
        }

        _state.value = _state.value.copy(
            status = ConnectionState.CONNECTING,
            statusText = s.stStartingProxy
        )
        LogStore.addFriendlyLog(s.fStartingTurnGate)

        viewModelScope.launch(Dispatchers.IO) {
            _captchaCount.value = 0

            // 1. Запускаем vk-turn-proxy-client
            val started = proxyManager.start(
                serverAddress = profile.peerAddr,
                vkLink = profile.vkLink,
                listenAddress = profile.listenAddr,
                threads = profile.nValue,
                manualCaptcha = profile.manualCaptcha
            )

            if (!started) {
                _state.value = _state.value.copy(
                    status = ConnectionState.ERROR,
                    statusText = Strings.current.stProxyFailed
                )
                return@launch
            }

            _state.value = _state.value.copy(statusText = Strings.current.stConnecting)
            delay(1000)

            // 2. Парсим wg-quick конфиг и поднимаем WireGuard туннель
            try {
                val ctx = getApplication<Application>()
                val config = Config.parse(BufferedReader(StringReader(profile.wgQuickConfig)))

                // Исключаем своё приложение из VPN чтобы не было петли
                val existingIface = config.`interface`
                val newIface = com.wireguard.config.Interface.Builder().apply {
                    parsePrivateKey(existingIface.keyPair.privateKey.toBase64())
                    existingIface.addresses.forEach { addAddress(it) }
                    existingIface.dnsServers.forEach { addDnsServer(it) }
                    existingIface.mtu.ifPresent { setMtu(it) }
                    excludeApplication(ctx.packageName)
                }.build()

                val newConfig = Config.Builder()
                    .setInterface(newIface)
                    .apply { config.peers.forEach { addPeer(it) } }
                    .build()

                tunnel = WgTunnel()
                backend.setState(tunnel!!, Tunnel.State.UP, newConfig)

                // Оставляем статус CONNECTING, показываем что ждём handshake
                val cur = _state.value.statusText
                if (cur.isBlank() || cur == Strings.current.stStartingProxy) {
                    _state.value = _state.value.copy(statusText = Strings.current.stWgHandshake)
                }

                // Ждём handshake - проверяем latestHandshakeEpochMillis у peer.
                // Таймаут 180 сек (учитываем что captcha может решаться до 60 сек).
                val handshakeSucceeded = waitForHandshake(timeoutMs = 180_000)

                if (handshakeSucceeded) {
                    TunVpnService.connectionState = ConnectionState.CONNECTED
                    _state.value = _state.value.copy(
                        status = ConnectionState.CONNECTED,
                        statusText = Strings.current.stConnected
                    )
                    LogStore.addFriendlyLog(Strings.current.fConnected)
                    LogStore.addAppLog("VPN tunnel UP - handshake complete with ${profile.peerAddr}")
                    Log.i(TAG, "VPN tunnel UP with handshake")
                } else {
                    Log.w(TAG, "Handshake timeout, rolling back")
                    try { backend.setState(tunnel!!, Tunnel.State.DOWN, null) } catch (_: Exception) {}
                    tunnel = null
                    proxyManager.stop()
                    _state.value = _state.value.copy(
                        status = ConnectionState.ERROR,
                        statusText = Strings.current.stError
                    )
                    LogStore.addAppLog("Handshake timeout")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start VPN", e)
                proxyManager.stop()
                _state.value = _state.value.copy(
                    status = ConnectionState.ERROR,
                    statusText = Strings.current.stVpnFailed.format(e.message ?: "")
                )
            }
        }
    }

    /**
     * Отключение: остановка VPN и proxy.
     */
    fun disconnect() {
        // Если висит captcha dialog — закрываем немедленно (пользователь
        // отменил либо мы сами останавливаемся).
        _showCaptchaDialog.value = false
        _state.value = _state.value.copy(
            status = ConnectionState.DISCONNECTING,
            statusText = Strings.current.stDisconnecting
        )
        viewModelScope.launch(Dispatchers.IO) {
            try {
                tunnel?.let { backend.setState(it, Tunnel.State.DOWN, null) }
                tunnel = null
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping VPN", e)
            }
            proxyManager.stop()

            TunVpnService.connectionState = ConnectionState.DISCONNECTED
            _state.value = _state.value.copy(
                status = ConnectionState.DISCONNECTED,
                statusText = Strings.current.stDisconnected
            )
            LogStore.addFriendlyLog(Strings.current.fDisconnected)
        }
    }

    /**
     * Polling latestHandshakeEpochMillis у WireGuard peer.
     * Возвращает true когда handshake успешно прошёл (>0), false по таймауту.
     */
    private suspend fun waitForHandshake(timeoutMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val t = tunnel ?: return false
            try {
                val stats = backend.getStatistics(t)
                val peerKeys = stats.peers()
                for (key in peerKeys) {
                    val peerStats = stats.peer(key)
                    if (peerStats != null && peerStats.latestHandshakeEpochMillis() > 0) {
                        return true
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "getStatistics failed: ${e.message}")
            }
            delay(1000)
        }
        return false
    }

    override fun onCleared() {
        try {
            tunnel?.let { backend.setState(it, Tunnel.State.DOWN, null) }
        } catch (_: Exception) {}
        proxyManager.stop()
        super.onCleared()
    }

    private class WgTunnel : Tunnel {
        override fun getName(): String = "tun-vpn"
        override fun onStateChange(newState: Tunnel.State) {
            Log.d(TAG, "Tunnel state: $newState")
        }
    }

    companion object {
        private const val TAG = "MainViewModel"
    }
}

data class UiState(
    val profiles: List<VpnProfile> = emptyList(),
    val selectedProfileId: String? = null,
    val status: ConnectionState = ConnectionState.DISCONNECTED,
    val statusText: String = "",
    val proxyLogs: List<String> = emptyList()
)
