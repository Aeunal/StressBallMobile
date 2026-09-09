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
 * descriptions, ball and skin names) lives here so `:core` stays the single
 * source of truth for what things do, and so the strings are unit-tested.
 */
interface GameText {
    val language: Language
    fun upgradeName(id: String): String
    fun upgradeTagline(id: String): String
    fun upgradeEffect(id: String, level: Int): String
    fun category(category: UpgradeCategory): String
    fun achievementTitle(id: String): String
    fun achievementDescription(id: String): String
    fun ballTypeName(id: String): String
    fun rarityName(rarity: Rarity): String
    fun traitsDescription(traits: BallTraits): String
    fun skinName(id: String): String
    fun skinDescription(id: String): String
    fun slotName(slot: SkinSlot): String
    fun buffName(kind: BuffKind): String
    fun buffDescription(kind: BuffKind): String
}

object GameTexts {
    val all: List<GameText> = listOf(EnglishText, TurkishText)
    fun of(language: Language): GameText = all.first { it.language == language }
}

private fun n0(v: Double) = NumberFormat.compact(v, 0)
private fun n1(v: Double) = NumberFormat.compact(v, 1)
private fun n2(v: Double) = NumberFormat.compact(v, 2)
private fun pct(v: Double) = n0((v - 1.0) * 100.0)
private fun signedPct(v: Double) = (if (v >= 1.0) "+" else "") + n0((v - 1.0) * 100.0) + "%"
private fun legendaryOdds(luck: Int) =
    BallTypes.odds(luck).filterKeys { it >= Rarity.LEGENDARY }.values.sum() * 100.0

/** Buff and trait lines are built from the numbers so they never drift from the catalogue. */
private fun describeBuff(b: SkinBuff, income: String, grip: String, cap: String, fuel: String, combo: String, motor: String, sep: String): String {
    val parts = ArrayList<String>()
    if (b.income != 1.0) parts += "${signedPct(b.income)} $income"
    if (b.grip != 1.0) parts += "${signedPct(b.grip)} $grip"
    if (b.cap != 1.0) parts += "${signedPct(b.cap)} $cap"
    if (b.turboFuel != 1.0) parts += "${signedPct(b.turboFuel)} $fuel"
    if (b.comboMax != 1.0) parts += "${signedPct(b.comboMax)} $combo"
    if (b.motor != 1.0) parts += "${signedPct(b.motor)} $motor"
    return parts.joinToString(sep)
}

private fun describeTraits(t: BallTraits, income: String, drag: String, grip: String, turbo: String, cap: String, none: String, sep: String): String {
    val parts = ArrayList<String>()
    if (t.income != 1.0) parts += "${signedPct(t.income)} $income"
    if (t.drag != 1.0) parts += "${signedPct(t.drag)} $drag"
    if (t.grip != 1.0) parts += "${signedPct(t.grip)} $grip"
    if (t.turbo != 1.0) parts += "${signedPct(t.turbo)} $turbo"
    if (t.cap != 1.0) parts += "${signedPct(t.cap)} $cap"
    return if (parts.isEmpty()) none else parts.joinToString(sep)
}

object EnglishText : GameText {
    override val language = Language.EN

    override fun upgradeName(id: String): String = when (id) {
        Upgrades.GEAR -> "Gear Cap"
        Upgrades.GRIP -> "Grip Tape"
        Upgrades.TRACTION -> "Rubber Tread"
        Upgrades.GIMBAL -> "Gimbal Mount"
        Upgrades.OVERDRIVE -> "Turbo"
        Upgrades.NITRO -> "Nitro Kick"
        Upgrades.KERS -> "Kinetic Harvester"
        Upgrades.BEARINGS -> "Slick Bearings"
        Upgrades.FLYWHEEL -> "Flywheel Core"
        Upgrades.AERO -> "Aero Shell"
        Upgrades.MOTOR -> "Micro Motor"
        Upgrades.GYRO -> "Gyro Memory"
        Upgrades.COUNTER -> "Precision Counter"
        Upgrades.PETALS -> "Petal Shell"
        Upgrades.RESONANCE -> "Resonance Tuner"
        Upgrades.COMBO_LOCK -> "Combo Lock"
        Upgrades.COOLING -> "Liquid Cooling"
        Upgrades.CRYO -> "Cryo Core"
        Upgrades.HEATSINK -> "Heat Sink"
        Upgrades.RACK -> "Bearing Rack"
        Upgrades.INTEREST -> "Investor"
        Upgrades.LUCKY -> "Lucky Charm"
        Upgrades.CHEST_LUCK -> "Lucky Chest"
        else -> id
    }

    override fun upgradeTagline(id: String): String = when (id) {
        Upgrades.GEAR -> "A taller gear on the cap turns one fast finger circle into more ball turns. Slow turns stay one-to-one. Works from the top view."
        Upgrades.GRIP -> "The ball catches your finger faster and brakes harder when you hold it."
        Upgrades.TRACTION -> "A grippier face. Swiping across the ball pushes it harder, so even a swipe slower than the ball still helps instead of braking."
        Upgrades.GIMBAL -> "Flips the ball to show you its cap. From above you circle it instead of swiping, which is far easier to keep going."
        Upgrades.OVERDRIVE -> "Pinch the ball from top and bottom. Squeeze harder for more thrust, pinch fast for a kick. Fuel burns while squeezed and only refills after a cooldown."
        Upgrades.NITRO -> "A faster pinch kicks harder."
        Upgrades.KERS -> "A regenerative brake. Spin you shed by braking with your finger comes back as points."
        Upgrades.BEARINGS -> "Oiled bearings. Less drag, so the ball keeps its speed much longer."
        Upgrades.FLYWHEEL -> "A heavy core stores momentum; the ball coasts far longer."
        Upgrades.AERO -> "Smooths the shell. Air drag grows with the square of speed; it is what stands between you and ten thousand RPM."
        Upgrades.MOTOR -> "A tiny motor in the cap keeps the ball turning while you rest."
        Upgrades.GYRO -> "Remembers how it was spinning while the app is closed."
        Upgrades.COUNTER -> "Counts every revolution more generously."
        Upgrades.PETALS -> "Above ${Stats.PETAL_THRESHOLD_RPM.toInt()} RPM the shell opens and multiplies income."
        Upgrades.RESONANCE -> "Sustained spinning builds a combo that multiplies everything."
        Upgrades.COMBO_LOCK -> "The resonance combo fades slower when you slow down."
        Upgrades.COOLING -> "Raises the RPM the ball can survive before it overheats."
        Upgrades.CRYO -> "Multiplies the whole RPM cap. Expensive, and it shows."
        Upgrades.HEATSINK -> "Squeezing an empty turbo brakes less."
        Upgrades.RACK -> "Balls in the garage keep spinning on the rack and earn a share of their motor income."
        Upgrades.INTEREST -> "Your point balance earns interest. Spend late, earn more."
        Upgrades.LUCKY -> "Golden sparks appear more often and Frenzy lasts longer."
        Upgrades.CHEST_LUCK -> "Better odds of rare balls from every chest."
        else -> ""
    }

    override fun upgradeEffect(id: String, level: Int): String = when (id) {
        Upgrades.GEAR -> "Fast circles x${n2(Stats.gearRatio(level))}"
        Upgrades.GRIP -> "Grip ${n0(Stats.gripAccel(level) / Stats.gripAccel(0) * 100)}%"
        Upgrades.TRACTION -> "Swipe x${n2(Stats.tractionRatio(level))}"
        Upgrades.GIMBAL -> if (level == 0) "Side view only" else "Top view unlocked"
        Upgrades.OVERDRIVE -> "${n1(Stats.turboCapacity(level))}s fuel, ${n1(Stats.turboCooldown(level))}s cooldown, ${n1(Stats.turboRefill(level))}s refill, limit x${n1(Stats.turboCapMultiplier(level))}"
        Upgrades.NITRO -> "Kick x${n2(Stats.nitroFactor(level))}"
        Upgrades.KERS -> "Recovers ${n0(Stats.kersFraction(level) * 100)}% of braking"
        Upgrades.BEARINGS -> "Drag ${n0(Stats.dragPercent(level))}%"
        Upgrades.FLYWHEEL -> "Inertia x${n2(Stats.inertia(level))}"
        Upgrades.AERO -> "Air drag ${n0(Stats.aeroPercent(level))}%"
        Upgrades.MOTOR -> if (level == 0) "No motor" else "Idles at ${n0(Stats.motorRpm(level))} RPM"
        Upgrades.GYRO -> "Offline ${n0(Stats.offlineEfficiency(level) * 100)}% for up to ${Stats.offlineCapHours(level)}h"
        Upgrades.COUNTER -> "${n2(Stats.pointsPerRev(level))} points / rev"
        Upgrades.PETALS -> if (level == 0) "Locked" else "x${n2(Stats.petalMultiplier(level))} when open"
        Upgrades.RESONANCE -> if (level == 0) "Locked" else "Combo up to x${n2(1 + Stats.maxCombo(level))}"
        Upgrades.COMBO_LOCK -> "Combo fades ${n0(Stats.comboDecayFactor(level) * 100)}% as fast"
        Upgrades.COOLING -> "Max ${n0(Stats.rpmCap(level))} RPM"
        Upgrades.CRYO -> "Cap x${n2(Stats.cryoFactor(level))}"
        Upgrades.HEATSINK -> "Overheat brake ${n0(Stats.heatSinkFactor(level) * 100)}%"
        Upgrades.RACK -> "Garage balls earn ${n0(Stats.rackEfficiency(level) * 100)}%"
        Upgrades.INTEREST -> if (level == 0) "No interest" else "${n2(Stats.interestPerSecond(level) * 60 * 100)}% per minute"
        Upgrades.LUCKY -> "Spark every ${n0(Stats.sparkInterval(level))}s, Frenzy ${n0(Stats.frenzyDuration(level))}s"
        Upgrades.CHEST_LUCK -> "Legendary or better ${n1(legendaryOdds(level))}%"
        else -> ""
    }

    override fun category(category: UpgradeCategory): String = when (category) {
        UpgradeCategory.MANUAL -> "Manual"
        UpgradeCategory.IDLE -> "Idle"
        UpgradeCategory.SCORE -> "Score"
        UpgradeCategory.SPECIAL -> "Special"
        UpgradeCategory.GARAGE -> "Garage"
    }

    override fun achievementTitle(id: String): String = when (id) {
        "first_spin" -> "First Spin"
        "hummingbird" -> "Hummingbird"
        "on_fire" -> "On Fire"
        "turbine" -> "Turbine"
        "ludicrous" -> "Ludicrous Speed"
        "plasma" -> "Plasma"
        "singularity" -> "Singularity"
        "supernova" -> "Supernova"
        "quantum" -> "Quantum"
        "thousand_turns" -> "A Thousand Turns"
        "million_turns" -> "Million Turns"
        "pocket_money" -> "Pocket Money"
        "millionaire" -> "Millionaire"
        "billionaire" -> "Billionaire"
        "in_bloom" -> "In Bloom"
        "hands_free" -> "Hands Free"
        "top_down" -> "Top Down"
        "maxed" -> "Maxed Out"
        "stylist" -> "Stylist"
        "collector" -> "Collector"
        "garage_full" -> "Full Garage"
        "legendary" -> "Legendary"
        "exotic" -> "Exotic"
        "trader" -> "Trader"
        "lucky" -> "Lucky"
        "gem_hoarder" -> "Gem Hoarder"
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
        "singularity" -> "Reach 8,000 RPM."
        "supernova" -> "Reach 16,000 RPM."
        "quantum" -> "Reach 32,000 RPM."
        "thousand_turns" -> "Spin 1,000 revolutions in total."
        "million_turns" -> "Spin 1,000,000 revolutions in total."
        "pocket_money" -> "Earn 10K points."
        "millionaire" -> "Earn 1M points."
        "billionaire" -> "Earn 1B points."
        "in_bloom" -> "Own a Petal Shell."
        "hands_free" -> "Own a Micro Motor."
        "top_down" -> "Fit a Gimbal Mount."
        "maxed" -> "Max out any upgrade."
        "stylist" -> "Buy three skins."
        "collector" -> "Own three balls."
        "garage_full" -> "Fill the garage."
        "legendary" -> "Pull a Legendary or better ball."
        "exotic" -> "Pull an Exotic ball."
        "trader" -> "Sell a ball."
        "lucky" -> "Catch a golden spark."
        "gem_hoarder" -> "Hold 50 gems."
        "zen_master" -> "Perform a Zen reset."
        "marathon" -> "Play for a total of one hour."
        else -> ""
    }

    override fun ballTypeName(id: String): String = when (id) {
        "rubber_red" -> "Rubber Red"
        "ocean" -> "Ocean"
        "mint" -> "Mint"
        "amber" -> "Amber"
        "carbon" -> "Carbon"
        "chrome" -> "Chrome"
        "neon" -> "Neon"
        "marble" -> "Marble"
        "obsidian" -> "Obsidian"
        "aurora" -> "Aurora"
        "jade" -> "Jade"
        "solar" -> "Solar"
        "glacier" -> "Glacier"
        "dragon" -> "Dragon"
        "nebula" -> "Nebula"
        "phoenix" -> "Phoenix"
        "singularity" -> "Singularity"
        "chronos" -> "Chronos"
        else -> id
    }

    override fun rarityName(rarity: Rarity): String = when (rarity) {
        Rarity.COMMON -> "Common"
        Rarity.RARE -> "Rare"
        Rarity.VERY_RARE -> "Very Rare"
        Rarity.LEGENDARY -> "Legendary"
        Rarity.MYTHIC -> "Mythic"
        Rarity.EXOTIC -> "Exotic"
    }

    override fun traitsDescription(traits: BallTraits): String =
        describeTraits(traits, "income", "drag", "grip", "turbo fuel", "RPM cap", "No special traits", ", ")

    override fun skinName(id: String): String = when (id) {
        "stripes" -> "Racing Stripes"
        "spots" -> "Ladybird"
        "hex" -> "Hex Armor"
        "rings" -> "Saturn Rings"
        "stars" -> "Starfield"
        "cracks" -> "Magma Cracks"
        "ember" -> "Ember Core"
        "crystal" -> "Crystal Core"
        "void" -> "Void Core"
        "storm" -> "Storm Core"
        "prism" -> "Prism Core"
        "clockwork" -> "Clockwork Core"
        else -> id
    }

    override fun skinDescription(id: String): String {
        val def = Skins.byId[id] ?: return ""
        return describeBuff(def.buff, "income", "grip", "RPM cap", "turbo fuel", "max combo", "motor RPM", ", ")
    }

    override fun slotName(slot: SkinSlot): String = when (slot) {
        SkinSlot.OUTER -> "Outer"
        SkinSlot.INTERIOR -> "Interior"
    }

    override fun buffName(kind: BuffKind): String = when (kind) {
        BuffKind.FRENZY -> "Frenzy"
        BuffKind.JACKPOT -> "Jackpot"
        BuffKind.RECHARGE -> "Recharge"
        BuffKind.WILD_GRIP -> "Wild Grip"
    }

    override fun buffDescription(kind: BuffKind): String = when (kind) {
        BuffKind.FRENZY -> "x${n0(Stats.FRENZY_MULT)} income"
        BuffKind.JACKPOT -> "${n0(Stats.JACKPOT_MINUTES)} minutes of income at once"
        BuffKind.RECHARGE -> "Turbo refilled, free fuel for ${n0(Stats.RECHARGE_SECONDS)}s"
        BuffKind.WILD_GRIP -> "x${n0(Stats.WILD_GRIP_MULT)} grip for ${n0(Stats.WILD_GRIP_SECONDS)}s"
    }
}

object TurkishText : GameText {
    override val language = Language.TR

    override fun upgradeName(id: String): String = when (id) {
        Upgrades.GEAR -> "Dişli Kapak"
        Upgrades.GRIP -> "Tutuş Bandı"
        Upgrades.TRACTION -> "Kauçuk Diş"
        Upgrades.GIMBAL -> "Gimbal Yuvası"
        Upgrades.OVERDRIVE -> "Turbo"
        Upgrades.NITRO -> "Nitro Vuruş"
        Upgrades.KERS -> "Kinetik Hasat"
        Upgrades.BEARINGS -> "Kaygan Rulmanlar"
        Upgrades.FLYWHEEL -> "Volan Çekirdek"
        Upgrades.AERO -> "Aero Kabuk"
        Upgrades.MOTOR -> "Mikro Motor"
        Upgrades.GYRO -> "Jiro Hafıza"
        Upgrades.COUNTER -> "Hassas Sayaç"
        Upgrades.PETALS -> "Yaprak Kabuk"
        Upgrades.RESONANCE -> "Rezonans Ayarı"
        Upgrades.COMBO_LOCK -> "Kombo Kilidi"
        Upgrades.COOLING -> "Sıvı Soğutma"
        Upgrades.CRYO -> "Kriyo Çekirdek"
        Upgrades.HEATSINK -> "Isı Emici"
        Upgrades.RACK -> "Rulman Rafı"
        Upgrades.INTEREST -> "Yatırımcı"
        Upgrades.LUCKY -> "Uğur Tılsımı"
        Upgrades.CHEST_LUCK -> "Şanslı Sandık"
        else -> id
    }

    override fun upgradeTagline(id: String): String = when (id) {
        Upgrades.GEAR -> "Kapaktaki büyük dişli, hızlı bir parmak turunu daha çok top turuna çevirir. Yavaş turlar bire bir kalır. Üstten görünümde çalışır."
        Upgrades.GRIP -> "Top parmağını daha çabuk yakalar; tutunca daha sert frenler."
        Upgrades.TRACTION -> "Daha tutucu yüzey. Topun üstünden kaydırmak topu daha çok iter; toptan yavaş bir kaydırma bile frenlemek yerine yardım eder."
        Upgrades.GIMBAL -> "Topu kapağı sana bakacak şekilde çevirir. Üstten bakarken kaydırmak yerine daire çizersin; sürdürmesi çok daha kolaydır."
        Upgrades.OVERDRIVE -> "Topu üstten ve alttan sık. Daha çok sıkarsan daha çok itiş; hızlı sıkış ekstra vuruş katar. Yakıt sıkarken biter ve ancak bekleme süresinden sonra dolar."
        Upgrades.NITRO -> "Daha hızlı sıkış daha sert vurur."
        Upgrades.KERS -> "Geri kazanımlı fren. Parmağınla frenlerken kaybolan dönüş puan olarak geri gelir."
        Upgrades.BEARINGS -> "Yağlanmış rulmanlar. Daha az sürtünme; top hızını çok daha uzun korur."
        Upgrades.FLYWHEEL -> "Ağır çekirdek momentumu saklar; top çok daha uzun döner."
        Upgrades.AERO -> "Kabuğu düzleştirir. Hava direnci hızın karesiyle büyür; seninle on bin RPM arasındaki duvar odur."
        Upgrades.MOTOR -> "Kapaktaki minik motor sen dinlenirken topu döndürür."
        Upgrades.GYRO -> "Uygulama kapalıyken nasıl döndüğünü hatırlar."
        Upgrades.COUNTER -> "Her turu daha cömert sayar."
        Upgrades.PETALS -> "${Stats.PETAL_THRESHOLD_RPM.toInt()} RPM üstünde kabuk açılır ve geliri çarpar."
        Upgrades.RESONANCE -> "Sürekli dönüş, her şeyi çarpan bir kombo biriktirir."
        Upgrades.COMBO_LOCK -> "Yavaşlayınca rezonans kombosu daha yavaş erir."
        Upgrades.COOLING -> "Topun aşırı ısınmadan dayanabildiği RPM limitini yükseltir."
        Upgrades.CRYO -> "RPM limitinin tamamını çarpar. Pahalıdır ve belli olur."
        Upgrades.HEATSINK -> "Boş turboyu sıkmak daha az frenler."
        Upgrades.RACK -> "Garajdaki toplar rafta dönmeye devam eder ve motor gelirlerinin bir kısmını kazandırır."
        Upgrades.INTEREST -> "Puan bakiyen faiz kazanır. Geç harca, çok kazan."
        Upgrades.LUCKY -> "Altın kıvılcımlar daha sık çıkar, Çılgınlık daha uzun sürer."
        Upgrades.CHEST_LUCK -> "Her sandıktan nadir top çıkma ihtimali artar."
        else -> ""
    }

    override fun upgradeEffect(id: String, level: Int): String = when (id) {
        Upgrades.GEAR -> "Hızlı daireler x${n2(Stats.gearRatio(level))}"
        Upgrades.GRIP -> "Tutuş %${n0(Stats.gripAccel(level) / Stats.gripAccel(0) * 100)}"
        Upgrades.TRACTION -> "Kaydırma x${n2(Stats.tractionRatio(level))}"
        Upgrades.GIMBAL -> if (level == 0) "Sadece yandan" else "Üstten görünüm açık"
        Upgrades.OVERDRIVE -> "${n1(Stats.turboCapacity(level))} sn yakıt, ${n1(Stats.turboCooldown(level))} sn bekleme, ${n1(Stats.turboRefill(level))} sn dolum, limit x${n1(Stats.turboCapMultiplier(level))}"
        Upgrades.NITRO -> "Vuruş x${n2(Stats.nitroFactor(level))}"
        Upgrades.KERS -> "Frenlemenin %${n0(Stats.kersFraction(level) * 100)} kadarı geri kazanılır"
        Upgrades.BEARINGS -> "Sürtünme %${n0(Stats.dragPercent(level))}"
        Upgrades.FLYWHEEL -> "Eylemsizlik x${n2(Stats.inertia(level))}"
        Upgrades.AERO -> "Hava direnci %${n0(Stats.aeroPercent(level))}"
        Upgrades.MOTOR -> if (level == 0) "Motor yok" else "Boşta ${n0(Stats.motorRpm(level))} RPM"
        Upgrades.GYRO -> "Çevrimdışı %${n0(Stats.offlineEfficiency(level) * 100)}, en fazla ${Stats.offlineCapHours(level)} sa"
        Upgrades.COUNTER -> "${n2(Stats.pointsPerRev(level))} puan / tur"
        Upgrades.PETALS -> if (level == 0) "Kilitli" else "Açıkken x${n2(Stats.petalMultiplier(level))}"
        Upgrades.RESONANCE -> if (level == 0) "Kilitli" else "Kombo en fazla x${n2(1 + Stats.maxCombo(level))}"
        Upgrades.COMBO_LOCK -> "Kombo %${n0(Stats.comboDecayFactor(level) * 100)} hızda erir"
        Upgrades.COOLING -> "En fazla ${n0(Stats.rpmCap(level))} RPM"
        Upgrades.CRYO -> "Limit x${n2(Stats.cryoFactor(level))}"
        Upgrades.HEATSINK -> "Aşırı ısınma freni %${n0(Stats.heatSinkFactor(level) * 100)}"
        Upgrades.RACK -> "Garaj topları %${n0(Stats.rackEfficiency(level) * 100)} kazandırır"
        Upgrades.INTEREST -> if (level == 0) "Faiz yok" else "Dakikada %${n2(Stats.interestPerSecond(level) * 60 * 100)}"
        Upgrades.LUCKY -> "Her ${n0(Stats.sparkInterval(level))} sn'de kıvılcım, Çılgınlık ${n0(Stats.frenzyDuration(level))} sn"
        Upgrades.CHEST_LUCK -> "Efsanevi ve üstü %${n1(legendaryOdds(level))}"
        else -> ""
    }

    override fun category(category: UpgradeCategory): String = when (category) {
        UpgradeCategory.MANUAL -> "Elle"
        UpgradeCategory.IDLE -> "Boşta"
        UpgradeCategory.SCORE -> "Puan"
        UpgradeCategory.SPECIAL -> "Özel"
        UpgradeCategory.GARAGE -> "Garaj"
    }

    override fun achievementTitle(id: String): String = when (id) {
        "first_spin" -> "İlk Dönüş"
        "hummingbird" -> "Sinek Kuşu"
        "on_fire" -> "Alev Aldı"
        "turbine" -> "Türbin"
        "ludicrous" -> "Çılgın Hız"
        "plasma" -> "Plazma"
        "singularity" -> "Tekillik"
        "supernova" -> "Süpernova"
        "quantum" -> "Kuantum"
        "thousand_turns" -> "Bin Tur"
        "million_turns" -> "Milyon Tur"
        "pocket_money" -> "Harçlık"
        "millionaire" -> "Milyoner"
        "billionaire" -> "Milyarder"
        "in_bloom" -> "Çiçek Açtı"
        "hands_free" -> "Eller Serbest"
        "top_down" -> "Kuş Bakışı"
        "maxed" -> "Tavan Yaptı"
        "stylist" -> "Stilist"
        "collector" -> "Koleksiyoncu"
        "garage_full" -> "Garaj Dolu"
        "legendary" -> "Efsanevi"
        "exotic" -> "Egzotik"
        "trader" -> "Tüccar"
        "lucky" -> "Şanslı"
        "gem_hoarder" -> "Mücevher Avcısı"
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
        "singularity" -> "8.000 RPM'e ulaş."
        "supernova" -> "16.000 RPM'e ulaş."
        "quantum" -> "32.000 RPM'e ulaş."
        "thousand_turns" -> "Toplam 1.000 tur döndür."
        "million_turns" -> "Toplam 1.000.000 tur döndür."
        "pocket_money" -> "10K puan kazan."
        "millionaire" -> "1M puan kazan."
        "billionaire" -> "1B puan kazan."
        "in_bloom" -> "Bir Yaprak Kabuk al."
        "hands_free" -> "Bir Mikro Motor al."
        "top_down" -> "Bir Gimbal Yuvası tak."
        "maxed" -> "Herhangi bir yükseltmeyi sonuna kadar al."
        "stylist" -> "Üç kaplama satın al."
        "collector" -> "Üç topa sahip ol."
        "garage_full" -> "Garajı doldur."
        "legendary" -> "Efsanevi veya daha iyi bir top çek."
        "exotic" -> "Egzotik bir top çek."
        "trader" -> "Bir top sat."
        "lucky" -> "Bir altın kıvılcım yakala."
        "gem_hoarder" -> "50 mücevher biriktir."
        "zen_master" -> "Bir Zen sıfırlaması yap."
        "marathon" -> "Toplam bir saat oyna."
        else -> ""
    }

    override fun ballTypeName(id: String): String = when (id) {
        "rubber_red" -> "Kauçuk Kırmızı"
        "ocean" -> "Okyanus"
        "mint" -> "Nane"
        "amber" -> "Kehribar"
        "carbon" -> "Karbon"
        "chrome" -> "Krom"
        "neon" -> "Neon"
        "marble" -> "Mermer"
        "obsidian" -> "Obsidyen"
        "aurora" -> "Aurora"
        "jade" -> "Yeşim"
        "solar" -> "Güneş"
        "glacier" -> "Buzul"
        "dragon" -> "Ejderha"
        "nebula" -> "Nebula"
        "phoenix" -> "Anka"
        "singularity" -> "Tekillik"
        "chronos" -> "Kronos"
        else -> id
    }

    override fun rarityName(rarity: Rarity): String = when (rarity) {
        Rarity.COMMON -> "Sıradan"
        Rarity.RARE -> "Nadir"
        Rarity.VERY_RARE -> "Çok Nadir"
        Rarity.LEGENDARY -> "Efsanevi"
        Rarity.MYTHIC -> "Mitik"
        Rarity.EXOTIC -> "Egzotik"
    }

    override fun traitsDescription(traits: BallTraits): String =
        describeTraits(traits, "gelir", "sürtünme", "tutuş", "turbo yakıtı", "RPM limiti", "Özel bir niteliği yok", ", ")

    override fun skinName(id: String): String = when (id) {
        "stripes" -> "Yarış Şeritleri"
        "spots" -> "Uğur Böceği"
        "hex" -> "Petek Zırh"
        "rings" -> "Satürn Halkaları"
        "stars" -> "Yıldız Tarlası"
        "cracks" -> "Magma Çatlakları"
        "ember" -> "Kor Çekirdek"
        "crystal" -> "Kristal Çekirdek"
        "void" -> "Boşluk Çekirdeği"
        "storm" -> "Fırtına Çekirdeği"
        "prism" -> "Prizma Çekirdek"
        "clockwork" -> "Saat Mekanizması"
        else -> id
    }

    override fun skinDescription(id: String): String {
        val def = Skins.byId[id] ?: return ""
        return describeBuff(def.buff, "gelir", "tutuş", "RPM limiti", "turbo yakıtı", "en yüksek kombo", "motor RPM", ", ")
    }

    override fun slotName(slot: SkinSlot): String = when (slot) {
        SkinSlot.OUTER -> "Dış"
        SkinSlot.INTERIOR -> "İç"
    }

    override fun buffName(kind: BuffKind): String = when (kind) {
        BuffKind.FRENZY -> "Çılgınlık"
        BuffKind.JACKPOT -> "Büyük İkramiye"
        BuffKind.RECHARGE -> "Yeniden Dolum"
        BuffKind.WILD_GRIP -> "Vahşi Tutuş"
    }

    override fun buffDescription(kind: BuffKind): String = when (kind) {
        BuffKind.FRENZY -> "x${n0(Stats.FRENZY_MULT)} gelir"
        BuffKind.JACKPOT -> "${n0(Stats.JACKPOT_MINUTES)} dakikalık gelir tek seferde"
        BuffKind.RECHARGE -> "Turbo doldu, ${n0(Stats.RECHARGE_SECONDS)} sn bedava yakıt"
        BuffKind.WILD_GRIP -> "${n0(Stats.WILD_GRIP_SECONDS)} sn boyunca x${n0(Stats.WILD_GRIP_MULT)} tutuş"
    }
}
