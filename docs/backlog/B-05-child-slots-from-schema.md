---
id: B-05
title: "childSlots(schemas): дочерние слоты компонента из $ref схемы"
status: done
priority: P1
size: S
stage: upstream
epic: research-studio
---

# B-05 — `childSlots(schemas)`: дочерние слоты из `$ref` схемы

`KompotComponent` не объявляет детей; вложенность — конвенция каждого типа: `column.children`,
`row.children`, `paginated_list.initialItems` **и** `emptyState`, `wizard_screen.content`, у
потребителя — `surface.children`, а `bottom_nav.items` — не компоненты вовсе. Единственный обход в
toolkit'е — `collectJsonObjects(JsonElement)` в `kompot-spec/.../JsonWalk.kt`, без путей и без
понятия «слот». konekt держит список руками (`KonektWalk.kt`) и пишет, что пять копий этого списка
существовали и каждая протухала отдельно — обход, который не знал про `emptyState`, был слеп ровно
тогда, когда на экране одна вещь. При этом схема знает всё: свойство, чей `$ref` или
`items.$ref` ведёт в иерархию `KompotComponent`, — это слот.

- **Решение: `public fun childSlots(schemas: Map<String, JsonObject>): Map<String, List<Slot>>`**
  в `kompot-spec` — wire type → слоты (`name`, `many: Boolean`, `required`), выведенные из
  `$defs` по `x-kompot-kind: "variant"` и `$ref` на `KompotComponent`; плюс
  `walk(element: JsonElement, slots): Sequence<Pair<JsonPath, JsonObject>>`. Потому что список,
  который выводится из генерируемого артефакта, не может отстать от типов — а ручной отстаёт.
- Альтернатива — рефлексия по `SerialDescriptor` в рантайме клиента: работает на JVM, но студии
  нужна и JSON-сторона (узел без рендерера — всё равно узел дерева), а схема уже есть.
- Не делаем: не добавляем `children` в `KompotComponent` — контракт core открыт намеренно; не
  выражаем «column может содержать только X».

- AC: тест: по 14 схемам toolkit'а `childSlots` даёт `column → [children*]`,
  `paginated_list → [initialItems*, emptyState]`, `wizard_screen → [content]`, `text → []`;
  `walk` по `kompot-client-tck/corpus/*.json` посещает столько же объектов с `type`, сколько
  `collectJsonObjects`.
- Якоря: `kompot-spec/src/main/kotlin/io/github/youndie/kompot/spec/{JsonWalk,KompotSchemaGenerator,KompotProtocol}.kt`,
  `kompot-spec/schema/kompot.profile.schema.json`.

## Итог

В `kompot-spec`: `Slot(name, many, required)`, `childSlots(schemas, hierarchy = "KompotComponent")`,
`JsonNode(path, value)`, `walkJsonObjects(element)`, константа `KompotProtocol.COMPONENT_HIERARCHY`.
Пять тестов в `ChildSlotsTest` зелёные.

- **Закрытый список берётся из `discriminator.mapping` профиля,** а не сканированием `$defs` по
  `x-kompot-kind: "variant"`. Профиль — это типы, которые **данная сборка** может получить; скан
  ответил бы на другой вопрос — «типы, оказавшиеся на класспасе». Поэтому `childSlots` без профиля
  падает с объяснением, а не молча отдаёт половину.
- **`walk` получился структурным, без параметра `slots`,** и это отступление от формулировки задачи.
  Обход по слотам ровно настолько полон, насколько полна схема: узел незнакомого типа, тело в
  конверте (`KompotFormResponse.screen`), свойство, добавленное на прошлой неделе, — каждое место,
  где такой обход слепнет молча. Схема говорит, что такое **слот**; чем является дерево, она не
  решает. Поэтому AC «`walk` по корпусу посещает столько же объектов, сколько `collectJsonObjects`»
  выполняется буквально — и это именно то, чем он полезен: сторож на потерю узлов при добавлении
  путей. Тест падает громко, если корпус переехал, — иначе он проверял бы пустоту.
- **Отрицательная половина проверяется отдельно.** Без «`text`, `table`, `button`, `image` — слотов
  нет» утверждения про `column.children` проходили бы у реализации «любой массив — слот».
  `table.rows` — массив строк, и он должен остаться не-слотом.
- **Полнота — сравнением ключей с mapping профиля.** Карта, которая отвечает за часть типов и молча
  пропускает остальные, оставляет вызывающего с `slots[type].orEmpty()` и без способа узнать, какое
  из двух значит пустой список.
- Путь — нотация валидатора (`$`, `$.screen.children[0]`), чтобы находка и узел сходились без
  перевода одной записи в другую. Уникальность путей внутри одного тела тоже проверяется.
- Ожидание в тесте было неверным один раз: `wizard_screen.content` — **required**. Исправлено
  ожидание, а не код.
