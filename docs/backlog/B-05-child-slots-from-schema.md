---
id: B-05
title: "childSlots(schemas): дочерние слоты компонента из $ref схемы"
status: open
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
