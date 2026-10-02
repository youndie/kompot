#!/usr/bin/env python3
"""
Проверяет, что правка того, что отдаёт плагин Claude Code, поднимает его версию.

    python3 docs/scripts/plugin_version.py                      # против origin/main
    python3 docs/scripts/plugin_version.py --base <rev>          # против чего сравнивать (CI)

Зачем. Репозиторий — плагин `kompot` (`.claude-plugin/plugin.json`): скилл `skills/kompot-layout`
и утилиты `tools/canvas`, которые скилл велит запускать. Установка сравнивает строку `version` и
больше ничего: `claude plugin update` при той же версии отвечает «уже последняя», сколько бы
коммитов ни ушло в main. Правка скилла под старым номером не доходит ни до одной установки — и
ничем себя не выдаёт: в репозитории она есть, у потребителя её нет. В соседнем репозитории скиллов
так подряд вышли шесть изменений под одним номером, и правило «не забывать бамп» продержалось два
дня, пока не стало проверкой.

**Граница.** Отдаётся весь репозиторий, но версия обязана расти только от правки `skills/` и
`tools/canvas/`: остальное скилл не читает, и бамп плагина на каждую правку модуля был бы шумом,
который научились бы пропускать. Проверяется факт подъёма номера, а не то, насколько он поднят.
"""
import json
import os
import re
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
MANIFEST = ".claude-plugin/plugin.json"
SHIPPED = ("skills/", "tools/canvas/")


def git(*args, check=True):
    done = subprocess.run(("git",) + args, cwd=ROOT, capture_output=True, text=True)
    if check and done.returncode != 0:
        raise SystemExit(f"git {' '.join(args)} упал: {done.stderr.strip()}")
    return done


def dotted(version):
    if not re.fullmatch(r"\d+(\.\d+)*", version or ""):
        raise ValueError(f"версия {version!r} — не числа через точку")
    return tuple(int(x) for x in version.split("."))


def verdict(changed, old, new):
    """None, если всё в порядке, иначе текст отказа. old — None, если у базы плагина ещё нет."""
    shipped = [f for f in changed if f.startswith(SHIPPED)]
    if not shipped or old is None:
        return None
    try:
        if dotted(new) > dotted(old):
            return None
    except ValueError as e:
        return f"{MANIFEST}: {e}"
    return (
        f"{MANIFEST}: версия {new} не выше {old} у базы, а отдаваемых файлов изменено {len(shipped)} "
        f"(первый: {shipped[0]}) — `claude plugin update` этой правки не увидит. Подними `version`."
    )


def selftest():
    """Каждое правило показано срабатывающим: проверка, которая не может упасть, не проверка."""
    cases = [
        (["skills/kompot-layout/SKILL.md"], "0.1.0", "0.1.0", True),
        (["skills/kompot-layout/SKILL.md"], "0.1.0", "0.1.1", False),
        (["tools/canvas/canvas_diff.py"], "0.1.0", "0.1.0", True),
        (["skills/kompot-layout/SKILL.md"], "0.2.0", "0.1.9", True),
        (["skills/kompot-layout/SKILL.md"], "0.1.0", "0.1.0-rc", True),
        (["kompot-core/src/commonMain/kotlin/A.kt", "README.md"], "0.1.0", "0.1.0", False),
        (["skills/kompot-layout/SKILL.md"], None, "0.1.0", False),
        # Утилита вне tools/canvas скиллом не читается.
        (["tools/api-metadata-audit.py"], "0.1.0", "0.1.0", False),
    ]
    for changed, old, new, fails in cases:
        if (verdict(changed, old, new) is not None) != fails:
            raise SystemExit(f"самопроверка не сработала на {changed}, {old} → {new}")


def main():
    selftest()

    base = "origin/main"
    if "--base" in sys.argv:
        base = sys.argv[sys.argv.index("--base") + 1]

    # Неразрешимая база — это не «правок нет», а проверка, не нашедшая своего предмета.
    if git("rev-parse", "--verify", "--quiet", f"{base}^{{commit}}", check=False).returncode != 0:
        print(f"база {base} не разрешается в коммит — сравнивать не с чем", file=sys.stderr)
        return 2

    merge_base = git("merge-base", base, "HEAD").stdout.strip()
    # --no-renames: файл, уехавший из skills/, — удаление из того, что отдаётся, и тоже требует бампа.
    changed = git("diff", "--no-renames", "--name-only", f"{merge_base}..HEAD").stdout.split()

    before = git("show", f"{merge_base}:{MANIFEST}", check=False)
    old = json.loads(before.stdout)["version"] if before.returncode == 0 else None
    with open(os.path.join(ROOT, MANIFEST)) as f:
        new = json.load(f).get("version")

    problem = verdict(changed, old, new)
    if problem:
        print(problem, file=sys.stderr)
        return 1
    print(f"plugin: версия {new}, у базы {old or 'плагина нет'}; отдаваемых файлов изменено "
          f"{sum(f.startswith(SHIPPED) for f in changed)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
