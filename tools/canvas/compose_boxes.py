#!/usr/bin/env python3
"""Turn a Compose semantics dump (`onRoot(useUnmergedTree = true).printToString()`) into layout boxes.

The dump is what a Compose UI test prints: one `Node #N at (l=…, t=…, r=…, b=…)px` line per node,
followed by its properties, `Text = '[…]'` among them. This keeps every node that carries text and
writes the same shape `canvas_frame.py` writes for the DOM, so `canvas_diff.py` can compare the two.

    compose_boxes.py client/build/semantics/welcome.txt --out build/canvas/welcome.compose.json

Get the dump from the product's own composition root at the product's own size — a tree rendered by
hand photographs a client nobody ships — for example:

    runDesktopComposeUiTest(width = 1080, height = 1920) {
        setContent { RecordedScreen("welcome") }
        waitForIdle()
        File("build/semantics/welcome.txt").writeText(onRoot(useUnmergedTree = true).printToString(Int.MAX_VALUE))
    }

Coordinates are px at the test's density (1 on the desktop runner), which is what the canvas's CSS
px are at scale 1.
"""
import argparse
import json
import re
import sys

NODE = re.compile(r"Node #(\d+) at \(l=([\d.-]+), t=([\d.-]+), r=([\d.-]+), b=([\d.-]+)\)px")
TEXT = re.compile(r"Text = '\[(.*?)\]'", re.S)


def parse(dump: str) -> dict:
    boxes = []
    matches = list(NODE.finditer(dump))
    for index, match in enumerate(matches):
        end = matches[index + 1].start() if index + 1 < len(matches) else len(dump)
        block = dump[match.end():end]
        text = TEXT.search(block)
        left, top, right, bottom = (float(match.group(i)) for i in range(2, 6))
        boxes.append(
            {
                "node": int(match.group(1)),
                "text": text.group(1).replace("\n", " ") if text else None,
                "l": left,
                "t": top,
                "w": right - left,
                "h": bottom - top,
            }
        )
    root = boxes[0] if boxes else {"w": 0, "h": 0}
    return {"width": root["w"], "height": root["h"], "boxes": boxes}


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("dump", help="the printToString() output")
    parser.add_argument("--out", required=True, help="where the boxes json goes")
    args = parser.parse_args()
    with open(args.dump, encoding="utf-8") as f:
        result = parse(f.read())
    result["source"] = args.dump
    with open(args.out, "w", encoding="utf-8") as f:
        json.dump(result, f, ensure_ascii=False, indent=1)
    texts = sum(1 for b in result["boxes"] if b["text"])
    print(f"{args.dump}: {int(result['width'])}×{int(result['height'])}, {len(result['boxes'])} nodes, {texts} with text → {args.out}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
