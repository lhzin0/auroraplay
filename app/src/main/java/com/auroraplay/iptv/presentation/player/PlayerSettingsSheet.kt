package com.auroraplay.iptv.presentation.player

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.auroraplay.iptv.core.theme.AuroraColors
import com.auroraplay.iptv.core.theme.frostSurface
import com.auroraplay.iptv.presentation.components.rememberTvFocusVisuals
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

/** Floating ⋮ settings panel — anchored top-right, fades + scales in from
 * that corner, closes on an outside tap. Holds the controls pulled out of the
 * bottom bar (speed, aspect ratio) with icon + name + current value. */
@Composable
fun PlayerSettingsSheet(
    speedLabel: String,
    resizeLabel: String,
    onOpenSpeed: () -> Unit,
    onCycleResize: () -> Unit,
    onDismiss: () -> Unit,
    hazeState: HazeState? = null,
) {
    var open by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { open = true }
    fun close() { open = false; closing = true }
    // Let the exit animation play before the caller drops us — but only once a
    // close was actually requested, never on the initial (open == false) frame.
    LaunchedEffect(closing) { if (closing) { delay(180.milliseconds); onDismiss() } }

    Box(
        modifier = Modifier
            .fillMaxSize()
            // Barely-there dim so the panel reads as floating without blacking
            // out the video underneath.
            .background(Color.Black.copy(alpha = if (open) 0.12f else 0f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { close() },
            ),
    ) {
        androidx.compose.animation.AnimatedVisibility(
            visible = open,
            enter = fadeIn(tween(150)) + scaleIn(tween(170), initialScale = 0.9f, transformOrigin = androidx.compose.ui.graphics.TransformOrigin(1f, 0f)),
            exit = fadeOut(tween(140)) + scaleOut(tween(140), targetScale = 0.9f, transformOrigin = androidx.compose.ui.graphics.TransformOrigin(1f, 0f)),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .displayCutoutPadding()
                // Sits BELOW the top bar so it never covers the ⋮ that opens
                // and closes it (56dp top bar + a small gap).
                .padding(top = 60.dp, end = 10.dp),
        ) {
            Column(
                modifier = Modifier
                    .width(200.dp)
                    // Translucent "glass" — the video shows through. Follows the
                    // FrostGlass setting: real backdrop blur of the video on
                    // API 31+, flat black wash when the toggle is off.
                    .frostSurface(
                        shape = RoundedCornerShape(16.dp),
                        flat = Color.Black.copy(alpha = 0.62f),
                        tint = AuroraColors.SurfaceDark,
                        haze = hazeState,
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    )
                    .padding(vertical = 4.dp),
            ) {
                PlayerSettingRow(Icons.Default.Speed, "Velocidade", speedLabel) { close(); onOpenSpeed() }
                PlayerSettingRow(Icons.Default.AspectRatio, "Proporção", resizeLabel) { onCycleResize() }
            }
        }
    }
}

@Composable
private fun PlayerSettingRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    name: String,
    value: String,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val visuals = rememberTvFocusVisuals(interactionSource, pressed = pressed, pressedScale = 0.99f, focusedScale = 1f)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = visuals.ringAlpha * 0.12f))
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = AuroraColors.TextSecondary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Text(name, style = MaterialTheme.typography.bodyLarge, color = AuroraColors.TextPrimary, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
    }
}
