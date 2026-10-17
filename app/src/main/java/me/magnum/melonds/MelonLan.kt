package me.magnum.melonds

import me.magnum.melonds.domain.model.lan.LanDiscoveredSession
import me.magnum.melonds.domain.model.lan.LanPlayer

/**
 * Bridge to the native LAN multiplayer session (melonDS's LAN interface). Calls that touch the network can block
 * (notably [startClient], which waits up to 5 seconds for the host), so never call these from the main thread.
 */
object MelonLan {
    const val MODE_NONE = 0
    const val MODE_BROWSING = 1
    const val MODE_HOSTING = 2
    const val MODE_CLIENT = 3

    external fun startDiscovery(): Boolean
    external fun endDiscovery()
    external fun startHost(playerName: String, maxPlayers: Int): Boolean
    external fun startClient(playerName: String, hostAddress: String): Boolean
    external fun endSession()
    external fun getMode(): Int
    external fun getMaxPlayers(): Int
    external fun getDiscoveredSessions(): Array<LanDiscoveredSession>
    external fun getPlayers(): Array<LanPlayer>
}
