# Decisions

← [Back to the plan index](PLAN.md)

Everything here was agreed before building. Change a row only on purpose, and note why.

| Area | Decision |
|---|---|
| Platform | Android only. Kotlin + Jetpack Compose. Single `app` module. |
| Minimum Android | Android 10 (API 29). Target 36, compile 37 (changed 2026-10-10: Compose BOM 2026.09.00, Navigation 2.10.2 and Lifecycle 2.11.0 require compile 37; target stays 36 so runtime behaviour is unchanged). |
| Backend | None. Everything on the phone. No accounts. Local-only, designed so sync can be added later. Exception (2026-10-10): map tiles are downloaded from OpenFreeMap; walks, plants and explored cells never leave the phone. |
| Plant identification | Google AIY plants V1 on-device classifier. 2,101 species. All collectable (plant-based, not flower-only). |
| Plant art | On-device: ML Kit subject segmentation (cut-out) + Magenta arbitrary style transfer (repaint). Shown as a plant in a pot. |
| Card data | Built once on the developer machine by a script (iNaturalist, GBIF, Wikidata, Perenual + estimates). Shipped as JSON inside the APK. Never fetched on the phone. |
| Rarity | Worldwide observation counts only. No country filter. |
| Walking | GPS walks started in-app (foreground service) + passive daily steps via Health Connect. |
| Water | One shared tank. Only walking fills it. Plants drink automatically. |
| Wilt / death | Wilt after 3 days without a drink. Wither after 7. Withered plants leave the garden but stay in the collection greyed out. |
| Revival | Only with elixir. 1 elixir = 500 m walked inside never-visited area. |
| Duplicates | Scanning a species that is alive in the garden does nothing except a friendly "already in your garden" message. A **withered** species can be scanned again and replants as a new seedling (decided 2026-10-10); elixir revive remains the way to bring the original plant back. |
| Scan flow | Live camera only. Confirm from top three guesses. No gallery import. |
| Garden layout | Free placement that snaps to a grid, plus shelf rows for potted plants. |
| Reminders | Morning walk nudge + wilting alerts. Both can be turned off. Off until the first plant exists. |
| Map | MapLibre Native Android + OpenFreeMap vector tiles (free, no key), pastel-restyled. Cream fog layer (~70 % opacity) with explored 100 m cells cut out; Drip as the position marker; "© OpenStreetMap contributors" attribution. Offline / no tiles: plain Canvas meadow map from the same cell data. (decided 2026-10-10) |
| Toolchain | Kotlin 2.4.21 (overrides AGP 9.1.1's bundled 2.2.10, because LiteRT 2.3.0 ships Kotlin 2.4 metadata), KSP 2.3.12, compile SDK 37. (2026-10-10) |
| Version 1 scope | Scan, card, garden, walks, water, wilt/wither, elixir revive, fog-of-war explored map. |
| Later | Achievements, sharing, sync, friends. |
| Style | Very friendly, cute, joyful colours, plants in pots, small animations everywhere. |

## Out of scope for version 1

Accounts, cloud sync, friends, trading, leaderboards, achievements, sharing images, gallery import, iOS, tablet layouts, plant care advice.
