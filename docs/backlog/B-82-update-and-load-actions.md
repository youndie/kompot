---
id: B-82
title: "update и load: ответ, подменяющий узлы, и GET, отвечающий действием"
status: done
priority: P1
size: L
stage: partial-updates
epic: research-partial-updates
blocked_by: [B-81]
---

# B-82 — update и load: ответ, подменяющий узлы, и GET, отвечающий действием

Проводная половина [research-partial-updates](../research/research-partial-updates.md) §4.2–4.5.

- **`update { updates: [UpdateComponentMessage], deeplink?, history? }`** — подменяет узлы по
  `id` через хранилище из [B-81](B-81-screen-keeps-its-tree.md). Кадры применяются по порядку, на
  одинаковый `id` побеждает последний, незнакомый `id` игнорируется. `deeplink` и `history`
  (`push` — умолчание, `replace`, незнакомое слово значит `push`) передаются приложению: историю
  ведёт оно.
- **`load { url }`** — `GET url`, ответ `KompotAction` идёт через всю цепочку. Ответ на `load`,
  после которого на том же экране нажат ещё один, отбрасывается. Признак «загрузка в пути» доступен
  приложению. Новый вид эндпоинта `load` в §16.1.
- Модуль выбирает реализация, причину записать: кандидаты — `kompot-commands` (рядом с
  `perform`) и `kompot-realtime` (дом `UpdateComponentMessage`).
- Вместе с типами: SPEC (§4, §16.1, §16.4, §15 — пример деградации `load` в дереве), схемы и
  проверка совместимости, DSL (`DslReachesTheWireTest`), TS-типы, кейсы `kompot-client-tck`, обход
  вида `load` в `kompot-tck`, `ktor`-хелпер ответа, `UPGRADING.md`.

- AC:
  - `perform` отвечает `update` с двумя узлами → на экране подменены оба, остальные узлы и их
    состояние не тронуты, стек не вырос;
  - `load` с ответом `update{deeplink}` → узлы подменены, приложение получило адрес и `push`;
  - два `load` подряд, первый отвечает позже → на экране ответ второго;
  - `load` отвечает `navigate` → обычный переход;
  - кейсы корпуса: незнакомый `id`, порядок кадров, незнакомое слово `history`;
  - `checkSchemaCompatibility` — `COMPATIBLE`; мутации убиты.
- Якоря: `kompot-commands/`, `kompot-realtime/`, `kompot-client/.../Perform.kt`, `Refresh.kt`,
  `kompot-ktor/`, `kompot-spec/SPEC.md`, `kompot-spec/schema/`, `kompot-client-tck/corpus/`,
  `kompot-tck/`.

## Находки

### Итерация 1 — 2026-10-09

- **Оба действия — в `kompot-commands`, рядом с `perform`.** `update` переиспользует
  `UpdateComponentMessage`, поэтому `kompot-commands` теперь берёт `api(projects.kompotRealtime)`;
  обратного ребра нет. Отвергнут `kompot-realtime` как дом `update`: у модуля канала нет ни одной
  полиморфной регистрации, `update` завёл бы ему первый `SerializersModule`, и каждое приложение,
  собирающее свой `Json`, получило бы второй модуль, о котором легко забыть, — и забывшее декодировало
  бы `update` в `UnknownAction` молча. Модуль команд уже регистрируют все, кто отвечает на `perform`,
  а `update` — самый частый ответ на него и на `load`. Схема: `KompotActionUpdate` и
  `KompotActionLoad` в `kompot-commands.schema.json`, кадр — `$ref` в `kompot-realtime.schema.json`
  (realtime раньше в списке модулей и владеет определением).
- **Клиент.** `withUpdates(overrides, onAddress)` пишет кадры в хранилище экрана из B-81 и отдаёт
  приложению `(deeplink, history)`, где `history` уже прочитана: `UpdateHistory.of` — `replace` только
  если так сказано, иначе `push`. Перехода тулкит не делает. `withLoad(scope, state, sink, load)`
  делает `GET` через лямбду приложения и отдаёт ответ вниз по цепочке, как `withPerform`, с отчётом о
  незнакомом в сток. «Тот же экран» — это `KompotLoadState` (по умолчанию — свой у обработчика):
  новое нажатие отменяет прежнюю загрузку, а решает номер нажатия, поэтому ответ транспорта, не
  заметившего отмену, тоже отбрасывается. `isLoading` — признак для индикатора приложения.
- **Сервер.** DSL `kompotUpdate(deeplink, history) { … }` — `UpdateBuilder : KompotContainerContext`,
  внутри работает обычный DSL экрана, кадр адресуется `id` самого узла; узел без `id` builder
  отвергает (иначе он получил бы путь, которого на экране нет, и молча игнорировался бы по §10.2).
  `respondKompotUpdate(json, deeplink, history) { … }` в `kompot-ktor` (модуль берёт
  `api(projects.kompotCommands)`). Тест «каждое поле `update` достижимо из builder» — тем же приёмом,
  что `DslReachesTheWireTest`, в `kompot-commands`; `load` и `perform` строятся конструктором, как
  все действия.
- **Найдено попутно и исправлено, потому что мешало самому пункту:** `kompotEngineSerializersModule`
  не регистрировал `kompot-commands` — `kompotJson()` декодировал `perform` в `UnknownAction`, хотя
  движок исполняет его `withPerform`. Теперь регистрирует; приложение, добавлявшее модуль само,
  работает как раньше (проверено тестом). Запись в `UPGRADING.md` как «не поломка».
- **Не исправлено, только записано:** экспорт DSL в `kompot-studio` (`DslExport.action`) печатает
  импорт `io.github.youndie.kompot.standard.<Имя>Action` для любого действия, а `perform`, `load`,
  `update` живут в `io.github.youndie.kompot.commands` — экспорт экрана с таким действием не
  скомпилируется. Дефект старше пункта (для `perform` он был и раньше).
- **Корпус случаев научился экрану.** Формат знал только форму, поэтому: `ClientCase.form` стал
  необязательным, добавлены `screen`, шаг `answer` и ожидания `nodes` (узел несёт как минимум эти
  ключи), `absent`, `addresses`; у `KompotFormClient` четыре операции экрана с реализацией по
  умолчанию — старый адаптер отчитывает случай об экране непроверенным. Это ломает ABI
  `kompot-client-tck` — коммит с `!`, запись в `UPGRADING.md`. Правила `16.4.1`–`16.4.8` получили
  номера в SPEC, `COVERAGE.md` теперь «§9 и §16.4»: корпус держит `16.4.1`–`16.4.4`, остальное — сервер,
  `kompot-tck` и тесты клиента. Случаи об экране гоняет `ScreenCorpusTest` в `kompot-client`:
  настоящая композиция, `withUpdates`, нарисованное читается обёрткой реестра (`decorated`). form-core
  ничего не рисует, поэтому `ClientCorpusTest` в `kompot-client-tck` берёт только случаи формы.
  `tools/ts-check.py` компилирует теперь и деревья, и ответы случаев (`update` иначе через `tsc` не
  проходил ни разу: в теле площадки его нет).
- **`kompot-tck`:** проверка `load` (адрес `load` — `GET` вида `load`, общая с `perform` функция
  `actionTargets`), и тело эндпоинта вида `load` без объявленной схемы сверяется с `KompotAction`
  профиля.
- **Мутации (все убиты, исходники восстановлены, `git status` чистый после каждой):**
  1. `withUpdates` не пишет кадры (`take(0)`) — красные `UpdateAndLoadTest` (три теста AC) и
     `ScreenCorpusTest`;
  2. кадры в обратном порядке — красный `ScreenCorpusTest` (случай порядка кадров);
  3. `UpdateHistory.of`: незнакомое слово — `replace` — красные `UpdateAndLoadTest` в
     `kompot-commands` и в клиенте, `ScreenCorpusTest`;
  4. адрес отдаётся с сырым словом `history` — красные «an update names its history…» и
     `ScreenCorpusTest`;
  5. ответ на прежнее нажатие не отбрасывается — красный «an answer to an earlier press is dropped…»
     (desktop-тест двух нажатий держит отмена, поэтому нужен и этот, с `NonCancellable`);
  6. отмена прежней загрузки снята — **выжила** первой попыткой (номер нажатия держит экран);
     дописан тест «a second press cancels the load of the first», после него красный;
  7. `isLoading` не сбрасывается в `finally` — красный «a failed load does not leave the screen
     loading»;
  8. ответ не уходит в сток — красный «an unknown answer is reported to the sink»;
  9. движок без `kompotCommandsSerializersModule` — красные `LoadTest` (engine Json) и
     `ScreenCorpusTest`;
  10. builder принимает узел без `id` — красный «an unnamed node in an update is refused»;
  11. `respondKompotUpdate` теряет `deeplink` — красный `RespondKompotUpdateTest`;
  12–14. `kompot-tck`: проверка `load` снята, вид не сравнивается, запасная схема `load` снята —
     красные тесты `LoadTargetTest` (и `PerformTemplatedTargetTest` для общего сравнения вида);
  15–17. раннер корпуса: `nodes` не сравниваются, `absent` не проверяется, шаг `answer` не исполняется —
     красные `ClientCorpusRunnerTest` и/или `ScreenCorpusTest`.
- **Где что гонялось.** WSL (`wsl-run`, scope `MemoryMax=6G`): `jvmTest`/`testAndroidHostTest`/
  `checkKotlinAbi`/`ktlintCheck` у `kompot-commands`, `desktopTest`/`testAndroidHostTest`/
  `checkKotlinAbi`/`ktlintCheck` у `kompot-client`, `check` у `kompot-ktor`, `kompot-spec`,
  `kompot-client-tck`, `kompot-tck`, `desktopTest` зависимых (`kompot-ds-material-compose`,
  `kompot-wizard-client`, `kompot-forms-client`, `kompot-preview`, `kompot-theme-client`,
  `kompot-studio`) — зелёные; все мутации. ABI-дампы сняты `updateKotlinAbi` на WSL и забраны из
  `build/kotlin/abi/` через `wsl-run cat`. Мак (`LOCAL=1`): голдены схем, TS-типов,
  `client-corpus.schema.json` и `COVERAGE.md` (`KOMPOT_SPEC_RECORD=true`), `ktlintFormat`,
  `checkSchemaCompatibility -Pkompot.compat.base=origin/main` — `COMPATIBLE` (два новых типа в
  деградирующей иерархии), `docs/scripts/*`, `tools/ts-check.py`. wasmJs-браузерные тесты не
  гонялись: общий код без платформенных веток.

