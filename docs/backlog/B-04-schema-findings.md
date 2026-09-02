---
id: B-04
title: "JsonSchemaValidator: структурированная ошибка вместо String"
status: open
priority: P1
size: S
stage: upstream
epic: research-studio
---

# B-04 — `JsonSchemaValidator`: структурированная ошибка вместо `String`

`JsonSchemaValidator.validate(value, ref): List<String>` (`kompot-spec/.../JsonSchemaValidator.kt`)
собирает путь при спуске — `"$"`, `"$.screen.children[0]"` — и вклеивает его в сообщение:
`"$path: required property \"id\" is missing"`. TCK это устраивает: отчёт читает человек. Студии
нужно подсветить узел в дереве и строку в тексте, а для этого путь нужно вынуть обратно —
парсить собственный префикс или форкнуть класс на 230 строк.

- **Решение: `validate` возвращает `List<SchemaFinding>` с `path: JsonPath` (список сегментов:
  имя или индекс), `message: String`, `keyword: String` (`required`, `type`, `pattern`,
  `discriminator`…), и `toString()` печатает прежнюю строку.** Потому что путь уже вычислен в
  момент ошибки — его надо перестать выбрасывать, а не восстанавливать.
- `TckRunner` и `TckFinding.message` продолжают печатать ту же строку; `kompot-tck` тесты не
  меняются, кроме типа.
- Альтернатива — второй валидатор (OptimumCode/json-schema-validator, KMP, output-форматы
  2020-12): полнее драфта, но kompot печатает своё подмножество ключевых слов и хочет проверять
  ровно его, а не всё, что понимает чужая библиотека; и это вторая зависимость на JVM-only модуле,
  чья единственная работа — совпадать с генератором.
- Не делаем: не расширяем набор ключевых слов; не добавляем severity — у схемы её нет.

- AC: тест в `kompot-spec` получает у ошибки `path == listOf("screen", "children", 0)` и
  `keyword == "required"`; `TckRunner` печатает прежний текст; `kompot-tck` зелёный без правки
  ожиданий.
- Якоря: `kompot-spec/src/main/kotlin/io/github/youndie/kompot/spec/JsonSchemaValidator.kt`,
  `kompot-tck/src/main/kotlin/io/github/youndie/kompot/tck/TckRunner.kt` (`SCREEN_SCHEMA`, проверка `schema`).
