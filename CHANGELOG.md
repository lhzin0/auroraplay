## 1.36.0 — 2026-09-04

- **Próximo episódio automático** volta a pular para o episódio seguinte. A
  contagem regressiva no fim do episódio agora dispara de verdade (e também
  quando o vídeo termina, mesmo se a contagem não fechar em cheio).
- Busca por gênero com **vírgula**: "ação, comédia" traz só filmes e séries que
  têm **os dois** gêneros. Um termo só continua buscando por título ou gênero.
- As versões passam a ser compiladas e assinadas fora do CI; o GitHub Actions
  só reconstrói o site.

## 1.35.0 — 2026-09-03

- Busca por gênero mais precisa: casa por palavra inteira (fim dos falsos
  positivos tipo "ação" trazendo "coração") e reconhece mais gêneros em
  português e inglês (drama, aventura, crime, mistério, fantasia, biografia,
  esporte, novela, dorama, entre outros).
- Novo card **Histórico** nas Configurações, abaixo de **Perfil**: lista os
  filmes e séries assistidos no perfil, com progresso. Fica salvo até você
  tocar em **Limpar** — sem limpeza automática — e entra no backup.
- **Remover de Continuar assistindo** agora só tira o card da fileira: o
  histórico e o tempo assistido são mantidos, e retomar o título traz o card
  de volta. Em séries, remove todos os episódios de uma vez.
- Card de atualização unificado no card **Versão**, no fim das Configurações;
  o card separado "Atualizações do app" saiu, sem informação duplicada.
- Tela **Editar Perfil** redesenhada para o mesmo visual do resto do app
  (cards agrupados, campos e botões padronizados), sem mudar as funções.
- Card **Backup** reposicionado logo abaixo de **Dados**.
- Primeira versão publicada nas Releases do GitHub.

## 1.34.0 — 2026-09-03

- Atualizações pelo GitHub, consulta diária e download automático em redes não tarifadas.
- Progresso e cancelamento nas notificações; instalação iniciada pelo usuário.
- Validação de hash, pacote, versão e certificado do APK antes da instalação.
- Página oficial com instalação, recursos, ajuda, privacidade e download da versão atual.

## 1.33.0 — 2026-09-03

- Assinatura de produção com rotação a partir do Android 9, preservando a
  atualização das instalações existentes; Android 7/8 mantêm a assinatura legada.
- Backup protegido por senha com AES-256-GCM, validação antes da restauração
  e leitura de backups JSON antigos. Credenciais incluídas; downloads excluídos.
- Removida a permissão ampla de armazenamento. Removidos logs HTTP e cache de
  requisições autenticadas; bloqueado downgrade HTTPS→HTTP na API de conexão.
- Credenciais de desenvolvimento retiradas do Gradle rastreado; biblioteca de
  armazenamento cifrado atualizada para a versão estável 1.1.0.
- Scripts para assinatura, verificação e cópia de recuperação das chaves.

## 1.32.0 — 2026-09-02

- Sincronização com notificação do Android, progresso por etapa, cancelamento e
  aviso de conclusão/falha. Trabalho continua ao sair da tela e evita duplicatas
  por conexão. Minhas conexões mostra loading e desabilita Atualizar enquanto ativo.
- Removidos botão e diálogo de senha. Cadastro/importação já guarda a credencial
  automaticamente, e a restauração aplica a senha do arquivo às playlists
  correspondentes e agenda a atualização do catálogo sem nova digitação.
- Exportação avisa quando uma conexão antiga não tem senha, em vez de gerar
  silenciosamente um backup incompleto. Backups antigos seguem legíveis.
- Downloads continuam excluídos. Nomes dos campos do backup de conexões
  preservados no APK otimizado para manter a portabilidade entre versões.

## 1.31.0 — 2026-09-02

- Seletor de salvar/restaurar permite provedores de nuvem, como Google Drive,
  além de pastas locais, SD e USB. A disponibilidade depende dos apps instalados
  e das contas conectadas; nenhuma configuração OAuth própria é necessária.
- Backup v2 inclui link, login e senha das playlists. Na restauração, as senhas
  vão para o armazenamento cifrado do app; senhas já existentes são preservadas
  e credenciais não são aplicadas a conexões com outro servidor/login.
- Arquivos v1 permanecem legíveis, sem senhas. Downloads continuam excluídos.
  A interface informa que o arquivo JSON inclui as senhas em texto legível.

## 1.30.0 — 2026-09-02

- Backup manual em arquivo JSON com escolha de pasta pelo seletor do Android,
  incluindo cartão SD/USB quando disponibilizado pelo aparelho. Restauração
  validada e confirmada antes da importação, também disponível sem perfil.
- Downloads, vídeos baixados, fila de downloads, catálogo e cache são excluídos;
  preservados perfis, conexões sem senha, favoritos, histórico e ajustes.
- Removidos API Drive, autorização OAuth, dependências e envios automáticos.
  Agendamentos antigos são cancelados na atualização; backups existentes não
  são apagados. Auto Backup do Android e transferência automática são desativados.
- Instruções em [docs/backup-em-arquivo.md](docs/backup-em-arquivo.md).

## 1.29.0 — 2026-09-02 (substituído na 1.30.0)

- Backup automático no Google Drive com escolha de conta e apenas o escopo
  `drive.appdata`, sem acesso aos demais arquivos. Agendamento diário e após
  o uso, status do último envio, envio manual e desconexão nas Configurações.
- Recuperação disponível antes de criar um perfil. Cada aparelho preserva o
  backup dos demais; a importação combina dados e mantém o histórico mais
  recente. Senhas continuam locais e podem ser informadas na conexão restaurada.
- Snapshot validado, escrita atômica, regras R8 para Gson e testes de privacidade,
  API e restauração. A configuração OAuth foi removida na versão 1.30.0.

## 1.28.0 — 2026-09-02

- **Backup no Google (automático).** O app agora salva seus **perfis, playlists
  (sem a senha), favoritos, histórico assistido e ajustes** num arquivinho que
  o backup automático do Android envia pra sua conta Google. Ao instalar num
  celular novo (ou reinstalar), esses dados voltam sozinhos — o catálogo
  (canais/filmes) ressincroniza da playlist. Como a senha do Xtream fica num
  cofre cifrado que **não** vai no backup, a conexão volta marcada como
  "offline" e você só re-digita a senha uma vez por aparelho.
  - `UserDataBackup`: snapshot JSON em `files/backup/user_data.json` (só dados
    do usuário, ~1 KB — nada de catálogo, que estouraria a cota de 25 MB).
    Reescrito ao mandar o app pro segundo plano (`MainActivity.onStop`,
    com *debounce*), restaurado em `AuroraApplication` quando a tabela de
    perfis está vazia.
  - Manifesto: `allowBackup=true` + `backup_rules.xml` /
    `data_extraction_rules.xml` que incluem só `files/backup/` e **excluem** o
    banco, o DataStore cru e `aurora_secure_credentials`.

## 1.27.2 — 2026-09-02

- **Preview da timeline funcionando no S23 (Exynos) também.** O caminho
  `ImageReader` (RGBA *ou* YUV) não serve em decoders de hardware Samsung: o
  buffer dos planos ou é só-GPU com capacidade que "mente" (SIGSEGV) ou vem
  com ponteiro nulo (JNI abort) — o app fechava ao arrastar a timeline. Novo
  caminho: o decoder renderiza numa `SurfaceTexture` e um contexto EGL/GLES2
  minúsculo fora de tela amostra essa textura para um FBO de 320×180 e faz
  `glReadPixels` para um buffer **nosso** — a mesma leitura GPU que o
  `TextureView.getBitmap` usa, e a única que funciona nesses decoders.
  Testado no S23: o card mostra o frame real, cor e orientação corretas, sem
  crash mesmo arrastando rápido de ponta a ponta várias vezes.

## 1.27.1 — 2026-09-02

- **Preview da timeline FUNCIONANDO.** O `ScrubPreviewEngine` da 1.27.0 tinha a
  arquitetura certa mas não conseguia ler os frames do decoder. Causa: um
  `ImageReader` RGBA entrega um buffer só de GPU cuja capacidade "mente" (lê
  em memória não mapeada → SIGSEGV) e o `wrapHardwareBuffer` não aceita o
  formato YUV que o decoder realmente produz. Agora o `ImageReader` usa
  `YUV_420_888` (o formato nativo do decoder, com planos de verdade mapeados
  na CPU) e a conversão YUV→RGB é feita já reduzida para 256px, lendo de
  `ByteArray` no heap — sem passeio em `ByteBuffer`, sem crash. Testado no
  emulador: o card mostra o frame real e acompanha o dedo, ficando mais denso
  conforme a grade de pré-geração preenche o cache.
- **Modo Cinema fica ligado até você desligar.** Virou um ajuste persistido
  (`cinemaMode`): o botão no player é a única coisa que liga/desliga, e ele
  continua ligado ao trocar de episódio, sair e voltar ao player, e reabrir o
  app. (Antes, avançar de episódio recriava a tela e zerava para desligado.)

## 1.27.0 — 2026-09-02

### Preview da timeline reescrito

A prévia da timeline foi refeita do zero. O modelo antigo fazia `seek` +
decode de um frame a cada movimento do dedo num `ExoPlayer` headless
sequencial (`Mutex`), com balde de 3s e `delay` fixo — daí o atraso, o
congelamento e a sensação de não acompanhar o dedo.

Agora (`ScrubPreviewEngine`):

- **Um** decoder de preview persistente por vídeo, `prepare()` uma única vez —
  nunca um por movimento, nunca `prepare()` por thumbnail.
- A posição visual e o horário do card são da UI e **nunca** esperam o
  decoder.
- Durante o arraste só se registra a posição mais recente; posições antigas
  são descartadas, sem fila e sem `Mutex`.
- Os frames entram num cache por posição (`TreeMap`, LRU de 64) e o card
  mostra **na hora** o frame mais próximo já disponível — vai ficando nítido
  conforme o decoder alcança (comportamento tipo Netflix/YouTube).
- Pré-geração em segundo plano: uma grade de frames ao longo do vídeo é
  preenchida quando ocioso, e a região perto do dedo tem prioridade.
- Leitura de frame via `Bitmap.wrapHardwareBuffer` (API 29+) — **sem**
  aritmética manual de buffer, que era a causa do `SIGSEGV` nativo. Abaixo do
  Android 10 a prévia fica indisponível (a barra mostra só o horário).
- Ao soltar a timeline: um único `seek` real do player principal, o decoder de
  preview para. O player principal nunca é bloqueado.
- `ThumbnailPreviewGenerator` e `ExoFrameGrabber` removidos.

### Também

- **Barra de progresso só em "Continuar assistindo".** Os cards das trilhas de
  gênero (Ação, Comédia…) não mostram mais a barrinha de tempo.
- **"Canais em destaque" → "Canais recentes".** Agora é o histórico real dos
  últimos 10 canais que você abriu, do mais recente ao mais antigo. Não
  aparece nada até você assistir a um canal. (guardado em `watch_progress`
  com `type = 'LIVE'`, sem migração de banco.)
- **Rádios removidas.** Categorias de rádio ("RÁDIOS", "RÁDIO FM", "WEB
  RÁDIO"…) e seus canais são descartados na sincronização e também filtrados
  na leitura, então somem sem precisar re-sincronizar. (No teste eram 1393 de
  ~2500 "canais".)
- **Modo Cinema não pisca mais com o vídeo parado.** O loop de amostragem
  agora para quando a reprodução está pausada (mostra um frame fixo), e o
  multiplicador `0.9` de opacidade — que fazia as duas camadas somarem *mais
  claro* no meio do fade e voltarem a cada ciclo — foi removido: agora é um
  crossfade linear de brilho constante.

## 1.26.10 — 2026-09-02

- **Crash nativo ao mexer na timeline / ao reabrir o app.** A prévia de
  miniatura (`ExoFrameGrabber.toBitmap`) lia o frame decodificado direto do
  buffer nativo do `ImageReader` com aritmética de stride manual. Em aparelhos
  Samsung/Exynos a capacidade informada desse buffer é maior que a região
  realmente mapeada → leitura em memória não mapeada → **SIGSEGV** que nenhum
  try/catch Kotlin segura, derrubando o processo (visto no S23). A extração de
  frame no aparelho foi **desligada** — a barra de progresso já cai para só o
  horário quando não há frame. Volta quando for reescrita num caminho seguro
  (PixelCopy / SurfaceTexture GL).
- **Canais "sumindo" durante a sincronização.** `clear()` + `upsertAll()` de
  cada seção não eram atômicos: quem estivesse observando a lista via um
  intervalo vazio no meio de toda sincronização (e a de abertura roda sozinha).
  Agora é um `@Transaction` único (`ChannelDao.replace` etc.) — a lista nunca
  pisca "Nenhum canal disponível" no meio de uma atualização. *(O banco do S23
  estava íntegro o tempo todo: 2531 canais, 49 categorias, todas com canais.)*
- **Filtros da Busca ocupam a largura da barra.** As 4 abas
  (Tudo / Filmes / Séries / Canais) agora se distribuem em partes iguais na
  mesma largura do campo de busca — sem o espaço vazio à direita.
- **Ícone estilo OneDark 3D.** O fundo do ícone virou uma "plaquinha" escura
  fosca com leve domo de luz no topo (gradiente #24262F → #0B0C10 + brilho
  radial + vinheta), no espírito do pack OneDark 3D. A marca "A" continua por
  cima.

## 1.26.9 — 2026-09-02

- **Canais ao vivo sumindo (regressão da 1.26.7).** A sincronização apagava a
  tabela de canais **antes** de saber se a busca no servidor tinha dado certo —
  um `getLiveStreams()` que falhava (timeout, 5xx, limite do provedor) virava
  lista vazia e limpava tudo. Com a sincronização automática ao abrir, qualquer
  falha passageira zerava o catálogo. Agora cada seção (canais / filmes /
  séries) só é substituída quando a busca **realmente traz dados**; se falhar,
  mantém o que já estava. *(Depois de instalar, puxe para atualizar em Canais
  uma vez para repovoar.)*
- **PiP não abria janela — só continuava o áudio.** Em Android 12+ o app agora
  usa auto-entrada (`setAutoEnterEnabled`): o sistema encolhe a janela sozinho
  ao ir para a Home/Recentes. O `onUserLeaveHint` (que chegava tarde demais na
  navegação por gestos) virou só o caminho de Android 8–11. *(O One UI pode
  pedir a permissão "Picture-in-picture" do app uma vez.)*
- **Modo Cinema: confirmação explícita.** Tocar no botão mostra "Modo cinema
  ligado / desligado" no centro e o botão ganha um fundo aceso quando está
  ativo — antes só mudava um tom de cor num ícone de 20dp e parecia que "não
  fazia nada / não desligava".
- **Abrir filme/série ficou rápido.** A página não espera mais os
  `get_vod_info` / `get_series_info` / TMDB: aparece na hora com o que já está
  no catálogo local e a sinopse, o elenco de "parecidos" e o trailer entram
  depois. Em séries, a lista de episódios mostra um "Carregando episódios…"
  enquanto chega.
- **Busca por gênero.** Digite "romance", "drama", "dorama", "ação"… e a busca
  geral também traz o que está nessas categorias/gêneros — sem acento e sem
  caixa (então "acao" acha "AÇÃO"), com alguns sinônimos PT/EN.
- **Prévia da timeline não fica mais preta.** O extrator ignora os frames de
  "flush" logo após o seek (~380ms) e recusa frames pretos/chapados, pegando o
  primeiro com imagem de verdade.
- **Animações de abertura/fechamento.** Navegar para uma tela desliza da
  direita com leve recuo da tela de trás; Voltar reverte. (O `NavHost` usava um
  cross-fade chapado.)
- **Configurações mais enxutas.** Saíram o botão "Animações" (o polimento de
  movimento agora é sempre ligado) e o card "Informações e trailers" (era só
  texto — as sinopses automáticas e os trailers continuam iguais).

## 1.26.8 — 2026-09-02

- **Ícone sem o fundo preto.** O `background` do ícone adaptativo virou
  totalmente transparente — sobra só a marca "A". Os PNGs legados (API < 26)
  foram trocados por um vetor em `mipmap-anydpi/` (a marca sobre transparente,
  ~1,5x maior já que aí não há máscara do launcher). Nota: launcher decide o
  que fazer com fundo transparente — Samsung One UI mostra um squircle branco;
  o Pixel Launcher preenche de preto; launchers customizados deixam ver o
  wallpaper.

## 1.26.7 — 2026-09-02

- **Modo Cinema sem piscar.** O `Crossfade` fazia as duas camadas caírem de
  opacidade ao mesmo tempo no meio da transição → a tarja escurecia e voltava
  a cada ciclo (o "piscar"). Agora são duas camadas explícitas: a anterior
  fica 100% opaca **por baixo** enquanto a nova aparece **por cima** (fade
  linear de 2s) — nunca há vale de opacidade. Amostra a cada ~2,7s.
- **Troca rápida de episódio no player.** Nova ação "Episódios" (só em séries)
  abre uma gaveta lateral com todos os episódios agrupados por temporada,
  episódio atual destacado, toque troca na hora mantendo o player aberto
  (`PlayerViewModel.switchToEpisode`). O "Próximo ep." continua.
- **Picture-in-Picture.** Toggle em Configurações › Reprodução (ligado por
  padrão). Ao apertar Home durante um vídeo tocando, o app entra em PiP
  (proporção do vídeo). Em PiP a interface fica só o vídeo.
- **Sincronização automática.** Configurações › Dados: Desligada / 12h / 24h /
  Semanal (padrão 24h). Ao abrir o app, se houver playlist ativa e a última
  sincronização for mais antiga que o intervalo escolhido, sincroniza em
  segundo plano.

## 1.26.6 — 2026-09-02

- **Modo Cinema de volta ao efeito "cinematográfico".** A versão de cor
  chapada (1.26.1–1.26.5) parecia ambilight — "luzes saindo do canto". Voltou
  a ser o frame **borrado e esticado** nas tarjas (estilo YouTube ambient):
  amostra 96×54 da `TextureView`, desenhada `scale(1.35)` + `blur(44dp)` +
  leve escurecimento, preenchendo a tarja inteira (não um degradê que some).
  A "troca brusca de cor" que motivou a mudança anterior foi resolvida de
  outro jeito: **cross-dissolve linear de 1,8s** entre amostras (a cada
  1,5s), então um corte de cena derrete em vez de piscar. Só desenha a tarja
  que existe de verdade (sem mínimo artificial de 16dp).

## 1.26.5 — 2026-09-02

- **Corrige "app crashando sem nem abrir".** O APK de debug estava marcado
  com `android:testOnly="true"` — o Android recusa abrir um APK assim. Causa:
  o histórico de execução do Gradle (`.gradle/8.14.5/executionHistory`) e o
  `configuration-cache` guardavam `-Pandroid.injected.testOnly=true` de um
  build da IDE, e os builds seguintes da linha de comando herdavam. Desliguei
  o `configuration-cache` no `gradle.properties` e limpei os caches.
- **`[L]` = Legendado.** Novo `MovieEntity.audioLabel` / `SeriesEntity.audioLabel`
  (coluna, migração 4→5) calculado no sync a partir do nome/categoria **crus**
  — antes o `title()` já tinha comido o `[L]`, então o selo "Legendado" só
  pegava por categoria. Agora pega o `[L]`/`[D]` no título também.
- **Título de episódio limpo.** `MetadataSanitizer.episodeTitle` tira "(2026)",
  "[L]" e o código de temporada/episódio colado no fim ("... (2026) [L] S01
  E01" → "..."). E o subtítulo do player não repete o nome da série quando o
  "título" do episódio é o próprio nome do show.
- **Badges de canal:** cantos 16→18dp e um `clip` a mais no preenchimento
  para garantir o arredondamento.

## 1.26.4 — 2026-09-02

- **Modo Cinema — brilho nas quatro bordas.** Antes só preenchia a tarja de
  um eixo (a de cima/baixo ficava preta enquanto as laterais brilhavam).
  Agora desenha o brilho hugando os quatro lados, do tamanho da tarja real
  de cada eixo, com um mínimo de 16dp para que topo e base sempre peguem uma
  luz de ambiente.
- **Selo "Legendado".** Séries/filmes que o provedor só oferece legendado
  agora mostram um selo "Legendado" na página de detalhes (linha de
  metadados + no banner), para não gerar confusão sobre o áudio agora que o
  catálogo unifica dublado/legendado. Detectado por título + categoria
  (`MetadataSanitizer.audioLabelOf` → `Movie.audioLabel` / `Series.audioLabel`).
- **Ano no título.** "(2026)" que sobrava no nome quando vinha *antes* de um
  marcador ("... (2026) LEGENDADO") agora é removido — `toDomain` re-roda o
  `title()` depois de tirar o marcador.

## 1.26.3 — 2026-09-02

- **Badges de canal: cantos mais arredondados** (10→16dp) e **FrostGlass**.
  Com o efeito ligado, o badge vira "vidro colorido fosco": mesma cor puxada
  para ~80% (o cartão escuro aparece através), brilho de topo mais forte,
  borda um pouco mais clara. Desligado, continua o badge sólido.

## 1.26.2 — 2026-09-02

- **Botão de mudo do hero removido.** O toggle "silenciar prévia" no canto
  inferior esquerdo do banner da Home não fazia nada (a prévia é uma imagem,
  não vídeo) e ficava por baixo do botão "Assistir". Saiu.
- **Badges de canal melhores** (`ChannelAvatar`). Monograma: canal que começa
  com número usa o número ("91 Rock" → "91R", "102 FM Macapa" → "102",
  "01 FM" → "01F") — que é como IPTV identifica canal; senão as primeiras
  letras. Visual: paleta mais suave/dessaturada, degradê diagonal + brilho
  no canto superior, fio de luz na borda, tipografia mais firme.

## 1.26.1 — 2026-09-02

- **Modo Cinema sem troca brusca de cor.** Em vez de fazer *crossfade* entre
  cópias borradas do frame (que "pisca" num corte de cena), agora ele reduz
  cada amostra da `TextureView` a uma **cor média** do topo e da base do
  frame, passa por uma média móvel exponencial (um corte seco só move o
  brilho ~40%) e ainda anima a cor final com um `tween` de 1,4s. Resultado:
  a luz **desliza**, nunca troca de repente. As tarjas viram um degradê da
  cor sangrando da borda da tela para o vídeo (nada de segunda imagem).
  Amostragem a cada 1,2s; intensidade limitada a um brilho sutil.

## 1.26.0 — 2026-09-02

FrostGlass mais presente, avatares de canal gerados, guia de programação
refeito, e a troca dublado/legendado no player **removida**.

- **Troca Dublado ⇄ Legendado no player: removida.** Os metadados do
  provedor eram inconsistentes demais para parear as duas cópias de um
  título no aparelho de forma confiável. `getMovieAudioVariants`,
  `AudioStreamVariant`, o segmento na barra do player e o `selectAudioVariant`
  saíram. O que fica: o **catálogo mantém só a cópia dublada** e descarta a
  legendada gêmea — agora **para filmes E séries** (`observeMovies` +
  `observeSeries` + Busca). Continua não-destrutivo (o banco guarda tudo).
- **FrostGlass mais visível.** O gradiente fosco inline (cartões, chips,
  campo de busca) subiu para ~0,95→0,76 de opacidade com um tom mais claro
  (`SurfaceHigh`), então cartão não some mais no fundo quase-preto. O caminho
  com blur de fundo (barra de navegação, ⋮ do player) segue no alpha 170.
- **FrostGlass em mais botões:** `AppButton` ("Assistir" etc.) agora é vidro
  fosco com tom de destaque quando o efeito está ligado.
- **Avatar de canal gerado** (`ChannelAvatar`): sem logo do provedor, o canal
  ganha um badge com iniciais + gradiente derivado do nome (determinístico,
  offline). Buscar logo na internet por nome não é confiável, então o badge é
  o fallback. Usado na lista de Canais e no guia.
- **Guia de programação refeito.** Linha vira cartão fosco: logo + nome lado
  a lado num bloco de largura fixa (timelines alinhadas), "Sem programação"
  virou texto discreto à direita, blocos de programa refinados com marcador
  de "agora". Cabeçalho ganhou a data.
- **Sem subtítulo nos canais.** A lista de Canais mostra só o nome (a busca
  de EPG por linha durante o scroll também saiu).

## 1.25.17 — 2026-09-02

Troca Dublado ⇄ Legendado no player: detecção do gêmeo muito mais tolerante.

- **`variantKeyBase`** (nova): tira a "cauda" de marca do fim do título de
  forma agressiva — " - LEG", " LEGENDADO", " [L]", " DUAL ÁUDIO" etc. — só
  para agrupar (nunca no nome exibido). Antes, `variantKey` só tirava marca
  colada no fim; "Filme LEG" (com espaço) não batia com "Filme".
- **`getMovieAudioVariants` afrouxado.** Casa pelo **base sem ano** e só
  descarta um candidato se os dois têm ano *conhecido e diferente* (ano nulo
  de qualquer lado ainda pareia). Assim o gêmeo é achado mesmo quando o
  provedor preenche o ano só numa das cópias. Ainda exige que o par seja
  dublado+legendado (ou mesmo título+ano) pra evitar oferecer troca entre
  filmes diferentes de nome parecido.
- O segmento **Dublado ⇄ Legendado** na barra superior do player aparece
  assim que ≥2 versões são resolvidas; um toque troca o stream mantendo a
  posição (o `PlayerManager` reprepara porque a URL muda).

## 1.25.16 — 2026-09-02

- **Playlist de teste embutida (só debug).** Novo `DebugConnectionSeeder`:
  numa instalação limpa do build de debug, pré-carrega a conexão Xtream
  "HubPlay" (+ um perfil "Debug" 🧪) para pular o onboarding a cada teste.
  As credenciais vivem em `BuildConfig.SEED_XTREAM_*`, que são **vazios no
  build de release** (definidos só no `buildTypes.debug`), e a chamada em
  `AuroraApplication` é cercada por `BuildConfig.DEBUG`. Nunca sobrescreve
  uma conexão/perfil criado pelo usuário — só preenche tabela vazia.

## 1.25.15 — 2026-09-02

Dublado/Legendado: detecção mais forte + troca rápida no player (feedback com print).

- **Detecção pela categoria, não só pelo título.** Novo
  `MetadataSanitizer.audioVariantFrom(name, categoryName)`: um filme numa
  categoria "…Legendado…" é legendado mesmo sem marca no título (era o caso
  de "Pennyworth", "The Witcher: A Origem", "O Jardim dos Esquecidos" — o
  título das duas cópias é idêntico). Legendado ganha quando os dois sinais
  aparecem.
- **`collapseAudioVariants` reescrito.** Agrupa por `variantKey` e colapsa o
  grupo quando: (a) mistura uma cópia legendada com uma não-legendada, ou
  (b) a chave tem ano (mesmo título + mesmo ano ⇒ duplicata), com um teto de
  4 por grupo pra não esconder demais. Sobrevive a dublada. Home, grade de
  Filmes e Busca herdam. Nada é apagado do banco.
- **Troca rápida no player.** Segmento **Dublado ⇄ Legendado** na barra
  superior (aparece quando há ≥2 versões): um toque troca o stream mantendo
  a posição, sem abrir menu — como em qualquer streamer. A folha "Áudio e
  legendas" completa continua na ação "Áudio" da barra de baixo.
- `getMovieAudioVariants` agora classifica por título + categoria, rotula
  "Dublado"/"Legendado" e usa uma busca `LIKE` mais ampla (LIMIT 200) pra
  achar o gêmeo mesmo em categoria de nome diferente.

## 1.25.14 — 2026-09-02

FrostGlass: blur real do fundo nas superfícies flutuantes (lib Haze).

- Nova dependência **`dev.chrisbanes.haze:haze:1.6.10`**. `Modifier.frostSurface`
  ganhou um parâmetro `haze: HazeState?`: quando informado (e o aparelho é
  API 31+), a superfície vira um **RenderEffect blur de verdade** do conteúdo
  marcado com `Modifier.hazeSource(...)`. Sem `HazeState`, ou em API < 31,
  cai no fosco estilizado de antes (degradê translúcido, alpha 170).
- Ligado em: **barra de navegação inferior** (fonte = conteúdo das abas, em
  `AuroraNavGraph`) e **pop-over ⋮ do player** (fonte = a `TextureView` do
  vídeo). É o visual da imagem de referência.
- As superfícies embutidas (chips, cartões de canal, cartões de
  Configurações, campo de busca) seguem no fosco estilizado — blur de fundo
  não faz sentido para elemento que não flutua sobre conteúdo.
- Toggle "Vidro fosco" continua valendo para os dois caminhos; desligado,
  tudo volta ao chapado.

## 1.25.13 — 2026-09-02

FrostGlass agora cobre as superfícies que faltavam (feedback com prints).

- `Modifier.frostSurface` aplicado também a: **chips de categoria**
  (`CategoryChip` — linhas "Todos/Favoritos/…" em Canais/Busca/Filmes/
  Séries), **campo de busca** (`ContextualSearchField` do `PageHeader`),
  **cartão de canal** (`ChannelCard` da lista de Canais) e os **cartões de
  seção** de Configurações (`SettingsSection`). O estado selecionado dos
  chips/cartão continua com o preenchimento chapado de destaque; só o
  estado neutro vira vidro.
- Sem mudança de forma/tamanho/padding/borda — continua só troca de
  material, e o toggle desligado devolve o visual chapado atual.
- Nota: o blur real de fundo da imagem de referência (estilo Mihon) exige
  a lib Haze, que foi descartada antes; o efeito aqui é o fosco
  estilizado (degradê translúcido, alpha 170), consistente em todo Android.

## 1.25.12 — 2026-09-02

Dublado/Legendado unificado — catálogo + seletor no player.

- **Dedup no catálogo (não destrutivo).** `MetadataSanitizer` ganhou
  `stripAudioMarkers`, `audioVariant`, `audioVariantLabel` e `variantKey`
  (nome-base sem marcador/ano, sem acento, `[a-z0-9]` + ano). Em
  `ContentRepositoryImpl.observeMovies` e `search`, cópias "DUBLADO" e
  "LEGENDADO" do mesmo filme colapsam numa linha só (fica a dublada quando
  existe, senão a primeira), preservando a ordem. Só junta quando **ambas**
  estão marcadas — dois filmes diferentes de mesmo nome sem ano nunca são
  escondidos. O banco mantém todas as linhas: favorito/"continuar
  assistindo" no gêmeo oculto ainda abre. Home, grade de Filmes e Busca
  herdam o efeito (todos passam por `observeMovies`).
- **Nome limpo.** `MovieEntity.toDomain`/`SeriesEntity.toDomain` removem o
  sufixo "- DUBLADO" / "(Legendado)" / "[L]" do nome exibido; a linha
  guardada no banco mantém o marcador (o dedup e o player precisam dele).
- **Seletor no player.** Novo `AudioAndSubtitlesSheet` (uma folha só —
  a de legendas antes era inalcançável). Seção **Áudio**: primeiro as
  faixas-irmãs de stream ("Dublado" / "Legendado (áudio original)"),
  depois as faixas de áudio embutidas; seção **Legendas**: "Desativado" +
  faixas embutidas. `PlayerViewModel.selectAudioVariant` troca a URL do
  stream mantendo a posição (via `resumePositionMillis`, o
  `PlayerScreenContent` reprepara dali). Ação "Áudio" na barra aparece
  quando há faixas extras, legendas OU um gêmeo dublado/legendado.
- `ContentRepository.getMovieAudioVariants` resolve os gêmeos por
  `variantKey` (busca LIKE limitada pela palavra mais longa do título).

## 1.25.11 — 2026-09-02

FrostGlass — efeito de vidro fosco com toggle (Configurações › Interface).

- **Novo `LocalFrostGlass` + `Modifier.frostSurface(shape, flat, tint)`**
  (`core/theme/FrostGlass.kt`). Ligado, as superfícies de vidro flutuantes
  do app viram um painel grafite fosco: degradê com topo levemente
  iluminado a ~67% de opacidade (alpha 170). Desligado, cada uma volta
  exatamente ao preenchimento chapado de antes. Só troca o *material* —
  forma, tamanho, padding, cor de texto e hierarquia intactos.
- **Aplicado de forma consistente em:** `GlassButton` (usado no hero da
  Home, detalhes de filme/série, telas de estado/erro), a **barra de
  navegação inferior** flutuante e o **pop-over ⋮ do player**.
- **Sem blur de fundo (RenderEffect):** é API 31+ e se comporta diferente
  entre aparelhos; o fosco estilizado lê como vidro em todo Android e não
  interage com as camadas de gesto/Cinema do player.
- **Toggle** "Vidro fosco (FrostGlass)" em Configurações › Interface,
  **ligado por padrão** (`AppSettings.frostGlass`, chave `frost_glass`).
  `MainActivity` observa só essa flag (como faz com o accent) para o
  root não recompor a cada ajuste não relacionado.

## 1.25.10 — 2026-09-02

Player: fim do crash do Modo Cinema + glifo fantasma do toque duplo.

- **Modo Cinema reescrito, sem segundo decodificador.** O caminho antigo
  pedia frames ao `ExoFrameGrabber` (um segundo `ExoPlayer` + `ImageReader`
  numa thread própria) — um `MediaCodec` de vídeo rodando em paralelo com o
  do player principal, que estourava em aparelhos com pool de codecs
  pequeno. Toda essa lógica saiu do `PlayerViewModel`. Agora o
  `PlayerScreen` amostra o próprio `TextureView` do player
  (`getBitmap(192, 108)` a cada ~0,9s — leitura de GPU de frames já
  decodificados), com `Crossfade` lento entre amostras para o brilho
  "andar" como luz de cinema. Pior caso: sem brilho, nunca crash.
- **`ExoFrameGrabber` agora só serve à preview da timeline.** O grabber e o
  `ThumbnailPreviewGenerator` continuam iguais; apenas deixaram de ser
  chamados pelo Modo Cinema.
- **Glifo de ±5/10s "fantasma" no toque duplo — corrigido.** A guarda era
  `hiddenSeekRippleId > 0`, que fica verdadeira para sempre depois do
  primeiro uso; daí o glifo de avanço/retrocesso piscava toda vez que os
  controles sumiam, sem nenhum seek por trás. Virou um estado de uso único
  (`HiddenSeekRipple?`) que se anula ao fim da animação (`onFinished`) e
  sempre que os controles reaparecem.
- **Ligar o Cinema volta o vídeo para "Ajustar" (FIT).** O brilho só pode
  pintar a tarja que o FIT deixa; com o vídeo em zoom o botão parecia não
  fazer nada.

## 1.25.9 — 2026-09-02

- **Contador discreto de "próximo episódio"** no player. Quando o avanço
  automático está armado (últimos ~40s de um episódio de série, com a opção
  ligada), aparece uma pílula pequena no canto inferior direito: "Próximo
  ep. em Ns" + "Cancelar". Fica visível com ou sem os controles, afastada
  das áreas de gesto (`displayCutoutPadding` + `navigationBarsPadding`), e o
  ticker de posição passa a 1s enquanto ela conta para não pular segundos.
  "Cancelar" desliga o avanço só para o episódio atual.

## 1.25.8 — 2026-08-31

Refino do player (preservando o design atual) + episódio automático.

- **Modo Cinema — blindagem extra contra crash.** O toque no botão agora é
  `runCatching`; o job de amostragem de frame no ViewModel ganhou
  `CoroutineExceptionHandler` + try/catch (cancelamento repassado) e o
  overlay só desenha bitmap válido/não reciclado. Pior caso possível: sem
  brilho de cinema — nunca derruba o player.
- **Timeline** afastada das bordas (padding 18→28dp) e centrada/simétrica:
  espaçador de 64dp à esquerda espelhando o slot de tempo à direita, que
  encolheu para 64dp. Evita conflito com os gestos de borda do Android.
- **Ícones do player sem contorno.** Removidas todas as `.border(...)`: a
  bolinha da timeline virou um círculo branco sólido (com sombra suave), a
  pílula da barra de ações e o cartão de preview perderam o traço, o popup
  de ajustes idem.
- **Popup dos três pontos (⋮)** reposicionado para abrir ABAIXO do botão,
  sem cobrir o próprio ⋮.
- **Controle de brilho** sem cápsula/traço: só ícone + barra + botão, com
  sombra para legibilidade; barra mais larga (8→12dp), botão maior (16→18dp)
  e área útil um pouco maior (52×156 → 56×176dp).
- **Próximo episódio automático** agora funciona: com a opção ligada em
  Configurações › Reprodução, séries avançam sozinhas nos últimos ~40s do
  episódio (heurística de "créditos" — o Xtream não fornece marcadores),
  uma vez por episódio.

Ainda pendente (passo dedicado): efeito FrostGlass + toggle, e a unificação
dublado/legendado com seletor de idioma no player.

## 1.25.7 — 2026-08-31

- **Crash ao tocar em "Cinema" no player** — corrigido. O modo cinema pede
  um quadro do vídeo ao `ExoFrameGrabber`, que monta um segundo ExoPlayer +
  `ImageReader` e faz seek/decodificação numa `HandlerThread` própria
  (`aurora-thumb`). Só a *chamada* `handler.post { … }` estava dentro de
  `runCatching` — o corpo que roda na thread não. Como o app não instala
  `UncaughtExceptionHandler`, qualquer exceção ali (ExoPlayer/MediaCodec/
  ImageReader falhando num codec ou stream fora do comum) derrubava o
  processo inteiro. Em canais **ao vivo** o `prewarm()` é pulado, então o
  botão Cinema era a primeira coisa que ligava esse caminho — daí "crasha em
  qualquer player".
  Agora cada bloco que roda na `aurora-thumb` está encapsulado
  (`try/catch` + `runCatching` no listener do `ImageReader` e na init do
  player), a thread tem seu próprio `uncaughtExceptionHandler` como última
  rede, e o `latch` da inicialização sempre é liberado. Falha de extração
  volta a ser só "sem quadro de cinema", nunca um crash. Preview da timeline
  (que usa o mesmo grabber) ganha a mesma proteção.

## 1.25.6 — 2026-08-31

Versões 1.25.3–1.25.6 saíram sem entrada no changelog. Resumo pelos diffs:
`ExoFrameGrabber.toBitmap` reescrito (cópia RGBA linha a linha, evita o
preview riscado); `PlayerManager` não recarrega a reprodução ao promover o
preview inline para tela cheia (`lastRequestedUrl`); ajustes de timeline no
player e de layout em `DetailMediaPager`/`TrailerPreview`/`SeriesDetails`;
`MetadataEnricher` e arte em `drawable-nodpi`.

## 1.25.2 — 2026-08-31

- **Trailer refeito, sem HTML/bridge**: agora é só a página `/embed/` do
  YouTube num WebView via `loadUrl`. O wrapper anterior (HTML próprio + JS
  IFrame API) nunca funcionava direito dentro do WebView — `loadDataWithBaseURL`
  dá origem opaca à página e as respostas `postMessage` do YouTube eram
  descartadas, deixando o quadro preto. A página nua renderiza o próprio
  pôster e controles e toca inline em qualquer aparelho; `autoplay=1&mute=1`
  dá o início automático mudo estilo Netflix; controles nativos do YouTube
  cuidam de som/pausa/seek. Pílula "YouTube / Abrir no YouTube" sempre
  disponível (e vira a ação principal se o embed falhar).
- **Título estampado no banner**: `DetailBanner` desenha o título em caixa
  alta, peso Black, tracking e sombra suave sobre um degradê inferior — o
  card de detalhe passa a ler como um herói de streaming em vez de um
  screenshot. A linha de metadados continua só no bloco de info abaixo.

## 1.25.1 — 2026-08-31

- **Barra de busca travada**: o campo de texto era controlado por
  `state.query`, que só era atualizado pelo pipeline **com `debounce(250)`**.
  Digitando continuamente, o `debounce` nunca emitia e o campo parecia
  congelado / comia caracteres. Agora `updateQuery`/`updateFilter` atualizam
  o estado do campo e dos chips na hora; só o cálculo de resultados continua
  com debounce. Novo `searchedQuery` evita o flash de "Nenhum resultado"
  enquanto o debounce alcança.

## 1.25.0 — 2026-08-31

- **Trailer nas séries**: a página de detalhes de série agora resolve o
  trailer oficial no TMDB (`tv/{id}/videos`, só YouTube) e ganha a aba
  Banner ⇄ Trailer, igual aos filmes. `trailerYoutubeId` nunca é uma URL de
  episódio/Xtream.
- **Cobertura do trailer ampliada** (filmes e séries): a busca no TMDB tenta
  título limpo + ano → sem ano → em inglês, e os vídeos em pt-BR → en-US, de
  modo que praticamente todo título com trailer no TMDB passa a ter um.
- **Player do trailer** migrado para a YouTube IFrame Player API oficial
  (`new YT.Player`), que funciona dentro do WebView onde o *handshake* manual
  via `postMessage` ficava preto; *watchdog* de 8s e erro de script caem no
  "Abrir no YouTube".
- **Perfis**: conteúdo adulto ("+18"/"XXX"/"Adultos") removido do herói
  rotativo do seletor de perfil (`MatureContentFilter`).

## [1.24.0] — 2026-08-31

### UI/UX
- Revisão ampla da experiência visual com maior densidade, hierarquia e responsividade, sem descaracterizar o AuroraPlay.
- Bottom navigation compactada e refinada.
- Detalhes mantêm Banner ⇄ Trailer no mesmo palco, trailer inline e controles integrados.
- Indicadores do pager corrigidos.
- Preservadas funcionalidades, arquitetura e identidade roxa existentes.

# Changelog

## 1.23.5
- Corrigido o posicionamento da seta de voltar nas páginas de filme e série: ela agora fica em um cabeçalho próprio acima do banner/trailer e nunca sobrepõe a mídia.
- Reforçada a navegação para o player: IDs de conteúdo agora usam `Uri.encode`, evitando problemas com caracteres reservados em rotas.
- Removida a decodificação manual duplicada do `contentId` no NavHost.
- Protegida a abertura de `Continuar/Assistir` contra IDs inválidos, conexão ausente, conteúdo inexistente e URL de reprodução vazia.
- Exceções durante o carregamento do player agora são convertidas em estado de erro com opção de voltar, em vez de derrubar a tela.
- `PlayerManager.play()` passou a validar a URL e capturar falhas de preparação do Media3/ExoPlayer.

## 1.23.4
- Corrigido o mini-player inline do trailer: reprodução real com Media3/ExoPlayer.
- Áudio habilitado por padrão; botão de mudo no canto inferior esquerdo.
- Detecção explícita de MIME para MP4, HLS, DASH, WebM, Matroska e MPEG-TS.
- Player usa HTTP datasource com User-Agent e redirects para maior compatibilidade com servidores Xtream.
- Corrigido o estado do Play/Pause para não divergir do estado real do ExoPlayer.
- Reprodução inicia quando o player chega a READY e a página do trailer está ativa.
- Mantido trailer inline, sem fullscreen.

## 1.23.3 — 2026-08-31
- Ajustado o palco de mídia das páginas de detalhes para respeitar a área segura superior do Android.
- Trailer agora funciona como mini-player Media3 inline, sem navegação para tela cheia.
- Play/Pause permanece disponível no centro; mudo fica no canto inferior esquerdo.
- Adicionados progresso e tempo do trailer quando disponíveis.
- Banner permanece como primeira página e o trailer como segunda, alternáveis por deslize horizontal.
- Refinada a proporção do card para melhor visualização em retrato e paisagem.

## [1.23.2] — 2026-08-31

`versionCode 45` · trailer inline e correções de área segura

### Trailer inline
- Trailer agora usa um mini-player Media3/ExoPlayer real quando existe uma URL de reprodução disponível no catálogo.
- Reprodução automática apenas quando a página Trailer está ativa; ao voltar para Banner, o player é pausado.
- Botão central Play/Pause sempre disponível quando o trailer está parado e reaparece após interação.
- Mudo no canto inferior esquerdo e botão de expansão no canto inferior direito, sem navegação automática para fullscreen.
- Falha de reprodução cai para o fallback visual de imagens, evitando crash da página de detalhes.

### Área segura
- Conteúdo das telas de detalhes recebe `WindowInsets.navigationBars` no final da lista.
- O card do trailer respeita a área segura para não ficar atrás da barra/gestos do Android.
- Mantida a navegação Banner ⇄ Trailer por deslize horizontal.

---

## [1.23.1] — 2026-08-31

`versionCode 44` · refinamento da página de detalhes e trailer inline

### Detalhes — mídia
- Banner agora é sempre a primeira página do estágio de mídia.
- Deslize horizontal para alternar entre Banner e Trailer.
- Trailer permanece inline na própria página, sem fullscreen e sem navegação para o player.
- Prévia cinematográfica inicia automaticamente ao entrar na página do trailer.
- Mantido botão de mudo no canto inferior esquerdo.
- Pager não mantém páginas fora da viewport, reduzindo trabalho desnecessário.

# Changelog

## 1.23.0 — 2026-08-31
- Corrigida a abertura do trailer para permanecer inline, sem navegar para o PlayerScreen e sem exigir tela cheia.
- O trailer agora expande dentro da própria página e continua usando as imagens reais do título com crossfade, autoplay visual e movimento suave.
- Mantido o botão de mudo no canto inferior esquerdo; como o catálogo atual fornece imagens, ele permanece preparado para áudio futuro sem iniciar reprodução do conteúdo completo.
- Banner passou a aparecer antes do trailer nas páginas de filmes e séries.
- “Continuar/Assistir” e “Minha lista” agora dividem a largura disponível sem espaço morto.
- Ações secundárias de séries usam slots uniformes para evitar sobras laterais.
- Hero em paisagem usa largura controlada para manter proporção e alinhamento.

## 1.22.2 — 2026-08-31
- Corrigido o Modo Cinematográfico no player: a superfície de vídeo agora usa `TextureView`, permitindo que a camada cinematográfica seja renderizada acima da superfície sem ser ocultada por `SurfaceView`.
- As áreas de letterbox superior/inferior e laterais passam a revelar corretamente o frame cinematográfico quando o modo `FIT` cria essas barras.
- Mantido o vídeo original sem blur ou alteração de cor.
- A superfície do vídeo e o shutter foram configurados como transparentes fora da imagem renderizada, evitando que o fundo preto do `PlayerView` esconda o Cinema.
- Mantido o comportamento responsivo para diferentes proporções e o modo `ZOOM` sem Cinema quando não existem barras visíveis.

# Changelog

## 1.22.1 — 2026-08-31
- Modo paisagem da Home agora remove a barra superior do aplicativo e entrega o hero/trailer como palco horizontal imersivo.
- Hero em paisagem usa backdrop, proporção cinematográfica e largura total, sem exigir tela cheia para visualizar a prévia.
- Ações “Assistir” e “Minha lista” ficam centralizadas como um grupo, com largura equilibrada e áreas de toque consistentes.
- Adicionado controle de mudo no canto inferior esquerdo da prévia, preparado para trailers com áudio e mantendo o estado visual enquanto o catálogo ainda fornece imagens.
- TrailerPreview das páginas de filme/série também se adapta à paisagem, ocupando melhor a largura disponível.
- Mantidos o carrossel, autoplay visual, crossfade, Ken Burns, navegação e funcionalidades existentes.

## 1.22.0
- Novo trailer visual nas páginas de filmes e séries.
- Prévia automática com fotos do próprio título, crossfade e movimento Ken Burns sutil.
- Botão central de reprodução e indicador de progresso das cenas.
- Séries usam backdrop, poster e thumbnails de episódios disponíveis.
- Filmes usam backdrop e poster disponíveis no catálogo.
- A prévia não inicia o conteúdo completo automaticamente; tocar no trailer abre a reprodução normal.

# Changelog

## [1.21.3] — 2026-08-31

`versionCode 39` · refinamento visual e responsividade do player em paisagem

### Player
- Corrigida a referência horizontal usada pela barra de ações inferiores: o grupo agora é centralizado em relação ao player inteiro, não em relação a uma área alterada por insets laterais.
- Regra de alinhamento explícita: quantidade ímpar posiciona o ícone central no centro geométrico; quantidade par posiciona o intervalo entre os dois ícones centrais no centro geométrico.
- Slots uniformes e espaçamento compacto evitam que labels de tamanhos diferentes alterem o eixo visual do grupo.
- Adicionada cápsula visual discreta para agrupar as ações e melhorar a leitura em paisagem.
- Slot reduzido automaticamente quando há cinco ou mais ações, evitando que o conjunto fique largo demais.
- Transporte `−10 · Play/Pause · +10` continua centralizado independentemente da quantidade de ações inferiores.
- Removido o `systemBarsPadding()` do container geral dos controles, que podia deslocar horizontalmente a barra inferior em paisagem com insets assimétricos.

### Versão
- `app/build.gradle.kts`: `versionName = 1.21.3` e `versionCode = 39`.

---

## [1.21.1] — 2026-08-31

`versionCode 37` · correções de compilação e estabilidade do player

### Correções
- Removidos imports de APIs de ponteiro inexistentes/não utilizados que causavam `Unresolved reference` no `PlayerScreen`.
- Migrado `hiltViewModel()` para o pacote atual `androidx.hilt.lifecycle.viewmodel.compose`, eliminando a depreciação do pacote antigo.
- Corrigido o uso de `Modifier.offset` com estado animado para a sobrecarga lambda, evitando conversões incorretas entre `Float`, `Dp` e `IntOffset`.
- Adicionado `@OptIn(UnstableApi::class)` ao componente de geração de previews que utiliza Media3 instável.
- Removidos parâmetros de `PlayerControlsOverlay` que não eram utilizados e geravam avisos.
- Na 1.21.1 o efeito ainda era composto como fundo; a 1.21.2 substitui essa estratégia por renderização explícita nas áreas de letterbox, compatível com superfícies de vídeo opacas.
- Mantido o modo Cinematográfico restrito às áreas vazias, sem alterar a imagem principal.

### Versão
- `app/build.gradle.kts`: `versionName = 1.21.1` e `versionCode = 37`.

---

# Changelog

## [1.21.2] — 2026-08-31

`versionCode 38` · correção definitiva do player

### Player
- Corrigido o fluxo de toque do player: um toque simples na área vazia somente abre/fecha os controles e não pode iniciar seek.
- Corrigido o feedback residual de ±10s que podia reaparecer ao esconder os controles depois de um seek anterior.
- Play/Pause, −10/+10 e canais anterior/próximo passaram a ocupar slots simétricos de largura fixa, mantendo o centro visual alinhado em diferentes telas.
- O Modo Cinematográfico deixou de depender de uma superfície de vídeo transparente: agora é desenhado sobre o PlayerView somente nas barras de letterbox, preservando integralmente o vídeo.
- Adicionada a dimensão real do vídeo ao `PlaybackUiState` através de `onVideoSizeChanged`, permitindo calcular barras superior/inferior ou laterais responsivamente.

### Versão
- `app/build.gradle.kts`: `versionName = 1.21.2` e `versionCode = 38`.

---


Todas as mudanças relevantes do AuroraPlay.

## [1.21.0] — 2026-08-31

`versionCode 36` · redesign do modo ambiente para modo cinematográfico

### Player — modo cinematográfico
- **Modo Ambiente removido e substituído por Cinema**: o player agora usa
  frames reais do vídeo como fonte visual do fundo, em vez de apenas uma cor
  dominante.
- O frame é **esticado para preencher a área disponível, desfocado e
  escurecido** antes de ser exibido atrás do vídeo, reproduzindo o princípio
  visual documentado pelo YouTube para seu Ambient mode: usar thumbnails/storyboards,
  ampliar, desfocar e aplicar uma camada escura para criar uma extensão
  luminosa sem competir com o conteúdo principal.
- O vídeo principal permanece **100% intacto**: nenhuma cor, blur ou scrim é
  aplicado sobre a superfície do vídeo. O efeito só aparece nas áreas que
  ficariam vazias devido à diferença de proporção.
- Atualização do frame a cada ~6 segundos com `Crossfade` de 900 ms, evitando
  mudanças bruscas e reduzindo o custo de decodificação.
- Decodificação e processamento continuam fora da Main Thread; o efeito é
  pausado durante buffering para não piorar stalls.
- Novo rótulo **Cinema** e ícone de cinema no controle inferior.
- A implementação foi pensada para manter boa experiência tanto em aparelhos
  mais fortes quanto em dispositivos mais modestos, seguindo a preocupação
  do próprio YouTube com diferentes classes de hardware.

### Versão
- `app/build.gradle.kts`: `versionName = 1.21.0` e `versionCode = 36`.

**Convenção de versionamento**

| Tipo de mudança | Incremento | Exemplo |
|---|---|---|
| Correção de bug | `x.x.PATCH` | 1.3.0 → 1.3.1 |
| Atualização maior (novas funcionalidades, redesign) | `x.MINOR.x` | 1.2.1 → 1.3.0 |
| Mudança estrutural incompatível | `MAJOR.x.x` | 1.x.x → 2.0.0 |

O `versionCode` incrementa de 1 em 1 em toda liberação, independente do tipo.

---

## [1.20.0] — 2026-08-30

`versionCode 35` · navegação por 3 botões · player (brilho, timeline, animações, centralização, alinhamento, tempo de seek) · preview de quadro via ExoPlayer · desempenho (reprodução, busca) · troca rápida de perfil + trava infantil · carrossel infinito · nomes limpos · ícones sem fundo · menu ⋮ do card · banner de conexão

### Novidades desta rodada (sobre a 1.19)
- **Alinhamento** definitivo dos controles do player: anel de carregamento
  dentro do botão Play; barra de ações com itens de largura igual (a do meio
  cai no centro exato, sob o Play); cluster de transporte no centro real.
- **Duplo-toque para pular** restrito às faixas externas (25%) e fora do meio
  vertical — não dispara mais por engano ao abrir/fechar os controles; o
  feedback ficou mais curto.
- **Carrossel do herói** dá a volta: passar do último card volta ao primeiro.
- **Busca** — trocar de aba (Todos/Filmes/Séries/Canais) não trava mais: a
  deduplicação/filtragem do catálogo inteiro saiu da thread principal
  (`flowOn(Default)` + `conflate`).
- **Trava infantil**: pela troca rápida de perfil, sair de um perfil infantil
  pede biometria / bloqueio do aparelho.
- **Preview da timeline** pré-aquece o decodificador ao abrir o vídeo (seek
  `PREVIOUS_SYNC`), pra primeira raspagem já vir com quadro.
- **Versão** movida para 1.20.0 (`versionCode 35`) — a string vive só em
  `app/build.gradle.kts`, como manda o README.

### Player — ajustes finos (seek, centralização, timeline)
- **Ícone de carregamento** agora é um anel **dentro do próprio botão
  Play/Pause** enquanto está em buffer — sempre concêntrico com ele, em vez
  de um elemento centralizado à parte que destoava alguns pixels em certas
  proporções de tela. Com os controles escondidos, volta a ser o *spinner*
  central de sempre.
- **Duplo-toque para pular** só conta nos **terços externos** da tela (o
  terço central e as faixas de cima/baixo são zona morta) — assim tocar numa
  área vazia perto do meio, ou tocar pra abrir/fechar os controles, não
  adianta mais o vídeo. Continua funcionando com os controles escondidos.
- Com os controles **visíveis**, o duplo-toque gira **um único** botão
  −10/+10 (o do lado tocado), sem desenhar um segundo ícone ao lado.
- **−10 / +10** afastados do Play (espaçamento 28→44dp) — não ficam mais
  colados no botão central. Vale também para o feedback com os controles
  escondidos.
- **Cluster de transporte** alinhado pelo `Box` de tela cheia (centro real
  da tela em qualquer proporção), não pelo meio entre a barra de cima e a de
  baixo.
- **Barra inferior de ações** segue a contagem: 2–3 itens (VOD) viram um
  grupo centralizado sob o transporte; o conjunto maior do ao vivo mantém o
  espalhamento por toda a largura.
- **Bolinha da timeline** um pouco maior (13→16dp; 20→24dp ao arrastar).
- **Tempo de seek configurável**: Configurações › Reprodução › "Avançar /
  retroceder" → **10s** ou **5s**. Os botões trocam o glifo (10/5) e o
  duplo-toque acompanha.

### Preview da timeline — quadro real via ExoPlayer
- O `MediaMetadataRetriever` do Android não abre a maioria dos VOD de Xtream
  (bytes que não batem com o `.mp4` declarado) e ainda travava ~30s por URL
  ruim. Trocado por um **ExoPlayer sem tela** que renderiza num `ImageReader`
  320×180 e lê o quadro de volta — ele abre o que também consegue *tocar*.
  Uma extração por vez (`Mutex`), tempo máximo de 6s, e a URL é marcada como
  "sem thumbnail" na 1ª falha. Onde o servidor entrega um arquivo
  genuinamente inválido, o cartão mostra só o horário-alvo.

### Nomes — não parecerem os do provedor
- `categoryName` de canais/filmes/séries passa pelo `MetadataSanitizer` (na
  gravação **e** na leitura, então vale para o que já foi sincronizado):
  tira `➤`, `#`, `|BR|`, contadores tipo `[2]` e o ano solto no fim
  ("# Lançamentos 2026 [2]" → "Lançamentos").

### Ícones sem fundo
- Os chips de ícone das Configurações e o `(i)` do herói perderam o círculo
  de fundo — só o glifo, um pouco maior.

### Cartão "Continuar assistindo" — menu ⋮
- O ⋮ abre uma **folha inferior** (estilo referência) com título + "Mais
  informações" / "Remover da fileira", em vez de remover na hora.

### Desempenho — travamentos de vídeo (ao vivo, filmes e séries)
- **Fallback de decodificador** ligado (`DefaultRenderersFactory
  .setEnableDecoderFallback(true)`). Quando o decodificador de vídeo
  primário (quase sempre o de hardware) falha ao iniciar ou lança erro no
  meio de um stream com codec/perfil fora do comum, o ExoPlayer cai para
  outro decodificador em vez de estourar um erro fatal — é a diferença
  entre um engasgo de um quadro e uma tela preta num canal problemático.
- **Política de buffer** ajustada para streaming instável em vez dos
  padrões do ExoPlayer para arquivo local (teto de 50s, 5s após
  *rebuffer*): acumula até 2 min de VOD quando a banda permite (um oscilo
  depois é absorvido pelo buffer, não vira parada); após uma parada, espera
  um colchão de 6s antes de retomar pra não recair num laço de *buffering*;
  prioriza duração sobre limite de bytes pra streams 1080p/4K ainda
  pré-carregarem segundos suficientes.
- **Timeouts de HTTP** explícitos no `DataSource` de mídia (conexão e
  leitura em 20s). Origens de IPTV costumam demorar a responder e redirecionar
  http→https; sem timeout, uma conexão travada ficava presa nos 8s padrão e
  aparecia como imagem congelada.
- **`setHandleAudioBecomingNoisy(true)`** — pausa ao desconectar o fone em
  vez de seguir tocando alto no viva-voz.
- **Ticker de posição** do player passa a 2s (era 500ms fixos) quando os
  controles estão escondidos — nada mostra a posição nesse momento, então
  não faz sentido reavaliar a árvore inteira do player duas vezes por
  segundo justo na hora em que o decodificador quer a CPU. Volta a 500ms
  assim que os controles reaparecem.
- **Modo Ambiente** pula o ciclo de amostragem enquanto o player está
  *rebuffering* — decodificar um quadro abre uma leitura própria no mesmo
  stream, e empilhar isso durante uma parada só alonga a parada.

### Correção — banner "Sem conexão com a internet" preso
- O `NetworkMonitor` só considerava a rede online se ela tivesse
  `NET_CAPABILITY_VALIDATED`. Emuladores e muitas redes reais demoram (ou
  nunca conseguem) passar na sondagem de *captive portal* do Android, então
  essa *capability* fica ausente mesmo com tráfego funcionando — e o banner
  vermelho ficava fixo na tela. Agora basta `NET_CAPABILITY_INTERNET` (um
  *captive portal* passar como "online" é um problema bem menor, já que esse
  fluxo só controla o banner e não bloqueia nada).
- Passou a usar `registerDefaultNetworkCallback` (a rede que o app de fato
  usa) em vez de um `NetworkRequest` vazio que casava com qualquer rede do
  aparelho; `onLost`/`onUnavailable` tratados explicitamente.

### Correção — telas cortadas em aparelhos com navegação por 3 botões
- **Início, Canais e Buscar**: o recuo inferior das listas era um valor fixo
  de 96dp — a altura da própria barra flutuante. Em aparelhos com a barra de
  navegação de 3 botões (não gestos) o *inset* do sistema é bem maior, então
  a barra flutuante sobe com o `navigationBarsPadding()` e as últimas fileiras
  ficavam escondidas atrás dela. Agora o recuo é `96dp + inset real da barra
  do sistema` (`floatingBarClearance`), então nada fica coberto — e em
  aparelhos com gestos o resultado é idêntico ao de antes.
- **Player**: o overlay de controles agora aplica `systemBarsPadding()` além
  do `displayCutoutPadding()`. É uma rede de segurança para telas com a barra
  de 3 botões em paisagem, onde algumas skins de fabricante mantêm uma faixa
  da barra visível mesmo em modo imersivo — a fileira de ações e a timeline
  não ficam mais por baixo dela. Contribui 0 onde as barras somem de verdade.
- **Escolha de perfil**: o botão "Gerenciar perfis" tinha só 16dp de margem
  inferior e ficava por baixo da barra de 3 botões. Agora o painel aplica
  `navigationBarsPadding()`.
- **Adicionar conexão**: o formulário (rolável) ganhou `navigationBarsPadding()`
  para o botão "Conectar" não encostar na barra do sistema no fim da rolagem.

### Player — brilho e animações dos botões (conforme referência)
- **Slider de brilho** deixou de ser uma lasca fina: pílula mais larga
  (40→52dp), trilho de 5→8dp, altura efetiva ~100→156dp, e um
  **botão (knob)** na linha de preenchimento — alvo de arraste óbvio e
  leitura clara do nível. A sensibilidade acompanha a altura (arrastar a
  altura toda = faixa toda), então o controle maior não fica "lento".
- **−10 / +10**: **um único ícone** que dá uma volta completa no sentido do
  *seek* ao tocar (antes era meio-giro de 32° que voltava). Sem disco extra,
  sem texto "+10" ao lado — só o glifo girando, como no vídeo de referência.
- **Duplo-toque na tela** (pular 10s) usa **a mesma animação** do botão — um
  glifo branco girando uma volta e sumindo. Antes era um "burst" com disco
  escuro à parte; agora o toque no botão e o duplo-toque no vídeo são um só.
- **Canais**: os botões de canal anterior/próximo (que não tinham animação
  nenhuma) agora dão um empurrãozinho no sentido da troca e voltam com mola.
- **Play/Pause** ganhou um repique de escala (0.88→1) ao pressionar, além do
  crossfade que já existia entre os ícones.

### Player — timeline e centralização
- **Timeline**: trilho de 3→4dp e uma **bolinha** de posição de verdade — um
  círculo roxo com anel branco (legível sobre qualquer quadro), que cresce
  para 20dp e ganha um halo enquanto se arrasta. Precisão bem melhor que o
  pontinho de 11dp de antes.
- **Preview ao arrastar**: o cartão com o horário-alvo agora aparece durante
  **toda** a raspagem (antes só surgia se um quadro tivesse sido extraído);
  quando o extrator não consegue um quadro para aquele stream, mostra o
  horário e um marcador em vez de nada.
- **Ícone de carregamento** volta a ficar **concêntrico com o Play/Pause**. O
  aglomerado de transporte (−10 · Play · +10) e o brilho passaram a se
  alinhar pelo `Box` de tela cheia, não pelo meio de uma barra superior e uma
  inferior de alturas diferentes — então ficam no **centro real da tela em
  qualquer proporção**, no mesmo ponto do *spinner* de buffer.

### Player — falhas de reprodução por canal/título ("Não foi possível reproduzir")
Investigação: **não é um bug geral** — acontece em canais/títulos específicos
em que o servidor Xtream entrega um stream que não bate com o container que
ele declara (HLS inexistente para o canal, `container_extension = mp4` com
bytes que o extrator não abre, entradas mortas). Canais/títulos íntegros
tocam normalmente. Mitigações no código:
- **Canal ao vivo**: a URL é pedida como HLS (`…/live/…/<id>.m3u8`), mas
  muitos servidores só empacotam aquele canal como MPEG-TS puro. No primeiro
  erro, o player tenta `…<id>.ts` uma vez antes de mostrar a mensagem — por
  canal, não global.
- **Preview da timeline**: o `MediaMetadataRetriever` ficava ~30s preso em
  cada URL ruim, e um arraste rápido enfileirava uma dezena dessas chamadas
  nativas em paralelo. Agora: uma única extração por vez (`Mutex`), tempo
  máximo de 6s, `User-Agent` no request, e a URL é marcada como "sem
  thumbnail" na primeira falha e nunca mais tentada na sessão.

### Perfil — troca rápida
- Nova linha **"Trocar perfil"** em Configurações › Perfil (aparece com 2+
  perfis): abre uma folha com todos os perfis e troca o ativo **na hora**,
  sem passar pela tela "Escolha o seu perfil". Início, favoritos e "continuar
  assistindo" já observam o perfil ativo e se atualizam sozinhos.

---

## [1.18.0] — 2026-08-29

`versionCode 33` · refatoração da UI do player

### Player — refatorado (não só reposicionado)
- **Menu ⋮ (três pontos)** no canto superior direito → painel flutuante
  (fade + escala a partir do canto, fecha ao tocar fora) com **Velocidade**
  e **Proporção** (ícone + nome + valor atual). Esses dois saíram da barra
  inferior.
- **Controles centrais**: hierarquia `−10 · Play/Pause · +10`, centralizada.
  −10/+10 agora com alvo de toque de 60dp e glifo 36dp (antes 38dp num
  `IconButton` padrão apertado); Play/Pause reduzido de 72→64dp. Ao tocar
  −10/+10: seek imediato + um giro/pulso curto do ícone. Play↔Pause com
  crossfade + escala (`AnimatedContent`), sem troca seca.
- **Sem texto "+10s/−10s" no centro.** Substituído por um *ripple* discreto
  do ícone no lado tocado (duplo-toque), que some sozinho.
- **Timeline fina**: barra própria de 3dp (a `Slider` do Material trava em
  4dp), thumb de 11dp (14 ao arrastar), progresso roxo. Recuada das bordas
  (`start 20dp`), sem encostar no controle de brilho. Tempo restante/atual
  alinhado ao fim da barra, sem sobreposição.
- **Preview ao arrastar**: o frame da posição flutua acima da barra,
  acompanha o dedo sem sair da tela (clamp 10–90%), com timestamp, e some
  com fade ao soltar. Continua usando o gerador de frame sob demanda (não
  recarrega o vídeo).
- **Barra inferior** compacta (ícones 19dp, menos destaque que o transporte),
  centralizada, sem os buracos deixados por Velocidade/Proporção. Ambiente
  mantém o roxo quando ativo.
- **Brilho**: afastado do notch/punch-hole — o overlay inteiro agora aplica
  `displayCutoutPadding()`, e o slider ganhou `start 20dp` + recuo vertical,
  altura menor, sempre alcançável com uma mão.
- **Aparecer/sumir** dos controles com fade + escala suave (180/150ms); a
  ocultação automática pausa enquanto a timeline está sendo arrastada.
- Tudo com `displayCutoutPadding()` / sem coordenadas fixas → notch,
  punch-hole central, telas pequenas/grandes.

### Tela de perfil
- **Enquadramento dos slides**: passa a preferir o **pôster** (arte feita
  com o rosto no terço superior) em vez do backdrop; crop com viés pro topo
  (`BiasAlignment(0,-0.5)`) para o rosto aparecer mesmo com vários
  personagens; *ken burns* quase imperceptível em pôster (1→1.025) para não
  perder o enquadramento.
- **Bug da faixa colorida** corrigido — o `.scale()` do zoom vazava do hero
  pro painel; `clipToBounds()`.

---

## [1.17.0] — 2026-08-29

`versionCode 32` · performance + redesign da tela de perfil + guia do canal no player

### Performance (engasgos / travamentos)
- **Montagem das fileiras da Início saiu da thread principal.** O
  `GetHomeContentUseCase.build()` mapeia/filtra o catálogo inteiro
  (milhares de itens) em carrosséis, e rodava na thread que coletava —
  a principal, no pull-to-refresh. Durante o sync as `Flow`s do Room
  re-emitiam em rajada e cada emissão refazia tudo na main + recompunha
  a `LazyColumn` inteira. Agora: `.flowOn(Dispatchers.Default)` +
  `.conflate()` (junta a rajada) + `.distinctUntilChanged()` (descarta
  reconstruções idênticas).
- **`syncConnection` e os `observe*` do catálogo** ganharam `flowOn`
  (IO/Default). O mapeamento DTO→entidade de ~10k+ filmes na main era
  parte do congelamento ao "recarregar".
- **Mexer numa configuração não recompõe mais o app inteiro.** O
  `MainActivity` observava o `AppSettings` inteiro só pra pegar a cor de
  destaque — qualquer toggle (animações, qualidade, Wi-Fi…) re-emitia e
  recompunha a raiz. Agora observa só `accentColorHex` (`map` +
  `distinctUntilChanged`).
- **Cards de lista não criam mais animações ociosas em celular.** Todo
  card/linha/botão criava 2 `animateFloatAsState` (escala + anel) do
  realce de foco de TV — que **nunca** anima fora de TV. Numa grade são
  dezenas de `Animatable` vivos à toa. Novo `LocalIsTvDevice`: fora de
  TV, `tvFocusable`/`rememberTvFocusVisuals` retornam estático, sem
  animação.
- *Observação:* isto é build **debug** — o Compose em debug é 3–5× mais
  lento em recomposição. Fluidez real deve ser medida num build release.

### Redesign — "Escolha o seu perfil"
- Estava "muito feio" (um backdrop fixo + um círculo roxo gigante). Agora
  segue a referência (Netflix):
  - **Hero com arte em tela cheia** ocupando o espaço que a grade não usa
    (sem mais vão preto embaixo).
  - **Slides rotativos** de filmes **e** séries, com **crossfade** suave
    (dissolve arte + título juntos, como no vídeo) e um leve *ken burns*
    (zoom lento de 18s).
  - **Título reconstruído como logo** sobre a arte — caixa alta,
    extra-bold, tracking largo, sombra suave — já que o Xtream/TMDB só dá
    pôster/backdrop sem o letreiro embutido.
  - Slides lidam com URL malformada do provedor (`http://`,
    `image.tmdb.org//t/p`) que fazia o Coil falhar calado → hero preto.
    Backdrops (paisagem, enquadram melhor) vêm primeiro; pôster retrato
    entra alinhado ao topo.
  - Grade **3 por linha** de tiles arredondados coloridos (emoji/foto,
    nome embaixo), com selo "Infantil", cadeado e o "Adicionar" no modo
    gerenciar. Sem o contador de slides (a pedido).

### Adicionado
- **Guia do canal no player ao vivo.** Nova ação **"Programação"** na
  fileira de controles ao vivo → abre um painel lateral com a grade do
  dia daquele canal (horário • programa • sinopse), com marcador
  "AGORA". Busca via `get_short_epg` sob demanda ao abrir.

### Consistência
- **Botão voltar unificado.** Novo componente `BackButton` (disco 36dp
  `SurfaceHigh`, ícone 20dp) aplicado em Downloads, Notificações, Guia de
  programação, Editar perfil e Adicionar conexão — antes cada tela tinha
  o seu (ícone solto grudado no título, ou um círculo preto grande).
- **Ícone do launcher menor por dentro.** A marca "A" ia quase de ponta a
  ponta do círculo. Encolhida para ~1/3 do canvas, com a folga que uma
  máscara circular espera (os PNGs legados pré-API 26 continuam como
  estavam — trocar antes de publicar).

---

## [1.16.1] — 2026-08-29

`versionCode 31` · ajustes de UI a pedido (prints)

### Alterado (UI)
- **Nome não aparece mais duas vezes na Busca.** No "Recomendados para
  você" a miniatura do pôster tinha o título sobreposto (com um gradiente
  atrás) **e** o mesmo título como rótulo da linha ao lado. Removida a
  legenda de cima da miniatura — fica só o pôster limpo + o rótulo.
- **Busca de canais dentro do player.** O painel lateral "Canais" (troca
  rápida de canal no player ao vivo) ganhou um campo de busca no topo,
  logo abaixo do título, que filtra a lista pelo nome. O painel também
  passou a "engolir" os toques para não fechar sozinho ao tocar no campo.
- **"Minhas conexões": seta e barra de topo.** O círculo do botão voltar
  ainda estava no tamanho antigo (42dp, preto 45%) — igualado ao das
  Configurações (36dp, `SurfaceHigh`, ícone 20dp) com respiro até o
  título. O título virou `titleLarge` com `weight(1f)` + reticências (não
  quebra mais em duas linhas quando espremido pelos 3 ícones de ação), e
  os ícones de importar/exportar ficaram no mesmo tamanho (22dp); "+"
  segue com a cor de destaque.

### Não mexido (aguardando confirmação)
- Imagem 6 (destaque/hero da Início): tinha um retângulo vermelho mas
  nenhuma instrução escrita. Se a ideia era o mesmo "tirar o nome que
  repete a arte", dá pra fazer — mas remover o título do hero afeta
  pôsteres que **não** trazem o nome na arte, então deixei como estava.

### Nota de verificação
- Build limpo passa (Gradle 8.14.3 / JDK 21). As 4 mudanças são
  mecânicas/baixo risco (remoção de 2 composables; um `BasicTextField` +
  filtro; e ajustes de tamanho/estilo idênticos aos já validados nas
  Configurações). Não consegui confirmar print a print no emulador nesta
  rodada — a automação de toque estava caindo em elementos errados.

---

## [1.16.0] — 2026-08-29

`versionCode 30` · ajustes de UI + regressão do download

### Corrigido
- **"Bug do download" voltou — de novo.** Os três consertos das sessões
  anteriores tinham sido revertidos no repo: o manifest voltou a apontar
  para `.player.AuroraDownloadService` (classe inexistente — está em
  `.player.download`), o `AuroraDownloadService` voltou a passar `0` como
  `channelNameResourceId` (crash `Resources$NotFoundException` no
  `onCreate`), e o `strings.xml` perdeu `download_notification_channel_name`.
  Reaplicados os três. Verificado num emulador API 37: download real
  rodando (21 MB e subindo), sem crash. **Se reclamarem de download de
  novo, conferir esses três pontos primeiro.**
- **Progresso parado em "0%".** `DownloadTracker` só se atualizava nas
  *transições* de estado do Media3 (na fila → baixando → concluído),
  nunca conforme os bytes chegam. Reintroduzido o poll de 1s de
  `downloadManager.currentDownloads` enquanto há download ativo.

### Alterado (UI)
- **"Continuar assistindo" agora é pôster retrato, no estilo da
  referência (Netflix).** Era um card paisagem 16:9 com título e
  "Continuar de 4:34" embaixo e um ✕ no canto. Agora: pôster 2:3, um anel
  de play central (contorno branco), barra de progresso fina colada na
  base do pôster, e a tira ⓘ | ⋮ logo abaixo. Sem título nem legenda
  embaixo (o cabeçalho da fileira já nomeia). "Remover da fileira" saiu
  do ✕ de canto e vive no ⋮.
- **Submenus de reprodução.** "Qualidade", "Áudio preferido" e "Legenda
  preferida" nas Configurações abrem um diálogo com opções de rádio em
  vez de: ciclar cego (qualidade) ou só limpar o valor (áudio/legenda).
  Qualidade → Automática / Alta / Média / Baixa. Áudio e legenda →
  Perguntar no player / Português / Inglês / Espanhol.
- **Voltar de "Minhas conexões" leva de volta a "Configurações"**, não à
  Início. `MainShell.currentTab` virou `rememberSaveable` — abrir uma
  sub-tela das Configurações destruía a composição e o `remember` comum
  resetava a aba para HOME na volta.
- **Círculo do botão voltar nas Configurações** menor (42→36dp, mesmo
  tamanho dos chips de ícone da própria tela) e com fundo mais discreto
  (`SurfaceHigh` em vez de preto 45%), com mais respiro até o título.

### Nota de verificação
- Build limpo passa (Gradle 8.14.3 / JDK 21). Tarefas de "Minhas
  conexões → Configurações", submenus e círculo do botão voltar
  confirmadas no emulador. A troca do card de "Continuar assistindo"
  compila e é estrutural (proporção + remoção de 2 textos), mas não deu
  para tirar print ao vivo: a fileira só aparece quando há progresso de
  reprodução salvo, e não havia neste perfil durante o teste.

---

## [1.15.0] — 2026-08-28

`versionCode 29` · redesign da tela de Downloads + limpeza + performance + upgrade de toolchain

### Build / toolchain
- **Toolchain subida para acompanhar as libs (Kotlin 2.x).** As
  dependências já estavam à frente do toolchain — `media3 1.11.0` exige
  `compileSdk 36`, e o Compose novo exige Kotlin 2.x — então o build
  quebrava com 48 erros de "AAR metadata" antes de compilar qualquer
  linha. Subimos o conjunto todo, de forma coerente:
  - Gradle `8.7` → `8.14.3`
  - Android Gradle Plugin `8.5.2` → `8.12.0`
  - Kotlin `1.9.24` → `2.2.0`; KSP `1.9.24-1.0.20` → `2.2.0-2.0.2`
  - Hilt `2.51.1` → `2.57`
  - `compileSdk` e `targetSdk` `34` → `36`
  - Compose: removido o `composeOptions { kotlinCompilerExtensionVersion }`
    — desde o Kotlin 2.0 o compilador do Compose vem junto do Kotlin e é
    aplicado como plugin próprio (`org.jetbrains.kotlin.plugin.compose`,
    versionado junto do Kotlin).
  - Compose BOM `2024.06.00` → `2025.09.00`; `core-ktx` `1.13.1` →
    `1.16.0`; `lifecycle` `2.8.4` → `2.9.4`; `activity-compose` `1.9.1` →
    `1.11.0`; `navigation-compose` `2.7.7` → `2.9.5`;
    `hilt-navigation-compose`/`hilt-work` `1.2.0` → `1.3.0`; Room `2.6.1`
    → `2.7.1`; DataStore `1.1.1` → `1.1.7`; WorkManager `2.9.1` →
    `2.10.1`; Coroutines `1.8.1` → `1.10.2`; Coil `2.6.0` → `2.7.0`;
    play-services-cast `21.5.0` → `22.0.0`.
  - `targetSdk 36` (Android 16) muda comportamento de runtime — em
    especial edge-to-edge passa a ser obrigatório.

- **Verificado:** build limpo (`./gradlew clean :app:assembleDebug`) passa
  no Gradle 8.14.3 / JDK 21 e o APK instala e roda sem crash num emulador
  API 37, com Home / Ajustes / Downloads renderizando corretos em
  edge-to-edge. Wrapper (`gradlew`, `gradlew.bat`, `gradle-wrapper.jar`)
  gerado — antes o repo não tinha.

### Migrações de API exigidas pelo upgrade
- **Pull-to-refresh reescrito.** O Material3 removeu a API experimental
  antiga (`PullToRefreshContainer` + `rememberPullToRefreshState()` com
  `.isRefreshing`/`.endRefresh()`/`.nestedScrollConnection`). Home, Filmes
  e Séries passaram para o `PullToRefreshBox(isRefreshing, onRefresh)`
  novo — o estado de "atualizando" agora é do chamador (`var isRefreshing`).
- Correções pontuais que o K2 / as libs novas passaram a exigir e que
  antes não eram nem compiladas (o build parava nos 48 erros de AAR):
  `Long.dp` → `.toInt().dp` no bloco de EPG; `import` faltando de `tween`
  (PlayerScreen), `toFileSizeLabel` (SeriesDetailsScreen) e
  `lazy.items` (SeriesScreen); smart-cast do K2 em propriedade delegada
  (`state.pinError` → `?.let`); `PaddingValues(horizontal=, top=, bottom=)`
  inválido → `start/end/top/bottom` (SettingsScreen).

### Alterado
- **Tela "Downloads" agrupada por título, com capa.** Antes era uma lista
  plana onde cada episódio baixado virava uma linha solta. Agora é um
  card por filme e um card por série (modelo Netflix): a série junta
  todos os episódios baixados sob a capa dela, mostra "N episódios •
  tamanho total" e expande num toque para a lista de episódios (cada um
  com progresso, assistir e remover); ainda tem "Remover todos". Filme é
  um card único com a própria capa.
  Ponto técnico: nada disso consulta o catálogo. A `posterUrl` e as
  chaves de agrupamento (`groupKey`/`groupTitle`/`sortKey`) viajam dentro
  do próprio campo `data` do download do Media3, do mesmo jeito que o
  título e a rota de navegação já viajavam — a tela continua funcionando
  **100% offline**. `DownloadState` ganhou esses campos; downloads
  antigos (sem os campos) caem no comportamento antigo, um card por item.

### Removido
- **`DownloadExporter` (código morto).** A "pasta personalizada" e o
  "Exportar cópia" já tinham saído da UI e das configurações na 1.14.1,
  mas a classe `DownloadExporter` ficou para trás — e ainda importava
  `androidx.documentfile`, que a 1.14.1 removeu do build. Ou seja: era
  código que não compilava e não era chamado de lugar nenhum. Deletado.

### Melhorado (performance)
- **Engasgo ao trocar de aba (Início/Canais/Buscar/Ajustes).** O
  `MainShell` troca de aba com um `when(currentTab)`, então a aba que sai
  é destruída por completo — ao voltar pra Início, todas as fileiras eram
  reconstruídas do zero e o scroll pulava pro topo. Agora um
  `rememberSaveableStateHolder` guarda o estado `rememberSaveable` de cada
  aba (em especial o offset de scroll da `LazyList`) enquanto ela está
  fora de tela e restaura na volta — a troca fica barata e cai onde
  você parou. A reestruturação completa de navegação (back stack próprio
  por aba) continua em aberto; isto resolve o sintoma visível sem mexer
  na semântica de "voltar".

---

## [1.14.2] — 2026-08-28

`versionCode 28` · correção de bug real, confirmado em aparelho

### Corrigido
- **Progresso de download travado em 0% mesmo baixando de verdade.**
  Confirmado com print de um build compilado rodando (Pixel 10 API 37):
  `Download.percentDownloaded` do Media3 fica indefinido (-1) quando o
  servidor não manda o cabeçalho `Content-Length` — comum em streams de
  VOD via Xtream. O código convertia esse "não sei" silenciosamente para
  `0f`, então a UI mostrava "0%" pra sempre, independente do download
  estar progredindo. Agora `DownloadState` carrega `hasKnownPercentage` e
  `bytesDownloaded` — quando a porcentagem não existe, a UI mostra um
  spinner indeterminado (giro contínuo, sem preenchimento fixo) mais o
  tamanho já baixado ("Baixando — 142 MB"), em vez de fingir saber um
  número que não existe. Afeta os três lugares que mostram progresso:
  botão de filme, botão de próximo episódio, ícone por episódio e a tela
  de Downloads.

---

## [1.14.1] — 2026-08-28

`versionCode 27` · remoção + correção de performance

### Removido
- **Pasta personalizada para downloads e "Exportar cópia".** A pedido —
  não mudava onde o download principal ficava (sempre privado, dentro do
  app), só criava uma cópia adicional manual, e era o único código desta
  sessão inteira que não reaproveitava um padrão já testado no projeto.
  `DownloadExporter` deletado, dependência `androidx.documentfile`
  removida, toda a UI e configuração relacionadas também.

### Corrigido
- **Engasgo ao rolar a Home rápido.** `scrolledPast` (usado só para
  decidir a opacidade do fundo da barra do topo) lia
  `listState.firstVisibleItemScrollOffset` direto no corpo do
  composable, sem `derivedStateOf`. Esse valor muda a cada pixel rolado —
  sem o `derivedStateOf`, a tela **inteira** (carrossel, todas as
  fileiras de gênero) recompunha a cada pixel, não só o pedacinho que
  realmente precisava mudar. Era o único lugar do projeto com esse
  padrão.

### Encontrado, não corrigido
- **Trocar de aba (Início/Canais/Buscar/Ajustes) desmonta e remonta a
  tela inteira** — o `MainShell` usa um `when(currentTab)` em vez de
  back stack próprio por aba, então cada troca reconstrói tudo do zero
  (e a posição de scroll da Home reseta). Corrigir isso de verdade é uma
  reestruturação de navegação, não um ajuste pontual — fica como decisão
  em aberto.

---

## [1.14.0] — 2026-08-28

`versionCode 26` · funcionalidades novas

### Adicionado
- **Tela "Downloads".** Lista tudo que já foi baixado (filmes e episódios),
  com progresso animado, opção de assistir e remover. Ponto técnico
  importante: o `DownloadState` agora guarda título e informação de
  navegação decodificados do próprio campo `data` que o download do
  Media3 já carrega — a tela funciona **mesmo sem conexão ativa**, sem
  precisar consultar o catálogo, porque tudo que ela precisa já está
  salvo junto com o download em si.
- **Notificações — histórico dentro do app.** Além do aviso do sistema
  que já existia, todo "novo episódio disponível" agora também é
  registrado (`NotificationStore`, persistido) e aparece numa tela própria,
  com marcador de não lida no sininho.
- **Ícones de Downloads e Notificações** na Home, mesma posição da
  referência (Netflix): canto superior direito, ao lado da marca do app.
- **Animação circular de progresso** nos botões de download (filme e
  episódio) — em vez de um ícone estático, mostra um anel se enchendo
  conforme o download avança, igual à referência.

---

## [1.13.1] — 2026-08-28

`versionCode 25` · ajuste fino do player

### Corrigido
- **Controles centrais flutuando longe demais da barra de progresso.**
  A seção do meio usava `weight(1f)`, centralizando o cluster de
  play/pause em *todo* o espaço sobrando entre o topo e o rodapé — em
  telas onde esse espaço é grande, isso deixava um vão enorme antes da
  timeline. Agora o espaço flexível vai todo para *acima* do conjunto
  (`Spacer(weight(1f))` sozinho ali), e o cluster (brilho + controles
  centrais) tem altura fixa, ficando colado à barra de progresso e à
  fileira de ações, do jeito que a referência mostrava.
- **"Próximo episódio"** deixou de ser um botão separado flutuando acima
  da fileira de ações e passou a ser mais um item dela mesma
  ("Próximo ep."), no mesmo estilo dos outros.

### Removido
- Botões **Legendas** e **PiP** da fileira de ações do player, a pedido
  explícito — simplifica a fileira pra bater com a referência.

---

## [1.13.0] — 2026-08-28

`versionCode 24` · redesign do player

### Alterado
- **Player: estrutura de Box independentes → Column de três seções.**
  Topo (voltar/título/pular introdução), meio (brilho + controles centrais,
  ocupando o espaço restante via `weight(1f)`) e rodapé (barra de progresso
  + indicador de tempo, canal em destaque/próximo episódio, botões de
  ação). Como uma `Column` nunca deixa os filhos se sobreporem no eixo
  principal, isso **garante** por construção que o slider de brilho nunca
  encosta na barra de progresso em telas mais baixas — antes disso
  dependia de padding/altura fixos coincidirem por sorte.
- **Indicador de tempo central removido.** A barra de progresso mostrava
  três textos (posição • tempo restante • duração) — o do meio saiu.
  Agora é um único indicador, ao lado direito da própria barra (mesma
  linha, não uma linha separada abaixo), que alterna entre "-restante" e
  "decorrido" a cada toque. Preferência mantida enquanto os controles
  somem e voltam (estado hasteado na tela, não dentro do overlay).
- **Botões de ação centralizados** na parte inferior, em vez de
  empurrados pra direita com um espaçador — ainda com scroll horizontal
  de segurança em telas estreitas.
- Nenhuma lógica de reprodução, streaming, episódios, áudio, legenda,
  velocidade, PiP, bloqueio, brilho ou download foi tocada — só
  reorganização e apresentação visual, como pedido.

---

## [1.12.0] — 2026-08-28

`versionCode 23` · funcionalidade nova

### Adicionado
- **Pasta personalizada para downloads.** Configurável em Configurações →
  Dados, via seletor de pasta oficial do Android (Storage Access
  Framework). O download privado do app (Media3, isolado, não aparece na
  galeria) continua sendo a cópia principal e nunca é alterado — a pasta
  personalizada gera uma cópia *adicional*, feita lendo o mesmo cache
  de leitura já usado (e comprovado) para reprodução offline, em vez de
  reconstruir manualmente os arquivos internos do `SimpleCache` (risco
  real de corrupção que optei por não correr). Ação "Exportar cópia"
  aparece na tela de detalhes do filme só quando já baixado e a pasta
  está configurada.

---

## [1.11.0] — 2026-08-28

`versionCode 22` · correção crítica + funcionalidade nova

### Corrigido
- **Causa raiz do download não iniciar.** O vídeo enviado mostrou o botão
  "Baixar" sem nenhuma reação. Confirmado bug documentado do Media3
  (androidx/media#2614): em Android 15+, `DownloadService` com tipo
  `dataSync` pode disparar `ForegroundServiceStartNotAllowedException` ao
  tentar promover-se a foreground, matando o serviço em silêncio — sem
  crash visível na Activity, só o download nunca progredindo. O projeto
  estava em Media3 1.4.0 (2024); atualizado para **1.11.0** (atual),
  depois de confirmar que nenhuma API removida entre essas versões é usada
  no projeto.
- **Ícone: PNGs legados 100% opacos com cartão quadrado cravado.** O
  Adaptive Icon (XML, API 26+) já estava correto — foreground e background
  devidamente separados. O problema real estava nos PNGs de fallback
  (`mipmap-*/ic_launcher.png`, modo RGB sem canal alfa) usados por
  launchers/pacotes de ícone mais simples que leem a imagem direto em vez
  de resolver o Adaptive Icon. Regenerados como PNG transparente (RGBA),
  só com a marca — mesmo tratamento em todas as densidades.

### Adicionado
- **Baixar somente com Wi-Fi.** Toggle em Configurações → Dados, aplicado
  em tempo real via `DownloadManager.requirements` (mutável em runtime) e
  reaplicado a cada abertura do app a partir do valor salvo.
- **Desbloqueio de perfil por biometria.** Complementa o PIN — nunca
  substitui: só aparece quando o perfil já tem PIN configurado, e é
  sempre a ALTERNATIVA à digitação, nunca um mecanismo de trava
  independente. Usa `BiometricPrompt` oficial do Android; qualquer
  cancelamento, erro ou ausência de biometria configurada cai de volta no
  diálogo de PIN normal. `MainActivity` precisou passar de
  `ComponentActivity` para `FragmentActivity` (exigência do BiometricPrompt).
  Banco de dados de perfis: versão 3 → 4.

---

## [1.10.0] — 2026-08-28

`versionCode 21` · atualização maior

### Adicionado
- **Banner global "Sem conexão com a internet".** `NetworkMonitor` observa
  `ConnectivityManager` uma vez, no nível mais alto do app (`MainActivity`,
  acima de toda a navegação) — em vez de cada tela reagir à sua própria
  chamada que falhou, existe agora um único aviso consistente, visível em
  qualquer tela, inclusive durante reprodução.

### Alterado
- **Configurações, reformulação visual completa.** Mesma funcionalidade,
  apresentação nova: seções agrupadas em cards arredondados
  (`AuroraColors.SurfaceDark` + `AuroraRadius.Card`) em vez de uma lista
  plana; ícones em chip circular; divisores recuados a partir do ícone;
  avatar real do perfil em vez de um ícone genérico de pessoa; paleta de cor
  de destaque com seleção animada em vez de troca instantânea de tamanho;
  "Versão" e "Sobre e privacidade" deixaram de ter seta de navegação — não
  levam a lugar nenhum, então prometer isso com um chevron era o problema,
  não a falta de uma tela de destino. Largura do conteúdo limitada e
  centralizada, para não esticar sem propósito em tablet ou TV. Cada linha
  ganhou o mesmo anel de foco para D-pad que o resto do app já usa.

---

## [1.9.1] — 2026-08-28

`versionCode 20` · correção de bug

### Adicionado
- **Idioma de áudio/legenda lembrado entre vídeos.** `preferredAudioLang` e
  `preferredSubtitleLang` existiam no modelo de configurações desde antes,
  mas nada os lia nem havia UI para defini-los — todo vídeo exigia escolher
  o idioma do zero. Agora a primeira escolha manual no player (faixa de
  áudio ou legenda) fica valendo para todos os vídeos seguintes,
  automaticamente, via `TrackSelectionParameters.setPreferredAudioLanguage`/
  `setPreferredTextLanguage` do próprio ExoPlayer — não por tentar remapear
  índices de faixa entre vídeos diferentes, que não têm correspondência
  alguma de um vídeo pro outro. Visível e limpável em Configurações →
  Reprodução.

---

## [1.9.0] — 2026-08-28

`versionCode 19` · atualização maior

### Adicionado
- **PIN opcional por perfil.** Configurável no editor de perfil ("Bloquear com
  PIN"), hash SHA-256 (nunca texto puro). Ao selecionar um perfil trancado, um
  diálogo pede o PIN antes de liberar o acesso. Cadeadinho no avatar de
  perfis trancados.
- **Guia de programação (EPG completo).** Nova tela a partir de Canais: uma
  linha por canal com a faixa horizontal dos próximos programas, largura
  proporcional à duração. Carrega sob demanda conforme a linha entra em tela,
  não a lista inteira de uma vez.
- **Modo ambiente no player.** Amostra a cor dominante do frame atual a cada
  4s (reaproveitando o mesmo extrator de frame da prévia de arraste) e projeta
  um gradiente radial tingido nas bordas da tela — por cima do vídeo, não
  atrás: a view do player pinta opaco por cima de qualquer camada colocada
  atrás dela, então um brilho ali nunca apareceria.
- **Perfil infantil, filtro por lista branca.** Trocado de "esconder conteúdo
  adulto" para "mostrar somente conteúdo categorizado como infantil"
  (`KidsContentFilter`, substitui `MatureContentFilter`). Aplicado em Home,
  Filmes, Séries, Busca, Canais e no Guia de EPG. Sem categoria infantil no
  provedor, o perfil não vê nada — modo de falha seguro, intencional.
- **Download de filme, de verdade.** A tela de detalhes de filme nunca
  chamava `toggleDownload()` — o ViewModel já suportava tudo, mas não havia
  botão algum na UI. Séries já tinham; agora ambos têm o mesmo botão com
  estados Baixar/Baixando/Baixado.

### Corrigido
- **Crash ao baixar.** `PlatformScheduler` (usado por `AuroraDownloadService`)
  exige um `<service>` declarado no manifesto
  (`PlatformScheduler$PlatformSchedulerService`); sem ele, o `DownloadManager`
  crasha ao tentar agendar o job de rechecagem de rede assim que um download
  começa. Adicionado.
- **Migração de banco faltando.** `isKids` foi adicionado à entidade de perfil
  em uma sessão anterior sem nunca ganhar uma migração — quem já tinha o app
  instalado corria risco de crash/perda de dados ao abrir esta versão. Banco
  bumpado de 2 para 3, com migração cobrindo `isKids` e o novo `pinHash`
  juntos.
- **Seek bar travando.** Cada tick do arraste chamava `seekTo()` de verdade no
  player — dezenas de vezes por segundo, mais rápido do que o decoder aguenta.
  Arrastar agora só move a posição local e pede a prévia da miniatura; a busca
  real acontece uma vez, ao soltar o dedo.
- **Ano duplicado na descrição do filme.** A tela renderizava uma linha de
  texto solta ("2004 • 1h46min • Drama") e o `MetadataBadgeRow`
  ("2004 [7.3] 1h46min [HD]") ao mesmo tempo. Consolidado em uma linha, com
  gênero incluído.
- **Permissão de armazenamento em runtime.** `WRITE_EXTERNAL_STORAGE`
  (`maxSdkVersion=28`) já estava correto no manifesto, mas nunca era pedida em
  runtime — necessária em API 24-28 mesmo para a pasta privada do app.
- Slider de brilho reposicionado com `displayCutoutPadding()` — usa o inset
  real do recorte de câmera do aparelho em vez de um padding fixo chutado.

### Alterado
- Fileira de ações do player: ícones ganharam texto (Favoritar, Canais,
  Áudio, Legendas, Velocidade, Proporção, PiP, Bloquear, Ambiente), com
  scroll horizontal de segurança em telas menores.
- `tvFocusable()` (D-pad/Android TV), que tinha ficado sem uso, agora
  consolida a animação de foco compartilhada entre botões e cards — extraído
  como `rememberTvFocusVisuals()` onde havia fundo opaco entre o `scale` e a
  `border` (ordem de desenho não deixava usar o modifier bundlado direto).
- Limpeza: `.gitignore` adicionado (o zip do projeto caía de ~9,3MB para
  ~280KB sem `.gradle`/`.idea`/`build`), 4 dependências não usadas removidas
  do Gradle, 4 classes órfãs deletadas (`SearchContentUseCase`,
  `GetLiveChannelsUseCase`, `DynamicAccentController`,
  `GetContinueWatchingUseCase`), `strings.xml` de 34 entradas não usadas
  para 1.
- Pull-to-refresh estendido de Home para Filmes e Séries também.

---

## [1.8.2] — 2026-08-27

`versionCode 18` · correção de bug

### Alterado
- Aura do hero mais intensa: duas camadas empilhadas em vez de uma — um banho
  amplo e difuso (blur 80dp) que leva a cor até as bordas da tela, mais um
  núcleo menor e mais forte (blur 34dp) colado ao card. Uma camada só ou se
  espalha fina demais para ser vista, ou fica um bloco de cor com borda dura.
- A aura agora sobe **atrás da barra de status**. O recuo de status bar saiu do
  contêiner e passou para o pager, então a área da aura começa em y=0 e o brilho
  transborda por cima, enquanto o card continua abaixo do relógio.

---

## [1.8.1] — 2026-08-27

`versionCode 17` · correção de bug

### Corrigido
- **A aura do hero não aparecia.** `Modifier.blur` usa
  `BlurredEdgeTreatment.Rectangle` por padrão, que recorta o desfoque nos
  limites do layout. Como o propósito de um brilho é justamente vazar para
  fora desses limites, o efeito estava sendo cortado exatamente onde deveria
  se espalhar, resultando no fundo quase preto em volta do card. Passa a usar
  `BlurredEdgeTreatment.Unbounded`, com opacidade maior e raio de desfoque
  menor.
- Vão preto grande acima do card: `statusBarsPadding` somado a 46dp empurrava o
  carrossel para baixo. A linha da marca é um overlay transparente, então o
  recuo caiu para 20dp.
- Card do hero maior: recuo lateral do pager de 44dp para 26dp e proporção de
  0,62 para 0,68, deixando os vizinhos apenas assomando em vez de um card
  estreito no meio de um fundo preto.

---

## [1.8.0] — 2026-08-27

`versionCode 16` · atualização maior

### Adicionado
- **Aura no carrossel do hero.** O brilho ao redor do card agora é tingido pela
  cor da própria capa, extraída com `androidx.palette`. Prefere uma amostra
  vibrante à cor dominante literal: pôsteres costumam ser majoritariamente
  escuros, e a dominante de um pôster escuro é quase preta — não produziria
  brilho visível. A cor faz transição suave ao deslizar, então trocar de card
  não estala entre paletas.
- Cards vizinhos assomam nas laterais (`contentPadding` + `pageSpacing`), com
  escala e opacidade reduzidas, para o olho pousar no card ativo sem uma borda
  dura separando.
- Título, subtítulo, metadados e botões passam para dentro do card, sobre um
  scrim vertical — o terço inferior de um pôster é imprevisível, então o texto
  precisa do próprio contraste em vez de depender da arte.
- Títulos no formato "Obra: Temporada" são divididos em título e subtítulo.

### Notas de implementação
- Em Android 11 ou anterior, `Modifier.blur` não tem efeito (depende de
  `RenderEffect`, API 31+), então a aura cai para um gradiente radial. Sem esse
  desvio apareceria um retângulo de cor com borda dura em vez de brilho.
- O indicador de página ativo também adota a cor da capa.

---

## [1.7.1] — 2026-08-27

`versionCode 15` · correção de bug

### Adicionado
- Ícone do app: o "A" em gradiente ciano→azul→roxo sobre grafite quase preto,
  substituindo o placeholder vetorial. A marca é desenhada como **traço**
  (`strokeLineCap`/`strokeLineJoin` arredondados), não como triângulo
  preenchido: as hastes da referência têm espessura constante, e um
  preenchimento afina em direção ao vértice — foi o que fez as duas primeiras
  tentativas ficarem erradas. Geometria e cores medidas diretamente da arte
  de referência. Entregue como adaptive icon (fundo e
  primeiro plano separados) mais PNGs legados em cinco densidades, porque
  Android 7.0 e 7.1 antecedem o adaptive icon e ficariam sem ícone.
- Banner de Android TV (320×180) com a marca, no lugar do retângulo sólido.

### Corrigido
- `android:roundIcon` apontava para o asset quadrado, então launchers com
  máscara circular exibiam um quadrado dentro de um círculo. Agora aponta para
  `ic_launcher_round`.
- Removido `tv_banner.xml`, que passaria a colidir com o `tv_banner.png` novo
  (dois recursos de mesmo nome na mesma pasta são erro de AAPT).

### Notas de implementação
- O contorno interno do "A" é vazado com `fillType="evenOdd"` em vez de ser
  pintado na cor do fundo, para o desenho continuar correto sob qualquer
  máscara de launcher.
- O glifo fica dentro da zona segura de 66dp do adaptive icon, então nenhum
  recorte de launcher corta a letra.

---

## [1.7.0] — 2026-08-27

`versionCode 14` · atualização maior

### Alterado
- **Sinopses funcionam sem configuração.** A Wikipédia passa a ser a fonte
  padrão de metadados: sem chave, sem conta, sem cadastro. Busca no wiki em
  português e recorre ao inglês, já que muitos títulos internacionais só têm
  artigo em inglês. A chave do TMDB continua existindo como melhoria opcional
  (capas, gêneros e notas melhor estruturados) e tem prioridade quando
  preenchida — pedir que o usuário registre um token de desenvolvedor era uma
  barreira que a maioria nunca passaria.
- Hero: recuo superior reduzido a `statusBarsPadding` + 8dp. Somar o recuo da
  barra de status a uma altura de linha inteira deixava um vão grande vazio
  antes do pôster.
- Capa do hero passou a `ContentScale.Fit`, alinhando com as grades.

### Adicionado
- Efeito ambilight ao redor do card do hero: cópia ampliada e desfocada da
  própria arte sangrando por trás do pôster, então o brilho sempre carrega as
  cores do título. Em Android 11 ou anterior, onde `Modifier.blur` não tem
  efeito (depende de `RenderEffect`, API 31+), cai para um halo radial suave —
  sem esse desvio, apareceria uma cópia nítida e ampliada atrás do card.

### Corrigido
- Barra inferior ocultada na tela de Ajustes, onde cobria o campo da chave e as
  últimas linhas do formulário.

### Removido
- Filtro de canais na busca já saiu na 1.6.1; nesta versão o texto de
  Configurações foi reescrito para refletir a nova fonte padrão.

---

## [1.6.1] — 2026-08-27

`versionCode 13` · correção de bug

### Corrigido
- Placeholders desalinhados em 3 telas (busca, adicionar conexão, chave TMDB):
  um `Text` de placeholder dentro de um `Box` assume `TopStart`, então flutuava
  acima do centro do campo enquanto o campo de texto real ficava centralizado.
  Todos passam a usar `Modifier.align(Alignment.CenterStart)`.
- Rótulo dos chips de categoria ligeiramente alto dentro da pílula: passaram a
  usar `Box` com altura mínima e conteúdo centralizado, em vez de depender do
  padding do próprio `Text` — as métricas de ascendente/descendente da fonte
  não são simétricas.
- Sugestões da busca ignoravam o filtro ativo: escolher "Séries" ainda listava
  filmes. As sugestões agora saem do mesmo pool que o filtro seleciona.
- Capas cortadas nas grades e nas sugestões. A arte das playlists não é
  confiavelmente 2:3, e `ContentScale.Crop` cortava as bordas de pôsteres
  quadrados ou em paisagem. Trocado por `Fit`, e a miniatura das sugestões
  passou de moldura 16:9 para 2:3.
- Barra com o nome do app demorava a aparecer: a opacidade era ligada só a
  `firstVisibleItemIndex`, que muda apenas depois de o hero inteiro (um item
  muito alto) sair da tela. Agora reage também ao deslocamento de rolagem.

### Removido
- Filtro "Canais" da busca. A aba Canais já lista os canais com suas próprias
  categorias e EPG, e a presença deles diluía as sugestões de filmes e séries.

---

## [1.6.0] — 2026-08-26

`versionCode 12` · atualização maior

### Alterado
- Navegação inferior reestruturada: Filmes e Séries deixam de ocupar uma aba
  cada (seguem acessíveis pelos trilhos da Home e pela busca) e dão lugar a
  Buscar e Ajustes, necessárias a partir de qualquer tela. O ícone de
  configurações saiu do topo da Home.
- `HeroCarousel` perdeu o fundo desfocado e escurecido, que lia como uma
  moldura escura em volta do pôster em vez de brilho ambiente. O pôster fica
  direto sobre o fundo da página, com espaçador de status bar acima.
- Títulos dos cards passam a ocupar um slot fixo de duas linhas
  (`minLines = 2`): nomes longos não são mais cortados no meio e as linhas da
  grade deixam de ficar irregulares.
- Tela de Canais: cabeçalho vem antes do player e a prévia só aparece depois de
  um canal ser escolhido, em vez de abrir com um retângulo preto ocupando o
  topo e empurrando o título para o meio da tela.
- Lista de episódios: o download virou ícone compacto à direita da linha, no
  lugar do botão de largura total sob cada episódio, que dominava a lista e
  empurrava o episódio seguinte para fora da tela.

### Adicionado
- Tela de busca global com filtros (Tudo / Filmes / Séries / Canais) e
  recomendações derivadas dos gêneros que o perfil realmente assistiu, exibidas
  enquanto a consulta está vazia.

---

## [1.6.0] — 2026-08-26

`versionCode 12` · atualização maior

### Alterado
- Barra de navegação reestruturada para **Início · Canais · Buscar · Ajustes**.
  Filmes e Séries deixam de ser abas: seus catálogos completos passam a ser
  alcançados pelo "Ver tudo" das trilhas da Home, e a lupa e a engrenagem, que
  antes viviam no topo da Home, agora ficam disponíveis de qualquer tela.
- Ícone de configurações removido do cabeçalho da Home, que agora exibe apenas
  a marca.
- Hero em tela cheia: sem recuo lateral, sem espaçamento entre páginas e sem
  moldura arredondada — eram esses três que desenhavam a borda escura em volta
  do card. O slide também ganhou recuo superior para o pôster nunca ficar sob
  o relógio.

### Adicionado
- Tela de busca global com filtros (Tudo / Filmes / Séries / Canais). Com o
  campo vazio, lista recomendações derivadas dos gêneros que o perfil realmente
  assistiu; sem histórico, cai para as adições mais recentes.

### Corrigido
- Nomes de filmes com descendentes cortados na grade: o texto de 12sp estava em
  uma caixa de linha de 15sp, que cortava o "g" de "Zero" e "Minions" no meio
  do glifo. Caixa ampliada para 18sp.
- A aba Canais abria carregando o primeiro canal da lista automaticamente, o
  que produzia um retângulo preto com spinner acima do título. A seleção agora
  só acontece por toque.

---

## [1.5.1] — 2026-08-26

`versionCode 11` · correção de bug

### Corrigido
- `HeroCarousel` não compilava: `HorizontalPager`, `rememberPagerState` e
  `PagerState.currentPage` ainda são `@ExperimentalFoundationApi` nesta versão
  do Compose e exigem opt-in explícito. Adicionado `@file:OptIn` — aqui a forma
  do Kotlin é a correta, ao contrário do `UnstableApi` do Media3, que usa o
  marcador do AndroidX.

---

## [1.5.0] — 2026-08-26

`versionCode 10` · atualização maior

Reformulação da tela principal e dos seletores, seguindo os padrões das
referências enviadas.

### Adicionado
- `HeroCarousel`: destaque agora é um carrossel deslizável de até 5 títulos.
  Cada slide é um card de pôster arredondado sobre uma cópia desfocada e
  escurecida da própria arte — assim o fundo compartilha a paleta do título
  sem depender de um backdrop separado, que a maioria das playlists Xtream não
  fornece. Indicadores de página animam a largura do item ativo.
- `SeasonDropdown`: as temporadas passam de faixa horizontal de chips para um
  submenu único, que mostra a temporada atual, a contagem de episódios e todas
  as opções em um toque — relevante em séries com muitas temporadas, onde a
  faixa exigia rolagem lateral e não indicava quantas existiam.
- Sugestões na pesquisa por página: com o campo aberto e vazio, a tela de
  Filmes lista recomendações derivadas dos gêneros que o perfil realmente
  assistiu, em vez de uma página em branco.
- `SuggestionRow`: linha de sugestão com miniatura, título e ação de play.
- Variante somente-ícone no `GlassButton`, usada pela ação de detalhes do hero.

### Alterado
- Barra de navegação inferior virou uma pilha compacta centralizada que envolve
  os itens, em vez de um contêiner de largura total.
- `HomeContent.heroItem` deu lugar a `heroItems: List<MediaItem>`, alimentada
  por um pool de destaque, recomendados e adições recentes. O primeiro item é a
  mesma escolha da build anterior, então nada muda para quem tem apenas um
  título elegível.

### Removido
- `HeroBanner`, substituído pelo carrossel, e o helper `heroDescription`, sem
  uso desde que o hero passou a exibir tags em vez de sinopse.

---

## [1.4.2] — 2026-08-26

`versionCode 9` · correção de bug

Primeira compilação bem-sucedida do ciclo; esta versão zera os 17 avisos que ela
apontou.

### Corrigido
- `@OptIn` do Kotlin não surtia efeito sobre `UnstableApi` do Media3, que usa
  `@RequiresOptIn` do AndroidX e não o do Kotlin — o compilador avisava que a
  anotação era ignorada. Substituído por `@androidx.annotation.OptIn` nas
  declarações de `PlayerManager`, `AuroraDownloadService`, `DownloadModule` e
  `DownloadTracker` (a anotação AndroidX não aceita alvo de arquivo).
- Ícones depreciados migrados para as variantes `AutoMirrored`
  (`ArrowBack`, `VolumeUp`, `ViewList`) em 7 arquivos, que espelham
  automaticamente em layouts RTL. Os imports precisaram ser explícitos: o
  wildcard `icons.filled.*` não cobre o pacote `automirrored`.
- `isLive` era declarado em `PlayerScreenContent` mas ignorado. Agora zera a
  posição inicial em transmissões ao vivo, onde restaurar uma posição salva
  falha ou joga o espectador para trás do ponto ao vivo.

### Removido
- Variáveis mortas: `bg` em `CategoryChip` e `density` em `PlayerScreen`.

### Interno
- As cores de destaque em Configurações ganharam rótulo de acessibilidade
  (`onClickLabel`), aproveitando o nome que era desestruturado e descartado.

---

## [1.4.1] — 2026-08-26

`versionCode 8` · correção de bug

### Corrigido
- `combinedClickable` é uma extensão de `Modifier`, não uma função de nível
  superior; a chamada isolada em `Misc.kt` não resolvia. Reescrita como
  `this.combinedClickable(...)` com o import correspondente.
- `LoadingSkeleton` usava delegate `by` sem `import androidx.compose.runtime.getValue`,
  resultando em "State<Float> has no method getValue".
- `Icons.Default.MultitrackAudio` não existe no `material-icons-extended`;
  substituído por `Icons.Default.Audiotrack` no seletor de áudio do player.

### Removido
- `SearchBar` de `Misc.kt`, órfã desde a remoção da pesquisa global em 1.2.0
  (a pesquisa por página vive em `PageHeader`).

### Interno
- Conversão de cor de perfil passa a usar a extensão KTX `String.toColorInt()`
  em vez de `android.graphics.Color.parseColor`.

---

## [1.4.0] — 2026-08-26

`versionCode 7` · atualização maior

Padrões de apresentação inspirados em serviços de streaming modernos, aplicados
com a identidade própria do AuroraPlay (acento violeta, sem elementos
proprietários de terceiros).

### Adicionado
- Rodapé de ações no card de "Continuar assistindo": ⓘ detalhes e ⋮ opções,
  separados por uma hairline e anexados ao card, formando uma superfície única
  em vez de imagem solta com texto abaixo.
- `MetadataBadgeRow`: linha de metadados na tela de detalhes com ano, avaliação
  e qualidade em badges, omitindo automaticamente os campos ausentes em vez de
  deixar separadores soltos.
- `RemainingTimeRow`: barra de progresso com rótulo de tempo restante nos
  detalhes de um título parcialmente assistido.
- `DetailActionRow` e `IconTextAction`: ações em ícone sobre rótulo abaixo da
  sinopse.

### Alterado
- Ação principal da tela de detalhes passa a alternar entre "Continuar" e
  "Assistir" conforme exista progresso salvo, e ocupa a largura total.
- `MovieDetailsViewModel` expõe progresso de reprodução (fração, posição e
  tempo restante), que antes não chegava à camada de apresentação.
- Barra de progresso do card de continuar assistindo agora encosta nas bordas
  do frame (variante `rounded = false`), lendo como linha do tempo do título.

---

## [1.3.1] — 2026-08-26

`versionCode 6` · correção de bug

### Corrigido
- Compilação quebrada por uso inválido de `Modifier.padding()`: a função não
  possui sobrecarga que misture um eixo (`horizontal`/`vertical`) com uma borda
  isolada (`top`/`bottom`/`start`/`end`). Trocado pela forma explícita de quatro
  bordas em `PlayerOptionSheets.kt` (3 ocorrências) e `SettingsScreen.kt` (1).
- Import não utilizado (`foundation.background`) removido de `PlayerOptionSheets.kt`.

### Interno
- Versão exibida em Configurações passa a ler `BuildConfig.VERSION_NAME` em vez
  de uma string literal, que já estava desatualizada em "1.0.0".

---

## [1.3.0] — 2026-08-26

`versionCode 5` · atualização maior

Conclusão do redesign de UI/UX (segunda parte).

### Adicionado
- Tela unificada de perfil (`ProfileEditorScreen`) usada tanto para criar quanto
  para editar, com rota `edit_profile/{profileId}`.
- `updateProfile` / `getProfile` na cadeia de repositório de perfis, que não existiam.
- `tvFocusable()`: tratamento de foco compartilhado para navegação por D-pad,
  aplicado a cards, botões e chips de categoria.

### Alterado
- Tipografia reescrita com escala explícita (11/12/14/16/18/22/26/32/40), cada
  passo com sua própria `lineHeight`, e `LineHeightStyle` centralizado — este
  último resolve a causa raiz dos textos parecerem verticalmente desalinhados
  dentro de containers centralizados.
- Sinopse nas telas de detalhes promovida a `bodyLarge` com largura máxima de
  620dp, evitando linhas longas demais em tablet e TV.
- `SectionHeader`, carrosséis da Home e top bar alinhados ao mesmo `Spacing.gutter`.

### Corrigido
- Insets da barra de status aplicados em Detalhes de Filme, Detalhes de Série,
  Adicionar conexão, Minhas conexões e Configurações (o player permanece
  imersivo por decisão de design).
- `CategoryChip` não recebia foco por controle remoto (usava `clickable` sem
  `interactionSource`), o que fazia o foco desaparecer ao navegar pelos filtros.

### Removido
- `AddProfileScreen` (substituída pelo editor unificado) e o método morto
  `addProfile` do `ProfileViewModel`.

---

## [1.2.0] — 2026-08-26

`versionCode 4` · atualização maior

Redesign de UI/UX (primeira parte).

### Adicionado
- `PageHeader`: cabeçalho padrão com insets da barra de status e pesquisa
  contextual por página ("Pesquisar filmes...", "Pesquisar séries...",
  "Pesquisar canais...").
- Escala de espaçamento compartilhada (`Spacing`), substituindo valores ad-hoc.
- Gerenciamento de perfis: modo gerenciar, edição e exclusão com diálogo de
  confirmação nomeando o perfil.
- `SmartCategoryBuilder`: gera trilhas por gênero real e recência, substituindo
  as categorias cruas da playlist (`✅ Apple TV+`, `4k [Dual Áudio] [2]`).
- Confirmação de saída ao pressionar voltar na Home; voltar de outra aba retorna
  à Home em vez de fechar o aplicativo.
- Skeletons nas grades de Filmes e Séries e estados vazios distintos para
  "sem resultado de busca" e "categoria vazia".

### Alterado
- Barra inferior reprojetada nos princípios da One UI: contêiner flutuante,
  cápsula translúcida no item ativo, ícone e rótulo tratados como uma unidade
  centralizada, alvos de toque de 56dp.
- Home dedicada a conteúdo sob demanda; trilha de canais ao vivo removida.

### Corrigido
- Nomes de perfil verticalmente desalinhados: slot de altura fixa (20dp),
  linha única e largura fixa por card.
- Títulos de cards cortados nas grades, por gutter inconsistente.
- Itens duplicados no catálogo (`distinctBy` sobre o título normalizado) —
  provedores frequentemente listam o mesmo título sob tags de qualidade
  diferentes.

### Removido
- Aba de pesquisa global, `SearchScreen` e `SearchViewModel`, em favor da
  pesquisa contextual por página.

---

## [1.1.1] — 2026-08-26

`versionCode 3` · correção de bug

### Corrigido
- `Icons.Default.InfoOutline` não existe no `material-icons-extended`; trocado
  por `Icons.Outlined.Info` em `HeroBanner.kt`.
- Controles do player agrupados no canto superior: `AnimatedVisibility` cria um
  nó de layout que envolve o conteúdo, então os alinhamentos internos resolviam
  contra uma caixa wrap-content. Corrigido com `Modifier.fillMaxSize()`.
- `DownloadModule.kt` não compilava: falta de opt-in `@UnstableApi` (as APIs de
  download offline do Media3 são instáveis) e incompatibilidade de tipo no
  construtor do `DownloadManager`, que espera `DataSource.Factory` e recebia um
  `DefaultDownloaderFactory`.

---

## [1.1.0] — 2026-08-26

`versionCode 2` · atualização maior

Paridade de funcionalidades do player.

### Adicionado
- Seleção de faixa de áudio e legendas via `TrackSelector`, com bottom sheets.
- Velocidade de reprodução e controle de volume.
- Picture-in-Picture e reprodução em tela cheia na horizontal.
- Downloads offline (`DownloadManager` do Media3) com cache dedicado e
  `NoOpCacheEvictor`, para que nada seja removido sem ação do usuário.
- Suporte a Chromecast via `CastPlayer`, compartilhando a mesma interface
  `Player` do ExoPlayer.
- Miniaturas de pré-visualização ao arrastar a barra de progresso.
- Enriquecimento de metadados pelo TMDB quando a playlist não traz sinopse
  (requer chave gratuita, configurável em Configurações).

---

## [1.0.0] — 2026-08-20

`versionCode 1` · versão inicial

Cliente IPTV/Xtream em Kotlin + Jetpack Compose com Clean Architecture:
integração Xtream, Room, DataStore, credenciais criptografadas, Hilt,
player Media3, perfis locais, favoritos, histórico, continuar assistindo,
suporte a Android TV e tratamento de erros.
