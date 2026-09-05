package com.auroraplay.iptv.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.auroraplay.iptv.core.theme.AuroraColors
import com.auroraplay.iptv.core.theme.frostSurface

/**
 * Consistent spacing scale used across every screen, so gaps between
 * sections/cards/text stop being ad-hoc per screen.
 */
object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 28.dp

    /** Horizontal page gutter — every screen uses this so titles, rows and
     * grids share one left edge instead of drifting a few dp apart. */
    val gutter = 20.dp

    /** Bottom inset so content never hides behind the floating nav bar. */
    val navBarClearance = 96.dp
}

/**
 * [Spacing.navBarClearance] plus the live system navigation-bar inset.
 *
 * The flat 96.dp is the height of the floating nav bar itself — enough on
 * gesture navigation, where the system inset is a thin handle. On devices
 * using the classic 3-button navigation bar the system inset is far taller,
 * so `navigationBarsPadding()` pushes the floating bar up by that much and a
 * flat 96.dp of content padding leaves the last rows hidden behind it.
 *
 * Use this for the scrolling tabs that sit behind the floating bar (Início,
 * Canais, Buscar); the other screens have no floating bar and keep the flat
 * value.
 */
val floatingBarClearance: Dp
    @Composable
    get() = Spacing.navBarClearance +
        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

/**
 * Standard page header: page title + a per-page contextual search field.
 *
 * Applies `statusBarsPadding()` itself — this was the cause of the page
 * title and category chips being drawn underneath the system clock on the
 * Movies / Series / Live / Search screens.
 *
 * Search is intentionally scoped to the page (`placeholder` says what is
 * being searched) rather than a global search tab, so results can never mix
 * channels into a movie search.
 */
@Composable
fun PageHeader(
    title: String,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    searchPlaceholder: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    onSearchOpenChange: (Boolean) -> Unit = {},
) {
    var searchOpen by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(searchOpen) {
        if (searchOpen) runCatching { focusRequester.requestFocus() }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = Spacing.gutter)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = AuroraColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            IconAction(
                icon = if (searchOpen) Icons.Default.Close else Icons.Default.Search,
                contentDescription = if (searchOpen) "Fechar pesquisa" else "Pesquisar",
                onClick = {
                    searchOpen = !searchOpen
                    onSearchOpenChange(searchOpen)
                    if (!searchOpen) onSearchQueryChange("")
                },
            )
            trailing?.invoke()
        }

        AnimatedVisibility(
            visible = searchOpen,
            enter = fadeIn(tween(150)) + expandVertically(tween(180)),
            exit = fadeOut(tween(120)) + shrinkVertically(tween(150)),
        ) {
            ContextualSearchField(
                query = searchQuery,
                onQueryChange = onSearchQueryChange,
                placeholder = searchPlaceholder,
                focusRequester = focusRequester,
                modifier = Modifier.padding(bottom = Spacing.md),
            )
        }
    }
}

@Composable
private fun IconAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(44.dp) // comfortable touch/focus target
            .clip(RoundedCornerShape(100.dp))
            .tvFocusable(shape = CircleShape, accent = MaterialTheme.colorScheme.primary)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = AuroraColors.TextPrimary, modifier = Modifier.size(22.dp))
    }
}

@Composable
fun ContextualSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .frostSurface(RoundedCornerShape(14.dp), flat = AuroraColors.SurfaceHigh)
            .padding(horizontal = Spacing.lg),
    ) {
        Icon(Icons.Default.Search, contentDescription = null, tint = AuroraColors.TextTertiary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(Spacing.md))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(
                    placeholder,
                    style = MaterialTheme.typography.bodyMedium,
                    color = AuroraColors.TextTertiary,
                    modifier = Modifier.align(Alignment.CenterStart),
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(
                    color = AuroraColors.TextPrimary,
                    fontSize = MaterialTheme.typography.bodyMedium.fontSize,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 13.dp)
                    .then(focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier),
            )
        }
        if (query.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(100.dp))
                    .tvFocusable(shape = CircleShape, accent = MaterialTheme.colorScheme.primary)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onQueryChange("") },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Close, contentDescription = "Limpar", tint = AuroraColors.TextTertiary, modifier = Modifier.size(16.dp))
            }
        }
    }
}

/**
 * Horizontal category strip. Kept separate from PageHeader so it scrolls
 * with the content rather than pinning under the title.
 */
@Composable
fun CategoryStrip(
    categories: List<Pair<String?, String>>, // id (null = "Todos") to label
    selectedId: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = Spacing.gutter),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        items(categories, key = { it.first ?: "__all__" }) { (id, label) ->
            CategoryChip(
                text = label,
                selected = id == selectedId,
                onClick = { onSelect(id) },
            )
        }
    }
}
