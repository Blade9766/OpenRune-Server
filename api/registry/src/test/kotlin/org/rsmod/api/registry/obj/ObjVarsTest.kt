package org.rsmod.api.registry.obj

import dev.openrune.ServerCacheManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.registry.zone.ZoneUpdateMap
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.obj.Obj
import org.rsmod.map.CoordGrid

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class ObjVarsTest {
    private val coords = CoordGrid(3222, 3218, 0)

    @Test
    fun `dropped charged obj keeps its vars on the ground`() {
        val registry = ObjRegistry(ZoneUpdateMap())
        val obj = Obj.fromOwner(player(), coords, InvObj(TRIDENT, vars = CHARGES))
        registry.add(obj)
        val ground = registry.findAll(coords).single()
        assertEquals(CHARGES, ground.vars)
        assertEquals(1, ground.count)
    }

    @Test
    fun `split non-stackable objs keep vars and owner`() {
        val registry = ObjRegistry(ZoneUpdateMap())
        val player = player()
        val obj = Obj.fromOwner(player, coords, InvObj(TRIDENT, count = 3, vars = CHARGES))
        assertInstanceOf(ObjRegistryResult.Add.Split::class.java, registry.add(obj))
        val ground = registry.findAll(coords).toList()
        assertEquals(3, ground.size)
        for (single in ground) {
            assertEquals(CHARGES, single.vars)
            assertEquals(player.observerUUID, single.nullableOwnerId)
        }
    }

    @Test
    fun `stackable objs with different vars do not merge`() {
        val registry = ObjRegistry(ZoneUpdateMap())
        val player = player()
        registry.add(Obj.fromOwner(player, coords, InvObj(COINS, count = 10)))
        registry.add(Obj.fromOwner(player, coords, InvObj(COINS, count = 5, vars = 7)))
        registry.add(Obj.fromOwner(player, coords, InvObj(COINS, count = 1)))
        val ground = registry.findAll(coords).toList()
        assertEquals(2, ground.size)
        assertEquals(11, ground.single { it.vars == 0 }.count)
        assertEquals(5, ground.single { it.vars == 7 }.count)
    }

    @Test
    fun `server and death drop factories keep vars`() {
        val obj = InvObj(TRIDENT, vars = CHARGES)
        val player = player()
        assertEquals(CHARGES, Obj.fromServer(MapClock(1), coords, obj).vars)
        assertEquals(CHARGES, Obj.fromPvp(player, player, obj).vars)
    }

    private fun player(): Player =
        Player().apply {
            observerUUID = 42L
            coords = this@ObjVarsTest.coords
        }

    companion object {
        private const val TRIDENT = "obj.tots_charged"
        private const val COINS = "obj.coins"
        private const val CHARGES = 1234

        @JvmStatic
        @BeforeAll
        fun cache() {
            ServerCacheManager.init(240).close()
        }
    }
}
