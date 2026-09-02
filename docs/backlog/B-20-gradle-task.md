---
id: B-20
title: "Gradle-задача kompotStudio"
status: open
priority: P2
size: S
stage: v2-editor
epic: research-studio
blocked_by: [B-09]
---

# B-20 — Gradle-задача `kompotStudio`

После B-09 у потребителя есть 15 строк `main` и run-конфигурация в IDE. Этого хватает одному
разработчику и не хватает команде: «как открыть студию» должно быть одной командой, как
`./gradlew :client:viddikShowroom` у viddik (`JavaExec`, `mainClass = …Launcher`).

- **Решение: `kompot-studio-gradle-plugin` с id `io.github.youndie.kompot.studio`, задача
  `kompotStudio` типа `JavaExec` на `desktopMain`-classpath потребителя, `mainClass` —
  `KompotStudioLauncher`, который ищет в classpath `KompotStudioConfig` через `ServiceLoader`
  (`META-INF/services/io.github.youndie.kompot.studio.KompotStudioConfigProvider`).** Потребитель
  пишет провайдер вместо `main`. Потому что `viddikShowroom` уже показал, что рефлексивный запуск
  из чужого classpath — правильная форма для «библиотеки, запускаемой у потребителя», а
  `ServiceLoader` честнее `Class.forName` по строке.
- Задача добавляет `compose.desktop.currentOs` и viddik-testing-core в **свой** classpath
  (`runtimeOnly`), а не в зависимости модуля потребителя — так модуль-клиент остаётся
  публикуемым без хоста в POM.
- `hotRunJvm` — не наша задача: Compose Hot Reload подхватывает любой `JavaExec` через свой
  плагин; в README — одна строка, как включить.
- Альтернатива — задача в `build.gradle.kts` потребителя копипастой: та же строка в каждом
  проекте и та же сигнатурная ломкость, что у appframe в `shopPreview`.
- Не делаем: `:kompot-studio` в сборке kompot остаётся с обычным `main` для разработки
  студии; плагин публикуется на тот же Reposilite, что `viddik-gradle-plugin`.

- AC: в konekt `./gradlew :client:kompotStudio` открывает студию с конфигом из провайдера; без
  провайдера задача падает с сообщением, какой интерфейс реализовать.
- Якоря: `kompot-studio-gradle-plugin/` (новый), `viddik/viddik-gradle-plugin/src/main/kotlin/.../{ViddikPlugin,ViddikLayout}.kt`
  (образец), `settings.gradle.kts`, `kompot-bom/build.gradle.kts`.
