package me.magnum.melonds.ui.lan

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.Button
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import me.magnum.melonds.R
import me.magnum.melonds.ui.emulator.EmulatorActivity
import me.magnum.melonds.ui.theme.MelonTheme

/**
 * Entry point for the DS Lobby app. Connects the LAN session the lobby asked for, then boots the game.
 */
@AndroidEntryPoint
class LanLaunchActivity : AppCompatActivity() {

    private val viewModel by viewModels<LanLaunchViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT))
        super.onCreate(savedInstanceState)

        setContent {
            val state by viewModel.state.collectAsStateWithLifecycle()

            LaunchedEffect(state) {
                val ready = state as? LanLaunchViewModel.State.Ready ?: return@LaunchedEffect
                bootGame(ready.romUri)
            }

            BackHandler { cancelAndClose() }

            MelonTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colors.background) {
                    LanLaunchContent(
                        state = state,
                        onRetry = viewModel::retry,
                        onCancel = ::cancelAndClose,
                    )
                }
            }
        }
    }

    private fun bootGame(romUri: String) {
        val intent = Intent(this, EmulatorActivity::class.java).apply {
            putExtra(EmulatorActivity.KEY_URI, romUri)
            // Boot the game again even if it's already running: the in-game wireless menu needs to see the new session
            putExtra(EmulatorActivity.KEY_LAN_FRESH_BOOT, true)
        }
        startActivity(intent)
        finish()
    }

    private fun cancelAndClose() {
        viewModel.cancel()
        finish()
    }
}

@Composable
private fun LanLaunchContent(
    state: LanLaunchViewModel.State,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (state) {
            is LanLaunchViewModel.State.Connecting, is LanLaunchViewModel.State.Ready -> {
                val message = when {
                    state is LanLaunchViewModel.State.Connecting && state.isHost -> stringResource(R.string.lan_starting_host)
                    state is LanLaunchViewModel.State.Connecting && state.hostName != null -> stringResource(R.string.lan_connecting_to, state.hostName)
                    else -> stringResource(R.string.lan_lobby_connecting)
                }
                CircularProgressIndicator()
                Spacer(Modifier.height(24.dp))
                Text(text = message, style = MaterialTheme.typography.h6, textAlign = TextAlign.Center)
                Spacer(Modifier.height(32.dp))
                OutlinedButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
            }
            is LanLaunchViewModel.State.Failed -> {
                Text(
                    text = stringResource(if (state.isHost) R.string.lan_error_host_failed else R.string.lan_error_connect_failed),
                    style = MaterialTheme.typography.body1,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(32.dp))
                Row {
                    OutlinedButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
                    Spacer(Modifier.width(16.dp))
                    Button(onClick = onRetry) { Text(stringResource(R.string.lan_lobby_retry)) }
                }
            }
            LanLaunchViewModel.State.InvalidRequest -> {
                Text(
                    text = stringResource(R.string.lan_lobby_invalid_request),
                    style = MaterialTheme.typography.body1,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(32.dp))
                Button(onClick = onCancel) { Text(stringResource(R.string.ok)) }
            }
        }
    }
}
