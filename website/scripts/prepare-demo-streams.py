#!/usr/bin/env python3
"""Inspect official HLS samples and publish small masters with accurate PT-BR labels."""

import json
import math
import re
import urllib.request
from datetime import datetime, timezone
from pathlib import Path
from urllib.parse import urljoin

ROOT = Path(__file__).resolve().parents[1] / "demo"
SOURCES = {
    "elephants": "https://playertest.longtailvideo.com/adaptive/elephants_dream_v4/index.m3u8",
    "sintel": "https://cdn.bitmovin.com/content/assets/sintel/hls/playlist.m3u8",
    "bipbop": "https://devstreaming-cdn.apple.com/videos/streaming/examples/img_bipbop_adv_example_fmp4/master.m3u8",
    "angel": "https://storage.googleapis.com/shaka-demo-assets/angel-one-hls/hls.m3u8",
}
BASE = "https://raw.githubusercontent.com/lhzin0/auroraplay/main/website/demo/"
LANGUAGES = {"en": "Inglês", "eng": "Inglês", "es": "Espanhol", "spa": "Espanhol", "de": "Alemão", "deu": "Alemão", "ger": "Alemão", "fr": "Francês", "fra": "Francês", "fre": "Francês", "it": "Italiano", "ita": "Italiano", "pt": "Português", "por": "Português", "pt-br": "Português (Brasil)", "ja": "Japonês", "jpn": "Japonês", "zh": "Chinês", "zho": "Chinês", "ar": "Árabe", "ara": "Árabe", "ru": "Russo", "rus": "Russo", "nl": "Holandês", "nld": "Holandês", "und": "Idioma não informado"}
ATTR = re.compile(r'([A-Z0-9-]+)=("[^"]*"|[^,]*)')
URI = re.compile(r'URI="([^"]+)"')


def attributes(line):
    return {key: value.strip('"') for key, value in ATTR.findall(line.split(":", 1)[1])}


def fetch_text(url):
    request = urllib.request.Request(url, headers={"User-Agent": "AuroraPlay-Demo-Fixtures/1.0"})
    with urllib.request.urlopen(request, timeout=25) as response:
        body = response.read(2_000_001)
        if len(body) > 2_000_000:
            raise ValueError("Manifest is too large")
        text = body.decode("utf-8-sig")
        return text, response.geturl()


def fetch(url):
    text, final_url = fetch_text(url)
    if not text.lstrip().startswith("#EXTM3U"):
        raise ValueError("Not an HLS playlist")
    return text, final_url


def normalized_language(attrs):
    value = attrs.get("LANGUAGE", "und")
    # The official Elephants Dream fixture misspells Spanish as `sp`.
    if value == "sp" and "spanish" in attrs.get("NAME", "").lower():
        return "es"
    return value


def readable_label(attrs, codec=None):
    language = normalized_language(attrs)
    key = language.lower()
    label = LANGUAGES.get(key) or LANGUAGES.get(key.split("-")[0]) or language
    kind = attrs.get("TYPE")
    original = attrs.get("NAME", "")
    if kind == "AUDIO":
        if codec:
            label += " • " + codec
        channels = attrs.get("CHANNELS", "").split("/")[0]
        if channels in {"1", "2", "6", "8"}:
            label += {"1": " • Mono", "2": " • Estéreo", "6": " • 5.1", "8": " • 7.1"}[channels]
        if "describes-video" in attrs.get("CHARACTERISTICS", ""):
            label += " • Audiodescrição"
        if "commentary" in original.lower():
            label += " • Comentários"
    elif kind == "SUBTITLES":
        if attrs.get("FORCED") == "YES":
            label += " • Forçada"
        if "transcribes-spoken-dialog" in attrs.get("CHARACTERISTICS", ""):
            label += " • SDH"
    if language == "und" and original:
        label = original + " • Idioma não informado"
    return label.replace('"', "'")


def audio_codecs(master):
    codecs = {}
    for line in master.splitlines():
        if line.startswith("#EXT-X-STREAM-INF:"):
            attrs = attributes(line)
            value = attrs.get("CODECS", "")
            kind = "Dolby Digital Plus" if "ec-3" in value else "Dolby Digital" if "ac-3" in value else "AAC" if "mp4a.40" in value else None
            if attrs.get("AUDIO") and kind:
                codecs[attrs["AUDIO"]] = kind
    # This older master omits its audio codec from the variant CODECS field.
    if 'GROUP-ID="aac"' in master:
        codecs.setdefault("aac", "AAC")
    return codecs


def test_subtitles(key, duration, timestamp_map=None):
    # These are original test cues, explicitly not a translation of the film.
    messages = {
        "pt-BR": ("Português (Brasil) • Demonstração", "Legenda de demonstração em português. Esta faixa testa o player e não traduz os diálogos."),
        "en": ("Inglês • Demonstração", "English demonstration subtitles. This track tests the player; it does not translate the dialogue."),
        "es": ("Espanhol • Demonstração", "Subtítulos de demostración en español. Esta pista prueba el reproductor; no traduce los diálogos."),
    }
    def stamp(seconds):
        milliseconds = round(seconds * 1000)
        hours, milliseconds = divmod(milliseconds, 3_600_000)
        minutes, milliseconds = divmod(milliseconds, 60_000)
        seconds, milliseconds = divmod(milliseconds, 1000)
        return f"{hours:02d}:{minutes:02d}:{seconds:02d}.{milliseconds:03d}"
    result = []
    folder = ROOT / "subtitles" / key
    folder.mkdir(parents=True, exist_ok=True)
    for language, (label, message) in messages.items():
        cues = ["WEBVTT", ""]
        if timestamp_map:
            cues = ["WEBVTT", timestamp_map, ""]
        for index, start in enumerate(range(0, math.ceil(duration), 12)):
            end = min(start + 7, duration)
            if end <= start:
                continue
            cues += [str(index + 1), f"{stamp(start)} --> {stamp(end)}", message, ""]
        (folder / f"{language}.vtt").write_text("\n".join(cues) + "\n", encoding="utf-8")
        playlist = f"#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-TARGETDURATION:{math.ceil(duration)}\n#EXT-X-MEDIA-SEQUENCE:0\n#EXT-X-PLAYLIST-TYPE:VOD\n#EXTINF:{duration:.3f},\n{language}.vtt\n#EXT-X-ENDLIST\n"
        (folder / f"{language}.m3u8").write_text(playlist, encoding="utf-8")
        result.append(dict(language=language, label=label, uri=f"{BASE}subtitles/{key}/{language}.m3u8", generated=True))
    return result


def prepare(key, source):
    original, origin = fetch(source)
    if "#EXT-X-STREAM-INF:" not in original or "#EXT-X-SESSION-KEY:" in original:
        raise ValueError("Expected a clear master playlist")
    output, tracks = [], []
    children = {}
    codecs = audio_codecs(original)
    duration = None
    for line in original.splitlines():
        if not line:
            continue
        if line.startswith("#EXT-X-MEDIA:"):
            attrs = attributes(line)
            if attrs.get("TYPE") in {"AUDIO", "SUBTITLES"} and attrs.get("URI"):
                child, _ = fetch(urljoin(origin, attrs["URI"]))
                children[attrs["URI"]] = child
                if attrs.get("TYPE") == "AUDIO" and duration is None:
                    duration = sum(float(match) for match in re.findall(r"#EXTINF:([\d.]+)", child))
                label = readable_label(attrs, codecs.get(attrs.get("GROUP-ID")))
                line = re.sub(r'(?<=,)NAME="[^"]*"', lambda _: 'NAME="' + label + '"', line)
                language = normalized_language(attrs)
                if language != attrs.get("LANGUAGE"):
                    line = re.sub(r'LANGUAGE="[^"]*"', lambda _: 'LANGUAGE="' + language + '"', line)
                tracks.append({"type": attrs["TYPE"], "language": language, "label": label,
                               "group": attrs.get("GROUP-ID"), "channels": attrs.get("CHANNELS"),
                               "source_name": attrs.get("NAME"), "uri": urljoin(origin, attrs["URI"])})
        if line.startswith("#"):
            line = URI.sub(lambda match: 'URI="' + urljoin(origin, match[1]) + '"', line)
        else:
            line = urljoin(origin, line)
        output.append(line)
    audio = [track for track in tracks if track["type"] == "AUDIO"]
    subtitles = [track for track in tracks if track["type"] == "SUBTITLES"]
    if len(audio) < 2 or not subtitles:
        raise ValueError("Source must expose actual alternate audio and subtitle renditions")
    if not duration or duration <= 0:
        raise ValueError("Expected a finite VOD audio timeline")
    timestamp_map = None
    first_subtitle = subtitles[0]
    child = children.get(next(attrs["URI"] for line in original.splitlines() if line.startswith("#EXT-X-MEDIA:") and (attrs := attributes(line)).get("TYPE") == "SUBTITLES" and attrs.get("URI")), "")
    segment = next((line.strip() for line in child.splitlines() if line.strip() and not line.startswith("#")), None)
    if segment:
        try:
            vtt, _ = fetch_text(urljoin(first_subtitle["uri"], segment))
            timestamp_map = next((line for line in vtt.splitlines() if line.startswith("X-TIMESTAMP-MAP=")), None)
        except Exception:
            pass
    generated = test_subtitles(key, duration, timestamp_map)
    for group in dict.fromkeys(track["group"] for track in subtitles):
        for track in generated:
            auto = "NO" if any(value["language"] == track["language"] for value in subtitles if value["group"] == group) else "YES"
            output.insert(2, f'#EXT-X-MEDIA:TYPE=SUBTITLES,GROUP-ID="{group}",NAME="{track["label"]}",LANGUAGE="{track["language"]}",DEFAULT=NO,AUTOSELECT={auto},FORCED=NO,URI="{track["uri"]}"')
            tracks.append(dict(type="SUBTITLES", group=group, **track))
    identities = [(track["type"], track["group"], track["label"]) for track in tracks]
    if len(identities) != len(set(identities)):
        raise ValueError("Duplicate rendition names in the same HLS group")
    content = "\n".join(output) + "\n"
    for kind in ["movie", "series", "channel"]:
        path = ROOT / kind / key / "master.m3u8"
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content, encoding="utf-8")
    source_path = ROOT / "sources" / (key + ".m3u8")
    source_path.parent.mkdir(parents=True, exist_ok=True)
    source_path.write_text(original, encoding="utf-8")
    return {"source": source, "checked_at": datetime.now(timezone.utc).isoformat(), "duration": duration,
            "audio": audio, "subtitles": [track for track in tracks if track["type"] == "SUBTITLES"]}


def main():
    registry_path = ROOT / "stream-tracks.json"
    registry = json.loads(registry_path.read_text()) if registry_path.exists() else {}
    successes = 0
    for key, source in SOURCES.items():
        try:
            registry[key] = prepare(key, source)
            successes += 1
            print(f"{key}: {len(registry[key]['audio'])} áudios, {len(registry[key]['subtitles'])} legendas verificadas")
        except Exception as error:
            print(f"{key}: fonte não atualizada ({type(error).__name__}: {error}); preservando versão anterior, se existir")
    if not registry:
        raise RuntimeError("No alternate-audio source could be verified in this run")
    if not successes:
        print("Nenhuma fonte respondeu nesta execução; usando os últimos masters verificados para manter o guia disponível.")
    registry_path.write_text(json.dumps(registry, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
