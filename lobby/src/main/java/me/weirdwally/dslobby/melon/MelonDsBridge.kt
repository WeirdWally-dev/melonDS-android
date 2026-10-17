package me.weirdwally.dslobby.melon

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.util.Log
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A melonDS build installed on this phone that can take lobby launches */
data class MelonInstall(val packageName: String, val label: String)

/** A game from melonDS's ROM list */
data class LibraryGame(
    val id: Long,
    val name: String,
    /** 4-character game code from the ROM header. The last character is the region */
    val gameCode: String,
    val romUri: String,
    val iconUri: Uri,
) {
    /** Same game, any region. Most DS games link up across regions */
    val baseCode: String get() = gameCode.take(3)
}

sealed class LibraryResult {
    data class Loaded(val games: List<LibraryGame>) : LibraryResult()
    /** The installed melonDS predates the lobby hooks */
    data object Outdated : LibraryResult()
    data class Failed(val message: String) : LibraryResult()
}

/**
 * Everything the lobby needs from melonDS: which builds are installed, their ROM lists, and launching a game with a LAN
 * session already set up.
 */
class MelonDsBridge(private val context: Context) {
    companion object {
        private const val TAG = "MelonDsBridge"
    }

    private val iconCache = LruCache<Uri, ImageBitmap>(64)

    fun findInstalls(): List<MelonInstall> {
        val intent = Intent(LobbyContract.ACTION_LAN_LAUNCH)
        val packageManager = context.packageManager
        val matches: List<ResolveInfo> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(intent, 0)
        }

        return matches
            .map { it.activityInfo.packageName }
            .distinct()
            .map { packageName ->
                val label = runCatching {
                    val appInfo = packageManager.getApplicationInfo(packageName, 0)
                    packageManager.getApplicationLabel(appInfo).toString()
                }.getOrDefault(packageName)
                MelonInstall(packageName, describe(label, packageName))
            }
            .sortedBy { it.packageName.length }
    }

    suspend fun loadLibrary(melonPackage: String): LibraryResult = withContext(Dispatchers.IO) {
        val uri = libraryUri(melonPackage).buildUpon().appendPath(LobbyContract.LIBRARY_PATH_ROMS).build()
        try {
            val cursor = context.contentResolver.query(uri, null, null, null, null) ?: return@withContext LibraryResult.Outdated
            val games = cursor.use {
                val idColumn = it.getColumnIndexOrThrow(LobbyContract.COLUMN_ID)
                val nameColumn = it.getColumnIndexOrThrow(LobbyContract.COLUMN_NAME)
                val codeColumn = it.getColumnIndexOrThrow(LobbyContract.COLUMN_GAME_CODE)
                val uriColumn = it.getColumnIndexOrThrow(LobbyContract.COLUMN_ROM_URI)
                buildList {
                    while (it.moveToNext()) {
                        val id = it.getLong(idColumn)
                        add(
                            LibraryGame(
                                id = id,
                                name = it.getString(nameColumn),
                                gameCode = it.getString(codeColumn),
                                romUri = it.getString(uriColumn),
                                iconUri = libraryUri(melonPackage).buildUpon()
                                    .appendPath(LobbyContract.LIBRARY_PATH_ICON)
                                    .appendPath(id.toString())
                                    .build(),
                            )
                        )
                    }
                }
            }
            LibraryResult.Loaded(games.sortedBy { it.name.lowercase() })
        } catch (e: SecurityException) {
            Log.w(TAG, "melonDS refused the library request", e)
            LibraryResult.Failed(e.message ?: "Permission denied")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load the melonDS library", e)
            LibraryResult.Outdated
        }
    }

    suspend fun loadIcon(game: LibraryGame): ImageBitmap? {
        iconCache.get(game.iconUri)?.let { return it }
        return withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openInputStream(game.iconUri)?.use { BitmapFactory.decodeStream(it) }
            }.getOrNull()?.asImageBitmap()?.also { iconCache.put(game.iconUri, it) }
        }
    }

    fun buildHostIntent(melonPackage: String, game: LibraryGame, playerName: String, colour: Int, maxPlayers: Int): Intent {
        return baseIntent(melonPackage, game, playerName, colour).apply {
            putExtra(LobbyContract.EXTRA_ROLE, LobbyContract.ROLE_HOST)
            putExtra(LobbyContract.EXTRA_MAX_PLAYERS, maxPlayers)
        }
    }

    fun buildJoinIntent(melonPackage: String, game: LibraryGame, playerName: String, colour: Int, hostAddress: String, hostName: String): Intent {
        return baseIntent(melonPackage, game, playerName, colour).apply {
            putExtra(LobbyContract.EXTRA_ROLE, LobbyContract.ROLE_JOIN)
            putExtra(LobbyContract.EXTRA_HOST_ADDRESS, hostAddress)
            putExtra(LobbyContract.EXTRA_HOST_NAME, hostName)
        }
    }

    private fun baseIntent(melonPackage: String, game: LibraryGame, playerName: String, colour: Int): Intent {
        return Intent(LobbyContract.ACTION_LAN_LAUNCH).apply {
            setPackage(melonPackage)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(LobbyContract.EXTRA_ROM_URI, game.romUri)
            putExtra(LobbyContract.EXTRA_PLAYER_NAME, playerName)
            putExtra(LobbyContract.EXTRA_FAVOURITE_COLOUR, colour)
        }
    }

    private fun libraryUri(melonPackage: String): Uri {
        return Uri.Builder()
            .scheme("content")
            .authority(melonPackage + LobbyContract.LIBRARY_AUTHORITY_SUFFIX)
            .build()
    }

    private fun describe(label: String, packageName: String): String {
        return when {
            packageName.endsWith(".nightly") -> "$label (nightly)"
            packageName.endsWith(".dev") -> "$label (dev)"
            else -> label
        }
    }
}
