#!/usr/bin/env python3
"""Generate the Kotlin a kompot consumer needs from a named `tokens.json`.

The named file is the design system's source: every colour, type style and surface the product
keeps, under the name both ends use. From it this writes:

  * for the SHARED module (server and client alike) — `<Prefix>Tokens.kt`: an object of colour
    tokens and one of typography tokens. A colour with an `m3` role is that role's Material token
    (`ColorToken("primary")`), so the server keeps naming `M3Colors.Primary` and this file agrees;
    a colour without one is the product's own word (`ColorToken("lime")`). Same for type;
  * for the CLIENT — `<Prefix>Palette.kt` (the hex values, the Material `ColorScheme` built from
    the `m3` roles, and `resolve(token)` for the product's own colours), `<Prefix>TypeScale.kt`
    (`typography(family)` filling the Material slots, one function per extra style, and
    `resolve(token, base)`), `<Prefix>Surfaces.kt` (radius and inset per surface word), and — when
    the file has a `tones` section — `<Prefix>Tones.kt` (tone × density → container, content,
    outline; a surface word carries `"outline": "strong"|"soft"` to say whether it draws one).

Every file starts with the sha256 of the tokens file it came from, so a test can refuse a build
whose generated code is older than its source. Run it again after every edit of tokens.json:

    canvas_generate.py design/tokens.json --prefix Boulab \\
        --shared-package dev.boulab.components --shared-out components/src/main/kotlin/dev/boulab/components \\
        --client-package dev.boulab.client.theme --client-out client/src/main/kotlin/dev/boulab/client/theme

tokens.json:

    {"colors":   {"brand": {"hex": "#E24916", "m3": "primary"}, "lime": {"hex": "#D6F08D"}},
     "type":     {"display_large": {"size": 210, "weight": 900, "letterSpacing": -10, "lineHeight": 182, "m3": "displayLarge"},
                  "overline": {"size": 26, "weight": 800, "letterSpacing": 2, "lineHeight": 30}},
     "surfaces": {"card": {"radius": 44, "padding": [24, 28]}, "chip": {"radius": "pill", "padding": [22, 36]}}}

A `families` section names the faces (`"base"` plus others, e.g. `"narrow"`); a type entry with
`"family": "narrow"` is set in that face, and `typography()` takes one FontFamily parameter per
family. `"decoration": "line-through"` puts a strike through a style (an old price).
`hex` may carry an alpha as `#RRGGBB@NN` (percent). `m3` on a colour is one Material role or a
list of them (the canvas's paper is `background` and `onPrimary`); the shared token is the first. `padding` is CSS order: one value, [vertical,
horizontal], [top, horizontal, bottom] or [top, right, bottom, left]. `radius` is a number, "pill"
(fully round), or four numbers — which the client rounds symmetrically, because the wire carries no
shape and one shape per word is the rule; the canvas's asymmetric corner is a difference to write
down, not to generate. Entries may carry `absorbs`, read by `canvas_tokens.py --check` only.
"""
import argparse
import hashlib
import json
import os
import sys

M3_COLOR_ROLES = {
    "primary", "onPrimary", "primaryContainer", "onPrimaryContainer",
    "secondary", "onSecondary", "secondaryContainer", "onSecondaryContainer",
    "tertiary", "onTertiary", "tertiaryContainer", "onTertiaryContainer",
    "background", "onBackground", "surface", "onSurface", "surfaceVariant", "onSurfaceVariant",
    "error", "onError", "errorContainer", "onErrorContainer", "outline", "outlineVariant",
}
M3_TYPE_SLOTS = [
    "displayLarge", "displayMedium", "displaySmall", "headlineLarge", "headlineMedium", "headlineSmall",
    "titleLarge", "titleMedium", "titleSmall", "bodyLarge", "bodyMedium", "bodySmall",
    "labelLarge", "labelMedium", "labelSmall",
]
WEIGHTS = {100: "Thin", 200: "ExtraLight", 300: "Light", 400: "Normal", 500: "Medium", 600: "SemiBold", 700: "Bold", 800: "ExtraBold", 900: "Black"}


def snake_to_pascal(name: str) -> str:
    return "".join(part[:1].upper() + part[1:] for part in name.split("_"))


def snake_to_camel(name: str) -> str:
    p = snake_to_pascal(name)
    return p[:1].lower() + p[1:]


def m3_snake(role: str) -> str:
    out = ""
    for ch in role:
        out += "_" + ch.lower() if ch.isupper() else ch
    return out


def kotlin_color(hexv: str) -> str:
    base, _, alpha = hexv.partition("@")
    literal = f"Color(0xFF{base.lstrip('#').upper()})"
    return f"{literal}.copy(alpha = {int(alpha) / 100:.2f}f)" if alpha else literal


def roles_of(entry: dict) -> list:
    """`m3` is one Material role or a list of them: the canvas's paper is `background` and `onPrimary`."""
    roles = entry.get("m3")
    return [roles] if isinstance(roles, str) else list(roles or [])


def header(source: str, digest: str) -> str:
    return (
        f"// GENERATED from {source} by kompot tools/canvas/canvas_generate.py. Do not edit; edit the\n"
        f"// tokens and run the generator again. A test compares this digest with the file's.\n"
        f"// tokens sha256: {digest}\n\n"
    )


def shared_tokens(tokens: dict, package: str, prefix: str, head: str) -> str:
    lines = [head, f"package {package}", "", "import io.github.youndie.kompot.ColorToken", "import io.github.youndie.kompot.TypographyToken", ""]
    lines += ["// Every colour the canvas keeps, as the token the server sends. An `m3` colour is Material's", "// token so the two vocabularies never disagree on a key.", f"object {prefix}Colors {{"]
    for name, entry in tokens.get("colors", {}).items():
        roles = roles_of(entry)
        key = m3_snake(roles[0]) if roles else name
        lines.append(f'    val {snake_to_pascal(name)}: ColorToken = ColorToken("{key}")')
    lines += ["}", "", f"object {prefix}Typography {{"]
    for name, entry in tokens.get("type", {}).items():
        key = m3_snake(entry["m3"]) if entry.get("m3") else name
        lines.append(f'    val {snake_to_pascal(name)}: TypographyToken = TypographyToken("{key}")')
    lines += ["}", ""]
    return "\n".join(lines)


def client_palette(tokens: dict, package: str, prefix: str, head: str) -> str:
    colors = tokens.get("colors", {})
    lines = [head, f"package {package}", "", "import androidx.compose.material3.ColorScheme", "import androidx.compose.material3.lightColorScheme",
             "import androidx.compose.ui.graphics.Color", "import io.github.youndie.kompot.ColorToken", ""]
    lines += [f"object {prefix}Palette {{"]
    for name, entry in colors.items():
        lines.append(f"    val {snake_to_pascal(name)}: Color = {kotlin_color(entry['hex'])}")
    lines += ["", "    // The Material roles the canvas names; everything else keeps Material's defaults.", "    val scheme: ColorScheme =", "        lightColorScheme("]
    for name, entry in colors.items():
        for role in roles_of(entry):
            if role not in M3_COLOR_ROLES:
                sys.exit(f"colour {name}: unknown m3 role {role}")
            lines.append(f"            {role} = {snake_to_pascal(name)},")
    lines += ["        )", "", "    // The product's own colours, by the token the server sends; null for a Material one.", "    fun resolve(token: ColorToken): Color? =", "        when (token.key) {"]
    for name, entry in colors.items():
        if not roles_of(entry):
            lines.append(f'            "{name}" -> {snake_to_pascal(name)}')
    lines += ["            else -> null", "        }", "}", ""]
    return "\n".join(lines)


def client_type_scale(tokens: dict, package: str, prefix: str, head: str) -> str:
    types = tokens.get("type", {})
    lines = [head, f"package {package}", "", "import androidx.compose.material3.Typography", "import androidx.compose.ui.text.TextStyle",
             "import androidx.compose.ui.text.font.FontFamily", "import androidx.compose.ui.text.font.FontWeight",
             "import androidx.compose.ui.text.style.TextDecoration", "import androidx.compose.ui.unit.sp",
             "import io.github.youndie.kompot.TypographyToken", ""]
    families = tokens.get("families", {"base": None})
    extra_families = [f for f in families if f != "base"]
    params = ["family: FontFamily = FontFamily.Default"] + [f"{snake_to_camel(f)}: FontFamily = family" for f in extra_families]
    lines += [f"object {prefix}TypeScale {{",
              "    private fun style(", "        family: FontFamily,", "        size: Int,", "        weight: FontWeight,", "        lineHeight: Int,", "        letterSpacing: Float,",
              "        decoration: TextDecoration? = null,",
              "    ): TextStyle =", "        TextStyle(", "            fontFamily = family,", "            fontSize = size.sp,", "            fontWeight = weight,",
              "            lineHeight = lineHeight.sp,", "            letterSpacing = letterSpacing.sp,", "            textDecoration = decoration,", "        )", ""]
    lines += ["    // The Material slots, in the product's sizes. The families are parameters so a screenshot",
              "    // fixture can pass the pinned face and record goldens that travel between machines" +
              (";" if extra_families else ".")]
    if extra_families:
        lines.append("    // " + ", ".join(f"`{snake_to_camel(f)}` is the canvas's {families[f]}" for f in extra_families) + ", falling back to the base family.")
    lines += ["    fun typography(", *[f"        {p}," for p in params], "    ): Typography =", "        Typography("]
    by_slot = {entry["m3"]: (name, entry) for name, entry in types.items() if entry.get("m3")}
    for slot in M3_TYPE_SLOTS:
        if slot in by_slot:
            name, e = by_slot[slot]
            lines.append(f"            {slot} = {style_call(e)},")
    lines += ["        )", ""]
    extras = [(name, e) for name, e in types.items() if not e.get("m3")]
    for name, e in extras:
        # an extra style inherits the family of a Material slot in the same family, so the fixture's
        # pinned face reaches it too
        donor = next((slot for slot in M3_TYPE_SLOTS if slot in by_slot and by_slot[slot][1].get("family") == e.get("family")), "bodyMedium")
        lines += [f"    // `{name}`: derived from the scale so it inherits the family the scale was built with.",
                  f"    fun {snake_to_camel(name)}(base: Typography): TextStyle =",
                  f"        base.{donor}.copy(",
                  f"            fontSize = {e['size']}.sp,",
                  f"            fontWeight = FontWeight.{WEIGHTS[int(e['weight'])]},",
                  f"            lineHeight = {int(e.get('lineHeight', round(e['size'] * 1.2)))}.sp,",
                  f"            letterSpacing = {float(e.get('letterSpacing', 0))}f.sp,"]
        if e.get("decoration") == "line-through":
            lines.append("            textDecoration = TextDecoration.LineThrough,")
        elif e.get("decoration") == "underline":
            lines.append("            textDecoration = TextDecoration.Underline,")
        lines += [f"        )", ""]
    lines += ["    // The product's own styles, by the token the server sends; null for a Material one.", "    fun resolve(", "        token: TypographyToken,", "        base: Typography,", "    ): TextStyle? =", "        when (token.key) {"]
    for name, _ in extras:
        lines.append(f'            "{name}" -> {snake_to_camel(name)}(base)')
    lines += ["            else -> null", "        }", "}", ""]
    return "\n".join(lines)


def style_call(e: dict) -> str:
    line = int(e.get("lineHeight", round(e["size"] * 1.2)))
    family = snake_to_camel(e["family"]) if e.get("family") and e["family"] != "base" else "family"
    deco = {"line-through": ", TextDecoration.LineThrough", "underline": ", TextDecoration.Underline"}.get(e.get("decoration"), "")
    return f"style({family}, {e['size']}, FontWeight.{WEIGHTS[int(e['weight'])]}, {line}, {float(e.get('letterSpacing', 0))}f{deco})"


def client_surfaces(tokens: dict, package: str, prefix: str, head: str) -> str:
    surfaces = tokens.get("surfaces", {})
    lines = [head, f"package {package}", "", "import androidx.compose.foundation.layout.PaddingValues", "import androidx.compose.foundation.shape.CircleShape",
             "import androidx.compose.foundation.shape.RoundedCornerShape", "import androidx.compose.ui.graphics.RectangleShape", "import androidx.compose.ui.graphics.Shape",
             "import androidx.compose.ui.unit.dp", ""]
    lines += ["// A surface word's geometry: the shape from the radius, the inset from the padding. One shape per",
              "// word — the canvas's asymmetric corners are a documented difference, not a field.",
              "data class CanvasSurface(", "    val shape: Shape,", "    val inset: PaddingValues,", ")", "",
              f"object {prefix}Surfaces {{"]
    for name, e in surfaces.items():
        lines.append(f"    val {snake_to_camel(name)}: CanvasSurface = CanvasSurface({shape_of(e['radius'])}, {padding_of(e['padding'])})")
    lines += ["", "    fun of(word: String): CanvasSurface? =", "        when (word) {"]
    for name in surfaces:
        lines.append(f'            "{name}" -> {snake_to_camel(name)}')
    lines += ["            else -> null", "        }", "}", ""]
    return "\n".join(lines)


def shape_of(radius) -> str:
    if radius == "pill" or radius == "50%":
        return "CircleShape"
    if isinstance(radius, list):
        radius = max(radius)
    return "RectangleShape" if int(radius) == 0 else f"RoundedCornerShape({int(radius)}.dp)"


def padding_of(padding) -> str:
    p = padding if isinstance(padding, list) else [padding]
    if len(p) == 1:
        return f"PaddingValues({p[0]}.dp)"
    if len(p) == 2:
        return f"PaddingValues(horizontal = {p[1]}.dp, vertical = {p[0]}.dp)"
    if len(p) == 3:
        return f"PaddingValues(start = {p[1]}.dp, top = {p[0]}.dp, end = {p[1]}.dp, bottom = {p[2]}.dp)"
    return f"PaddingValues(start = {p[3]}.dp, top = {p[0]}.dp, end = {p[1]}.dp, bottom = {p[2]}.dp)"


def client_tones(tokens: dict, package: str, prefix: str, head: str) -> str:
    """Tone × density → paint. A tone names its container and content colours and, if it has one,
    its outline; a surface word says whether it draws an outline at all (`strong`, `soft`, none).
    """
    tones = tokens.get("tones", {})
    surfaces = tokens.get("surfaces", {})
    lines = [head, f"package {package}", "", "import androidx.compose.ui.graphics.Color", ""]
    lines += ["// What a tone is painted with. `Color.Unspecified` content means: inherit what is around.",
              "data class TonePaint(", "    val container: Color,", "    val content: Color,", "    val outline: Color,", ")", "",
              f"object {prefix}Tones {{",
              "    private val strong = setOf(" + ", ".join(f'"{w}"' for w, e in surfaces.items() if e.get("outline") == "strong") + ")",
              "    private val soft = setOf(" + ", ".join(f'"{w}"' for w, e in surfaces.items() if e.get("outline") == "soft") + ")",
              "", "    // null for a tone this file does not know: the caller draws the neutral thing.", "    fun paint(", "        tone: String,", "        density: String,", "    ): TonePaint? {",
              "        val outlined = density in strong || density in soft", "        return when (tone) {"]
    for name, e in tones.items():
        if "container" not in e:
            lines.append(f'            "{name}" -> TonePaint(Color.Transparent, Color.Unspecified, Color.Transparent)')
            continue
        container = f"{prefix}Palette.{snake_to_pascal(e['container'])}"
        content = f"{prefix}Palette.{snake_to_pascal(e['content'])}"
        outline = e.get("outline")
        soft = e.get("outlineSoft")
        if outline and soft:
            out = f"if (!outlined) Color.Transparent else if (density in soft) {prefix}Palette.{snake_to_pascal(soft)} else {prefix}Palette.{snake_to_pascal(outline)}"
        elif outline:
            out = f"if (outlined) {prefix}Palette.{snake_to_pascal(outline)} else Color.Transparent"
        else:
            out = "Color.Transparent"
        lines.append(f'            "{name}" -> TonePaint({container}, {content}, {out})')
    lines += ["            else -> null", "        }", "    }", "}", ""]
    return "\n".join(lines)


def write(path: str, content: str) -> None:
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        f.write(content)
    print(f"→ {path}")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("tokens")
    parser.add_argument("--prefix", required=True, help="the product's name in identifiers: Boulab → BoulabPalette")
    parser.add_argument("--shared-package")
    parser.add_argument("--shared-out")
    parser.add_argument("--client-package")
    parser.add_argument("--client-out")
    args = parser.parse_args()
    with open(args.tokens, "rb") as f:
        raw = f.read()
    digest = hashlib.sha256(raw).hexdigest()
    tokens = json.loads(raw.decode("utf-8"))
    head = header(os.path.basename(args.tokens), digest)
    if args.shared_out:
        write(os.path.join(args.shared_out, f"{args.prefix}Tokens.kt"), shared_tokens(tokens, args.shared_package, args.prefix, head))
    if args.client_out:
        write(os.path.join(args.client_out, f"{args.prefix}Palette.kt"), client_palette(tokens, args.client_package, args.prefix, head))
        write(os.path.join(args.client_out, f"{args.prefix}TypeScale.kt"), client_type_scale(tokens, args.client_package, args.prefix, head))
        write(os.path.join(args.client_out, f"{args.prefix}Surfaces.kt"), client_surfaces(tokens, args.client_package, args.prefix, head))
        if tokens.get("tones"):
            write(os.path.join(args.client_out, f"{args.prefix}Tones.kt"), client_tones(tokens, args.client_package, args.prefix, head))
    print(f"tokens sha256 {digest[:12]}…")
    return 0


if __name__ == "__main__":
    sys.exit(main())
