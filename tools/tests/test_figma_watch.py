"""tools/figma_watch.py 단위 테스트 (네트워크 없음).

실행: python3 -m unittest discover -s tools/tests -v
"""
import copy
import json
import sys
import tempfile
import unittest
from pathlib import Path
from unittest import mock

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
import figma_watch as fw  # noqa: E402


def _box(x, y, w, h):
    return {"x": x, "y": y, "width": w, "height": h}


def make_section():
    """섹션 > H01 화면(텍스트·버튼) + C05 화면 + 노트, Figma REST 응답 모양."""
    return {
        "id": "351:2611", "name": fw.SECTION_NAME, "type": "SECTION",
        "absoluteBoundingBox": _box(0, 0, 5000, 3000),
        "children": [
            {
                "id": "352:2601", "name": "H01 홈", "type": "FRAME",
                "absoluteBoundingBox": _box(100, 200, 1920, 1080),
                "fills": [{"type": "SOLID", "color": {"r": 1, "g": 1, "b": 1, "a": 1}}],
                "children": [
                    {"id": "352:2700", "name": "제목", "type": "TEXT", "characters": "오늘의 추천 코스",
                     "absoluteBoundingBox": _box(124, 224, 300, 40),
                     "style": {"fontFamily": "Asta Sans", "fontWeight": 700, "fontSize": 32, "lineHeightPx": 40},
                     "fills": [{"type": "SOLID", "color": {"r": 0.0667, "g": 0.0667, "b": 0.0667, "a": 1}}]},
                    {"id": "352:2701", "name": "버튼", "type": "FRAME", "cornerRadius": 16,
                     "absoluteBoundingBox": _box(124, 300, 392, 92), "layoutMode": "HORIZONTAL", "itemSpacing": 8,
                     "paddingTop": 12, "paddingRight": 16, "paddingBottom": 12, "paddingLeft": 16,
                     "fills": [{"type": "GRADIENT_LINEAR", "gradientStops": [
                         {"color": {"r": 0.1216, "g": 0.502, "b": 1, "a": 1}},
                         {"color": {"r": 0.5412, "g": 0.1176, "b": 0.902, "a": 1}}]}]},
                ],
            },
            {
                "id": "355:4702", "name": "C05 케어", "type": "FRAME",
                "absoluteBoundingBox": _box(2200, 200, 1920, 1080),
                "children": [
                    {"id": "355:4710", "name": "남은 시간", "type": "TEXT", "characters": "15분 후 수유",
                     "absoluteBoundingBox": _box(2224, 224, 200, 30),
                     "style": {"fontFamily": "Asta Sans", "fontWeight": 600, "fontSize": 24}},
                ],
            },
            {"id": "357:1716", "name": "노트", "type": "TEXT", "characters": "메모",
             "absoluteBoundingBox": _box(100, 50, 200, 20), "style": {}},
        ],
    }


class NormalizeTest(unittest.TestCase):
    def test_records_text_size_color_and_relative_position(self):
        snap = fw.normalize(make_section())
        title = snap["nodes"]["352:2700"]
        self.assertEqual(title["text"], "오늘의 추천 코스")
        self.assertEqual(title["font"], "Asta Sans/700/32/40")
        self.assertEqual(title["fill"], ["#111111"])
        self.assertEqual((title["x"], title["y"], title["w"], title["h"]), (24, 24, 300, 40))
        self.assertEqual(title["screen"], "352:2601")
        btn = snap["nodes"]["352:2701"]
        self.assertEqual(btn["fill"], ["linear(#1F80FF>#8A1EE6)"])
        self.assertEqual(btn["layout"], "horizontal gap8 pad12/16/12/16")
        self.assertEqual(btn["radius"], 16)

    def test_screens_get_codes_from_known_ids(self):
        snap = fw.normalize(make_section())
        self.assertEqual(snap["screens"]["352:2601"]["code"], "H01")
        self.assertEqual(snap["screens"]["355:4702"]["code"], "C05")
        self.assertEqual(snap["screens"]["357:1716"]["code"], "")

    def test_semi_transparent_color_and_hidden(self):
        sec = make_section()
        t = sec["children"][1]["children"][0]
        t["fills"] = [{"type": "SOLID", "opacity": 0.5, "color": {"r": 0, "g": 0, "b": 0, "a": 1}}]
        t["visible"] = False
        rec = fw.normalize(sec)["nodes"]["355:4710"]
        self.assertEqual(rec["fill"], ["#000000@0.5"])
        self.assertTrue(rec["hidden"])

    def test_dump_and_load_roundtrip_one_node_per_line(self):
        snap = fw.normalize(make_section())
        text = fw.dump_snapshot(snap, {"version": "1"})
        node_lines = [l for l in text.splitlines() if l.startswith('"35')]
        self.assertEqual(len(node_lines), len(snap["nodes"]))
        with tempfile.TemporaryDirectory() as d:
            p = Path(d) / "s.json"
            p.write_text(text, encoding="utf-8")
            back = fw.load_snapshot(p)
        self.assertEqual(back["nodes"], json.loads(json.dumps(snap["nodes"])))
        self.assertEqual(back["meta"], {"version": "1"})


class DiffTest(unittest.TestCase):
    def setUp(self):
        self.old_sec = make_section()
        self.old = fw.normalize(self.old_sec)

    def _new(self, mutate):
        sec = copy.deepcopy(self.old_sec)
        mutate(sec)
        return fw.normalize(sec)

    def test_no_change(self):
        d = fw.diff(self.old, fw.normalize(copy.deepcopy(self.old_sec)))
        self.assertEqual(d["groups"], {})
        self.assertEqual(d["counts"], {"added": 0, "removed": 0, "changed": 0})

    def test_text_change_grouped_by_screen_and_routed_to_care(self):
        new = self._new(lambda s: s["children"][1]["children"][0].update(characters="10분 후 수유"))
        d = fw.diff(self.old, new)
        self.assertEqual(list(d["groups"]), ["355:4702"])
        line = d["groups"]["355:4702"][0]
        self.assertIn("문구 15분 후 수유 → 10분 후 수유", line)
        self.assertEqual(fw.sessions_for(d["groups"], d["colors"]), {"care-c01": ["C05"]})

    def test_added_removed_and_size_change(self):
        def mutate(s):
            home = s["children"][0]["children"]
            home[1]["absoluteBoundingBox"]["width"] = 400
            home.append({"id": "352:2800", "name": "새 칩", "type": "TEXT", "characters": "비 와요",
                         "absoluteBoundingBox": _box(124, 400, 80, 20), "style": {}})
            s["children"][1]["children"].clear()
        d = fw.diff(self.old, self._new(mutate))
        self.assertEqual(d["counts"], {"added": 1, "removed": 1, "changed": 1})
        home = "\n".join(d["groups"]["352:2601"])
        self.assertIn("추가: TEXT `새 칩` '비 와요'", home)
        self.assertIn("너비 392 → 400", home)
        self.assertIn("삭제: TEXT `남은 시간`", "\n".join(d["groups"]["355:4702"]))

    def test_new_color_goes_to_every_session(self):
        def mutate(s):
            s["children"][0]["fills"][0]["color"] = {"r": 0.302, "g": 0.333, "b": 0.941, "a": 1}
        d = fw.diff(self.old, self._new(mutate))
        self.assertEqual(d["colors"]["added"], ["#4D55F0"])
        self.assertEqual(d["colors"]["removed"], ["#FFFFFF"])
        sess = fw.sessions_for(d["groups"], d["colors"])
        self.assertEqual(sess["home-h01-h02"], ["H01", "공통"])
        self.assertEqual(sess["drive-voice-d01"], ["공통"])

    def test_whole_screen_added_reported_once(self):
        def mutate(s):
            s["children"].append({"id": "356:6661", "name": "D01 주행", "type": "FRAME",
                                  "absoluteBoundingBox": _box(0, 2000, 1920, 1080),
                                  "children": [{"id": "356:6700", "name": "x", "type": "TEXT",
                                                "characters": "a", "style": {}}]})
        d = fw.diff(self.old, self._new(mutate))
        self.assertEqual(d["groups"]["356:6661"], ["**화면 추가**: `D01 주행` (`356:6661`)"])
        self.assertEqual(fw.sessions_for(d["groups"], d["colors"]), {"drive-voice-d01": ["D01"]})

    def test_changelog_entry_truncates_long_groups(self):
        def mutate(s):
            kids = s["children"][0]["children"]
            for i in range(fw.MAX_LINES_PER_GROUP + 5):
                kids.append({"id": f"352:{3000 + i}", "name": f"n{i}", "type": "RECTANGLE"})
        new = self._new(mutate)
        text = fw.render_changelog_entry(fw.diff(self.old, new), self.old, new, "2026-11-10 12:00")
        self.assertIn("추가 45 · 삭제 0 · 변경 0", text)
        self.assertIn("… 외 5건", text)
        self.assertIn("### H01 · H01 홈", text)


class ChangelogAndStateTest(unittest.TestCase):
    def test_prepend_keeps_newest_first(self):
        with tempfile.TemporaryDirectory() as d:
            with mock.patch.object(fw, "CHANGELOG", Path(d) / "CHANGELOG.md"):
                fw.prepend_changelog("## 첫째\n")
                fw.prepend_changelog("## 둘째\n")
                text = fw.CHANGELOG.read_text(encoding="utf-8")
        self.assertIn("# Figma 11/10 디자인 변경 기록", text)
        self.assertLess(text.index("## 둘째"), text.index("## 첫째"))

    def test_tier1_monthly_cap(self):
        now = fw._dt.datetime(2026, 11, 10)
        state = {}
        for _ in range(3):
            self.assertTrue(fw.tier1_allowed(state, 3, now))
            fw.note_tier1(state, now)
        self.assertFalse(fw.tier1_allowed(state, 3, now))
        self.assertTrue(fw.tier1_allowed(state, 3, fw._dt.datetime(2026, 12, 1)))

    def test_token_from_env_file_never_printed(self):
        with tempfile.TemporaryDirectory() as d:
            env = Path(d) / ".env"
            env.write_text("OTHER=1\nFIGMA_TOKEN='abc-secret'\n", encoding="utf-8")
            with mock.patch.dict(fw.os.environ, {}, clear=True), \
                    mock.patch.object(fw, "_env_files", return_value=[env]):
                self.assertEqual(fw.load_token(), "abc-secret")

    def test_check_without_token_exits_2(self):
        with mock.patch.object(fw, "load_token", return_value=None), \
                mock.patch("builtins.print") as p:
            self.assertEqual(fw.main(["check"]), 2)
        self.assertIn("no_token", p.call_args[0][0])

    def test_check_flow_baseline_unchanged_changed(self):
        sec = make_section()
        calls = []

        def fake_get(path, token, params=None):
            calls.append(path)
            if path.endswith("/meta"):
                return {"file": {"version": fake_get.version, "last_touched_at": "t"}}
            return {"version": fake_get.version, "lastModified": "t",
                    "nodes": {fw.SECTION_ID: {"document": fake_get.doc}}}
        fake_get.version, fake_get.doc = "1", sec
        with tempfile.TemporaryDirectory() as d:
            w = Path(d)
            with mock.patch.multiple(fw, WATCH_DIR=w, SNAPSHOT=w / "snapshot.json", STATE=w / "state.json",
                                     CHANGELOG=w / "CHANGELOG.md", load_token=lambda: "tok", api_get=fake_get), \
                    mock.patch("builtins.print") as p:
                self.assertEqual(fw.main(["check"]), 0)
                self.assertIn('"baseline"', p.call_args[0][0])
                self.assertEqual(fw.main(["check"]), 0)
                self.assertIn('"unchanged"', p.call_args[0][0])
                fake_get.version = "2"
                fake_get.doc = copy.deepcopy(sec)
                fake_get.doc["children"][1]["children"][0]["characters"] = "곧 수유"
                self.assertEqual(fw.main(["check"]), 0)
                out = json.loads(p.call_args[0][0])
                changelog = (w / "CHANGELOG.md").read_text(encoding="utf-8")
                state = json.loads((w / "state.json").read_text(encoding="utf-8"))
        self.assertEqual(out["status"], "changed")
        self.assertEqual(out["sessions"], {"care-c01": ["C05"]})
        self.assertIn("문구 15분 후 수유 → 곧 수유", changelog)
        self.assertEqual(sum(1 for c in calls if c.endswith("/nodes")), 2)  # unchanged 회차엔 트리 조회 없음
        self.assertEqual(state["version"], "2")
        self.assertEqual(sum(state["tier1_calls"].values()), 2)


if __name__ == "__main__":
    unittest.main()
