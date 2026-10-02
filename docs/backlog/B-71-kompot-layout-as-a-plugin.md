---
id: B-71
title: "Скилл kompot-layout ставится плагином, а не симлинком с одной машины"
status: done
priority: P2
size: XS
---

# B-71 — Скилл kompot-layout ставится плагином

`skills/kompot-layout/SKILL.md` лежит в репозитории с 2 сентября, но до Claude доходил только через
симлинк из личного каталога скиллов на рабочую копию kompot. У потребителей тулкита, которые
верстают на нём экраны, скилла не было: поставить его было нечем, кроме как склонировать kompot и
повторить симлинк руками. Заодно скилл ссылается на `tools/canvas` как на «kompot's `tools/canvas`»,
и в чужом проекте эти утилиты было негде взять.

- **Плагином делается весь репозиторий** (`.claude-plugin/plugin.json` в корне), а не каталог скилла:
  тогда `tools/canvas`, которые скилл велит запускать, приезжают в установку вместе с ним и лежат в
  `${CLAUDE_PLUGIN_ROOT}/tools/canvas`. Ключа `skills` в манифесте нет: `skills/` — каталог, который
  Claude Code сканирует по умолчанию, и ключ его только дополнил бы (`claude plugin details`
  показывает один скилл и с ключом, и без).
- **Каталог — kotlin-skills**, запись с источником `github` на `youndie/kompot`. Свой маркетплейс в
  kompot не заводится: второй каталог ради одного плагина — второе место, которое надо помнить.
- **Версия закреплена (0.1.0) и растёт проверкой**, `docs/scripts/plugin_version.py` в CI: правка
  `skills/` или `tools/canvas/` без подъёма `version` красная. Без версии установка шла бы за каждым
  коммитом main, но и правка любого модуля считалась бы новой версией скилла.

- AC: `claude plugin install kompot@kotlin-skills` → в `claude plugin details kompot` один скилл
  `kompot-layout`; правка `SKILL.md` без подъёма версии — красный шаг CI с именем файла.
- Якоря: `.claude-plugin/plugin.json`, `docs/scripts/plugin_version.py`, `.github/workflows/build.yaml`,
  README «Laying out a screen».
