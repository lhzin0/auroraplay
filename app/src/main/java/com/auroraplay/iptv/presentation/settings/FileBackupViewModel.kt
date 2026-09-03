package com.auroraplay.iptv.presentation.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.auroraplay.iptv.data.backup.UserDataBackup
import com.auroraplay.iptv.data.backup.MissingBackupPasswordException
import com.auroraplay.iptv.data.backup.BackupPasswordRequiredException
import com.auroraplay.iptv.data.backup.BackupAuthenticationException
import com.auroraplay.iptv.sync.CatalogSyncScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.IOException
import javax.inject.Inject

@HiltViewModel
class FileBackupViewModel @Inject constructor(
    private val backup: UserDataBackup,
    private val syncScheduler: CatalogSyncScheduler,
) : ViewModel() {
    private val working = MutableStateFlow(false)
    val busy = working.asStateFlow()
    private val feedback = MutableStateFlow<String?>(null)
    val message = feedback.asStateFlow()
    private val locked = MutableStateFlow<Uri?>(null)
    val lockedBackup = locked.asStateFlow()
    private var exportPrepared = false
    private var exportPassword: CharArray? = null

    fun prepareExport(password: CharArray?) {
        cancelExport()
        exportPassword = password
        exportPrepared = true
    }

    fun cancelExport() {
        exportPassword?.fill('\u0000')
        exportPassword = null
        exportPrepared = false
    }

    fun save(uri: Uri?) {
        if (uri == null) { cancelExport(); return }
        // If Android killed the process while the picker was open, never silently
        // downgrade an encrypted export to plaintext. Ask the user to start again.
        if (!exportPrepared) {
            feedback.value = "Inicie novamente o backup para escolher a proteção do arquivo."
            return
        }
        val password = exportPassword
        exportPassword = null
        exportPrepared = false
        runAction(saving = true, cleanup = { password?.fill('\u0000') }) {
            backup.saveToDocument(uri, password)
            if (password == null) "Backup salvo com as credenciais completas, sem criptografia."
            else "Backup protegido salvo. Guarde a senha do arquivo para restaurá-lo em outro aparelho."
        }
    }

    fun dismissLockedBackup() { locked.value = null }

    fun restore(uri: Uri, password: CharArray? = null) = runAction(
        saving = false, cleanup = { password?.fill('\u0000') },
    ) {
        val result = try { backup.restoreFromDocument(uri, password) }
        catch (e: BackupPasswordRequiredException) { locked.value = uri; throw e }
        catch (e: BackupAuthenticationException) { locked.value = uri; throw e }
        locked.value = null
        var scheduled = true
        for (id in result.readyConnectionIds) {
            try { syncScheduler.enqueue(id) }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { scheduled = false }
        }
        buildString {
            append("Backup restaurado. As senhas do arquivo foram aplicadas automaticamente.")
            if (result.readyConnectionIds.isNotEmpty()) append(
                if (scheduled) " A sincronização do catálogo foi agendada; acompanhe em Minhas conexões ou nas notificações."
                else " Toque em Atualizar em Minhas conexões para sincronizar o catálogo."
            )
            if (result.missingPasswords > 0) append(" Este backup antigo não contém a senha de ${result.missingPasswords} conexão(ões). Importe um backup completo ou cadastre novamente essas conexões pelo botão +.")
        }
    }

    fun pickerUnavailable() {
        feedback.value = "Este aparelho não disponibilizou um seletor de arquivos. Verifique se há um gerenciador de arquivos compatível instalado."
    }

    fun clearMessage() { feedback.value = null }

    override fun onCleared() { cancelExport() }

    private fun runAction(saving: Boolean, cleanup: () -> Unit = {}, action: suspend () -> String) {
        if (working.value) { cleanup(); return }
        working.value = true
        feedback.value = null
        viewModelScope.launch {
            try {
                feedback.value = action()
            } catch (e: CancellationException) {
                throw e
            } catch (e: SecurityException) {
                feedback.value = "Sem permissão para acessar esse arquivo. Escolha novamente uma pasta ou arquivo pelo seletor do Android."
            } catch (_: MissingBackupPasswordException) {
                feedback.value = "Backup não salvo: uma conexão antiga está sem senha. Restaure um backup completo ou cadastre novamente essa conexão pelo botão + em Minhas conexões."
            } catch (_: BackupPasswordRequiredException) {
                feedback.value = "Informe a senha que protege este arquivo de backup."
            } catch (_: BackupAuthenticationException) {
                feedback.value = "Senha incorreta ou arquivo alterado. Nenhum dado foi importado."
            } catch (e: IllegalArgumentException) {
                feedback.value = if (saving) "Não foi possível gerar o backup dos dados atuais."
                else "Arquivo inválido, incompatível ou maior que 20 MB. Nenhum dado foi importado."
            } catch (e: IOException) {
                feedback.value = if (saving) "Não foi possível concluir a gravação. Confira o espaço disponível e se a pasta ou o armazenamento externo está acessível; depois salve um novo arquivo."
                else "Não foi possível concluir a restauração. Confira o acesso ao arquivo e o espaço disponível; depois tente novamente. Dados já importados serão preservados."
            } catch (_: Exception) {
                feedback.value = "Não foi possível concluir a operação. Tente novamente."
            } finally {
                cleanup()
                working.value = false
            }
        }
    }
}
