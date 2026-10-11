"""Shared helpers for the Wallko plant-db tools (stdlib + requests)."""
from __future__ import annotations

import json
import os
import tempfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent.parent
CACHE = HERE / "cache"
LABELS_JSON = CACHE / "labels.json"
MODEL = ROOT / "app/src/main/assets/models/aiy_plants_v1.tflite"
GAME_RULES = ROOT / "app/src/main/java/com/example/wallko/core/config/GameRules.kt"
SPECIES_OUT = ROOT / "app/src/main/assets/plants/species.json"
USER_AGENT = "Wallko-plantdb/0.1 (personal project)"

TYPES = ["tree", "shrub", "herb", "grass", "fern", "succulent", "cactus",
         "vine", "aquatic", "orchid", "other"]
MONTHS = ["JANUARY", "FEBRUARY", "MARCH", "APRIL", "MAY", "JUNE", "JULY",
          "AUGUST", "SEPTEMBER", "OCTOBER", "NOVEMBER", "DECEMBER"]


def read_json(path: Path, default=None):
    try:
        with open(path, encoding="utf-8") as f:
            return json.load(f)
    except FileNotFoundError:
        return default


def write_json(path: Path, data, compact: bool = False) -> None:
    """Atomic write so an interrupted run never leaves a half-written file."""
    path.parent.mkdir(parents=True, exist_ok=True)
    fd, tmp = tempfile.mkstemp(dir=path.parent, prefix=".tmp-", suffix=".json")
    with os.fdopen(fd, "w", encoding="utf-8") as f:
        if compact:
            json.dump(data, f, ensure_ascii=False, separators=(",", ":"))
        else:
            json.dump(data, f, ensure_ascii=False, indent=1)
    os.chmod(tmp, 0o644)
    os.replace(tmp, path)


def load_labels(include_background: bool = False) -> list[dict]:
    labels = read_json(LABELS_JSON)
    if labels is None:
        raise SystemExit("cache/labels.json missing - run extract_labels.py first")
    return [l for l in labels if include_background or not l.get("background")]


def read_env(path: Path = HERE / ".env") -> dict[str, str]:
    """Minimal KEY=VALUE parser. Values are never printed."""
    env: dict[str, str] = {}
    try:
        for line in path.read_text(encoding="utf-8").splitlines():
            line = line.strip()
            if not line or line.startswith("#") or "=" not in line:
                continue
            k, v = line.split("=", 1)
            env[k.strip().removeprefix("export ").strip()] = v.strip().strip('"').strip("'")
    except FileNotFoundError:
        pass
    return env
