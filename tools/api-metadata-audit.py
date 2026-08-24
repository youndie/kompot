#!/usr/bin/env python3
"""Every dependency a module's public API mentions must be advertised in its api variant.

The defect this exists for is silent in both directions: a build where every project dependency is
`implementation` compiles, tests and publishes green, and the metadata it produces says the module
needs nothing but the Kotlin standard library. Only a consumer finds out, with

    Cannot access class 'io.github.youndie.kompot.KompotAction'.
    Check your module classpath for missing or conflicting dependencies.

and only if that consumer does not already name the module itself for other reasons.

What it does NOT cover, so that nobody reads a clean run as more than it is: dependencies outside
io.github.youndie. Mapping androidx.compose.foundation.layout.PaddingValues to the artefact that
provides it needs a resolved classpath, and this script has only the sources and ~/.m2 — so a public
signature handing out a third-party type from an `implementation` dependency passes here and fails at
a consumer. That gap is covered from the other side, by a consumer-side reader in the publish
workflow, and it is where #70 was found.

Run against a local publication:

    ./gradlew publishToMavenLocal -PVERSION=<v>
    python3 tools/api-metadata-audit.py <v>
"""
import glob, json, os, re, subprocess, sys, zipfile

VERSION = sys.argv[1] if len(sys.argv) > 1 else sys.exit("usage: api-metadata-audit.py <version>")
M2 = os.path.expanduser("~/.m2/repository/io/github/youndie")
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

# Fully-qualified class -> owning module, read from the sources. By CLASS and not by package: the
# toolkit deliberately puts several modules in one package — kompot-client declares its renderers in
# io.github.youndie.kompot, the very package kompot-core owns — so package ownership is not a function.
# An earlier version of this tool mapped by package, gave KompotAction to whichever module was globbed
# first, and therefore reported a clean run over the exact defect it was written to find.
CLASS_OWNER = {}
DECLARATION = re.compile(r"^(?:public\s+|internal\s+|expect\s+|actual\s+|sealed\s+|abstract\s+|open\s+|data\s+|value\s+|enum\s+|annotation\s+|fun\s+)*(?:class|interface|object)\s+(\w+)")

for gradle in glob.glob(ROOT + "/*/build.gradle.kts"):
    module = os.path.basename(os.path.dirname(gradle))
    sources = glob.glob(f"{ROOT}/{module}/src/*Main/kotlin/**/*.kt", recursive=True)
    sources += glob.glob(f"{ROOT}/{module}/src/main/kotlin/**/*.kt", recursive=True)
    for src in sources:
        text = open(src).read()
        package = re.match(r"\s*package\s+([\w.]+)", text)
        if not package:
            continue
        for line in text.splitlines():
            declared = DECLARATION.match(line)
            if declared:
                CLASS_OWNER.setdefault(f"{package.group(1)}.{declared.group(1)}", module)
        # File facades: Components.kt in package p becomes p.ComponentsKt
        CLASS_OWNER.setdefault(f"{package.group(1)}.{os.path.basename(src)[:-3]}Kt", module)


def owner_of(cls):
    return CLASS_OWNER.get(cls)


def api_deps(artifact):
    path = f"{M2}/{artifact}/{VERSION}/{artifact}-{VERSION}.module"
    if not os.path.exists(path):
        return set()
    return {
        dep["module"]
        for variant in json.load(open(path)).get("variants", [])
        if "api" in variant["name"].lower() and "ources" not in variant["name"]
        for dep in variant.get("dependencies", [])
    }


def reachable(artifact, seen=None):
    """What a consumer's compile classpath really gets: the api edges, followed."""
    seen = set() if seen is None else seen
    for dep in api_deps(artifact):
        if dep in seen:
            continue
        seen.add(dep)
        for candidate in (dep, dep + "-jvm", dep + "-desktop"):
            if os.path.exists(f"{M2}/{candidate}/{VERSION}"):
                reachable(candidate, seen)
                break
    return seen


def mentioned(artifact):
    jars = glob.glob(f"{M2}/{artifact}/{VERSION}/{artifact}-{VERSION}.jar")
    if not jars:
        return None
    with zipfile.ZipFile(jars[0]) as jar:
        classes = [n[:-6].replace("/", ".") for n in jar.namelist() if n.endswith(".class")]
    if not classes:
        return set()
    dumped = subprocess.run(["javap", "-cp", jars[0], "-s"] + classes, capture_output=True, text=True)
    return {sig.replace("/", ".").split("$")[0] for sig in re.findall(r"L([\w/$]+);", dumped.stdout)}


gaps = {}
for artifact in sorted(os.path.basename(os.path.dirname(p)) for p in glob.glob(f"{M2}/*/{VERSION}")):
    refs = mentioned(artifact)
    if refs is None:
        continue
    advertised = reachable(artifact) | {re.sub(r"-(jvm|desktop)$", "", artifact), artifact}
    missing = sorted(
        {owner_of(c) for c in refs if c.startswith("io.github.youndie")} - {None} - advertised
    )
    if missing:
        gaps[artifact] = missing

for artifact, missing in gaps.items():
    print(f"{artifact}: public API mentions {missing}, which a consumer cannot reach")

if gaps:
    sys.exit(f"\n{len(gaps)} module(s) advertise less than their public API needs")
print(f"checked {len(glob.glob(f'{M2}/*/{VERSION}'))} published artifacts: every public API is reachable")
