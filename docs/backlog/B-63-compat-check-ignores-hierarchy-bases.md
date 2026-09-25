---
id: B-63
title: "Проверка совместимости не видит изменений в базе иерархии"
status: wip
priority: P1
size: S/M
stage: release-0.38
---

# B-63 — Проверка совместимости не видит изменений в базе иерархии

`:kompot-spec:checkSchemaCompatibility` сравнивает члены иерархии, её дискриминатор и флаги
`open`/`degrades`, а свойства и `required` **базового определения** иерархии (`KompotComponent` в
`kompot-core.schema.json`: `id`, `modifiers`, с B-49 — `fallback`) не сравнивает ни правилами, ни
«растяжкой» остатка: `residualChanges` вычитает из определения ровно `properties` и `required`, считая,
что их разобрали правила, а для иерархий этого никто не делает.

Минимальный репро (2026-09-25, найдено в [B-49](B-49-local-state.md)): из `required` базы
`KompotComponent` убран `id`, из её свойств — `modifiers`. Отчёт — те же «14 files against 14,
3 changes», «Nothing incompatible. The protocol version stands.» Изменение, по §15 заведомо
несовместимое, проходит молча, хотя §15 обещает обратное: «Молчанием такое изменение не проходит».

- **Решение:** базовые свойства и `required` иерархии проверять теми же правилами, что свойства
  обычного определения (`propertyChanges`), — поле базы есть поле каждого узла иерархии.
  Альтернатива «включить их в остаток» хуже: остаток отвечает `UNCLASSIFIED`, а у изменений базы
  классификация известна (§15: поле с умолчанием — совместимо, изменение `required` — нет).
- Не делаем: пересмотр того, какие поля лежат в базе.

- AC: репро выше даёт `BREAKING` на `id` и на `modifiers`; добавление `fallback` в базу (B-49)
  классифицируется как совместимое добавление поля; тест в `SchemaCompatibilityTest` держит оба
  случая и падает, если сравнение баз убрать.
- Якоря: `kompot-spec/src/main/kotlin/io/github/youndie/kompot/spec/SchemaCompatibility.kt`
  (`hierarchyChanges`, `residualChanges`, модель иерархии), `kompot-spec/SPEC.md` §15.
