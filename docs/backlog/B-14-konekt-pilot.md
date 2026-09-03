---
id: B-14
title: "Пилот на konekt: записи и brand-a/b дают кадры голденов"
status: question
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

## Почему остановлено на вопросе (03.09.2026)

Задача упирается не в код, а в два решения, которые принимает не исполнитель.

**1. Студии, которую можно взять, ещё нет.** Пилот — это `client/src/jvmMain/.../Studio.kt` в konekt,
и он должен резолвить `io.github.youndie:kompot-studio-desktop`. Модуль публикуется (B-09), но
публикация происходит **на пуш в `main`**, а вся работа лежит локальной веткой `feat/kompot-studio`.
`settings.gradle.kts` konekt тянет kompot только из `https://reposilite.kotlin.website/snapshots`,
`mavenLocal()` там нет, а версия тулкита пинится BOM'ом (`kompot = 0.36.1.112`). Значит пилот требует
одного из двух:

- пуш ветки и прогон CI — выход за «коммить локально»; или
- `mavenLocal()` в репозиториях konekt **и** подмена версии BOM на локальную сборку — это правка
  зависимостной обвязки чужого репозитория и посадка konekt на неопубликованный тулкит, что задача
  сама и запрещает («не меняем konekt сверх одного файла и задачи»).

**2. Правки в konekt — это второй репозиторий.** Файл, gradle-задача и тест сравнения с
`Brand_A.png` физически не могут лежать в kompot: konekt — закрытый потребитель, а в этом
репозитории про потребителей не пишут (см. `docs/research/research-studio.md` и README модулей).
Обратное направление — притащить записи и голдены konekt в kompot — нарушает то же правило и
вдобавок не сработало бы: рендереры `esim_card`, `plan_card` и остальных двенадцати типов живут
в konekt, и без них кадр не совпадёт ни с одним голденом.

**Что для этого готово.** Всё, чего пилот требует от тулкита: `KompotStudioConfig` с `frame`,
`brands`, `schemas`, `extensionTypes`, `crossReferenceKeys`, `pageLoader` (B-09, B-02, B-12);
источники — файл, каталог, HTTP (B-10); дерево с пометкой типа вне профиля (B-11); четыре слоя
диагностики (B-12). Оракул на стороне тулкита тоже есть: `StudioRenderTest` доказывает, что фрейм
потребителя решает цвет, а `kompotStudioFrame` — что сервер-тема доезжает.

**Чего пилот всё ещё стоит.** Он единственный проверяет то, что внутри kompot проверить нечем:
совпадут ли четыре кадра студии с четырьмя голденами `viddikVerify` у сборки, где есть свой словарь,
две темы и клиентские формы. Пока он не пройден, «студия фотографирует того же клиента» — гипотеза.

**Развилка для владельца:** влить ветку в `main` (тогда пилот делается штатно, из опубликованной
версии) — или разрешить временный `mavenLocal()` в konekt против локальной сборки тулкита.
