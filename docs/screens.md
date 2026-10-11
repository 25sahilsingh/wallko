# Screens

← [Back to the plan index](PLAN.md)

Every screen in version 1 and what it shows.

Bottom navigation, 4 tabs: **Garden**, **Scan**, **Walk**, **Map**. Collection and Settings reachable from Garden.

1. **Onboarding** (3 pages, swipe): what the game is, why walking, permissions with friendly copy. Asks camera + activity recognition + location (while in use) + notifications + Health Connect, one at a time, each with a Drip illustration explaining why.
2. **Garden**: scrolling 2D meadow. Grid cells for ground plants, shelf rows for pots. Drag to move (snaps). Tap a pot → card sheet. Tank gauge pinned top-right. Wilting plants get a small "!" bubble.
3. **Scan**: full-screen CameraX preview, capture button, after capture: top-3 guess sheet with photo thumbnails, confidence shown as 1–5 leaves (never a raw percentage). Confirm → new card. Low confidence → "Hmm, get closer or try another angle" with tips.
4. **Card** (bottom sheet / full page): potted sprite hero, name, Latin name, rarity ribbon, stats as cute icon rows (Thirst drops, Sun suns, Growth sprout speed), bloom season, badges, description, "found on" date and place name, original photo in a polaroid frame. Estimated values show a tiny "~" with a tooltip.
5. **Collection**: grid of all owned cards; withered ones greyed with "Revive (1 elixir)" button. Filter by rarity/type. Progress "42 / 2,101 plants found".
6. **Walk**: big Start button. Live: elapsed time, distance, steps, drops earned, new-area cells, dawn badge. Stop → summary card with totals and any elixir. Also shows today's passive steps from Health Connect.
7. **Map**: fog-of-war. Explored 100 m cells drawn as soft green patches; unexplored shown under a cream fog. Your position as Drip. Current walk path drawn as dotted footsteps.
8. **Settings**: reminders on/off and time, paint style for sprites (3 choices, re-renders on change), units, data export (JSON), about and licences.
