#!/usr/bin/env python3
"""Extract the classifier labels from the zip appended to aiy_plants_v1.tflite.

Writes cache/labels.json: [{index, latin, common_en, background?}, ...] where
`index` is the model output index. The `__background__` row (index 2101 in this
model) is kept but flagged `background: true` so later steps skip it.
"""
from __future__ import annotations

import re
import sys
import zipfile

from common import LABELS_JSON, MODEL, write_json


def main() -> int:
    with zipfile.ZipFile(MODEL) as z:
        latin = z.read("0-labels.txt").decode("utf-8").splitlines()
        english = z.read("0-labels-en.txt").decode("utf-8").splitlines()
    if len(latin) != len(english):
        raise SystemExit(f"label files differ in length: {len(latin)} vs {len(english)}")

    out = []
    for i, (lat, en) in enumerate(zip(latin, english)):
        lat = lat.strip()
        row = {"index": i, "latin": lat, "common_en": None}
        if lat == "__background__" or en.strip() == "N/A":
            row["background"] = True
        else:
            m = re.fullmatch(re.escape(lat) + r"\s*\((.+)\)\s*", en.strip())
            if m:
                common = m.group(1).strip()
                if common and common.lower() != lat.lower():
                    row["common_en"] = common
        out.append(row)

    write_json(LABELS_JSON, out)
    bg = [r["index"] for r in out if r.get("background")]
    named = sum(1 for r in out if r["common_en"])
    print(f"{len(out)} labels, background at {bg}, {len(out) - len(bg)} species, "
          f"{named} with English names -> {LABELS_JSON}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
