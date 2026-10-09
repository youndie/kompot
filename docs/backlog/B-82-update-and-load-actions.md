---
id: B-82
title: "update и load: ответ, подменяющий узлы, и GET, отвечающий действием"
status: open
priority: P1
size: L
stage: partial-updates
epic: research-partial-updates
blocked_by: [B-81]
---

# B-82 — update и load: ответ, подменяющий узлы, и GET, отвечающий действием

Проводная половина [research-partial-updates](../research/research-partial-updates.md) §4.2–4.5.

- **`update { updates: [UpdateComponentMessage], deeplink?, history? }`** — подменяет узлы по
  `id` через хранилище из [B-81](B-81-screen-keeps-its-tree.md). Кадры применяются по порядку, на
  одинаковый `id` побеждает последний, незнакомый `id` игнорируется. `deeplink` и `history`
  (`push` — умолчание, `replace`, незнакомое слово значит `push`) передаются приложению: историю
  ведёт оно.
- **`load { url }`** — `GET url`, ответ `KompotAction` идёт через всю цепочку. Ответ на `load`,
  после которого на том же экране нажат ещё один, отбрасывается. Признак «загрузка в пути» доступен
  приложению. Новый вид эндпоинта `load` в §16.1.
- Модуль выбирает реализация, причину записать: кандидаты — `kompot-commands` (рядом с
  `perform`) и `kompot-realtime` (дом `UpdateComponentMessage`).
- Вместе с типами: SPEC (§4, §16.1, §16.4, §15 — пример деградации `load` в дереве), схемы и
  проверка совместимости, DSL (`DslReachesTheWireTest`), TS-типы, кейсы `kompot-client-tck`, обход
  вида `load` в `kompot-tck`, `ktor`-хелпер ответа, `UPGRADING.md`.

- AC:
  - `perform` отвечает `update` с двумя узлами → на экране подменены оба, остальные узлы и их
    состояние не тронуты, стек не вырос;
  - `load` с ответом `update{deeplink}` → узлы подменены, приложение получило адрес и `push`;
  - два `load` подряд, первый отвечает позже → на экране ответ второго;
  - `load` отвечает `navigate` → обычный переход;
  - кейсы корпуса: незнакомый `id`, порядок кадров, незнакомое слово `history`;
  - `checkSchemaCompatibility` — `COMPATIBLE`; мутации убиты.
- Якоря: `kompot-commands/`, `kompot-realtime/`, `kompot-client/.../Perform.kt`, `Refresh.kt`,
  `kompot-ktor/`, `kompot-spec/SPEC.md`, `kompot-spec/schema/`, `kompot-client-tck/corpus/`,
  `kompot-tck/`.
