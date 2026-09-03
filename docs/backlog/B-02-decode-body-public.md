---
id: B-02
title: "kompot-preview: публичный decodeKompotBody и параметр pageLoader"
status: done
priority: P1
size: S
stage: upstream
epic: research-studio
---

# B-02 — `kompot-preview`: публичный `decodeKompotBody`, параметр `pageLoader`

`KompotPreview(body: String, …)` определяет форму тела структурно — `"schema"` в корне →
`KompotFormResponse`, `"screen"` → `KompotScreenResponse`, иначе полиморфный корень — в
`private fun decodeBody` (`kompot-preview/src/commonMain/.../KompotPreview.kt`). Студии та же логика
нужна для дерева слева и для диагностики; приватная функция заставит её скопировать три ветки и
разойтись с превью в первый же раз, когда появится четвёртая форма тела. Вторая дыра: превью не
предоставляет `LocalKompotPageLoader`, и тело с `paginated_list` падает с
`LocalKompotPageLoader not provided` (`kompot-client/.../Components.kt`) — для голдена это допустимо,
для живого набора текста нет.

- **Решение: вынести `public fun Json.decodeKompotBody(body: String): KompotDecodedBody`**
  (`screen: KompotComponent`, `schema: FormSchema`, `realtimeTopic: String?`) в `kompot-preview` и
  вызывать её из `KompotPreview`. Потому что «какие формы тела бывают» — факт протокола, и он должен
  быть в одном месте для превью, студии и любого будущего инструмента.
- **`KompotPreview` получает `pageLoader: KompotPageLoader? = null`** и предоставляет
  `LocalKompotPageLoader`, если передан. Дефолт остаётся «не предоставлен»: голден с пагинацией,
  который тихо грузит пустую страницу, — тот же класс ошибки, что серый плейсхолдер вместо рендерера.
- Альтернатива — студия делает свой `CompositionLocalProvider` вокруг `KompotPreview`: работает,
  но тогда параметры превью и обёртка вокруг него — два места, где решается, что видит экран.
- Не делаем: не меняем дефолт `onDegraded = ::failOnDegradation` — бросать в голдене правильно.

- AC: тест в `kompot-preview` декодирует три формы тела через публичную функцию и получает те же
  объекты, что `KompotPreview`; тело с `paginated_list` рисуется при переданном `pageLoader` и
  по-прежнему падает без него.
- Якоря: `kompot-preview/src/commonMain/kotlin/io/github/youndie/kompot/preview/KompotPreview.kt`,
  `kompot-preview/src/desktopTest/.../KompotPreviewTest.kt`, `kompot-standard/.../Pagination.kt`.

## Итог

В `kompot-preview`: `KompotBodyShape` (`FORM`/`SCREEN`/`COMPONENT`, каждая со своим `screenProperty`),
`kompotBodyShape(root)`, `Json.decodeKompotBody(body): KompotDecodedBody(screen, schema, realtimeTopic)`,
параметр `pageLoader: KompotPageLoader? = null`. Пять тестов в `DecodeKompotBodyTest`; три аудита
публикации зелёные.

- **Вынесено не только «декодирование», но и сам вопрос о форме.** Студии нужен корень тела как
  **JSON-подузел** (дерево строится из текста, а не из объектов), а не декодированный компонент, —
  `decodeKompotBody` ей для этого не годится. Поэтому фактом протокола сделан `kompotBodyShape`:
  превью решает им, что декодировать, студия — где корень дерева. Без этого копия из трёх ветвей
  осталась бы в студии и разошлась бы на первом же четвёртом конверте — молча, нарисовав дерево
  конверта вместо экрана.
- **`realtimeTopic` до сих пор терялся.** У `decodeBody` не было поля, куда его положить; теперь есть,
  и студия сможет подписаться на канал, не декодируя тело второй раз.
- **Порядок ветвей — это правило, а не случайность:** у формы в теле есть **и** `schema`, **и**
  `screen`, так что прочитанный наоборот он превратил бы каждую форму в screen-ответ и потерял схему.
  Тест утверждает именно это.
- **Дефолт `pageLoader = null` сохранён** в превью и повторён в `KompotStudioConfig`: тихо подсунутая
  пустая страница — тот же класс ошибки, что серый плейсхолдер, записанный в голден. Тесты проверяют
  **обе** половины (падает без загрузчика, рисует с ним) — по отдельности каждая проходит и у
  реализации, которая параметр игнорирует.
- `kompot-preview` и `kompot-studio` объявили `api(projects.kompotStandard)`: `KompotPageLoader`
  теперь в их публичных сигнатурах.
