<!-- Título no formato Conventional Commits, ex.: fix(sync): não apagar catálogo em falha de rede -->

## O que muda

<!-- Descreva a mudança e o motivo. Ligue a issue: "Closes #123". -->

## Tipo

- [ ] `fix` — correção de bug
- [ ] `feat` — nova funcionalidade
- [ ] `refactor` — sem mudança de comportamento
- [ ] `docs` — documentação
- [ ] `chore` / `ci` — build, dependências, automação
- [ ] `website` — site em `website/`

## Impacto de versão

- [ ] Sem mudança de versão
- [ ] `x.x.PATCH`
- [ ] `x.MINOR.x`
- [ ] `MAJOR.x.x` (quebra de compatibilidade — explique)

Se marcou uma mudança de versão: atualizei `versionName`/`versionCode` em
`app/build.gradle.kts` e o `CHANGELOG.md`.

## Verificações

- [ ] `./gradlew testDebugUnitTest lint assembleDebug` passou localmente
- [ ] `./gradlew connectedDebugAndroidTest` (se toquei em código com teste instrumentado)
- [ ] `node website/scripts/build.mjs` (se toquei no site)
- [ ] Testado em dispositivo/emulador — descreva abaixo

**Testado em:** <!-- ex.: Pixel 8 emulador API 35; Galaxy S23 Android 14 -->

## Checklist

- [ ] Segui o estilo do arquivo que editei
- [ ] Não incluí segredos, credenciais, backups ou material de assinatura
- [ ] Sem logs de HTTP / URLs de mídia em log
- [ ] Prints/vídeos (se houver) não mostram login, senha ou link de playlist

## Notas para a revisão

<!-- Pontos de atenção, decisões de design, o que falta. -->
