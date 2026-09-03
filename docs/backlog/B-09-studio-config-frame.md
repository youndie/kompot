---
id: B-09
title: "KompotStudioConfig и frame; дефолтный frame из файлов KompotTheme"
status: done
priority: P0
size: M
stage: v1-viewer
epic: research-studio
blocked_by: [B-08]
---

# B-09 — `KompotStudioConfig` и `frame`; дефолтный frame из файлов `KompotTheme`

Студия должна рисовать экран рендерерами **потребителя**, а бренд у потребителя — не только
`KompotTheme`: konekt резолвит формы в `KonektShapeScale.byBrand`, шрифты в `KonektTypography`,
и собирает всё в `KonektTheme(theme, darkMode, typography)`; его скриншотный `BrandFrame(brand,
content)` — ровно та композиция, которую фотографируют голдены. Если студия попробует собрать
бренд сама из `KompotTheme`, она сфотографирует «второго клиента, которого никто не выпускает»
(комментарий в `ScreenshotHarness.kt`).

- **Решение: `KompotStudioConfig(registry, json, frame, brands, schemas, extensionTypes,
  vocabulary, samples, sources, pageLoader)` и `fun kompotStudio(config): Unit` (открывает
  окно).** `frame: @Composable (brand: String?, dark: Boolean, content) -> Unit` — центр контракта:
  потребитель отдаёт композицию целиком, студия только просит «в бренде X, тёмную». Потому что
  бренд — свойство сборки клиента, и студия не должна знать, из чего он состоит.
- **Дефолтный frame** для проекта без своей темы: список `KompotTheme` из JSON-файлов →
  `RemoteThemeDesignSystem(theme, Material3DesignSystem(), darkModeOverride = dark)` +
  `MaterialTheme(colorScheme = theme.toMaterialColorScheme(base, dark))`. Без файлов —
  `Material3DesignSystem()` и системная тема.
- Модуль публикуется как `kompot-studio-desktop` под BOM, `compose.desktop.currentOs` — только в
  `desktopTest` (как в `kompot-preview`); приложение потребителя добавляет его само — иначе
  хост окажется в POM.
- Альтернатива — `brands: Map<String, KompotTheme>` без `frame`: достаточно для toolkit'а, и
  неверно для первого же потребителя с формами (konekt).
- Не делаем: не читаем словарь и образцы ниоткуда, кроме конфига; студия без конфига работает
  на стандартных рендерерах с четырьмя слоями диагностики (B-12).

- AC: `kompotStudio(KompotStudioConfig(registry = kompotStandardRenderers…))` открывает окно и
  рисует тело; с `frame` от konekt-подобного теста кнопка рисуется в форме и цвете бренда;
  переключение `brand`/`dark` перерисовывает без перезапуска.
- Якоря: `kompot-studio/src/desktopMain/kotlin/io/github/youndie/kompot/studio/{KompotStudio,KompotStudioConfig,DefaultFrame}.kt`
  (новые), `kompot-theme-client/.../RemoteThemeDesignSystem.kt`,
  `kompot-ds-material-compose/.../Material3RemoteTheme.kt`, `kompot-bom/build.gradle.kts`.

## Итог

`KompotStudioConfig` + `kompotStudio(config)` + `kompotStudioFrame(themes)` + `kompotThemesFrom(dir)`;
модуль публикуется как `kompot-studio-desktop` и стоит в BOM. Четыре теста зелёные; окно проверено
запуском: `window showing=true size=1280x803 decorated=true jbr=true vendorVersion=JBR-25.0.4.1`.

- **Как `frame` отдаёт студии дизайн-систему.** `KompotPreview` берёт `designSystem` **параметром** и
  сам кладёт его в `LocalKompotDesignSystem` — значит frame не может передать бренд через локаль,
  её перетрут. Решение: студия провайдит `LocalKompotRegistry` и `LocalKompotDesignSystem`
  **над** фреймом, а **внутри** него читает обратно и отдаёт в `KompotPreview`. Frame, который
  ставит своё, выигрывает; frame, который не ставит ничего, рисует. Следствие, ради которого это и
  сделано: скриншотный `BrandFrame` konekt (он ставит `LocalKompotRegistry` сам) годится без правок.
- **Тест бренда проверяет обе стороны.** «Цвет бренда A появился» проходит и на фрейме, который
  игнорирует аргумент; поэтому рядом «цвет бренда B **не** появился». То же у дефолтного фрейма:
  контроль — `brand = null`, где сервер-тема не должна доехать.
- **Jewel 0.40 требует Java 25** (class file 69). На JBR 21 запуск падал `UnsupportedClassVersionError`
  на `JewelTheme` — после успешной сборки, публикации и зелёных тестов. Toolchain запуска и тестов —
  JBR 25; публикуемый байткод остался 17, `jvm-floor-audit` это подтверждает.
- **Задачу `run` настраивает `afterEvaluate`.** Compose-плагин строит её в своём `afterEvaluate`:
  `tasks.named("run")` на конфигурации падает («Task with name 'run' not found»), а `configureEach`
  находит задачу и проигрывает — сборка остаётся зелёной, а приложение стартует на чужой JVM и без
  skiko. Второй вариант опаснее первого именно тем, что выглядит рабочим.
- **`compose.desktop.currentOs` — в отдельной конфигурации `studioRuntime`,** только на класспасе
  `run`, и в `desktopTest`. В `desktopMain` его нельзя: модуль теперь публикуется, и хост уехал бы
  в POM. Проверено: в `kompot-studio-desktop-*.pom` ни одного упоминания skiko или хоста.
- **Исключение в стороже BOM снято** — оно жило ровно столько, сколько модуль не публиковался.
- **Чего в конфиге пока нет:** `sources`, `vocabulary`, `samples`, `pageLoader`. У каждого свой
  читатель — B-10, B-19, B-18, B-02 — и поле, которое никто не читает, это поле, которое разойдётся
  со своим будущим смыслом. `ScreenSource` заводит B-10, до него окно открывается на теле-параметре.

## Три аудита публикации

`publishToMavenLocal -PVERSION=0.36.1.9001` + `api-metadata-audit` (188 артефактов), `artifact-name-audit`
(466 файлов), `jvm-floor-audit` (35 jar, 70 jvm-вариантов) — все зелёные со студией внутри.
