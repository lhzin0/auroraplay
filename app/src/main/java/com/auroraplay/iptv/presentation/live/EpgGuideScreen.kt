package com.auroraplay.iptv.presentation.live

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil.compose.AsyncImage
import com.auroraplay.iptv.core.theme.AuroraColors
import com.auroraplay.iptv.core.theme.frostSurface
import com.auroraplay.iptv.domain.model.EpgProgram
import com.auroraplay.iptv.presentation.components.ChannelAvatar
import com.auroraplay.iptv.presentation.components.EmptyState
import com.auroraplay.iptv.presentation.components.Spacing
import com.auroraplay.iptv.presentation.components.rememberTvFocusVisuals
import com.auroraplay.iptv.presentation.components.tvBringIntoViewOnFocus
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun EpgGuideScreen(
    onBack: () -> Unit,
    onOpenChannel: (String) -> Unit,
    viewModel: EpgGuideViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().background(AuroraColors.BackgroundBase).statusBarsPadding()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            com.auroraplay.iptv.presentation.components.BackButton(onClick = onBack)
            Spacer(Modifier.width(8.dp))
            Column {
                Text("Guia de programação", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = AuroraColors.TextPrimary)
                Text(
                    remember { SimpleDateFormat("EEEE, d 'de' MMMM", Locale.forLanguageTag("pt-BR")) }
                        .format(java.util.Date())
                        .replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.labelMedium,
                    color = AuroraColors.TextTertiary,
                )
            }
        }

        when {
            state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
            state.rows.isEmpty() -> EmptyState(message = "Nenhum canal disponível.")
            else -> LazyColumn(
                contentPadding = PaddingValues(start = Spacing.gutter, end = Spacing.gutter, top = Spacing.sm, bottom = Spacing.navBarClearance),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                items(state.rows, key = { it.channel.id }) { row ->
                    // Fires once per row composition — only channels actually
                    // scrolled into view ever request their timeline.
                    LaunchedEffect(row.channel.id) { viewModel.ensureTimeline(row.channel.id) }
                    EpgChannelRow(row = row, onClick = { onOpenChannel(row.channel.id) })
                }
            }
        }
    }
}

@Composable
private fun EpgChannelRow(row: ChannelEpgRow, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val visuals = rememberTvFocusVisuals(interactionSource, pressed = pressed, pressedScale = 0.99f, focusedScale = 1f)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 76.dp)
            .tvBringIntoViewOnFocus()
            .frostSurface(shape, flat = AuroraColors.SurfaceHigh)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = visuals.ringAlpha * 0.12f), shape)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(10.dp),
    ) {
        // Channel identity: logo + name side by side, fixed width so every
        // row's timeline starts at the same x.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.width(132.dp),
        ) {
            ChannelAvatar(
                name = row.channel.name,
                logoUrl = row.channel.logoUrl,
                shape = CircleShape,
                modifier = Modifier.size(40.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                row.channel.name,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = AuroraColors.TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(Modifier.width(Spacing.md))

        if (row.programs.isEmpty()) {
            Text(
                "Sem programação",
                style = MaterialTheme.typography.labelMedium,
                color = AuroraColors.TextTertiary,
                modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.End,
            )
        } else {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f),
            ) {
                items(row.programs) { program -> EpgProgramBlock(program) }
            }
        }
    }
}

@Composable
private fun EpgProgramBlock(program: EpgProgram) {
    val now = System.currentTimeMillis()
    val isNow = now in program.startMillis until program.endMillis
    val accent = MaterialTheme.colorScheme.primary
    // Duration-proportional width (2dp/min) is a reasonable feel without
    // needing every row to share one absolute, synchronized time axis.
    val minutes = ((program.endMillis - program.startMillis) / 60_000L).coerceAtLeast(1)
    val widthDp = (minutes * 2).toInt().coerceIn(96, 240).dp
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.forLanguageTag("pt-BR")) }

    Column(
        modifier = Modifier
            .width(widthDp)
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (isNow) accent.copy(alpha = 0.20f) else AuroraColors.SurfaceDark)
            .then(if (isNow) Modifier.border(1.dp, accent.copy(alpha = 0.55f), RoundedCornerShape(10.dp)) else Modifier)
            .padding(horizontal = 9.dp, vertical = 7.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (isNow) {
                Box(Modifier.size(5.dp).clip(CircleShape).background(accent))
                Spacer(Modifier.width(5.dp))
            }
            Text(
                timeFormat.format(java.util.Date(program.startMillis)),
                style = MaterialTheme.typography.labelSmall,
                color = if (isNow) accent else AuroraColors.TextTertiary,
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            program.title.ifBlank { "Sem título" },
            style = MaterialTheme.typography.bodySmall,
            color = AuroraColors.TextPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
