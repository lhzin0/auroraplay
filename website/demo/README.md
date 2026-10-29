# Catálogo público de demonstração do AuroraPlay

Adicione esta URL como uma playlist **M3U** no aplicativo, sem usuário ou senha:

https://raw.githubusercontent.com/lhzin0/auroraplay/main/website/demo/aurora-demo.m3u

O cabeçalho da lista aponta automaticamente para o guia **XMLTV**:

https://raw.githubusercontent.com/lhzin0/auroraplay/main/website/demo/aurora-demo.xml

Se a conexão já existia antes de o guia ser adicionado, atualize a playlist e o
guia. Se necessário, informe a URL do XMLTV nas configurações dessa conexão.

## O que testar

- 16 filmes/amostras: os quatro filmes originais e clipes curtos H.264,
  HEVC, AV1 e VP9, em resoluções diferentes.
- 4 séries de demonstração, cada uma com 2 temporadas e 3 episódios por
  temporada: catálogo, seleção de temporada, progresso e próximo episódio.
- 10 canais de demonstração: HLS adaptativo, baixa latência, áudios
  alternativos, legendas IMSC e três canais esportivos fictícios.
- 960 programas XMLTV: um dia anterior e sete dias a partir do dia de geração,
  com horários explícitos no fuso de São Paulo, sem sobreposição ou lacunas.
- Partidas fictícias de futebol, basquete e futebol feminino, para a
  identificação de jogos no guia e a página “Jogos de hoje”.

**Todo o guia é fictício. Os vídeos exibidos não correspondem à programação
anunciada. Os canais esportivos não transmitem partidas reais.** As séries são
coleções de amostras reutilizadas para exercitar o aplicativo, não produções
novas. O guia sozinho não confirma placares nem o estado “ao vivo” de um jogo.
Classificação, chaveamento e placares simulados completos são ferramentas do
build de debug do aplicativo, separadas deste arquivo público.

O workflow `refresh-demo.yml` tenta renovar as datas quatro vezes por dia e
publica alterações quando o dia muda. Também permite atualização manual.
O link canônico acima lê diretamente a branch `main`; a cópia no site depende
da publicação do site. O GitHub pode atrasar ou desabilitar execuções agendadas
em repositórios inativos. A playlist é pública enquanto o repositório e o
serviço estiverem disponíveis; os vídeos e imagens dependem de hosts externos.

## Fontes e créditos

- Big Buck Bunny (2008), Blender Foundation / Peach, CC BY 3.0:
  https://peach.blender.org/about/
- Sintel (2010), Blender Foundation / Durian, CC BY 3.0:
  https://durian.blender.org/about/
- Elephants Dream (2006), Blender Foundation / Orange, CC BY 2.5:
  https://orange.blender.org/background/
- Tears of Steel (2012), Blender Foundation / Mango, CC BY 3.0:
  https://mango.blender.org/sharing/
- Amostras e variantes de codec: https://test-videos.co.uk/
- HLS da Mux: https://test-streams.mux.dev/
- HLS da Apple: https://developer.apple.com/streaming/examples/
- HLS da Unified Streaming: https://demo.unified-streaming.com/
- Amostras de áudio e baixa latência também usadas nos testes oficiais do hls.js:
  https://github.com/video-dev/hls.js/blob/master/tests/test-streams.js
- Imagens ilustrativas de demonstração: https://picsum.photos/

Nenhuma mídia é copiada para este repositório; os arquivos apenas referenciam
as fontes. Disponibilidade e compatibilidade dos codecs dependem da fonte e do
aparelho. Para validar novamente a estrutura local: `python3
website/scripts/generate-demo.py --check`.
