---
id: B-25
title: "Модуль плейграунда и страница на GH Pages"
status: wip
priority: P0
size: S/M
stage: playground
epic: playground
---

# B-25 — Модуль плейграунда и страница на GH Pages

У тулкита нет ни одной страницы, на которую можно послать человека: README рассказывает про
деградацию словами, а увидеть её негде. При этом `wasmJs { browser() }` уже объявлен у
`kompot-client`, `kompot-preview`, `kompot-forms-client`, `kompot-theme-client` и
`kompot-ds-material-compose` — витрина стоит дистрибутива и деплоя, а не портирования.

- **Решение: отдельный модуль-приложение `kompot-playground`, к которому НЕ применяется
  `io.github.youndie.sborka.publish`.** Это не библиотека: в `kompot-bom` он попасть не должен, и
  три аудита публикации (`api-metadata-audit`, `artifact-name-audit`, `jvm-floor-audit`) не должны
  начать спрашивать с него метаданные библиотеки.
- **GH Pages публичного репозитория, а не kotlin.website.** Публичному репозиторию раннеры GitHub
  бесплатны, страница лежит рядом с кодом, который её собирает, и приватный лендинг в открытый
  репозиторий не протаскивается. Ссылку с kotlin.website можно поставить когда угодно — обратное
  (сначала лендинг, потом код) связало бы выкладку витрины с чужим релизным циклом.
- **Деплой — свой workflow на пуш в `main`**, а не шаг в `build.yaml`: сборка и публикация страницы
  имеют разные права (`pages: write`, `id-token: write`), и мешать их с прогоном тестов значит
  выдать эти права каждому PR.
- Альтернатива — положить готовый дистрибутив в ветку `gh-pages` руками: ветка живёт своей жизнью,
  и первый же расхожий коммит делает страницу старше кода, о чём никто не узнает.
- Не делаем: сервера. Плейграунд рисует тело, которое лежит рядом с ним; живого бэкенда у витрины
  нет и по замыслу не будет (иначе витрина становится сервисом, который надо держать живым).

- AC: пуш в `main` → на `https://youndie.github.io/kompot/` открывается экран, собранный настоящими
  рендерерами kompot (не картинка); в консоли браузера пусто; `./gradlew build` на маке и в CI
  по-прежнему зелёный, а `publishToMavenLocal` не публикует новый модуль.
- Якоря: `settings.gradle.kts`, `kompot-playground/build.gradle.kts`,
  `kompot-playground/src/wasmJsMain/kotlin/io/github/youndie/kompot/playground/Main.kt`,
  `kompot-playground/src/wasmJsMain/resources/index.html`, `.github/workflows/pages.yaml`.
