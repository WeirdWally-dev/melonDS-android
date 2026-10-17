package me.weirdwally.dslobby.net

import org.json.JSONArray
import org.json.JSONObject

data class Member(val id: String, val name: String, val colour: Int)

/**
 * A room as the host advertises it. [members] always starts with the host.
 * @param seq Identifies this particular room, so stale packets from an earlier room are ignored
 * @param starting The host pressed Start. Clients that missed the Go packet launch when they see this
 */
data class RoomInfo(
    val seq: Long,
    val gameCode: String,
    val title: String,
    val maxPlayers: Int,
    val members: List<Member>,
    val starting: Boolean,
) {
    val host: Member get() = members.first()
    val isFull: Boolean get() = members.size >= maxPlayers
}

/**
 * Lobby messages. Everything is a small JSON datagram on [Protocol.PORT]. Beacons are broadcast once a second by every
 * device with the lobby open; the rest are sent straight to one device.
 */
sealed class Message {
    abstract val senderId: String

    /** "I'm here", plus the room this device is hosting, if any */
    data class Beacon(override val senderId: String, val name: String, val colour: Int, val room: RoomInfo?) : Message()

    /** Sent by a client to its host every second while in the room. Doubles as a keepalive */
    data class Join(override val senderId: String, val name: String, val colour: Int, val seq: Long) : Message()

    data class Leave(override val senderId: String, val seq: Long) : Message()

    /** The host turned a client away, or the room the client asked about no longer exists */
    data class Deny(override val senderId: String, val seq: Long, val reason: DenyReason) : Message()

    /** The host pressed Start */
    data class Go(override val senderId: String, val seq: Long) : Message()
}

enum class DenyReason(val wire: String) {
    FULL("full"),
    CLOSED("closed");

    companion object {
        fun fromWire(value: String) = entries.firstOrNull { it.wire == value } ?: CLOSED
    }
}

object Protocol {
    const val PORT = 7090
    private const val MAGIC = "dslobby"
    private const val VERSION = 1

    fun encode(message: Message): ByteArray {
        val json = JSONObject()
            .put("p", MAGIC)
            .put("v", VERSION)
            .put("i", message.senderId)

        when (message) {
            is Message.Beacon -> {
                json.put("t", "beacon").put("n", message.name).put("c", message.colour)
                message.room?.let { json.put("r", encodeRoom(it)) }
            }
            is Message.Join -> json.put("t", "join").put("n", message.name).put("c", message.colour).put("s", message.seq)
            is Message.Leave -> json.put("t", "leave").put("s", message.seq)
            is Message.Deny -> json.put("t", "deny").put("s", message.seq).put("why", message.reason.wire)
            is Message.Go -> json.put("t", "go").put("s", message.seq)
        }
        return json.toString().toByteArray(Charsets.UTF_8)
    }

    fun decode(data: ByteArray, length: Int): Message? {
        return runCatching {
            val json = JSONObject(String(data, 0, length, Charsets.UTF_8))
            if (json.optString("p") != MAGIC || json.optInt("v") != VERSION) return null
            val sender = json.getString("i")

            when (json.getString("t")) {
                "beacon" -> Message.Beacon(
                    senderId = sender,
                    name = json.getString("n").take(MAX_NAME),
                    colour = json.optInt("c", 0).coerceIn(0, 15),
                    room = json.optJSONObject("r")?.let(::decodeRoom),
                )
                "join" -> Message.Join(sender, json.getString("n").take(MAX_NAME), json.optInt("c", 0).coerceIn(0, 15), json.getLong("s"))
                "leave" -> Message.Leave(sender, json.getLong("s"))
                "deny" -> Message.Deny(sender, json.getLong("s"), DenyReason.fromWire(json.optString("why")))
                "go" -> Message.Go(sender, json.getLong("s"))
                else -> null
            }
        }.getOrNull()
    }

    private const val MAX_NAME = 10

    private fun encodeRoom(room: RoomInfo): JSONObject {
        val members = JSONArray()
        room.members.forEach { members.put(JSONObject().put("i", it.id).put("n", it.name).put("c", it.colour)) }
        return JSONObject()
            .put("s", room.seq)
            .put("g", room.gameCode)
            .put("t", room.title)
            .put("m", room.maxPlayers)
            .put("p", members)
            .put("go", room.starting)
    }

    private fun decodeRoom(json: JSONObject): RoomInfo? {
        val membersJson = json.getJSONArray("p")
        val members = (0 until membersJson.length()).map { index ->
            val member = membersJson.getJSONObject(index)
            Member(member.getString("i"), member.getString("n").take(MAX_NAME), member.optInt("c", 0).coerceIn(0, 15))
        }
        if (members.isEmpty()) return null

        return RoomInfo(
            seq = json.getLong("s"),
            gameCode = json.getString("g"),
            title = json.getString("t").take(64),
            maxPlayers = json.getInt("m").coerceIn(2, 16),
            members = members,
            starting = json.optBoolean("go", false),
        )
    }
}
