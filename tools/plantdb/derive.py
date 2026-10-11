#!/usr/bin/env python3
"""Merge every source into final species rows (no network).

Precedence per field: perenual > inaturalist/gbif/wikidata > estimated > default.
Each row carries `sources`: field -> perenual|inaturalist|gbif|wikidata|estimated|default|model
("model" = taken from the classifier's own label file, used only for common names).

Rarity: percentile rank of worldwide observation count, most observed first, cut at the
GameRules boundaries (Common <40, Uncommon <70, Rare <90, Epic <98, Legendary >=98), so the
fewest observations = Legendary. Species with no count get the median count (tagged default);
ties are broken by a stable hash of the Latin name so a labels-only build still spreads tiers.

Run standalone to write cache/derived.json and print a summary; build.py imports derive_all().
"""
from __future__ import annotations

import hashlib
import html
import re
import statistics
import sys

from common import CACHE, GAME_RULES, MONTHS, TYPES, load_labels, read_json, write_json
import fetch

ESTIMATES = CACHE / "estimates.json"
DERIVED = CACHE / "derived.json"

# ---------------------------------------------------------------- type heuristics

FAMILY_TYPE = {
    "Cactaceae": "cactus", "Orchidaceae": "orchid", "Poaceae": "grass", "Cyperaceae": "grass",
    "Juncaceae": "grass", "Typhaceae": "grass", "Crassulaceae": "succulent", "Aizoaceae": "succulent",
    "Asphodelaceae": None, "Nymphaeaceae": "aquatic", "Pontederiaceae": "aquatic",
    "Potamogetonaceae": "aquatic", "Hydrocharitaceae": "aquatic", "Nelumbonaceae": "aquatic",
    "Lemnaceae": "aquatic", "Alismataceae": "aquatic", "Menyanthaceae": "aquatic",
    "Pinaceae": "tree", "Cupressaceae": "tree", "Fagaceae": "tree", "Betulaceae": "tree",
    "Juglandaceae": "tree", "Sapindaceae": None, "Arecaceae": "tree", "Araucariaceae": "tree",
    "Podocarpaceae": "tree", "Taxaceae": "tree", "Salicaceae": None, "Ericaceae": "shrub",
    "Polypodiaceae": "fern", "Dryopteridaceae": "fern", "Pteridaceae": "fern", "Aspleniaceae": "fern",
    "Blechnaceae": "fern", "Athyriaceae": "fern", "Dennstaedtiaceae": "fern", "Osmundaceae": "fern",
    "Thelypteridaceae": "fern", "Onocleaceae": "fern", "Cyatheaceae": "fern", "Dicksoniaceae": "fern",
    "Hymenophyllaceae": "fern", "Lygodiaceae": "fern", "Ophioglossaceae": "fern",
    "Woodsiaceae": "fern", "Cystopteridaceae": "fern", "Lindsaeaceae": "fern", "Gleicheniaceae": "fern",
    "Equisetaceae": "other", "Lycopodiaceae": "other", "Selaginellaceae": "other",
    "Vitaceae": "vine", "Passifloraceae": "vine", "Cucurbitaceae": "vine",
    "Convolvulaceae": None, "Apocynaceae": None,
    "Asteraceae": "herb", "Lamiaceae": "herb", "Apiaceae": "herb", "Brassicaceae": "herb",
    "Ranunculaceae": "herb", "Caryophyllaceae": "herb", "Geraniaceae": "herb", "Violaceae": "herb",
    "Orobanchaceae": "herb", "Plantaginaceae": "herb", "Boraginaceae": "herb", "Liliaceae": "herb",
    "Iridaceae": "herb", "Amaryllidaceae": "herb", "Campanulaceae": "herb", "Polygonaceae": "herb",
    "Droseraceae": "herb", "Sarraceniaceae": "herb", "Lentibulariaceae": "herb",
}

# Keywords in common or Latin names -> type. First match wins; checked as whole words.
KEYWORD_TYPE = [
    ("cactus", "cactus"), ("cholla", "cactus"), ("prickly pear", "cactus"), ("saguaro", "cactus"),
    ("hedgehog cactus", "cactus"), ("barrel", "cactus"),
    ("orchid", "orchid"), ("lady's slipper", "orchid"), ("ladies' tresses", "orchid"), ("helleborine", "orchid"),
    ("fern", "fern"), ("maidenhair", "fern"), ("bracken", "fern"), ("spleenwort", "fern"), ("polypody", "fern"),
    ("moss", "other"), ("lichen", "other"), ("liverwort", "other"), ("horsetail", "other"),
    ("clubmoss", "other"), ("spikemoss", "other"), ("quillwort", "other"),
    ("water lily", "aquatic"), ("waterlily", "aquatic"), ("pondweed", "aquatic"), ("duckweed", "aquatic"),
    ("lotus", "aquatic"), ("eelgrass", "aquatic"), ("seagrass", "aquatic"), ("water hyacinth", "aquatic"),
    ("grass", "grass"), ("sedge", "grass"), ("rush", "grass"), ("bamboo", "grass"), ("reed", "grass"),
    ("cattail", "grass"), ("bulrush", "grass"),
    ("stonecrop", "succulent"), ("sedum", "succulent"), ("houseleek", "succulent"), ("agave", "succulent"),
    ("aloe", "succulent"), ("echeveria", "succulent"), ("ice plant", "succulent"), ("yucca", "succulent"),
    ("vine", "vine"), ("ivy", "vine"), ("creeper", "vine"), ("grape", "vine"), ("clematis", "vine"),
    ("honeysuckle", "vine"), ("morning glory", "vine"), ("bindweed", "vine"), ("wisteria", "vine"),
    ("tree", "tree"), ("oak", "tree"), ("maple", "tree"), ("pine", "tree"), ("spruce", "tree"),
    ("fir", "tree"), ("birch", "tree"), ("elm", "tree"), ("ash", "tree"), ("willow", "tree"),
    ("poplar", "tree"), ("aspen", "tree"), ("cedar", "tree"), ("cypress", "tree"), ("juniper", "tree"),
    ("palm", "tree"), ("eucalyptus", "tree"), ("gum", "tree"), ("hickory", "tree"), ("walnut", "tree"),
    ("chestnut", "tree"), ("beech", "tree"), ("alder", "tree"), ("hemlock", "tree"), ("larch", "tree"),
    ("magnolia", "tree"), ("linden", "tree"), ("basswood", "tree"), ("sycamore", "tree"), ("redwood", "tree"),
    ("acacia", "tree"), ("wattle", "tree"), ("mahogany", "tree"), ("ebony", "tree"), ("fig", "tree"),
    ("cherry", "tree"), ("plum", "tree"), ("apple", "tree"), ("pear", "tree"), ("mesquite", "tree"),
    ("shrub", "shrub"), ("bush", "shrub"), ("rose", "shrub"), ("currant", "shrub"), ("gooseberry", "shrub"),
    ("blueberry", "shrub"), ("huckleberry", "shrub"), ("heath", "shrub"), ("heather", "shrub"),
    ("azalea", "shrub"), ("rhododendron", "shrub"), ("manzanita", "shrub"), ("sagebrush", "shrub"),
    ("hawthorn", "shrub"), ("sumac", "shrub"), ("viburnum", "shrub"), ("dogwood", "shrub"),
    ("elderberry", "shrub"), ("holly", "shrub"), ("laurel", "shrub"), ("broom", "shrub"), ("gorse", "shrub"),
    ("bramble", "shrub"), ("blackberry", "shrub"), ("raspberry", "shrub"), ("indigo", "shrub"),
    ("daisy", "herb"), ("aster", "herb"), ("lily", "herb"), ("violet", "herb"), ("buttercup", "herb"),
    ("clover", "herb"), ("thistle", "herb"), ("mint", "herb"), ("poppy", "herb"), ("iris", "herb"),
    ("goldenrod", "herb"), ("sunflower", "herb"), ("lupine", "herb"), ("milkweed", "herb"),
    ("primrose", "herb"), ("sundew", "herb"), ("pitcher plant", "herb"), ("wildflower", "herb"),
]
GENUS_TYPE = {
    "Quercus": "tree", "Acer": "tree", "Pinus": "tree", "Picea": "tree", "Abies": "tree", "Betula": "tree",
    "Fraxinus": "tree", "Ulmus": "tree", "Populus": "tree", "Eucalyptus": "tree", "Juglans": "tree",
    "Carya": "tree", "Tilia": "tree", "Fagus": "tree", "Salix": "tree", "Prunus": "tree", "Magnolia": "tree",
    "Ficus": "tree", "Acacia": "tree", "Juniperus": "tree", "Cupressus": "tree", "Larix": "tree", "Tsuga": "tree",
    "Opuntia": "cactus", "Cylindropuntia": "cactus", "Echinocereus": "cactus", "Echinocactus": "cactus",
    "Ferocactus": "cactus", "Mammillaria": "cactus", "Carnegiea": "cactus",
    "Sedum": "succulent", "Echeveria": "succulent", "Aloe": "succulent", "Agave": "succulent",
    "Crassula": "succulent", "Dudleya": "succulent", "Sempervivum": "succulent", "Kalanchoe": "succulent",
    "Carex": "grass", "Juncus": "grass", "Cyperus": "grass", "Poa": "grass", "Festuca": "grass",
    "Nymphaea": "aquatic", "Nuphar": "aquatic", "Potamogeton": "aquatic", "Lemna": "aquatic",
    "Equisetum": "other", "Lycopodium": "other", "Selaginella": "other", "Sphagnum": "other",
    "Polytrichum": "other", "Dicranum": "other", "Hypnum": "other",
    "Asplenium": "fern", "Polypodium": "fern", "Dryopteris": "fern", "Pteridium": "fern",
    "Adiantum": "fern", "Polystichum": "fern", "Athyrium": "fern", "Blechnum": "fern", "Osmunda": "fern",
    "Hymenophyllum": "fern", "Cyathea": "fern",
    "Vitis": "vine", "Hedera": "vine", "Lonicera": "vine", "Clematis": "vine", "Parthenocissus": "vine",
    "Rosa": "shrub", "Rubus": "shrub", "Ribes": "shrub", "Vaccinium": "shrub", "Rhododendron": "shrub",
    "Arctostaphylos": "shrub", "Ceanothus": "shrub", "Salvia": "herb", "Artemisia": "shrub",
}
CARNIVOROUS_GENERA = {"Drosera", "Dionaea", "Sarracenia", "Nepenthes", "Pinguicula", "Utricularia",
                      "Darlingtonia", "Drosophyllum", "Byblis", "Cephalotus", "Heliamphora", "Genlisea",
                      "Aldrovanda"}
CARNIVOROUS_FAMILIES = {"Droseraceae", "Sarraceniaceae", "Lentibulariaceae", "Nepenthaceae",
                        "Cephalotaceae", "Byblidaceae", "Drosophyllaceae"}
EVERGREEN_FAMILIES = {"Pinaceae", "Cupressaceae", "Araucariaceae", "Podocarpaceae", "Taxaceae",
                      "Cactaceae", "Arecaceae"}

DECIDUOUS_CONIFERS = {"Larix", "Taxodium", "Metasequoia", "Glyptostrobus", "Pseudolarix"}

# Per-type defaults: thirst 1-5, sun 1-3, growth.
TYPE_DEFAULTS = {
    "tree": (3, 3, "SLOW"), "shrub": (3, 3, "MEDIUM"), "herb": (3, 3, "FAST"), "grass": (2, 3, "FAST"),
    "fern": (4, 1, "MEDIUM"), "succulent": (1, 3, "SLOW"), "cactus": (1, 3, "SLOW"),
    "vine": (3, 2, "FAST"), "aquatic": (5, 3, "FAST"), "orchid": (3, 2, "SLOW"), "other": (4, 2, "MEDIUM"),
}

GENERIC_WIKIDATA = re.compile(r"^(species|genus|subspecies|variety|taxon) of [\w\s-]+$", re.I)
SEASON_MONTHS = {"spring": [3, 4, 5], "summer": [6, 7, 8], "fall": [9, 10, 11], "autumn": [9, 10, 11],
                 "winter": [12, 1, 2]}


def keyword_type(common: str | None, latin: str) -> str | None:
    genus = latin.split()[0]
    if genus in GENUS_TYPE:
        return GENUS_TYPE[genus]
    text = f" {(common or '').lower()} "
    for kw, t in KEYWORD_TYPE:
        if re.search(rf"(?<![a-z]){re.escape(kw)}s?(?![a-z])", text):
            return t
    return None


def perenual_type(s: str) -> str | None:
    s = s.lower()
    for kw, t in [("cactus", "cactus"), ("succulent", "succulent"), ("orchid", "orchid"), ("fern", "fern"),
                  ("grass", "grass"), ("sedge", "grass"), ("rush", "grass"), ("bamboo", "grass"),
                  ("aquatic", "aquatic"), ("water", "aquatic"), ("vine", "vine"), ("climber", "vine"),
                  ("creeper", "vine"), ("tree", "tree"), ("conifer", "tree"), ("palm", "tree"),
                  ("shrub", "shrub"), ("bush", "shrub"), ("herb", "herb"), ("flower", "herb"),
                  ("wildflower", "herb"), ("perennial", "herb"), ("annual", "herb"), ("bulb", "herb")]:
        if kw in s:
            return t
    return None


def first_sentence(text: str | None, limit: int = 160) -> str | None:
    if not text:
        return None
    text = re.sub(r"<[^>]+>", "", html.unescape(text))
    text = re.sub(r"\s*\([^)]*\)", "", text)  # drop parentheticals (authorities, pronunciations)
    text = re.sub(r"\s+", " ", text).strip()
    m = re.match(r"(.+?[.!?])(\s|$)", text)
    s = m.group(1) if m else text
    if len(s) > limit:
        cut = s[: limit - 1].rsplit(" ", 1)[0].rstrip(",;:")
        s = cut + "…"
    return s or None


def rarity_bounds() -> list[int]:
    text = GAME_RULES.read_text(encoding="utf-8")
    names = ["COMMON_MAX_PERCENTILE", "UNCOMMON_MAX_PERCENTILE", "RARE_MAX_PERCENTILE", "EPIC_MAX_PERCENTILE"]
    out = []
    for n in names:
        m = re.search(rf"{n}\s*=\s*(\d+)", text)
        if not m:
            raise SystemExit(f"{n} not found in {GAME_RULES}")
        out.append(int(m.group(1)))
    return out


def tier_for(percentile: float, bounds: list[int]) -> str:
    for b, name in zip(bounds, ["COMMON", "UNCOMMON", "RARE", "EPIC"]):
        if percentile < b:
            return name
    return "LEGENDARY"


def stable_hash(s: str) -> int:
    return int.from_bytes(hashlib.sha1(s.encode()).digest()[:8], "big")


# ---------------------------------------------------------------- main merge

def derive_all() -> list[dict]:
    estimates = read_json(ESTIMATES, {}) or {}
    labels = load_labels()
    rows = []
    for sp in labels:
        i, latin = sp["index"], sp["latin"]
        inat, gbif, wd, per = (fetch.parse_inat(i), fetch.parse_gbif(i),
                               fetch.parse_wikidata(i), fetch.parse_perenual(i))
        est = estimates.get(str(i))
        if est and est.get("confidence") == "low":
            est = None  # low confidence -> fall back to type defaults (docs/plant-database.md)
        src: dict[str, str] = {}
        row = {"index": i, "latin": latin}

        # common name: iNat's preferred English name reads best; then the model label; then Perenual.
        common = None
        for val, tag in [(inat.get("common"), "inaturalist"), (sp.get("common_en"), "model"),
                         (per.get("common_name"), "perenual")]:
            if val:
                common, src["common"] = val[0].upper() + val[1:], tag
                break
        row["common"] = common

        family = None
        for val, tag in [(per.get("family"), "perenual"), (inat.get("family"), "inaturalist"),
                         (gbif.get("family"), "gbif"), (wd.get("family"), "wikidata")]:
            if val:
                family, src["family"] = val, tag
                break
        row["family"] = family

        ptype = None
        for val, tag in [(perenual_type(per["type"]) if per.get("type") else None, "perenual"),
                         (FAMILY_TYPE.get(family) if family else None, src.get("family")),
                         (est["type"] if est else None, "estimated"),
                         (keyword_type(common, latin), "default")]:
            if val:
                ptype, src["type"] = val, tag
                break
        if ptype is None:  # nearly all classifier species are flowering plants
            ptype, src["type"] = "herb", "default"
        row["type"] = ptype
        d_thirst, d_sun, d_growth = TYPE_DEFAULTS[ptype]

        desc = None
        wd_desc = wd.get("description")
        if wd_desc and GENERIC_WIKIDATA.match(wd_desc.strip()):
            wd_desc = None
        for val, tag in [(first_sentence(per.get("description")), "perenual"),
                         (first_sentence(inat.get("wikipedia_summary")), "inaturalist"),
                         (est["description"] if est else None, "estimated"),
                         (first_sentence(wd_desc), "wikidata")]:
            if val:
                desc, src["description"] = val, tag
                break
        row["description"] = desc

        row["_obs"] = (inat.get("observations"), gbif.get("occurrences"))

        # thirst
        watering = {"none": 1, "minimum": 2, "average": 3, "frequent": 4}.get(str(per.get("watering", "")).lower())
        if ptype == "aquatic" and watering:
            watering = 5
        if watering:
            row["thirst"], src["thirst"] = watering, "perenual"
        elif est:
            row["thirst"], src["thirst"] = est["thirst"], "estimated"
        else:
            row["thirst"], src["thirst"] = d_thirst, "default"

        # sun
        sun = None
        if per.get("sunlight"):
            levels = " | ".join(s.strip().lower() for s in per["sunlight"])
            if "full sun" in levels or re.search(r"(^|\| )sun($| \|)", levels):
                sun = 3
            elif "part" in levels or "filtered" in levels:
                sun = 2
            elif "shade" in levels:
                sun = 1
        if sun:
            row["sun"], src["sun"] = sun, "perenual"
        elif est:
            row["sun"], src["sun"] = est["sun"], "estimated"
        else:
            row["sun"], src["sun"] = d_sun, "default"

        growth = {"high": "FAST", "moderate": "MEDIUM", "medium": "MEDIUM", "low": "SLOW"}.get(
            str(per.get("growth_rate", "")).lower())
        if growth:
            row["growth"], src["growth"] = growth, "perenual"
        elif est:
            row["growth"], src["growth"] = est["growth"].upper(), "estimated"
        else:
            row["growth"], src["growth"] = d_growth, "default"

        # bloom (months, northern-hemisphere seasons for Perenual)
        bloom = None
        if per.get("flowering_season"):
            months = SEASON_MONTHS.get(str(per["flowering_season"]).strip().lower())
            if months:
                bloom, src["bloom"] = months, "perenual"
        elif per.get("flowers") is False:
            bloom, src["bloom"] = [], "perenual"
        if bloom is None and est:
            bloom, src["bloom"] = est["bloom"], "estimated"
        if bloom is None:
            bloom, src["bloom"] = [], "default"
        row["bloom"] = [MONTHS[m - 1] for m in sorted(set(bloom))]

        # badges: only when a source supports them
        badges, btags = [], []
        toxic = None
        if "poisonous_to_humans" in per or "poisonous_to_pets" in per:
            toxic = bool(per.get("poisonous_to_humans")) or bool(per.get("poisonous_to_pets"))
            ttag = "perenual"
        elif est and est.get("toxic") is not None:
            toxic, ttag = est["toxic"], "estimated"
        if toxic:
            badges.append("toxic"); btags.append(ttag)
        genus = latin.split()[0]
        if genus in CARNIVOROUS_GENERA or family in CARNIVOROUS_FAMILIES:
            badges.append("carnivorous"); btags.append(src.get("family", "default") if family in CARNIVOROUS_FAMILIES else "default")
        if (per.get("type") and "evergreen" in per["type"].lower()):
            badges.append("evergreen"); btags.append("perenual")
        elif family in EVERGREEN_FAMILIES and genus not in DECIDUOUS_CONIFERS:
            badges.append("evergreen"); btags.append(src.get("family", "default"))
        detail = (fetch.cached("perenual", "detail", i) or {}).get("body") or {}
        if isinstance(detail, dict):
            if detail.get("drought_tolerant") is True:
                badges.append("drought-tolerant"); btags.append("perenual")
            attracts = " ".join(a.lower() for a in detail.get("attracts") or [] if isinstance(a, str))
            if any(k in attracts for k in ("bee", "butterfl", "hummingbird", "pollinator")):
                badges.append("pollinator-friendly"); btags.append("perenual")
        row["badges"] = badges
        if badges:
            order = ["perenual", "inaturalist", "gbif", "wikidata", "estimated", "default"]
            src["badges"] = min(btags, key=lambda t: order.index(t) if t in order else 99)
        row["sources"] = src
        rows.append(row)

    assign_rarity(rows)
    return rows


def assign_rarity(rows: list[dict]) -> None:
    # Fill missing iNat counts from GBIF, scaled by the median iNat/GBIF ratio when both exist.
    both = [inat / gbif for inat, gbif in (r["_obs"] for r in rows) if inat and gbif]
    ratio = statistics.median(both) if both else 1.0
    known = []
    for r in rows:
        inat, gbif = r["_obs"]
        if inat is not None:
            r["observations"], r["sources"]["observations"] = int(inat), "inaturalist"
        elif gbif is not None:
            r["observations"], r["sources"]["observations"] = int(round(gbif * ratio)), "gbif"
        else:
            r["observations"] = None
        if r["observations"] is not None:
            known.append(r["observations"])
    median = int(statistics.median(known)) if known else 0
    for r in rows:
        if r["observations"] is None:
            r["observations"], r["sources"]["observations"] = median, "default"
        del r["_obs"]

    bounds = rarity_bounds()
    ranked = sorted(rows, key=lambda r: (-r["observations"], stable_hash(r["latin"])))
    n = len(ranked)
    for rank, r in enumerate(ranked):
        r["rarity"] = tier_for(100.0 * rank / n, bounds)
        r["sources"]["rarity"] = r["sources"]["observations"]


def main() -> int:
    rows = derive_all()
    write_json(DERIVED, rows)
    from collections import Counter
    print(f"{len(rows)} species -> {DERIVED}")
    print("rarity:", dict(Counter(r["rarity"] for r in rows)))
    print("type:", dict(Counter(r["type"] for r in rows)))
    for field in ("common", "family", "type", "description", "observations", "thirst", "sun", "growth", "bloom"):
        print(f"  {field:12s}", dict(Counter(r["sources"].get(field, "-") for r in rows)))
    print("badges:", dict(Counter(b for r in rows for b in r["badges"])))
    return 0


if __name__ == "__main__":
    sys.exit(main())
