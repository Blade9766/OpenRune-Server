package org.rsmod.content.skills.construction

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.content.skills.construction.data.Floor
import org.rsmod.content.skills.construction.data.HouseLocation
import org.rsmod.content.skills.construction.data.RoomType
import org.rsmod.content.skills.construction.house.HouseAdverts
import org.rsmod.content.skills.construction.house.HouseState
import org.rsmod.content.skills.construction.house.HouseVisitors
import org.rsmod.content.skills.construction.house.Room

class HouseAdvertsTest {
    private fun option(type: RoomType, key: String, label: String): Int =
        type.hotspot(key)!!.options.indexOfFirst { it.label == label }.also { check(it >= 0) { label } }

    @Test
    fun `a plain house has nothing in any column`() {
        val state = HouseState().apply { createStarterHouse() }
        assertEquals(HouseAdverts.Features(false, 0, 0, 0, 0, false), HouseAdverts.features(state))
    }

    @Test
    fun `the columns read the best of each feature built`() {
        val state = HouseState().apply { createStarterHouse() }
        fun room(type: RoomType, x: Int, vararg built: Pair<String, String>) {
            val room = Room(type, 0)
            for ((key, label) in built) {
                room.furniture[key] = option(type, key, label)
            }
            state[Floor.GROUND, x, 0] = room
        }
        room(RoomType.CHAPEL, 0, "altar" to "Gilded altar")
        room(RoomType.PORTAL_NEXUS, 1, "nexus" to "Gilded portal nexus")
        room(RoomType.ACHIEVEMENT_GALLERY, 2, "jewellery_box" to "Ornate jewellery box", "altar" to "Lunar altar")
        room(RoomType.SUPERIOR_GARDEN, 3, "pool" to "Fancy rejuvenation pool")
        room(RoomType.WORKSHOP, 4, "repair" to "Armour stand")

        val features = HouseAdverts.features(state)
        assertEquals(HouseAdverts.Features(true, 2, 3, 4, 2, true), features)
        assertEquals(
            "Zezima|1|83|Y|2|3|4|2|Y",
            HouseAdverts.line("Zezima", HouseLocation.RIMMINGTON, 83, features),
        )

        state[Floor.GROUND, 2, 0]!!.furniture["altar"] =
            RoomType.ACHIEVEMENT_GALLERY.hotspot("altar")!!.options.indexOfLast { it.label == "Occult altar" }
        assertEquals(4, HouseAdverts.features(state).spellbookAltar)
    }

    @Test
    fun `every town's board id matches the board's clientscript`() {
        val ids = HouseLocation.entries.associate { it.name to it.boardId }
        assertEquals(
            mapOf(
                "RIMMINGTON" to 1, "TAVERLEY" to 2, "POLLNIVNEACH" to 3, "RELLEKKA" to 4, "BRIMHAVEN" to 5,
                "YANILLE" to 6, "HOSIDIUS" to 8, "PRIFDDINAS" to 9,
            ),
            ids,
        )
        assertEquals(HouseLocation.HOSIDIUS, HouseLocation.forBoardId(8))
    }

    @Test
    @ResourceLock("ServerCacheManager")
    fun `the boards, interface, varbits, lock and script exist`() {
        ServerCacheManager.init(240).close()
        HouseLocation.entries.forEach { it.board.asRSCM(RSCMType.LOC) }
        "interface.poh_board".asRSCM(RSCMType.INTERFACE)
        listOf("addremove", "refresh_button").forEach { "component.poh_board:$it".asRSCM(RSCMType.COMPONENT) }
        listOf("varbit.poh_board_last_loc", HouseAdverts.ADVERTISING_VARBIT, HouseVisitors.LOCKED_VARBIT).forEach { it.asRSCM(RSCMType.VARBIT) }
        "clientscript.[clientscript,poh_board_addline]".asRSCM(RSCMType.CLIENTSCRIPT)
    }
}
