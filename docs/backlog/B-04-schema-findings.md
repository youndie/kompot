---
id: B-04
title: "JsonSchemaValidator: структурированная ошибка вместо String"
status: done
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

## Итог

`JsonSchemaValidator.validate` возвращает `List<SchemaFinding>`; `JsonPath` (сегменты `Name`/`Index`),
`keyword` (`required`, `type`, `pattern`, `discriminator`, `oneOf`, `enum`, `const`, `not`,
`additionalProperties`). `toString()` печатает прежнюю строку. Четыре теста в `SchemaFindingTest`;
`kompot-tck` зелёный (11 классов) **без правки ожиданий**; три аудита публикации зелёные.

- **Путь перестал вычисляться и выбрасываться.** Он и раньше считался при спуске — просто вклеивался
  в начало предложения. Теперь по дереву спускается `JsonPath`, а строка получается из него, а не
  наоборот: одно представление, два вида.
- **Сегменты типизированы**, а не `List<Any>` из строк и чисел, как в формулировке AC. Смешанный
  список делает кастом каждое использование; утверждение в тесте стало
  `listOf(Name("screen"), Name("children"), Index(0))` — то же самое, только называет себя.
- **Сторож на схождение двух нотаций.** Отдельный тест требует, чтобы `finding.path.toString()` был
  среди путей `walkJsonObjects` того же тела: студия сшивает находки со строками дерева по строке,
  и если нотации разойдутся, сшивка станет пустой — молча.
- **Контроль «чистое тело не даёт находок»** рядом: без него все утверждения выше проходят и у
  валидатора, который ругается на всё.
- **TCK печатает ровно ту же строку** — `.map { it.toString() }` в двух местах. Замеченная по дороге
  кривизна (`"[$index]$it"` даёт `[0]$.foo`, что не JSONPath) **не тронута**: это текст отчёта для
  человека, задача его явно выводила за рамки, и менять его значило бы двигать ожидания, которые AC
  просил не двигать.
- В студии `StudioFinding` получил `path` и `keyword` — их читателя даёт B-12.
