package com.auroraplay.iptv.presentation.profiles

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.auroraplay.iptv.core.theme.AuroraColors
import com.auroraplay.iptv.presentation.components.AppButton
import com.auroraplay.iptv.presentation.components.ProfileAvatar
import com.auroraplay.iptv.presentation.components.Spacing

private val avatarEmojis = listOf("🎬", "🍿", "📺", "🎮", "⚽", "🚀", "🐱", "🎧", "🌙", "⭐")
private val avatarColors = listOf("#7C5CFF", "#32D8E0", "#2ED47A", "#FFA53D", "#FF4FA3", "#FF4B4B")

/**
 * Single screen used for both creating and editing a profile — same layout,
 * same validation, so the two flows can't drift apart visually. When
 * [profileId] is null it creates; otherwise it loads and updates.
 */
@Composable
fun ProfileEditorScreen(
    profileId: String?,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: ProfileEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        viewModel.updateAvatarUri(uri.toString())
    }

    LaunchedEffect(profileId) { viewModel.load(profileId) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AuroraColors.BackgroundBase)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.gutter)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 56.dp)) {
            com.auroraplay.iptv.presentation.components.BackButton(onClick = onBack)
            Spacer(Modifier.width(Spacing.sm))
            Text(
                text = if (state.isEditing) "Editar perfil" else "Novo perfil",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = AuroraColors.TextPrimary,
            )
        }

        Spacer(Modifier.height(Spacing.xl))

        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            ProfileAvatar(emoji = state.emoji, colorHex = state.colorHex, avatarUri = state.avatarUri, size = 112.dp, onClick = { imagePicker.launch(arrayOf("image/*")) })
        }

        Spacer(Modifier.height(Spacing.xl))

        Text("Nome", style = MaterialTheme.typography.labelLarge, color = AuroraColors.TextSecondary)
        Spacer(Modifier.height(Spacing.sm))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(AuroraColors.SurfaceHigh)
                .padding(horizontal = Spacing.lg),
        ) {
            if (state.name.isEmpty()) {
                Text(
                    "Como devemos chamar este perfil?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AuroraColors.TextTertiary,
                    modifier = Modifier.align(Alignment.CenterStart),
                )
            }
            BasicTextField(
                value = state.name,
                onValueChange = viewModel::updateName,
                singleLine = true,
                textStyle = TextStyle(
                    color = AuroraColors.TextPrimary,
                    fontSize = MaterialTheme.typography.bodyLarge.fontSize,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth().padding(vertical = 15.dp),
            )
        }

        Spacer(Modifier.height(Spacing.xl))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Foto do perfil", style = MaterialTheme.typography.labelLarge, color = AuroraColors.TextSecondary)
            Text(
                "Escolher foto",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { imagePicker.launch(arrayOf("image/*")) },
            )
        }
        Spacer(Modifier.height(Spacing.sm))
        Text(
            "Use uma foto do seu dispositivo ou escolha um avatar abaixo.",
            style = MaterialTheme.typography.bodySmall,
            color = AuroraColors.TextTertiary,
        )
        Spacer(Modifier.height(Spacing.md))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            items(avatarEmojis) { emoji ->
                val selected = emoji == state.emoji
                Box(contentAlignment = Alignment.Center) {
                    ProfileAvatar(
                        emoji = emoji,
                        colorHex = state.colorHex,
                        size = 56.dp,
                        onClick = { viewModel.updateEmoji(emoji) },
                    )
                    if (selected) {
                        Box(
                            Modifier
                                .size(64.dp)
                                .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                        )
                    }
                }
            }
        }
        if (!state.avatarUri.isNullOrBlank()) {
            TextButton(onClick = { viewModel.updateAvatarUri(null) }) {
                Text("Remover foto", color = AuroraColors.TextSecondary)
            }
        }

        Spacer(Modifier.height(Spacing.xl))
        Text("Cor", style = MaterialTheme.typography.labelLarge, color = AuroraColors.TextSecondary)
        Spacer(Modifier.height(Spacing.md))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            items(avatarColors) { hex ->
                val color = runCatching { Color(android.graphics.Color.parseColor(hex)) }
                    .getOrDefault(AuroraColors.AccentDefault)
                val selected = hex.equals(state.colorHex, ignoreCase = true)
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(color)
                        .clickable { viewModel.updateColor(hex) },
                    contentAlignment = Alignment.Center,
                ) {
                    if (selected) {
                        Icon(Icons.Default.Check, contentDescription = "Selecionada", tint = Color.Black, modifier = Modifier.size(22.dp))
                    }
                }
            }
        }

        Spacer(Modifier.height(Spacing.xl))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(AuroraColors.SurfaceHigh)
                .clickable { viewModel.updateIsKids(!state.isKids) }
                .padding(horizontal = Spacing.lg, vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Perfil infantil", style = MaterialTheme.typography.titleMedium, color = AuroraColors.TextPrimary)
                Spacer(Modifier.height(2.dp))
                Text(
                    "Esconde conteúdo adulto neste perfil.",
                    style = MaterialTheme.typography.bodySmall,
                    color = AuroraColors.TextTertiary,
                )
            }
            Switch(
                checked = state.isKids,
                onCheckedChange = viewModel::updateIsKids,
                colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary),
            )
        }

        Spacer(Modifier.height(Spacing.md))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(AuroraColors.SurfaceHigh)
                .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Bloquear com PIN", style = MaterialTheme.typography.titleMedium, color = AuroraColors.TextPrimary)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "Pede um PIN de 4 dígitos para entrar neste perfil.",
                        style = MaterialTheme.typography.bodySmall,
                        color = AuroraColors.TextTertiary,
                    )
                }
                Switch(
                    checked = state.lockEnabled,
                    onCheckedChange = viewModel::toggleLock,
                    colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary),
                )
            }

            if (state.lockEnabled && state.hasSavedPin && !state.isChangingPin) {
                Spacer(Modifier.height(Spacing.sm))
                TextButton(onClick = { viewModel.requestChangePin() }) {
                    Text("Alterar PIN", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
            }

            if (state.lockEnabled && (!state.hasSavedPin || state.isChangingPin)) {
                Spacer(Modifier.height(Spacing.md))
                PinField(label = "Novo PIN", value = state.newPin, onValueChange = viewModel::updateNewPin)
                Spacer(Modifier.height(Spacing.sm))
                PinField(label = "Confirmar PIN", value = state.confirmPin, onValueChange = viewModel::updateConfirmPin)
                state.pinError?.let { pinError ->
                    Spacer(Modifier.height(Spacing.sm))
                    Text(pinError, style = MaterialTheme.typography.bodySmall, color = AuroraColors.Error)
                }
            }

            if (state.lockEnabled) {
                val context = LocalContext.current
                val biometricManager = remember { androidx.biometric.BiometricManager.from(context) }
                val canUseBiometrics = remember {
                    biometricManager.canAuthenticate(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK) ==
                        androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS
                }
                Spacer(Modifier.height(Spacing.md))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Desbloquear com biometria", style = MaterialTheme.typography.titleMedium, color = AuroraColors.TextPrimary)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            if (canUseBiometrics) "Usa a impressão digital ou reconhecimento facial já configurados no aparelho, no lugar do PIN."
                            else "Nenhuma biometria configurada neste aparelho — configure em Ajustes do sistema para usar aqui.",
                            style = MaterialTheme.typography.bodySmall,
                            color = AuroraColors.TextTertiary,
                        )
                    }
                    Switch(
                        checked = state.biometricEnabled && canUseBiometrics,
                        enabled = canUseBiometrics,
                        onCheckedChange = viewModel::updateBiometricEnabled,
                        colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary),
                    )
                }
            }
        }

        Spacer(Modifier.height(Spacing.xxl + Spacing.lg))
        AppButton(
            text = if (state.isEditing) "Salvar alterações" else "Criar perfil",
            onClick = { viewModel.save { onSaved() } },
            icon = Icons.Default.Check,
            fullWidth = true,
        )
        if (state.name.isBlank()) {
            Spacer(Modifier.height(Spacing.sm))
            Text(
                "Dê um nome ao perfil para continuar.",
                style = MaterialTheme.typography.bodySmall,
                color = AuroraColors.TextTertiary,
            )
        }
        Spacer(Modifier.height(Spacing.navBarClearance))
    }
}

@Composable
private fun PinField(label: String, value: String, onValueChange: (String) -> Unit) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = AuroraColors.TextSecondary)
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(AuroraColors.SurfaceDark)
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(color = AuroraColors.TextPrimary, fontSize = 18.sp, letterSpacing = 6.sp),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                visualTransformation = PasswordVisualTransformation(mask = '•'),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
