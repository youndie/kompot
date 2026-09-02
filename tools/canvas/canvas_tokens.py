#!/usr/bin/env python3
"""Inventory the tokens a canvas uses, and hold a named tokens.json to it.

A design canvas spells everything as hex and px. A kompot screen spells the same things as words:
colour tokens, typography tokens, surface densities. The distance between the two is a table
somebody types by hand — sixteen colours, fifteen type styles, a dozen insets — and a table typed
by hand drifts the day the canvas changes.

This reads the `*.boxes.json` files `canvas_frame.py` writes (every element with its computed
style) and collects what is DISTINCT:

  * colours — every text colour, fill and border, as hex (alpha kept), with where it is used;
  * type styles — every (size, weight, letter spacing, line height) a text leaf is set in;
  * surfaces — every (radius, padding, border) an element with a fill has.

Two modes:

    canvas_tokens.py build/canvas/*.boxes.json --report build/canvas/inventory.md --draft build/canvas/tokens.draft.json

writes a report a person reads and a DRAFT tokens.json whose entries are auto-named (`c_e24916`,
`t_210_900`, `s_44_28x36`): rename the keys, add `m3` roles, drop what the product does not keep,
and commit it as `design/tokens.json`. Then

    canvas_tokens.py build/canvas/*.boxes.json --check design/tokens.json

fails with the values the canvas uses that the named file has no entry for — the guard that a
new hex on the canvas is a named decision, not a drift. `canvas_generate.py` turns the named file
into Kotlin for both ends.

Values are matched exactly. A canvas that says #FFF8F5 in one place and #FFF8F6 in another has
two colours, and the report will show both; that is the canvas's inconsistency to see, not the
tool's to hide.
"""
import argparse
import collections
import glob
import json
import re
import sys

RGB = re.compile(r"rgba?\(\s*(\d+),\s*(\d+),\s*(\d+)(?:,\s*([\d.]+))?\s*\)")
# A leaf that is emoji only: drawn by the platform's colour font, so its `color` and its type style
# are nobody's decision and would inventory as a black text style nobody set.
EMOJI = re.compile(r"^[\U0001F000-\U0001FAFF\u2600-\u27BF\uFE0F\u200D\s]+$")
PX = re.compile(r"(-?[\d.]+)px")


def hex_of(css: str):
    """`rgb(226, 73, 22)` → `#E24916`; `rgba(255, 248, 245, 0.18)` → `#FFF8F5@18`."""
    if not css:
        return None
    m = RGB.match(css.strip())
    if not m:
        return css
    r, g, b = (int(m.group(i)) for i in (1, 2, 3))
    alpha = m.group(4)
    out = f"#{r:02X}{g:02X}{b:02X}"
    if alpha is not None and float(alpha) < 1:
        out += f"@{round(float(alpha) * 100)}"
    return out


def px(value: str):
    """`-10px` → -10.0; `normal` → None."""
    if not value:
        return None
    m = PX.match(value.strip())
    return round(float(m.group(1)), 1) if m else None


def padding_of(css: str) -> list:
    """`28px 36px` → [28, 36]; `24px 28px 26px` → [24, 28, 26]; `0px` → [0]."""
    parts = [px(p) for p in css.split()]
    parts = [int(p) if p is not None and p == int(p) else p for p in parts]
    if len(parts) == 4 and parts[0] == parts[2] and parts[1] == parts[3]:
        parts = parts[:2]
    if len(parts) == 2 and parts[0] == parts[1]:
        parts = parts[:1]
    return parts


def radius_of(css: str):
    """`44px` → 44; `48px 48px 48px 20px` → [48, 48, 48, 20]; `50%` → "50%"."""
    parts = css.split()
    if len(parts) == 1:
        v = px(parts[0])
        return int(v) if v is not None and v == int(v) else parts[0]
    return [int(px(p)) if px(p) is not None else p for p in parts]


def border_of(css: str):
    """`2px solid rgb(224, 204, 194)` → (2, "#E0CCC2")."""
    if not css:
        return None
    width = px(css)
    colour = RGB.search(css)
    return (int(width) if width else None, hex_of(colour.group(0)) if colour else None)


def num(v):
    return int(v) if isinstance(v, float) and v == int(v) else v


def inventory(files: list) -> dict:
    colours = collections.defaultdict(lambda: {"text": 0, "fill": 0, "border": 0, "samples": []})
    types = collections.defaultdict(lambda: {"count": 0, "samples": []})
    surfaces = collections.defaultdict(lambda: {"count": 0, "sizes": [], "samples": []})

    def sample(bucket, text, limit=4):
        if text and text not in bucket and len(bucket) < limit:
            bucket.append(text)

    for path in files:
        with open(path, encoding="utf-8") as f:
            data = json.load(f)
        board = data.get("artboard", path)
        for box in data["boxes"]:
            st = box.get("style", {})
            text = box.get("text")
            where = f"{board}:{(text or box.get('tag'))[:30]}"
            if text and box.get("tag") != "image-slot" and not EMOJI.match(text):
                c = hex_of(st.get("color"))
                if c:
                    colours[c]["text"] += 1
                    sample(colours[c]["samples"], where)
                key = (num(st.get("fontSize")), str(st.get("fontWeight")), px(st.get("letterSpacing")) or 0, px(st.get("lineHeight")))
                types[key]["count"] += 1
                sample(types[key]["samples"], where)
            fill = hex_of(st.get("background"))
            border = border_of(st.get("border"))
            if fill:
                colours[fill]["fill"] += 1
                sample(colours[fill]["samples"], where)
            if border and border[1]:
                colours[border[1]]["border"] += 1
                sample(colours[border[1]]["samples"], where)
            if fill or border:
                key = (json.dumps(radius_of(st.get("borderRadius", "0px"))), json.dumps(padding_of(st.get("padding", "0px"))), border[0] if border else 0)
                surfaces[key]["count"] += 1
                b = box.get("box", box)
                size = f"{int(b['w'])}×{int(b['h'])}"
                if size not in surfaces[key]["sizes"] and len(surfaces[key]["sizes"]) < 4:
                    surfaces[key]["sizes"].append(size)
                sample(surfaces[key]["samples"], where)
    return {"colours": colours, "types": types, "surfaces": surfaces}


def draft(inv: dict) -> dict:
    out = {"colors": {}, "type": {}, "surfaces": {}}
    for hexv, use in sorted(inv["colours"].items(), key=lambda kv: -(kv[1]["text"] + kv[1]["fill"] + kv[1]["border"])):
        key = "c_" + hexv.lstrip("#").lower().replace("@", "_a")
        out["colors"][key] = {"hex": hexv, "m3": None, "uses": {k: use[k] for k in ("text", "fill", "border") if use[k]}, "samples": use["samples"]}
    for (size, weight, spacing, line), use in sorted(inv["types"].items(), key=lambda kv: (-(kv[0][0] or 0), kv[0][1])):
        key = f"t_{size}_{weight}"
        if key in out["type"]:
            key += f"_{str(spacing).replace('-', 'm').replace('.', 'p')}"
        entry = {"size": size, "weight": int(weight) if str(weight).isdigit() else weight, "letterSpacing": spacing, "m3": None, "count": use["count"], "samples": use["samples"]}
        if line is not None:
            entry["lineHeight"] = num(line)
        out["type"][key] = entry
    for (radius, padding, border), use in sorted(inv["surfaces"].items(), key=lambda kv: -kv[1]["count"]):
        r, p = json.loads(radius), json.loads(padding)
        key = "s_" + (str(r) if not isinstance(r, list) else "x".join(map(str, r))).replace("%", "pct") + "_" + "x".join(map(str, p))
        entry = {"radius": r, "padding": p, "count": use["count"], "sizes": use["sizes"], "samples": use["samples"]}
        if border:
            entry["border"] = border
        out["surfaces"][key] = entry
    return out


def report(inv: dict) -> str:
    lines = ["# Canvas token inventory", ""]
    lines += ["## Colours", "", "| hex | text | fill | border | where |", "| --- | ---: | ---: | ---: | --- |"]
    for hexv, use in sorted(inv["colours"].items(), key=lambda kv: -(kv[1]["text"] + kv[1]["fill"] + kv[1]["border"])):
        lines.append(f"| `{hexv}` | {use['text'] or ''} | {use['fill'] or ''} | {use['border'] or ''} | {', '.join(use['samples'])} |")
    lines += ["", "## Type styles", "", "| size | weight | spacing | line | uses | where |", "| ---: | ---: | ---: | ---: | ---: | --- |"]
    for (size, weight, spacing, line), use in sorted(inv["types"].items(), key=lambda kv: (-(kv[0][0] or 0), kv[0][1])):
        lines.append(f"| {size} | {weight} | {spacing} | {num(line) if line is not None else '—'} | {use['count']} | {', '.join(use['samples'])} |")
    lines += ["", "## Surfaces", "", "| radius | padding | border | uses | sizes | where |", "| --- | --- | ---: | ---: | --- | --- |"]
    for (radius, padding, border), use in sorted(inv["surfaces"].items(), key=lambda kv: -kv[1]["count"]):
        lines.append(f"| {radius} | {padding} | {border or ''} | {use['count']} | {', '.join(use['sizes'])} | {', '.join(use['samples'])} |")
    return "\n".join(lines) + "\n"


def check(inv: dict, named: dict) -> list:
    """Every value on the canvas has a named entry; the list of those that do not.

    An entry may carry `absorbs`, the canvas values it deliberately stands for: a 30/600 and a
    30/700 the product draws as one label style, a decorative circle's darker orange the product
    does not paint. Absorbing is a decision written down, which is what the check is for.
    """
    problems = []
    known_hex = {e["hex"] for e in named.get("colors", {}).values()}
    known_hex |= {h for e in named.get("colors", {}).values() for h in e.get("absorbs", [])}
    for hexv in inv["colours"]:
        if hexv not in known_hex:
            problems.append(f"colour {hexv} has no name (used {sum(v for k, v in inv['colours'][hexv].items() if k != 'samples')}×: {', '.join(inv['colours'][hexv]['samples'][:2])})")
    known_type = {(num(e["size"]), str(e["weight"]), e.get("letterSpacing", 0) or 0) for e in named.get("type", {}).values()}
    known_type |= {
        (num(a["size"]), str(a["weight"]), a.get("letterSpacing", 0) or 0)
        for e in named.get("type", {}).values()
        for a in e.get("absorbs", [])
    }
    for (size, weight, spacing, _line) in inv["types"]:
        if (num(size), str(weight), spacing) not in known_type:
            problems.append(f"type {size}/{weight} spacing {spacing} has no name")
    known_surface = {(json.dumps(e["radius"]), json.dumps(e["padding"])) for e in named.get("surfaces", {}).values()}
    known_surface |= {
        (json.dumps(a["radius"]), json.dumps(a["padding"]))
        for e in named.get("surfaces", {}).values()
        for a in e.get("absorbs", [])
    }
    for (radius, padding, _border) in inv["surfaces"]:
        if (radius, padding) not in known_surface:
            problems.append(f"surface radius {radius} padding {padding} has no name")
    return problems


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("boxes", nargs="+", help="*.boxes.json from canvas_frame.py (globs allowed)")
    parser.add_argument("--report", help="write the inventory as markdown here")
    parser.add_argument("--draft", help="write an auto-named tokens.json here, to be renamed by hand")
    parser.add_argument("--check", help="a named tokens.json; fail on canvas values it has no entry for")
    args = parser.parse_args()
    files = [f for pattern in args.boxes for f in sorted(glob.glob(pattern))]
    if not files:
        sys.exit("no boxes files")
    inv = inventory(files)
    if args.report:
        with open(args.report, "w", encoding="utf-8") as f:
            f.write(report(inv))
    if args.draft:
        with open(args.draft, "w", encoding="utf-8") as f:
            json.dump(draft(inv), f, ensure_ascii=False, indent=1)
    print(f"{len(files)} artboards: {len(inv['colours'])} colours, {len(inv['types'])} type styles, {len(inv['surfaces'])} surfaces")
    if args.check:
        with open(args.check, encoding="utf-8") as f:
            problems = check(inv, json.load(f))
        for p in problems:
            print("  " + p)
        return 1 if problems else 0
    if not (args.report or args.draft):
        print(report(inv))
    return 0


if __name__ == "__main__":
    sys.exit(main())
