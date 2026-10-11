package org.rsmod.content.events.halloween

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
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
import org.rsmod.api.player.events.interact.HeldObjEvents
import org.rsmod.api.player.events.interact.HeldUEvents
import org.rsmod.api.player.events.interact.LocEvents
import org.rsmod.api.player.events.interact.NpcEvents
import org.rsmod.api.player.input.ResumePauseButtonInput
import org.rsmod.api.player.interact.NpcInteractions
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.api.player.protect.clearPendingAction
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.random.DefaultGameRandom
import org.rsmod.api.registry.controller.ControllerRegistry
import org.rsmod.api.registry.loc.LocRegistry
import org.rsmod.api.registry.loc.LocRegistryNormal
import org.rsmod.api.registry.loc.LocRegistryRegion
import org.rsmod.api.registry.npc.NpcRegistry
import org.rsmod.api.registry.region.RegionRegistry
import org.rsmod.api.registry.zone.ZonePlayerActivityBitSet
import org.rsmod.api.registry.zone.ZoneUpdateMap
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.coroutine.GameCoroutine
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.client.Client
import org.rsmod.game.entity.ControllerList
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.InvVirtualStorageHolder
import org.rsmod.game.inv.Inventory
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocShape
import org.rsmod.game.map.LocZoneStorage
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.game.region.RegionListLarge
import org.rsmod.game.region.RegionListSmall
import org.rsmod.game.region.RegionListWorldEntity
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class HalloweenInteractionTest {
    @Test
    fun `the season puts the children out and takes them back in`() {
        val f = Fixture()
        f.world.tick()
        assertEquals(1, f.npcsAt("npc.hw22_costumed_child_1"))
        f.world.tick()
        assertEquals(1, f.npcsAt("npc.hw22_costumed_child_1"))
        f.season.override = false
        f.world.tick()
        assertEquals(0, f.npcsAt("npc.hw22_costumed_child_1"))
    }

    @Test
    fun `the four junk pieces become one charm and nothing else is touched`() {
        val f = Fixture()
        JUNK.forEachIndexed { slot, junk -> f.give(junk, slot) }
        f.give(COINS, 4, count = 50)
        f.useItemOnItem(JUNK[0], JUNK[1])
        JUNK.forEach { assertEquals(0, f.player.inv.count(it), it) }
        assertEquals(1, f.player.inv.count(CHARM))
        assertEquals(50, f.player.inv.count(COINS))
        assertEquals(2, f.player.inv.occupiedSpace())
    }

    @Test
    fun `a missing junk piece leaves every piece in place`() {
        val f = Fixture()
        JUNK.take(3).forEachIndexed { slot, junk -> f.give(junk, slot) }
        f.useItemOnItem(JUNK[0], JUNK[1])
        JUNK.take(3).forEach { assertEquals(1, f.player.inv.count(it), it) }
        assertEquals(0, f.player.inv.count(CHARM))
    }

    @Test
    fun `depositing every treat hands out each gift exactly once`() {
        val f = Fixture()
        f.give(CAULDRON, 0)
        f.player.treats = 720
        f.depositAll()
        assertEquals(0, f.player.treats)
        assertEquals(720, f.player.treatsDeposited)
        assertEquals(1, f.player.inv.count("obj.hw23_reward_cobwebcape"))
        assertEquals(1, f.player.inv.count("obj.hw23_reward_spiderhat"))
        assertEquals(2, f.player.inv.count("obj.set_halloween_mask"))
        assertEquals(2, f.player.inv.count("obj.pumpkin"))
        assertEquals(1, f.player.vars["varbit.emote_trick"])
        for (gift in 1..9) assertEquals(1, f.player.vars["varbit.hw23_reward_received_$gift"], "$gift")
        val before = f.snapshot()

        f.claim()
        assertEquals(before, f.snapshot())
    }

    @Test
    fun `a full pack holds a gift back until there is room and loses nothing`() {
        val f = Fixture()
        f.give(CAULDRON, 0)
        for (slot in 1 until 28) f.give(DAGGER, slot)
        f.player.treats = 100
        f.depositAll()
        assertEquals(0, f.player.treats)
        assertEquals(100, f.player.treatsDeposited)
        assertEquals(0, f.player.inv.count("obj.hw23_reward_cobwebcape"))
        assertEquals(0, f.player.vars["varbit.hw23_reward_received_1"])
        assertEquals(27, f.player.inv.count(DAGGER))

        f.player.inv[27] = null
        f.claim()
        assertEquals(1, f.player.inv.count("obj.hw23_reward_cobwebcape"))
        assertEquals(1, f.player.vars["varbit.hw23_reward_received_1"])

        f.player.inv[f.slotOf("obj.hw23_reward_cobwebcape")] = null
        f.claim()
        assertEquals(0, f.player.inv.count("obj.hw23_reward_cobwebcape"))
    }

    @Test
    fun `changing the cauldron style swaps it in place and needs deposits`() {
        val f = Fixture()
        f.give(CAULDRON, 3)
        f.heldOp4(CAULDRON, 3)
        assertEquals(1, f.player.inv.count(CAULDRON))

        f.player.treatsDeposited = 2000
        f.heldOp4(CAULDRON, 3)
        assertEquals(0, f.player.inv.count(CAULDRON))
        assertEquals(1, f.player.inv.count("obj.hw22_treat_cauldron_1"))
        assertEquals(1, f.player.inv.occupiedSpace())

        f.heldOp4("obj.hw22_treat_cauldron_1", 3)
        assertEquals(1, f.player.inv.count(CAULDRON))
        assertEquals(1, f.player.inv.occupiedSpace())
    }

    @Test
    fun `spider hat styles only cycle through the ones earned at the cauldron`() {
        val f = Fixture()
        f.give(SPIDER_HAT, 0)
        f.heldOp3(SPIDER_HAT, 0)
        assertEquals(1, f.player.inv.count(SPIDER_HAT))

        VarPlayerIntMapSetter.set(f.player, "varbit.hw23_reward_received_5", 1)
        f.heldOp3(SPIDER_HAT, 0)
        assertEquals(1, f.player.inv.count("obj.hw23_reward_spiderhat03"))
        f.heldOp3("obj.hw23_reward_spiderhat03", 0)
        assertEquals(1, f.player.inv.count(SPIDER_HAT))
        assertEquals(1, f.player.inv.occupiedSpace())
    }

    @Test
    fun `the terrifying charm scares up five treats three times then crumbles`() {
        val f = Fixture()
        f.dressUp()
        f.wear(CHARM, QUIVER)
        repeat(3) {
            f.trickOrTreat(f.spawn("npc.man"))
            assertEquals(5 * (it + 1), f.player.treats)
        }
        assertEquals(null, f.player.worn[QUIVER])
        assertEquals(0, f.player.inv.occupiedSpace())
    }

    @Test
    fun `a recently visited villager gives nothing more`() {
        val f = Fixture()
        f.dressUp()
        val man = f.spawn("npc.man")
        f.player.ghostlyPresence = true
        f.trickOrTreat(man)
        assertEquals(5, f.player.treats)
        assertFalse(f.player.ghostlyPresence)

        f.trickOrTreat(man)
        assertEquals(5, f.player.treats)
        assertEquals(0, f.player.inv.occupiedSpace())
    }

    @Test
    fun `nobody hands out treats without a costume`() {
        val f = Fixture()
        f.give(CAULDRON, 0)
        f.player.ghostlyPresence = true
        f.trickOrTreat(f.spawn("npc.man"))
        assertEquals(0, f.player.treats)
        assertTrue(f.player.ghostlyPresence)
    }

    private class Fixture {
        val events = EventBus()
        private val client = RecordingClient()
        private val coroutine = GameCoroutine("halloween-test")
        private var result: Result<Unit>? = null
        private val random = DefaultGameRandom(7L)
        private val collision = CollisionFlagMap()
        private val clock = MapClock(100)
        private val npcs = NpcList()
        private val context =
            ProtectedAccessContextFactory.empty()
                .copy(
                    getEventBus = { events },
                    getAlignment = { TextAlignment() },
                    getNpcInteractions = { NpcInteractions(events) },
                    getRandom = { random },
                    getCollision = { collision },
                )

        @OptIn(InternalApi::class)
        val player =
            Player().apply {
                this.client = this@Fixture.client
                uuid = 1031L
                slotId = 1
                assignUid()
                coords = CoordGrid(3196, 3430, 0)
                currentMapClock = 100
                processedMapClock = 100
                inv = Inventory(checkNotNull(ServerCacheManager.getInventory("inv.inv".asRSCM())), arrayOfNulls(28))
                worn = Inventory(checkNotNull(ServerCacheManager.getInventory("inv.worn".asRSCM())), arrayOfNulls(14))
            }

        private val npcRepo: NpcRepository
        private val locRepo: LocRepository
        val season = HalloweenSeason().apply { override = true }
        val world: HalloweenWorld

        fun npcsAt(type: String): Int = npcs.count { it != null && it.id == type.asRSCM(RSCMType.NPC) }

        init {
            val scripts = ScriptContext(events, CheatCommandMap(), EngineQueueCache())
            val updates = ZoneUpdateMap()
            val storage = LocZoneStorage()
            val normal = LocRegistryNormal(updates, collision, storage)
            val npcRegistry = NpcRegistry(npcs, collision, events)
            val regions =
                RegionRegistry(
                RegionListSmall(),
                RegionListLarge(),
                RegionListWorldEntity(),
                normal,
                collision,
                storage,
                npcRegistry,
                ControllerRegistry(clock, ControllerList()),
                ZonePlayerActivityBitSet(),
            )
            npcRepo = NpcRepository(clock, npcRegistry, npcs)
            val locRegistry =
                LocRegistry(storage, normal, LocRegistryRegion(updates, collision, storage, regions))
            locRepo = LocRepository(clock, locRegistry, regions)
            world = HalloweenWorld(season, locRepo, npcRepo, clock, random)
            with(TrickOrTreatScript(season, npcRepo)) { scripts.startup() }
            with(GreatCauldronScript(world)) { scripts.startup() }
            with(HalloweenItemsScript(season)) { scripts.startup() }
            with(KeyTreatersScript(season)) { scripts.startup() }
            with(CostumedChildrenScript()) { scripts.startup() }
            with(GenerousTreaterScript(season, world)) { scripts.startup() }
            with(HalloweenWorldScript(season, world)) { scripts.startup() }
        }

        fun access() = ProtectedAccess(player, coroutine, context)

        fun give(obj: String, slot: Int, count: Int = 1) {
            player.inv[slot] = InvObj(obj, count)
        }

        fun wear(obj: String, slot: Int) {
            player.worn[slot] = InvObj(obj, 1)
        }

        fun dressUp() {
            wear("obj.hw22_witch_hat", HAT)
            wear("obj.hw22_witch_torso", BODY)
            wear("obj.hw22_witch_robes", LEGS)
            wear(CAULDRON, SHIELD)
        }

        fun spawn(type: String): Npc {
            val npc = Npc(type, player.coords.translateZ(1))
            npcRepo.add(npc, Int.MAX_VALUE)
            return npc
        }

        fun slotOf(obj: String): Int = (0 until 28).first { player.inv[it]?.id == obj.asRSCM() }

        fun snapshot(): List<Pair<Int, Int>> =
            player.inv.objs.mapNotNull { it?.let { obj -> obj.id to obj.count } }.sortedBy { it.first }

        fun trickOrTreat(npc: Npc) {
            start { assertTrue(events.publish(this, NpcEvents.Op4(npc))) }
            finish()
        }

        fun depositAll() {
            locOp(1)
            finish(listOf(1))
        }

        fun claim() {
            locOp(3)
            finish()
        }

        private fun locOp(op: Int) {
            val type = checkNotNull(ServerCacheManager.getObject(GREAT_CAULDRON.asRSCM(RSCMType.LOC)))
            val loc =
                locRepo.add(CoordGrid(3195, 3433, 0), GREAT_CAULDRON, Int.MAX_VALUE, LocAngle.West, LocShape.CentrepieceStraight)
            val bound = BoundLocInfo(loc, type)
            val event =
                when (op) {
                    1 -> LocEvents.Op1(bound, bound, type)
                    else -> LocEvents.Op3(bound, bound, type)
                }
            start { assertTrue(events.publish(this, event)) }
        }

        fun heldOp3(obj: String, slot: Int) {
            val type = checkNotNull(ServerCacheManager.getItem(obj.asRSCM()))
            start { assertTrue(events.publish(this, HeldObjEvents.Op3(slot, InvObj(obj, 1), type, player.inv))) }
            finish()
        }

        fun heldOp4(obj: String, slot: Int) {
            val type = checkNotNull(ServerCacheManager.getItem(obj.asRSCM()))
            start { assertTrue(events.publish(this, HeldObjEvents.Op4(slot, InvObj(obj, 1), type, player.inv))) }
            finish()
        }

        fun useItemOnItem(first: String, second: String) {
            val firstType = checkNotNull(ServerCacheManager.getItem(first.asRSCM()))
            val secondType = checkNotNull(ServerCacheManager.getItem(second.asRSCM()))
            start {
                val event = HeldUEvents.Type(firstType, slotOf(first), secondType, slotOf(second))
                assertTrue(events.publish(this, event))
            }
            finish()
        }

        private fun start(block: suspend ProtectedAccess.() -> Unit) {
            while (player.isDelayed) {
                player.currentMapClock++
                player.processedMapClock = player.currentMapClock
            }
            player.clearPendingAction(events)
            result = null
            player.activeCoroutine = coroutine
            val body: suspend () -> Unit = { access().block() }
            body.startCoroutine(
                object : Continuation<Unit> {
                    override val context = EmptyCoroutineContext

                    override fun resumeWith(result: Result<Unit>) {
                        this@Fixture.result = result
                    }
                }
            )
            result?.getOrThrow()
        }

        fun finish(options: List<Int> = emptyList()) {
            val selections = options.iterator()
            repeat(300) {
                if (coroutine.isIdle) return
                advance(selections)
            }
            fail<Unit>("Interaction did not finish: ${output()}")
        }

        private fun advance(options: Iterator<Int>) {
            if (coroutine.isAwaiting(ResumePauseButtonInput::class)) {
                val input =
                    when {
                        player.ui.containsModal("interface.chatmenu") ->
                            ResumePauseButtonInput(
                                "component.chatmenu:options",
                                if (options.hasNext()) options.next() else 1,
                            )
                        player.ui.containsModal("interface.objectbox") ->
                            ResumePauseButtonInput("component.objectbox:universe", -1)
                        else -> {
                            val parent =
                                listOf("chat_left", "chat_right", "messagebox").firstOrNull {
                                    player.ui.containsModal("interface.$it")
                                } ?: error("Unknown dialogue: ${output()}")
                            ResumePauseButtonInput("component.$parent:continue", -1)
                        }
                    }
                coroutine.resumeWith(input)
            } else {
                player.currentMapClock++
                player.processedMapClock = player.currentMapClock
                clock.cycle++
                coroutine.advance()
            }
            result?.getOrThrow()
        }

        fun output() = client.messages.joinToString("\n")
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
        private const val CAULDRON = "obj.hw22_treat_cauldron_0"
        private const val CHARM = "obj.hw22_trick_charm"
        private const val SPIDER_HAT = "obj.hw23_reward_spiderhat"
        private const val COINS = "obj.coins"
        private const val DAGGER = "obj.bronze_dagger"
        private const val GREAT_CAULDRON = "loc.hw22_communitygoal_0"
        private val JUNK =
            listOf("obj.hw22_trick_banana", "obj.hw22_trick_sock", "obj.hw22_trick_egg", "obj.hw22_trick_wool")

        private const val HAT = 0
        private const val BODY = 4
        private const val SHIELD = 5
        private const val LEGS = 7
        private const val QUIVER = 13

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
