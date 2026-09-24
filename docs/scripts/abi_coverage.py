#!/usr/bin/env python3
"""
Проверяет, что у каждого публикуемого модуля включена проверка ABI и лежит эталонный дамп.

    python3 docs/scripts/abi_coverage.py

Зачем. `checkKotlinAbi` ловит молчаливую поломку Kotlin-API — но только там, где включён, а
включается он двумя строками в `build.gradle.kts` модуля. Строк 36, и тридцать седьмой модуль
заведут без них: сторож, которого нет, выглядит как сторож, которому нечего сказать. Проверка
сравнивает два множества, и оба читает из репозитория, а не из списка.

Обновить дамп после осознанного изменения API:

    ./gradlew updateKotlinAbi          # все модули
    ./gradlew :kompot-client:updateKotlinAbi

**Дампы сняты на Linux, потому что на нём же их проверяет CI.** Для модулей этой сборки вывод
совпал на macOS и на Linux (klib-таргеты кросс-компилируются), но совпадение проверено, а не
обещано: если после обновления на другой машине `checkKotlinAbi` краснеет на CI, смотреть надо на
строку `// Targets:` в klib-дампе, а не на свой код.

**Android-таргет — с Kotlin 2.4.20.** До него KGP клал только `jvm`-половину
(`api/jvm/<module>.api`) и klib, и API, доступное лишь android-варианту, не охранялось ничем. 2.4.20
дампит и его (`api/android/<module>.api`) — граница, записанная здесь при включении проверки, закрылась
обновлением компилятора, а не конфигурацией (B-41).
"""
import os
import pathlib
import re
import sys

ROOT = pathlib.Path(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))

# ПРИМЕНЁННЫЙ плагин, а не упоминание. Первая версия этой проверки искала подстроку по всему файлу и
# записала в публикуемые `kompot-playground` — у которого в комментарии написано, почему
# `sborka.publish` там ОТСУТСТВУЕТ. Модуль успел получить дамп, которого ему не нужно.
PUBLISH = re.compile(r'^\s*id\("io\.github\.youndie\.sborka\.publish"\)', re.M)
KOTLIN = re.compile(r'^\s*kotlin\("(multiplatform|jvm)"\)', re.M)
ABI = re.compile(r"^\s*abiValidation\b", re.M)

# Публикуется и Kotlin-кода не содержит вовсе: `java-platform`, один pom с ограничениями версий.
# Названо здесь, а не определено по факту, — и с обратной проверкой ниже, потому что исключение,
# которое никто не перечитывает, это способ для модуля выйти из-под сторожа навсегда.
NO_KOTLIN_AT_ALL = {"kompot-bom"}


def modules():
    return sorted(p.parent for p in ROOT.glob("*/build.gradle.kts"))


def main():
    published, enabled, dumped, kotlin = set(), set(), set(), set()
    for module in modules():
        text = (module / "build.gradle.kts").read_text()
        name = module.name
        if PUBLISH.search(text):
            published.add(name)
        if ABI.search(text):
            enabled.add(name)
        if KOTLIN.search(text):
            kotlin.add(name)
        if list(module.glob("api/**/*.api")):
            dumped.add(name)

    problems = []

    # Сторож холостого прогона: ноль публикуемых модулей — это сломанный разбор, а не репозиторий
    # без библиотек, и все сравнения ниже прошли бы молча.
    if len(published) < 10:
        raise SystemExit(f"публикуемых модулей нашлось {len(published)} — столько не бывает, сломан разбор")

    for name in sorted(NO_KOTLIN_AT_ALL & kotlin):
        problems.append(
            f"{name} назван модулем без Kotlin, а Kotlin-плагин применяет — значит у него есть "
            f"публичное API и оно ничем не охраняется"
        )

    for name in sorted(published - enabled - NO_KOTLIN_AT_ALL):
        problems.append(f"{name} публикуется, а abiValidation в нём не включён — его API не охраняет ничто")

    for name in sorted(published - dumped - NO_KOTLIN_AT_ALL):
        problems.append(f"{name} публикуется, а эталонного дампа в api/ нет — ./gradlew :{name}:updateKotlinAbi")

    for name in sorted(enabled - published):
        problems.append(f"{name} не публикуется, а дамп ABI держит — его никто не резолвит, и дамп будет гнить")

    if problems:
        print("\n".join(problems), file=sys.stderr)
        return 1

    print(f"ABI: публикуемых модулей — {len(published)}, под проверкой — {len(enabled)}, без Kotlin — {len(NO_KOTLIN_AT_ALL)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
