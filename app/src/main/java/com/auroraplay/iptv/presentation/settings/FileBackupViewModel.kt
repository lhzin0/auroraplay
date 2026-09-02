package com.auroraplay.iptv.presentation.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.auroraplay.iptv.data.backup.UserDataBackup
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.IOException
import javax.inject.Inject

@HiltViewModel
class FileBackupViewModel @Inject constructor(private val backup: UserDataBackup) : ViewModel() {
    private val working = MutableStateFlow(false)
    val busy = working.asStateFlow()
    private val feedback = MutableStateFlow<String?>(null)
    val message = feedback.asStateFlow()

    fun save(uri: Uri) = runAction("Backup salvo no arquivo escolhido.", saving = true) {
        backup.saveToDocument(uri)
    }

    fun restore(uri: Uri) = runAction(
        "Backup restaurado com as senhas disponíveis no arquivo. Atualize o catálogo em Minhas conexões. Backups antigos podem exigir que você informe as senhas.",
        saving = false,
    ) { backup.restoreFromDocument(uri) }

    fun pickerUnavailable() {
        feedback.value = "Este aparelho não disponibilizou um seletor de arquivos. Verifique se há um gerenciador de arquivos compatível instalado."
    }

    fun clearMessage() { feedback.value = null }

    private fun runAction(success: String, saving: Boolean, action: suspend () -> Unit) {
        if (working.value) return
        working.value = true
        feedback.value = null
        viewModelScope.launch {
            try {
                action()
                feedback.value = success
            } catch (e: CancellationException) {
                throw e
            } catch (e: SecurityException) {
                feedback.value = "Sem permissão para acessar esse arquivo. Escolha novamente uma pasta ou arquivo pelo seletor do Android."
            } catch (e: IllegalArgumentException) {
                feedback.value = if (saving) "Não foi possível gerar o backup dos dados atuais."
                else "Arquivo inválido, incompatível ou maior que 20 MB. Nenhum dado foi importado."
            } catch (e: IOException) {
                feedback.value = if (saving) "Não foi possível concluir a gravação. Confira o espaço disponível e se a pasta ou o armazenamento externo está acessível; depois salve um novo arquivo."
                else "Não foi possível concluir a restauração. Confira o acesso ao arquivo e o espaço disponível; depois tente novamente. Dados já importados serão preservados."
            } catch (_: Exception) {
                feedback.value = "Não foi possível concluir a operação. Tente novamente."
            } finally {
                working.value = false
            }
        }
    }
}
