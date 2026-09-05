package com.auroraplay.iptv.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.auroraplay.iptv.core.theme.AuroraColors

/**
 * Season picker as a single dropdown.
 *
 * A horizontal chip strip forces the viewer to scroll sideways to reach later
 * seasons and gives no indication of how many exist; a dropdown shows the
 * current season, the total, and every option in one tap — which matters for
 * long-running shows with a dozen seasons.
 */
@Composable
fun SeasonDropdown(
    seasons: List<Int>,
    selectedSeason: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(AuroraColors.SurfaceHigh)
                .tvFocusable(shape = RoundedCornerShape(10.dp), accent = MaterialTheme.colorScheme.primary, enabled = seasons.size > 1)
                .clickable(enabled = seasons.size > 1) { expanded = true }
                .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        ) {
            Text(
                text = "Temporada $selectedSeason",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = AuroraColors.TextPrimary,
            )
            if (seasons.size > 1) {
                Spacer(Modifier.width(Spacing.sm))
                Icon(
                    Icons.Default.ArrowDropDown,
                    contentDescription = "Escolher temporada",
                    tint = AuroraColors.TextSecondary,
                    modifier = Modifier.rotate(if (expanded) 180f else 0f),
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(AuroraColors.BackgroundElevated),
        ) {
            seasons.forEach { season ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = "Temporada $season",
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (season == selectedSeason) MaterialTheme.colorScheme.primary else AuroraColors.TextPrimary,
                        )
                    },
                    trailingIcon = {
                        if (season == selectedSeason) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                    },
                    onClick = {
                        onSelect(season)
                        expanded = false
                    },
                )
            }
        }
    }
}
