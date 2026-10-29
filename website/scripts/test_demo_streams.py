import importlib.util
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

HERE = Path(__file__).resolve().parent
SPEC = importlib.util.spec_from_file_location("demo_streams", HERE / "prepare-demo-streams.py")
STREAMS = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(STREAMS)


class StreamMetadataTest(unittest.TestCase):
    def test_spanish_typo_and_commentary_remain_distinct(self):
        spanish = {"TYPE": "AUDIO", "LANGUAGE": "sp", "NAME": "Spanish"}
        self.assertEqual("es", STREAMS.normalized_language(spanish))
        self.assertEqual("Espanhol • AAC", STREAMS.readable_label(spanish, "AAC"))
        commentary = {"TYPE": "AUDIO", "LANGUAGE": "en", "NAME": "Commentary (eng)"}
        self.assertEqual("Inglês • AAC • Comentários", STREAMS.readable_label(commentary, "AAC"))
        self.assertNotEqual(STREAMS.readable_label(commentary), STREAMS.readable_label({**commentary, "NAME": "English"}))

    def test_verified_master_is_rebased_and_uses_genuine_renditions(self):
        source = STREAMS.SOURCES["elephants"]
        original = (HERE.parent / "demo/sources/elephants.m3u8").read_text()
        def fetch(url):
            return (original if url == source else "#EXTM3U\n#EXTINF:30,\nmedia.ts\n#EXT-X-ENDLIST\n", url)
        with tempfile.TemporaryDirectory() as folder, patch.object(STREAMS, "ROOT", Path(folder)), patch.object(STREAMS, "fetch", fetch), patch.object(STREAMS, "fetch_text", return_value=("WEBVTT\nX-TIMESTAMP-MAP=LOCAL:00:00:00.000,MPEGTS:900000\n", source)):
            info = STREAMS.prepare("elephants", source)
            self.assertEqual(3, len(info["audio"]))
            self.assertEqual({"en", "es"}, {track["language"] for track in info["audio"]})
            self.assertEqual(5, len(info["subtitles"]))
            master = (Path(folder) / "movie/elephants/master.m3u8").read_text()
            self.assertIn('LANGUAGE="es",NAME="Espanhol • AAC"', master)
            self.assertNotIn('LANGUAGE="sp"', master)
            self.assertIn('LANGUAGE="pt-BR"', master)
            self.assertIn("b160000-commentary.m3u8", master)
            names = [(track["group"], track["label"]) for track in info["audio"]]
            self.assertEqual(len(names), len(set(names)))
            self.assertEqual(master, (Path(folder) / "series/elephants/master.m3u8").read_text())
            self.assertEqual(master, (Path(folder) / "channel/elephants/master.m3u8").read_text())
            vtt = (Path(folder) / "subtitles/elephants/pt-BR.vtt").read_text()
            self.assertIn("não traduz os diálogos", vtt)
            self.assertIn("MPEGTS:900000", vtt)

    def test_surround_names_preserve_codec_and_channel_layout(self):
        original = (HERE.parent / "demo/sources/bipbop.m3u8").read_text()
        codecs = STREAMS.audio_codecs(original)
        self.assertEqual("AAC", codecs["aud1"])
        self.assertEqual("Dolby Digital", codecs["aud2"])
        self.assertEqual("Dolby Digital Plus", codecs["aud3"])
        attrs = {"TYPE": "AUDIO", "LANGUAGE": "en", "NAME": "English", "CHANNELS": "6"}
        self.assertEqual("Inglês • Dolby Digital • 5.1", STREAMS.readable_label(attrs, codecs["aud2"]))


if __name__ == "__main__":
    unittest.main()
