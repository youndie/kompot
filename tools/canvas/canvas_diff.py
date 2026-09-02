#!/usr/bin/env python3
"""Compare the layout of a canvas artboard with the layout of the Compose frame that implements it.

Both sides are boxes with text (`canvas_frame.py` for the DOM, `compose_boxes.py` for Compose).
Text is the key: a string the server sent is the same string the canvas shows, so every text leaf
on the canvas should have a twin in the frame at about the same place and about the same size.
Containers are not matched — they have no text of their own, and the canvas's `div` nesting has
nothing to do with the wire's — but a container that is wrong moves the texts inside it, and that
is what shows.

For every canvas text, in reading order, the nearest unmatched Compose text with the same words is
taken, and the report says how far it moved (dx, dy) and how much it changed size (dw, dh). Three
outcomes are flagged, and any of them fails the run:

  * PART     — no twin with the same words, but a frame string that CONTAINS the canvas string (or
               the other way round): a spanned text the canvas drew as two elements, a button whose
               label carries an icon. Informational; read it, it is usually right;
  * MISSING  — the canvas shows a string the frame does not (a node not sent, or sent unreadable);
  * EXTRA    — the frame shows a string the canvas does not (with --strict; otherwise informational);
  * OFF      — a twin further than --tolerance px away, or taller/shorter by more than it;
  * ZERO     — a twin with no height or no width: the layout crushed it, and a tap on it goes nowhere.

A photo slot (`<image-slot>`) is a frame on the canvas and a caption centred in a frame on the
wire, so it is compared by its centre; the caption's own size is not held against the frame's.

    canvas_diff.py build/canvas/1a.boxes.json build/canvas/welcome.compose.json --tolerance 24

The numbers are what a person then reads against the canvas; the tool finds the row, not the reason.
"""
import argparse
import json
import re
import sys

SPACE = re.compile(r"[\s  ]+")


def norm(text: str) -> str:
    return SPACE.sub(" ", text).strip().casefold()


def load(path: str) -> list:
    with open(path, encoding="utf-8") as f:
        data = json.load(f)
    return [dict(b, key=norm(b["text"]), slot=b.get("tag") == "image-slot") for b in data["boxes"] if b.get("text")]


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("canvas", help="boxes from canvas_frame.py")
    parser.add_argument("compose", help="boxes from compose_boxes.py")
    parser.add_argument("--tolerance", type=float, default=24.0, help="px a text may move or resize without a flag")
    parser.add_argument("--strict", action="store_true", help="a string on the frame that the canvas lacks fails too")
    parser.add_argument("--json", help="also write the rows as json")
    args = parser.parse_args()

    canvas = load(args.canvas)
    compose = load(args.compose)
    unmatched = list(range(len(compose)))
    rows = []
    for c in sorted(canvas, key=lambda b: (round(b["t"] / 8), b["l"])):
        candidates = [i for i in unmatched if compose[i]["key"] == c["key"]]
        partial = False
        if not candidates:
            # A frame string holding the canvas string, or held by it: spans and icon labels. The
            # twin is shared, not consumed, so both halves of a spanned text find it.
            partial = True
            candidates = [
                i for i in range(len(compose)) if len(c["key"]) >= 2 and (c["key"] in compose[i]["key"] or compose[i]["key"] in c["key"])
            ]
        if not candidates:
            rows.append({"text": c["text"], "status": "MISSING", "canvas": c, "compose": None})
            continue
        i = min(candidates, key=lambda j: abs(compose[j]["t"] - c["t"]) + abs(compose[j]["l"] - c["l"]))
        if not partial:
            unmatched.remove(i)
        elif i in unmatched:
            unmatched.remove(i)
        m = compose[i]
        if c["slot"]:
            # A photo slot on the canvas is a frame; on the wire it is a caption centred in one. So
            # a slot is compared by its centre, and its size is the frame's, not the caption's.
            dx = (m["l"] + m["w"] / 2) - (c["l"] + c["w"] / 2)
            dy = (m["t"] + m["h"] / 2) - (c["t"] + c["h"] / 2)
            dw, dh = 0.0, 0.0
        else:
            dx, dy, dw, dh = m["l"] - c["l"], m["t"] - c["t"], m["w"] - c["w"], m["h"] - c["h"]
        if m["w"] <= 0 or m["h"] <= 0:
            status = "ZERO"
        elif partial:
            status = "PART"
        elif max(abs(dx), abs(dy), abs(dh)) > args.tolerance:
            status = "OFF"
        else:
            status = "ok"
        rows.append({"text": c["text"], "status": status, "canvas": c, "compose": m, "dx": dx, "dy": dy, "dw": dw, "dh": dh})
    for i in unmatched:
        rows.append({"text": compose[i]["text"], "status": "EXTRA", "canvas": None, "compose": compose[i]})

    def cell(b):
        return "—" if b is None else f"{b['l']:.0f},{b['t']:.0f} {b['w']:.0f}×{b['h']:.0f}"

    print(f"| status | text | canvas x,y w×h | compose x,y w×h | dx | dy | dh |")
    print(f"| --- | --- | --- | --- | ---: | ---: | ---: |")
    for r in rows:
        text = (r["text"] or "")[:40].replace("|", "\\|")
        d = (f"{r['dx']:+.0f}", f"{r['dy']:+.0f}", f"{r['dh']:+.0f}") if "dx" in r else ("", "", "")
        print(f"| {r['status']} | {text} | {cell(r['canvas'])} | {cell(r['compose'])} | {d[0]} | {d[1]} | {d[2]} |")

    counts = {}
    for r in rows:
        counts[r["status"]] = counts.get(r["status"], 0) + 1
    print()
    print(", ".join(f"{k}: {v}" for k, v in sorted(counts.items())))
    if args.json:
        with open(args.json, "w", encoding="utf-8") as f:
            json.dump(rows, f, ensure_ascii=False, indent=1)
    failing = {"MISSING", "OFF", "ZERO"} | ({"EXTRA"} if args.strict else set())
    return 1 if any(r["status"] in failing for r in rows) else 0


if __name__ == "__main__":
    sys.exit(main())
