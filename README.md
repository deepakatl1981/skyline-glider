# Skyline Glider

An endless rooftop runner for Android in the spirit of Subway Surfers — except you're
parkouring across a neon-dusk city skyline with a wingsuit strapped to your back.

Built as a pure native Android app: **Kotlin + Jetpack Compose**, no game engine, no
binary assets. Every building, character, coin and sound effect is generated in code.

---

## Build & run

1. Open the `skyline-glider` folder in **Android Studio** (Koala / 2024.1 or newer).
2. Let Gradle sync — it will fetch the wrapper and dependencies automatically.
3. Run on a device or emulator (minSdk 24, targetSdk 35).

If you prefer the command line and have Gradle installed locally, generate the wrapper
first — the binary `gradle-wrapper.jar` isn't checked in:

```bash
cd skyline-glider
gradle wrapper --gradle-version 8.7
./gradlew assembleDebug
```

---

## Controls

| Gesture | Keyboard | Action |
| --- | --- | --- |
| Swipe ← / → | ← → or A / D | Move between rooftop lanes |
| Swipe ↑ | ↑, W or space | Jump — again mid-air to snap open the wingsuit |
| Swipe ↓ | ↓ or S | Slide (on the ground) or dive (in the air, sliding on landing) |
| Tap | — | Jump |
| — | Esc or P | Pause / resume |

Keyboard input exists for emulator and desktop-Android testing; touch is the shipping
control scheme and works identically.

The double-swipe-up is the core skill expression: a plain jump clears gaps, but only a
wingsuit glide reaches the coin arcs strung over the void.

---

## What's in the game

**Movement & hazards**

- Three-lane rooftop track with a real vanishing point (custom pinhole projection)
- Wingsuit glide: 2.7 s of air time, slower fall, 12% forward speed bonus
- Roof gaps you must jump or glide across
- Wind zones that shove you sideways — swipe into the gust to shed the drift
- Patrol drones that sweep laterally at chest height (slide under or glide over)
- AC units, water towers, antennas and scaffolding bars

**Progression**

- Coins, collected and banked between runs
- Power-ups: 🧲 Magnet (9 s), 🛡 Shield (14 s, absorbs one hit), ⚡ Speed Boost (6.5 s)
- Six unlockable gliders, each with its own suit / accent / wing palette
- Rotating missions (glide distance, drones passed, near misses, wind metres, …) with
  three difficulty tiers that escalate as you clear them
- Seven-day daily reward ladder with streak tracking
- "Second Wind" revive for 250 coins, once per run
- Best score, farthest distance and run count persisted in SharedPreferences

**Feel**

- Procedural parallax skyline: three layers, thousands of individually lit windows
- Sunset gradient sky, twinkling stars, blinking aircraft-warning lights
- Screen shake on impact, speed streaks during boost, dust on landing, coin sparkles
- Synthesised chiptune SFX (WAVs written to the cache on first launch) + haptics

---

## Project layout

```
app/src/main/java/com/skyline/glider/
├── MainActivity.kt            immersive fullscreen host
├── core/                      the simulation — knows nothing about Compose layout
│   ├── Cfg.kt                 every tunable number in the game
│   ├── Models.kt              obstacles, pickups, gaps, wind zones, particles
│   ├── Proj.kt                pseudo-3D pinhole projection
│   ├── LevelGen.kt            streaming pattern-based course generator
│   ├── GameEngine.kt          physics, collisions, power-ups, scoring
│   ├── GameEvent.kt           events raised for audio / haptics
│   └── Palette.kt             shared colour language
├── data/
│   ├── Content.kt             characters, mission pool, daily rewards
│   └── SaveStore.kt           SharedPreferences-backed, exposed as Compose state
├── audio/SoundBank.kt         runtime WAV synthesiser + SoundPool + vibration
├── render/                    all drawing, as DrawScope extensions
│   ├── Shapes.kt              quads, 3D boxes, two-segment limbs
│   ├── Backdrop.kt            sky, sun, stars, parallax city
│   ├── Track.kt               rooftops, parapets, gaps, wind overlays
│   ├── Entities.kt            obstacles, pickups, particles (painter's algorithm)
│   ├── Hero.kt                the animated runner + wingsuit + power-up auras
│   └── Portrait.kt            static glider pose for menus and the shop
└── ui/                        Compose screens
    ├── GameApp.kt             navigation
    ├── GameScreen.kt          frame loop, swipe input, HUD, pause, game over
    ├── MenuScreen.kt          title, daily reward sheet, options
    ├── ShopScreen.kt          character unlocks
    ├── MissionsScreen.kt      mission progress and claims
    └── Widgets.kt             shared neon UI kit
```

---

## How the rendering works

There's no 3D pipeline. `Proj` maps a world point `(x, y, z)` to the screen with
`scale = CAM_DEPTH / (CAM_DEPTH + z)`, so distant geometry converges on a horizon line
at 35.5% screen height. Obstacles are drawn as three-face boxes (top, one side, front)
using that projection, which reads as solid geometry for a fraction of the cost.

Entities are painted far-to-near via a two-pointer merge over the obstacle and pickup
lists, with the hero slotted in at `z = 0` — so a drone genuinely passes behind you one
frame and in front of you the next.

---

## Tuning

Almost all balance lives in `core/Cfg.kt`. A few worth knowing:

| Constant | Effect |
| --- | --- |
| `BASE_SPEED` / `MAX_SPEED` | 13.5 → 34 m/s over roughly 6.4 km |
| `JUMP_V` / `GRAVITY` | 0.8 s of air time — covers a 10.8 m gap at the start |
| `GLIDE_MAX_TIME` | how long the wingsuit stays open |
| `LANE_SHIFT_SPEED` | 8.5 lanes/s; lower feels heavier |
| `REVIVE_COST` | price of a Second Wind |

Course spacing in `LevelGen` is expressed in **seconds of reaction time**, not metres,
so patterns automatically spread out as the run accelerates. That's the single most
important fairness rule in the game — keep it if you add new patterns.

---

## Adding your own content

- **New obstacle**: add to `ObstacleKind`, give it collision bounds in `Models.kt`, a
  draw function in `render/Entities.kt`, and reference it from a `LevelGen` pattern.
- **New pattern**: add to `LevelGen.Pattern`, weight it in `pick()`, and emit it in
  `emit()` — return the z-cursor position where the next pattern should start.
- **New character**: append to `CHARACTERS` in `data/Content.kt`. Colours alone define
  the look; no art needed.
- **New mission**: append a `MissionDef` to `MISSION_POOL` and, if it needs a new stat,
  add a field to `RunStats` and a branch to `valueFor`.
