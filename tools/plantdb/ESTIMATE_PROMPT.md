# Estimating plant care fields (instructions for an LLM agent)

You are filling in care data for one batch of plant species for **Wallko**, a casual plant-collecting game. Players will read the result, so be accurate, and be honest when you are unsure.

## Input

You get one file, `tools/plantdb/cache/estimate_batches/batch_NNN.json`:

```json
{"batch": 3, "species": [
  {"index": 451, "latin": "Urtica dioica", "common": "Stinging nettle",
   "family": "Urticaceae", "type_hint": "Herb", "wikidata": "species of plant"}
]}
```

Only `index` and `latin` are always present. `common`, `family`, `type_hint` and `wikidata` are hints and may be missing.

## Output

Write **one file**, `tools/plantdb/cache/estimate_answers/batch_NNN.json`, using the same `NNN` as the input. It holds a JSON **array** with exactly one object per input species, in the same order. Write nothing else: no markdown fences, no comments, no trailing commas.

```json
[
  {"index": 451, "type": "herb", "thirst": 4, "sun": 2, "growth": "fast",
   "bloom": [6, 7, 8, 9], "description": "A tough leafy herb whose tiny hairs sting when touched; caterpillars love it.",
   "toxic": false, "confidence": "high"}
]
```

Every object must have exactly these keys:

| key | type | rule |
|---|---|---|
| `index` | int | Copied from the input. Never change it. |
| `type` | string | One of `tree`, `shrub`, `herb`, `grass`, `fern`, `succulent`, `cactus`, `vine`, `aquatic`, `orchid`, `other`. Use `herb` for non-woody flowering plants and wildflowers, `grass` for grasses, sedges and rushes, and `other` for mosses, lichens, liverworts, horsetails and anything that fits nowhere else. |
| `thirst` | int 1–5 | How much water it wants. **1** = cactus-dry (desert plants, drought-adapted succulents). **2** = drought tolerant (Mediterranean shrubs, dry-prairie plants). **3** = average garden plant. **4** = likes moist soil (woodland ferns, streamside plants). **5** = bog or aquatic plant (water lilies, sundews, sphagnum). |
| `sun` | int 1–3 | Light it prefers in the wild. **1** = shade (forest floor). **2** = partial shade or dappled light. **3** = full sun. |
| `growth` | string | `fast` (annuals, weeds, most grasses, vigorous vines), `medium` (most perennials and shrubs), `slow` (most trees, cacti, cycads, many orchids and conifers). |
| `bloom` | int array | Months it usually flowers in its **native range**, 1 = January … 12 = December, ascending, no duplicates. For Southern Hemisphere natives, give the months where they grow. Use `[]` for plants that never flower (ferns, mosses, conifers, cycads, horsetails) or when you have no idea. |
| `description` | string | One friendly, factual sentence of at most **160 characters**, written for a curious player. Mention something memorable: a use, a smell, a habitat or a quirk. Don't open with the plant's name. Don't invent facts. |
| `toxic` | bool or null | `true` if any part is notably poisonous to people or pets when eaten, `false` if it is known to be safe or edible, `null` if you don't know. |
| `confidence` | string | `high` if you know this species well. `medium` if you are reasoning from the genus or family. `low` if you are mostly guessing. |

## Rules

- **Be honest.** If you don't recognise the species, estimate from the genus, family or name, and set `confidence` to `low` (or `medium` if you know the genus well). Low-confidence answers are replaced by safe defaults, so a low rating is useful, not a failure.
- Describe the wild species, not a cultivar.
- Use the hints, but trust your knowledge if a hint is clearly wrong (for example `wikidata: "species of insect"`).
- Plain ASCII or UTF-8 text. No HTML and no line breaks in `description`.
- Before you finish, check that the array length equals the input `species` length, that every `index` matches, and that every `description` is 160 characters or fewer.

After every answer file is written, the developer runs `python3 estimate.py merge`, which rejects any object that breaks these rules.
