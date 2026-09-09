package com.aeunal.stressball.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class GameTextTest {
    @Test
    fun `every language covers every upgrade, achievement, ball, skin, buff, rarity and category`() {
        assertEquals(Language.entries.size, GameTexts.all.size)
        for (text in GameTexts.all) {
            val lang = text.language
            for (def in Upgrades.all) {
                assertNotEquals(def.id, text.upgradeName(def.id), "$lang name missing for ${def.id}")
                assertTrue(text.upgradeTagline(def.id).isNotBlank(), "$lang tagline missing for ${def.id}")
                for (level in 0..def.maxLevel) {
                    assertTrue(text.upgradeEffect(def.id, level).isNotBlank(), "$lang effect missing for ${def.id}@$level")
                }
            }
            for (a in Achievements.all) {
                assertNotEquals(a.id, text.achievementTitle(a.id), "$lang title missing for ${a.id}")
                assertTrue(text.achievementDescription(a.id).isNotBlank(), "$lang description missing for ${a.id}")
            }
            for (b in BallTypes.all) {
                assertNotEquals(b.id, text.ballTypeName(b.id), "$lang name missing for ${b.id}")
                assertTrue(text.traitsDescription(b.traits).isNotBlank())
            }
            for (s in Skins.all) {
                assertNotEquals(s.id, text.skinName(s.id), "$lang name missing for ${s.id}")
                assertTrue(text.skinDescription(s.id).isNotBlank(), "$lang description missing for ${s.id}")
            }
            for (k in BuffKind.entries) {
                assertTrue(text.buffName(k).isNotBlank())
                assertTrue(text.buffDescription(k).isNotBlank())
            }
            for (r in Rarity.entries) assertTrue(text.rarityName(r).isNotBlank())
            for (cat in UpgradeCategory.entries) assertTrue(text.category(cat).isNotBlank())
            for (slot in SkinSlot.entries) assertTrue(text.slotName(slot).isNotBlank())
        }
    }

    @Test
    fun `effects reflect the level`() {
        for (text in GameTexts.all) {
            assertNotEquals(text.upgradeEffect(Upgrades.GEAR, 0), text.upgradeEffect(Upgrades.GEAR, 1))
            assertNotEquals(text.upgradeEffect(Upgrades.COOLING, 0), text.upgradeEffect(Upgrades.COOLING, 3))
            assertNotEquals(text.upgradeEffect(Upgrades.CHEST_LUCK, 0), text.upgradeEffect(Upgrades.CHEST_LUCK, 3))
        }
        assertTrue(EnglishText.upgradeEffect(Upgrades.COOLING, 0).contains("500"))
        assertTrue(TurkishText.upgradeEffect(Upgrades.COOLING, 0).contains("500"))
        assertTrue(EnglishText.skinDescription("stripes").contains("+10%"))
        assertTrue(EnglishText.traitsDescription(BallTypes.get("glacier").traits).contains("-45%"))
        assertEquals("No special traits", EnglishText.traitsDescription(BallTraits()))
    }

    @Test
    fun `language codes round trip`() {
        assertEquals(Language.TR, Language.fromCode("tr"))
        assertEquals(Language.EN, Language.fromCode("en"))
        assertEquals(null, Language.fromCode("xx"))
        assertEquals(null, Language.fromCode(null))
        assertEquals(TurkishText, GameTexts.of(Language.TR))
    }
}
