package org.rsmod.content.skills.construction

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.content.skills.construction.data.Floor
import org.rsmod.content.skills.construction.data.Heraldry
import org.rsmod.content.skills.construction.data.Heraldry.Crest
import org.rsmod.content.skills.construction.data.RoomType
import org.rsmod.content.skills.construction.house.HouseMode

@ResourceLock("ServerCacheManager")
class ThroneRoomTest {
    @Test
    fun `the throne room is ground floor only with a single south door`() {
        val room = RoomType.THRONE_ROOM
        assertEquals(setOf(Floor.GROUND), room.floors)
        assertEquals(15, room.roomTypeId)
        assertEquals(listOf(3), (0..3).filter { room.hasDoor(it, 0) })
    }

    @Test
    fun `every crest has a shield in every wood`() {
        for (wood in listOf("oak", "teak", "mahogany")) {
            for (crest in Crest.entries) {
                Heraldry.crestDecor(Heraldry.crestDecor(wood), crest)!!.asRSCM(RSCMType.LOC)
            }
        }
    }

    @Test
    fun `a shield takes the owner's crest and nothing else is repainted`() {
        assertEquals("loc.poh_decor_teak_zamorak", Heraldry.crestDecor("loc.poh_decor_teak_arrav", Crest.ZAMORAK))
        assertNull(Heraldry.crestDecor("loc.poh_decor_teak_arrav", null))
        assertNull(Heraldry.crestDecor("loc.poh_wall_deco_3", Crest.ZAMORAK))
    }

    @Test
    fun `every lever has the challenge mode op`() {
        for (lever in listOf("loc.poh_lever_oak_4", "loc.poh_lever_teak_4", "loc.poh_lever_mag_4")) {
            val type = ServerCacheManager.getObject(lever.asRSCM(RSCMType.LOC))!!
            assertEquals("Challenge-mode", type.actions.getOpOrNull(1), lever)
        }
        "seq.poh_lever_pull".asRSCM(RSCMType.SEQ)
    }

    @Test
    fun `the house mode and attack op flag share the challenge mode varp`() {
        "varbit.poh_house_mode".asRSCM(RSCMType.VARBIT)
        "varbit.poh_house_attack_op".asRSCM(RSCMType.VARBIT)
        assertEquals(listOf("OFF", "CHALLENGE", "PVP"), HouseMode.entries.map { it.name })
    }

    @Test
    fun `the sprung trapdoor, the fall and the cages' animations exist`() {
        "loc.deserttreasure_pitfall_animated".asRSCM(RSCMType.LOC)
        listOf(
            "human_falling",
            "human_falling_end",
            "cage_throneroom_fall",
            "cage_throneroom_raise",
            "magic_cage_lesser",
            "magic_cage_greater",
        ).forEach { "seq.$it".asRSCM(RSCMType.SEQ) }
    }

    companion object {
        @JvmStatic
        @BeforeAll
        fun loadCache() {
            ServerCacheManager.init(240).close()
        }
    }
}
