package com.auroraplay.iptv.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.auroraplay.iptv.core.theme.AuroraColors
import com.auroraplay.iptv.presentation.components.Spacing

private fun iconFor(tab: MainTab, selected: Boolean): ImageVector = when (tab) {
    MainTab.HOME -> if (selected) Icons.Filled.Home else Icons.Outlined.Home
    MainTab.LIVE -> if (selected) Icons.Filled.LiveTv else Icons.Outlined.LiveTv
    MainTab.SEARCH -> Icons.Filled.Search
    MainTab.SETTINGS -> if (selected) Icons.Filled.Settings else Icons.Outlined.Settings
}

/**
 * Floating bottom navigation, following One UI's newer navigation principles:
 * a detached rounded container with generous touch targets, a translucent
 * capsule marking the active destination, and icon+label treated as one
 * centred unit rather than two separately-positioned elements (which is why
 * the labels previously looked off-centre relative to their icons).
 *
 * Applies navigationBarsPadding() so it floats above the system gesture bar.
 */
@Composable
fun AuroraBottomNavBar(
    currentTab: MainTab,
    onTabSelected: (MainTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
    ) {
        Row(
            modifier = Modifier
                .align(Alignment.Center)
                .clip(RoundedCornerShape(32.dp))
                .background(AuroraColors.BackgroundElevated.copy(alpha = 0.98f))
                .padding(horizontal = 6.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MainTab.entries.forEach { tab ->
                NavItem(
                    tab = tab,
                    selected = tab == currentTab,
                    onClick = { onTabSelected(tab) },
                )
            }
        }
    }
}

@Composable
private fun NavItem(
    tab: MainTab,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()

    val accent = MaterialTheme.colorScheme.primary
    val contentColor by animateColorAsState(
        targetValue = if (selected) accent else AuroraColors.TextTertiary,
        animationSpec = tween(180),
        label = "navContent",
    )
    // Capsule behind the active item; doubles as the focus ring on TV.
    val capsuleAlpha by animateFloatAsState(
        targetValue = when {
            selected -> 0.16f
            focused -> 0.10f
            else -> 0f
        },
        animationSpec = tween(180),
        label = "navCapsule",
    )
    val iconLift by animateDpAsState(
        targetValue = if (selected) (-1).dp else 0.dp,
        animationSpec = tween(180),
        label = "navLift",
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .widthIn(min = 72.dp)
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(accent.copy(alpha = capsuleAlpha))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Icon(
            imageVector = iconFor(tab, selected),
            contentDescription = tab.label,
            tint = contentColor,
            modifier = Modifier
                .offset(y = iconLift)
                .size(21.dp),
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = tab.label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = contentColor,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }
}

/** Side navigation rail used on Android TV where D-pad navigation is primary. */
@Composable
fun AuroraTvNavRail(
    currentTab: MainTab,
    onTabSelected: (MainTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(112.dp)
            .background(AuroraColors.BackgroundElevated)
            .padding(vertical = Spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        MainTab.entries.forEach { tab ->
            NavItem(
                tab = tab,
                selected = tab == currentTab,
                onClick = { onTabSelected(tab) },
                modifier = Modifier.fillMaxWidth(0.86f),
            )
        }
    }
}
