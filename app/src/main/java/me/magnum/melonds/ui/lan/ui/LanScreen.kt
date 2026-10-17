package me.magnum.melonds.ui.lan.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.ContentAlpha
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.LocalContentAlpha
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import me.magnum.melonds.R
import me.magnum.melonds.domain.model.lan.LanDiscoveredSession
import me.magnum.melonds.domain.model.lan.LanPlayer
import me.magnum.melonds.domain.model.lan.LanPlayerStatus
import me.magnum.melonds.domain.model.lan.LanSessionState
import me.magnum.melonds.impl.lan.LanSessionManager

@Composable
fun LanScreen(
    state: LanSessionState,
    playerName: String,
    maxPlayers: Int,
    manualAddress: String,
    onPlayerNameChange: (String) -> Unit,
    onMaxPlayersChange: (Int) -> Unit,
    onManualAddressChange: (String) -> Unit,
    onHost: () -> Unit,
    onJoinSession: (LanDiscoveredSession) -> Unit,
    onJoinManualAddress: () -> Unit,
    onRetryDiscovery: () -> Unit,
    onLeaveSession: () -> Unit,
    onChooseGame: () -> Unit,
    onBackClick: () -> Unit,
) {
    Scaffold(
        topBar = {
            Box(Modifier.background(MaterialTheme.colors.primaryVariant).statusBarsPadding()) {
                TopAppBar(
                    title = { Text(stringResource(R.string.lan_multiplayer)) },
                    backgroundColor = MaterialTheme.colors.primary,
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(
                                painter = rememberVectorPainter(Icons.AutoMirrored.Filled.ArrowBack),
                                contentDescription = null,
                            )
                        }
                    },
                    windowInsets = WindowInsets.safeDrawing.exclude(WindowInsets(bottom = Int.MAX_VALUE)),
                )
            }
        },
        backgroundColor = MaterialTheme.colors.surface,
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { padding ->
        val contentModifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .consumeWindowInsets(padding)

        when (state) {
            is LanSessionState.Idle, is LanSessionState.Browsing -> {
                SetupContent(
                    modifier = contentModifier,
                    sessions = (state as? LanSessionState.Browsing)?.sessions,
                    playerName = playerName,
                    maxPlayers = maxPlayers,
                    manualAddress = manualAddress,
                    onPlayerNameChange = onPlayerNameChange,
                    onMaxPlayersChange = onMaxPlayersChange,
                    onManualAddressChange = onManualAddressChange,
                    onHost = onHost,
                    onJoinSession = onJoinSession,
                    onJoinManualAddress = onJoinManualAddress,
                    onRetryDiscovery = onRetryDiscovery,
                )
            }
            is LanSessionState.Connecting -> ConnectingContent(contentModifier, state)
            is LanSessionState.InSession -> {
                SessionContent(
                    modifier = contentModifier,
                    state = state,
                    onLeaveSession = onLeaveSession,
                    onChooseGame = onChooseGame,
                )
            }
        }
    }
}

@Composable
private fun SetupContent(
    modifier: Modifier,
    sessions: List<LanDiscoveredSession>?,
    playerName: String,
    maxPlayers: Int,
    manualAddress: String,
    onPlayerNameChange: (String) -> Unit,
    onMaxPlayersChange: (Int) -> Unit,
    onManualAddressChange: (String) -> Unit,
    onHost: () -> Unit,
    onJoinSession: (LanDiscoveredSession) -> Unit,
    onJoinManualAddress: () -> Unit,
    onRetryDiscovery: () -> Unit,
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = playerName,
            onValueChange = onPlayerNameChange,
            label = { Text(stringResource(R.string.lan_player_name)) },
            singleLine = true,
        )

        Card(modifier = Modifier.fillMaxWidth(), elevation = 2.dp) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionTitle(stringResource(R.string.lan_host_game))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        modifier = Modifier.weight(1f),
                        text = stringResource(R.string.lan_max_players),
                    )
                    IconButton(
                        onClick = { onMaxPlayersChange(maxPlayers - 1) },
                        enabled = maxPlayers > LanSessionManager.MIN_PLAYERS,
                    ) {
                        Icon(rememberVectorPainter(Icons.Default.Remove), contentDescription = null)
                    }
                    Text(
                        modifier = Modifier.width(32.dp),
                        text = maxPlayers.toString(),
                        style = MaterialTheme.typography.h6,
                    )
                    IconButton(
                        onClick = { onMaxPlayersChange(maxPlayers + 1) },
                        enabled = maxPlayers < LanSessionManager.MAX_PLAYERS,
                    ) {
                        Icon(rememberVectorPainter(Icons.Default.Add), contentDescription = null)
                    }
                }
                Button(modifier = Modifier.fillMaxWidth(), onClick = onHost) {
                    Text(stringResource(R.string.lan_host))
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth(), elevation = 2.dp) {
            Column(Modifier.padding(vertical = 16.dp)) {
                SectionTitle(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    text = stringResource(R.string.lan_join_game),
                )
                Spacer(Modifier.height(8.dp))

                when {
                    sessions == null -> {
                        // Discovery isn't running (e.g. the discovery port is in use)
                        Column(Modifier.padding(horizontal = 16.dp)) {
                            SecondaryText(stringResource(R.string.lan_discovery_unavailable))
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(onClick = onRetryDiscovery) {
                                Text(stringResource(R.string.retry))
                            }
                        }
                    }
                    sessions.isEmpty() -> {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(12.dp))
                            SecondaryText(stringResource(R.string.lan_searching))
                        }
                    }
                    else -> {
                        sessions.forEach { session ->
                            DiscoveredSessionItem(session = session, onClick = { onJoinSession(session) })
                        }
                    }
                }

                Divider(Modifier.padding(vertical = 8.dp))

                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SecondaryText(stringResource(R.string.lan_join_by_ip_description))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            modifier = Modifier.weight(1f),
                            value = manualAddress,
                            onValueChange = onManualAddressChange,
                            label = { Text(stringResource(R.string.lan_host_ip_address)) },
                            placeholder = { Text("192.168.1.20") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Go),
                            keyboardActions = KeyboardActions(onGo = { onJoinManualAddress() }),
                        )
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = onJoinManualAddress,
                            enabled = manualAddress.isNotBlank(),
                        ) {
                            Text(stringResource(R.string.lan_join))
                        }
                    }
                }
            }
        }

        SecondaryText(stringResource(R.string.lan_same_network_hint))
    }
}

@Composable
private fun DiscoveredSessionItem(session: LanDiscoveredSession, onClick: () -> Unit) {
    val isFull = session.playerCount >= session.maxPlayers
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isFull, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = rememberVectorPainter(Icons.Default.Wifi),
            contentDescription = null,
            tint = MaterialTheme.colors.secondary,
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(text = session.name, style = MaterialTheme.typography.body1)
            SecondaryText(session.address)
        }
        Text(
            text = stringResource(R.string.lan_player_count, session.playerCount, session.maxPlayers),
            style = MaterialTheme.typography.body2,
        )
    }
}

@Composable
private fun ConnectingContent(modifier: Modifier, state: LanSessionState.Connecting) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Text(
            text = if (state.isHost) {
                stringResource(R.string.lan_starting_host)
            } else {
                stringResource(R.string.lan_connecting_to, state.hostAddress.orEmpty())
            },
        )
    }
}

@Composable
private fun SessionContent(
    modifier: Modifier,
    state: LanSessionState.InSession,
    onLeaveSession: () -> Unit,
    onChooseGame: () -> Unit,
) {
    // When the host drops, melonDS marks its entry as disconnected, so no player is left with the host status
    val hostLost = !state.isHost && state.players.none { it.playerStatus == LanPlayerStatus.HOST }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Card(modifier = Modifier.fillMaxWidth(), elevation = 2.dp) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SectionTitle(
                    if (state.isHost) stringResource(R.string.lan_hosting) else stringResource(R.string.lan_connected)
                )
                if (state.isHost) {
                    if (state.localAddress != null) {
                        Text(stringResource(R.string.lan_your_ip, state.localAddress))
                    }
                    SecondaryText(stringResource(R.string.lan_host_waiting_hint))
                }
                if (hostLost) {
                    Text(
                        text = stringResource(R.string.lan_host_lost),
                        color = MaterialTheme.colors.error,
                    )
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth(), elevation = 2.dp) {
            Column(Modifier.padding(vertical = 16.dp)) {
                SectionTitle(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    text = stringResource(R.string.lan_players, state.players.size, state.maxPlayers),
                )
                Spacer(Modifier.height(8.dp))
                state.players.forEach { player ->
                    PlayerItem(player, state.maxPlayers)
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth(), elevation = 2.dp) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SectionTitle(stringResource(R.string.lan_next_steps))
                Text(stringResource(R.string.lan_next_steps_description))
            }
        }

        Button(modifier = Modifier.fillMaxWidth(), onClick = onChooseGame) {
            Text(stringResource(R.string.lan_choose_game))
        }
        OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = onLeaveSession) {
            Text(stringResource(R.string.lan_leave_session))
        }
    }
}

@Composable
private fun PlayerItem(player: LanPlayer, maxPlayers: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            modifier = Modifier.width(48.dp),
            text = "${player.id + 1}/$maxPlayers",
            style = MaterialTheme.typography.body2,
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = if (player.isLocal) stringResource(R.string.lan_player_you, player.name) else player.name,
                style = MaterialTheme.typography.body1,
                fontWeight = if (player.isLocal) FontWeight.Bold else FontWeight.Normal,
            )
            val statusText = when (player.playerStatus) {
                LanPlayerStatus.HOST -> stringResource(R.string.lan_status_host)
                LanPlayerStatus.CLIENT -> stringResource(R.string.lan_status_connected)
                LanPlayerStatus.CONNECTING -> stringResource(R.string.lan_status_connecting)
                LanPlayerStatus.DISCONNECTED -> stringResource(R.string.lan_status_disconnected)
                LanPlayerStatus.UNKNOWN -> "?"
            }
            SecondaryText(if (player.address.isNotEmpty()) "$statusText · ${player.address}" else statusText)
        }
        if (!player.isLocal && (player.playerStatus == LanPlayerStatus.HOST || player.playerStatus == LanPlayerStatus.CLIENT)) {
            Text(
                text = stringResource(R.string.lan_ping, player.ping),
                style = MaterialTheme.typography.body2,
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        modifier = modifier,
        text = text,
        style = MaterialTheme.typography.h6,
    )
}

@Composable
private fun SecondaryText(text: String) {
    CompositionLocalProvider(LocalContentAlpha provides ContentAlpha.medium) {
        Text(text = text, style = MaterialTheme.typography.body2)
    }
}
