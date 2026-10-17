package me.magnum.melonds.domain.model.lan

/**
 * A player in the current LAN session. Instantiated from native code.
 *
 * @param id The player slot (0 is always the host)
 * @param status Raw status value from melonDS. Use [playerStatus] instead
 * @param address The player's IPv4 address. Empty for the local player
 * @param ping Round trip time in milliseconds
 */
data class LanPlayer(
    val id: Int,
    val name: String,
    val status: Int,
    val address: String,
    val isLocal: Boolean,
    val ping: Int,
) {
    val playerStatus: LanPlayerStatus get() = LanPlayerStatus.fromNative(status)
}

enum class LanPlayerStatus {
    CLIENT,
    HOST,
    CONNECTING,
    DISCONNECTED,
    UNKNOWN;

    companion object {
        // Mirrors LAN::PlayerStatus in melonDS (0 = no player)
        fun fromNative(value: Int): LanPlayerStatus {
            return when (value) {
                1 -> CLIENT
                2 -> HOST
                3 -> CONNECTING
                4 -> DISCONNECTED
                else -> UNKNOWN
            }
        }
    }
}
