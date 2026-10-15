package org.rsmod.api.invtx

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
import org.rsmod.api.inv.storage.PlayerItemStorageContext
import org.rsmod.api.inv.storage.PlayerItemStorageHook
import org.rsmod.api.inv.storage.VirtualItemConsumePolicy
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class VirtualInvTransactionsTest {
    @Test
    fun `deferred add leaves storage untouched until commit`() {
        val (player, bag) = setup(COAL, capacity = 10)
        val result = player.invAdd(player.inv, COAL, 4, autoCommit = false)
        assertTrue(result.success)
        assertEquals(0, bag.stored)
        result.commitAll()
        assertEquals(4, bag.stored)
        assertEquals(0, player.inv.physicalCount(COAL))
    }

    @Test
    fun `deferred add that is never committed changes nothing`() {
        val (player, bag) = setup(COAL, capacity = 3)
        val result = player.invAdd(player.inv, COAL, 5, autoCommit = false)
        assertTrue(result.success)
        assertEquals(0, bag.stored)
        assertEquals(0, player.inv.physicalCount(COAL))
    }

    @Test
    fun `failed add into a full inventory rolls the storage back`() {
        val (player, bag) = setup(COAL, capacity = 3)
        player.fill(JUNK)
        val result = player.invAdd(player.inv, COAL, 5)
        assertFalse(result.success)
        assertEquals(0, bag.stored)
        assertEquals(0, player.inv.physicalCount(COAL))
        assertEquals(28, player.inv.physicalCount(JUNK))
    }

    @Test
    fun `split add fills storage then inventory and keeps every obj`() {
        val (player, bag) = setup(COAL, capacity = 3)
        val result = player.invAdd(player.inv, COAL, 5)
        assertTrue(result.success)
        assertEquals(5, result.completed())
        assertEquals(3, bag.stored)
        assertEquals(2, player.inv.physicalCount(COAL))
    }

    @Test
    fun `stack overflow on the inventory part rolls the storage back`() {
        val (player, bag) = setup(COINS, capacity = 3)
        player.inv[0] = InvObj(COINS, Int.MAX_VALUE - 5)
        val result = player.invAdd(player.inv, COINS, 10)
        assertFalse(result.success)
        assertEquals(0, bag.stored)
        assertEquals(Int.MAX_VALUE - 5, player.inv.physicalCount(COINS))
    }

    @Test
    fun `deferred delete leaves storage untouched until commit`() {
        val (player, bag) = setup(COAL, capacity = 10, policy = VirtualItemConsumePolicy.StorageFirst)
        bag.stored = 4
        player.inv[0] = InvObj(COAL)
        player.inv[1] = InvObj(COAL)
        val result = player.invDel(player.inv, COAL, 5, autoCommit = false)
        assertTrue(result.success)
        assertEquals(4, bag.stored)
        assertEquals(2, player.inv.physicalCount(COAL))
        result.commitAll()
        assertEquals(0, bag.stored)
        assertEquals(1, player.inv.physicalCount(COAL))
    }

    @Test
    fun `failed inventory part of a delete restores the storage`() {
        val (player, bag) = setup(COAL, capacity = 10, policy = VirtualItemConsumePolicy.StorageFirst)
        bag.stored = 4
        player.inv[0] = InvObj(COAL)
        player.inv[1] = InvObj(COAL)
        val result = player.invDel(player.inv, COAL, 6, slot = 5)
        assertFalse(result.success)
        assertEquals(4, bag.stored)
        assertEquals(2, player.inv.physicalCount(COAL))
    }

    @Test
    fun `storage only delete commits with the transaction`() {
        val (player, bag) = setup(COAL, capacity = 10, policy = VirtualItemConsumePolicy.StorageFirst)
        bag.stored = 4
        val deferred = player.invDel(player.inv, COAL, 3, autoCommit = false)
        assertTrue(deferred.success)
        assertEquals(4, bag.stored)
        deferred.commitAll()
        assertEquals(1, bag.stored)

        val immediate = player.invDel(player.inv, COAL, 1)
        assertTrue(immediate.success)
        assertEquals(0, bag.stored)
    }

    private fun setup(
        item: String,
        capacity: Int,
        policy: VirtualItemConsumePolicy = VirtualItemConsumePolicy.InventoryFirst,
    ): Pair<Player, TestBag> {
        val bag = TestBag(item, capacity, policy)
        cachedPlayerItemStorage = PlayerItemStorage(setOf(bag))
        val player =
            Player().apply {
                inv =
                    Inventory(
                        checkNotNull(ServerCacheManager.getInventory("inv.inv".asRSCM())),
                        arrayOfNulls(28),
                    )
            }
        return player to bag
    }

    private fun Player.fill(obj: String) {
        for (slot in 0 until inv.size) {
            inv[slot] = InvObj(obj)
        }
    }

    private class TestBag(
        private val item: String,
        private val capacity: Int,
        override val consumePolicy: VirtualItemConsumePolicy,
    ) : PlayerItemStorageHook {
        var stored = 0

        override fun shouldProcess(ctx: PlayerItemStorageContext): Boolean =
            ctx.itemInternal == item

        override fun contains(ctx: PlayerItemStorageContext): Int = stored

        override fun remove(ctx: PlayerItemStorageContext, amount: Int): Int {
            val removed = minOf(stored, amount)
            stored -= removed
            return removed
        }

        override fun add(ctx: PlayerItemStorageContext, amount: Int): Int {
            val added = minOf(capacity - stored, amount)
            stored += added
            return added
        }
    }

    companion object {
        private const val COAL = "obj.coal"
        private const val COINS = "obj.coins"
        private const val JUNK = "obj.bronze_dagger"

        @JvmStatic
        @BeforeAll
        fun cache() {
            ServerCacheManager.init(240).close()
            cachedInventoryTransactions = InvTransactions.from()
        }
    }
}
