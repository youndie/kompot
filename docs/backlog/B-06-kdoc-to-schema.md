---
id: B-06
title: "KSP переносит KDoc компонента и свойств в description схемы"
status: open
priority: P2
size: M
stage: upstream
epic: research-studio
---

# B-06 — KSP переносит KDoc в `description` схемы

Схема печатается из `SerialDescriptor`'ов, а у дескриптора нет комментариев: описания в
`kompot-spec/schema/*.json` есть только там, где их написали руками в `KompotToolkitSpec.kt`
(~30 свойств: deeplink, url, realtimeTopic, theme…) и по одному на модуль. `spacing`, `maxLines`,
`variant`, `ellipsis` — без описаний; `default` и `examples` не печатаются вовсе. Проза живёт в
KDoc и в SPEC.md. Для второй реализации на другом стеке это уже неудобно; для инспектора свойств в
студии (B-21) — блокер: панель, где у поля нет ни описания, ни умолчания, хуже текста.

- **Решение: `kompot-registry-processor` при обработке `@KompotComponentMarker` читает KDoc
  класса и свойств и пишет их в сгенерированный объект `generated<Tag>Docs: Map<String, ComponentDoc>`;
  `KompotSpecModule` получает `docs` и генератор кладёт `description` в `$defs`.** Потому что
  единственное место, где проза живёт рядом с типом и проверяется ревью, — это KDoc, и KSP уже
  видит эти классы.
- Значения по умолчанию — из KSP тоже (`KSValueParameter.hasDefault` и текст выражения), в
  `x-kompot-default` как строка: JSON `default` требует значения, а текст `Uuid.random()` им не
  является.
- Альтернатива — kotlinx-schema (JetBrains, experimental): умеет KDoc → schema, но заменяет
  генератор целиком; генератор kompot печатает своё подмножество и `x-kompot-*`, и терять это
  ради описаний — не та цена.
- Не делаем: не описываем действия (`KompotAction`) — их регистрации пишутся руками и маркера у
  них нет; отдельная задача, если понадобится.

- AC: `kompot-standard.schema.json` содержит `description` у `text.maxLines` и `column.spacing`,
  взятые из KDoc; `ToolkitSchemaGoldenTest` перезаписан; модуль без KDoc печатает то же, что
  сегодня.
- Якоря: `kompot-registry-processor/src/main/kotlin/.../KompotRegistrySymbolProcessor.kt`,
  `kompot-spec/src/main/kotlin/.../{KompotSchemaGenerator,KompotSpecModule,KompotToolkitSpec}.kt`,
  `kompot-spec/schema/`.
