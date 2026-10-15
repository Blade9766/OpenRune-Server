package org.rsmod.content.skills.construction

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.config.refs.params
import org.rsmod.api.table.QuestRow
import org.rsmod.content.skills.construction.data.Nexus
import org.rsmod.content.skills.construction.data.Portals
import org.rsmod.content.skills.construction.data.Portals.Destination
import org.rsmod.content.skills.construction.data.RoomType

@ResourceLock("ServerCacheManager")
class PortalsTest {
    @Test
    fun `every destination has a portal in every frame`() {
        val built = RoomType.PORTAL_CHAMBER.hotspots.filter { it.key.startsWith("portal_") }
            .flatMap { group -> group.options.flatMap { it.built } }
            .toSet()
        assertEquals(Portals.FRAMES.toSet(), built)
        for (frame in Portals.FRAMES) {
            for (destination in Destination.entries) {
                destination.portal(frame).asRSCM(RSCMType.LOC)
            }
        }
    }

    @Test
    fun `every spell destination is a teleport spell that lands somewhere`() {
        val books = mapOf("Standard" to 0, "Ancients" to 1, "Lunars" to 2, "Arceuus" to 3)
        val spells = ServerCacheManager.getItems().values.filter { it.paramOrNull(params.spell_name) != null }
        for (destination in Destination.entries.filter { it.spell != null }) {
            val spell =
                spells.firstOrNull {
                    it.paramOrNull(params.spell_name) == destination.spell &&
                        (destination.spellbook == null ||
                            it.paramOrNull(params.spell_spellbook) == books.getValue(destination.spellbook!!))
                }
            assertNotNull(spell, destination.name)
            assertNotNull(spell!!.paramOrNull(params.spell_telecoord), destination.name)
        }
    }

    @Test
    fun `two-way portals toggle and every space has a varbit`() {
        for (destination in Destination.entries.filter { it.alternate != null }) {
            destination.alternate!!.varbit.asRSCM(RSCMType.VARBIT)
        }
        Portals.VARBITS.forEach { it.asRSCM(RSCMType.VARBIT) }
        for (focus in Portals.FOCI) {
            val type = ServerCacheManager.getObject(focus.asRSCM(RSCMType.LOC))!!
            assertEquals("Direct-portal", type.actions.getOpOrNull(0), focus)
        }
        assertEquals(Destination.entries.size, Destination.entries.map { it.key }.toSet().size)
    }

    @Test
    fun `the basalt destinations cost basalt and salts, need My Arm, and are on the nexus too`() {
        val basalt = Destination.entries.filter { it.spell == null }
        assertEquals(listOf(Destination.TROLL_STRONGHOLD, Destination.WEISS), basalt)
        for (destination in basalt) {
            assertNotNull(destination.fixed)
            destination.cost.forEach { (obj, _) -> obj.asRSCM(RSCMType.OBJ) }
            destination.teleportObj!!.asRSCM(RSCMType.OBJ)
            assertEquals("Making Friends with My Arm", QuestRow.getRow("dbrow.${destination.quest}").displayname)
        }
        Portals.NOTES.forEach { (obj, note) -> assertEquals(note.asRSCM(RSCMType.OBJ), ServerCacheManager.getItem(obj.asRSCM(RSCMType.OBJ))!!.certlink) }
        assertEquals("Te salt", ServerCacheManager.getItem("obj.red_salt".asRSCM(RSCMType.OBJ))!!.name)
        assertEquals("Urt salt", ServerCacheManager.getItem("obj.green_salt".asRSCM(RSCMType.OBJ))!!.name)
        assertEquals("Efh salt", ServerCacheManager.getItem("obj.blue_salt".asRSCM(RSCMType.OBJ))!!.name)
        val nexusObjs = Nexus.destinations.mapNotNull { it.spellObj }.toSet()
        for (destination in basalt) {
            assertTrue(destination.teleportObj!!.asRSCM(RSCMType.OBJ) in nexusObjs, destination.name)
        }
    }

    companion object {
        @JvmStatic
        @BeforeAll
        fun loadCache() {
            ServerCacheManager.init(240).close()
        }
    }
}
