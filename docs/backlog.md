# Бэклог: kompot-studio

> Роль документа: бэклог одной фичи — редактора/превью экрана server-driven UI
> ([research-studio](research/research-studio.md)). Задачи лежат по файлу на штуку в
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

## Решения, принятые до задач

- **Библиотека у потребителя, не автономное приложение** — иначе не получить настоящие рендереры
  без второй копии Compose в classpath (research §5.1).
- **Источник правды — текст тела (wire JSON)**, не объект и не DSL; DSL остаётся в IDE (§5.3).
- **Бренд — это `frame` от потребителя**, а не `KompotTheme`: формы и шрифты клиентские (§5.2).
- **Дочерние слоты выводятся из схемы**, ручного списка контейнеров в студии нет (§1.3).
- **Jewel** для оболочки, на линии CMP 1.11 вместе с kompot; appframe студии не нужен (§5.5).

## Этапы

| stage | что это | критерий |
|---|---|---|
| `upstream` | швы в toolkit'е, без которых студия дублирует приватный код | публикуются как обычные модули kompot |
| `spike` | доказательство сборки: Jewel + material3 + `KompotPreview` + Hot Reload в одном окне | решения §5.1/§5.5 подтверждены или пересмотрены |
| `v1-viewer` | смотреть и линтить: источники, дерево, рендер, бренд/тема, диагностика | пилот на konekt даёт кадры голденов |
| `v2-editor` | править и снимать: текст, дерево, голдены, истории, словарь проекта | экран правится в студии и уезжает в фикстуру |
| `v3-builder` | собирать: инспектор, палитра, DnD, экспорт DSL | пригодно дизайнеру/PM |

<!-- BEGIN INDEX (генерируется docs/scripts/backlog_index.py — руками не править) -->

## Открыто (10)

| Задача | | Этап | Приоритет | Размер | Ждёт |
|---|---|---|---|---|---|
| [B-14](backlog/B-14-konekt-pilot.md) `[?]` | Пилот на konekt: записи и brand-a/b дают кадры голденов | v1-viewer | P0 | S | B-09, B-10, B-12 |
| [B-03](backlog/B-03-remember-design-system-dark.md) `[ ]` | rememberKompotDesignSystem пробрасывает darkMode | upstream | P1 | XS | — |
| [B-17](backlog/B-17-goldens.md) `[ ]` | Снять кадр и сравнить с голденом через viddik | v2-editor | P1 | M | B-09 |
| [B-06](backlog/B-06-kdoc-to-schema.md) `[ ]` | KSP переносит KDoc компонента и свойств в description схемы | upstream | P2 | M | — |
| [B-07](backlog/B-07-deterministic-dsl-ids.md) `[ ]` | DSL: детерминированный id вместо Uuid.random() при пропуске | upstream | P2 | S | — |
| [B-19](backlog/B-19-project-vocabulary-lint.md) `[ ]` | Слой словаря проекта: открытые слова и токены кита | v2-editor | P2 | S/M | B-12 |
| [B-20](backlog/B-20-gradle-task.md) `[ ]` | Gradle-задача kompotStudio | v2-editor | P2 | S | B-09 |
| [B-21](backlog/B-21-inspector.md) `[ ]` | Инспектор свойств по схеме | v3-builder | P2 | L | B-06, B-16 |
| [B-22](backlog/B-22-palette-dnd.md) `[ ]` | Палитра типов и drag-and-drop в дереве | v3-builder | P3 | L | B-21 |
| [B-23](backlog/B-23-dsl-export.md) `[ ]` | Экспорт DSL-черновика из тела | v3-builder | P3 | M | B-07 |

## Сделано (13)

**Швы в toolkit'е**

- [B-01](backlog/B-01-compose-line.md) — Одна линия Compose: kompot, viddik, Jewel в libs.versions.toml
- [B-02](backlog/B-02-decode-body-public.md) — kompot-preview: публичный decodeKompotBody и параметр pageLoader
- [B-04](backlog/B-04-schema-findings.md) — JsonSchemaValidator: структурированная ошибка вместо String
- [B-05](backlog/B-05-child-slots-from-schema.md) — childSlots(schemas): дочерние слоты компонента из $ref схемы

**Spike**

- [B-08](backlog/B-08-spike-studio.md) — Spike :kompot-studio: Jewel + KompotPreview + Hot Reload в одном окне

**v1 — смотреть и линтить**

- [B-09](backlog/B-09-studio-config-frame.md) — KompotStudioConfig и frame; дефолтный frame из файлов KompotTheme
- [B-10](backlog/B-10-sources.md) — Источники тела: файл, каталог с watch, HTTP с ETag и NavigationGraph
- [B-11](backlog/B-11-tree-from-schema.md) — Дерево экрана из JSON по слотам схемы (Jewel LazyTree)
- [B-12](backlog/B-12-diagnostics.md) — Диагностика: синтаксис, схема, правила тела, деградации
- [B-13](backlog/B-13-switches-and-action-log.md) — Бренд, тема, размер устройства, состояния формы, лог действий

**v2 — править и снимать**

- [B-15](backlog/B-15-text-editor.md) — Редактор текста с подсветкой JSON и синхронизацией каретка ↔ узел
- [B-16](backlog/B-16-tree-edits.md) — Правки в дереве: переставить, дублировать, удалить, сохранить
- [B-18](backlog/B-18-stories.md) — Истории: образцы словаря, состояния формы, GeneratedViddikRegistry

<!-- END INDEX -->

## Что специально не в бэклоге

Автономный дистрибутив с загрузкой чужих jar; встроенный Kotlin-скриптинг; правила «column
может содержать только X»; замена `tools/canvas` (макет ↔ провод остаётся входом, студия — выходом).
Причины — research §7.
