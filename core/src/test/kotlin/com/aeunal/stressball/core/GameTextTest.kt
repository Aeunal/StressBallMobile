package com.aeunal.stressball.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class GameTextTest {
    @Test
    fun `every language covers every upgrade, achievement, cosmetic and category`() {
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
            for (c in Cosmetics.all) {
                assertNotEquals(c.id, text.cosmeticName(c.id), "$lang name missing for ${c.id}")
            }
            for (cat in UpgradeCategory.entries) assertTrue(text.category(cat).isNotBlank())
            for (slot in CosmeticSlot.entries) assertTrue(text.slotName(slot).isNotBlank())
        }
    }

    @Test
    fun `effects reflect the level`() {
        for (text in GameTexts.all) {
            assertNotEquals(text.upgradeEffect(Upgrades.GEAR, 0), text.upgradeEffect(Upgrades.GEAR, 1))
            assertNotEquals(text.upgradeEffect(Upgrades.COOLING, 0), text.upgradeEffect(Upgrades.COOLING, 3))
        }
        assertTrue(EnglishText.upgradeEffect(Upgrades.COOLING, 0).contains("500"))
        assertTrue(TurkishText.upgradeEffect(Upgrades.COOLING, 0).contains("500"))
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
