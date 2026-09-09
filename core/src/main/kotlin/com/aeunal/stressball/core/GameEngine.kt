package com.aeunal.stressball.core

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign
import kotlin.random.Random

/**
 * The deterministic simulation behind the game.
 *
 * The engine owns a [GameState] (the whole profile) and mutates it through a
 * small set of operations. All time-dependent behaviour goes through [tick],
 * which makes the engine trivial to unit test and to run at any frame rate.
 * Physics runs on the active ball; garage balls idle at their motor floor and
 * earn a share of that income.
 *
 * Inputs ([setFinger], [setTurbo]) are "current controls": set them whenever
 * they change and the next ticks use them. They are not persisted.
 * The formulas behind every step are documented in [Stats].
 *
 * Not thread-safe: drive it from a single thread (the UI's game loop).
 */
class GameEngine(
    initial: GameState = GameState(),
    private val random: Random = Random.Default,
) {

    var state: GameState = initial
        private set

    var fingerTouching: Boolean = false
        private set

    /** Finger angular velocity around the ball centre (rad/s, signed). See [FingerSample.twistOmega]. */
    var fingerTwist: Double = 0.0
        private set

    /** Ball speed implied by the finger dragging the face (rad/s, signed). See [FingerSample.dragOmega]. */
    var fingerDrag: Double = 0.0
        private set

    /** How hard the ball is squeezed, 0..1. */
    var turboWeight: Double = 0.0
        private set

    /** How fast the squeeze is tightening (1/s, never negative). */
    var squeezeRate: Double = 0.0
        private set

    val active: BallState get() = state.active

    // --------------------------------------------------------------------
    // Inputs
    // --------------------------------------------------------------------

    /**
     * Reports the finger. While [touching], the ball is gripped: it chases the
     * speed the finger implies, which also means a finger held still brakes
     * the ball. Which reading is used depends on the view: from the side a
     * stroke across the face rolls the ball ([dragOmega]); from above a
     * circling finger twists it like the cap ([twistOmega]).
     */
    fun setFinger(touching: Boolean, twistOmega: Double = 0.0, dragOmega: Double = 0.0) {
        fingerTouching = touching
        fingerTwist = if (touching && twistOmega.isFinite()) twistOmega else 0.0
        fingerDrag = if (touching && dragOmega.isFinite()) dragOmega else 0.0
    }

    /**
     * Squeeze the ball. [weight] 0..1 scales thrust, fuel burn, the raised
     * cap and the income bonus; [squeezeRate] (1/s, how fast the pinch is
     * closing) adds a kick on top.
     */
    fun setTurbo(weight: Double, squeezeRate: Double = 0.0) {
        turboWeight = if (weight.isFinite()) weight.coerceIn(0.0, 1.0) else 0.0
        this.squeezeRate = if (squeezeRate.isFinite()) max(0.0, squeezeRate) else 0.0
    }

    /** Speed the finger is asking for right now, for the current view. */
    fun fingerTarget(): Double {
        val b = active
        val circularity = if (b.topView) 1.0 else 0.0
        return Stats.fingerTarget(fingerTwist, fingerDrag, circularity, b.level(Upgrades.GEAR), b.level(Upgrades.TRACTION))
    }

    /** Looks at the ball from above (needs the Gimbal Mount) or back from the side. */
    fun setTopView(top: Boolean): Boolean {
        if (top && active.level(Upgrades.GIMBAL) <= 0) return false
        updateActive { it.copy(topView = top) }
        return true
    }

    // --------------------------------------------------------------------
    // Shop
    // --------------------------------------------------------------------

    /** Buys one level of [id] (on the active ball or the account) if affordable and below max level. */
    fun buy(id: String): Boolean {
        val def = Upgrades.get(id)
        val level = state.level(id)
        if (level >= def.maxLevel) return false
        val cost = def.costAt(level)
        if (state.points < cost) return false
        val newLevel = level + 1
        val gemReward = if (newLevel >= def.maxLevel) Stats.GEMS_PER_MAX else 0L
        state = if (def.scope == UpgradeScope.ACCOUNT) {
            state.copy(
                points = state.points - cost,
                gems = state.gems + gemReward,
                accountUpgrades = state.accountUpgrades + (id to newLevel),
            )
        } else {
            val b = active
            state.copy(
                points = state.points - cost,
                gems = state.gems + gemReward,
                balls = replaceBall(b.copy(upgrades = b.upgrades + (id to newLevel), invested = b.invested + cost)),
            )
        }
        unlockAchievements()
        return true
    }

    fun canBuy(id: String): Boolean {
        val def = Upgrades.get(id)
        val level = state.level(id)
        return level < def.maxLevel && state.points >= def.costAt(level)
    }

    /** Free gems, for now. */
    fun topUpGems() {
        state = state.copy(gems = state.gems + Stats.GEM_TOP_UP)
        unlockAchievements()
    }

    /** Buys a skin with gems and equips it. */
    fun buySkin(id: String): Boolean {
        val def = Skins.byId[id] ?: return false
        if (Skins.isOwned(state, id) || state.gems < def.gems) return false
        state = state.copy(gems = state.gems - def.gems, ownedSkins = state.ownedSkins + id)
        equipSkin(id)
        unlockAchievements()
        return true
    }

    /** Equips an owned skin in its slot, replacing whatever was there. */
    fun equipSkin(id: String): Boolean {
        val def = Skins.byId[id] ?: return false
        if (!Skins.isOwned(state, id)) return false
        state = when (def.slot) {
            SkinSlot.OUTER -> state.copy(outerSkin = id)
            SkinSlot.INTERIOR -> state.copy(interiorSkin = id)
        }
        return true
    }

    fun unequipSkin(slot: SkinSlot) {
        state = when (slot) {
            SkinSlot.OUTER -> state.copy(outerSkin = null)
            SkinSlot.INTERIOR -> state.copy(interiorSkin = null)
        }
    }

    // --------------------------------------------------------------------
    // Garage
    // --------------------------------------------------------------------

    fun chestCost(): Double = Stats.chestCost(state.balls.size)

    fun canOpenChest(): Boolean = state.balls.size < Stats.MAX_BALLS && state.points >= chestCost()

    /** Pays for a chest and adds the ball it contains. Returns the new ball, or null. */
    fun openChest(): BallState? {
        if (!canOpenChest()) return null
        val cost = chestCost()
        val type = BallTypes.roll(random, state.accountLevel(Upgrades.CHEST_LUCK))
        val ball = BallState(id = "b${state.nextBallNumber}", typeId = type.id)
        state = state.copy(
            points = state.points - cost,
            balls = state.balls + ball,
            nextBallNumber = state.nextBallNumber + 1,
            chestsOpened = state.chestsOpened + 1,
        )
        unlockAchievements()
        return ball
    }

    /** Makes [id] the ball being played. */
    fun switchBall(id: String): Boolean {
        if (state.ball(id) == null || id == state.activeBallId) return false
        state = state.copy(activeBallId = id)
        setFinger(false)
        setTurbo(0.0)
        return true
    }

    /** Sells a ball for its rarity value plus half of what was invested. The last ball cannot be sold. */
    fun sellBall(id: String): Boolean {
        val ball = state.ball(id) ?: return false
        if (state.balls.size <= 1) return false
        val remaining = state.balls.filter { it.id != id }
        state = state.copy(
            points = state.points + Stats.sellValue(ball),
            balls = remaining,
            activeBallId = if (state.activeBallId == id) remaining.first().id else state.activeBallId,
            ballsSold = state.ballsSold + 1,
        )
        unlockAchievements()
        return true
    }

    // --------------------------------------------------------------------
    // Golden sparks
    // --------------------------------------------------------------------

    /** Taps the golden spark if one is showing; grants a random buff. Returns it. */
    fun tapSpark(): BuffKind? {
        if (state.sparkRemaining <= 0.0) return null
        val lucky = state.accountLevel(Upgrades.LUCKY)
        val r = random.nextDouble()
        val kind = when {
            r < 0.40 -> BuffKind.FRENZY
            r < 0.65 -> BuffKind.JACKPOT
            r < 0.85 -> BuffKind.RECHARGE
            else -> BuffKind.WILD_GRIP
        }
        var s = state.copy(
            sparkRemaining = 0.0,
            sparkTimer = Stats.sparkInterval(lucky),
            sparksTapped = state.sparksTapped + 1,
        )
        s = when (kind) {
            BuffKind.FRENZY -> s.copy(buff = kind, buffRemaining = Stats.frenzyDuration(lucky))
            BuffKind.JACKPOT -> {
                val payout = max(Stats.JACKPOT_MIN_POINTS, incomePerSecond(s) * 60.0 * Stats.JACKPOT_MINUTES)
                s.copy(
                    points = s.points + payout,
                    totalPointsEarned = s.totalPointsEarned + payout,
                    balls = s.balls.map { if (it.id == s.activeBallId) it.copy(pointsThisRun = it.pointsThisRun + payout) else it },
                )
            }
            BuffKind.RECHARGE -> s.copy(
                buff = kind, buffRemaining = Stats.RECHARGE_SECONDS,
                balls = s.balls.map { if (it.id == s.activeBallId) it.copy(turboCharge = 1.0, turboCooldown = 0.0) else it },
            )
            BuffKind.WILD_GRIP -> s.copy(buff = kind, buffRemaining = Stats.WILD_GRIP_SECONDS)
        }
        state = s
        unlockAchievements()
        return kind
    }

    // --------------------------------------------------------------------
    // Prestige, load, save
    // --------------------------------------------------------------------

    /** Zen the player would receive from resetting the active ball right now. */
    fun zenOnReset(): Long = Stats.zenFor(active.pointsThisRun)

    /**
     * Prestige the active ball: its upgrades, speed and the point balance are
     * given up for Zen, which multiplies income permanently. Other balls,
     * skins, records and achievements are kept.
     */
    fun prestige(): Boolean {
        val gain = zenOnReset()
        if (gain <= 0) return false
        val b = active
        state = state.copy(
            points = 0.0,
            zen = state.zen + gain,
            balls = replaceBall(
                BallState(
                    id = b.id, typeId = b.typeId,
                    totalRevolutions = b.totalRevolutions, bestRpm = b.bestRpm,
                    prestigeCount = b.prestigeCount + 1,
                ),
            ),
        )
        unlockAchievements()
        return true
    }

    /** Replaces the whole state, e.g. after loading a save. */
    fun load(newState: GameState) {
        state = newState
        setFinger(false)
        setTurbo(0.0)
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
        val b = s.active
        val type = BallTypes.get(b.typeId)
        val skin = Skins.buff(s)
        val bearings = b.level(Upgrades.BEARINGS)
        val flywheel = b.level(Upgrades.FLYWHEEL)
        val turboLevel = b.level(Upgrades.OVERDRIVE)
        val baseCap = rpmToOmega(baseRpmCap(b, type, skin))

        // 0. Timers: buff and golden spark.
        val buffRemaining = max(0.0, s.buffRemaining - dt)
        val buff = if (buffRemaining > 0.0) s.buff else null
        var sparkRemaining = s.sparkRemaining
        var sparkTimer = s.sparkTimer
        if (sparkRemaining > 0.0) {
            sparkRemaining = max(0.0, sparkRemaining - dt)
            if (sparkRemaining == 0.0) sparkTimer = Stats.sparkInterval(s.accountLevel(Upgrades.LUCKY))
        } else {
            sparkTimer -= dt
            if (sparkTimer <= 0.0) {
                sparkRemaining = Stats.SPARK_WINDOW
                sparkTimer = 0.0
            }
        }

        var omega = b.omega
        var charge = b.turboCharge
        var cooldown = b.turboCooldown
        val w = turboWeight
        val boosting = w > 0.0 && charge > 0.0
        var recovered = 0.0 // speed shed by finger braking, for the Kinetic Harvester

        // 1. Finger: a slipping clutch. Pull towards the implied speed at the
        //    grip rate, but never harder than the rubber can transmit.
        if (fingerTouching) {
            val target = fingerTarget()
            val k = min(1.0, Stats.gripRate(b.level(Upgrades.GRIP)) * dt)
            val wild = if (buff == BuffKind.WILD_GRIP) Stats.WILD_GRIP_MULT else 1.0
            val limit = Stats.gripAccel(b.level(Upgrades.GRIP)) * type.traits.grip * skin.grip * wild * dt
            val next = omega + ((target - omega) * k).coerceIn(-limit, limit)
            if (abs(next) < abs(omega)) recovered += abs(omega) - abs(next)
            omega = next
        }

        // 2. Turbo: thrust and fuel burn scale with the squeeze; a fast pinch
        //    kicks extra. Squeezing an empty tank brakes. Fuel only refills
        //    after a cooldown that every squeeze restarts.
        val capacity = Stats.turboCapacity(turboLevel) * type.traits.turbo * skin.turboFuel
        if (w > 0.0) {
            if (charge > 0.0) {
                val dir = if (omega < 0.0) -1.0 else 1.0
                val thrust = Stats.turboAccel(turboLevel) * w +
                    Stats.turboSqueezeGain(turboLevel) * Stats.nitroFactor(b.level(Upgrades.NITRO)) * squeezeRate
                omega += dir * thrust * dt
                if (buff != BuffKind.RECHARGE) charge = max(0.0, charge - w * dt / capacity)
            } else {
                val brake = Stats.TURBO_OVERHEAT_BRAKE * Stats.heatSinkFactor(b.level(Upgrades.HEATSINK)) * w * dt
                val next = towardsZero(omega, brake)
                recovered += abs(omega) - abs(next)
                omega = next
            }
            cooldown = Stats.turboCooldown(turboLevel)
        } else if (cooldown > 0.0) {
            cooldown = max(0.0, cooldown - dt)
        } else {
            charge = min(1.0, charge + dt / Stats.turboRefill(turboLevel))
        }

        // 3. Bearing friction (Coulomb + viscous over inertia) and air drag.
        val beforeFriction = omega
        val bearing = (Stats.viscousFriction(bearings, flywheel) * abs(omega) + Stats.constantFriction(bearings, flywheel)) * type.traits.drag
        val aero = Stats.aeroDrag(b.level(Upgrades.AERO)) * omega * omega / Stats.inertia(flywheel)
        omega = towardsZero(omega, (bearing + aero) * dt)

        // 4. Motor floor: once the ball is at its idle speed friction cannot
        //    slow it below that; from rest it ramps up towards the floor.
        val motorOmega = rpmToOmega(motorRpm(b, skin))
        if (motorOmega > 0.0 && abs(omega) < motorOmega) {
            val dir = if (beforeFriction < 0.0) -1.0 else 1.0
            val before = abs(beforeFriction)
            val ramped = before + (motorOmega - before) * min(1.0, Stats.MOTOR_RESPONSE * dt)
            omega = dir * min(motorOmega, max(abs(omega), ramped))
        }

        // 5. Speed limit. The squeeze raises the cap in proportion; speed left
        //    above the current cap bleeds off instead of snapping.
        val boostedCap = if (boosting) baseCap * (1.0 + w * (Stats.turboCapMultiplier(turboLevel) - 1.0)) else baseCap
        val excess = abs(b.omega) - boostedCap
        val ceiling = if (excess < OVER_CAP_SNAP) boostedCap else boostedCap + excess * (1.0 - min(1.0, Stats.OVER_CAP_DRAG * dt))
        if (abs(omega) > ceiling) omega = sign(omega) * ceiling

        // 6. Resonance combo.
        val rpm = omegaToRpm(abs(omega))
        val maxCombo = Stats.maxCombo(b.level(Upgrades.RESONANCE)) * skin.comboMax
        var combo = if (rpm >= Stats.RESONANCE_THRESHOLD_RPM) {
            b.combo + Stats.COMBO_BUILD_RATE * dt
        } else {
            b.combo - Stats.COMBO_DECAY_RATE * Stats.comboDecayFactor(b.level(Upgrades.COMBO_LOCK)) * dt
        }
        combo = combo.coerceIn(0.0, maxCombo)

        // 7. Income: the active ball, energy recovered while braking, the
        //    garage, and interest.
        val perRev = incomePerRevolution(s, b, type, skin, rpm, combo, if (boosting) w else 0.0, buff)
        val revolutions = abs(omega) / TWO_PI * dt
        val kers = recovered / TWO_PI * Stats.kersFraction(b.level(Upgrades.KERS)) * perRev
        val activeEarned = revolutions * perRev + kers
        val garageEarned = garageIncomePerSecond(s) * dt
        val interest = s.points * Stats.interestPerSecond(s.accountLevel(Upgrades.INTEREST)) * dt
        val earned = activeEarned + garageEarned + interest

        state = s.copy(
            points = s.points + earned,
            totalPointsEarned = s.totalPointsEarned + earned,
            playTimeSeconds = s.playTimeSeconds + dt,
            buff = buff,
            buffRemaining = buffRemaining,
            sparkTimer = sparkTimer,
            sparkRemaining = sparkRemaining,
            balls = replaceBall(
                b.copy(
                    omega = omega,
                    combo = combo,
                    turboCharge = charge,
                    turboCooldown = cooldown,
                    pointsThisRun = b.pointsThisRun + activeEarned,
                    totalRevolutions = b.totalRevolutions + revolutions,
                    bestRpm = max(b.bestRpm, rpm),
                ),
            ),
        )
        unlockAchievements()
    }

    /** Reduces |value| by [amount] without crossing zero. */
    private fun towardsZero(value: Double, amount: Double): Double =
        if (abs(value) <= amount) 0.0 else value - sign(value) * amount

    private fun replaceBall(ball: BallState): List<BallState> = state.balls.map { if (it.id == ball.id) ball else it }

    private fun updateActive(f: (BallState) -> BallState) {
        state = state.copy(balls = replaceBall(f(active)))
    }

    // --------------------------------------------------------------------
    // Formulas shared by the step, the view and offline progress
    // --------------------------------------------------------------------

    private fun baseRpmCap(b: BallState, type: BallTypeDef, skin: SkinBuff): Double =
        Stats.rpmCap(b.level(Upgrades.COOLING)) * Stats.cryoFactor(b.level(Upgrades.CRYO)) * type.traits.cap * skin.cap

    private fun motorRpm(b: BallState, skin: SkinBuff): Double = Stats.motorRpm(b.level(Upgrades.MOTOR)) * skin.motor

    private fun petalsOpen(b: BallState, rpm: Double): Boolean =
        b.level(Upgrades.PETALS) > 0 && rpm >= Stats.PETAL_THRESHOLD_RPM

    private fun totalMultiplier(
        s: GameState, b: BallState, type: BallTypeDef, skin: SkinBuff,
        rpm: Double, combo: Double, boostWeight: Double, buff: BuffKind?,
    ): Double {
        val petals = if (petalsOpen(b, rpm)) Stats.petalMultiplier(b.level(Upgrades.PETALS)) else 1.0
        val resonance = 1.0 + combo
        val zen = Stats.zenMultiplier(s.zen)
        val turbo = 1.0 + boostWeight * (Stats.TURBO_INCOME_MULT - 1.0)
        val frenzy = if (buff == BuffKind.FRENZY) Stats.FRENZY_MULT else 1.0
        return petals * resonance * zen * turbo * frenzy * type.traits.income * skin.income
    }

    private fun incomePerRevolution(
        s: GameState, b: BallState, type: BallTypeDef, skin: SkinBuff,
        rpm: Double, combo: Double, boostWeight: Double, buff: BuffKind?,
    ): Double = Stats.pointsPerRev(b.level(Upgrades.COUNTER)) * totalMultiplier(s, b, type, skin, rpm, combo, boostWeight, buff)

    /** Idle income of one ball at its motor floor, before garage/offline efficiency. */
    private fun motorIncomePerSecond(s: GameState, b: BallState): Double {
        val type = BallTypes.get(b.typeId)
        val skin = if (b.id == s.activeBallId) Skins.buff(s) else SkinBuff.NONE
        val motor = motorRpm(b, skin)
        if (motor <= 0.0) return 0.0
        return rpmToOmega(motor) / TWO_PI * incomePerRevolution(s, b, type, skin, motor, 0.0, 0.0, null)
    }

    /** Income per second of every ball that is not being played. */
    fun garageIncomePerSecond(s: GameState = state): Double {
        val rack = Stats.rackEfficiency(s.accountLevel(Upgrades.RACK))
        return s.balls.filter { it.id != s.activeBallId }.sumOf { motorIncomePerSecond(s, it) } * rack
    }

    /** The active ball's current income per second (no garage, no interest). */
    private fun incomePerSecond(s: GameState): Double {
        val b = s.active
        val type = BallTypes.get(b.typeId)
        val skin = Skins.buff(s)
        val rpm = b.rpm
        val boosting = turboWeight > 0.0 && b.turboCharge > 0.0
        return abs(b.omega) / TWO_PI * incomePerRevolution(s, b, type, skin, rpm, b.combo, if (boosting) turboWeight else 0.0, s.activeBuff)
    }

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
     * [GameState.lastSavedEpochMs]. Every ball is assumed to have coasted to
     * its motor floor: the active one earns at its Gyro Memory efficiency,
     * garage balls at the rack's share of that. Duration is capped by the
     * active ball's Gyro Memory.
     */
    fun applyOfflineProgress(nowEpochMs: Long): OfflineReport? {
        val last = state.lastSavedEpochMs
        if (last <= 0L || nowEpochMs <= last) return null
        val secondsAway = (nowEpochMs - last) / 1000.0
        if (secondsAway < MIN_OFFLINE_SECONDS) return null

        val s = state
        val gyro = s.active.level(Upgrades.GYRO)
        val credited = min(secondsAway, Stats.offlineCapHours(gyro) * 3600.0)
        val efficiency = Stats.offlineEfficiency(gyro)
        val perSecond = motorIncomePerSecond(s, s.active) + garageIncomePerSecond(s)
        val earned = perSecond * credited * efficiency

        val activeMotor = rpmToOmega(motorRpm(s.active, Skins.buff(s)))
        state = s.copy(
            points = s.points + earned,
            totalPointsEarned = s.totalPointsEarned + earned,
            buff = null,
            buffRemaining = 0.0,
            lastSavedEpochMs = nowEpochMs,
            balls = s.balls.map { b ->
                val motor = if (b.id == s.activeBallId) activeMotor else rpmToOmega(motorRpm(b, SkinBuff.NONE))
                val dir = if (b.omega < 0.0) -1.0 else 1.0
                b.copy(
                    omega = dir * motor,
                    combo = 0.0,
                    turboCharge = 1.0,
                    turboCooldown = 0.0,
                    pointsThisRun = if (b.id == s.activeBallId) b.pointsThisRun + earned else b.pointsThisRun,
                    totalRevolutions = b.totalRevolutions + motor / TWO_PI * credited,
                )
            },
        )
        unlockAchievements()
        return OfflineReport(secondsAway, credited, earned, efficiency)
    }

    // --------------------------------------------------------------------
    // Read model
    // --------------------------------------------------------------------

    fun view(): GameView {
        val s = state
        val b = s.active
        val type = BallTypes.get(b.typeId)
        val skin = Skins.buff(s)
        val rpm = b.rpm
        val turboLevel = b.level(Upgrades.OVERDRIVE)
        val w = turboWeight
        val boosting = w > 0.0 && b.turboCharge > 0.0
        val baseCap = baseRpmCap(b, type, skin)
        val buff = s.activeBuff
        val perRev = incomePerRevolution(s, b, type, skin, rpm, b.combo, if (boosting) w else 0.0, buff)
        val rack = Stats.rackEfficiency(s.accountLevel(Upgrades.RACK))
        return GameView(
            state = s,
            ball = b,
            type = type,
            rpm = rpm,
            direction = b.direction,
            rpmCap = if (boosting) baseCap * (1.0 + w * (Stats.turboCapMultiplier(turboLevel) - 1.0)) else baseCap,
            baseRpmCap = baseCap,
            pointsPerSecond = abs(b.omega) / TWO_PI * perRev,
            pointsPerRev = perRev,
            totalMultiplier = totalMultiplier(s, b, type, skin, rpm, b.combo, if (boosting) w else 0.0, buff),
            petalsOpen = petalsOpen(b, rpm),
            petalMultiplier = Stats.petalMultiplier(b.level(Upgrades.PETALS)),
            comboMultiplier = 1.0 + b.combo,
            zenMultiplier = Stats.zenMultiplier(s.zen),
            turboCharge = b.turboCharge,
            turboCooldown = b.turboCooldown,
            turboWeight = w,
            turboBoosting = boosting,
            turboOverheating = w > 0.0 && b.turboCharge <= 0.0,
            turboCapMultiplier = Stats.turboCapMultiplier(turboLevel),
            motorRpm = motorRpm(b, skin),
            zenOnReset = zenOnReset(),
            fxTier = Stats.fxTier(rpm),
            fingerTouching = fingerTouching,
            bodyColor = type.body,
            capColor = type.cap,
            auraColor = type.aura,
            outerSkin = Skins.equipped(s, SkinSlot.OUTER),
            interiorSkin = Skins.equipped(s, SkinSlot.INTERIOR),
            topView = b.topView,
            topViewUnlocked = b.level(Upgrades.GIMBAL) > 0,
            gems = s.gems,
            chestCost = chestCost(),
            canOpenChest = canOpenChest(),
            garageFull = s.balls.size >= Stats.MAX_BALLS,
            balls = s.balls.map { ball ->
                val isActive = ball.id == s.activeBallId
                BallSummary(
                    id = ball.id,
                    typeId = ball.typeId,
                    rarity = BallTypes.get(ball.typeId).rarity,
                    rpm = ball.rpm,
                    pointsPerSecond = if (isActive) abs(b.omega) / TWO_PI * perRev else motorIncomePerSecond(s, ball) * rack,
                    upgradeLevels = ball.upgrades.values.sum(),
                    sellValue = Stats.sellValue(ball),
                    isActive = isActive,
                    prestigeCount = ball.prestigeCount,
                )
            },
            garageIncomePerSecond = garageIncomePerSecond(s),
            sparkActive = s.sparkRemaining > 0.0,
            sparkRemaining = s.sparkRemaining,
            buff = buff,
            buffRemaining = s.buffRemaining,
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
