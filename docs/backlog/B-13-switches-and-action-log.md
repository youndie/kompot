---
id: B-13
title: "Бренд, тема, размер устройства, состояния формы, лог действий"
status: open
priority: P1
size: S/M
stage: v1-viewer
epic: research-studio
blocked_by: [B-09]
---

# B-13 — Бренд, тема, размер, состояния формы, лог действий

Тулбар студии — то, что у DivKit Playground стоит над кадром: размер устройства, тема, и то,
чего там нет — бренд. Плюс два kompot-специфичных переключателя: состояние формы (README
kompot: «форма — не одна картинка: пустая, заполненная и со всеми ошибками — три») и действия,
которые в превью просто теряются (`actionHandler = KompotActionHandler {}`).

- **Решение: в тайтлбаре `DecoratedWindow` — `brand ▾` (из `config.brands`), `☾` (dark),
  `393×852 ▾` (пресеты: 360×640, 393×852 как канвас konekt, 768×1024, «по окну»); в панели рендера
  — `Form: empty | filled | errors` → `KompotPreviewState(values, allFieldsChanged)`; внизу — лог
  `KompotAction` с `toString()` и временем.** Потому что каждый из переключателей меняет ровно
  один параметр уже существующего API (`frame(brand, dark)`, `Modifier.requiredSize`,
  `KompotPreviewState`, `actionHandler`) — это не фича, это ручки.
- `navigate` в логе при источнике `Http` с графом — кликабелен: открывает маршрут по deeplink
  (`NavigationGraph.routeFor`). `open_url` — не открываем ничего, только пишем.
- Плотность 1, как в `tools/canvas`: CSS px макета = dp кадра.
- Альтернатива — `isSystemInDarkTheme()` и размер окна: это и есть «запусти клиент».
- Не делаем: `filled` заполняет поля значениями из образцов (`samples`) — если их нет, то
  первым допустимым значением типа; редактор значений полей — v3.

- AC: переключение бренда и темы перерисовывает кадр без перезагрузки тела; кадр 393×852 при
  большем окне обрезан рамкой устройства; нажатие кнопки в кадре добавляет строку в лог;
  `errors` показывает форму со всеми ошибками валидации.
- Якоря: `kompot-studio/.../{Toolbar,ActionLog,FormStateSwitch}.kt` (новые),
  `kompot-preview/.../KompotPreview.kt` (`KompotPreviewState`), `kompot-navigation/.../NavigationGraph.kt`.
