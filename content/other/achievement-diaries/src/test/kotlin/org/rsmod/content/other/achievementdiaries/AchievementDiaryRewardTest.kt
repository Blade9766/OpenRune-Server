package org.rsmod.content.other.achievementdiaries

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
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
import org.rsmod.api.achievementdiaries.AchievementDiaries
import org.rsmod.api.achievementdiaries.DiaryClaim
import org.rsmod.api.inv.storage.PlayerItemStorage
import org.rsmod.api.invtx.InvTransactionsScript
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.api.table.AchievementDiaryTasksRow
import org.rsmod.api.table.AchievementDiaryTiersRow
import org.rsmod.coroutine.GameCoroutine
import org.rsmod.events.EventBus
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.client.Client
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.InvVirtualStorageHolder
import org.rsmod.game.inv.Inventory
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class AchievementDiaryRewardTest {
    @Test
    fun `completing every easy task completes the tier exactly once`() {
        val f = Fixture()
        val tasks = easyTasks()
        tasks.dropLast(1).forEach { assertTrue(f.diaries.complete(f.player, it)) }
        assertEquals(tasks.size - 1, f.player.vars[COUNT_VARBIT])
        assertEquals(0, f.player.vars[COMPLETE_VARBIT])

        assertTrue(f.diaries.complete(f.player, tasks.last()))
        assertFalse(f.diaries.complete(f.player, tasks.last()))
        assertEquals(tasks.size, f.player.vars[COUNT_VARBIT])
        assertEquals(1, f.player.vars[COMPLETE_VARBIT])
        assertEquals(1, f.player.vars["varbit.started_lumbridge_diary"])
    }

    @Test
    fun `an unfinished tier hands out nothing`() {
        val f = Fixture()
        easyTasks().dropLast(1).forEach { f.diaries.complete(f.player, it) }
        assertEquals(DiaryClaim.Nothing, f.diaries.claimNext(f.access(), LUMBRIDGE))
        assertEquals(0, f.player.inv.occupiedSpace())
    }

    @Test
    fun `claiming hands out the ring and lamp once`() {
        val f = Fixture()
        f.completeEasy()
        f.player.inv[0] = InvObj(COINS, 100)

        val claim = f.diaries.claimNext(f.access(), LUMBRIDGE)
        assertTrue(claim is DiaryClaim.Claimed)
        assertEquals(1, f.player.inv.count(RING))
        assertEquals(1, f.player.inv.count(LAMP))
        assertEquals(100, f.player.inv.count(COINS))
        assertEquals(1, f.player.vars[REWARD_VARBIT])

        assertEquals(DiaryClaim.Nothing, f.diaries.claimNext(f.access(), LUMBRIDGE))
        assertEquals(1, f.player.inv.count(RING))
        assertEquals(1, f.player.inv.count(LAMP))
        assertEquals(3, f.player.inv.occupiedSpace())
    }

    @Test
    fun `one free slot holds the whole reward back`() {
        val f = Fixture()
        f.completeEasy()
        for (slot in 0 until 27) f.player.inv[slot] = InvObj(DAGGER, 1)

        assertEquals(DiaryClaim.NeedSpace(2), f.diaries.claimNext(f.access(), LUMBRIDGE))
        assertEquals(0, f.player.inv.count(RING))
        assertEquals(0, f.player.inv.count(LAMP))
        assertEquals(27, f.player.inv.count(DAGGER))
        assertEquals(0, f.player.vars[REWARD_VARBIT])

        f.player.inv[26] = null
        f.player.inv[25] = null
        assertTrue(f.diaries.claimNext(f.access(), LUMBRIDGE) is DiaryClaim.Claimed)
        assertEquals(1, f.player.inv.count(RING))
        assertEquals(1, f.player.inv.count(LAMP))
        assertEquals(25, f.player.inv.count(DAGGER))
    }

    @Test
    fun `a ring held anywhere is not lost`() {
        val f = Fixture()
        f.completeEasy()
        f.diaries.claimNext(f.access(), LUMBRIDGE)
        assertTrue(f.diaries.lostRewards(f.access(), LUMBRIDGE).isEmpty())

        val slot = (0 until 28).first { f.player.inv[it]?.id == RING.asRSCM() }
        f.player.inv[slot] = null
        f.bank[0] = InvObj(RING, 1)
        assertTrue(f.diaries.lostRewards(f.access(), LUMBRIDGE).isEmpty())

        f.bank[0] = null
        assertEquals(1, f.diaries.lostRewards(f.access(), LUMBRIDGE).size)
    }

    @Test
    fun `every lamp skill button resolves to a stat`() {
        val stats = (0 until 24).map { lampStat(it) }
        assertTrue(stats.all { it != null && it.startsWith("stat.") }, stats.toString())
        assertEquals(24, stats.toSet().size)
    }

    private fun easyTasks(): List<AchievementDiaryTasksRow> =
        AchievementDiaryTasksRow.all().filter { it.tier.rowId == AchievementDiaryTiersRow.getRow(EASY_TIER).rowId }

    private class Fixture {
        val diaries = AchievementDiaries()
        private val coroutine = GameCoroutine("diary-test")

        @OptIn(InternalApi::class)
        val player =
            Player().apply {
                client = NoopClient
                uuid = 1031L
                slotId = 1
                assignUid()
                coords = CoordGrid(3222, 3218, 0)
                inv = Inventory(checkNotNull(ServerCacheManager.getInventory("inv.inv".asRSCM())), arrayOfNulls(28))
                worn = Inventory(checkNotNull(ServerCacheManager.getInventory("inv.worn".asRSCM())), arrayOfNulls(14))
            }

        fun access() = ProtectedAccess(player, coroutine, ProtectedAccessContextFactory.empty())

        val bank: Inventory
            get() = access().bank

        fun completeEasy() {
            AchievementDiaryTasksRow.all()
                .filter { it.tier.rowId == AchievementDiaryTiersRow.getRow(EASY_TIER).rowId }
                .forEach { diaries.complete(player, it) }
        }
    }

    private object NoopClient : Client<Any, Any> {
        override fun write(message: Any) {}

        override fun close() {}

        override fun read(player: Player) {}

        override fun flush() {}

        override fun flushHighPriority() {}

        override fun unregister(service: Any, player: Player) {}
    }

    companion object {
        private const val LUMBRIDGE = 6
        private const val EASY_TIER = "dbrow.diary_lumbridge_easy"
        private const val RING = "obj.lumbridge_ring_easy"
        private const val LAMP = "obj.diary_easy_lamp"
        private const val COINS = "obj.coins"
        private const val DAGGER = "obj.bronze_dagger"
        private const val COUNT_VARBIT = "varbit.lumbridge_easy_count"
        private const val COMPLETE_VARBIT = "varbit.lumbridge_diary_easy_complete"
        private const val REWARD_VARBIT = "varbit.lumbridge_easy_reward"

        private val restored = mutableListOf<() -> Unit>()

        @OptIn(InternalApi::class)
        @JvmStatic
        @BeforeAll
        fun cache() {
            ServerCacheManager.init(241).close()
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
