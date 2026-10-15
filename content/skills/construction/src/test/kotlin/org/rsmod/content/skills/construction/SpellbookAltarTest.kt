package org.rsmod.content.skills.construction

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.combat.commons.magic.Spellbook
import org.rsmod.content.skills.construction.data.FurnitureRows
import org.rsmod.content.skills.construction.data.RoomType

@ResourceLock("ServerCacheManager")
class SpellbookAltarTest {
    private val altar = RoomType.ACHIEVEMENT_GALLERY.hotspot("altar")!!

    @Test
    fun `the occult altar is built over each of the other three altars`() {
        assertEquals(listOf(null, null, null, 0, 1, 2), altar.options.map { it.upgradeFrom })
        assertEquals(
            listOf("Ancient altar", "Lunar altar", "Dark altar"),
            altar.options.filterNot { it.upgrade }.map { it.label },
        )
        val occultRows = altar.options.filter { it.upgradeFrom != null }.map { FurnitureRows.of(it)!!.rowId }
        assertEquals(3, occultRows.toSet().size)
    }

    @Test
    fun `each single altar venerates and the occult altar names the other books`() {
        for (loc in listOf("loc.poh_altar_ancient", "loc.poh_altar_lunar", "loc.poh_altar_dark")) {
            assertEquals("Venerate", ops(loc)[0], loc)
        }
        val variants =
            mapOf(
                Spellbook.Standard to "loc.poh_altar_occult_standard",
                Spellbook.Ancients to "loc.poh_altar_occult_ancient",
                Spellbook.Lunars to "loc.poh_altar_occult_lunar",
                Spellbook.Arceuus to "loc.poh_altar_occult_arceuus",
            )
        val names = mapOf(Spellbook.Standard to "Standard", Spellbook.Ancients to "Ancient", Spellbook.Lunars to "Lunar", Spellbook.Arceuus to "Arceuus")
        for ((book, loc) in variants) {
            assertEquals((Spellbook.entries - book).map(names::getValue), ops(loc).subList(1, 4), loc)
        }
    }

    private fun ops(loc: String): List<String?> {
        val type = ServerCacheManager.getObject(loc.asRSCM(RSCMType.LOC))!!
        return (0..4).map { type.actions.getOpOrNull(it) }
    }

    companion object {
        @JvmStatic
        @BeforeAll
        fun loadCache() {
            ServerCacheManager.init(240).close()
        }
    }
}
