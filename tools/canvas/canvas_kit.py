#!/usr/bin/env python3
"""Build a design-system kit for Claude Design out of a named `tokens.json`.

A canvas drawn in raw hex and px has to be read by a person to become a kompot tree. A canvas drawn
with the product's WORDS — a surface at `option` density in the `brand` tone, a text in
`display_large` coloured `on_primary` — can be read by a script (`canvas_tree.py`). This writes the
kit that makes such a canvas possible:

  * `kompot.js` — applies the numbers a node carries (`data-spacing`, `data-max-width`, `data-width`,
    `data-height`, `data-max-lines`) as styles; the sheet stays classes, the markup stays words;
  * `kompot.css` — one class per word: `[data-kompot=surface][data-tone=brand]`,
    `[data-density=card]`, `[data-style=display_large]`, `[data-variant=pay]`, … with the tokens'
    values, so a designer who uses the attributes gets the product's look for free and a screen
    that looks right IS a screen the converter can read;
  * `cards/*.html` — preview cards for the Design System pane (first line `<!-- @dsCard … -->`):
    colours, the type scale, tone × density, buttons, photo and glyph words;
  * `README.md` — the markup contract, in the designer's language;
  * `examples/*.html` — copied from `--examples`, artboards written in the vocabulary.

    canvas_kit.py design/tokens.json --prefix boulab --title "БОУЛ ЛАБ" --examples design/kit --out build/kit

The markup contract (what `canvas_tree.py` reads):

    <div data-kompot="column|row|surface|text|button|glyph|photo|screen_header|spacer" data-id="…" …>

    column, row     data-spacing  data-fill="width|height|both"  data-weight  data-max-width  data-action
    surface         data-tone  data-density  data-spacing  data-align="start|center"  data-rule  data-pinned  data-weight  data-action
    text            data-style  data-color  data-max-width  data-max-lines; <span data-style data-color data-action> for spans
    button          data-variant  data-action  data-width  data-height
    glyph           data-tone  data-frame="disc|rounded|none"  data-size="small|medium|large"  data-color
    photo           data-size="thumb|tile|square|hero"; the text is the caption
    screen_header   data-action (the way back)
    spacer          an empty column with weight 1
    table           <div data-row [data-header]><span data-cell>…</span>…</div> per row

    data-action is a deeplink; `data-id` must be unique on the screen (the conformance walk checks it).
"""
import argparse
import glob
import json
import os
import shutil
import sys


def css_color(hexv: str) -> str:
    base, _, alpha = hexv.partition("@")
    if not alpha:
        return base
    r, g, b = (int(base[i:i + 2], 16) for i in (1, 3, 5))
    return f"rgba({r}, {g}, {b}, {int(alpha) / 100:.2f})"


def m3_snake(role: str) -> str:
    return "".join("_" + c.lower() if c.isupper() else c for c in role)


def roles_of(entry: dict) -> list:
    roles = entry.get("m3")
    return [roles] if isinstance(roles, str) else list(roles or [])


def padding_css(p) -> str:
    p = p if isinstance(p, list) else [p]
    return " ".join(f"{v}px" for v in p)


def radius_css(r) -> str:
    if r in ("pill", "50%"):
        return "999px"
    if isinstance(r, list):
        r = max(r)
    return f"{int(r)}px"


def build_css(tokens: dict, prefix: str) -> str:
    colors = tokens["colors"]
    var = {}  # token key (product name or m3 snake role) → css var name
    lines = [f"/* GENERATED from tokens.json by kompot tools/canvas/canvas_kit.py — the {prefix} vocabulary as CSS. */", ":root {"]
    for name, e in colors.items():
        lines.append(f"  --k-{name}: {css_color(e['hex'])};")
        var[name] = f"var(--k-{name})"
        for role in roles_of(e):
            key = m3_snake(role)
            lines.append(f"  --k-{key}: var(--k-{name});")
            var[key] = f"var(--k-{key})"
    lines.append("}")
    lines += [
        "",
        "/* layout: the standard nodes */",
        "[data-kompot] { box-sizing: border-box; min-width: 0; }",
        "[data-kompot=column] { display: flex; flex-direction: column; align-items: stretch; }",
        "[data-kompot=row] { display: flex; flex-direction: row; align-items: center; }",
        "[data-kompot=spacer] { flex: 1 1 0; }",
        "[data-weight] { flex: 1 1 0; }",
        "[data-fill=width], [data-fill=both] { width: 100%; }",
        "[data-fill=height], [data-fill=both] { height: 100%; }",
        "[data-kompot=text] { white-space: pre-line; }",
        "[data-action] { cursor: pointer; }",
        "",
        "/* the screen: a kiosk artboard */",
        f".k-screen {{ width: 1080px; height: 1920px; overflow: hidden; background: {var.get('background', var.get('paper', '#fff'))}; color: {var.get('on_background', '#000')}; font-family: '{tokens.get('families', {}).get('base', 'Roboto Flex')}', system-ui, sans-serif; position: relative; }}",
        ".k-screen > [data-kompot=column] { height: 100%; }",
        "[data-kompot=screen_header] { position: absolute; top: 48px; left: 48px; z-index: 1; }",
        "[data-kompot=table] { display: flex; flex-direction: column; width: 100%; border: 1px solid var(--k-outline_variant, #ddd); border-radius: 12px; overflow: hidden; font-size: 28px; }",
        "[data-kompot=table] [data-row] { display: flex; padding: 12px 16px; border-top: 1px solid var(--k-outline_variant, #ddd); }",
        "[data-kompot=table] [data-row]:first-child { border-top: none; }",
        "[data-kompot=table] [data-row][data-header] { font-weight: 800; background: var(--k-surface_variant, #eee); }",
        "[data-kompot=table] [data-cell] { flex: 1 1 0; }",
        "[data-kompot=unknown] { border: 2px dashed #B00020; color: #B00020; padding: 12px; font-size: 24px; }",
        "/* a row whose children ALL carry a weight is a grid row: its cells are as tall as the tallest */",
        "[data-kompot=row]:not(:has(> :not([data-weight]))) { align-items: stretch; }",
        "[data-pinned] { margin-top: auto; }",
        "",
        "/* tones: what a surface is painted with */",
    ]
    tones = tokens.get("tones", {})
    surfaces = tokens.get("surfaces", {})
    for tone, e in tones.items():
        if "container" not in e:
            lines.append(f"[data-kompot=surface][data-tone={tone}] {{ background: transparent; }}")
            continue
        lines.append(f"[data-kompot=surface][data-tone={tone}] {{ background: {var[e['container']]}; color: {var[e['content']]}; }}")
        outline, soft = e.get("outline"), e.get("outlineSoft")
        for word, s in surfaces.items():
            kind = s.get("outline")
            if not kind or not outline:
                continue
            colour = var[soft] if (kind == "soft" and soft) else var[outline]
            lines.append(f"[data-kompot=surface][data-tone={tone}][data-density={word}] {{ border: 2px solid {colour}; }}")
    lines += ["", "/* densities: shape and inset per surface word */"]
    for word, s in surfaces.items():
        if word.startswith("button_") or word.startswith("glyph_"):
            continue
        fill = "width: 100%;" if word in ("screen", "band", "body", "card", "tile", "plain") else ""
        grow = " display: flex; flex-direction: column; height: 100%;" if word in ("screen", "body") else " display: flex; flex-direction: column;"
        clip = " overflow: hidden;" if s.get("outline") == "soft" else ""  # a card clips the photo that bleeds to its edge
        lines.append(f"[data-kompot=surface][data-density={word}] {{ border-radius: {radius_css(s['radius'])}; padding: {padding_css(s['padding'])}; {fill}{grow}{clip} }}")
    lines += ["[data-kompot=surface][data-align=center] { align-items: center; text-align: center; }",
              "/* data-spacing, data-max-width, data-width, data-height are numbers on the node: kompot.js applies them */"]
    families = tokens.get("families", {})
    lines += ["", "/* type: the scale and the product's own styles */"]
    for name, e in tokens.get("type", {}).items():
        key = m3_snake(e["m3"]) if e.get("m3") else name
        line = e.get("lineHeight", round(e["size"] * 1.2))
        fam = families.get(e.get("family", "base"))
        extra = (f" font-family: '{fam}', sans-serif;" if fam and e.get("family", "base") != "base" else "") + (" text-decoration: line-through;" if e.get("decoration") == "line-through" else "")
        lines.append(f"[data-style={key}] {{ font-size: {e['size']}px; font-weight: {e['weight']}; line-height: {line}px; letter-spacing: {e.get('letterSpacing', 0)}px;{extra} }}")
    rules = tokens.get("rules")
    if rules:
        lw, aw = rules.get("lineWidth", 1), rules.get("accentWidth", 6)
        line_c, acc_c = var[rules.get("line", "outline_variant")], var[rules.get("accent", "primary")]
        lines += ["", "/* rules: hairlines and the accent edge, words on a surface */",
                  f"[data-rule=above] {{ border-top: {lw}px solid {line_c}; }}",
                  f"[data-rule=below] {{ border-bottom: {lw}px solid {line_c}; }}",
                  f"[data-rule=around] {{ border-top: {lw}px solid {line_c}; border-bottom: {lw}px solid {line_c}; }}",
                  f"[data-rule=start] {{ border-left: {lw}px solid {line_c}; }}",
                  f"[data-rule=accent] {{ border-left: {aw}px solid {acc_c}; }}",
                  f"[data-rule=between] > [data-kompot]:not(:first-child) {{ border-top: {lw}px solid {line_c}; }}",
                  f"[data-rule=dashed] {{ border: {lw}px dashed {var.get('outline', line_c)}; }}"]
    lines += ["", "/* colour tokens on text and glyphs */"]
    for key, v in var.items():
        lines.append(f"[data-color={key}] {{ color: {v}; }}")
    lines += ["", "/* buttons: emphasis words */", "[data-kompot=button] { display: inline-flex; align-items: center; justify-content: center; border: none; white-space: nowrap; }"]
    for variant, b in tokens.get("buttons", {}).items():
        s = surfaces[b["surface"]]
        size = f" width: {b['size']}px; height: {b['size']}px; padding: 0;" if b.get("size") else f" padding: {padding_css(s['padding'])};"
        border = f" border: 2px solid {var[b['outline']]};" if b.get("outline") else ""
        t = tokens["type"].get(b["type"], {})
        weight = 600 if b.get("size") else t.get("weight", 700)
        lines.append(f"[data-kompot=button][data-variant={variant}] {{ background: {var[b['container']]}; color: {var[b['content']]}; border-radius: {radius_css(s['radius'])};{size}{border} font-size: {t.get('size', 30)}px; font-weight: {weight}; }}")
    card_pad = surfaces.get("card", {}).get("padding", [0])
    cp = card_pad if isinstance(card_pad, list) else [card_pad]
    top, side = (cp[0], cp[1] if len(cp) > 1 else cp[0])
    lines += ["", "/* a leading tile photo on a card bleeds to the card's edge, as the client draws it */",
              f"[data-density=card] > [data-kompot=photo][data-size=tile]:first-child {{ margin: -{top}px -{side}px 0; width: calc(100% + {2 * side}px); }}"]
    back = tokens.get("buttons", {}).get("back")
    if back:
        bs = surfaces[back["surface"]]
        lines += ["", f"[data-kompot=screen_header] {{ display: flex; align-items: center; justify-content: center; width: {back['size']}px; height: {back['size']}px; border-radius: {radius_css(bs['radius'])}; background: {var[back['container']]}; color: {var[back['content']]}; font-size: 44px; font-weight: 600;" + (f" border: 2px solid {var[back['outline']]};" if back.get("outline") else "") + " }"]
    lines += ["", "/* photos: slots the kiosk has not loaded */", f"[data-kompot=photo] {{ display: flex; align-items: center; justify-content: center; background: {var.get('sand', '#eee')}; color: {var.get('on_surface_variant', '#666')}; font-size: 28px; font-weight: 700; text-align: center; flex: none; overflow: hidden; }}"]
    for word, p in tokens.get("photos", {}).items():
        w = f"width: {p['width']}px;" if p.get("width") else "width: 100%;"
        lines.append(f"[data-kompot=photo][data-size={word}] {{ {w} height: {p['height']}px; border-radius: {p.get('radius', 0)}px; }}")
    lines += ["", "/* glyphs: a mark or an emoji in a frame */", "[data-kompot=glyph] { display: inline-flex; align-items: center; justify-content: center; font-weight: 900; flex: none; }"]
    for tone, e in tones.items():
        if "container" in e:
            lines.append(f"[data-kompot=glyph][data-tone={tone}] {{ background: {var[e['container']]}; color: {var[e['content']]}; }}")
    for word, g in tokens.get("glyphs", {}).get("sizes", {}).items():
        lines.append(f"[data-kompot=glyph][data-size={word}] {{ width: {g['box']}px; height: {g['box']}px; font-size: {g['glyph']}px; }}")
        lines.append(f"[data-kompot=glyph][data-size={word}][data-frame=none] {{ width: auto; height: auto; background: none; font-size: {g['bare']}px; }}")
    lines.append("[data-kompot=glyph][data-frame=disc] { border-radius: 50%; }")
    lines.append("[data-kompot=glyph][data-frame=rounded] { border-radius: 33%; }")
    return "\n".join(lines) + "\n"


KOMPOT_JS = """// GENERATED by kompot tools/canvas/canvas_kit.py. The numbers a node carries — a gap, a cap, a fixed
// size — become styles here, so the markup stays the wire's words and the sheet stays classes.
(function () {
  function apply() {
    document.querySelectorAll('[data-spacing]').forEach(function (el) { el.style.gap = el.dataset.spacing + 'px'; });
    document.querySelectorAll('[data-max-width]').forEach(function (el) { el.style.maxWidth = el.dataset.maxWidth + 'px'; });
    document.querySelectorAll('[data-width]').forEach(function (el) { el.style.width = el.dataset.width + 'px'; el.style.flex = 'none'; });
    document.querySelectorAll('[data-height]').forEach(function (el) { el.style.height = el.dataset.height + 'px'; });
    document.querySelectorAll('[data-max-lines]').forEach(function (el) {
      el.style.display = '-webkit-box'; el.style.webkitBoxOrient = 'vertical';
      el.style.webkitLineClamp = el.dataset.maxLines; el.style.overflow = 'hidden';
    });
  }
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', apply); else apply();
  new MutationObserver(apply).observe(document.documentElement, { childList: true, subtree: true });
})();
"""


FONT_LINK = "https://fonts.googleapis.com/css2?family=Roboto+Flex:opsz,wght,wdth@8..144,100..1000,25..151&display=swap"


def card(group: str, title: str, body: str, css: str, width: int = 1080) -> str:
    return (
        f'<!-- @dsCard group="{group}" name="{title}" viewport="{width}" -->\n'
        f"<!DOCTYPE html><html><head><meta charset=\"utf-8\"><title>{title}</title>\n"
        f"<link rel=\"preconnect\" href=\"https://fonts.googleapis.com\"><link href=\"{FONT_LINK}\" rel=\"stylesheet\">\n"
        f"<script>{KOMPOT_JS}</script>\n"
        f"<style>{css}\nbody {{ margin: 0; padding: 40px; background: #E8DED8; font-family: 'Roboto Flex', system-ui, sans-serif; }}\n"
        f".k-card {{ display: flex; flex-direction: column; gap: 24px; }} .k-label {{ font-size: 22px; font-weight: 600; color: #5C443B; }}\n"
        f".k-row {{ display: flex; flex-wrap: wrap; gap: 24px; align-items: flex-start; }} .k-swatch {{ width: 200px; }} .k-swatch i {{ display: block; height: 96px; border-radius: 24px; border: 1px solid rgba(0,0,0,.08); }}</style></head>\n"
        f"<body><div class=\"k-card\">{body}</div></body></html>\n"
    )


def build_cards(tokens: dict, css: str, title: str) -> dict:
    cards = {}
    sw = []
    for name, e in tokens["colors"].items():
        roles = ", ".join(m3_snake(r) for r in roles_of(e)) or "—"
        sw.append(f"<div class=\"k-swatch\"><i style=\"background:{css_color(e['hex'])}\"></i><div class=\"k-label\">{name}<br><small>{e['hex']} · {roles}</small></div></div>")
    cards["colors.html"] = card(title, "Colours", f"<div class=\"k-label\">data-color=… on text and glyphs; a tone names these for a surface</div><div class=\"k-row\">{''.join(sw)}</div>", css)
    ty = []
    for name, e in tokens["type"].items():
        key = m3_snake(e["m3"]) if e.get("m3") else name
        ty.append(f"<div><div class=\"k-label\">data-style=\"{key}\" · {e['size']}/{e['weight']}</div><div data-kompot=\"text\" data-style=\"{key}\" data-color=\"on_background\">Собери свой боул</div></div>")
    cards["type.html"] = card(title, "Type scale", "".join(ty), css)
    rows = []
    for tone in tokens.get("tones", {}):
        cells = []
        for word in ("card", "tile", "chip", "tag", "pill", "segment", "option", "badge"):
            cells.append(f"<div data-kompot=\"surface\" data-tone=\"{tone}\" data-density=\"{word}\" data-spacing=\"4\" style=\"min-width:160px\"><div data-kompot=\"text\" data-style=\"label_large\">{word}</div></div>")
        # The ground a tone is shown on: the brand for glass (it is paper over the brand), the canvas
        # grey for the rest — paper and bare would vanish on paper.
        ground = css_color(tokens["colors"]["brand"]["hex"]) if tone == "glass" and "brand" in tokens["colors"] else "#E8DED8"
        rows.append(f"<div class=\"k-label\">data-tone=\"{tone}\"</div><div class=\"k-row\" style=\"background:{ground};padding:16px;border-radius:24px\">{''.join(cells)}</div>")
    cards["surfaces.html"] = card(title, "Surfaces: tone × density", "".join(rows), css, width=1400)
    bt = "".join(f"<div><div class=\"k-label\">data-variant=\"{v}\"</div><div data-kompot=\"button\" data-variant=\"{v}\">{'+' if b.get('size') else 'К оплате'}</div></div>" for v, b in tokens.get("buttons", {}).items())
    cards["buttons.html"] = card(title, "Buttons", f"<div class=\"k-row\">{bt}</div>", css)
    ph = "".join(f"<div><div class=\"k-label\">photo data-size=\"{w}\"</div><div data-kompot=\"photo\" data-size=\"{w}\" style=\"max-width:475px\">Фото</div></div>" for w in tokens.get("photos", {}))
    gl = "".join(f"<div><div class=\"k-label\">glyph {s} / {f}</div><div data-kompot=\"glyph\" data-tone=\"peach\" data-frame=\"{f}\" data-size=\"{s}\">🍜</div></div>" for s in tokens.get("glyphs", {}).get("sizes", {}) for f in tokens.get("glyphs", {}).get("frames", []))
    cards["media.html"] = card(title, "Photo and glyph", f"<div class=\"k-row\">{ph}</div><div class=\"k-row\">{gl}</div>", css)
    return cards


def build_readme(tokens: dict, title: str) -> str:
    tones = ", ".join(f"`{t}`" for t in tokens.get("tones", {}))
    densities = ", ".join(f"`{w}`" for w in tokens.get("surfaces", {}) if not w.startswith(("button_", "glyph_")))
    styles = ", ".join(f"`{m3_snake(e['m3']) if e.get('m3') else n}`" for n, e in tokens.get("type", {}).items())
    colors = ", ".join(f"`{n}`" for n in tokens["colors"]) + ", " + ", ".join(sorted({f"`{m3_snake(r)}`" for e in tokens["colors"].values() for r in roles_of(e)}))
    variants = ", ".join(f"`{v}`" for v in tokens.get("buttons", {}))
    return f"""# {title} — словарь kompot для канваса

Экран этого продукта на проводе — дерево из нескольких типов узлов со **словами** на них. Макет,
нарисованный этими же словами, читается скриптом (`canvas_tree.py`) и становится черновиком
серверного дерева; макет, нарисованный в hex и px, читается человеком. Подключите `./kompot.css`
и размечайте узлы атрибутами — стили придут сами.

```html
<link rel="stylesheet" href="./kompot.css">
<script src="./kompot.js"></script>
<div class="k-screen">
  <div data-kompot="column" data-id="welcome" data-fill="both">
    <div data-kompot="surface" data-id="welcome-page" data-tone="brand" data-density="screen" data-weight="1">
      <div data-kompot="text" data-id="welcome-headline" data-style="display_large" data-color="on_primary">Собери свой боул</div>
      <div data-kompot="spacer" data-id="welcome-gap"></div>
      <div data-kompot="button" data-id="go" data-variant="pay" data-action="/kiosk/menu">К оплате</div>
    </div>
  </div>
</div>
```

| узел | атрибуты |
| --- | --- |
| `column`, `row` | `data-spacing`, `data-fill="width\\|height\\|both"`, `data-weight`, `data-max-width`, `data-action` |
| `surface` | `data-tone`, `data-density`, `data-spacing`, `data-align="start\\|center"`, `data-rule="above\\|below\\|around\\|between\\|start\\|accent\\|dashed"`, `data-pinned`, `data-weight`, `data-action` |
| `text` | `data-style`, `data-color`, `data-max-width`, `data-max-lines`; `<span data-style data-color data-action>` внутри — спаны |
| `button` | `data-variant`, `data-action`, `data-width`, `data-height` |
| `glyph` | `data-tone`, `data-frame="disc\\|rounded\\|none"`, `data-size="small\\|medium\\|large"`, `data-color` |
| `photo` | `data-size="thumb\\|tile\\|square\\|hero"`; текст — подпись |
| `screen_header` | `data-action` — путь назад; оболочка рисует его поверх содержимого |
| `spacer` | пустая колонка с весом 1: то, что раздвигает соседей на фиксированном экране |
| `table` | `<div data-row [data-header]><span data-cell>…</span></div>` на строку — стандартная таблица kompot |

**Слова.** Тона: {tones}. Плотности: {densities}. Стили: {styles}.
Цвета: {colors}. Варианты кнопок: {variants}.

**Правила, которые проверяет конвертер:** у каждого узла свой `data-id`, уникальный на экране;
`data-action` — диплинк, он уходит на сервер как есть; текст на тонированной поверхности несёт
`data-color`; прижатый низ — `data-pinned` у поверхности прямо в корне; корень с `data-fill="both"`
— фиксированный экран, прокрутки нет, распорки работают.

Новое **слово** (тон, плотность, вариант) — правка `tokens.json` и деплой сервера; новый **узел** —
релиз клиента. Кит сгенерирован из `tokens.json`; правьте файл, не CSS.
"""


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("tokens")
    parser.add_argument("--prefix", required=True, help="the product's short name, for comments")
    parser.add_argument("--title", required=True, help="the group the Design System pane files the cards under")
    parser.add_argument("--examples", help="a directory of artboards written in the vocabulary, copied into examples/")
    parser.add_argument("--out", default="build/kit")
    parser.add_argument("--font-link", help="the Google Fonts stylesheet the cards and examples load")
    args = parser.parse_args()
    with open(args.tokens, encoding="utf-8") as f:
        tokens = json.load(f)
    global FONT_LINK
    if args.font_link:
        FONT_LINK = args.font_link
    css = build_css(tokens, args.prefix)
    os.makedirs(os.path.join(args.out, "cards"), exist_ok=True)
    with open(os.path.join(args.out, "kompot.css"), "w", encoding="utf-8") as f:
        f.write(css)
    with open(os.path.join(args.out, "kompot.js"), "w", encoding="utf-8") as f:
        f.write(KOMPOT_JS)
    for name, html in build_cards(tokens, css, args.title).items():
        with open(os.path.join(args.out, "cards", name), "w", encoding="utf-8") as f:
            f.write(html)
    with open(os.path.join(args.out, "README.md"), "w", encoding="utf-8") as f:
        f.write(build_readme(tokens, args.title))
    if args.examples:
        os.makedirs(os.path.join(args.out, "examples"), exist_ok=True)
        for path in glob.glob(os.path.join(args.examples, "*.html")):
            shutil.copy(path, os.path.join(args.out, "examples", os.path.basename(path)))
        # the examples link ../kompot.css? No: they link ./kompot.css, so the sheet sits beside them too.
        shutil.copy(os.path.join(args.out, "kompot.css"), os.path.join(args.out, "examples", "kompot.css"))
        shutil.copy(os.path.join(args.out, "kompot.js"), os.path.join(args.out, "examples", "kompot.js"))
    print(f"kit → {args.out}: kompot.css, kompot.js, {len(os.listdir(os.path.join(args.out, 'cards')))} cards, README" + (f", {len(glob.glob(os.path.join(args.out, 'examples', '*.html')))} examples" if args.examples else ""))
    return 0


if __name__ == "__main__":
    sys.exit(main())
