---
id: B-64
title: "Продакшн-компиляция wasm падает по памяти — на Linux-машине каждый раз, на CI однажды"
status: open
priority: infra
size: S
stage: release-0.38
---

# B-64 — Продакшн-компиляция wasm падает по памяти

`compileProductionExecutableKotlinWasmJs` у `kompot-playground` и `kompot-preview` падает с
`OutOfMemoryError: GC overhead limit exceeded` («Not enough memory to run compilation») при полном
`./gradlew build`. На Linux-машине (16 ГБ) — почти при каждой полной сборке в этом релизе, лечится
повтором. На CI — впервые 2026-09-25, на PR #179 (run 36112190400, первая попытка); повтор прошёл.
С B-49 в плейграунде больше кода, и запас кончается у всех.

Сейчас `kotlin.daemon.jvmargs=-Xmx3072M` и `org.gradle.jvmargs=-Xmx3072M` (`gradle.properties`) — на
оба процесса, которые при полной сборке живут одновременно, и параллельно с ними — воркеры тестов.

- **Решение — выяснить, чего не хватает, прежде чем поднимать число:** замерить пик демона Kotlin на
  этой задаче (`-Xlog:gc` или `jcmd GC.heap_info`), затем поднять `kotlin.daemon.jvmargs` ровно до
  пика с запасом, и проверить, что на раннере CI сумма демонов с воркерами влезает (объём памяти
  раннера — снять в самом прогоне, `free -m` шагом перед сборкой, а не взять из документации). Альтернатива — компилировать wasm вне демона
  (`kotlin.compiler.execution.strategy=in-process`) — меняет, где живёт память, а не сколько её.
- Не делаем: отключение продакшн-сборки wasm в `check` — это проверка того, что страница вообще
  собирается.

- AC: три полных `./gradlew build` подряд на Linux-машине — без падения по памяти; CI на PR этой
  задачи — зелёный с первой попытки; в `gradle.properties` рядом с числом записано, откуда оно.
- Якоря: `gradle.properties`, `kompot-playground/build.gradle.kts`, `.github/workflows/build.yaml`.
