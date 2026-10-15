package org.rsmod.content.skills.construction

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.content.skills.construction.data.Paintings
import org.rsmod.content.skills.construction.data.RoomType

@ResourceLock("ServerCacheManager")
class PaintingsTest {
    private val paintings = Paintings.PORTRAITS + Paintings.LANDSCAPES + Paintings.MAPS

    @Test
    fun `every painting sir renitee sells exists, behind real quests`() {
        for (painting in paintings) {
            painting.obj.asRSCM(RSCMType.OBJ)
            painting.quests.forEach { "dbrow.$it".asRSCM(RSCMType.DBROW) }
        }
        "varp.qp".asRSCM(RSCMType.VARP)
    }

    @Test
    fun `every painting he sells hangs in exactly one quest hall space`() {
        val spaces = listOf("portrait", "landscape", "map").map { RoomType.QUEST_HALL.hotspot(it)!! }
        val framed = spaces.flatMap { it.options }.flatMap { option -> option.materials.map { it.obj } }
        for (painting in paintings) {
            assertEquals(1, framed.count { it == painting.obj }, painting.option)
        }
        for (space in spaces) {
            space.options.flatMap { it.built }.forEach { it.asRSCM(RSCMType.LOC) }
        }
    }

    @Test
    fun `every quest a piece of furniture needs is a real quest`() {
        val quests =
            RoomType.entries.flatMap { it.hotspots }.flatMap { it.options }.mapNotNull { it.quest }
        assertEquals(6, quests.toSet().size)
        quests.forEach { "dbrow.$it".asRSCM(RSCMType.DBROW) }
    }

    companion object {
        @JvmStatic
        @BeforeAll
        fun loadCache() {
            ServerCacheManager.init(240).close()
        }
    }
}
