package me.magnum.melonds.domain.model.lan

/**
 * A LAN session advertised by a host on the local network. Instantiated from native code.
 */
data class LanDiscoveredSession(
    val address: String,
    val name: String,
    val playerCount: Int,
    val maxPlayers: Int,
    val isPlaying: Boolean,
)
