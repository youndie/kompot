#!/usr/bin/env python3
"""The file a consumer receives must be named with the version it asked for.

Gradle module metadata gives every artifact two strings: `url`, where the file really is, and `name`,
what it is called once it arrives. They come from different places — the url from the publication's
coordinate, the name from the archive task, which takes it from the PROJECT version — so they can
disagree, and nothing in a build notices when they do. Everything is green: the artifact uploads, the
consumer's resolve succeeds, the file downloads from the right url and lands under a name carrying a
version that was never released. Two releases then put identically-named files on a classpath, and
anything reading file names rather than coordinates — an SBOM, a licence scan, shadow-jar
deduplication, a cache — cannot tell them apart.

Module metadata is not the whole publication, and the gap is the point of the second pass below. A
sources jar and a javadoc jar are attached to the POM rather than listed as variant files, so they
appear in no `.module` at all — and the javadoc jar is the one artefact Maven Central refuses a release
without. The check that exists because a file name can lie was blind to the file the portal insists on.

Run against a local publication:

    ./gradlew publishToMavenLocal -PVERSION=<v>
    python3 tools/artifact-name-audit.py <v>
"""
import glob, json, os, sys

VERSION = sys.argv[1] if len(sys.argv) > 1 else sys.exit("usage: artifact-name-audit.py <version>")
M2 = os.path.expanduser("~/.m2/repository/io/github/youndie")

modules = sorted(glob.glob(f"{M2}/*/{VERSION}/*.module"))
if not modules:
    sys.exit(f"no module metadata published under {VERSION} — the audit would pass by finding nothing")

wrong, checked = [], 0
for path in modules:
    artifact = os.path.basename(os.path.dirname(os.path.dirname(path)))
    seen = set()
    for variant in json.load(open(path)).get("variants", []):
        for file in variant.get("files", []):
            entry = (file["name"], file["url"])
            if entry in seen:
                continue
            seen.add(entry)
            checked += 1
            # Only the version is compared, not the whole name. The Kotlin plugin gives some
            # artifacts a base name of its own — a klib arrives as kompot-core-iosArm64Main rather
            # than kompot-core-iosarm64 — and that is every KMP library in the ecosystem, not a
            # defect: two releases still produce two distinguishable names. The version is the part
            # that must not lie. Matching whole segments, so that 0.27.0 does not satisfy 0.27.0.46.
            if f"-{VERSION}." not in file["name"] and f"-{VERSION}-" not in file["name"]:
                wrong.append((artifact, file["name"], file["url"]))

for artifact, name, url in wrong:
    print(f"{artifact}: published as {url}, arrives named {name}")

# The second pass: everything that actually lies in the published directory, whether or not any
# metadata mentions it. Checksums and signatures carry the name of the file they belong to plus a
# suffix, so they are covered by the same rule rather than exempted from it.
IGNORED = ("maven-metadata",)
beside, beside_wrong = 0, []
for path in sorted(glob.glob(f"{M2}/*/{VERSION}/*")):
    name = os.path.basename(path)
    if not os.path.isfile(path) or name.startswith(IGNORED):
        continue
    beside += 1
    if f"-{VERSION}." not in name and f"-{VERSION}-" not in name:
        beside_wrong.append((os.path.basename(os.path.dirname(os.path.dirname(path))), name))

for artifact, name in beside_wrong:
    print(f"{artifact}: {name} lies in the {VERSION} directory under another version's name")

if not beside:
    sys.exit(f"nothing lies beside the metadata under {VERSION} — the second pass would pass by finding nothing")

if wrong or beside_wrong:
    sys.exit(f"\n{len(wrong) + len(beside_wrong)} artifact(s) reach a consumer under a name that is not their version")
print(f"checked {checked} artifacts across {len(modules)} modules and {beside} files beside them: every file arrives named with {VERSION}")
