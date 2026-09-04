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
import androidx.compose.material.icons.filled.PhotoLibrary
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
 *
 * Laid out in grouped rounded cards to match Settings: an "Identidade" card
 * (avatar + name + emoji + colour), then "Perfil infantil" and "Bloqueio".
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
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        viewModel.updateAvatarUri(uri.toString())
    }

    LaunchedEffect(profileId) { viewModel.load(profileId) }

    Box(
        Modifier
            .fillMaxSize()
            .background(AuroraColors.BackgroundBase),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 640.dp)
                .fillMaxWidth()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.gutter),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .padding(top = 8.dp, bottom = 4.dp),
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(44.dp)
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        .border(1.dp, Color.White.copy(alpha = 0.06f), CircleShape),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Voltar",
                        tint = AuroraColors.TextPrimary,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Spacer(Modifier.width(Spacing.md))
                Text(
                    text = if (state.isEditing) "Editar perfil" else "Novo perfil",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = AuroraColors.TextPrimary,
                )
            }

            Spacer(Modifier.height(Spacing.lg))

            // --- Identidade ---
            EditorSection("Identidade") {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        ProfileAvatar(
                            emoji = state.emoji,
                            colorHex = state.colorHex,
                            avatarUri = state.avatarUri,
                            size = 104.dp,
                            onClick = { imagePicker.launch(arrayOf("image/*")) },
                        )
                        Spacer(Modifier.height(Spacing.sm))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .clickable { imagePicker.launch(arrayOf("image/*")) }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        ) {
                            Icon(
                                Icons.Default.PhotoLibrary,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "Escolher foto",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        if (!state.avatarUri.isNullOrBlank()) {
                            TextButton(
                                onClick = { viewModel.updateAvatarUri(null) },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            ) {
                                Text("Remover foto", style = MaterialTheme.typography.labelMedium, color = AuroraColors.TextSecondary)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(Spacing.lg))
                FieldLabel("Nome")
                Spacer(Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(AuroraColors.SurfaceDark)
                        .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 14.dp),
                    )
                }

                Spacer(Modifier.height(Spacing.lg))
                FieldLabel("Avatar")
                Spacer(Modifier.height(Spacing.sm))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    items(avatarEmojis) { emoji ->
                        val selected = emoji == state.emoji && state.avatarUri.isNullOrBlank()
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(60.dp)) {
                            ProfileAvatar(
                                emoji = emoji,
                                colorHex = state.colorHex,
                                size = 52.dp,
                                onClick = { viewModel.updateEmoji(emoji) },
                            )
                            if (selected) {
                                Box(
                                    Modifier
                                        .size(60.dp)
                                        .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape),
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(Spacing.lg))
                FieldLabel("Cor")
                Spacer(Modifier.height(Spacing.sm))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    items(avatarColors) { hex ->
                        val color = runCatching { Color(android.graphics.Color.parseColor(hex)) }
                            .getOrDefault(AuroraColors.AccentDefault)
                        val selected = hex.equals(state.colorHex, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(color)
                                .then(
                                    if (selected) Modifier.border(2.dp, Color.White, CircleShape)
                                    else Modifier,
                                )
                                .clickable { viewModel.updateColor(hex) },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (selected) {
                                Icon(Icons.Default.Check, contentDescription = "Selecionada", tint = Color.Black, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(Spacing.lg))

            // --- Perfil infantil ---
            EditorSection("Perfil infantil") {
                ToggleRow(
                    title = "Perfil infantil",
                    subtitle = "Mostra apenas conteúdo apropriado neste perfil.",
                    checked = state.isKids,
                    onCheckedChange = viewModel::updateIsKids,
                )
            }

            Spacer(Modifier.height(Spacing.lg))

            // --- Bloqueio ---
            EditorSection("Bloqueio") {
                ToggleRow(
                    title = "Bloquear com PIN",
                    subtitle = "Pede um PIN de 4 dígitos para entrar neste perfil.",
                    checked = state.lockEnabled,
                    onCheckedChange = viewModel::toggleLock,
                )

                if (state.lockEnabled && state.hasSavedPin && !state.isChangingPin) {
                    TextButton(
                        onClick = { viewModel.requestChangePin() },
                        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp),
                    ) {
                        Text("Alterar PIN", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    }
                }

                if (state.lockEnabled && (!state.hasSavedPin || state.isChangingPin)) {
                    Spacer(Modifier.height(Spacing.sm))
                    PinField(label = "Novo PIN", value = state.newPin, onValueChange = viewModel::updateNewPin)
                    Spacer(Modifier.height(Spacing.sm))
                    PinField(label = "Confirmar PIN", value = state.confirmPin, onValueChange = viewModel::updateConfirmPin)
                    state.pinError?.let { pinError ->
                        Spacer(Modifier.height(Spacing.sm))
                        Text(pinError, style = MaterialTheme.typography.bodySmall, color = AuroraColors.Error)
                    }
                }

                if (state.lockEnabled) {
                    val biometricManager = remember { androidx.biometric.BiometricManager.from(context) }
                    val canUseBiometrics = remember {
                        biometricManager.canAuthenticate(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK) ==
                            androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS
                    }
                    Spacer(Modifier.height(Spacing.md))
                    ToggleRow(
                        title = "Desbloquear com biometria",
                        subtitle = if (canUseBiometrics) {
                            "Usa a digital ou o rosto já configurados no aparelho, no lugar do PIN."
                        } else {
                            "Nenhuma biometria configurada — configure nos Ajustes do sistema para usar aqui."
                        },
                        checked = state.biometricEnabled && canUseBiometrics,
                        enabled = canUseBiometrics,
                        onCheckedChange = viewModel::updateBiometricEnabled,
                    )
                }
            }

            Spacer(Modifier.height(Spacing.xl))
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
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }
            Spacer(Modifier.height(Spacing.navBarClearance))
        }
    }
}

/** A rounded graphite group, matching the Settings section cards. */
@Composable
private fun EditorSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = AuroraColors.TextPrimary,
            modifier = Modifier.padding(start = 2.dp, bottom = 10.dp),
        )
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(AuroraColors.SurfaceHigh)
                .border(1.dp, Color.White.copy(alpha = 0.045f), RoundedCornerShape(18.dp))
                .padding(horizontal = Spacing.lg, vertical = Spacing.lg),
            content = content,
        )
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = AuroraColors.TextSecondary)
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = AuroraColors.TextPrimary)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = AuroraColors.TextTertiary)
        }
        Spacer(Modifier.width(Spacing.md))
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary),
        )
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
                .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(10.dp))
                .padding(horizontal = Spacing.md, vertical = Spacing.md),
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
