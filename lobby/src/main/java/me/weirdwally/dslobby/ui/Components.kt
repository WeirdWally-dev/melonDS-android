package me.weirdwally.dslobby.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.weirdwally.dslobby.DsColours
import me.weirdwally.dslobby.melon.LibraryGame

object LobbyColors {
    val shell = Color(0xFFD9DEE3)
    val hinge = Color(0xFF8E99A4)
    val panel = Color(0xFFF7F8FA)
    val panelBorder = Color(0xFFB7C0C9)
    val stripe = Color(0xFFE9EDF1)
    val text = Color(0xFF26313C)
    val muted = Color(0xFF6E7A86)
    val disabled = Color(0xFFC3CAD1)
}

/**
 * The two screens of a DS, stacked with a hinge between them. The top screen shows status; the bottom one is where
 * you tap.
 */
@Composable
fun DsShell(
    top: @Composable BoxScope.() -> Unit,
    bottom: @Composable BoxScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LobbyColors.shell)
            .safeDrawingPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        DsScreen(modifier = Modifier.weight(0.38f), striped = true, content = top)
        Hinge()
        DsScreen(modifier = Modifier.weight(0.62f), striped = false, content = bottom)
    }
}

@Composable
private fun DsScreen(modifier: Modifier, striped: Boolean, content: @Composable BoxScope.() -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(LobbyColors.panel)
            .then(
                if (striped) {
                    Modifier.drawBehind {
                        val step = 6.dp.toPx()
                        var y = 0f
                        while (y < size.height) {
                            drawLine(LobbyColors.stripe, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
                            y += step
                        }
                    }
                } else {
                    Modifier
                }
            )
            .border(2.dp, LobbyColors.panelBorder, shape)
            .padding(16.dp),
        content = content,
    )
}

@Composable
private fun Hinge() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(22.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(2) {
            Box(
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .size(width = 46.dp, height = 8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(LobbyColors.hinge),
            )
        }
    }
}

/** A player's name on their favourite colour, like the DS shows in PictoChat and Download Play */
@Composable
fun NameTag(name: String, colour: Int, modifier: Modifier = Modifier, large: Boolean = false) {
    val textColor = if (DsColours.needsDarkText(colour)) LobbyColors.text else Color.White
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(if (large) 10.dp else 8.dp))
            .background(DsColours[colour])
            .padding(horizontal = if (large) 14.dp else 10.dp, vertical = if (large) 6.dp else 4.dp),
    ) {
        Text(
            text = name,
            color = textColor,
            fontWeight = FontWeight.Bold,
            fontSize = if (large) 20.sp else 15.sp,
            maxLines = 1,
        )
    }
}

/** The game's 32x32 banner icon, scaled up without smoothing so it stays crisp pixel art */
@Composable
fun GameIcon(game: LibraryGame?, loadIcon: suspend (LibraryGame) -> ImageBitmap?, size: Dp) {
    var bitmap by remember(game?.iconUri) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(game?.iconUri) {
        bitmap = game?.let { loadIcon(it) }
    }

    val shape = RoundedCornerShape(size / 8)
    Box(
        modifier = Modifier
            .size(size)
            .clip(shape)
            .background(LobbyColors.stripe)
            .border(1.dp, LobbyColors.panelBorder, shape),
        contentAlignment = Alignment.Center,
    ) {
        val current = bitmap
        if (current != null) {
            Image(
                bitmap = current,
                contentDescription = game?.name,
                filterQuality = FilterQuality.None,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text("?", color = LobbyColors.muted, fontWeight = FontWeight.Bold, fontSize = (size.value / 2.5f).sp)
        }
    }
}

@Composable
fun DsButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accent: Color? = null,
) {
    val shape = RoundedCornerShape(10.dp)
    val background = when {
        !enabled -> LobbyColors.stripe
        accent != null -> accent
        else -> Color.White
    }
    val textColor = when {
        !enabled -> LobbyColors.disabled
        accent != null && accent.lum() < 0.6f -> Color.White
        else -> LobbyColors.text
    }
    Box(
        modifier = modifier
            .clip(shape)
            .background(background)
            .border(2.dp, if (enabled) LobbyColors.panelBorder else LobbyColors.stripe, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = textColor, fontWeight = FontWeight.Bold, fontSize = 16.sp, textAlign = TextAlign.Center, maxLines = 1)
    }
}

private fun Color.lum(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = LobbyColors.muted,
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
        letterSpacing = 1.sp,
        modifier = modifier,
    )
}

@Composable
fun BodyText(text: String, modifier: Modifier = Modifier, color: Color = LobbyColors.text, center: Boolean = false) {
    Text(
        text = text,
        color = color,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        textAlign = if (center) TextAlign.Center else TextAlign.Start,
        modifier = modifier,
    )
}

@Composable
fun Title(text: String, modifier: Modifier = Modifier, maxLines: Int = 2) {
    Text(
        text = text,
        color = LobbyColors.text,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 26.sp,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

/** An open slot in a room, waiting for someone to fill it */
@Composable
fun EmptySlot(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "slot")
    val pulse by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
        label = "pulse",
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .drawBehind {
                drawRoundRect(
                    color = LobbyColors.panelBorder,
                    style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx()),
                )
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Text("Waiting…", color = LobbyColors.muted, modifier = Modifier.padding(start = 12.dp).alpha(pulse))
    }
}

/** Expanding rings, for "looking for players" */
@Composable
fun SignalPulse(colour: Color, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "signal")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing)),
        label = "progress",
    )
    Box(
        modifier = modifier
            .size(72.dp)
            .drawBehind {
                val maxRadius = size.minDimension / 2
                repeat(3) { ring ->
                    val p = (progress + ring / 3f) % 1f
                    drawCircle(colour.copy(alpha = (1f - p) * 0.8f), radius = maxRadius * p, style = Stroke(width = 3.dp.toPx()))
                }
                drawCircle(colour, radius = 6.dp.toPx())
            },
    )
}

@Composable
fun ColumnScope.Gap(height: Dp) = Spacer(Modifier.height(height))

@Composable
fun HGap(width: Dp) = Spacer(Modifier.width(width))
