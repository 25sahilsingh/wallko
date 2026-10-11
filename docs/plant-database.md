# Build-time plant database (`tools/plantdb/`)

← [Back to the plan index](PLAN.md)

Python scripts run once on the developer machine. Not part of the APK.

Runs once on the developer machine. Resumable, every raw response cached on disk.

1. `extract_labels.py` — read the 2,101 Latin names from the model's embedded metadata, keyed by output index.
2. `fetch.py` — per species: iNaturalist (common name, observation count, photo, Wikipedia), GBIF (occurrence count), Wikidata (family, description, type), Perenual when quota allows (watering, sunlight, cycle, bloom, growth, toxicity). About 1 req/s. First three sources ≈ 3 h total. Perenual trickles at 100/day.
3. `estimate.py` — for care fields still missing, batches of 50 species are sent to Claude with a strict JSON schema; results tagged `estimated`. If low confidence, type-based default tagged `default`.
4. `derive.py` — apply formulas (rarity percentiles, thirst/sun/growth mapping, badges).
5. `build.py` — write `app/src/main/assets/plants/species.json` with a `dbVersion`.

Version 1 ships with sources 1–3 plus estimates. Perenual values replace estimates in later rebuilds.

Licensing note: iNaturalist photos carry per-photo licences. The app shows the **user's own photo** on cards. Reference photos are only used by the script for the developer's reference, not shipped.
