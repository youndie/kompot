---
id: B-06
title: "KSP переносит KDoc компонента и свойств в description схемы"
status: done
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

## Итог

`KompotComponentDoc` в `kompot-registry-annotations`; процессор читает KDoc класса и свойств и пишет
`generated<Tag>Docs`; `KompotSpecModule.docs` и генератор кладут `description` в `$defs`. Четыре теста
в `SchemaDescriptionTest`; голдены схем перезаписаны; три аудита публикации зелёные.

- **Посылка задачи не сходилась с кодом: KDoc в протокольных модулях НОЛЬ.** Тулкит комментирует
  свойства `//`-строками, а `KSDeclaration.docString` их не видит. Механизм без входа — это
  «написано, но никем не вызывается», поэтому задача включила и первый KDoc: `text`, `column`, `row`,
  `button` и свойства `maxLines`, `ellipsis`, `spacing`, `variant` — ровно те, что задача называла
  пробелом.
- **`//`-комментарии не собираются, и это решение, а не ограничение.** Они объясняют **код**: почему
  свойство появилось, что ломалось до него, в каком порядке идёт фолбэк. Схему читает один адресат —
  тот, кто реализует этот провод на другом стеке, — и десять строк истории в `description` сделали бы
  её длиннее и хуже. KDoc стал помеченным каналом: написать его значит сказать «эта фраза для схемы».
  Отсюда же и то, что фича opt-in и почти пустая: тип без KDoc печатает ровно ту схему, что вчера.
- **Ключ — `serialName`, а не ключ схемы.** Процессор знает `@SerialName`, но не знает, как генератор
  назовёт определение; вычислять ключ дважды — это способ разойтись на одном типе так, что никто не
  заметит.
- **Приоритет: рукописные `annotations` побеждают KDoc.** Они пишутся в spec-модуль намеренно, обычно
  чтобы задать формат, которого нет в типе, и комментарий не должен их тихо подменять. Проверено
  тестом на синтетическом модуле, где описаны оба.
- **Контроль «тип без KDoc не изменился»** — отдельным тестом и самим дифом голденов: изменился
  **только** `kompot-standard.schema.json`, остальные тринадцать файлов байт в байт прежние.
- **`x-kompot-default` не сделан, и вот почему.** KSP отдаёт `KSValueParameter.hasDefault: Boolean`,
  но **не текст выражения** — а булево «умолчание есть» схема уже несёт через `required`. Печатать
  `x-kompot-default: true` значило бы дублировать `required` под другим именем. Текст умолчания
  достижим только разбором исходника, то есть вторым парсером Kotlin.
- **Аудит поймал мою же ошибку:** `KompotStudioScreen` (добавлен в B-14) отдаёт `KompotPreviewState`,
  а `kompot-preview` был объявлен `implementation` — та же форма #70, что пилот нашёл в konekt.
  Исправлено на `api`.
