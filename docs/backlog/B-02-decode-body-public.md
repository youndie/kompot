---
id: B-02
title: "kompot-preview: публичный decodeKompotBody и параметр pageLoader"
status: open
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
