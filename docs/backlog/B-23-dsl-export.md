---
id: B-23
title: "Экспорт DSL-черновика из тела"
status: open
priority: P3
size: M
stage: v3-builder
epic: research-studio
blocked_by: [B-07]
---

# B-23 — Экспорт DSL-черновика из тела

Тело, собранное в студии (B-16, B-21, B-22), — JSON; сервер пишет экран на Kotlin DSL
(`kompotScreen { column { text(…) } }`). Дорога обратно — руками. Это направление
`tools/canvas/canvas_tree.py` уже ходит для макета («JSON-дерево — черновик серверного
ответа»); студия может дойти на шаг дальше — до Kotlin.

- **Решение: «Экспорт → Kotlin DSL» печатает черновик из `JsonElement` по слотам схемы: для
  типов toolkit'а — вызовы `column`/`row`/`text`/`button`/`table`/`paginatedList` из
  `kompot-standard/.../Dsl.kt`; для типов потребителя — конструктор data class'а с именованными
  аргументами (имя класса — из `x-kompot-wire-type` ↔ регистрация KSP неизвестна схеме, поэтому
  `CamelCase(wireName) + "Component"` с пометкой «проверь имя»); id печатаются явно, если не
  совпадают с детерминированным (B-07).** Потому что черновик, который компилируется после
  правки двух имён, экономит больше, чем стоит генератор.
- Действия — через `NavigateAction(...)` и прочие конструкторы `kompot-standard`; неизвестное
  действие — `TODO("<type>")`.
- Альтернатива — kotlin-scripting в обратную сторону (исполнять DSL в студии): полный компилятор
  в процессе ради превью, которое и так есть из JSON.
- Не делаем: не генерируем формы (`kompot-forms-standard` DSL связывает поле и компонент одним
  вызовом — черновик из JSON эту связь не восстановит честно); не форматируем ktlint'ом.

- AC: экспорт тела из `kompot-client-tck/corpus/` компилируется в тестовом модуле после
  подстановки импортов; экран konekt экспортируется с `UsageCounterCardComponent(...)` и
  пометкой на имени.
- Якоря: `kompot-studio/.../export/DslExport.kt` (новый), `kompot-standard/.../Dsl.kt`,
  `tools/canvas/canvas_tree.py` (та же задача для макета).
