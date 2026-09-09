# CLAUDE.md

Guidance for AI assistants working in this repository.

## What this is

An Android idle/incremental game ("Stress Ball"). The player spins a virtual
fidget ball (swipe from the side, circle from above); RPM earns points; points
buy upgrades and chests of rarer balls; gems buy skins. See README.md for the
game design.

## Layout

- `core/` — pure Kotlin/JVM game engine. **All game rules live here.**
  - `GameState.kt` the serializable profile: currencies, `balls: List<BallState>` (each with its own upgrades, speed, turbo, view), account upgrades, skins, achievements, spark/buff timers. Ids/fields are part of the save format.
  - `Stats.kt` every balancing formula and the physical model (clutch-like finger, Coulomb + viscous bearings + air drag, weighted squeeze turbo), chest odds/costs, spark timings, effect-tier RPM thresholds.
  - `SpinInput.kt` turns raw finger motion into twist / surface-drag speeds; the engine picks one by the ball's view (`topView`).
  - Catalogues (ids only, no text): `Upgrades.kt` (scope BALL or ACCOUNT), `BallTypes.kt` (rarity, colours, traits), `Skins.kt` (OUTER/INTERIOR, gems, buffs), `Achievements.kt`.
  - `GameText.kt` every player-facing string tied to game content, in English and Turkish. Add a language here and in `app/src/main/res/values-<lang>/strings.xml`.
  - `GameEngine.kt` the simulation on the active ball plus garage income: inputs `setFinger()`, `setTurbo(weight, squeezeRate)`; `tick(dt)`, `buy()`, `openChest()`, `switchBall()`, `sellBall()`, `buySkin()`, `equipSkin()`, `topUpGems()`, `tapSpark()`, `setTopView()`, `prestige()`, `applyOfflineProgress()`. Takes a `Random` for tests.
  - `SaveCodec.kt` JSON persistence, tolerant of unknown keys and out-of-range values, migrates saves from versions 1–3 (single flat ball).
  - Tests in `core/src/test`; `TestStates.single()` builds one-ball profiles. Run with `./gradlew :core:test`.
- `app/` — Android (Jetpack Compose, Material 3, DataStore).
  - `game/GameViewModel.kt` owns the engine, runs the 60 Hz loop in the foreground, autosaves (on an app-scoped coroutine so saves survive onStop), emits one-shot events (offline, achievement, chest, spark).
  - `ui/BallCanvas.kt` draws the ball in both views (animated flip), the disc morph, skins and the tiered effects; `ui/SpinGesture.kt` routes one finger to `SpinInput` and two fingers to the pinch turbo; `ui/GarageSheet.kt`, `ui/SkinsSheet.kt`, `ui/UpgradesSheet.kt`, `ui/StatsSheet.kt`; `ui/AppLanguage.kt` applies the language (default Turkish).
  - UI chrome strings live in `res/values/strings.xml` and `res/values-tr/strings.xml`.

## Rules of thumb

- The root `build.gradle.kts` declares no plugins on purpose: `:app` must load the Android Gradle Plugin and the Kotlin Android plugin in the same classloader, so each module declares its own plugins from the version catalog.
- Put gameplay logic in `:core` and cover it with a unit test; keep `:app` to rendering, input and persistence.
- Never rename upgrade, achievement, ball-type or skin ids: they are persisted. Bump `GameState.CURRENT_VERSION` and extend `SaveCodec` when the layout changes.
- Every string in `GameText` must exist in every language; `GameTextTest` enforces it. Buff and trait lines are generated from the numbers.
- Achievements grant Zen the moment they are met, which multiplies income; tests that compare incomes should divide by `Stats.zenMultiplier`.
- `:app` is only included by `settings.gradle.kts` when an Android SDK is configured. Without one, `./gradlew :core:test` still works; the APK is built by CI.
- Repositories are ordered Maven Central before Google so `:core` resolves without access to Google's Maven.

## Commands

```bash
./gradlew :core:test
./gradlew :app:assembleDebug
./gradlew :app:lintDebug
```
