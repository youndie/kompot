#!/usr/bin/env python3
"""
Compiles real wire bodies against the generated TypeScript declarations (kompot-spec/types/).

    python3 tools/ts-check.py            # needs node; fetches TypeScript through npx

The declarations are generated and held by a golden test, which proves they match the schema — not
that they are usable. This does the second half: every playground example body (the page's own
screens, covering most of the vocabulary) and every form in the client case corpus is typed as what
it is on the wire and handed to `tsc --strict`.

Against the STRICT file (kompot.strict.d.ts), because these bodies are written by a server: the open
file's unknown branch accepts any mistake as a type it does not know. A body that deliberately carries
a type outside the toolkit — the playground's demo plug-in — is what an older client reads, and is
typed against the OPEN file instead.

Then a control, every run: one nested value in a body is broken, and tsc must reject it. A check that
also accepts the broken body checks nothing, and fails here rather than staying green.
"""
import copy
import glob
import json
import os
import re
import subprocess
import sys
import tempfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TYPES = os.path.join(ROOT, "kompot-spec", "types")
STRICT = os.path.join(TYPES, "kompot.strict.d.ts")
OPEN = os.path.join(TYPES, "kompot.d.ts")
TYPESCRIPT = "typescript@5.6.3"


def playground_bodies():
    for path in sorted(glob.glob(os.path.join(ROOT, "kompot-playground", "src", "wasmJsMain", "**", "*.kt"), recursive=True)):
        text = open(path, encoding="utf-8").read()
        for m in re.finditer(r'(?:private |internal )?val (\w+): String =\s*"""(.*?)"""', text, re.S):
            yield f"playground {m.group(1)}", json.loads(m.group(2))


def corpus_bodies():
    for path in sorted(glob.glob(os.path.join(ROOT, "kompot-client-tck", "corpus", "*.json"))):
        case = json.load(open(path, encoding="utf-8"))
        if isinstance(case, dict) and "form" in case:
            yield f"corpus {os.path.basename(path)}", case["form"]


# What a body is on the wire, by its shape.
def wire_type(body):
    if "formId" in body and "fields" in body:
        return "FormSchema"
    if "schema" in body and "screen" in body:
        return "KompotFormResponse"
    if "screen" in body:
        return "KompotScreenResponse"
    return "KompotComponent"


def known_types():
    return set(re.findall(r'^\s+type: "([a-z_]+)";', open(STRICT, encoding="utf-8").read(), re.M))


def types_in(node, out):
    if isinstance(node, dict):
        if isinstance(node.get("type"), str):
            out.add(node["type"])
        for v in node.values():
            types_in(v, out)
    elif isinstance(node, list):
        for v in node:
            types_in(v, out)
    return out


def compile_bodies(bodies, known):
    """The names of the bodies tsc rejected; exits if it rejected the declarations themselves."""
    with tempfile.TemporaryDirectory() as work:
        lines = [
            f'import type * as Strict from "{STRICT[:-5]}";',
            f'import type * as Open from "{OPEN[:-5]}";',
        ]
        for i, (name, body) in enumerate(bodies):
            side = "Strict" if types_in(body, set()) <= known else "Open"
            lines.append(f"// {name}")
            lines.append(f"export const body{i}: {side}.{wire_type(body)} = {json.dumps(body, ensure_ascii=False)};")
        check = os.path.join(work, "check.ts")
        open(check, "w", encoding="utf-8").write("\n".join(lines) + "\n")
        done = subprocess.run(
            ["npx", "-y", "-p", TYPESCRIPT, "tsc", "--noEmit", "--strict", "--target", "es2020", "--moduleResolution", "node", STRICT, OPEN, check],
            capture_output=True,
            text=True,
        )
        out = done.stdout + done.stderr
        if done.returncode == 0:
            return [], out
        # Each body takes two lines — its name, then its constant — from line 4 on.
        rejected = sorted({(int(n) - 4) // 2 for n in re.findall(r"check\.ts\((\d+),", out)})
        if not rejected:
            sys.exit("tsc rejected the declarations themselves, before any body:\n" + out)
        return [bodies[i][0] for i in rejected], out


def break_one(bodies):
    """A copy of the bodies with one NESTED value of the wrong type: a `spacing` below the root."""
    broken = copy.deepcopy(bodies)
    for name, body in broken:
        for child in body.get("children", []) if isinstance(body, dict) else []:
            if isinstance(child, dict) and child.get("type") in ("row", "column"):
                child["spacing"] = "wide"
                return broken, name
    sys.exit("control: no nested row or column to break — the control would pass without checking")


def main():
    bodies = list(playground_bodies()) + list(corpus_bodies())
    if not bodies:
        sys.exit("no bodies found — the check would pass without checking anything")
    known = known_types()

    rejected, out = compile_bodies(bodies, known)
    if rejected:
        print(out)
        for name in rejected:
            print(f"  rejected: {name}")
        sys.exit(f"tsc rejected {len(rejected)} of {len(bodies)} bodies")

    broken, where = break_one(bodies)
    rejected, _ = compile_bodies(broken, known)
    if where not in rejected:
        sys.exit(f"control: a nested `spacing: \"wide\"` in {where} was accepted — the check checks nothing")

    print(f"tsc: {len(bodies)} bodies accepted; the control (a broken nested value in {where}) rejected")


if __name__ == "__main__":
    main()
