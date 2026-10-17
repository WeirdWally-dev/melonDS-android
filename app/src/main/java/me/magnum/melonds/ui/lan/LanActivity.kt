package me.magnum.melonds.ui.lan

import android.graphics.Color
import android.os.Bundle
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import me.magnum.melonds.R
import me.magnum.melonds.ui.lan.ui.LanScreen
import me.magnum.melonds.ui.theme.MelonTheme

@AndroidEntryPoint
class LanActivity : AppCompatActivity() {

    private val viewModel by viewModels<LanViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT))
        super.onCreate(savedInstanceState)

        setContent {
            val state by viewModel.sessionState.collectAsStateWithLifecycle()
            val playerName by viewModel.playerName.collectAsStateWithLifecycle()
            val maxPlayers by viewModel.maxPlayers.collectAsStateWithLifecycle()
            val manualAddress by viewModel.manualAddress.collectAsStateWithLifecycle()

            LaunchedEffect(Unit) {
                viewModel.events.collectLatest { event ->
                    val message = when (event) {
                        LanViewModel.Event.HOST_FAILED -> R.string.lan_error_host_failed
                        LanViewModel.Event.CONNECT_FAILED -> R.string.lan_error_connect_failed
                        LanViewModel.Event.DISCOVERY_FAILED -> R.string.lan_error_discovery_failed
                    }
                    Toast.makeText(this@LanActivity, message, Toast.LENGTH_LONG).show()
                }
            }

            MelonTheme {
                LanScreen(
                    state = state,
                    playerName = playerName,
                    maxPlayers = maxPlayers,
                    manualAddress = manualAddress,
                    onPlayerNameChange = viewModel::setPlayerName,
                    onMaxPlayersChange = viewModel::setMaxPlayers,
                    onManualAddressChange = viewModel::setManualAddress,
                    onHost = viewModel::host,
                    onJoinSession = { viewModel.join(it.address) },
                    onJoinManualAddress = viewModel::joinManualAddress,
                    onRetryDiscovery = viewModel::retryDiscovery,
                    onLeaveSession = viewModel::leaveSession,
                    // The session lives on in LanSessionManager; going back to the ROM list is how a game is chosen
                    onChooseGame = { finish() },
                    onBackClick = { onSupportNavigateUp() },
                )
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
