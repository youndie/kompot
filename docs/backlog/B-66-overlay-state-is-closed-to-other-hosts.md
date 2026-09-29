---
id: B-66
title: "Слой поверх экрана рисует только Material: состояние KompotOverlays закрыто для чужого хоста"
status: open
priority: P1
size: S
stage: adoption-0.38
---

# B-66 — Слой поверх экрана рисует только Material

`KompotOverlays` (`kompot-ds-material-compose/.../Overlays.kt`) хранит, что показано поверх экрана, в
`internal var presented` и `internal var asking`; наружу открыт только `isOpen`. Прочитать, **что**
рисовать, и закрыть слой может только `KompotOverlayHost` того же модуля — `ModalBottomSheet` и
`AlertDialog` Material 3 с их формой, ручкой и цветами. Дизайн-система потребителя получает `present`
и `confirm` через `withOverlays`, но нарисовать их по своему макету не может: чужой хост не
компилируется. Нашёл konekt при переходе на 0.38 — шторка подтверждения покупки на его макете не
материаловская.

Это та же ошибка, что `resolveSurface` (#80): механизм выпущен, а заменить его отрисовку нечем —
второй реализации не было, и закрытость никто не заметил.

- **Решение: хост — сменная часть, состояние — общая.** `presented` и `asking` читаются публично,
  меняются по-прежнему только через цепочку (`withOverlays`) и через публичные `dismiss()` (закрыть
  верхний слой — то, что делает `close`) и `agree()` (закрыть вопрос и отдать его действие в
  обработчик). Так чужой хост делает ровно то, что делает материаловский, и не может разойтись с
  §12.5 в том, что значит «согласился».
- Отброшено: сделать сеттеры публичными. Хост тогда может положить в слой то, чего сервер не
  присылал, и семантика «слой один, `close` закрывает верхний» живёт уже в каждом хосте.
- Отброшено сейчас: перенести `KompotOverlays` и `withOverlays` в модуль без дизайн-системы. Это
  правильное место (состояние от Material не зависит), но смена пакета — поломка у всех, кто уже
  вызывает `withOverlays`; решается вместе с 1.0, а не заплатой.
- `KompotOverlayHost` остаётся и переписывается на тот же публичный API — это и есть проверка, что
  его хватает.

- AC: хост, объявленный вне `kompot-ds-material-compose`, рисует `present` (`sheet` и `dialog`) и
  `confirm` своими компонентами, закрывает их и выполняет согласованное действие — тест в модуле
  без доступа к `internal`.
- AC: `checkKotlinAbi` показывает только добавления; запись в `UPGRADING.md` не нужна.
- AC: README `kompot-ds-material-compose` (или `kompot-client`) говорит, как написать свой хост.
- Якоря: `kompot-ds-material-compose/src/commonMain/kotlin/io/github/youndie/kompot/ds/material/Overlays.kt`,
  `kompot-ds-material-compose/src/desktopTest/kotlin/io/github/youndie/kompot/ds/material/OverlaysTest.kt`.
