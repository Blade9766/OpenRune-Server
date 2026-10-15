package org.rsmod.content.areas.city.rimmington.npcs

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.ItemServerType
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
class PhialsExchangeTest {
    @Test
    fun `coins left over keep their slot so nothing is taken without room`() {
        val player = player()
        player.inv[0] = InvObj(COINS, 100)
        player.inv[1] = InvObj(note, 10)
        player.fillRest()
        assertEquals(0, player.exchangeNotes(1, note, 2))
        assertEquals(100, player.inv.physicalCount(COINS))
        assertEquals(10, player.inv[1]?.count)
        assertEquals(0, player.inv.physicalCount(LOBSTER))
    }

    @Test
    fun `spending the last coins frees their slot for an item`() {
        val player = player()
        player.inv[0] = InvObj(COINS, FEE)
        player.inv[1] = InvObj(note, 10)
        player.fillRest()
        assertEquals(1, player.exchangeNotes(1, note, 10))
        assertEquals(0, player.inv.physicalCount(COINS))
        assertEquals(9, player.inv[1]?.count)
        assertEquals(1, player.inv.physicalCount(LOBSTER))
    }

    @Test
    fun `exchanging every note frees the note slot too`() {
        val player = player()
        player.inv[0] = InvObj(COINS, 2 * FEE)
        player.inv[1] = InvObj(note, 2)
        player.fillRest()
        assertEquals(2, player.exchangeNotes(1, note, 2))
        assertEquals(0, player.inv.physicalCount(COINS))
        assertEquals(0, player.inv.count(note.id))
        assertEquals(2, player.inv.physicalCount(LOBSTER))
    }

    @Test
    fun `amount is capped by coins and free space`() {
        val player = player()
        player.inv[0] = InvObj(COINS, 3 * FEE + 2)
        player.inv[1] = InvObj(note, 50)
        assertEquals(3, player.exchangeNotes(1, note, 50))
        assertEquals(2, player.inv.physicalCount(COINS))
        assertEquals(47, player.inv[1]?.count)
        assertEquals(3, player.inv.physicalCount(LOBSTER))
    }

    @Test
    fun `huge stacks exchange only what fits and keep totals exact`() {
        val player = player()
        player.inv[0] = InvObj(COINS, Int.MAX_VALUE)
        player.inv[1] = InvObj(note, Int.MAX_VALUE)
        val exchanged = player.exchangeNotes(1, note, Int.MAX_VALUE)
        assertEquals(26, exchanged)
        assertEquals(Int.MAX_VALUE - 26 * FEE, player.inv.physicalCount(COINS))
        assertEquals(Int.MAX_VALUE - 26, player.inv[1]?.count)
        assertEquals(26, player.inv.physicalCount(LOBSTER))
    }

    @Test
    fun `notes moved out of the slot are not exchanged`() {
        val player = player()
        player.inv[0] = InvObj(COINS, 100)
        player.inv[2] = InvObj(note, 5)
        assertEquals(0, player.exchangeNotes(1, note, 5))
        assertEquals(100, player.inv.physicalCount(COINS))
        assertEquals(5, player.inv[2]?.count)
        assertEquals(0, player.inv.physicalCount(LOBSTER))
    }

    @Test
    fun `too few coins exchange nothing`() {
        val player = player()
        player.inv[0] = InvObj(COINS, FEE - 1)
        player.inv[1] = InvObj(note, 5)
        assertEquals(0, player.exchangeNotes(1, note, 5))
        assertEquals(FEE - 1, player.inv.physicalCount(COINS))
        assertEquals(5, player.inv[1]?.count)
    }

    private fun player(): Player =
        Player().apply {
            inv =
                Inventory(
                    checkNotNull(ServerCacheManager.getInventory("inv.inv".asRSCM())),
                    arrayOfNulls(28),
                )
        }

    private fun Player.fillRest() {
        for (slot in 0 until inv.size) {
            if (inv[slot] == null) {
                inv[slot] = InvObj(JUNK)
            }
        }
    }

    private fun Inventory.count(id: Int): Int = objs.filterNotNull().filter { it.id == id }.sumOf { it.count }

    companion object {
        private const val COINS = "obj.coins"
        private const val LOBSTER = "obj.lobster"
        private const val JUNK = "obj.bronze_dagger"

        private val note: ItemServerType by lazy {
            val lobster = checkNotNull(ServerCacheManager.getItem(LOBSTER.asRSCM()))
            checkNotNull(ServerCacheManager.getItem(lobster.certlink))
        }

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
