package com.auroraplay.iptv.presentation.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.auroraplay.iptv.core.theme.AuroraColors

/** Recovery is accessible before creating a profile or entering Xtream credentials. */
@Composable
fun FileBackupScreen(onBack: () -> Unit) {
    Scaffold(
        containerColor = AuroraColors.BackgroundBase,
        topBar = { TextButton(onClick = onBack, modifier = Modifier.statusBarsPadding()) { Text("Voltar aos perfis") } },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(20.dp)) {
            item { FileBackupSection() }
        }
    }
}
