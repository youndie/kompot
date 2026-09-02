---
id: B-10
title: "Источники тела: файл, каталог с watch, HTTP с ETag и NavigationGraph"
status: open
priority: P0
size: M
stage: v1-viewer
epic: research-studio
blocked_by: [B-08]
---

# B-10 — Источники: файл, каталог с watch, HTTP с ETag + `NavigationGraph`

Цикл бэкенд-разработчика: правит DSL в IDE → сервер (или тест, пишущий фикстуру) отдаёт тело →
хочется увидеть его без перезапуска клиента. Тело у потребителя лежит в трёх местах: файл записи
(`/recorded/home-screen.json` в konekt), каталог таких записей, работающий сервер с
`NavigationGraph` (`routes: List<ScreenRoute(deeplink, endpoint, title, kind)>`) и ETag'ом
(`respondWithETag`: sha256 тела, 304 на `If-None-Match`).

- **Решение: `sealed interface ScreenSource`: `File(path)`, `Directory(path)`, `Http(baseUrl,
  headers, graphPath?)`.** Каталог — `WatchService` с `SensitivityWatchEventModifier.HIGH` (на
  macOS это polling; 2 с достаточно). HTTP — опрос по `If-None-Match`, перерисовка только на 200;
  список экранов слева — из `NavigationGraph`, загрузка по `endpoint`, тип тела по `kind`
  (`screen` / `form` / `live_screen`). Потому что все три уже существуют у потребителя как
  артефакты, и студия должна открывать их, а не просить новый формат.
- Bearer-токен для HTTP — из заголовков конфига источника; `kompot-auth`'овский
  `UpdateSessionAction` из лога действий можно «принять» кнопкой — этого в v1 нет, только заголовок.
- Альтернатива — только файл: цикл «сохранить ответ руками → открыть» убивает главное — скорость.
- Не делаем: live-канал (`KompotRealtimeSource`) — v2 при необходимости; запись тела в
  фикстуру — B-16.

- AC: открытие файла рисует его; правка файла на диске перерисовывает ≤ 3 с; при `Http` с
  `graphPath` слева список маршрутов, клик грузит экран; повторный опрос с неизменённым ETag не
  перерисовывает (видно по счётчику рендеров в статусной строке).
- Якоря: `kompot-studio/.../source/{ScreenSource,FileSource,DirectorySource,HttpSource}.kt`
  (новые), `kompot-navigation/.../NavigationGraph.kt`, `kompot-ktor/src/jvmMain/.../ETagResponses.kt`,
  `kompot-client-cache/.../KompotScreenFetcher.kt` (контракт, под который пишется HTTP).
