---
id: B-18
title: "Истории: образцы словаря, состояния формы, GeneratedViddikRegistry"
status: open
priority: P1
size: M
stage: v2-editor
epic: research-studio
blocked_by: [B-09]
---

# B-18 — Истории: образцы словаря, состояния формы, `GeneratedViddikRegistry`

Секция 06 канваса konekt — «словарь компонентов, все состояния, включая unknown-component block»
— нарисована руками и стареет; konekt держит рядом `konektDictionary: List<Pair<wireName,
KompotComponent>>` (один полностью заполненный экземпляр на тип) и `@ViddikScreenshot`-фикстуры
с `GeneratedViddikRegistry.components`. Это и есть истории Storybook — их только некому показать.

- **Решение: вкладка «Истории» слева с тремя источниками.** (1) `config.samples` — по узлу на
  тип, тело = `encodeKompotComponent(sample)` (полиморфно, чтобы `type` у корня был); (2)
  вариации: для каждого образца × каждое слово из `config.vocabulary[type][field]` (например,
  `usage_counter_card.state ∈ CounterStates.all`) — так «все состояния» перестают быть
  рисунком; для формы — `KompotPreviewState` empty/filled/errors; (3) `GeneratedViddikRegistry`
  из classpath потребителя рефлексией, как `ViddikShowroomLauncher.loadComponents()`, — это
  композиции, а не тела, рисуются напрямую. Потому что три списка уже существуют у потребителя
  как проверяемые артефакты, и студия им ничего не добавляет, кроме окна.
- Сетка историй — `ViddikComponent`-подобная карточка с кадром; клик открывает как обычный
  экран (дерево, диагностика, бренд).
- Альтернатива — свой формат историй (`*.stories.json`): четвёртый список того же, что уже в трёх.
- Не делаем: не генерируем образцы из схемы (у неё нет `examples`, B-06 даёт только описания);
  «тип без образца» показывается как пустая карточка с записью «нет образца» — это и есть
  проверка `RendererCoverageIsDocumentedTest` глазами.

- AC: для konekt вкладка показывает 14 карточек словаря, счётчик по вариациям
  (`usage_counter_card` ×4 состояния), и голден-фикстуры viddik под их `group`; тип без образца
  виден как отсутствие.
- Якоря: `kompot-studio/.../stories/{Stories,SampleStories,VocabularyVariants,ViddikStories}.kt`
  (новые), `viddik/viddik-testing-core/.../ViddikShowroomLauncher.kt`,
  `kompot-core/.../KompotJson.kt`.
