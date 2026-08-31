package com.auroraplay.iptv.presentation.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.auroraplay.iptv.core.theme.AuroraColors
import com.auroraplay.iptv.core.util.toRelativeTimeLabel
import com.auroraplay.iptv.presentation.components.EmptyState
import com.auroraplay.iptv.presentation.components.Spacing

@Composable
fun NotificationsScreen(
    onBack: () -> Unit,
    viewModel: NotificationsViewModel = hiltViewModel(),
) {
    val notifications by viewModel.notifications.collectAsState()

    // Opening this screen is itself the "I've seen these" action — same
    // convention as a notification tray clearing its unread state on open.
    LaunchedEffect(Unit) { viewModel.markAllRead() }

    Column(Modifier.fillMaxSize().background(AuroraColors.BackgroundBase).statusBarsPadding()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            com.auroraplay.iptv.presentation.components.BackButton(onClick = onBack)
            Spacer(Modifier.width(8.dp))
            Text("Notificações", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = AuroraColors.TextPrimary)
        }

        if (notifications.isEmpty()) {
            EmptyState(message = "Nenhuma notificação ainda. Avisamos aqui quando uma série favorita ganhar episódio novo.")
        } else {
            LazyColumn(contentPadding = PaddingValues(horizontal = Spacing.gutter, vertical = Spacing.sm)) {
                items(notifications, key = { it.id }) { notification ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(AuroraColors.SurfaceDark)
                            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
                    ) {
                        Box(
                            modifier = Modifier.size(36.dp).clip(CircleShape).background(AuroraColors.SurfaceHigh),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(Spacing.md))
                        Column(Modifier.weight(1f)) {
                            Text(notification.title, style = MaterialTheme.typography.titleMedium, color = AuroraColors.TextPrimary)
                            Text(notification.message, style = MaterialTheme.typography.bodySmall, color = AuroraColors.TextSecondary)
                            Text(
                                notification.timestampMillis.toRelativeTimeLabel(),
                                style = MaterialTheme.typography.labelSmall,
                                color = AuroraColors.TextTertiary,
                            )
                        }
                    }
                    Spacer(Modifier.height(Spacing.sm))
                }
                item { Spacer(Modifier.height(Spacing.navBarClearance)) }
            }
        }
    }
}
