package org.rsmod.tools.wiki.dumping.wiki

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Expected rarities are the rates the wiki renders for the same template calls. */
class WikiDropTableTemplatesTest {
    @Test
    fun `wilderness slayer rates match the wiki`() {
        assertRarities("{{WildernessSlayerDropTable|combat=92|hitpoints=89}}", "1/96", "1/249")
        assertRarities(
            "{{WildernessSlayerDropTable|combat=30|hitpoints=20|slayerbonus=yes|revenant=yes}}",
            "1/615",
            "1/304",
        )
        assertRarities("{{WildernessSlayerDropTable|combat=400|hitpoints=500|revenant=yes}}", "1/43", "1/80")
        assertRarities("{{WildernessSlayerDropTable|combat=200|hitpoints=300|boss=yes}}", "1/77", "1/30")
    }

    @Test
    fun `catacombs rates match the wiki`() {
        assertRarities("{{CatacombsDropTable|hitpoints=115|hitpointsmax=130}}", "1/256", "1/385")
        assertRarities("{{CatacombsDropTable|hitpoints=450}}", "1/66", "1/50")
        assertRarities("{{CatacombsDropTable|hitpoints=37}}", "1/308", "1/463")
        assertRarities("{{CatacombsDropTable|hitpoints=200|superior=yes|catacombsonly=yes}}", "1/200", "Always")
    }

    @Test
    fun `catacombs drops are area gated and roll one totem piece`() {
        val drops = WikiDropTableTemplates.expand("{{CatacombsDropTable|hitpoints=60|catacombsonly=yes}}", "Catacombs")
        assertEquals(listOf("Ancient shard", "Dark totem base"), drops.map { it.name })
        assertTrue(drops.all { it.area == WikiDropTableTemplates.CATACOMBS_AREA && it.section == WikiDropSection.Tertiary })
        assertEquals(listOf(false, true), drops.map { it.darkTotemPiece })

        val giantsDen = WikiDropTableTemplates.expand("{{CatacombsDropTable|hitpoints=130|giantsden=yes}}", "Catacombs")
        assertTrue(giantsDen.all { it.area == "area.catacombs_of_kourend,area.giantsden" })
    }

    @Test
    fun `wilderness slayer drops need a krystilia task`() {
        val drops = WikiDropTableTemplates.expand("{{WildernessSlayerDropTable|combat=86|hitpoints=111}}", "Wilderness")
        assertEquals(listOf("Larran's key", "Slayer's enchantment"), drops.map { it.name })
        assertTrue(drops.all { it.wildernessSlayerTask && it.area == null })
    }

    @Test
    fun `drops listed under a wilderness slayer heading need a krystilia task`() {
        val wikitext =
            """
            ==Drops==
            ===Tertiary===
            {{DropsTableHead}}
            {{DropsLine|name=Clue scroll (easy)|quantity=1|rarity=1/128}}
            {{DropsTableBottom}}
            ===Wilderness Slayer tertiary===
            {{DropsTableHead}}
            {{DropsLine|name=Larran's key|quantity=1|rarity=1/90}}
            {{DropsTableBottom}}
            """.trimIndent()
        val drops = WikiDropParser.parseDrops(wikitext).associate { it.name to it.wildernessSlayerTask }
        assertEquals(mapOf("Clue scroll (easy)" to false, "Larran's key" to true), drops)
    }

    private fun assertRarities(template: String, vararg expected: String) {
        assertEquals(expected.toList(), WikiDropTableTemplates.expand(template, "").map { it.rarity })
    }
}
