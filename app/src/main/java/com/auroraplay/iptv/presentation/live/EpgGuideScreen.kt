package com.auroraplay.iptv.presentation.live

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import com.auroraplay.iptv.domain.model.EpgProgram
import com.auroraplay.iptv.presentation.components.EmptyState
import com.auroraplay.iptv.presentation.components.Spacing
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun EpgGuideScreen(
    onBack: () -> Unit,
    onOpenChannel: (String) -> Unit,
    viewModel: EpgGuideViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    Column(Modifier.fillMaxSize().background(AuroraColors.BackgroundBase).statusBarsPadding()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            com.auroraplay.iptv.presentation.components.BackButton(onClick = onBack)
            Spacer(Modifier.width(8.dp))
            Text("Guia de programação", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = AuroraColors.TextPrimary)
        }

        when {
            state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
            state.rows.isEmpty() -> EmptyState(message = "Nenhum canal disponível.")
            else -> LazyColumn(contentPadding = PaddingValues(bottom = Spacing.navBarClearance)) {
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
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.gutter, vertical = Spacing.sm),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(76.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(AuroraColors.SurfaceHigh),
                contentAlignment = Alignment.Center,
            ) {
                if (row.channel.logoUrl != null) {
                    AsyncImage(model = row.channel.logoUrl, contentDescription = row.channel.name, modifier = Modifier.fillMaxSize().clip(CircleShape))
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                row.channel.name,
                style = MaterialTheme.typography.labelSmall,
                color = AuroraColors.TextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }

        Spacer(Modifier.width(Spacing.sm))

        if (row.programs.isEmpty()) {
            Text(
                "Sem informação de programação",
                style = MaterialTheme.typography.bodySmall,
                color = AuroraColors.TextTertiary,
                modifier = Modifier.weight(1f).padding(vertical = 16.dp),
            )
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(row.programs) { program -> EpgProgramBlock(program) }
            }
        }
    }
}

@Composable
private fun EpgProgramBlock(program: EpgProgram) {
    val now = System.currentTimeMillis()
    val isNow = now in program.startMillis until program.endMillis
    // Duration-proportional width (2dp/min) is a reasonable feel without
    // needing every row to share one absolute, synchronized time axis.
    val minutes = ((program.endMillis - program.startMillis) / 60_000L).coerceAtLeast(1)
    val widthDp = (minutes * 2).toInt().coerceIn(90, 260).dp
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.forLanguageTag("pt-BR")) }

    Column(
        modifier = Modifier
            .width(widthDp)
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isNow) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f) else AuroraColors.SurfaceHigh)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Text(
            timeFormat.format(java.util.Date(program.startMillis)),
            style = MaterialTheme.typography.labelSmall,
            color = if (isNow) MaterialTheme.colorScheme.primary else AuroraColors.TextTertiary,
        )
        Text(
            program.title.ifBlank { "Sem título" },
            style = MaterialTheme.typography.bodySmall,
            color = AuroraColors.TextPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
