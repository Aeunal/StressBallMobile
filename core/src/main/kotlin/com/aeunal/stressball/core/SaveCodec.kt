package com.aeunal.stressball.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * JSON (de)serialisation of [GameState]. Tolerant of unknown/missing keys and
 * out-of-range values, and able to read saves from before the garage
 * existed (versions 1–3), which had a single flat ball.
 */
object SaveCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
        // Parse NaN/Infinity instead of rejecting the whole save; sanitised() clamps them.
        allowSpecialFloatingPointValues = true
    }

    fun encode(state: GameState): String = json.encodeToString(GameState.serializer(), state)

    /** Returns null if [text] is blank or cannot be parsed. */
    fun decode(text: String?): GameState? {
        if (text.isNullOrBlank()) return null
        return runCatching {
            val root = json.parseToJsonElement(text).jsonObject
            val version = root["version"]?.jsonPrimitive?.intOrNull ?: 1
            if (version < 4) {
                json.decodeFromJsonElement(LegacyStateV3.serializer(), root).toV4()
            } else {
                json.decodeFromJsonElement(GameState.serializer(), root)
            }
        }.getOrNull()?.sanitised()
    }

    /** The flat, single-ball save layout used by versions 1–3. */
    @Serializable
    private data class LegacyStateV3(
        val points: Double = 0.0,
        val pointsThisRun: Double = 0.0,
        val totalPointsEarned: Double = 0.0,
        val omega: Double = 0.0,
        val upgrades: Map<String, Int> = emptyMap(),
        val zen: Long = 0,
        val prestigeCount: Int = 0,
        val bestRpm: Double = 0.0,
        val totalRevolutions: Double = 0.0,
        val combo: Double = 0.0,
        val turboCharge: Double = 1.0,
        val turboCooldown: Double = 0.0,
        val playTimeSeconds: Double = 0.0,
        val lastSavedEpochMs: Long = 0L,
        val achievements: Set<String> = emptySet(),
    ) {
        fun toV4(): GameState = GameState(
            points = points,
            zen = zen,
            totalPointsEarned = totalPointsEarned,
            balls = listOf(
                BallState(
                    id = "b1",
                    typeId = BallTypes.DEFAULT,
                    omega = omega,
                    upgrades = upgrades,
                    combo = combo,
                    turboCharge = turboCharge,
                    turboCooldown = turboCooldown,
                    pointsThisRun = pointsThisRun,
                    totalRevolutions = totalRevolutions,
                    bestRpm = bestRpm,
                    prestigeCount = prestigeCount,
                    invested = upgrades.entries.sumOf { (id, level) ->
                        val def = Upgrades.byId[id] ?: return@sumOf 0.0
                        (0 until level.coerceAtMost(def.maxLevel)).sumOf { def.costAt(it) }
                    },
                ),
            ),
            activeBallId = "b1",
            nextBallNumber = 2,
            achievements = achievements,
            playTimeSeconds = playTimeSeconds,
            lastSavedEpochMs = lastSavedEpochMs,
        )
    }

    /** Clamp any values a hand-edited or corrupted save could have put out of range. */
    private fun GameState.sanitised(): GameState {
        val ballDefs = Upgrades.ofScope(UpgradeScope.BALL).associateBy { it.id }
        val accountDefs = Upgrades.ofScope(UpgradeScope.ACCOUNT).associateBy { it.id }
        val seen = HashSet<String>()
        var cleanBalls = balls.filter { it.id.isNotBlank() && seen.add(it.id) }.map { b ->
            b.copy(
                typeId = if (b.typeId in BallTypes.byId) b.typeId else BallTypes.DEFAULT,
                omega = b.omega.finiteOr(0.0),
                upgrades = b.upgrades.filterKeys { it in ballDefs }
                    .mapValues { (id, lvl) -> lvl.coerceIn(0, ballDefs.getValue(id).maxLevel) },
                combo = b.combo.finiteOr(0.0).coerceAtLeast(0.0),
                turboCharge = b.turboCharge.finiteOr(1.0).coerceIn(0.0, 1.0),
                turboCooldown = b.turboCooldown.finiteOr(0.0).coerceAtLeast(0.0),
                pointsThisRun = b.pointsThisRun.finiteOr(0.0).coerceAtLeast(0.0),
                totalRevolutions = b.totalRevolutions.finiteOr(0.0).coerceAtLeast(0.0),
                bestRpm = b.bestRpm.finiteOr(0.0).coerceAtLeast(0.0),
                prestigeCount = b.prestigeCount.coerceAtLeast(0),
                invested = b.invested.finiteOr(0.0).coerceAtLeast(0.0),
                topView = b.topView && b.level(Upgrades.GIMBAL) > 0,
            )
        }
        if (cleanBalls.isEmpty()) cleanBalls = listOf(BallState(id = "b1", typeId = BallTypes.DEFAULT))
        val active = if (cleanBalls.any { it.id == activeBallId }) activeBallId else cleanBalls.first().id
        val maxNumber = cleanBalls.mapNotNull { it.id.removePrefix("b").toIntOrNull() }.maxOrNull() ?: 1
        val owned = ownedSkins.filter { it in Skins.byId }.toSet()
        val cleaned = copy(ownedSkins = owned)
        return copy(
            points = points.finiteOr(0.0).coerceAtLeast(0.0),
            gems = gems.coerceAtLeast(0),
            zen = zen.coerceAtLeast(0),
            totalPointsEarned = totalPointsEarned.finiteOr(0.0).coerceAtLeast(0.0),
            balls = cleanBalls.take(Stats.MAX_BALLS),
            activeBallId = active,
            nextBallNumber = maxOf(nextBallNumber, maxNumber + 1),
            accountUpgrades = accountUpgrades.filterKeys { it in accountDefs }
                .mapValues { (id, lvl) -> lvl.coerceIn(0, accountDefs.getValue(id).maxLevel) },
            ownedSkins = owned,
            outerSkin = outerSkin?.takeIf { Skins.byId[it]?.slot == SkinSlot.OUTER && Skins.isOwned(cleaned, it) },
            interiorSkin = interiorSkin?.takeIf { Skins.byId[it]?.slot == SkinSlot.INTERIOR && Skins.isOwned(cleaned, it) },
            achievements = achievements.filter { it in Achievements.byId }.toSet(),
            playTimeSeconds = playTimeSeconds.finiteOr(0.0).coerceAtLeast(0.0),
            chestsOpened = chestsOpened.coerceAtLeast(0),
            ballsSold = ballsSold.coerceAtLeast(0),
            sparksTapped = sparksTapped.coerceAtLeast(0),
            sparkTimer = sparkTimer.finiteOr(Stats.FIRST_SPARK_SECONDS).coerceAtLeast(0.0),
            sparkRemaining = sparkRemaining.finiteOr(0.0).coerceIn(0.0, Stats.SPARK_WINDOW),
            buffRemaining = buffRemaining.finiteOr(0.0).coerceAtLeast(0.0),
            buff = if (buffRemaining.finiteOr(0.0) > 0.0) buff else null,
            version = GameState.CURRENT_VERSION,
        )
    }

    private fun Double.finiteOr(fallback: Double) = if (isFinite()) this else fallback
}
