package org.rsmod.content.quest.area.karamja.piratestreasure

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.InventoryServerType
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.random.Random
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
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
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.player.dialogue.align.TextAlignment
import org.rsmod.api.player.events.interact.HeldObjEvents
import org.rsmod.api.player.events.interact.NpcEvents
import org.rsmod.api.player.events.interact.NpcUEvents
import org.rsmod.api.player.hit.modifier.NoopPlayerHitModifier
import org.rsmod.api.player.input.ResumePauseButtonInput
import org.rsmod.api.player.interact.LocInteractions
import org.rsmod.api.player.interact.LocUInteractions
import org.rsmod.api.player.interact.NpcInteractions
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.api.player.protect.clearPendingAction
import org.rsmod.api.random.DefaultGameRandom
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
import org.rsmod.content.generic.locs.spade.SpadeScript
import org.rsmod.content.quest.area.SpadeDigging
import org.rsmod.content.quest.area.karamja.piratestreasure.PiratesTreasureQuest.Companion.BANANA
import org.rsmod.content.quest.area.karamja.piratestreasure.PiratesTreasureQuest.Companion.BANANA_CRATE
import org.rsmod.content.quest.area.karamja.piratestreasure.PiratesTreasureQuest.Companion.BANANA_RUM
import org.rsmod.content.quest.area.karamja.piratestreasure.PiratesTreasureQuest.Companion.CASKET
import org.rsmod.content.quest.area.karamja.piratestreasure.PiratesTreasureQuest.Companion.CHEST_KEY
import org.rsmod.content.quest.area.karamja.piratestreasure.PiratesTreasureQuest.Companion.COINS
import org.rsmod.content.quest.area.karamja.piratestreasure.PiratesTreasureQuest.Companion.GROCERY_CRATE
import org.rsmod.content.quest.area.karamja.piratestreasure.PiratesTreasureQuest.Companion.HECTORS_CHEST
import org.rsmod.content.quest.area.karamja.piratestreasure.PiratesTreasureQuest.Companion.KARAMJA_RUM
import org.rsmod.content.quest.area.karamja.piratestreasure.PiratesTreasureQuest.Companion.LUTHAS
import org.rsmod.content.quest.area.karamja.piratestreasure.PiratesTreasureQuest.Companion.PIRATE_MESSAGE
import org.rsmod.content.quest.area.karamja.piratestreasure.PiratesTreasureQuest.Companion.REDBEARD_FRANK
import org.rsmod.content.quest.area.karamja.piratestreasure.PiratesTreasureQuest.Companion.SPADE
import org.rsmod.content.quest.area.karamja.piratestreasure.PiratesTreasureQuest.Companion.STAGE_COMPLETE
import org.rsmod.content.quest.area.karamja.piratestreasure.PiratesTreasureQuest.Companion.STAGE_KEY
import org.rsmod.content.quest.area.karamja.piratestreasure.PiratesTreasureQuest.Companion.STAGE_MESSAGE
import org.rsmod.content.quest.area.karamja.piratestreasure.PiratesTreasureQuest.Companion.STAGE_STARTED
import org.rsmod.content.quest.area.karamja.piratestreasure.PiratesTreasureQuest.Companion.TREASURE_SPOT
import org.rsmod.content.quest.area.karamja.piratestreasure.PiratesTreasureQuest.Companion.WHITE_APRON
import org.rsmod.content.quest.area.karamja.piratestreasure.PiratesTreasureQuest.Companion.WYDIN
import org.rsmod.content.quest.area.karamja.piratestreasure.PiratesTreasureQuest.Companion.WYDIN_DOOR
import org.rsmod.content.quest.area.karamja.piratestreasure.npcs.Luthas
import org.rsmod.content.quest.area.karamja.piratestreasure.npcs.RedbeardFrank
import org.rsmod.content.quest.area.varrock.dragonslayer.DoorPassage
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
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.util.EntityFaceAngle
import org.rsmod.game.interact.InteractionOp
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.InvVirtualStorageHolder
import org.rsmod.game.inv.Inventory
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocEntity
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.map.LocZoneStorage
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.game.queue.WorldQueueList
import org.rsmod.game.region.RegionListLarge
import org.rsmod.game.region.RegionListSmall
import org.rsmod.game.region.RegionListWorldEntity
import org.rsmod.game.seq.EntitySeq
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

/**
 * Drives the quest's real scripts through the event bus: Frank's offer, the job with Luthas and
 * the rum hidden in his crate, the customs search, Wydin's job and back room, Hector's chest, the
 * gardener's ambush and the dig, plus the refusals and recoveries along the way.
 */
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class PiratesTreasureInteractionTest {

    @Test fun `the full quest path smuggles the rum and digs up the treasure once`() {
        val f = Fixture()
        f.at(FRANK_TILE)
        f.choose(1, 1)
        f.talk(REDBEARD_FRANK)
        assertEquals(STAGE_STARTED, f.stage())
        assertTrue(f.journal().contains("Karamjan rum"))

        f.at(PLANTATION)
        f.choose(1)
        f.talk(LUTHAS)
        assertTrue(f.player.ptLuthasJob)

        f.give(KARAMJA_RUM)
        f.locU(BANANA_CRATE, CRATE_TILE, KARAMJA_RUM)
        assertTrue(f.player.ptCrateRum)
        assertEquals(0, f.count(KARAMJA_RUM))
        f.give(BANANA, 10)
        f.locOp(BANANA_CRATE, CRATE_TILE, InteractionOp.Op2)
        assertEquals(10, f.player.ptCrateBananas)
        assertEquals(0, f.count(BANANA))

        f.choose(2)
        f.talk(LUTHAS)
        assertEquals(30, f.count(COINS))
        assertTrue(f.player.ptRumShipped)
        assertFalse(f.player.ptCrateRum)
        assertEquals(0, f.player.ptCrateBananas)
        assertTrue(f.journal().contains("back room"))

        f.at(STORE_TILE)
        f.give(WHITE_APRON)
        f.dialogueWith(WYDIN) { askWydinForJob() }
        assertTrue(f.player.ptWydinJob)
        assertTrue(f.said("put your white apron on first"))
        f.wearApron()

        f.at(BACK_ROOM)
        f.locOp(GROCERY_CRATE, GROCERY_TILE)
        assertEquals(1, f.count(KARAMJA_RUM))
        assertFalse(f.player.ptRumShipped)
        assertTrue(f.said("find your bottle of rum"))

        f.at(FRANK_TILE)
        f.talk(REDBEARD_FRANK)
        assertEquals(STAGE_KEY, f.stage())
        assertEquals(0, f.count(KARAMJA_RUM))
        assertEquals(1, f.count(CHEST_KEY))
        assertTrue(f.journal().contains("Blue Moon Inn"))

        f.at(CHEST_TILE.translateX(1))
        f.locOp(HECTORS_CHEST, CHEST_TILE)
        assertTrue(f.said("The chest is locked."))
        f.locU(HECTORS_CHEST, CHEST_TILE, CHEST_KEY)
        assertEquals(STAGE_MESSAGE, f.stage())
        assertEquals(1, f.count(PIRATE_MESSAGE))
        assertEquals(0, f.count(CHEST_KEY))

        f.give(SPADE)
        f.at(TREASURE_SPOT)
        f.dig()
        val gardener = checkNotNull(f.dig.gardenerOf(f.player.uid))
        assertTrue(f.player.ptGardenerAppeared)
        assertEquals(STAGE_MESSAGE, f.stage())
        f.dig()
        assertTrue(f.said("I can't dig up anything with him attacking me!"))
        assertEquals(0, f.count(CASKET))

        gardener.hitpoints = 0
        f.dig()
        assertEquals(STAGE_COMPLETE, f.stage())
        assertEquals(1, f.count(CASKET))
        assertEquals(2, f.player.vars["varp.qp"])
        assertTrue(f.player.ui.containsModal("interface.questscroll"))

        f.dig()
        assertEquals(1, f.count(CASKET))
        assertEquals(2, f.player.vars["varp.qp"])

        f.held(CASKET)
        assertEquals(0, f.count(CASKET))
        assertEquals(480, f.count(COINS))
        assertEquals(1, f.count("obj.gold_ring"))
        assertEquals(1, f.count("obj.emerald"))
    }

    @Test fun `declining frank leaves the quest unstarted`() {
        val f = Fixture()
        f.choose(1, 2)
        f.talk(REDBEARD_FRANK)
        assertEquals(0, f.stage())
        f.choose(3)
        f.talk(REDBEARD_FRANK)
        assertTrue(f.said("Customs Agents are on the warpath"))
        assertEquals(0, f.stage())
    }

    @Test fun `frank waits for plain karamjan rum`() {
        val f = Fixture(STAGE_STARTED)
        f.talk(REDBEARD_FRANK)
        assertTrue(f.said("Not surprising"))
        f.give(BANANA_RUM)
        f.talk(REDBEARD_FRANK)
        assertTrue(f.said("banana stuck in it"))
        assertEquals(1, f.count(BANANA_RUM))
        assertEquals(STAGE_STARTED, f.stage())
    }

    @Test fun `using the rum on frank hands it over`() {
        val f = Fixture(STAGE_STARTED)
        f.give(KARAMJA_RUM)
        f.npcU(REDBEARD_FRANK, KARAMJA_RUM)
        assertEquals(STAGE_KEY, f.stage())
        assertEquals(1, f.count(CHEST_KEY))
        assertTrue(f.said("Frank, I have some Karamja Rum."))
    }

    @Test fun `frank replaces a lost key until the message is found`() {
        val f = Fixture(STAGE_KEY)
        f.choose(2)
        f.talk(REDBEARD_FRANK)
        assertEquals(1, f.count(CHEST_KEY))
        assertTrue(f.said("lost my chest key"))
        f.choose(2)
        f.talk(REDBEARD_FRANK)
        assertEquals(1, f.count(CHEST_KEY))

        f.jump(STAGE_MESSAGE)
        f.drop(CHEST_KEY)
        f.give(PIRATE_MESSAGE)
        f.choose(2)
        f.talk(REDBEARD_FRANK)
        assertEquals(0, f.count(CHEST_KEY))
        f.drop(PIRATE_MESSAGE)
        f.choose(2)
        f.talk(REDBEARD_FRANK)
        assertEquals(1, f.count(CHEST_KEY))
        f.at(CHEST_TILE.translateX(1))
        f.locU(HECTORS_CHEST, CHEST_TILE, CHEST_KEY)
        assertEquals(1, f.count(PIRATE_MESSAGE))
        assertEquals(STAGE_MESSAGE, f.stage())
    }

    @Test fun `the crate is only packed for luthas`() {
        val f = Fixture(STAGE_STARTED)
        f.at(PLANTATION)
        f.give(BANANA)
        f.give(KARAMJA_RUM)
        f.locU(BANANA_CRATE, CRATE_TILE, BANANA)
        f.locU(BANANA_CRATE, CRATE_TILE, KARAMJA_RUM)
        assertTrue(f.said("Why would I want to do that?"))
        assertEquals(0, f.player.ptCrateBananas)
        assertFalse(f.player.ptCrateRum)
        assertEquals(1, f.count(BANANA))
        assertEquals(1, f.count(KARAMJA_RUM))
    }

    @Test fun `the crate takes ten bananas and one bottle`() {
        val f = Fixture(STAGE_STARTED)
        f.at(PLANTATION)
        f.player.ptLuthasJob = true
        f.give(BANANA, 12)
        f.locOp(BANANA_CRATE, CRATE_TILE, InteractionOp.Op2)
        assertEquals(10, f.player.ptCrateBananas)
        assertEquals(2, f.count(BANANA))
        f.locU(BANANA_CRATE, CRATE_TILE, BANANA)
        assertTrue(f.said("The crate is already full."))
        assertEquals(2, f.count(BANANA))

        f.give(KARAMJA_RUM, 2)
        f.locU(BANANA_CRATE, CRATE_TILE, KARAMJA_RUM)
        f.locU(BANANA_CRATE, CRATE_TILE, KARAMJA_RUM)
        assertTrue(f.said("already some rum in here"))
        assertEquals(1, f.count(KARAMJA_RUM))

        f.locOp(BANANA_CRATE, CRATE_TILE)
        assertTrue(f.said("The crate is full of bananas."))
        assertTrue(f.said("some rum stashed in here"))
    }

    @Test fun `luthas pays for crates without the quest but ships no rum`() {
        val f = Fixture()
        f.at(PLANTATION)
        f.choose(1)
        f.talk(LUTHAS)
        f.give(KARAMJA_RUM)
        f.locU(BANANA_CRATE, CRATE_TILE, KARAMJA_RUM)
        assertFalse(f.player.ptCrateRum)
        f.player.ptCrateBananas = 10
        f.choose(2)
        f.talk(LUTHAS)
        assertEquals(30, f.count(COINS))
        assertFalse(f.player.ptRumShipped)
    }

    @Test fun `customs confiscate rum found in the search`() {
        val f = Fixture(STAGE_STARTED)
        f.at(CUSTOMS_TILE)
        f.give(KARAMJA_RUM, 2)
        var boarded = false
        f.choose(2)
        f.dialogueWith(CUSTOMS) { customsBoarding { boarded = true } }
        assertFalse(boarded)
        assertEquals(0, f.count(KARAMJA_RUM))
        assertTrue(f.said("confiscates your rum"))

        f.choose(1, 2, 1)
        f.dialogueWith(CUSTOMS) { customsBoarding { boarded = true } }
        assertTrue(f.said("banned the import of intoxicating spirits"))
        assertTrue(boarded)
    }

    @Test fun `customs quick travel still confiscates rum`() {
        val f = Fixture(STAGE_STARTED)
        f.give(KARAMJA_RUM)
        var confiscated = false
        f.act { confiscated = confiscateRum() }
        assertTrue(confiscated)
        assertEquals(0, f.count(KARAMJA_RUM))
        f.act { confiscated = confiscateRum() }
        assertFalse(confiscated)
    }

    @Test fun `wydin keeps the back room for aproned staff`() {
        val f = Fixture(STAGE_STARTED)
        f.at(STORE_TILE)
        f.spawn(WYDIN, STORE_TILE.translateX(2))
        f.choose(2)
        f.locOp(WYDIN_DOOR, DOOR_TILE)
        assertTrue(f.said("Only employees of the grocery store can go in."))
        assertEquals(STORE_TILE, f.player.coords)

        f.give(WHITE_APRON)
        f.choose(1)
        f.locOp(WYDIN_DOOR, DOOR_TILE)
        assertTrue(f.player.ptWydinJob)
        f.locOp(WYDIN_DOOR, DOOR_TILE)
        assertTrue(f.said("put your white apron on before going in there"))
    }

    @Test fun `wydin turns away an applicant without an apron`() {
        val f = Fixture(STAGE_STARTED)
        f.at(STORE_TILE)
        f.dialogueWith(WYDIN) { askWydinForJob() }
        assertFalse(f.player.ptWydinJob)
        assertTrue(f.said("Gerrant's fish store"))
        assertEquals("Can I get a job here?", wydinJobOption(f.treasure, f.player))
        f.jump(STAGE_KEY)
        assertNull(wydinJobOption(f.treasure, f.player))
    }

    @Test fun `the grocery crate holds rum only once it has been shipped`() {
        val f = Fixture(STAGE_STARTED)
        f.at(BACK_ROOM)
        f.locOp(GROCERY_CRATE, GROCERY_TILE)
        assertEquals(0, f.count(KARAMJA_RUM))
        f.player.ptRumShipped = true
        f.locOp(GROCERY_CRATE, GROCERY_TILE)
        f.locOp(GROCERY_CRATE, GROCERY_TILE)
        assertEquals(1, f.count(KARAMJA_RUM))
    }

    @Test fun `digging finds nothing before the message`() {
        val f = Fixture(STAGE_KEY)
        f.give(SPADE)
        f.at(TREASURE_SPOT)
        f.dig()
        assertTrue(f.said("You dig, but find nothing of interest."))
        assertNull(f.dig.gardenerOf(f.player.uid))
        assertFalse(f.player.ptGardenerAppeared)
    }

    @Test fun `the gardener leaves when the player runs out of the park`() {
        val f = Fixture(STAGE_MESSAGE)
        f.give(SPADE)
        f.at(TREASURE_SPOT)
        f.dig()
        val gardener = checkNotNull(f.dig.gardenerOf(f.player.uid))
        f.at(TREASURE_SPOT.translateX(FaladorParkDig.PARK_RADIUS + 5))
        f.dig.tick(gardener)
        assertNull(f.dig.gardenerOf(f.player.uid))
        f.at(TREASURE_SPOT)
        f.dig()
        assertEquals(STAGE_COMPLETE, f.stage())
    }

    @Test fun `frank wants no share of the treasure or more rum`() {
        val f = Fixture(STAGE_COMPLETE)
        f.give(CASKET)
        f.npcU(REDBEARD_FRANK, CASKET)
        assertTrue(f.said("fair and square"))
        f.give(KARAMJA_RUM)
        f.npcU(REDBEARD_FRANK, KARAMJA_RUM)
        assertTrue(f.said("still have to finish this bottle"))
        assertEquals(1, f.count(KARAMJA_RUM))
    }

    @Test fun `the message reads as the scroll`() {
        val f = Fixture(STAGE_MESSAGE)
        f.give(PIRATE_MESSAGE)
        f.held(PIRATE_MESSAGE)
        assertTrue(f.player.ui.containsModal("interface.scroll"))
    }

    @Test fun `resetting the quest clears its flags but not luthas's job`() {
        val f = Fixture(STAGE_STARTED)
        f.player.ptLuthasJob = true
        f.player.ptCrateBananas = 4
        f.player.ptCrateRum = true
        f.player.ptRumShipped = true
        f.player.ptWydinJob = true
        f.player.ptGardenerAppeared = true
        f.treasure.quest.resetQuest(f.player)
        assertEquals(0, f.stage())
        assertFalse(f.player.ptCrateRum || f.player.ptRumShipped || f.player.ptWydinJob)
        assertFalse(f.player.ptGardenerAppeared)
        assertTrue(f.player.ptLuthasJob)
        assertEquals(4, f.player.ptCrateBananas)
    }

    @Test fun `save and reload keeps the stage and the smuggling flags`() {
        val f = Fixture(STAGE_STARTED)
        f.player.ptRumShipped = true
        f.player.ptWydinJob = true
        val loaded = f.saveAndReload()
        assertEquals(STAGE_STARTED, f.treasure.stage(loaded))
        assertTrue(loaded.ptRumShipped)
        assertTrue(loaded.ptWydinJob)
    }

    private class Fixture(stage: Int = 0) {
        val events = EventBus()
        private val client = RecordingClient()
        private val collision = CollisionFlagMap()
        private val npcs = NpcList()
        private val players = PlayerList()
        private val coroutine = GameCoroutine("pirates-treasure-test")
        private var result: Result<Unit>? = null
        private val context = ProtectedAccessContextFactory.empty().copy(
            getEventBus = { events }, getAlignment = { TextAlignment() },
            getCollision = { collision }, getNpcList = { npcs },
            getNpcInteractions = { NpcInteractions(events) },
            getRandom = { DefaultGameRandom(Random(7)) },
            getHitModifier = { NoopPlayerHitModifier },
        )
        private val clock = MapClock(100)
        private val objRepo = ObjRepository(clock, ObjRegistry(ZoneUpdateMap()))
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
            coords = FRANK_TILE
            currentMapClock = 100
            processedMapClock = 100
            pendingSequence = EntitySeq.NULL
            pendingFaceAngle = EntityFaceAngle.NULL
            inv = Inventory(InventoryServerType(size = 28, flags = 0), arrayOfNulls(28))
            worn = Inventory(InventoryServerType(size = 14, flags = 0), arrayOfNulls(14))
            statMap.setBaseLevel("stat.hitpoints", 10)
            statMap.setCurrentLevel("stat.hitpoints", 10)
        }

        val treasure = PiratesTreasureQuest()
        val dig: FaladorParkDig

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
            for (tile in listOf(FRANK_TILE, PLANTATION, STORE_TILE, CHEST_TILE, TREASURE_SPOT, CUSTOMS_TILE)) {
                for (dx in -24..24 step 8) for (dz in -24..24 step 8) {
                    collision.allocateIfAbsent(tile.x + dx, tile.z + dz, tile.level)
                }
            }
            players[player.slotId] = player
            val spade = SpadeDigging()
            dig = FaladorParkDig(treasure, spade, npcRepo, objRepo, players,
                AiPlayerInteractions(events, players), clock, collision)
            val scripts = ScriptContext(events, CheatCommandMap(), EngineQueueCache())
            with(treasure) { scripts.startup() }
            with(SpadeScript(setOf(spade))) { scripts.startup() }
            with(dig) { scripts.startup() }
            with(RedbeardFrank(treasure, objRepo)) { scripts.startup() }
            with(Luthas(objRepo)) { scripts.startup() }
            with(BananaCrate(treasure)) { scripts.startup() }
            with(WydinsStore(treasure, DoorPassage(locRepo, WorldQueueList(), players, collision), npcs)) {
                scripts.startup()
            }
            with(HectorsChest(treasure, locRepo, objRepo)) { scripts.startup() }
            if (stage > 0) treasure.quest.jumpToStage(player, stage)
        }

        fun access() = ProtectedAccess(player, coroutine, context)

        fun stage(): Int = treasure.stage(player)

        fun jump(stage: Int) = treasure.quest.jumpToStage(player, stage)

        fun journal(): String = treasure.questLog(access())

        fun at(tile: CoordGrid) {
            player.coords = tile
        }

        fun choose(vararg options: Int) {
            picks += options.toList()
        }

        fun give(obj: String, count: Int = 1) {
            repeat(count) {
                val slot = player.inv.indexOfFirst { it == null }
                player.inv[slot] = InvObj(obj, 1)
            }
        }

        fun wearApron() {
            drop(WHITE_APRON)
            player.worn[APRON_SLOT] = InvObj(WHITE_APRON, 1)
        }

        fun drop(obj: String) {
            val slot = player.inv.indexOfFirst { it?.id == obj.asRSCM() }
            player.inv[slot] = null
        }

        fun count(obj: String): Int = player.inv.count(obj)

        fun said(text: String): Boolean = output().contains(text)

        fun spawn(type: String, at: CoordGrid): Npc {
            val npc = Npc(type, at)
            npcRepo.add(npc, Int.MAX_VALUE)
            return npc
        }

        fun talk(type: String) {
            val npc = spawn(type, player.coords.translateX(1))
            dispatch { assertTrue(events.publish(this, NpcEvents.Op1(npc))) }
            npcRepo.del(npc, Int.MAX_VALUE)
        }

        fun dialogueWith(type: String, conversation: suspend org.rsmod.api.player.dialogue.Dialogue.() -> Unit) {
            val npc = spawn(type, player.coords.translateX(1))
            dispatch { startDialogue(npc) { conversation() } }
            npcRepo.del(npc, Int.MAX_VALUE)
        }

        fun act(block: suspend ProtectedAccess.() -> Unit) = dispatch(block)

        fun npcU(type: String, obj: String) {
            val npc = spawn(type, player.coords.translateX(1))
            val slot = player.inv.indexOfFirst { it?.id == obj.asRSCM() }
            val objType = checkNotNull(ServerCacheManager.getItem(obj.asRSCM()))
            val npcType = checkNotNull(ServerCacheManager.getNpc(type.asRSCM(RSCMType.NPC)))
            dispatch { assertTrue(events.publish(this, NpcUEvents.Op(npc, slot, objType, npcType))) }
            npcRepo.del(npc, Int.MAX_VALUE)
        }

        fun held(obj: String) {
            val slot = player.inv.indexOfFirst { it?.id == obj.asRSCM() }
            dispatch {
                assertTrue(events.publish(this, HeldObjEvents.Op1(slot, checkNotNull(player.inv[slot]),
                    checkNotNull(ServerCacheManager.getItem(obj.asRSCM())), player.inv)))
            }
        }

        fun dig() = held(SPADE)

        fun locOp(symbol: String, coords: CoordGrid, op: InteractionOp = InteractionOp.Op1) {
            val loc = bound(symbol, coords)
            val event = LocInteractions(BoundValidator(collision), events).opTrigger(player, loc, op)
            val trigger = checkNotNull(event) { "No $op handler for $symbol" }
            dispatch { assertTrue(events.publish(this, trigger)) }
        }

        fun locU(symbol: String, coords: CoordGrid, obj: String) {
            val loc = bound(symbol, coords)
            val objType = checkNotNull(ServerCacheManager.getItem(obj.asRSCM()))
            val slot = player.inv.indexOfFirst { it?.id == obj.asRSCM() }
            val locType = checkNotNull(ServerCacheManager.getObject(symbol.asRSCM()))
            val event = with(locU) { access().opTrigger(loc, loc, locType, objType, slot) }
            val trigger = checkNotNull(event) { "No $obj handler for $symbol" }
            dispatch { assertTrue(events.publish(this, trigger)) }
        }

        fun saveAndReload(): Player {
            val loaded = Player()
            loaded.vars.backing.putAll(player.vars.backing)
            player.attr[QUEST_STAGE_MAP_ATTR]?.let { loaded.attr[QUEST_STAGE_MAP_ATTR] = it.toMutableMap() }
            treasure.quest.syncState(loaded)
            return loaded
        }

        private fun bound(symbol: String, coords: CoordGrid): BoundLocInfo {
            val type = checkNotNull(ServerCacheManager.getObject(symbol.asRSCM()))
            return BoundLocInfo(LocInfo(coords.level, coords, LocEntity(type.id, 10, 0)), type)
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
        val FRANK_TILE = CoordGrid(3053, 3251, 0)
        val PLANTATION = CoordGrid(2940, 3150, 0)
        val CRATE_TILE = CoordGrid(2939, 3149, 0)
        val CUSTOMS_TILE = CoordGrid(2954, 3147, 0)
        val STORE_TILE = CoordGrid(3013, 3204, 0)
        val DOOR_TILE = CoordGrid(3012, 3204, 0)
        val BACK_ROOM = CoordGrid(3010, 3207, 0)
        val GROCERY_TILE = CoordGrid(3009, 3207, 0)
        val CHEST_TILE = CoordGrid(3219, 3396, 1)
        const val CUSTOMS = "npc.customs_officer_1op"
        const val APRON_SLOT = 4

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
