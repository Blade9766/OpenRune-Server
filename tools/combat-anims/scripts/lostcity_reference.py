"""Snapshots the npc combat data of LostCity's 2004 content into tools/combat-anims resources.

LostCity (https://github.com/LostCityRS/Content) rebuilds the 2004 server with Jagex's own config
names, so its npc configs carry the real `attack_anim`, `defend_anim`, `death_anim`,
`attack_sound`, `defend_sound` and `death_sound` of every monster of that era. The combat-anims
dumper matches these entries to OSRS npcs (by the OSRS ids LostCity notes in comments, or by
display name and models) and resolves the names against the OSRS cache.

Usage (from the repository root): python tools/combat-anims/scripts/lostcity_reference.py
"""

import json
import re
import urllib.parse
import urllib.request
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

REPO = "LostCityRS/Content"
BRANCH = "274"
RAW = f"https://raw.githubusercontent.com/{REPO}/{BRANCH}/"
OUT = Path(__file__).resolve().parent.parent / "src/main/resources"

FIELDS = ["attack_anim", "defend_anim", "death_anim", "attack_sound", "defend_sound", "death_sound"]


def fetch(path):
    with urllib.request.urlopen(RAW + urllib.parse.quote(path)) as response:
        return response.read().decode("utf-8", errors="replace")


def npc_files():
    url = f"https://api.github.com/repos/{REPO}/git/trees/{BRANCH}?recursive=1"
    with urllib.request.urlopen(url) as response:
        tree = json.load(response)
    return sorted(x["path"] for x in tree["tree"] if x["path"].endswith(".npc"))


def model_ids():
    ids = {}
    for line in fetch("pack/model.pack").splitlines():
        if "=" in line:
            model_id, name = line.split("=", 1)
            ids[name.strip()] = int(model_id)
    return ids


def parse(text, models):
    entries, current = [], None
    for raw in text.splitlines():
        line = raw.strip()
        header = re.match(r"^\[(.+)\]$", line)
        if header:
            current = {"key": header.group(1), "name": "", "models": [], "osrs": [], "params": {}}
            entries.append(current)
            continue
        if current is None:
            continue
        osrs = re.search(r"dump\.npc npc_(\d+)", line)
        if osrs:
            current["osrs"].append(int(osrs.group(1)))
            continue
        if line.startswith("//") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        if key == "name":
            current["name"] = value
        elif key.startswith("model") and key[5:].isdigit():
            numbered = re.match(r"model_(\d+)", value)
            current["models"].append(int(numbered.group(1)) if numbered else models.get(value, -1))
        elif key == "param":
            param, _, param_value = value.partition(",")
            if param in FIELDS:
                current["params"][param] = param_value
    return entries


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    models = model_ids()
    with ThreadPoolExecutor(16) as pool:
        texts = list(pool.map(fetch, npc_files()))
    entries = [e for text in texts for e in parse(text, models)]
    lines = ["# key\tname\tosrs_ids\tmodels\t" + "\t".join(FIELDS)]
    for e in sorted(entries, key=lambda e: e["key"]):
        if not any(e["params"].get(f) not in (None, "null") for f in FIELDS):
            continue
        row = [
            e["key"],
            e["name"],
            ",".join(map(str, e["osrs"])),
            ",".join(map(str, e["models"])),
        ] + [e["params"].get(f, "") for f in FIELDS]
        lines.append("\t".join(row))
    (OUT / "lostcity-npc-combat.tsv").write_text("\n".join(lines) + "\n", encoding="utf-8")

    genders = ["# model\tgender"]
    for name, model_id in sorted(models.items(), key=lambda x: x[1]):
        if name.startswith("woman_"):
            genders.append(f"{model_id}\tf")
        elif name.startswith("man_"):
            genders.append(f"{model_id}\tm")
    (OUT / "lostcity-body-models.tsv").write_text("\n".join(genders) + "\n", encoding="utf-8")
    print(f"Wrote {len(lines) - 1} npcs and {len(genders) - 1} body models to {OUT}")


if __name__ == "__main__":
    main()
