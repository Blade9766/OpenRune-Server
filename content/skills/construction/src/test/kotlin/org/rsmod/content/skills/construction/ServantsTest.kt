package org.rsmod.content.skills.construction

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.varp.VarpLifetime
import dev.openrune.types.varp.baseVar
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.table.FoodRow
import org.rsmod.content.skills.construction.data.Floor
import org.rsmod.content.skills.construction.data.Kitchen
import org.rsmod.content.skills.construction.data.RoomType
import org.rsmod.content.skills.construction.data.Servant
import org.rsmod.content.skills.construction.house.HouseState
import org.rsmod.content.skills.construction.house.Room

@ResourceLock("ServerCacheManager")
class ServantsTest {
    @Test
    fun `every servant npc and fetchable item exists`() {
        for (servant in Servant.entries) {
            servant.npc.asRSCM(RSCMType.NPC)
            servant.guildNpc.asRSCM(RSCMType.NPC)
        }
        "npc.poh_chief_servant".asRSCM(RSCMType.NPC)
        Servant.FETCHABLE.forEach { (_, obj) -> obj.asRSCM(RSCMType.OBJ) }
        "queue.poh_servant_trip".asRSCM(RSCMType.QUEUE)
    }

    @Test
    fun `a hired servant is saved with the player`() {
        for (name in listOf("varbit.poh_servant_type", "varbit.poh_servant_pay")) {
            val varbit = checkNotNull(ServerCacheManager.getVarbit(name.asRSCM(RSCMType.VARBIT)))
            assertEquals(VarpLifetime.Perm, varbit.baseVar.scope, name)
        }
    }

    @Test
    fun `servant types fit their varbit and wages fit the trip counter`() {
        assertEquals(Servant.entries.size, Servant.entries.map { it.type }.toSet().size)
        assertTrue(Servant.entries.all { it.type in 1..15 })
        assertTrue(Servant.TRIPS_PER_WAGE <= 63)
    }

    @Test
    fun `every served tea, dish and drink exists`() {
        Kitchen.HOUSE_ONLY.forEach { it.asRSCM(RSCMType.OBJ) }
        Servant.entries.forEach { it.dish.asRSCM(RSCMType.OBJ) }
    }

    @Test
    fun `the best shelves in a working kitchen choose the cup`() {
        fun kitchen(stove: Int, shelves: Int): HouseState {
            val state = HouseState(owned = true)
            val room = Room(RoomType.KITCHEN, 0)
            room.furniture += mapOf("stove" to stove, "larder" to 0, "sink" to 0, "shelves" to shelves)
            state[Floor.GROUND, 1, 1] = room
            return state
        }
        assertEquals(Kitchen.Cup.CLAY, Kitchen.teaCup(kitchen(stove = 3, shelves = 3)))
        assertEquals(Kitchen.Cup.PORCELAIN, Kitchen.teaCup(kitchen(stove = 3, shelves = 4)))
        assertEquals(Kitchen.Cup.TRIMMED, Kitchen.teaCup(kitchen(stove = 6, shelves = 6)))
        assertEquals(null, Kitchen.teaCup(kitchen(stove = 2, shelves = 6)), "a firepit can't boil a kettle")
    }

    @Test
    fun `every barrel drink copies an ordinary drink the food table knows`() {
        val foods = FoodRow.all()
        for (drink in Kitchen.BARREL_DRINKS) {
            val ordinary = Kitchen.ordinaryDrink(drink).asRSCM(RSCMType.OBJ)
            val row = foods.firstOrNull { food -> food.items.any { it.id == ordinary } }
            assertTrue(row != null && row.effect.isNotBlank(), "$drink has no food row with an effect")
        }
    }

    @Test
    fun `shelves and barrels hand out things that exist`() {
        Kitchen.SHELVES.keys.forEach { it.asRSCM(RSCMType.LOC) }
        Kitchen.BARRELS.forEach { it.asRSCM(RSCMType.LOC) }
        Kitchen.FILLABLE_GLASSES.forEach { it.asRSCM(RSCMType.OBJ) }
        for (index in 0..6) {
            Kitchen.shelfItems(index).forEach { it.asRSCM(RSCMType.OBJ) }
        }
        assertEquals(3, Kitchen.shelfItems(0).size, "wooden shelves 1: teapot, cup, kettle")
        assertEquals(9, Kitchen.shelfItems(6).size, "teak shelves 2 add all six extras")
        assertEquals(Kitchen.BARRELS.size, Kitchen.BARREL_DRINKS.size)
    }

    @Test
    fun `every brewing step uses things that exist`() {
        (Kitchen.SINKS + Kitchen.OVENS + Kitchen.FIREPITS + Kitchen.LARDERS.keys).forEach {
            it.asRSCM(RSCMType.LOC)
        }
        Kitchen.LARDERS.values.flatten().forEach { it.asRSCM(RSCMType.OBJ) }
        for (pot in Kitchen.Cup.entries) {
            (listOf(pot.teapot, pot.teapotWithLeaves) + (1..Kitchen.SERVINGS).map(pot::teapot)).forEach {
                it.asRSCM(RSCMType.OBJ)
            }
        }
        listOf(Kitchen.KETTLE_EMPTY, Kitchen.KETTLE_WATER, Kitchen.KETTLE_BOILED, Kitchen.TEA_LEAVES, Kitchen.MILK)
            .forEach { it.asRSCM(RSCMType.OBJ) }
    }

    companion object {
        @JvmStatic
        @BeforeAll
        fun loadCache() {
            ServerCacheManager.init(240).close()
        }
    }
}
