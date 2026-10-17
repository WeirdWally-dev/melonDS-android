package me.weirdwally.dslobby

import android.content.ActivityNotFoundException
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import me.weirdwally.dslobby.melon.LibraryResult
import me.weirdwally.dslobby.net.RoomState
import me.weirdwally.dslobby.ui.GamePickerScreen
import me.weirdwally.dslobby.ui.LaunchingScreen
import me.weirdwally.dslobby.ui.NearbyScreen
import me.weirdwally.dslobby.ui.RoomScreen
import me.weirdwally.dslobby.ui.SetupScreen

class MainActivity : ComponentActivity() {

    private val viewModel by viewModels<LobbyViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent { LobbyApp(viewModel) }
    }

    override fun onStart() {
        super.onStart()
        viewModel.onForeground()
    }

    override fun onStop() {
        super.onStop()
        viewModel.onBackground()
    }

    @Composable
    private fun LobbyApp(viewModel: LobbyViewModel) {
        val profile by viewModel.profile.collectAsState()
        val installs by viewModel.installs.collectAsState()
        val library by viewModel.library.collectAsState()
        val lobby by viewModel.lobby.collectAsState()
        val launching by viewModel.launching.collectAsState()

        var editingProfile by remember { mutableStateOf(false) }
        var pickingGame by remember { mutableStateOf(false) }
        var notice by remember { mutableStateOf<String?>(null) }
        var noticeId by remember { mutableStateOf(0) }

        LaunchedEffect(Unit) {
            viewModel.events.collect { event ->
                when (event) {
                    is LobbyViewModel.UiEvent.Launch -> {
                        try {
                            startActivity(event.intent)
                        } catch (_: ActivityNotFoundException) {
                            Toast.makeText(this@MainActivity, "Couldn't open melonDS. Is it still installed?", Toast.LENGTH_LONG).show()
                        }
                    }
                    is LobbyViewModel.UiEvent.Notice -> {
                        notice = event.text
                        noticeId++
                        if (event.vibrate) buzz()
                    }
                }
            }
        }
        LaunchedEffect(noticeId) {
            delay(3_000)
            notice = null
        }

        val roomState = lobby.room
        val currentLaunch = launching

        when {
            !profile.isComplete || editingProfile -> {
                BackHandler(enabled = editingProfile) { editingProfile = false }
                SetupScreen(
                    profile = profile,
                    installs = installs,
                    selectedInstall = viewModel.selectedInstall(),
                    isFirstRun = !profile.isComplete,
                    onSave = { name, colour, melonPackage ->
                        viewModel.saveProfile(name, colour, melonPackage)
                        editingProfile = false
                    },
                    onCancel = { editingProfile = false },
                )
            }
            currentLaunch != null -> LaunchingScreen(currentLaunch, profile)
            roomState !is RoomState.None -> {
                BackHandler { viewModel.leaveRoom() }
                RoomScreen(
                    profile = profile,
                    roomState = roomState,
                    notice = notice,
                    loadIcon = viewModel::loadIcon,
                    onMaxPlayersChange = viewModel::setMaxPlayers,
                    onStart = viewModel::startGame,
                    onLeave = viewModel::leaveRoom,
                )
            }
            pickingGame -> {
                BackHandler { pickingGame = false }
                GamePickerScreen(
                    profile = profile,
                    games = (library as? LibraryResult.Loaded)?.games.orEmpty(),
                    loadIcon = viewModel::loadIcon,
                    onPick = { game ->
                        pickingGame = false
                        viewModel.openRoom(game, maxPlayers = 4)
                    },
                    onBack = { pickingGame = false },
                )
            }
            else -> NearbyScreen(
                profile = profile,
                lobby = lobby,
                library = library,
                hasInstall = installs.isNotEmpty(),
                notice = notice,
                findMatchingGame = viewModel::findMatchingGame,
                loadIcon = viewModel::loadIcon,
                onJoin = viewModel::join,
                onOpenRoom = { pickingGame = true },
                onEditProfile = { editingProfile = true },
                onRetryNetwork = viewModel::retryNetwork,
            )
        }
    }

    private fun buzz() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(40)
        }
    }
}
