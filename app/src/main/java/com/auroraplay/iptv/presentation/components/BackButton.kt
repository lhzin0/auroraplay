package com.auroraplay.iptv.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.auroraplay.iptv.core.theme.AuroraColors

/**
 * The one back affordance every non-overlay screen header uses: a compact
 * 36dp disc (same footprint as the settings icon chips) with a 20dp arrow.
 * Screens differed — some had a plain 48dp icon jammed against the title,
 * some a big black circle — so this exists to keep them identical. Detail
 * screens that float their back button over artwork keep their own styling.
 */
@Composable
fun BackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = AuroraColors.TextPrimary,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(36.dp)
            .background(AuroraColors.SurfaceHigh, CircleShape),
    ) {
        Icon(
            Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Voltar",
            tint = tint,
            modifier = Modifier.size(20.dp),
        )
    }
}
