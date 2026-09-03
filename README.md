# AuroraPlay

**Reprodutor IPTV/Xtream para Android e Android TV.** Kotlin + Jetpack Compose,
Clean Architecture (MVVM), Media3/ExoPlayer.

![versão](https://img.shields.io/badge/versão-1.34.0-8476fa)
![minSdk](https://img.shields.io/badge/minSdk-24%20(Android%207.0)-3ddc84)
![targetSdk](https://img.shields.io/badge/targetSdk-36-3ddc84)
![licença](https://img.shields.io/badge/licença-proprietária-lightgrey)
![plataforma](https://img.shields.io/badge/plataforma-Android%20%7C%20Android%20TV-555)

<!-- Ativar quando o repositório for público:
[![CI](https://github.com/lhzin0/auroraplay/actions/workflows/ci.yml/badge.svg)](https://github.com/lhzin0/auroraplay/actions/workflows/ci.yml)
[![Release](https://github.com/lhzin0/auroraplay/actions/workflows/release.yml/badge.svg)](https://github.com/lhzin0/auroraplay/actions/workflows/release.yml)
-->

Site: <https://lhzin0.github.io/auroraplay/> · Downloads:
[GitHub Releases](https://github.com/lhzin0/auroraplay/releases)

---

## Sobre

AuroraPlay organiza e reproduz **as suas próprias** conexões Xtream Codes:
canais ao vivo, filmes e séries, com perfis locais, favoritos, "continuar
assistindo", busca por gênero, backup portátil e atualização pelo próprio app.
A interface se adapta a celular/tablet e a Android TV (navegação inferior vs.
_rail_ lateral, detectados em tempo de execução).

> **Aviso.** O AuroraPlay **não fornece, hospeda, indexa ou revende** canais,
> filmes, séries ou assinaturas. Não há listas, servidores ou credenciais
> embutidos. Você conecta uma playlist Xtream à qual já tem acesso e é
> responsável pela origem e pela legalidade dela.

## Funcionalidades

- **Perfis locais** ("Quem está assistindo?") com nome, avatar, cor; favoritos
  e histórico por perfil; PIN/biometria em aparelhos compatíveis; perfil
  infantil com filtro baseado no catálogo.
- **Conexões Xtream**: cadastro com servidor/login/senha, múltiplas conexões e
  padrão, teste de acesso, importação/exportação, credenciais guardadas com
  `EncryptedSharedPreferences` (AES-256).
- **Home** com _hero banner_ e trilhos dinâmicos (continuar assistindo, ao
  vivo, filmes, séries, canais recentes, favoritos, recomendados).
- **TV ao vivo** com prévia, lista de canais e categorias; "programa atual"
  com barra de progresso quando a playlist fornece EPG.
- **Filmes e séries** com categorias do servidor, detalhes, trailer _inline_,
  "mais como este", temporadas e episódios.
- **Player** Media3/ExoPlayer com controles próprios (play/pause, _seek_,
  próximo episódio, troca rápida de canais, auto-hide), **prévia na timeline**,
  **modo cinematográfico** persistente, **Picture-in-Picture**, transmissão
  para dispositivos Cast.
- **Busca global** com filtros (Todos/Canais/Filmes/Séries) e por gênero
  (romance, drama, dorama, ação, …).
- **Downloads** de filmes e episódios compatíveis (apenas Wi-Fi opcional).
- **Backup portátil** para um arquivo escolhido pelo usuário (pasta local,
  SD/USB ou nuvem), opcionalmente cifrado por senha.
- **Sincronização** do catálogo com progresso na tela e na notificação,
  continuando fora da tela; atualização periódica em segundo plano.
- **Atualização pelo app** (≥ 1.34.0): consulta as GitHub Releases, baixa e o
  usuário decide quando instalar.

Detalhes de disponibilidade (EPG, trailers, legendas, Cast) dependem do
conteúdo, do servidor e do aparelho.

## Tecnologias

| Área | Stack |
|---|---|
| Linguagem / UI | Kotlin, Jetpack Compose (Material 3), Navigation Compose |
| Arquitetura | Clean Architecture + MVVM, Hilt (DI), Coroutines/Flow |
| Dados | Room, DataStore Preferences, `androidx.security` (EncryptedSharedPreferences) |
| Rede | Retrofit, OkHttp, Gson |
| Mídia | Media3/ExoPlayer (HLS/DASH), Media3 Session, Cast, WorkManager |
| Imagens | Coil, Palette |
| Build | Gradle 8.14.x (KTS), AGP + KSP, JDK 21 (CLI) |

## Estrutura do repositório

```
app/                      # aplicativo Android (com.auroraplay.iptv)
  src/main/java/.../
    core/ domain/ data/ player/ navigation/ presentation/ sync/ update/
  src/test/                # testes JVM
  src/androidTest/         # testes instrumentados
docs/                      # arquitetura, segurança/assinatura, backup, atualizações, release-notes/
scripts/                   # PowerShell: inicializar assinatura, backup da identidade, build de release
website/                   # site estático (GitHub Pages), Node ESM, zero dependências
.github/                   # workflows (CI, Release, Pages, CodeQL), templates, RELEASING.md
CHANGELOG.md               # histórico completo (Keep a Changelog)
```

## Requisitos

- **Android Studio** Koala ou mais recente.
- **JDK 21** para o Gradle **pela linha de comando** (o Gradle 8.14.x falha com
  o JDK 25 que acompanha versões recentes do Android Studio). Aponte
  `JAVA_HOME`/`org.gradle.java.home` para um JDK 21.
- **Android SDK** com `platforms;android-36` e `build-tools;36.0.0`.
- Dispositivo/emulador com **Android 7.0 (API 24)** ou superior.

## Configuração

`local.properties` **não é versionado**. Chaves reconhecidas (todas opcionais):

```properties
sdk.dir=<caminho do Android SDK>

# Metadados e trailers oficiais (TMDB). Sem ela o app compila e roda com
# recursos de metadados limitados. Nunca vai para o build de release.
TMDB_API_KEY=

# Somente build de debug: pré-carrega uma playlist de teste (DebugConnectionSeeder).
SEED_XTREAM_NAME=
SEED_XTREAM_URL=
SEED_XTREAM_USER=
SEED_XTREAM_PASS=
```

Esses valores entram no `BuildConfig` via `localBuildString(...)`; variáveis de
ambiente de mesmo nome têm prioridade. **Nunca comite credenciais.**

## Executar

1. `File → Open` e selecione a pasta do projeto no Android Studio.
2. Deixe o Gradle Sync concluir (precisa de internet na primeira vez).
3. Rode a configuração `app` em um emulador/dispositivo API 24+.
   - Android TV é detectado por `UiModeManager` e troca a navegação
     automaticamente.

Pela linha de comando:

```bash
./gradlew installDebug
```

## Build

```bash
# Debug
./gradlew assembleDebug        # app/build/outputs/apk/debug/

# Release (sai NÃO assinado de propósito)
./gradlew :app:assembleRelease # app/build/outputs/apk/release/app-release-unsigned.apk
```

O APK de release é assinado **depois** do build, com rotação de chave
(_lineage_ v3, esquema APK Signature v3) para instalar por cima das versões
antigas sem desinstalar:

- **Local:** `./scripts/build-release.ps1` (usa a identidade em
  `%LOCALAPPDATA%\AuroraPlay\signing`, criada uma vez por
  `scripts/initialize-signing.ps1`). Gera `build/release/` com o APK assinado,
  `SHA256SUMS.txt` e `release.json`, e verifica os certificados nas APIs
  24/27/28/32/33/36.
- **CI:** `.github/workflows/release.yml` faz o mesmo a partir de _secrets_ ao
  publicar uma tag `vX.Y.Z`. Ver [.github/RELEASING.md](.github/RELEASING.md).

`org.gradle.configuration-cache` fica **desligado de propósito** — ver o
comentário em `gradle.properties`.

## Testes

```bash
./gradlew testDebugUnitTest           # testes JVM
./gradlew lint                        # Android Lint
./gradlew connectedDebugAndroidTest   # instrumentados (precisa de emulador/dispositivo)
```

O CI (`.github/workflows/ci.yml`) roda `testDebugUnitTest lint assembleDebug`
em cada PR, mais o build do site.

## Arquitetura

`presentation → domain ← data`, com `player` isolado. ViewModels expõem
`StateFlow`; repositórios têm _single source of truth_ no Room e a rede só
preenche o cache. Visão completa (persistência, sincronização, player, prévia
da timeline, atualização, backup, Android TV) em
[docs/arquitetura.md](docs/arquitetura.md).

## Site

`website/` é um site estático para GitHub Pages, em Node ESM **sem
dependências**. `npm run dev` (porta 4173) para prévia, `npm run build` para
gerar e validar `dist/`. Publicado por `.github/workflows/pages.yml`. Ver
[website/README.md](website/README.md).

## Releases e atualização pelo app

- Publicação: [.github/RELEASING.md](.github/RELEASING.md).
- Atualização in-app e verificações de integridade:
  [docs/atualizacoes-github.md](docs/atualizacoes-github.md).

## Segurança

- Política e canal privado de report: [SECURITY.md](SECURITY.md).
- Assinatura, migração de chave e proteções de dados:
  [docs/seguranca-e-assinatura.md](docs/seguranca-e-assinatura.md).
- **Nunca** comite credenciais, backups, `local.properties` ou material de
  assinatura (`*.jks`, `*.keystore`, `*.p12`, `*.lineage`, `identity.json`) —
  o `.gitignore` já bloqueia esses padrões.

## Backup

Arquivo escolhido pelo usuário (SAF), opcionalmente cifrado (AES-256-GCM,
chave por PBKDF2). Auto Backup do Android fica desativado de propósito. Uso e
testes: [docs/backup-em-arquivo.md](docs/backup-em-arquivo.md).

## Roadmap / limitações conhecidas

Pontos que encaixam na arquitetura atual (repositório/ViewModel/tela) sem
reestruturação:

- **EPG completo:** a UI já mostra "programa atual"; falta disparar
  `get_short_epg` na sincronização e montar a grade com linha do tempo.
- **Perfil ↔ conexão no onboarding:** o modelo suporta a associação, mas a
  navegação ainda passa `profileId = null` em `AddConnectionScreen`.
- **UI de faixas:** o player expõe `availableAudioTracks` /
  `availableSubtitleTracks` / `setPlaybackSpeed`; falta a tela de seleção sobre
  o `TrackSelector` do Media3.
- **Aba "Downloads":** a infraestrutura e o botão "Baixar" existem; falta uma
  lista única de tudo que foi baixado.
- **Artes finais:** ícones de launcher/banner de TV ainda são vetores simples.

## Contribuição

Projeto proprietário; contribuições são coordenadas pelo mantenedor. Fluxo,
padrões de commit (Conventional Commits) e verificações em
[CONTRIBUTING.md](CONTRIBUTING.md). Participação sujeita ao
[Código de Conduta](CODE_OF_CONDUCT.md). Ajuda e suporte:
[SUPPORT.md](SUPPORT.md).

## Histórico

Registro completo de versões em [CHANGELOG.md](CHANGELOG.md), uma entrada
`## X.Y.Z — data` por release, da mais recente para a mais antiga.
`versionName`/`versionCode` vivem apenas em `app/build.gradle.kts` e aparecem
em `Ajustes → Sobre` via `BuildConfig`.

| Tipo de mudança | Incremento |
|---|---|
| Correção de bug | `x.x.PATCH` |
| Funcionalidade / redesign | `x.MINOR.x` |
| Quebra de compatibilidade | `MAJOR.x.x` |

## Licença

Proprietária — **todos os direitos reservados**. Ver [LICENSE](LICENSE). O APK
distribuído pelos canais oficiais é gratuito para uso pessoal, sem garantia.
