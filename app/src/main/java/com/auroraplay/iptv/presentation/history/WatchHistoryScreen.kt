package com.auroraplay.iptv.presentation.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.HistoryToggleOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil.compose.AsyncImage
import com.auroraplay.iptv.core.theme.AuroraColors
import com.auroraplay.iptv.core.util.toRelativeTimeLabel
import com.auroraplay.iptv.domain.model.ContentType
import com.auroraplay.iptv.presentation.components.Spacing

@Composable
fun WatchHistoryScreen(
    onBack: () -> Unit,
    onOpenMovie: (String) -> Unit,
    onOpenSeries: (String) -> Unit,
    viewModel: WatchHistoryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    var confirmClear by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .background(AuroraColors.BackgroundBase)
            .statusBarsPadding(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 16.dp, top = 8.dp, bottom = 6.dp),
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(44.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Voltar",
                    tint = AuroraColors.TextPrimary,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                "Histórico",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = AuroraColors.TextPrimary,
                modifier = Modifier.weight(1f),
            )
            if (state.entries.isNotEmpty()) {
                TextButton(onClick = { confirmClear = true }) {
                    Text("Limpar", color = AuroraColors.Error)
                }
            }
        }

        when {
            state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }

            state.entries.isEmpty() -> Column(
                Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    Icons.Default.HistoryToggleOff,
                    contentDescription = null,
                    tint = AuroraColors.TextTertiary,
                    modifier = Modifier.size(48.dp),
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    "Nada por aqui ainda",
                    style = MaterialTheme.typography.titleMedium,
                    color = AuroraColors.TextPrimary,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Filmes e séries que você assistir aparecem aqui e ficam salvos até você apagar.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AuroraColors.TextTertiary,
                )
            }

            else -> LazyColumn(
                contentPadding = PaddingValues(
                    start = Spacing.gutter,
                    end = Spacing.gutter,
                    top = 4.dp,
                    bottom = Spacing.navBarClearance,
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(state.entries, key = { it.type.name + ":" + it.id }) { entry ->
                    HistoryRow(
                        entry = entry,
                        onClick = {
                            when (entry.type) {
                                ContentType.SERIES -> onOpenSeries(entry.id)
                                else -> onOpenMovie(entry.id)
                            }
                        },
                    )
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            containerColor = AuroraColors.SurfaceDark,
            title = { Text("Limpar histórico?", color = AuroraColors.TextPrimary) },
            text = {
                Text(
                    "Remove a lista de conteúdos assistidos deste perfil. O progresso de \"Continuar assistindo\" não é afetado.",
                    color = AuroraColors.TextSecondary,
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.clearHistory(); confirmClear = false }) {
                    Text("Limpar", color = AuroraColors.Error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) {
                    Text("Cancelar", color = MaterialTheme.colorScheme.primary)
                }
            },
        )
    }
}

@Composable
private fun HistoryRow(entry: HistoryEntry, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(AuroraColors.SurfaceDark)
            .clickable(onClick = onClick)
            .padding(10.dp),
    ) {
        AsyncImage(
            model = entry.posterUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(width = 54.dp, height = 80.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(AuroraColors.SurfaceHigh),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                entry.title,
                style = MaterialTheme.typography.titleSmall,
                color = AuroraColors.TextPrimary,
                maxLines = 2,
            )
            Spacer(Modifier.height(3.dp))
            val meta = buildString {
                append(if (entry.type == ContentType.SERIES) "Série" else "Filme")
                entry.episodeLabel?.let { append(" • $it") }
                append(" • ")
                append(entry.lastWatchedMillis.toRelativeTimeLabel())
            }
            Text(meta, style = MaterialTheme.typography.bodySmall, color = AuroraColors.TextTertiary)
            if (entry.fraction > 0.01f && entry.fraction < 0.99f) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { entry.fraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(CircleShape),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = AuroraColors.SurfaceHigh,
                )
            }
        }
    }
}
