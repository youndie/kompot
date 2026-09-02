---
id: B-01
title: "Одна линия Compose: kompot, viddik, Jewel в libs.versions.toml"
status: open
priority: infra
size: S
stage: upstream
epic: research-studio
---

# B-01 — Одна линия Compose: kompot, viddik, Jewel в `libs.versions.toml`

kompot собран против Compose Multiplatform 1.11.1 и material3 1.11.0-alpha07
(`gradle/libs.versions.toml`); viddik в каталоге — 0.1.1.8, последняя версия линии 1.11, притом
подключён руками, без gradle-плагина (`kompot-ds-material-compose/build.gradle.kts`, `kspDesktopTest`
+ `srcDir("build/generated/ksp/…")`). viddik 0.3.x уже требует CMP 1.12; Jewel 0.38–0.40 собран
против 1.11.x, 0.41 — против 1.12. Смешение линий не падает при резолве — оно падает в рантайме
`NoSuchMethodError` на первом кадре, внутри рендерера. Студия добавляет в этот узел третью
библиотеку, и версия у неё должна выбираться там же, где у двух других.

- **Решение: Jewel входит в `libs.versions.toml` рядом с `viddik`, с комментарием «линия CMP 1.11;
  двигать только вместе с `compose-multiplatform`».** Потому что единственная защита от
  расхождения линий — одно место, где они перечислены, и один коммит, который их двигает.
- **Jewel — `jewel-int-ui-standalone` с точной версией `0.40.x-262.*`**, исключая
  `org.jetbrains.compose.material` (Material 2) по инструкции Jewel; material3 остаётся — на нём
  рендереры.
- Альтернатива — держать студию в отдельном репозитории со своим каталогом: тогда линия студии и
  линия toolkit'а расходятся тихо, как уже разошлись appframe (viddik 0.1.2.12) и kompot (0.1.1.8).
- Не делаем: не переводим kompot на 1.12 в этой задаче и не подключаем viddik-плагин — это
  отдельные изменения с собственной ценой.

- AC: `./gradlew :kompot-studio:run` (после B-08) стартует на JBR без `NoSuchMethodError`;
  `libs.versions.toml` содержит `jewel` с комментарием о линии; `./gradlew build` зелёный.
- Якоря: `gradle/libs.versions.toml`, `kompot-ds-material-compose/build.gradle.kts`,
  `skills/kompot-layout/SKILL.md` (п. 6 «The toolkit's Compose line»).
