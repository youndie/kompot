# Бэклог kompot

> Роль документа: бэклог двух поверхностей одного механизма «показать экран тем же рендерером, что
> у клиента» — студии для разработчика ([research-studio](research/research-studio.md)) и публичного
> плейграунда для постороннего (этап `playground`), — а с этапа `release-0.38` и следующего большого
> релиза тулкита. Задачи лежат по файлу на штуку в
> [`backlog/`](backlog/) — `B-NN-<slug>.md`. Здесь — этапы, индекс (генерируется) и решения,
> которые задачей не являются.
>
> Новая задача: скопировать [`templates/backlog-item.md`](templates/backlog-item.md), взять
> следующий свободный `B-NN`, после правки прогнать `python3 docs/scripts/backlog_index.py`
> (`--check` — то же, что в CI; `--against origin/main` — номер не занят на ветке).

## Цель

Бэкенд-разработчик пишет экран и **видит его тем же рендерером, что у клиента, не запуская
клиент**: дерево слева, живой рендер справа, бренд А/Б и тема переключаются, линт говорит, что
клиент не нарисует, до деплоя. Инструмент — библиотека `:kompot-studio`, которую потребитель
запускает у себя (как `viddikShowroom`); оболочка — Jewel.

**Плейграунд** — тот же механизм, обращённый к постороннему: страница, на которой видно, **что
делает старый клиент с типом, которого он не знает**. Дерево слева, живой рендер справа,
переключатель «старый клиент» между ними: незнакомый тип, заглушка, серверный `fallback` (§2.1).
Собирается поверх уже объявленного `wasmJs`-таргета клиента; сервера у витрины нет.

## Решения, принятые до задач

- **Библиотека у потребителя, не автономное приложение** — иначе не получить настоящие рендереры
  без второй копии Compose в classpath (research §5.1).
- **Источник правды — текст тела (wire JSON)**, не объект и не DSL; DSL остаётся в IDE (§5.3).
- **Бренд — это `frame` от потребителя**, а не `KompotTheme`: формы и шрифты клиентские (§5.2).
- **Дочерние слоты выводятся из схемы**, ручного списка контейнеров в студии нет (§1.3).
- **Jewel** для оболочки, на той же линии CMP, что и kompot (с [B-40](backlog/B-40-compose-line-1-12.md) — 1.12); appframe студии не нужен (§5.5).
- **Плейграунд — не студия в браузере.** Jewel живёт только на JVM, а `kompot-spec` намеренно
  JVM-only (артефакт сборки и ревью), поэтому оболочка и дерево у витрины свои. Общее у них —
  `KompotPreview` и реестр рендереров, то есть ровно то, что делает картинку настоящей.

## Этапы

| stage | что это | критерий |
|---|---|---|
| `upstream` | швы в toolkit'е, без которых студия дублирует приватный код | публикуются как обычные модули kompot |
| `spike` | доказательство сборки: Jewel + material3 + `KompotPreview` + Hot Reload в одном окне | решения §5.1/§5.5 подтверждены или пересмотрены |
| `v1-viewer` | смотреть и линтить: источники, дерево, рендер, бренд/тема, диагностика | пилот на konekt даёт кадры голденов |
| `v2-editor` | править и снимать: текст, дерево, голдены, истории, словарь проекта | экран правится в студии и уезжает в фикстуру |
| `v3-builder` | собирать: инспектор, палитра, DnD, экспорт DSL | пригодно дизайнеру/PM |
| `playground` | публичная витрина: страница, показывающая деградацию старого клиента | посторонний видит три состояния, не написав ни строки |
| `release-0.38` | следующий большой релиз: линия Compose 1.12, словарь раскладки и отклика, долги | 0.38.0 на Central, записи в `UPGRADING.md` и §13 — см. ниже |

## Релиз 0.38

Первые три этапа строили инструменты **вокруг** провода; этот двигает сам провод и то, на чём он
собран. Версия в `gradle.properties` поднята до 0.38.0 вместе с заведением этапа, чтобы влитое по
пути не публиковалось под номером уже выпущенной 0.37.0.

Что входит, группами:

- **Линия и долги** — Compose 1.12 одним коммитом ([B-40](backlog/B-40-compose-line-1-12.md)),
  очередь renovate ([B-41](backlog/B-41-renovate-queue.md)), молчаливый отказ realtime
  ([B-42](backlog/B-42-realtime-failure-is-swallowed.md)), колесо над многострочным полем
  ([B-43](backlog/B-43-multiline-field-eats-the-wheel.md), issue #51).
- **Раскладка** — выравнивание, наложение, горизонтальная прокрутка, разделитель и отступ
  (B-44…B-47). Без них сервер не собирает обычную карточку, не заводя продуктовый тип.
- **Локальное состояние** — сначала исследование ([B-48](backlog/B-48-local-state-research.md)),
  потом реализация выбранной ступени. Решение необратимо: всё, что попадёт в провод, придётся
  поддерживать каждой реализации клиента.
- **Отклик на действие** — сообщение, диалог и подтверждение, `sequence`, `refresh` (B-50…B-53).
- **Наблюдаемость, доступность, чужие реализации** — показ по видимости, роли и заголовки, экран
  загрузки и ошибки, TypeScript-типы из схем (B-54…B-57).

Правило, общее для всех новых слов: **поле, где можно, тип с фолбэком, где нельзя, модификатор —
никогда** (§2.3 — закрытая иерархия, незнакомый узел роняет разбор всего ответа). Каждое слово
приходит с записанным поведением клиента 0.37 и фикстурой корпуса случаев, которая это держит.

<!-- BEGIN INDEX (генерируется docs/scripts/backlog_index.py — руками не править) -->

## Открыто (8)

| Задача | | Этап | Приоритет | Размер | Ждёт |
|---|---|---|---|---|---|
| [B-58](backlog/B-58-release-0-38-0.md) `[ ]` | Выпуск 0.38.0: версия, журналы, Central | release-0.38 | P0 | S | B-40, B-41, B-42, B-43, B-44, B-45, B-46, B-47, B-48, B-49, B-50, B-51, B-52, B-53, B-54, B-55, B-56, B-57 |
| [B-43](backlog/B-43-multiline-field-eats-the-wheel.md) `[?]` | Многострочное поле съедает колесо, когда прокручивать нечего (#51) | release-0.38 | P1 | S | — |
| [B-48](backlog/B-48-local-state-research.md) `[?]` | Сколько локального состояния пускать в провод? | release-0.38 | P1 | M | — |
| [B-49](backlog/B-49-local-state.md) `[ ]` | Локальное состояние экрана: реализация выбранной ступени | release-0.38 | P1 | L | B-48 |
| [B-56](backlog/B-56-loading-and-error-screens.md) `[?]` | Экран загрузки и экран ошибки — чьи они? | release-0.38 | P2 | M | — |
| [B-59](backlog/B-59-playground-old-client-for-any-type.md) `[ ]` | Переключатель «старый клиент» в плейграунде — для любого типа, а не только для демо-плагина | playground | P3 | S/M | — |
| [B-61](backlog/B-61-preview-content-missing-from-browser-a11y.md) `[ ]` | Содержимое предпросмотра в плейграунде не попадает в дерево доступности браузера | playground | P3 | S/M | — |
| [B-62](backlog/B-62-server-marks-the-measured-node.md) `[?]` | Как сервер помечает узел, который меряет эксперимент? | release-0.38 | P3 | M | — |

## Сделано (54)

**Швы в toolkit'е**

- [B-01](backlog/B-01-compose-line.md) — Одна линия Compose: kompot, viddik, Jewel в libs.versions.toml
- [B-02](backlog/B-02-decode-body-public.md) — kompot-preview: публичный decodeKompotBody и параметр pageLoader
- [B-03](backlog/B-03-remember-design-system-dark.md) — rememberKompotDesignSystem пробрасывает darkMode
- [B-04](backlog/B-04-schema-findings.md) — JsonSchemaValidator: структурированная ошибка вместо String
- [B-05](backlog/B-05-child-slots-from-schema.md) — childSlots(schemas): дочерние слоты компонента из $ref схемы
- [B-06](backlog/B-06-kdoc-to-schema.md) — KSP переносит KDoc компонента и свойств в description схемы
- [B-07](backlog/B-07-deterministic-dsl-ids.md) — DSL: детерминированный id вместо Uuid.random() при пропуске

**Spike**

- [B-08](backlog/B-08-spike-studio.md) — Spike :kompot-studio: Jewel + KompotPreview + Hot Reload в одном окне

**v1 — смотреть и линтить**

- [B-09](backlog/B-09-studio-config-frame.md) — KompotStudioConfig и frame; дефолтный frame из файлов KompotTheme
- [B-10](backlog/B-10-sources.md) — Источники тела: файл, каталог с watch, HTTP с ETag и NavigationGraph
- [B-11](backlog/B-11-tree-from-schema.md) — Дерево экрана из JSON по слотам схемы (Jewel LazyTree)
- [B-12](backlog/B-12-diagnostics.md) — Диагностика: синтаксис, схема, правила тела, деградации
- [B-13](backlog/B-13-switches-and-action-log.md) — Бренд, тема, размер устройства, состояния формы, лог действий
- [B-14](backlog/B-14-konekt-pilot.md) — Пилот на konekt: записи и brand-a/b дают кадры голденов

**v2 — править и снимать**

- [B-15](backlog/B-15-text-editor.md) — Редактор текста с подсветкой JSON и синхронизацией каретка ↔ узел
- [B-16](backlog/B-16-tree-edits.md) — Правки в дереве: переставить, дублировать, удалить, сохранить
- [B-17](backlog/B-17-goldens.md) — Снять кадр и сравнить с голденом через viddik
- [B-18](backlog/B-18-stories.md) — Истории: образцы словаря, состояния формы, GeneratedViddikRegistry
- [B-19](backlog/B-19-project-vocabulary-lint.md) — Слой словаря проекта: открытые слова и токены кита
- [B-20](backlog/B-20-gradle-task.md) — Gradle-задача kompotStudio
- [B-24](backlog/B-24-stubbed-pagination-is-not-a-golden.md) — Кадр, снятый с заглушкой пагинации, не голден — и должен об этом говорить

**v3 — собирать**

- [B-21](backlog/B-21-inspector.md) — Инспектор свойств по схеме
- [B-22](backlog/B-22-palette-dnd.md) — Палитра типов и drag-and-drop в дереве
- [B-23](backlog/B-23-dsl-export.md) — Экспорт DSL-черновика из тела

**Плейграунд**

- [B-25](backlog/B-25-playground-page.md) — Модуль плейграунда и страница на GH Pages
- [B-26](backlog/B-26-playground-body-and-render.md) — Живой рендер: текст тела слева, экран справа
- [B-27](backlog/B-27-playground-tree.md) — Дерево тела слева, выбор узла подсвечивает его в рендере
- [B-28](backlog/B-28-playground-demo-plugin.md) — Демо-компонент, объявленный как плагин деплоя
- [B-29](backlog/B-29-old-client-switch.md) — Переключатель «старый клиент»: три состояния деградации
- [B-30](backlog/B-30-playground-examples-and-links.md) — Набор готовых тел и ссылки на витрину
- [B-31](backlog/B-31-question-placeholder-for-unknown.md) — Должен ли незнакомый тип без fallback рисовать заглушку?
- [B-32](backlog/B-32-playground-states-under-test.md) — Три состояния витрины проверяются прогоном, а не глазами

**Релиз 0.38**

- [B-40](backlog/B-40-compose-line-1-12.md) — Линия Compose 1.12: четыре числа одним коммитом
- [B-41](backlog/B-41-renovate-queue.md) — Очередь renovate разобрана до релиза: Kotlin 2.4.20, AGP 9.4.1, coil 3.6.3
- [B-42](backlog/B-42-realtime-failure-is-swallowed.md) — Отказ подписки на обновления проглатывается молча
- [B-44](backlog/B-44-row-and-column-alignment.md) — Выравнивание и распределение у row и column
- [B-45](backlog/B-45-box-overlay.md) — box: наложение узлов друг на друга
- [B-46](backlog/B-46-horizontal-rail.md) — Горизонтальная прокрутка: рельс карточек
- [B-47](backlog/B-47-divider-and-spacer.md) — divider и spacer как узлы провода
- [B-50](backlog/B-50-show-message-action.md) — show_message: короткое сообщение в ответ на действие
- [B-51](backlog/B-51-dialog-sheet-and-confirm.md) — Диалог, шторка и подтверждение перед действием
- [B-52](backlog/B-52-sequence-action.md) — sequence: несколько действий одним ответом
- [B-53](backlog/B-53-refresh-action.md) — refresh: перезагрузить текущий экран, не называя его
- [B-54](backlog/B-54-impression-is-composition-not-visibility.md) — Показ считается по композиции, а не по видимости
- [B-55](backlog/B-55-accessibility-of-actionable-containers.md) — Доступность: роль и подпись у нажимаемого контейнера, заголовок у text
- [B-57](backlog/B-57-typescript-types-from-schema.md) — TypeScript-типы из schema/*.json для клиента не на Kotlin
- [B-60](backlog/B-60-unknown-actions-in-answers-go-unreported.md) — Незнакомое действие в ответе сервера не сообщается никуда

**Без этапа**

- [B-33](backlog/B-33-dokka-reads-ksp-output-too-early.md) — Dokka читает сгенерированное KSP раньше, чем оно появляется
- [B-34](backlog/B-34-degradation-vocabulary-is-ambiguous.md) — Сток деградации называет тип по-разному и путает «нарисован фолбэк» с «нарисована заглушка»
- [B-35](backlog/B-35-ksp-wiring-belongs-to-the-convention.md) — Обвязка KSP живёт в семи копиях вместо конвенции сборки
- [B-36](backlog/B-36-kompot-client-readme.md) — У kompot-client нет README, и рассказать про реестр и сток негде
- [B-37](backlog/B-37-readme-snippets-are-unchecked.md) — Kotlin-сниппеты в README ничем не компилируются и разойдутся молча
- [B-38](backlog/B-38-wire-name-is-not-obtainable.md) — Проводное имя компонента получить нечем, а студия его требует
- [B-39](backlog/B-39-nothing-guards-the-published-kotlin-api.md) — Молчаливую поломку Kotlin-API не ловит ничто

<!-- END INDEX -->

## Что специально не в бэклоге

Автономный дистрибутив с загрузкой чужих jar; встроенный Kotlin-скриптинг; правила «column
может содержать только X»; замена `tools/canvas` (макет ↔ провод остаётся входом, студия — выходом).
Причины — research §7.
