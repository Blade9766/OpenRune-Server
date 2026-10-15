package org.rsmod.content.skills.construction

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.content.skills.construction.data.Nexus
import org.rsmod.content.skills.construction.data.RoomType

@ResourceLock("ServerCacheManager")
class PortalNexusTest {
    private val room = RoomType.PORTAL_NEXUS

    @Test
    fun `the portal nexus is a unique room with four doors`() {
        assertEquals(28, room.roomTypeId)
        assertTrue(room.unique)
        assertEquals(listOf(0, 1, 2, 3), (0..3).filter { room.hasDoor(it, 0) })
        for (group in room.hotspots) {
            group.locs.forEach { it.asRSCM(RSCMType.LOC) }
            group.options.flatMap { it.built }.forEach { it.asRSCM(RSCMType.LOC) }
            group.options.flatMap { it.materials }.forEach { it.obj.asRSCM(RSCMType.OBJ) }
        }
    }

    @Test
    fun `the cache lists every destination with its thousandfold runes`() {
        val ids = Nexus.destinations.map { it.id }
        assertEquals((1..41).toList(), ids.filter { it < Nexus.ALTERNATE_OFFSET })
        val varrock = Nexus.of(1)!!
        assertEquals("Varrock", varrock.label)
        assertEquals(25, varrock.level)
        assertEquals(
            listOf("obj.lawrune" to 1000, "obj.firerune" to 1000, "obj.airrune" to 3000).map { (obj, n) -> obj.asRSCM(RSCMType.OBJ) to n },
            varrock.runes,
        )
        assertEquals(151, varrock.alternate)
        assertEquals("Grand Exchange", Nexus.of(151)!!.label)
        assertEquals(1, Nexus.of(151)!!.primary)
    }

    @Test
    fun `the nexus and amulet varbits and locs exist with their ops`() {
        Nexus.SLOTS.forEach { it.asRSCM(RSCMType.VARBIT) }
        Nexus.LEFT_CLICK.asRSCM(RSCMType.VARBIT)
        for (tier in Nexus.Tier.entries) {
            val type = ServerCacheManager.getObject(tier.loc.asRSCM(RSCMType.LOC))!!
            assertEquals(listOf("Teleport", "Teleport Menu", "Configuration"), (0..2).map { type.actions.getOpOrNull(it) })
        }
        for (amulet in Nexus.Amulet.entries) {
            amulet.varbit.asRSCM(RSCMType.VARBIT)
            for (loc in amulet.locs) {
                assertEquals("Teleport menu", ServerCacheManager.getObject(loc.asRSCM(RSCMType.LOC))!!.actions.getOpOrNull(1), loc)
            }
        }
        assertEquals("loc.poh_amulet_xeric_heart", Nexus.Amulet.XERIC.shown(4))
        assertEquals("loc.poh_amulet_digsite", Nexus.Amulet.DIGSITE.shown(0))
    }

    companion object {
        @JvmStatic
        @BeforeAll
        fun loadCache() {
            ServerCacheManager.init(240).close()
        }
    }
}
