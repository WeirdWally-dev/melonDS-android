package me.magnum.melonds.lobby

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.graphics.Bitmap
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.content.edit
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import me.magnum.melonds.common.romprocessors.RomFileProcessorFactory
import me.magnum.melonds.domain.model.rom.Rom
import me.magnum.melonds.domain.repositories.RomsRepository
import me.magnum.melonds.impl.RomIconProvider
import java.io.File
import java.io.FileNotFoundException

/**
 * Read-only view of the ROM library for the DS Lobby app, so players never have to point the lobby at their ROMs
 * separately. Rows: id, display name, 4-character game code (used to match the same game across devices) and the ROM
 * URI melonDS launches. Icons are served as PNG at icon/<id>.
 */
class LobbyLibraryProvider : ContentProvider() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface LobbyLibraryEntryPoint {
        fun romsRepository(): RomsRepository
        fun romFileProcessorFactory(): RomFileProcessorFactory
        fun romIconProvider(): RomIconProvider
    }

    companion object {
        private const val TAG = "LobbyLibraryProvider"
        private const val GAME_CODE_CACHE = "lobby_game_codes"
        private const val ROM_LOAD_TIMEOUT_MS = 10_000L
    }

    private val entryPoint by lazy {
        EntryPointAccessors.fromApplication(requireContext().applicationContext, LobbyLibraryEntryPoint::class.java)
    }

    // Reading the game code means opening the ROM (and decompressing it for archives), so remember it per URI
    private val gameCodeCache by lazy {
        requireContext().getSharedPreferences(GAME_CODE_CACHE, Context.MODE_PRIVATE)
    }

    override fun onCreate(): Boolean = true

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? {
        enforceLobbyCaller()
        if (uri.pathSegments.firstOrNull() != LobbyContract.LIBRARY_PATH_ROMS) return null

        val cursor = MatrixCursor(arrayOf(LobbyContract.COLUMN_ID, LobbyContract.COLUMN_NAME, LobbyContract.COLUMN_GAME_CODE, LobbyContract.COLUMN_ROM_URI))
        loadRoms().forEach { rom ->
            val gameCode = getGameCode(rom) ?: return@forEach
            cursor.addRow(arrayOf(romId(rom), rom.name, gameCode, rom.uri.toString()))
        }
        return cursor
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        enforceLobbyCaller()
        val segments = uri.pathSegments
        if (segments.size != 2 || segments[0] != LobbyContract.LIBRARY_PATH_ICON || mode != "r") {
            throw FileNotFoundException(uri.toString())
        }

        val id = segments[1].toLongOrNull() ?: throw FileNotFoundException(uri.toString())
        val rom = loadRoms().firstOrNull { romId(it) == id } ?: throw FileNotFoundException(uri.toString())
        val iconFile = File(requireContext().cacheDir, "lobby_icons/$id.png")
        if (!iconFile.isFile) {
            val icon = runBlocking { entryPoint.romIconProvider().getRomIcon(rom) } ?: throw FileNotFoundException(uri.toString())
            iconFile.parentFile?.mkdirs()
            iconFile.outputStream().use { icon.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        return ParcelFileDescriptor.open(iconFile, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun getType(uri: Uri): String? {
        return when (uri.pathSegments.firstOrNull()) {
            LobbyContract.LIBRARY_PATH_ICON -> "image/png"
            LobbyContract.LIBRARY_PATH_ROMS -> "vnd.android.cursor.dir/vnd.melonds.rom"
            else -> null
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException()
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException()
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException()

    private fun enforceLobbyCaller() {
        val caller = callingPackage
        val ownPackage = requireContext().packageName
        if (caller != ownPackage && caller?.startsWith(LobbyContract.LOBBY_PACKAGE_PREFIX) != true) {
            throw SecurityException("The ROM library is only available to the DS Lobby app")
        }
    }

    private fun loadRoms(): List<Rom> {
        return runBlocking {
            withTimeoutOrNull(ROM_LOAD_TIMEOUT_MS) { entryPoint.romsRepository().getRoms().first() }
        }.orEmpty().filterNot { it.isDsiWareTitle }
    }

    private fun romId(rom: Rom): Long {
        // Stable across queries and processes, which is all the lobby needs to fetch the matching icon
        return rom.uri.toString().hashCode().toLong() and 0xFFFFFFFFL
    }

    private fun getGameCode(rom: Rom): String? {
        val key = rom.uri.toString()
        gameCodeCache.getString(key, null)?.let { return it }

        val gameCode = runCatching {
            entryPoint.romFileProcessorFactory().getFileRomProcessorForDocument(rom.uri)?.getRomInfo(rom)?.gameCode
        }.onFailure {
            Log.w(TAG, "Couldn't read the game code of ${rom.name}", it)
        }.getOrNull()?.trim()?.takeIf { it.length == 4 }

        if (gameCode != null) {
            gameCodeCache.edit { putString(key, gameCode) }
        }
        return gameCode
    }
}
