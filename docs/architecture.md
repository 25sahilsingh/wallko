# Architecture

← [Back to the plan index](PLAN.md)

Code layout, core rules, libraries and permissions.

Single Gradle module, package-by-feature, MVVM with unidirectional data flow.

```
com.example.wallko
├── WallkoApp.kt                 Hilt application
├── core/
│   ├── config/GameRules.kt      ALL tunable numbers in one object ([game-rules.md](game-rules.md))
│   ├── db/                      Room database, DAOs, entities, seeding
│   ├── time/Clock.kt            injectable clock (tests can fake time)
│   └── geo/Cells.kt             lat/lng → 100 m cell id
├── data/
│   ├── plants/                  PlantRepository (bundled JSON → Room)
│   ├── garden/                  GardenRepository (owned plants, positions)
│   ├── walks/                   WalkRepository (walks, cells, step days)
│   └── wallet/                  WalletRepository (water, elixir ledger)
├── ml/
│   ├── classifier/              PlantClassifier interface + AiyPlantClassifier
│   ├── segment/                 SubjectSegmenter (ML Kit)
│   └── stylize/                 Stylizer (Magenta, cached style vector)
├── walk/
│   ├── WalkTrackingService.kt   foreground service, Fused Location
│   ├── StepSync.kt              Health Connect daily steps
│   └── WalkRewards.kt           pure functions: distance/cells/time → drops, elixir
├── game/
│   ├── GameEngine.kt            pure functions: apply time passing to garden
│   ├── DailyTickWorker.kt       WorkManager, runs engine + schedules reminders
│   └── SpriteWorker.kt          WorkManager, segment + stylise after a scan
└── ui/
    ├── theme/                   palette, type, shapes, motion specs
    ├── components/              PottedPlant, TankGauge, RarityRibbon, StatRow, DripMascot
    ├── onboarding/ garden/ scan/ card/ collection/ walk/ map/ settings/
    └── navigation/
```

Key rules:
- **Game logic is pure.** `GameEngine` and `WalkRewards` take plain data + a timestamp and return new data. No Android imports. Fully unit-tested.
- **Time is computed lazily.** The app never ticks in the background. On open, and once a day via WorkManager, it asks: "given the last processed time and now, what happened?" and applies it. Android kills background work; this design does not care.
- **ML is behind interfaces.** `PlantClassifier`, `SubjectSegmenter`, `Stylizer` are interfaces with one implementation each. Swapping the AIY model for PlantNet later touches one file.
- **All tunable numbers live in `GameRules`.** Nothing is hard-coded in UI or engine.

## Libraries (versions verified on Google Maven / Maven Central on 2026-10-10)

| Purpose | Artifact | Version |
|---|---|---|
| Compose | `androidx.compose:compose-bom` | 2026.09.00 |
| Navigation | `androidx.navigation:navigation-compose` | 2.10.2 |
| DI | `com.google.dagger:hilt-android` (+ KSP) | 2.60.1 |
| KSP | `com.google.devtools.ksp` | 2.3.12 |
| Database | `androidx.room:room-*` | 2.8.5 |
| Core / Lifecycle | `androidx.core:core-ktx` / `androidx.lifecycle:*` | 1.19.0 / 2.11.0 |
| Prefs | `androidx.datastore:datastore-preferences` | latest stable |
| Camera | `androidx.camera:camera-*` | 1.6.2 |
| On-device ML | `com.google.ai.edge.litert:litert` + `litert-support` | 2.3.0 |
| Segmentation | `com.google.android.gms:play-services-mlkit-subject-segmentation` | 16.0.0-beta1 |
| Steps | `androidx.health.connect:connect-client` | 1.1.0 |
| Location | `com.google.android.gms:play-services-location` | 21.4.0 |
| Background | `androidx.work:work-runtime-ktx` | latest stable |
| Animations | `com.airbnb.android:lottie-compose` | 6.7.1 |
| Map | Google Maps SDK for Android (`play-services-maps`) with a custom pastel style | latest stable |
| Images | `io.coil-kt.coil3:coil-compose` | latest stable |
| Serialization | `kotlinx-serialization-json` | latest stable |

Notes:
- AGP 9.1.1 has built-in Kotlin; Hilt needs the KSP plugin. **Verified 2026-10-10:** KSP 2.2.10-2.0.2 fails under built-in Kotlin ("Using kotlin.sourceSets DSL … is not allowed"); KSP 2.3.12 works with no workaround flag. Compile SDK is 37 because the libraries above require it.
- ML Kit subject segmentation is still beta and downloads its model via Play Services on first use (about 200 KB app size, model fetched once). The manifest meta-data `com.google.mlkit.vision.DEPENDENCIES = subject_segment` makes it download at install.
- Google Maps SDK for Android dynamic maps are free of charge but need an API key from a Google Cloud project. Alternative with no key: draw the explored map on a plain Compose Canvas as a "meadow map" with no streets. Decide in Phase 6; the cell data is identical either way.

## Permissions
`CAMERA`, `ACTIVITY_RECOGNITION`, `ACCESS_FINE_LOCATION`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_LOCATION`, `POST_NOTIFICATIONS` (Android 13+), Health Connect `READ_STEPS`. No background location: tracking only runs during a walk the user started, with a visible notification.
