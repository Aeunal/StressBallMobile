# CLAUDE.md

Guidance for AI assistants working in this repository.

## What this is

An Android idle/incremental game ("Stress Ball"). The player spins a virtual
fidget ball by drawing circles on the screen; RPM earns points; points buy
upgrades. See README.md for the game design.

## Layout

- `core/` — pure Kotlin/JVM game engine. **All game rules live here.**
  - `GameState.kt` serializable save state (ids/fields are part of the save format).
  - `Stats.kt` every balancing formula. `Upgrades.kt` the upgrade catalogue.
  - `GameEngine.kt` the simulation: `spin()`, `tick(dt)`, `buy()`, `overdrive()`, `prestige()`, `applyOfflineProgress()`.
  - `SaveCodec.kt` JSON persistence, tolerant of unknown keys.
  - Tests in `core/src/test`. Run with `./gradlew :core:test`.
- `app/` — Android (Jetpack Compose, Material 3, DataStore).
  - `game/GameViewModel.kt` owns the engine, runs the 60 Hz loop in the foreground, autosaves.
  - `ui/BallCanvas.kt` draws the ball; `ui/SpinGesture.kt` converts finger circles to rad/s.

## Rules of thumb

- Put gameplay logic in `:core` and cover it with a unit test; keep `:app` to rendering, input and persistence.
- Never rename upgrade or achievement ids: they are persisted.
- `:app` is only included by `settings.gradle.kts` when an Android SDK is configured. Without one, `./gradlew :core:test` still works; the APK is built by CI.
- Repositories are ordered Maven Central before Google so `:core` resolves without access to Google's Maven.

## Commands

```bash
./gradlew :core:test
./gradlew :app:assembleDebug
./gradlew :app:lintDebug
```
