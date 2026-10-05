package org.rsmod.content.skills.construction

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.content.skills.construction.data.Flatpacks
import org.rsmod.content.skills.construction.data.RoomType
import org.rsmod.content.skills.construction.data.Workshop

@ResourceLock("ServerCacheManager")
class WorkshopTest {
    private val options = RoomType.entries.flatMap { it.hotspots }.flatMap { it.options }

    @Test
    fun `every workshop symbol exists in the cache`() {
        val locs =
            Workshop.TOOL_STORES.keys +
                Workshop.CRAFTING_TABLES.keys +
                Workshop.REPAIR_BENCHES.keys +
                Flatpacks.WORKBENCHES.keys
        for (loc in locs) {
            loc.asRSCM(RSCMType.LOC)
        }
        val objs =
            Workshop.TOOL_STORES.values.flatten() +
                Workshop.CLOCKWORK_RECIPES.flatMap { recipe ->
                    recipe.ingredients.flatMap { it.objs } + recipe.output
                } +
                Workshop.REPAIRS.flatMap { repair ->
                    repair.rewards.mapNotNull { it.obj } + repair.item
                }
        for (obj in objs) {
            obj.asRSCM(RSCMType.OBJ)
        }
    }

    @Test
    fun `an upgrade always follows the piece it upgrades`() {
        for (room in RoomType.entries) {
            for (group in room.hotspots) {
                assertFalse(group.options.firstOrNull()?.upgrade == true, "${room.label}/${group.key}")
            }
        }
    }

    @Test
    fun `tool stores fill one more slot per tier`() {
        val tools = RoomType.WORKSHOP.hotspot("tools")!!
        for ((tier, option) in tools.options.withIndex()) {
            val filled = option.built.count { it !in tools.locs }
            assertEquals(tier + 1, filled, option.label)
        }
        assertEquals(Workshop.TOOL_STORES.keys, tools.options.last().built.toSet())
    }

    @Test
    fun `repair rewards are weighted like the wiki`() {
        for (repair in Workshop.REPAIRS) {
            val total = repair.rewards.sumOf { it.weight }
            assertTrue(total == 100 || total == 200, "${repair.item} weighs $total")
        }
    }

    @Test
    fun `flatpacks resolve for familiar furniture`() {
        fun flatpack(label: String) = Flatpacks.of(options.first { it.label == label })
        assertEquals("obj.poh_flatpack_armchair1", flatpack("Crude wooden chair"))
        assertEquals("obj.poh_flatpack_lecturn1", flatpack("Oak lectern"))
        assertEquals(null, flatpack("Bench with vice"))
    }

    @Test
    fun `no two different pieces pack into the same flatpack`() {
        val byFlatpack = options.filter { Flatpacks.of(it) != null }.groupBy { Flatpacks.of(it) }
        val clashes =
            byFlatpack.filterValues { pieces -> pieces.map { it.label }.distinct().size > 1 }
        assertTrue(clashes.isEmpty(), "Shared flatpacks: ${clashes.mapValues { (_, v) -> v.map { it.label } }}")
        assertTrue(byFlatpack.size >= 60, "Only ${byFlatpack.size} pieces can be packed")
    }

    companion object {
        @JvmStatic
        @BeforeAll
        fun loadCache() {
            ServerCacheManager.init(240).close()
        }
    }
}
