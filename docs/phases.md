# Phases and acceptance criteria

← [Back to the plan index](PLAN.md)

Build order. Each phase has a "Done when" line; do not start the next phase until it is true.

## Phase 0 — Foundation  ✅ done 2026-10-10 (builds, 4 tabs, tests pass) (1 day)
- minSdk 29, deps from [architecture.md](architecture.md#libraries-versions-verified-on-google-maven--maven-central-on-2026-10-10), Hilt + KSP wired, Room + DataStore, Navigation with 4 tabs, theme (palette, Nunito, shapes), `GameRules.kt`, assets folder with both models and three style images.
- Verify Hilt works under AGP 9 built-in Kotlin. If not, fall back to manual DI (a simple `AppGraph` object) rather than fighting the toolchain.
- Done when: app builds, shows 4 themed tabs with empty states and Drip, unit test runs.

## Phase 1 — Plant database  ✅ code complete 2026-10-10 (device check pending) (1 day of coding + ~3 h script runtime)
- `tools/plantdb` scripts, `species.json`, Room seeding, `PlantRepository`.
- Done when: collection screen lists all 2,101 species greyed out with names and rarity.

## Phase 2 — Scan and card  ✅ code complete 2026-10-10 (device check pending) (2 days)
- CameraX, `AiyPlantClassifier`, top-3 sheet, confidence rule, card creation, card sheet, duplicate message.
- Done when: scanning a houseplant produces a card with real stats; scanning it again says "already in your garden".

## Phase 3 — Garden and sprites  ✅ code complete 2026-10-10 (device check pending) (3 days)
- `PottedPlant` composable with growth states and sway, garden grid + shelves with drag-snap, `SpriteWorker` (segment + stylise + mask), style picker.
- Done when: a scanned plant appears as a seedling, then its stylised sprite blooms into the pot; it can be dragged to a shelf.

## Phase 4 — Walking and water  ✅ code complete 2026-10-10 (device check pending) (3 days)
- Health Connect daily steps, `WalkTrackingService` with Fused Location + notification, cell tracking, `WalkRewards`, wallet ledger, walk screen with live tank animation, dawn bonus, elixir progress.
- Done when: a real 10-minute walk shows distance, steps, new cells, drops, and the tank rises; a drive earns nothing.

## Phase 5 — Game clock  ✅ code complete 2026-10-10 (device check pending) (2 days)
- `GameEngine` (drinking, wilt, wither, growth, rarest-first), `DailyTickWorker`, lazy catch-up on app open, reminders (morning nudge, wilting alert), revive flow with elixir.
- Done when: with a faked clock, a plant wilts on day 3 and withers on day 7, and 1 elixir revives it with the animation.

## Phase 6 — Explored map  ✅ code complete 2026-10-10 (device check pending) (2 days)
- Fog-of-war over Google Maps (pastel style) or Canvas meadow map; current walk path; Drip as the position marker.
- Done when: explored cells render as green patches and grow during a live walk.

## Phase 7 — Polish (2–3 days)
- Onboarding with permission explanations, Lottie moments (scan success, new card, drink, revive, elixir), empty states, haptics, accessibility (content descriptions, large text), dark theme, data export, licences screen.
- Done when: a first-time user can go from install to first plant without reading anything.

Total: roughly 3 weeks of focused work.

Status 2026-10-10: Phases 0–6 written, compiling, 162 JVM unit tests green. Phase 7 in progress. Each "Done when" still needs a run on a real phone (walk, scan, multi-day clock).
