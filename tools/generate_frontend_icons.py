#!/usr/bin/env python3
"""Generate FrontendIcons.kt from the frontend's icon sources.

Reads:
  - prototypes/mobile-interactive/src/app/catalogIcons.tsx   (react-icons)
  - prototypes/mobile-interactive/node_modules/lucide-react/.../icons/*.js  (lucide stroke)

The frontend renders every SoundIcon with strokeWidth=1.5 (App.tsx SoundIcon) and
react-icons GenIcon sets stroke/fill to currentColor. So the Android output is NOT
uniformly "filled"; it mirrors the exact SVG the frontend draws:
  - filled react-icons  → fill + same-color 1.5px stroke
  - Tabler stroke family → stroke-only 1.5px round caps/joins
  - Material "M0 0h24v24H0z" bg rects (fill=none, inherit stroke) → thin frame
  - lucide → stroke-only 1.5px round caps/joins

Emits an ImageVector-per-icon Kotlin file into core/designsystem. The path data
is parsed at runtime with androidx.compose.ui.graphics.vector.PathParser, so the
generated file is data, not code — one lazy ImageVector per icon plus raw path
lists used by the animated header indicator.

Usage: python tools/generate_frontend_icons.py <repo-root>
"""

import json
import re
import sys
import os

REPO = sys.argv[1] if len(sys.argv) > 1 else "."
CATALOG = os.path.join(REPO, "prototypes/mobile-interactive/src/app/catalogIcons.tsx")
LUCIDE_DIR = os.path.join(
    REPO, "prototypes/mobile-interactive/node_modules/lucide-react/dist/esm/icons"
)

# ── lucide (stroke) icons used by the app ──────────────────────────────────
LUCIDE_NEEDED = [
    "waves", "wind", "trees", "footprints", "cloud-rain", "umbrella",
    "bird", "car", "building-2", "audio-lines", "radio", "volume-2",
    "home", "music-2", "timer", "bar-chart-2",
    "play", "pause",
    # full App.tsx lucide set (line 2-19 import)
    "plus", "search", "heart", "sliders-horizontal", "x", "check",
    "volume-x", "trash-2", "rotate-ccw", "moon", "book-open", "briefcase",
    "graduation-cap", "star", "tag", "archive", "chevron-right", "bookmark",
    "flag", "user", "pen", "chevron-down", "chevron-up", "sparkles",
    "flame", "droplets", "bug", "dog", "cat", "fish", "siren", "users",
    "map-pin", "plane", "church", "landmark", "beer", "train-front", "ship",
    "keyboard", "file-text", "clock-3", "bell", "fan", "projector", "disc-3",
    "activity", "shuffle", "share-2", "list-music", "upload", "save",
    "arrow-left", "pin", "trending-up", "grip-vertical", "settings-2",
    "more-horizontal", "mic", "image", "paperclip", "list-checks", "check-square",
    "heading-2", "undo-2", "redo-2", "headphones", "eraser", "folder-input",
    "archive-restore", "play-circle", "square", "pencil-line",
    "circle", "circle-check", "circle-alert", "circle-pause",
]


def camel(name: str) -> str:
    parts = re.split(r"[-_. ]+", name)
    return parts[0].lower() + "".join(p[:1].upper() + p[1:] for p in parts[1:])


def elem_to_d(tag: str, attr: dict) -> str:
    f = float
    if tag == "path":
        return attr.get("d", "")
    if tag == "circle":
        cx, cy, r = f(attr["cx"]), f(attr["cy"]), f(attr["r"])
        return f"M{cx - r} {cy}a{r} {r} 0 1 0 {2 * r} 0a{r} {r} 0 1 0 {-2 * r} 0Z"
    if tag == "ellipse":
        cx, cy, rx, ry = f(attr["cx"]), f(attr["cy"]), f(attr["rx"]), f(attr["ry"])
        return f"M{cx - rx} {cy}a{rx} {ry} 0 1 0 {2 * rx} 0a{rx} {ry} 0 1 0 {-2 * rx} 0Z"
    if tag == "line":
        x1, y1, x2, y2 = f(attr["x1"]), f(attr["y1"]), f(attr["x2"]), f(attr["y2"])
        return f"M{x1} {y1}L{x2} {y2}"
    if tag in ("polyline", "polygon"):
        # `points` is a flat list of x/y coordinates ("12 2 20 7 4 7"); group into pairs.
        vals = [p.strip() for p in attr["points"].split()]
        pairs = [" ".join(vals[i : i + 2]) for i in range(0, len(vals), 2)]
        d = "M" + pairs[0] + "".join("L" + p for p in pairs[1:])
        return d + ("Z" if tag == "polygon" else "")
    if tag == "rect":
        x, y, w, h = f(attr["x"]), f(attr["y"]), f(attr["width"]), f(attr["height"])
        rx = f(attr.get("rx", 0))
        ry = f(attr.get("ry", rx))
        if rx == 0:
            return f"M{x} {y}H{x + w}V{y + h}H{x}Z"
        # rounded rect, clockwise from top-left. SVG <rect rx> corners are true
        # elliptical arcs, so use A commands (not Q beziers) to match exactly.
        return (
            f"M{x + rx} {y}H{x + w - rx}A{rx} {ry} 0 0 1 {x + w} {y + ry}"
            f"V{y + h - ry}A{rx} {ry} 0 0 1 {x + w - rx} {y + h}"
            f"H{x + rx}A{rx} {ry} 0 0 1 {x} {y + h - ry}V{y + ry}"
            f"A{rx} {ry} 0 0 1 {x + rx} {y}Z"
        )
    raise ValueError(f"unhandled tag {tag}")


def parse_viewbox(vb: str):
    m = re.match(r"\s*(\d+)\s+(\d+)\s+(\d+)\s+(\d+)", vb)
    return float(m.group(3)), float(m.group(4))


def collect_nodes(node, out):
    tag = node.get("tag")
    attr = node.get("attr", {})
    if tag in ("path", "circle", "ellipse", "line", "polyline", "polygon", "rect"):
        # Keep every element here; whether it is emitted (and as fill vs stroke)
        # is decided in path_mode() so that the frontend's exact SVG semantics are
        # preserved (see the "fill=none" background note there).
        out.append((tag, attr))
    for c in node.get("child") or []:
        collect_nodes(c, out)


def path_mode(attr, is_stroke_root):
    """Return the ImageVector render mode for one react-icon element, mirroring the
    exact SVG semantics the frontend renders (react-icons GenIcon + `strokeWidth={1.5}`
    prop from App.tsx SoundIcon):
      - None            → fully invisible, skip
      - "fill"          → fill only (strokeWidth=0 / fill=currentColor override)
      - "stroke"        → stroke only (Tabler stroke family, round caps/joins)
      - "stroke_border" → stroke only (Material bg rect "M0 0h24v24H0z": fill=none but
                          inherits root stroke → draws a thin frame around the glyph)
      - "fill_stroke"   → fill + same-color stroke (filled react-icons; the frontend
                          strokes them at 1.5px in currentColor)
    """
    fill = attr.get("fill")
    stroke = attr.get("stroke")
    stroke_width = attr.get("strokeWidth", attr.get("stroke-width"))
    # Tabler background rect: fill="none" + stroke="none" → invisible.
    if fill == "none" and stroke == "none":
        return None
    # Filled-variant glyphs (TbBeerFilled / TbBowlFilled): stroke-width=0, fill=currentColor.
    if stroke_width == "0" or fill == "currentColor":
        return "fill"
    # Material background rect "M0 0h24v24H0z": fill="none" but inherits the root stroke,
    # so the frontend draws a thin square frame around the glyph (not a solid fill).
    if fill == "none":
        return "stroke_border" if not is_stroke_root else None
    # Tabler stroke family: glyph paths are stroked (round caps/joins from root attrs).
    if is_stroke_root:
        return "stroke"
    # Filled glyph: the frontend strokes it with the same currentColor at 1.5px.
    return "fill_stroke"


# ── parse catalogIcons.tsx (react-icons) ──────────────────────────────────
react_icons = {}  # name -> (viewW, viewH, [(d, fillRule)])
src = open(CATALOG, encoding="utf-8").read()
for m in re.finditer(
    r'export function (?P<name>[A-Za-z0-9]+)\(props: IconBaseProps\) \{\s*return GenIcon\((?P<json>\{.*?\})\)\(props\);',
    src,
    re.S,
):
    data = json.loads(m.group("json"))
    root = data["attr"]
    vw, vh = parse_viewbox(root["viewBox"])
    is_stroke_root = root.get("fill") == "none" and "stroke" in root
    nodes = []
    collect_nodes(data, nodes)
    els = []
    for tag, attr in nodes:
        mode = path_mode(attr, is_stroke_root)
        if mode is None:
            continue
        d = elem_to_d(tag, attr)
        if d:
            els.append((d, attr.get("fillRule") == "evenodd", mode))
    react_icons[m.group("name")] = (vw, vh, els)


# ── parse lucide .js files (stroke) ───────────────────────────────────────
lucide_icons = {}  # name -> (vw, vh, [d, ...])
for icon in LUCIDE_NEEDED:
    fp = os.path.join(LUCIDE_DIR, f"{icon}.js")
    if not os.path.exists(fp):
        print(f"WARN: missing lucide {icon}")
        continue
    text = open(fp, encoding="utf-8").read()
    # follow alias re-exports: `export { default } from './house.js';`
    alias = re.search(r"export \{\s*default\s*\}\s*from\s*['\"](.*?)['\"]", text)
    if alias:
        fp = os.path.join(LUCIDE_DIR, alias.group(1))
        if not os.path.exists(fp):
            fp = fp if fp.endswith(".js") else fp + ".js"
        text = open(fp, encoding="utf-8").read()
    arr_m = re.search(r"const __iconNode = \[(.*?)\];", text, re.S)
    if not arr_m:
        print(f"WARN: no __iconNode in {icon}")
        continue
    ds = []
    for em in re.finditer(r'\[\s*"(\w+)",\s*\{(.*?)\}\s*\]', arr_m.group(1), re.S):
        tag = em.group(1)
        attr = dict(re.findall(r'(\w+)\s*:\s*"([^"]*)"', em.group(2)))
        d = elem_to_d(tag, attr)
        if d:
            ds.append(d)
    lucide_icons[camel(icon)] = (24.0, 24.0, ds)


# ── Kotlin emission ───────────────────────────────────────────────────────
def kotlin_str(s: str) -> str:
    # escape for a double-quoted Kotlin string literal
    return '"' + s.replace("\\", "\\\\").replace('"', '\\"').replace("$", "\\$") + '"'


def render_icon(kotlin_name, vw, vh, els):
    """els: list of (d, evenodd, mode) — see path_mode() for the mode meanings."""
    dw, dh = 24.0, 24.0
    if vw != vh:
        dh = round(24.0 * vh / vw, 3)
    lines = [
        f"val {kotlin_name}: ImageVector by lazy {{",
        "    ImageVector.Builder(",
        f'        name = "{kotlin_name}",',
        f"        defaultWidth = {dw}.dp, defaultHeight = {dh}.dp,",
        f"        viewportWidth = {vw}f, viewportHeight = {vh}f,",
        "    ).apply {",
    ]
    for d, evenodd, mode in els:
        path_fill = "PathFillType.EvenOdd" if evenodd else "PathFillType.NonZero"
        if mode in ("stroke", "lucide_stroke"):
            # stroke-only glyph: lucide + Tabler stroke family (round caps/joins).
            lines.append(
                '        addPath(\n'
                f'            pathData = parsePath({kotlin_str(d)}),\n'
                f"            pathFillType = {path_fill},\n"
                "            fill = null,\n"
                "            stroke = SolidColor(Color.Black),\n"
                "            strokeLineWidth = 1.5f,\n"
                "            strokeLineCap = StrokeCap.Round,\n"
                "            strokeLineJoin = StrokeJoin.Round,\n"
                "        )"
            )
        elif mode == "stroke_border":
            # Material background rect (fill=none, inherits stroke): thin frame, butt/miter.
            lines.append(
                '        addPath(\n'
                f'            pathData = parsePath({kotlin_str(d)}),\n'
                f"            pathFillType = {path_fill},\n"
                "            fill = null,\n"
                "            stroke = SolidColor(Color.Black),\n"
                "            strokeLineWidth = 1.5f,\n"
                "            strokeLineCap = StrokeCap.Butt,\n"
                "            strokeLineJoin = StrokeJoin.Miter,\n"
                "        )"
            )
        elif mode == "fill":
            # filled-variant glyph (TbBeerFilled / TbBowlFilled): fill only.
            lines.append(
                '        addPath(\n'
                f'            pathData = parsePath({kotlin_str(d)}),\n'
                f"            pathFillType = {path_fill},\n"
                "            fill = SolidColor(Color.Black),\n"
                "            stroke = null,\n"
                "        )"
            )
        else:  # "fill_stroke": filled glyph + same-color 1.5px stroke (as the frontend draws).
            lines.append(
                '        addPath(\n'
                f'            pathData = parsePath({kotlin_str(d)}),\n'
                f"            pathFillType = {path_fill},\n"
                "            fill = SolidColor(Color.Black),\n"
                "            stroke = SolidColor(Color.Black),\n"
                "            strokeLineWidth = 1.5f,\n"
                "            strokeLineCap = StrokeCap.Butt,\n"
                "            strokeLineJoin = StrokeJoin.Miter,\n"
                "        )"
            )
    lines.append("    }.build()\n}")
    return "\n".join(lines)


out = []
out.append("""// @generated by tools/generate_frontend_icons.py — DO NOT EDIT BY HAND.
// Icons converted 1:1 from the frontend sources. The frontend renders every
// SoundIcon with strokeWidth=1.5 (App.tsx SoundIcon), so filled react-icons carry a
// same-color stroke, Tabler/lucide glyphs are stroked, and Material "M0 0h24v24H0z"
// background rects (fill=none, inheriting stroke) become thin frames.
//   - catalogIcons.tsx   (react-icons: filled + stroke, plus Tabler stroke family)
//   - lucide-react *.js  (stroke glyphs, strokeWidth 1.5, round caps/joins)

package com.soundist.core.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathNode
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

private fun parsePath(d: String): List<PathNode> = PathParser().parsePathString(d).toNodes()
""")

# react-icons: order by the frontend byId mapping for a readable file
for name in sorted(react_icons):
    vw, vh, els = react_icons[name]
    out.append(render_icon(camel(name), vw, vh, els))

for name, (vw, vh, els) in lucide_icons.items():
    out.append(render_icon(name, vw, vh, [(d, False, "lucide_stroke") for d in els]))

# raw path lists for the animated header indicator
for name in ("waves", "radio", "audio-lines"):
    key = camel(name)
    if key in lucide_icons:
        ds = lucide_icons[key][2]
        out.append(
            f"val header{name.replace('-', ' ').title().replace(' ', '')}Paths: List<String> = listOf(\n"
            + ",\n".join(f"    {kotlin_str(d)}" for d in ds)
            + "\n)"
        )

dst = os.path.join(
    REPO,
    "apps/android-native/core/designsystem/src/main/java/com/soundist/core/designsystem/FrontendIcons.kt",
)
os.makedirs(os.path.dirname(dst), exist_ok=True)
with open(dst, "w", encoding="utf-8", newline="\n") as f:
    f.write("\n\n".join(out) + "\n")

print(f"react-icons: {len(react_icons)}  lucide: {len(lucide_icons)}")
print(f"wrote {dst}")
