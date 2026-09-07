# Stress Ball

An Android idle game about a fidget toy: a red rubber stress ball with a
green gear cap. Draw circles on the ball with your finger to spin it. The
faster and longer you spin, the higher the RPM and the more points you earn.
Points buy upgrades that make the ball spin faster, coast longer, keep turning
on its own, and count every revolution for more.

The concept comes from a short video of the physical toy: "imagine it on the
screen, you keep drawing circles on it, you try to spin it faster and raise
the RPM, and it counts your score. Then challenges, leaderboards and so on."

## Gameplay

| Mechanic | What it does |
| --- | --- |
| **Spin gesture** | Circle your finger around the ball. Your finger's angular speed (times the Gear Cap ratio) is the target the ball accelerates towards. |
| **RPM** | The ball's speed. Friction slows it, upgrades fight friction. Above the cooling cap it overheats and cannot go faster. |
| **Points** | Earned per revolution: `points/rev × multipliers`. |
| **Overdrive** | A manual boost button: a burst of RPM plus doubled income for 8 seconds, on a cooldown. |
| **Petal Shell** | Above 300 RPM the shell opens and multiplies income. |
| **Resonance** | Staying above 120 RPM builds a combo multiplier that decays when you slow down. |
| **Micro Motor** | An idle floor: the ball never drops below the motor's RPM, so it earns while you rest. |
| **Gyro Memory** | Offline progress: the motor keeps earning for a capped number of hours at reduced efficiency. |
| **Zen reset** | Prestige. Trade the run for Zen; each Zen permanently adds +10% income. Challenges (achievements) also grant Zen. |

All formulas live in `core/src/main/kotlin/com/aeunal/stressball/core/Stats.kt`
and the upgrade catalogue in `Upgrades.kt`, so balancing is a one-file job.

## Project layout

```
core/   Pure-Kotlin game engine (physics, economy, upgrades, save format). JVM unit tests.
app/    Android app: Jetpack Compose UI, ViewModel game loop, DataStore persistence.
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

## Roadmap ideas

- Online leaderboards for best RPM and fastest 1M points.
- Daily challenges (e.g. reach 500 RPM without the motor).
- Sound and haptics tied to RPM.
- Cosmetic shells and caps.
