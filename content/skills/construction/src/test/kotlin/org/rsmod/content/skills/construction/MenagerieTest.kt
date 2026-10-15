package org.rsmod.content.skills.construction

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.content.other.pets.PetMenagerie
import org.rsmod.content.skills.construction.data.Floor
import org.rsmod.content.skills.construction.data.RoomType

@ResourceLock("ServerCacheManager")
class MenagerieTest {
    @Test
    fun `the outdoor menagerie alone has a habitat and stays on the ground floor`() {
        assertEquals(setOf(Floor.GROUND, Floor.UPPER), RoomType.MENAGERIE_INDOOR.floors)
        assertEquals(setOf(Floor.GROUND), RoomType.MENAGERIE_OUTDOOR.floors)
        assertEquals(null, RoomType.MENAGERIE_INDOOR.hotspot("habitat"))
        assertEquals(5, RoomType.MENAGERIE_OUTDOOR.hotspot("habitat")!!.options.size)
        for (room in listOf(RoomType.MENAGERIE_INDOOR, RoomType.MENAGERIE_OUTDOOR)) {
            for (group in room.hotspots) {
                group.locs.forEach { it.asRSCM(RSCMType.LOC) }
                group.options.flatMap { it.built }.forEach { it.asRSCM(RSCMType.LOC) }
            }
        }
    }

    @Test
    fun `every pet house tier can be viewed`() {
        for (tier in 1..6) {
            val type = ServerCacheManager.getObject("loc.poh_menagerie_pethouse_$tier".asRSCM(RSCMType.LOC))!!
            assertEquals("View", type.actions.getOpOrNull(0), "tier $tier")
        }
    }

    @Test
    fun `menagerie slots cover every form and every form varbit exists`() {
        assertEquals(71, PetMenagerie.size)
        assertEquals(11, PetMenagerie.slotOf("obj.kqpet_walking".asRSCM(RSCMType.OBJ)))
        PetMenagerie.formVarbits.forEach { it.asRSCM(RSCMType.VARBIT) }
        listOf("varp.prayer20", "varp.menagerie_contents2", "varp.menagerie_contents3").forEach { it.asRSCM(RSCMType.VARP) }
        "varbit.poh_menagerie_closed".asRSCM(RSCMType.VARBIT)
        listOf("poh_menagerie_initlist", "poh_menagerie_initroaming", "poh_menagerie_petlist")
            .forEach { "clientscript.[clientscript,$it]".asRSCM(RSCMType.CLIENTSCRIPT) }
    }

    @Test
    fun `cats, dogs, pet rocks and the other companions are kept as extras`() {
        val inv = ServerCacheManager.getInventory(PetMenagerie.EXTRAS.asRSCM(RSCMType.INV))!!
        assertEquals(12, inv.size)
        for (obj in listOf("obj.kittenobject", "obj.lazycatobject", "obj.vt_useless_rock", "obj.fishbowl_bluefish", "obj.poh_toy_cat", "obj.corgi_tan_puppy_object")) {
            assertTrue(PetMenagerie.isExtra(obj.asRSCM(RSCMType.OBJ)), obj)
        }
        assertFalse(PetMenagerie.isExtra("obj.coins".asRSCM(RSCMType.OBJ)))
        assertFalse(PetMenagerie.isExtra("obj.kqpet_walking".asRSCM(RSCMType.OBJ)))
        assertTrue(PetMenagerie.extraNpc("obj.kittenobject".asRSCM(RSCMType.OBJ)) != null)
        listOf(1, 2, 3).forEach {
            "loc.poh_menagerie_scratchingpost_$it".asRSCM(RSCMType.LOC)
            "loc.poh_menagerie_combatring_$it".asRSCM(RSCMType.LOC)
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
