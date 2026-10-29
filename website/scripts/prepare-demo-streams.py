#!/usr/bin/env python3
"""Inspect official HLS samples and publish small masters with accurate PT-BR labels."""

import json
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
}
LANGUAGES = {"en": "Inglês", "eng": "Inglês", "es": "Espanhol", "spa": "Espanhol", "de": "Alemão", "deu": "Alemão", "ger": "Alemão", "fr": "Francês", "fra": "Francês", "fre": "Francês", "it": "Italiano", "ita": "Italiano", "pt": "Português", "por": "Português", "pt-br": "Português (Brasil)", "ja": "Japonês", "jpn": "Japonês", "zh": "Chinês", "zho": "Chinês", "ar": "Árabe", "ara": "Árabe", "ru": "Russo", "rus": "Russo", "nl": "Holandês", "nld": "Holandês", "und": "Idioma não informado"}
ATTR = re.compile(r'([A-Z0-9-]+)=("[^"]*"|[^,]*)')
URI = re.compile(r'URI="([^"]+)"')


def attributes(line):
    return {key: value.strip('"') for key, value in ATTR.findall(line.split(":", 1)[1])}


def fetch(url):
    request = urllib.request.Request(url, headers={"User-Agent": "AuroraPlay-Demo-Fixtures/1.0"})
    with urllib.request.urlopen(request, timeout=25) as response:
        body = response.read(2_000_001)
        if len(body) > 2_000_000:
            raise ValueError("Manifest is too large")
        text = body.decode("utf-8-sig")
        if not text.lstrip().startswith("#EXTM3U"):
            raise ValueError("Not an HLS playlist")
        return text, response.geturl()


def readable_label(attrs):
    language = attrs.get("LANGUAGE", "und")
    key = language.lower()
    label = LANGUAGES.get(key) or LANGUAGES.get(key.split("-")[0]) or language
    kind = attrs.get("TYPE")
    original = attrs.get("NAME", "")
    if kind == "AUDIO":
        channels = attrs.get("CHANNELS", "").split("/")[0]
        if channels in {"1", "2", "6", "8"}:
            label += {"1": " • Mono", "2": " • Estéreo", "6": " • 5.1", "8": " • 7.1"}[channels]
        if "describes-video" in attrs.get("CHARACTERISTICS", ""):
            label += " • Audiodescrição"
    elif kind == "SUBTITLES":
        if attrs.get("FORCED") == "YES":
            label += " • Forçada"
        if "transcribes-spoken-dialog" in attrs.get("CHARACTERISTICS", ""):
            label += " • SDH"
    if language == "und" and original:
        label = original + " • Idioma não informado"
    return label.replace('"', "'")


def prepare(key, source):
    original, origin = fetch(source)
    if "#EXT-X-STREAM-INF:" not in original or "#EXT-X-SESSION-KEY:" in original:
        raise ValueError("Expected a clear master playlist")
    output, tracks = [], []
    children = {}
    for line in original.splitlines():
        if not line:
            continue
        if line.startswith("#EXT-X-MEDIA:"):
            attrs = attributes(line)
            if attrs.get("TYPE") in {"AUDIO", "SUBTITLES"} and attrs.get("URI"):
                child, _ = fetch(urljoin(origin, attrs["URI"]))
                children[attrs["URI"]] = child
                label = readable_label(attrs)
                line = re.sub(r'(?<=,)NAME="[^"]*"', lambda _: 'NAME="' + label + '"', line)
                tracks.append({"type": attrs["TYPE"], "language": attrs.get("LANGUAGE", "und"), "label": label,
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
    content = "\n".join(output) + "\n"
    for kind in ["movie", "series"]:
        path = ROOT / kind / key / "master.m3u8"
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content, encoding="utf-8")
    source_path = ROOT / "sources" / (key + ".m3u8")
    source_path.parent.mkdir(parents=True, exist_ok=True)
    source_path.write_text(original, encoding="utf-8")
    return {"source": source, "checked_at": datetime.now(timezone.utc).isoformat(), "audio": audio, "subtitles": subtitles}


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
    if not registry or not successes:
        raise RuntimeError("No alternate-audio source could be verified in this run")
    registry_path.write_text(json.dumps(registry, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
