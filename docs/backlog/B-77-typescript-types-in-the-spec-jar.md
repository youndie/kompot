---
id: B-77
title: "kompot.d.ts и kompot.strict.d.ts едут в jar kompot-spec рядом со схемами (#208)"
status: done
priority: P2
size: XS
---

# B-77 — TypeScript-типы в jar `kompot-spec`

Issue [#208](https://github.com/youndie/kompot/issues/208), найдено при переходе React-рендерера proba
на 0.39 (youndie/proba#34). Оба `.d.ts` (B-57) лежали только в репозитории: `processResources` клал в
jar схемы и `SPEC.md`, а типы — нет. Потребитель, закреплённый на опубликованной координате, получал
их одним из двух плохих способов: копировал файл с GitHub (а непрерывная версия `0.39.0.<run>` не
называет коммит, который он мог бы найти) или запускал генератор сам. proba делает второе — задача
`kompotTypes` в `server/build.gradle.kts` тянет `kompot-spec` со всем runtime-classpath, грузит его в
изолированный class loader и зовёт `TypeScriptDeclarations.render` рефлексией. Работает, побайтово
совпадает, но JVM оказалась в Node-задаче, и web-job proba не может проверить свои же типы.

- **Типы — ресурсы jar под тем же корнем:** `kompot-spec/types/kompot.d.ts` и
  `kompot-spec/types/kompot.strict.d.ts`, рядом с `kompot-spec/schema/` и `kompot-spec/SPEC.md`.
  Версия у них та же, что у схем, из которых они напечатаны; достаются `unzip` без JVM.
- **npm-пакета по-прежнему нет** — решение B-57 в силе: один потребитель, и ему хватает координаты,
  которую он уже скачивает ради корпуса.
- **`KompotSpecResources.typeScriptDeclarations(strict)`** и константы имён файлов в
  `KompotProtocol` — добавлены ради теста: путь, по которому распаковывает чужой стек, держит тест,
  читающий его с classpath так же, как читаются схемы и SPEC. ABI — только добавления.

- AC: в jar `kompot-spec` есть оба файла по путям выше, побайтово равные `kompot-spec/types/`;
  `SpecificationResourceTest` краснеет, если их перестанут упаковывать; путь записан в SPEC «Состав»,
  README модуля и корневом README.
- Якоря: `kompot-spec/build.gradle.kts` (`processResources`),
  `kompot-spec/src/main/kotlin/io/github/youndie/kompot/spec/KompotSpecResources.kt`,
  `SpecificationResourceTest`, `kompot-spec/api/kompot-spec.api`.

## Находки

### Итерация 1 — 2026-10-05

**Сначала красное.** Новый случай `SpecificationResourceTest` до правки `processResources` упал с
`No spec resource "kompot-spec/types/kompot.d.ts"`; после — зелёный. Тест сверяет упакованное с
файлами рядом со скриптом сборки: это те же байты, что держит голден-тест, только пока кто-то помнит их
упаковать. Сам jar (`:kompot-spec:jar`) проверен `unzip -l` — оба файла под `kompot-spec/types/`.

**Что снять в proba:** задачу `kompotTypes` (рефлексия над `TypeScriptDeclarations.render`) в
`server/build.gradle.kts` — заменить распаковкой `kompot-spec/types/kompot.d.ts` из jar той же
координаты — так же, как proba берёт корпус; проверка типов возвращается в web-job.
