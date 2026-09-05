package com.auroraplay.iptv.presentation.settings

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.auroraplay.iptv.core.theme.AuroraColors
import com.auroraplay.iptv.core.theme.frostSurface
import com.auroraplay.iptv.presentation.components.ProfileAvatar
import com.auroraplay.iptv.presentation.components.Spacing
import com.auroraplay.iptv.presentation.components.rememberTvFocusVisuals
import com.auroraplay.iptv.presentation.components.tvBringIntoViewOnFocus
import com.auroraplay.iptv.presentation.components.tvFocusable

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenConnections: () -> Unit,
    onOpenProfileEditor: (String) -> Unit = {},
    onOpenHistory: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val activeProfile by viewModel.activeProfile.collectAsStateWithLifecycle()
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    // "quality" | "audio" | "subtitle" | null — which option submenu is open.
    var openPicker by remember { mutableStateOf<String?>(null) }
    var showProfileSwitcher by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = AuroraColors.BackgroundBase,
        topBar = {
            Box(
                modifier = Modifier
                    .statusBarsPadding()
                    .fillMaxWidth()
                    .padding(start = 16.dp, top = 10.dp, bottom = 2.dp),
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(44.dp)
                        .tvFocusable(shape = CircleShape, accent = MaterialTheme.colorScheme.primary)
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        .border(1.dp, Color.White.copy(alpha = 0.06f), CircleShape),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Voltar",
                        tint = AuroraColors.TextPrimary,
                        modifier = Modifier.size(23.dp),
                    )
                }
            }
        }
    ) { padding ->
        // Capped width + centered: on a tablet or a landscape TV screen, a
        // list of settings stretched edge-to-edge reads as unfinished — every
        // other detail screen already caps its text/content this way.
        Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = Spacing.navBarClearance),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                item { SettingsHero() }
                item {
                    SettingsSection(title = "Perfil") {
                        activeProfile?.let { profile ->
                            SettingsRow(
                                leading = { ProfileAvatar(emoji = profile.avatarEmoji, colorHex = profile.avatarColorHex, avatarUri = profile.avatarUri, size = 40.dp) },
                                title = "Editar perfil",
                                subtitle = "${profile.name} • nome, avatar e cor",
                                onClick = { onOpenProfileEditor(profile.id) },
                                showDivider = profiles.size > 1,
                            )
                        }
                        if (profiles.size > 1) {
                            SettingsRow(
                                icon = Icons.Default.SwitchAccount,
                                title = "Trocar perfil",
                                subtitle = "Alternar rapidamente entre perfis",
                                onClick = { showProfileSwitcher = true },
                                showDivider = false,
                            )
                        }
                    }
                }

                // Histórico fica logo abaixo do card de Perfil: é uma lista por
                // perfil e só o usuário a apaga.
                item {
                    SettingsSection(title = "Histórico") {
                        SettingsRow(
                            icon = Icons.Default.History,
                            title = "Conteúdos assistidos",
                            subtitle = "Filmes e séries que você já assistiu neste perfil",
                            onClick = onOpenHistory,
                            showDivider = false,
                        )
                    }
                }

                item {
                    SettingsSection(title = "Conta e conexão") {
                        SettingsRow(
                            icon = Icons.Default.Cable,
                            title = "Minhas conexões",
                            subtitle = "Gerenciar conexões Xtream",
                            onClick = onOpenConnections,
                            showDivider = false,
                        )
                    }
                }

                item {
                    SettingsSection(title = "Reprodução") {
                        SettingsSwitchRow(
                            icon = Icons.Default.SkipNext,
                            title = "Próximo episódio automático",
                            checked = settings.autoPlayNext,
                            onCheckedChange = { viewModel.setAutoPlayNext(it) },
                        )
                        SettingsRow(
                            icon = Icons.Default.HighQuality,
                            title = "Qualidade",
                            subtitle = qualityLabel(settings.playbackQuality),
                            onClick = { openPicker = "quality" },
                        )
                        SettingsRow(
                            icon = Icons.Default.Audiotrack,
                            title = "Áudio preferido",
                            subtitle = langLabel(settings.preferredAudioLang),
                            onClick = { openPicker = "audio" },
                        )
                        SettingsRow(
                            icon = Icons.Default.Subtitles,
                            title = "Legenda preferida",
                            subtitle = langLabel(settings.preferredSubtitleLang),
                            onClick = { openPicker = "subtitle" },
                        )
                        SettingsRow(
                            icon = Icons.Default.Replay,
                            title = "Avançar / retroceder",
                            subtitle = "${settings.seekSeconds} segundos",
                            onClick = { openPicker = "seek" },
                        )
                        SettingsSwitchRow(
                            icon = Icons.Default.PictureInPictureAlt,
                            title = "Picture-in-Picture",
                            checked = settings.pipEnabled,
                            onCheckedChange = { viewModel.setPipEnabled(it) },
                            showDivider = false,
                        )
                    }
                }

                item {
                    SettingsSection(title = "Interface") {
                        Column(Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md)) {
                            Text("Cor de destaque", style = MaterialTheme.typography.titleMedium, color = AuroraColors.TextPrimary)
                            Spacer(Modifier.height(Spacing.md))
                            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                                AuroraColors.AccentPalette.forEach { (name, color) ->
                                    val hex = "#" + Integer.toHexString(color.toArgb()).takeLast(6)
                                    AccentSwatch(
                                        color = color,
                                        name = name,
                                        selected = settings.accentColorHex.equals(hex, ignoreCase = true),
                                        onClick = { viewModel.setAccentColor(hex) },
                                    )
                                }
                            }
                        }
                        SettingsDivider()
                        SettingsSwitchRow(
                            icon = Icons.Default.BlurOn,
                            title = "Vidro fosco (FrostGlass)",
                            checked = settings.frostGlass,
                            onCheckedChange = { viewModel.setFrostGlass(it) },
                            showDivider = false,
                        )
                        // "Animações" toggle intentionally not surfaced here — the
                        // motion polish is always on now; the setting still exists
                        // in AppSettings for a possible future accessibility need.
                    }
                }

                // "Informações e trailers" section removed from the UI — the
                // automatic-metadata and YouTube-trailer behaviour it merely
                // described is unchanged and still runs on the detail pages.

                item {
                    SettingsSection(title = "Notificações") {
                        SettingsSwitchRow(
                            icon = Icons.Default.NotificationsActive,
                            title = "Novos episódios de séries favoritas",
                            checked = settings.notifyNewEpisodes,
                            onCheckedChange = { viewModel.setNotifyNewEpisodes(it) },
                            showDivider = false,
                        )
                    }
                }

                item {
                    SettingsSection(title = "Dados") {
                        SettingsRow(
                            icon = Icons.Default.Sync,
                            title = "Sincronização automática",
                            subtitle = autoSyncLabel(settings.autoSyncHours),
                            onClick = { openPicker = "autosync" },
                        )
                        SettingsSwitchRow(
                            icon = Icons.Default.Wifi,
                            title = "Baixar somente com Wi-Fi",
                            checked = settings.downloadWifiOnly,
                            onCheckedChange = { viewModel.setDownloadWifiOnly(it) },
                        )
                        SettingsRow(icon = Icons.Default.CleaningServices, title = "Limpar cache", subtitle = "Remove catálogos armazenados localmente", onClick = { viewModel.clearCache() })
                        SettingsRow(
                            icon = Icons.Default.BugReport,
                            title = "Compartilhar log de erro",
                            subtitle = "Envia o registro do último fechamento inesperado",
                            onClick = {
                                val log = com.auroraplay.iptv.core.util.CrashLogWriter.latest(context)
                                if (log == null) {
                                    android.widget.Toast.makeText(context, "Nenhum erro registrado ainda.", android.widget.Toast.LENGTH_SHORT).show()
                                } else {
                                    val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.updates", log)
                                    val sendIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(android.content.Intent.createChooser(sendIntent, null))
                                }
                            },
                        )
                        SettingsRow(
                            icon = Icons.Default.RestartAlt,
                            title = "Restaurar configurações",
                            subtitle = "Volta às configurações padrão",
                            onClick = { viewModel.restoreDefaults() },
                            showDivider = false,
                            danger = true,
                        )
                    }
                }

                // Backup fica logo abaixo de "Dados": os dois tratam do que é
                // guardado e restaurado, então ficam lado a lado.
                item { FileBackupSection() }

                item {
                    // Card "Versão" no fim das Configurações: concentra a versão
                    // instalada e as atualizações disponíveis num lugar só, sem
                    // card separado de "Atualização do App".
                    SettingsSection(title = "Versão") {
                        com.auroraplay.iptv.update.AppUpdateSection()
                        SettingsDivider()
                        SettingsInfoRow(
                            icon = Icons.Default.PrivacyTip,
                            title = "Sobre e privacidade",
                            subtitle = "AuroraPlay funciona apenas com suas próprias conexões",
                            showDivider = false,
                        )
                    }
                }
            }

            when (openPicker) {
                "quality" -> SettingsPickerDialog(
                    title = "Qualidade de reprodução",
                    options = listOf("auto" to "Automática", "high" to "Alta (até 1080p)", "medium" to "Média (até 720p)", "low" to "Baixa (até 480p)"),
                    selected = settings.playbackQuality,
                    onSelect = { value -> value?.let(viewModel::setPlaybackQuality) },
                    onDismiss = { openPicker = null },
                    footnote = "O limite se aplica a streams com múltiplas resoluções. Um canal ou filme com uma única qualidade é reproduzido como está.",
                )
                "audio" -> SettingsPickerDialog(
                    title = "Áudio preferido",
                    options = LANG_OPTIONS,
                    selected = settings.preferredAudioLang,
                    onSelect = viewModel::setPreferredAudioLang,
                    onDismiss = { openPicker = null },
                )
                "subtitle" -> SettingsPickerDialog(
                    title = "Legenda preferida",
                    options = LANG_OPTIONS,
                    selected = settings.preferredSubtitleLang,
                    onSelect = viewModel::setPreferredSubtitleLang,
                    onDismiss = { openPicker = null },
                )
                "seek" -> SettingsPickerDialog(
                    title = "Avançar / retroceder",
                    options = listOf("10" to "10 segundos", "5" to "5 segundos"),
                    selected = settings.seekSeconds.toString(),
                    onSelect = { value -> value?.toIntOrNull()?.let(viewModel::setSeekSeconds) },
                    onDismiss = { openPicker = null },
                )
                "autosync" -> SettingsPickerDialog(
                    title = "Sincronização automática",
                    options = listOf("0" to "Desligada", "12" to "A cada 12 horas", "24" to "A cada 24 horas", "168" to "Semanal"),
                    selected = settings.autoSyncHours.toString(),
                    onSelect = { value -> value?.toIntOrNull()?.let(viewModel::setAutoSyncHours) },
                    onDismiss = { openPicker = null },
                )
            }

            if (showProfileSwitcher) {
                val activity = LocalContext.current as? androidx.fragment.app.FragmentActivity
                ProfileSwitcherDialog(
                    profiles = profiles,
                    activeProfileId = activeProfile?.id,
                    onSelect = { id ->
                        val leavingKids = activeProfile?.isKids == true
                        val target = profiles.find { it.id == id }
                        val needsAuth = leavingKids && target?.isKids != true
                        if (needsAuth && activity != null &&
                            authAvailable(activity)
                        ) {
                            promptAuth(
                                activity = activity,
                                onOk = { viewModel.switchProfile(id); showProfileSwitcher = false },
                            )
                        } else {
                            viewModel.switchProfile(id)
                            showProfileSwitcher = false
                        }
                    },
                    onDismiss = { showProfileSwitcher = false },
                )
            }
        }
    }
}

private val AUTHERS =
    androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK or
        androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL

private fun authAvailable(activity: androidx.fragment.app.FragmentActivity): Boolean =
    androidx.biometric.BiometricManager.from(activity).canAuthenticate(AUTHERS) ==
        androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS

/** Device biometric / screen-lock confirmation. Used to gate leaving a
 * child profile via the quick switch — a small parental speed bump, not a
 * security boundary. */
private fun promptAuth(activity: androidx.fragment.app.FragmentActivity, onOk: () -> Unit) {
    val info = androidx.biometric.BiometricPrompt.PromptInfo.Builder()
        .setTitle("Confirme sua identidade")
        .setSubtitle("Necessário para sair de um perfil infantil")
        .setAllowedAuthenticators(AUTHERS)
        .build()
    androidx.biometric.BiometricPrompt(
        activity,
        androidx.core.content.ContextCompat.getMainExecutor(activity),
        object : androidx.biometric.BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: androidx.biometric.BiometricPrompt.AuthenticationResult) = onOk()
        },
    ).authenticate(info)
}

/** Quick profile switch — swaps the active profile in place, without going
 * back out to the full "Escolha o seu perfil" screen. */
@Composable
private fun ProfileSwitcherDialog(
    profiles: List<com.auroraplay.iptv.domain.model.Profile>,
    activeProfileId: String?,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AuroraColors.SurfaceDark,
        title = { Text("Trocar perfil", style = MaterialTheme.typography.titleLarge, color = AuroraColors.TextPrimary) },
        text = {
            Column {
                profiles.forEach { profile ->
                    val isActive = profile.id == activeProfileId
                    val rowShape = RoundedCornerShape(10.dp)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(rowShape)
                            .tvFocusable(shape = rowShape, accent = MaterialTheme.colorScheme.primary, enabled = !isActive)
                            .clickable(enabled = !isActive) { onSelect(profile.id) }
                            .padding(vertical = Spacing.sm, horizontal = Spacing.xs),
                    ) {
                        ProfileAvatar(
                            emoji = profile.avatarEmoji,
                            colorHex = profile.avatarColorHex,
                            avatarUri = profile.avatarUri,
                            size = 36.dp,
                        )
                        Spacer(Modifier.width(Spacing.md))
                        Text(
                            profile.name,
                            style = MaterialTheme.typography.bodyLarge,
                            color = AuroraColors.TextPrimary,
                            modifier = Modifier.weight(1f),
                        )
                        if (isActive) {
                            Icon(Icons.Default.Check, contentDescription = "Perfil atual", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Fechar", color = MaterialTheme.colorScheme.primary)
            }
        },
    )
}

private fun autoSyncLabel(h: Int) = when (h) {
    0 -> "Desligada"
    12 -> "A cada 12 horas"
    168 -> "Semanal"
    else -> "A cada 24 horas"
}

private fun qualityLabel(q: String) = when (q) {
    "auto" -> "Automática"; "high" -> "Alta"; "medium" -> "Média"; "low" -> "Baixa"; else -> "Automática"
}

/** Audio / subtitle language presets. `null` keeps the current behavior:
 * decide the first time a track is picked in the player, reuse it after. */
private val LANG_OPTIONS: List<Pair<String?, String>> = listOf(
    null to "Perguntar no player",
    "pt" to "Português",
    "en" to "Inglês",
    "es" to "Espanhol",
)

private fun langLabel(code: String?): String = when (code) {
    null -> "Perguntar no player"
    "pt" -> "Português"
    "en" -> "Inglês"
    "es" -> "Espanhol"
    else -> code.uppercase()
}

/** Radio-list submenu for a single-choice playback setting. */
@Composable
private fun SettingsPickerDialog(
    title: String,
    options: List<Pair<String?, String>>,
    selected: String?,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit,
    footnote: String? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AuroraColors.SurfaceDark,
        title = { Text(title, style = MaterialTheme.typography.titleLarge, color = AuroraColors.TextPrimary) },
        text = {
            Column {
                options.forEach { (value, label) ->
                    val rowShape = RoundedCornerShape(10.dp)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(rowShape)
                            .tvFocusable(shape = rowShape, accent = MaterialTheme.colorScheme.primary)
                            .clickable { onSelect(value); onDismiss() }
                            .padding(vertical = Spacing.xs),
                    ) {
                        RadioButton(
                            selected = value == selected,
                            onClick = { onSelect(value); onDismiss() },
                            colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary),
                        )
                        Spacer(Modifier.width(Spacing.sm))
                        Text(label, style = MaterialTheme.typography.bodyLarge, color = AuroraColors.TextPrimary)
                    }
                }
                if (footnote != null) {
                    Spacer(Modifier.height(Spacing.sm))
                    Text(footnote, style = MaterialTheme.typography.bodySmall, color = AuroraColors.TextSecondary)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Fechar", color = MaterialTheme.colorScheme.primary)
            }
        },
    )
}

@Composable
private fun SettingsHero() {
    Column(modifier = Modifier.padding(top = 2.dp, bottom = 2.dp)) {
        Text(
            "Configurações",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = AuroraColors.TextPrimary,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Personalize sua experiência no AuroraPlay",
            style = MaterialTheme.typography.bodyLarge,
            color = AuroraColors.TextSecondary,
        )
    }
}

/** Rounded graphite groups keep related controls visually compact while the
 * page itself stays quiet and cinematic. */
@Composable
internal fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
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
                .frostSurface(RoundedCornerShape(18.dp), flat = AuroraColors.SurfaceHigh)
                .border(1.dp, Color.White.copy(alpha = 0.045f), RoundedCornerShape(18.dp)),
            content = content,
        )
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(color = AuroraColors.Divider, thickness = 1.dp, modifier = Modifier.padding(start = 70.dp))
}

@Composable
private fun SettingsIconChip(icon: ImageVector, danger: Boolean = false) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(AuroraColors.BackgroundBase),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = if (danger) AuroraColors.Error else AuroraColors.TextSecondary, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector? = null,
    leading: (@Composable () -> Unit)? = null,
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    showDivider: Boolean = true,
    danger: Boolean = false,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    // focusedScale = 1f on purpose: a full-width row scaling up would overlap
    // its neighbors above/below. The background tint (driven by the same
    // ringAlpha) is the focus affordance here instead of a border ring.
    val visuals = rememberTvFocusVisuals(interactionSource, pressed = pressed, pressedScale = 0.99f, focusedScale = 1f)

    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .tvBringIntoViewOnFocus()
                .fillMaxWidth()
                .scale(visuals.scale)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = visuals.ringAlpha * 0.12f))
                .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
                .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        ) {
            when {
                leading != null -> leading()
                icon != null -> SettingsIconChip(icon, danger)
            }
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = if (danger) AuroraColors.Error else AuroraColors.TextPrimary)
                subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = AuroraColors.TextTertiary) }
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = AuroraColors.TextTertiary, modifier = Modifier.size(20.dp))
        }
        if (showDivider) SettingsDivider()
    }
}

/** Same row shape as [SettingsRow], but not clickable and no chevron — for
 * values that are purely informational (version number, about text). */
@Composable
private fun SettingsInfoRow(icon: ImageVector, title: String, subtitle: String?, showDivider: Boolean = true) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.md),
        ) {
            SettingsIconChip(icon)
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = AuroraColors.TextPrimary)
                subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = AuroraColors.TextTertiary) }
            }
        }
        if (showDivider) SettingsDivider()
    }
}

@Composable
private fun SettingsSwitchRow(
    icon: ImageVector,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    showDivider: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val visuals = rememberTvFocusVisuals(interactionSource, pressed = pressed, pressedScale = 0.99f, focusedScale = 1f)

    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .tvBringIntoViewOnFocus()
                .fillMaxWidth()
                .scale(visuals.scale)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = visuals.ringAlpha * 0.12f))
                .clickable(interactionSource = interactionSource, indication = null) { onCheckedChange(!checked) }
                .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        ) {
            SettingsIconChip(icon)
            Spacer(Modifier.width(Spacing.md))
            Text(title, style = MaterialTheme.typography.titleMedium, color = AuroraColors.TextPrimary, modifier = Modifier.weight(1f))
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                    uncheckedThumbColor = AuroraColors.TextTertiary,
                    uncheckedTrackColor = AuroraColors.SurfaceHigh,
                    uncheckedBorderColor = Color.Transparent,
                ),
            )
        }
        if (showDivider) SettingsDivider()
    }
}

@Composable
private fun AccentSwatch(color: Color, name: String, selected: Boolean, onClick: () -> Unit) {
    val size by animateDpAsState(if (selected) 40.dp else 32.dp, tween(180), label = "accentSwatchSize")
    Box(
        modifier = Modifier
            .size(44.dp)
            .tvFocusable(shape = CircleShape, accent = Color.White)
            .clickable(onClickLabel = "Cor de destaque $name", onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(color)
        )
        if (selected) {
            Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
        }
    }
}
