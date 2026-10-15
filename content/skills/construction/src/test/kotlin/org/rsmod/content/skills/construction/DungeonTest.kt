package org.rsmod.content.skills.construction

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.content.skills.construction.data.Dungeon
import org.rsmod.content.skills.construction.data.RoomType

@ResourceLock("ServerCacheManager")
class DungeonTest {
    @Test
    fun `every door, trap and their states exist`() {
        for (door in Dungeon.Door.entries) {
            listOf(door.left, door.right, door.opened(door.left), door.opened(door.right))
                .forEach { it.asRSCM(RSCMType.LOC) }
        }
        for (trap in Dungeon.Trap.entries) {
            trap.built.asRSCM(RSCMType.LOC)
            trap.hidden.asRSCM(RSCMType.LOC)
        }
        Dungeon.TRAP_TIMER.asRSCM(RSCMType.TIMER)
        "obj.lockpick".asRSCM(RSCMType.OBJ)
    }

    @Test
    fun `every dungeon room but the oubliette and treasure room has both door spaces and only the corridor and cross have traps`() {
        for (room in RoomType.entries.filter { it.dungeon && it != RoomType.OUBLIETTE && it != RoomType.TREASURE_ROOM }) {
            val keys = room.hotspots.map { it.key }
            assertEquals(true, "door_north" in keys && "door_south" in keys, room.label)
            assertEquals(room != RoomType.DUNGEON_STAIRS, "trap_north" in keys, room.label)
        }
    }

    @Test
    fun `the marble trap drains a twentieth of agility plus one`() {
        assertEquals(1, Dungeon.marbleDrain(1))
        assertEquals(5, Dungeon.marbleDrain(99))
    }

    @Test
    fun `every guard has a statue and an npc that can be fought`() {
        for (guard in Dungeon.GUARDS) {
            Dungeon.guardStatue(guard).asRSCM(RSCMType.LOC)
            val npc = ServerCacheManager.getNpc("npc.$guard".asRSCM(RSCMType.NPC))!!
            assertEquals("Attack", npc.actions.getOpOrNull(1), guard)
        }
        for (room in RoomType.entries.filter { it.dungeon && it != RoomType.TREASURE_ROOM }) {
            assertEquals(true, room.hotspots.any { it.label == "Guard space" }, room.label)
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
