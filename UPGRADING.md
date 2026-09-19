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
