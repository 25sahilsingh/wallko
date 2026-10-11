#!/usr/bin/env python3
"""Write app/src/main/assets/plants/species.json from whatever data exists.

Works with labels only (everything defaulted) and gets richer as fetch/estimate data
arrives. Output: {"dbVersion": YYYYMMDDHH (UTC), "species": [...]}, compact JSON.
Row keys match the PlantSpecies Room entity; the per-row `dbVersion` column is filled
from the top-level value by the app, and null fields are omitted.

After the AIY species (0..2100) come the hand-written houseplants in extra_species.json
(plants BioCLIP can name but AIY lacks), from index 2102 on; 2101 stays the AIY background.
`--extras-only` swaps just those rows in the existing species.json, without the caches.
"""
from __future__ import annotations

import argparse
import datetime as dt
import sys

from common import HERE, SPECIES_OUT, TYPES, read_json, write_json
import derive

RARITIES = {"COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY"}
GROWTH = {"FAST", "MEDIUM", "SLOW"}
EXTRAS = HERE / "extra_species.json"
FIRST_EXTRA_INDEX = 2102  # AIY outputs 0..2100 are species, 2101 is its background row
KEYS = ["index", "latin", "common", "family", "type", "description", "observations", "rarity",
        "thirst", "sun", "growth", "bloom", "badges", "sources"]


def extra_rows() -> list[dict]:
    """Houseplants: hand-set care data, COMMON rarity (kept at home, widely grown), no observations."""
    rows = []
    for i, e in enumerate(read_json(EXTRAS, [])):
        hand = {k: "estimated" for k in ("family", "type", "thirst", "sun", "growth", "badges")}
        rows.append({"index": FIRST_EXTRA_INDEX + i, "latin": e["latin"], "common": e["common"],
                     "family": e.get("family"), "type": e["type"], "description": None,
                     "observations": 0, "rarity": e.get("rarity", "COMMON"), "thirst": e["thirst"],
                     "sun": e["sun"], "growth": e["growth"], "bloom": [], "badges": e.get("badges", []),
                     "sources": {"common": "model", **hand, "observations": "default", "rarity": "default"}})
    return rows


def check(rows: list[dict]) -> None:
    idx = [r["index"] for r in rows]
    aiy = [i for i in idx if i < FIRST_EXTRA_INDEX]
    assert aiy == list(range(len(aiy))), "AIY indices must be 0..N-1 in order (model output index)"
    assert idx[len(aiy):] == list(range(FIRST_EXTRA_INDEX, FIRST_EXTRA_INDEX + len(idx) - len(aiy))), \
        "extra indices must follow from FIRST_EXTRA_INDEX"
    latins = [r["latin"].lower() for r in rows]
    assert len(set(latins)) == len(latins), "duplicate latin name (an extra AIY already has?)"
    for r in rows:
        assert r["latin"] and r["latin"] != "__background__", r
        assert r["type"] in TYPES, r
        assert r["rarity"] in RARITIES and r["growth"] in GROWTH, r
        assert 1 <= r["thirst"] <= 5 and 1 <= r["sun"] <= 3, r
        assert isinstance(r["observations"], int) and r["observations"] >= 0, r
        assert r.get("description") is None or len(r["description"]) <= 160, r


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--db-version", type=int, help="override the YYYYMMDDHH version")
    ap.add_argument("--extras-only", action="store_true",
                    help="keep the AIY rows of the current species.json, replace only the extras")
    args = ap.parse_args()
    version = args.db_version or int(dt.datetime.now(dt.timezone.utc).strftime("%Y%m%d%H"))
    if args.extras_only:
        aiy = [r for r in read_json(SPECIES_OUT)["species"] if r["index"] < FIRST_EXTRA_INDEX]
        species = aiy + [{k: r[k] for k in KEYS if r.get(k) is not None} for r in extra_rows()]
        check(species)
        write_json(SPECIES_OUT, {"dbVersion": version, "species": species}, compact=True)
        print(f"wrote {len(aiy)} AIY + {len(species) - len(aiy)} extra species, dbVersion {version}")
        return 0
    rows = derive.derive_all() + extra_rows()
    check(rows)
    species = [{k: r[k] for k in KEYS if r.get(k) is not None} for r in rows]
    write_json(SPECIES_OUT, {"dbVersion": version, "species": species}, compact=True)
    size = SPECIES_OUT.stat().st_size
    print(f"wrote {len(species)} species, dbVersion {version}, {size:,} bytes -> {SPECIES_OUT}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
