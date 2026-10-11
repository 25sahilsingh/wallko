#!/usr/bin/env python3
"""Fetch per-species facts from iNaturalist, GBIF, Wikidata and Perenual.

Resumable: every raw response is cached under cache/raw/<source>/<step>/<index>.json
and never requested again (including "not found" answers). Network errors are not
cached, so a rerun retries them. Perenual is capped per UTC day (default 100 requests),
counted in cache/perenual_quota.json, so reruns on later days continue where they stopped.

Examples:
  python3 fetch.py --limit 5
  python3 fetch.py --sources inat,gbif,wikidata
  python3 fetch.py --sources perenual          # run once a day
"""
from __future__ import annotations

import argparse
import json
import datetime as dt
import sys
import time
from pathlib import Path

import requests

from common import CACHE, USER_AGENT, load_labels, read_env, read_json, write_json

RAW = CACHE / "raw"
QUOTA_FILE = CACHE / "perenual_quota.json"
PERENUAL_FREE_MAX_ID = 3000  # free plan serves species details only for ids 1..3000
ALL_SOURCES = ["inat", "gbif", "wikidata", "perenual"]

INAT = "https://api.inaturalist.org/v1"
GBIF = "https://api.gbif.org/v1"
WIKIDATA_SPARQL = "https://query.wikidata.org/sparql"
PERENUAL = "https://perenual.com/api/v2"


class Http:
    """One session, per-host minimum interval between requests."""

    def __init__(self, min_interval: float):
        self.s = requests.Session()
        self.s.headers["User-Agent"] = USER_AGENT
        self.s.headers["Accept"] = "application/json"
        self.min_interval = min_interval
        self.last: dict[str, float] = {}

    def get(self, host_key: str, url: str, params: dict) -> tuple[int, object]:
        status, body, _ = self.get_full(host_key, url, params)
        return status, body

    def get_full(self, host_key: str, url: str, params: dict) -> tuple[int, object, requests.Response | None]:
        wait = self.last.get(host_key, 0) + self.min_interval - time.monotonic()
        if wait > 0:
            time.sleep(wait)
        for attempt in range(3):
            try:
                r = self.s.get(url, params=params, timeout=30)
            except requests.RequestException as e:
                # Never print the exception text: it can contain the request URL (and API key).
                self.last[host_key] = time.monotonic()
                if attempt == 2:
                    raise FetchError(f"{host_key}: network error ({type(e).__name__})") from None
                time.sleep(5 * (attempt + 1))
                continue
            self.last[host_key] = time.monotonic()
            if r.status_code in (429, 500, 502, 503, 504) and attempt < 2:
                if r.status_code == 429 and host_key == "perenual":
                    return r.status_code, r.text[:200], r
                time.sleep(int(r.headers.get("Retry-After", "0") or 0) or 10 * (attempt + 1))
                continue
            try:
                body = r.json()
            except ValueError:
                body = r.text[:200]
            return r.status_code, body, r
        raise FetchError(f"{host_key}: gave up after retries")


class FetchError(Exception):
    pass


def cache_path(source: str, step: str, index: int) -> Path:
    return RAW / source / step / f"{index:04d}.json"


def cached(source: str, step: str, index: int):
    return read_json(cache_path(source, step, index))


def store(source: str, step: str, index: int, status: int, body) -> dict:
    rec = {"status": status, "fetchedAt": int(time.time()), "body": body}
    write_json(cache_path(source, step, index), rec)
    return rec


def get_cached(http: Http, source: str, step: str, index: int, url: str, params: dict) -> dict:
    rec = cached(source, step, index)
    if rec is not None:
        return rec
    status, body = http.get(source, url, params)
    if status >= 500 or status == 429:
        raise FetchError(f"{source}/{step}: HTTP {status}")
    return store(source, step, index, status, body)


# ---------------------------------------------------------------- iNaturalist

def fetch_inat(http: Http, sp: dict) -> None:
    i = sp["index"]
    search = get_cached(http, "inat", "search", i, f"{INAT}/taxa",
                        {"q": sp["latin"], "is_active": "true", "per_page": 10, "locale": "en"})
    taxon = pick_inat_taxon(search.get("body"), sp["latin"])
    if taxon is None:
        return
    get_cached(http, "inat", "detail", i, f"{INAT}/taxa/{taxon['id']}", {"locale": "en"})


def pick_inat_taxon(body, latin: str):
    results = (body or {}).get("results") or []
    plants = [t for t in results if t.get("iconic_taxon_name") in ("Plantae", None)]
    for t in plants:
        if (t.get("name") or "").lower() == latin.lower():
            return t
    for t in plants:  # synonym hit: matched_term equals the label
        if (t.get("matched_term") or "").lower() == latin.lower():
            return t
    return None


def parse_inat(index: int) -> dict:
    """Normalised facts from the cached iNaturalist detail (or search) record."""
    rec = cached("inat", "detail", index)
    taxon = None
    if rec and rec.get("status") == 200:
        res = (rec.get("body") or {}).get("results") or []
        taxon = res[0] if res else None
    if taxon is None:
        return {}
    out = {
        "common": taxon.get("preferred_common_name"),
        "observations": taxon.get("observations_count"),
        "wikipedia_summary": taxon.get("wikipedia_summary"),
        "iconic_taxon": taxon.get("iconic_taxon_name"),
    }
    for a in taxon.get("ancestors") or []:
        if a.get("rank") == "family":
            out["family"] = a.get("name")
    return {k: v for k, v in out.items() if v not in (None, "")}


# ---------------------------------------------------------------- GBIF

def fetch_gbif(http: Http, sp: dict) -> None:
    i = sp["index"]
    match = get_cached(http, "gbif", "match", i, f"{GBIF}/species/match",
                       {"name": sp["latin"], "kingdom": "Plantae", "strict": "false"})
    body = match.get("body") or {}
    key = body.get("acceptedUsageKey") or body.get("usageKey")
    if not key or body.get("matchType") == "NONE":
        return
    get_cached(http, "gbif", "occurrences", i, f"{GBIF}/occurrence/search",
               {"taxonKey": key, "limit": 0})


def parse_gbif(index: int) -> dict:
    out = {}
    m = cached("gbif", "match", index)
    if m and m.get("status") == 200:
        b = m.get("body") or {}
        if b.get("matchType") not in (None, "NONE") and b.get("kingdom") == "Plantae":
            out["family"] = b.get("family")
    o = cached("gbif", "occurrences", index)
    if o and o.get("status") == 200:
        out["occurrences"] = (o.get("body") or {}).get("count")
    return {k: v for k, v in out.items() if v not in (None, "")}


# ---------------------------------------------------------------- Wikidata

SPARQL = """
SELECT ?item ?desc ?familyName WHERE {
  ?item wdt:P225 "%s" .
  OPTIONAL { ?item schema:description ?desc FILTER(LANG(?desc) = "en") }
  OPTIONAL { ?item wdt:P171* ?fam . ?fam wdt:P105 wd:Q35409 ; wdt:P225 ?familyName . }
} LIMIT 5
"""


def fetch_wikidata(http: Http, sp: dict) -> None:
    latin = sp["latin"].replace("\\", "").replace('"', "")
    get_cached(http, "wikidata", "sparql", sp["index"], WIKIDATA_SPARQL,
               {"query": SPARQL % latin, "format": "json"})


def parse_wikidata(index: int) -> dict:
    rec = cached("wikidata", "sparql", index)
    if not rec or rec.get("status") != 200:
        return {}
    rows = ((rec.get("body") or {}).get("results") or {}).get("bindings") or []
    out = {}
    for r in rows:
        if "desc" in r and "description" not in out:
            out["description"] = r["desc"]["value"]
        if "familyName" in r and "family" not in out:
            out["family"] = r["familyName"]["value"]
    return out


# ---------------------------------------------------------------- Perenual

class PerenualQuota:
    def __init__(self, daily: int):
        self.daily = daily
        self.data = read_json(QUOTA_FILE, {}) or {}

    @property
    def today(self) -> str:
        return dt.datetime.now(dt.timezone.utc).date().isoformat()

    def used(self) -> int:
        return int(self.data.get(self.today, 0))

    def left(self) -> int:
        return max(0, self.daily - self.used())

    def spend(self) -> None:
        self.data = {self.today: self.used() + 1}  # only today's count matters
        write_json(QUOTA_FILE, self.data)

    def sync(self, resp) -> None:
        """Trust the server's X-RateLimit-Remaining when it is lower than our own count."""
        try:
            remaining = int(resp.headers["X-RateLimit-Remaining"])
            limit = int(resp.headers.get("X-RateLimit-Limit", self.daily))
        except (TypeError, KeyError, ValueError, AttributeError):
            return
        used = min(self.daily, max(0, min(limit, self.daily) - remaining))
        if used > self.used():
            self.data = {self.today: used}
            write_json(QUOTA_FILE, self.data)

    def exhaust(self) -> None:
        self.data = {self.today: self.daily}
        write_json(QUOTA_FILE, self.data)


def redact(obj, secret: str):
    """Perenual echoes the API key inside URLs in its responses; strip it before caching."""
    if not secret:
        return obj
    return json.loads(json.dumps(obj).replace(secret, "REDACTED"))


def perenual_get(http: Http, quota: PerenualQuota, key: str, step: str, index: int,
                 path: str, params: dict) -> dict | None:
    rec = cached("perenual", step, index)
    if rec is not None:
        return rec
    if quota.left() <= 0:
        return None
    quota.spend()
    status, body, resp = http.get_full("perenual", f"{PERENUAL}{path}", {**params, "key": key})
    quota.sync(resp)
    body = redact(body, key)
    if isinstance(body, str) and "upgrade" in body.lower():
        # Free tier refuses this record (details only for ids <= 3000). Cache as locked.
        return store("perenual", step, index, status, {"locked": True})
    if status == 429:
        quota.exhaust()
        return None
    if status >= 500:
        raise FetchError(f"perenual/{step}: HTTP {status}")
    return store("perenual", step, index, status, body)


def fetch_perenual(http: Http, sp: dict, quota: PerenualQuota, key: str) -> bool:
    """Returns False once today's quota is used up."""
    i = sp["index"]
    search = perenual_get(http, quota, key, "search", i, "/species-list", {"q": sp["latin"]})
    if search is None:
        return False
    pid = pick_perenual_id(search.get("body"), sp["latin"])
    if pid is None:
        return True
    if pid > PERENUAL_FREE_MAX_ID:
        store("perenual", "detail", i, 0, {"locked": True, "id": pid})  # no request spent
        return True
    detail = perenual_get(http, quota, key, "detail", i, f"/species/details/{pid}", {})
    return detail is not None


def pick_perenual_id(body, latin: str):
    for item in (body or {}).get("data") or []:
        names = item.get("scientific_name") or []
        if isinstance(names, str):
            names = [names]
        if any((n or "").lower().strip() == latin.lower() for n in names):
            try:
                return int(item.get("id"))
            except (TypeError, ValueError):
                return None
    return None


def _clean(v):
    """Perenual's free tier puts 'Upgrade Plans To Premium...' text in locked fields."""
    if isinstance(v, str) and ("upgrade" in v.lower() or "premium" in v.lower()):
        return None
    return v


def parse_perenual(index: int) -> dict:
    rec = cached("perenual", "detail", index)
    if not rec or rec.get("status") != 200 or not isinstance(rec.get("body"), dict):
        return {}
    b = rec["body"]
    out = {
        "watering": _clean(b.get("watering")),
        "sunlight": [s for s in (b.get("sunlight") or []) if _clean(s)] or None,
        "cycle": _clean(b.get("cycle")),
        "growth_rate": _clean(b.get("growth_rate")),
        "flowering_season": _clean(b.get("flowering_season")),
        "type": _clean(b.get("type")),
        "description": _clean(b.get("description")),
        "family": _clean(b.get("family")),
        "poisonous_to_humans": b.get("poisonous_to_humans"),
        "poisonous_to_pets": b.get("poisonous_to_pets"),
        "flowers": b.get("flowers"),
    }
    return {k: v for k, v in out.items() if v not in (None, "", [])}


# ---------------------------------------------------------------- main

def inat_observations(index: int) -> int:
    return parse_inat(index).get("observations") or 0


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--limit", type=int, default=None, help="only the first N species (by index)")
    ap.add_argument("--sources", default="inat,gbif,wikidata,perenual")
    ap.add_argument("--interval", type=float, default=1.0, help="seconds between requests per host")
    ap.add_argument("--perenual-daily", type=int, default=100, help="Perenual requests per UTC day")
    args = ap.parse_args()

    sources = [s.strip() for s in args.sources.split(",") if s.strip()]
    bad = [s for s in sources if s not in ALL_SOURCES]
    if bad:
        ap.error(f"unknown sources {bad}; choose from {ALL_SOURCES}")

    species = load_labels()
    if args.limit is not None:
        species = species[: args.limit]
    http = Http(args.interval)
    errors = 0

    fetchers = {"inat": fetch_inat, "gbif": fetch_gbif, "wikidata": fetch_wikidata}
    open_sources = [s for s in sources if s in fetchers]
    if open_sources:
        for n, sp in enumerate(species, 1):
            for s in open_sources:
                try:
                    fetchers[s](http, sp)
                except FetchError as e:
                    errors += 1
                    print(f"  [{sp['index']}] {sp['latin']}: {e}", file=sys.stderr)
            if n % 25 == 0 or n == len(species):
                print(f"{n}/{len(species)} species ({', '.join(open_sources)}), errors={errors}", flush=True)

    if "perenual" in sources:
        key = read_env().get("PERENUAL_API_KEY")
        if not key:
            print("perenual: PERENUAL_API_KEY not set in tools/plantdb/.env, skipping", file=sys.stderr)
        else:
            quota = PerenualQuota(args.perenual_daily)
            # Most-observed species first: those are the ones players will find.
            todo = sorted(species, key=lambda sp: -inat_observations(sp["index"]))
            done = 0
            for sp in todo:
                if cached("perenual", "search", sp["index"]) is not None and (
                        cached("perenual", "detail", sp["index"]) is not None
                        or pick_perenual_id((cached("perenual", "search", sp["index"]) or {}).get("body"),
                                            sp["latin"]) is None):
                    continue
                try:
                    if not fetch_perenual(http, sp, quota, key):
                        break
                    done += 1
                except FetchError as e:
                    errors += 1
                    print(f"  [{sp['index']}] {sp['latin']}: {e}", file=sys.stderr)
            cached_n = sum(1 for sp in species if cached("perenual", "detail", sp["index"]) is not None)
            print(f"perenual: {done} species touched this run, {quota.used()}/{quota.daily} requests used "
                  f"today, {cached_n}/{len(species)} species have details")

    print(f"done, errors={errors}")
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
