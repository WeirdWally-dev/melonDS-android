package me.magnum.melonds.impl.lan

import android.annotation.SuppressLint
import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import me.magnum.melonds.MelonLan
import me.magnum.melonds.domain.model.lan.LanSessionState
import java.net.Inet4Address
import java.net.NetworkInterface
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the app-wide LAN multiplayer session. The session outlives any single screen: players set it up in the lobby,
 * then go back to the ROM list and launch the game, and the session stays connected the whole time.
 */
@Singleton
class LanSessionManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private const val TAG = "LanSessionManager"
        private const val POLL_INTERVAL_MS = 500L
        const val MIN_PLAYERS = 2
        const val MAX_PLAYERS = 16
    }

    sealed class Result {
        data object Success : Result()
        data object HostFailed : Result()
        data object ConnectFailed : Result()
        data object AlreadyInSession : Result()
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    // Serializes start/stop requests so a double-tap can't start two sessions
    private val operationMutex = Mutex()
    private var pollJob: Job? = null

    private val wifiManager by lazy { context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager }
    private var multicastLock: WifiManager.MulticastLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    private val _state = MutableStateFlow<LanSessionState>(LanSessionState.Idle)
    val state: StateFlow<LanSessionState> = _state.asStateFlow()

    suspend fun startBrowsing(): Boolean = operationMutex.withLock {
        if (_state.value !is LanSessionState.Idle) {
            return@withLock _state.value is LanSessionState.Browsing
        }

        // Android drops broadcast packets unless a multicast lock is held, which would hide every host
        acquireNetworkLocks()
        val started = withContext(Dispatchers.IO) { MelonLan.startDiscovery() }
        if (started) {
            _state.value = LanSessionState.Browsing(emptyList())
            startPolling()
        } else {
            Log.w(TAG, "Failed to start LAN discovery")
            releaseNetworkLocks()
        }
        started
    }

    suspend fun stopBrowsing() = operationMutex.withLock {
        if (_state.value !is LanSessionState.Browsing) return@withLock

        stopPolling()
        withContext(Dispatchers.IO) { MelonLan.endDiscovery() }
        releaseNetworkLocks()
        _state.value = LanSessionState.Idle
    }

    /**
     * Stops browsing without waiting, for callers that are going away (e.g. a ViewModel being cleared). Does nothing if
     * a session was established in the meantime.
     */
    fun stopBrowsingInBackground() {
        scope.launch { stopBrowsing() }
    }

    suspend fun host(playerName: String, maxPlayers: Int): Result = operationMutex.withLock {
        val previousState = _state.value
        if (previousState.isInSession || previousState is LanSessionState.Connecting) return@withLock Result.AlreadyInSession

        stopPolling()
        acquireNetworkLocks()
        _state.value = LanSessionState.Connecting(isHost = true, hostAddress = null)

        val players = maxPlayers.coerceIn(MIN_PLAYERS, MAX_PLAYERS)
        val success = withContext(Dispatchers.IO) { MelonLan.startHost(sanitizeName(playerName), players) }
        if (success) {
            refreshState()
            startPolling()
            Result.Success
        } else {
            // A failed host start leaves the native side idle
            releaseNetworkLocks()
            _state.value = LanSessionState.Idle
            Result.HostFailed
        }
    }

    suspend fun join(playerName: String, hostAddress: String): Result = operationMutex.withLock {
        val previousState = _state.value
        if (previousState.isInSession || previousState is LanSessionState.Connecting) return@withLock Result.AlreadyInSession

        stopPolling()
        acquireNetworkLocks()
        _state.value = LanSessionState.Connecting(isHost = false, hostAddress = hostAddress)

        val success = withContext(Dispatchers.IO) { MelonLan.startClient(sanitizeName(playerName), hostAddress.trim()) }
        // On failure the native side goes back to browsing if it was browsing before, or to idle otherwise
        refreshState()
        if (_state.value is LanSessionState.Idle) {
            releaseNetworkLocks()
        } else {
            startPolling()
        }

        if (success) Result.Success else Result.ConnectFailed
    }

    suspend fun leave() = operationMutex.withLock {
        stopPolling()
        withContext(Dispatchers.IO) { MelonLan.endSession() }
        releaseNetworkLocks()
        _state.value = LanSessionState.Idle
    }

    private fun startPolling() {
        pollJob?.cancel()
        pollJob = scope.launch {
            while (isActive) {
                refreshState()
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    private fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
    }

    private suspend fun refreshState() {
        val newState = withContext(Dispatchers.IO) {
            when (MelonLan.getMode()) {
                MelonLan.MODE_BROWSING -> LanSessionState.Browsing(MelonLan.getDiscoveredSessions().toList())
                MelonLan.MODE_HOSTING, MelonLan.MODE_CLIENT -> {
                    LanSessionState.InSession(
                        isHost = MelonLan.getMode() == MelonLan.MODE_HOSTING,
                        players = MelonLan.getPlayers().sortedBy { it.id },
                        maxPlayers = MelonLan.getMaxPlayers(),
                        localAddress = findLocalIpv4Address(),
                    )
                }
                else -> LanSessionState.Idle
            }
        }
        _state.value = newState
    }

    private fun sanitizeName(name: String): String {
        return name.trim().ifEmpty { "Player" }
    }

    @SuppressLint("WakelockTimeout")
    private fun acquireNetworkLocks() {
        if (multicastLock == null) {
            multicastLock = wifiManager.createMulticastLock("melonDS:lan").apply {
                setReferenceCounted(false)
                acquire()
            }
        }

        if (wifiLock == null) {
            // Wi-Fi power saving adds a lot of latency, and local wireless needs a reply within a few milliseconds
            val lockMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                WifiManager.WIFI_MODE_FULL_LOW_LATENCY
            } else {
                @Suppress("DEPRECATION")
                WifiManager.WIFI_MODE_FULL_HIGH_PERF
            }
            wifiLock = wifiManager.createWifiLock(lockMode, "melonDS:lan").apply {
                setReferenceCounted(false)
                acquire()
            }
        }
    }

    private fun releaseNetworkLocks() {
        multicastLock?.let { if (it.isHeld) it.release() }
        multicastLock = null
        wifiLock?.let { if (it.isHeld) it.release() }
        wifiLock = null
    }

    /**
     * Finds this device's private IPv4 address. Enumerates interfaces instead of asking the active network so that it
     * also works when this phone is the Wi-Fi hotspot (the active network is then mobile data).
     */
    private fun findLocalIpv4Address(): String? {
        return runCatching {
            NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
                .filter { it.isUp && !it.isLoopback }
                // Prefer Wi-Fi and hotspot interfaces over things like VPNs or mobile data
                .sortedByDescending { iface -> listOf("wlan", "ap", "swlan", "eth").any { iface.name.startsWith(it) } }
                .flatMap { it.inetAddresses.toList() }
                .filterIsInstance<Inet4Address>()
                .firstOrNull { it.isSiteLocalAddress }
                ?.hostAddress
        }.getOrNull()
    }
}
