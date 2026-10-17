package me.weirdwally.dslobby

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.core.content.edit
import java.util.UUID

/** The 16 favourite colours from the DS system settings, in firmware order */
object DsColours {
    val all = listOf(
        Color(0xFF61829A), // Grey
        Color(0xFFBA4900), // Brown
        Color(0xFFFB0018), // Red
        Color(0xFFFB8AFB), // Pink
        Color(0xFFFB9200), // Orange
        Color(0xFFF3E300), // Yellow
        Color(0xFFAAFB00), // Lime
        Color(0xFF00FB00), // Green
        Color(0xFF00A238), // Dark green
        Color(0xFF49DB8A), // Sea green
        Color(0xFF30BAF3), // Turquoise
        Color(0xFF0059F3), // Blue
        Color(0xFF000092), // Dark blue
        Color(0xFF8A00D3), // Purple
        Color(0xFFD300EB), // Violet
        Color(0xFFFB0092), // Magenta
    )

    operator fun get(index: Int): Color = all[index.coerceIn(0, all.lastIndex)]

    /** Whether text on top of this colour should be dark */
    fun needsDarkText(index: Int): Boolean = index in setOf(3, 5, 6, 7, 9)
}

data class Profile(
    val id: String,
    val name: String,
    val colour: Int,
    /** Package of the melonDS build to launch, if the player picked one */
    val melonPackage: String?,
) {
    val isComplete: Boolean get() = name.isNotBlank()

    companion object {
        /** Same limit as the DS firmware nickname */
        const val MAX_NAME_LENGTH = 10
    }
}

class ProfileStore(context: Context) {
    private val prefs = context.getSharedPreferences("profile", Context.MODE_PRIVATE)

    fun load(): Profile {
        val id = prefs.getString("id", null) ?: UUID.randomUUID().toString().take(8).also { newId ->
            prefs.edit { putString("id", newId) }
        }
        return Profile(
            id = id,
            name = prefs.getString("name", "").orEmpty(),
            colour = prefs.getInt("colour", 10),
            melonPackage = prefs.getString("melon_package", null),
        )
    }

    fun save(profile: Profile) {
        prefs.edit {
            putString("name", profile.name.trim().take(Profile.MAX_NAME_LENGTH))
            putInt("colour", profile.colour)
            putString("melon_package", profile.melonPackage)
        }
    }
}
