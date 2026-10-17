package me.weirdwally.dslobby

import android.app.Application
import android.content.Intent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import me.weirdwally.dslobby.melon.LibraryGame
import me.weirdwally.dslobby.melon.LibraryResult
import me.weirdwally.dslobby.melon.MelonDsBridge
import me.weirdwally.dslobby.melon.MelonInstall
import me.weirdwally.dslobby.net.LobbyEngine
import me.weirdwally.dslobby.net.LobbyEvent
import me.weirdwally.dslobby.net.LobbyState
import me.weirdwally.dslobby.net.NearbyPlayer
import me.weirdwally.dslobby.net.RoomInfo

class LobbyViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        // Give the host's melonDS a head start so the first connection attempt usually succeeds
        private const val CLIENT_LAUNCH_DELAY_MS = 1_500L
    }

    sealed class UiEvent {
        data class Launch(val intent: Intent) : UiEvent()
        data class Notice(val text: String, val vibrate: Boolean = false) : UiEvent()
    }

    /** Shown while the phone is about to hand over to melonDS */
    data class Launching(val gameTitle: String, val hostName: String?)

    private val profileStore = ProfileStore(application)
    private val bridge = MelonDsBridge(application)
    private val engine = LobbyEngine(application)

    private val _profile = MutableStateFlow(profileStore.load())
    val profile: StateFlow<Profile> = _profile.asStateFlow()

    private val _installs = MutableStateFlow<List<MelonInstall>>(emptyList())
    val installs: StateFlow<List<MelonInstall>> = _installs.asStateFlow()

    /** null while loading */
    private val _library = MutableStateFlow<LibraryResult?>(null)
    val library: StateFlow<LibraryResult?> = _library.asStateFlow()

    private val _launching = MutableStateFlow<Launching?>(null)
    val launching: StateFlow<Launching?> = _launching.asStateFlow()

    val lobby: StateFlow<LobbyState> = engine.state

    private val uiEvents = Channel<UiEvent>(Channel.BUFFERED)
    val events: Flow<UiEvent> = uiEvents.receiveAsFlow()

    private var isForeground = false

    init {
        viewModelScope.launch {
            engine.events.collect(::onLobbyEvent)
        }
    }

    fun onForeground() {
        isForeground = true
        _launching.value = null
        refreshInstalls()
        startEngineIfReady()
    }

    fun onBackground() {
        isForeground = false
        engine.stop()
    }

    fun retryNetwork() {
        engine.stop()
        startEngineIfReady()
    }

    fun saveProfile(name: String, colour: Int, melonPackage: String?) {
        val updated = _profile.value.copy(name = name.trim().take(Profile.MAX_NAME_LENGTH), colour = colour, melonPackage = melonPackage)
        profileStore.save(updated)
        _profile.value = updated
        refreshInstalls()
        if (engine.state.value.isRunning) engine.updateProfile(updated) else startEngineIfReady()
    }

    fun refreshInstalls() {
        val installs = bridge.findInstalls()
        _installs.value = installs
        val chosen = selectedInstall()
        if (chosen == null) {
            _library.value = null
            return
        }
        viewModelScope.launch {
            _library.value = bridge.loadLibrary(chosen.packageName)
        }
    }

    fun selectedInstall(): MelonInstall? {
        val installs = _installs.value
        return installs.firstOrNull { it.packageName == _profile.value.melonPackage } ?: installs.firstOrNull()
    }

    suspend fun loadIcon(game: LibraryGame): ImageBitmap? = bridge.loadIcon(game)

    fun games(): List<LibraryGame> = (_library.value as? LibraryResult.Loaded)?.games.orEmpty()

    /** The copy of the room's game on this phone. Prefers the same region, but any region will do */
    fun findMatchingGame(room: RoomInfo): LibraryGame? {
        val games = games()
        return games.firstOrNull { it.gameCode == room.gameCode }
            ?: games.firstOrNull { it.baseCode == room.gameCode.take(3) }
    }

    fun openRoom(game: LibraryGame, maxPlayers: Int) = engine.openRoom(game, maxPlayers)

    fun setMaxPlayers(maxPlayers: Int) = engine.setMaxPlayers(maxPlayers)

    fun join(player: NearbyPlayer) {
        val room = player.room ?: return
        val game = findMatchingGame(room) ?: return
        engine.joinRoom(player.id, game)
    }

    fun leaveRoom() = engine.leaveRoom()

    fun startGame() = engine.startGame()

    private fun startEngineIfReady() {
        val profile = _profile.value
        if (isForeground && profile.isComplete) {
            engine.start(profile)
        }
    }

    private suspend fun onLobbyEvent(event: LobbyEvent) {
        val install = selectedInstall()
        val profile = _profile.value
        when (event) {
            is LobbyEvent.LaunchAsHost -> {
                if (install == null) return
                _launching.value = Launching(event.game.name, null)
                uiEvents.send(UiEvent.Launch(bridge.buildHostIntent(install.packageName, event.game, profile.name, profile.colour, event.maxPlayers)))
            }
            is LobbyEvent.LaunchAsClient -> {
                if (install == null) return
                _launching.value = Launching(event.game.name, event.hostName)
                delay(CLIENT_LAUNCH_DELAY_MS)
                uiEvents.send(UiEvent.Launch(bridge.buildJoinIntent(install.packageName, event.game, profile.name, profile.colour, event.hostAddress, event.hostName)))
            }
            is LobbyEvent.PlayerJoined -> uiEvents.send(UiEvent.Notice("${event.name} joined", vibrate = true))
            is LobbyEvent.PlayerLeft -> uiEvents.send(UiEvent.Notice("${event.name} left"))
            LobbyEvent.JoinedRoom -> uiEvents.send(UiEvent.Notice("You're in!", vibrate = true))
            is LobbyEvent.RoomEnded -> {
                val text = when (event.reason) {
                    LobbyEvent.RoomEnded.Reason.CLOSED -> "${event.hostName} closed the room"
                    LobbyEvent.RoomEnded.Reason.FULL -> "${event.hostName}'s room is full"
                    LobbyEvent.RoomEnded.Reason.LOST -> "Lost contact with ${event.hostName}"
                }
                uiEvents.send(UiEvent.Notice(text))
            }
        }
    }

    override fun onCleared() {
        engine.stop()
    }
}
