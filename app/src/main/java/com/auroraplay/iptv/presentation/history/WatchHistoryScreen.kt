package com.auroraplay.iptv.presentation.history

import androidx.compose.animation.AnimatedVisibility
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.HistoryToggleOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
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
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var confirmClear by remember { mutableStateOf(false) }
    var confirmDeleteSeries by remember { mutableStateOf<HistoryEntry?>(null) }
    val expanded = remember { mutableStateMapOf<String, Boolean>() }

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
                    HistoryCard(
                        entry = entry,
                        isExpanded = expanded[entry.id] == true,
                        onToggleExpand = { expanded[entry.id] = expanded[entry.id] != true },
                        onOpen = {
                            if (!entry.available) return@HistoryCard
                            when (entry.type) {
                                ContentType.SERIES -> onOpenSeries(entry.id)
                                else -> onOpenMovie(entry.id)
                            }
                        },
                        onDelete = {
                            if (entry.type == ContentType.SERIES && entry.episodes.size > 1) {
                                confirmDeleteSeries = entry
                            } else {
                                viewModel.deleteEntry(entry)
                            }
                        },
                        onDeleteEpisode = { viewModel.deleteEpisode(it) },
                    )
                }
            }
        }
    }

    if (confirmClear) {
        ConfirmDialog(
            title = "Limpar histórico?",
            body = "Remove a lista de conteúdos assistidos deste perfil. O progresso de \"Continuar assistindo\" não é afetado.",
            confirmLabel = "Limpar",
            onConfirm = { viewModel.clearHistory(); confirmClear = false },
            onDismiss = { confirmClear = false },
        )
    }

    confirmDeleteSeries?.let { entry ->
        ConfirmDialog(
            title = "Remover do histórico?",
            body = "Remove \"${entry.title}\" e os ${entry.episodes.size} episódios registrados. O progresso não é afetado.",
            confirmLabel = "Remover",
            onConfirm = { viewModel.deleteEntry(entry); confirmDeleteSeries = null },
            onDismiss = { confirmDeleteSeries = null },
        )
    }
}

@Composable
private fun HistoryCard(
    entry: HistoryEntry,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    onDeleteEpisode: (String) -> Unit,
) {
    val isSeriesWithEpisodes = entry.type == ContentType.SERIES && entry.episodes.isNotEmpty()
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(AuroraColors.SurfaceDark),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { if (isSeriesWithEpisodes) onToggleExpand() else onOpen() }
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
                if (!entry.available) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        "Indisponível no catálogo",
                        style = MaterialTheme.typography.labelSmall,
                        color = AuroraColors.TextTertiary,
                    )
                }
                if (entry.fraction > 0.01f && entry.fraction < 0.99f) {
                    Spacer(Modifier.height(8.dp))
                    ThinProgress(entry.fraction)
                }
            }
            if (isSeriesWithEpisodes) {
                Icon(
                    Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) "Recolher" else "Ver episódios",
                    tint = AuroraColors.TextTertiary,
                    modifier = Modifier
                        .size(24.dp)
                        .rotate(animateFloatAsState(if (isExpanded) 180f else 0f, label = "chev").value),
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.DeleteOutline,
                    contentDescription = "Remover do histórico",
                    tint = AuroraColors.TextTertiary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        AnimatedVisibility(visible = isExpanded && isSeriesWithEpisodes) {
            Column(Modifier.padding(start = 14.dp, end = 6.dp, bottom = 8.dp)) {
                HorizontalDivider(color = AuroraColors.Divider)
                entry.episodes.forEach { ep ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "${ep.label} • ${ep.lastWatchedMillis.toRelativeTimeLabel()}",
                                style = MaterialTheme.typography.bodySmall,
                                color = AuroraColors.TextSecondary,
                            )
                            if (ep.fraction > 0.01f && ep.fraction < 0.99f) {
                                Spacer(Modifier.height(6.dp))
                                ThinProgress(ep.fraction)
                            }
                        }
                        IconButton(onClick = { onDeleteEpisode(ep.contentId) }) {
                            Icon(
                                Icons.Default.DeleteOutline,
                                contentDescription = "Remover episódio do histórico",
                                tint = AuroraColors.TextTertiary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ThinProgress(fraction: Float) {
    LinearProgressIndicator(
        progress = { fraction },
        modifier = Modifier
            .fillMaxWidth()
            .height(3.dp)
            .clip(CircleShape),
        color = MaterialTheme.colorScheme.primary,
        trackColor = AuroraColors.SurfaceHigh,
    )
}

@Composable
private fun ConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AuroraColors.SurfaceDark,
        title = { Text(title, color = AuroraColors.TextPrimary) },
        text = { Text(body, color = AuroraColors.TextSecondary) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(confirmLabel, color = AuroraColors.Error) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar", color = MaterialTheme.colorScheme.primary) }
        },
    )
}
