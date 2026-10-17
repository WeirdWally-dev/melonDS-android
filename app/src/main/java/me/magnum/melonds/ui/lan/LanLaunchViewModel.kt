package me.magnum.melonds.ui.lan

import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.magnum.melonds.domain.model.lan.LanSessionState
import me.magnum.melonds.impl.lan.LanSessionManager
import me.magnum.melonds.lobby.LobbyContract
import javax.inject.Inject

/**
 * Sets up the LAN session requested by the DS Lobby app, then tells the activity to boot the game. The lobby already
 * found everyone and agreed on the game, so this screen is only visible for the second or two it takes to connect.
 */
@HiltViewModel
class LanLaunchViewModel @Inject constructor(
    private val lanSessionManager: LanSessionManager,
    private val sharedPreferences: SharedPreferences,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    companion object {
        // Players press Start at the same moment, so the host's session may need a moment to come up
        private const val JOIN_DEADLINE_MS = 30_000L
        private const val JOIN_RETRY_DELAY_MS = 1_000L
        private const val NICKNAME_MAX_LENGTH = 10
    }

    sealed class State {
        data class Connecting(val isHost: Boolean, val hostName: String?) : State()
        data class Ready(val romUri: String) : State()
        data class Failed(val isHost: Boolean) : State()
        data object InvalidRequest : State()
    }

    private val role = savedStateHandle.get<String>(LobbyContract.EXTRA_ROLE)
    private val hostAddress = savedStateHandle.get<String>(LobbyContract.EXTRA_HOST_ADDRESS)?.trim()
    private val hostName = savedStateHandle.get<String>(LobbyContract.EXTRA_HOST_NAME)
    private val playerName = savedStateHandle.get<String>(LobbyContract.EXTRA_PLAYER_NAME)?.trim()?.take(NICKNAME_MAX_LENGTH)
    private val maxPlayers = savedStateHandle.get<Int>(LobbyContract.EXTRA_MAX_PLAYERS) ?: 4
    private val romUri = savedStateHandle.get<String>(LobbyContract.EXTRA_ROM_URI)
    private val favouriteColour = savedStateHandle.get<Int>(LobbyContract.EXTRA_FAVOURITE_COLOUR) ?: -1

    private val isHost = role == LobbyContract.ROLE_HOST

    private val _state = MutableStateFlow<State>(State.Connecting(isHost, hostName))
    val state: StateFlow<State> = _state.asStateFlow()

    private var connectJob: Job? = null

    init {
        if (!isValidRequest()) {
            _state.value = State.InvalidRequest
        } else {
            applyProfile()
            connect()
        }
    }

    fun retry() {
        if (_state.value is State.Failed) connect()
    }

    fun cancel() {
        connectJob?.cancel()
        // Don't leave a half-made session behind
        if (_state.value !is State.Ready && _state.value !is State.InvalidRequest) {
            lanSessionManager.leaveInBackground()
        }
    }

    private fun isValidRequest(): Boolean {
        if (romUri.isNullOrBlank() || playerName.isNullOrBlank()) return false
        return when (role) {
            LobbyContract.ROLE_HOST -> true
            LobbyContract.ROLE_JOIN -> hostAddress != null && isLocalNetworkAddress(hostAddress)
            else -> false
        }
    }

    /**
     * The lobby profile is the player's DS profile: the name and colour show up in game, the way they did on a real DS.
     * Only the built-in firmware reads these settings; a custom firmware dump keeps its own profile.
     */
    private fun applyProfile() {
        sharedPreferences.edit {
            putString("firmware_settings_nickname", playerName)
            if (favouriteColour in 0..15) {
                putInt("firmware_settings_colour", favouriteColour)
            }
        }
    }

    private fun connect() {
        connectJob?.cancel()
        _state.value = State.Connecting(isHost, hostName)
        connectJob = viewModelScope.launch {
            val connected = if (isHost) startHosting() else joinHost(hostAddress!!)
            _state.value = if (connected) State.Ready(romUri!!) else State.Failed(isHost)
        }
    }

    private suspend fun startHosting(): Boolean {
        val current = lanSessionManager.state.value
        // A fresh room means a fresh session. Reusing an old one would keep players from a previous room connected
        if (current !is LanSessionState.Idle) {
            leaveCurrent(current)
        }
        return lanSessionManager.host(playerName!!, maxPlayers) is LanSessionManager.Result.Success
    }

    private suspend fun joinHost(address: String): Boolean {
        val current = lanSessionManager.state.value
        if (current is LanSessionState.InSession && !current.isHost && current.players.any { it.id == 0 && it.address == address }) {
            // Already connected to this host, e.g. the game was relaunched from the lobby
            return true
        }
        if (current !is LanSessionState.Idle) {
            leaveCurrent(current)
        }

        val deadline = System.currentTimeMillis() + JOIN_DEADLINE_MS
        while (System.currentTimeMillis() < deadline) {
            when (lanSessionManager.join(playerName!!, address)) {
                LanSessionManager.Result.Success -> return true
                LanSessionManager.Result.AlreadyInSession -> leaveCurrent(lanSessionManager.state.value)
                else -> delay(JOIN_RETRY_DELAY_MS)
            }
        }
        return false
    }

    private suspend fun leaveCurrent(current: LanSessionState) {
        if (current is LanSessionState.Browsing) {
            lanSessionManager.stopBrowsing()
        } else {
            lanSessionManager.leave()
        }
    }

    private fun isLocalNetworkAddress(address: String): Boolean {
        val octets = address.split(".").map { it.toIntOrNull() ?: return false }
        if (octets.size != 4 || octets.any { it !in 0..255 }) return false
        val (a, b) = octets
        return a == 10 ||
            (a == 172 && b in 16..31) ||
            (a == 192 && b == 168) ||
            (a == 169 && b == 254) ||
            // Carrier-grade NAT range, used by some phone hotspots
            (a == 100 && b in 64..127)
    }
}
