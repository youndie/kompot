---
id: B-03
title: "rememberKompotDesignSystem пробрасывает darkMode"
status: open
priority: P1
size: XS
stage: upstream
epic: research-studio
---

# B-03 — `rememberKompotDesignSystem` пробрасывает `darkMode`

`RemoteThemeDesignSystem(theme, fallback, darkModeOverride: Boolean? = null)` умеет рисовать тему
в заданном режиме — параметр заведён «для тестов и превью, где нет системного сигнала». Но
`@Composable fun rememberKompotDesignSystem(theme: KompotTheme?, fallback: KompotDesignSystem)`
(`kompot-theme-client/.../RemoteThemeDesignSystem.kt`) его не принимает и всегда читает
`isSystemInDarkTheme()`. konekt на этом уже обжёгся: `toMaterialColorScheme(base, darkMode)` брал
режим от вызывающего, а design system — от машины, и на тёмном Mac светлый кадр получил тёмную
карточку под светлой кнопкой (`design-brand-kit.md`, «Light and dark must be asked for together»);
теперь konekt строит `RemoteThemeDesignSystem` сам и просит апстрим-фикс. Студии переключатель
темы нужен независимо от хоста.

- **Решение: `rememberKompotDesignSystem(theme, fallback, darkMode: Boolean? = null)`**, где `null`
  — прежнее поведение. Потому что две половины бренда (`KompotDesignSystem` и `ColorScheme`)
  должны получать ответ на один и тот же вопрос из одного места, и удобная обёртка не должна быть
  единственным местом, где это невозможно.
- Альтернатива — оставить как есть и советовать конструктор напрямую: это и есть текущее
  состояние, в котором ошибку повторит следующий потребитель.
- Не делаем: не трогаем `rememberMaterialColorScheme` — у него `darkMode` уже есть.

- AC: тест в `kompot-theme-client`: при `darkMode = false` на хосте в тёмной теме токен
  резолвится из `light`; при `null` — как раньше. konekt может вернуться к обёртке.
- Якоря: `kompot-theme-client/src/commonMain/kotlin/io/github/youndie/kompot/theme/client/RemoteThemeDesignSystem.kt`,
  `kompot-ds-material-compose/.../Material3RemoteTheme.kt`.
