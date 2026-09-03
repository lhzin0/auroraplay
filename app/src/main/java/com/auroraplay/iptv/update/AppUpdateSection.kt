package com.auroraplay.iptv.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.auroraplay.iptv.BuildConfig
import com.auroraplay.iptv.core.theme.AuroraColors
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
internal class AppUpdateViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val manager: AppUpdateManager,
) : ViewModel() {
    val updates = manager.updates
    val busy = MutableStateFlow(false)
    val message = MutableStateFlow<String?>(null)
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val work = updates.map { it.workId }.distinctUntilChanged().flatMapLatest { id ->
        val uuid = id?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        if (uuid == null) flowOf(null) else WorkManager.getInstance(context).getWorkInfoByIdFlow(uuid)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun check() = action { manager.check() }
    fun setAuto(value: Boolean) = action { manager.setAutoDownload(value); null }
    fun download() = action { updates.value.release?.let { manager.download(it, automatic = false) }; "Download solicitado." }
    fun cancel() = action { manager.cancel(); "Download cancelado." }
    suspend fun installIntent() = manager.installIntent()
    private fun action(block: suspend () -> String?) {
        if (busy.value) return
        busy.value = true
        message.value = null
        viewModelScope.launch {
            try { message.value = block() }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { message.value = "Não foi possível concluir. Confira sua conexão e tente novamente." }
            finally { busy.value = false }
        }
    }
}

@Composable
internal fun AppUpdateSection(viewModel: AppUpdateViewModel = hiltViewModel()) {
    val state by viewModel.updates.collectAsState()
    val work by viewModel.work.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val message by viewModel.message.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var installing by remember { mutableStateOf(false) }
    var installMessage by remember { mutableStateOf<String?>(null) }
    val installer = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }
    fun install() {
        scope.launch {
            installing = true
            installMessage = null
            try { installer.launch(viewModel.installIntent()) }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { installMessage = "Não foi possível abrir uma atualização válida. Baixe novamente ou use o site oficial." }
            finally { installing = false }
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (Build.VERSION.SDK_INT < 26 || context.packageManager.canRequestPackageInstalls()) install()
        else installMessage = "Para instalar, permita atualizações por este aplicativo nas configurações do Android."
    }
    val available = state.release?.takeIf { it.versionCode > BuildConfig.VERSION_CODE }
    val ready = state.ready?.takeIf { it.versionCode > BuildConfig.VERSION_CODE }
    val active = work?.state?.isFinished == false
    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Atualizações do app", color = AuroraColors.TextPrimary, style = MaterialTheme.typography.titleLarge)
        Text("Versão instalada: ${BuildConfig.VERSION_NAME}", color = AuroraColors.TextSecondary)
        Text("O AuroraPlay consulta novas versões no GitHub diariamente. A instalação é aberta quando você escolher instalar.",
            color = AuroraColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Baixar automaticamente no Wi-Fi", Modifier.weight(1f), color = AuroraColors.TextPrimary)
            Switch(checked = state.autoDownload, enabled = !busy && !BuildConfig.DEBUG, onCheckedChange = viewModel::setAuto)
        }
        available?.let { release ->
            Text("Nova versão: ${release.version} • ${"%.1f".format(release.sizeBytes / 1048576.0)} MB", color = AuroraColors.TextPrimary)
            release.notes.forEach { Text("• $it", color = AuroraColors.TextSecondary, style = MaterialTheme.typography.bodySmall) }
            if (release.minSdk > Build.VERSION.SDK_INT) Text("Esta versão exige um Android mais recente.", color = AuroraColors.TextSecondary)
        }
        if (active) {
            val percent = work?.progress?.getInt("percent", 0) ?: 0
            LinearProgressIndicator(progress = { percent / 100f }, modifier = Modifier.fillMaxWidth())
            Text(if (work?.state == WorkInfo.State.RUNNING) "Baixando e verificando: $percent%"
                else "Aguardando rede disponível. O download automático usa Wi-Fi.", color = AuroraColors.TextSecondary)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(enabled = !busy, onClick = viewModel::check) { Text(if (busy) "Consultando…" else "Verificar agora") }
            if (available != null && available.minSdk <= Build.VERSION.SDK_INT && ready?.versionCode != available.versionCode && !BuildConfig.DEBUG) {
                Button(enabled = !busy && work?.state != WorkInfo.State.RUNNING, onClick = viewModel::download) {
                    Text(if (active) "Baixar agora" else "Baixar atualização")
                }
            }
            if (active) TextButton(onClick = viewModel::cancel) { Text("Cancelar download") }
            if (ready != null && !BuildConfig.DEBUG) Button(enabled = !installing, onClick = {
                try {
                    if (Build.VERSION.SDK_INT >= 26 && !context.packageManager.canRequestPackageInstalls()) {
                        permission.launch(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")))
                    } else install()
                } catch (_: Exception) { installMessage = "Este aparelho não disponibilizou o instalador. Use o site oficial." }
            }) { Text(if (installing) "Verificando…" else "Instalar ${ready.version}") }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        (installMessage ?: message ?: work?.outputData?.getString("error") ?: state.error)?.let {
            Text(it, color = AuroraColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
        }
        if (BuildConfig.DEBUG) Text("Edição de desenvolvimento: downloads automáticos e instalação desativados.", color = AuroraColors.TextSecondary)
        TextButton(onClick = {
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(AppRelease.WEBSITE))) }
        }) { Text("Abrir página oficial") }
    }
}
