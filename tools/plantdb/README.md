# Wallko plant database tools

Build-time Python scripts that produce `app/src/main/assets/plants/species.json`. They use only the standard library plus `requests`, and run from this directory with plain `python3` (not `-I`, which would hide `common.py`). Every network script is resumable: raw responses are cached under `cache/` (gitignored) and are never fetched twice. Requests send the User-Agent `Wallko-plantdb/0.1 (personal project)`, at most 1 per second per host.

## Run order

```sh
cd tools/plantdb
python3 extract_labels.py                       # 1. labels -> cache/labels.json
python3 fetch.py --sources inat,gbif,wikidata   # 2. ~5 requests/species, ~2-3 h; rerun to resume
python3 fetch.py --sources perenual             # 2b. once per day (100 requests/day)
python3 estimate.py prepare --batch-size 150    # 3. batches for species still missing care data
#    ... LLM agents fill cache/estimate_answers/batch_NNN.json per ESTIMATE_PROMPT.md ...
python3 estimate.py merge                       # 3b. validate answers -> cache/estimates.json
python3 derive.py                               # 4. optional: inspect cache/derived.json + stats
python3 build.py                                # 5. write species.json (always works)
```

`build.py` works at any stage. With only labels, every care field uses a type default.

## Scripts

| Script | What it does |
|---|---|
| `common.py` | Shared paths, atomic JSON writes, `.env` parser, constants (types, months). |
| `extract_labels.py` | Reads `0-labels.txt` / `0-labels-en.txt` from the zip appended to `aiy_plants_v1.tflite`. Writes `{index, latin, common_en}` per **model output index**. The `__background__` row (index **2101**, the last one) is detected by name and flagged `background: true`, leaving 2,101 species (indices 0–2100). |
| `fetch.py` | iNaturalist (taxa search, then taxon detail: common name, observation count, Wikipedia summary, family), GBIF (species match, then occurrence count), Wikidata (SPARQL on P225: English description and family), Perenual (species-list search, then details). Flags: `--limit N`, `--sources inat,gbif,wikidata,perenual`, `--interval`, `--perenual-daily`. |
| `estimate.py` | No API calls. `prepare` writes `cache/estimate_batches/batch_NNN.json` for species without Perenual care data. `merge` validates `cache/estimate_answers/batch_NNN.json` against the strict schema in `ESTIMATE_PROMPT.md`. |
| `derive.py` | Merges sources with precedence perenual > inaturalist/gbif/wikidata > estimated > default, and records provenance per field in `sources`. Computes rarity, thirst, sun, growth, bloom and badges. |
| `build.py` | Validates the rows and writes compact `species.json` with `dbVersion` = UTC `YYYYMMDDHH`. |

## Perenual

- The key is read from `.env` (`PERENUAL_API_KEY=...`) and is never printed. Perenual echoes the key inside URLs in its responses, so `fetch.py` replaces it with `REDACTED` before caching.
- Daily use is counted in `cache/perenual_quota.json` (UTC day) and synced with the server's `X-RateLimit-Remaining` header. Once 100 requests are used, the script stops and resumes on the next run.
- Species are tried most-observed first.
- The free plan serves details only for species ids up to 3000. Higher ids are cached as `locked` without spending a request.
- Each species costs 2 requests (search + details), so about 50 species per day.

## Derivation rules

- **Rarity**: species are ranked by worldwide observation count, most observed first. The tier comes from the percentile rank using the boundaries in `GameRules.kt` (`<40` Common, `<70` Uncommon, `<90` Rare, `<98` Epic, otherwise Legendary), so the fewest observations means Legendary. The count is the iNaturalist `observations_count`. When that is missing, the GBIF occurrence count is used, scaled by the median iNat/GBIF ratio. When both are missing, the median of known counts is used (tagged `default`). Ties are broken by a stable hash of the Latin name.
- **thirst**: Perenual watering maps None→1, Minimum→2, Average→3, Frequent→4 (aquatic plants get 5). Otherwise the estimate is used, then the type default.
- **sun**: Perenual sunlight maps any full sun→3, part shade→2, shade only→1.
- **growth**: Perenual High/Moderate/Low maps to FAST/MEDIUM/SLOW.
- **bloom**: Perenual season becomes northern-hemisphere months, or the estimate's months are used. Values are `java.time.Month` names (`"MARCH"`). Empty means unknown or non-flowering.
- **Low-confidence estimates** are ignored, and type defaults are used instead.
- **type**: Perenual type, then a family→type table, then the estimate, then a genus/keyword heuristic on the names (`default`), then `herb`.
- **badges** (only when supported by data): `toxic` (Perenual poisonous, or estimate), `carnivorous` (known genera and families), `evergreen` (Perenual type, or conifer/cactus/palm families), `drought-tolerant` and `pollinator-friendly` (Perenual).
- **sources** values: `perenual`, `inaturalist`, `gbif`, `wikidata`, `estimated`, `default`, plus `model` for common names taken from the classifier's own English label file.

## species.json

```json
{"dbVersion":2026101010,"species":[{"index":0,"latin":"Betula lenta","common":"Sweet birch", ...}]}
```

Row keys match the `PlantSpecies` Room entity (`index, latin, common, family, type, description, observations, rarity, thirst, sun, growth, bloom, badges, sources`). Null fields are omitted. The per-row `dbVersion` column is filled from the top-level value when the app seeds Room. `index` is the classifier output index.
