# Backup em arquivo

A partir da versão 1.31.0, o backup manual inclui as credenciais completas das
playlists em um arquivo JSON. O seletor do Android permite escolher uma pasta
local ou um serviço de nuvem instalado, como o Google Drive. O AuroraPlay não
usa a API do Drive nem exige projeto Cloud ou configuração OAuth.

## Salvar

1. Abra **Ajustes → Backup em arquivo → Salvar backup em arquivo**.
2. No seletor do Android, escolha uma pasta e confirme **Salvar**.
3. Aguarde a mensagem **Backup salvo no arquivo escolhido**.

O nome sugerido contém a data e a hora. O seletor cria um arquivo novo; nomes
repetidos recebem um sufixo, conforme o provedor. É possível escolher armazenamento
do aparelho, cartão SD ou USB quando o sistema e o dispositivo disponibilizam
esse local. Provedores de nuvem também são permitidos; não há o filtro
`EXTRA_LOCAL_ONLY`. Não pede permissão de acesso a todos os arquivos. Em aparelhos sem seletor
compatível, a interface explica a limitação.

### Google Drive nessa janela

1. Instale ou atualize o Google Drive, abra-o e conecte a conta desejada.
2. Volte ao AuroraPlay, toque em **Salvar backup em arquivo** e abra o menu
   lateral do seletor.
3. Escolha a conta do **Drive**, a pasta e confirme **Salvar**.

O Drive aparece quando o aplicativo instalado fornece essa integração ao seletor
do Android. O AuroraPlay não adiciona entradas próprias à janela do sistema.
A transferência e a disponibilidade offline ficam a cargo do provedor escolhido;
confira a sincronização no Drive antes de depender da cópia em outro aparelho.
O arquivo fica na pasta escolhida, não na antiga área privada `appDataFolder`.

## Restaurar

1. Abra **Restaurar de arquivo** e selecione o JSON.
2. Confirme a restauração. Cancelar não altera os dados.
3. As conexões importadas recebem link, login e a senha disponível no arquivo.
   Abra **Minhas conexões → Atualizar** para sincronizar o catálogo.

A restauração também está disponível na tela inicial quando não há perfis.
Ela combina registros, mantém perfis/conexões existentes, une favoritos e
preserva o histórico mais recente. Os ajustes do arquivo são aplicados.
O conteúdo inteiro é validado antes da importação, com limite de 20 MiB.
Novos arquivos usam o formato v2, com senhas associadas ao identificador da
conexão. Arquivos v1 continuam legíveis, mas não contêm senhas: nesse caso,
use **Minhas conexões → Senha** antes de atualizar. A API antiga do Drive não é
usada para recuperar backups da pasta privada.

As senhas importadas vão para o armazenamento cifrado de credenciais do app,
sem entrar no banco de catálogo. Senhas existentes são preservadas. Uma senha
do arquivo só é aplicada se o ID, endereço do servidor e login correspondem à
conexão local. A importação pode preencher a senha ausente de uma conexão
restaurada anteriormente; os três armazenamentos (banco, credenciais e ajustes)
não compartilham uma transação, e uma interrupção pode ser resolvida repetindo
a importação.

## O que entra no arquivo

- Perfis, incluindo nome, aparência e PIN em hash, quando definido.
- Conexões: nome, endereço/link do servidor, login e **senha salva no app**.
- Favoritos, histórico/posição de reprodução e ajustes.

**Downloads não entram no backup:** nenhum vídeo baixado, fila, estado ou banco
de downloads é exportado ou restaurado. Preferências, como baixar apenas no Wi-Fi,
podem ser mantidas como ajustes; isso não recria nem inicia downloads.
Catálogo, cache, fotos locais de perfil, biometria e chaves de API também são
excluídos. O backup não copia diretórios nem bancos inteiros.

O JSON não possui criptografia adicional. Guarde-o em local seguro, pois contém
dados pessoais, logins e senhas das conexões em texto legível. Não compartilhe
o arquivo nem a pasta de destino. Arquivos salvos fora da área do
app permanecem na pasta escolhida após desinstalação, sujeitos ao armazenamento.

## Substituição do método anterior

- Removidos autorização OAuth, cliente da API Drive, agendador e telas de conta Google.
- Ao atualizar, trabalhos antigos do Drive são cancelados e a seleção local de
  conta é descartada. Backups já existentes não são apagados.
- Desativado o Auto Backup do Android; todos os domínios são excluídos das regras
  de backup na nuvem e transferência entre aparelhos, incluindo armazenamento externo.
- O usuário escolhe quando e onde salvar. Não há novos envios automáticos.

## Validação

`testDebugUnitTest` verifica formatos v1/v2, senhas, exclusão de downloads,
rejeição de arquivos inválidos e limite de leitura. `connectedDebugAndroidTest`
verifica exportação e restauração das credenciais, preservação de senhas locais,
recuperação de arquivos antigos e contratos do seletor com nuvem habilitada.
`assembleDebug` e `assembleRelease` geram as variantes instaláveis.

Referência: [Storage Access Framework do Android](https://developer.android.com/training/data-storage/shared/documents-files).
