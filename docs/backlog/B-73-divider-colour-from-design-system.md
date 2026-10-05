---
id: B-73
title: "Разделитель без цвета рисуется outlineVariant Material, и дизайн-система его не меняет (#204)"
status: done
priority: P2
size: S
---

# B-73 — Разделитель без цвета рисуется outlineVariant Material

[#204](https://github.com/youndie/kompot/issues/204), найдено при переходе shashki на 0.39
([shashki#43](https://github.com/youndie/shashki/issues/43)).

SPEC §4.10 говорит, что цвет `divider` — «дизайн-системы (тот же, что у линий `table`)», если сервер
не назвал токен. В Kotlin-клиенте он был Material: `DividerRenderer` брал
`MaterialTheme.colorScheme.outlineVariant`, `TableRenderer` — его же для рамки и линий строк и
`surfaceVariant` для заливки заголовка. Ни один из трёх вопросов `KompotDesignSystem` об этом цвете не
задавался, так что деплой с не-Material дизайн-системой мог сказать, как выглядит его линия, только
токеном на каждом разделителе с сервера — так shashki и обходит (`hairline` на каждом `divider`).

- **Решение: две роли поверхности**, а не новый вызов. `resolveSurface` уже отвечает «как выглядит то,
  что рендерер рисует сам», и его пустой ответ уже значит «умолчание тулкита». `divider` — линия
  (`outline`), одна на разделитель и на рамку и линии таблицы, чтобы они читались одной линией;
  `table_header` — заливка (`container`) и цвет слов (`content`) строки-заголовка: `content` рядом с
  `container` по той же причине, по какой он вообще есть в `KompotSurface`.
- Не ответившая дизайн-система (`Color.Unspecified`) получает прежние цвета Material — ничего не
  меняется ни у `Material3DesignSystem`, ни у тем поверх неё.
- AC: дизайн-система, ответившая на роли, меняет цвет разделителя, рамки и линии таблицы и заливку
  заголовка; не ответившая рисует как раньше; голдены `kompot-ds-material-compose` не сдвинулись.
- Якоря: `kompot-client/.../Surface.kt` (`KompotSurfaceRoles`, `ruleColor`), `Stack.kt`
  (`DividerRenderer`), `Components.kt` (`TableRenderer`), SPEC §4.10, §6.

## Находки

### Итерация 1 — 2026-10-05

**Сначала красный.** `RuleColourTest` (desktop, по пикселям снимка): роли заведены, рендереры ещё не
спрашивают — разделитель вышел `#CAC4D0` (outlineVariant светлой схемы) вместо цвета роли, заливка
заголовка — surfaceVariant; контроль «дизайн-система не ответила» зелёный и на старом коде. После
правки — три из трёх.

**Голдены не сдвинулись:** `:kompot-ds-material-compose:desktopTest` зелёный без перезаписи —
`Material3DesignSystem` на роли не отвечает, и фолбэк тот же цвет, что был.

**API — только добавления:** `KompotSurfaceRoles.Divider` и `.TableHeader`; дамп ABI вырос на две
константы, объявленной поломки нет. SPEC §6 перечисляет новые роли, §4.10 называет роль `divider`.

**Что снимает shashki:** токен `hairline` на каждом `divider` — достаточно ответить на роль `divider`
в своей дизайн-системе (`KompotSurface(outline = …)`).
