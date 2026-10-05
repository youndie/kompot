---
id: B-76
title: "kompot.d.ts: ветка незнакомого компонента типизирует fallback и поля базы (#207)"
status: done
priority: P2
size: S
---

# B-76 — Ветка незнакомого компонента в `kompot.d.ts` типизирует `fallback`

Issue [#207](https://github.com/youndie/kompot/issues/207), найдено при переходе React-рендерера proba
на 0.39 (youndie/proba#34). В открытом файле `kompot-spec/types/kompot.d.ts` ветка незнакомого
компонента была `{ type: string; [property: string]: unknown }`. §2.1 читает `fallback` **только** на
пути незнакомого типа — то есть ровно на этой ветке, — и там он был `unknown`. Типизирован ключ был на
каждом известном варианте, где его читать незачем (это половина пишущей стороны, B-57). Каждому
читателю приходилось приводить тип руками; в proba это структурная проверка на `unknown`, которая
компилируется и с опечаткой в имени ключа.

- **Ветка незнакомого типа несёт базу иерархии.** Генератор печатает в `Unknown<Иерархия>` свойства
  её базы из схемы модуля (`kompot-core.schema.json#/$defs/KompotComponent`): у компонента это `id`,
  `modifiers?` и `fallback?: KompotComponent`, обязательность — по `required` базы. Строки не
  захардкожены: база уже была у генератора — из неё варианты наследовали `fallback` для пишущей
  стороны. Индексная сигнатура остаётся — незнакомый тип вправе нести что угодно ещё.
- **Дискриминатор — `string` без описания базы:** описание отсылает к закрытому списку профиля, а
  этот узел как раз вне его.
- **`UnknownKompotAction` не меняется**, и это следствие, а не исключение: база экшена — один `type`,
  эквивалента у экшена нет (§2.1: незнакомое намерение игнорируется).
- **Иерархии формы получили то же по тому же правилу:** `UnknownFormFieldDefinition` — `fieldId`,
  `rules?`, `visibleIf?`, `triggersPatch?`; `UnknownValidationRule` — `errorMessage`. Это свойства их
  баз, схема гарантирует их любому узлу иерархии. Что незнакомый тип формы роняет разбор (§2.2), типов
  не касается — провод его всё равно может принести.
- **Строгий файл не изменился** — и не должен: ветки незнакомого типа в нём нет, а `fallback` на каждом
  варианте компонента у пишущей стороны уже был.

- AC: `UnknownKompotComponent` в открытом файле — `type`, `id`, `modifiers?`,
  `fallback?: KompotComponent` и индексная сигнатура; `UnknownKompotAction` — только `type` и сигнатура;
  читатель берёт `node.fallback` как `KompotComponent | undefined` без приведения, опечатка не
  компилируется.
- Якоря: `kompot-spec/src/main/kotlin/io/github/youndie/kompot/spec/TypeScriptDeclarations.kt`,
  `kompot-spec/types/kompot.d.ts`, `TypeScriptGoldenTest`, `tools/ts-check.py`.

## Находки

### Итерация 1 — 2026-10-05

**Сначала красное.** Новый случай `TypeScriptGoldenTest` — «ветка незнакомого компонента называет
`fallback` и поля базы» — на старом генераторе упал: ожидались пять членов, напечатано два (`type` и
сигнатура). Случай для экшена («без `fallback`») был зелёным и до правки — он держит, что правка
ничего не добавит туда, где добавлять нечего.

**Проверка на `tsc`, а не только на тексте.** `tools/ts-check.py` теперь компилирует против открытого
файла читателя `equivalentOf(node: UnknownKompotComponent): KompotComponent | undefined`, который
возвращает `node.fallback`. Против старого файла — `TS2322: Type 'unknown' is not assignable to type
'KompotComponent | undefined'`; против нового — проходит. Контроль в каждом прогоне: тот же читатель с
`node.fallbak` обязан быть отвергнут (опечатка попадает в индексную сигнатуру и остаётся `unknown`).

**Что снять в proba:** структурную проверку в `render.tsx` (`equivalentOf`) — заменить чтением
`node.fallback` с ветки `UnknownKompotComponent`, когда proba возьмёт версию с этой правкой.
