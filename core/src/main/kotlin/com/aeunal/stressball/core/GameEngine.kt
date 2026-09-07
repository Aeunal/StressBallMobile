package com.aeunal.stressball.core

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * The deterministic simulation behind the game.
 *
 * The engine owns a [GameState] and mutates it through a small set of
 * operations. All time-dependent behaviour goes through [tick], which makes
 * the engine trivial to unit test and to run at any frame rate.
 *
 * Not thread-safe: drive it from a single thread (the UI's game loop).
 */
class GameEngine(initial: GameState = GameState()) {

    var state: GameState = initial
        private set

    /** Finger angular velocity (rad/s, geared, absolute) supplied for the current tick. */
    private var pendingFingerOmega: Double = 0.0

    // --------------------------------------------------------------------
    // Inputs
    // --------------------------------------------------------------------

    /**
     * Reports how fast the player's finger is circling the ball. Call this from
     * the gesture handler before every [tick]; the value is consumed by the
     * next tick and then cleared. Direction is irrelevant, only speed counts.
     */
    fun spin(fingerOmega: Double) {
        if (fingerOmega.isFinite()) {
            pendingFingerOmega = max(pendingFingerOmega, abs(fingerOmega))
        }
    }

    /** Fires Overdrive if unlocked and off cooldown. Returns true if it fired. */
    fun overdrive(): Boolean {
        val level = state.level(Upgrades.OVERDRIVE)
        if (level <= 0 || state.overdriveCooldown > 0.0) return false
        val cap = rpmToOmega(Stats.rpmCap(state.level(Upgrades.COOLING)))
        state = state.copy(
            omega = min(cap, state.omega + rpmToOmega(Stats.overdriveRpm(level))),
            overdriveCooldown = Stats.overdriveCooldown(level),
            overdriveRemaining = Stats.OVERDRIVE_DURATION,
        )
        return true
    }

    /** Buys one level of [id] if affordable and below max level. */
    fun buy(id: String): Boolean {
        val def = Upgrades.get(id)
        val level = state.level(id)
        if (level >= def.maxLevel) return false
        val cost = def.costAt(level)
        if (state.points < cost) return false
        state = state.copy(
            points = state.points - cost,
            upgrades = state.upgrades + (id to level + 1),
        )
        return true
    }

    fun canBuy(id: String): Boolean {
        val def = Upgrades.get(id)
        val level = state.level(id)
        return level < def.maxLevel && state.points >= def.costAt(level)
    }

    /** Zen the player would receive from a reset right now. */
    fun zenOnReset(): Long = Stats.zenFor(state.pointsThisRun)

    /**
     * Prestige: trade all points, upgrades and speed for Zen, which multiplies
     * income permanently. Returns false if the reset would grant no Zen.
     */
    fun prestige(): Boolean {
        val gain = zenOnReset()
        if (gain <= 0) return false
        state = GameState(
            zen = state.zen + gain,
            prestigeCount = state.prestigeCount + 1,
            totalPointsEarned = state.totalPointsEarned,
            bestRpm = state.bestRpm,
            totalRevolutions = state.totalRevolutions,
            playTimeSeconds = state.playTimeSeconds,
            achievements = state.achievements,
            lastSavedEpochMs = state.lastSavedEpochMs,
        )
        unlockAchievements()
        return true
    }

    /** Replaces the whole state, e.g. after loading a save. */
    fun load(newState: GameState) {
        state = newState
        pendingFingerOmega = 0.0
    }

    fun markSaved(epochMs: Long) {
        state = state.copy(lastSavedEpochMs = epochMs)
    }

    // --------------------------------------------------------------------
    // Simulation
    // --------------------------------------------------------------------

    /**
     * Advances the simulation by [dtSeconds]. Large steps are subdivided so
     * that a stalled frame cannot produce wild results.
     */
    fun tick(dtSeconds: Double) {
        if (!(dtSeconds > 0.0) || !dtSeconds.isFinite()) return
        var remaining = dtSeconds
        while (remaining > 0.0) {
            val step = min(remaining, MAX_STEP)
            step(step)
            remaining -= step
        }
        pendingFingerOmega = 0.0
    }

    private fun step(dt: Double) {
        val s = state
        val bearings = s.level(Upgrades.BEARINGS)
        val flywheel = s.level(Upgrades.FLYWHEEL)
        val capOmega = rpmToOmega(Stats.rpmCap(s.level(Upgrades.COOLING)))

        var omega = s.omega

        // 1. Manual spin: the ball chases the geared finger speed, never brakes.
        val target = pendingFingerOmega * Stats.gearRatio(s.level(Upgrades.GEAR))
        if (target > omega) {
            val k = min(1.0, Stats.coupling(s.level(Upgrades.GRIP)) * dt)
            omega += (target - omega) * k
        }

        // 2. Friction.
        val beforeFriction = omega
        val decel = Stats.viscousFriction(bearings, flywheel) * omega + Stats.constantFriction(bearings, flywheel)
        omega = max(0.0, omega - decel * dt)

        // 3. Motor floor: once the ball is at its idle speed friction can no
        //    longer slow it below that; from rest it ramps up towards the floor.
        val motorOmega = rpmToOmega(Stats.motorRpm(s.level(Upgrades.MOTOR)))
        if (omega < motorOmega) {
            val ramped = beforeFriction + (motorOmega - beforeFriction) * min(1.0, Stats.MOTOR_RESPONSE * dt)
            omega = min(motorOmega, max(omega, ramped))
        }

        // 4. Overheat cap.
        omega = min(omega, capOmega)

        // 5. Resonance combo.
        val rpm = omegaToRpm(omega)
        val maxCombo = Stats.maxCombo(s.level(Upgrades.RESONANCE))
        var combo = s.combo
        combo = if (rpm >= Stats.RESONANCE_THRESHOLD_RPM) {
            min(maxCombo, combo + Stats.COMBO_BUILD_RATE * dt)
        } else {
            max(0.0, combo - Stats.COMBO_DECAY_RATE * dt)
        }
        combo = min(combo, maxCombo)

        // 6. Overdrive timers.
        val overdriveRemaining = max(0.0, s.overdriveRemaining - dt)
        val overdriveCooldown = max(0.0, s.overdriveCooldown - dt)

        // 7. Income.
        val revolutions = omega / TWO_PI * dt
        val earned = revolutions * incomePerRevolution(s, rpm, combo, overdriveRemaining > 0.0)

        state = s.copy(
            omega = omega,
            combo = combo,
            overdriveRemaining = overdriveRemaining,
            overdriveCooldown = overdriveCooldown,
            points = s.points + earned,
            pointsThisRun = s.pointsThisRun + earned,
            totalPointsEarned = s.totalPointsEarned + earned,
            totalRevolutions = s.totalRevolutions + revolutions,
            bestRpm = max(s.bestRpm, rpm),
            playTimeSeconds = s.playTimeSeconds + dt,
        )
        unlockAchievements()
    }

    private fun incomePerRevolution(s: GameState, rpm: Double, combo: Double, overdriveActive: Boolean): Double =
        Stats.pointsPerRev(s.level(Upgrades.COUNTER)) * totalMultiplier(s, rpm, combo, overdriveActive)

    private fun totalMultiplier(s: GameState, rpm: Double, combo: Double, overdriveActive: Boolean): Double {
        val petals = if (petalsOpen(s, rpm)) Stats.petalMultiplier(s.level(Upgrades.PETALS)) else 1.0
        val resonance = 1.0 + combo
        val zen = Stats.zenMultiplier(s.zen)
        val overdrive = if (overdriveActive) Stats.OVERDRIVE_INCOME_MULT else 1.0
        return petals * resonance * zen * overdrive
    }

    private fun petalsOpen(s: GameState, rpm: Double): Boolean =
        s.level(Upgrades.PETALS) > 0 && rpm >= Stats.PETAL_THRESHOLD_RPM

    private fun unlockAchievements() {
        val fresh = Achievements.newlyMet(state)
        if (fresh.isNotEmpty()) {
            state = state.copy(
                achievements = state.achievements + fresh,
                zen = state.zen + fresh.size,
            )
        }
    }

    // --------------------------------------------------------------------
    // Offline progress
    // --------------------------------------------------------------------

    /**
     * Credits income for time spent away, based on the state saved at
     * [GameState.lastSavedEpochMs]. Only the motor's idle speed earns offline
     * (the ball is assumed to have coasted to its floor), scaled by the Gyro
     * Memory efficiency and capped in duration.
     */
    fun applyOfflineProgress(nowEpochMs: Long): OfflineReport? {
        val last = state.lastSavedEpochMs
        if (last <= 0L || nowEpochMs <= last) return null
        val secondsAway = (nowEpochMs - last) / 1000.0
        if (secondsAway < MIN_OFFLINE_SECONDS) return null

        val gyro = state.level(Upgrades.GYRO)
        val credited = min(secondsAway, Stats.offlineCapHours(gyro) * 3600.0)
        val efficiency = Stats.offlineEfficiency(gyro)

        val motorRpm = Stats.motorRpm(state.level(Upgrades.MOTOR))
        val motorOmega = rpmToOmega(motorRpm)
        val revolutions = motorOmega / TWO_PI * credited
        val earned = revolutions * incomePerRevolution(state, motorRpm, 0.0, false) * efficiency

        state = state.copy(
            omega = max(state.omega.coerceAtMost(motorOmega), motorOmega),
            combo = 0.0,
            overdriveRemaining = 0.0,
            overdriveCooldown = max(0.0, state.overdriveCooldown - secondsAway),
            points = state.points + earned,
            pointsThisRun = state.pointsThisRun + earned,
            totalPointsEarned = state.totalPointsEarned + earned,
            totalRevolutions = state.totalRevolutions + revolutions,
            lastSavedEpochMs = nowEpochMs,
        )
        unlockAchievements()
        return OfflineReport(secondsAway, credited, earned, efficiency)
    }

    // --------------------------------------------------------------------
    // Read model
    // --------------------------------------------------------------------

    fun view(): GameView {
        val s = state
        val rpm = omegaToRpm(s.omega)
        val overdriveActive = s.overdriveRemaining > 0.0
        val overdriveLevel = s.level(Upgrades.OVERDRIVE)
        val perRev = incomePerRevolution(s, rpm, s.combo, overdriveActive)
        return GameView(
            state = s,
            rpm = rpm,
            rpmCap = Stats.rpmCap(s.level(Upgrades.COOLING)),
            pointsPerSecond = s.omega / TWO_PI * perRev,
            pointsPerRev = perRev,
            totalMultiplier = totalMultiplier(s, rpm, s.combo, overdriveActive),
            petalsOpen = petalsOpen(s, rpm),
            petalMultiplier = Stats.petalMultiplier(s.level(Upgrades.PETALS)),
            comboMultiplier = 1.0 + s.combo,
            zenMultiplier = Stats.zenMultiplier(s.zen),
            overdriveActive = overdriveActive,
            overdriveUnlocked = overdriveLevel > 0,
            overdriveReady = overdriveLevel > 0 && s.overdriveCooldown <= 0.0,
            overdriveCooldown = s.overdriveCooldown,
            motorRpm = Stats.motorRpm(s.level(Upgrades.MOTOR)),
            zenOnReset = zenOnReset(),
        )
    }

    companion object {
        /** Largest single integration step in seconds. */
        const val MAX_STEP = 0.05
        /** Ignore trivially short absences. */
        const val MIN_OFFLINE_SECONDS = 10.0
    }
}
