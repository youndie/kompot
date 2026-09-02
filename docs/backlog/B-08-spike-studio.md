---
id: B-08
title: "Spike :kompot-studio: Jewel + KompotPreview + Hot Reload в одном окне"
status: done
priority: P0
size: M
stage: spike
epic: research-studio
blocked_by: [B-01]
---

# B-08 — Spike `:kompot-studio`: Jewel + `KompotPreview` + Hot Reload в одном окне

Research (§5.1, §5.5) принял три решения, ни одно из которых не проверено сборкой: что Jewel
0.40 (`IntUiTheme`, `DecoratedWindow`) и material3 1.11.0-alpha07 (на нём рендереры kompot) живут в
одном окне; что `KompotPreview` рисует внутри Jewel-сплита без потери `MaterialTheme`; что
Compose Hot Reload перерисовывает рендерер в этом окне. Каждое из трёх может оказаться ложным
по причине, которую видно только в рантайме — как skiko в `IdePreviewExperiment.kt`, где «да,
IntelliJ рисует» стоило отдельного эксперимента и двух задокументированных ловушек.

- **Решение: модуль `:kompot-studio`, `jvm("desktop")`, `explicitApi`, с `main` на
  `kompotCoreRenderers + kompotStandardRenderers` и `Material3DesignSystem()`.** Окно —
  `DecoratedWindow` + `HorizontalSplitLayout`; слева `TextArea` с телом, справа `KompotPreview`
  внутри `MaterialTheme`; снизу список из `JsonSchemaValidator(strictProfile = профиль toolkit'а)`
  и собранных `onDegraded`. Потому что spike должен быть минимальным срезом **всех** трёх
  сомнений, а не одного.
- `compose.desktop.currentOs` — в `desktopMain` этого модуля: он приложение, а не библиотека;
  публикация модуля — вопрос B-09, здесь не публикуем.
- Проверяемое на spike, по пунктам: (1) окно открывается на JBR и на Temurin (во втором —
  без декораций); (2) кнопка kompot рисуется в цветах `MaterialTheme`, а не Jewel; (3) правка
  `ButtonRenderer` при `hotRunJvm` меняет кадр без перезапуска; (4) `captureComposable` из
  viddik-testing-core снимает тот же кадр, что в окне; (5) `paginated_list` в теле падает (B-02
  ещё не сделана) — и это фиксируется, а не чинится.
- Альтернатива — начать с v1 сразу: если Jewel и material3 не уживаются, переписывать оболочку
  придётся после того, как на неё легли дерево и диагностика.
- Не делаем: дерево, источники, бренды, конфиг потребителя — всё в v1.

- AC: пять пунктов выше отвечены «да/нет» в комментарии к модулю; на «нет» в (2) или (3) —
  research §5.5 пересмотрен до B-09. `./gradlew :kompot-studio:run` открывает окно с телом из
  `kompot-client-tck/corpus/` и рисует его.
- Якоря: `settings.gradle.kts`, `kompot-studio/build.gradle.kts` (новый),
  `kompot-preview/src/desktopMain/.../IdePreviewExperiment.kt` (образец эксперимента),
  `kompot-ds-material-compose/build.gradle.kts` (как подключён viddik).

## Итог

Модуль `:kompot-studio` (`jvm("desktop")`, не публикуется) собирается и запускается; три теста
(`SpikeCaptureTest` ×2, `JetBrainsRuntimeTest`) зелёные.

**Четыре ответа из пяти — «да», пятый открыт.** Полностью, по пунктам, они записаны в шапке
`kompot-studio/src/desktopMain/.../Spike.kt`; сюда — то, из-за чего пришлось менять решения.

- **§5.5 ошибалась: `DecoratedWindow` не деградирует на не-JBR**, а бросает — `if (!JBR.isAvailable())
  error(...)` первой строкой. В студии развилка, и условие в ней — тот же `JBR.isAvailable()`.
  Приблизительная проверка по `java.vendor` не работает: **на JetBrains Runtime `java.vendor` равен
  `Oracle Corporation`**, и первая версия файла уводила студию на недекорированную ветку ровно на том
  рантайме, ради которого ветка и заводилась. Найдено запуском — окно само напечатало, кто оно.
- **Рантайм берётся toolchain'ом** (`JvmVendorSpec.JETBRAINS`, 21), а не «должен быть установлен».
  Лениво: `executable` ставится в `configureEach` под проверкой имени, поэтому `./gradlew build`
  ничего не скачивает. Два тупика по дороге: `tasks.named("run")` падает на конфигурации (Compose
  регистрирует `run` в своём `afterEvaluate`), а `javaLauncher` конфликтует с `executable`, который
  плагин уже выставил.
- **`:kompot-bom` требовал публикацию от каждого модуля.** Сторож правильный — «ноль публикаций»
  раньше значило «прочитан до конфигурации». Теперь у него явный список `notPublished` и проверка
  **в обе стороны**: незаявленный молчун валит сборку, и устаревшее исключение тоже.
- **Hot Reload остался непроверенным.** Задачи `hotRunDesktop`/`reload` регистрируются сами, рантайм
  подходит — не хватает сеанса с экраном. Записано открытым; §5.5 ресёрча исправлена по обоим пунктам.
- Тела-образцы — свои, в ресурсах модуля: `kompot-client-tck/corpus/` (якорь из AC) хранит пары
  «форма → шаги → исход», а не тела экранов, и для `KompotPreview` не годится.
