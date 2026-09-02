#!/usr/bin/env python3
"""Render one artboard of a Claude Design canvas (`.dc.html`) to a PNG and dump its layout boxes.

The canvas is HTML: a browser is its only faithful renderer. This opens the file in headless
Chromium through Playwright, lets the dc runtime (`support.js`) boot — it loads React from a CDN
and expands `<sc-for>` templates with the document's own `DCLogic` state, so a templated artboard
renders exactly as the design tool shows it — waits for the web fonts, and then, for the artboard
asked for:

  * writes `<out>/<id>.png`, the artboard's pixels at CSS scale 1 (1080×1920 stays 1080×1920);
  * writes `<out>/<id>.boxes.json`, one entry per visible element with its rect RELATIVE TO THE
    ARTBOARD and the computed style a token inventory needs (font size, weight, letter spacing,
    line height, colour, background, radius, padding). A TEXT LEAF — an element holding text and
    inline spans only — carries its text and the rect of the glyphs (a Range over its contents),
    which is what Compose reports for a text node; the element's own box, padding and all, is
    kept beside it as `box`. An `<image-slot>` carries its placeholder as text. Rects are in
    CSS px, which at scale 1 are the dp a Compose frame at density 1 uses.

The artboard is the largest direct child of the element with the given id: on a Claude Design
canvas the id sits on a wrapper holding a caption row and the frame, and the frame is the big one.
Pass `--frame` to name it yourself.

    canvas_frame.py design/kiosk.dc.html --artboard 1a --artboard 1b --out build/canvas

Requires `pip install playwright && playwright install chromium`; the CDN React and Google Fonts
need the network once (Chromium caches nothing between runs, so every run needs it).
"""
import argparse
import json
import os
import sys

try:
    from playwright.sync_api import sync_playwright
except ImportError:  # pragma: no cover - the message is the point
    sys.exit("canvas_frame.py needs Playwright: pip install playwright && playwright install chromium")

# Everything the DOM side knows about a box, evaluated inside the page. `own_text` is the text of the
# element's OWN text nodes, so a card does not repeat every string inside it: the diff matches leaves.
BOXES_JS = """
(frame) => {
  const origin = frame.getBoundingClientRect();
  const INLINE = new Set(['SPAN', 'B', 'I', 'EM', 'STRONG', 'BR', 'A', 'SMALL', 'SUP', 'SUB']);
  // A text leaf is an element whose children are text nodes and inline elements only: the runtime
  // renders a `{{ binding }}` as a span, and a <br> is a space, so "выбрано <span>2</span> из 6"
  // and "Собери<br>свой<br>боул" are one string each, the way Compose draws them.
  const isLeaf = (el) => {
    let hasText = false;
    for (const n of el.childNodes) {
      if (n.nodeType === Node.TEXT_NODE && n.textContent.trim()) hasText = true;
      else if (n.nodeType === Node.ELEMENT_NODE && !INLINE.has(n.tagName)) return false;
    }
    return hasText || (el.children.length > 0 && [...el.children].every((c) => INLINE.has(c.tagName) && c.textContent.trim()));
  };
  const leafText = (el) => {
    let s = '';
    for (const n of el.childNodes) {
      if (n.nodeType === Node.TEXT_NODE) s += n.textContent;
      else if (n.tagName === 'BR') s += ' ';
      else if (n.nodeType === Node.ELEMENT_NODE) s += ' ' + leafText(n) + ' ';
    }
    return s.replace(/\\s+/g, ' ').trim();
  };
  // The rect of the GLYPHS, not of the element: a chip's element box carries its padding, and the
  // Compose side reports the text node alone. A Range over the leaf's contents measures the ink.
  const textRect = (el) => {
    const range = document.createRange();
    range.selectNodeContents(el);
    const r = range.getBoundingClientRect();
    return (r.width > 0 && r.height > 0) ? r : el.getBoundingClientRect();
  };
  const out = [];
  const walk = (el, path) => {
    const r = el.getBoundingClientRect();
    if (r.width > 0 && r.height > 0) {
      const cs = getComputedStyle(el);
      const tag = el.tagName.toLowerCase();
      let text = null, tr = null;
      if (tag === 'image-slot') {
        text = el.getAttribute('placeholder') || null; tr = r;
      } else if (isLeaf(el)) {
        text = leafText(el) || null; tr = text ? textRect(el) : null;
      }
      const bg = cs.backgroundColor;
      out.push({
        path, tag, id: el.id || null,
        text,
        l: tr ? tr.left - origin.left : r.left - origin.left, t: tr ? tr.top - origin.top : r.top - origin.top,
        w: tr ? tr.width : r.width, h: tr ? tr.height : r.height,
        box: { l: r.left - origin.left, t: r.top - origin.top, w: r.width, h: r.height },
        style: {
          fontSize: parseFloat(cs.fontSize), fontWeight: cs.fontWeight,
          letterSpacing: cs.letterSpacing, lineHeight: cs.lineHeight,
          color: cs.color,
          background: (bg && bg !== 'rgba(0, 0, 0, 0)') ? bg : null,
          borderRadius: cs.borderRadius, padding: cs.padding, border: cs.borderTopWidth !== '0px' ? cs.borderTop : null,
        },
      });
      if (text !== null && tag !== 'image-slot') return;
    }
    let i = 0;
    for (const child of el.children) { walk(child, path + '/' + i); i += 1; }
  };
  walk(frame, '');
  return { width: origin.width, height: origin.height, boxes: out };
}
"""

FRAME_JS = """
(id) => {
  const wrapper = document.getElementById(id);
  if (!wrapper) return null;
  let best = null, area = 0;
  for (const child of wrapper.children) {
    const r = child.getBoundingClientRect();
    if (r.width * r.height > area) { area = r.width * r.height; best = child; }
  }
  if (!best) return null;
  best.setAttribute('data-canvas-frame', id);
  return { width: best.getBoundingClientRect().width, height: best.getBoundingClientRect().height };
}
"""


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("html", help="the .dc.html file; support.js and image-slot.js beside it")
    parser.add_argument("--artboard", action="append", required=True, help="id of an artboard wrapper (repeatable)")
    parser.add_argument("--frame", help="CSS selector of the frame inside the wrapper, or `self` when the id is on the frame")
    parser.add_argument("--out", default="build/canvas", help="where the png and boxes go")
    parser.add_argument("--settle", type=int, default=1500, help="ms to wait after fonts are ready, for the runtime to draw")
    args = parser.parse_args()

    html = os.path.abspath(args.html)
    if not os.path.exists(html):
        sys.exit(f"no such file: {html}")
    os.makedirs(args.out, exist_ok=True)

    with sync_playwright() as pw:
        browser = pw.chromium.launch()
        # A wide, tall viewport: the canvas lays artboards side by side, and a frame outside the
        # viewport still measures, but a lazy font may not load for text nobody could see.
        page = browser.new_page(viewport={"width": 2400, "height": 2200}, device_scale_factor=1)
        page.goto("file://" + html, wait_until="networkidle")
        page.wait_for_function("document.fonts.status === 'loaded'", timeout=30000)
        page.wait_for_timeout(args.settle)

        failures = 0
        for board in args.artboard:
            if args.frame:
                # `self`: the element with the id IS the frame; otherwise a selector inside it.
                wrapper = page.locator(f"[id='{board}']").first
                frame = wrapper if args.frame == "self" else wrapper.locator(args.frame).first
                size = frame.evaluate("el => { const r = el.getBoundingClientRect(); return {width: r.width, height: r.height}; }")
            else:
                size = page.evaluate(FRAME_JS, board)
                frame = page.locator(f"[data-canvas-frame='{board}']").first
            if not size:
                print(f"{board}: no artboard with that id", file=sys.stderr)
                failures += 1
                continue
            frame.scroll_into_view_if_needed()
            png = os.path.join(args.out, f"{board}.png")
            frame.screenshot(path=png)
            dump = frame.evaluate(BOXES_JS)
            dump["artboard"] = board
            dump["source"] = os.path.basename(html)
            with open(os.path.join(args.out, f"{board}.boxes.json"), "w", encoding="utf-8") as f:
                json.dump(dump, f, ensure_ascii=False, indent=1)
            texts = sum(1 for b in dump["boxes"] if b["text"])
            print(f"{board}: {int(size['width'])}×{int(size['height'])}, {len(dump['boxes'])} boxes, {texts} with text → {png}")
        browser.close()
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
