# Atualizações pelo GitHub

A partir da versão 1.34.0, **Ajustes → Atualizações do app** permite consultar,
baixar e instalar versões publicadas em `lhzin0/auroraplay`.

- Consulta diária, agendada pelo Android conforme disponibilidade de rede e
  bateria. **Verificar agora** permite consultar manualmente.
- Download automático ativado por padrão em redes não tarifadas, normalmente
  Wi-Fi. Pode ser desligado na tela de atualizações. Desligar também cancela
  um download automático ainda na fila, mas não interrompe um arquivo em andamento.
- **Baixar agora** usa a conexão atual, inclusive dados móveis. O progresso fica
  na tela e na notificação, com cancelamento. Sem internet, o trabalho aguarda
  rede; falhas de transporte têm até duas novas tentativas.
- O app não instala sozinho. Depois de baixar, toque em **Instalar**. Se o Android
  pedir, permita instalações pelo AuroraPlay e confirme no instalador do sistema.
  O Play Protect continua podendo analisar o APK.
- As versões anteriores à 1.34.0 precisam receber esta primeira atualização por
  download manual. Depois disso, as próximas podem ser recebidas dentro do app.

## Publicação de uma versão

1. Incremente `versionCode` e `versionName` em `app/build.gradle.kts`.
2. Execute `scripts/build-release.ps1` e valide o app.
3. Crie uma GitHub Release estável com a tag `vVERSAO`, por exemplo `v1.34.0`.
4. Antes de publicar, anexe `build/release/AuroraPlay-VERSAO.apk` e
   `build/release/release.json`. Não publique a release sem os dois arquivos.
5. Marque essa versão estável como a mais recente e publique. O app consulta
   `releases/latest/download/release.json`. Rascunhos e pré-lançamentos não entram.
6. O workflow do repositório reconstrói o site e acompanha a release publicada.

O GitHub Pages usa GitHub Actions. O ambiente `github-pages` permite a branch
`main` e as tags `v*`, para publicar tanto mudanças da página quanto releases.
O evento `release` dispara uma execução de `pages.yml` em `main` com
`workflow_dispatch`. Isso evita a reutilização de uma publicação anterior pelo
Pages quando o lançamento tem o mesmo commit da página, comportamento descrito
em https://github.com/actions/deploy-pages/issues/383. Apenas essa etapa recebe
`actions: write`; os arquivos do site e as consultas do app não recebem tokens.

O repositório público contém o site e os arquivos de distribuição. Não recebe o
histórico do projeto Android, credenciais locais ou chaves privadas de assinatura.

## Verificações e privacidade

O manifesto tem versão, código, aplicação, minSdk, tamanho, SHA-256, nome do
arquivo, URL e notas. O app limita tamanho e formato, fixa o repositório de origem,
aceita apenas HTTPS e os hosts de distribuição do GitHub. Nenhuma conta, token,
cookie ou credencial da playlist é usado. O GitHub recebe os dados normais da
conexão, como IP e solicitação do arquivo.

O download fica na pasta privada `files/updates`, fora dos backups. Antes de
oferecer e novamente antes de instalar, o app confere tamanho, SHA-256,
identificador, versão e certificado informado pelo APK. O certificado de produção
é fixado no aplicativo; Android 7/8 usam a identidade legada compatível. O
instalador Android valida a assinatura criptográfica efetiva. Não são aceitos
pacotes de outro aplicativo, certificados desconhecidos ou downgrade de versão.

A permissão `REQUEST_INSTALL_PACKAGES` serve somente para abrir o instalador do
APK escolhido. O FileProvider dá leitura apenas à subpasta de atualizações; não
expõe banco, senhas ou backups. O download concluído é removido depois que a
nova versão abre. As edições `.debug` não agendam nem instalam atualizações de
produção automaticamente.

## Publicação e validação de 3 de setembro de 2026

- Repositório público: https://github.com/lhzin0/auroraplay
- Página: https://lhzin0.github.io/auroraplay/
- Release estável `v1.34.0`, com APK assinado e `release.json` anexados.
- Build debug/release e 14 testes JVM concluídos com sucesso.
- Dois testes instrumentados de atualização passaram no emulador, incluindo
  download real do manifesto e do APK da release pública, validação do arquivo
  e rejeição de hash, versão ou certificado divergentes.
- APK 1.34.0 instalado sobre 1.33.0 no emulador, sem desinstalação.
- SHA-256: `6d7e8b6148aff99db381441482f097ea82764e77d44974a4ef71f6ecdc1d6eda`.
