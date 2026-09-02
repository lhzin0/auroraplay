package com.auroraplay.iptv.presentation.connections

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.auroraplay.iptv.core.theme.AuroraColors
import com.auroraplay.iptv.core.util.toRelativeTimeLabel
import com.auroraplay.iptv.domain.model.ConnectionStatus
import com.auroraplay.iptv.domain.model.XtreamConnection
import com.auroraplay.iptv.presentation.components.EmptyState
import kotlinx.coroutines.launch

@Composable
fun ConnectionsScreen(
    onBack: () -> Unit,
    onAddConnection: () -> Unit,
    viewModel: ConnectionsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var showExportWarning by remember { mutableStateOf(false) }
    var pendingExportJson by remember { mutableStateOf<String?>(null) }
    var passwordConnection by remember { mutableStateOf<XtreamConnection?>(null) }
    var restoredPassword by remember { mutableStateOf("") }

    val createDocumentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val json = pendingExportJson
        pendingExportJson = null
        if (uri == null || json == null) return@rememberLauncherForActivityResult
        val wrote = runCatching {
            context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
        }.isSuccess
        scope.launch {
            snackbarHostState.showSnackbar(if (wrote) "Backup exportado." else "Não foi possível salvar o arquivo.")
        }
    }

    val openDocumentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val json = runCatching {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        }.getOrNull()
        if (json == null) {
            scope.launch { snackbarHostState.showSnackbar("Não foi possível ler o arquivo.") }
            return@rememberLauncherForActivityResult
        }
        viewModel.importConnections(json) { result ->
            scope.launch {
                snackbarHostState.showSnackbar(
                    when {
                        result.imported > 0 && result.failed == 0 -> "Importada(s) ${result.imported} conexão(ões)."
                        result.imported > 0 && result.failed > 0 -> "Importada(s) ${result.imported}; ${result.failed} falharam (credenciais recusadas pelo servidor)."
                        else -> "Nenhuma conexão pôde ser importada — verifique o arquivo."
                    }
                )
            }
        }
    }

    Scaffold(
        containerColor = AuroraColors.BackgroundBase,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp).heightIn(min = 56.dp),
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(36.dp)
                        .background(AuroraColors.SurfaceHigh, CircleShape),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Voltar",
                        tint = AuroraColors.TextPrimary,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    "Minhas conexões",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    color = AuroraColors.TextPrimary,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                // Import / export share the same muted treatment; "add" is the
                // primary action and keeps the accent tint.
                IconButton(onClick = { openDocumentLauncher.launch(arrayOf("application/json")) }) {
                    Icon(Icons.Default.FileDownload, contentDescription = "Importar backup", tint = AuroraColors.TextSecondary, modifier = Modifier.size(22.dp))
                }
                IconButton(onClick = { showExportWarning = true }) {
                    Icon(Icons.Default.FileUpload, contentDescription = "Exportar backup", tint = AuroraColors.TextSecondary, modifier = Modifier.size(22.dp))
                }
                IconButton(onClick = onAddConnection) {
                    Icon(Icons.Default.Add, contentDescription = "Adicionar conexão", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    ) { padding ->
        if (state.connections.isEmpty() && !state.isLoading) {
            Box(Modifier.padding(padding).fillMaxSize()) {
                EmptyState(message = "Nenhuma conexão configurada ainda.")
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                modifier = Modifier.padding(padding),
            ) {
                items(state.connections, key = { it.id }) { connection ->
                    ConnectionRow(
                        connection = connection,
                        onSetDefault = { viewModel.setDefault(connection.id) },
                        onDelete = { viewModel.delete(connection.id) },
                        onEnterPassword = { passwordConnection = connection; restoredPassword = "" },
                        onTest = {
                            viewModel.testConnection(connection.id) { success, error ->
                                scope.launch {
                                    snackbarHostState.showSnackbar(
                                        if (success) "Conexão testada com sucesso." else (error ?: "Falha ao testar conexão.")
                                    )
                                }
                            }
                        },
                        onSync = {
                            viewModel.syncNow(
                                connection.id,
                                onStage = { },
                                onError = { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } },
                            )
                            scope.launch { snackbarHostState.showSnackbar("Sincronização iniciada.") }
                        },
                    )
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
    }

    passwordConnection?.let { connection ->
        AlertDialog(
            onDismissRequest = { passwordConnection = null; restoredPassword = "" },
            title = { Text("Senha de ${connection.name}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("A senha fica protegida neste aparelho e não é enviada ao backup automático.")
                    OutlinedTextField(
                        value = restoredPassword,
                        onValueChange = { restoredPassword = it },
                        label = { Text("Senha Xtream") },
                        singleLine = true,
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Password),
                    )
                }
            },
            confirmButton = {
                TextButton(enabled = restoredPassword.isNotBlank(), onClick = {
                    viewModel.savePassword(connection, restoredPassword) { message ->
                        scope.launch { snackbarHostState.showSnackbar(message) }
                    }
                    passwordConnection = null
                    restoredPassword = ""
                }) { Text("Salvar senha") }
            },
            dismissButton = { TextButton(onClick = { passwordConnection = null; restoredPassword = "" }) { Text("Cancelar") } },
        )
    }

    if (showExportWarning) {
        AlertDialog(
            onDismissRequest = { showExportWarning = false },
            containerColor = AuroraColors.BackgroundElevated,
            title = { Text("Exportar conexões", style = MaterialTheme.typography.titleLarge, color = AuroraColors.TextPrimary) },
            text = {
                Text(
                    "O arquivo gerado guarda usuário e senha de cada conexão em texto simples, para poder restaurá-las depois. Guarde-o em um lugar seguro e não o compartilhe.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AuroraColors.TextSecondary,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showExportWarning = false
                    viewModel.exportConnections { json ->
                        if (json != null) {
                            pendingExportJson = json
                            createDocumentLauncher.launch("auroraplay-conexoes.json")
                        } else {
                            scope.launch { snackbarHostState.showSnackbar("Nenhuma conexão para exportar.") }
                        }
                    }
                }) {
                    Text("Exportar", fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showExportWarning = false }) {
                    Text("Cancelar", color = AuroraColors.TextSecondary)
                }
            },
        )
    }
}

@Composable
private fun ConnectionRow(
    connection: XtreamConnection,
    onSetDefault: () -> Unit,
    onDelete: () -> Unit,
    onTest: () -> Unit,
    onSync: () -> Unit,
    onEnterPassword: () -> Unit,
) {
    var showActions by remember { mutableStateOf(false) }
    val statusColor = when (connection.status) {
        ConnectionStatus.ONLINE -> AuroraColors.Success
        ConnectionStatus.OFFLINE -> AuroraColors.Error
        else -> AuroraColors.TextTertiary
    }
    val shortUrl = connection.serverUrl.removePrefix("https://").removePrefix("http://").take(30)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(AuroraColors.SurfaceDark)
            .clickable { showActions = !showActions }
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(statusColor))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(connection.name, style = MaterialTheme.typography.titleMedium, color = AuroraColors.TextPrimary)
                    if (connection.isDefault) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "PADRÃO",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Text(shortUrl, style = MaterialTheme.typography.bodySmall, color = AuroraColors.TextTertiary)
                val syncLabel = connection.lastSyncMillis?.toRelativeTimeLabel() ?: "nunca sincronizado"
                Text("Última atualização: $syncLabel", style = MaterialTheme.typography.bodySmall, color = AuroraColors.TextSecondary)
            }
            Icon(
                if (showActions) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = AuroraColors.TextTertiary,
            )
        }

        if (showActions) {
            Spacer(Modifier.height(12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionChip("Testar", Icons.Default.NetworkCheck, onTest)
                ActionChip("Senha", Icons.Default.Key, onEnterPassword)
                ActionChip("Atualizar", Icons.Default.Sync, onSync)
                if (!connection.isDefault) ActionChip("Padrão", Icons.Default.Star, onSetDefault)
                ActionChip("Excluir", Icons.Default.Delete, onDelete, danger = true)
            }
        }
    }
}

@Composable
private fun ActionChip(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit, danger: Boolean = false) {
    val color = if (danger) AuroraColors.Error else AuroraColors.TextSecondary
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(100.dp))
            .background(AuroraColors.SurfaceHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Icon(icon, contentDescription = text, tint = color, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = color)
    }
}
