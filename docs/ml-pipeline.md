# On-device ML pipeline

← [Back to the plan index](PLAN.md)

How a photo becomes an identified plant and a stylised potted sprite.

## Identify (synchronous, during scan)
1. CameraX capture → rotate to upright → centre-crop → resize 224×224 → RGB bytes.
2. AIY model (`assets/models/aiy_plants_v1.tflite`, 5 MB, input `[1,224,224,3] uint8`, output `[1,2102] uint8`).
3. Divide by 255, drop the background class (the **last** output, index 2101, label `__background__`), take top-3. Species index = model output index 0..2100.
4. Confidence rule: top-1 ≥ 0.35 and (top-1 − top-2) ≥ 0.10 → show as confident. Otherwise show all three as "could be" and invite a retake. Thresholds live in `GameRules`.
   - **Not a plant / unknown plant:** if the background score is ≥ 0.30 show no guesses; otherwise only guesses scoring ≥ 0.30 (`MIN_GUESS_SCORE`) are shown, and if none are left show no guesses ("No plant found") and only offer a retake, so random photos can't be collected. Measured: room/curtain photos score background 0.66–0.79; lucky bamboo (not in AIY) scored a best species of 0.05; tulsi 0.11 (wrong); Red Campion 0.96 (right).
5. Labels come from the model's embedded metadata (also exported into the species JSON by index).

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
