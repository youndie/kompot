---
id: B-74
title: "DSL не достаёт до полей провода: heading, accessibilityLabel и action недоступны из kompotScreen (#205)"
status: done
priority: P2
size: S
---

# B-74 — DSL не достаёт до полей провода

[#205](https://github.com/youndie/kompot/issues/205), найдено при переходе shashki на 0.39
([shashki#43](https://github.com/youndie/shashki/issues/43)): её сервер пишет экраны через
`kompotScreen { … }`.

Поля доступности 0.38 ([B-55](B-55-accessibility-of-actionable-containers.md)) дошли до data class,
схемы и рендереров — и не дошли до DSL (`kompot-standard/.../Dsl.kt`): у `text` нет `heading`, у
`button` — `accessibilityLabel`, у `RowBuilder`/`ColumnBuilder` — ни подписи, ни `action` (§4.6,
старше 0.38). Сервер, написанный так, как пишут README и примеры, заголовок поставить не может; выход
один — `addComponent(TextComponent(…, heading = true))` с придуманным руками `id`, который иначе дал
бы `nextChildPath()`. Сборка зелёная, экран рисуется, у читалки просто нет заголовков.

- **Решение:** параметры с умолчанием у функций и сеттеры у построителей стеков — в стиле
  `alignment`/`arrangement`/`scrollable`. Новые параметры встают **перед** `modifierBlock`, чтобы
  вызов с хвостовой лямбдой значил то же, что раньше.
- **Сторож:** тест, который спрашивает сам провод, а не список, который ведут руками. Для каждого
  компонента, зарегистрированного сгенерированным модулем сериализаторов, свидетель строится только
  через DSL, каждое поле — не по умолчанию, и кодируется без умолчаний: поле, до которого DSL не
  достаёт, остаётся по умолчанию и пропадает из JSON. Новое поле data class попадает в дескриптор, и
  тест краснеет, пока DSL его не научится ставить; новый тип без вызова DSL — тоже (у него нет
  свидетеля).
- AC: из DSL ставятся все поля провода стандартных компонентов; сторож красный на старом DSL и на
  мутациях; вызовы, написанные до этого, компилируются без правки.
- Якоря: `kompot-standard/.../Dsl.kt`, `DslReachesTheWireTest`, `UPGRADING.md`.

## Находки

### Итерация 1 — 2026-10-05

**Дыр оказалось больше, чем в issue.** Сторож на старом DSL назвал двенадцать полей:
`button.accessibilityLabel`, `button.variant`, `column.accessibilityLabel`, `column.action`,
`divider.modifiers`, `expandable.modifiers`, `row.accessibilityLabel`, `row.action`, `text.ellipsis`,
`text.heading`, `text.maxLines`, `text.spans`. Закрыты все: `variant` у кнопки и модификаторы у
`divider` и `expandable` — того же рода (поле на проводе, до которого DSL не дотягивается). Строка
таблицы (`TableRow`) проверена отдельно — её строит свой построитель; там всё было на месте.

**Мутации:** убран параметр `accessibilityLabel` у `button` — сторож назвал ровно
`button.accessibilityLabel`; добавлено поле `probe` в `TextComponent` без поддержки в DSL — назвал
`text.probe`.

**Бинарная поломка объявлена.** Новые параметры меняют JVM-сигнатуры `text`, `button`, `divider`,
`expandable`; исходники вызовов не меняются (кроме двух краёв: `modifierBlock` позиционно после
именованных и позиционный `header` у `expandable`). В репозитории так всегда и делали — запись
«binary only» в `UPGRADING.md` и `!`, скрытых перегрузок нет ни одной.

**Экспорт DSL в студии** печатал `modifierBlock` у `text` и `button` позиционно после именованных
аргументов — с новыми параметрами такой черновик перестал бы компилироваться. Теперь печатает по
имени (тест `DslExportTest`, красный на старом экспортёре). Что экспорт до сих пор теряет поля
`heading`, `accessibilityLabel`, `action` и т. п., — отдельная задача
([B-78](B-78-dsl-export-keeps-every-wire-field.md)), здесь не тронута.

**Что снимает shashki:** заголовки чека и промо — `text(…, heading = true)` вместо
`addComponent(TextComponent(…, heading = true))` с придуманным `id`.
