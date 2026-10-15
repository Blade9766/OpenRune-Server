package org.rsmod.content.drops

import dtx.core.Single
import dtx.rs.RSWeightedTable
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.droptable.DropRollItem
import org.rsmod.content.drops.tables.monsters.eliteMimicDropTable
import org.rsmod.content.drops.tables.monsters.masterMimicDropTable
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.Inventory
import org.rsmod.game.inv.InventoryMap

class DropConditionsTest {
    private fun player(inv: Map<String, Int> = emptyMap(), bank: Map<String, Int> = emptyMap()): Player {
        fun inventory(objs: Map<String, Int>): Inventory =
            mockk {
                every { contains(any<String>()) } answers { (objs[firstArg()] ?: 0) > 0 }
                every { count(any<String>()) } answers { objs[firstArg()] ?: 0 }
            }
        val invs = mockk<InventoryMap>()
        every { invs["inv.inv"] } returns inventory(inv)
        every { invs["inv.bank"] } returns inventory(bank)
        return mockk { every { invMap } returns invs }
    }

    @Test
    fun `essence pouches drop strictly in size order and stop once owned`() {
        val small = "obj.rcu_pouch_small"
        val medium = "obj.rcu_pouch_medium"
        val large = "obj.rcu_pouch_large"

        val fresh = player()
        assertTrue(fresh.needsEssencePouch(small, emptyList()))
        assertFalse(fresh.needsEssencePouch(medium, listOf(small)))

        val hasSmallInBank = player(bank = mapOf(small to 1))
        assertFalse(hasSmallInBank.needsEssencePouch(small, emptyList()))
        assertTrue(hasSmallInBank.needsEssencePouch(medium, listOf(small)))
        assertFalse(hasSmallInBank.needsEssencePouch(large, listOf(small, medium)))
    }

    @Test
    fun `hydra pieces come eye then fang then heart`() {
        assertEquals("obj.hydra_eye", player().nextHydraPiece())
        assertEquals("obj.hydra_fang", player(inv = mapOf("obj.hydra_eye" to 1)).nextHydraPiece())
        assertEquals(
            "obj.hydra_heart",
            player(inv = mapOf("obj.hydra_eye" to 1), bank = mapOf("obj.hydra_fang" to 1)).nextHydraPiece(),
        )
        val all = mapOf("obj.hydra_eye" to 1, "obj.hydra_fang" to 1, "obj.hydra_heart" to 1)
        assertNull(player(bank = all).nextHydraPiece())
    }

    @Test
    fun `tattered page roll gives the page held least across inventory and bank`() {
        val owned =
            player(
                inv = mapOf("obj.hosdun_moon_page" to 2),
                bank = mapOf("obj.hosdun_sun_page" to 1, "obj.hosdun_temple_page" to 3),
            )
        assertEquals("obj.hosdun_sun_page", owned.fewestTatteredPage())
    }

    @Test
    fun `mimic tables match the wiki first-attempt rates exactly`() {
        assertMimicRates(eliteMimicDropTable.mainWeights(), total = 28750, rareDenominator = 250)
        assertMimicRates(masterMimicDropTable.mainWeights(), total = 26220, rareDenominator = 228)
    }

    private fun assertMimicRates(weights: Map<String, Double>, total: Int, rareDenominator: Int) {
        assertEquals(total.toDouble(), weights.values.sum())
        assertEquals(6.0 / rareDenominator, weights.getValue("obj.ring_of_3rd_age") / total, 1e-12)
        val thirdAge = weights.filterKeys { it.startsWith("obj.trail_") || it.startsWith("obj.3a_") }
        assertEquals(23, thirdAge.size)
        assertEquals(1.0 / rareDenominator, thirdAge.values.sum() / total, 1e-12)
    }

    private fun dtx.rs.RSDropTable<Player, DropRollItem>.mainWeights(): Map<String, Double> {
        val main = tableEntries.filterIsInstance<RSWeightedTable<Player, DropRollItem>>().single()
        return main.tableEntries.associate { entry ->
            val item = (entry.rollable as Single<*, *>).result as DropRollItem
            item.obj to entry.weight
        }
    }
}
