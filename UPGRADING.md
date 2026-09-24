# Upgrading

What a Kotlin consumer has to change when a published API stops being what it was.

The wire has its own journal — [§13 of SPEC.md](kompot-spec/SPEC.md) — and the two are deliberately
separate. That one is addressed to somebody implementing a server on another stack; this one to
somebody compiling against these artifacts.

**Nothing here is a promise of stability.** kompot is `0.x`, a version carries the CI run number on
its tail, and a breaking change is allowed at any of them. What is promised is that it is written
down.

A Kotlin break is loud where a wire break is silent: a consumer's build stops with a type error
instead of a person meeting a hole on a screen. So the value of this file is not the warning — it is
the answer. The compiler names a type and says nothing about why it changed or what to write
instead; that is what each entry below is for.

Only **declared** breaks reach this file: a commit carrying `!` after its scope or a
`BREAKING CHANGE:` footer is what `docs/scripts/breaking_changes.py` holds to it in CI. A change that
breaks a consumer without saying so is not caught by anything here, and
[B-39](docs/backlog/B-39-nothing-guards-the-published-kotlin-api.md) is about that gap.

---

## 0.38.0 — `RowComponent` and `ColumnComponent` gain `alignment` and `arrangement`, `RowComponent` gains `scrollable` (binary only)

**Was**

```kotlin
RowComponent(id = "actions", children = buttons, spacing = 8)
```

**Now** — the same line compiles and means the same thing:

```kotlin
RowComponent(id = "actions", children = buttons, spacing = 8)
RowComponent(id = "actions", children = buttons, spacing = 8, arrangement = StackArrangement.SPACE_BETWEEN)
```

**What to change.** Nothing in source. Recompile against 0.38: the constructors and `copy` of both
classes take two more parameters (three for `RowComponent`, which also gained `scrollable`), so a library compiled against 0.37 that builds these nodes fails at
run time with `NoSuchMethodError` until it is rebuilt. Positional calls past `action` did not exist,
so no call site changes meaning.

**Why it was worth breaking.** A server could not say "the price on the right" or "the buttons at
both ends" without inventing a spacer node the design does not have. Fields on the wire, not a
modifier, so an older client still draws the stack (SPEC.md §4.7); a data class cannot gain a field
without its constructor changing.

## 0.38.0 — the Compose half moves to Compose Multiplatform 1.12

**Was**

```toml
compose-multiplatform = "1.11.1"
compose-material3 = "1.11.0-alpha07"
```

**Now**

```toml
compose-multiplatform = "1.12.1"
compose-material3 = "1.12.0-alpha03"
```

**What to change.** A build that depends on anything in the Compose half (`kompot-client`,
`kompot-ds-material-compose`, `kompot-forms-client`, `kompot-wizard-client`, `kompot-theme-client`,
`kompot-preview`, `kompot-images-client-coil`, `kompot-studio`) moves to the 1.12 line itself: the
Compose plugin, `material3` on its 1.12 alpha, and anything else compiled against one line of
Compose. The protocol modules have no Compose in them and ask for nothing on that front.

- **Android:** `compileSdk` 37 or later, for **every** kompot module, the protocol ones included. AGP
  writes a library's `compileSdk` into its AAR as `minCompileSdk`, so this is a requirement on the
  app, not a detail of kompot's build. For the Compose half it would come anyway (Compose 1.12
  brings androidx material3 1.5, which demands 37); the protocol modules follow so that the whole
  toolkit is built against one Android API level.
- **`kompot-studio`:** the screenshot tester it reaches at run time is viddik **0.6**, from Maven
  Central, under `io.github.youndie.viddik` (package and group both; up to 0.3.3 it was
  `ru.workinprogress`). The studio looks for the registry viddik's processor generates by that
  package's name, so a consumer still on the old viddik gets an empty stories panel and no capture
  buttons instead of an error. Jewel, if you pin it, is `0.41.0-262.10968.63`.
- **viddik 0.6 in your own tests** declares Java 21 in its Gradle metadata. A test classpath that
  asks for an older Java finds no variant; ask for 21 on the test configurations only, as
  `kompot-ds-material-compose/build.gradle.kts` does.

**Why it was worth breaking.** Mixing Compose lines does not fail where it could be read: it
resolves cleanly, compiles, and throws `NoSuchMethodError` on the first frame inside a renderer.
While kompot stayed on 1.11, every app using it was held on 1.11 too, for no reason of its own.
Moving the four numbers together (Compose, material3, viddik, Jewel) is the only way the line stays
one line.

## 0.37.0 — `KompotStudioConfig.samples` takes components, not pairs

**Was**

```kotlin
samples = listOf("product_card" to ProductCardComponent(id = "sample", title = "Sample"))
```

**Now**

```kotlin
samples = listOf(ProductCardComponent(id = "sample", title = "Sample"))
```

**What to change.** Drop the wire name from every entry. Nothing replaces it: the studio derives the
name by encoding the sample with the `Json` the configuration already carries, which is the string a
server would write.

**Why it was worth breaking.** The name was a string somebody typed, and there was no supported way
to compute one — `KompotComponent.wireType()` is `internal` to the client. A typo was silent: the
palette matched the name against the profile, missed, and showed no sample, which looks exactly like
a configuration that declared none. This module's own readme documented a `wireTypeOf` helper that
had never been written, and nobody found out by using it.

Three things now fail loudly, before the window opens, that used to be a panel quietly showing less:
a sample the `Json` cannot encode, two samples of one type, and a sample whose type no schema
declares (`extensionTypes` counts as a declaration).

Merged in [#154](https://github.com/youndie/kompot/pull/154); the item is
[B-38](docs/backlog/B-38-wire-name-is-not-obtainable.md).
