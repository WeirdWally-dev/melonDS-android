package me.magnum.melonds.domain.model.lan

sealed class LanSessionState {
    /** No session and not looking for one */
    data object Idle : LanSessionState()

    /** Listening for hosts on the local network */
    data class Browsing(val sessions: List<LanDiscoveredSession>) : LanSessionState()

    /** A host or join request is in progress */
    data class Connecting(val isHost: Boolean, val hostAddress: String?) : LanSessionState()

    /** In a session, either as the host or as a client */
    data class InSession(
        val isHost: Boolean,
        val players: List<LanPlayer>,
        val maxPlayers: Int,
        /** This device's IPv4 address on the current Wi-Fi network, if known. Shown to the host so others can join by IP */
        val localAddress: String?,
    ) : LanSessionState()

    val isInSession: Boolean get() = this is InSession
}
