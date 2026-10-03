package org.rsmod.content.quest.area.falador.doricsquest

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.InventoryServerType
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.annotations.InternalApi
import org.rsmod.api.inv.storage.PlayerItemStorage
import org.rsmod.api.invtx.InvTransactionsScript
import org.rsmod.api.player.dialogue.align.TextAlignment
import org.rsmod.api.player.events.interact.LocEvents
import org.rsmod.api.player.events.interact.NpcEvents
import org.rsmod.api.player.input.ResumePauseButtonInput
import org.rsmod.api.player.interact.NpcInteractions
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.api.player.protect.clearPendingAction
import org.rsmod.api.registry.obj.ObjRegistry
import org.rsmod.api.registry.zone.ZoneUpdateMap
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.content.quest.area.falador.doricsquest.DoricsQuest.Companion.BRONZE_PICKAXE
import org.rsmod.content.quest.area.falador.doricsquest.DoricsQuest.Companion.CLAY
import org.rsmod.content.quest.area.falador.doricsquest.DoricsQuest.Companion.COINS
import org.rsmod.content.quest.area.falador.doricsquest.DoricsQuest.Companion.COPPER_ORE
import org.rsmod.content.quest.area.falador.doricsquest.DoricsQuest.Companion.IRON_ORE
import org.rsmod.content.quest.area.falador.doricsquest.DoricsQuest.Companion.STAGE_COMPLETE
import org.rsmod.content.quest.area.falador.doricsquest.DoricsQuest.Companion.STAGE_STARTED
import org.rsmod.content.quest.area.falador.doricsquest.npcs.Doric
import org.rsmod.content.quest.area.falador.doricsquest.npcs.DoricDialogue
import org.rsmod.content.quest.manager.QuestRequirementMode
import org.rsmod.content.quest.manager.QuestRequirementPolicy
import org.rsmod.content.quest.manager.QuestRequirements
import org.rsmod.coroutine.GameCoroutine
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.client.Client
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.InvVirtualStorageHolder
import org.rsmod.game.inv.Inventory
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocEntity
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class DoricsQuestInteractionTest {
    @Test
    fun `asking to use the anvils starts the quest and hands over a bronze pickaxe`() {
        val f = Fixture()
        f.talk(1, 1, 2)
        assertEquals(STAGE_STARTED, f.stage())
        assertEquals(STAGE_STARTED, f.player.vars["varp.doricquest"])
        assertEquals(1, f.player.inv.count(BRONZE_PICKAXE))
        assertEquals(0, f.player.vars["varp.qp"])
        assertTrue(f.said("Could you get me 6 clay, 4 copper ore, and 2 iron ore"))
    }

    @Test
    fun `asking about the whetstone offers the same quest`() {
        val f = Fixture()
        f.talk(2, 1, 2)
        assertEquals(STAGE_STARTED, f.stage())
        assertTrue(f.said("The whetstone is for more advanced smithing"))
    }

    @Test
    fun `declining leaves the quest unstarted`() {
        val f = Fixture()
        f.talk(1, 2)
        assertEquals(0, f.stage())
        assertEquals(0, f.player.inv.count(BRONZE_PICKAXE))
        assertTrue(f.said("That is your choice. Nice to meet you anyway."))
    }

    @Test
    fun `small talk never starts the quest`() {
        for (option in 3..5) {
            val f = Fixture()
            f.talk(option, 1)
            assertEquals(0, f.stage(), "option $option")
        }
    }

    @Test
    fun `a miner below level 15 is told about the iron ore`() {
        val f = Fixture(miningLevel = 1)
        f.talk(1, 1, 1)
        assertTrue(f.said("But I'm not a good enough miner to get iron ore."))
        val g = Fixture(miningLevel = 15)
        g.talk(1, 1, 1)
        assertFalse(g.said("not a good enough miner"))
    }

    @Test
    fun `partial materials are not taken`() {
        val f = Fixture(STAGE_STARTED)
        f.give(CLAY, 6)
        f.give(COPPER_ORE, 4)
        f.give(IRON_ORE, 1)
        f.talk(2)
        assertEquals(STAGE_STARTED, f.stage())
        assertEquals(6, f.player.inv.count(CLAY))
        assertEquals(1, f.player.inv.count(IRON_ORE))
        assertTrue(f.said("Sorry, I don't have them all yet."))
    }

    @Test
    fun `handing in the materials completes the quest with its rewards`() {
        val f = Fixture(STAGE_STARTED)
        f.give(CLAY, 7)
        f.give(COPPER_ORE, 4)
        f.give(IRON_ORE, 2)
        f.talk()
        f.assertComplete()
        assertEquals(1, f.player.inv.count(CLAY))
        assertTrue(f.said("I have everything you need!"))
        assertTrue(f.said("You hand the clay, copper, and iron to Doric."))
        assertTrue(f.player.ui.containsModal("interface.questscroll"))
    }

    @Test
    fun `bringing the exact materials before starting completes it straight away`() {
        val f = Fixture()
        f.materials()
        f.talk(1, 1)
        f.assertComplete()
        assertEquals(1, f.player.inv.count(BRONZE_PICKAXE))
        assertTrue(f.said("out of pure coincidence"))
        assertTrue(f.said("In fact, in the exact quantities too!"))
    }

    @Test
    fun `surplus materials skip the exact quantities line`() {
        val f = Fixture()
        f.materials()
        f.give(CLAY, 1)
        f.talk(1, 1)
        f.assertComplete()
        assertFalse(f.said("in the exact quantities too"))
    }

    @Test
    fun `Doric chats about metalworking after the quest`() {
        val f = Fixture(STAGE_COMPLETE)
        f.talk()
        assertTrue(f.said("how is your metalworking coming along?"))
        assertEquals(STAGE_COMPLETE, f.stage())
    }

    @Test
    fun `the journal strikes each material once enough is carried`() {
        val f = Fixture(STAGE_STARTED)
        f.give(CLAY, 6)
        f.give(COPPER_ORE, 3)
        val journal = f.journal()
        assertTrue(journal.contains("<str>6 clay</str>"), journal)
        assertFalse(journal.contains("<str>4 copper ore</str>"), journal)
        assertTrue(journal.contains("Dwarven Mine"), journal)
        f.give(COPPER_ORE, 1)
        f.give(IRON_ORE, 2)
        assertTrue(f.journal().contains("I have everything Doric asked for."))
    }

    @Test
    fun `Doric objects to his anvils being used before the quest`() =
        respectingProgress {
            val f = Fixture()
            assertFalse(f.mayUseAnvil(1, 2))
            assertTrue(f.said("Hey, who said you could use that?"))
            assertEquals(0, f.stage())

            assertFalse(f.mayUseAnvil(1, 1, 2))
            assertEquals(STAGE_STARTED, f.stage())
            assertEquals(1, f.player.inv.count(BRONZE_PICKAXE))
        }

    @Test
    fun `the anvils stay closed mid-quest and open once it is done`() {
        val f = Fixture(STAGE_STARTED)
        assertFalse(f.mayUseAnvil(2))
        assertTrue(f.said("Have you got my materials yet, traveller?"))
        f.materials()
        assertFalse(f.mayUseAnvil())
        f.assertComplete()
        assertTrue(f.mayUseAnvil())
    }

    @Test
    fun `an unstarted quest the policy counts complete leaves the anvils open`() {
        val f = Fixture()
        assertTrue(f.mayUseAnvil())
        assertFalse(f.said("who said you could use that"))
    }

    @Test
    fun `other anvils are never gated`() =
        respectingProgress {
            val f = Fixture()
            assertTrue(f.mayUseAnvil(anvil = "loc.anvil"))
        }

    @Test
    fun `the whetstone needs asking first`() =
        respectingProgress {
            val f = Fixture()
            f.whetstone()
            assertTrue(f.said("You should probably ask before using that."))
            val g = Fixture(STAGE_COMPLETE)
            g.whetstone()
            assertFalse(g.said("You should probably ask"))
        }

    private fun respectingProgress(block: () -> Unit) {
        val previous = QuestRequirements.activePolicy()
        QuestRequirements.install(QuestRequirementPolicy(QuestRequirementMode.RespectProgress))
        try {
            block()
        } finally {
            QuestRequirements.install(previous)
        }
    }

    private class Fixture(stage: Int = 0, miningLevel: Int = 1) {
        private val events = EventBus()
        private val client = RecordingClient()
        private val coroutine = GameCoroutine("dorics-quest-test")
        private var result: Result<Unit>? = null
        private val picks = ArrayDeque<Int>()
        private val context =
            ProtectedAccessContextFactory.empty()
                .copy(
                    getEventBus = { events },
                    getAlignment = { TextAlignment() },
                    getNpcInteractions = { NpcInteractions(events) },
                )

        @OptIn(InternalApi::class)
        val player =
            Player().apply {
                this.client = this@Fixture.client
                uuid = 3893L
                observerUUID = 3893L
                slotId = 1
                assignUid()
                coords = CoordGrid(2952, 3450, 0)
                currentMapClock = 100
                processedMapClock = 100
                inv = Inventory(InventoryServerType(size = 28, flags = 0), arrayOfNulls(28))
                worn = Inventory(InventoryServerType(size = 14, flags = 0), arrayOfNulls(14))
                statMap.setBaseLevel("stat.mining", miningLevel.toByte())
                statMap.setCurrentLevel("stat.mining", miningLevel.toByte())
            }

        val dorics = DoricsQuest()
        private val dialogue =
            DoricDialogue(dorics, ObjRepository(MapClock(100), ObjRegistry(ZoneUpdateMap())))
        private val anvils = DoricsAnvils(dorics, dialogue)

        init {
            val scripts = ScriptContext(events, CheatCommandMap(), EngineQueueCache())
            with(dorics) { scripts.startup() }
            with(Doric(dorics, dialogue)) { scripts.startup() }
            if (stage > 0) dorics.quest.jumpToStage(player, stage)
        }

        fun access() = ProtectedAccess(player, coroutine, context)

        fun stage(): Int = dorics.stage(player)

        fun journal(): String = dorics.questLog(access())

        fun give(obj: String, count: Int) {
            repeat(count) { player.inv[player.inv.indexOfFirst { it == null }] = InvObj(obj, 1) }
        }

        fun materials() {
            give(CLAY, 6)
            give(COPPER_ORE, 4)
            give(IRON_ORE, 2)
        }

        fun talk(vararg options: Int) {
            picks += options.toList()
            val npc = Npc(DoricDialogue.DORIC, player.coords.translateZ(1))
            dispatch { assertTrue(events.publish(this, NpcEvents.Op1(npc))) }
        }

        fun mayUseAnvil(vararg options: Int, anvil: String = DoricsAnvils.DORICS_ANVIL): Boolean {
            picks += options.toList()
            val type = checkNotNull(ServerCacheManager.getObject(anvil.asRSCM(RSCMType.LOC)))
            var allowed = false
            dispatch { allowed = with(anvils) { mayUse(type) } }
            return allowed
        }

        fun whetstone() {
            val type = checkNotNull(ServerCacheManager.getObject("loc.devious_whetstone".asRSCM(RSCMType.LOC)))
            val loc = BoundLocInfo(LocInfo(0, CoordGrid(2953, 3451, 0), LocEntity(type.id, 10, 1)), type)
            dispatch { assertTrue(events.publish(this, LocEvents.Op1(loc, loc, type))) }
        }

        fun said(text: String): Boolean = output().contains(text)

        fun assertComplete() {
            assertEquals(STAGE_COMPLETE, stage())
            assertEquals(STAGE_COMPLETE, player.vars["varp.doricquest"])
            assertEquals(1, player.vars["varp.qp"])
            assertEquals(1300, player.statMap.getXP("stat.mining"))
            assertEquals(180, player.inv.count(COINS))
            assertEquals(0, player.inv.count(COPPER_ORE))
            assertEquals(0, player.inv.count(IRON_ORE))
        }

        private fun dispatch(block: suspend ProtectedAccess.() -> Unit) {
            player.clearPendingAction(events)
            result = null
            player.activeCoroutine = coroutine
            val access = access()
            val start: suspend () -> Unit = { access.block() }
            start.startCoroutine(
                object : Continuation<Unit> {
                    override val context = EmptyCoroutineContext

                    override fun resumeWith(result: Result<Unit>) {
                        this@Fixture.result = result
                    }
                }
            )
            result?.getOrThrow()
            repeat(300) {
                if (coroutine.isIdle) {
                    picks.clear()
                    return
                }
                step()
                result?.getOrThrow()
            }
            fail<Unit>("Interaction did not finish: ${output()}")
        }

        private fun step() {
            if (coroutine.isAwaiting(ResumePauseButtonInput::class)) {
                val parent =
                    listOf("chat_left", "chat_right", "messagebox", "chatmenu", "objectbox")
                        .firstOrNull { player.ui.containsModal("interface.$it") }
                        ?: error("Unknown dialogue: ${output()}")
                val input =
                    when (parent) {
                        "chatmenu" ->
                            ResumePauseButtonInput(
                                "component.chatmenu:options",
                                picks.removeFirstOrNull() ?: 1,
                            )
                        "objectbox" -> ResumePauseButtonInput("component.objectbox:universe", -1)
                        else -> ResumePauseButtonInput("component.$parent:continue", -1)
                    }
                coroutine.resumeWith(input)
            } else {
                player.currentMapClock++
                player.processedMapClock = player.currentMapClock
                coroutine.advance()
            }
        }

        fun output() = client.messages.joinToString("\n").replace("<br>", " ")
    }

    private class RecordingClient : Client<Any, Any> {
        val messages = mutableListOf<Any>()

        override fun write(message: Any) {
            messages += message
        }

        override fun close() {}

        override fun read(player: Player) {}

        override fun flush() {}

        override fun flushHighPriority() {}

        override fun unregister(service: Any, player: Player) {}
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
