---
name: kompot-layout
description: "Use when laying out or restyling a screen in a kompot server-driven UI (Kotlin, Compose): building the component tree on the server, adding or extending a wire type, writing or fixing a renderer, matching a design canvas, pricing a change as a server deploy or a client release. Triggers: 'сверстай экран на kompot', 'добавь компонент в словарь', 'экран не похож на макет', 'kompot renderer', 'server-driven UI layout', 'BDUI screen', 'match the canvas'."
---

# Laying out on kompot

kompot is a server-driven UI toolkit: **the server describes a screen as a tree of components, the
client renders it through a dictionary of renderers.** A new screen ships from the server; a new
*kind* of screen ships with the client. Everything below follows from that one sentence, and most of
it was paid for by a screen that looked wrong on a device after every check had passed.

## Step 0 — read the project before writing a node

The dictionary is the project's, not the toolkit's. Find, in this order:

1. **The wire types** — the `@SerialName("…")` components in the shared module, the list of wire
   names, the test dictionary with one sample per type, the client's coverage test that says every
   type has a renderer, and the design document's paragraph that lists the types in prose. **These
   four lists are kept in step by tests and they fail on order** — a new type goes into all four, in
   the same position.
2. **The design system** — where colours, typography and *surfaces* (shape, container, content,
   outline, min height) are resolved per role; the served brand kit overlays it. What is a token
   here (`primary_container`, `title_medium`) is the server's to name and the kit's to paint. If
   the product keeps a named tokens file (`design/tokens.json`), the palette, the scale, the
   surface geometry and the server's token vocabulary are *generated* from it (kompot's
   `tools/canvas/canvas_generate.py`) and stamped with its digest — edit the file, run the
   generator, never the Kotlin; a value the product merges is written down as `absorbs`, not
   left for a later reader to rediscover.
3. **The shell** — what the client pulls out of the root before rendering: the bottom bar, a pinned
   footer, a screen header. One level deep, root children only, one of each; two is a server
   mistake and is drawn as sent so somebody sees it.
4. **The canvas** — the design and its written list of *deliberate differences*. A screen matched
   to the canvas is not a screen that copies it; the list says what stays different and why. A
   canvas drawn in the wire's own words (kompot's `tools/canvas` kit: `data-kompot`, `data-id`,
   `data-tone`…) converts to the tree the server sends and the list becomes a diff by id
   (`canvas_tree.py --compare`); a canvas drawn in hex and px is read by a person, and the
   differences list is written by hand. The server's own recordings render into such a canvas
   (`canvas_render.py`), so the canvas can start from what the wire says and the round trip
   canvas → tree → canvas is a test: a recording that does not survive it names a type or a word
   the kit does not know.
5. **The pricing table** (operator boundaries or its equivalent): every change to the wire is priced
   there as *server deploy* or *client release* the day it lands, with what an older client draws.
6. **The toolkit's Compose line**, before picking the screenshot tool's version. kompot's client
   modules are compiled against one Compose Multiplatform line and a material3 of that line; a
   newer foundation beside them fails at *runtime*, inside a renderer, not at resolution. The
   screenshot tool (viddik) moves lines on its own — take the last version on the toolkit's line,
   not the newest. The same holds for every desktop-side library that shares the composition: the
   screenshot tool and the shell of a preview tool are pinned in the version catalogue beside the
   Compose version, and the three move in one commit or not at all.

Project conventions beat this skill. This skill says what is the same everywhere.

Between two designs on one codebase, what stays is the scaffold — the shell, the recording and
screenshot machinery, the guards, the two standard shell types — and what goes is every word:
a second design names its own tones and densities (`paper` became `panel`), and reusing the
first design's words to save a rename is how a token stops meaning what it says.

## The contract: what travels, what does not

| Travels on the wire (a server deploy) | Stays in the client (a client release) |
|---|---|
| structure — which nodes, in what order, nested how | how a node is drawn — the renderer |
| every string a person reads, including headings, captions, button words | the type scale's family; the sizes and weights per token |
| colours and text styles **as token names** (`on_surface_variant`, `headline_medium`) | what a token resolves to per brand, and the served kit repaints it |
| open words: a surface's *tone* and *density*, a button's *emphasis*, a counter's *state*, a status | what each word looks like; an unknown word draws the neutral thing |
| an action, and which node presses it | shape scale, corner radii, insets, control heights |
| a vector icon as path data on a 24-grid | the icon's colour (from the role) and size |

Two rules keep the boundary honest:

- **Nothing about a node's meaning is assembled on the client.** The server puts the action on the
  button and the word on the label; the client posts the action back unchanged. A client that
  composes a deeplink or a sentence is a second server.
- **Every text on a tinted surface carries its colour token.** kompot's text renderer draws
  `on_surface` unless the node names a colour; it does not read the surface's content colour. Ink
  on the brand orange is what a screen without the token looks like — assert "every text has a
  colour" over the recordings, once, instead of finding it frame by frame.
- **Every open word has a fallback, and the fallback is the ordinary thing.** A tone the client
  has never heard of draws the neutral card; an emphasis it does not know draws the ordinary
  button; an unknown component draws a *visible* degradation block, never nothing, because a
  screen with a hole is a screen nobody reports.

Price every wire change as you make it: a new field on an existing type is a deploy (old clients
ignore it — say what they draw); a new type or a new word the design system must know is a release.

## Building a screen on the server

- **Root is a column; ids are unique across the tree.** The conformance walk checks ids; the day a
  table row and a banner share one, the walk is what finds it — not a golden.
- **Shell slots by position:** the screen header first, the bottom bar last, the pinned footer a
  root child. The client pulls them out; everything else scrolls. What floats *over* content is a
  shell slot and nothing else: the wire has no overlay word, so a badge drawn over a hero photo
  either becomes a slot the shell knows or moves beside the title and goes on the differences
  list. And a footer that does not span the window (a split screen with a rail down its side) is
  not the shell's: it stays in its column, and the fixed screen keeps it at the bottom with a
  weighted area above it.
- **A screen that must fill the window is a *fixed* screen** — a kiosk page, an outcome, a wizard
  step with its answer at the bottom — and the lazy root cannot draw it: `weight` among a lazy
  root's children collapses, so spacers push nothing. Say it with a word the dictionary already
  has (`size.height = fill` on the root), let the shell draw that root as a plain column, and
  build the page with weighted empty columns as spacers. The shell owns the degradation: a fixed
  screen on a window *shorter* than its design hands its last children zero height — two option
  cards were 0 tall and a tap went nowhere, while every frame at the design's size looked right —
  so lay it out at no less than the design's height inside a scroll.
- **Figures are formatted on the server, invisible characters spelled out.** A no-break space
  typed into a source file is indistinguishable from a space; write `"\u00A0"` and test the
  string, or the golden and the test disagree on a character nobody can see.
- **One area word per column.** `screen` and `body` take the height they are given; a column that
  holds two of them gives the rest to the first and nothing to the second — a category title on
  `body` squeezed the list under it to zero. The part that should take the remainder is the area,
  and it carries the weight; everything else is a `band`. The wire may also ask a surface to fill
  (`size.height = fill`), and the client honours that the same way.
- **A density that has no inset must not fill.** The neutral `plain` word is what a row's small
  members use (a language switch, a price column, a quantity tray); a `plain` that fills its
  width takes the row from its neighbours — a photo measured 0×270, a pay bar 0 wide. Fill is a
  property of the area words (`screen`, `body`) and of what the wire asks for with `size`.
- **Lines are words, not nodes.** A hairline above or below a band, a box around a tray, a rule
  between list rows, the accent edge on a selected tile: one `rule` word on the surface, drawn
  behind the content by the client. A 1-px surface sent as a divider is geometry on the wire and a
  node the walk has to name.
- **A control that must be square is sized on the wire.** A design-system surface carries
  `minHeight` and no `minWidth`; a glyph button (`+`, `←`) gets `size` with both dimensions, and
  the pricing row says so.
- **One back control per screen.** If the screen owns its way back (a wizard's step back, a sheet's
  close), send the header with that action and no `Back` pill in the content. Two controls that go
  different ways with nothing on screen to tell them apart is the defect this rule came from.
- **One control of full weight per screen.** The answer is the filled pill; the way out is quiet or
  a link. Two primaries is a screen asking one question twice — assert it over every state the
  screen has, not the one you are looking at.
- **Headings are the server's copy**, one per screen or step, in a headline token; the paragraph
  under it in a body token and the secondary text colour. A screen that opens on prose has no
  heading, and the canvas will have one.
- **Tables are surfaces with dividers**; chips are surfaces at chip density; a card is a surface on
  the page ground. Do not invent a `table` or `chip` type — the dictionary grows by *words on a
  container*, not by nouns.
- **Outcomes have one shape:** a mark in a disc, a headline, one paragraph, a receipt table — the
  same on the paid side and the failed side, so they read as one screen in two states.
- **Captions carry the state word first** (`Running low · …`), coloured by the client from the
  state; the ordinary state sends no caption at all.
- **A live update sends the node the screen would have sent.** Whatever builds a node for a screen
  is the only thing that builds its replacement; a push that differs in one field rearranges the
  screen while somebody is looking at it. And the update frame names the node it carries, once —
  a wrapper with the same id recurses at every draw.
- **Copy stays true under load:** "nothing has been charged" beside a balance that already moved
  is a contradiction a subscriber will report. Say *held*.

## Rendering on the client

- **Draw children through the registry, never by knowing them.** A container renderer calls
  `LocalKompotRegistry.current.RenderNode(child)`. And **provide the registry wherever you render a
  node outside the screen's own tree** — a shell footer, a header — or the first surface there
  throws `LocalKompotRegistry not provided` on the stand while the screenshot harness, which
  provides one around everything, showed it drawn.
- **A container renderer reads its children's `weight`, or every spacer collapses.** The weight
  is the parent's to apply — `Box(Modifier.weight(w), propagateMinConstraints = true)` around each
  child, as kompot's own column does — and a renderer that forwards children without it draws the
  first frame with everything pressed to the top. A surface that stands for an *area* (`screen`,
  `body`) also tells its inner column to fill the height it was given; otherwise the spacers have
  nothing to divide.
- **A grid row is a row whose every child is weighted.** Give it `height(IntrinsicSize.Max)` and
  `fillMaxHeight()` on the cells and the cards come out equal; a row with one weighted child (a
  footer's figures beside its button) keeps centring instead. The standard row has no cross-axis
  word at all — override its renderer with `CenterVertically` once rather than sending geometry.
- **Compose synthetic nodes, do not paint twice.** A tag chip on a card, a `Choose` pill, a status
  chip: build the `surface`/`button` node and render it through the registry. A chip painted in two
  renderers is two chips the moment one of them changes.
- **Modifier order is layout.** `clip` before `background` (or the ground leaks square corners);
  `padding` before `background` paints inside the inset and leaves a bare margin; `clickable` last
  shrinks the target to what is left — move the inset to the child. A cap (`widthIn(max)`) goes
  *before* a fill, or it does nothing and reads as if it did.
- **The root column is lazy.** Its children are separate items: `weight` among them collapses,
  spacers do not centre, a row root scrolls nothing.
- **Geometry from one place.** Card, item, notice, chip tiers with their inset and their shape from
  the design system's shape scale; a renderer that hard-codes a radius is a brand that cannot
  change it.
- **Surfaces from the design system, by role**, including size: kompot ≥ 0.35 lets a surface carry
  `minHeight` and `contentPadding`, so a pill is 56 tall because the design system says so. Assert
  the served theme keeps every surface field — an overlay that answered for colours and dropped
  the height once gave the pill its toolkit height back.
- **Paint the window and the page ground explicitly.** Outside a `Surface` the content colour is
  black; a control that colours its own text draws black on dark, and the loading and error states
  are a white sheet.
- **What a camera reads is not themed.** A QR draws black modules on a fixed light tile in both
  themes; a "black on white" comment above a tile that was never painted is how a dark-mode code
  became unscannable.

## Typography, goldens, fixtures

- **Bundle the product's faces as static instances** (one file per weight, metrics equalised, no
  variable fonts; Google Fonts serves static TTFs per weight to an empty user agent, and a
  variable axis the canvas used — `wdth` — does not survive the trip, so it goes on the
  differences list); a second face (a narrow companion) is a property of the type token, and every
  extra style derives from a Material slot of the *same* family, so the pinned face reaches it in
  the goldens; the platform face drifts 4–8% of pixels between a Mac and a Linux runner,
  static instances 0.07%, and that remainder never goes away — so **goldens are recorded on the
  screenshot tool's pinned family with the product's scale** (`viddikTypography(yourScale)`), and
  the scale is passed down to the innermost `MaterialTheme` the app builds. Frames for people
  (a README) are drawn separately, in the product's faces, and are not goldens.
- **The dictionary's defaults live with the tokens, not in a tool.** The wire omits a default
  (`tone`, `frame`, `size`); whoever compares or renders the wire must know it, and the second
  product's default is not the first's (`paper` became `panel`). Keep a `defaults` section in the
  tokens file and read it from there.
- **Fixtures are the server's own responses**, captured through the API on a stand — never hand-
  written and never hand-edited past a value swap that the server itself would produce. After a
  screen changes, re-record the fixtures that draw it and the goldens; a golden of the old screen
  with every check green is the failure mode. The cheapest guard is a server test that *compares*
  its live answers with the recordings and rewrites them only under a flag
  (`-Pboulab.record=true` or its equivalent): a screen that changed fails the server's own suite
  until the recording, and then the golden, is redone.
- **Record every theme the product ships, and no other.** A dark frame of a single-theme kiosk is
  noise nobody reads; a light-only golden set of a two-theme app hides half the product.
- **Emoji are the platform's, not the pinned face's.** viddik pins text glyphs; a colour emoji is
  drawn by the host's emoji font, so a golden with one differs across machines by that glyph
  alone. Vector icons on the 24-grid if the goldens must travel; emoji only where the canvas
  insists, and say so in the differences list.
- **The schema golden** (JSON schema of the dictionary) is regenerated when a type or field is
  added; the CI test that compares it is the one that fails otherwise.
- **The screenshot harness provides what the app provides — and so hides what the app forgets.**
  Keep one test that drives the real app through the real screen source over a real socket for
  every shell feature (a pinned footer, a header, a copy action), and a stand test that walks the
  product. Prove a new guard by mutation: remove the thing it guards, watch it fail, put it back.
- **The stand's window is not the product's** (Compose's desktop runner opens 1024×768). Walk the
  product at its own size (`runDesktopComposeUiTest(width, height)`) *and* keep one walk at the
  small size — that is the window that found the crushed controls. Scroll to a control before
  tapping it, except the ones the shell draws outside the scroll (a floating header), where
  `performScrollTo` refuses. And when a wait times out, dump the tree (`onRoot().printToString()`):
  a node at `t=688, b=688` names the defect; "condition not satisfied" does not.

## The loop: measure before looking

The order that made the second screen one round of fixes instead of three:

1. Inventory the canvas mechanically (colours, type styles, surfaces), name the tokens, generate
   the design system; a table typed by hand drifts the day the canvas changes.
2. Build the screens, record the fixtures, dump the semantics, and **diff geometry by text** against
   the canvas before opening a frame. A row with `dy` names a band that moved; `ZERO` names a
   control crushed to nothing; a text `MISSING` names copy the server sends differently. Fix those.
3. Only then read the frames, once, for what a diff cannot see: paint, rules, faces, a glyph in the
   wrong disc.
4. Walk the product on the stand, at the design's size and at the runner's.

Every row that stays after step 2 is either fixed or a line in the differences list — not a row
somebody meant to look at.

## Before calling a layout done

1. The conformance walk on the stand (unique ids, every route walked).
2. Goldens for every screen touched, in every theme the product ships, re-recorded from the
   server's fixtures and read against the canvas — with a box diff when the canvas is renderable
   (kompot's `tools/canvas`: the artboard's text leaves against the frame's semantics, matched by
   the strings the server sent; a row with `dy` is a band that moved, `ZERO` is a crushed
   control), and by eye for what a diff cannot see. Never accepted.
3. The stand test through the real client, at the product's size and at the runner's (scroll to a
   control instead of assuming the window holds it; a pinned footer and a floating header are
   *not* in the scroll).
4. The four dictionary lists agree; the coverage and registration tests pass in order.
5. The pricing row for every wire change, with what an older client draws.
6. The design document's deliberate-differences list updated where a screen now matches or now
   deliberately does not.

## Upstream limits worth knowing

- A surface could not be sized until kompot 0.35 (`minHeight`, `contentPadding`); before that the
  height was the toolkit's.
- `paginated_list` shows what it received first; a reload does not refresh it (kompot#40) — a
  content fingerprint in the list id is the workaround, and it breaks paging if applied to a paged
  list.
- The wizard client half needs a real form schema; a flow without forms takes the engine only and
  draws its own chrome from `step_meter`.
- The wire carries no shape and no family on purpose: a brand kit is colour and type from the
  server, shape from the client. Do not put a radius on the wire to match one mockup.
- `row` has no cross-axis alignment and no wrap (kompot 0.36): a flow of chips is one line, and a
  row centres only if the client's row renderer says so. `text` reads no content colour from its
  surface. A `button` carries one text: a control with a verb and a figure is a `surface` with an
  action.
