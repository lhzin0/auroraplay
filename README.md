# AuroraPlay

Cliente IPTV/Xtream premium para uso pessoal, construído em Kotlin + Jetpack
Compose com Clean Architecture (MVVM). Funciona **apenas** com conexões
Xtream Codes que você mesmo configurar — não há listas, credenciais ou
servidores pré-embutidos.

## Como abrir o projeto

1. Instale o **Android Studio** (Koala ou mais recente).
2. `File > Open` e selecione a pasta deste projeto.
3. Deixe o Gradle Sync baixar as dependências (é necessária conexão à
   internet na primeira sincronização).
4. Rode em um emulador/dispositivo com **Android 7.0 (API 24)** ou superior.
   Também funciona em Android TV (detecta automaticamente via
   `UiModeManager` e troca a navegação inferior por um rail lateral).

> Este projeto foi escrito e revisado neste ambiente sem acesso ao Android
> SDK/Gradle real (sandbox sem rede), então a validação foi estática —
> balanceamento de chaves/parênteses, assinaturas de composables e bindings
> do Hilt foram conferidos manualmente, mas recomendo rodar
> `./gradlew assembleDebug` no Android Studio antes de considerar definitivo.
> É comum sobrarem 1–2 ajustes pequenos de import em um projeto deste
> tamanho gerado de uma vez.

## Versionamento

| Tipo de mudança | Incremento |
|---|---|
| Correção de bug | `x.x.PATCH` |
| Atualização maior (funcionalidades, redesign) | `x.MINOR.x` |
| Mudança incompatível | `MAJOR.x.x` |

A versão vive em `app/build.gradle.kts` e é lida na tela de Configurações via
`BuildConfig.VERSION_NAME` — não duplique a string em outro lugar. O histórico
completo está em [CHANGELOG.md](CHANGELOG.md).

## Arquitetura

```
app/src/main/java/com/auroraplay/iptv/
├── core/           # tema, DI (Hilt), utilitários
├── domain/         # models, interfaces de repositório, use cases
├── data/           # API Xtream (Retrofit), Room, DataStore, mappers, repositórios
├── player/         # PlayerManager (ExoPlayer/Media3) + PlaybackService
├── navigation/      # NavGraph, bottom bar / TV rail
└── presentation/    # telas + ViewModels por feature
    ├── profiles/ connections/ home/ live/ movies/ series/ search/ player/ settings/
    └── components/  # design system (MediaCard, HeroBanner, estados, etc.)
```

## Funcionalidades implementadas

- Seleção/criação de perfis locais ("Quem está assistindo?")
- Adicionar conexão Xtream com estados de progresso ("Conectando...",
  "Sincronizando canais/filmes/séries...")
- Gerenciador de conexões (testar, sincronizar, definir padrão, excluir)
- Home com hero banner + carrosséis dinâmicos (continuar assistindo,
  ao vivo, filmes, séries, recentes, favoritos, recomendados)
- TV ao vivo com preview + lista de canais + categorias
- Catálogo de Filmes e Séries com categorias dinâmicas do servidor
- Detalhes de filme/série (banner, trailer inline, "Mais como este", temporadas/episódios)
- Player Media3/ExoPlayer com controles próprios (play/pause, seek,
  próximo episódio, lista rápida de canais ao vivo, auto-hide) e **Modo Cinematográfico**
- Busca global com filtros (Todos/Canais/Filmes/Séries)
- Favoritos e "continuar assistindo" persistidos localmente por perfil
- Configurações (cor de destaque, qualidade, autoplay, limpar cache, etc.)
- Credenciais armazenadas com `EncryptedSharedPreferences` (AES-256)

## Versão atual

**1.26.7** — `versionCode 77`. Modo Cinema sem piscar (duas camadas, a anterior
opaca por baixo enquanto a nova aparece por cima). Troca rápida de episódio no
player ("Episódios" → gaveta lateral). Picture-in-Picture (toggle em
Configurações, entra no Home durante o vídeo). Sincronização automática ao
abrir (Desligada/12h/24h/Semanal).

## Novidades desta revisão

### 1.26.7 — 2026-09-02

- **Modo Cinema:** o flicker vinha do `Crossfade` (as duas camadas caíam de
  opacidade juntas). Agora a camada anterior fica 100% por baixo e a nova
  aparece por cima em 2s — sem vale de opacidade.
- **Troca rápida de episódio:** ação "Episódios" no player → gaveta lateral
  com todos os episódios por temporada, toque troca na hora.
- **Picture-in-Picture:** toggle em Configurações › Reprodução (padrão on);
  Home durante o vídeo → PiP na proporção do vídeo.
- **Sincronização automática:** Configurações › Dados — Desligada/12h/24h/
  Semanal; ao abrir o app, sincroniza se estiver mais velho que o intervalo.

### 1.26.6 — 2026-09-02

- **Modo Cinema:** de volta ao frame borrado + esticado nas tarjas (não
  degradê de cor). Amostra 96×54, `scale(1.35)` + `blur(44dp)`, tarja inteira.
  Sem flicker: cross-dissolve **linear** de 1,8s entre amostras (1,5s).

### 1.26.5 — 2026-09-02

- **Fix "app não abre":** APK de debug estava `testOnly` — caches do Gradle
  (`executionHistory` + configuration-cache) herdando `android.injected.testOnly`
  de um build da IDE. Cache limpo + configuration-cache desligado.
- **`[L]` → Legendado:** `Movie.audioLabel` / `Series.audioLabel` agora vem de
  uma coluna calculada no sync a partir do nome cru, então o `[L]`/`[D]` no
  título é reconhecido (não só a categoria).
- **Título de episódio** perde "(2026)", "[L]" e o "S01 E01" colado no fim.
- **Badges de canal** mais arredondados (18dp) + clip extra.

### 1.26.4 — 2026-09-02

- **Modo Cinema:** brilho nas 4 bordas (mínimo 16dp) — topo e base pegam luz
  também.
- **Selo "Legendado"** na página de detalhes de séries/filmes só-legendado
  (`Movie.audioLabel` / `Series.audioLabel`), para não confundir com o áudio.
- **Ano no título:** "(2026)" antes de um marcador ("... (2026) LEGENDADO")
  agora é removido.

### 1.26.3 — 2026-09-02

- **Badges de canal:** cantos 10→16dp e FrostGlass — com o efeito ligado vira
  vidro colorido fosco (mesma cor a ~80%, brilho de topo, borda mais clara);
  desligado, badge sólido.

### 1.26.2 — 2026-09-02

- **Badges de canal melhores** (`ChannelAvatar`): monograma usa o número do
  canal quando o nome começa com número ("91 Rock" → "91R", "102 FM..." →
  "102"); paleta mais suave, degradê + brilho + fio de luz na borda.
- **Botão de mudo do hero removido** — não fazia nada e ficava sob o
  "Assistir".

### 1.26.1 — 2026-09-02

- **Modo Cinema suave.** Sem crossfade de imagem borrada (que piscava num
  corte). Cada amostra da `TextureView` vira cor média (topo/base) → média
  móvel exponencial → `tween` 1,4s. As tarjas são um degradê dessa cor
  sangrando da borda; brilho sutil, amostra a cada 1,2s.

### 1.26.0 — 2026-09-02

- **Troca dublado/legendado no player: removida** (não dava para parear as
  cópias de forma confiável). O catálogo continua colapsando o par e mantendo
  a **dublada** — agora para filmes e séries.
- **FrostGlass** mais opaco/visível nos cartões inline; aplicado também ao
  `AppButton`. Avatar de canal gerado (`ChannelAvatar`) quando falta logo.
  Guia de programação refeito (cartões, alinhamento, "agora", data no
  cabeçalho). Canais sem subtítulo.

### 1.25.17 — 2026-09-02

- **Detecção do gêmeo dublado/legendado bem mais tolerante.** Nova
  `variantKeyBase` poda " - LEG" / " LEGENDADO" / " [L]" do fim do título só
  para agrupar; `getMovieAudioVariants` casa pelo base **sem ano** e só
  descarta candidato com ano conhecido e diferente. O segmento Dublado ⇄
  Legendado na barra superior do player aparece assim que há ≥2 versões e um
  toque troca o stream mantendo a posição.

### 1.25.16 — 2026-09-02

- **Playlist de teste no build de debug.** `DebugConnectionSeeder` pré-carrega
  a conexão "HubPlay" + um perfil "Debug" numa instalação limpa, para não
  refazer o onboarding a cada teste. `BuildConfig.SEED_XTREAM_*` são vazios no
  release (definidos só em `buildTypes.debug`) e a chamada é cercada por
  `BuildConfig.DEBUG`. Só preenche tabela vazia — não mexe no que o usuário
  criou.

### 1.25.15 — 2026-09-02

- **Unificação Dublado/Legendado mais forte.** `audioVariantFrom(name,
  categoria)` classifica pelo nome da categoria também, então cópias sem
  marca no título (Pennyworth, The Witcher…) agora se juntam.
  `collapseAudioVariants` colapsa grupos que misturam legendado + não, ou que
  compartilham título+ano. Home, Filmes e Busca herdam; nada some do banco.
- **Troca rápida no player.** Segmento Dublado ⇄ Legendado na barra superior
  (quando há ≥2 versões): um toque troca o stream mantendo a posição, sem
  menu. A folha completa "Áudio e legendas" segue na ação "Áudio".

### 1.25.14 — 2026-09-02

- **Blur real do fundo no FrostGlass.** Nova dep `dev.chrisbanes.haze:haze`.
  `Modifier.frostSurface(…, haze = state)` faz RenderEffect blur de verdade
  no Android 12+; sem `HazeState`/abaixo disso, cai no fosco estilizado.
  Ligado na barra de navegação (fonte = conteúdo das abas) e no pop-over ⋮
  do player (fonte = `TextureView` do vídeo). Chips/cartões/busca seguem no
  fosco estilizado (blur de fundo não se aplica a elemento não-flutuante).

### 1.25.13 — 2026-09-02

- **FrostGlass nas superfícies que faltavam.** `Modifier.frostSurface` agora
  também vale para `CategoryChip` (chips de categoria), `ContextualSearchField`
  (campo de busca do cabeçalho), `ChannelCard` (lista de Canais) e
  `SettingsSection` (cartões de Configurações). Estado selecionado segue
  chapado; só o neutro vira vidro. Sem mudança de layout; toggle desligado
  volta ao visual atual.

### 1.25.12 — 2026-09-02

- **Dublado/Legendado unificado.** Catálogo: `observeMovies`/`search` colapsam
  as cópias "DUBLADO" e "LEGENDADO" do mesmo filme numa linha só (fica a
  dublada), sem apagar nada do banco — Home, Filmes e Busca herdam. Nomes
  exibidos perdem o sufixo do provedor. Player: folha única "Áudio e
  legendas" com seção de faixas-irmãs ("Dublado" / "Legendado (áudio
  original)") que troca a URL do stream mantendo a posição — e a folha de
  legendas, antes inalcançável, agora abre por ali.

### 1.25.11 — 2026-09-02

- **FrostGlass.** Novo `LocalFrostGlass` + `Modifier.frostSurface`: painéis de
  vidro flutuantes (botão de vidro, barra de navegação, pop-over ⋮ do player)
  ganham um material grafite fosco (degradê, alpha 170) quando ligado; quando
  desligado voltam ao visual chapado atual. Toggle em Configurações ›
  Interface, ligado por padrão. Sem blur de fundo (compatível com todo
  Android, sem conflito com gestos/Cinema).

### 1.25.10 — 2026-09-02

- **Modo Cinema não derruba mais o app.** O brilho ambiente parava de vez em
  quando o processo porque montava um segundo `ExoPlayer`/`MediaCodec` em
  paralelo com o player principal. Agora ele lê frames já decodificados
  direto do `TextureView` na tela (`getBitmap` num alvo minúsculo — leitura
  de GPU barata), com crossfade lento entre amostras. Sem decodificador
  extra: o pior caso é ficar sem brilho, nunca um crash.
- **Toque duplo para pular:** o glifo de ±5/10s que aparecia sozinho a cada
  vez que os controles sumiam (mesmo sem pular nada) foi corrigido — ele era
  disparado por uma guarda que ficava verdadeira para sempre depois do
  primeiro uso. Agora é um sinal de uso único que se apaga sozinho ao fim da
  animação e quando os controles voltam.
- **Toggle de Cinema** também volta o vídeo para "Ajustar", senão um vídeo
  com zoom não tem tarja para o brilho aparecer e o botão parecia morto.

### 1.24.0 — 2026-08-31

- Revisão ampla de UI/UX preservando a identidade preta/grafite + roxo Aurora.
- Navegação inferior refinada e mais compacta, mantendo alvos de toque confortáveis.
- Página de detalhes consolidada em um único palco Banner ⇄ Trailer inline, sem fullscreen obrigatório.
- Indicador do pager corrigido para representar corretamente banner e trailer.
- Hierarquia de ações, metadados e conteúdo mantida responsiva em retrato/paisagem.
- Mantidas arquitetura MVVM, estados, favoritos, progresso, downloads, pesquisa, perfis e player existentes.


### 1.23.5 — 2026-08-31

- Seta de voltar reposicionada acima do banner/trailer.
- Navegação do player protegida contra IDs e URLs inválidos.
- Falhas de carregamento do conteúdo agora são exibidas na própria tela em vez de causar crash.

## 1.23.3 — 2026-08-31

`versionCode 46` · refinamento da página de detalhes e mini-player de trailer

- Card de trailer afastado da barra de status por `statusBarsPadding`, evitando que o conteúdo fique atrás da interface do Android.
- Trailer agora é um mini-player Media3 real dentro da página, sem abrir tela cheia.
- Botão Play/Pause permanece visível no centro do trailer.
- Mudo permanece fixo no canto inferior esquerdo.
- Adicionados indicador de progresso e tempo decorrido do vídeo quando a duração está disponível.
- Removido o botão de fullscreen do trailer.
- Banner continua sendo a primeira página do carrossel; deslize horizontalmente para o trailer.
- Banner e trailer usam o mesmo palco de mídia e proporção responsiva, seguindo o preview de referência.

## [1.21.3] — 2026-08-31

`versionCode 39` · refinamento visual e responsividade do player em paisagem

### Player — interface em paisagem
- A centralização das ações inferiores agora usa a largura real do player, sem deslocamento causado por `systemBarsPadding` ou recortes assimétricos do Android.
- Com quantidade **ímpar** de ações, o ícone central fica exatamente no centro do player.
- Com quantidade **par**, o centro matemático fica exatamente no espaço entre os dois ícones centrais.
- Os botões de ação usam slots de largura uniforme e uma cápsula visual discreta, mantendo espaçamento e leitura consistentes em diferentes proporções.
- A largura dos slots se adapta quando há muitos botões para evitar que o grupo ocupe espaço excessivo em telas estreitas.
- O transporte central permanece independente da barra inferior: `retroceder · play/pause · avançar` continua alinhado ao centro geométrico do player.
- Reduzidos deslocamentos laterais e excesso de espaçamento no modo paisagem, preservando áreas de toque confortáveis.
- Mantidos o vídeo, o Modo Cinematográfico, a timeline e todas as funcionalidades existentes.

### Versão
- `app/build.gradle.kts`: `versionName = 1.21.3` e `versionCode = 39`.


### 1.21.2 — Player: gestos, alinhamento e Cinema
- Toque simples no espaço vazio apenas alterna os controles; nunca gera seek ou animação de avanço/retrocesso.
- Feedback de ±10s agora só nasce de um duplo toque real quando os controles estão ocultos, evitando que uma animação antiga apareça ao fechar os controles.
- Play/Pause, retroceder e avançar usam três slots de largura idêntica e centro matematicamente simétrico.
- Modo Cinematográfico é renderizado somente nas áreas de letterbox reais, inclusive barras superior/inferior, sem cobrir ou modificar o vídeo.
- O player informa a dimensão real do vídeo ao Compose para calcular as barras de forma responsiva.


- **Player em paisagem**: ao abrir, trava em landscape + modo imersivo e
  restaura tudo ao sair — o vídeo preenche a tela em vez de ficar
  letterboxed no meio de uma janela vertical.
- **Gestos no player**: duplo toque esquerda/direita = ±10s, arraste
  vertical à esquerda = brilho, à direita = volume, toque simples =
  mostrar/esconder controles.
- **Controles novos**: legendas, faixa de áudio, velocidade, proporção
  (ajustar/preencher/zoom), bloqueio de tela, Picture-in-Picture,
  "pular introdução".
- **Ao vivo**: canal anterior/próximo, lista rápida lateral, favoritar,
  faixa de "programa atual" com barra de progresso do EPG, e toque no
  preview para abrir em tela cheia.
- **Sinopses**: busca primeiro na própria playlist (`get_vod_info` /
  `get_series_info`); se o servidor não trouxer nada, busca no TMDB.
  Configure sua chave gratuita em **Configurações > Metadados > Chave TMDB**.
  Sem chave, o app funciona igual, só sem o complemento.
- **Continuar assistindo estilo Netflix**: salva posição + temporada/episódio
  a cada 5s e ao sair; retoma exatamente de onde parou; cards landscape 16:9
  com barra de progresso, rótulo "T1 E3 • 32:41" e botão para remover da fila.
  (Corrigido um bug em que o progresso de séries — salvo como
  `seriesId:episodeId` — nunca casava com o card da série na Home.)
- **Textos e interfaces**: novo `MetadataSanitizer` limpa nomes de categoria
  decorados pelo provedor ("➤# DRAMA" → "Drama"), remove durações falsas
  ("00:00:00"), extrai o ano de títulos como "Minions (2015)" e nunca
  renderiza separador "•" sobrando quando um campo está vazio.
- **Cards de canal**: a Home usava o card de pôster 2:3 para canais, o que
  produzia caixas cinza altas e vazias; agora há um `ChannelTile` 16:9 com
  o logo centralizado.
- **Fluidez da Home**: chaves estáveis + `contentType` por linha permitem que
  o Compose reaproveite as linhas na rolagem, a barra superior aparece por
  fade só depois do hero, e o job de conteúdo é cancelado ao trocar de perfil.

## Limitações conhecidas / próximos passos sugeridos

Dado o escopo do pedido, alguns refinamentos ficaram como próximo passo
natural em vez de serem aprofundados nesta primeira entrega:

- **EPG completo**: o `PlayerScreen` e a tela Ao Vivo já exibem "programa
  atual" com barra de progresso, mas a busca do `get_short_epg` ainda não é
  disparada durante a sincronização — os campos ficam nulos até isso ser
  ligado, e a grade de programação com linha do tempo não foi construída.
- **Miniaturas ao arrastar a barra**: plugadas. O `ThumbnailPreviewGenerator`
  extrai o quadro com um ExoPlayer sem tela (o `MediaMetadataRetriever` do
  Android falha na maioria dos VOD de Xtream). Quando o servidor entrega um
  arquivo genuinamente inválido, o cartão mostra só o horário-alvo.
- **Downloads offline**: infra completa (`DownloadTracker`,
  `AuroraDownloadService`, cache dedicado) e botão "Baixar" em filmes e
  episódios; falta uma aba "Downloads" listando tudo em um só lugar.
- **Perfil vinculado à conexão**: no fluxo de onboarding,
  `AddConnectionScreen` recebe `profileId = null` — a associação
  conexão↔perfil existe no modelo de dados mas a navegação ainda não repassa
  o id do perfil recém-criado.
- **Legendas/faixas de áudio/velocidade de reprodução**: o player expõe
  ganchos (`availableAudioTracks`, `availableSubtitleTracks` em
  `PlaybackUiState`, `setPlaybackSpeed`) mas a UI de seleção ainda não foi
  construída sobre o `TrackSelector` do Media3.
- Ícones do launcher/banner da TV são placeholders vetoriais simples —
  troque por artes finais antes de publicar.

Nenhuma dessas pendências exige reestruturar a arquitetura — encaixam nos
mesmos pontos (repositório, ViewModel, tela) já existentes.

## Trailer visual
As páginas de filmes e séries exibem uma prévia cinematográfica baseada nas imagens disponíveis do próprio título, com troca automática, crossfade, movimento sutil e botão de reprodução.

## 1.23.4 — Trailer mini-player
O trailer inline agora usa o stream de vídeo disponível no catálogo com Media3/ExoPlayer, inicia quando fica pronto, mantém áudio habilitado por padrão e oferece mudo no canto inferior esquerdo.
