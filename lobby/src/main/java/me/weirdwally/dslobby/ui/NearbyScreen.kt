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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import me.weirdwally.dslobby.DsColours
import me.weirdwally.dslobby.Profile
import me.weirdwally.dslobby.melon.LibraryGame
import me.weirdwally.dslobby.melon.LibraryResult
import me.weirdwally.dslobby.net.LobbyState
import me.weirdwally.dslobby.net.NearbyPlayer
import me.weirdwally.dslobby.net.RoomInfo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun NearbyScreen(
    profile: Profile,
    lobby: LobbyState,
    library: LibraryResult?,
    hasInstall: Boolean,
    notice: String?,
    findMatchingGame: (RoomInfo) -> LibraryGame?,
    loadIcon: suspend (LibraryGame) -> ImageBitmap?,
    onJoin: (NearbyPlayer) -> Unit,
    onOpenRoom: () -> Unit,
    onEditProfile: () -> Unit,
    onRetryNetwork: () -> Unit,
) {
    DsShell(
        top = { TopStatus(profile, lobby, notice) },
        bottom = {
            Column(modifier = Modifier.fillMaxSize()) {
                LibraryProblem(library, hasInstall)

                val hosts = lobby.players.filter { it.room != null }
                val others = lobby.players.filter { it.room == null }

                Box(modifier = Modifier.weight(1f)) {
                    when {
                        lobby.networkError -> CenteredMessage(
                            title = "Can't reach the network",
                            body = "Another app may be using the lobby's port, or Wi-Fi is off.",
                            action = "Try again" to onRetryNetwork,
                        )
                        lobby.localAddress == null -> CenteredMessage(
                            title = "Not on Wi-Fi",
                            body = "Connect to the same Wi-Fi as your friends, or join someone's hotspot.",
                        )
                        lobby.players.isEmpty() -> Searching(profile.colour)
                        else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (hosts.isNotEmpty()) {
                                item { SectionTitle("OPEN ROOMS") }
                                items(hosts, key = { "host-" + it.id }) { player ->
                                    RoomRow(player, player.room!!, findMatchingGame(player.room), loadIcon, onJoin)
                                }
                            }
                            if (others.isNotEmpty()) {
                                item { SectionTitle("NEARBY", Modifier.padding(top = if (hosts.isEmpty()) 0.dp else 8.dp)) }
                                items(others, key = { "player-" + it.id }) { player ->
                                    PlayerRow(player, lobby.players)
                                }
                            }
                        }
                    }
                }
                Gap(10.dp)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DsButton("Profile", onClick = onEditProfile)
                    DsButton(
                        text = "Open a room",
                        onClick = onOpenRoom,
                        enabled = library is LibraryResult.Loaded && library.games.isNotEmpty() && !lobby.networkError,
                        accent = DsColours[profile.colour],
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        },
    )
}

@Composable
private fun TopStatus(profile: Profile, lobby: LobbyState, notice: String?) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(5_000)
        }
    }
    val time = remember(now) { SimpleDateFormat("H:mm", Locale.getDefault()).format(Date(now)) }
    val date = remember(now) { SimpleDateFormat("EEE d MMM", Locale.getDefault()).format(Date(now)) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            NameTag(profile.name, profile.colour)
            Box(Modifier.weight(1f))
            WifiBars(active = lobby.localAddress != null)
        }
        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(time, color = LobbyColors.text, fontSize = 52.sp, fontWeight = FontWeight.Bold)
                Text(date, color = LobbyColors.muted, fontSize = 15.sp)
            }
        }
        Text(
            text = notice ?: when (val count = lobby.players.size) {
                0 -> "No one nearby yet"
                1 -> "1 player nearby"
                else -> "$count players nearby"
            },
            color = if (notice != null) LobbyColors.text else LobbyColors.muted,
            fontWeight = if (notice != null) FontWeight.Bold else FontWeight.Normal,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Four little bars, lit when on Wi-Fi. The DS showed the same thing while looking for players */
@Composable
fun WifiBars(active: Boolean) {
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        listOf(6, 10, 14, 18).forEach { height ->
            Box(
                Modifier
                    .padding(top = (18 - height).dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(if (active) LobbyColors.text else LobbyColors.disabled)
                    .padding(horizontal = 2.dp, vertical = (height / 2).dp)
            )
        }
    }
}

@Composable
private fun LibraryProblem(library: LibraryResult?, hasInstall: Boolean) {
    val message = when {
        !hasInstall -> "melonDS with local multiplayer isn't installed. Open Profile for a download link."
        library is LibraryResult.Outdated -> "Your melonDS is too old for the lobby. Update to the latest LAN build."
        library is LibraryResult.Failed -> "melonDS wouldn't share its game list: ${library.message}"
        library is LibraryResult.Loaded && library.games.isEmpty() -> "melonDS has no games yet. Add your ROM folder in melonDS first."
        else -> null
    } ?: return

    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .clip(shape)
            .background(Color(0xFFFFF4D6))
            .border(1.dp, Color(0xFFE6C677), shape)
            .padding(12.dp),
    ) {
        BodyText(message)
    }
}

@Composable
private fun Searching(colour: Int) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SignalPulse(DsColours[colour])
        Gap(12.dp)
        BodyText("Looking for players…", center = true)
        Gap(4.dp)
        BodyText("Open DS Lobby on another phone on the same Wi-Fi.", color = LobbyColors.muted, center = true)
    }
}

@Composable
fun CenteredMessage(title: String, body: String, action: Pair<String, () -> Unit>? = null) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Title(title)
        Gap(6.dp)
        BodyText(body, color = LobbyColors.muted, center = true)
        if (action != null) {
            Gap(14.dp)
            DsButton(action.first, onClick = action.second)
        }
    }
}

@Composable
private fun RoomRow(
    host: NearbyPlayer,
    room: RoomInfo,
    localGame: LibraryGame?,
    loadIcon: suspend (LibraryGame) -> ImageBitmap?,
    onJoin: (NearbyPlayer) -> Unit,
) {
    val canJoin = localGame != null && !room.isFull && !room.starting
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color.White)
            .border(2.dp, DsColours[host.colour], shape)
            .clickable(enabled = canJoin) { onJoin(host) }
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GameIcon(localGame, loadIcon, 48.dp)
        HGap(12.dp)
        Column(modifier = Modifier.weight(1f)) {
            Text(room.title, color = LobbyColors.text, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                NameTag(host.name, host.colour)
                HGap(8.dp)
                Text("${room.members.size}/${room.maxPlayers}", color = LobbyColors.muted, fontSize = 14.sp)
            }
            val status = when {
                localGame == null -> "You don't have this game"
                room.starting -> "Starting…"
                room.isFull -> "Full"
                localGame.gameCode != room.gameCode -> "Your copy is from another region"
                else -> null
            }
            if (status != null) {
                Text(status, color = LobbyColors.muted, fontSize = 12.sp)
            }
        }
        if (canJoin) {
            HGap(8.dp)
            DsButton("Join", onClick = { onJoin(host) }, accent = DsColours[host.colour])
        }
    }
}

@Composable
private fun PlayerRow(player: NearbyPlayer, everyone: List<NearbyPlayer>) {
    val hostName = player.inRoomOf?.let { hostId -> everyone.firstOrNull { it.id == hostId }?.name }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NameTag(player.name, player.colour)
        HGap(10.dp)
        Text(
            text = if (hostName != null) "In $hostName's room" else "Looking around",
            color = LobbyColors.muted,
            fontSize = 14.sp,
        )
    }
}
