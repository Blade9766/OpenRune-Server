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
import org.rsmod.content.skills.construction.data.Combat
import org.rsmod.content.skills.construction.data.RoomType
import org.rsmod.content.skills.construction.house.CombatRing
import org.rsmod.map.CoordGrid

@ResourceLock("ServerCacheManager")
class CombatRoomTest {
    private val room = RoomType.COMBAT_ROOM

    @Test
    fun `the combat room has doors east, south and west`() {
        assertEquals(22, room.roomTypeId)
        assertEquals(listOf(0, 2, 3), (0..3).filter { room.hasDoor(it, 0) })
    }

    @Test
    fun `every ring piece, hotspot and dummy exists`() {
        for (group in room.hotspots) {
            group.locs.forEach { it.asRSCM(RSCMType.LOC) }
            group.options.flatMap { it.built }.forEach { it.asRSCM(RSCMType.LOC) }
        }
        for (dummy in Combat.Dummy.entries) {
            dummy.loc.asRSCM(RSCMType.LOC)
            val npc = ServerCacheManager.getNpc(dummy.npc.asRSCM(RSCMType.NPC))!!
            assertEquals("Attack", npc.actions.getOpOrNull(1), dummy.name)
            assertEquals("category.poh_combat_dummy".asRSCM(RSCMType.CATEGORY), npc.category, dummy.name)
            dummy.unlockAny.forEach { it.asRSCM(RSCMType.OBJ) }
        }
        Combat.Rack.entries.flatMap { it.items }.forEach { it.asRSCM(RSCMType.OBJ) }
        Combat.VARIANTS_VARP.asRSCM(RSCMType.VARP)
        listOf("human_jump_hurdle", "human_unarmedpunch").forEach { "seq.$it".asRSCM(RSCMType.SEQ) }
    }

    @Test
    fun `each ring builds walls and floor only where its hotspots are named for it`() {
        val group = room.hotspot("ring")!!
        val pieces = group.options.map { option -> group.locs.zip(option.built).toMap() }
        val boxing = pieces[0]
        assertEquals("loc.poh_boxing_ringwall_blue", boxing.getValue("loc.poh_gr_1_wall_bluecorner"))
        assertEquals("loc.poh_boxing_ringwall_red", boxing.getValue("loc.poh_gr_1_wall_redcorner"))
        assertEquals("loc.poh_boxing_ring_mat_middle", boxing.getValue("loc.poh_gr_1_floor_middle"))
        assertEquals("loc.poh_gr_1_wall_ranging", boxing.getValue("loc.poh_gr_1_wall_ranging"))
        val pedestals = pieces[3]
        assertEquals(Combat.MAGIC_BARRIER, pedestals.getValue("loc.poh_gr_1_wall_ranging"))
        assertEquals("loc.poh_magic_circle_mat", pedestals.getValue("loc.poh_gr_1_floor_se"))
        assertEquals("loc.poh_gr_1_floor_middle", pedestals.getValue("loc.poh_gr_1_floor_middle"))
        val beam = pieces[4]
        assertEquals(Combat.BEAM, listOf("nw", "n", "ne").map { beam.getValue("loc.poh_gr_1_floor_$it") })
        assertEquals("loc.poh_agility_rail", beam.getValue("loc.poh_gr_1_wall_agility"))
        assertEquals("loc.poh_gr_1_wall_combat", beam.getValue("loc.poh_gr_1_wall_combat"))
    }

    @Test
    fun `a ring is left across its nearest edge`() {
        val ring = CombatRing(Combat.Ring.COMBAT)
        for (x in 10..13) for (z in 20..23) ring.tiles += CoordGrid(x, z, 1)
        assertEquals(CoordGrid(9, 21, 1), ring.exitFrom(CoordGrid(10, 21, 1)))
        assertEquals(CoordGrid(12, 24, 1), ring.exitFrom(CoordGrid(12, 23, 1)))
    }

    @Test
    fun `ornate dummy forms are paid for once and remembered`() {
        var variants = 0
        assertEquals(Combat.Dummy.ORNATE_UNDEAD, Combat.shownForm(variants))
        assertTrue(Combat.unlocked(variants, Combat.Dummy.ORNATE))
        assertFalse(Combat.unlocked(variants, Combat.Dummy.ORNATE_DRAGON))
        variants = Combat.withForm(Combat.withUnlocked(variants, Combat.Dummy.ORNATE_DRAGON), Combat.Dummy.ORNATE_DRAGON)
        assertEquals(Combat.Dummy.ORNATE_DRAGON, Combat.shownForm(variants))
        assertTrue(Combat.unlocked(variants, Combat.Dummy.ORNATE_DRAGON))
        assertFalse(Combat.unlocked(variants, Combat.Dummy.ORNATE_KURASK))
        assertEquals(Combat.Dummy.ORNATE_DRAGON.loc, Combat.shownDummy(Combat.Dummy.ORNATE_UNDEAD.loc, variants))
    }

    companion object {
        @JvmStatic
        @BeforeAll
        fun loadCache() {
            ServerCacheManager.init(240).close()
        }
    }
}
