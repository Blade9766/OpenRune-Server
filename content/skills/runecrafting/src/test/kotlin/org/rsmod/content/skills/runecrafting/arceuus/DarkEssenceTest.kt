package org.rsmod.content.skills.runecrafting.arceuus

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
import org.rsmod.content.skills.runecrafting.arceuus.DarkEssence.DARK_BLOCK
import org.rsmod.content.skills.runecrafting.arceuus.DarkEssence.DENSE_BLOCK
import org.rsmod.content.skills.runecrafting.arceuus.DarkEssence.FRAGMENTS
import org.rsmod.content.skills.runecrafting.arceuus.DarkEssence.MAX_FRAGMENTS
import org.rsmod.content.skills.runecrafting.arceuus.DarkEssence.chiselDarkBlock
import org.rsmod.content.skills.runecrafting.arceuus.DarkEssence.venerateDenseBlocks
import org.rsmod.events.EventBus
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.plugin.scripts.ScriptContext

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class DarkEssenceTest {
    @Test
    fun `chiselable blocks stop at the 108 fragment cap`() {
        assertEquals(27, DarkEssence.chiselableBlocks(0, 27))
        assertEquals(26, DarkEssence.chiselableBlocks(0, 26))
        assertEquals(1, DarkEssence.chiselableBlocks(104, 5))
        assertEquals(0, DarkEssence.chiselableBlocks(105, 5))
        assertEquals(0, DarkEssence.chiselableBlocks(MAX_FRAGMENTS, 5))
        assertEquals(0, DarkEssence.chiselableBlocks(0, 0))
    }

    @Test
    fun `venerating drains one prayer point per block down to zero`() {
        assertEquals(26, DarkEssence.prayerDrain(26, 99))
        assertEquals(10, DarkEssence.prayerDrain(26, 10))
        assertEquals(0, DarkEssence.prayerDrain(26, 0))
    }

    @Test
    fun `venerating swaps every dense block in place and nothing else`() {
        val player = player()
        player.inv[0] = InvObj(CHISEL)
        for (slot in 1 until 27) player.inv[slot] = InvObj(DENSE_BLOCK)
        player.inv[27] = InvObj(JUNK)
        assertEquals(26, player.venerateDenseBlocks())
        assertEquals(0, player.inv.count(DENSE_BLOCK))
        assertEquals(26, player.inv.count(DARK_BLOCK))
        for (slot in 1 until 27) assertEquals(DARK_BLOCK.asRSCM(), player.inv[slot]?.id)
        assertEquals(1, player.inv.count(CHISEL))
        assertEquals(1, player.inv.count(JUNK))
    }

    @Test
    fun `venerating with no dense blocks does nothing`() {
        val player = player()
        player.inv[0] = InvObj(DARK_BLOCK)
        assertEquals(0, player.venerateDenseBlocks())
        assertEquals(1, player.inv.count(DARK_BLOCK))
    }

    @Test
    fun `chiselling a full inventory turns blocks into one fragment stack`() {
        val player = player()
        player.inv[0] = InvObj(CHISEL)
        for (slot in 1 until 28) player.inv[slot] = InvObj(DARK_BLOCK)
        var chiselled = 0
        while (player.chiselDarkBlock()) chiselled++
        assertEquals(27, chiselled)
        assertEquals(0, player.inv.count(DARK_BLOCK))
        assertEquals(MAX_FRAGMENTS, player.inv.count(FRAGMENTS))
        assertEquals(1, player.inv.objs.count { it?.id == FRAGMENTS.asRSCM() })
        assertEquals(1, player.inv.count(CHISEL))
    }

    @Test
    fun `chiselling past the cap keeps the block`() {
        val player = player()
        player.inv[0] = InvObj(FRAGMENTS, 105)
        player.inv[1] = InvObj(DARK_BLOCK)
        assertFalse(player.chiselDarkBlock())
        assertEquals(105, player.inv.count(FRAGMENTS))
        assertEquals(1, player.inv.count(DARK_BLOCK))
    }

    @Test
    fun `chiselling with no dark block adds no fragments`() {
        val player = player()
        player.inv[0] = InvObj(FRAGMENTS, 8)
        player.inv[1] = InvObj(DENSE_BLOCK)
        assertFalse(player.chiselDarkBlock())
        assertEquals(8, player.inv.count(FRAGMENTS))
        assertEquals(1, player.inv.count(DENSE_BLOCK))
    }

    @Test
    fun `one chisel adds four fragments to an existing stack`() {
        val player = player()
        player.inv[0] = InvObj(FRAGMENTS, 100)
        player.inv[1] = InvObj(DARK_BLOCK)
        assertTrue(player.chiselDarkBlock())
        assertEquals(104, player.inv.count(FRAGMENTS))
        assertEquals(0, player.inv.count(DARK_BLOCK))
    }

    private fun player(): Player =
        Player().apply {
            inv =
                Inventory(
                    checkNotNull(ServerCacheManager.getInventory("inv.inv".asRSCM())),
                    arrayOfNulls(28),
                )
        }

    private fun Inventory.count(obj: String): Int {
        val id = obj.asRSCM()
        return objs.filterNotNull().filter { it.id == id }.sumOf { it.count }
    }

    companion object {
        private const val CHISEL = "obj.chisel"
        private const val JUNK = "obj.bronze_dagger"

        @JvmStatic
        @BeforeAll
        fun cache() {
            ServerCacheManager.init(241).close()
            with(InvTransactionsScript(PlayerItemStorage(emptySet()))) {
                ScriptContext(EventBus(), CheatCommandMap(), EngineQueueCache()).startup()
            }
        }
    }
}
