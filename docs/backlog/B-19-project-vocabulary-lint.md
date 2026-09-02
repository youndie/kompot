---
id: B-19
title: "Слой словаря проекта: открытые слова и токены кита"
status: open
priority: P2
size: S/M
stage: v2-editor
epic: research-studio
blocked_by: [B-12]
---

# B-19 — Слой словаря проекта: открытые слова и токены кита

Схема закрывает типы и поля, но kompot намеренно оставляет открытыми **слова**: `button.variant`,
`checkbox_input.variant`, у потребителя `surface.tone`, `counter.state`, и токены `ColorToken`/
`TypographyToken`. Неизвестное слово рисует нейтральный вариант — тихо; неназванный в ките
токен падает на встроенную палитру — «один контрол в одном состоянии в Material-фиолетовом внутри
вашего бренда, и находит его покупатель со скриншотом» (`design-brand-kit.md`). Ни то, ни другое
не ошибка схемы, и ни то, ни другое не найдёт слой 4: рендер состоялся.

- **Решение: пятый слой диагностики, `severity = warning`, из двух проверок над телом.**
  (1) Слова: `config.vocabulary: Map<wireType, Map<field, Set<String>>>` (konekt отдаёт
  `CounterStates.all`, `ButtonEmphasis`, `SurfaceTones`…) — значение поля не из множества →
  «`tone = "papper"` неизвестно этому клиенту, нарисуется `neutral`». (2) Токены: каждый
  `ColorToken`/`TypographyToken` в теле (поля помечены в схеме `x-kompot-kind: "token"`) должен
  быть назван в **каждом** ките из `config.brands` **в обеих палитрах**, либо входить в
  `M3Colors.all`/`M3Typography` — иначе «токен `promo_gold` не назван в brand-b/dark →
  дефолт Material». Потому что оба класса ошибок уже описаны потребителем как реальные, и обе
  проверки — сравнение множеств.
- Токены кита студия читает из `KompotTheme` (`light.colors.keys`, `dark?.colors?.keys`,
  `typography.keys`) — тех же объектов, что даёт `frame`/дефолтный frame; отдельного ввода нет.
- Альтернатива — ждать `enum` на проводе: kompot отказался от этого осознанно (Vocabulary.kt в
  konekt объясняет почему), и линт — правильная цена открытости.
- Не делаем: не проверяем слова, для которых потребитель не дал множества; не проверяем
  `SurfaceRole` — он не путешествует.

- AC: тело konekt с `state = "dormant"` при словаре без этого слова даёт warning с путём; тело с
  `ColorToken("promo_gold")` при двух китах даёт по warning на кит/палитру, где его нет; при
  добавлении токена в кит warning исчезает без перезапуска (кит перечитан).
- Якоря: `kompot-studio/.../diagnostics/{VocabularyRules,TokenRules}.kt` (новые),
  `kompot-ds-material/.../Material3Tokens.kt` (`M3Colors.all`), `kompot-theme/.../KompotTheme.kt`,
  `kompot-spec/schema/*.json` (`x-kompot-kind: "token"`).
