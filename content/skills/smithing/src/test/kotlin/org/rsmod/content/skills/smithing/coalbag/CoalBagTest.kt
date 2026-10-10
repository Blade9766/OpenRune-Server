package org.rsmod.content.skills.smithing.coalbag

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import org.junit.jupiter.api.Assertions.assertEquals
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
class CoalBagTest {
    @Test
    fun `filling an open bag moves only inventory coal`() {
        val player = player()
        CoalBag.addStored(player, 25)
        repeat(5) { player.inv[it + 1] = InvObj(COAL) }
        assertEquals(2, CoalBag.fillFromInventory(player))
        assertEquals(27, CoalBag.storedAmount(player))
        assertEquals(3, player.inv.physicalCount(COAL))
    }

    @Test
    fun `filling with no inventory coal does not cycle the stored coal`() {
        val player = player()
        CoalBag.addStored(player, 10)
        assertEquals(0, CoalBag.fillFromInventory(player))
        assertEquals(10, CoalBag.storedAmount(player))
        assertEquals(0, player.inv.physicalCount(COAL))
    }

    @Test
    fun `filling a full bag takes nothing`() {
        val player = player()
        CoalBag.addStored(player, 27)
        player.inv[1] = InvObj(COAL)
        assertEquals(0, CoalBag.fillFromInventory(player))
        assertEquals(27, CoalBag.storedAmount(player))
        assertEquals(1, player.inv.physicalCount(COAL))
    }

    @Test
    fun `emptying into a full inventory keeps the coal in the bag`() {
        val player = player()
        CoalBag.addStored(player, 10)
        for (slot in 1 until 28) player.inv[slot] = InvObj(JUNK)
        assertEquals(0, CoalBag.emptyIntoInventory(player))
        assertEquals(10, CoalBag.storedAmount(player))
        assertEquals(0, player.inv.physicalCount(COAL))
    }

    @Test
    fun `emptying moves only what fits`() {
        val player = player()
        CoalBag.addStored(player, 10)
        for (slot in 1 until 25) player.inv[slot] = InvObj(JUNK)
        assertEquals(3, CoalBag.emptyIntoInventory(player))
        assertEquals(7, CoalBag.storedAmount(player))
        assertEquals(3, player.inv.physicalCount(COAL))
    }

    private fun player(): Player =
        Player().apply {
            inv =
                Inventory(
                    checkNotNull(ServerCacheManager.getInventory("inv.inv".asRSCM())),
                    arrayOfNulls(28),
                )
            worn =
                Inventory(
                    checkNotNull(ServerCacheManager.getInventory("inv.worn".asRSCM())),
                    arrayOfNulls(14),
                )
            inv[0] = InvObj("obj.coal_bag_open")
        }

    companion object {
        private const val COAL = "obj.coal"
        private const val JUNK = "obj.bronze_dagger"

        @JvmStatic
        @BeforeAll
        fun cache() {
            ServerCacheManager.init(240).close()
            with(InvTransactionsScript(PlayerItemStorage(setOf(CoalBagStorageHook())))) {
                ScriptContext(EventBus(), CheatCommandMap(), EngineQueueCache()).startup()
            }
        }
    }
}
