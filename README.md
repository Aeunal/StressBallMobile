# Stress Ball

An Android idle game about a fidget toy: a red rubber stress ball with a
green gear cap. Draw circles on the ball with your finger to spin it. The
faster and longer you spin, the higher the RPM and the more points you earn.
Points buy upgrades that make the ball spin faster, coast longer, keep turning
on its own, and count every revolution for more.

The concept comes from a short video of the physical toy: "imagine it on the
screen, you keep drawing circles on it, you try to spin it faster and raise
the RPM, and it counts your score. Then challenges, leaderboards and so on."

The game ships in English and Turkish (follows the device language, with an
in-app override under Style).

## Gameplay

| Mechanic | What it does |
| --- | --- |
| **Grip** | While your finger is on the ball it is gripped: circling spins it, holding still brakes it. Slow turns move the ball exactly as far as your finger did; fast circles engage the Gear Cap and multiply. A stock ball is stiff: it slips under a fast finger and bleeds speed quickly, so early upgrades are felt immediately. |
| **RPM** | The ball's speed. Friction slows it, Slick Bearings and the Flywheel fight friction. Above the Liquid Cooling cap it overheats and cannot go faster. |
| **Points** | Earned per revolution: `points/rev × multipliers`. |
| **Turbo** | Hold the button for instant momentum and a raised RPM cap, at doubled income. The charge drains while held and refills when released; keep holding it empty and the ball brakes instead. While boosting the ball is pressed into a disc. |
| **Petal Shell** | Above 300 RPM the shell opens and multiplies income. |
| **Resonance** | Staying above 120 RPM builds a combo multiplier that decays when you slow down. |
| **Micro Motor** | An idle floor: the ball never drops below the motor's RPM, so it earns while you rest. |
| **Gyro Memory** | Offline progress: the motor keeps earning for a capped number of hours at reduced efficiency. |
| **Style** | Ball and cap colours bought with points. |
| **Effects** | The ball throws sparks from 350 RPM, wears a corona of flame from 900, crackles with lightning from 2,000 and wraps itself in plasma from 4,000. |
| **Zen reset** | Prestige. Trade the run for Zen; each Zen permanently adds +10% income. Challenges (achievements) also grant Zen. Looks are kept. |

All formulas live in `core/src/main/kotlin/com/aeunal/stressball/core/Stats.kt`,
the upgrade catalogue in `Upgrades.kt`, cosmetics in `Cosmetics.kt` and every
player-facing string tied to game content in `GameText.kt` (both languages),
so balancing and wording are one-file jobs.

## Project layout

```
core/   Pure-Kotlin game engine (physics, economy, upgrades, cosmetics, text, save format). JVM unit tests.
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

- Online leaderboards for best RPM and fastest 1M points.
- Daily challenges (e.g. reach 500 RPM without the motor).
- Sound tied to RPM and the effect tiers.
- More cosmetics: shell patterns, cap shapes, spark colours.
