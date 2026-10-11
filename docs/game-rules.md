# Game rules

← [Back to the plan index](PLAN.md)

The game loop and the numbers that drive it. Every number here lives in `core/config/GameRules.kt`, nowhere else.

## The game loop in one picture

```
   go for a walk ──► steps + distance + new area
        │                     │
        │                     ├──► water drops  ──► tank ──► plants drink daily
        │                     └──► elixir (new area only) ──► revive withered plant
        │
        └──► scan a plant ──► identify ──► card + potted sprite ──► garden
```

Tank empty → plants cannot drink → day 3 wilting → day 7 withered.

## Numbers

Water unit: **drops**. Start: tank capacity 50, 20 drops gifted, first scan free.

## Earning
| Source | Drops |
|---|---|
| Passive steps (Health Connect, daily) | 1 drop per 100 steps, capped at 15,000 steps/day |
| GPS walk distance | 2 drops per 100 m (replaces, does not stack with, the steps taken during the walk) |
| New cell entered (100 m × 100 m, first visit ever) | +3 drops per cell |
| Dawn window, 4:00–7:00 local, applies to walk drops | ×2 |
| Rare+ card found | one-time +10 drops |

## Elixir
- Every **500 m walked inside never-visited cells** = 1 elixir. Partial progress persists across walks.
- Reviving costs 1 elixir. No other way to earn it.

## Drinking
- Each plant drinks `thirst` drops per day (1–5). Drinking is automatic at the daily tick.
- Tank capacity grows: 50 + 10 per distinct species ever owned (withered included, so it never shrinks), max 500. Grows the moment a new species is planted.
- If the tank cannot cover a plant's drink, that plant goes unwatered that day. Plants are served rarest-first, so a common weed wilts before a legendary orchid.

## Wilt and wither
- 3 consecutive days unwatered → WILTING (visual + alert).
- 7 consecutive days unwatered → WITHERED (leaves garden, greyed in collection).
- A single successful drink resets the counter and clears wilting.

## Growth
- Growth days to GROWN: fast 3, medium 7, slow 14 (from the species' growth field). Only days with a drink count.

## Rarity tiers (by worldwide observation count percentile across all 2,101)
Common 0–40 %, Uncommon 40–70 %, Rare 70–90 %, Epic 90–98 %, Legendary top 2 %.

## Anti-silliness
- Steps capped per day (above). GPS points with speed > 12 km/h do not count toward distance (driving).
- Walk must be ≥ 2 minutes and ≥ 100 m to count.

## Edge cases (as implemented in `GameRules.kt`, 2026-10-10)
- Steps and distance pay per **full** block: 199 steps = 1 drop, 199 m walked = 2 drops.
- Dawn window includes 04:00 and excludes 07:00.
- Rarity tiers include their lower bound and exclude their upper bound; Legendary is 98–100 % inclusive.
- Confidence thresholds are compared with ≥ and a 1e-6 tolerance, so an exact 0.35 / 0.10 counts as confident.
- Tank capacity = 50 + 10 per distinct species ever owned (withered included), max 500.
