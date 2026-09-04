# Publicando uma versão

O APK de release é **compilado e assinado no PC** com
`scripts/build-release.ps1` e publicado à mão com `gh release create`. O GitHub
Actions **não** compila o app — só reconstrói o site (`pages.yml`) depois que a
release sai.

```
PC:  build-release.ps1  ──►  build/release/{APK, release.json, SHA256SUMS.txt}
     gh release create vX.Y.Z … --latest
                    │  evento release:published
                    ▼
GitHub Actions:  pages.yml  ──►  reconstrói o site apontando para a release
```

## Pré-requisitos (uma vez)

- **Identidade de assinatura** em `%LOCALAPPDATA%\AuroraPlay\signing\`
  (`production.p12`, `production.password.dpapi`, `legacy-debug.keystore`,
  `auroraplay.lineage`, `identity.json`), criada por
  `scripts/initialize-signing.ps1`. **Nunca regere** — faça
  `scripts/export-signing-backup.ps1 -Destination <pasta privada>` e guarde a
  senha de recuperação à parte.
- **GitHub Pages**: `Settings → Pages → Source` = **GitHub Actions**.
- `gh` autenticado na conta `lhzin0`.
- JDK 21 (ex.: `~/.jdks/jbr-21.0.11`) e Android SDK `build-tools;36.0.0`.

## Passo a passo

1. `app/build.gradle.kts`: incremente `versionName` e `versionCode`
   (`versionCode` sempre sobe).
2. Atualize `CHANGELOG.md`.
3. Crie `docs/release-notes/X.Y.Z.json` — array JSON de strings curtas e
   **verdadeiras** (vão para o site e para o corpo da release).
4. Commit na `main` e `git push`.
5. Compile e assine:
   ```powershell
   .\scripts\build-release.ps1 -JavaHome "$HOME\.jdks\jbr-21.0.11"
   ```
   Sai `build/release/AuroraPlay-X.Y.Z.apk` + `release.json` + `SHA256SUMS.txt`,
   com as assinaturas verificadas nas APIs 24/27/28/32/33/36.
6. O `release.json` do script sai com BOM e com notas fixas — **regere** um
   limpo a partir de `docs/release-notes/X.Y.Z.json` e do `sizeBytes`/`sha256`
   reais do APK antes de anexar:
   ```bash
   node -e '
     const fs=require("fs"),c=require("crypto");
     const apk="build/release/AuroraPlay-X.Y.Z.apk";
     const buf=fs.readFileSync(apk);
     const m={applicationId:"com.auroraplay.iptv",minSdk:24,version:"X.Y.Z",
       versionCode:NN,publishedAt:new Date().toISOString().slice(0,10),
       minAndroid:"7.0",fileName:"AuroraPlay-X.Y.Z.apk",
       downloadUrl:"./downloads/AuroraPlay-X.Y.Z.apk",
       sizeBytes:buf.length,sha256:c.createHash("sha256").update(buf).digest("hex"),
       notes:JSON.parse(fs.readFileSync("docs/release-notes/X.Y.Z.json","utf8"))};
     fs.writeFileSync("build/release/release.json",JSON.stringify(m,null,2)+"\n");
     console.log(m);
   '
   ```
7. Publique:
   ```bash
   gh release create vX.Y.Z \
     build/release/AuroraPlay-X.Y.Z.apk \
     build/release/release.json \
     build/release/SHA256SUMS.txt \
     --repo lhzin0/auroraplay --target main --latest \
     --title "AuroraPlay X.Y.Z" --notes-file <corpo.md>
   ```
8. `pages.yml` dispara sozinho (`release:published`) e republica o site com a
   nova versão. O app (≥ 1.34.0) passa a ver a versão em
   `Ajustes → Versão → Verificar agora`.

## Verificação

- `sha256sum AuroraPlay-X.Y.Z.apk` == `sha256` do `release.json` == linha do `SHA256SUMS.txt`.
- <https://lhzin0.github.io/auroraplay/> mostra a nova versão; o botão baixa o asset da release.
- Instale por cima da versão anterior sem desinstalar (exceção: 1.34.0 → 1.35.0
  no Android 9+ exigiu reinstalar por causa da troca de chave — ver abaixo).

## Histórico de assinatura

- **1.35.0** — a identidade de produção anterior (`c3a9a8b7…`) não estava
  disponível; foi regerada. Chave legada (Android 7/8) inalterada; **nova chave
  de produção** `32b13173f273b1decd060d38b65a8a1012e3ed4bc7c6bfd548f7f06d3f2748bd`,
  fixada em `GithubUpdateClient.PRODUCTION_CERTIFICATE`. Da 1.35.0 em diante a
  chave não muda.

## Google Play (opcional)

`./gradlew :app:bundleRelease` gera o `.aab` para o Play Console (use Play App
Signing, chave de upload = `production.p12`). O `.aab` não instala direto num
aparelho; o APK da GitHub Release continua sendo o de sideload. Não misture os
dois canais para o mesmo `applicationId` com chaves diferentes.
