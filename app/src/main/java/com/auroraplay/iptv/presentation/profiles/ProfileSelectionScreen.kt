package com.auroraplay.iptv.presentation.profiles

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil.compose.AsyncImage
import com.auroraplay.iptv.core.theme.AuroraColors
import com.auroraplay.iptv.domain.model.Profile
import com.auroraplay.iptv.presentation.components.Spacing
import kotlinx.coroutines.delay

@Composable
fun ProfileSelectionScreen(
    onProfileSelected: () -> Unit,
    onAddProfile: () -> Unit,
    onEditProfile: (String) -> Unit = {},
    onOpenBackup: () -> Unit = {},
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    var manageMode by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<Profile?>(null) }
    // Editing or deleting a PIN-locked / kids profile is gated by an auth
    // challenge; `second` is true for a delete, false for an edit.
    var pendingManage by remember { mutableStateOf<Pair<Profile, Boolean>?>(null) }
    // A locked profile is intercepted here rather than deeper in the
    // selection flow, so both the single-profile and grid layouts share the
    // exact same gate without duplicating the check.
    var pendingUnlock by remember { mutableStateOf<Profile?>(null) }
    val context = LocalContext.current
    val activity = context as? androidx.fragment.app.FragmentActivity

    fun unlockNow(profile: Profile) {
        viewModel.selectProfile(profile.id)
        onProfileSelected()
    }

    fun attemptSelect(profile: Profile) {
        if (!profile.isLocked) {
            unlockNow(profile)
            return
        }
        // Biometric is only ever offered as a shortcut past the PIN, never a
        // replacement for it — any failure, cancel, or missing hardware just
        // falls back to the same PIN dialog a non-biometric profile uses.
        if (profile.biometricEnabled && activity != null) {
            val promptInfo = androidx.biometric.BiometricPrompt.PromptInfo.Builder()
                .setTitle("Desbloquear ${profile.name}")
                .setSubtitle("Confirme sua identidade para continuar")
                .setNegativeButtonText("Usar PIN")
                .build()
            val prompt = androidx.biometric.BiometricPrompt(
                activity,
                androidx.core.content.ContextCompat.getMainExecutor(context),
                object : androidx.biometric.BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: androidx.biometric.BiometricPrompt.AuthenticationResult) {
                        unlockNow(profile)
                    }
                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        // Covers both "Usar PIN" and a hard cancel/lockout —
                        // either way the PIN dialog is the correct next step.
                        pendingUnlock = profile
                    }
                    // onAuthenticationFailed (a single bad fingerprint read) is
                    // deliberately not overridden: the system prompt itself
                    // already lets the person retry without AuroraPlay
                    // stepping in to fall back early on one bad read.
                },
            )
            prompt.authenticate(promptInfo)
        } else {
            pendingUnlock = profile
        }
    }

    val slides = state.slides

    Column(modifier = Modifier.fillMaxSize().background(AuroraColors.BackgroundBase)) {
        // ---- Hero: rotating artwork, fills whatever the picker doesn't ----
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .heightIn(min = 200.dp)
                // The ken-burns .scale() and the two stacked Crossfade layers
                // would otherwise draw past this box into the picker panel
                // below (the colored strip bug) — clip them here.
                .clipToBounds(),
        ) {
            if (slides.isNotEmpty()) {
                var index by remember(slides) { mutableIntStateOf(0) }
                LaunchedEffect(slides) {
                    if (slides.size > 1) {
                        while (true) {
                            delay(6500)
                            index = (index + 1) % slides.size
                        }
                    }
                }
                val slide = slides[index.coerceIn(0, slides.lastIndex)]

                // Very slow drift so the still never feels frozen. Kept tiny on
                // portrait posters so the composition the designer framed (face
                // upper-centre) is preserved — barely a "zoom", more a breath.
                val zoomTarget = if (slide.wide) 1.05f else 1.025f
                val zoom by rememberInfiniteTransition(label = "kenBurns").animateFloat(
                    initialValue = 1f,
                    targetValue = zoomTarget,
                    animationSpec = infiniteRepeatable(tween(18000, easing = LinearEasing), RepeatMode.Reverse),
                    label = "kenBurnsZoom",
                )

                // The whole hero (art + scrim + title) dissolves between slides,
                // matching the reference's crossfade.
                Crossfade(
                    targetState = slide,
                    animationSpec = tween(850),
                    label = "heroSlide",
                    modifier = Modifier.fillMaxSize(),
                ) { s ->
                    Box(Modifier.fillMaxSize()) {
                        AsyncImage(
                            model = s.imageUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            // Bias the crop toward the upper third of a portrait
                            // poster — that's where the main face sits — instead
                            // of a dead-centre slice; backdrops stay centred.
                            alignment = if (s.wide) Alignment.Center else BiasAlignment(0f, -0.5f),
                            modifier = Modifier.fillMaxSize().scale(zoom),
                        )
                        Box(
                            Modifier.matchParentSize().background(
                                Brush.verticalGradient(
                                    0.00f to Color.Black.copy(alpha = 0.42f),
                                    0.14f to Color.Transparent,
                                    0.46f to AuroraColors.BackgroundBase.copy(alpha = 0.08f),
                                    0.66f to AuroraColors.BackgroundBase.copy(alpha = 0.70f),
                                    0.82f to AuroraColors.BackgroundBase,
                                    1.00f to AuroraColors.BackgroundBase,
                                )
                            )
                        )
                        // Title card — the artwork is a plain poster/backdrop
                        // with no baked title, so render one styled to read like
                        // a logo (centred, tracked-out, extra-bold, soft shadow).
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .padding(horizontal = Spacing.lg)
                                .padding(bottom = Spacing.lg),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                s.title.uppercase(),
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 1.8.sp,
                                    lineHeight = 26.sp,
                                    shadow = Shadow(Color.Black.copy(alpha = 0.55f), Offset(0f, 3f), 14f),
                                ),
                                color = Color.White,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Star, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(12.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    s.label.uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AuroraColors.TextSecondary,
                                    letterSpacing = 1.5.sp,
                                )
                            }
                        }
                    }
                }
            }
            Text(
                "AuroraPlay",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(horizontal = Spacing.gutter, vertical = Spacing.sm),
            )
        }

        // ---- Profile picker panel: wraps its content, so the hero above
        //      soaks up all the remaining height instead of a dead gap ----
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.gutter)
                // Clear the system navigation bar: with 3-button navigation its
                // inset is tall enough to sit over the "Gerenciar perfis"
                // button, which had only a flat 16dp bottom margin. Contributes
                // ~0 on gesture navigation.
                .navigationBarsPadding()
                .padding(top = Spacing.lg, bottom = Spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                if (manageMode) "Toque para editar" else "Escolha o seu perfil",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = AuroraColors.TextPrimary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(Spacing.lg))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.md, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
                maxItemsInEachRow = 3,
                modifier = Modifier.fillMaxWidth(),
            ) {
                state.profiles.forEach { profile ->
                    ProfileTile(
                        profile = profile,
                        manageMode = manageMode,
                        modifier = Modifier.width(96.dp),
                        onClick = {
                            if (!manageMode) attemptSelect(profile)
                            else if (viewModel.isProtected(profile)) pendingManage = profile to false
                            else onEditProfile(profile.id)
                        },
                        onLongClick = { manageMode = true },
                        onDelete = {
                            if (viewModel.isProtected(profile)) pendingManage = profile to true
                            else pendingDelete = profile
                        },
                    )
                }
                if (manageMode) {
                    ActionTile(
                        icon = Icons.Default.Add,
                        label = "Adicionar",
                        modifier = Modifier.width(96.dp),
                        onClick = onAddProfile,
                    )
                }
            }

            Spacer(Modifier.height(Spacing.sm))
            if (state.profiles.isEmpty() && !state.isLoading) {
                TextButton(onClick = onOpenBackup) {
                    Text("Restaurar backup de arquivo", color = MaterialTheme.colorScheme.primary)
                }
            }
            TextButton(onClick = { manageMode = !manageMode }) {
                Icon(
                    if (manageMode) Icons.Default.Check else Icons.Default.Edit,
                    contentDescription = null,
                    tint = AuroraColors.TextSecondary,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(Spacing.sm))
                Text(
                    text = if (manageMode) "Concluir" else "Gerenciar perfis",
                    style = MaterialTheme.typography.labelLarge,
                    color = AuroraColors.TextSecondary,
                )
            }
        }
    }

    pendingDelete?.let { profile ->
        DeleteProfileDialog(
            profileName = profile.name,
            onCancel = { pendingDelete = null },
            onConfirm = {
                viewModel.deleteProfile(profile.id)
                pendingDelete = null
            },
        )
    }

    pendingUnlock?.let { profile ->
        PinUnlockDialog(
            profileName = profile.name,
            expectedHash = profile.pinHash!!,
            onCancel = { pendingUnlock = null },
            onUnlocked = {
                pendingUnlock = null
                viewModel.selectProfile(profile.id)
                onProfileSelected()
            },
        )
    }

    pendingManage?.let { (profile, isDelete) ->
        ProfileManageChallenge(
            profile = profile,
            onDismiss = { pendingManage = null },
            onAuthorized = {
                viewModel.authorizeManagement(profile.id)
                pendingManage = null
                if (isDelete) pendingDelete = profile else onEditProfile(profile.id)
            },
        )
    }
}

@Composable
private fun PinUnlockDialog(
    profileName: String,
    expectedHash: String,
    onCancel: () -> Unit,
    onUnlocked: () -> Unit,
) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var attempts by remember { mutableStateOf(0) }
    var cooldownUntil by remember { mutableStateOf(0L) }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(cooldownUntil) {
        while (System.currentTimeMillis() < cooldownUntil) { now = System.currentTimeMillis(); delay(500) }
        now = System.currentTimeMillis()
    }
    val lockedOut = now < cooldownUntil

    AlertDialog(
        onDismissRequest = onCancel,
        containerColor = AuroraColors.BackgroundElevated,
        title = { Text("PIN de $profileName", style = MaterialTheme.typography.titleLarge, color = AuroraColors.TextPrimary) },
        text = {
            Column {
                Text(
                    "Este perfil está protegido. Digite o PIN de 4 dígitos para continuar.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AuroraColors.TextSecondary,
                )
                Spacer(Modifier.height(Spacing.md))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(AuroraColors.SurfaceDark)
                        .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                ) {
                    androidx.compose.foundation.text.BasicTextField(
                        value = pin,
                        onValueChange = { v ->
                            if (!lockedOut && v.length <= 4 && v.all { it.isDigit() }) {
                                pin = v
                                error = null
                            }
                        },
                        singleLine = true,
                        enabled = !lockedOut,
                        textStyle = androidx.compose.ui.text.TextStyle(
                            color = AuroraColors.TextPrimary,
                            fontSize = 18.sp,
                            letterSpacing = 6.sp,
                        ),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(mask = '•'),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                error?.let {
                    Spacer(Modifier.height(Spacing.sm))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = AuroraColors.Error)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = !lockedOut, onClick = {
                if (com.auroraplay.iptv.core.util.PinHasher.matches(pin, expectedHash)) {
                    onUnlocked()
                } else {
                    pin = ""
                    attempts++
                    if (attempts >= 5) {
                        cooldownUntil = System.currentTimeMillis() + 30_000L
                        error = "Muitas tentativas. Aguarde 30s."
                        attempts = 0
                    } else {
                        error = "PIN incorreto."
                    }
                }
            }) {
                Text("Entrar", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text("Cancelar", color = AuroraColors.TextSecondary) }
        },
    )
}

/** Netflix-style rounded-square tile: colored avatar, name below, with the
 * lock / kids / delete badges the picker needs. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProfileTile(
    profile: Profile,
    manageMode: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        label = "profileTileScale",
    )
    val tileColor = runCatching { Color(android.graphics.Color.parseColor(profile.avatarColorHex)) }
        .getOrDefault(AuroraColors.AccentDefault)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.scale(scale),
    ) {
        Box(Modifier.size(96.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(22.dp))
                    .background(tileColor)
                    .combinedClickable(
                        interactionSource = interaction,
                        indication = null,
                        onClick = onClick,
                        onLongClick = onLongClick,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (!profile.avatarUri.isNullOrBlank()) {
                    AsyncImage(
                        model = profile.avatarUri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Text(profile.avatarEmoji, fontSize = 40.sp)
                }
            }
            if (profile.isKids) {
                Text(
                    "Infantil",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 6.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.5f))
                        .padding(horizontal = 6.dp, vertical = 1.dp),
                )
            }
            if (profile.isLocked) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Default.Lock, "Perfil bloqueado com PIN", tint = Color.White, modifier = Modifier.size(12.dp)) }
            }
            if (manageMode) {
                Box(
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(AuroraColors.Error)
                        .clickable { onDelete() },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Default.Delete, "Excluir ${profile.name}", tint = Color.White, modifier = Modifier.size(13.dp)) }
            }
        }
        Spacer(Modifier.height(Spacing.sm))
        Text(
            profile.name,
            style = MaterialTheme.typography.bodyMedium,
            color = AuroraColors.TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

/** Same footprint as [ProfileTile] for the "Adicionar" slot. */
@Composable
private fun ActionTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(AuroraColors.SurfaceHigh)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = AuroraColors.TextSecondary, modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.height(Spacing.sm))
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = AuroraColors.TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun DeleteProfileDialog(
    profileName: String,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        containerColor = AuroraColors.BackgroundElevated,
        title = {
            Text("Excluir perfil", style = MaterialTheme.typography.titleLarge, color = AuroraColors.TextPrimary)
        },
        text = {
            Text(
                "O perfil \"$profileName\" será removido, junto com seus favoritos e seu histórico. Isso não pode ser desfeito.",
                style = MaterialTheme.typography.bodyMedium,
                color = AuroraColors.TextSecondary,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Excluir", color = AuroraColors.Error, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text("Cancelar", color = AuroraColors.TextSecondary)
            }
        },
    )
}
