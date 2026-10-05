package org.rsmod.content.skills.construction

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.content.skills.construction.data.Heraldry
import org.rsmod.content.skills.construction.data.Heraldry.Crest
import org.rsmod.content.skills.construction.data.Heraldry.Requirement

@ResourceLock("ServerCacheManager")
class HeraldryTest {
    @Test
    fun `sir renitee offers sixteen crests in four pages`() {
        assertEquals(16, Crest.entries.size)
        assertEquals(0, Crest.entries.size % 4)
        assertEquals(Crest.entries, Crest.entries.map { Crest.of(it.id) })
        assertEquals(null, Crest.of(0))
    }

    @Test
    fun `a first crest never needs anything`() {
        assertTrue(Heraldry.FREE_CRESTS.isNotEmpty())
        assertTrue(Heraldry.FREE_CRESTS.all { it.requirement == Requirement.NONE })
        assertTrue(Crest.MONEY !in Heraldry.FREE_CRESTS)
    }

    @Test
    fun `every painted item exists for every crest`() {
        for (crest in Crest.entries) {
            val products = Heraldry.products(crest)
            assertEquals(7, products.size, crest.name)
            for (product in products) {
                product.output.asRSCM(RSCMType.OBJ)
                product.materials.forEach { (obj, _) -> obj.asRSCM(RSCMType.OBJ) }
            }
        }
        Heraldry.TOY_HORSEYS.forEach { it.asRSCM(RSCMType.OBJ) }
    }

    @Test
    fun `crest requirements point at real quests and locs`() {
        for (crest in Crest.entries.filter { it.requirement == Requirement.QUEST }) {
            "dbrow.${crest.quest}".asRSCM(RSCMType.DBROW)
        }
        Heraldry.STANDS.keys.forEach { it.asRSCM(RSCMType.LOC) }
        "npc.poh_herald_of_falador".asRSCM(RSCMType.NPC)
        "varp.poh_family_crest".asRSCM(RSCMType.VARP)
    }

    companion object {
        @JvmStatic
        @BeforeAll
        fun loadCache() {
            ServerCacheManager.init(240).close()
        }
    }
}
