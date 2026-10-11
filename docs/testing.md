# Testing

← [Back to the plan index](PLAN.md)

What is tested, and how.

- **Unit** (JUnit): `GameEngine`, `WalkRewards`, `Cells`, rarity percentiles, confidence rule, JSON → entity mapping. Time is injected, so day-3 and day-7 rules are tested in milliseconds.
- **Instrumented**: Room migrations/seeding, classifier on 10 fixture photos (expect correct top-3 for 8+), sprite pipeline produces a PNG with transparency.
- **Manual**: a real walk at dawn and one at noon; a drive; a day with the app closed (catch-up tick); permission denial paths.
