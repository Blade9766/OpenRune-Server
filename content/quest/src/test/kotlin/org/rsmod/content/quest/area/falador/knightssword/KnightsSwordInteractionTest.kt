package org.rsmod.content.quest.area.falador.knightssword

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
import org.rsmod.api.inv.storage.PlayerItemStorage
import org.rsmod.api.invtx.InvTransactionsScript
import org.rsmod.api.player.dialogue.align.TextAlignment
import org.rsmod.api.player.events.interact.HeldObjEvents
import org.rsmod.api.player.events.interact.NpcEvents
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
import org.rsmod.api.registry.region.RegionRegistry
import org.rsmod.api.registry.zone.ZonePlayerActivityBitSet
import org.rsmod.api.registry.zone.ZoneUpdateMap
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.route.BoundValidator
import org.rsmod.content.quest.area.falador.knightssword.KnightsSwordQuest.Companion.BLURITE_ORE
import org.rsmod.content.quest.area.falador.knightssword.KnightsSwordQuest.Companion.BLURITE_SWORD
import org.rsmod.content.quest.area.falador.knightssword.KnightsSwordQuest.Companion.IRON_BAR
import org.rsmod.content.quest.area.falador.knightssword.KnightsSwordQuest.Companion.PORTRAIT
import org.rsmod.content.quest.area.falador.knightssword.KnightsSwordQuest.Companion.REDBERRY_PIE
import org.rsmod.content.quest.area.falador.knightssword.KnightsSwordQuest.Companion.SIR_VYVIN
import org.rsmod.content.quest.area.falador.knightssword.KnightsSwordQuest.Companion.SQUIRE
import org.rsmod.content.quest.area.falador.knightssword.KnightsSwordQuest.Companion.STAGE_COMPLETE
import org.rsmod.content.quest.area.falador.knightssword.KnightsSwordQuest.Companion.STAGE_DESIGN_SHOWN
import org.rsmod.content.quest.area.falador.knightssword.KnightsSwordQuest.Companion.STAGE_PICTURE_NEEDED
import org.rsmod.content.quest.area.falador.knightssword.KnightsSwordQuest.Companion.STAGE_PIE_GIVEN
import org.rsmod.content.quest.area.falador.knightssword.KnightsSwordQuest.Companion.STAGE_PORTRAIT_LOCATED
import org.rsmod.content.quest.area.falador.knightssword.KnightsSwordQuest.Companion.STAGE_RELDO
import org.rsmod.content.quest.area.falador.knightssword.KnightsSwordQuest.Companion.STAGE_STARTED
import org.rsmod.content.quest.area.falador.knightssword.KnightsSwordQuest.Companion.THURGO
import org.rsmod.content.quest.area.falador.knightssword.VyvinsRoom.Companion.CUPBOARD
import org.rsmod.content.quest.area.falador.knightssword.VyvinsRoom.Companion.CUPBOARD_OPEN
import org.rsmod.content.quest.area.falador.knightssword.VyvinsRoom.Companion.CUPBOARD_SHUT
import org.rsmod.content.quest.area.falador.knightssword.VyvinsRoom.Post
import org.rsmod.content.quest.area.falador.knightssword.npcs.ImcandoLore
import org.rsmod.content.quest.area.falador.knightssword.npcs.Squire
import org.rsmod.content.quest.area.falador.knightssword.npcs.Thurgo
import org.rsmod.content.quest.manager.QUEST_STAGE_MAP_ATTR
import org.rsmod.coroutine.GameCoroutine
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
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
import org.rsmod.game.loc.LocEntity
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.map.Direction
import org.rsmod.game.map.LocZoneStorage
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.game.region.RegionListLarge
import org.rsmod.game.region.RegionListSmall
import org.rsmod.game.region.RegionListWorldEntity
import org.rsmod.game.seq.EntitySeq
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

/**
 * Drives the quest's real scripts through the event bus: the whole path from the squire to the
 * handover, every missing-item refusal, being caught at the cupboard and trying again, item
 * recovery after losing the portrait, ore or sword, crafting on a full pack, the optional spare
 * sword, save and reload between objectives, and that the reward is only ever granted once.
 */
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class KnightsSwordInteractionTest {

    @Test fun `the full quest path delivers the sword and grants the rewards once`() {
        val f = Fixture()
        f.startQuest()
        assertEquals(STAGE_STARTED, f.stage())
        assertTrue(f.journal().contains("Reldo"))

        f.askReldo()
        assertEquals(STAGE_RELDO, f.stage())
        assertTrue(f.journal().contains("redberry pie"))

        f.give(REDBERRY_PIE)
        f.talk(THURGO)
        assertEquals(STAGE_PIE_GIVEN, f.stage())
        assertEquals(0, f.count(REDBERRY_PIE))

        f.talk(THURGO)
        assertEquals(STAGE_PICTURE_NEEDED, f.stage())
        f.talk(SQUIRE)
        assertEquals(STAGE_PORTRAIT_LOCATED, f.stage())
        assertTrue(f.journal().contains("cupboard"))

        f.vyvinAt(Post.WINDOW)
        f.openAndSearch()
        assertEquals(1, f.count(PORTRAIT))
        f.lookAt(PORTRAIT)
        assertTrue(f.said("pale blue blade"))

        f.talk(THURGO)
        assertEquals(STAGE_DESIGN_SHOWN, f.stage())
        assertTrue(f.journal().contains("one blurite ore and two iron bars"))

        f.give(BLURITE_ORE)
        f.give(IRON_BAR, 2)
        f.talk(THURGO)
        assertEquals(1, f.count(BLURITE_SWORD))
        assertEquals(0, f.count(BLURITE_ORE))
        assertEquals(0, f.count(IRON_BAR))
        assertTrue(f.ks.hasForgedSword(f.player))
        assertTrue(f.said("hammers the blurite"))

        val xp = f.smithingXp()
        f.talk(SQUIRE)
        assertEquals(STAGE_COMPLETE, f.stage())
        assertEquals(0, f.count(BLURITE_SWORD))
        assertEquals(1, f.player.vars["varp.qp"])
        assertEquals(12_725, f.smithingXp() - xp)
        assertTrue(f.player.ui.containsModal("interface.questscroll"))
    }

    @Test fun `declining the squire leaves the quest unstarted`() {
        val f = Fixture()
        f.choose(1, 2)
        f.talk(SQUIRE)
        assertEquals(0, f.stage())
        f.choose(2)
        f.talk(SQUIRE)
        assertEquals(0, f.stage())
    }

    @Test fun `nobody moves the quest on before its turn`() {
        val f = Fixture()
        f.give(REDBERRY_PIE)
        f.talk(THURGO)
        assertEquals(0, f.stage())
        assertEquals(1, f.count(REDBERRY_PIE))
        assertEquals(null, f.lore.reldoOption(f.player))

        f.jump(STAGE_STARTED)
        f.talk(THURGO)
        assertEquals(STAGE_STARTED, f.stage())
        assertEquals(1, f.count(REDBERRY_PIE))

        f.jump(STAGE_PICTURE_NEEDED)
        f.vyvinAt(Post.WINDOW)
        f.openAndSearch()
        assertEquals(0, f.count(PORTRAIT))
        assertTrue(f.said("Nothing you need"))

        f.jump(STAGE_DESIGN_SHOWN)
        f.talk(SQUIRE)
        assertEquals(STAGE_DESIGN_SHOWN, f.stage())
    }

    @Test fun `thurgo turns away a visitor without a pie`() {
        val f = Fixture(STAGE_RELDO)
        f.talk(THURGO)
        assertEquals(STAGE_RELDO, f.stage())
        assertTrue(f.said("partial to redberry pie"))
        assertTrue(f.ks.hint(f.player).contains("redberry pie"))
    }

    @Test fun `the pie is eaten exactly once`() {
        val f = Fixture(STAGE_RELDO)
        f.give(REDBERRY_PIE, 2)
        f.talk(THURGO)
        assertEquals(STAGE_PIE_GIVEN, f.stage())
        assertEquals(1, f.count(REDBERRY_PIE))
        f.choose(1)
        f.talk(THURGO)
        assertEquals(STAGE_PICTURE_NEEDED, f.stage())
        f.talk(THURGO)
        assertEquals(1, f.count(REDBERRY_PIE))
    }

    @Test fun `sir vyvin catches a search from his desk and the player can try again`() {
        val f = Fixture(STAGE_PORTRAIT_LOCATED)
        f.vyvinAt(Post.DESK)
        assertEquals(Direction.NorthEast, f.room.facing)
        f.openAndSearch()
        assertTrue(f.said("Keep your hands OUT of my cupboard"))
        assertTrue(f.said("Wait until he looks away"))
        assertEquals(0, f.count(PORTRAIT))
        assertEquals(STAGE_PORTRAIT_LOCATED, f.stage())

        f.vyvinAt(Post.WINDOW)
        assertTrue(f.room.isLookingAway())
        f.openAndSearch()
        assertEquals(1, f.count(PORTRAIT))
    }

    @Test fun `sir vyvin back at his desk catches a search of the cupboard left open`() {
        val f = Fixture(STAGE_PORTRAIT_LOCATED)
        f.vyvinAt(Post.WINDOW)
        f.locOp(CUPBOARD_SHUT, CUPBOARD)
        f.vyvinAt(Post.DESK)
        f.locOp(CUPBOARD_OPEN, CUPBOARD)
        assertEquals(0, f.count(PORTRAIT))
        assertTrue(f.said("Keep your hands OUT"))
    }

    @Test fun `sir vyvin warns before turning back from the window`() {
        val f = Fixture()
        val vyvin = f.vyvinAt(Post.WINDOW)
        repeat(Post.WINDOW.ticks) { f.room.patrol(vyvin) }
        assertFalse(f.room.isLookingAway())
    }

    @Test fun `a full pack keeps the portrait in the cupboard until there is room`() {
        val f = Fixture(STAGE_PORTRAIT_LOCATED)
        f.fill()
        f.vyvinAt(Post.WINDOW)
        f.openAndSearch()
        assertEquals(0, f.count(PORTRAIT))
        assertTrue(f.said("don't have room"))
        f.player.inv[0] = null
        f.openAndSearch()
        assertEquals(1, f.count(PORTRAIT))
    }

    @Test fun `a lost portrait can be found again until thurgo has seen it`() {
        val f = Fixture(STAGE_PORTRAIT_LOCATED)
        f.vyvinAt(Post.WINDOW)
        f.openAndSearch()
        f.drop(PORTRAIT)
        f.openAndSearch()
        assertEquals(1, f.count(PORTRAIT))
        f.talk(THURGO)
        assertEquals(STAGE_DESIGN_SHOWN, f.stage())
        f.drop(PORTRAIT)
        f.openAndSearch()
        assertEquals(0, f.count(PORTRAIT))
        assertEquals(STAGE_DESIGN_SHOWN, f.stage())
    }

    @Test fun `the portrait goes back in the cupboard once thurgo has seen it`() {
        val f = Fixture(STAGE_PORTRAIT_LOCATED)
        f.vyvinAt(Post.WINDOW)
        f.openAndSearch()
        f.locU(CUPBOARD_SHUT, PORTRAIT)
        assertEquals(1, f.count(PORTRAIT))
        f.talk(THURGO)
        f.locU(CUPBOARD_SHUT, PORTRAIT)
        assertEquals(0, f.count(PORTRAIT))
    }

    @Test fun `thurgo takes nothing until ore and both bars are there`() {
        val f = Fixture(STAGE_DESIGN_SHOWN)
        f.give(IRON_BAR, 2)
        f.talk(THURGO)
        assertTrue(f.said("I still need a blurite ore"))
        assertEquals(2, f.count(IRON_BAR))

        f.drop(IRON_BAR)
        f.give(BLURITE_ORE)
        f.talk(THURGO)
        assertTrue(f.said("two iron bars"))
        assertEquals(1, f.count(BLURITE_ORE))
        assertEquals(1, f.count(IRON_BAR))
        assertEquals(0, f.count(BLURITE_SWORD))
        assertTrue(f.ks.hint(f.player).contains("iron bars"))
    }

    @Test fun `crafting on a full pack swaps the materials for the sword`() {
        val f = Fixture(STAGE_DESIGN_SHOWN)
        f.give(BLURITE_ORE)
        f.give(IRON_BAR, 2)
        f.fill()
        assertEquals(0, f.player.inv.freeSpace())
        f.talk(THURGO)
        assertEquals(1, f.count(BLURITE_SWORD))
        assertEquals(0, f.count(BLURITE_ORE))
        assertEquals(0, f.count(IRON_BAR))
        assertEquals(2, f.player.inv.freeSpace())
    }

    @Test fun `a lost sword is forged again from fresh materials`() {
        val f = Fixture(STAGE_DESIGN_SHOWN)
        f.forge()
        f.drop(BLURITE_SWORD)
        assertTrue(f.journal().contains("no longer have it"))
        f.talk(SQUIRE)
        assertTrue(f.said("Don't you start losing swords"))
        assertEquals(STAGE_DESIGN_SHOWN, f.stage())
        f.forge()
        assertEquals(1, f.count(BLURITE_SWORD))
    }

    @Test fun `a spare sword can be made before the handover and only one is taken`() {
        val f = Fixture(STAGE_DESIGN_SHOWN)
        f.forge()
        f.give(BLURITE_ORE)
        f.give(IRON_BAR, 2)
        f.choose(1, 1)
        f.talk(THURGO)
        assertEquals(2, f.count(BLURITE_SWORD))
        f.talk(SQUIRE)
        assertEquals(STAGE_COMPLETE, f.stage())
        assertEquals(1, f.count(BLURITE_SWORD))
    }

    @Test fun `talking to the squire again grants nothing more`() {
        val f = Fixture(STAGE_DESIGN_SHOWN)
        f.forge()
        f.talk(SQUIRE)
        val xp = f.smithingXp()
        f.access().ifCloseSub("interface.questscroll")
        f.give(BLURITE_SWORD)
        f.talk(SQUIRE)
        assertEquals(1, f.count(BLURITE_SWORD))
        assertEquals(xp, f.smithingXp())
        assertEquals(1, f.player.vars["varp.qp"])
        assertFalse(f.player.ui.containsModal("interface.questscroll"))
    }

    @Test fun `the dungeon on foot - mining stays available and nothing has to die`() {
        val f = Fixture(STAGE_DESIGN_SHOWN)
        assertTrue(f.ks.dungeonWarning(f.player, KnightsSwordQuest.ICE_DUNGEON_TRAPDOOR)!!.contains("guarded"))
        assertEquals(null, f.ks.dungeonWarning(f.player, CoordGrid(3000, 3000, 0)))
        f.give(BLURITE_ORE)
        assertEquals(null, f.ks.dungeonWarning(f.player, KnightsSwordQuest.ICE_DUNGEON_TRAPDOOR))
    }

    @Test fun `defeat loses only items, all of which can be replaced`() {
        val f = Fixture(STAGE_PORTRAIT_LOCATED)
        f.vyvinAt(Post.WINDOW)
        f.openAndSearch()
        f.talk(THURGO)
        f.give(BLURITE_ORE)
        f.give(IRON_BAR, 2)
        f.dieLosingEverything()
        assertEquals(STAGE_DESIGN_SHOWN, f.stage())
        f.forge()
        assertEquals(1, f.count(BLURITE_SWORD))
        f.dieLosingEverything()
        assertTrue(f.ks.hasForgedSword(f.player))
        f.forge()
        f.talk(SQUIRE)
        assertEquals(STAGE_COMPLETE, f.stage())
    }

    @Test fun `save and reload keeps every objective`() {
        val f = Fixture()
        f.startQuest()
        f.askReldo()
        var loaded = f.saveAndReload()
        assertEquals(STAGE_RELDO, f.ks.stage(loaded))
        f.jump(STAGE_DESIGN_SHOWN)
        f.forge()
        loaded = f.saveAndReload()
        assertEquals(STAGE_DESIGN_SHOWN, f.ks.stage(loaded))
        assertTrue(f.ks.hasForgedSword(loaded))
        f.talk(SQUIRE)
        loaded = f.saveAndReload()
        assertTrue(f.ks.isComplete(loaded))
    }

    @Test fun `resetting the quest clears the forged flag`() {
        val f = Fixture(STAGE_DESIGN_SHOWN)
        f.forge()
        f.ks.quest.resetQuest(f.player)
        assertEquals(0, f.stage())
        assertFalse(f.ks.hasForgedSword(f.player))
    }

    @Test fun `hints only ever name the current obstacle`() {
        val f = Fixture()
        val hints = (0..STAGE_COMPLETE).map { stage ->
            f.jump(stage)
            f.ks.hint(f.player)
        }
        assertTrue(hints[0].contains("squire"))
        assertTrue(hints[STAGE_STARTED].contains("Reldo"))
        assertFalse(hints[STAGE_STARTED].contains("Thurgo"))
        assertFalse(hints[STAGE_RELDO].contains("portrait"))
        assertTrue(hints[STAGE_PORTRAIT_LOCATED].contains("window"))
        assertFalse(hints[STAGE_PORTRAIT_LOCATED].contains("blurite"))
        assertTrue(hints[STAGE_DESIGN_SHOWN].contains("blurite"))
        assertEquals(hints.size, hints.distinct().size)
    }

    private class Fixture(stage: Int = 0) {
        val events = EventBus()
        private val client = RecordingClient()
        private val collision = CollisionFlagMap()
        private val npcs = NpcList()
        private val coroutine = GameCoroutine("knights-sword-test")
        private var result: Result<Unit>? = null
        private val context = ProtectedAccessContextFactory.empty().copy(
            getEventBus = { events }, getAlignment = { TextAlignment() },
            getCollision = { collision }, getNpcList = { npcs },
            getNpcInteractions = { NpcInteractions(events) },
        )
        private val clock = MapClock(100)
        val npcRepo: NpcRepository
        private val locRepo: LocRepository
        private val picks = ArrayDeque<Int>()
        private val locU = LocUInteractions::class.java.getDeclaredConstructor(EventBus::class.java)
            .apply { isAccessible = true }.newInstance(events)

        @OptIn(InternalApi::class)
        val player = Player().apply {
            this.client = this@Fixture.client
            uuid = 4242L
            observerUUID = 4242L
            slotId = 1
            assignUid()
            coords = SEARCH_TILE
            currentMapClock = 100
            processedMapClock = 100
            pendingSequence = EntitySeq.NULL
            pendingFaceAngle = EntityFaceAngle.NULL
            inv = Inventory(InventoryServerType(size = 28, flags = 0), arrayOfNulls(28))
            worn = Inventory(InventoryServerType(size = 14, flags = 0), arrayOfNulls(14))
            statMap.setBaseLevel("stat.mining", 10)
            statMap.setCurrentLevel("stat.mining", 10)
        }

        val ks = KnightsSwordQuest()
        val lore = ImcandoLore(ks)
        val room: VyvinsRoom

        init {
            val updates = ZoneUpdateMap()
            val storage = LocZoneStorage()
            val normal = LocRegistryNormal(updates, collision, storage)
            val npcRegistry = NpcRegistry(npcs, collision, events)
            val regions = RegionRegistry(RegionListSmall(), RegionListLarge(), RegionListWorldEntity(),
                normal, collision, storage, npcRegistry,
                ControllerRegistry(clock, ControllerList()), ZonePlayerActivityBitSet())
            locRepo = LocRepository(clock, LocRegistry(storage, normal,
                LocRegistryRegion(updates, collision, storage, regions)), regions)
            npcRepo = NpcRepository(clock, npcRegistry, npcs)
            for ((x0, z0, level) in listOf(Triple(2976, 3328, 2), Triple(2976, 3336, 2), Triple(2992, 3136, 0), Triple(3200, 3488, 0))) {
                for (x in x0..x0 + 16 step 8) for (z in z0..z0 + 16 step 8) collision.allocateIfAbsent(x, z, level)
            }
            room = VyvinsRoom(ks, locRepo, npcs, collision)
            val scripts = ScriptContext(events, CheatCommandMap(), EngineQueueCache())
            with(ks) { scripts.startup() }
            with(room) { scripts.startup() }
            with(Squire(ks)) { scripts.startup() }
            with(Thurgo(ks)) { scripts.startup() }
            if (stage > 0) ks.quest.jumpToStage(player, stage)
        }

        fun access() = ProtectedAccess(player, coroutine, context)

        fun stage(): Int = ks.stage(player)

        fun jump(stage: Int) = ks.quest.jumpToStage(player, stage)

        fun choose(vararg options: Int) {
            picks += options.toList()
        }

        fun give(obj: String, count: Int = 1) {
            repeat(count) {
                val slot = player.inv.indexOfFirst { it == null }
                player.inv[slot] = InvObj(obj, 1)
            }
        }

        fun fill() {
            while (player.inv.freeSpace() > 0) give("obj.bronze_dagger")
        }

        fun drop(obj: String) {
            val slot = player.inv.indexOfFirst { it?.id == obj.asRSCM() }
            player.inv[slot] = null
        }

        fun dieLosingEverything() {
            for (slot in 0 until player.inv.size) player.inv[slot] = null
        }

        fun count(obj: String): Int = player.inv.count(obj)

        fun said(text: String): Boolean = output().contains(text)

        fun smithingXp(): Int = player.statMap.getXP("stat.smithing")

        fun journal(): String = ks.questLog(access())

        fun startQuest() {
            choose(1, 1)
            talk(SQUIRE)
        }

        fun askReldo() {
            val reldo = Npc("npc.reldo_normal", player.coords.translateX(1))
            npcRepo.add(reldo, Int.MAX_VALUE)
            dispatch { startDialogue(reldo) { with(lore) { reldoImcando() } } }
        }

        fun forge() {
            give(BLURITE_ORE)
            give(IRON_BAR, 2)
            talk(THURGO)
        }

        fun openAndSearch() {
            locOp(CUPBOARD_SHUT, CUPBOARD)
            locOp(CUPBOARD_OPEN, CUPBOARD)
        }

        /** Runs Sir Vyvin's routine until he stands at [post] looking its way; his walks are skipped. */
        fun vyvinAt(post: Post): Npc {
            val vyvin = npcs.firstOrNull { it != null && it.isType(SIR_VYVIN) }
                ?: Npc(SIR_VYVIN, Post.DESK.tile).also { npcRepo.add(it, Int.MAX_VALUE) }
            repeat(200) {
                room.patrol(vyvin)
                if (room.post == post && vyvin.coords == post.tile && room.facing == post.facing) return vyvin
                vyvin.coords = room.post.tile
            }
            fail<Unit>("Sir Vyvin never reached $post")
            return vyvin
        }

        fun talk(type: String, at: CoordGrid = player.coords.translateX(1)) {
            val npc = Npc(type, at)
            npcRepo.add(npc, Int.MAX_VALUE)
            dispatch { assertTrue(events.publish(this, NpcEvents.Op1(npc))) }
        }

        fun lookAt(obj: String) {
            val slot = player.inv.indexOfFirst { it?.id == obj.asRSCM() }
            val type = checkNotNull(ServerCacheManager.getItem(obj.asRSCM()))
            dispatch { assertTrue(events.publish(this, HeldObjEvents.Op1(slot, checkNotNull(player.inv[slot]), type, player.inv))) }
        }

        fun locOp(symbol: String, coords: CoordGrid, op: InteractionOp = InteractionOp.Op1) {
            val loc = bound(symbol, coords)
            val event = LocInteractions(BoundValidator(collision), events).opTrigger(player, loc, op)
            val trigger = checkNotNull(event) { "No $op handler for $symbol" }
            dispatch { assertTrue(events.publish(this, trigger)) }
        }

        fun locU(symbol: String, obj: String) {
            val loc = bound(symbol, CUPBOARD)
            val objType = checkNotNull(ServerCacheManager.getItem(obj.asRSCM()))
            val slot = player.inv.indexOfFirst { it?.id == obj.asRSCM() }
            val locType = checkNotNull(ServerCacheManager.getObject(symbol.asRSCM()))
            val event = with(locU) { access().opTrigger(loc, loc, locType, objType, slot) }
            val trigger = checkNotNull(event) { "No $obj handler for $symbol" }
            dispatch { assertTrue(events.publish(this, trigger)) }
        }

        /** What the account save keeps: the varps and the persistent quest-stage attribute. */
        fun saveAndReload(): Player {
            val loaded = Player()
            loaded.vars.backing.putAll(player.vars.backing)
            player.attr[QUEST_STAGE_MAP_ATTR]?.let { loaded.attr[QUEST_STAGE_MAP_ATTR] = it.toMutableMap() }
            ks.quest.syncState(loaded)
            return loaded
        }

        private fun bound(symbol: String, coords: CoordGrid): BoundLocInfo {
            val type = checkNotNull(ServerCacheManager.getObject(symbol.asRSCM()))
            return BoundLocInfo(LocInfo(coords.level, coords, LocEntity(type.id, 10, 1)), type)
        }

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
        val SEARCH_TILE = CoordGrid(2984, 3335, 2)

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
