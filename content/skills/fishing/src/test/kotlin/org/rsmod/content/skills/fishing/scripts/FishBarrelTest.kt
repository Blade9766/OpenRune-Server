package org.rsmod.content.skills.fishing.scripts

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.inv.storage.PlayerItemStorage
import org.rsmod.api.invtx.InvTransactionsScript
import org.rsmod.events.EventBus
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.plugin.scripts.ScriptContext

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class FishBarrelTest {
    @Test
    fun `empty moves as many fish as fit and keeps the rest`() {
        val player = player()
        for (slot in 0 until 25) player.inv[slot] = InvObj(JUNK)
        val store = mutableMapOf(SHRIMP to 5, TROUT to 2)
        assertEquals(3, FishBarrel.empty(player, store))
        assertEquals(7, store.values.sum() + player.inv.physicalCount(SHRIMP) + player.inv.physicalCount(TROUT))
        assertEquals(0, player.inv.freeSpace())
    }

    @Test
    fun `empty into a full inventory moves nothing`() {
        val player = player()
        for (slot in 0 until 28) player.inv[slot] = InvObj(JUNK)
        val store = mutableMapOf(SHRIMP to 5)
        assertEquals(0, FishBarrel.empty(player, store))
        assertEquals(mapOf(SHRIMP to 5), store)
    }

    @Test
    fun `empty with room clears the barrel`() {
        val player = player()
        val store = mutableMapOf(SHRIMP to 5, TROUT to 2)
        assertEquals(7, FishBarrel.empty(player, store))
        assertTrue(store.isEmpty())
        assertEquals(5, player.inv.physicalCount(SHRIMP))
        assertEquals(2, player.inv.physicalCount(TROUT))
    }

    @Test
    fun `fill stops at capacity and leaves the remainder in the inventory`() {
        val player = player()
        for (slot in 0 until 20) player.inv[slot] = InvObj(SHRIMP)
        for (slot in 20 until 28) player.inv[slot] = InvObj(TROUT)
        val store = mutableMapOf(SHRIMP to 5)
        assertEquals(23, FishBarrel.fill(player, store, listOf(SHRIMP, TROUT)))
        assertEquals(FishBarrel.CAPACITY, store.values.sum())
        assertEquals(25, store[SHRIMP])
        assertEquals(3, store[TROUT])
        assertEquals(5, player.inv.physicalCount(TROUT))
        assertEquals(0, player.inv.physicalCount(SHRIMP))
    }

    @Test
    fun `deposit only stores a fish that left the inventory`() {
        val player = player()
        val store = mutableMapOf<String, Int>()
        assertFalse(FishBarrel.deposit(player, store, SHRIMP))
        assertTrue(store.isEmpty())

        player.inv[0] = InvObj(SHRIMP)
        assertTrue(FishBarrel.deposit(player, store, SHRIMP))
        assertEquals(mapOf(SHRIMP to 1), store)
        assertEquals(0, player.inv.physicalCount(SHRIMP))
    }

    @Test
    fun `deposit into a full barrel keeps the fish`() {
        val player = player()
        player.inv[0] = InvObj(SHRIMP)
        val store = mutableMapOf(TROUT to FishBarrel.CAPACITY)
        assertFalse(FishBarrel.deposit(player, store, SHRIMP))
        assertEquals(1, player.inv.physicalCount(SHRIMP))
        assertEquals(mapOf(TROUT to FishBarrel.CAPACITY), store)
    }

    private fun player(): Player =
        Player().apply {
            inv =
                Inventory(
                    checkNotNull(ServerCacheManager.getInventory("inv.inv".asRSCM())),
                    arrayOfNulls(28),
                )
        }

    companion object {
        private const val SHRIMP = "obj.raw_shrimp"
        private const val TROUT = "obj.raw_trout"
        private const val JUNK = "obj.bronze_dagger"

        @JvmStatic
        @BeforeAll
        fun cache() {
            ServerCacheManager.init(240).close()
            with(InvTransactionsScript(PlayerItemStorage(emptySet()))) {
                ScriptContext(EventBus(), CheatCommandMap(), EngineQueueCache()).startup()
            }
        }
    }
}
