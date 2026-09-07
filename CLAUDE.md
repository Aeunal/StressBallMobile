# CLAUDE.md

Guidance for AI assistants working in this repository.

## What this is

An Android idle/incremental game ("Stress Ball"). The player spins a virtual
fidget ball by drawing circles on the screen; RPM earns points; points buy
upgrades and cosmetics. See README.md for the game design.

## Layout

- `core/` — pure Kotlin/JVM game engine. **All game rules live here.**
  - `GameState.kt` serializable save state (ids/fields are part of the save format). `omega` is signed.
  - `Stats.kt` every balancing formula and the physical model (clutch-like finger, Coulomb + viscous bearings, weighted squeeze turbo), plus the effect-tier RPM thresholds.
  - `SpinInput.kt` turns raw finger motion into twist / surface-drag speeds and a circularity weight; `GameEngine.setFinger()` blends them.
  - `Upgrades.kt` / `Cosmetics.kt` / `Achievements.kt` catalogues (ids only, no text).
  - `GameText.kt` every player-facing string tied to game content, in English and Turkish. Add a language here and in `app/src/main/res/values-<lang>/strings.xml`.
  - `GameEngine.kt` the simulation: inputs `setFinger()`, `setTurbo(weight, squeezeRate)`; `tick(dt)`, `buy()`, `buyCosmetic()`, `equipCosmetic()`, `prestige()`, `applyOfflineProgress()`.
  - `SaveCodec.kt` JSON persistence, tolerant of unknown keys and out-of-range values.
  - Tests in `core/src/test`. Run with `./gradlew :core:test`.
- `app/` — Android (Jetpack Compose, Material 3, DataStore).
  - `game/GameViewModel.kt` owns the engine, runs the 60 Hz loop in the foreground, autosaves (on an app-scoped coroutine so saves survive onStop).
  - `ui/BallCanvas.kt` draws the ball, the disc morph and the tiered effects; `ui/SpinGesture.kt` routes one finger to `SpinInput` and two fingers to the pinch turbo; `ui/AppLanguage.kt` applies the language (default Turkish).
  - UI chrome strings live in `res/values/strings.xml` and `res/values-tr/strings.xml`.

## Rules of thumb

- The root `build.gradle.kts` declares no plugins on purpose: `:app` must load the Android Gradle Plugin and the Kotlin Android plugin in the same classloader, so each module declares its own plugins from the version catalog.
- Put gameplay logic in `:core` and cover it with a unit test; keep `:app` to rendering, input and persistence.
- Never rename upgrade, achievement or cosmetic ids: they are persisted.
- Every string in `GameText` must exist in every language; `GameTextTest` enforces it.
- `:app` is only included by `settings.gradle.kts` when an Android SDK is configured. Without one, `./gradlew :core:test` still works; the APK is built by CI.
- Repositories are ordered Maven Central before Google so `:core` resolves without access to Google's Maven.

## Commands

```bash
./gradlew :core:test
./gradlew :app:assembleDebug
./gradlew :app:lintDebug
```
