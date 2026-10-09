---
id: B-84
title: "Пришедшее заново равное дерево тоже сбрасывает подмены"
status: wip
priority: P1
size: S
stage: partial-updates
epic: research-partial-updates
blocked_by: [B-82]
---

# B-84 — Пришедшее заново равное дерево тоже сбрасывает подмены

Хранилище подмен (B-81) сбрасывается только деревом, **не равным** тому, что на экране:
`ProvideScreenOverrides` держит номер дерева под `remember(overrides, root)` («равное дерево — не
новость», `NodeOverrides.kt`). После B-82 это правило расходится с §16.4. `update` подменил узлы и
сменил адрес. Человек нажимает «назад», и приложение загружает экран прежнего адреса — **то же самое
дерево**, равное исходному, — но подмены остаются. На экране результаты фильтра, а в адресе —
страница без него. Нашёл открытый потребитель haul (у него это B-63; обход — новое хранилище на
каждый пришедший экран).

Воспроизведение (desktopTest):

```kotlin
val overrides = KompotNodeOverrides()
var tree by mutableStateOf<KompotComponent>(ColumnComponent("page", listOf(TextComponent("t", "before"))))
setContent { CompositionLocalProvider(LocalKompotNodeOverrides provides overrides) { KompotScreen(tree, …) } }
overrides.override("t", TextComponent("t", "after"))                  // что делает `update`
tree = ColumnComponent("page", listOf(TextComponent("t", "before")))  // «назад»: экран того адреса, равный
// рисуется «after», а экран адреса говорит «before»
```

- **Решение: дерево сбрасывает подмены, когда оно пришло, а не когда отличается.** «Пришло» — это
  событие доставки: загрузка завершилась (`KompotScreenLoader` знает каждую) или приложение отдало
  экрану новое дерево. Равенство значений остаётся только для перекомпозиции, при которой ничего не
  приходило.
- Состояние узлов под `id` (§4.4, §4.12) не трогается: сбрасываются подмены, а не выбор человека.
- Не делаем: ничего в проводе.

- AC: тест-воспроизведение выше красный на `main` и зелёный после; `refresh`, вернувший равное
  дерево, тоже сбрасывает подмены; перекомпозиция с тем же деревом подмен не сбрасывает; SPEC §4.4
  уточнён; мутации убиты.
- Якоря: `kompot-client/src/commonMain/kotlin/io/github/youndie/kompot/NodeOverrides.kt`,
  `ScreenLoader.kt`, `Components.kt`, `kompot-spec/SPEC.md` §4.4.
