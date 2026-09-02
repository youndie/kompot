---
id: B-07
title: "DSL: детерминированный id вместо Uuid.random() при пропуске"
status: open
priority: P2
size: S
stage: upstream
epic: research-studio
---

# B-07 — DSL: детерминированный id вместо `Uuid.random()`

`kompotScreen { column { text("…") } }` в `kompot-standard/.../Dsl.kt` подставляет
`id ?: Uuid.random().toString()` в шести местах. Два вызова одного и того же DSL дают два разных
дерева; диф по id (`tools/canvas/canvas_tree.py --compare`, записи стенда, будущий диф студии
«что изменилось») видит переименование каждого узла вместо правки одного. Live-обновление по id
(`LocalKompotRealtimeUpdates`) с таким id не адресуется вовсе.

- **Решение: id по умолчанию — путь узла в дереве** (`root`, `root/0`, `root/0/2`), выданный
  билдером при `build()`, когда родитель знает позицию ребёнка. Потому что уникальность в
  пределах дерева — единственное требование SPEC (§ про ids, проверяется TCK `component-id`), а
  путь уникален и стабилен между двумя сборками одного экрана.
- Явный id по-прежнему побеждает; сервер, которому нужен адресуемый узел, называет его сам —
  это не меняется.
- Альтернатива — хеш содержимого: стабилен, пока не меняется текст, и меняется ровно тогда, когда
  правка должна читаться как правка узла, а не его замена.
- Не делаем: не трогаем `UnknownComponent(id = "unknown")` и не требуем id от пользователей DSL.

- AC: два вызова `kompotScreen { … }` с одним телом дают равные деревья; `FormStandardDslTest`
  и тесты DSL зелёные; `kompot-tck` `component-id` на дереве из DSL без явных id проходит.
- Якоря: `kompot-standard/src/commonMain/kotlin/io/github/youndie/kompot/standard/Dsl.kt`,
  `kompot-core/src/commonMain/.../dsl/`, `kompot-forms-standard` (DSL форм).
