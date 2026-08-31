package com.auroraplay.iptv.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontWeight
import com.auroraplay.iptv.core.theme.AuroraColors

/**
 * Back behavior for the main shell.
 *
 * - On a non-Home tab, back returns to Home rather than leaving the app, so
 *   Home behaves as the persistent root of the experience.
 * - On Home, back asks for confirmation instead of closing immediately,
 *   which is what previously made the app exit unexpectedly.
 */
@Composable
fun MainShellBackHandler(
    currentTab: MainTab,
    onNavigateToHome: () -> Unit,
    onConfirmExit: () -> Unit,
) {
    var showExitDialog by remember { mutableStateOf(false) }

    BackHandler(enabled = true) {
        if (currentTab != MainTab.HOME) {
            onNavigateToHome()
        } else {
            showExitDialog = true
        }
    }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            containerColor = AuroraColors.BackgroundElevated,
            title = {
                Text(
                    "Sair do AuroraPlay?",
                    style = MaterialTheme.typography.titleLarge,
                    color = AuroraColors.TextPrimary,
                )
            },
            text = {
                Text(
                    "Seu progresso de reprodução já está salvo.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AuroraColors.TextSecondary,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showExitDialog = false
                    onConfirmExit()
                }) {
                    Text("Sair", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitDialog = false }) {
                    Text("Cancelar", color = AuroraColors.TextSecondary)
                }
            },
        )
    }
}
