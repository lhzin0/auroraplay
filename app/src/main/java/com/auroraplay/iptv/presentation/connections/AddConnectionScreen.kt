package com.auroraplay.iptv.presentation.connections

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.auroraplay.iptv.core.theme.AuroraColors
import com.auroraplay.iptv.presentation.components.AppButton
import com.auroraplay.iptv.presentation.components.ErrorState

@Composable
fun AddConnectionScreen(
    profileId: String?,
    onBack: () -> Unit,
    onConnected: () -> Unit,
    viewModel: AddConnectionViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AuroraColors.BackgroundBase)
            .statusBarsPadding()
    ) {
        if (state.step == AddConnectionStep.FORM || state.step == AddConnectionStep.ERROR) {
            com.auroraplay.iptv.presentation.components.BackButton(
                onClick = onBack,
                modifier = Modifier.padding(8.dp),
            )
        }

        AnimatedContent(targetState = state.step, label = "addConnectionStep") { step ->
            when (step) {
                AddConnectionStep.FORM -> ConnectionForm(
                    onConnect = { name, url, user, pass, backupUrl -> viewModel.connect(name, url, user, pass, profileId, backupUrl) }
                )
                AddConnectionStep.ERROR -> Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    ErrorState(message = state.errorMessage ?: "Não foi possível conectar ao servidor.", onRetry = { viewModel.resetToForm() })
                }
                AddConnectionStep.DONE -> SyncStatusView(
                    message = "Conexão configurada com sucesso.",
                    isDone = true,
                    onContinue = onConnected,
                )
                else -> SyncStatusView(message = messageForStep(step), isDone = false)
            }
        }
    }
}

private fun messageForStep(step: AddConnectionStep): String = when (step) {
    AddConnectionStep.CONNECTING -> "Conectando ao servidor..."
    AddConnectionStep.SYNC_CHANNELS -> "Sincronizando canais..."
    AddConnectionStep.SYNC_MOVIES -> "Sincronizando filmes..."
    AddConnectionStep.SYNC_SERIES -> "Sincronizando séries..."
    else -> "Conectando..."
}

@Composable
private fun SyncStatusView(message: String, isDone: Boolean, onContinue: (() -> Unit)? = null) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (isDone) {
            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = AuroraColors.Success, modifier = Modifier.size(56.dp))
        } else {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(20.dp))
        Text(message, style = MaterialTheme.typography.titleLarge, color = AuroraColors.TextPrimary)
        if (isDone && onContinue != null) {
            Spacer(Modifier.height(24.dp))
            AppButton(text = "Continuar", onClick = onContinue)
        }
    }
}

@Composable
private fun ConnectionForm(onConnect: (String, String, String, String, String?) -> Unit) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var backupUrl by remember { mutableStateOf("") }
    var showBackupField by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = 24.dp)
    ) {
        Text("Adicionar conexão", style = MaterialTheme.typography.headlineMedium, color = AuroraColors.TextPrimary)
        Spacer(Modifier.height(6.dp))
        Text(
            "Configure sua conexão Xtream para acessar seus canais, filmes e séries.",
            style = MaterialTheme.typography.bodyMedium,
            color = AuroraColors.TextSecondary,
        )
        Spacer(Modifier.height(28.dp))

        LabeledField(label = "Nome da conexão", value = name, onChange = { name = it }, placeholder = "Ex: Minha lista principal")
        Spacer(Modifier.height(16.dp))
        LabeledField(label = "URL do servidor", value = url, onChange = { url = it }, placeholder = "https://servidor.exemplo.com", keyboardType = KeyboardType.Uri)
        if (url.trim().startsWith("http://", ignoreCase = true)) {
            Text("Este servidor usa HTTP: login, senha e conteúdo trafegam sem criptografia. Use o endereço HTTPS do provedor, se disponível.",
                color = AuroraColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(16.dp))
        LabeledField(label = "Usuário", value = username, onChange = { username = it }, placeholder = "usuario")
        Spacer(Modifier.height(16.dp))
        LabeledField(
            label = "Senha",
            value = password,
            onChange = { password = it },
            placeholder = "••••••••",
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            trailingText = if (showPassword) "Ocultar" else "Mostrar",
            onTrailingClick = { showPassword = !showPassword },
        )

        Spacer(Modifier.height(20.dp))
        if (showBackupField) {
            LabeledField(
                label = "Servidor de backup (opcional)",
                value = backupUrl,
                onChange = { backupUrl = it },
                placeholder = "https://servidor-reserva.exemplo.com",
                keyboardType = KeyboardType.Uri,
            )
            Text(
                "Usado automaticamente se o servidor principal ficar fora do ar — mesma conta, outro endereço.",
                style = MaterialTheme.typography.bodySmall,
                color = AuroraColors.TextTertiary,
                modifier = Modifier.padding(top = 6.dp),
            )
        } else {
            Text(
                "+ Adicionar servidor de backup",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { showBackupField = true },
                ),
            )
        }

        Spacer(Modifier.height(32.dp))
        AppButton(
            text = "Conectar",
            onClick = { onConnect(name, url, username, password, backupUrl.trim().ifBlank { null }) },
            fullWidth = true,
        )
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun LabeledField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingText: String? = null,
    onTrailingClick: (() -> Unit)? = null,
) {
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge, color = AuroraColors.TextSecondary)
        Spacer(Modifier.height(6.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(AuroraColors.SurfaceHigh)
                .padding(horizontal = 16.dp)
        ) {
            Box(Modifier.weight(1f)) {
                if (value.isEmpty()) {
                    Text(
                        placeholder,
                        style = MaterialTheme.typography.bodyLarge,
                        color = AuroraColors.TextTertiary,
                        modifier = Modifier.align(Alignment.CenterStart),
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onChange,
                    singleLine = true,
                    visualTransformation = visualTransformation,
                    keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                    textStyle = TextStyle(color = AuroraColors.TextPrimary, fontSize = MaterialTheme.typography.bodyLarge.fontSize),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
                )
            }
            if (trailingText != null && onTrailingClick != null) {
                Text(
                    trailingText,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onTrailingClick,
                    ),
                )
            }
        }
    }
}
