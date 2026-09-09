package com.aeunal.stressball.core

import kotlin.math.pow

/** Broad grouping used by the UI to organise the shop. */
enum class UpgradeCategory { MANUAL, IDLE, SCORE, SPECIAL, GARAGE }

/** Whether an upgrade belongs to one ball or to the whole account. */
enum class UpgradeScope { BALL, ACCOUNT }

/**
 * Static definition of a purchasable upgrade. Player-facing text lives in
 * [GameText] so it can be localised.
 *
 * Costs follow a geometric curve: `baseCost * growth^level`. Reaching
 * [maxLevel] awards gems.
 */
data class UpgradeDef(
    val id: String,
    val category: UpgradeCategory,
    val baseCost: Double,
    val growth: Double,
    val maxLevel: Int,
    val scope: UpgradeScope = UpgradeScope.BALL,
) {
    fun costAt(level: Int): Double = baseCost * growth.pow(level)
}

/**
 * The upgrade catalogue. The ids are part of the save format: never rename them,
 * only add new entries.
 */
object Upgrades {
    // Manual
    const val GEAR = "gear"
    const val GRIP = "grip"
    const val TRACTION = "traction"
    const val GIMBAL = "gimbal"
    /** The turbo. Keeps its historical id. */
    const val OVERDRIVE = "overdrive"
    const val NITRO = "nitro"
    const val KERS = "kers"
    // Idle
    const val BEARINGS = "bearings"
    const val FLYWHEEL = "flywheel"
    const val AERO = "aero"
    const val MOTOR = "motor"
    const val GYRO = "gyro"
    // Score
    const val COUNTER = "counter"
    const val PETALS = "petals"
    const val RESONANCE = "resonance"
    const val COMBO_LOCK = "combo_lock"
    // Special
    const val COOLING = "cooling"
    const val CRYO = "cryo"
    const val HEATSINK = "heatsink"
    // Garage (account)
    const val RACK = "rack"
    const val INTEREST = "interest"
    const val LUCKY = "lucky"
    const val CHEST_LUCK = "chest_luck"

    val all: List<UpgradeDef> = listOf(
        UpgradeDef(GEAR, UpgradeCategory.MANUAL, baseCost = 15.0, growth = 1.6, maxLevel = 20),
        UpgradeDef(GRIP, UpgradeCategory.MANUAL, baseCost = 25.0, growth = 1.55, maxLevel = 20),
        UpgradeDef(TRACTION, UpgradeCategory.MANUAL, baseCost = 20.0, growth = 1.6, maxLevel = 20),
        UpgradeDef(GIMBAL, UpgradeCategory.MANUAL, baseCost = 400.0, growth = 1.0, maxLevel = 1),
        UpgradeDef(OVERDRIVE, UpgradeCategory.MANUAL, baseCost = 300.0, growth = 2.2, maxLevel = 8),
        UpgradeDef(NITRO, UpgradeCategory.MANUAL, baseCost = 900.0, growth = 2.0, maxLevel = 10),
        UpgradeDef(KERS, UpgradeCategory.MANUAL, baseCost = 700.0, growth = 1.9, maxLevel = 10),

        UpgradeDef(BEARINGS, UpgradeCategory.IDLE, baseCost = 40.0, growth = 1.7, maxLevel = 30),
        UpgradeDef(FLYWHEEL, UpgradeCategory.IDLE, baseCost = 150.0, growth = 1.75, maxLevel = 20),
        UpgradeDef(AERO, UpgradeCategory.IDLE, baseCost = 5_000.0, growth = 2.0, maxLevel = 15),
        UpgradeDef(MOTOR, UpgradeCategory.IDLE, baseCost = 100.0, growth = 1.5, maxLevel = 50),
        UpgradeDef(GYRO, UpgradeCategory.IDLE, baseCost = 250.0, growth = 2.0, maxLevel = 8),

        UpgradeDef(COUNTER, UpgradeCategory.SCORE, baseCost = 60.0, growth = 1.65, maxLevel = 40),
        UpgradeDef(PETALS, UpgradeCategory.SCORE, baseCost = 500.0, growth = 2.2, maxLevel = 10),
        UpgradeDef(RESONANCE, UpgradeCategory.SCORE, baseCost = 1200.0, growth = 2.0, maxLevel = 10),
        UpgradeDef(COMBO_LOCK, UpgradeCategory.SCORE, baseCost = 2_500.0, growth = 2.0, maxLevel = 10),

        UpgradeDef(COOLING, UpgradeCategory.SPECIAL, baseCost = 800.0, growth = 1.6, maxLevel = 30),
        UpgradeDef(CRYO, UpgradeCategory.SPECIAL, baseCost = 250_000.0, growth = 4.0, maxLevel = 5),
        UpgradeDef(HEATSINK, UpgradeCategory.SPECIAL, baseCost = 1_500.0, growth = 2.0, maxLevel = 10),

        UpgradeDef(RACK, UpgradeCategory.GARAGE, baseCost = 20_000.0, growth = 2.2, maxLevel = 7, scope = UpgradeScope.ACCOUNT),
        UpgradeDef(INTEREST, UpgradeCategory.GARAGE, baseCost = 50_000.0, growth = 3.0, maxLevel = 5, scope = UpgradeScope.ACCOUNT),
        UpgradeDef(LUCKY, UpgradeCategory.GARAGE, baseCost = 10_000.0, growth = 2.5, maxLevel = 10, scope = UpgradeScope.ACCOUNT),
        UpgradeDef(CHEST_LUCK, UpgradeCategory.GARAGE, baseCost = 100_000.0, growth = 3.0, maxLevel = 5, scope = UpgradeScope.ACCOUNT),
    )

    val byId: Map<String, UpgradeDef> = all.associateBy { it.id }

    fun get(id: String): UpgradeDef = byId[id] ?: error("Unknown upgrade id: $id")

    fun ofScope(scope: UpgradeScope): List<UpgradeDef> = all.filter { it.scope == scope }
}
