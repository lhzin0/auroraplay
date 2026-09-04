# Publicando uma versão

Os APKs de release são **compilados e assinados pelo GitHub Actions**
(`.github/workflows/release.yml`), nunca enviados manualmente. A publicação do
site acontece em seguida, por `pages.yml`.

## Visão geral do fluxo

```
tag vX.Y.Z  ──►  release.yml
                   ├─ assembleRelease (não assinado)
                   ├─ assina com lineage v3 (chave legada ─► produção)
                   ├─ verifica APIs 24/27/28/32/33/36
                   ├─ gera release.json + SHA256SUMS.txt
                   └─ cria a GitHub Release (APK + release.json + SHA256SUMS)
                          │  evento release:published
                          ▼
                        pages.yml  ──►  reconstrói o site puxando a release e faz deploy no Pages
```

## Pré-requisitos (uma vez)

### Segredos do repositório (Settings → Secrets and variables → Actions)

| Segredo | Conteúdo |
|---|---|
| `SIGNING_LEGACY_KEYSTORE_BASE64` | `legacy-debug.keystore` em base64 (alias `androiddebugkey`, senha `android`) |
| `SIGNING_PRODUCTION_KEYSTORE_BASE64` | `production.p12` em base64 (PKCS12, alias `auroraplay`) |
| `SIGNING_PRODUCTION_PASSWORD` | senha do store **e** da chave de produção (mesmo valor) |
| `SIGNING_LINEAGE_BASE64` | `auroraplay.lineage` em base64 |

Gere o base64 a partir da identidade local
(`%LOCALAPPDATA%\AuroraPlay\signing`, criada por
`scripts/initialize-signing.ps1`):

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("$env:LOCALAPPDATA\AuroraPlay\signing\legacy-debug.keystore")) | Set-Clipboard
[Convert]::ToBase64String([IO.File]::ReadAllBytes("$env:LOCALAPPDATA\AuroraPlay\signing\production.p12"))          | Set-Clipboard
[Convert]::ToBase64String([IO.File]::ReadAllBytes("$env:LOCALAPPDATA\AuroraPlay\signing\auroraplay.lineage"))      | Set-Clipboard
```

A senha DPAPI (`production.password.dpapi`) **não** serve como
`SIGNING_PRODUCTION_PASSWORD`: ela só abre na conta Windows que a criou. Use a
senha em texto usada ao gerar a chave. Nunca comite chaves, senhas, `.lineage`
ou `identity.json`.

### GitHub Pages

`Settings → Pages → Build and deployment → Source` = **GitHub Actions**.
Ambiente `github-pages`: liberar a branch `main` e as tags `v*`.

## Passo a passo de cada release

1. `app/build.gradle.kts`: incremente `versionName` e `versionCode`
   (`versionCode` sempre sobe).
2. Atualize `CHANGELOG.md`.
3. Crie `docs/release-notes/X.Y.Z.json` — um array JSON de strings curtas e
   **verdadeiras** (aparecem no site e no corpo da release). Sem esse arquivo o
   workflow falha de propósito.
4. Commit na `main` e valide o app (o CI roda testes + lint + `assembleDebug`).
5. Crie e envie a tag (ex.: versão `1.35.0` → tag `v1.35.0`):
   ```bash
   git tag v1.35.0
   git push origin v1.35.0
   ```
6. Acompanhe **Actions → Release**. Ao concluir, confira a GitHub Release:
   `AuroraPlay-X.Y.Z.apk`, `release.json` e `SHA256SUMS.txt` anexados, marcada
   como *latest*, sem *draft*/*prerelease*.
7. `pages.yml` dispara sozinho (evento `release:published`) e republica o site
   já apontando para a nova release.

### Reexecutar sem recriar a tag

`Actions → Release → Run workflow` e informe a tag existente (ex.: `v1.35.0`).
O job atualiza os anexos com `--clobber`.

## Verificação após publicar

- `sha256sum AuroraPlay-X.Y.Z.apk` == `sha256` do `release.json` == linha do `SHA256SUMS.txt`.
- Site em <https://lhzin0.github.io/auroraplay/> mostra a nova versão e o botão
  baixa o asset da GitHub Release.
- App (≥ 1.34.0): `Ajustes → Atualizações do app → Verificar agora` encontra a versão.
- Instale por cima de uma instalação anterior sem desinstalar (migração de
  assinatura v3 a partir do Android 9).

## Primeira release (`v1.34.0`)

O site precisa de uma release publicada para o `pages.yml` puxar o APK
(`RELEASE_FROM_GITHUB=true`). Até lá, `website/downloads/AuroraPlay-1.34.0.apk`
fica versionado como semente. Depois da primeira release por CI, esse APK pode
sair do controle de versão (já está no `.gitignore`, com exceção só para a
versão atual).

## Se a assinatura por CI não estiver pronta

`scripts/build-release.ps1` continua produzindo `build/release/` com o APK
assinado, `release.json` e `SHA256SUMS.txt`. Dá para criar a GitHub Release
manualmente anexando esses três arquivos; o `pages.yml` segue funcionando.

O `release.json` gerado pelo script sai com BOM e com notas fixas da 1.34.0 —
para publicar à mão, regere o `release.json` com as notas de
`docs/release-notes/<versão>.json` e os `sizeBytes`/`sha256` reais do APK
(mesmo formato que `.github/workflows/release.yml` monta), e só então
`gh release create vX.Y.Z <apk> <release.json> <SHA256SUMS.txt> --latest`.

## Histórico de assinatura

- **1.35.0** — a identidade de produção anterior (`c3a9a8b7…`) não estava
  disponível; foi regerada com `scripts/initialize-signing.ps1`. Chave legada
  (Android 7/8) inalterada; **nova chave de produção**
  `32b13173f273b1decd060d38b65a8a1012e3ed4bc7c6bfd548f7f06d3f2748bd`, fixada em
  `GithubUpdateClient.PRODUCTION_CERTIFICATE`. Instalações da 1.34.0 no
  Android 9+ precisam reinstalar. **Não regere de novo** — faça
  `scripts/export-signing-backup.ps1 -Destination <pasta privada>` e guarde a
  senha de recuperação à parte.

## Google Play (opcional, recomendado a médio prazo)

O sideload por APK sempre vai esbarrar no Play Protect enquanto a chave/
desenvolvedor não tiverem reputação (e, no Brasil, a verificação de
desenvolvedor passou a ser exigida em 2026). Para uma distribuição mais limpa:

```bash
./gradlew :app:bundleRelease   # app/build/outputs/bundle/release/app-release.aab
```

- No Play Console, use **Play App Signing**: o `.aab` é enviado assinado com a
  **chave de upload** (pode ser o mesmo `production.p12`, alias `auroraplay`),
  e o Google gerencia a chave que assina os APKs entregues.
- O `.aab` **não instala** direto num aparelho — o APK da GitHub Release
  continua sendo o de sideload. Misturar os dois canais para o mesmo
  `applicationId` com chaves diferentes causa conflito de assinatura ao trocar
  de origem; escolha um canal principal por aparelho.
- Uma faixa de **teste interno/fechado** já basta para uso pessoal e não exige
  revisão pública completa.
