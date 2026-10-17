package me.weirdwally.dslobby.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.weirdwally.dslobby.DsColours
import me.weirdwally.dslobby.LobbyViewModel
import me.weirdwally.dslobby.Profile
import me.weirdwally.dslobby.melon.LibraryGame
import me.weirdwally.dslobby.net.RoomState

@Composable
fun GamePickerScreen(
    profile: Profile,
    games: List<LibraryGame>,
    loadIcon: suspend (LibraryGame) -> ImageBitmap?,
    onPick: (LibraryGame) -> Unit,
    onBack: () -> Unit,
) {
    DsShell(
        top = {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Title("Open a room")
                Gap(6.dp)
                BodyText(
                    "Pick a game. Nearby players who have it can join, then you start everyone together.",
                    color = LobbyColors.muted,
                    center = true,
                )
            }
        },
        bottom = {
            Column(modifier = Modifier.fillMaxSize()) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 92.dp),
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(games, key = { it.id }) { game ->
                        Column(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onPick(game) }
                                .padding(6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            GameIcon(game, loadIcon, 64.dp)
                            Gap(4.dp)
                            Text(
                                text = game.name,
                                color = LobbyColors.text,
                                fontSize = 12.sp,
                                lineHeight = 14.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
                Gap(10.dp)
                Row {
                    DsButton("Back", onClick = onBack, modifier = Modifier.weight(1f))
                }
            }
        },
    )
}

/** A room, from the host's side or a player's side */
@Composable
fun RoomScreen(
    profile: Profile,
    roomState: RoomState,
    notice: String?,
    loadIcon: suspend (LibraryGame) -> ImageBitmap?,
    onMaxPlayersChange: (Int) -> Unit,
    onStart: () -> Unit,
    onLeave: () -> Unit,
) {
    val (room, game, isHost) = when (roomState) {
        is RoomState.Hosting -> Triple(roomState.room, roomState.game, true)
        is RoomState.InRoom -> Triple(roomState.room, roomState.game, false)
        RoomState.None -> return
    }
    val hostColour = room.host.colour

    DsShell(
        top = {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                GameIcon(game, loadIcon, 80.dp)
                Gap(10.dp)
                Title(room.title, maxLines = 1)
                Gap(4.dp)
                val status = when {
                    notice != null -> notice
                    isHost && room.members.size < 2 -> "Waiting for players to join"
                    isHost -> "Press Start when everyone's here"
                    roomState is RoomState.InRoom && !roomState.confirmed -> "Joining ${room.host.name}'s room…"
                    else -> "Waiting for ${room.host.name} to start"
                }
                BodyText(status, color = LobbyColors.muted, center = true)
            }
        },
        bottom = {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionTitle("PLAYERS ${room.members.size}/${room.maxPlayers}", Modifier.weight(1f))
                    if (isHost) {
                        Stepper(
                            value = room.maxPlayers,
                            canDecrease = room.maxPlayers > maxOf(2, room.members.size),
                            canIncrease = room.maxPlayers < 8,
                            onChange = onMaxPlayersChange,
                        )
                    }
                }
                Gap(8.dp)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    room.members.forEachIndexed { index, member ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            NameTag(member.name, member.colour)
                            HGap(10.dp)
                            val label = buildList {
                                if (index == 0) add("Host")
                                if (member.id == profile.id) add("You")
                            }.joinToString(" · ")
                            Text(label, color = LobbyColors.muted, fontSize = 14.sp)
                        }
                    }
                    repeat((room.maxPlayers - room.members.size).coerceAtLeast(0)) {
                        EmptySlot()
                    }
                }
                Gap(10.dp)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DsButton(if (isHost) "Close" else "Leave", onClick = onLeave, modifier = Modifier.weight(1f))
                    if (isHost) {
                        DsButton(
                            text = "Start",
                            onClick = onStart,
                            enabled = room.members.size >= 2 && !room.starting,
                            accent = DsColours[hostColour],
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        },
    )
}

@Composable
private fun Stepper(value: Int, canDecrease: Boolean, canIncrease: Boolean, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        StepButton("−", enabled = canDecrease) { onChange(value - 1) }
        Text("Max $value", color = LobbyColors.text, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 10.dp))
        StepButton("+", enabled = canIncrease) { onChange(value + 1) }
    }
}

@Composable
private fun StepButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(if (enabled) Color.White else LobbyColors.stripe)
            .border(2.dp, LobbyColors.panelBorder, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (enabled) LobbyColors.text else LobbyColors.disabled, fontWeight = FontWeight.Bold, fontSize = 20.sp)
    }
}

@Composable
fun LaunchingScreen(launching: LobbyViewModel.Launching, profile: Profile) {
    DsShell(
        top = {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                SignalPulse(DsColours[profile.colour])
                Gap(12.dp)
                Title(launching.gameTitle, maxLines = 1)
            }
        },
        bottom = {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator(color = DsColours[profile.colour])
                Gap(14.dp)
                BodyText(
                    text = if (launching.hostName != null) "Connecting to ${launching.hostName}…" else "Starting the session…",
                    center = true,
                )
                Gap(4.dp)
                BodyText("Then open the game's wireless mode, just like on a DS.", color = LobbyColors.muted, center = true)
            }
        },
    )
}
