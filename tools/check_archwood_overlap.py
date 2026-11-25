#!/usr/bin/env python3
"""Fails if any of our crafter recipes makes an item that an Archwood Colonies crafter recipe already makes.

Archwood Colonies comes first: its crafters (e.g. the Fletcher's Arcanist armor) make those items, so ours must not.

Usage: check_archwood_overlap.py <archwood colonies jar> <our crafterrecipes dir>
       check_archwood_overlap.py --self-test <archwood colonies jar>   (a duplicate Arcanist robes recipe must fail)
"""
import json
import sys
import tempfile
import zipfile
from pathlib import Path


def archwood_outputs(jar):
    outputs = {}
    with zipfile.ZipFile(jar) as z:
        for name in z.namelist():
            if "/crafterrecipes/" in name and name.endswith(".json"):
                recipe = json.loads(z.read(name))
                result = recipe.get("result")
                if isinstance(result, dict) and "id" in result:
                    outputs.setdefault(result["id"], []).append(name)
    return outputs


def our_outputs(folder):
    outputs = {}
    for path in sorted(Path(folder).rglob("*.json")):
        result = json.loads(path.read_text()).get("result")
        if isinstance(result, dict) and "id" in result:
            outputs.setdefault(result["id"], []).append(str(path))
    return outputs


def check(jar, folder):
    theirs = archwood_outputs(jar)
    if not theirs:
        print(f"no Archwood crafter recipes found in {jar}", file=sys.stderr)
        return 2
    clashes = {item: files for item, files in our_outputs(folder).items() if item in theirs}
    for item, files in sorted(clashes.items()):
        print(f"{item}: made by Archwood ({', '.join(theirs[item])}) and by us ({', '.join(files)})", file=sys.stderr)
    if clashes:
        print(f"{len(clashes)} crafter recipe(s) duplicate Archwood Colonies; remove ours.", file=sys.stderr)
        return 1
    print(f"Archwood overlap check: {len(theirs)} Archwood outputs, no duplicates.")
    return 0


def self_test(jar):
    with tempfile.TemporaryDirectory() as tmp:
        dup = {"type": "recipe", "crafter": "arcanist_custom", "inputs": [{"id": "minecraft:leather", "count": 1}],
               "result": {"id": "ars_nouveau:arcanist_robes", "count": 1}}
        Path(tmp, "robes.json").write_text(json.dumps(dup))
        if check(jar, tmp) != 1:
            print("self-test FAILED: a duplicate Arcanist robes recipe was not caught", file=sys.stderr)
            return 1
    print("self-test passed: a duplicate Arcanist robes recipe is caught.")
    return 0


if __name__ == "__main__":
    if len(sys.argv) == 3 and sys.argv[1] == "--self-test":
        sys.exit(self_test(sys.argv[2]))
    if len(sys.argv) != 3:
        print(__doc__, file=sys.stderr)
        sys.exit(2)
    sys.exit(check(sys.argv[1], sys.argv[2]))
