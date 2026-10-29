# AuroraPlay — catálogo público para testes

**M3U:** https://raw.githubusercontent.com/lhzin0/auroraplay/main/website/demo/aurora-demo.m3u

**XMLTV:** https://raw.githubusercontent.com/lhzin0/auroraplay/main/website/demo/aurora-demo.xml

Atualize a conexão e o guia no aplicativo. O cabeçalho da M3U inclui url-tvg.
Se a conexão antiga não importar o guia, informe a URL XMLTV nas suas configurações.

## Conteúdo

- **160 filmes/amostras**, em doze categorias, com endereços de capas distintos.
- **40 séries de teste e 888 episódios**. As 36 séries novas têm três temporadas
  de oito episódios; as quatro coleções originais foram preservadas.
- **48 canais de demonstração**, incluindo futebol, basquete, futebol feminino,
  animação, documentários, cultura, aventura, multiáudio e compatibilidade.
- **4.608 programas XMLTV**, cobrindo o dia anterior e sete dias a partir da geração,
  sem lacunas ou sobreposição por canal e com o fuso de São Paulo explícito.

Títulos novos e programação são fictícios e trazem [TESTE]. Vídeos são reutilizados
intencionalmente; as capas são ilustrativas. Os programas do guia não correspondem
ao vídeo reproduzido. Os canais de esportes **não transmitem partidas reais**.

## Áudios e legendas

Os novos filmes e episódios apontam para masters HLS com **faixas reais**, verificadas
nas amostras oficiais. movie/ e series/ no endereço permitem ao AuroraPlay separar
filmes, episódios e canais corretamente, mesmo quando todos usam HLS.

- Idiomas são lidos do manifesto da fonte, sem inventar dublagens.
- A fonte Elephants Dream tem inglês, espanhol e comentários em inglês.
  O código incorreto sp da amostra é normalizado para es.
- Faixas de comentários recebem Comentários; som multicanal distingue AAC,
  Dolby Digital e Dolby Digital Plus, além de estéreo e 5.1 quando a fonte informa.
- Legendas originais são preservadas com idioma e tipo corretos.
- Também há legendas **de demonstração** em português do Brasil, inglês e espanhol,
  com texto criado para testar o player. Elas não são tradução dos diálogos e são
  identificadas como Demonstração no seletor. O mapeamento de timestamps da fonte
  é preservado quando disponível.
- O BipBop é um sinal técnico; suas faixas não devem ser confundidas com dublagens.

As faixas disponíveis e a data da última verificação estão em
[stream-tracks.json](stream-tracks.json). Os hosts externos podem ficar indisponíveis.
Os masters mantêm links absolutos para as mídias das fontes; somente manifests,
legendas de teste e dados de catálogo são hospedados neste repositório.

## Esportes e disponibilidade

Os jogos do guia exercitam a página “Jogos de hoje”. A M3U/XMLTV não fornece
classificação ou chaveamento. O suporte aos campeonatos fictícios dos times Aurora,
Cometas, Solar e Lunar requer a atualização correspondente do app. As ferramentas
de debug oferecem estados controlados de jogo e testes de chaveamentos.

O workflow refresh-demo.yml tenta atualizar fontes e datas quatro vezes por dia.
Quando uma fonte falha, sua última configuração verificada é preservada. O link
canônico lê a branch main; a cópia no site depende do deploy do site. GitHub pode
atrasar execuções ou desativar agendas de repositórios inativos. Não há garantia de
disponibilidade permanente dos vídeos externos.

## Fontes e créditos

- Filmes abertos: Blender Foundation, Big Buck Bunny (CC BY 3.0), Sintel (CC BY 3.0),
  Elephants Dream (CC BY 2.5) e Tears of Steel (CC BY 3.0).
- https://peach.blender.org/about/ · https://durian.blender.org/about/
- https://orange.blender.org/background/ · https://mango.blender.org/sharing/
- Amostras de codecs: https://test-videos.co.uk/
- Mux: https://test-streams.mux.dev/
- Apple: https://developer.apple.com/streaming/examples/
- Bitmovin: https://bitmovin.com/demos/multi-audio-tracks/
- Amostras oficiais do hls.js: https://github.com/video-dev/hls.js/blob/master/tests/test-streams.js
- Amostras oficiais Shaka: https://github.com/shaka-project/shaka-player/blob/main/demo/common/assets.js
- Fotos ilustrativas: https://picsum.photos/

Fontes de terceiros permanecem sujeitas aos direitos dos respectivos titulares.
O catálogo se destina exclusivamente a testar a interface e o player.

Validação local: python3 website/scripts/generate-demo.py --check e
python3 -m unittest discover -s website/scripts -p test_demo_streams.py.
