package com.auroraplay.iptv.update

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Modifier
import com.auroraplay.iptv.core.theme.AuroraColors
import com.auroraplay.iptv.core.theme.AuroraPlayTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class AppUpdateActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AuroraPlayTheme {
                Column(Modifier.fillMaxSize().background(AuroraColors.BackgroundBase)
                    .systemBarsPadding().verticalScroll(rememberScrollState())) {
                    TextButton(onClick = { finish() }) { Text("Voltar") }
                    AppUpdateSection()
                }
            }
        }
    }
}
