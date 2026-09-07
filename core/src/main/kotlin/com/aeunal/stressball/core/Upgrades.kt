package com.aeunal.stressball.core

import kotlin.math.pow

/** Broad grouping used by the UI to organise the shop. */
enum class UpgradeCategory { MANUAL, IDLE, SCORE, SPECIAL }

/**
 * Static definition of a purchasable upgrade. Player-facing text lives in
 * [GameText] so it can be localised.
 *
 * Costs follow a geometric curve: `baseCost * growth^level`.
 */
data class UpgradeDef(
    val id: String,
    val category: UpgradeCategory,
    val baseCost: Double,
    val growth: Double,
    val maxLevel: Int,
) {
    fun costAt(level: Int): Double = baseCost * growth.pow(level)
}

/**
 * The upgrade catalogue. The ids are part of the save format: never rename them,
 * only add new entries.
 */
object Upgrades {
    const val GEAR = "gear"
    const val GRIP = "grip"
    const val BEARINGS = "bearings"
    const val FLYWHEEL = "flywheel"
    const val MOTOR = "motor"
    const val COUNTER = "counter"
    const val PETALS = "petals"
    const val RESONANCE = "resonance"
    const val COOLING = "cooling"
    /** The turbo. Keeps its historical id. */
    const val OVERDRIVE = "overdrive"
    const val GYRO = "gyro"

    val all: List<UpgradeDef> = listOf(
        UpgradeDef(GEAR, UpgradeCategory.MANUAL, baseCost = 15.0, growth = 1.6, maxLevel = 20),
        UpgradeDef(GRIP, UpgradeCategory.MANUAL, baseCost = 25.0, growth = 1.55, maxLevel = 20),
        UpgradeDef(OVERDRIVE, UpgradeCategory.MANUAL, baseCost = 300.0, growth = 2.2, maxLevel = 8),
        UpgradeDef(BEARINGS, UpgradeCategory.IDLE, baseCost = 40.0, growth = 1.7, maxLevel = 30),
        UpgradeDef(FLYWHEEL, UpgradeCategory.IDLE, baseCost = 150.0, growth = 1.75, maxLevel = 20),
        UpgradeDef(MOTOR, UpgradeCategory.IDLE, baseCost = 100.0, growth = 1.5, maxLevel = 50),
        UpgradeDef(GYRO, UpgradeCategory.IDLE, baseCost = 250.0, growth = 2.0, maxLevel = 8),
        UpgradeDef(COUNTER, UpgradeCategory.SCORE, baseCost = 60.0, growth = 1.65, maxLevel = 40),
        UpgradeDef(PETALS, UpgradeCategory.SCORE, baseCost = 500.0, growth = 2.2, maxLevel = 10),
        UpgradeDef(RESONANCE, UpgradeCategory.SCORE, baseCost = 1200.0, growth = 2.0, maxLevel = 10),
        UpgradeDef(COOLING, UpgradeCategory.SPECIAL, baseCost = 800.0, growth = 1.9, maxLevel = 15),
    )

    val byId: Map<String, UpgradeDef> = all.associateBy { it.id }

    fun get(id: String): UpgradeDef = byId[id] ?: error("Unknown upgrade id: $id")
}
