---
id: B-12
title: "Диагностика: синтаксис, схема, правила тела, деградации"
status: open
priority: P0
size: L
stage: v1-viewer
epic: research-studio
blocked_by: [B-02, B-04]
---

# B-12 — Диагностика: синтаксис, схема, правила тела, деградации

«Экран уезжает без релиза клиента» значит, что ошибку в экране находит не компилятор. Сегодня её
находит либо TCK на живом сервере (`kompot-tck`: `component-id`, `text-spans`, `schema`,
`form-fields`…), либо человек с устройством. В студии четыре из этих источников работают над телом
без сервера, и каждый отвечает на свой вопрос: «это вообще JSON», «это тело по профилю сборки»,
«это тело по правилам, которых схема не выражает», «этот клиент это нарисует».

- **Решение: панель с четырьмя слоями и одним типом записи `Finding(layer, path: JsonPath,
  message, severity)`.**
  1. синтаксис — `SerializationException`, offset → строка/колонка;
  2. схема — `JsonSchemaValidator(schemas, strictProfile, extensionTypes)` по
     `kompot.profile.schema.json#/$defs/KompotComponent` (B-04 даёт путь);
  3. правила тела, перенесённые из `TckRunner` как чистые функции над `JsonElement`:
     id непустой и уникальный; `text` равен конкатенации `spans`; для `KompotFormResponse` —
     `fieldId` схемы ↔ полей экрана ↔ ссылок; паттерны deeplink/url уже в схеме;
  4. деградации настоящего рендера — `onDegraded(kind, originalType)`: `UNKNOWN_COMPONENT`,
     `UNRENDERABLE_COMPONENT`, `UNKNOWN_ACTION`, плюс «корень без `type`» (`originalType == "unknown"`,
     ровно тот случай `call.respond(component)`, ради которого превью принимает строку).
  Потому что каждый слой существует и проверен отдельно; студия их не изобретает, а собирает.
- Клик по записи выбирает узел в дереве (B-11); слои 3 и 4 не требуют от потребителя ничего,
  кроме `schemas`.
- Правила слоя 3 живут в `kompot-spec` как `BodyRules.check(body: JsonElement): List<Finding>`,
  и `TckRunner` вызывает их же — иначе два списка правил разойдутся.
- Альтернатива — гонять `TckRunner` с фейковым транспортом: он требует OpenAPI и ходит по
  маршрутам; для одного тела это лишнее.
- Не делаем: слой словаря проекта (открытые слова, токены кита) — B-19; `etag`, `pagination`,
  `idempotency`, `auth` — остаются TCK.

- AC: тело с дублем id, `text ≠ spans` и типом вне профиля даёт три записи с путями; клик
  выделяет узел; корень, закодированный конкретным сериализатором, даёт запись слоя 4 с подсказкой
  про `respondKompotComponent`; `kompot-tck` использует `BodyRules` и остаётся зелёным.
- Якоря: `kompot-spec/src/main/kotlin/.../{JsonSchemaValidator,BodyRules(новый)}.kt`,
  `kompot-tck/src/main/kotlin/.../TckRunner.kt` (проверки `component-id`, `text-spans`, `form-fields`),
  `kompot-preview/.../KompotPreview.kt` (`failOnDegradation`, `NO_DISCRIMINATOR`),
  `kompot-studio/.../diagnostics/` (новый).
