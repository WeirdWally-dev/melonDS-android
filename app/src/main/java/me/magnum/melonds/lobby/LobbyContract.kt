package me.magnum.melonds.lobby

/**
 * Contract between melonDS and the DS Lobby companion app (the :lobby module). The lobby finds nearby players and
 * forms a room; melonDS owns the actual LAN session and the emulation.
 *
 * The lobby keeps a copy of these values in its own LobbyContract. Keep both in sync.
 */
object LobbyContract {
    /** Activity action handled by [me.magnum.melonds.ui.lan.LanLaunchActivity]. Fixed, so the lobby can find every installed variant */
    const val ACTION_LAN_LAUNCH = "me.magnum.melonds.action.LAN_LAUNCH"

    const val EXTRA_ROLE = "me.magnum.melonds.extra.LAN_ROLE"
    const val EXTRA_HOST_ADDRESS = "me.magnum.melonds.extra.LAN_HOST_ADDRESS"
    const val EXTRA_HOST_NAME = "me.magnum.melonds.extra.LAN_HOST_NAME"
    const val EXTRA_PLAYER_NAME = "me.magnum.melonds.extra.LAN_PLAYER_NAME"
    const val EXTRA_MAX_PLAYERS = "me.magnum.melonds.extra.LAN_MAX_PLAYERS"
    const val EXTRA_ROM_URI = "me.magnum.melonds.extra.LAN_ROM_URI"
    /** DS favourite colour index (0-15). Optional; -1 or missing leaves the firmware colour alone */
    const val EXTRA_FAVOURITE_COLOUR = "me.magnum.melonds.extra.LAN_FAVOURITE_COLOUR"

    const val ROLE_HOST = "host"
    const val ROLE_JOIN = "join"

    /** The library provider's authority is the melonDS package name plus this suffix */
    const val LIBRARY_AUTHORITY_SUFFIX = ".lobbylibrary"
    const val LIBRARY_PATH_ROMS = "roms"
    const val LIBRARY_PATH_ICON = "icon"

    const val COLUMN_ID = "_id"
    const val COLUMN_NAME = "name"
    const val COLUMN_GAME_CODE = "game_code"
    const val COLUMN_ROM_URI = "rom_uri"

    /** Only the lobby app may read the ROM library */
    const val LOBBY_PACKAGE_PREFIX = "me.weirdwally.dslobby"
}
