# Segurança e distribuição — 1.33.0

O aviso “A verificação do app é recomendada” é uma solicitação de análise do
Google Play Protect. A assinatura de produção não é um certificado de aprovação
do Google e não garante que a mensagem deixe de aparecer. A orientação na
instalação é escolher **Verificar app** e aguardar o resultado.

## Gerar uma versão assinada

Execute `./scripts/build-release.ps1` no PowerShell. O script usa JDK 21,
compila `assembleRelease`, assina o APK e verifica os certificados para APIs
24, 27, 28, 32, 33 e 36. O resultado fica em `build/release/`, acompanhado de
`SHA256SUMS.txt` e `release.json`. Só esses arquivos públicos devem ir para a
página de distribuição. O APK intermediário `app-release-unsigned.apk` não é
instalável; não distribua esse arquivo nem um APK antigo deixado em `app/build`.

`initialize-signing.ps1` prepara a identidade uma única vez. Ela fica em
`%LOCALAPPDATA%/AuroraPlay/signing`, fora do repositório, com acesso restrito à
conta Windows atual e SYSTEM. Inclui:

- `production.p12`: chave privada RSA 3072 e certificado de produção.
- `production.password.dpapi`: senha aleatória protegida pelo Windows DPAPI.
- `legacy-debug.keystore`: cópia da identidade usada nos APKs anteriores.
- `auroraplay.lineage`: prova de rotação assinada pelas duas identidades.
- `identity.json`: impressões digitais públicas usadas na validação.

Não regenere ou substitua esses arquivos. A perda das chaves pode impedir
atualizações futuras. A senha DPAPI só pode ser recuperada pela mesma conta
Windows com seu material de proteção; copiar apenas esse arquivo para outro
computador não basta. Para uma cópia recuperável em outro computador, use
`./scripts/export-signing-backup.ps1 -Destination <pasta-privada>`, que solicita
uma senha de recuperação localmente. Guarde essa senha separada da cópia.
Nenhuma chave privada, senha, arquivo de assinatura ou backup de usuário deve
ser publicado no GitHub.

## Atualização sem desinstalação

O identificador continua `com.auroraplay.iptv` e o código da versão passa de 88
para 89. O APK usa rotação do esquema v3 a partir do Android 9 (API 28).
Nesses aparelhos, o Android pode atualizar a instalação antiga para a chave
de produção conservando o UID, os arquivos e as chaves do Android Keystore.
Android 7 e 8 continuam verificando a assinatura antiga no bloco v2: é a
limitação de compatibilidade desses sistemas, que não suportam rotação v3.

Instale a versão nova por cima da anterior. Não é preciso desinstalar ou limpar
dados. Uma instalação com outra chave ou o pacote `.debug` é uma edição
diferente e não faz parte dessa migração. Exporte um backup dos dados antes de
uma migração manual. Downloads não são exportados e seriam perdidos ao remover
o armazenamento do aplicativo.

## Proteções desta versão

- Backup opcionalmente cifrado com AES-256-GCM, autenticação do cabeçalho e do
  conteúdo, salt de 16 bytes e nonce de 12 bytes novos a cada exportação.
- Chave do arquivo derivada da senha por PBKDF2-HMAC-SHA1 com 1.300.000
  iterações. Essa variante atende à compatibilidade com Android 7/8, onde a
  fábrica PBKDF2-HMAC-SHA256 não está disponível em todas as versões.
- Senha do arquivo apenas em memória durante a operação; não é guardada em
  preferências ou no estado salvo da tela. A restauração autentica e valida
  todo o arquivo antes de modificar dados. Senha errada/arquivo adulterado não
  importam dados. Limite de 20 MiB de conteúdo, mais 52 bytes de envelope.
- Backups JSON antigos continuam legíveis e a exportação sem criptografia é
  uma escolha explícita. Credenciais completas continuam incluídas; downloads
  continuam excluídos. A senha do arquivo não é a senha da playlist.
- Removida a permissão ampla WRITE_EXTERNAL_STORAGE, desnecessária para os
  diretórios privados do app nas versões Android suportadas e para o SAF.
- Nenhum log HTTP, inclusive em debug. Exceções do preview não imprimem URLs.
- Requisições da API de conexão sem cache HTTP em disco; cache antigo removido.
  Apenas o cliente separado de metadados TMDB mantém cache de respostas.
- Redirecionamentos HTTPS→HTTP do cliente da API de conexão são rejeitados.
  Certificados TLS continuam validados pelas autoridades de sistema.
- Credenciais de desenvolvimento saíram do Gradle rastreado; configurações
  locais/variáveis de ambiente alimentam apenas o build apropriado.

HTTP informado pelo usuário continua permitido para compatibilidade com
servidores IPTV existentes. O formulário explica a exposição e sugere HTTPS.
Isso não adiciona criptografia a um servidor que só oferece HTTP. URLs de mídia
e downloads locais ainda podem conter credenciais exigidas pelo provedor.
O hash SHA-256 da distribuição detecta divergências no arquivo; não prova que
um APK seja livre de vulnerabilidades. Esta revisão não substitui uma auditoria
completa ou a análise independente do Play Protect.

## Validação em 3 de setembro de 2026

- Build debug/release e lint obrigatório de release concluídos.
- 11 testes JVM e 14 testes instrumentados passaram, incluindo senha errada,
  adulteração, leitura de JSON antigo e restauração completa das credenciais.
- No emulador Android API 37, instalação do APK 1.32.0 e atualização direta para
  1.33.0 concluídas. Uma instrumentação isolada confirmou a preservação do UID,
  de dados sintéticos cifrados e da chave no Android Keystore, com decifragem
  bem-sucedida depois da troca de assinatura. Não houve desinstalação intermediária.
- Assinaturas verificadas com apksigner nas APIs 24, 27, 28, 32, 33 e 36.
  Android 7/8 receberam verificação criptográfica, sem teste de instalação física.
- APK final sem flag debuggable, versão 1.33.0/código 89, minSdk 24/targetSdk 36.
- Site recompilado com validação dos links locais, tamanho e SHA-256 do APK.
  A publicação no GitHub foi concluída depois, com a versão 1.34.0; veja
  [Atualizações pelo GitHub](atualizacoes-github.md).

## Referências

- https://developer.android.com/tools/apksigner
- https://developer.android.com/training/data-storage/app-specific
- https://developer.android.com/privacy-and-security/cryptography
- https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html
- https://developers.google.com/android/play-protect/warning-dev-guidance
