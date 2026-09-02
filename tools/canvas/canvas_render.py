#!/usr/bin/env python3
"""Draw the wire: turn recorded kompot screens into a Claude Design canvas written in the vocabulary.

The other direction of `canvas_tree.py`. A server's recorded responses — the trees it actually
sends — become artboards on one `.dc.html` canvas, each node a `data-kompot` element with its words
as attributes, styled by the kit's `kompot.css`. The canvas is then the screen as the server says
it, not a picture somebody drew of it: a designer edits it in Claude Design inside the vocabulary,
and `canvas_tree.py --compare` tells the server what changed, by id and by word.

    canvas_render.py client/src/test/resources/recorded/*.json --kit build/kit --title "Киоск БОУЛ ЛАБ" \\
        --out build/kit/kiosk.dc.html

`--kit` is where `kompot.css`/`kompot.js` are (they are linked as siblings, so ship them beside the
canvas); `--runtime support.js` copies the Claude Design runtime beside it too, which the app's
canvas mode needs. Artboard ids are the file names (`welcome`, `menu-grid`), captions optional via
`--caption welcome="1a · Приветствие"`.

The round trip is the test: `canvas_tree.py <canvas> --artboard welcome --compare welcome.json`
must report no difference, for every screen rendered.
"""
import argparse
import glob
import html
import json
import os
import shutil
import sys

# The wire leaves a default out; the canvas says it out loud, because the kit's CSS keys on the
# attribute and a card without `data-density="card"` is a card without its inset.
from canvas_tree import DEFAULTS, load_defaults


def esc(s) -> str:
    return html.escape(str(s), quote=True)


def attrs_of(node: dict) -> dict:
    a = {}
    for m in node.get("modifiers", []):
        if m.get("type") == "weight":
            a["data-weight"] = str(int(m["value"]) if float(m["value"]).is_integer() else m["value"])
        elif m.get("type") == "size":
            w, h = m.get("width") == "Fill", m.get("height") == "Fill"
            if w and h:
                a["data-fill"] = "both"
            elif w:
                a["data-fill"] = "width"
            elif h:
                a["data-fill"] = "height"
            if "widthDp" in m:
                a["data-width"] = str(m["widthDp"])
            if "heightDp" in m:
                a["data-height"] = str(m["heightDp"])
            if "maxWidthDp" in m:
                a["data-max-width"] = str(m["maxWidthDp"])
    act = node.get("action") or node.get("backAction")
    if act and act.get("type") == "navigate":
        a["data-action"] = act["deeplink"]
    return a


def render(node: dict, depth: int = 0, defaults: dict = DEFAULTS) -> str:
    pad = "  " * depth
    kind = node["type"]
    node = {**defaults.get(kind, {}), **node}
    a = {"data-kompot": kind, "data-id": node.get("id", "")}
    a.update(attrs_of(node))
    inner = ""
    if kind in ("column", "row"):
        if kind == "column" and not node.get("children") and "data-weight" in a and "data-action" not in a:
            a["data-kompot"] = "spacer"
            del a["data-weight"]
        if node.get("spacing"):
            a["data-spacing"] = str(node["spacing"])
        inner = "".join(render(c, depth + 1, defaults) for c in node.get("children", []))
    elif kind == "surface":
        for w in ("tone", "density", "align", "rule"):
            if w in node:
                a[f"data-{w}"] = node[w]
        if node.get("spacing"):
            a["data-spacing"] = str(node["spacing"])
        if node.get("pinned"):
            a["data-pinned"] = ""
        inner = "".join(render(c, depth + 1, defaults) for c in node.get("children", []))
    elif kind == "text":
        for w in ("style", "color"):
            if w in node:
                a[f"data-{w}"] = node[w]
        if "maxLines" in node:
            a["data-max-lines"] = str(node["maxLines"])
        if node.get("spans"):
            parts = []
            for s in node["spans"]:
                sa = {k: v for k, v in (("data-style", s.get("style")), ("data-color", s.get("color")), ("data-action", (s.get("action") or {}).get("deeplink"))) if v}
                parts.append("<span " + " ".join(f'{k}="{esc(v)}"' for k, v in sa.items()) + ">" + esc(s.get("text", "")) + "</span>")
            inner = "".join(parts)
        else:
            inner = esc(node.get("text", "")).replace("\n", "<br>")
    elif kind == "button":
        if "variant" in node:
            a["data-variant"] = node["variant"]
        inner = esc(node.get("text", ""))
    elif kind == "glyph":
        for w in ("tone", "frame", "size", "color"):
            if w in node:
                a[f"data-{w}"] = node[w]
        inner = esc(node.get("glyph", ""))
    elif kind == "photo":
        if "size" in node:
            a["data-size"] = node["size"]
        inner = esc(node.get("caption", ""))
    elif kind == "screen_header":
        inner = "←"
    elif kind == "table":
        rows = []
        for r in node.get("rows", []):
            cells = "".join(f'<span data-cell>{esc(c)}</span>' for c in r.get("cells", []))
            rows.append(f'<div data-row{" data-header" if r.get("header") else ""}>{cells}</div>')
        inner = "".join(rows)
    else:
        # A type the kit does not know draws a visible block, never nothing.
        a["data-kompot"] = "unknown"
        inner = esc(kind)
    attr = " ".join(f'{k}="{esc(v)}"' if v != "" or k == "data-id" else k for k, v in a.items())
    if inner and "\n" not in inner and kind in ("text", "button", "glyph", "photo", "screen_header", "table", "unknown"):
        return f"{pad}<div {attr}>{inner}</div>\n"
    if inner:
        return f"{pad}<div {attr}>\n{inner}{pad}</div>\n"
    return f"{pad}<div {attr}></div>\n"


def artboard(name: str, caption: str, tree: dict, defaults: dict = DEFAULTS) -> str:
    return (
        f'    <div id="{esc(name)}-board" style="display:flex; flex-direction:column; gap:20px">\n'
        f'      <div style="display:flex; align-items:center; gap:16px">\n'
        f'        <div style="background:#221510; color:#FFF8F5; font-size:26px; font-weight:700; padding:8px 20px; border-radius:100px">{esc(name)}</div>\n'
        f'        <div style="font-size:30px; font-weight:600; color:#221510">{esc(caption)}</div>\n'
        f'      </div>\n'
        f'      <div class="k-screen" id="{esc(name)}" data-artboard="{esc(name)}" style="border-radius:56px; box-shadow:0 40px 80px -20px rgba(59,10,0,.45)">\n'
        + render(tree, 4, defaults)
        + "      </div>\n    </div>\n"
    )


def canvas(boards: list, title: str) -> str:
    return (
        '<!DOCTYPE html>\n<html>\n<head>\n<meta charset="utf-8">\n<meta name="viewport" content="width=device-width, initial-scale=1">\n'
        '<script src="./support.js"></script>\n</head>\n<body>\n<x-dc>\n<helmet>\n<meta name="design_doc_mode" content="canvas">\n'
        '<link rel="preconnect" href="https://fonts.googleapis.com">\n<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>\n'
        '<link href="https://fonts.googleapis.com/css2?family=Roboto+Flex:opsz,wght,wdth,GRAD@8..144,100..1000,25..151,-200..150&display=swap" rel="stylesheet">\n'
        '<link rel="stylesheet" href="./kompot.css">\n<script src="./kompot.js"></script>\n'
        '<style>\n  body { margin: 0; background: #E8DED8; font-family: "Roboto Flex", system-ui, sans-serif; -webkit-font-smoothing: antialiased; }\n</style>\n</helmet>\n\n'
        '<section style="display:flex; flex-direction:column; gap:44px; padding:80px 80px 140px">\n'
        '  <div style="display:flex; align-items:baseline; gap:24px; flex-wrap:wrap">\n'
        f'    <div style="font-size:64px; font-weight:800; letter-spacing:-2px; color:#221510">{esc(title)}</div>\n'
        '    <div style="font-size:28px; font-weight:500; color:#5C443B">то, что шлёт сервер · в словаре kompot · 1080×1920</div>\n'
        '  </div>\n\n  <div style="display:flex; gap:72px; align-items:flex-start; flex-wrap:wrap">\n\n'
        + "\n".join(boards)
        + '\n  </div>\n</section>\n\n</x-dc>\n<script type="text/x-dc" data-dc-script data-props="{}">\nclass Component extends DCLogic {}\n</script>\n</body>\n</html>\n'
    )


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("recordings", nargs="+", help="recorded wire trees (globs allowed); the file name is the artboard id")
    parser.add_argument("--kit", required=True, help="directory holding kompot.css and kompot.js")
    parser.add_argument("--runtime", help="the Claude Design support.js to copy beside the canvas")
    parser.add_argument("--title", default="kompot")
    parser.add_argument("--caption", action="append", default=[], help='name="caption" (repeatable)')
    parser.add_argument("--out", required=True, help="the .dc.html to write")
    parser.add_argument("--tokens", help="the product's tokens.json, for the dictionary's defaults and the font link")
    args = parser.parse_args()
    defaults = load_defaults(args.tokens)
    captions = dict(c.split("=", 1) for c in args.caption)
    files = [f for pattern in args.recordings for f in sorted(glob.glob(pattern))]
    boards = []
    for path in files:
        name = os.path.splitext(os.path.basename(path))[0]
        with open(path, encoding="utf-8") as f:
            tree = json.load(f)
        boards.append(artboard(name, captions.get(name, name), tree, defaults))
    out_dir = os.path.dirname(os.path.abspath(args.out))
    os.makedirs(out_dir, exist_ok=True)
    with open(args.out, "w", encoding="utf-8") as f:
        f.write(canvas(boards, args.title))
    for sibling in ("kompot.css", "kompot.js"):
        src = os.path.join(args.kit, sibling)
        if os.path.abspath(src) != os.path.join(out_dir, sibling):
            shutil.copy(src, os.path.join(out_dir, sibling))
    if args.runtime:
        shutil.copy(args.runtime, os.path.join(out_dir, "support.js"))
    print(f"{len(boards)} artboards → {args.out}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
