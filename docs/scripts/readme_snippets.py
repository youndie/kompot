#!/usr/bin/env python3
"""
Проверяет, что имена в ```kotlin-блоках README всё ещё существуют.

    python3 docs/scripts/readme_snippets.py            # проверить (то же, что в CI)
    python3 docs/scripts/readme_snippets.py --list     # показать блоки, из которых ничего не вышло

Зачем. Блоки в README выглядят как код и им ничего не являются: ни один из них не компилируется,
и переименование публичного имени оставляет в документации строку, которая собирается только на
вид. Узнаёт об этом первым не автор правки, а тот, кто скопировал блок к себе. В репозитории это
уже происходило: `drawnAsFallback` переименован (B-34), `UnknownComponentRenderer` получил
параметр (B-31), `kompotJson` из константы стал функцией.

Граница проверки проходит ровно здесь:

* **ловит** пропавшее имя: переименовали, удалили, опечатались;
* **ловит** координату модуля, которого в сборке больше нет;
* **не ловит** изменение сигнатуры. Имя на месте, аргументы другие — блок по-прежнему не
  компилируется, и проверка молчит. По той же причине не проверяются метки именованных
  аргументов: это имена параметров, то есть сигнатура;
* **не ловит** совпадение: выдуманное автором `registry` проходит, потому что где-то в
  исходниках есть `val registry`. Проверка отвечает «такое имя в репозитории объявлено», а не
  «именно это имя видно из этого места».

Компилировать блоки по-настоящему здесь нечем, и это не лень: они ссылаются на классы из модулей,
которые не лежат на одном класспасе (клиент — Compose, spec и tck — JVM-инструменты), а часть
блоков — Gradle DSL и куски с многоточием, которые не компилируются в принципе.

Почему «имя объявлено», а не «имя встречается». Второе проще и бесполезно: комментарий
«It replaced a boolean named drawnAsFallback» оставляет старое имя в исходниках навсегда, и
проверка «встречается» пройдёт по нему.

Незнакомое имя надо либо объявить, либо занести в один из списков ниже — с той разницей, что в
PLACEHOLDERS нельзя положить имя, начинающееся с kompot/Kompot/generated. Это ровно та дыра,
которую список мог бы прикрыть: имя тулкита, выданное за выдуманное автором примера.
"""
import os
import re
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

# Ключевые и мягкие ключевые слова Kotlin: они не имена, проверять нечего.
KEYWORDS = set("""
val var fun class object interface typealias enum annotation data sealed abstract open companion init
override public private internal protected const lateinit vararg suspend inline noinline crossinline
reified operator infix external actual expect tailrec
if else when for while do try catch finally throw return break continue
is as in out by where this super null true false it field get set
import package constructor delegate receiver param property setparam dynamic
""".split())

# Чужие имена: stdlib, Compose, kotlinx.serialization, Gradle Kotlin DSL, Ktor. Список короткий и
# стабильный — он растёт только когда в README появляется блок с новой внешней библиотекой.
FOREIGN = {
    "String", "Int", "Boolean", "Throwable", "List", "Map", "Pair", "listOf", "mapOf", "map", "filter", "forEach",
    "firstOrNull", "check", "require", "toString", "to", "run", "let", "also", "emptyList", "emptyMap",
    "Serializable", "SerialName", "Json", "JsonObject", "JsonPrimitive", "encodeToString", "serializer",
    "Composable", "Preview", "CompositionLocalProvider", "provides", "Modifier", "remember",
    "plugins", "dependencies", "repositories", "implementation", "maven", "platform", "ksp", "arg",
    "includeGroup", "id", "version", "url", "content", "group", "name",
    "get", "call", "respond",
}

# Имена, придуманные автором примера, чтобы читатель подставил своё. Сверять их не с чем — но
# спрятать за этим списком настоящее имя тулкита нельзя, см. FORBIDDEN_PLACEHOLDER_PREFIXES.
PLACEHOLDERS = {
    # «твоё приложение»
    "myRegistry", "myRenderers", "myJson", "myDesignSystem", "mySpec", "myComponentsSpecModule",
    "MyBrandFrame", "MyClientAdapter",
    # выдуманный каталог товаров из раздела «What it looks like»
    "ProductCardComponent", "ProductCardRenderer", "productCard", "checkoutScreen",
    "PromoBannerComponent", "PromoBannerRenderer",
    # чужая телеметрия в примере стока деградации
    "analytics", "crashReporter", "breadcrumb",
    # транспорт приложения в примере withPerform
    "performOnServer",
    # прочие подстановки в примерах студии и TCK
    "showcaseComponents", "recordingsDir", "homeSubmitBody",
    # значения читателя в примере сборки экрана
    "rootComponent", "actionHandler",
}

FORBIDDEN_PLACEHOLDER_PREFIXES = ("kompot", "Kompot", "generated")

# То, что пишет KSP-процессор. Суффиксы не переписаны из головы: они сверяются с исходником
# процессора на каждом прогоне, поэтому смена шаблона имени красит проверку, а не проходит мимо.
GENERATED_SUFFIXES = ("SerializersModule", "Docs", "Renderers")
PROCESSOR = (
    "kompot-registry-processor/src/main/kotlin/io/github/youndie/kompot/registry/processor/"
    "KompotRegistrySymbolProcessor.kt"
)


def run(*args):
    return subprocess.run(args, cwd=ROOT, capture_output=True, text=True).stdout


def declared_names():
    """Имена, ОБЪЯВЛЕННЫЕ в исходниках репозитория."""
    names = set()
    # `\b` в git grep не поддерживается — отсюда пробелы вместо границ слова в шаблонах.
    patterns = [
        (r"(class|object|interface|typealias|fun|val|var) +[A-Za-z_][A-Za-z0-9_]*",
         r"([A-Za-z_][A-Za-z0-9_]*)$"),
        # `fun interface KompotDegradationSink` — имя третьим словом
        (r"fun interface +[A-Za-z_][A-Za-z0-9_]*", r"([A-Za-z_][A-Za-z0-9_]*)$"),
        # расширение: `fun ApplicationCall.respondKompotComponent(...)`
        (r"fun +[A-Za-z_][A-Za-z0-9_<>?, ]*\.[A-Za-z_][A-Za-z0-9_]*", r"\.([A-Za-z_][A-Za-z0-9_]*)$"),
        # элементы enum: по одному на строке, заглавными
        (r"^ +[A-Z][A-Z0-9_]+,", r"([A-Z][A-Z0-9_]+),$"),
    ]
    for grep, extract in patterns:
        names.update(re.findall(extract, run("git", "grep", "-hoE", grep, "--", "*.kt"), re.M))
    return names


def modules():
    """Пути Gradle-модулей: то, что может стоять после `io.github.youndie:`."""
    with open(os.path.join(ROOT, "settings.gradle.kts")) as handle:
        return set(re.findall(r'include\("(?::)?([A-Za-z0-9-]+)"\)', handle.read()))


def strip_noise(code):
    """Комментарии и строковые литералы: имя внутри них ничего не обещает читателю."""
    # СТРОКИ ПЕРВЫМИ, и порядок здесь не вкусовой. Снять сначала `//`-комментарии значит съесть
    # половину `"http://localhost:5000"` — остаток строки перестаёт быть строкой, и `http` уезжает
    # в проверку как имя. Ровно это и выпало первым прогоном.
    code = re.sub(r'"""(.*?)"""', '""', code, flags=re.S)
    code = re.sub(r'"(?:[^"\\\n]|\\.)*"', '""', code)
    code = re.sub(r"/\*.*?\*/", "", code, flags=re.S)
    code = re.sub(r"//[^\n]*", "", code)
    return code


def strip_argument_labels(code):
    """`registry = ...` — имя ПАРАМЕТРА, то есть сигнатура, которой эта проверка не видит.

    Оставить их — падать будет каждый блок, и список исключений съест проверку целиком.

    Идёт СТРОГО ПОСЛЕ block_local и не раньше: `val schemas = KompotSpec.generateAll(...)` без
    метки превращается в `val  KompotSpec…`, и разбор локальных объявлений записывает KompotSpec
    в локальные — то есть перестаёт проверять настоящее имя, не сказав ни слова. Так и было,
    пока самопроверка не показала пустой список там, где ждали ровно одно ненайденное имя.
    """
    return re.sub(r"[A-Za-z_][A-Za-z0-9_]*\s*=(?!=)", "", code)


def block_local(code):
    """Имена, которые блок объявляет сам: локальные val/fun/class и параметры лямбд.

    Сверять их не с чем и не надо: объявление у читателя перед глазами, в тех же десяти строках.
    Без этого правила каждое `val kompotVersion` и `class StudioProvider` пришлось бы заносить в
    список исключений — то есть держать список, который растёт с каждым новым примером.
    """
    names = set(re.findall(r"(?:val|var|fun|class|object|interface) +([A-Za-z_][A-Za-z0-9_]*)", code))
    # параметры лямбд: `{ brand, dark, content -> … }`
    for params in re.findall(r"\{([^}\n]*)->", code):
        names.update(re.findall(r"[A-Za-z_][A-Za-z0-9_]*", params))
    # параметры функций и свойства с типом: всё, что стоит перед двоеточием. Имя ПОСЛЕ двоеточия —
    # тип — под правило не попадает и проверяется как обычно.
    names.update(re.findall(r"([A-Za-z_][A-Za-z0-9_]*)\s*:", code))
    return names


def readmes():
    return sorted(run("git", "ls-files", "README.md", "*/README.md", "*/*/README.md").split())


def blocks_of(path):
    with open(os.path.join(ROOT, path)) as handle:
        text = handle.read()
    found = re.findall(r"^```kotlin\n(.*?)^```$", text, re.S | re.M)
    # Сторож холостого прогона, по каждому файлу: README, где слово есть, а блоков не нашлось, —
    # это сломанный разбор, а не файл без кода, и молча он выглядит как зелёная проверка.
    if "```kotlin" in text and not found:
        raise SystemExit(f"{path}: в файле есть ```kotlin, а разбор не нашёл ни одного блока")
    return found


def coordinates_of(path):
    with open(os.path.join(ROOT, path)) as handle:
        return set(re.findall(r"io\.github\.youndie:([A-Za-z0-9-]+)", handle.read()))


def sanity(declared):
    if len(declared) < 500:
        raise SystemExit(f"объявленных имён найдено {len(declared)} — столько не бывает, сломан разбор исходников")

    with open(os.path.join(ROOT, PROCESSOR)) as handle:
        processor = handle.read()
    for suffix in GENERATED_SUFFIXES:
        if f'"generated${{moduleTag}}{suffix}"' not in processor:
            raise SystemExit(
                f"процессор больше не пишет generated<Tag>{suffix} — правило про сгенерированные "
                f"имена в этом файле устарело вместе с ним ({PROCESSOR})"
            )

    forbidden = sorted(n for n in PLACEHOLDERS if n.startswith(FORBIDDEN_PLACEHOLDER_PREFIXES))
    if forbidden:
        raise SystemExit(
            "в PLACEHOLDERS попали имена тулкита: " + ", ".join(forbidden) +
            " — список для подстановок читателя, а не для того, чтобы гасить проверку"
        )


def check(verbose=False):
    declared = declared_names()
    sanity(declared)
    generated = re.compile(r"^generated[A-Z][A-Za-z0-9]*(" + "|".join(GENERATED_SUFFIXES) + r")$")
    known_modules = modules()

    problems = []
    checked = 0
    silent = []
    for path in readmes():
        # Область видимости — ФАЙЛ, а не блок: README читают подряд, и второй блок спокойно
        # пользуется `val spec` из первого. Проверка, считающая блоки порознь, требует от текста
        # того, чего от него не требует читатель.
        local = set()
        for number, body in enumerate(blocks_of(path), start=1):
            clean = strip_noise(body)
            local |= block_local(clean)
            code = strip_argument_labels(clean)
            confirmed = 0
            for word in dict.fromkeys(re.findall(r"[A-Za-z_][A-Za-z0-9_]*", code)):
                if word in KEYWORDS or word in FOREIGN or word in PLACEHOLDERS or word in local:
                    continue
                if word in declared or generated.match(word):
                    confirmed += 1
                    continue
                problems.append(f"{path}, блок {number}: имя `{word}` не объявлено нигде в репозитории")
            checked += confirmed
            if confirmed == 0:
                silent.append(f"{path}, блок {number}")
                if verbose:
                    print(f"  {path}, блок {number}: настоящих имён не нашлось — проверка о нём ничего не сказала")

        for coordinate in sorted(coordinates_of(path)):
            if coordinate in known_modules:
                checked += 1
            else:
                problems.append(f"{path}: координата io.github.youndie:{coordinate} — такого модуля в сборке нет")

    return problems, checked, silent


def selftest():
    """Проверка, что проверка срабатывает.

    Без неё сломанный разбор выглядит как зелёный прогон: ноль найденных имён — ноль претензий.
    """
    declared = declared_names()
    fixture = "val registry = KompotRegistry(kompotCoreRenderers)\nval gone = KompotRegistryThatNeverWas()\n"
    clean = strip_noise(fixture)
    local = block_local(clean)
    code = strip_argument_labels(clean)
    words = [w for w in re.findall(r"[A-Za-z_][A-Za-z0-9_]*", code) if w not in KEYWORDS and w not in local]
    missing = [w for w in words if w not in declared]
    if missing != ["KompotRegistryThatNeverWas"]:
        raise SystemExit(f"самопроверка не сработала: ожидалось одно ненайденное имя, вышло {missing}")


def main():
    selftest()
    problems, checked, silent = check("--list" in sys.argv)
    if problems:
        print("\n".join(problems))
        print(f"\nнайдено проблем: {len(problems)}")
        return 1
    print(f"README: имён и координат проверено — {checked}, все на месте")
    # Молчание про эти блоки печатается всегда, а не прячется за флагом: проверка, которая не
    # говорит, о чём она НЕ высказалась, читается как проверка всего.
    if silent:
        print(f"блоков, о которых проверка ничего не сказала — {len(silent)} (Gradle DSL и подобное; --list покажет)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
