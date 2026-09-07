package com.aeunal.stressball.core

/** Languages the game ships. [code] is the BCP-47 language tag. */
enum class Language(val code: String) {
    EN("en"),
    TR("tr");

    companion object {
        fun fromCode(code: String?): Language? = entries.firstOrNull { it.code == code }
    }
}

/**
 * Every player-facing string produced by the engine, in one language.
 *
 * Android UI chrome (buttons, dialogs) is localised with resources in `:app`;
 * text that is tied to game content (upgrade names, effect lines, challenge
 * descriptions) lives here so `:core` stays the single source of truth for
 * what an upgrade does, and so the strings are unit-tested.
 */
interface GameText {
    val language: Language
    fun upgradeName(id: String): String
    fun upgradeTagline(id: String): String
    fun upgradeEffect(id: String, level: Int): String
    fun category(category: UpgradeCategory): String
    fun achievementTitle(id: String): String
    fun achievementDescription(id: String): String
    fun cosmeticName(id: String): String
    fun slotName(slot: CosmeticSlot): String
}

object GameTexts {
    val all: List<GameText> = listOf(EnglishText, TurkishText)
    fun of(language: Language): GameText = all.first { it.language == language }
}

private fun n0(v: Double) = NumberFormat.compact(v, 0)
private fun n1(v: Double) = NumberFormat.compact(v, 1)
private fun n2(v: Double) = NumberFormat.compact(v, 2)

object EnglishText : GameText {
    override val language = Language.EN

    override fun upgradeName(id: String): String = when (id) {
        Upgrades.GEAR -> "Gear Cap"
        Upgrades.GRIP -> "Grip Tape"
        Upgrades.OVERDRIVE -> "Turbo"
        Upgrades.BEARINGS -> "Slick Bearings"
        Upgrades.FLYWHEEL -> "Flywheel Core"
        Upgrades.MOTOR -> "Micro Motor"
        Upgrades.GYRO -> "Gyro Memory"
        Upgrades.COUNTER -> "Precision Counter"
        Upgrades.PETALS -> "Petal Shell"
        Upgrades.RESONANCE -> "Resonance Tuner"
        Upgrades.COOLING -> "Liquid Cooling"
        else -> id
    }

    override fun upgradeTagline(id: String): String = when (id) {
        Upgrades.GEAR -> "A taller gear on the cap turns one fast finger circle into more ball turns. Slow turns stay one-to-one."
        Upgrades.GRIP -> "The ball catches your finger faster and brakes harder when you hold it."
        Upgrades.OVERDRIVE -> "Hold for instant momentum. The charge drains while held and refills when released; holding it empty slows you down."
        Upgrades.BEARINGS -> "Oiled bearings. Less drag, so the ball keeps its speed much longer."
        Upgrades.FLYWHEEL -> "A heavy core stores momentum; the ball coasts far longer."
        Upgrades.MOTOR -> "A tiny motor in the cap keeps the ball turning while you rest."
        Upgrades.GYRO -> "Remembers how it was spinning while the app is closed."
        Upgrades.COUNTER -> "Counts every revolution more generously."
        Upgrades.PETALS -> "Above ${Stats.PETAL_THRESHOLD_RPM.toInt()} RPM the shell opens and multiplies income."
        Upgrades.RESONANCE -> "Sustained spinning builds a combo that multiplies everything."
        Upgrades.COOLING -> "Raises the RPM the ball can survive before it overheats."
        else -> ""
    }

    override fun upgradeEffect(id: String, level: Int): String = when (id) {
        Upgrades.GEAR -> "Fast circles x${n2(Stats.gearRatio(level))}"
        Upgrades.GRIP -> "Grip ${n0(Stats.gripAccel(level) / Stats.gripAccel(0) * 100)}%"
        Upgrades.OVERDRIVE -> "${n1(Stats.turboCapacity(level))}s boost, ${n0(Stats.turboRefill(level))}s refill, limit x${n1(Stats.turboCapMultiplier(level))}"
        Upgrades.BEARINGS -> "Drag ${n0(Stats.dragPercent(level))}%"
        Upgrades.FLYWHEEL -> "Inertia x${n2(Stats.inertia(level))}"
        Upgrades.MOTOR -> if (level == 0) "No motor" else "Idles at ${n0(Stats.motorRpm(level))} RPM"
        Upgrades.GYRO -> "Offline ${n0(Stats.offlineEfficiency(level) * 100)}% for up to ${Stats.offlineCapHours(level)}h"
        Upgrades.COUNTER -> "${n2(Stats.pointsPerRev(level))} points / rev"
        Upgrades.PETALS -> if (level == 0) "Locked" else "x${n2(Stats.petalMultiplier(level))} when open"
        Upgrades.RESONANCE -> if (level == 0) "Locked" else "Combo up to x${n2(1 + Stats.maxCombo(level))}"
        Upgrades.COOLING -> "Max ${n0(Stats.rpmCap(level))} RPM"
        else -> ""
    }

    override fun category(category: UpgradeCategory): String = when (category) {
        UpgradeCategory.MANUAL -> "Manual"
        UpgradeCategory.IDLE -> "Idle"
        UpgradeCategory.SCORE -> "Score"
        UpgradeCategory.SPECIAL -> "Special"
    }

    override fun achievementTitle(id: String): String = when (id) {
        "first_spin" -> "First Spin"
        "hummingbird" -> "Hummingbird"
        "on_fire" -> "On Fire"
        "turbine" -> "Turbine"
        "ludicrous" -> "Ludicrous Speed"
        "plasma" -> "Plasma"
        "thousand_turns" -> "A Thousand Turns"
        "million_turns" -> "Million Turns"
        "pocket_money" -> "Pocket Money"
        "millionaire" -> "Millionaire"
        "billionaire" -> "Billionaire"
        "in_bloom" -> "In Bloom"
        "hands_free" -> "Hands Free"
        "stylist" -> "Stylist"
        "zen_master" -> "Zen Master"
        "marathon" -> "Marathon"
        else -> id
    }

    override fun achievementDescription(id: String): String = when (id) {
        "first_spin" -> "Reach 60 RPM."
        "hummingbird" -> "Reach 300 RPM."
        "on_fire" -> "Reach 900 RPM."
        "turbine" -> "Reach 1,000 RPM."
        "ludicrous" -> "Reach 3,000 RPM."
        "plasma" -> "Reach 4,000 RPM."
        "thousand_turns" -> "Spin 1,000 revolutions in total."
        "million_turns" -> "Spin 1,000,000 revolutions in total."
        "pocket_money" -> "Earn 10K points."
        "millionaire" -> "Earn 1M points."
        "billionaire" -> "Earn 1B points."
        "in_bloom" -> "Own a Petal Shell."
        "hands_free" -> "Own a Micro Motor."
        "stylist" -> "Buy three looks."
        "zen_master" -> "Perform a Zen reset."
        "marathon" -> "Play for a total of one hour."
        else -> ""
    }

    override fun cosmeticName(id: String): String = when (id) {
        "body_red" -> "Classic Red"
        "body_ocean" -> "Ocean"
        "body_mint" -> "Mint"
        "body_gold" -> "Gold"
        "body_violet" -> "Violet"
        "body_carbon" -> "Carbon"
        "body_pearl" -> "Pearl"
        "body_void" -> "Void"
        "cap_green" -> "Forest Green"
        "cap_navy" -> "Navy"
        "cap_orange" -> "Orange"
        "cap_chrome" -> "Chrome"
        "cap_rose" -> "Rose"
        "cap_onyx" -> "Onyx"
        else -> id
    }

    override fun slotName(slot: CosmeticSlot): String = when (slot) {
        CosmeticSlot.BODY -> "Ball"
        CosmeticSlot.CAP -> "Cap"
    }
}

object TurkishText : GameText {
    override val language = Language.TR

    override fun upgradeName(id: String): String = when (id) {
        Upgrades.GEAR -> "Dişli Kapak"
        Upgrades.GRIP -> "Tutuş Bandı"
        Upgrades.OVERDRIVE -> "Turbo"
        Upgrades.BEARINGS -> "Kaygan Rulmanlar"
        Upgrades.FLYWHEEL -> "Volan Çekirdek"
        Upgrades.MOTOR -> "Mikro Motor"
        Upgrades.GYRO -> "Jiro Hafıza"
        Upgrades.COUNTER -> "Hassas Sayaç"
        Upgrades.PETALS -> "Yaprak Kabuk"
        Upgrades.RESONANCE -> "Rezonans Ayarı"
        Upgrades.COOLING -> "Sıvı Soğutma"
        else -> id
    }

    override fun upgradeTagline(id: String): String = when (id) {
        Upgrades.GEAR -> "Kapaktaki büyük dişli, hızlı bir parmak turunu daha çok top turuna çevirir. Yavaş turlar bire bir kalır."
        Upgrades.GRIP -> "Top parmağını daha çabuk yakalar; tutunca daha sert frenler."
        Upgrades.OVERDRIVE -> "Basılı tut: anında hız. Şarj tutarken azalır, bırakınca dolar; boşken tutmaya devam edersen yavaşlatır."
        Upgrades.BEARINGS -> "Yağlanmış rulmanlar. Daha az sürtünme; top hızını çok daha uzun korur."
        Upgrades.FLYWHEEL -> "Ağır çekirdek momentumu saklar; top çok daha uzun döner."
        Upgrades.MOTOR -> "Kapaktaki minik motor sen dinlenirken topu döndürür."
        Upgrades.GYRO -> "Uygulama kapalıyken nasıl döndüğünü hatırlar."
        Upgrades.COUNTER -> "Her turu daha cömert sayar."
        Upgrades.PETALS -> "${Stats.PETAL_THRESHOLD_RPM.toInt()} RPM üstünde kabuk açılır ve geliri çarpar."
        Upgrades.RESONANCE -> "Sürekli dönüş, her şeyi çarpan bir kombo biriktirir."
        Upgrades.COOLING -> "Topun aşırı ısınmadan dayanabildiği RPM'i yükseltir."
        else -> ""
    }

    override fun upgradeEffect(id: String, level: Int): String = when (id) {
        Upgrades.GEAR -> "Hızlı daireler x${n2(Stats.gearRatio(level))}"
        Upgrades.GRIP -> "Tutuş %${n0(Stats.gripAccel(level) / Stats.gripAccel(0) * 100)}"
        Upgrades.OVERDRIVE -> "${n1(Stats.turboCapacity(level))} sn turbo, ${n0(Stats.turboRefill(level))} sn dolum, limit x${n1(Stats.turboCapMultiplier(level))}"
        Upgrades.BEARINGS -> "Sürtünme %${n0(Stats.dragPercent(level))}"
        Upgrades.FLYWHEEL -> "Eylemsizlik x${n2(Stats.inertia(level))}"
        Upgrades.MOTOR -> if (level == 0) "Motor yok" else "Boşta ${n0(Stats.motorRpm(level))} RPM"
        Upgrades.GYRO -> "Çevrimdışı %${n0(Stats.offlineEfficiency(level) * 100)}, en fazla ${Stats.offlineCapHours(level)} sa"
        Upgrades.COUNTER -> "${n2(Stats.pointsPerRev(level))} puan / tur"
        Upgrades.PETALS -> if (level == 0) "Kilitli" else "Açıkken x${n2(Stats.petalMultiplier(level))}"
        Upgrades.RESONANCE -> if (level == 0) "Kilitli" else "Kombo en fazla x${n2(1 + Stats.maxCombo(level))}"
        Upgrades.COOLING -> "En fazla ${n0(Stats.rpmCap(level))} RPM"
        else -> ""
    }

    override fun category(category: UpgradeCategory): String = when (category) {
        UpgradeCategory.MANUAL -> "Elle"
        UpgradeCategory.IDLE -> "Boşta"
        UpgradeCategory.SCORE -> "Puan"
        UpgradeCategory.SPECIAL -> "Özel"
    }

    override fun achievementTitle(id: String): String = when (id) {
        "first_spin" -> "İlk Dönüş"
        "hummingbird" -> "Sinek Kuşu"
        "on_fire" -> "Alev Aldı"
        "turbine" -> "Türbin"
        "ludicrous" -> "Çılgın Hız"
        "plasma" -> "Plazma"
        "thousand_turns" -> "Bin Tur"
        "million_turns" -> "Milyon Tur"
        "pocket_money" -> "Harçlık"
        "millionaire" -> "Milyoner"
        "billionaire" -> "Milyarder"
        "in_bloom" -> "Çiçek Açtı"
        "hands_free" -> "Eller Serbest"
        "stylist" -> "Stilist"
        "zen_master" -> "Zen Ustası"
        "marathon" -> "Maraton"
        else -> id
    }

    override fun achievementDescription(id: String): String = when (id) {
        "first_spin" -> "60 RPM'e ulaş."
        "hummingbird" -> "300 RPM'e ulaş."
        "on_fire" -> "900 RPM'e ulaş."
        "turbine" -> "1.000 RPM'e ulaş."
        "ludicrous" -> "3.000 RPM'e ulaş."
        "plasma" -> "4.000 RPM'e ulaş."
        "thousand_turns" -> "Toplam 1.000 tur döndür."
        "million_turns" -> "Toplam 1.000.000 tur döndür."
        "pocket_money" -> "10K puan kazan."
        "millionaire" -> "1M puan kazan."
        "billionaire" -> "1B puan kazan."
        "in_bloom" -> "Bir Yaprak Kabuk al."
        "hands_free" -> "Bir Mikro Motor al."
        "stylist" -> "Üç görünüm satın al."
        "zen_master" -> "Bir Zen sıfırlaması yap."
        "marathon" -> "Toplam bir saat oyna."
        else -> ""
    }

    override fun cosmeticName(id: String): String = when (id) {
        "body_red" -> "Klasik Kırmızı"
        "body_ocean" -> "Okyanus"
        "body_mint" -> "Nane"
        "body_gold" -> "Altın"
        "body_violet" -> "Mor"
        "body_carbon" -> "Karbon"
        "body_pearl" -> "İnci"
        "body_void" -> "Boşluk"
        "cap_green" -> "Orman Yeşili"
        "cap_navy" -> "Lacivert"
        "cap_orange" -> "Turuncu"
        "cap_chrome" -> "Krom"
        "cap_rose" -> "Gül"
        "cap_onyx" -> "Oniks"
        else -> id
    }

    override fun slotName(slot: CosmeticSlot): String = when (slot) {
        CosmeticSlot.BODY -> "Top"
        CosmeticSlot.CAP -> "Kapak"
    }
}
