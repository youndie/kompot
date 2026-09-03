#!/usr/bin/env python3
"""
Собирает индекс задач в docs/backlog.md из frontmatter файлов docs/backlog/B-*.md.

    python3 docs/scripts/backlog_index.py                    # переписать индекс
    python3 docs/scripts/backlog_index.py --check            # только проверить (CI)
    python3 docs/scripts/backlog_index.py --against origin/main   # id не занят на ветке (CI на PR)

Перенесён из репозитория docs (scripts/backlog_index.py) вместе с шаблоном задачи; отличается
только таблицей этапов. Индекс генерируется, а не ведётся руками: статус задачи меняется в её
собственном файле, и единственный способ не дать индексу разъехаться с задачами — не хранить
статус дважды. Правится всё, что между маркерами BEGIN/END INDEX; остальной текст backlog.md —
ручной.
"""
import os
import re
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
INDEX = os.path.join(ROOT, "backlog.md")
ITEMS = os.path.join(ROOT, "backlog")

BEGIN = "<!-- BEGIN INDEX (генерируется docs/scripts/backlog_index.py — руками не править) -->"
END = "<!-- END INDEX -->"

# Этап — свойство задачи, а не папка: он меняется, а путь к файлу должен пережить это.
# Описание этапов — таблица «Этапы» в backlog.md.
STAGES = [
    ("upstream", "Швы в toolkit'е"),
    ("spike", "Spike"),
    ("v1-viewer", "v1 — смотреть и линтить"),
    ("v2-editor", "v2 — править и снимать"),
    ("v3-builder", "v3 — собирать"),
    (None, "Без этапа"),
]

MARK = {"open": "`[ ]`", "wip": "`[~]`", "done": "`[x]`",
        "question": "`[?]`", "dropped": "`[—]`"}

PRIO_ORDER = {"P0": 0, "P1": 1, "P2": 2, "P3": 3, "infra": 4, None: 5}


def parse(path):
    text = open(path, encoding="utf-8").read()
    if not text.startswith("---\n"):
        sys.exit(f"{os.path.basename(path)}: нет frontmatter")
    raw = text.split("---\n", 2)[1]

    fm = {}
    for line in raw.split("\n"):
        if not line.strip():
            continue
        if ":" not in line:
            sys.exit(f"{os.path.basename(path)}: не разобрать строку frontmatter: {line}")
        key, _, value = line.partition(":")
        value = value.strip()
        if value.startswith("[") and value.endswith("]"):
            value = [v.strip() for v in value[1:-1].split(",") if v.strip()]
        else:
            value = value.strip('"')
        fm[key.strip()] = value

    name = os.path.basename(path)
    for key in ("id", "title", "status"):
        if key not in fm:
            sys.exit(f"{name}: нет обязательного поля {key}")
    if not name.startswith(fm["id"] + "-"):
        sys.exit(f"{name}: имя файла не совпадает с id {fm['id']}")
    if fm["status"] not in MARK:
        sys.exit(f"{name}: неизвестный статус {fm['status']}")
    if fm.get("stage") is not None and fm["stage"] not in {s for s, _ in STAGES}:
        sys.exit(f"{name}: неизвестный этап {fm['stage']} — см. таблицу этапов в backlog.md")

    fm["file"] = f"backlog/{name}"
    return fm


def link(item):
    return f'[{item["id"]}]({item["file"]})'


def render(items):
    out = [BEGIN, ""]

    live = [i for i in items if i["status"] != "done"]
    live.sort(key=lambda i: (PRIO_ORDER.get(i.get("priority"), 5), i["id"]))

    out += [f"## Открыто ({len(live)})", ""]
    if live:
        out += ["| Задача | | Этап | Приоритет | Размер | Ждёт |", "|---|---|---|---|---|---|"]
        for i in live:
            blockers = ", ".join(i.get("blocked_by", [])) or "—"
            out.append(
                f'| {link(i)} {MARK[i["status"]]} | {i["title"]} | {i.get("stage") or "—"} '
                f'| {i.get("priority", "—")} | {i.get("size", "—")} | {blockers} |'
            )
    else:
        out.append("Открытых задач нет.")

    done = [i for i in items if i["status"] == "done"]
    out += ["", f"## Сделано ({len(done)})", ""]
    for slug, title in STAGES:
        chunk = sorted([i for i in done if i.get("stage") == slug], key=lambda i: i["id"])
        if not chunk:
            continue
        out += [f"**{title}**", ""]
        out += [f'- {link(i)} — {i["title"]}' for i in chunk]
        out.append("")

    out += [END]
    return "\n".join(out).rstrip("\n") + "\n"


LINK = re.compile(r"\]\((?!https?:|#)([^)#]+\.md)(?:#[^)]*)?\)")


def check_links(files):
    """
    Ссылки задач на доки — относительные, а файлы задач лежат на уровень ниже корня docs.
    Пропущенный `../` не ломает ничего локально и не ловится ни одним тестом: ссылка просто
    отдаёт 404 на GitHub, и замечает это читатель, а не автор.
    """
    broken = []
    for name in files:
        path = os.path.join(ITEMS, name)
        with open(path, encoding="utf-8") as f:
            for target in LINK.findall(f.read()):
                if not os.path.exists(os.path.normpath(os.path.join(ITEMS, target))):
                    broken.append(f"  backlog/{name} → {target}")
    if broken:
        sys.exit("битые ссылки в задачах:\n" + "\n".join(sorted(set(broken))))


def check_against(ref):
    """
    Ловит занятый номер: id, свободный в момент создания ветки, но занятый другой задачей,
    которую успели влить раньше. Обычная проверка дубликатов этого не видит — в каждой ветке по
    отдельности дубликата нет, он появляется только после второго мержа.

    Сравниваем по имени файла, а не по frontmatter: одинаковый номер с разным slug — это и есть
    занятый номер, а совпадение id с именем файла проверяется отдельно в parse().
    """
    try:
        out = subprocess.run(
            ["git", "ls-tree", "-r", "--name-only", ref, "--", "backlog/"],
            cwd=ROOT,
            capture_output=True,
            text=True,
            check=True,
        ).stdout
    except (subprocess.CalledProcessError, FileNotFoundError) as e:
        sys.exit(f"не удалось прочитать {ref}: {e}")

    theirs = {}
    their_slugs = {}
    for path in out.splitlines():
        name = os.path.basename(path)
        m = re.match(r"^(B-\d+)-(.*\.md)$", name)
        if m:
            theirs[m.group(1)] = name
            their_slugs[m.group(2)] = name

    ours = sorted(f for f in os.listdir(ITEMS) if re.match(r"^B-\d+-.*\.md$", f))

    taken = []
    for name in ours:
        num = re.match(r"^(B-\d+)-", name).group(1)
        other = theirs.get(num)
        if other and other != name:
            taken.append(f"  {num}: у нас {name}, на {ref} уже {other}")

    if taken:
        sys.exit(
            f"номер задачи уже занят на {ref} — возьмите свободный и переименуйте файл:\n"
            + "\n".join(taken)
        )

    # Зеркальный случай: тот же slug под другим номером — задачу перенумеровали в одной ветке,
    # а вторая принесла её же под прежним номером, и обе проверки по номеру промолчали.
    renamed = []
    for name in ours:
        slug = name.split("-", 2)[2]
        other = their_slugs.get(slug)
        if other and other != name:
            renamed.append(f"  {slug}: у нас {name}, на {ref} уже {other}")

    if renamed:
        sys.exit(
            f"эта задача уже есть на {ref} под другим номером — заберите тамошний номер, "
            f"иначе после мержа она будет лежать дважды:\n" + "\n".join(renamed)
        )


def _utf8_stdout():
    """Консоль Windows по умолчанию cp1252 и роняет скрипт на первом же кириллическом
    символе — до того, как он успевает сообщить результат."""
    for stream in (sys.stdout, sys.stderr):
        if hasattr(stream, "reconfigure"):
            try:
                stream.reconfigure(encoding="utf-8", errors="replace")
            except (ValueError, OSError):
                pass


def main():
    _utf8_stdout()
    check = "--check" in sys.argv

    if "--against" in sys.argv:
        check_against(sys.argv[sys.argv.index("--against") + 1])
        return

    files = sorted(f for f in os.listdir(ITEMS) if re.match(r"^B-\d+-.*\.md$", f))
    items = [parse(os.path.join(ITEMS, f)) for f in files]

    ids = [i["id"] for i in items]
    dupes = {i for i in ids if ids.count(i) > 1}
    if dupes:
        sys.exit("дублирующиеся id: " + ", ".join(sorted(dupes)))

    # Один и тот же текст под двумя номерами: `id` у файлов разные, и каждый по отдельности
    # законен. Появляется, когда задачу перенумеровали в одной ветке, а вливали параллельно.
    by_slug = {}
    for f in files:
        by_slug.setdefault(f.split("-", 2)[2], []).append(f)
    slug_dupes = {slug: fs for slug, fs in by_slug.items() if len(fs) > 1}
    if slug_dupes:
        lines = [f"  {slug}: " + ", ".join(sorted(fs)) for slug, fs in sorted(slug_dupes.items())]
        sys.exit("одна задача под разными номерами:\n" + "\n".join(lines))

    known = set(ids)
    for i in items:
        for dep in i.get("blocked_by", []):
            if dep not in known:
                sys.exit(f'{i["id"]}: blocked_by указывает на несуществующую {dep}')

    check_links(files)

    text = open(INDEX, encoding="utf-8").read()
    if BEGIN not in text or END not in text:
        sys.exit("в backlog.md нет маркеров BEGIN/END INDEX")

    head, rest = text.split(BEGIN, 1)
    _, tail = rest.split(END, 1)
    updated = head + render(items).rstrip("\n") + tail

    if check:
        if updated != text:
            sys.exit("индекс в backlog.md устарел — запусти docs/scripts/backlog_index.py")
        print(f"индекс актуален: {len(items)} задач")
        return

    open(INDEX, "w", encoding="utf-8").write(updated)
    print(f"индекс обновлён: {len(items)} задач")


if __name__ == "__main__":
    main()
