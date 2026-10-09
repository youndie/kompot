# kompot-client

Читающая сторона: **реестр рендереров**, `kompotJson`, деградация и её сток. Модуль отвечает за то,
что происходит между «пришло тело» и «нарисован экран», и здесь описано только это — правила самого
протокола в [SPEC.md](../kompot-spec/SPEC.md), и пересказывать их этот файл не будет.

## Два словаря, а не один

Тело проходит через **два** независимых набора, и потребитель собирает оба:

| | чем задаётся | что знает |
|---|---|---|
| `kompotJson(...)` | `SerializersModule` | какие типы вообще **раскодируются** |
| `KompotRegistry(...)` | `RenderersMap` | у каких типов есть **чем рисовать** |

Разделение не формальное: у него два разных провала. Тип, которого нет в первом, приезжает как
`UnknownComponent` — так и задумано, это §2.1, старый клиент переживает новый сервер. Тип, который
раскодировался, но не нашёл рендерера, — **сборка без плагина**: ошибка, которой никто не
проектировал. Поэтому у деградации два вида (`UNKNOWN_COMPONENT` и `UNRENDERABLE_COMPONENT`), и
путать их в логе нельзя.

## Реестр

«Плагин» здесь — обычная `Map<KClass<out KompotComponent>, KompotComponentRenderer<*>>`. Реестр
собирается сложением:

```kotlin
val registry = KompotRegistry(
    kompotCoreRenderers +           // UnknownComponent — деградация; без него экран падает
    kompotStandardRenderers +       // column, row, text, button, table, paginated_list
    generatedFormsClientRenderers + // :kompot-forms-client, сгенерирован KSP
    myRenderers,                    // свои
)
```

`generated<Tag>Renderers` пишет KSP по `@KompotComponentMarker` на классе рендерера — тип
выводится из его же generic-аргумента, руками ничего перечислять не надо. Точно так же собирается
`kompotJson(myModule)`: движок знает свои типы, приложение добавляет свои.

`kompotCoreRenderers` обязателен. Без него незнакомый тип не деградирует, а остаётся без рендерера —
то есть превращается из спроектированного отверстия в тихий провал.

Экран рисуется `KompotScreen(...)` (он же кладёт реестр в `LocalKompotRegistry`); дизайн-систему
потребитель кладёт сам:

```kotlin
CompositionLocalProvider(LocalKompotDesignSystem provides Material3DesignSystem()) {
    KompotScreen(rootComponent, registry, formController, actionHandler)
}
```

Шов, о котором стоит знать заранее: **`KompotLazyScreen` вместо `KompotScreen`**, если на экране
может оказаться `paginated_list`. Обычный экран — `Column` в вертикальном скролле, и ленивый список
внутри него Compose измерить не может; ленивый вариант делает корень экрана самим `LazyColumn`.

Уже собранный реестр можно обернуть, не разбирая: `registry.decorated { renderers -> ... }` — это то,
чем пользуются превью, скриншот-харнесс и студия, которым отдают готовый реестр, а не карту.

## Деградация: куда она уходит

По умолчанию — `println`. Этого хватает ровно до первого вопроса «сколько установок не видят этот
компонент»: в logcat это не тег, который кто-то фильтрует, на iOS этого нет вообще, и ни там, ни там
оно не доезжает до логирования самого приложения.

Свой сток — один `CompositionLocal`:

```kotlin
CompositionLocalProvider(
    LocalKompotDegradationSink provides KompotDegradationSink { kind, originalType, outcome ->
        analytics.count("kompot.degradation", "kind" to kind.name, "type" to originalType)
        crashReporter.breadcrumb("$kind $originalType -> $outcome")
    },
) { /* KompotScreen(...) */ }
```

`originalType` — **проводное** имя (`promo_banner`), а не имя Kotlin-класса: это та строка, которую
пишет сервер, объявляет схема и грепает человек.

### Три исхода — это то, что увидел человек перед экраном

| `KompotDegradationOutcome` | что на экране | кто это решил |
|---|---|---|
| `NOTHING` | дырка; остальной экран цел | никто, так работает деградация |
| `PLACEHOLDER` | заглушка тулкита | эта сборка |
| `SERVER_FALLBACK` | замена, названная сервером | сервер, осознанно |

Исход и вид — разные вопросы, и пары не произвольные:

* `UNKNOWN_COMPONENT` — любой из трёх. `SERVER_FALLBACK`, если сервер прислал замену; иначе
  `NOTHING`, а с опцией ниже — `PLACEHOLDER`.
* `UNRENDERABLE_COMPONENT` — **всегда `PLACEHOLDER`**, никакой опции. Спроектированная деградация
  тихая, ошибка сборки — нет.
* `UNKNOWN_ACTION` — всегда `NOTHING`: нажатие доехало до приложения и ничего не может сделать.

Раньше на этом месте стоял `Boolean` `drawnAsFallback`, и лог сообщал «нарисовано через fallback»
там, где никакого fallback не было и сервер его не присылал.

### Ответы сервера — через тот же сток

Незнакомое действие, которое поднял узел экрана, сток видит сам: `RenderNode` оборачивает обработчик.
Ответ сервера — на `perform`, на сабмит — входит в цепочку мимо этой обёртки, поэтому о незнакомом
действии в нём (и о незнакомой части `sequence`) сообщают `withPerform`, `withLoad` и `withLoginSubmit` — тому
стоку, который им передали. Передавайте тот же, что стоит в `LocalKompotDegradationSink`:

```kotlin
actionHandler.withPerform(scope, sink) { url, payload -> performOnServer(url, payload) }
```

Без стока они печатают, как сток по умолчанию. Ответы сценария (`withWizardNavigation`) идут в
колбэки приложения, и тулкит их не видит — сообщать о незнакомом в них должно приложение.

### Отказ канала обновлений

Экран с `realtimeTopic` (§10 SPEC) переживает падение канала так же, как дыру в словаре: дерево и
уже пришедшие обновления остаются, новых больше нет. Живой экран, тихо ставший собственной
фотографией, выглядит ровно как экран, который никто не обновлял, — поэтому отказ тоже уходит в сток,
отдельным членом `onRealtimeFailure(topic, cause)`.

Отдельным, а не четвёртым `KompotDegradationKind`, потому что форма другая: проводного имени типа тут
нет, а нужное читающему — почему канал ушёл, 401 или обрыв, — это исключение. У члена есть реализация
по умолчанию (тот же `println`), поэтому сток-лямбда выше компилируется как раньше; чтобы отказ тоже
доехал до аналитики, сток пишется объектом:

```kotlin
object : KompotDegradationSink {
    override fun onUnknown(kind: KompotDegradationKind, originalType: String, outcome: KompotDegradationOutcome) {
        analytics.count("kompot.degradation", "kind" to kind.name, "type" to originalType)
    }

    override fun onRealtimeFailure(topic: String, cause: Throwable) {
        crashReporter.breadcrumb("kompot realtime $topic failed: $cause")
    }
}
```

Отмена подписки отказом не считается: уход с экрана и смена топика завершают её намеренно.
Переподключения тулкит не делает — что показывать и когда пробовать снова, решает приложение,
получив событие.

## Действия, которые клиент исполняет сам

Узлы рисует реестр, а действия исполняет **цепочка обработчиков**, и в ней есть только то, что
приложение туда поставило. Действия 0.38 без своего звена в цепочке доходят до приложения как есть и
по §2.1 не делают ничего: у потребителя, обновившего зависимость, но не цепочку, кнопка с `confirm`
не спросит и не удалит — она просто ничего не сделает.

| действие | звено | модуль |
|---|---|---|
| `sequence` | `withSequences(followUp)` | `kompot-client` |
| `refresh` | `withRefresh(scope) { … }` — перезапросить экран | `kompot-client` |
| `update` | `withUpdates(overrides) { deeplink, history -> … }` — подменить узлы, адрес отдать приложению | `kompot-client` |
| `load` | `withLoad(scope, state) { url -> … }` — `GET`, ответ в цепочку | `kompot-client` |
| `show_message` | `withSnackbarMessages(host, scope, followUp)` | `kompot-ds-material-compose` |
| `present`, `confirm`, `close` поверх слоя | `withOverlays(overlays)` и `KompotOverlayHost` | `kompot-ds-material-compose` |

`followUp` — вершина цепочки: действие, пришедшее из ответа, из шага `sequence` или с кнопки
сообщения, проходит всю цепочку заново, а не только звенья под текущим:

```kotlin
lateinit var top: KompotActionHandler
top =
    myHandler
        .withRefresh(scope) { reloadMyScreen() }
        .withSequences { top.handle(it) }
        .withSnackbarMessages(mySnackbarHost, scope) { top.handle(it) }
        .withOverlays(overlays)
```

**Подмена узлов ответом и загрузка без перехода** (§16.4). `update` пишет свои кадры в хранилище
подмен экрана — то же, в которое пишет канал обновлений, — поэтому `withUpdates` получает то хранилище,
которое стоит над экраном в `LocalKompotNodeOverrides`. Перехода он не делает: если сервер назвал новый
адрес экрана, его получает колбэк вместе с `UpdateHistory.PUSH` или `REPLACE` (незнакомое слово уже
прочитано как `push`), а в историю его кладёт приложение. `withLoad` делает `GET` и отдаёт ответ вниз
по цепочке, как `withPerform`, поэтому оба ставятся **над** `withUpdates`. Из двух `load` одного экрана
побеждает последнее нажатие: ответ на прежнее отбрасывается, даже если пришёл позже. Экран — это
`KompotLoadState`, и он же говорит приложению, едет ли загрузка, — для своего индикатора:

```kotlin
val overrides = remember { KompotNodeOverrides() }
val loading = rememberKompotLoadState()
val handler =
    remember(overrides) {
        myHandler
            .withUpdates(overrides) { deeplink, history -> myHistory.record(deeplink, replace = history == UpdateHistory.REPLACE) }
            .withLoad(scope, loading, sink) { url -> myGet(url) }
            .withPerform(scope, sink) { url, payload -> performOnServer(url, payload) }
    }
CompositionLocalProvider(LocalKompotNodeOverrides provides overrides) {
    if (loading.isLoading) MyProgressBar()
    KompotLazyScreen(screen, registry, formController, handler)
}
```

`KompotOverlayHost(overlays, top)` ставится над экраном — он рисует слой и вопрос тем же реестром.
Переход (`navigate`) закрывает слой, прежде чем уйти дальше: слой лежит поверх экрана и уходит
вместе с ним (§12.5), поэтому `KompotOverlays` можно держать и одно на весь навигационный хост.

Шторка по своему макету, а не материаловская, — это свой хост. Он читает `presented` и `asking` и
отвечает тремя вызовами: `dismiss()` — закрыть верхний слой (свайп, тап мимо, то же, что `close`),
`refuse()` — отказаться от вопроса, `agree(top)` — согласиться и отдать действие вершине цепочки.
Записать в слой что-то своё хост не может: слой меняют только цепочка и эти три вызова.

```kotlin
overlays.presented?.let { layer ->
    MySheet(onDismiss = { overlays.dismiss() }) {
        LocalKompotRegistry.current.RenderNode(layer.content, top, formController)
    }
}
overlays.asking?.let { question ->
    MyQuestion(question.question, onYes = { overlays.agree(top) }, onNo = { overlays.refuse() })
}
```

**Экран, показанный слоем.** Сервер может попросить показать экран шторкой или диалогом двумя
способами (§12.1): маршрутом графа (`presentation` — за адрес) и заголовком ответа
`X-Kompot-Presentation` (за состояние: один адрес заказа отвечает и подтверждением, которое шторка,
и результатом, который нет). Старый клиент не знает ни того, ни другого и открывает экран, как
раньше, — поэтому сервер ставит их сразу. Клиент, который слой рисует, загружает экран, спрашивает
`PresentationHeader` — заголовок побеждает маршрут — и кладёт дерево в тот же слой, что и `present`:
значения `presentation` и `PresentKind` совпадают.

```kotlin
val (screen, header) = myLoadScreen(address)
when (val shownAs = PresentationHeader.presentedAs(header, route = myGraph.routeFor(deeplink))) {
    ScreenRoutePresentation.SCREEN -> myShowScreen(screen)
    else -> top.handle(PresentAction(content = screen, kind = shownAs))
}
```

Клиент без слоя передаёт `supported = setOf(ScreenRoutePresentation.SCREEN)` и получает экран на любой
ответ. Сервер на Ktor ставит заголовок `call.setPresentationHeader("sheet")` до ответа.

`tabs` и `expandable` подключать не нужно: их состояние живёт в рендерерах `kompotStandardRenderers`.
Загрузку и ошибку экрана рисует приложение (§12.6) — через `KompotScreenLoader(key, load, failed)`.
По умолчанию каждый новый `key` — другой экран, и рисуется экран загрузки. Приложение, у которого
в ключе адрес с фильтрами, передаёт `screenKey` грубее ключа (например, путь без query): тогда новый
`key` под тем же `screenKey` грузится за нарисованным деревом, и прокрутка с раскрытыми узлами
остаются на месте. Едет ли загрузка и есть ли под ошибкой дерево, видно в `KompotScreenLoaderState`
(параметр `state`). Кадры канала и прочие
подмены узлов пишутся в `KompotNodeOverrides` экрана (`LocalKompotNodeOverrides`); новое дерево их
сбрасывает (§4.4).

## Видимые заглушки — опция, а не умолчание

Речь только про `UNKNOWN_COMPONENT` — про тип, которого эта сборка не знает. В релизе он не рисует
ничего: это спроектированное поведение, а не сбой, и заглушка на его месте — лишний шум для человека,
который просто пользуется приложением. В debug-сборке, на
QA-стенде и в инструменте правки экранов удобно обратное. Одна строка, добавляемая **после**
`kompotCoreRenderers`, потому что она заменяет то, что тот зарегистрировал:

```kotlin
KompotRegistry(kompotCoreRenderers + myRenderers + kompotVisiblePlaceholderRenderers)
```

Серверный `fallback` выигрывает в обоих режимах: флаг про то, что делать, когда рисовать нечего.
Почему умолчание именно такое — [SPEC.md §2.1](../kompot-spec/SPEC.md).

## Свой рендерер

```kotlin
@KompotComponentMarker
class PromoBannerRenderer : KompotComponentRenderer<PromoBannerComponent> {
    @Composable
    override fun Render(
        component: PromoBannerComponent,
        actionHandler: KompotActionHandler,
        formController: FormController,
    ) { /* ... */ }
}
```

`formController` в сигнатуре есть всегда, даже у рендерера, который не имеет отношения к формам:
поле формы — такой же узел дерева, и контроллер едет сквозь дерево вместе с обработчиком действий.

## Посмотреть, как это выглядит

[**youndie.github.io/kompot**](https://youndie.github.io/kompot/) — тело слева, экран справа,
переключатель «какой клиент это читает» между ними. Витрина собрана из `wasmJs`-таргета этого же
репозитория, теми же рендерерами: незнакомый тип, заглушка и серверный `fallback` там — не картинка,
а этот модуль.
