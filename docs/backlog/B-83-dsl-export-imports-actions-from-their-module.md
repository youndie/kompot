---
id: B-83
title: "Экспорт DSL импортирует каждое действие из его модуля: perform, load, update"
status: done
priority: P2
size: S
stage: partial-updates
blocked_by: [B-82]
---

# B-83 — Экспорт DSL импортирует каждое действие из его модуля

Хвост [B-82](B-82-update-and-load-actions.md), записанный там в «Находках». Экспорт студии
(`kompot-studio/.../export/DslExport.kt`, `DslWriter.action`) печатал для любого действия импорт
`io.github.youndie.kompot.standard.<Имя>Action`, а `perform`, `load` и `update` живут в
`io.github.youndie.kompot.commands` — черновик экрана с таким действием не компилировался. Для
`perform` дефект старше B-82. Тот же импорт был неверен и для `submit_form` (kompot-forms),
`update_session` (kompot-auth) и шагов мастера, а у мастера ещё и имя: `wizard_next` — это
`NextStepAction`, не `WizardNextAction`. Сверх импорта черновик терял содержимое: `payload` у
`perform` печатался `TODO("payload")`, кадры `update` — `TODO("updates")`; это компилируется и
падает при первом вызове.

- **Решение: имя и пакет класса берутся из `Json`, которым студия декодирует тело,** а не
  угадываются по слову на проводе. `SerializersModule.dumpTo` отдаёт для каждой полиморфной
  регистрации её `KClass`, то есть ровно тот класс, в который декодирует клиент, — черновик, который
  его называет, компилируется там же, где этот клиент. Слово, зарегистрированное двумя разными
  классами, не разрешается (черновик не знает, какой имелся в виду); слово, которого `Json` не знает,
  остаётся `TODO("<слово>")`, как и раньше. Тот же поиск теперь даёт импорт и конструкторам
  компонентов (`TextComponent(…)` в запасной ветке раньше печатался без импорта) и значениям полей.
- **Отброшено: таблица «файл схемы → пакет».** Пакет она бы дала, имя — нет (`NextStepAction`), и
  её пришлось бы держать руками рядом с растущим набором модулей.
- **`update` печатается своим builder'ом, `kompotUpdate(deeplink, history) { … }`:** кадры пишутся
  теми же вызовами, что и экран, и каждый узел кадра несёт свой `id` явно — builder отвергает узел
  без `id`, поэтому правило «не печатать `id`, который DSL дал бы сам» внутри `update` не действует.
  Кадр, чей `componentId` не совпадает с `id` узла, или поле, которого у builder'а нет, оставляют
  конструктор `UpdateAction(updates = listOf(UpdateComponentMessage(…)))`: builder такой кадр
  сказать не может, а молча перенаправить кадр на другой узел хуже.
- **`payload` и любое поле-словарь печатаются `mapOf("ключ" to …)`.** Словарь и вложенный класс на
  проводе — оба объект без дискриминатора; различает их только схема (`additionalProperties` —
  схема, а не `true`/`false`), так же как `format: float` уже различал Float.
- **Действие внутри действия** (шаги `sequence`) печатается как действие: раньше оно шло через
  конструктор компонента и выходило `CloseComponent()`.
- Не делаем: экспорт `perform`/`load` builder'ом — у них его нет, они строятся конструктором, как
  все действия (B-82).

- AC: свидетель `WITNESS_BODY` с `perform` (с `payload`), `load` и `update` (два кадра, один —
  контейнер) экспортируется в `WitnessScreenDraft.kt`, файл компилируется, круг JSON → черновик →
  JSON совпадает по всем полям; черновик старого экспортёра не компилируется; сторож покрытия
  требует от свидетеля каждое поле трёх действий и кадра; мутации убиты.
- Якоря: `kompot-studio/.../export/DslExport.kt`, `DslExportRoundTripTest`, `DslExportTest`,
  `WitnessScreenDraft.kt`, `DraftRegenerator`, `kompot-studio/build.gradle.kts`.

## Находки

### Итерация 1 — 2026-10-09

**Сначала красное.** Свидетель с тремя новыми кнопками, напечатанный старым экспортёром, не
компилируется: `Unresolved reference 'LoadAction'`, `'PerformAction'`, `'UpdateAction'` — все три
импортированы из `io.github.youndie.kompot.standard`; там же `payload = TODO("payload")` и
`updates = listOf(TODO("updates"), TODO("updates"))`. После правки свидетель компилируется, круг
зелёный, `SampleScreenDraft.kt` выходит прежним байт в байт.

**Свидетелю нужен свой `Json`.** `kompotJson()` студии не регистрирует значения полей
(`text_value` и др. — в `formStandardSerializersModule`), поэтому тело с `payload` студия по
умолчанию не декодирует вовсе. Свидетель и `DraftRegenerator` берут `witnessConfig` —
`kompotJson(formStandardSerializersModule)`, как сделало бы приложение, шлющее `perform` с
`payload`. Тестовый сорс-сет студии получил `kompot-commands`: основному коду модуль не нужен,
экспорт только печатает имена из него, а компилирует их свидетель.

**Мутации (все убиты, исходники восстановлены после каждой; где сказано «перепечатан» — свидетель
перепечатан мутантом, чтобы красным стал круг, а не сравнение байт):**

1. `imported` пишет импорт `io.github.youndie.kompot.standard.<Имя>` — красные «the exporter still
   produces the witness draft», «an action from another module is imported from that module» и
   «an update whose frame names another node keeps the constructor»;
2. `kompotUpdate` не печатает `history`, перепечатан — круг назвал ровно `update.history`;
3. поля-словари не распознаются, перепечатан — круг падает на `NotImplementedError: payload`;
4. узлы кадра печатаются с путём вместо `id` (тот, что «DSL дал бы сам», выпадает), перепечатан —
   круг падает на отказе builder'а «a node of an update … needs id»;
5. из свидетеля убран `history` — сторож покрытия назвал `update.history`;
6. вложенное действие идёт через конструктор компонента — красный «an action inside a sequence is
   printed as an action»;
7. `update` не сверяет `componentId` с `id` узла — красный «an update whose frame names another node
   keeps the constructor».

Грабля прогона: повтор той же мутации берёт задачу теста из build cache — `DraftRegenerator` не
исполняется, а в `/tmp` остаётся свидетель прошлой мутации, и падение выглядит чужим. Мутации с
перепечаткой гонялись с `--rerun`.

**Где гонялось.** WSL (`wsl-run`, scope `MemoryMax=5G`, `--max-workers=2`, демон выключен):
`:kompot-studio:desktopTest` (114 тестов), `checkKotlinAbi`, `ktlintCheck`; перепечатка свидетеля —
`DraftRegenerator` с `-Pdraft.witness.out=/tmp/…`, забран `wsl-run cat`. Мак (`LOCAL=1`):
`ktlintFormat`.
