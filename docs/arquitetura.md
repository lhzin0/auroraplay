# Arquitetura

AuroraPlay é um app Android de tela única (`MainActivity` + Navigation Compose)
em **Kotlin + Jetpack Compose**, organizado por **Clean Architecture (MVVM)**
com injeção de dependência por **Hilt**.

## Camadas

```
presentation  ──►  domain  ◄──  data
                     ▲
                   player  (isolado; usado por presentation)
```

| Pacote (`com.auroraplay.iptv.*`) | Responsabilidade |
|---|---|
| `core/` | Tema, módulos Hilt (`di/`), utilitários (`MetadataSanitizer`, etc.) |
| `domain/` | Modelos, interfaces de repositório, _use cases_. Sem dependência de Android framework além do mínimo. |
| `data/` | Implementações de repositório, API Xtream (Retrofit/OkHttp), Room, DataStore, `EncryptedSharedPreferences`, _mappers_, backup |
| `player/` | `PlayerManager` (Media3/ExoPlayer), `PlaybackService`, `ScrubPreviewEngine` |
| `navigation/` | `AuroraNavGraph`, barra inferior (celular) / _rail_ lateral (Android TV) |
| `presentation/` | Uma pasta por _feature_ (`profiles`, `connections`, `home`, `live`, `movies`, `series`, `search`, `player`, `settings`) + `components/` (design system) |
| `sync/` | `CatalogSyncWorker` + agendamento/notradução via WorkManager (atualização periódica do catálogo) |
| `update/` | Atualização in-app a partir de GitHub Releases (`AppUpdateManager`, `GithubUpdateClient`, `AppUpdateActivity`, workers) |

`presentation` depende de `domain`; `data` implementa `domain`; `presentation`
nunca importa `data` diretamente — só interfaces do `domain` resolvidas por Hilt.

## Fluxo de dados

- **ViewModel** expõe `StateFlow<UiState>`; a tela coleta com
  `collectAsStateWithLifecycle`.
- **Use cases** combinam repositórios (ex.: `GetHomeContentUseCase` junta
  "continuar assistindo" + histórico de canais + trilhos por gênero).
- **Repositórios** têm _single source of truth_ no Room; a rede só preenche o
  cache. Leituras da UI são `Flow` do banco (`observeChannels`,
  `observeCategories`, `search`).

## Persistência

| Mecanismo | Uso |
|---|---|
| **Room** (`AppDatabase`) | Catálogo (canais, categorias, filmes, séries, episódios), favoritos, progresso de reprodução, histórico. Escritas de sincronização em `@Transaction replace(...)` (limpa + insere de forma atômica). |
| **DataStore (Preferences)** | Ajustes do app (`AppSettings`): cor de destaque, qualidade, autoplay, modo cinema, intervalo de sincronização, etc. |
| **EncryptedSharedPreferences** (`SecureCredentialStore`, arquivo `aurora_secure_credentials`) | Credenciais Xtream (servidor, login, senha), AES-256. |

## Sincronização do catálogo

`ContentRepositoryImpl.syncConnection` busca cada seção (canais, categorias,
filmes, séries) de forma independente com `getOrNull()`. Só faz
`clear` + `upsert` quando a resposta **não** vem vazia, dentro de uma
transação — uma falha transitória de rede não apaga o catálogo. Categorias de
rádio são filtradas (`MetadataSanitizer.isRadioCategory`).

Gatilhos: sincronização ao abrir o app (respeitando o intervalo configurado),
botão "Atualizar" em _Minhas conexões_, e `CatalogSyncWorker` periódico.

## Player

- `PlayerManager` (`@Singleton`) detém uma instância de ExoPlayer; a UI usa um
  `TextureView` (`player_surface.xml`).
- **Picture-in-Picture**: `setAutoEnterEnabled` (API 31+) via
  `MainActivity.buildPipParams`; `onUserLeaveHint` cobre API 26–30.
- **Modo cinematográfico**: _toggle_ persistido (`AppSettings.cinemaMode`).
  A camada de brilho amostra o `TextureView` já em tela (`getBitmap`) — não há
  segundo decodificador.
- **Prévia da timeline** (`ScrubPreviewEngine`): um ExoPlayer _headless_ por
  vídeo desenha em um `SurfaceTexture`; um contexto **EGL14 + GLES2**
  _off-screen_ lê o quadro com `glReadPixels` para um buffer próprio (mesmo
  caminho de leitura da GPU que `TextureView.getBitmap`). Cache LRU por
  posição. Esse desenho evita o acesso a planos de `ImageReader`, que causava
  crash nativo em hardware Samsung/Exynos.

## Atualização in-app (≥ 1.34.0)

`update/` consulta as releases de `lhzin0/auroraplay`, baixa o APK para
`files/updates` (via WorkManager, download automático opcional em Wi-Fi) e
abre o instalador do sistema quando o usuário toca em **Instalar**. Antes de
oferecer e antes de instalar, confere tamanho, SHA-256, `applicationId`,
versão e certificado; o certificado de produção é fixado no app. O app nunca
se instala sozinho. Ver [atualizacoes-github.md](atualizacoes-github.md).

## Backup

Arquivo escolhido pelo usuário via SAF (pasta local, SD/USB ou provedor de
nuvem instalado). `.aurorabackup` = AES-256-GCM com chave derivada por PBKDF2;
exportação JSON sem senha é opção explícita e traz as credenciais legíveis.
Auto Backup do Android fica desativado. Ver
[backup-em-arquivo.md](backup-em-arquivo.md).

## Android TV

`UiModeManager` detecta o modo TV em tempo de execução e troca a navegação
inferior por um _rail_ lateral; os mesmos ViewModels e telas são reaproveitados.

## Build

`compileSdk 36`, `minSdk 24`, `targetSdk 36`, `jvmTarget 17`. O build **pela
linha de comando** exige **JDK 21** (o Gradle 8.14.x falha com JDK 25). O
`org.gradle.configuration-cache` fica desligado de propósito (ver comentário em
`gradle.properties`). `BuildConfig` recebe `TMDB_API_KEY` e `SEED_XTREAM_*` de
`local.properties`/variáveis de ambiente; o build de release não embute
credenciais e sai **não assinado** — a assinatura (lineage v3) é feita depois
por `scripts/build-release.ps1` ou por `.github/workflows/release.yml`.
