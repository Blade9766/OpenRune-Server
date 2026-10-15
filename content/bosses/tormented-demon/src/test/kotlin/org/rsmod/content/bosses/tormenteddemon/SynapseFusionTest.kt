package org.rsmod.content.bosses.tormenteddemon

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.InventoryServerType
import dev.openrune.types.util.UncheckedType
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
@OptIn(UncheckedType::class)
class SynapseFusionTest {
    @Test
    fun `fusing takes one of every input and gives one product`() {
        val player = playerWith(SYNAPSE to 2, IRON_BAR to 1, BATTLESTAFF to 1)
        assertTrue(player.fuseSynapse(player.inv, STAFF_INPUTS, PURGING_STAFF))
        assertEquals(1, player.count(SYNAPSE))
        assertEquals(0, player.count(IRON_BAR))
        assertEquals(0, player.count(BATTLESTAFF))
        assertEquals(1, player.count(PURGING_STAFF))
    }

    @Test
    fun `an input gone after the delay takes nothing and gives nothing`() {
        val player = playerWith(SYNAPSE to 1, IRON_BAR to 1)
        assertFalse(player.fuseSynapse(player.inv, STAFF_INPUTS, PURGING_STAFF))
        assertEquals(1, player.count(SYNAPSE))
        assertEquals(1, player.count(IRON_BAR))
        assertEquals(0, player.count(PURGING_STAFF))
    }

    @Test
    fun `a repeated fuse cannot spend the same inputs twice`() {
        val player = playerWith(SYNAPSE to 1, ARCLIGHT to 1)
        val inputs = listOf(SYNAPSE, ARCLIGHT)
        assertTrue(player.fuseSynapse(player.inv, inputs, EMBERLIGHT))
        assertFalse(player.fuseSynapse(player.inv, inputs, EMBERLIGHT))
        assertEquals(0, player.count(SYNAPSE))
        assertEquals(0, player.count(ARCLIGHT))
        assertEquals(1, player.count(EMBERLIGHT))
    }

    @Test
    fun `a full inventory still has room for the product`() {
        val player = playerWith(SYNAPSE to 1, LONGBOW to 1)
        val inv = player.inv
        for (slot in inv.objs.indices) {
            if (inv[slot] == null) inv[slot] = InvObj(FILLER.asRSCM(), 1)
        }
        assertTrue(player.fuseSynapse(inv, listOf(SYNAPSE, LONGBOW), SCORCHING_BOW))
        assertEquals(1, player.count(SCORCHING_BOW))
        assertEquals(0, player.count(SYNAPSE))
        assertEquals(0, player.count(LONGBOW))
        assertEquals(26, player.count(FILLER))
    }

    private fun playerWith(vararg objs: Pair<String, Int>): Player =
        Player().apply {
            inv = Inventory(InventoryServerType(size = 28, flags = 0), arrayOfNulls(28))
            var slot = 0
            for ((obj, count) in objs) {
                repeat(count) { inv[slot++] = InvObj(obj.asRSCM(), 1) }
            }
        }

    private fun Player.count(obj: String): Int {
        val id = obj.asRSCM()
        return inv.objs.filterNotNull().filter { it.id == id }.sumOf { it.count }
    }

    companion object {
        private const val SYNAPSE = "obj.tormented_synapse"
        private const val IRON_BAR = "obj.iron_bar"
        private const val BATTLESTAFF = "obj.battlestaff"
        private const val ARCLIGHT = "obj.arclight"
        private const val LONGBOW = "obj.unstrung_magic_longbow"
        private const val PURGING_STAFF = "obj.purging_staff"
        private const val EMBERLIGHT = "obj.emberlight"
        private const val SCORCHING_BOW = "obj.scorching_bow"
        private const val FILLER = "obj.bronze_dagger"
        private val STAFF_INPUTS = listOf(SYNAPSE, IRON_BAR, BATTLESTAFF)
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
                val field =
                    Class.forName(owner).getDeclaredField(name).apply { isAccessible = true }
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
