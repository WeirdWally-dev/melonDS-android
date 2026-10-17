package me.magnum.melonds.ui.lan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import me.magnum.melonds.domain.model.lan.LanSessionState
import me.magnum.melonds.domain.repositories.SettingsRepository
import me.magnum.melonds.impl.lan.LanSessionManager
import javax.inject.Inject

@HiltViewModel
class LanViewModel @Inject constructor(
    private val lanSessionManager: LanSessionManager,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    enum class Event {
        HOST_FAILED,
        CONNECT_FAILED,
        DISCOVERY_FAILED,
    }

    val sessionState: StateFlow<LanSessionState> = lanSessionManager.state

    private val _playerName = MutableStateFlow(settingsRepository.getFirmwareConfiguration().nickname)
    val playerName = _playerName.asStateFlow()

    private val _maxPlayers = MutableStateFlow(4)
    val maxPlayers = _maxPlayers.asStateFlow()

    private val _manualAddress = MutableStateFlow("")
    val manualAddress = _manualAddress.asStateFlow()

    private val eventChannel = Channel<Event>(Channel.BUFFERED)
    val events: Flow<Event> = eventChannel.receiveAsFlow()

    init {
        // Start looking for hosts as soon as the lobby opens, unless a session is already running
        viewModelScope.launch {
            if (lanSessionManager.state.value is LanSessionState.Idle) {
                if (!lanSessionManager.startBrowsing()) {
                    eventChannel.send(Event.DISCOVERY_FAILED)
                }
            }
        }
    }

    fun setPlayerName(name: String) {
        // Same limit as the DS firmware nickname
        _playerName.value = name.take(10)
    }

    fun setMaxPlayers(count: Int) {
        _maxPlayers.value = count.coerceIn(LanSessionManager.MIN_PLAYERS, LanSessionManager.MAX_PLAYERS)
    }

    fun setManualAddress(address: String) {
        _manualAddress.value = address.filter { it.isDigit() || it == '.' }.take(15)
    }

    fun host() {
        viewModelScope.launch {
            if (lanSessionManager.host(_playerName.value, _maxPlayers.value) is LanSessionManager.Result.HostFailed) {
                eventChannel.send(Event.HOST_FAILED)
            }
        }
    }

    fun join(address: String) {
        if (address.isBlank()) return

        viewModelScope.launch {
            if (lanSessionManager.join(_playerName.value, address) is LanSessionManager.Result.ConnectFailed) {
                eventChannel.send(Event.CONNECT_FAILED)
            }
        }
    }

    fun joinManualAddress() {
        join(_manualAddress.value)
    }

    fun leaveSession() {
        viewModelScope.launch {
            lanSessionManager.leave()
            // Back to the lobby's join/host view
            if (!lanSessionManager.startBrowsing()) {
                eventChannel.send(Event.DISCOVERY_FAILED)
            }
        }
    }

    fun retryDiscovery() {
        viewModelScope.launch {
            if (!lanSessionManager.startBrowsing()) {
                eventChannel.send(Event.DISCOVERY_FAILED)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        // Leaving the lobby stops browsing (it keeps the discovery port open and Wi-Fi awake), but an established
        // session stays alive so the players can go pick their game
        lanSessionManager.stopBrowsingInBackground()
    }
}
