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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
            .addCategory(Intent.CATEGORY_OPENABLE)
}

internal class OpenBackupDocument : ActivityResultContracts.OpenDocument() {
    override fun createIntent(context: Context, input: Array<String>): Intent =
        super.createIntent(context, input)
            .addCategory(Intent.CATEGORY_OPENABLE)
}

@Composable
fun FileBackupSection(viewModel: FileBackupViewModel = hiltViewModel()) {
    val busy by viewModel.busy.collectAsState()
    val message by viewModel.message.collectAsState()
    var pendingRestore by rememberSaveable { mutableStateOf<String?>(null) }
    val saveDocument = rememberLauncherForActivityResult(CreateBackupDocument()) { uri ->
        uri?.let(viewModel::save)
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
                    val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.ROOT).format(Date())
                    try {
                        saveDocument.launch("AuroraPlay-backup-$timestamp.json")
                    } catch (_: Exception) { viewModel.pickerUnavailable() }
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
                "O arquivo .json contém as senhas das playlists em texto legível. Guarde-o em uma pasta privada e não compartilhe o backup.",
                color = AuroraColors.TextSecondary, style = MaterialTheme.typography.bodySmall,
            )
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            message?.let { Text(it, color = AuroraColors.TextPrimary, style = MaterialTheme.typography.bodyMedium) }
        }
    }

    pendingRestore?.let { selectedUri ->
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            containerColor = AuroraColors.SurfaceDark,
            title = { Text("Restaurar este backup?") },
            text = {
                Text("Os dados serão combinados com os deste aparelho. Perfis, conexões e senhas já configuradas serão mantidos, e o histórico mais recente será preservado. Os ajustes do arquivo serão aplicados.\n\nAs senhas presentes no backup serão recuperadas para as conexões que ainda não têm senha. Depois, atualize o catálogo. Downloads não serão importados.")
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
