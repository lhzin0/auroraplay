#!/usr/bin/env python3
"""Public, credential-free M3U/XMLTV test fixture. Python standard library only."""

import argparse
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
    return rows


def m3u(rows):
    lines = [f'#EXTM3U url-tvg="{BASE}aurora-demo.xml"',
             "# Catálogo de demonstração. Séries são coleções de vídeos de teste.",
             "# Os canais Esportes têm programação FICTÍCIA; exibem vídeos de teste, não jogos reais.",
             "# Canais HLS sob demanda não são transmissões lineares. O guia é uma simulação de interface.",
             "# Codecs HEVC, AV1 e VP9 dependem da capacidade do aparelho."]
    for row in rows:
        lines += [f'#EXTINF:-1 tvg-id="{row["id"]}" tvg-logo="{row["logo"]}" group-title="{row["group"]}",{row["name"]}', row["url"]]
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
    assert len(rows) == 50 and len({row["id"] for row in rows}) == len(rows)
    assert len({row["name"] for row in rows}) == len(rows)
    assert Counter(row["kind"] for row in rows) == {"movie": 16, "episode": 24, "channel": 10}
    assert playlist.count("#EXTINF:") == len(rows)
    for row in rows:
        assert row["url"].startswith("https://")
        assert all(character not in row["name"] for character in ['\n', '\r', ','])
        if row["kind"] == "episode":
            assert re.search(r"\bS\d{2}E\d{2}\b", row["name"])
            assert row["url"].rsplit(".", 1)[-1] in {"mp4", "webm", "mov"}
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
    print("Demo válida: 16 filmes/amostras, 4 séries (24 episódios), 10 canais e 960 programas XMLTV.")


if __name__ == "__main__":
    main()
