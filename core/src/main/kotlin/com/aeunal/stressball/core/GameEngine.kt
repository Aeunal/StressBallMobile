package com.aeunal.stressball.core

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign

/**
 * The deterministic simulation behind the game.
 *
 * The engine owns a [GameState] and mutates it through a small set of
 * operations. All time-dependent behaviour goes through [tick], which makes
 * the engine trivial to unit test and to run at any frame rate.
 *
 * Inputs ([setFinger], [setTurbo]) are "current controls": set them whenever
 * they change and the next ticks use them. They are not persisted.
 *
 * Not thread-safe: drive it from a single thread (the UI's game loop).
 */
class GameEngine(initial: GameState = GameState()) {

    var state: GameState = initial
        private set

    var fingerTouching: Boolean = false
        private set

    /** Signed finger angular velocity around the ball, rad/s. Only meaningful while touching. */
    var fingerOmega: Double = 0.0
        private set

    var turboHeld: Boolean = false
        private set

    // --------------------------------------------------------------------
    // Inputs
    // --------------------------------------------------------------------

    /**
     * Reports the finger. While [touching], the ball is gripped: it chases the
     * finger's speed, which also means a finger held still brakes the ball.
     * [omega] is the finger's angular velocity around the ball centre in rad/s,
     * positive for a clockwise circle on screen.
     */
    fun setFinger(touching: Boolean, omega: Double = 0.0) {
        fingerTouching = touching
        fingerOmega = if (touching && omega.isFinite()) omega else 0.0
    }

    /** Hold-to-boost. Thrust while charge remains, brake once it is empty, refill when released. */
    fun setTurbo(held: Boolean) {
        turboHeld = held
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
        unlockAchievements()
        return true
    }

    fun canBuy(id: String): Boolean {
        val def = Upgrades.get(id)
        val level = state.level(id)
        return level < def.maxLevel && state.points >= def.costAt(level)
    }

    /** Buys a cosmetic with points and equips it. Returns false if unaffordable or already owned. */
    fun buyCosmetic(id: String): Boolean {
        val def = Cosmetics.byId[id] ?: return false
        if (Cosmetics.isOwned(state, id)) return false
        if (state.points < def.cost) return false
        state = state.copy(
            points = state.points - def.cost,
            ownedCosmetics = state.ownedCosmetics + id,
            equipped = state.equipped + (def.slot.name to id),
        )
        unlockAchievements()
        return true
    }

    /** Equips an owned cosmetic. Returns false if unknown or not owned. */
    fun equipCosmetic(id: String): Boolean {
        val def = Cosmetics.byId[id] ?: return false
        if (!Cosmetics.isOwned(state, id)) return false
        state = state.copy(equipped = state.equipped + (def.slot.name to id))
        return true
    }

    /** Zen the player would receive from a reset right now. */
    fun zenOnReset(): Long = Stats.zenFor(state.pointsThisRun)

    /**
     * Prestige: trade all points, upgrades and speed for Zen, which multiplies
     * income permanently. Cosmetics, records and achievements are kept.
     * Returns false if the reset would grant no Zen.
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
            ownedCosmetics = state.ownedCosmetics,
            equipped = state.equipped,
            lastSavedEpochMs = state.lastSavedEpochMs,
        )
        unlockAchievements()
        return true
    }

    /** Replaces the whole state, e.g. after loading a save. */
    fun load(newState: GameState) {
        state = newState
        fingerTouching = false
        fingerOmega = 0.0
        turboHeld = false
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
    }

    private fun step(dt: Double) {
        val s = state
        val bearings = s.level(Upgrades.BEARINGS)
        val flywheel = s.level(Upgrades.FLYWHEEL)
        val turboLevel = s.level(Upgrades.OVERDRIVE)
        val baseCap = rpmToOmega(Stats.rpmCap(s.level(Upgrades.COOLING)))

        var omega = s.omega
        var charge = s.turboCharge
        val boosting = turboHeld && charge > 0.0
        val overheating = turboHeld && charge <= 0.0

        // 1. Finger grip: chase the geared finger speed in either direction,
        //    limited by how hard the grip can push or brake.
        if (fingerTouching) {
            val target = Stats.gearedTarget(fingerOmega, s.level(Upgrades.GEAR))
            val k = min(1.0, Stats.gripRate(s.level(Upgrades.GRIP)) * dt)
            val limit = Stats.gripAccel(s.level(Upgrades.GRIP)) * dt
            omega += ((target - omega) * k).coerceIn(-limit, limit)
        }

        // 2. Turbo.
        if (boosting) {
            val dir = if (omega < 0.0) -1.0 else 1.0
            omega += dir * Stats.turboAccel(turboLevel) * dt
            charge = max(0.0, charge - dt / Stats.turboCapacity(turboLevel))
        } else if (overheating) {
            omega = towardsZero(omega, Stats.TURBO_OVERHEAT_BRAKE * dt)
        } else {
            charge = min(1.0, charge + dt / Stats.turboRefill(turboLevel))
        }

        // 3. Friction.
        val beforeFriction = omega
        val decel = Stats.viscousFriction(bearings, flywheel) * abs(omega) + Stats.constantFriction(bearings, flywheel)
        omega = towardsZero(omega, decel * dt)

        // 4. Motor floor: once the ball is at its idle speed friction cannot
        //    slow it below that; from rest it ramps up towards the floor.
        val motorOmega = rpmToOmega(Stats.motorRpm(s.level(Upgrades.MOTOR)))
        if (motorOmega > 0.0 && abs(omega) < motorOmega) {
            val dir = if (beforeFriction < 0.0) -1.0 else 1.0
            val before = abs(beforeFriction)
            val ramped = before + (motorOmega - before) * min(1.0, Stats.MOTOR_RESPONSE * dt)
            omega = dir * min(motorOmega, max(abs(omega), ramped))
        }

        // 5. Speed limit. Boosting raises the cap; once released, speed above
        //    the base cap bleeds off instead of snapping.
        val ceiling = if (boosting) {
            baseCap * Stats.turboCapMultiplier(turboLevel)
        } else {
            val excess = abs(s.omega) - baseCap
            if (excess < OVER_CAP_SNAP) baseCap else baseCap + excess * (1.0 - min(1.0, Stats.OVER_CAP_DRAG * dt))
        }
        if (abs(omega) > ceiling) omega = sign(omega) * ceiling

        // 6. Resonance combo.
        val rpm = omegaToRpm(abs(omega))
        val maxCombo = Stats.maxCombo(s.level(Upgrades.RESONANCE))
        var combo = if (rpm >= Stats.RESONANCE_THRESHOLD_RPM) {
            s.combo + Stats.COMBO_BUILD_RATE * dt
        } else {
            s.combo - Stats.COMBO_DECAY_RATE * dt
        }
        combo = combo.coerceIn(0.0, maxCombo)

        // 7. Income.
        val revolutions = abs(omega) / TWO_PI * dt
        val earned = revolutions * incomePerRevolution(s, rpm, combo, boosting)

        state = s.copy(
            omega = omega,
            combo = combo,
            turboCharge = charge,
            points = s.points + earned,
            pointsThisRun = s.pointsThisRun + earned,
            totalPointsEarned = s.totalPointsEarned + earned,
            totalRevolutions = s.totalRevolutions + revolutions,
            bestRpm = max(s.bestRpm, rpm),
            playTimeSeconds = s.playTimeSeconds + dt,
        )
        unlockAchievements()
    }

    /** Reduces |value| by [amount] without crossing zero. */
    private fun towardsZero(value: Double, amount: Double): Double =
        if (abs(value) <= amount) 0.0 else value - sign(value) * amount

    private fun incomePerRevolution(s: GameState, rpm: Double, combo: Double, boosting: Boolean): Double =
        Stats.pointsPerRev(s.level(Upgrades.COUNTER)) * totalMultiplier(s, rpm, combo, boosting)

    private fun totalMultiplier(s: GameState, rpm: Double, combo: Double, boosting: Boolean): Double {
        val petals = if (petalsOpen(s, rpm)) Stats.petalMultiplier(s.level(Upgrades.PETALS)) else 1.0
        val resonance = 1.0 + combo
        val zen = Stats.zenMultiplier(s.zen)
        val turbo = if (boosting) Stats.TURBO_INCOME_MULT else 1.0
        return petals * resonance * zen * turbo
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

        val dir = if (state.omega < 0.0) -1.0 else 1.0
        state = state.copy(
            omega = dir * motorOmega,
            combo = 0.0,
            turboCharge = 1.0,
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
        val rpm = s.rpm
        val turboLevel = s.level(Upgrades.OVERDRIVE)
        val boosting = turboHeld && s.turboCharge > 0.0
        val baseCap = Stats.rpmCap(s.level(Upgrades.COOLING))
        val perRev = incomePerRevolution(s, rpm, s.combo, boosting)
        return GameView(
            state = s,
            rpm = rpm,
            direction = s.direction,
            rpmCap = if (boosting) baseCap * Stats.turboCapMultiplier(turboLevel) else baseCap,
            baseRpmCap = baseCap,
            pointsPerSecond = abs(s.omega) / TWO_PI * perRev,
            pointsPerRev = perRev,
            totalMultiplier = totalMultiplier(s, rpm, s.combo, boosting),
            petalsOpen = petalsOpen(s, rpm),
            petalMultiplier = Stats.petalMultiplier(s.level(Upgrades.PETALS)),
            comboMultiplier = 1.0 + s.combo,
            zenMultiplier = Stats.zenMultiplier(s.zen),
            turboCharge = s.turboCharge,
            turboBoosting = boosting,
            turboOverheating = turboHeld && s.turboCharge <= 0.0,
            turboCapMultiplier = Stats.turboCapMultiplier(turboLevel),
            motorRpm = Stats.motorRpm(s.level(Upgrades.MOTOR)),
            zenOnReset = zenOnReset(),
            fxTier = Stats.fxTier(rpm),
            fingerTouching = fingerTouching,
            bodyColor = Cosmetics.equipped(s, CosmeticSlot.BODY).color,
            capColor = Cosmetics.equipped(s, CosmeticSlot.CAP).color,
        )
    }

    companion object {
        /** Largest single integration step in seconds. */
        const val MAX_STEP = 0.05
        /** Ignore trivially short absences. */
        const val MIN_OFFLINE_SECONDS = 10.0
        /** Speed above the cap (rad/s) below which the bleed-off just snaps to the cap. */
        const val OVER_CAP_SNAP = 0.05
    }
}
