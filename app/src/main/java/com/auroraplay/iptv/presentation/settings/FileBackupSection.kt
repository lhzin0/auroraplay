package com.auroraplay.iptv.presentation.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.auroraplay.iptv.core.theme.AuroraColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** SAF grants access to just the document selected by the user, including removable volumes. */
internal class CreateBackupDocument : ActivityResultContracts.CreateDocument("application/json") {
    override fun createIntent(context: Context, input: String): Intent =
        super.createIntent(context, input)
            .setType(if (input.endsWith(".json")) "application/json" else "application/octet-stream")
            .addCategory(Intent.CATEGORY_OPENABLE)
}

internal class OpenBackupDocument : ActivityResultContracts.OpenDocument() {
    override fun createIntent(context: Context, input: Array<String>): Intent =
        super.createIntent(context, input)
            .addCategory(Intent.CATEGORY_OPENABLE)
}

@Composable
fun FileBackupSection(viewModel: FileBackupViewModel = hiltViewModel()) {
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val lockedBackup by viewModel.lockedBackup.collectAsStateWithLifecycle()
    var pendingRestore by rememberSaveable { mutableStateOf<String?>(null) }
    var exportDialog by remember { mutableStateOf(false) }
    val saveDocument = rememberLauncherForActivityResult(CreateBackupDocument()) { uri ->
        viewModel.save(uri)
    }
    val openDocument = rememberLauncherForActivityResult(OpenBackupDocument()) { uri ->
        pendingRestore = uri?.toString()
    }

    SettingsSection(title = "Backup em arquivo") {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "Salve uma cópia dos seus dados na pasta que você escolher e restaure quando precisar.",
                color = AuroraColors.TextPrimary, style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "Inclui perfis, playlists com link, login e senha, favoritos, histórico e ajustes. Downloads e vídeos baixados não entram no backup; catálogo e fotos de perfil locais também ficam de fora.",
                color = AuroraColors.TextSecondary, style = MaterialTheme.typography.bodySmall,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = !busy && pendingRestore == null, onClick = {
                    viewModel.clearMessage()
                    exportDialog = true
                }) { Text("Salvar backup em arquivo") }
                OutlinedButton(enabled = !busy && pendingRestore == null, onClick = {
                    viewModel.clearMessage()
                    try {
                        // Some file managers label JSON as text/plain or application/octet-stream.
                        // Accept the selection, then validate its contents before importing.
                        openDocument.launch(arrayOf("*/*"))
                    } catch (_: Exception) { viewModel.pickerUnavailable() }
                }) { Text("Restaurar de arquivo") }
            }
            Text(
                "Escolha uma pasta do aparelho, cartão SD, pendrive ou Google Drive no seletor do Android. Para o Drive aparecer, instale e abra o app Google Drive com sua conta conectada. Os locais disponíveis dependem do aparelho.",
                color = AuroraColors.TextSecondary, style = MaterialTheme.typography.bodySmall,
            )
            Text(
                "Você pode proteger o arquivo com uma senha. Ela será necessária apenas ao restaurar o backup; as playlists continuam conectando automaticamente.",
                color = AuroraColors.TextSecondary, style = MaterialTheme.typography.bodySmall,
            )
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            message?.let { Text(it, color = AuroraColors.TextPrimary, style = MaterialTheme.typography.bodyMedium) }
        }
    }

    if (exportDialog) {
        BackupPasswordDialog(
            exporting = true, busy = false, message = null,
            onDismiss = { exportDialog = false },
            onConfirm = { password ->
                exportDialog = false
                viewModel.prepareExport(password)
                val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.ROOT).format(Date())
                val extension = if (password == null) "json" else "aurorabackup"
                try { saveDocument.launch("AuroraPlay-backup-$timestamp.$extension") }
                catch (_: Exception) { viewModel.cancelExport(); viewModel.pickerUnavailable() }
            },
        )
    }

    lockedBackup?.let { uri ->
        BackupPasswordDialog(
            exporting = false, busy = busy, message = message,
            onDismiss = viewModel::dismissLockedBackup,
            onConfirm = { password -> viewModel.restore(uri, password) },
        )
    }

    pendingRestore?.let { selectedUri ->
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            containerColor = AuroraColors.SurfaceDark,
            title = { Text("Restaurar este backup?") },
            text = {
                Text("Os dados serão combinados com os deste aparelho. Perfis e conexões serão mantidos, e o histórico mais recente será preservado. Os ajustes do arquivo serão aplicados.\n\nAs senhas do backup serão aplicadas às playlists correspondentes, substituindo as senhas atuais dessas conexões. A sincronização do catálogo será iniciada automaticamente. Downloads não serão importados.")
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingRestore = null
                    viewModel.restore(Uri.parse(selectedUri))
                }) { Text("Restaurar") }
            },
            dismissButton = { TextButton(onClick = { pendingRestore = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun BackupPasswordDialog(
    exporting: Boolean,
    busy: Boolean,
    message: String?,
    onDismiss: () -> Unit,
    onConfirm: (CharArray?) -> Unit,
) {
    // Passwords stay in memory only, never in saved instance state or preferences.
    var protect by remember { mutableStateOf(true) }
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    val valid = if (exporting && !protect) true
        else if (exporting) password.length in 8..1024 && password == confirmation
        else password.isNotEmpty() && password.length <= 1024
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        containerColor = AuroraColors.SurfaceDark,
        title = { Text(if (exporting) "Proteger backup" else "Senha do backup") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (exporting) {
                    Text("Proteger arquivo com senha")
                    Switch(checked = protect, enabled = !busy, onCheckedChange = { protect = it })
                }
                if (protect) {
                    Text(if (exporting) "Crie uma senha com pelo menos 8 caracteres. Guarde-a: ela não fica salva no app e não pode ser recuperada."
                        else "Use a senha escolhida ao salvar este arquivo, não a senha da playlist.")
                    OutlinedTextField(
                        value = password, onValueChange = { if (it.length <= 1024) password = it },
                        enabled = !busy, singleLine = true, label = { Text("Senha do arquivo") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    )
                    if (exporting) OutlinedTextField(
                        value = confirmation, onValueChange = { if (it.length <= 1024) confirmation = it },
                        enabled = !busy, singleLine = true, label = { Text("Confirmar senha") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    )
                } else Text("O arquivo JSON terá link, login e senha das playlists em texto legível. Guarde-o em uma pasta privada.")
                message?.let { Text(it) }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(enabled = !busy && valid, onClick = {
                val secret = if (protect) password.toCharArray() else null
                password = ""
                confirmation = ""
                onConfirm(secret)
            }) { Text(if (exporting) "Escolher onde salvar" else "Restaurar") }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Cancelar") } },
    )
}
