package org.rsmod.content.quest.area.seers.elementalworkshop

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.InventoryServerType
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.startCoroutine
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.annotations.InternalApi
import org.rsmod.api.death.NpcDeath
import org.rsmod.api.inv.storage.PlayerItemStorage
import org.rsmod.api.invtx.InvTransactionsScript
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.player.dialogue.align.TextAlignment
import org.rsmod.api.player.events.interact.HeldObjEvents
import org.rsmod.api.player.events.interact.HeldUDefaultEvents
import org.rsmod.api.player.events.interact.HeldUEvents
import org.rsmod.api.player.events.interact.NpcEvents
import org.rsmod.api.player.input.ResumePauseButtonInput
import org.rsmod.api.player.interact.LocInteractions
import org.rsmod.api.player.interact.LocUInteractions
import org.rsmod.api.player.interact.NpcInteractions
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.api.player.protect.clearPendingAction
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.registry.npc.NpcRegistry
import org.rsmod.api.registry.obj.ObjRegistry
import org.rsmod.api.registry.zone.ZoneUpdateMap
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.route.BoundValidator
import org.rsmod.content.quest.area.seers.elementalworkshop.ElementalWorkshopQuest.Companion.BOOK
import org.rsmod.content.quest.area.seers.elementalworkshop.ElementalWorkshopQuest.Companion.BOWL
import org.rsmod.content.quest.area.seers.elementalworkshop.ElementalWorkshopQuest.Companion.COAL
import org.rsmod.content.quest.area.seers.elementalworkshop.ElementalWorkshopQuest.Companion.HAMMER
import org.rsmod.content.quest.area.seers.elementalworkshop.ElementalWorkshopQuest.Companion.KEY
import org.rsmod.content.quest.area.seers.elementalworkshop.ElementalWorkshopQuest.Companion.KNIFE
import org.rsmod.content.quest.area.seers.elementalworkshop.ElementalWorkshopQuest.Companion.LAVA_BOWL
import org.rsmod.content.quest.area.seers.elementalworkshop.ElementalWorkshopQuest.Companion.LEATHER
import org.rsmod.content.quest.area.seers.elementalworkshop.ElementalWorkshopQuest.Companion.METAL
import org.rsmod.content.quest.area.seers.elementalworkshop.ElementalWorkshopQuest.Companion.NEEDLE
import org.rsmod.content.quest.area.seers.elementalworkshop.ElementalWorkshopQuest.Companion.ORE
import org.rsmod.content.quest.area.seers.elementalworkshop.ElementalWorkshopQuest.Companion.SHIELD
import org.rsmod.content.quest.area.seers.elementalworkshop.ElementalWorkshopQuest.Companion.SLASHED_BOOK
import org.rsmod.content.quest.area.seers.elementalworkshop.ElementalWorkshopQuest.Companion.THREAD
import org.rsmod.coroutine.GameCoroutine
import org.rsmod.events.EventBus
import org.rsmod.events.SuspendEvent
import org.rsmod.game.MapClock
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.client.Client
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.util.EntityFaceAngle
import org.rsmod.game.interact.InteractionOp
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.InvVirtualStorageHolder
import org.rsmod.game.inv.Inventory
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocEntity
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.game.queue.WorldQueueList
import org.rsmod.game.seq.EntitySeq
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

/**
 * Drives the quest's real scripts through the event bus: the whole path from bookcase to shield,
 * the wrong valve order, every missing-item and premature-machinery refusal, item recovery, the
 * elemental fight and its defeat, and that the rewards are only ever granted once.
 */
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class ElementalWorkshopInteractionTest {

    @Test fun `the full quest path crafts the shield and grants the rewards`() {
        val f = Fixture()
        f.op(BOOKCASE)
        assertEquals(1, f.count(BOOK))
        f.read(BOOK)
        assertEquals(ElementalWorkshopQuest.STAGE_STARTED, f.ew.stage(f.player))

        f.give(KNIFE)
        f.heldU(BOOK, KNIFE)
        assertEquals(0, f.count(BOOK))
        assertEquals(1, f.count(SLASHED_BOOK))
        assertEquals(1, f.count(KEY))
        assertEquals(1, f.player.vars["varbit.elemental_workshop_book"])

        f.startMachinery()
        f.awakenAndKill()
        assertEquals(1, f.groundOre())
        f.give(ORE)
        f.give(COAL, 4)
        f.locU(FURNACE, ORE)
        assertEquals(1, f.count(METAL))
        assertEquals(0, f.count(ORE))
        assertEquals(0, f.count(COAL))
        assertEquals(1, f.player.vars["varbit.elemental_workshop_metal_smelted"])

        f.give(HAMMER)
        f.op(WORKBENCH)
        assertEquals(1, f.count(SHIELD))
        assertEquals(0, f.count(METAL))
        assertEquals(1, f.count(HAMMER))
        assertEquals(1, f.count(SLASHED_BOOK))
        assertEquals(ElementalWorkshopQuest.STAGE_COMPLETE, f.ew.stage(f.player))
        assertEquals(2, f.player.vars["varbit.elementalworkshop"])
        assertEquals(1, f.player.vars["varp.qp"])
        assertEquals(5000, f.player.statMap.getXP("stat.crafting"))
        assertEquals(5000, f.player.statMap.getXP("stat.smithing"))
        assertTrue(f.player.ui.containsModal("interface.questscroll"))
    }

    @Test fun `a second shield after completion grants no further rewards`() {
        val f = Fixture()
        f.completeQuest()
        f.access().ifCloseSub("interface.questscroll")
        f.give(METAL)
        f.op(WORKBENCH)
        assertEquals(2, f.count(SHIELD))
        assertEquals(1, f.player.vars["varp.qp"])
        assertEquals(5000, f.player.statMap.getXP("stat.crafting"))
        assertEquals(5000, f.player.statMap.getXP("stat.smithing"))
        assertFalse(f.player.ui.containsModal("interface.questscroll"))
    }

    @Test fun `turning the west control first jams the east until the lever resets them`() {
        val f = Fixture()
        f.unlocked()
        f.op(WEST_CONTROL)
        assertTrue(f.said("falls silent"))
        f.op(EAST_CONTROL)
        assertTrue(f.said("won't budge"))
        assertEquals(0, f.player.vars["varbit.elemental_workshop_gate2"])
        assertEquals(1, f.player.vars["varbit.elemental_workshop_gate1"])

        f.op(WATER_LEVER)
        assertTrue(f.said("flow gates resetting"))
        assertEquals(0, f.player.vars["varbit.elemental_workshop_gate1"])
        assertEquals(0, f.player.vars["varbit.elemental_workshop_gate2"])
        assertEquals(0, f.player.vars["varbit.elemental_workshop_switch"])

        f.op(EAST_CONTROL)
        f.op(WEST_CONTROL)
        assertTrue(f.said("rushing along the channel"))
        f.op(WATER_LEVER)
        assertTrue(f.said("water wheel starting up"))
        assertEquals(1, f.player.vars["varbit.elemental_workshop_switch"])
        f.op(EAST_CONTROL)
        assertTrue(f.said("best to leave the controls"))
    }

    @Test fun `a half-set water system is reset by the lever rather than started`() {
        val f = Fixture()
        f.unlocked()
        f.op(EAST_CONTROL)
        f.op(WATER_LEVER)
        assertTrue(f.said("flow gates resetting"))
        assertEquals(0, f.player.vars["varbit.elemental_workshop_switch"])
    }

    @Test fun `the bellows refuse to be fixed without every material and keep the tools`() {
        val f = Fixture()
        f.unlocked()
        f.give(NEEDLE)
        f.give(THREAD)
        f.op(BELLOWS)
        assertTrue(f.said("You need a piece of leather, some thread and a needle"))
        assertEquals(1, f.count(THREAD))
        f.give(LEATHER)
        f.player.statMap.setCurrentLevel("stat.crafting", 19)
        f.op(BELLOWS)
        assertTrue(f.said("Crafting level of 20"))
        assertEquals(1, f.count(LEATHER))
        f.player.statMap.setCurrentLevel("stat.crafting", 20)
        f.locU(BELLOWS, LEATHER)
        assertTrue(f.said("You stitch the leather"))
        assertEquals(1, f.count(NEEDLE))
        assertEquals(0, f.count(LEATHER))
        assertEquals(0, f.count(THREAD))
        assertEquals(1, f.player.vars["varbit.elemental_workshop_bellows"])
    }

    @Test fun `the bellows lever needs repaired bellows and a turning wheel`() {
        val f = Fixture()
        f.unlocked()
        f.op(BELLOWS_LEVER)
        assertTrue(f.said("Looks like they need fixing"))
        f.set("varbit.elemental_workshop_bellows", 1)
        f.op(BELLOWS_LEVER)
        assertTrue(f.said("Nothing is driving"))
        assertEquals(0, f.player.vars["varbit.elemental_workshop_bellows_switch"])
        f.set("varbit.elemental_workshop_switch", 1)
        f.op(BELLOWS_LEVER)
        assertEquals(1, f.player.vars["varbit.elemental_workshop_bellows_switch"])
    }

    @Test fun `smelting too early never consumes the ore or coal`() {
        val f = Fixture()
        f.unlocked()
        f.give(ORE)
        f.give(COAL, 4)
        f.locU(FURNACE, ORE)
        assertTrue(f.said("furnace is cold"))
        f.set("varbit.elemental_workshop_fire", 1)
        f.locU(FURNACE, COAL)
        assertTrue(f.said("needs air from the bellows"))
        f.set("varbit.elemental_workshop_switch", 1)
        f.set("varbit.elemental_workshop_bellows", 1)
        f.set("varbit.elemental_workshop_bellows_switch", 1)
        f.player.inv[f.slotOf(COAL)] = null
        f.locU(FURNACE, ORE)
        assertTrue(f.said("four heaps of coal"))
        f.give(COAL)
        f.player.statMap.setCurrentLevel("stat.smithing", 19)
        f.locU(FURNACE, ORE)
        assertTrue(f.said("Smithing level of 20"))
        assertEquals(1, f.count(ORE))
        assertEquals(4, f.count(COAL))
        assertEquals(0, f.count(METAL))
    }

    @Test fun `lava lights the furnace once and hands the bowl back`() {
        val f = Fixture()
        f.unlocked()
        f.give(BOWL)
        f.locU(FURNACE, BOWL)
        assertTrue(f.said("The bowl is empty"))
        f.locU(TROUGH, BOWL)
        assertEquals(1, f.count(LAVA_BOWL))
        f.locU(FURNACE, LAVA_BOWL)
        assertTrue(f.said("bursts to life"))
        assertEquals(1, f.count(BOWL))
        assertEquals(1, f.player.vars["varbit.elemental_workshop_fire"])
    }

    @Test fun `the workbench names whatever is missing`() {
        val f = Fixture()
        f.unlocked()
        f.give(METAL)
        f.op(WORKBENCH)
        assertTrue(f.said("You need instructions to follow"))
        f.give(SLASHED_BOOK)
        f.op(WORKBENCH)
        assertTrue(f.said("You need a hammer"))
        assertEquals(1, f.count(METAL))
        assertEquals(0, f.count(SHIELD))
        assertEquals(ElementalWorkshopQuest.STAGE_STARTED, f.ew.stage(f.player))
    }

    @Test fun `the book cannot be cut before it is read and a sword works as well as a knife`() {
        val f = Fixture(stage = 0)
        f.give(BOOK)
        f.give(SCIMITAR)
        f.heldDefault(BOOK, SCIMITAR)
        assertTrue(f.said("haven't even read"))
        assertEquals(1, f.count(BOOK))
        f.read(BOOK)
        f.heldDefault(BOOK, SCIMITAR)
        assertEquals(1, f.count(SLASHED_BOOK))
        assertEquals(1, f.count(KEY))
        assertEquals(1, f.count(SCIMITAR))
    }

    @Test fun `a lost book and key can be found again until the wall is unlocked`() {
        val f = Fixture()
        f.op(BOOKCASE)
        assertEquals(1, f.count(BOOK))
        f.op(BOOKCASE)
        assertEquals(1, f.count(BOOK))
        assertTrue(f.said("You find nothing of interest"))

        f.give(KNIFE)
        f.heldU(KNIFE, BOOK)
        f.player.inv[f.slotOf(KEY)] = null
        f.op(BOOKCASE)
        assertEquals(1, f.count(BOOK))
        f.heldU(BOOK, KNIFE)
        assertEquals(1, f.count(KEY))
        assertEquals(1, f.count(SLASHED_BOOK))

        f.player.inv[f.slotOf(KEY)] = null
        f.set("varbit.elemental_workshop_key", 1)
        f.op(BOOKCASE)
        assertEquals(0, f.count(BOOK))

        f.player.inv[f.slotOf(SLASHED_BOOK)] = null
        f.op(BOOKCASE)
        assertEquals(1, f.count(BOOK))
    }

    @Test fun `the crates restock what is missing but never duplicate it`() {
        val f = Fixture()
        f.unlocked()
        f.op(BOWL_BOXES)
        assertEquals(1, f.count(BOWL))
        f.op(BOWL_BOXES)
        assertEquals(1, f.count(BOWL))
        f.player.inv[f.slotOf(BOWL)] = null
        f.op(BOWL_BOXES)
        assertEquals(1, f.count(BOWL))

        f.op(LEATHER_CRATE)
        f.op(THREAD_CRATE)
        f.op(NEEDLE_CRATE)
        f.op(HAMMER_CRATE)
        f.op(COAL_CRATE)
        assertEquals(1, f.count(LEATHER))
        assertEquals(1, f.count(THREAD))
        assertEquals(1, f.count(NEEDLE))
        assertEquals(1, f.count(HAMMER))
        assertEquals(4, f.count(COAL))
        f.op(COAL_CRATE)
        assertEquals(4, f.count(COAL))

        f.set("varbit.elemental_workshop_bellows", 1)
        f.player.inv[f.slotOf(LEATHER)] = null
        f.op(LEATHER_CRATE)
        assertEquals(0, f.count(LEATHER))
    }

    @Test fun `mining a rock without a pickaxe or the level is refused`() {
        val f = Fixture()
        f.unlocked()
        val rock = f.rock()
        f.npcOp(rock)
        assertTrue(f.said("You need a pickaxe"))
        f.give(PICKAXE)
        f.player.statMap.setCurrentLevel("stat.mining", 19)
        f.npcOp(rock)
        assertTrue(f.said("Mining level of 20"))
        assertTrue(rock.isVisible)
    }

    @Test fun `only the awakened elemental drops ore and only for whoever woke it`() {
        val f = Fixture()
        f.unlocked()
        val roaming = Npc(ElementalRocks.ROAMING, f.player.coords)
        f.npcRepo.add(roaming, 100)
        f.kill(roaming, f.player)
        assertEquals(0, f.groundOre())

        val elemental = f.awaken()
        val other = Player().apply { coords = f.player.coords }
        f.kill(elemental, other)
        assertEquals(0, f.groundOre())
        assertEquals(0, f.player.vars["varbit.elemental_workshop_ore_found"])

        val second = f.awaken()
        f.kill(second, f.player)
        assertEquals(1, f.groundOre())
        assertEquals(1, f.player.vars["varbit.elemental_workshop_ore_found"])
    }

    @Test fun `a lost fight leaves the player free to wake another rock`() {
        val f = Fixture()
        f.unlocked()
        val first = f.awaken()
        f.npcOp(f.rock(CoordGrid(2696, 9880)))
        assertTrue(f.said("already have an angry rock"))
        assertEquals(f.player.uid, f.rocks.ownerOf(first))

        f.player.coords = RESPAWN
        val second = f.awaken(CoordGrid(2696, 9910))
        assertFalse(first.isSlotAssigned)
        assertNull(f.rocks.ownerOf(first))
        assertEquals(f.player.uid, f.rocks.ownerOf(second))
    }

    @Test fun `resetting the quest clears every workshop flag`() {
        val f = Fixture()
        f.completeQuest()
        f.ew.quest.resetQuest(f.player)
        for (flag in FLAGS) assertEquals(0, f.player.vars[flag], flag)
        assertEquals(0, f.player.vars["varp.qp"])
    }

    @Test fun `the journal follows the machinery without giving the valve order away`() {
        val f = Fixture()
        f.unlocked()
        val before = f.ew.questLog(f.access())
        assertTrue(before.contains("water wheel"))
        assertFalse(before.contains("east control") || before.contains("dawn"))
        f.startMachinery()
        val after = f.ew.questLog(f.access())
        assertTrue(after.contains("<str>I set the water controls and started the water wheel.</str>"))
        assertTrue(after.contains("<str>I relit the furnace"))
    }

    private class Fixture(stage: Int = ElementalWorkshopQuest.STAGE_STARTED) {
        val events = EventBus()
        private val client = RecordingClient()
        private val collision = CollisionFlagMap()
        private val npcs = NpcList()
        private val players = PlayerList()
        private val coroutine = GameCoroutine("elemental-workshop-test")
        private var result: Result<Unit>? = null
        private val context = ProtectedAccessContextFactory.empty().copy(
            getEventBus = { events }, getAlignment = { TextAlignment() },
            getCollision = { collision }, getNpcList = { npcs },
            getNpcInteractions = { NpcInteractions(events) },
        )
        private val clock = MapClock(100)
        private val objRegistry = ObjRegistry(ZoneUpdateMap())
        val npcRepo = NpcRepository(clock, NpcRegistry(npcs, collision, events), npcs)
        private val objRepo = ObjRepository(clock, objRegistry)
        private val locU = LocUInteractions::class.java.getDeclaredConstructor(EventBus::class.java)
            .apply { isAccessible = true }.newInstance(events)

        @OptIn(InternalApi::class)
        val player = Player().apply {
            this.client = this@Fixture.client
            uuid = 4242L
            observerUUID = 4242L
            slotId = 1
            assignUid()
            coords = CENTRE
            currentMapClock = 100
            processedMapClock = 100
            pendingSequence = EntitySeq.NULL
            pendingFaceAngle = EntityFaceAngle.NULL
            inv = Inventory(InventoryServerType(size = 28, flags = 0), arrayOfNulls(28))
            worn = Inventory(InventoryServerType(size = 14, flags = 0), arrayOfNulls(14))
            for (stat in listOf("stat.mining", "stat.smithing", "stat.crafting")) {
                statMap.setBaseLevel(stat, 20)
                statMap.setCurrentLevel(stat, 20)
            }
        }

        val ew = ElementalWorkshopQuest()
        val rocks = ElementalRocks(ew, npcRepo, objRepo, NpcDeath(npcRepo, players, objRepo, emptySet(), emptySet()), players, WorldQueueList(), AiPlayerInteractions(events, players))

        /** The death handler's order: the owner is read, the npc deleted, then the ore given. */
        fun kill(npc: Npc, killer: Player) {
            val owner = rocks.ownerOf(npc)
            val at = npc.coords
            if (npc.isSlotAssigned) npcRepo.del(npc, Int.MAX_VALUE)
            assertNull(rocks.ownerOf(npc))
            rocks.rewardOre(owner, killer, at)
        }

        init {
            players[player.slotId] = player
            val scripts = ScriptContext(events, CheatCommandMap(), EngineQueueCache())
            with(ew) { scripts.startup() }
            with(ShieldBook(ew, objRepo)) { scripts.startup() }
            with(WorkshopMachinery(ew, objRepo)) { scripts.startup() }
            with(rocks) { scripts.startup() }
            for (x in 2688..2744 step 8) for (z in 9864..9912 step 8) collision.allocateIfAbsent(x, z, 0)
            if (stage > 0) ew.quest.jumpToStage(player, stage)
        }

        fun access() = ProtectedAccess(player, coroutine, context)

        fun give(obj: String, count: Int = 1) {
            val perSlot = if (item(obj).stackable) count else 1
            repeat(count / perSlot) {
                val slot = player.inv.indexOfFirst { it == null }
                player.inv[slot] = InvObj(obj, perSlot)
            }
        }

        fun count(obj: String): Int = player.inv.count(obj)

        fun slotOf(obj: String): Int = player.inv.indexOfFirst { it?.id == obj.asRSCM() }

        fun said(text: String): Boolean = output().contains(text)

        fun set(varbit: String, value: Int) = VarPlayerIntMapSetter.set(player, varbit, value)

        fun unlocked() {
            set("varbit.elemental_workshop_book", 1)
            set("varbit.elemental_workshop_key", 1)
        }

        fun op(symbol: String) {
            val loc = loc(symbol)
            val event = LocInteractions(BoundValidator(collision), events).opTrigger(player, loc, InteractionOp.Op1)
            dispatch(checkNotNull(event) { "No op1 handler for $symbol" })
        }

        fun locU(symbol: String, obj: String) {
            val loc = loc(symbol)
            val objType = checkNotNull(ServerCacheManager.getItem(obj.asRSCM()))
            val event = with(locU) { access().opTrigger(loc, loc, locType(symbol), objType, slotOf(obj)) }
            dispatch(checkNotNull(event) { "No $obj handler for $symbol" })
        }

        /** Tries both orders, as the client's held-use dispatch does. */
        fun heldU(first: String, second: String) {
            val forward = HeldUEvents.Type(item(first), slotOf(first), item(second), slotOf(second))
            val reverse = HeldUEvents.Type(item(second), slotOf(second), item(first), slotOf(first))
            dispatch(if (events.contains(HeldUEvents.Type::class.java, forward.id)) forward else reverse)
        }

        fun heldDefault(first: String, second: String) {
            dispatch(HeldUDefaultEvents.Type(item(first), slotOf(first), item(second), slotOf(second)))
        }

        fun read(book: String) {
            val slot = slotOf(book)
            dispatch(HeldObjEvents.Op1(slot, checkNotNull(player.inv[slot]), item(book), player.inv))
            repeat(3) { tick() }
            coroutine.cancel()
            assertInstanceOf(CancellationException::class.java, result?.exceptionOrNull())
            result = null
            player.activeCoroutine = null
            access().ifCloseSub("interface.book")
        }

        fun rock(at: CoordGrid = CoordGrid(2695, 9881)): Npc {
            val rock = Npc(ElementalRocks.ROCK, at)
            npcRepo.add(rock, Int.MAX_VALUE)
            return rock
        }

        fun npcOp(npc: Npc) = dispatch(NpcEvents.Op1(npc))

        fun awaken(at: CoordGrid = CoordGrid(2695, 9881)): Npc {
            if (count(PICKAXE) == 0) give(PICKAXE)
            player.coords = at.translateX(1)
            val rock = rock(at)
            npcOp(rock)
            assertTrue(said("It's alive!"), output())
            assertFalse(rock.isVisible)
            return npcs.filterNotNull().last { it.isType(ElementalRocks.AWAKENED) && it.coords == at }
        }

        fun awakenAndKill() {
            val elemental = awaken()
            assertEquals(player.uid, rocks.ownerOf(elemental))
            kill(elemental, player)
            player.coords = CENTRE
        }

        fun groundOre(): Int =
            objRegistry.findAll(CoordGrid(2695, 9881)).count { it.type == ORE.asRSCM() }

        fun startMachinery() {
            unlocked()
            op(EAST_CONTROL)
            op(WEST_CONTROL)
            op(WATER_LEVER)
            give(NEEDLE)
            give(THREAD)
            give(LEATHER)
            op(BELLOWS)
            op(BELLOWS_LEVER)
            give(BOWL)
            locU(TROUGH, BOWL)
            locU(FURNACE, LAVA_BOWL)
            assertTrue(ew.isWheelRunning(player), output())
            assertTrue(ew.isBellowsPumping(player), output())
            assertTrue(ew.isFurnaceLit(player), output())
        }

        fun completeQuest() {
            startMachinery()
            give(SLASHED_BOOK)
            give(HAMMER)
            give(METAL)
            op(WORKBENCH)
            assertEquals(ElementalWorkshopQuest.STAGE_COMPLETE, ew.stage(player))
        }

        private fun item(obj: String) = checkNotNull(ServerCacheManager.getItem(obj.asRSCM()))

        private fun locType(symbol: String) = checkNotNull(ServerCacheManager.getObject(symbol.asRSCM()))

        private fun loc(symbol: String): BoundLocInfo {
            val type = checkNotNull(ServerCacheManager.getObject(symbol.asRSCM()))
            return BoundLocInfo(LocInfo(0, player.coords.translateZ(1), LocEntity(type.id, 10, 0)), type)
        }

        private fun dispatch(event: SuspendEvent<ProtectedAccess>) {
            player.clearPendingAction(events)
            result = null
            player.activeCoroutine = coroutine
            val block: suspend () -> Unit = { assertTrue(events.publish(access(), event)) }
            block.startCoroutine(object : Continuation<Unit> {
                override val context = EmptyCoroutineContext
                override fun resumeWith(result: Result<Unit>) { this@Fixture.result = result }
            })
            result?.getOrThrow()
            finish()
        }

        private fun finish() {
            repeat(200) {
                if (coroutine.isIdle) return
                if (player.ui.containsModal("interface.book")) return
                if (coroutine.isAwaiting(ResumePauseButtonInput::class)) {
                    val parent = listOf("chat_left", "chat_right", "messagebox", "chatmenu", "objectbox", "objectbox_double")
                        .firstOrNull { player.ui.containsModal("interface.$it") }
                        ?: error("Unknown dialogue: ${output()}")
                    val input = when (parent) {
                        "chatmenu" -> ResumePauseButtonInput("component.chatmenu:options", 1)
                        "objectbox" -> ResumePauseButtonInput("component.objectbox:universe", -1)
                        "objectbox_double" -> ResumePauseButtonInput("component.objectbox_double:pausebutton", -1)
                        else -> ResumePauseButtonInput("component.$parent:continue", -1)
                    }
                    coroutine.resumeWith(input)
                } else {
                    tick()
                }
                result?.getOrThrow()
            }
            fail<Unit>("Interaction did not finish: ${output()}")
        }

        private fun tick() {
            player.currentMapClock++
            player.processedMapClock = player.currentMapClock
            player.pendingSequence = EntitySeq.NULL
            player.pendingFaceAngle = EntityFaceAngle.NULL
            coroutine.advance()
        }

        fun output() = client.messages.joinToString("\n")
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
        val CENTRE = CoordGrid(2716, 9888, 0)
        val RESPAWN = CoordGrid(2757, 3477, 0)

        const val BOOKCASE = "loc.elemental_workshop_bookcase"
        const val EAST_CONTROL = "loc.elemental_workshop_valve_1"
        const val WEST_CONTROL = "loc.elemental_workshop_valve_2"
        const val WATER_LEVER = "loc.elemental_workshop_water_lever"
        const val BELLOWS = "loc.elemental_workshop_bellows_multiloc"
        const val BELLOWS_LEVER = "loc.elemental_workshop_air_lever"
        const val TROUGH = "loc.elemental_workshop_trough_3"
        const val FURNACE = "loc.elemental_workshop_furnace"
        const val WORKBENCH = "loc.elemental_workshop_workbench"
        const val LEATHER_CRATE = "loc.elemental_workshop_box_1"
        const val NEEDLE_CRATE = "loc.elemental_workshop_box_2"
        const val BOWL_BOXES = "loc.elemental_workshop_box_4"
        const val HAMMER_CRATE = "loc.elemental_workshop_box_5"
        const val THREAD_CRATE = "loc.elemental_workshop_box_7"
        const val COAL_CRATE = "loc.elemental_workshop_box_8"
        const val PICKAXE = "obj.bronze_pickaxe"
        const val SCIMITAR = "obj.bronze_scimitar"

        val FLAGS =
            listOf(
                "varbit.elementalworkshop",
                "varbit.elemental_workshop_book",
                "varbit.elemental_workshop_key",
                "varbit.elemental_workshop_gate1",
                "varbit.elemental_workshop_gate2",
                "varbit.elemental_workshop_switch",
                "varbit.elemental_workshop_bellows",
                "varbit.elemental_workshop_fire",
                "varbit.elemental_workshop_bellows_switch",
                "varbit.elemental_workshop_ore_found",
                "varbit.elemental_workshop_metal_smelted",
            )

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
