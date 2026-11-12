#!/usr/bin/env python3
"""Figma 11/10 디자인 변경 감시 (이슈 #157).

Figma REST API(개인 액세스 토큰, 읽기 전용)로 페이지 `종합 정리`(`130:5`) 전체를 조회해
정규화 스냅샷을 만들고, 직전 스냅샷과 비교해 변경 기록을 남긴다.

호출 순서 (싼 것부터):
  1) GET /v1/files/:key/meta      (Tier 3)  → version 이 같으면 끝
     실패(403 등)하면 GET /v1/files/:key/versions (Tier 2)로 대신 확인
  2) GET /v1/files/:key/nodes?ids=130:5&geometry=omit  (Tier 1, 바뀐 경우만)

화면 단위: 페이지 최상위 노드와 모든 SECTION의 자식. 같은 이름 화면이 여러 섹션에 있으므로
(피드백 섹션 `369:11525`는 원본 섹션 `351:2611`의 복사본) 화면은 노드 id로 구분하고,
기록에는 `코드@섹션id`(예: `H01@369:11525`)로 쓴다. 화면 코드는 이름에서 읽는다.

토큰: 환경 변수 FIGMA_TOKEN, 없으면 레포 루트 `.env`의 FIGMA_TOKEN.
토큰 값은 어디에도 출력하지 않는다.

사용:
  python3 tools/figma_watch.py check            # 주기 확인 (기본)
  python3 tools/figma_watch.py check --force    # version 같아도 트리 조회
  python3 tools/figma_watch.py baseline         # 첫 스냅샷만 저장 (비교 없음)
  python3 tools/figma_watch.py diff OLD NEW     # 저장된 스냅샷 두 개 비교 (네트워크 없음)
  python3 tools/figma_watch.py normalize RAW    # 저장된 API 응답(JSON) → 스냅샷

종료 코드: 0 정상(변경 없음 포함), 2 설정 오류(토큰 없음 등), 3 호출 한도(429)·월 한도 초과, 4 API 오류.
표준 출력 마지막 줄은 요약 JSON(`{"status": ...}`)이다. 주기 작업은 이 줄을 읽는다.
"""
from __future__ import annotations

import argparse
import datetime as _dt
import json
import os
import re
import subprocess
import sys
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path
from typing import Any

FILE_KEY = "xeLwkzhH8tnsazQdOriGU2"
ROOT_ID = "130:5"
ROOT_NAME = "종합 정리"
API = "https://api.figma.com/v1"

REPO = Path(__file__).resolve().parent.parent
WATCH_DIR = REPO / "docs/design/pleos-1110/watch"
SNAPSHOT = WATCH_DIR / "snapshot.json"
STATE = WATCH_DIR / "state.json"
CHANGELOG = WATCH_DIR / "CHANGELOG.md"

# Tier 1(View/Collab 좌석은 월 20회까지) 보호용 월 상한. 넘으면 --force 없이는 조회하지 않는다.
DEFAULT_TIER1_MONTHLY_CAP = 30  # 사용자 결정 2026-11-11 (15 → 20), 2026-11-12 (20 → 30, 21번째 호출이 실제로 막히지 않음)

# 이름 앞의 화면 코드: H01, H02-1, C05, C05-T1(유아기 변형), D01, G03, G01-B, O04-1, S02-1, K7-2, HF03 … 또는 Lib·Type·Tile.
# "Note · C05"는 C05 화면의 노트로 본다. "S02 아이 정보"처럼 가운뎃점 없이 이어져도 된다.
_CODE_RE = re.compile(r"^(?:Note\s*·\s*)?([A-Z]{1,2}\d{1,2}(?:-(?:\d+|B|T\d+))?|Lib|Type|Tile)(?=\s|$|·)")
# 발표 슬라이드("01 Title", "17 04 사용자 여정 지도" …): 기록은 하되 세션 알림은 없다.
_SLIDE_RE = re.compile(r"^\d{2} ")

# 화면 코드 → 담당 세션. 위에서부터 처음 맞는 줄을 쓴다. 맞는 줄이 없으면(O·S02·옛 K·HF 등) 통제 세션만 받는다.
# 출처: 감사 지시서 docs/agent-tasks/2026-11-11-full-audit.md (이슈 #201) "figma-watch 감시 범위" 절.
SESSION_RULES: list[tuple[str, tuple[str, ...]]] = [
    (r"G\d{2}(?:-B)?", ("1111-grow-jelly",)),
    (r"C\d{2}(?:-T\d+)?", ("child-mode", "1111-feeding-cleanup")),
    (r"H\d{2}(?:-\d+)?|D\d{2}|Lib|Type|Tile", ("1111-shell-polish",)),
]
SESSION_TASK_FILES = {
    "1111-grow-jelly": "docs/agent-tasks/1111-grow-jelly.md",
    "child-mode": "docs/agent-tasks/192-child-care-mode.md",
    "1111-feeding-cleanup": "docs/agent-tasks/1111-feeding-cleanup.md",
    "1111-shell-polish": "docs/agent-tasks/1111-shell-polish.md",
}


def screen_code(name: str) -> str:
    if _SLIDE_RE.match(name or ""):
        return "slide"
    m = _CODE_RE.match(name or "")
    return m.group(1) if m else ""


def sessions_of_code(code: str) -> tuple[str, ...]:
    for pat, sess in SESSION_RULES:
        if code and re.fullmatch(pat, code):
            return sess
    return ()


MAX_LINES_PER_GROUP = 40


# ---------------------------------------------------------------- 정규화

def _hex(color: dict, opacity: float | None = None) -> str:
    r, g, b = (round(color.get(c, 0) * 255) for c in "rgb")
    a = color.get("a", 1) * (1 if opacity is None else opacity)
    s = f"#{r:02X}{g:02X}{b:02X}"
    return s if a >= 0.999 else f"{s}@{round(a, 2)}"


def _paints(paints: list | None) -> list[str]:
    out = []
    for p in paints or []:
        if p.get("visible") is False:
            continue
        t = p.get("type", "")
        op = p.get("opacity")
        if t == "SOLID":
            out.append(_hex(p.get("color", {}), op))
        elif t.startswith("GRADIENT"):
            stops = ">".join(_hex(s.get("color", {})) for s in p.get("gradientStops", []))
            out.append(f"{t.replace('GRADIENT_', '').lower()}({stops})")
        elif t == "IMAGE":
            out.append("image")
        else:
            out.append(t.lower())
    return out


def _r(v: Any) -> Any:
    return round(v) if isinstance(v, (int, float)) else v


def _node_record(n: dict, origin: tuple[float, float] | None) -> dict:
    rec: dict[str, Any] = {"name": n.get("name", ""), "type": n.get("type", "")}
    box = n.get("absoluteBoundingBox") or {}
    if box:
        rec["w"], rec["h"] = _r(box.get("width")), _r(box.get("height"))
        if origin is not None:
            rec["x"], rec["y"] = _r(box.get("x", 0) - origin[0]), _r(box.get("y", 0) - origin[1])
    if n.get("visible") is False:
        rec["hidden"] = True
    if n.get("type") == "TEXT":
        rec["text"] = n.get("characters", "")
        st = n.get("style") or {}
        font = [st.get("fontFamily"), st.get("fontWeight"), _r(st.get("fontSize")), _r(st.get("lineHeightPx"))]
        rec["font"] = "/".join("" if f is None else str(f) for f in font)
    fills = _paints(n.get("fills"))
    if fills:
        rec["fill"] = fills
    strokes = _paints(n.get("strokes"))
    if strokes:
        rec["stroke"] = strokes + [str(_r(n.get("strokeWeight", 0)))]
    if n.get("cornerRadius"):
        rec["radius"] = _r(n["cornerRadius"])
    elif n.get("rectangleCornerRadii"):
        rec["radius"] = [_r(v) for v in n["rectangleCornerRadii"]]
    if n.get("opacity", 1) < 0.999:
        rec["opacity"] = round(n["opacity"], 2)
    if n.get("layoutMode") and n["layoutMode"] != "NONE":
        pads = [n.get(k, 0) for k in ("paddingTop", "paddingRight", "paddingBottom", "paddingLeft")]
        rec["layout"] = f"{n['layoutMode'].lower()} gap{_r(n.get('itemSpacing', 0))} pad{'/'.join(str(_r(p)) for p in pads)}"
    effects = [e.get("type", "").lower() for e in n.get("effects") or [] if e.get("visible", True)]
    if effects:
        rec["effects"] = effects
    if n.get("componentId"):
        rec["component"] = n["componentId"]
    return rec


def normalize(root: dict) -> dict:
    """페이지(또는 섹션) 노드 → {"root": id, "screens": {...}, "nodes": {id: rec}}.

    화면 = 루트의 자식 중 SECTION이 아닌 것 + 모든 SECTION(중첩 포함)의 자식 중 SECTION이 아닌 것.
    screens[id] = {"name", "code", "section"(속한 섹션 id, 페이지 최상위면 "")}.
    rec 에는 parent, screen(화면 id), 화면 기준 상대 좌표가 들어간다. 루트·섹션 노드 자신은 screen 이 없다.
    """
    nodes: dict[str, dict] = {}
    screens: dict[str, dict] = {}

    def walk(n: dict, parent: str | None, screen: str | None, origin: tuple[float, float] | None,
             container: str | None) -> None:
        rec = _node_record(n, origin)
        rec["parent"] = parent
        if screen:
            rec["screen"] = screen
        nodes[n["id"]] = rec
        is_container = screen is None and (n["id"] == root["id"] or n.get("type") == "SECTION")
        for c in n.get("children") or []:
            if is_container and c.get("type") != "SECTION":
                box = c.get("absoluteBoundingBox") or {}
                section = "" if n["id"] == root["id"] and root.get("type") != "SECTION" else n["id"]
                screens[c["id"]] = {"name": c.get("name", ""), "code": screen_code(c.get("name", "")),
                                    "section": section}
                walk(c, n["id"], c["id"], (box.get("x", 0), box.get("y", 0)), None)
            else:
                walk(c, n["id"], screen, origin, None)

    walk(root, None, None, None, None)
    return {"file": FILE_KEY, "root": root["id"], "screens": screens, "nodes": nodes}


def dump_snapshot(snap: dict, meta: dict) -> str:
    """git diff 가 읽기 쉽게 노드 하나를 한 줄로 쓴다."""
    head = {k: v for k, v in snap.items() if k != "nodes"}
    head["meta"] = meta
    lines = ["{", f'"head": {json.dumps(head, ensure_ascii=False, sort_keys=True)},', '"nodes": {']
    items = sorted(snap["nodes"].items(), key=lambda kv: _id_key(kv[0]))
    for i, (nid, rec) in enumerate(items):
        comma = "," if i < len(items) - 1 else ""
        lines.append(f"{json.dumps(nid)}: {json.dumps(rec, ensure_ascii=False, sort_keys=True)}{comma}")
    lines += ["}", "}", ""]
    return "\n".join(lines)


def load_snapshot(path: Path) -> dict:
    raw = json.loads(path.read_text(encoding="utf-8"))
    head = raw["head"]
    snap = {k: v for k, v in head.items() if k != "meta"}
    snap["nodes"] = raw["nodes"]
    snap["meta"] = head.get("meta", {})
    return snap


def _id_key(nid: str) -> tuple:
    return tuple(int(p) if p.isdigit() else 0 for p in re.split(r"[:;I]", nid) if p != "")


# ---------------------------------------------------------------- 비교

FIELD_LABEL = {
    "text": "문구", "name": "이름", "type": "종류", "w": "너비", "h": "높이", "x": "x", "y": "y",
    "font": "글꼴", "fill": "채우기", "stroke": "선", "radius": "모서리", "opacity": "불투명도",
    "layout": "오토레이아웃", "effects": "효과", "hidden": "숨김", "component": "컴포넌트", "parent": "부모",
}
IGNORED = {"screen"}


def _fmt(v: Any) -> str:
    if v is None:
        return "없음"
    if isinstance(v, list):
        return ", ".join(str(x) for x in v)
    s = str(v).replace("\n", "⏎")
    return s if len(s) <= 60 else s[:57] + "…"


def screen_key(snap: dict, sid: str) -> str:
    """`코드@섹션id`(섹션 밖이면 `코드`). 코드가 없으면 노드 id."""
    info = snap["screens"].get(sid, {})
    code = info.get("code", "")
    if not code or code == "slide":
        return sid
    return f"{code}@{info['section']}" if info.get("section") else code


def _screen_label(snap: dict, sid: str | None) -> str:
    if not sid:
        return "페이지·섹션 공통"
    info = snap["screens"].get(sid, {})
    return f"{screen_key(snap, sid)} · {info.get('name', '')} (`{sid}`)"


def diff(old: dict, new: dict) -> dict:
    """두 스냅샷 비교 → {"groups": {화면 id 또는 "": [줄...]}, "counts": {...}, "colors": {...}}."""
    groups: dict[str, list[str]] = {}
    counts = {"added": 0, "removed": 0, "changed": 0}
    on, nn = old["nodes"], new["nodes"]

    def add(sid: str | None, line: str) -> None:
        groups.setdefault(sid or "", []).append(line)

    # 화면 추가·삭제
    for sid in sorted(set(new["screens"]) - set(old["screens"]), key=_id_key):
        add(sid, f"**화면 추가**: `{new['screens'][sid]['name']}` (`{sid}`)")
    for sid in sorted(set(old["screens"]) - set(new["screens"]), key=_id_key):
        add(sid, f"**화면 삭제**: `{old['screens'][sid]['name']}` (`{sid}`)")
    whole_new = set(new["screens"]) - set(old["screens"])
    whole_old = set(old["screens"]) - set(new["screens"])

    for nid in sorted(set(nn) - set(on), key=_id_key):
        r = nn[nid]
        if r.get("screen") in whole_new or nid in whole_new:
            continue
        counts["added"] += 1
        desc = f" '{_fmt(r.get('text'))}'" if "text" in r else ""
        add(r.get("screen"), f"추가: {r['type']} `{r['name']}`{desc} (`{nid}`)")
    for nid in sorted(set(on) - set(nn), key=_id_key):
        r = on[nid]
        if r.get("screen") in whole_old or nid in whole_old:
            continue
        counts["removed"] += 1
        desc = f" '{_fmt(r.get('text'))}'" if "text" in r else ""
        add(r.get("screen"), f"삭제: {r['type']} `{r['name']}`{desc} (`{nid}`)")
    for nid in sorted(set(nn) & set(on), key=_id_key):
        a, b = on[nid], nn[nid]
        if a == b:
            continue
        parts = []
        for k in sorted((set(a) | set(b)) - IGNORED, key=lambda k: list(FIELD_LABEL).index(k) if k in FIELD_LABEL else 99):
            if a.get(k) != b.get(k):
                parts.append(f"{FIELD_LABEL.get(k, k)} {_fmt(a.get(k))} → {_fmt(b.get(k))}")
        if parts:
            counts["changed"] += 1
            add(b.get("screen"), f"변경: `{b['name']}` (`{nid}`): " + "; ".join(parts))

    colors_old = {c for r in on.values() for c in r.get("fill", []) + r.get("stroke", [])[:-1]}
    colors_new = {c for r in nn.values() for c in r.get("fill", []) + r.get("stroke", [])[:-1]}
    return {
        "groups": groups,
        "counts": counts,
        "colors": {"added": sorted(colors_new - colors_old), "removed": sorted(colors_old - colors_new)},
    }


def sessions_for(groups: dict[str, list[str]], snap: dict, old: dict | None = None) -> dict[str, list[str]]:
    """변경 화면 → {세션: [화면 키]}. 공통 변경·색·담당 없는 화면은 여기 넣지 않는다(통제 세션이 전체를 받음)."""
    out: dict[str, list[str]] = {}
    for sid in groups:
        if not sid:
            continue
        src = snap if sid in snap["screens"] else (old or snap)
        code = src["screens"].get(sid, {}).get("code", "")
        for sess in sessions_of_code(code):
            out.setdefault(sess, []).append(screen_key(src, sid))
    return {k: sorted(set(v)) for k, v in out.items()}


def render_changelog_entry(d: dict, old: dict, new: dict, when: str) -> str:
    c = d["counts"]
    om, nm = old.get("meta", {}), new.get("meta", {})
    lines = [
        f"## {when} — 추가 {c['added']} · 삭제 {c['removed']} · 변경 {c['changed']}",
        "",
        f"- Figma 버전: `{om.get('version', '?')}` ({om.get('lastModified', '?')}) → `{nm.get('version', '?')}` ({nm.get('lastModified', '?')})",
    ]
    if d["colors"]["added"] or d["colors"]["removed"]:
        lines.append(f"- 페이지 전체 색: 새로 쓰임 {_fmt(d['colors']['added']) or '없음'} / 더 이상 안 쓰임 {_fmt(d['colors']['removed']) or '없음'}")
    sess = sessions_for(d["groups"], new, old)
    if sess:
        lines.append("- 관련 세션: " + ", ".join(f"`{s}`({', '.join(v)})" for s, v in sorted(sess.items())))
    lines.append("- 변경된 화면의 기준 PNG(`assets/screens/`)는 아직 옛 그림이다. 구현 전에 이 기록을 우선한다.")
    lines.append("")
    for sid in sorted(d["groups"], key=lambda s: (s != "", _id_key(s) if s else ())):
        rows = d["groups"][sid]
        lines.append(f"### {_screen_label(new if sid in new['screens'] or not sid else old, sid or None)}")
        lines.append("")
        for row in rows[:MAX_LINES_PER_GROUP]:
            lines.append(f"- {row}")
        if len(rows) > MAX_LINES_PER_GROUP:
            lines.append(f"- … 외 {len(rows) - MAX_LINES_PER_GROUP}건 (전체는 `snapshot.json`의 git diff로 확인)")
        lines.append("")
    return "\n".join(lines)


CHANGELOG_HEAD = """# Figma 11/10 디자인 변경 기록

감시 대상: 파일 `{key}` · 페이지 `{name}`(`{sid}`) 전체.
도구: `tools/figma_watch.py` (이슈 #157, 지시 파일 `docs/agent-tasks/157-figma-watch.md`).
최신 기록이 위에 온다. 스냅샷은 `snapshot.json`(노드 한 줄씩), 마지막 확인 버전은 `state.json`.

<!-- entries -->
"""


def prepend_changelog(entry: str) -> None:
    text = CHANGELOG.read_text(encoding="utf-8") if CHANGELOG.exists() else CHANGELOG_HEAD.format(
        key=FILE_KEY, name=ROOT_NAME, sid=ROOT_ID)
    marker = "<!-- entries -->\n"
    if marker not in text:
        text += "\n" + marker
    head, tail = text.split(marker, 1)
    CHANGELOG.write_text(head + marker + "\n" + entry.rstrip() + "\n\n" + tail.lstrip("\n"), encoding="utf-8")


# ---------------------------------------------------------------- 네트워크

class ApiError(Exception):
    def __init__(self, code: int, msg: str, headers: dict | None = None):
        super().__init__(msg)
        self.code, self.headers = code, headers or {}


def _env_files() -> list[Path]:
    files = [REPO / ".env"]
    try:
        common = subprocess.run(["git", "-C", str(REPO), "rev-parse", "--git-common-dir"],
                                capture_output=True, text=True, check=True).stdout.strip()
        main_root = (REPO / common).resolve().parent
        files.append(main_root / ".env")
    except Exception:
        pass
    return files


def load_token() -> str | None:
    if os.environ.get("FIGMA_TOKEN"):
        return os.environ["FIGMA_TOKEN"].strip()
    for f in _env_files():
        if not f.exists():
            continue
        for line in f.read_text(encoding="utf-8").splitlines():
            m = re.match(r"\s*FIGMA_TOKEN\s*=\s*(.*)$", line)
            if m and m.group(1).strip().strip("'\""):
                return m.group(1).strip().strip("'\"")
    return None


def api_get(path: str, token: str, params: dict | None = None) -> dict:
    url = f"{API}{path}"
    if params:
        url += "?" + urllib.parse.urlencode(params)
    req = urllib.request.Request(url, headers={"X-Figma-Token": token, "User-Agent": "figma-watch/1"})
    try:
        with urllib.request.urlopen(req, timeout=120) as r:
            return json.loads(r.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        hdr = {k: e.headers.get(k) for k in ("Retry-After", "X-Figma-Rate-Limit-Type", "X-Figma-Plan-Tier") if e.headers.get(k)}
        body = e.read().decode("utf-8", "replace")[:300]
        raise ApiError(e.code, f"HTTP {e.code}: {body}", hdr) from None


def fetch_version(token: str) -> dict:
    """{"version", "lastModified", "via"} — meta(Tier 3) 우선, 안 되면 versions(Tier 2)."""
    try:
        m = api_get(f"/files/{FILE_KEY}/meta", token)
        f = m.get("file", m)
        return {"version": str(f.get("version")), "lastModified": f.get("last_touched_at"), "via": "meta"}
    except ApiError as e:
        if e.code == 429:
            raise
        v = api_get(f"/files/{FILE_KEY}/versions", token, {"page_size": 1})
        top = (v.get("versions") or [{}])[0]
        return {"version": str(top.get("id")), "lastModified": top.get("created_at"), "via": "versions"}


def fetch_root(token: str) -> tuple[dict, dict]:
    r = api_get(f"/files/{FILE_KEY}/nodes", token, {"ids": ROOT_ID, "geometry": "omit"})
    node = (r.get("nodes") or {}).get(ROOT_ID) or {}
    doc = node.get("document")
    if not doc:
        raise ApiError(404, f"페이지 {ROOT_ID}을 찾지 못함")
    return doc, {"version": str(r.get("version")), "lastModified": r.get("lastModified")}


# ---------------------------------------------------------------- 상태

def load_state() -> dict:
    return json.loads(STATE.read_text(encoding="utf-8")) if STATE.exists() else {}


def save_state(state: dict) -> None:
    STATE.write_text(json.dumps(state, ensure_ascii=False, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def _now() -> _dt.datetime:
    return _dt.datetime.now().astimezone()


def tier1_allowed(state: dict, cap: int, now: _dt.datetime) -> bool:
    return state.get("tier1_calls", {}).get(now.strftime("%Y-%m"), 0) < cap


def note_tier1(state: dict, now: _dt.datetime) -> None:
    calls = state.setdefault("tier1_calls", {})
    month = now.strftime("%Y-%m")
    calls[month] = calls.get(month, 0) + 1


# ---------------------------------------------------------------- 명령

def emit(**summary: Any) -> None:
    print(json.dumps(summary, ensure_ascii=False))


def cmd_check(args: argparse.Namespace, baseline_only: bool = False) -> int:
    token = load_token()
    if not token:
        emit(status="no_token", message="FIGMA_TOKEN이 환경 변수와 레포 루트 .env에 없음")
        return 2
    WATCH_DIR.mkdir(parents=True, exist_ok=True)
    state, now = load_state(), _now()
    try:
        ver = fetch_version(token)
    except ApiError as e:
        emit(status="rate_limited" if e.code == 429 else "api_error", code=e.code, headers=e.headers, step="version",
             message=str(e)[:200])
        return 3 if e.code == 429 else 4
    if not args.force and not baseline_only and SNAPSHOT.exists() and ver["version"] == state.get("version"):
        emit(status="unchanged", version=ver["version"], lastModified=ver["lastModified"], via=ver["via"])
        return 0
    if not args.force and not tier1_allowed(state, args.tier1_cap, now):
        emit(status="tier1_cap", message=f"이번 달 트리 조회 {args.tier1_cap}회 상한 도달 (--force로 무시)", version=ver["version"])
        return 3
    try:
        doc, meta = fetch_root(token)
    except ApiError as e:
        emit(status="rate_limited" if e.code == 429 else "api_error", code=e.code, headers=e.headers, step="nodes",
             message=str(e)[:200])
        return 3 if e.code == 429 else 4
    note_tier1(state, now)
    new = normalize(doc)
    new["meta"] = {**meta, "fetchedAt": now.isoformat(timespec="minutes")}
    result: dict[str, Any] = {"version": meta["version"], "lastModified": meta["lastModified"]}
    if baseline_only or not SNAPSHOT.exists():
        SNAPSHOT.write_text(dump_snapshot(new, new["meta"]), encoding="utf-8")
        prepend_changelog(f"## {now:%Y-%m-%d %H:%M} — 기준선 저장\n\n- Figma 버전 `{meta['version']}` ({meta['lastModified']}), "
                          f"화면·노트 {len(new['screens'])}개, 노드 {len(new['nodes'])}개.\n")
        status = "baseline"
    else:
        old = load_snapshot(SNAPSHOT)
        d = diff(old, new)
        if d["groups"] or d["colors"]["added"] or d["colors"]["removed"]:
            prepend_changelog(render_changelog_entry(d, old, new, f"{now:%Y-%m-%d %H:%M}"))
            SNAPSHOT.write_text(dump_snapshot(new, new["meta"]), encoding="utf-8")
            status = "changed"
            result.update(counts=d["counts"], sessions=sessions_for(d["groups"], new, old),
                          screens=sorted(_screen_label(new if s in new["screens"] else old, s or None) for s in d["groups"]))
        else:
            status = "section_unchanged"  # 파일의 다른 페이지만 바뀜 (상태 이름은 주기 작업과 맞추려고 유지)
    state.update(version=meta["version"], lastModified=meta["lastModified"], checkedAt=now.isoformat(timespec="minutes"))
    save_state(state)
    emit(status=status, **result)
    return 0


def cmd_diff(args: argparse.Namespace) -> int:
    old, new = load_snapshot(Path(args.old)), load_snapshot(Path(args.new))
    d = diff(old, new)
    print(render_changelog_entry(d, old, new, "비교"))
    emit(status="changed" if d["groups"] else "section_unchanged", counts=d["counts"],
         sessions=sessions_for(d["groups"], new, old))
    return 0


def cmd_normalize(args: argparse.Namespace) -> int:
    raw = json.loads(Path(args.raw).read_text(encoding="utf-8"))
    doc = raw.get("nodes", {}).get(ROOT_ID, {}).get("document", raw)
    snap = normalize(doc)
    meta = {"version": str(raw.get("version")), "lastModified": raw.get("lastModified")}
    Path(args.out).write_text(dump_snapshot(snap, meta), encoding="utf-8")
    emit(status="ok", screens=len(snap["screens"]), nodes=len(snap["nodes"]))
    return 0


def main(argv: list[str] | None = None) -> int:
    p = argparse.ArgumentParser(description="Figma 11/10 디자인 변경 감시")
    sub = p.add_subparsers(dest="cmd")
    for name in ("check", "baseline"):
        s = sub.add_parser(name)
        s.add_argument("--force", action="store_true", help="version 비교·월 상한을 무시하고 트리 조회")
        s.add_argument("--tier1-cap", type=int, default=DEFAULT_TIER1_MONTHLY_CAP)
    s = sub.add_parser("diff")
    s.add_argument("old")
    s.add_argument("new")
    s = sub.add_parser("normalize")
    s.add_argument("raw")
    s.add_argument("out")
    args = p.parse_args(argv)
    if args.cmd in (None, "check"):
        if args.cmd is None:
            args = p.parse_args(["check"])
        return cmd_check(args)
    if args.cmd == "baseline":
        return cmd_check(args, baseline_only=True)
    if args.cmd == "diff":
        return cmd_diff(args)
    return cmd_normalize(args)


if __name__ == "__main__":
    sys.exit(main())
