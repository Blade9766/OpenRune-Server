package org.rsmod.content.skills.construction

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.content.skills.construction.data.Costumes
import org.rsmod.content.skills.construction.data.Costumes.Store
import org.rsmod.content.skills.construction.data.RoomType

@ResourceLock("ServerCacheManager")
class CostumeRoomTest {
    @Test
    fun `the costume room is unique with one south door and a group per store`() {
        val room = RoomType.COSTUME_ROOM
        assertEquals(true, room.unique)
        assertEquals(23, room.roomTypeId)
        assertEquals(listOf(3), (0..3).filter { room.hasDoor(it, 0) })
        for (store in Store.entries) {
            val group = room.hotspot(store.group)!!
            assertEquals(store.closed, group.options.map { it.built.single() }, store.name)
            assertEquals(store.closed.size, store.pages.size, store.name)
            assertEquals(store.closed.size, store.limits.size, store.name)
        }
    }

    @Test
    fun `stores open, are searched and close`() {
        for (store in Store.entries) {
            for ((option, closed) in store.closed.withIndex()) {
                val open = store.opened.getOrNull(option)
                if (open == null) {
                    assertEquals("Search", op(closed, 0), closed)
                    continue
                }
                assertEquals("Open", op(closed, 0), closed)
                assertEquals("Search", op(open, 0), open)
                assertEquals("Close", op(open, 1), open)
            }
        }
    }

    @Test
    fun `stores take only their own sets and chests only their tiers`() {
        val mysticHat = "obj.mystic_hat".asRSCM(RSCMType.OBJ)
        assertNotNull(Costumes.setFor(Store.MAGIC_WARDROBE, 0, mysticHat))
        assertNull(Costumes.setFor(Store.ARMOUR_CASE, 2, mysticHat))
        val strawBoater = "obj.strawboater_red".asRSCM(RSCMType.OBJ)
        assertNull(Costumes.setFor(Store.TREASURE_CHEST, 0, strawBoater))
        assertNotNull(Costumes.setFor(Store.TREASURE_CHEST, 1, strawBoater))
        for (store in Store.entries) {
            assertTrue(Costumes.setsOf(store, store.closed.lastIndex).isNotEmpty(), store.name)
        }
    }

    @Test
    fun `the storage inventory and view varbits exist`() {
        Costumes.INV.asRSCM(RSCMType.INV)
        listOf("varbit.poh_costume_store", "varbit.poh_costume_option", "varbit.poh_costume_page")
            .forEach { it.asRSCM(RSCMType.VARBIT) }
        "varp.if2".asRSCM(RSCMType.VARP)
        "clientscript.[clientscript,poh_costumes_init]".asRSCM(RSCMType.CLIENTSCRIPT)
    }

    private fun op(loc: String, index: Int): String? =
        ServerCacheManager.getObject(loc.asRSCM(RSCMType.LOC))!!.actions.getOpOrNull(index)

    companion object {
        @JvmStatic
        @BeforeAll
        fun loadCache() {
            ServerCacheManager.init(240).close()
        }
    }
}
