package com.auroraplay.iptv.presentation.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.auroraplay.iptv.core.theme.AuroraColors

/** Shimmering placeholder block used while content loads. */
@Composable
fun LoadingSkeleton(modifier: Modifier = Modifier, shape: RoundedCornerShape = RoundedCornerShape(12.dp)) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(tween(800, easing = LinearEasing), RepeatMode.Reverse),
        label = "skeletonAlpha",
    )
    Box(
        modifier = modifier
            .clip(shape)
            .background(AuroraColors.SurfaceHigh.copy(alpha = alpha))
    )
}

@Composable
fun HomeRowSkeleton(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        LoadingSkeleton(Modifier.width(160.dp).height(20.dp).padding(start = 20.dp))
        Spacer(Modifier.height(12.dp))
        Row(Modifier.padding(horizontal = 20.dp)) {
            repeat(4) {
                LoadingSkeleton(Modifier.width(128.dp).height(190.dp))
                Spacer(Modifier.width(12.dp))
            }
        }
    }
}

@Composable
fun EmptyState(
    message: String = "Nenhum conteúdo disponível",
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Default.SearchOff,
) {
    StateMessage(icon = icon, message = message, modifier = modifier)
}

@Composable
fun ErrorState(
    message: String = "Não foi possível carregar o conteúdo.",
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    StateMessage(icon = Icons.Default.ErrorOutline, message = message, onRetry = onRetry, modifier = modifier)
}

@Composable
fun ServerOfflineState(
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    StateMessage(icon = Icons.Default.CloudOff, message = "Não foi possível conectar ao servidor.", onRetry = onRetry, modifier = modifier)
}

@Composable
private fun StateMessage(
    icon: ImageVector,
    message: String,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = AuroraColors.TextTertiary, modifier = Modifier.size(40.dp))
        Spacer(Modifier.height(12.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = AuroraColors.TextSecondary, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if (onRetry != null) {
            Spacer(Modifier.height(16.dp))
            GlassButton(text = "Tentar novamente", onClick = onRetry)
        }
    }
}
