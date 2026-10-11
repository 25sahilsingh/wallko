# Design system: "Sunny Meadow"

← [Back to the plan index](PLAN.md)

Colours, shapes, the potted-plant visual, animations and the mascot.

Friendly, rounded, chunky, bright. Think a children's picture book, not a fitness app.

## Palette

| Name | Hex | Use |
|---|---|---|
| Cream | `#FFF8E7` | App background |
| Sky | `#8ED6FF` | Top bars, water, primary accents |
| Grass | `#7ED957` | Success, healthy plants, primary button |
| Sunflower | `#FFD93D` | Highlights, dawn bonus, stars |
| Peach | `#FFB5A7` | Cards, soft surfaces |
| Berry | `#FF6F91` | Rare badges, hearts, "new!" |
| Lavender | `#C3B1E1` | Elixir, night/dawn, secondary |
| Terracotta | `#E07A5F` | Pots |
| Soil | `#8D5B4C` | Pot rims, text on light surfaces |
| Ink | `#3D3A4B` | Body text (never pure black) |
| Wilt | `#C9B79C` | Wilting state tint |

Dark theme: same hues, Cream becomes `#2B2A33`, surfaces `#3A3845`. Keep it warm, not grey.

## Shape and type
- Corner radius: 24 dp cards, 32 dp sheets, fully round buttons. No sharp corners anywhere.
- Font: **Nunito** (rounded, friendly, open licence) bundled in `res/font`. Display sizes extra bold.
- Buttons are tall (56 dp) with a soft drop shadow and a 2 dp darker bottom edge (the "toy button" look).
- Icons: rounded Material Symbols, filled.

## Plant in a pot (the hero visual)
Every plant is drawn as: **pot** (vector, terracotta, with a tiny face: two dots and a smile) + **soil** + **sprite** (the stylised cut-out of the user's photo) + **state overlay**.

Growth stages scale the sprite and swap the overlay:
1. **Seedling** (day 0–1): small green sprout vector, sprite not yet shown. Pot face: curious.
2. **Growing** (day 2–growth days): sprite at 60–90 %. Pot face: happy.
3. **Grown**: sprite 100 %, occasional sparkle. Pot face: proud.
4. **Wilting**: sprite tinted Wilt, drooped 8°, pot face: worried, a sweat drop.
5. **Withered**: sprite desaturated, pot face: sleeping (closed eyes), small "zzz". Shown only in the collection, not the garden.

## Animations (Compose `animate*` + Lottie)
- **Idle sway**: every pot sprite rocks ±2° on a 3 s ease loop, offset per plant so the garden feels alive.
- **Tap bounce**: tap a pot → squash-and-stretch 1.0 → 1.15 → 0.95 → 1.0 over 300 ms.
- **Scan**: camera viewfinder with soft rounded corners, a slow sweeping shimmer. On success: Lottie confetti burst + card flips in.
- **New card**: card slides up from the bottom with a spring, sprite "blooms" (scale 0 → 1 with overshoot).
- **Drinking** (daily tick, shown on next open): small blue drops fall into pots with happy faces, tank level animates down.
- **Walk live screen**: tank fills with a liquid wave animation, drops float up as you earn them. Footsteps trail behind the mascot.
- **Dawn bonus**: 4–7 am, the walk screen sky turns Lavender → Peach gradient with a rising sun and a "×2 early bird" badge.
- **Wilting**: sprite droops with a slow ease, a single sweat drop loops on the pot.
- **Revive**: Lavender sparkles swirl into the pot, sprite springs upright, pot face goes from sleeping to surprised to happy.
- **Elixir earned**: a small lavender bottle pops up from the explored map with a chime.
- **Empty states**: a lone pot with a seed and a speech bubble ("Let's find your first plant!").

## Mascot
A small watering can named **Drip** with a face. Appears in onboarding, empty states, reminders, and walks beside the step counter. Vector only, 4–5 expressions.
