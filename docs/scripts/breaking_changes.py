#!/usr/bin/env python3
"""
Проверяет, что объявленное ломающее изменение попало в UPGRADING.md.

    python3 docs/scripts/breaking_changes.py                      # против origin/main
    python3 docs/scripts/breaking_changes.py --base <rev>          # против чего сравнивать (CI)

Зачем. Провод и Kotlin-API ломаются по-разному. Ломающее изменение провода тихое — старый клиент
встречает дыру на экране, — и у него есть журнал (§13 SPEC.md) и машинная проверка схем (§15).
Ломающее изменение Kotlin-API громкое: сборка потребителя останавливается с ошибкой типов. Но
ошибка называет тип и молчит про причину, а причина и «что писать вместо» живут в сообщении
коммита, которого потребитель не читает.

Отсюда UPGRADING.md — и отсюда же эта проверка: журнал, который заводят и забывают обновлять, хуже
отсутствующего. Неполный журнал выглядит полным, и потребитель, не нашедший в нём своей поломки,
решает, что сломался сам.

**Граница.** Ловится только ОБЪЯВЛЕННОЕ: `!` после scope или футер `BREAKING CHANGE:` — то, что
требует Conventional Commits и чего в этом репозитории держится хук. Изменение, которое ломает
потребителя молча, не ловится ничем: см. B-39 про отсутствие проверки бинарной совместимости. И
проверяется факт правки файла, а не то, что в ней написано про нужный модуль, — второе не проверить
без разбора естественного языка.
"""
import os
import re
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
JOURNAL = "UPGRADING.md"

# Conventional Commits: `feat(studio)!: …` в заголовке или `BREAKING CHANGE: …` футером. Хук
# ~/.claude/hooks/conventional-commit.py пропускает оба, поэтому оба здесь.
SUBJECT = re.compile(r"^[a-z]+(\([^)]*\))?!:", re.M)
FOOTER = re.compile(r"^BREAKING[ -]CHANGE:", re.M)


def git(*args):
    done = subprocess.run(("git",) + args, cwd=ROOT, capture_output=True, text=True)
    if done.returncode != 0:
        raise SystemExit(f"git {' '.join(args)} упал: {done.stderr.strip()}")
    return done.stdout


def declares_break(message):
    return bool(SUBJECT.search(message) or FOOTER.search(message))


def selftest():
    """Проверка, что проверка срабатывает — на обеих формах и на отрицательном контроле.

    Без неё сломанная регулярка даёт ноль ломающих коммитов, то есть зелёный прогон с тем же
    выводом, что и у ветки, в которой ничего не ломали.
    """
    cases = [
        ("feat(studio)!: derive a sample wire name\n\nbody", True),
        ("refactor: move things\n\nBREAKING CHANGE: KompotStudioConfig.samples changed\n", True),
        ("feat(studio): add a panel\n\nbody without a footer\n", False),
        # Слово в прозе — не объявление: футер стоит в начале строки и с двоеточием.
        ("docs: explain what a breaking change is\n\nprose mentioning BREAKING CHANGE inline\n", False),
    ]
    for message, expected in cases:
        if declares_break(message) != expected:
            raise SystemExit(f"самопроверка не сработала на:\n{message}")


def main():
    selftest()

    base = "origin/main"
    if "--base" in sys.argv:
        base = sys.argv[sys.argv.index("--base") + 1]

    # Неразрешимая база — это не «ломающих коммитов нет», а проверка, не нашедшая своего предмета.
    resolved = subprocess.run(
        ("git", "rev-parse", "--verify", "--quiet", f"{base}^{{commit}}"),
        cwd=ROOT, capture_output=True, text=True,
    )
    if resolved.returncode != 0:
        print(f"база {base} не разрешается в коммит — сравнивать не с чем", file=sys.stderr)
        return 2

    merge_base = git("merge-base", base, "HEAD").strip()
    span = f"{merge_base}..HEAD"

    commits = [c for c in git("log", "--format=%H", span).split() if c]
    if not commits:
        print(f"breaking: в {span} нет коммитов — проверять нечего")
        return 0

    declared = [c for c in commits if declares_break(git("log", "-1", "--format=%B", c))]
    if not declared:
        print(f"breaking: коммитов в диапазоне — {len(commits)}, объявленных ломающих нет")
        return 0

    touched = git("diff", "--name-only", span).split()
    if JOURNAL in touched:
        print(f"breaking: объявленных ломающих коммитов — {len(declared)}, {JOURNAL} правился")
        return 0

    subjects = "\n  ".join(git("log", "-1", "--format=%s", c).strip() for c in declared)
    print(
        f"эти коммиты объявляют ломающее изменение, а {JOURNAL} в том же диапазоне не менялся:\n"
        f"  {subjects}\n\n"
        f"Потребитель узнает о поломке от своего компилятора, и это единственное место, где написано, "
        f"почему и что писать вместо. Добавь запись — или сними `!`/футер, если изменение ничего не "
        f"ломает.",
        file=sys.stderr,
    )
    return 1


if __name__ == "__main__":
    sys.exit(main())
