# On-device ML pipeline

← [Back to the plan index](PLAN.md)

How a photo becomes an identified plant and a stylised potted sprite.

## Identify (synchronous, during scan)
`HybridPlantClassifier`: **AIY is the gate, BioCLIP names the plant** when its files are on the phone. The scan sheet shows "Identifying…" meanwhile; both models run on their own background threads.

1. CameraX capture → rotate to upright (≤ 1600 px long edge). This bitmap feeds both models.
2. **AIY** (`assets/models/aiy_plants_v1.tflite`, 5 MB): centre-crop → resize 224×224 → RGB bytes, input `[1,224,224,3] uint8`, output `[1,2102] uint8`, divided by 255. The background class is the **last** output (index 2101, label `__background__`).
3. **Not a plant:** if the background score is ≥ 0.30 (`MAX_BACKGROUND_SCORE`) show no guesses and only offer a retake. BioCLIP is not run. Measured: room/curtain photos score background 0.66–0.79.
4. **BioCLIP** (only if the photo passed step 3 and the files are present): read from `<external files>/bioclip/` (`bioclip_img_int8.onnx` 87 MB, `text_table_f32.bin` little-endian float32 rows × 512, `text_labels.txt` one `latin|common` per row). Not in the APK. Loaded once on first scan (ONNX Runtime, 4 threads; about 0.8 s), then about 0.65 s per photo on the Redmi test phone.
   - Preprocessing (CLIP): halve while the short side is ≥ 448, filtered-resize the short side to 224, centre crop 224², `/255`, mean `(0.48145466, 0.4578275, 0.40821073)`, std `(0.26862954, 0.26130258, 0.27577711)`, NCHW float input `pixels`. Android's filtered resize is not PIL's bicubic, so scores can differ slightly from the PC.
   - Score per row = softmax(100 × image·row). Both vectors are already L2-normalised.
   - Row → species: rows 0..2100 are AIY's labels in AIY order, so they are the same species index (checked by name at load; a mismatch disables BioCLIP). Row 2101 is AIY's background and is never a guess. Later rows (houseplants) are matched **by Latin name** to the species catalogue, so a houseplant that AIY already has (coconut, monstera…) lands on the AIY species; `Sansevieria trifasciata` is folded into `Dracaena trifasciata`. Rows that map to the same species have their scores **summed**, because identical prompts split the probability. Rows with no species are dropped.
   - Top-3 species, then the same rules as AIY (step 5).
5. **Guess rules** (`GameRules`): only guesses scoring ≥ 0.30 (`MIN_GUESS_SCORE`) are shown; none left → "No plant found" and only a retake, so random photos and wild guesses can't be collected. Confident when top-1 ≥ 0.35 and (top-1 − unfiltered top-2) ≥ 0.10; otherwise all shown guesses are "could be" with a retake invite.
6. **Fallback:** BioCLIP files missing → AIY's own top-3 with the rules in step 5, exactly as before (files are checked again on the next scan). BioCLIP fails to load → AIY only until the app restarts. BioCLIP fails on one photo → AIY for that photo.
7. Guesses carry the **species index**. AIY species are 0..2100 (= AIY output index). Houseplants AIY lacks are 2102+ (2101 stays unused), hand-written in `tools/plantdb/extra_species.json` and appended to `species.json` by `build.py`; they are COMMON with no observation count.
8. AIY labels come from the model's embedded metadata (also exported into the species JSON by index). BioCLIP names come from `text_labels.txt`; the card uses the catalogue's names.

## Make the potted sprite (asynchronous, WorkManager, after the card exists)
1. ML Kit subject segmentation on the original photo → foreground mask + bounding box.
2. Crop to the box with padding, resize to 384×384 (letterbox on Cream).
3. Style transfer: style vector `[1,1,1,100]` is precomputed once per bundled style image (resized to 256×256 for `style_predict.tflite`) and cached in DataStore; transformer takes content `[1,384,384,3] float` + vector → stylised `[1,384,384,3]`. The vector actually used is blended: `0.4 × style + 0.6 × the photo's own style vector` (`GameRules.Ml.STYLE_STRENGTH`). Pure style (1.0) repainted plants in the style image's pink/blue blobs.
4. Multiply alpha by the resized mask, feather edges 2 px, trim transparent borders.
5. Save `sprite_<id>.png` in internal storage; store path on `OwnedPlant`. Garden shows a seedling until this lands, then blooms it in.
6. Three bundled style images (`assets/styles/`, 384×384): watercolour, flat pastel, crayon. Generated in-house from the Sunny Meadow palette (no third-party licence); swap for public-domain artworks later if stylisation looks weak. Changing style in Settings re-queues sprite jobs.

Models are checked into `app/src/main/assets/models/` (total ≈ 7.8 MB): `aiy_plants_v1.tflite`, `style_predict.tflite`, `style_transfer.tflite`. All Apache 2.0. The Magenta pair is int8-weight but takes and returns **float32** (transfer output in 0..1; input 0 = content, input 1 = style vector). The AIY model embeds `0-labels.txt` and `0-labels-en.txt` (2,102 lines each, background last). They are also extracted as `assets/models/aiy_plants_labels.txt` / `aiy_plants_labels_en.txt`, which the app reads (`litert-metadata` is not used: its manifest namespace clashes with `litert-support`). Sources, sizes and checksums: [`assets/ASSETS.md`](../app/src/main/assets/ASSETS.md).

## Research

Plant-naming research (BioCLIP, houseplants, on-device test): see [ml-research.md](ml-research.md).
