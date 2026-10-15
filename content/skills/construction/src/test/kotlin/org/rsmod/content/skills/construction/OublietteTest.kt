package org.rsmod.content.skills.construction

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.content.skills.construction.data.Floor
import org.rsmod.content.skills.construction.data.Oubliette
import org.rsmod.content.skills.construction.data.RoomType

@ResourceLock("ServerCacheManager")
class OublietteTest {
    @Test
    fun `the oubliette is a dungeon room with four doorways`() {
        val room = RoomType.OUBLIETTE
        assertEquals(setOf(Floor.DUNGEON), room.floors)
        assertEquals(16, room.roomTypeId)
        assertEquals(listOf(0, 1, 2, 3), (0..3).filter { room.hasDoor(it, 0) })
    }

    @Test
    fun `every prison gate can be opened, picked and forced`() {
        for (cage in Oubliette.Cage.entries) {
            cage.wall.asRSCM(RSCMType.LOC)
            assertEquals(listOf("Open", "Pick-lock", "Force"), ops(cage.door, 0..2), cage.name)
            assertEquals("Close", ops(cage.openDoor, 0..0).single(), cage.name)
        }
    }

    @Test
    fun `trapdoors open, close and lead down, and ladders climb`() {
        for (trapdoor in Oubliette.TRAPDOORS) {
            assertEquals("Open", ops(trapdoor, 0..0).single(), trapdoor)
            assertEquals(listOf("Go-down", "Close"), ops(Oubliette.openTrapdoor(trapdoor), 0..1), trapdoor)
        }
        for (ladder in Oubliette.LADDERS) {
            assertEquals("Climb", ops(ladder, 0..0).single(), ladder)
        }
    }

    @Test
    fun `every throne room cage and the drop queue exist`() {
        Oubliette.ThroneFloor.entries.mapNotNull { it.cage }.forEach { it.asRSCM(RSCMType.LOC) }
        Oubliette.DROP_QUEUE.asRSCM(RSCMType.QUEUE)
        assertEquals(Oubliette.ThroneFloor.entries.size, RoomType.THRONE_ROOM.hotspot("floor")!!.options.size)
        assertEquals(Oubliette.Hazard.entries.size, RoomType.OUBLIETTE.hotspot("floor")!!.options.size)
    }

    private fun ops(loc: String, range: IntRange): List<String?> {
        val type = ServerCacheManager.getObject(loc.asRSCM(RSCMType.LOC))!!
        return range.map { type.actions.getOpOrNull(it) }
    }

    companion object {
        @JvmStatic
        @BeforeAll
        fun loadCache() {
            ServerCacheManager.init(240).close()
        }
    }
}
