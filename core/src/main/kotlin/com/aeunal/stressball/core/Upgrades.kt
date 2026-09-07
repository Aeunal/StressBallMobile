package com.aeunal.stressball.core

import kotlin.math.pow

/** Broad grouping used by the UI to organise the shop. */
enum class UpgradeCategory(val title: String) {
    MANUAL("Manual"),
    IDLE("Idle"),
    SCORE("Score"),
    SPECIAL("Special"),
}

/**
 * Static definition of a purchasable upgrade.
 *
 * Costs follow a geometric curve: `baseCost * growth^level`. [describe] returns
 * a human readable effect line for the given level, so the shop can show what
 * the next purchase does.
 */
data class UpgradeDef(
    val id: String,
    val name: String,
    val tagline: String,
    val category: UpgradeCategory,
    val baseCost: Double,
    val growth: Double,
    val maxLevel: Int,
    val describe: (level: Int) -> String,
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
    const val OVERDRIVE = "overdrive"
    const val GYRO = "gyro"

    val all: List<UpgradeDef> = listOf(
        UpgradeDef(
            id = GEAR,
            name = "Gear Cap",
            tagline = "A taller gear on the cap turns one finger circle into more ball revolutions.",
            category = UpgradeCategory.MANUAL,
            baseCost = 15.0, growth = 1.6, maxLevel = 25,
        ) { l -> "Finger spin x${fmt2(Stats.gearRatio(l))}" },
        UpgradeDef(
            id = GRIP,
            name = "Grip Tape",
            tagline = "The ball catches your finger faster instead of slipping.",
            category = UpgradeCategory.MANUAL,
            baseCost = 25.0, growth = 1.55, maxLevel = 25,
        ) { l -> "Grip ${fmt2(Stats.coupling(l))}/s" },
        UpgradeDef(
            id = OVERDRIVE,
            name = "Overdrive Button",
            tagline = "Slam the cap for a burst of RPM and a short income surge.",
            category = UpgradeCategory.MANUAL,
            baseCost = 300.0, growth = 2.5, maxLevel = 8,
        ) { l -> if (l == 0) "Locked" else "+${fmt0(Stats.overdriveRpm(l))} RPM, ${fmt0(Stats.overdriveCooldown(l))}s cooldown" },
        UpgradeDef(
            id = BEARINGS,
            name = "Slick Bearings",
            tagline = "Less drag, so the ball keeps its speed longer.",
            category = UpgradeCategory.IDLE,
            baseCost = 40.0, growth = 1.7, maxLevel = 30,
        ) { l -> "Drag ${fmt0(Stats.viscousFriction(l, 0) / Stats.BASE_VISCOUS * 100)}%" },
        UpgradeDef(
            id = FLYWHEEL,
            name = "Flywheel Core",
            tagline = "A heavy core stores momentum; the ball coasts much longer.",
            category = UpgradeCategory.IDLE,
            baseCost = 150.0, growth = 1.75, maxLevel = 20,
        ) { l -> "Inertia x${fmt2(Stats.inertia(l))}" },
        UpgradeDef(
            id = MOTOR,
            name = "Micro Motor",
            tagline = "A tiny motor in the cap keeps the ball turning while you rest.",
            category = UpgradeCategory.IDLE,
            baseCost = 100.0, growth = 1.5, maxLevel = 50,
        ) { l -> "Idle floor ${fmt0(Stats.motorRpm(l))} RPM" },
        UpgradeDef(
            id = GYRO,
            name = "Gyro Memory",
            tagline = "Remembers how it was spinning while the app is closed.",
            category = UpgradeCategory.IDLE,
            baseCost = 250.0, growth = 2.0, maxLevel = 8,
        ) { l -> "Offline ${fmt0(Stats.offlineEfficiency(l) * 100)}% for up to ${Stats.offlineCapHours(l)}h" },
        UpgradeDef(
            id = COUNTER,
            name = "Precision Counter",
            tagline = "Counts every revolution more generously.",
            category = UpgradeCategory.SCORE,
            baseCost = 60.0, growth = 1.65, maxLevel = 40,
        ) { l -> "${fmt2(Stats.pointsPerRev(l))} points / rev" },
        UpgradeDef(
            id = PETALS,
            name = "Petal Shell",
            tagline = "Above ${Stats.PETAL_THRESHOLD_RPM.toInt()} RPM the shell opens and multiplies income.",
            category = UpgradeCategory.SCORE,
            baseCost = 500.0, growth = 2.2, maxLevel = 10,
        ) { l -> if (l == 0) "Locked" else "x${fmt2(Stats.petalMultiplier(l))} when open" },
        UpgradeDef(
            id = RESONANCE,
            name = "Resonance Tuner",
            tagline = "Sustained spinning builds a combo that multiplies everything.",
            category = UpgradeCategory.SCORE,
            baseCost = 1200.0, growth = 2.0, maxLevel = 10,
        ) { l -> if (l == 0) "Locked" else "Combo up to x${fmt2(1 + Stats.maxCombo(l))}" },
        UpgradeDef(
            id = COOLING,
            name = "Liquid Cooling",
            tagline = "Raises the RPM the ball can survive before it overheats.",
            category = UpgradeCategory.SPECIAL,
            baseCost = 800.0, growth = 2.0, maxLevel = 15,
        ) { l -> "Max ${fmt0(Stats.rpmCap(l))} RPM" },
    )

    val byId: Map<String, UpgradeDef> = all.associateBy { it.id }

    fun get(id: String): UpgradeDef = byId[id] ?: error("Unknown upgrade id: $id")

    private fun fmt0(v: Double) = NumberFormat.compact(v, 0)
    private fun fmt2(v: Double) = NumberFormat.compact(v, 2)
}
