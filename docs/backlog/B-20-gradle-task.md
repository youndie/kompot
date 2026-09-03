---
id: B-20
title: "Gradle-задача kompotStudio"
status: done
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

## Итог

Модуль `kompot-studio-gradle-plugin` (id `io.github.youndie.kompot.studio`), задача `kompotStudio`,
и в студии — `KompotStudioConfigProvider` + `KompotStudioLauncher` через `ServiceLoader`. Два теста
в `LauncherTest`; аудиты зелёные (189 модулей, маркер плагина публикуется). **Проверено на konekt**:
`./gradlew :client:kompotStudio` поднимает JBR 25, лаунчер находит провайдера, собирает конфиг и
доходит до окна — на WSL падает ровно на `HeadlessException`, то есть на отсутствии дисплея и ни на
чём другом. Без провайдера — сообщение, называющее интерфейс и файл регистрации (обе половины AC).

- **Две конфигурации, а не одна.** Gradle 9 требует объявлять зависимости на `dependencyScope` и
  резолвить через `resolvable`, который её расширяет; объявление на резолвимой падает прямым текстом
  («Dependencies can not be declared against …»). Это ролевая модель, говорящая, что работы разные.
- **`compilation = "test"` — не деталь.** У первого настоящего потребителя фрейм бренда, записи и
  голдены лежат в тестовых исходниках, и плагин, предлагающий только `main`, был бы там непригоден.
  Настройка появилась потому, что пилот это показал, а не на всякий случай.
- **viddik в classpath задачи НЕ добавляется**, хотя план просил. Он живёт в репозитории, который
  сборка потребителя может не объявлять, и тогда отсутствующий репозиторий превратился бы в задачу,
  которая даже не конфигурируется. Съёмка кадра достижима рефлексией, и её отсутствие — поддержанное
  состояние; сборке, которой нужны голдены, это одна строка.
- **`ServiceLoader`, а не имя класса строкой:** провайдер, переставший существовать, — это ошибка
  сборки в момент переименования, а `Class.forName` — сообщение в рантайме про класс, который никто
  не помнит. И регистрация лежит там, где читатель её найдёт.
- **Классpath берётся лямбдой**, а не сразу: Kotlin-плагин к моменту `apply` ещё не объявил таргеты,
  и ранний запрос даёт пустой classpath и задачу, которая стартует и ничего не находит.
- **`gradleKotlinDsl()` рядом с `gradleApi()`:** `create`/`register`/`getByType` живут в Kotlin DSL, и
  без него каждый из них читается как «unresolved reference» на типе, который очевидно на месте.
