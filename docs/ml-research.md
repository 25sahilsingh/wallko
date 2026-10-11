# ML research: plant naming (Oct 2026)

Findings so far on fixing wrong or missing plant names. Read this before touching `ml/`.
Scripts are in `tools/ml-research/`. They were recovered after a reboot wiped `/tmp`, which
held the venv, the models and the test photos, so all of those need rebuilding (see **Rebuild**).

## Status (2026-10-11)

| Item | State |
|---|---|
| "Not a plant" rule (AIY background ≥ 0.30 → no guesses; each guess must be ≥ 0.30) | In app, unit-tested, **installed on phone** |
| Pink sprite fix (style blended 0.4 with the photo's own style) | In app, installed |
| Collection "Epic" chip off screen (rarity chips now wrap with FlowRow) | In app, installed |
| BioCLIP on-device speed test (`androidTest/.../ml/BioClipDeviceTest.kt`) | **Run** on Redmi 21061119BI (8 cores, int8, 4 threads): load 794 ms, about 620–720 ms per photo. Right: lucky bamboo 0.98, tulsi 1.00, Red Campion 0.99. Wrong: areca → Nikau palm 0.37, bean → Kudzu 0.20 (no bean in the list), money plant crop → Butcher's-broom 0.35 (money plant 2nd at 0.13). Rooms: low random names 0.12–0.47 |
| BioCLIP in the real scan flow | On `main`. Hybrid as planned below, see [ml-pipeline.md](ml-pipeline.md#identify-synchronous-during-scan). Debug APK 163 → 313 MB with ONNX Runtime for 4 ABIs, 187 MB after limiting to `arm64-v8a` + `armeabi-v7a` |
| BioCLIP files on other phones | Downloaded on first run (Wi-Fi, battery not low) from GitHub release `bioclip-v1`, size + SHA-256 checked; AIY only until then. adb push to `<external files>/bioclip/` still works for development. See [ml-pipeline.md](ml-pipeline.md#getting-the-bioclip-files) |
| Bigger name list from TreeOfLife-10M precomputed embeddings (`tol_fetch.py`, `tol_eval.py`) | Tested on PC: full 109k plants 3/7 right, 44k with common names 4/7, our 2,162 4/7. Big lists lose to rare close relatives. Plan: curated 2–5k list, vectors taken from ToL (no text encoder needed) |

## The problem

The user's real plants are lucky bamboo, money plant (pothos), areca palm and tulsi. The AIY Plants V1
model (2,101 mostly wild North American species plus a `__background__` row) does not contain them,
so it either guesses wrong or scores very low.

| Photo | AIY result |
|---|---|
| Rooms or walls | background 0.66–0.79 (a good "not a plant" signal) |
| Lucky bamboo | top guess 0.05 |
| Tulsi | Chickweed 0.11 (wrong) |
| Areca palm | Nikau palm 0.58 (wrong but confident, so a threshold can't fix it) |
| Bean plant | 0.04 |
| Red Campion (in AIY) | 0.96 (correct) |

## Models checked

| Model | Verdict |
|---|---|
| dima806 houseplant ViT (Apache, 47 classes, 87 MB int8) | Right plant, but only at 6–8%. Rejected |
| MobileViT houseplants (5.6 MB) | No licence; confidently wrong. Rejected |
| PlantNet-300K | Has none of the user's three plants. Rejected |
| iNaturalist full model | Private. Not available |
| **BioCLIP** (imageomics/bioclip, ViT-B/16, MIT) | **Best**. Chosen for the on-device test |

## BioCLIP: how it works here

- **Zero-shot:** the app never trains. On the PC, every species name becomes a 512-number "text
  embedding" stored in a table. On the phone, the photo becomes a 512-number "image embedding". The
  best match is the row with the largest dot product. Adding a species means adding a table row, so
  any plant list works, including Indian plants and houseplants.
- **Image encoder:** 86.2M params. 345 MB as fp32 ONNX, **87 MB as int8** after onnxruntime
  `quantize_dynamic`. The output is already normalised, so the phone only computes dot products.
- **Text table:** 2,162 rows × 512 (AIY's 2,102 label lines, including its background row, + 60 from `houseplants.txt`). Score =
  softmax(100 × dot).
- **Prompt:** `a photo of {latin}.` tested best. Adding common names made it *worse*: "Money plant
  (Golden pothos)" ranked lower, and the old synonym *Scindapsus aureus* ranked #630.
- **Preprocessing (must match exactly):** resize the short side to 224 (bicubic), centre crop to
  224×224, mean `(0.48145466, 0.4578275, 0.40821073)`, std `(0.26862954, 0.26130258, 0.27577711)`,
  NCHW float input named `pixels`.

### BioCLIP int8 results (PC, about 0.2–0.3 s per image)

| Photo | Result |
|---|---|
| Lucky bamboo | 0.99–1.00 ✅ |
| Money plant (larger crop) | 0.77 ✅ (a tight crop works better) |
| Tulsi | 1.00 ✅ |
| Red Campion | 0.98 ✅ |
| Areca palm | 2nd at 0.25, behind Nikau palm 0.58 ⚠️ |
| Bean | Kudzu 0.72 ❌ (bean isn't in the list; add it) |
| Rooms | **Not rejected**: BioCLIP always picks *some* species |

## Planned design (hybrid; built on branch `bioclip-scan`)

1. **AIY = gate.** Keep the existing background-score rule to reject non-plants. BioCLIP can't do this.
2. **BioCLIP = namer.** If AIY says "plant", BioCLIP names it from the custom list (AIY species +
   houseplants + Indian plants).
3. Keep `MIN_GUESS_SCORE` and the confidence rule from `GameRules.Ml` on the BioCLIP scores.
4. **Runtime: ONNX Runtime Android** (`com.microsoft.onnxruntime:onnxruntime-android:1.31.0`, now
   `implementation`). TFLite conversion was abandoned because `ai-edge-torch` pulls
   GBs of CUDA libraries.
5. **Open questions:**
   - Size: 87 MB model vs. current APK. Not shipped in the APK: the app downloads it on first run
     into `<files>/bioclip/` (adb push to `<external files>/bioclip/` still works) and uses AIY until then.
   - ORT adds about 30 MB per ABI. ABIs are now limited to `arm64-v8a` + `armeabi-v7a` (no emulator libs).
   - Scan latency on a mid-range phone. The device test answers this.

## Running the on-device test

1. Rebuild the files (below), then stage them as:
   ```
   bioclip/
     bioclip_img_int8.onnx
     text_table_f32.bin      # little-endian float32, rows × 512
     text_labels.txt         # one "latin|common" per line, same order as the table
     photos/*.jpg
   ```
2. Push and run (USB is far faster than wireless adb; a 165 MB install took 30+ min over Wi-Fi):
   ```
   adb push bioclip /sdcard/Android/data/com.example.wallko/files/
   ./gradlew :app:installDebugAndroidTest   # or: adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
   adb shell am instrument -w -e class com.example.wallko.ml.BioClipDeviceTest \
       com.example.wallko.test/androidx.test.runner.AndroidJUnitRunner
   adb logcat -d -s BioClipDeviceTest
   ```
   The test skips itself if the files are missing. For each photo it logs first-run and average ms
   (4 threads) and the top 2 names.
3. The test APK is about 143 MB because ORT ships native libraries for every processor type.

## Rebuild (the `/tmp` files are gone)

- Venv: CPU-only torch (`pip install --timeout 600 torch --index-url https://download.pytorch.org/whl/cpu`),
  then `open_clip_torch onnxruntime pillow numpy onnx`. Check pip's real exit status: piping into
  `tail` hid a timeout once and pulled GPU torch.
- Download the weights: `imageomics/bioclip` from Hugging Face (`open_clip_pytorch_model.bin`).
- Export: `python tools/ml-research/bioclip_export.py <weights_dir> <aiy_labels_en.txt> <out_dir> tools/ml-research/houseplants.txt`
- Quantise and compare fp32 vs int8: `python tools/ml-research/bioclip_q.py <out_dir> photo.jpg[@x1,y1,x2,y2] ...`
  (remove its `sys.path.append('/tmp/...pylib')` line if `onnx` is installed in the venv).
- `text_table.npy` (fp16) → `text_table_f32.bin` and `text_labels.json` → `text_labels.txt`: convert
  with a few lines of numpy. The device test expects f32 little-endian and `latin|common` lines.
- Test photos: the user's own photos of bamboo, money plant, tulsi, areca palm, a bean plant and rooms
  were in `/tmp/wallko_debug` and are lost. Pull fresh ones from the phone's camera folder.

## Other scripts in `tools/ml-research/`

`classify.py` (AIY TFLite on photos), `stylize.py` and `blend.py` (Magenta style transfer and the 0.4
blend that fixed the pink sprites), `hp_test.py` (houseplant ViT check), `bioclip_test.py` and
`bioclip_rank.py` (PyTorch BioCLIP top-k, and where a given species ranks).
