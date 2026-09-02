---
id: B-17
title: "Снять кадр и сравнить с голденом через viddik"
status: open
priority: P1
size: M
stage: v2-editor
epic: research-studio
blocked_by: [B-09]
---

# B-17 — Снять кадр и сравнить с голденом через viddik

README kompot: «превью и голден — один вход и две проверки». Сейчас вторая проверка живёт
только в тестах (`viddikVerify` у konekt, ручная проводка в `kompot-ds-material-compose`);
из студии, где экран только что стал правильным, до голдена — переключение в IDE, аннотация,
`VIDDIK_RECORD_MODE=true`. viddik уже отдаёт всё нужное как функции:
`captureComposable(width, height, compositionLocals, fontScale, content): BufferedImage`,
`ImageDiffer.diff(expected, actual, channelTolerance): DiffResult`, конвенцию имени
`"${group}_${name}".png` и каталога `src/<testSourceSet>/snapshots`.

- **Решение: кнопки «Снять кадр» и «Сравнить с голденом» в панели рендера.** Кадр —
  `captureComposable` с той же композицией, что в окне: `frame(brand, dark) { KompotPreview(…) }`,
  и с `LocalViddikDarkTheme provides dark` — так же, как это читает `BrandFrame` в konekt.
  Сравнение — `ImageDiffer.diff` с голденом по имени `<группа>_<имя>.png` из `snapshotsDir`
  конфига; диф показывается рядом с кадром. Потому что viddik-функции уже детерминированы
  (пиннинг растеризации через `Matrix44`), а вызов из студии ничего в них не меняет.
- `viddik-testing-core` тянет `compose.desktop.currentOs` и JUnit как `api` — поэтому зависимость
  на стороне **приложения потребителя** (`runtimeOnly`), а студия обращается через маленький
  интерфейс `FrameCapture` с реализацией по умолчанию через рефлексию, как
  `ViddikShowroomLauncher` грузит реестр. Без viddik в classpath кнопки скрыты.
- Типографика кадра — `viddikTypography(scale)` из `frame` потребителя, иначе голден не
  портативен между Mac и Linux (skills/kompot-layout: 4–8 % пикселей на платформенном шрифте).
- Альтернатива — Roborazzi desktop: второй тестер на той же линии Compose ради одной функции.
- Не делаем: не запускаем `viddikVerify`; запись голдена — «сохранить кадр как …», а не запись
  в тестовый каталог с решением за потребителя.

- AC: кадр из студии для konekt brand-a light совпадает с `Brand_A.png` в допуске
  `DEFAULT_TOLERANCE_PERCENT`; при изменении цвета в ките диф показывает красные пиксели;
  без viddik в classpath студия работает, кнопок нет.
- Якоря: `kompot-studio/.../capture/{FrameCapture,ViddikCapture}.kt` (новые),
  `viddik/viddik-testing-core/.../{CaptureEngine,ImageDiffer,ViddikEngine,ViddikShowroomLauncher}.kt`.
