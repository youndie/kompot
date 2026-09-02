---
id: B-11
title: "Дерево экрана из JSON по слотам схемы (Jewel LazyTree)"
status: open
priority: P0
size: M
stage: v1-viewer
epic: research-studio
blocked_by: [B-05, B-08]
---

# B-11 — Дерево экрана из JSON по слотам схемы

Левая панель — дерево узлов тела. Строить его из объектов (`KompotComponent`) нельзя: у
неизвестного клиенту типа объект — `UnknownComponent(originalType, fallback)`, и дерево потеряло
бы то, что в нём важнее всего показать. Строить по ручному списку контейнеров — повторить
`KonektWalk` с его пятью протухшими копиями. Источник структуры — JSON и слоты из схемы (B-05).

- **Решение: узел = `JsonObject` с `type`; дети — по `childSlots(schemas)`; подпись —
  `type#id`, для `text` — начало текста; путь узла — `JsonPath` в формате префикса валидатора
  (`$.screen.children[0]`).** Виджет — Jewel `LazyTree` с `SpeedSearchArea`. Потому что один
  формат пути у дерева, у ошибок схемы (B-04) и у правил тела (B-12) делает диагностику
  кликабельной без единого парсера.
- Выбор узла → в рендере подсвечивается рамка: через `LocalKompotRealtimeUpdates` **не** подменяем
  (это изменило бы дерево); вместо этого декоратор карты рендереров (как `withImpressionTracking`)
  оборачивает `Render` в `Box(border)` для выбранного id. Клик по узлу в рендере (обратное
  направление) — v2, если понадобится.
- Узел, чьего типа нет в профиле, помечается в дереве иконкой и попадает в диагностику; узел без
  рендерера (`UNRENDERABLE_COMPONENT`) — другой иконкой, из `onDegraded`.
- Альтернатива — дерево из `KompotComponent` через `when` по типам: ломается на первом
  компоненте потребителя.
- Не делаем: правки в дереве — B-16.

- AC: тело из `kompot-client-tck/corpus/` показывает дерево с теми же узлами, что `collectJsonObjects`;
  клик по узлу подсвечивает его в рендере; для тела с `esim_transfer_widget` узел есть, помечен,
  рендер показывает плейсхолдер.
- Якоря: `kompot-studio/.../tree/{ScreenTree,TreeModel,SelectionDecorator}.kt` (новые),
  `kompot-client/.../ImpressionTracking.kt` (образец декоратора), `kompot-spec` (B-05).
