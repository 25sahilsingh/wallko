#!/usr/bin/env python3
"""Write app/src/main/assets/plants/species.json from whatever data exists.

Works with labels only (everything defaulted) and gets richer as fetch/estimate data
arrives. Output: {"dbVersion": YYYYMMDDHH (UTC), "species": [...]}, compact JSON.
Row keys match the PlantSpecies Room entity; the per-row `dbVersion` column is filled
from the top-level value by the app, and null fields are omitted.
"""
from __future__ import annotations

import argparse
import datetime as dt
import sys

from common import SPECIES_OUT, TYPES, write_json
import derive

RARITIES = {"COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY"}
GROWTH = {"FAST", "MEDIUM", "SLOW"}
KEYS = ["index", "latin", "common", "family", "type", "description", "observations", "rarity",
        "thirst", "sun", "growth", "bloom", "badges", "sources"]


def check(rows: list[dict]) -> None:
    idx = [r["index"] for r in rows]
    assert idx == list(range(len(rows))), "indices must be 0..N-1 in order (model output index)"
    for r in rows:
        assert r["latin"] and r["latin"] != "__background__", r
        assert r["type"] in TYPES, r
        assert r["rarity"] in RARITIES and r["growth"] in GROWTH, r
        assert 1 <= r["thirst"] <= 5 and 1 <= r["sun"] <= 3, r
        assert isinstance(r["observations"], int) and r["observations"] >= 0, r
        assert r["description"] is None or len(r["description"]) <= 160, r


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--db-version", type=int, help="override the YYYYMMDDHH version")
    args = ap.parse_args()
    version = args.db_version or int(dt.datetime.now(dt.timezone.utc).strftime("%Y%m%d%H"))
    rows = derive.derive_all()
    check(rows)
    species = [{k: r[k] for k in KEYS if r.get(k) is not None} for r in rows]
    write_json(SPECIES_OUT, {"dbVersion": version, "species": species}, compact=True)
    size = SPECIES_OUT.stat().st_size
    print(f"wrote {len(species)} species, dbVersion {version}, {size:,} bytes -> {SPECIES_OUT}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
