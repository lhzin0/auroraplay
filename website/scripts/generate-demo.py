#!/usr/bin/env python3
"""Public, credential-free M3U/XMLTV test fixture. Python standard library only."""

import argparse
import html
import json
import re
import xml.etree.ElementTree as ET
from collections import Counter
from datetime import date, datetime, time, timedelta
from pathlib import Path
from zoneinfo import ZoneInfo

ROOT = Path(__file__).resolve().parents[1] / "demo"
BASE = "https://raw.githubusercontent.com/lhzin0/auroraplay/main/website/demo/"
ZONE = ZoneInfo("America/Sao_Paulo")
DISCLAIMER = (
    "PROGRAMAÇÃO FICTÍCIA PARA TESTES. Horários, partidas e equipes são simulados. "
    "O player reproduz vídeos públicos de demonstração; eles não correspondem ao programa anunciado. "
    "Não é transmissão esportiva real."
)


def clip(subject, height=720, codec="h264", container="mp4"):
    filename = {"bigbuckbunny": "Big_Buck_Bunny", "sintel": "Sintel", "jellyfish": "Jellyfish"}[subject]
    return f"https://test-videos.co.uk/vids/{subject}/{container}/{codec}/{height}/{filename}_{height}_10s_5MB.{container}"


BBB = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8"
TEARS = "https://demo.unified-streaming.com/k8s/features/stable/video/tears-of-steel/tears-of-steel.ism/.m3u8"
BIPBOP = "https://devstreaming-cdn.apple.com/videos/streaming/examples/bipbop_16x9/bipbop_16x9_variant.m3u8"
LIVE = "https://cph-p2p-msl.akamaized.net/hls/live/2000341/test/master.m3u8"
BBB_MP4 = "https://archive.org/download/BigBuckBunny_124/Content/big_buck_bunny_720p_surround.mp4"
SINTEL_MP4 = "https://archive.org/download/Sintel/sintel-2048-surround.mp4"
ED_MP4 = "https://archive.org/download/ElephantsDream/ed_hd.mp4"
TOS_MOV = "https://download.blender.org/demo/movies/ToS/tears_of_steel_720p.mov"


def entry(key, name, group, url, kind="movie", logo=None):
    return dict(id=key, name=name, group=group, url=url, kind=kind,
                logo=logo or f"https://picsum.photos/seed/{key}/400/600")


THEMES = [
    ("Aventura", "Horizonte", ["Perdido", "de Cristal", "Distante", "Secreto", "Azul", "Dourado", "da Aurora", "Infinito", "do Norte", "de Fogo", "Esquecido", "Selvagem"]),
    ("Ficção científica", "Planeta", ["Vermelho", "de Vidro", "Silencioso", "Paralelo", "Elétrico", "Oculto", "da Memória", "Infinito", "do Amanhã", "de Gelo", "Desconhecido", "Digital"]),
    ("Fantasia", "Reino", ["das Nuvens", "de Jade", "Invisível", "das Estrelas", "Encantado", "do Vento", "da Névoa", "de Prata", "das Marés", "do Tempo", "das Sombras", "Impossível"]),
    ("Suspense", "Arquivo", ["Secreto", "Noturno", "Invisível", "da Meia-Noite", "Silencioso", "Perdido", "da Janela", "do Farol", "Esquecido", "da Floresta", "da Chuva", "Final"]),
    ("Documentários", "Retratos", ["do Oceano", "do Deserto", "da Cidade", "da Montanha", "da Floresta", "do Céu", "do Inverno", "da Aurora", "do Vento", "da Terra", "do Silêncio", "do Amanhecer"]),
    ("Drama", "Cartas", ["ao Horizonte", "de Outono", "da Estação", "da Memória", "da Primavera", "do Interior", "da Praia", "da Janela", "ao Vento", "do Caminho", "de Agosto", "da Última Noite"]),
    ("Comédia", "Clube", ["dos Distraídos", "da Bagunça", "dos Vizinhos", "da Segunda-Feira", "dos Atrasados", "da Esquina", "do Improviso", "das Ideias", "do Elevador", "das Férias", "do Acaso", "dos Inventores"]),
    ("Animação", "Ateliê", ["das Cores", "de Papel", "dos Sonhos", "das Nuvens", "de Madeira", "das Estrelas", "das Formas", "de Argila", "do Arco-Íris", "das Folhas", "do Pequeno Sol", "das Aventuras"]),
    ("Natureza", "Trilhas", ["do Mar", "da Mata", "do Cerrado", "da Serra", "da Cascata", "do Bosque", "da Ilha", "do Rio", "do Lago", "da Geada", "da Gruta", "do Vale"]),
    ("Mistério", "Segredos", ["do Farol", "da Biblioteca", "da Rua Azul", "do Relógio", "do Jardim", "da Ponte", "do Mapa", "da Torre", "do Espelho", "da Estação", "do Retrato", "do Último Trem"]),
    ("Viagens", "Diário", ["da Costa", "do Interior", "de Bordo", "da Estrada", "da Montanha", "do Viajante", "do Arquipélago", "do Vale", "da Vila", "de Inverno", "da Fronteira", "do Destino"]),
    ("Curtas", "Instantes", ["de Luz", "de Chuva", "de Verão", "de Inverno", "de Cor", "de Vento", "de Areia", "de Silêncio", "da Cidade", "do Campo", "da Noite", "do Amanhecer"]),
]

STORIES = {
    "Aventura": "Uma equipe de exploradores encontra um mapa incompleto e parte em busca de uma passagem esquecida. Entre paisagens desconhecidas e escolhas difíceis, o grupo descobre que chegar ao destino exige mais confiança do que coragem.",
    "Ficção científica": "Um sinal recebido de um lugar aparentemente desabitado coloca uma pesquisadora diante de uma descoberta capaz de mudar o futuro. Enquanto tenta compreender a mensagem, ela percebe que alguém já conhece as respostas que procura.",
    "Fantasia": "Uma jovem cartógrafa encontra um mapa capaz de revelar caminhos que ninguém consegue enxergar. Para impedir o desaparecimento de seu reino, ela precisa atravessar terras esquecidas e descobrir a verdadeira origem de uma antiga magia.",
    "Suspense": "Uma gravação sem remetente reúne pessoas que acreditavam ter deixado o passado para trás. A cada nova pista, as versões se contradizem e uma decisão tomada anos antes volta a ameaçar todos os envolvidos.",
    "Documentários": "Uma jornada de observação acompanha paisagens, comunidades e pequenas transformações que costumam passar despercebidas. Histórias pessoais e descobertas do cotidiano revelam novas maneiras de olhar para o mundo.",
    "Drama": "Um reencontro inesperado obriga uma família a lidar com promessas antigas e planos interrompidos. Entre lembranças e novos começos, cada pessoa precisa decidir o que está disposta a deixar para trás.",
    "Comédia": "Uma ideia aparentemente simples reúne um grupo de pessoas que não poderia ser mais diferente. Quando o plano sai do controle, pequenos imprevistos se transformam em uma sequência de encontros improváveis e soluções nada convencionais.",
    "Animação": "Um pequeno inventor transforma objetos esquecidos em companheiros de aventura. Quando uma de suas criações ganha vida, ele embarca em uma viagem cheia de cor, descobertas e amizades para encontrar um novo lar.",
    "Natureza": "Um percurso por ambientes naturais acompanha a vida em diferentes escalas, dos detalhes quase invisíveis às grandes paisagens. Mudanças de luz, água e estações revelam as relações que mantêm cada ecossistema em movimento.",
    "Mistério": "Um objeto encontrado por acaso conecta acontecimentos que pareciam não ter relação. Para compreender a ligação entre eles, uma investigadora precisa reconstruir memórias, seguir pistas discretas e questionar sua própria versão dos fatos.",
    "Viagens": "Uma viagem sem roteiro se transforma em uma coleção de encontros, paisagens e histórias locais. A cada parada, novos caminhos revelam que conhecer um lugar também significa aprender a enxergar de outra maneira.",
    "Curtas": "Um encontro breve muda o rumo de um dia comum. Contada em pequenos gestos e detalhes, a história acompanha o instante em que uma escolha simples abre espaço para uma possibilidade inesperada.",
}
TEST_NOTE = "Sinopse fictícia para testes. O vídeo reproduzido é uma amostra de demonstração."
EPISODE_BEATS = ["uma descoberta coloca o plano inicial em dúvida", "uma nova pista aproxima o grupo de uma resposta", "um encontro inesperado revela outra versão da história", "uma escolha difícil muda o caminho dos protagonistas", "as consequências do episódio anterior exigem uma nova estratégia", "uma revelação conecta acontecimentos que pareciam isolados", "o grupo se prepara para enfrentar seu maior desafio", "a jornada chega a um ponto decisivo e abre espaço para um novo começo"]


def descriptions(rows):
    profiles_path = ROOT / "stream-tracks.json"
    profiles = json.loads(profiles_path.read_text(encoding="utf-8")) if profiles_path.exists() else {}
    originals = {
        "aurora.movie.bbb": "Um grande coelho tenta aproveitar um dia tranquilo, mas três pequenos animais transformam sua rotina em uma sequência de provocações. Quando a brincadeira vai longe demais, ele prepara uma resposta à altura. Curta de animação aberto da Blender Foundation.",
        "aurora.movie.sintel": "Uma jovem viajante atravessa territórios perigosos à procura do pequeno dragão que resgatou e criou. Sua busca a leva a enfrentar perdas e a descobrir o verdadeiro custo de uma promessa. Curta de animação aberto da Blender Foundation.",
        "aurora.movie.elephants": "Dois personagens percorrem uma estrutura mecânica enigmática guiados por visões diferentes sobre o lugar em que vivem. A jornada coloca imaginação, controle e confiança em conflito. Curta de animação aberto da Blender Foundation.",
        "aurora.movie.tears": "Em uma Amsterdã do futuro, um grupo tenta recriar acontecimentos do passado para impedir uma catástrofe. Relações humanas e máquinas se cruzam em um experimento que pode decidir o destino de todos. Curta aberto da Blender Foundation.",
    }
    collections = {
        "Coleção Blender [TESTE]": "Uma coleção de filmes abertos e amostras da Blender Foundation, organizada em temporadas para testar a navegação entre episódios, o progresso e a reprodução automática do próximo vídeo.",
        "Dez Segundos [TESTE]": "Uma série de vídeos curtos que combina animação e imagens da natureza. Os episódios rápidos permitem testar o fim da reprodução, a troca de temporada e o botão de próximo episódio sem longas esperas.",
        "Laboratório de Imagem [TESTE]": "Uma coleção comparativa de amostras em 360p, 720p e 1080p. Cada episódio ajuda a avaliar o carregamento, a nitidez e o comportamento do player em diferentes resoluções.",
        "Laboratório de Codecs [TESTE]": "Uma coleção técnica com amostras H.264, HEVC, AV1 e VP9. Os episódios permitem verificar a compatibilidade do aparelho, a decodificação e o tratamento de formatos diferentes.",
    }
    for row in rows:
        source = row["url"].rstrip("/").split("/")[-2] if row["url"].endswith("/master.m3u8") else None
        duration = profiles.get(source, {}).get("duration")
        if duration:
            row["duration_secs"] = round(duration)
        elif "_10s_" in row["url"]:
            row["duration_secs"] = 10
        if row["kind"] == "channel":
            continue
        genre = row["group"].split(" | ")[-1]
        row["genre"] = genre
        if row["kind"] == "episode":
            match = re.search(r"\sS(\d{2})E(\d{2})\b(?:\s*-\s*(.*))?", row["name"])
            series_name = row["name"][:match.start()]
            season, episode = int(match[1]), int(match[2])
            title = match[3] or f"Episódio {episode}"
            row.update(series_name=series_name, season=season, episode=episode, episode_title=title)
            clean = series_name.replace(" [TESTE]", "")
            row["series_description"] = collections.get(series_name) or f"{clean}. {STORIES.get(genre, STORIES['Aventura'])} Ao longo de três temporadas, novas pistas ampliam a jornada e as relações entre os personagens. {TEST_NOTE}"
            if series_name in collections:
                row["description"] = f"Temporada {season}, episódio {episode}: {title}. Amostra selecionada para testar a navegação, o progresso e a reprodução deste episódio. O conteúdo pertence à coleção de demonstração."
            else:
                row["description"] = f"Temporada {season}, episódio {episode}: {title}. Neste capítulo de {clean}, {EPISODE_BEATS[(episode - 1) % len(EPISODE_BEATS)]}. {TEST_NOTE}"
        elif row["id"] in originals:
            row["description"] = originals[row["id"]]
        elif row["id"].startswith("aurora.clip.") or row["id"].startswith("aurora.codec."):
            row["description"] = f"{row['name']}. Vídeo curto de demonstração para avaliar a abertura do player, a qualidade da imagem e o término da reprodução. A compatibilidade do codec depende do aparelho utilizado."
        else:
            clean = row["name"].replace(" [TESTE]", "")
            row["description"] = f"{clean}. {STORIES.get(genre, STORIES['Aventura'])} {TEST_NOTE}"
    return rows


def expanded_catalog():
    registry_path = ROOT / "stream-tracks.json"
    if not registry_path.is_file():
        raise RuntimeError("Prepare as fontes com prepare-demo-streams.py antes de ampliar o catálogo")
    registry = json.loads(registry_path.read_text(encoding="utf-8"))
    sources = [key for key, value in registry.items() if len(value.get("audio", [])) >= 2 and value.get("subtitles")]
    if not sources:
        raise ValueError("Nenhuma fonte multiáudio com legendas disponível")
    rows = []
    for group_index, (genre, noun, suffixes) in enumerate(THEMES):
        for item_index, suffix in enumerate(suffixes):
            key = f"aurora.demo.movie.{group_index:02d}.{item_index:02d}"
            source = sources[(group_index + item_index) % len(sources)]
            rows.append(entry(key, f"{noun} {suffix} [TESTE]", f"Filmes | {genre}", f"{BASE}movie/{source}/master.m3u8"))
        for variant, prefix in enumerate(["Crônicas", "Histórias", "Caminhos"]):
            show_key = f"aurora-demo-show-{group_index:02d}-{variant}"
            title = f"{prefix} de {noun} [TESTE]"
            for season in range(1, 4):
                for number in range(1, 9):
                    source = sources[(group_index + variant + season + number) % len(sources)]
                    rows.append(entry(f"{show_key}.s{season}e{number}",
                        f"{title} S{season:02d}E{number:02d} - {suffixes[(number + season) % len(suffixes)]}",
                        f"Séries | {genre}", f"{BASE}series/{source}/master.m3u8", "episode",
                        f"https://picsum.photos/seed/{show_key}/400/600"))
    channel_groups = [("Documentários", 5), ("Animação", 5), ("Aventura", 5), ("Clássicos", 4), ("Multiáudio", 6), ("Cultura", 4)]
    for group_index, (genre, count) in enumerate(channel_groups):
        for number in range(1, count + 1):
            source = sources[(group_index + number) % len(sources)]
            key = f"aurora.channel.extra.{group_index}.{number}"
            rows.append(entry(key, f"Aurora {genre} {number:02d} [TESTE]", f"Canais | {genre}",
                f"{BASE}channel/{source}/master.m3u8", "channel", f"https://picsum.photos/seed/{key}/240/240"))
    for sport, label in [("sports1", "Futebol"), ("sports2", "Basquete"), ("sports3", "Feminino")]:
        for number in range(2, 5):
            source = sources[number % len(sources)]
            key = f"aurora.channel.extra.{number}.{sport}"
            rows.append(entry(key, f"Aurora Esportes {label} {number} [TESTE]", f"Esportes | {label}",
                f"{BASE}channel/{source}/master.m3u8", "channel", f"https://picsum.photos/seed/{key}/240/240"))
    return rows


def catalog():
    # Keep the original film names/addresses: existing watch progress survives.
    rows = [
        entry("aurora.movie.bbb", "Big Buck Bunny (2008)", "Filmes de demonstração", BBB_MP4,
              logo="https://archive.org/services/img/BigBuckBunny_124"),
        entry("aurora.movie.sintel", "Sintel (2010)", "Filmes de demonstração", SINTEL_MP4,
              logo="https://archive.org/services/img/Sintel"),
        entry("aurora.movie.elephants", "Elephants Dream (2006)", "Filmes de demonstração", ED_MP4,
              logo="https://archive.org/services/img/ElephantsDream"),
        entry("aurora.movie.tears", "Tears of Steel (2012)", "Filmes de demonstração", TOS_MOV),
    ]
    for subject, title in [("bigbuckbunny", "Coelho"), ("sintel", "Dragão"), ("jellyfish", "Água-viva")]:
        for height in [360, 720, 1080]:
            rows.append(entry(f"aurora.clip.{subject}.{height}", f"{title} - Amostra H.264 {height}p (10 s)",
                              "Curtas de teste", clip(subject, height)))
    codecs = [("h265", "mp4", "HEVC"), ("av1", "mp4", "AV1"), ("vp9", "webm", "VP9")]
    for codec, container, label in codecs:
        rows.append(entry(f"aurora.codec.{codec}", f"Coelho - Amostra {label} 720p (10 s)",
                          "Compatibilidade de codecs", clip("bigbuckbunny", codec=codec, container=container)))

    shows = [
        ("blender", "Coleção Blender [TESTE]", "Séries de demonstração", [
            ("O coelho", BBB_MP4), ("O dragão", SINTEL_MP4), ("O sonho", ED_MP4),
            ("O aço", TOS_MOV), ("Coelho curto", clip("bigbuckbunny")), ("Dragão curto", clip("sintel")),
        ]),
        ("shorts", "Dez Segundos [TESTE]", "Séries curtas", [
            ("Coelho", clip("bigbuckbunny")), ("Dragão", clip("sintel")), ("Água-viva", clip("jellyfish")),
            ("Coelho Full HD", clip("bigbuckbunny", 1080)), ("Dragão Full HD", clip("sintel", 1080)),
            ("Água-viva Full HD", clip("jellyfish", 1080)),
        ]),
        ("picture", "Laboratório de Imagem [TESTE]", "Séries técnicas", [
            ("Coelho 360p", clip("bigbuckbunny", 360)), ("Coelho 720p", clip("bigbuckbunny")),
            ("Coelho 1080p", clip("bigbuckbunny", 1080)), ("Mar 360p", clip("jellyfish", 360)),
            ("Mar 720p", clip("jellyfish")), ("Mar 1080p", clip("jellyfish", 1080)),
        ]),
        ("codecs", "Laboratório de Codecs [TESTE]", "Séries técnicas", [
            ("H.264", clip("bigbuckbunny")), ("HEVC", clip("bigbuckbunny", codec="h265")),
            ("AV1", clip("bigbuckbunny", codec="av1")),
            ("VP9", clip("bigbuckbunny", codec="vp9", container="webm")),
            ("Sintel H.264", clip("sintel")), ("Água-viva H.264", clip("jellyfish")),
        ]),
    ]
    for slug, title, group, episodes in shows:
        for i, (episode_title, url) in enumerate(episodes):
            season, number = i // 3 + 1, i % 3 + 1
            rows.append(entry(f"aurora.series.{slug}.s{season}e{number}",
                              f"{title} S{season:02d}E{number:02d} - {episode_title}", group, url, "episode",
                              logo=f"https://picsum.photos/seed/aurora-series-{slug}/400/600"))

    channels = [
        ("live", "Canal de teste ao vivo", "Canais de teste", LIVE),
        ("bbb", "Big Buck Bunny HLS", "Canais de teste", BBB),
        ("bipbop", "Bip Bop", "Canais de teste", BIPBOP),
        ("tears", "Tears of Steel HLS", "Canais de teste", TEARS),
        ("sports1", "Aurora Esportes Futebol [TESTE]", "Esportes de teste", BBB),
        ("sports2", "Aurora Esportes Basquete [TESTE]", "Esportes de teste", TEARS),
        ("sports3", "Aurora Esportes Feminino [TESTE]", "Esportes de teste", BIPBOP),
        ("latency", "Aurora Baixa Latência [TESTE]", "Canais técnicos", "https://stream.mux.com/v69RSHhFelSm4701snP22dYz2jICy4E4FUyk02rW4gxRM.m3u8"),
        ("audio", "Aurora Áudios Alternativos [TESTE]", "Canais técnicos", "https://storage.googleapis.com/shaka-demo-assets/angel-one-hls/hls.m3u8"),
        ("captions", "Aurora Legendas IMSC [TESTE]", "Canais técnicos", "https://test-streams.mux.dev/tos_ismc/main.m3u8"),
    ]
    rows += [entry(f"aurora.channel.{key}", name, group, url, "channel",
                   f"https://picsum.photos/seed/aurora-channel-{key}/240/240")
             for key, name, group, url in channels]
    return descriptions(rows + expanded_catalog())


def m3u(rows):
    lines = [f'#EXTM3U url-tvg="{BASE}aurora-demo.xml"',
             "# Catálogo de demonstração. Séries são coleções de vídeos de teste.",
             "# Os canais Esportes têm programação FICTÍCIA; exibem vídeos de teste, não jogos reais.",
             "# Canais HLS sob demanda não são transmissões lineares. O guia é uma simulação de interface.",
             "# Codecs HEVC, AV1 e VP9 dependem da capacidade do aparelho."]
    for row in rows:
        attrs = {"tvg-id": row["id"], "tvg-logo": row["logo"], "group-title": row["group"]}
        if row.get("description"):
            attrs["tvg-description"] = row["description"]
        if row.get("series_description"):
            attrs["series-description"] = row["series_description"]
        attributes = " ".join(f'{key}="{html.escape(value, quote=True)}"' for key, value in attrs.items())
        lines += [f'#EXTINF:-1 {attributes},{row["name"]}', row["url"]]
    return "\n".join(lines) + "\n"


def programming(channel, start):
    slot = start.hour // 2
    if channel["id"].endswith("sports1"):
        pairs = [("Aurora FC", "Cometas FC"), ("Estrela Azul", "Atlético Lunar"),
                 ("Solar FC", "Horizonte FC"), ("Vale Verde", "União Boreal")]
        home, away = pairs[(slot + start.day) % len(pairs)]
        return f"Futebol - Liga Aurora [TESTE]: {home} x {away}", "Esporte"
    if channel["id"].endswith("sports2"):
        pairs = [("Aurora Basquete", "Cometas Basquete"), ("Solar Basquete", "Lunar Basquete")]
        home, away = pairs[(slot + start.day) % 2]
        return f"Copa Aurora Basquete [TESTE]: {home} x {away}", "Esporte"
    if channel["id"].endswith("sports3"):
        pairs = [("Aurora Feminino", "Cometas Feminino"), ("Solar Feminino", "Lunar Feminino")]
        home, away = pairs[(slot + start.day) % 2]
        return f"Futebol - Liga Feminina [TESTE]: {home} x {away}", "Esporte"
    titles = ["Abertura da programação", "Sessão animação", "Laboratório de imagem", "Curtas em sequência",
              "Faixa documental", "Teste de áudio e legenda", "Sessão aventura", "Coleção Blender",
              "Laboratório de codecs", "Sessão da noite", "Vídeos de demonstração", "Faixa noturna"]
    return f"{titles[slot]} [TESTE]", "Demonstração"


def xmltv(rows, day):
    root = ET.Element("tv", {"generator-info-name": "AuroraPlay Demo - FICTÍCIO", "source-info-name": "Dados simulados para testes"})
    channels = [row for row in rows if row["kind"] == "channel"]
    for channel in channels:
        node = ET.SubElement(root, "channel", id=channel["id"])
        ET.SubElement(node, "display-name", lang="pt").text = channel["name"]
        ET.SubElement(node, "icon", src=channel["logo"])
    first = datetime.combine(day - timedelta(days=1), time.min, ZONE)
    for channel in channels:
        for slot in range(8 * 12):
            start = first + timedelta(hours=2 * slot)
            end = start + timedelta(hours=2)
            title, category = programming(channel, start)
            node = ET.SubElement(root, "programme", {"channel": channel["id"],
                "start": start.strftime("%Y%m%d%H%M%S %z"), "stop": end.strftime("%Y%m%d%H%M%S %z")})
            ET.SubElement(node, "title", lang="pt").text = title
            ET.SubElement(node, "desc", lang="pt").text = DISCLAIMER
            ET.SubElement(node, "category", lang="pt").text = category
    ET.indent(root, space="  ")
    return ET.tostring(root, encoding="utf-8", xml_declaration=True).decode("utf-8") + "\n"


def validate(rows, playlist, guide):
    assert len(rows) == 1096 and len({row["id"] for row in rows}) == len(rows)
    assert len({row["name"] for row in rows}) == len(rows)
    assert Counter(row["kind"] for row in rows) == {"movie": 160, "episode": 888, "channel": 48}
    assert playlist.count("#EXTINF:") == len(rows)
    for row in rows:
        assert row["url"].startswith("https://")
        assert all(character not in row["name"] for character in ['\n', '\r', ','])
        if row["kind"] == "episode":
            assert re.search(r"\bS\d{2}E\d{2}\b", row["name"])
            assert row["url"].rsplit(".", 1)[-1] in {"mp4", "webm", "mov"} or "/series/" in row["url"]
            assert len(row.get("series_description", "")) >= 80
        if row["kind"] != "channel":
            assert len(row.get("description", "")) >= 80
        if row["url"].startswith(BASE) and row["url"].endswith("master.m3u8"):
            expected_kind = "series" if row["kind"] == "episode" else "movie" if row["kind"] == "movie" else "channel"
            assert f"/{expected_kind}/" in row["url"], "HLS deve ser classificado corretamente pelo app"
    assert len({row["logo"] for row in rows if row["kind"] == "movie"}) == 160
    shows = {re.split(r"\sS\d{2}E\d{2}\b", row["name"], maxsplit=1)[0] for row in rows if row["kind"] == "episode"}
    assert len(shows) == 40
    root = ET.fromstring(guide)
    expected = {row["id"] for row in rows if row["kind"] == "channel"}
    assert {node.attrib["id"] for node in root.findall("channel")} == expected
    for channel in expected:
        programmes = root.findall(f"programme[@channel='{channel}']")
        assert len(programmes) == 96
        previous = None
        for node in programmes:
            start = datetime.strptime(node.attrib["start"], "%Y%m%d%H%M%S %z")
            end = datetime.strptime(node.attrib["stop"], "%Y%m%d%H%M%S %z")
            assert end > start and (previous is None or start == previous)
            previous = end
            assert "[TESTE]" in node.findtext("title")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--date", type=date.fromisoformat, help="Data local para uma geração reproduzível (AAAA-MM-DD)")
    parser.add_argument("--check", action="store_true", help="Validar os arquivos existentes sem alterá-los")
    args = parser.parse_args()
    rows = catalog()
    if args.check:
        playlist = (ROOT / "aurora-demo.m3u").read_text(encoding="utf-8")
        guide = (ROOT / "aurora-demo.xml").read_text(encoding="utf-8")
        assert playlist == m3u(rows), "M3U não corresponde ao gerador"
    else:
        playlist = m3u(rows)
        guide = xmltv(rows, args.date or datetime.now(ZONE).date())
    validate(rows, playlist, guide)
    if not args.check:
        ROOT.mkdir(parents=True, exist_ok=True)
        (ROOT / "aurora-demo.m3u").write_text(playlist, encoding="utf-8")
        (ROOT / "aurora-demo.xml").write_text(guide, encoding="utf-8")
        (ROOT / "catalog.json").write_text(json.dumps({"schema_version": 1, "generated_date": (args.date or datetime.now(ZONE).date()).isoformat(), "entries": rows}, ensure_ascii=False, separators=(",", ":")) + "\n", encoding="utf-8")
    print("Demo válida: 160 filmes/amostras, 40 séries (888 episódios), 48 canais e 4608 programas XMLTV.")


if __name__ == "__main__":
    main()
