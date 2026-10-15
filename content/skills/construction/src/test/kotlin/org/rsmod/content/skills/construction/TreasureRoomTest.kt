package org.rsmod.content.skills.construction

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.content.skills.construction.data.Dungeon
import org.rsmod.content.skills.construction.data.Floor
import org.rsmod.content.skills.construction.data.RoomType
import org.rsmod.content.skills.construction.data.Treasure

@ResourceLock("ServerCacheManager")
class TreasureRoomTest {
    @Test
    fun `the treasure room is a dungeon room with a single south door`() {
        val room = RoomType.TREASURE_ROOM
        assertEquals(setOf(Floor.DUNGEON), room.floors)
        assertEquals(20, room.roomTypeId)
        assertEquals(listOf(3), (0..3).filter { room.hasDoor(it, 0) })
    }

    @Test
    fun `every chest opens and its open twin can be searched`() {
        for (chest in Treasure.Chest.entries) {
            assertEquals("Open", op(chest.closed), chest.name)
            assertEquals("Search", op(chest.open), chest.name)
        }
        assertEquals(listOf(10_000, 20_000, 50_000, 75_000, 100_000), Treasure.Chest.entries.map { it.limit })
    }

    @Test
    fun `every guardian is a statue and an npc that can be fought`() {
        for (guardian in Treasure.GUARDIANS) {
            assertEquals(true, guardian in Dungeon.GUARDS, guardian)
            Dungeon.guardStatue(guardian).asRSCM(RSCMType.LOC)
            val npc = ServerCacheManager.getNpc("npc.$guardian".asRSCM(RSCMType.NPC))!!
            assertEquals("Attack", npc.actions.getOpOrNull(1), guardian)
        }
        "dbrow.quest_dragonslayer2".asRSCM(RSCMType.DBROW)
        Treasure.TREASURE_VARP.asRSCM(RSCMType.VARP)
        Treasure.COOLDOWN_VARP.asRSCM(RSCMType.VARP)
    }

    private fun op(loc: String): String? =
        ServerCacheManager.getObject(loc.asRSCM(RSCMType.LOC))!!.actions.getOpOrNull(0)

    companion object {
        @JvmStatic
        @BeforeAll
        fun loadCache() {
            ServerCacheManager.init(240).close()
        }
    }
}
