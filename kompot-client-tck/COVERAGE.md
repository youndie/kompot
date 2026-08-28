# Покрытие §9

Сгенерировано `ClauseCoverageTest` по правилам §9 спеки и по случаям корпуса.
Правило без случая — не дефект: корпус неполон и говорит об этом здесь, а не молчит.

| Правило | Случаи | Если не корпус — то кто |
|---|---|---|
| `9.1.1` | — | сервер: согласованность конверта |
| `9.1.2` | — | сервер: форма ответа |
| `9.1.3` | — | сервер: форма ответа |
| `9.2.1` | — | сервер: `kompot-tck`, связность схемы |
| `9.2.2` | — | сервер: `kompot-tck`, связность схемы |
| `9.2.3` | — | сервер: `kompot-tck`, связность схемы |
| `9.2.4` | — | сервер: `kompot-tck`, связность схемы |
| `9.3.1` | `9.3-required-if-follows-a-neighbour` |  |
| `9.4.1` | `9.4-not-equals-hides-a-field-while-a-neighbour-differs` |  |
| `9.4.2` | `9.4-a-hidden-field-leaves-the-payload`, `9.4-a-hidden-field-is-not-validated` |  |
| `9.4.3` | `9.4-an-error-on-a-field-that-then-hides` |  |
| `9.4.4` | `9.4-a-field-returns-when-its-condition-holds-again` |  |
| `9.5.1` | `9.5-an-amount-within-a-balance-passes` |  |
| `9.5.2` | — | сервер: тело ошибки |
| `9.5.3` | `9.5-regex-passes-an-empty-value`, `9.5-required-before-regex-blocks-an-empty-value` |  |
| `9.5.4` | `9.5-blur-raises-the-error-the-rule-carries` |  |
| `9.5.5` | `9.5-validation-waits-for-blur`, `9.5-blur-raises-the-error-the-rule-carries` |  |
| `9.5.6` | `9.5-required-before-regex-blocks-an-empty-value`, `9.3-required-if-follows-a-neighbour`, `9.5-an-amount-over-a-balance-is-refused` |  |
| `9.6.5` | — | отрисовка: тест рендерера `BoundReadOnlyFieldTest`, не корпус |
| `9.6.6` | — | сервер: `kompot-tck`, проверка `patch` |
| `9.6.1` | `9.6-a-patch-is-requested-once-by-the-field-that-triggers-it` |  |
| `9.6.2` | `9.6-a-field-that-triggers-no-patch-sends-nothing` |  |
| `9.6.3` | `9.6-a-patch-replaces-a-value`, `9.6-a-patch-clears-what-it-names` |  |
| `9.6.4` | — | сервер: патч против нового конверта |
| `9.7.10` | — | отрисовка: тесты `AmountInputRendererTest`, не корпус |
| `9.7.11` | — | отрисовка: тест `VisualFormattingTest`, не корпус |
| `9.7.1` | `9.6-a-patch-replaces-a-value`, `9.7-an-entity-value-keeps-its-metadata` |  |
| `9.7.2` | `9.7-an-entity-value-keeps-its-metadata` |  |
| `9.7.3` | `9.5-an-amount-over-a-balance-is-refused` |  |
| `9.7.4` | `9.7-an-entity-value-keeps-its-metadata` |  |
| `9.7.5` | `9.5-an-amount-within-a-balance-passes` |  |
| `9.7.6` | `9.7-an-initial-value-fills-an-untouched-field` |  |
| `9.7.7` | — | адаптер не принимает значения клиента при загрузке |
| `9.7.8` | — | правило для плагина полей, на проводе не наблюдается |
| `9.7.9` | — | отрисовка: снимки, а не корпус |
| `9.8.1` | — | структура схемы, не решение клиента |
| `9.8.2` | — | адаптер не умеет источники данных |

Корпус держит 19 из 37; ещё 18 держит не он.
Остальные — 0 — не держит никто.
