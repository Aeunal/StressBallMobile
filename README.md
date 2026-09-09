# Stress Ball

An Android idle game about a fidget toy: a rubber stress ball with a gear
cap. Swipe across the ball to roll it, or flip it and circle the cap to spin
it. RPM earns points; points buy upgrades, chests full of rarer balls, and a
garage where every ball you own keeps earning.

The concept comes from a short video of the physical toy: "imagine it on the
screen, you keep drawing circles on it, you try to spin it faster and raise
the RPM, and it counts your score. Then challenges, leaderboards and so on."

The game ships in Turkish (default) and English, switchable under Skins.

## Gameplay

| Mechanic | What it does |
| --- | --- |
| **Grip** | While one finger is on the ball it is gripped, like a slipping clutch: the ball chases the speed your finger implies, up to how hard the rubber can push (Grip Tape). Holding still brakes it; with the Kinetic Harvester, braking pays the lost spin back as points. **From the side** (default) you swipe across the face: the surface follows your finger, so a swipe with the spin adds speed and one against it slows or reverses it. A swipe slower than the surface brakes unless the Rubber Tread over-rolls it. **From above** (Gimbal Mount) you circle the cap: slow turns move it one-to-one, fast circles engage the Gear Cap, and it is far easier to keep going. |
| **RPM** | The ball's speed. Bearing friction (Coulomb + viscous, over inertia) and air drag (∝ speed²) slow it; Slick Bearings, the Flywheel and the Aero Shell fight them. Above the Liquid Cooling / Cryo Core cap it overheats and cannot go faster. |
| **Points** | Earned per revolution: `points/rev × multipliers` (petals, resonance combo, Zen, turbo, Frenzy, ball traits, skins). |
| **Turbo** | Pinch the ball from top and bottom. The squeeze is a weight from 0 to 1: thrust, fuel burn, the raised RPM cap and the income bonus all scale with it, and a fast pinch adds a kick (Nitro). The ball flattens into a disc as far as you squeeze. Stock fuel lasts about a second; after use there is a cooldown before it refills. Squeezing an empty tank brakes (less with a Heat Sink). |
| **Garage** | You own up to 8 balls and play one at a time. The others idle at their motor floor and earn a Bearing Rack share of that income, online and offline. Switch any time. |
| **Chests** | A chest holds a random named ball: Common → Rare → Very Rare → Legendary → Mythic → Exotic, each with its own colours, aura and traits (income, drag, grip, turbo fuel, cap). The price triples per ball owned; Lucky Chest improves the odds. Balls can be sold for their rarity value plus half of what was invested in them. |
| **Gems and skins** | Maxing any upgrade awards gems (the + button also tops up, free for now). Gems buy skins: one **outer** (patterns on the shell: stripes, spots, hex armour, Saturn rings, starfield, magma cracks) and one **interior** (a glow from inside: ember, crystal, void, storm, prism, clockwork) can be active at a time, each with a permanent buff. |
| **Golden sparks** | Every few minutes a spark appears for 12 seconds. Tap it for Frenzy (×7 income), a Jackpot (15 minutes of income), a Recharge (full turbo, free fuel) or Wild Grip (×3 grip). Lucky Charm makes them more frequent. |
| **Effects** | Sparks from 350 RPM, flames from 900, lightning from 2,000, plasma from 4,000, a light-bending singularity from 8,000, supernova shockwaves from 16,000 and a quantum glitch from 32,000. |
| **Zen reset** | Prestige the active ball: its upgrades, speed and the point balance go, and each Zen gained permanently adds +10% income. Other balls, skins, achievements and records stay. Challenges (achievements) also grant Zen. |

All formulas live in `core/src/main/kotlin/com/aeunal/stressball/core/Stats.kt`;
the catalogues are `Upgrades.kt` (23 upgrades, ball- and account-scoped),
`BallTypes.kt`, `Skins.kt` and `Achievements.kt`; every player-facing string
tied to game content is in `GameText.kt` (both languages).

## Project layout

```
core/   Pure-Kotlin game engine (physics, economy, garage, chests, skins, sparks, text, save format + migration). JVM unit tests.
app/    Android app: Jetpack Compose UI, ViewModel game loop, DataStore persistence, effects.
```

`:core` has no Android dependency. `settings.gradle.kts` only includes `:app`
when an Android SDK is configured (`ANDROID_HOME`, `ANDROID_SDK_ROOT`, or
`sdk.dir` in `local.properties`), so the engine can be built and tested on
any machine with a JDK.

## Building

Requirements: JDK 17+, Android Studio (or the Android SDK with API 36).

```bash
./gradlew :core:test            # engine unit tests, no SDK needed
./gradlew :app:assembleDebug    # debug APK -> app/build/outputs/apk/debug/
./gradlew :app:installDebug     # install on a connected device
```

Open the root folder in Android Studio to run on an emulator or device.

CI (`.github/workflows/android.yml`) runs the engine tests, assembles the
debug APK and uploads it as a workflow artifact on every push.

## Installing a dev build on a phone

Every push also publishes the APK as a pre-release tagged after the branch,
so the download URL is stable and always serves that branch's newest build:

```
https://github.com/Aeunal/StressBallMobile/releases/download/dev-<branch-with-dashes>/stressball-debug.apk
```

Open that link in the phone's browser and allow installs from the browser when
Android asks. The build is debug-signed, so uninstall it before installing a
release-signed build later — the signatures do not match.

## Roadmap ideas

- Online leaderboards (best RPM, fastest 1M) and clans: need a backend and a
  server-authoritative save; the local JSON save is not tamper-proof.
- Expeditions: send a garage ball away for an hour to bring back gems or a chest.
- Daily challenges with their own rewards.
- Sound tied to RPM and the effect tiers.
