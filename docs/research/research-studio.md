---
id: research-studio
title: kompot-studio — редактор и превью экрана server-driven UI
type: research
status: draft
date: 2026-09-03
---

# kompot-studio: ресёрч

**Задача.** Ценность kompot — «экран уезжает без релиза клиента», но экран кто-то пишет: сегодня это
DSL/JSON руками и проверка запуском клиента. Нужен Compose Desktop-инструмент: слева дерево экрана,
справа живой рендер **тем же** рендерером, что у клиента, переключатель brand kit А/Б и тем, лint
против словаря компонентов. Аудитория v1 — бэкенд-разработчик, пишущий экраны. Живёт модулем в kompot.

**Главный вывод.** Почти все кирпичи уже есть, и они уже стоят правильно: `KompotPreview` — это
ровно тот шов «тело ответа → настоящий рендер», который нужен студии; `kompot-spec` даёт закрытый
список типов и валидатор с путями; `kompot-theme-client` умеет наложить серверный кит с явным
`darkModeOverride`; viddik даёт headless-снимок и диф; konekt показывает, как выглядит словарь
проекта, brand kit, `BrandFrame` и записанные фикстуры. Чего нет — оболочки, которая это соединяет,
и трёх мелких швов в toolkit'е (§5). Инструмент строится как **библиотека, которую потребитель
запускает у себя** (как `viddikShowroom`), а не как автономное приложение, — иначе не получить
«настоящий рендерер» без загрузки чужих jar в свою Compose-линию.

---

## 1. Что уже есть в kompot

### 1.1 Шов рендера — `kompot-preview`

`kompot-preview/src/commonMain/kotlin/io/github/youndie/kompot/preview/KompotPreview.kt`:

```kotlin
@Composable
public fun KompotPreview(
    body: String,
    registry: KompotRegistry,
    designSystem: KompotDesignSystem,
    state: KompotPreviewState = KompotPreviewState(),
    json: Json = kompotJson(),
    actionHandler: KompotActionHandler = KompotActionHandler {},
    onDegraded: (kind: KompotDegradationKind, originalType: String) -> Unit = ::failOnDegradation,
)
public class KompotPreviewState(values: Map<String, FieldValue> = emptyMap(), allFieldsChanged: Boolean = false)
```

Вход — **строка тела**, не объект (намеренно: `call.respond(component)` теряет `type` у корня).
Форма тела определяется структурно: `"schema"` в корне → `KompotFormResponse`, `"screen"` →
`KompotScreenResponse`, иначе — полиморфный корень. Не-форменный экран всё равно получает
`FormController` над пустой схемой. Эта логика (`decodeBody`) — `private`; студии нужна такая же для
дерева, придётся вынести.

Три вещи в `KompotPreview`, о которых студия должна знать:

- `onDegraded` по умолчанию **бросает** (`failOnDegradation`) — правильно для голдена, неправильно
  для живого набора текста. Студия передаёт собирающую лямбду и показывает деградации как диагностику.
  Адаптер к `KompotDegradationSink` теряет флаг `drawnAsFallback`; если он нужен — ставить
  `LocalKompotDegradationSink` самим вокруг `RenderNode`.
- `LocalKompotPageLoader` не предоставляется: тело с `paginated_list` упадёт на
  `LocalKompotPageLoader not provided`. Студия даёт заглушку (следующая страница — из фикстуры или пусто).
- `compose.desktop.currentOs` есть только в `desktopTest` (в опубликованном source set он бы прибил
  хост в POM) — приложение-студия обязано взять его сама. Там же `IdePreviewExperiment.kt`
  документирует ловушку: `Could not initialize class org.jetbrains.skia.Surface` после одного
  прерванного кадра отравляет процесс превью насовсем.

### 1.2 Реестр и точки входа — `kompot-client`

`kompot-client/src/commonMain/kotlin/io/github/youndie/kompot/Components.kt`:

```kotlin
public typealias RenderersMap = Map<KClass<out KompotComponent>, KompotComponentRenderer<out KompotComponent>>
public class KompotRegistry(renderers: RenderersMap) {
    companion object { operator fun invoke(vararg renderers: RenderersMap, decorator: (RenderersMap) -> RenderersMap = { it }): KompotRegistry }
    @Composable fun <T : KompotComponent> RenderNode(component: T, actionHandler: KompotActionHandler, formController: FormController)
}
```

`RenderNode` — единственная точка диспетчеризации: подменяет узел live-обновлением по id, ищет
рендерер по `actual::class`, при промахе репортит `UNRENDERABLE_COMPONENT` и рисует
`UnknownComponentPlaceholder`. `KompotDegradationKind` = `UNKNOWN_COMPONENT | UNRENDERABLE_COMPONENT |
UNKNOWN_ACTION`; `KompotDegradationSink.onUnknown(kind, originalType, drawnAsFallback)`.

CompositionLocal'ы, которые обязан дать хост: `LocalKompotRegistry`, `LocalKompotDesignSystem`
(оба `error()` без значения), `LocalKompotPageLoader` (только для пагинации),
`LocalKompotDegradationSink` (по умолчанию `println`), `LocalKompotRealtimeUpdates`
(`Map<String, KompotComponent>`, по умолчанию пусто — **готовый хук для «подменить узел в студии»**).

`KompotDesignSystem` — три метода: `resolveColor(ColorToken)`, `resolveTypography(TypographyToken)`,
`resolveSurface(SurfaceRole)` (с дефолтом). Токены — открытые `value class` над `String`.
`kompotJson(applicationModule)` собирает `Json` из `kompotEngineSerializersModule` + модуль приложения;
`ignoreUnknownKeys = true`, `classDiscriminator = "type"`.

Импрессии — декоратор карты (`withImpressionTracking(tracker, naming)`), а не свойство реестра: студии
это подходит — можно обернуть реестр потребителя и показывать «что залогировалось бы».

### 1.3 Словарь — `kompot-spec`

`kompot-spec` (JVM-only) генерирует JSON Schema 2020-12 из `SerialDescriptor`'ов; 14 файлов в
`kompot-spec/schema/` (13 модульных + `kompot.profile.schema.json`). API:

```kotlin
KompotSpec.generateAll(modules: List<KompotSpecModule>): List<GeneratedSchema>
KompotSpec.profile(schemas, extensions = emptyMap()): JsonObject
KompotSpecResources(root).schemas(): Map<String, JsonObject>   // из jar; SchemaFiles.loadAll() — с диска
JsonSchemaValidator(documents, strictProfile: JsonObject? = null, extensionTypes: Set<String> = emptySet())
    .validate(value: JsonElement, ref: String): List<String>
```

Что схема **даёт** линтеру и инспектору: закрытый список типов сборки (профиль: `oneOf` +
`discriminator.mapping` wireName → `$ref`), имена и JSON-типы свойств, `required`, `const` для
`type`, `enum` для Kotlin-enum'ов (`SizeType`), `pattern` для deeplink/url/endpoint/topic, маркеры
`x-kompot-kind` (`hierarchy|variant|object|token|enum|extension`), `x-kompot-wire-type`,
`x-kompot-open`, `x-kompot-degrades`. `TckRunner.SCREEN_SCHEMA` показывает рабочий вызов:
`validate(json, "kompot.profile.schema.json#/$defs/KompotComponent")` со `strictProfile`.

Чего **нет**:

- **описаний** — только рукописные `annotations` в `KompotToolkitSpec.kt` (~30 свойств: deeplink,
  url, reloadUrl, realtimeTopic, theme colors/typography, perform.url…) плюс по одному на модуль;
  `spacing`, `maxLines`, `variant`, `ellipsis` без описаний. Нет `default`, нет `examples`, нет
  `title` на свойствах;
- **правил вложенности** — `column.children.items` = `$ref` на открытый `KompotComponent`; под
  профилем это «любой известный тип». «Column может содержать только X» невыразимо и не нужно;
- **структуры у ошибок** — `List<String>` с путём в префиксе (`$.screen.children[0].text: required
  property "id" is missing`). Для кликабельных диагностик — парсить префикс или форкнуть класс
  (~230 строк).

Но из схемы **выводится то, чего сегодня нет нигде**: какие поля — дочерние слоты. Свойство, чей тип
(или `items`) есть `$ref` на иерархию `KompotComponent`, — это слот. Значит обход дерева в студии
можно построить из схемы, а не из ручного списка. konekt держит такой список руками
(`KonektWalk.kt`) и пишет: «пять копий этого списка существовали, и каждая протухала отдельно».
Единственный обход в kompot — `collectJsonObjects(JsonElement)` в `kompot-spec/.../JsonWalk.kt`, без путей.

### 1.4 Правила, которых схема не выражает — `kompot-tck`

`TckRunner` ходит по живому серверу через `TckTransport` + OpenAPI; офлайн он не работает. Но три
проверки — чистые функции над телом и переносятся в студию напрямую: `component-id` (id непустой и
уникальный), `text-spans` (`text` == конкатенация `spans`), `schema`. Четвёртая, `form-fields`
(fieldId схемы ↔ экрана ↔ ссылок), — тоже над телом, если тело — `KompotFormResponse`. Остальные
(`etag`, `pagination`, `idempotency`, `auth`, `updates`, `navigation`, `perform`) требуют сервера и
остаются TCK.

### 1.5 Темы и бренды

Wire: `KompotTheme(id, light: KompotPalette, dark: KompotPalette?, typography: Map<String, KompotTextStyle>)`
(`kompot-theme`, без Compose). Клиент:

```kotlin
public class RemoteThemeDesignSystem(theme: KompotTheme, fallback: KompotDesignSystem, darkModeOverride: Boolean? = null) : KompotDesignSystem
@Composable public fun rememberKompotDesignSystem(theme: KompotTheme?, fallback: KompotDesignSystem): KompotDesignSystem
public fun KompotTheme.toMaterialColorScheme(base: ColorScheme, darkMode: Boolean): ColorScheme      // kompot-ds-material-compose
@Composable public fun rememberMaterialColorScheme(theme: KompotTheme?, darkMode: Boolean = isSystemInDarkTheme()): ColorScheme
```

Оверлей по токену: цвет из темы, иначе fallback; типографика — merge по полям; `resolveSurface`
делегируется fallback'у целиком. `darkModeOverride` существует «для тестов и превью» — это ровно
переключатель студии. Но `rememberKompotDesignSystem` его **не пробрасывает** — konekt уже наткнулся
(ночная карточка под дневной кнопкой, `design-brand-kit.md`, «Light and dark must be asked for
together») и строит `RemoteThemeDesignSystem` сам. Апстрим-фикс — параметр `darkMode` в `remember`-обёртке.

Полный бренд = **и** design system, **и** `MaterialTheme(colorScheme = …)`: рендереры читают
`MaterialTheme` напрямую для хрома (кнопки, поля). Формы (радиусы) — клиентская константа
(`SurfaceRole` не путешествует; в konekt — `KonektShapeScale.byBrand`). Реестра «несколько тем» в
toolkit'е нет; `KompotTheme.id` — только диагностика. `experiments-core` — A/B на сервере, к темам не
относится.

### 1.6 Остальное, что пригодится

- `kompot-core`: `KompotComponent { id; modifiers }`, `KompotModifierNode` — **закрытый** sealed
  (`padding|background|gradient|size|weight`), `Json.decodeKompotComponent(body)` /
  `encodeKompotComponent`.
- DSL (`kompot-standard/.../Dsl.kt`): `kompotScreen { column { text(…) } }`; пропущенный id →
  `Uuid.random()` — для редактора и дифов по id это шум, генерировать детерминированно.
- `kompot-navigation`: `NavigationGraph(routes: List<ScreenRoute(deeplink, endpoint, title?, kind)>)` —
  готовое «дерево экранов» слева, если студия подключена к серверу.
- `kompot-client-cache`: `KompotScreenFetcher { suspend fun fetch(key, ifNoneMatch): KompotFetchResult }` —
  контракт, под который в студии пишется HTTP-источник с ETag.
- `kompot-ktor`: `respondWithETag` (sha256 тела) — студия может опрашивать по `If-None-Match` и
  перерисовывать только на 200.
- Версии (`gradle/libs.versions.toml`): Kotlin 2.4.10, Compose Multiplatform 1.11.1, material3
  1.11.0-alpha07, serialization 1.11.0, KSP 2.3.11, viddik 0.1.1.8, sborka 0.1.0.18; JVM toolchain 17.
- В репозитории уже есть `tools/canvas/` (Python: макет Claude Design ↔ дерево провода, диф по id,
  генерация токенов/кита) и `skills/kompot-layout/SKILL.md` (пришли из ветки `feat/canvas-tools`).
  Слово «canvas» здесь занято макетом — имя инструмента не должно с ним путаться.

---

## 2. appframe и viddik

**appframe** (`ru.workinprogress:appframe-desktop`, в ходу 0.0.12, CMP 1.11.1, только `jvm("desktop")`) —
это **только оконный хром**: `AppFrame(onCloseRequest, state, title, icon, style: TitleBarStyle,
onKeyEvent…, actions: RowScope.() -> Unit, content)`. Никаких панелей, дерева, табов, редактора,
диалогов. Даёт окно и слот действий в тайтлбаре — туда просятся переключатели бренда/темы/размера.
Сигнатура уже ломала `:shopPreview` — считать её опубликованным контрактом.

**viddik** (`github.com/youndie/viddik`) — screenshot-тестер через реальное Skiko-окно. Полезное для студии:

```kotlin
data class ViddikComponent(name, group, width = 400, height = AUTO_HEIGHT, fontScale = 1f, tolerancePercent: Double?, content: @Composable () -> Unit)
fun captureComposable(width, height, compositionLocals: List<ProvidedValue<*>>, fontScale, content): BufferedImage   // viddik-testing-core
object ImageDiffer { fun diff(expected, actual, channelTolerance): DiffResult }   // diffImage, mismatchPercent, matches(...)
object ViddikEngine { fun verify(component, snapshotsDir, reportsDir, tolerancePercent, …) }
@Composable fun ViddikShowroom(components: List<ViddikComponent>, modifier: Modifier = Modifier)   // LazyColumn по group → detail; без поиска и панелей
val LocalViddikDarkTheme: ProvidableCompositionLocal<Boolean>; fun viddikTypography(base): Typography
```

Голдены — PNG `"${group}_${name}".png` в `src/<testSourceSet>/snapshots`, запись через
`VIDDIK_RECORD_MODE`. KSP генерирует `GeneratedViddikRegistry.components`, а `ViddikShowroomLauncher`
грузит его **рефлексией** из classpath'а потребителя — тот же приём подходит студии для вкладки «истории».

Две оговорки. `viddik-testing-core` тянет `compose.desktop.currentOs` и JUnit как `api` — это
test-classpath-инструмент; в студии использовать `captureComposable`/`ImageDiffer` можно, но
зависимость должна быть `runtimeOnly` на стороне приложения, не библиотеки. И **линии Compose**:
viddik 0.3.x требует CMP 1.12, kompot на 1.11.1 привязан к viddik 0.1.1.8 (руками, без gradle-плагина,
в `kompot-ds-material-compose`), appframe — 0.1.2.12. Смешивание падает в рантайме
(`NoSuchMethodError` на первом кадре). Студия должна ехать на одной линии со всеми тремя.

---

## 3. konekt: как выглядит словарь и brand kit у потребителя

`docs/design/design-app-canvas.md` — интерфейсный дизайн (Claude Design, 9 секций, 393×852, light/dark)
и **словарь компонентов сборки**: `usage_counter_card`, `plan_card`, `esim_qr`, `esim_card`,
`order_row`, `banner`, `snackbar`, `step_meter`, `skeleton` (+ позже `bottom_nav`, `surface`,
`slider_input`, `icon`, `screen_header`); один KSP-модуль со своим `kompotModuleTag`;
`shared/spec/schema/konekt-components.schema.json` генерируется из типов. Секция 06 канваса — «все
контролы во всех состояниях, включая unknown-component block» — это буквально Storybook-галерея,
нарисованная руками.

Что konekt держит рядом со словарём и что студия может читать как конфиг проекта:

| в konekt | что это | для студии |
|---|---|---|
| `shared/components/.../Vocabulary.kt` — `CounterStates.all`, `PlanStates`, `ButtonEmphasis`, `SurfaceTones`… | открытые слова полей (`state`, `variant`, `tone`); неизвестное слово рисует нейтральное | lint «слово не из словаря → нарисуется нейтральный вариант», варианты историй |
| `commonTest/.../KonektDictionary.kt` — `List<Pair<wireName, KompotComponent>>` | один полностью заполненный экземпляр на тип | истории «каждый тип по одному», проверка «у каждого типа есть рендерер» |
| `KonektWalk.kt` | ручной обход контейнеров (`column`, `row`, `paginated_list.initialItems+emptyState`, `surface`) | не нужен, если слоты выводятся из схемы (§1.3) |
| `client/.../screenshots/ScreenshotHarness.kt` — `BrandFrame(brand, content)` | `KonektTheme(theme = BrandKits.kits()[brand], darkMode = LocalViddikDarkTheme, typography = viddikTypography(…))` + `LocalKompotRegistry provides konektRegistry()` | **это и есть «frame» студии**: потребитель отдаёт `@Composable (brand, dark, content) -> Unit` |
| `RecordedScreenScreenshots.kt` — `/recorded/home-screen.json`, декод «клиентским `Json`» | голден с тела, записанного со стенда | режим «открыть запись» и «записать с сервера» |
| `server/src/main/resources/themes/brand-a.json`, `brand-b.json` | wire `KompotTheme`, все 20 токенов `M3Colors.all` в обеих палитрах | переключатель А/Б; lint «токен не назван в ките → дефолтный фиолетовый» |
| `KonektShapeScale.byBrand` | формы — клиентская константа | причина, почему бренд нельзя переключить одним JSON: студии нужен frame от потребителя |
| `KonektSpec.profile()` + `KonektSchemaGoldenTest` | закрытый список типов сборки | вход линтера |

Итог: словарь у потребителя — это **схема сборки + Vocabulary + образцы + brand kits + frame**.
Студия не должна знать ни одного из них заранее; она принимает их параметрами.

---

## 4. Внешний контекст (проверено в сентябре 2026)

- **DivKit Playground** (divkit.tech/playground) — ближайший аналог: Monaco JSON слева, веб-рендерер
  справа, тулбар размеров/темы/RTL, панели **Errors (N)** и **Structure**, строка «Components: 11 /
  Time to render: 26 ms», визуальный режим `?design=1` с палитрой и инспектором, матрица «фича →
  минимальная версия платформы». Живой превью на телефоне — через WebSocket к приложению DivKit
  Playground. Рендер — веб, не нативный. Брать: панели Errors/Structure, счётчик узлов, пресеты
  устройств, матрица поддержки по версиям клиента.
- **Judo** (Mac-приложение, SwiftUI-рендер, закрыто), **Nativeblocks** (аннотация `@NativeBlock` →
  схема из кода → веб-студия, закрыто), **Builder.io** (реестр компонентов управляет инспектором).
  Airbnb GP, Lyft, Uber — публичных редакторов нет; у Lyft полезна идея «capabilities per client
  version». Beagle архивирован (2024), Stac — галерея, не редактор.
- **Storybook для Compose**: Showkase — Android-only; **Kotlin/Storytale** — мультиплатформенная
  галерея от JetBrains, но только dev-сборки (`0.0.4-alpha…`), без Maven Central. Ничего готового.
- **Compose Hot Reload** — 1.0.0 стабилен (январь 2026), 1.2.0 текущий; включён по умолчанию в CMP
  ≥ 1.10 для desktop; требует JBR, Kotlin ≥ 2.1.20, JVM target ≤ 21. Это инструмент dev-цикла
  (`hotRunJvm` + Gradle continuous), не встраиваемый рантайм — но если студия запускается у
  потребителя через `hotRunJvm`, **правка рендерера в konekt перерисовывается в окне студии без
  перезапуска**. Это и есть «Storybook для рендереров» даром. kompot на toolchain 17 и CMP 1.11.1 подходит.
- **Jewel** (IDE-подобный UI kit JetBrains): standalone на Maven Central, 0.40.0 под CMP 1.11.0
  (0.41 — под 1.12); есть `LazyTree`, `HorizontalSplitLayout`, `TabStrip`, `SpeedSearchArea`. Pre-1.0,
  версия привязана к билду IJP, нужно исключать `compose.material`. Берём: линия совпадает с
  kompot, а дерево/сплиты/табы иначе пришлось бы писать самим (§5.5).
- **Редактор кода в Compose**: зрелого нет. Практика — `BasicTextField(TextFieldState)` +
  `OutputTransformation`/`AnnotatedString` с собственным JSON-лексером или Highlights (KMP).
  Qawaz/compose-code-editor заброшен (2024). `components-splitpane` — всё ещё `@Experimental`.
- **Валидация JSON Schema (KMP)**: OptimumCode/json-schema-validator 0.5.5 — 2020-12, `JsonElement`,
  структурированный output. Альтернатива своему валидатору, если понадобится полнота драфта; для
  подмножества, которое печатает kompot, свой хватает. **kotlinx-schema** (JetBrains, experimental) —
  если захочется описания из KDoc в схему.
- **Файловые события**: `WatchService` на macOS — polling (~10 с по умолчанию, 2 с с
  `SensitivityWatchEventModifier.HIGH`); JDK-8293067 (FSEvents) не закрыт. Для «смотреть каталог
  записей» хватит 2 с или `directory-watcher` (JNA).
- **DTCG Design Tokens 2025.10** — первый стабильный формат; Resolver (light/dark, brand A/B) —
  черновик «do not implement». Не менять wire `KompotTheme`, но экспорт/импорт DTCG для кита — дешёвая
  и уместная опция позже.

---

## 5. Архитектура

### 5.1 Форма поставки: библиотека, запускаемая у потребителя

Три варианта:

| | как | плюсы | минусы |
|---|---|---|---|
| **A. библиотека `kompot-studio` + `main` у потребителя** | потребитель пишет 15 строк: `KompotStudio(registry = konektRegistry(), json = konektClientJson, frame = ::BrandFrame, …)`; запускает `./gradlew :client:studio` / `hotRunJvm` | настоящие рендереры и `Json` потребителя в одном classpath; одна Compose-линия; Hot Reload рендереров; как `viddikShowroom` | у каждого проекта свой запуск (решается gradle-задачей позже) |
| B. автономное приложение, грузит jar рендереров | `URLClassLoader` поверх сборки потребителя | «скачал и открыл» | две копии Compose runtime, версия линии, CompositionLocal через границу загрузчиков — не работает без изоляции; Hot Reload недоступен |
| C. плагин IDE | превью в IntelliJ | рядом с кодом | `IdePreviewExperiment.kt` уже показал цену skiko в IDE; отдельный toolchain плагина |

**Выбор — A.** B неработоспособен в разумный срок; C — возможная надстройка над A (та же
композиция, другой хост), не первый шаг. Модуль `:kompot-studio`, `jvm("desktop")` only,
`explicitApi`, публикуется как `kompot-studio-desktop` под BOM. Внутри модуля — сама студия и
`kompot-studio/src/desktopMain` пример на `kompotStandardRenderers` (как `IdePreviewExperiment`).

### 5.2 Контракт с потребителем

```kotlin
public class KompotStudioConfig(
    val registry: KompotRegistry,                 // konektRegistry()
    val json: Json,                               // konektClientJson — «тем же Json, что клиент»
    val frame: @Composable (brand: String?, dark: Boolean, content: @Composable () -> Unit) -> Unit,
    val brands: List<String> = emptyList(),       // имена, которые понимает frame
    val schemas: Map<String, JsonObject>,         // KompotSpecResources(...).schemas() + модули сборки
    val extensionTypes: Set<String> = emptySet(),
    val vocabulary: Map<String, Map<String, Set<String>>> = emptyMap(), // wireType -> field -> words
    val samples: List<Pair<String, KompotComponent>> = emptyList(),      // konektDictionary
    val sources: List<ScreenSource>,              // файлы, каталог записей, HTTP + NavigationGraph
    val pageLoader: KompotPageLoader = StubPageLoader,
)
```

`frame` — центральное решение: бренд нельзя переключить одним `KompotTheme`, потому что формы и
шрифты — клиентские (`KonektShapeScale`, `KonektTypography`); значит **потребитель отдаёт композицию
целиком**, а студия только просит её «в бренде X, тёмную». Для проекта без своей темы студия даёт
дефолтный frame: `RemoteThemeDesignSystem(theme, Material3DesignSystem(), darkModeOverride = dark)` +
`MaterialTheme(colorScheme = theme.toMaterialColorScheme(base, dark))` по списку `KompotTheme` из файлов.

### 5.3 Модель документа

Источник правды — **текст тела** (wire JSON), не объект и не DSL. Причины: это то, что видит клиент
(включая потерянный `type` у корня); это то, что записывает стенд и что лежит в фикстурах; это то,
что валидирует схема. Kotlin DSL остаётся в IDE: цикл «правлю DSL → сервер/тест пишет тело → студия
подхватывает файл или URL». Встраивать kotlin-scripting (полный компилятор в процессе, 1–3 с на
скрипт, JSR-223 выпилен) — не для v1.

Дерево слева строится из `JsonElement` + схемы: узел = объект с `type`; дочерние слоты — свойства,
чей `$ref`/`items.$ref` ведёт в иерархию `KompotComponent` (§1.3). Путь узла — JSON-путь
(`$.screen.children[0]`), тот же формат, что в префиксе ошибок валидатора, — поэтому диагностика
кликабельна без парсинга чего-то нового. Правки в дереве (переставить, удалить, дублировать) —
copy-on-write над `JsonObject` и обратная запись в текст.

### 5.4 Панели

```
┌ AppFrame ─ [источник ▾] [brand A|B] [☾] [393×852 ▾] [⟳ watch] ─────────────┐
│ Экраны/истории │ Дерево (JSON-путь)  │ Рендер: KompotPreview в frame(brand, dark) │
│ NavigationGraph│ ▸ column#root       │   ┌────────────┐                           │
│ записи/*.json  │   ▸ text#title      │   │ живой экран│   Действия: navigate app://…│
│ образцы словаря│   ▸ surface#hero    │   └────────────┘   Форма: [empty|filled|errors]│
├───────────────┴────────────────────┴───────────────────────────────────────────┤
│ Текст тела (BasicTextField + подсветка)  │ Диагностика: schema · rules · degradation · vocabulary│
└──────────────────────────────────────────┴────────────────────────────────────────────────┘
```

- **Рендер** — `KompotPreview(body, registry, designSystem, state, json, actionHandler, onDegraded)`
  внутри `frame`; `actionHandler` пишет в лог действий, `navigate` при подключённом сервере
  переходит по `NavigationGraph`. Размеры — пресеты (393×852 как канвас konekt) через
  `Modifier.requiredSize` + `Box(clip)`; плотность 1, как в `tools/canvas`.
- **Диагностика**, четыре слоя, каждый со своим источником: (1) синтаксис — `SerializationException`
  с offset'ом; (2) `JsonSchemaValidator` со `strictProfile` — типы/поля/паттерны; (3) правила тела,
  перенесённые из TCK — ids, `text`/`spans`, `form-fields`; (4) деградации настоящего рендера через
  `onDegraded` — «этот клиент не нарисует `esim_transfer_widget`». Плюс (5) словарь проекта:
  неизвестное слово в открытом поле, `ColorToken`, которого нет ни в ките бренда, ни в
  `M3Colors.all`. Слои 1–4 не требуют от потребителя ничего, кроме `schemas`.
- **Истории** — три источника: `samples` (один экземпляр на тип), состояния формы
  (`KompotPreviewState`: пустая / заполненная / все ошибки), `GeneratedViddikRegistry` потребителя
  (рефлексией, как `ViddikShowroomLauncher`). Это «секция 06» канваса, но живая.
- **Голдены** — «снять кадр» = `captureComposable(width, height, compositionLocals = …) { frame(brand, dark) { KompotPreview(…) } }`;
  «сравнить с голденом» = `ImageDiffer.diff` + картинка дифа. Запись — в
  `src/<testSourceSet>/snapshots` по конвенции viddik, чтобы `viddikVerify` потребителя потом это
  и проверял.

### 5.5 UI-кирпичи: Jewel

Оболочка — **Jewel** (`org.jetbrains.jewel:jewel-int-ui-standalone`, 0.40.x): `LazyTree` для дерева,
`HorizontalSplitLayout`/`VerticalSplitLayout` для панелей, `TabStrip` для источников и вкладок
диагностики, `SpeedSearchArea` для поиска по дереву, `DecoratedWindow` вместо appframe (тот же
Compose-нарисованный тайтлбар, но в стиле IDE и на JBR). Jewel 0.38+ собран против CMP 1.11.x —
та же линия, что у kompot (1.11.1); 0.41 переедет на 1.12 вместе с kompot, когда тот переедет.

Три условия, которые надо проверить на spike, а не предполагать:

- Jewel просит исключить `org.jetbrains.compose.material` (Material 2); kompot использует
  **material3** — конфликта быть не должно, но рендер-панель обязана жить внутри `MaterialTheme`
  потребителя (рендереры читают `MaterialTheme.colorScheme`), а хром — внутри `IntUiTheme`.
  Граница — `frame` из §5.2, ровно там, где она и так нужна.
- ~~`DecoratedWindow` рассчитан на JetBrains Runtime — тот же JBR, что нужен Compose Hot Reload;
  на другом JDK он деградирует до обычного окна. Приемлемо.~~
  **Неверно, исправлено спайком B-08 (03.09.2026).** `DecoratedWindow` не деградирует: первая его
  строка — `if (!JBR.isAvailable()) error("DecoratedWindow can only be used on JetBrainsRuntime")`.
  На не-JBR студия падала бы на старте с сообщением про Jewel. Развилка живёт в самой студии,
  и условие в ней — тот же `JBR.isAvailable()`, а не догадка по `java.vendor`: **на JBR
  `java.vendor` равен `Oracle Corporation`**, и приблизительная проверка уводила студию на
  недекорированную ветку ровно на том рантайме, ради которого ветка и заводилась.
  Рантайм больше не «должен быть установлен»: `:kompot-studio` берёт его toolchain'ом
  (`JvmVendorSpec.JETBRAINS`, 21) — лениво, только на запуске и на тестах.
- Версия Jewel привязана к билду IJP (`0.40.0-262.10315.125`); её фиксируем в
  `libs.versions.toml` рядом с viddik и не поднимаем отдельно от линии Compose.

Файловые диалоги — AWT `FileDialog`. Редактор — `BasicTextField(TextFieldState)` + свой JSON-лексер
на `AnnotatedString` (в Jewel есть `TextArea`, но без подсветки); тела экранов < 100 КБ,
производительности хватит. appframe при Jewel не нужен — `DecoratedWindow` закрывает его роль.

### 5.6 Что попросить у toolkit'а (мелкое, но блокирующее чистоту)

1. `kompot-preview`: вынести `decodeBody` в публичную функцию (`decodeKompotBody(json, body): DecodedBody`)
   и дать `KompotPreview` параметр `pageLoader`.
2. `kompot-theme-client`: `rememberKompotDesignSystem(theme, fallback, darkMode: Boolean? = null)`
   (konekt уже обходит это руками).
3. `kompot-spec`: структурированная ошибка `SchemaFinding(path, message, rule?)` вместо `String`, и
   помощник `childSlots(schemas): Map<wireType, List<propertyName>>`, выведенный из `$ref`'ов, — им
   же можно заменить ручные `konektWalk`.
4. `kompot-registry-processor`: переносить KDoc компонента и его свойств в схему
   (`description`) — это улучшает SPEC для второй реализации и делает инспектор в v3 возможным.
5. DSL: детерминированные id вместо `Uuid.random()` при пропуске (например, из пути в дереве).

---

## 6. Риски

| риск | почему | что делать |
|---|---|---|
| одна линия Compose на kompot + viddik + Jewel + студию | смешение падает в рантайме, не при резолве | студия в репозитории kompot, версии из `libs.versions.toml`; viddik 0.1.1.8; Jewel 0.40.x (линия 1.11); переезд на 1.12 — одним коммитом для всех трёх |
| Jewel pre-1.0, версия привязана к билду IJP | API помечены experimental, ломаются между 0.x | обёртки над `LazyTree`/`SplitLayout` в одном файле студии, чтобы миграция была локальной |
| skiko в процессе | `~/.skiko` холодный, отравление после прерванного кадра | приложение (не библиотека) берёт `compose.desktop.currentOs`; рендер за `try` с кнопкой «перезапустить превью» |
| `KompotPreview` бросает на деградации | дефолт для голденов | всегда передавать собирающий `onDegraded`; дефолт не менять |
| `kompot-spec` — JVM-only и тянет все протокольные модули | ок для desktop, невозможно для iOS/wasm | студия только desktop; это осознанно |
| JSON-текст как источник правды — редактирование неудобно | нет инспектора | v1 — для бэкенд-разработчика, у которого DSL в IDE; инспектор после п.4 §5.6 |
| словарь проекта не в toolkit'е | `Vocabulary`, образцы, frame — у потребителя | всё параметрами конфигурации; студия без конфигурации работает на `kompotStandardRenderers` с 4 слоями диагностики |
| `WatchService` polling на macOS | 2–10 с | `HIGH` sensitivity; ETag-опрос для HTTP |

---

## 7. План

**Spike — сделан (B-08, 03.09.2026); один вопрос из пяти остался открытым.** Hot Reload в окне
студии **не проверен**: задачи `hotRunDesktop`/`hotReloadDesktopMain`/`reload` регистрируются
Compose-плагином сами, рантайм теперь JBR, но подтвердить «правка `ButtonRenderer` меняет кадр без
перезапуска» может только сеанс с экраном. Остальные четыре подтверждены запуском и тестами
(`SpikeCaptureTest`, `JetBrainsRuntimeTest`). Исходный план спайка:

**Spike (1–2 дня).** `:kompot-studio` с `jvm("desktop")`, `main` на `kompotStandardRenderers`:
Jewel `DecoratedWindow` + `HorizontalSplitLayout`, файл → `KompotPreview` во `frame` по умолчанию,
тёмная/светлая, собирающий `onDegraded`, панель с `JsonSchemaValidator` по профилю toolkit'а.
Проверить: Jewel 0.40 и material3 1.11.0-alpha07 живут в одном окне; `hotRunJvm` перерисовывает
рендерер; `captureComposable` снимает кадр из той же композиции. Результат — уверенность в §5.1,
§5.5 и §5.6. Бэклог — `docs/backlog.md`.

**v1 «смотреть и линтить».** Источники: файл, каталог (watch), HTTP с ETag + `NavigationGraph`;
дерево из JSON по слотам из схемы; рендер; бренд/тема/размер; диагностика слоёв 1–4; лог действий;
состояния формы. Конфиг потребителя из §5.2. Пилот на konekt: открыть `/recorded/home-screen.json`
и `brand-a.json`/`brand-b.json`, получить те же кадры, что `Brand_A.png`/`Brand_B.png`.

**v2 «править и снимать».** Редактор текста с подсветкой и синхронизацией каретка ↔ узел; правки в
дереве; сохранить; снять кадр/сравнить с голденом; истории из образцов и viddik-реестра; слой 5
(словарь, токены кита). Gradle-задача `kompotStudio` по образцу `viddikShowroom`.

**v3 «собирать».** Инспектор свойств по схеме (после описаний из KDoc), палитра типов, drag-and-drop
в дереве, экспорт DSL-черновика. Для дизайнера/PM — только здесь.

Не делаем: автономный дистрибутив с загрузкой чужих jar; встроенный Kotlin-скриптинг; правила
«column может содержать только X» (их нет в модели и не должно быть); замену `tools/canvas`
(макет ↔ провод — другая задача, канвас остаётся входом, студия — выходом).

---

## Источники

kompot: `kompot-preview/src/commonMain/.../KompotPreview.kt`, `kompot-preview/src/desktopMain/.../IdePreviewExperiment.kt`,
`kompot-client/src/commonMain/.../{Components,KompotClient,DesignSystem,Degradation,Realtime,ImpressionTracking}.kt`,
`kompot-spec/src/main/kotlin/.../{KompotSpecModule,KompotToolkitSpec,JsonSchemaValidator,KompotSpecResources,JsonWalk}.kt`,
`kompot-tck/src/main/kotlin/.../TckRunner.kt`, `kompot-theme/.../KompotTheme.kt`,
`kompot-theme-client/.../RemoteThemeDesignSystem.kt`, `kompot-ds-material-compose/.../Material3RemoteTheme.kt`,
`kompot-navigation/.../NavigationGraph.kt`, `kompot-client-cache/.../KompotScreenFetcher.kt`,
`tools/canvas/README.md`, `skills/kompot-layout/SKILL.md`, `gradle/libs.versions.toml`.
appframe: `appframe/appframe/src/desktopMain/kotlin/ru/workinprogress/appframe/{AppFrame,TitleBar,TitleBarStyle}.kt`. viddik: `README.md`,
`viddik-annotations/.../{ViddikComponent,ViddikShowroom}.kt`, `viddik-testing-core/.../{CaptureEngine,ViddikEngine,ImageDiffer}.kt`.
konekt (github.com/youndie/konekt): `docs/design/design-app-canvas.md`, `docs/design/design-brand-kit.md`,
`shared/components/.../{Vocabulary,KonektWalk}.kt`, `shared/components/src/commonTest/.../KonektDictionary.kt`,
`client/src/jvmTest/.../screenshots/{ScreenshotHarness,RecordedScreenScreenshots}.kt`, `shared/spec/schema/README.md`.
Внешние: divkit.tech/playground · github.com/JetBrains/compose-hot-reload · jewel-ui.dev ·
github.com/Kotlin/Storytale · github.com/OptimumCode/json-schema-validator · github.com/Kotlin/kotlinx-schema ·
designtokens.org/tr/drafts/format · bugs.openjdk.org/browse/JDK-8293067.
