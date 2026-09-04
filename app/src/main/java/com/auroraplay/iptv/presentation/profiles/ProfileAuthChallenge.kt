package com.auroraplay.iptv.presentation.profiles

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.auroraplay.iptv.core.theme.AuroraColors
import com.auroraplay.iptv.core.util.PinHasher
import com.auroraplay.iptv.domain.model.Profile
import kotlinx.coroutines.delay

private const val MAX_ATTEMPTS = 5
private const val COOLDOWN_MS = 30_000L
private val DEVICE_AUTHENTICATORS =
    BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL

fun deviceAuthAvailable(activity: FragmentActivity?): Boolean =
    activity != null &&
        BiometricManager.from(activity).canAuthenticate(DEVICE_AUTHENTICATORS) ==
        BiometricManager.BIOMETRIC_SUCCESS

/** Fingerprint / face / screen-lock confirmation — the parental speed bump for
 * a kids profile with no PIN, and the biometric shortcut for a PIN-locked one. */
fun runDeviceAuth(
    activity: FragmentActivity,
    title: String,
    subtitle: String,
    onSuccess: () -> Unit,
    onError: () -> Unit = {},
) {
    val info = BiometricPrompt.PromptInfo.Builder()
        .setTitle(title)
        .setSubtitle(subtitle)
        .setAllowedAuthenticators(DEVICE_AUTHENTICATORS)
        .build()
    BiometricPrompt(
        activity,
        ContextCompat.getMainExecutor(activity),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onError()
        },
    ).authenticate(info)
}

/**
 * Blocking challenge to manage (edit / delete / unprotect) [profile]:
 * its own PIN when it has one — with a biometric shortcut and attempt
 * limiting — otherwise a device-credential prompt, and a plain confirmation
 * only when the device has no secure lock at all.
 */
@Composable
fun ProfileManageChallenge(
    profile: Profile,
    onAuthorized: () -> Unit,
    onDismiss: () -> Unit,
) {
    val activity = LocalContext.current as? FragmentActivity

    if (!profile.isLocked) {
        // Kids-only profile: device credential, or a confirmation if the phone
        // has no lock screen (can't force one on the user without locking them
        // out of their own profile management).
        var showConfirm by remember { mutableStateOf(false) }
        LaunchedEffect(profile.id) {
            if (deviceAuthAvailable(activity)) {
                runDeviceAuth(
                    activity!!,
                    title = "Confirme sua identidade",
                    subtitle = "Necessário para gerenciar \"${profile.name}\"",
                    onSuccess = onAuthorized,
                    onError = { showConfirm = true },
                )
            } else {
                showConfirm = true
            }
        }
        if (showConfirm) {
            AlertDialog(
                onDismissRequest = onDismiss,
                containerColor = AuroraColors.BackgroundElevated,
                title = { Text("Perfil infantil", color = AuroraColors.TextPrimary) },
                text = {
                    Text(
                        "\"${profile.name}\" tem restrição infantil. " +
                            (if (deviceAuthAvailable(activity)) "Confirme para gerenciá-lo." else "Este aparelho não tem bloqueio de tela; confirme que um responsável está gerenciando este perfil."),
                        color = AuroraColors.TextSecondary,
                    )
                },
                confirmButton = {
                    TextButton(onClick = onAuthorized) {
                        Text("Continuar", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                    }
                },
                dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar", color = AuroraColors.TextSecondary) } },
            )
        }
        return
    }

    // PIN-locked profile.
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var attempts by remember { mutableIntStateOf(0) }
    var cooldownUntil by remember { mutableStateOf(0L) }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(cooldownUntil) {
        while (System.currentTimeMillis() < cooldownUntil) {
            now = System.currentTimeMillis()
            delay(500)
        }
        now = System.currentTimeMillis()
    }
    val lockedOut = now < cooldownUntil

    fun submit() {
        if (lockedOut) return
        if (PinHasher.matches(pin, profile.pinHash!!)) {
            onAuthorized()
            return
        }
        pin = ""
        attempts++
        if (attempts >= MAX_ATTEMPTS) {
            cooldownUntil = System.currentTimeMillis() + COOLDOWN_MS
            error = "Muitas tentativas. Aguarde ${COOLDOWN_MS / 1000}s."
            attempts = 0
        } else {
            error = "PIN incorreto."
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AuroraColors.BackgroundElevated,
        title = { Text("PIN de ${profile.name}", style = MaterialTheme.typography.titleLarge, color = AuroraColors.TextPrimary) },
        text = {
            Column {
                Text(
                    "Digite o PIN de 4 dígitos para gerenciar este perfil.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AuroraColors.TextSecondary,
                )
                Spacer(Modifier.height(12.dp))
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(AuroraColors.SurfaceDark)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    BasicTextField(
                        value = pin,
                        onValueChange = { v -> if (!lockedOut && v.length <= 4 && v.all { it.isDigit() }) { pin = v; error = null } },
                        singleLine = true,
                        enabled = !lockedOut,
                        textStyle = TextStyle(color = AuroraColors.TextPrimary, fontSize = 18.sp, letterSpacing = 6.sp),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        visualTransformation = PasswordVisualTransformation(mask = '•'),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = AuroraColors.Error)
                }
                if (profile.biometricEnabled && deviceAuthAvailable(activity)) {
                    TextButton(onClick = {
                        runDeviceAuth(
                            activity!!,
                            title = "Desbloquear ${profile.name}",
                            subtitle = "Confirme sua identidade para gerenciar este perfil",
                            onSuccess = onAuthorized,
                        )
                    }) { Text("Usar biometria", color = MaterialTheme.colorScheme.primary) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = ::submit, enabled = !lockedOut) {
                Text("Entrar", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar", color = AuroraColors.TextSecondary) } },
    )
}
