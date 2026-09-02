---
id: B-14
title: "Пилот на konekt: записи и brand-a/b дают кадры голденов"
status: open
priority: P0
size: S
stage: v1-viewer
epic: research-studio
blocked_by: [B-09, B-10, B-12]
---

# B-14 — Пилот на konekt: записи и brand-a/b дают кадры голденов

Единственный настоящий потребитель с собственным словарём (14 типов), двумя brand kit'ами
(`server/src/main/resources/themes/brand-a.json`, `brand-b.json`), клиентскими формами
(`KonektShapeScale`), записанной фикстурой (`/recorded/home-screen.json`) и голденами
(`client/src/jvmTest/snapshots/Brand_A.png`, `Brand_A_Dark.png`, `Brand_B.png`, `Brand_B_Dark.png`)
— konekt. Если студия на нём не даёт те же кадры, что `viddikVerify`, значит она фотографирует не
того клиента.

- **Решение: в konekt — `client/src/jvmMain/.../Studio.kt` (15 строк) и задача
  `./gradlew :client:studio`, конфиг из того, что уже есть: `konektRegistry()`,
  `konektClientJson`, `frame = { brand, dark, content -> KonektTheme(BrandKits.kits()[brand],
  dark, …) { CompositionLocalProvider(LocalKompotRegistry …) { content() } } }`,
  `schemas = 13 toolkit'а + konekt-components + konekt-esim`, `samples = konektDictionary`.**
  Потому что цель пилота — не «работает на демо», а «работает на сборке, у которой уже есть
  голдены как оракул».
- Проверка — не глазами: снять кадр через `captureComposable` из студии (B-17 не нужна, вызов
  прямой в тесте пилота) и сравнить `ImageDiffer.diff` с `Brand_A.png` в допуске viddik.
- Что пилот обязан показать сверх кадров: `esim_transfer_widget` в теле — узел в дереве, запись
  слоя 4, unknown-block в рендере; `paginated_list` истории заказов — с заглушкой `pageLoader`.
- Альтернатива — пилот на `kompot-client-tck/corpus/`: нет ни брендов, ни словаря, ни голденов.
- Не делаем: не меняем konekt сверх одного файла и задачи; найденное в toolkit'е — отдельными
  задачами здесь.

- AC: `:client:studio` открывает окно с `home-screen.json`; бренд A/B, светлая/тёмная — четыре
  кадра совпадают с четырьмя голденами в допуске `DEFAULT_TOLERANCE_PERCENT`; список расхождений
  с ожиданиями research — в комментарии к задаче.
- Якоря: `github.com/youndie/konekt`: `client/src/jvmTest/.../screenshots/ScreenshotHarness.kt`
  (`BrandFrame`), `RecordedScreenScreenshots.kt`, `client/src/commonMain/.../theme/KonektTheme.kt`,
  `shared/spec/schema/`, `shared/components/src/commonTest/.../KonektDictionary.kt`.
