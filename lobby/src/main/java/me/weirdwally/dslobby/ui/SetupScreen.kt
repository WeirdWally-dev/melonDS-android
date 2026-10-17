package me.weirdwally.dslobby.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import me.weirdwally.dslobby.DsColours
import me.weirdwally.dslobby.Profile
import me.weirdwally.dslobby.melon.MelonInstall

private const val RELEASES_URL = "https://github.com/WeirdWally-dev/melonDS-android/releases"

/**
 * First-run setup, like a new DS asking for your name and favourite colour. Also reachable later to edit the profile.
 */
@Composable
fun SetupScreen(
    profile: Profile,
    installs: List<MelonInstall>,
    selectedInstall: MelonInstall?,
    isFirstRun: Boolean,
    onSave: (name: String, colour: Int, melonPackage: String?) -> Unit,
    onCancel: () -> Unit,
) {
    var name by remember { mutableStateOf(profile.name) }
    var colour by remember { mutableIntStateOf(profile.colour) }
    var melonPackage by remember { mutableStateOf(selectedInstall?.packageName) }
    val context = LocalContext.current

    DsShell(
        top = {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Title(if (isFirstRun) "Welcome!" else "Your profile")
                Gap(6.dp)
                BodyText(
                    text = if (isFirstRun) "Set up your profile once. Nearby players will see this name and colour, and so will your games."
                    else "This is how nearby players see you.",
                    color = LobbyColors.muted,
                    center = true,
                )
                Gap(16.dp)
                NameTag(name = name.ifBlank { "?" }, colour = colour, large = true)
            }
        },
        bottom = {
            Column(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                ) {
                    SectionTitle("NAME")
                    Gap(6.dp)
                    NameField(value = name, onValueChange = { name = it.replace("\n", "").take(Profile.MAX_NAME_LENGTH) })
                    Gap(16.dp)

                    SectionTitle("FAVOURITE COLOUR")
                    Gap(6.dp)
                    ColourGrid(selected = colour, onSelect = { colour = it })
                    Gap(16.dp)

                    SectionTitle("EMULATOR")
                    Gap(6.dp)
                    if (installs.isEmpty()) {
                        BodyText("melonDS with local multiplayer isn't installed on this phone. Install it first, then come back.")
                        Gap(8.dp)
                        DsButton("Get melonDS", onClick = {
                            context.startActivity(Intent(Intent.ACTION_VIEW, RELEASES_URL.toUri()))
                        })
                    } else {
                        installs.forEach { install ->
                            InstallRow(install, selected = install.packageName == melonPackage, onClick = { melonPackage = install.packageName })
                            Gap(6.dp)
                        }
                    }
                }
                Gap(10.dp)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (!isFirstRun) {
                        DsButton("Cancel", onClick = onCancel, modifier = Modifier.weight(1f))
                    }
                    DsButton(
                        text = "OK",
                        onClick = { onSave(name, colour, melonPackage) },
                        enabled = name.isNotBlank(),
                        accent = DsColours[colour],
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        },
    )
}

@Composable
private fun NameField(value: String, onValueChange: (String) -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(color = LobbyColors.text, fontSize = 20.sp, fontWeight = FontWeight.Bold),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color.White)
            .border(2.dp, LobbyColors.panelBorder, shape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) {
                    Text("Up to 10 letters", color = LobbyColors.disabled, fontSize = 20.sp)
                }
                inner()
            }
        },
    )
}

@Composable
private fun ColourGrid(selected: Int, onSelect: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        DsColours.all.indices.chunked(8).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { index ->
                    val shape = RoundedCornerShape(8.dp)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(shape)
                            .background(DsColours[index])
                            .border(if (index == selected) 3.dp else 1.dp, if (index == selected) LobbyColors.text else LobbyColors.panelBorder, shape)
                            .clickable { onSelect(index) },
                    )
                }
            }
        }
    }
}

@Composable
private fun InstallRow(install: MelonInstall, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) LobbyColors.stripe else Color.White)
            .border(2.dp, if (selected) LobbyColors.text else LobbyColors.panelBorder, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(install.label, color = LobbyColors.text, fontWeight = FontWeight.Bold)
            Text(install.packageName, color = LobbyColors.muted, fontSize = 12.sp)
        }
        if (selected) Text("✓", color = LobbyColors.text, fontWeight = FontWeight.Bold, fontSize = 18.sp)
    }
}
