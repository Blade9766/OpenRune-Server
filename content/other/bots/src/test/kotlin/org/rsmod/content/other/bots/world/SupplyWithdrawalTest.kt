package org.rsmod.content.other.bots.world

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.annotations.InternalApi
import org.rsmod.api.inv.storage.PlayerItemStorage
import org.rsmod.api.invtx.InvTransactionsScript
import org.rsmod.content.other.bots.core.SupplyLoads
import org.rsmod.content.other.bots.core.SupplyPart
import org.rsmod.events.EventBus
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.InvVirtualStorageHolder
import org.rsmod.game.inv.Inventory
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.plugin.scripts.ScriptContext

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class SupplyWithdrawalTest {
    private val iron by lazy { "obj.iron_ore".asRSCM(RSCMType.OBJ) }
    private val coal by lazy { "obj.coal".asRSCM(RSCMType.OBJ) }
    private val steel by lazy { listOf(SupplyPart(iron, 1), SupplyPart(coal, 2)) }

    private val player =
        Player().apply {
            inv = Inventory(checkNotNull(ServerCacheManager.getInventory("inv.inv".asRSCM())), arrayOfNulls(28))
        }
    private val bank = Inventory(checkNotNull(ServerCacheManager.getInventory("inv.bank".asRSCM())), arrayOfNulls(800))

    private fun total(id: Int): Long =
        (player.inv.objs.asSequence() + bank.objs.asSequence()).sumOf { if (it?.id == id) it.count.toLong() else 0L }

    private fun banked(id: Int): Long = bank.objs.sumOf { if (it?.id == id) it.count.toLong() else 0L }

    @Test
    fun `a planned load moves whole sets and keeps every ore and coal accounted for`() {
        bank[0] = InvObj("obj.iron_ore", 100)
        bank[1] = InvObj("obj.coal", 100)
        val load = SupplyLoads.plan(steel, player.inv.freeSpace(), ::banked)
        assertTrue(withdrawLoad(player, bank, load))
        assertEquals(9, player.inv.count("obj.iron_ore"))
        assertEquals(18, player.inv.count("obj.coal"))
        assertEquals(100, total(iron))
        assertEquals(100, total(coal))
    }

    @Test
    fun `a load that no longer fits the inventory moves nothing`() {
        bank[0] = InvObj("obj.iron_ore", 100)
        bank[1] = InvObj("obj.coal", 100)
        val load = SupplyLoads.plan(steel, player.inv.freeSpace(), ::banked)
        player.inv[0] = InvObj("obj.bronze_bar", 1)
        player.inv[1] = InvObj("obj.bronze_bar", 1)
        assertFalse(withdrawLoad(player, bank, load))
        assertEquals(0, player.inv.count("obj.iron_ore") + player.inv.count("obj.coal"))
        assertEquals(100, banked(iron))
        assertEquals(100, banked(coal))
    }

    @Test
    fun `a part that vanished from the bank after planning moves nothing`() {
        bank[0] = InvObj("obj.iron_ore", 100)
        bank[1] = InvObj("obj.coal", 100)
        val load = SupplyLoads.plan(steel, player.inv.freeSpace(), ::banked)
        bank[1] = InvObj("obj.coal", 3)
        assertFalse(withdrawLoad(player, bank, load))
        assertEquals(100, banked(iron))
        assertEquals(3, banked(coal))
        assertEquals(0, player.inv.occupiedSpace())

        bank[1] = null
        assertFalse(withdrawLoad(player, bank, load))
        assertEquals(100, banked(iron))
        assertEquals(0, player.inv.occupiedSpace())
    }

    @Test
    fun `withdrawing the same load twice cannot take more than the bank holds`() {
        bank[0] = InvObj("obj.iron_ore", 9)
        bank[1] = InvObj("obj.coal", 18)
        val load = SupplyLoads.plan(steel, player.inv.freeSpace(), ::banked)
        assertTrue(withdrawLoad(player, bank, load))
        assertFalse(withdrawLoad(player, bank, load))
        assertEquals(9, total(iron))
        assertEquals(18, total(coal))
    }

    companion object {
        private val restored = mutableListOf<() -> Unit>()

        @OptIn(InternalApi::class)
        @JvmStatic
        @BeforeAll
        fun cache() {
            ServerCacheManager.init(240).close()
            for ((owner, name) in
                listOf(
                    "org.rsmod.api.invtx.InvTransactionsScriptKt" to "cachedInventoryTransactions",
                    "org.rsmod.api.invtx.VirtualInvTransactionsKt" to "cachedPlayerItemStorage",
                )) {
                val field = Class.forName(owner).getDeclaredField(name).apply { isAccessible = true }
                val old = field.get(null)
                restored += { field.set(null, old) }
            }
            val oldStorage = InvVirtualStorageHolder.instance
            restored += { InvVirtualStorageHolder.instance = oldStorage }
            with(InvTransactionsScript(PlayerItemStorage(emptySet()))) {
                ScriptContext(EventBus(), CheatCommandMap(), EngineQueueCache()).startup()
            }
        }

        @JvmStatic
        @AfterAll
        fun restore() {
            restored.asReversed().forEach { it() }
            restored.clear()
        }
    }
}
