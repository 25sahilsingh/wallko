# Wallko — Project Plan

A cosy Android game where you go for a walk, scan real plants, collect them as cards,
and grow them in a cute virtual garden. Walking fills your water tank. Plants drink on
their own. If you stop walking, they wilt, and eventually wither. Walking into places
you have never been earns elixir, the only thing that can bring a withered plant back.

Goal of the app: get you outside, moving, and touching grass every day.

Plan written 2026-10-10. This file is the index; the detail lives in the other files in this folder.

---

## Start here

| If you want to know… | Read |
|---|---|
| What has been decided, and what is out of scope | [decisions.md](decisions.md) |
| What is still undecided | [questions.md](questions.md) |
| What to build next, and when a phase is done | [phases.md](phases.md) |

## Game

| Doc | Contents |
|---|---|
| [game-rules.md](game-rules.md) | Game loop, drops, elixir, drinking, wilt/wither, growth, rarity tiers, anti-cheat limits |
| [design-system.md](design-system.md) | "Sunny Meadow" palette, shapes, type, plant-in-a-pot states, animations, Drip the mascot |
| [screens.md](screens.md) | The 8 screens and what each shows |

## Engineering

| Doc | Contents |
|---|---|
| [architecture.md](architecture.md) | Package layout, key rules, library versions, permissions |
| [data-model.md](data-model.md) | Room tables and how species data is seeded |
| [ml-pipeline.md](ml-pipeline.md) | Plant identification and potted-sprite generation |
| [plant-database.md](plant-database.md) | Build-time Python scripts that produce `species.json` |
| [testing.md](testing.md) | Unit, instrumented and manual tests |
| [risks.md](risks.md) | Known risks and their fallbacks |

---

## How to keep these docs tidy

- A new question goes in `questions.md`. Once it's answered, move it to `decisions.md`.
- A number change goes in `game-rules.md` **and** `GameRules.kt` together.
- A finished phase gets ticked in `phases.md`.
