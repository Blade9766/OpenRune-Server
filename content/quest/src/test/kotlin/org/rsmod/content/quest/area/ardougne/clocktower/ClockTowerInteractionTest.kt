package org.rsmod.content.quest.area.ardougne.clocktower

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
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
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.inv.storage.PlayerItemStorage
import org.rsmod.api.invtx.InvTransactionsScript
import org.rsmod.api.player.dialogue.align.TextAlignment
import org.rsmod.api.player.events.interact.NpcEvents
import org.rsmod.api.player.events.interact.ObjEvents
import org.rsmod.api.player.hook.PlayerTeleportValidator
import org.rsmod.api.player.input.ResumePauseButtonInput
import org.rsmod.api.player.interact.LocInteractions
import org.rsmod.api.player.interact.LocUInteractions
import org.rsmod.api.player.interact.NpcInteractions
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.api.player.protect.clearPendingAction
import org.rsmod.api.registry.controller.ControllerRegistry
import org.rsmod.api.registry.loc.LocRegistry
import org.rsmod.api.registry.loc.LocRegistryNormal
import org.rsmod.api.registry.loc.LocRegistryRegion
import org.rsmod.api.registry.npc.NpcRegistry
import org.rsmod.api.registry.obj.ObjRegistry
import org.rsmod.api.registry.region.RegionRegistry
import org.rsmod.api.registry.zone.ZonePlayerActivityBitSet
import org.rsmod.api.registry.zone.ZoneUpdateMap
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.route.BoundValidator
import org.rsmod.content.quest.area.ardougne.clocktower.ClockTowerQuest.Companion.COINS
import org.rsmod.content.quest.area.ardougne.clocktower.ClockTowerQuest.Companion.KOJO
import org.rsmod.content.quest.area.ardougne.clocktower.ClockTowerQuest.Companion.STAGE_COMPLETE
import org.rsmod.content.quest.area.ardougne.clocktower.ClockTowerQuest.Companion.STAGE_STARTED
import org.rsmod.content.quest.area.ardougne.clocktower.RatCage.Gate
import org.rsmod.content.quest.area.ardougne.clocktower.RatCage.Lever
import org.rsmod.content.quest.area.ardougne.clocktower.npcs.BrotherKojo
import org.rsmod.coroutine.GameCoroutine
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.area.AreaIndex
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.client.Client
import org.rsmod.game.entity.ControllerList
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.util.EntityFaceAngle
import org.rsmod.game.interact.InteractionOp
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.InvVirtualStorageHolder
import org.rsmod.game.inv.Inventory
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocEntity
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.loc.LocShape
import org.rsmod.game.map.LocZoneStorage
import org.rsmod.game.obj.Obj
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.game.region.RegionListLarge
import org.rsmod.game.region.RegionListSmall
import org.rsmod.game.region.RegionListWorldEntity
import org.rsmod.game.seq.EntitySeq
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.loc.LocLayerConstants

/**
 * Drives Clock Tower's real scripts through the event bus: Kojo's offer and every progress line,
 * one cog at a time, the hot black cog, wrong spindles, the cage levers and gates, the poisoned
 * trough and the far gate, the payment, and a reset.
 */
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class ClockTowerInteractionTest {

    @Test fun `the whole quest from Kojo's offer to the payment`() {
        val f = Fixture()
        f.choose(1)
        f.talk()
        assertEquals(STAGE_STARTED, f.stage())
        assertTrue(f.said("strange beasts"))

        f.talk()
        assertTrue(f.said("Place one cog on a pole"))

        val replies = listOf("Come see me when you've done the other three.", "Two to go.", "One left.")
        val order = listOf(Cog.RED, Cog.BLUE, Cog.WHITE, Cog.BLACK)
        for ((index, cog) in order.withIndex()) {
            if (cog == Cog.BLACK) f.give("obj.bucket_water")
            f.take(cog)
            assertEquals(1, f.count(cog.obj), cog.name)
            f.useOnSpindle(cog, cog.spindle)
            assertEquals(0, f.count(cog.obj))
            assertTrue(f.clockTower.isPlaced(f.player, cog))
            assertTrue(f.said("The cog fits perfectly."))
            if (index < replies.size) {
                f.talk()
                assertTrue(f.said(replies[index]), "after the ${cog.label} cog")
                assertEquals(STAGE_STARTED, f.stage())
            }
        }
        assertTrue(f.journal().contains("I should tell Brother Kojo"))

        f.talk()
        assertEquals(STAGE_COMPLETE, f.stage())
        assertEquals(500, f.count(COINS))
        assertEquals(1, f.player.vars["varp.qp"])
        assertTrue(f.player.ui.containsModal("interface.questscroll"))

        f.talk()
        assertTrue(f.said("It's just like new."))
        assertEquals(500, f.count(COINS), "the reward is paid once")
    }

    @Test fun `declining leaves the quest unstarted`() {
        val f = Fixture()
        f.choose(2)
        f.talk()
        assertEquals(0, f.stage())
        assertTrue(f.said("change your mind"))
    }

    @Test fun `only one cog can be carried at a time`() {
        val f = Fixture(STAGE_STARTED)
        f.take(Cog.RED)
        val blue = f.take(Cog.BLUE)
        assertEquals(1, f.count(Cog.RED.obj))
        assertEquals(0, f.count(Cog.BLUE.obj))
        assertTrue(f.said("too heavy to carry more than one"))
        assertTrue(f.onFloor(blue))
    }

    @Test fun `the black cog needs water or cold gloves`() {
        val f = Fixture(STAGE_STARTED)
        val hot = f.take(Cog.BLACK)
        assertEquals(0, f.count(Cog.BLACK.obj))
        assertTrue(f.said("red hot"))
        assertTrue(f.onFloor(hot))

        f.give("obj.jug_water")
        f.take(Cog.BLACK)
        assertEquals(1, f.count(Cog.BLACK.obj))
        assertEquals(1, f.count("obj.jug_empty"))
        assertTrue(f.said("You pour water over the cog"))

        val g = Fixture(STAGE_STARTED)
        g.wear("obj.ice_gloves")
        g.take(Cog.BLACK)
        assertEquals(1, g.count(Cog.BLACK.obj))
        assertFalse(g.said("pour water"))
    }

    @Test fun `a cog only fits the broken spindle of its own colour`() {
        val f = Fixture(STAGE_STARTED)
        f.give(Cog.RED.obj)
        f.useOnSpindle(Cog.RED, Cog.BLUE.spindle)
        assertTrue(f.said("The red cog doesn't fit on this spindle."))
        f.useOnSpindle(Cog.RED, Cog.RED.fittedSpindle)
        assertTrue(f.said("already has a cog"))
        assertEquals(1, f.count(Cog.RED.obj))
        assertFalse(f.clockTower.isPlaced(f.player, Cog.RED))

        val unstarted = Fixture()
        unstarted.give(Cog.RED.obj)
        unstarted.useOnSpindle(Cog.RED, Cog.RED.spindle)
        assertEquals(1, unstarted.count(Cog.RED.obj))
        assertFalse(unstarted.clockTower.isPlaced(unstarted.player, Cog.RED))
    }

    @Test fun `a duplicate cog is kept once its spindle is done, and none can be taken after the quest`() {
        val f = Fixture(STAGE_STARTED)
        f.take(Cog.RED)
        f.useOnSpindle(Cog.RED, Cog.RED.spindle)
        f.take(Cog.RED)
        f.useOnSpindle(Cog.RED, Cog.RED.spindle)
        assertEquals(1, f.count(Cog.RED.obj))
        assertTrue(f.said("already fitted a cog"))

        val done = Fixture(STAGE_COMPLETE)
        val cog = done.take(Cog.WHITE)
        assertEquals(0, done.count(Cog.WHITE.obj))
        assertTrue(done.onFloor(cog))
    }

    @Test fun `the levers open the cage only while both are up`() {
        val f = Fixture(STAGE_STARTED)
        assertTrue(f.gateClosed(Gate.OUTER))
        assertFalse(f.gateClosed(Gate.INNER))

        f.pull(Lever.WEST)
        assertTrue(f.hasLoc(Lever.WEST.pulledLoc, Lever.WEST.coords))
        assertFalse(f.gateClosed(Gate.OUTER))
        assertTrue(f.said("You pull the lever up."))

        f.pull(Lever.EAST)
        assertTrue(f.hasLoc(Lever.EAST.pulledLoc, Lever.EAST.coords))
        assertTrue(f.gateClosed(Gate.INNER))
        assertTrue(f.said("You pull the lever down."))

        f.pull(Lever.EAST, pulled = true)
        assertTrue(f.hasLoc(Lever.EAST.startLoc, Lever.EAST.coords))
        assertFalse(f.gateClosed(Gate.INNER))
        assertFalse(f.gateClosed(Gate.OUTER))

        f.pull(Lever.WEST, pulled = true)
        assertTrue(f.gateClosed(Gate.OUTER))

        f.openGate(Gate.OUTER)
        assertTrue(f.said("doesn't seem to open from here"))
        assertTrue(f.gateClosed(Gate.OUTER))
    }

    @Test fun `poisoning the trough kills the rats and frees the far gate`() {
        val f = Fixture(STAGE_STARTED)
        f.at(CoordGrid(2580, 9656, 0))
        f.throughFarGate()
        assertTrue(f.said("This door does not seem to be openable."))
        assertEquals(CoordGrid(2580, 9656, 0), f.player.coords)

        val rats = RatCage.RATS.mapIndexed { i, type -> f.spawn(type, CoordGrid(2582 + i, 9657, 0)) }
        val outside = f.spawn(RatCage.RATS[0], CoordGrid(2600, 9657, 0))
        f.give(RatCage.RAT_POISON)
        f.locU(RatCage.TROUGH, CoordGrid(2586, 9654, 0), RatCage.RAT_POISON)
        assertEquals(0, f.count(RatCage.RAT_POISON))
        assertTrue(f.said("The rats swarm towards the poisoned food..."))
        assertTrue(f.said("They seem to be dying."))
        assertTrue(f.clockTower.ratsPoisoned(f.player))
        assertTrue(rats.none { it.isVisible }, "every rat in the cage dies")
        assertTrue(outside.isVisible, "rats outside the cage are untouched")

        f.throughFarGate()
        assertTrue(f.said("shaken the door loose"))
        assertEquals(CoordGrid(2578, 9656, 0), f.player.coords)
    }

    @Test fun `poison is only used on the trough during the quest`() {
        val f = Fixture()
        f.give(RatCage.RAT_POISON)
        f.locU(RatCage.TROUGH, CoordGrid(2586, 9654, 0), RatCage.RAT_POISON)
        assertEquals(1, f.count(RatCage.RAT_POISON))
        assertFalse(f.clockTower.ratsPoisoned(f.player))
    }

    @Test fun `Kojo holds the coins for a full pack`() {
        val f = Fixture(STAGE_STARTED)
        for (cog in Cog.entries) f.clockTower.place(f.player, cog)
        f.fill()
        f.talk()
        assertEquals(STAGE_STARTED, f.stage())
        assertTrue(f.said("no room for the coins"))
        f.player.inv[0] = null
        f.talk()
        assertEquals(STAGE_COMPLETE, f.stage())
        assertEquals(500, f.count(COINS))
    }

    @Test fun `resetting the quest clears every cog and the poisoned rats`() {
        val f = Fixture(STAGE_STARTED)
        for (cog in Cog.entries) f.clockTower.place(f.player, cog)
        f.clockTower.poisonRats(f.player)
        f.clockTower.quest.resetQuest(f.player)
        assertEquals(0, f.stage())
        assertEquals(0, f.clockTower.placedCount(f.player))
        assertFalse(f.clockTower.ratsPoisoned(f.player))
    }

    private class Fixture(stage: Int = 0) {
        val events = EventBus()
        private val client = RecordingClient()
        private val collision = CollisionFlagMap()
        private val npcs = NpcList()
        private val coroutine = GameCoroutine("clock-tower-test")
        private var result: Result<Unit>? = null
        private val context = ProtectedAccessContextFactory.empty().copy(
            getEventBus = { events }, getAlignment = { TextAlignment() },
            getCollision = { collision }, getNpcList = { npcs },
            getNpcInteractions = { NpcInteractions(events) },
            getTeleportValidator = { PlayerTeleportValidator(emptySet()) },
            getAreaChecker = { AreaChecker(regions, AreaIndex()) },
        )
        private lateinit var regions: RegionRegistry
        private val clock = MapClock(100)
        private val npcRepo: NpcRepository
        private val locRepo: LocRepository
        private val objRegistry = ObjRegistry(ZoneUpdateMap())
        private val objRepo = ObjRepository(clock, objRegistry)
        private val picks = ArrayDeque<Int>()
        private val locU = LocUInteractions::class.java.getDeclaredConstructor(EventBus::class.java)
            .apply { isAccessible = true }.newInstance(events)

        @OptIn(InternalApi::class)
        val player = Player().apply {
            this.client = this@Fixture.client
            uuid = 2902L
            observerUUID = 2902L
            slotId = 1
            assignUid()
            coords = KOJO_TILE
            currentMapClock = 100
            processedMapClock = 100
            pendingSequence = EntitySeq.NULL
            pendingFaceAngle = EntityFaceAngle.NULL
            inv = Inventory(InventoryServerType(size = 28, flags = 0), arrayOfNulls(28))
            worn = Inventory(InventoryServerType(size = 14, flags = 0), arrayOfNulls(14))
        }

        val clockTower = ClockTowerQuest()

        init {
            val updates = ZoneUpdateMap()
            val storage = LocZoneStorage()
            val normal = LocRegistryNormal(updates, collision, storage)
            val npcRegistry = NpcRegistry(npcs, collision, events)
            regions = RegionRegistry(RegionListSmall(), RegionListLarge(), RegionListWorldEntity(),
                normal, collision, storage, npcRegistry,
                ControllerRegistry(clock, ControllerList()), ZonePlayerActivityBitSet())
            locRepo = LocRepository(clock, LocRegistry(storage, normal,
                LocRegistryRegion(updates, collision, storage, regions)), regions)
            npcRepo = NpcRepository(clock, npcRegistry, npcs)
            for ((x0, z0, level) in listOf(Triple(2560, 3232, 0), Triple(2560, 3232, 1), Triple(2560, 3232, 2), Triple(2560, 9632, 0))) {
                for (x in x0..x0 + 63 step 8) for (z in z0..z0 + 31 step 8) collision.allocateIfAbsent(x, z, level)
            }
            for (lever in Lever.entries) {
                locRepo.add(lever.coords, lever.startLoc, Int.MAX_VALUE, LocAngle.South, LocShape.WallDecorStraightNoOffset)
            }
            locRepo.add(Gate.OUTER.coords, Gate.OUTER.closedLoc, Int.MAX_VALUE, LocAngle.East, LocShape.WallStraight)
            locRepo.add(Gate.INNER.coords, RatCage.OPEN_GATE, Int.MAX_VALUE, LocAngle.South, LocShape.WallStraight)
            val scripts = ScriptContext(events, CheatCommandMap(), EngineQueueCache())
            with(clockTower) { scripts.startup() }
            with(BrotherKojo(clockTower)) { scripts.startup() }
            with(ClockTowerCogs(clockTower, objRepo)) { scripts.startup() }
            with(RatCage(clockTower, locRepo, npcRepo, npcs)) { scripts.startup() }
            if (stage > 0) clockTower.quest.jumpToStage(player, stage)
        }

        fun access() = ProtectedAccess(player, coroutine, context)

        fun stage(): Int = clockTower.stage(player)

        fun journal(): String = clockTower.questLog(access())

        fun choose(vararg options: Int) {
            picks += options.toList()
        }

        fun give(obj: String, count: Int = 1) {
            repeat(count) {
                val slot = player.inv.indexOfFirst { it == null }
                player.inv[slot] = InvObj(obj, 1)
            }
        }

        fun wear(obj: String) {
            val type = checkNotNull(ServerCacheManager.getItem(obj.asRSCM()))
            player.worn[type.wearpos1] = InvObj(obj, 1)
        }

        fun fill() {
            while (player.inv.freeSpace() > 0) give("obj.bronze_dagger")
        }

        fun count(obj: String): Int = player.inv.count(obj)

        fun said(text: String): Boolean = output().contains(text)

        fun at(coords: CoordGrid) {
            player.coords = coords
        }

        fun talk() {
            val npc = Npc(KOJO, player.coords.translateX(1))
            npcRepo.add(npc, Int.MAX_VALUE)
            dispatch { assertTrue(events.publish(this, NpcEvents.Op1(npc))) }
        }

        fun spawn(type: String, coords: CoordGrid): Npc {
            val npc = Npc(type, coords)
            npcRepo.add(npc, Int.MAX_VALUE)
            return npc
        }

        fun take(cog: Cog): Obj {
            val obj = objRepo.add(cog.obj, player.coords, Int.MAX_VALUE)
            dispatch { assertTrue(events.publish(this, ObjEvents.Op3(obj))) }
            return obj
        }

        fun onFloor(obj: Obj): Boolean = objRegistry.findAll(obj.coords).any { it === obj }

        fun useOnSpindle(cog: Cog, spindle: String) = locU(spindle, player.coords.translateZ(1), cog.obj)

        fun pull(lever: Lever, pulled: Boolean = false) {
            val type = if (pulled) lever.pulledLoc else lever.startLoc
            locOp(bound(type, lever.coords, LocShape.WallDecorStraightNoOffset, LocAngle.South))
        }

        fun openGate(gate: Gate) {
            locOp(bound(gate.closedLoc, gate.coords, LocShape.WallStraight, LocAngle.East))
        }

        fun throughFarGate() {
            locOp(bound(RatCage.WHITE_COG_GATE, CoordGrid(2579, 9656, 0), LocShape.WallStraight, LocAngle.West))
        }

        fun hasLoc(type: String, coords: CoordGrid): Boolean = locRepo.findLoc(coords, type)

        fun gateClosed(gate: Gate): Boolean = hasLoc(gate.closedLoc, gate.coords)

        fun locU(symbol: String, coords: CoordGrid, obj: String) {
            val loc = bound(symbol, coords, LocShape.CentrepieceStraight, LocAngle.West)
            val objType = checkNotNull(ServerCacheManager.getItem(obj.asRSCM()))
            val slot = player.inv.indexOfFirst { it?.id == obj.asRSCM() }
            val locType = checkNotNull(ServerCacheManager.getObject(symbol.asRSCM()))
            val event = with(locU) { access().opTrigger(loc, loc, locType, objType, slot) }
            val trigger = checkNotNull(event) { "No $obj handler for $symbol" }
            dispatch { assertTrue(events.publish(this, trigger)) }
        }

        private fun locOp(loc: BoundLocInfo) {
            val event = LocInteractions(BoundValidator(collision), events).opTrigger(player, loc, InteractionOp.Op1)
            val trigger = checkNotNull(event) { "No op1 handler for ${loc.id}" }
            dispatch { assertTrue(events.publish(this, trigger)) }
        }

        private fun bound(symbol: String, coords: CoordGrid, shape: LocShape, angle: LocAngle): BoundLocInfo {
            val type = checkNotNull(ServerCacheManager.getObject(symbol.asRSCM()))
            return BoundLocInfo(LocInfo(shape.layer(), coords, LocEntity(type.id, shape.id, angle.id)), type)
        }

        private fun LocShape.layer(): Int = LocLayerConstants.of(id)

        private fun dispatch(block: suspend ProtectedAccess.() -> Unit) {
            player.clearPendingAction(events)
            result = null
            player.activeCoroutine = coroutine
            val access = access()
            val start: suspend () -> Unit = { access.block() }
            start.startCoroutine(object : Continuation<Unit> {
                override val context = EmptyCoroutineContext
                override fun resumeWith(result: Result<Unit>) { this@Fixture.result = result }
            })
            result?.getOrThrow()
            repeat(600) {
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
                val parent = listOf("chat_left", "chat_right", "messagebox", "chatmenu", "objectbox", "objectbox_double")
                    .firstOrNull { player.ui.containsModal("interface.$it") }
                    ?: error("Unknown dialogue: ${output()}")
                val input = when (parent) {
                    "chatmenu" -> ResumePauseButtonInput("component.chatmenu:options", picks.removeFirstOrNull() ?: 1)
                    "objectbox" -> ResumePauseButtonInput("component.objectbox:universe", -1)
                    "objectbox_double" -> ResumePauseButtonInput("component.objectbox_double:pausebutton", -1)
                    else -> ResumePauseButtonInput("component.$parent:continue", -1)
                }
                coroutine.resumeWith(input)
            } else {
                player.currentMapClock++
                player.processedMapClock = player.currentMapClock
                player.pendingSequence = EntitySeq.NULL
                player.pendingFaceAngle = EntityFaceAngle.NULL
                coroutine.advance()
            }
        }

        fun output() = client.messages.joinToString("\n").replace("<br>", " ")
    }

    private class RecordingClient : Client<Any, Any> {
        val messages = mutableListOf<Any>()
        override fun write(message: Any) { messages += message }
        override fun close() {}
        override fun read(player: Player) {}
        override fun flush() {}
        override fun flushHighPriority() {}
        override fun unregister(service: Any, player: Player) {}
    }

    companion object {
        val KOJO_TILE = CoordGrid(2569, 3248, 0)

        private val restored = mutableListOf<() -> Unit>()

        @OptIn(InternalApi::class)
        @JvmStatic @BeforeAll fun cache() {
            ServerCacheManager.init(240).close()
            for ((owner, name) in listOf(
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

        @JvmStatic @AfterAll fun restore() {
            restored.asReversed().forEach { it() }
            restored.clear()
        }
    }
}
