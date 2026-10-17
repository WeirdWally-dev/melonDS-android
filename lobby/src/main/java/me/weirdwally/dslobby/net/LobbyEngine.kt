package me.weirdwally.dslobby.net

import android.content.Context
import android.net.wifi.WifiManager
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import me.weirdwally.dslobby.Profile
import me.weirdwally.dslobby.melon.LibraryGame
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import kotlin.concurrent.thread

data class NearbyPlayer(
    val id: String,
    val name: String,
    val colour: Int,
    val address: String,
    /** The room this player is hosting */
    val room: RoomInfo?,
    /** The host whose room this player is in, if they're in someone else's room */
    val inRoomOf: String?,
)

sealed class RoomState {
    data object None : RoomState()

    data class Hosting(val room: RoomInfo, val game: LibraryGame) : RoomState()

    /**
     * In someone else's room. [room] is the host's latest advertisement; [confirmed] once the host lists this player
     */
    data class InRoom(
        val hostId: String,
        val hostName: String,
        val hostAddress: String,
        val seq: Long,
        val game: LibraryGame,
        val room: RoomInfo,
        val confirmed: Boolean,
    ) : RoomState()
}

data class LobbyState(
    val isRunning: Boolean = false,
    /** Couldn't open the lobby port */
    val networkError: Boolean = false,
    val localAddress: String? = null,
    val players: List<NearbyPlayer> = emptyList(),
    val room: RoomState = RoomState.None,
)

sealed class LobbyEvent {
    data class LaunchAsHost(val game: LibraryGame, val maxPlayers: Int) : LobbyEvent()
    data class LaunchAsClient(val game: LibraryGame, val hostAddress: String, val hostName: String) : LobbyEvent()
    data class PlayerJoined(val name: String) : LobbyEvent()
    data class PlayerLeft(val name: String) : LobbyEvent()
    data object JoinedRoom : LobbyEvent()
    data class RoomEnded(val hostName: String, val reason: Reason) : LobbyEvent() {
        enum class Reason { CLOSED, FULL, LOST }
    }
}

/**
 * The lobby on the local network. Every phone with the lobby open broadcasts a beacon each second, so nearby players
 * show up on their own like on a DS. Rooms are hosted by one phone; joining, keepalives and the start signal are sent
 * directly between the phones.
 *
 * All state changes happen on one serial dispatcher, so handlers never race each other.
 */
class LobbyEngine(context: Context) {
    companion object {
        private const val TAG = "LobbyEngine"
        private const val TICK_MS = 1_000L
        private const val PEER_TIMEOUT_MS = 4_500L
        private const val MEMBER_TIMEOUT_MS = 5_000L
        private const val HOST_TIMEOUT_MS = 6_000L
        private const val STARTING_GRACE_MS = 3_000L
        private const val GO_REPEATS = 4
        private const val GO_INTERVAL_MS = 120L
    }

    private class Peer(
        val id: String,
        var name: String,
        var colour: Int,
        var address: InetAddress,
        var lastSeen: Long,
        var room: RoomInfo?,
    )

    private class MemberLink(val member: Member, var address: InetAddress, var lastSeen: Long)

    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val serial = Dispatchers.Default.limitedParallelism(1)

    private var scope: CoroutineScope? = null
    private var socket: DatagramSocket? = null
    private var multicastLock: WifiManager.MulticastLock? = null

    private var profile: Profile? = null
    private val peers = mutableMapOf<String, Peer>()
    private val members = linkedMapOf<String, MemberLink>()
    private var roomState: RoomState = RoomState.None
    private var startingSince = 0L
    private var networkError = false
    private var nextSeq = SystemClock.elapsedRealtime()
    private var broadcastTargets: List<InetAddress> = emptyList()
    private var broadcastTargetsAge = 0L

    private val _state = MutableStateFlow(LobbyState())
    val state: StateFlow<LobbyState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<LobbyEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<LobbyEvent> = _events.asSharedFlow()

    fun start(profile: Profile) {
        if (scope != null) {
            updateProfile(profile)
            return
        }
        this.profile = profile

        val newScope = CoroutineScope(SupervisorJob() + serial)
        scope = newScope

        multicastLock = wifiManager.createMulticastLock("dslobby").apply {
            setReferenceCounted(false)
            acquire()
        }

        val newSocket = try {
            DatagramSocket(null).apply {
                reuseAddress = true
                broadcast = true
                bind(InetSocketAddress(Protocol.PORT))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Couldn't open the lobby port", e)
            networkError = true
            _state.value = LobbyState(isRunning = true, networkError = true)
            return
        }
        socket = newSocket

        thread(name = "LobbyReceiver", isDaemon = true) { receiveLoop(newSocket, newScope) }
        newScope.launch {
            while (isActive) {
                tick()
                delay(TICK_MS)
            }
        }
        publish()
    }

    fun stop() {
        val currentScope = scope ?: return
        // Tell the room we're gone instead of letting it time out. Runs on the serial dispatcher (never the main
        // thread, which can't touch the network) and finishes before the socket closes
        runBlocking(serial) {
            withTimeoutOrNull(500L) { runCatching { sendGoodbye() } }
        }
        scope = null
        currentScope.cancel()
        socket?.close()
        socket = null
        multicastLock?.let { if (it.isHeld) it.release() }
        multicastLock = null
        peers.clear()
        members.clear()
        roomState = RoomState.None
        networkError = false
        _state.value = LobbyState()
    }

    fun updateProfile(profile: Profile) = onSerial {
        this@LobbyEngine.profile = profile
        sendBeacon()
        publish()
    }

    fun openRoom(game: LibraryGame, maxPlayers: Int) = onSerial {
        val me = profile ?: return@onSerial
        if (roomState !is RoomState.None) leaveCurrentRoom()

        members.clear()
        roomState = RoomState.Hosting(
            room = RoomInfo(
                seq = nextSeq++,
                gameCode = game.gameCode,
                title = game.name,
                maxPlayers = maxPlayers.coerceIn(2, 8),
                members = listOf(Member(me.id, me.name, me.colour)),
                starting = false,
            ),
            game = game,
        )
        sendBeacon()
        publish()
    }

    fun setMaxPlayers(maxPlayers: Int) = onSerial {
        val hosting = roomState as? RoomState.Hosting ?: return@onSerial
        val newMax = maxPlayers.coerceIn(maxOf(2, hosting.room.members.size), 8)
        roomState = hosting.copy(room = hosting.room.copy(maxPlayers = newMax))
        sendBeacon()
        publish()
    }

    fun joinRoom(hostId: String, game: LibraryGame) = onSerial {
        val host = peers[hostId] ?: return@onSerial
        val room = host.room ?: return@onSerial
        if (roomState !is RoomState.None) leaveCurrentRoom()

        roomState = RoomState.InRoom(
            hostId = host.id,
            hostName = host.name,
            hostAddress = host.address.hostAddress.orEmpty(),
            seq = room.seq,
            game = game,
            room = room,
            confirmed = false,
        )
        sendJoin()
        publish()
    }

    fun leaveRoom() = onSerial {
        leaveCurrentRoom()
        sendBeacon()
        publish()
    }

    fun startGame() = onSerial {
        val hosting = roomState as? RoomState.Hosting ?: return@onSerial
        if (hosting.room.members.size < 2 || hosting.room.starting) return@onSerial

        roomState = hosting.copy(room = hosting.room.copy(starting = true))
        startingSince = SystemClock.elapsedRealtime()
        val targets = members.values.map { it.address }
        // UDP can drop a packet, so say it a few times. Clients also launch when they see the starting flag in the beacon
        repeat(GO_REPEATS) {
            targets.forEach { send(Message.Go(myId(), hosting.room.seq), it) }
            sendBeacon()
            delay(GO_INTERVAL_MS)
        }
        _events.tryEmit(LobbyEvent.LaunchAsHost(hosting.game, hosting.room.maxPlayers))
        publish()
    }

    private fun onSerial(block: suspend CoroutineScope.() -> Unit) {
        scope?.launch(block = block)
    }

    private fun myId(): String = profile?.id.orEmpty()

    private fun receiveLoop(socket: DatagramSocket, scope: CoroutineScope) {
        val buffer = ByteArray(4096)
        while (!socket.isClosed) {
            val packet = DatagramPacket(buffer, buffer.size)
            try {
                socket.receive(packet)
            } catch (_: Exception) {
                break
            }
            val message = Protocol.decode(packet.data, packet.length) ?: continue
            val from = packet.address ?: continue
            scope.launch { handle(message, from) }
        }
    }

    private fun handle(message: Message, from: InetAddress) {
        if (message.senderId == myId()) return
        val now = SystemClock.elapsedRealtime()

        when (message) {
            is Message.Beacon -> onBeacon(message, from, now)
            is Message.Join -> onJoin(message, from, now)
            is Message.Leave -> {
                if ((roomState as? RoomState.Hosting)?.room?.seq == message.seq) {
                    members.remove(message.senderId)?.let { removed ->
                        rebuildHostedRoom()
                        _events.tryEmit(LobbyEvent.PlayerLeft(removed.member.name))
                    }
                }
            }
            is Message.Deny -> {
                val inRoom = roomState as? RoomState.InRoom
                if (inRoom != null && inRoom.hostId == message.senderId && inRoom.seq == message.seq) {
                    roomState = RoomState.None
                    val reason = if (message.reason == DenyReason.FULL) LobbyEvent.RoomEnded.Reason.FULL else LobbyEvent.RoomEnded.Reason.CLOSED
                    _events.tryEmit(LobbyEvent.RoomEnded(inRoom.hostName, reason))
                }
            }
            is Message.Go -> {
                val inRoom = roomState as? RoomState.InRoom
                if (inRoom != null && inRoom.hostId == message.senderId && inRoom.seq == message.seq) {
                    launchAsClient(inRoom, from)
                }
            }
        }
        publish()
    }

    private fun onBeacon(beacon: Message.Beacon, from: InetAddress, now: Long) {
        val peer = peers.getOrPut(beacon.senderId) { Peer(beacon.senderId, beacon.name, beacon.colour, from, now, beacon.room) }
        peer.name = beacon.name
        peer.colour = beacon.colour
        peer.address = from
        peer.lastSeen = now
        peer.room = beacon.room

        val inRoom = roomState as? RoomState.InRoom ?: return
        if (inRoom.hostId != beacon.senderId) return

        val room = beacon.room
        if (room == null || room.seq != inRoom.seq) {
            roomState = RoomState.None
            _events.tryEmit(LobbyEvent.RoomEnded(inRoom.hostName, LobbyEvent.RoomEnded.Reason.CLOSED))
            return
        }

        val listed = room.members.any { it.id == myId() }
        if (room.starting && listed) {
            launchAsClient(inRoom, from)
            return
        }
        if (listed && !inRoom.confirmed) {
            _events.tryEmit(LobbyEvent.JoinedRoom)
        }
        roomState = inRoom.copy(room = room, hostName = beacon.name, hostAddress = from.hostAddress.orEmpty(), confirmed = listed || inRoom.confirmed)
    }

    private fun onJoin(join: Message.Join, from: InetAddress, now: Long) {
        val hosting = roomState as? RoomState.Hosting
        if (hosting == null || hosting.room.seq != join.seq) {
            send(Message.Deny(myId(), join.seq, DenyReason.CLOSED), from)
            return
        }

        val existing = members[join.senderId]
        if (existing != null) {
            existing.address = from
            existing.lastSeen = now
            if (existing.member.name != join.name || existing.member.colour != join.colour) {
                members[join.senderId] = MemberLink(Member(join.senderId, join.name, join.colour), from, now)
                rebuildHostedRoom()
            }
            return
        }

        if (hosting.room.starting || hosting.room.isFull) {
            send(Message.Deny(myId(), join.seq, if (hosting.room.starting) DenyReason.CLOSED else DenyReason.FULL), from)
            return
        }

        members[join.senderId] = MemberLink(Member(join.senderId, join.name, join.colour), from, now)
        rebuildHostedRoom()
        _events.tryEmit(LobbyEvent.PlayerJoined(join.name))
        // Let everyone see the new player right away instead of on the next tick
        sendBeacon()
    }

    private fun launchAsClient(inRoom: RoomState.InRoom, from: InetAddress) {
        roomState = RoomState.None
        _events.tryEmit(LobbyEvent.LaunchAsClient(inRoom.game, from.hostAddress.orEmpty(), inRoom.hostName))
    }

    private fun tick() {
        val now = SystemClock.elapsedRealtime()

        peers.values.removeAll { now - it.lastSeen > PEER_TIMEOUT_MS }

        when (val current = roomState) {
            is RoomState.Hosting -> {
                if (current.room.starting) {
                    // Everyone has had their chance to see the start signal
                    if (now - startingSince > STARTING_GRACE_MS) {
                        members.clear()
                        roomState = RoomState.None
                    }
                } else {
                    val lost = members.values.filter { now - it.lastSeen > MEMBER_TIMEOUT_MS }
                    if (lost.isNotEmpty()) {
                        lost.forEach { members.remove(it.member.id) }
                        rebuildHostedRoom()
                        lost.forEach { _events.tryEmit(LobbyEvent.PlayerLeft(it.member.name)) }
                    }
                }
            }
            is RoomState.InRoom -> {
                val host = peers[current.hostId]
                if (host == null || now - host.lastSeen > HOST_TIMEOUT_MS) {
                    roomState = RoomState.None
                    _events.tryEmit(LobbyEvent.RoomEnded(current.hostName, LobbyEvent.RoomEnded.Reason.LOST))
                } else {
                    sendJoin()
                }
            }
            RoomState.None -> Unit
        }

        sendBeacon()
        publish()
    }

    private fun rebuildHostedRoom() {
        val hosting = roomState as? RoomState.Hosting ?: return
        val me = profile ?: return
        roomState = hosting.copy(
            room = hosting.room.copy(members = listOf(Member(me.id, me.name, me.colour)) + members.values.map { it.member })
        )
    }

    private fun leaveCurrentRoom() {
        when (val current = roomState) {
            is RoomState.Hosting -> {
                // After Start, the players are on their way into the game; don't call them back
                if (!current.room.starting) {
                    members.values.forEach { send(Message.Deny(myId(), current.room.seq, DenyReason.CLOSED), it.address) }
                }
                members.clear()
            }
            is RoomState.InRoom -> {
                peers[current.hostId]?.let { send(Message.Leave(myId(), current.seq), it.address) }
            }
            RoomState.None -> Unit
        }
        roomState = RoomState.None
    }

    private fun sendGoodbye() {
        leaveCurrentRoom()
        sendBeacon()
    }

    private fun sendJoin() {
        val inRoom = roomState as? RoomState.InRoom ?: return
        val me = profile ?: return
        val host = peers[inRoom.hostId] ?: return
        send(Message.Join(me.id, me.name, me.colour, inRoom.seq), host.address)
    }

    private fun sendBeacon() {
        val me = profile ?: return
        val room = (roomState as? RoomState.Hosting)?.room
        val beacon = Message.Beacon(me.id, me.name, me.colour, room)
        getBroadcastTargets().forEach { send(beacon, it) }
    }

    private fun send(message: Message, to: InetAddress) {
        val socket = socket ?: return
        val data = Protocol.encode(message)
        try {
            socket.send(DatagramPacket(data, data.size, to, Protocol.PORT))
        } catch (e: Exception) {
            // Happens when Wi-Fi drops; the next tick tries again
            Log.d(TAG, "Send to $to failed: ${e.message}")
        }
    }

    /**
     * Broadcast addresses of every active IPv4 network, refreshed every few seconds so switching Wi-Fi or turning on a
     * hotspot is picked up. The limited broadcast address is kept as a fallback.
     */
    private fun getBroadcastTargets(): List<InetAddress> {
        val now = SystemClock.elapsedRealtime()
        if (broadcastTargets.isNotEmpty() && now - broadcastTargetsAge < 5_000L) return broadcastTargets

        val directed = runCatching {
            NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.interfaceAddresses }
                .mapNotNull { it.broadcast }
        }.getOrDefault(emptyList())

        broadcastTargets = (directed + InetAddress.getByName("255.255.255.255")).distinct()
        broadcastTargetsAge = now
        return broadcastTargets
    }

    private fun findLocalAddress(): String? {
        return runCatching {
            NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
                .filter { it.isUp && !it.isLoopback }
                .sortedByDescending { iface -> listOf("wlan", "ap", "swlan", "eth").any { iface.name.startsWith(it) } }
                .flatMap { it.inetAddresses.toList() }
                .filterIsInstance<Inet4Address>()
                .firstOrNull { it.isSiteLocalAddress }
                ?.hostAddress
        }.getOrNull()
    }

    private fun publish() {
        if (scope == null) return
        val hostOf = mutableMapOf<String, String>()
        peers.values.forEach { peer ->
            peer.room?.members?.drop(1)?.forEach { member -> hostOf[member.id] = peer.id }
        }
        (roomState as? RoomState.Hosting)?.room?.members?.drop(1)?.forEach { hostOf[it.id] = myId() }

        _state.value = LobbyState(
            isRunning = true,
            networkError = networkError,
            localAddress = findLocalAddress(),
            players = peers.values
                .map { NearbyPlayer(it.id, it.name, it.colour, it.address.hostAddress.orEmpty(), it.room, hostOf[it.id]) }
                .sortedBy { it.name.lowercase() },
            room = roomState,
        )
    }
}
