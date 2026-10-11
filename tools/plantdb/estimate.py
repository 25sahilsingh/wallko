#!/usr/bin/env python3
"""LLM-estimated care fields, with no API calls from this script.

  python3 estimate.py prepare [--batch-size 150] [--force]
      Writes cache/estimate_batches/batch_NNN.json for species that lack care data
      (no Perenual details). Existing batch files are kept unless --force.

  python3 estimate.py merge
      Reads cache/estimate_answers/batch_NNN.json (written by an LLM agent following
      ESTIMATE_PROMPT.md), validates every item against the strict schema, and writes
      the valid ones to cache/estimates.json. Invalid items are reported and skipped.
"""
from __future__ import annotations

import argparse
import sys

from common import CACHE, TYPES, load_labels, read_json, write_json
import fetch

BATCH_DIR = CACHE / "estimate_batches"
ANSWER_DIR = CACHE / "estimate_answers"
ESTIMATES = CACHE / "estimates.json"

SCHEMA_KEYS = {"index", "type", "thirst", "sun", "growth", "bloom", "description", "toxic", "confidence"}


def has_care_data(index: int) -> bool:
    p = fetch.parse_perenual(index)
    return "watering" in p and "sunlight" in p


def known_facts(sp: dict) -> dict:
    i = sp["index"]
    inat, gbif, wd, per = (fetch.parse_inat(i), fetch.parse_gbif(i),
                           fetch.parse_wikidata(i), fetch.parse_perenual(i))
    row = {"index": i, "latin": sp["latin"],
           "common": inat.get("common") or sp.get("common_en")}
    family = per.get("family") or inat.get("family") or gbif.get("family") or wd.get("family")
    if family:
        row["family"] = family
    if per.get("type"):
        row["type_hint"] = per["type"]
    if wd.get("description"):
        row["wikidata"] = wd["description"]
    return row


def prepare(batch_size: int, force: bool) -> int:
    BATCH_DIR.mkdir(parents=True, exist_ok=True)
    existing = sorted(BATCH_DIR.glob("batch_*.json"))
    if existing and not force:
        print(f"{len(existing)} batch files already exist in {BATCH_DIR} (use --force to rebuild)")
        return 0
    for f in existing:
        f.unlink()
    already = set((read_json(ESTIMATES, {}) or {}).keys())
    todo = [known_facts(sp) for sp in load_labels()
            if not has_care_data(sp["index"]) and str(sp["index"]) not in already]
    n = 0
    for start in range(0, len(todo), batch_size):
        n += 1
        write_json(BATCH_DIR / f"batch_{n:03d}.json",
                   {"batch": n, "instructions": "tools/plantdb/ESTIMATE_PROMPT.md",
                    "species": todo[start:start + batch_size]})
    print(f"{len(todo)} species need estimates -> {n} batch files in {BATCH_DIR}")
    return 0


def validate(item, allowed_indices: set[int]) -> list[str]:
    errs = []
    if not isinstance(item, dict):
        return ["not an object"]
    missing = SCHEMA_KEYS - item.keys()
    extra = item.keys() - SCHEMA_KEYS - {"latin"}  # echoing latin back is tolerated
    if missing:
        errs.append(f"missing {sorted(missing)}")
    if extra:
        errs.append(f"unexpected {sorted(extra)}")
    g = item.get

    def is_int(v):
        return isinstance(v, int) and not isinstance(v, bool)

    if not is_int(g("index")) or g("index") not in allowed_indices:
        errs.append(f"bad index {g('index')!r}")
    if g("type") not in TYPES:
        errs.append(f"bad type {g('type')!r}")
    if not is_int(g("thirst")) or not 1 <= g("thirst") <= 5:
        errs.append(f"bad thirst {g('thirst')!r}")
    if not is_int(g("sun")) or not 1 <= g("sun") <= 3:
        errs.append(f"bad sun {g('sun')!r}")
    if g("growth") not in ("fast", "medium", "slow"):
        errs.append(f"bad growth {g('growth')!r}")
    b = g("bloom")
    if not isinstance(b, list) or not all(is_int(m) and 1 <= m <= 12 for m in b) or len(set(b)) != len(b or []):
        errs.append(f"bad bloom {b!r}")
    d = g("description")
    if not isinstance(d, str) or not d.strip() or len(d) > 160:
        errs.append(f"bad description (len {len(d) if isinstance(d, str) else 'n/a'})")
    if g("toxic") not in (True, False, None):
        errs.append(f"bad toxic {g('toxic')!r}")
    if g("confidence") not in ("low", "medium", "high"):
        errs.append(f"bad confidence {g('confidence')!r}")
    return errs


def merge() -> int:
    species = {sp["index"]: sp for sp in load_labels()}
    estimates = read_json(ESTIMATES, {}) or {}
    files = sorted(ANSWER_DIR.glob("batch_*.json"))
    if not files:
        print(f"no answer files in {ANSWER_DIR}")
        return 0
    ok = bad = 0
    for f in files:
        try:
            data = read_json(f)
        except ValueError as e:
            print(f"{f.name}: invalid JSON ({e})")
            bad += 1
            continue
        if isinstance(data, dict):
            data = data.get("answers", data.get("species"))
        if not isinstance(data, list):
            print(f"{f.name}: expected a JSON array of answers")
            bad += 1
            continue
        for item in data:
            errs = validate(item, set(species))
            if errs:
                bad += 1
                print(f"{f.name}: index {item.get('index') if isinstance(item, dict) else '?'}: {'; '.join(errs)}")
                continue
            if item.get("latin") and item["latin"] != species[item["index"]]["latin"]:
                bad += 1
                print(f"{f.name}: index {item['index']}: latin {item['latin']!r} does not match "
                      f"{species[item['index']]['latin']!r}")
                continue
            clean = {k: item[k] for k in SCHEMA_KEYS}
            clean["bloom"] = sorted(clean["bloom"])
            clean["description"] = clean["description"].strip()
            estimates[str(item["index"])] = clean
            ok += 1
    write_json(ESTIMATES, estimates)
    print(f"merged {ok} valid answers, {bad} rejected; {len(estimates)} species estimated in total -> {ESTIMATES}")
    return 1 if bad else 0


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = ap.add_subparsers(dest="cmd", required=True)
    p = sub.add_parser("prepare")
    p.add_argument("--batch-size", type=int, default=150)
    p.add_argument("--force", action="store_true", help="rebuild batch files even if some exist")
    sub.add_parser("merge")
    args = ap.parse_args()
    if args.cmd == "prepare":
        return prepare(args.batch_size, args.force)
    return merge()


if __name__ == "__main__":
    sys.exit(main())
