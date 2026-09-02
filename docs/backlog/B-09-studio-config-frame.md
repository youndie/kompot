---
id: B-09
title: "KompotStudioConfig и frame; дефолтный frame из файлов KompotTheme"
status: open
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
