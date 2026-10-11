# Wallko

A cosy Android game that gets you outside. Go for a walk, scan real plants with the camera, collect
them as cards and grow them in a little virtual garden. Walking fills your water tank and the plants
drink from it on their own. Stop walking and they wilt, then wither. Walking into places you have
never been earns elixir, the only thing that can bring a withered plant back.

Everything runs on the phone. There is no account and no backend. Walks, plants and explored areas
never leave the device. The only network use is map tiles (OpenFreeMap), the ML Kit segmentation
model (fetched by Google Play services).

- **Scan:** live camera, then pick from the top three guesses. The plant is cut out (ML Kit subject
  segmentation) and repainted in one of three styles (Magenta style transfer) as a potted sprite.
- **Garden, Collection:** a meadow with snap-to-grid plots and pot shelves, plus a card collection
  (2,100+ species) with rarity based on worldwide observation counts.
- **Walk, Map:** GPS walks in a foreground service, passive daily steps from Health Connect, and a
  fog-of-war map of the 100 m cells you have explored.

The full design lives in [`docs/`](docs/PLAN.md).

## Requirements

| | Version |
|---|---|
| Phone | Android 10 (API 29) or newer, with a camera. Target SDK 36. |
| Android Studio | A recent version that supports AGP 9.1 (Gradle 9.3.1 is fetched by the wrapper) |
| JDK | 21. The Gradle daemon toolchain is pinned to 21 and is downloaded automatically (foojay) if missing. Android Studio's bundled JBR 21 also works. |
| Android SDK | Platform **37** (compile SDK 37, minor 2) and current build tools, installed from the SDK Manager |
| Toolchain | AGP 9.1.1, Kotlin 2.4.21, KSP, Compose BOM 2026.09.00 (see `gradle/libs.versions.toml`) |

No API keys are needed to build or run the app.

## Build and install

With Android Studio: **File > Open** the repository folder, let Gradle sync, choose the `app` run
configuration and press Run with a phone connected (USB debugging on).

From the command line (create `local.properties` with `sdk.dir=/path/to/Android/Sdk`, or set
`ANDROID_HOME`):

```sh
./gradlew assembleDebug                 # APK in app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug                  # build and install on the connected phone
adb install -r app/build/outputs/apk/debug/app-debug.apk   # or install an existing APK

./gradlew testDebugUnitTest             # JVM unit tests
./gradlew connectedDebugAndroidTest     # instrumented tests (needs a phone or emulator)
```

The release build type has no signing config. Add your own (and keep the keystore out of git) if you
want a release APK.

## Plant-naming model (BioCLIP)

Wallko ships with Google's **AIY Plants V1** classifier (about 5 MB, inside the APK). It decides
whether the photo shows a plant at all and names it when nothing better is available.

The better namer is **BioCLIP** (int8 ONNX, about 92 MB in total with its label table). It is not in
the APK. Automatic download on first run is planned but **not built yet**. For now, get the three files
(`bioclip_img_int8.onnx`, `text_table_f32.bin`, `text_labels.txt`) from the GitHub release
[`bioclip-v1`](https://github.com/25sahilsingh/wallko/releases/tag/bioclip-v1) and copy them to the phone:

```sh
adb push bioclip_img_int8.onnx text_table_f32.bin text_labels.txt \
    /sdcard/Android/data/com.example.wallko/files/bioclip/
```

Without these files the app works normally with the built-in AIY model only. AIY always acts as the
"is this a plant?" gate; BioCLIP only renames photos that pass it. Details:
[`docs/ml-pipeline.md`](docs/ml-pipeline.md).

## Permissions

| Permission | Why |
|---|---|
| `CAMERA` | Scanning plants (live camera only, no gallery import) |
| `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION` | Tracking walks and the explored-area map; place name on a card |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_LOCATION` | GPS tracking only while a walk you started is running |
| `ACTIVITY_RECOGNITION` | Step counting from the phone's sensor |
| `android.health.permission.READ_STEPS` | Passive daily steps from Health Connect (optional) |
| `POST_NOTIFICATIONS`, `VIBRATE` | Walk notification, morning nudge and wilting alerts (can be turned off) |
| `INTERNET`, `ACCESS_NETWORK_STATE` | Map tiles |

Permissions are asked one at a time during onboarding, each with an explanation.

## Repository layout

| Path | What |
|---|---|
| `app/` | The Android app (single module, Kotlin + Jetpack Compose, Hilt, Room, CameraX, LiteRT, ONNX Runtime, MapLibre) |
| `app/src/main/assets/` | Bundled models, `plants/species.json` (card data), style images, licence texts. See [`ASSETS.md`](app/src/main/assets/ASSETS.md). |
| [`docs/`](docs/PLAN.md) | Plan, game rules, design system, screens, architecture, data model, ML pipeline, testing, risks |
| [`tools/plantdb/`](tools/plantdb/README.md) | Build-time Python scripts that produce `species.json` from iNaturalist, GBIF, Wikidata and (optionally) Perenual. A Perenual API key goes in `tools/plantdb/.env` (`PERENUAL_API_KEY=...`), which is gitignored. Without a key, that source is skipped. |
| `tools/ml-research/` | Python scripts used to evaluate and export the plant models. [`docs/ml-research.md`](docs/ml-research.md) describes the results and how to **rebuild the BioCLIP model** (download `imageomics/bioclip` weights, `bioclip_export.py` to ONNX plus the text table, `bioclip_q.py` to quantise to int8). |

## Licences

- **AIY Plants V1** classifier and **Magenta** arbitrary image stylization models: Copyright Google
  LLC, Apache License 2.0. Bundled unmodified.
- **BioCLIP** (`imageomics/bioclip`): MIT licence. Distributed through the GitHub release, not bundled.
- Style images are original works (CC0). Fonts, map data and libraries have their own licences, listed
  in `app/src/main/assets/licences/` and in the app under **Settings > About and licences**.

Sources, checksums and licence details for every bundled model are in
[`app/src/main/assets/ASSETS.md`](app/src/main/assets/ASSETS.md).

The Wallko source code itself does not have a licence yet.
