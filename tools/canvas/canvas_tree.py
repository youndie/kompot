#!/usr/bin/env python3
"""Read an artboard written in the kompot vocabulary and emit the wire tree — or diff it with one.

A canvas marked up with `data-kompot` attributes (see `canvas_kit.py` for the contract) is not a
picture of a screen: it IS the screen, minus the server. This walks the markup and writes the
component tree the server would send, in kompot's own JSON:

    canvas_tree.py design/kit/welcome.html --artboard welcome --out build/canvas/welcome.canvas.json

and, with a recording of what the server actually sends, says where the two disagree:

    canvas_tree.py design/kit/welcome.html --compare client/src/test/resources/recorded/welcome.json

The diff is by id: a node on one side without a twin on the other, a twin of another type, a word
that differs (tone, density, style, colour, variant…), a text that differs, children in another
order. Geometry is not compared here — that is `canvas_diff.py`'s job on the rendered frame; this
compares what the two trees SAY. A row that stays is a difference to write down.

Only the standard library: the HTML is parsed with html.parser, which is enough for the markup the
kit produces. Nodes without `data-kompot` are transparent — wrappers a designer adds for layout are
ignored, their children are read.
"""
import argparse
import json
import sys
from html.parser import HTMLParser

TYPES = {"column", "row", "surface", "text", "button", "glyph", "photo", "screen_header", "spacer", "table"}
WORDS = {
    "surface": ("tone", "density", "align"),
    "text": ("style", "color"),
    "button": ("variant",),
    "glyph": ("tone", "frame", "size", "color"),
    "photo": ("size",),
}


class Node:
    def __init__(self, kind, attrs):
        self.kind = kind
        self.attrs = attrs
        self.children = []
        self.text = []
        self.spans = []
        self.rows = []  # table: [{"cells": [...], "header": bool}]


VOID = {"br", "img", "hr", "meta", "link", "input", "wbr", "source"}


class Walker(HTMLParser):
    def __init__(self, artboard):
        super().__init__()
        self.artboard = artboard
        self.stack = []  # (node or None, tag)
        self.roots = []
        self.inside = artboard is None
        self.depth_in = None
        self.span = None
        self.cell = None

    def handle_starttag(self, tag, attrs):
        a = dict(attrs)
        if not self.inside:
            if a.get("id") == self.artboard or a.get("data-artboard") == self.artboard:
                self.inside = True
                self.depth_in = len(self.stack)
            if tag not in VOID:
                self.stack.append((None, tag))
            return
        kind = a.get("data-kompot")
        parent = next((n for n, _ in reversed(self.stack) if n is not None), None)
        if parent is not None and parent.kind == "table":
            if "data-row" in a:
                parent.rows.append({"cells": [], "header": "data-header" in a})
            elif "data-cell" in a and parent.rows:
                parent.rows[-1]["cells"].append("")
                self.cell = parent
            self.stack.append((None, tag))
            return
        if tag == "span" and parent is not None and parent.kind == "text":
            self.span = {"text": "", "style": a.get("data-style"), "color": a.get("data-color"), "action": a.get("data-action")}
            self.stack.append((None, tag))
            return
        if tag == "br" and parent is not None:
            parent.text.append("\n")
        if tag in VOID:
            # A void element has no end tag: it must not enter the stack, or the depth bookkeeping
            # that decides where an artboard ends is off by one for the rest of the document.
            return
        if kind in TYPES:
            node = Node(kind, {k[5:]: v for k, v in a.items() if k.startswith("data-")})
            if parent is None:
                self.roots.append(node)
            else:
                parent.children.append(node)
            self.stack.append((node, tag))
        else:
            self.stack.append((None, tag))

    def handle_endtag(self, tag):
        if tag in VOID:
            return
        if self.stack:
            node, _ = self.stack.pop()
            if self.cell is not None and node is None and tag == "span":
                self.cell = None
            if tag == "span" and self.span is not None:
                parent = next((n for n, _ in reversed(self.stack) if n is not None), None)
                if parent is not None and parent.kind == "text":
                    parent.spans.append(self.span)
                self.span = None
        if self.inside and self.depth_in is not None and len(self.stack) == self.depth_in:
            self.inside = False

    def handle_data(self, data):
        if not self.inside:
            return
        if self.span is not None:
            self.span["text"] += data
            return
        if self.cell is not None:
            self.cell.rows[-1]["cells"][-1] += data
            return
        node = next((n for n, _ in reversed(self.stack) if n is not None), None)
        if node is not None and node.kind in ("text", "button", "glyph", "photo"):
            node.text.append(data)


def clean(text: str) -> str:
    return " ".join(part.strip() for part in text.split("\n") if part.strip()).replace(" \n ", "\n") if "\n" not in text.strip() else "\n".join(p.strip() for p in text.strip().split("\n") if p.strip())


def action(deeplink):
    return {"type": "navigate", "deeplink": deeplink} if deeplink else None


def modifiers(a: dict) -> list:
    mods = []
    if "weight" in a:
        mods.append({"type": "weight", "value": float(a["weight"] or 1)})
    size = {}
    fill = a.get("fill")
    if fill in ("width", "both"):
        size["width"] = "Fill"
    if fill in ("height", "both"):
        size["height"] = "Fill"
    if "width" in a:
        size["widthDp"] = int(a["width"])
    if "height" in a:
        size["heightDp"] = int(a["height"])
    if "max-width" in a:
        size["maxWidthDp"] = int(a["max-width"])
    if size:
        mods.append({"type": "size", **size})
    return mods


def to_wire(node: Node) -> dict:
    a = node.attrs
    out = {"type": node.kind, "id": a.get("id")}
    mods = modifiers(a)
    if mods:
        out["modifiers"] = mods
    text = clean("".join(node.text))
    if node.kind == "spacer":
        out["type"] = "column"
        out.setdefault("modifiers", []).insert(0, {"type": "weight", "value": 1.0})
        out["children"] = []
        return out
    if node.kind in ("column", "row"):
        if "spacing" in a:
            out["spacing"] = int(a["spacing"])
        if action(a.get("action")):
            out["action"] = action(a["action"])
        out["children"] = [to_wire(c) for c in node.children]
    elif node.kind == "surface":
        for w in ("tone", "density", "align"):
            if w in a:
                out[w] = a[w]
        if "spacing" in a:
            out["spacing"] = int(a["spacing"])
        if "pinned" in a:
            out["pinned"] = True
        if action(a.get("action")):
            out["action"] = action(a["action"])
        out["children"] = [to_wire(c) for c in node.children]
    elif node.kind == "text":
        out["text"] = "" if node.spans else text
        if "style" in a:
            out["style"] = a["style"]
        if "color" in a:
            out["color"] = a["color"]
        if "max-lines" in a:
            out["maxLines"] = int(a["max-lines"])
        if node.spans:
            out["spans"] = [
                {k: v for k, v in {"text": s["text"], "style": s["style"], "color": s["color"], "action": action(s["action"])}.items() if v is not None}
                for s in node.spans
            ]
    elif node.kind == "button":
        out["text"] = text
        if "variant" in a:
            out["variant"] = a["variant"]
        out["action"] = action(a.get("action")) or {"type": "navigate", "deeplink": "?"}
    elif node.kind == "glyph":
        out["glyph"] = text
        for w in ("tone", "frame", "size", "color"):
            if w in a:
                out[w] = a[w]
    elif node.kind == "photo":
        out["caption"] = text
        if "size" in a:
            out["size"] = a["size"]
    elif node.kind == "screen_header":
        out["backAction"] = action(a.get("action")) or {"type": "navigate", "deeplink": "?"}
    elif node.kind == "table":
        out["rows"] = [{"cells": [c.strip() for c in r["cells"]], **({"header": True} if r["header"] else {})} for r in node.rows]
    return out


# --- the diff ------------------------------------------------------------------------------------

def index(tree: dict, path="", into=None):
    into = {} if into is None else into
    into[tree.get("id")] = (tree, path)
    for i, child in enumerate(tree.get("children", [])):
        index(child, f"{path}/{i}", into)
    return into


# What the wire leaves out when it is the default: the recording says nothing where the builder said
# nothing, and the canvas may say it out loud. Both sides are read through the same defaults.
DEFAULTS = {
    "surface": {"tone": "paper", "density": "card", "align": "start", "spacing": 0, "pinned": False},
    "glyph": {"tone": "peach", "frame": "rounded", "size": "medium"},
    "photo": {"size": "tile"},
    "column": {"spacing": 0},
    "row": {"spacing": 0},
}


def words(node: dict) -> dict:
    keep = {"type", "tone", "density", "align", "style", "color", "variant", "frame", "size", "spacing", "pinned", "maxLines"}
    out = {k: v for k, v in node.items() if k in keep}
    for k, v in DEFAULTS.get(node.get("type"), {}).items():
        out.setdefault(k, v)
    text = node.get("text") or node.get("glyph") or node.get("caption")
    if node.get("spans"):
        text = "".join(s.get("text", "") for s in node["spans"])
    if node.get("rows"):
        text = " | ".join(("# " if r.get("header") else "") + " · ".join(r.get("cells", [])) for r in node["rows"])
    if text is not None:
        out["text"] = " ".join(text.split())
    a = node.get("action") or node.get("backAction")
    if a:
        out["action"] = a.get("deeplink", a.get("type"))
    mods = node.get("modifiers") or []
    w = next((m for m in mods if m.get("type") == "weight"), None)
    if w:
        out["weight"] = w.get("value")
    s = next((m for m in mods if m.get("type") == "size"), None)
    if s:
        out["size_mod"] = json.dumps({k: v for k, v in s.items() if k != "type"}, sort_keys=True)
    out["children"] = [c.get("id") for c in node.get("children", [])]
    return out


def compare(canvas: dict, recorded: dict) -> list:
    rows = []
    a, b = index(canvas), index(recorded)
    for nid in a:
        if nid not in b:
            rows.append(("ONLY CANVAS", nid, a[nid][0].get("type"), ""))
    for nid in b:
        if nid not in a:
            rows.append(("ONLY WIRE", nid, b[nid][0].get("type"), ""))
    for nid in a:
        if nid not in b:
            continue
        wa, wb = words(a[nid][0]), words(b[nid][0])
        for key in sorted(set(wa) | set(wb)):
            if wa.get(key) != wb.get(key):
                rows.append(("DIFF", nid, key, f"canvas {wa.get(key)!r} · wire {wb.get(key)!r}"))
    return rows


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("html")
    parser.add_argument("--artboard", help="id (or data-artboard) of the element holding the screen; default: the whole document")
    parser.add_argument("--out", help="write the wire tree here")
    parser.add_argument("--compare", help="a recorded wire tree to diff against")
    args = parser.parse_args()
    with open(args.html, encoding="utf-8") as f:
        walker = Walker(args.artboard)
        walker.feed(f.read())
    if not walker.roots:
        sys.exit("no data-kompot root found" + (f" under #{args.artboard}" if args.artboard else ""))
    tree = to_wire(walker.roots[0])
    ids = [n.get("id") for n, _ in index(tree).values()]
    dupes = sorted({i for i in ids if ids.count(i) > 1 or i is None})
    if dupes:
        print(f"ids not unique or missing: {dupes}", file=sys.stderr)
    if args.out:
        with open(args.out, "w", encoding="utf-8") as f:
            json.dump(tree, f, ensure_ascii=False, indent=1)
        print(f"{len(ids)} nodes → {args.out}")
    if args.compare:
        with open(args.compare, encoding="utf-8") as f:
            recorded = json.load(f)
        rows = compare(tree, recorded)
        print("| status | id | field | detail |")
        print("| --- | --- | --- | --- |")
        for r in rows:
            print(f"| {r[0]} | {r[1]} | {r[2]} | {r[3]} |")
        print(f"\n{len(rows)} differences over {len(ids)} canvas nodes and {len(index(recorded))} wire nodes")
        return 1 if rows or dupes else 0
    if not args.out:
        print(json.dumps(tree, ensure_ascii=False, indent=1))
    return 1 if dupes else 0


if __name__ == "__main__":
    sys.exit(main())
