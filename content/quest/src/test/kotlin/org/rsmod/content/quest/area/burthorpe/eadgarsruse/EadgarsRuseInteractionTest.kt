@file:OptIn(InternalApi::class)

package org.rsmod.content.quest.area.burthorpe.eadgarsruse

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.InventoryServerType
import dev.openrune.types.varp.VarpLifetime
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.random.Random
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
import org.rsmod.api.player.events.interact.HeldObjEvents
import org.rsmod.api.player.events.interact.NpcEvents
import org.rsmod.api.player.hit.modifier.NoopPlayerHitModifier
import org.rsmod.api.player.hit.processor.DamageOnlyPlayerHitProcessor
import org.rsmod.api.player.hook.PlayerTeleportValidator
import org.rsmod.api.player.input.ResumePauseButtonInput
import org.rsmod.api.player.interact.HeldUInteractions
import org.rsmod.api.player.interact.LocInteractions
import org.rsmod.api.player.interact.LocUInteractions
import org.rsmod.api.player.interact.NpcInteractions
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.api.player.protect.clearPendingAction
import org.rsmod.api.random.DefaultGameRandom
import org.rsmod.api.registry.npc.NpcRegistry
import org.rsmod.api.registry.obj.ObjRegistry
import org.rsmod.api.registry.zone.ZoneUpdateMap
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.route.BoundValidator
import org.rsmod.content.generic.locs.passages.GenericPassageScript
import org.rsmod.content.quest.area.burthorpe.eadgarsruse.EadgarsRuseQuest.Companion.ALCO_CHUNKS
import org.rsmod.content.quest.area.burthorpe.eadgarsruse.EadgarsRuseQuest.Companion.BURNTMEAT
import org.rsmod.content.quest.area.burthorpe.eadgarsruse.EadgarsRuseQuest.Companion.BURNT_MEAT
import org.rsmod.content.quest.area.burthorpe.eadgarsruse.EadgarsRuseQuest.Companion.DRIED_THISTLE
import org.rsmod.content.quest.area.burthorpe.eadgarsruse.EadgarsRuseQuest.Companion.EADGAR
import org.rsmod.content.quest.area.burthorpe.eadgarsruse.EadgarsRuseQuest.Companion.FAKE_MAN
import org.rsmod.content.quest.area.burthorpe.eadgarsruse.EadgarsRuseQuest.Companion.GOUTWEED
import org.rsmod.content.quest.area.burthorpe.eadgarsruse.EadgarsRuseQuest.Companion.GRAIN
import org.rsmod.content.quest.area.burthorpe.eadgarsruse.EadgarsRuseQuest.Companion.GROUND_THISTLE
import org.rsmod.content.quest.area.burthorpe.eadgarsruse.EadgarsRuseQuest.Companion.LOGS
import org.rsmod.content.quest.area.burthorpe.eadgarsruse.EadgarsRuseQuest.Companion.PARROT
import org.rsmod.content.quest.area.burthorpe.eadgarsruse.EadgarsRuseQuest.Companion.PESTLE_AND_MORTAR
import org.rsmod.content.quest.area.burthorpe.eadgarsruse.EadgarsRuseQuest.Companion.PETE
import org.rsmod.content.quest.area.burthorpe.eadgarsruse.EadgarsRuseQuest.Companion.PINEAPPLE_CHUNKS
import org.rsmod.content.quest.area.burthorpe.eadgarsruse.EadgarsRuseQuest.Companion.RANARR_UNF
import org.rsmod.content.quest.area.burthorpe.eadgarsruse.EadgarsRuseQuest.Companion.RAW_CHICKEN
import org.rsmod.content.quest.area.burthorpe.eadgarsruse.EadgarsRuseQuest.Companion.ROBE
import org.rsmod.content.quest.area.burthorpe.eadgarsruse.EadgarsRuseQuest.Companion.SANFEW
import org.rsmod.content.quest.area.burthorpe.eadgarsruse.EadgarsRuseQuest.Companion.STAGE_ASKED_EADGAR
import org.rsmod.content.quest.area.burthorpe.eadgarsruse.EadgarsRuseQuest.Companion.STAGE_COMPLETE
import org.rsmod.content.quest.area.burthorpe.eadgarsruse.EadgarsRuseQuest.Companion.STAGE_COOK_FIRST
import org.rsmod.content.quest.area.burthorpe.eadgarsruse.EadgarsRuseQuest.Companion.STAGE_COOK_FOOLED
import org.rsmod.content.quest.area.burthorpe.eadgarsruse.EadgarsRuseQuest.Companion.STAGE_FAKE_MAN
import org.rsmod.content.quest.area.burthorpe.eadgarsruse.EadgarsRuseQuest.Companion.STAGE_FETCH_PARROT
import org.rsmod.content.quest.area.burthorpe.eadgarsruse.EadgarsRuseQuest.Companion.STAGE_HANDING_IN
import org.rsmod.content.quest.area.burthorpe.eadgarsruse.EadgarsRuseQuest.Companion.STAGE_MAKE_POTION
import org.rsmod.content.quest.area.burthorpe.eadgarsruse.EadgarsRuseQuest.Companion.STAGE_MET_COOK
import org.rsmod.content.quest.area.burthorpe.eadgarsruse.EadgarsRuseQuest.Companion.STAGE_PARROT_HIDDEN
import org.rsmod.content.quest.area.burthorpe.eadgarsruse.EadgarsRuseQuest.Companion.STAGE_PARROT_TRAINED
import org.rsmod.content.quest.area.burthorpe.eadgarsruse.EadgarsRuseQuest.Companion.STAGE_PARROT_WANTED
import org.rsmod.content.quest.area.burthorpe.eadgarsruse.EadgarsRuseQuest.Companion.STAGE_PLAN
import org.rsmod.content.quest.area.burthorpe.eadgarsruse.EadgarsRuseQuest.Companion.STAGE_STARTED
import org.rsmod.content.quest.area.burthorpe.eadgarsruse.EadgarsRuseQuest.Companion.STAGE_STOREROOM
import org.rsmod.content.quest.area.burthorpe.eadgarsruse.EadgarsRuseQuest.Companion.STOREROOM_KEY
import org.rsmod.content.quest.area.burthorpe.eadgarsruse.EadgarsRuseQuest.Companion.TEGID
import org.rsmod.content.quest.area.burthorpe.eadgarsruse.EadgarsRuseQuest.Companion.THISTLE
import org.rsmod.content.quest.area.burthorpe.eadgarsruse.EadgarsRuseQuest.Companion.THISTLE_NPC
import org.rsmod.content.quest.area.burthorpe.eadgarsruse.EadgarsRuseQuest.Companion.TROLL_POTION
import org.rsmod.content.quest.area.burthorpe.eadgarsruse.EadgarsRuseQuest.Companion.VODKA
import org.rsmod.content.quest.area.burthorpe.eadgarsruse.npcs.Burntmeat
import org.rsmod.content.quest.area.burthorpe.eadgarsruse.npcs.Eadgar
import org.rsmod.content.quest.area.burthorpe.eadgarsruse.npcs.SanfewEadgarsRuse
import org.rsmod.content.quest.area.burthorpe.trollstronghold.TrollStrongholdQuest
import org.rsmod.content.quest.area.taverley.druidicritual.DruidicRitualQuest
import org.rsmod.content.quest.area.taverley.druidicritual.npcs.Sanfew
import org.rsmod.content.quest.area.tirannwn.mourningsend.MournerClothing
import org.rsmod.content.quest.area.tirannwn.mourningsend.MourningsEndQuest
import org.rsmod.content.quest.manager.QUEST_STAGE_MAP_ATTR
import org.rsmod.content.quest.manager.QuestRequirementMode
import org.rsmod.content.quest.manager.QuestRequirementPolicy
import org.rsmod.content.quest.manager.QuestRequirements
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
import org.rsmod.game.seq.EntitySeq
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap
import sun.misc.Unsafe

/**
 * Drives Eadgar's Ruse's real scripts through the event bus: Sanfew's start and its requirements,
 * Eadgar and Burntmeat in either order, the parrot from the zoo, the rack, the fake man's
 * ingredients and Tegid's robe, the troll truth potion, the fake man, the storeroom and its
 * goutweed, the completion and Sanfew's goutweed exchange, and logging out and back in.
 */
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class EadgarsRuseInteractionTest {

    @Test fun `Sanfew checks both quests and boostable Herblore before starting`() {
        respectingProgress {
            val f = Fixture()
            f.troll.quest.jumpToStage(f.player, 0)
            f.choose(1)
            f.talk(SANFEW)
            assertEquals(0, f.stage(), "Troll Stronghold is missing")
            assertTrue(f.said("Not just yet, young 'un."))
            f.troll.quest.jumpToStage(f.player, TrollStrongholdQuest.STAGE_COMPLETE)
            f.setStat("stat.herblore", base = 25, current = 30)
            f.choose(1)
            f.talk(SANFEW)
            assertEquals(0, f.stage())
            assertTrue(f.said("not yet learned enough in the art of Herblore"))
            f.setStat("stat.herblore", base = 25, current = 31)
            f.choose(1, 2)
            f.talk(SANFEW)
            assertEquals(0, f.stage(), "declined")
            f.choose(1, 1)
            f.talk(SANFEW)
            assertEquals(STAGE_STARTED, f.stage(), "a boosted Herblore level is enough")
            assertTrue(f.journal().contains("goutweed"))
        }
    }

    @Test fun `Sanfew keeps his Druidic Ritual dialogue while that quest is under way`() {
        respectingProgress {
            val f = Fixture()
            f.ritual.quest.jumpToStage(f.player, DruidicRitualQuest.STAGE_SPOKEN_SANFEW)
            f.talk(SANFEW)
            assertTrue(f.said("Did you bring me the required ingredients"))
            assertEquals(0, f.stage())
        }
    }

    @Test fun `asking Eadgar first sends the player to the cook, who wants a human`() {
        val f = Fixture(STAGE_STARTED)
        f.choose(1)
        f.talk(EADGAR)
        assertEquals(STAGE_ASKED_EADGAR, f.stage())
        assertTrue(f.said("You should ask one of their cooks."))
        f.talk(BURNTMEAT)
        assertEquals(STAGE_MET_COOK, f.stage())
        f.choose(3)
        f.talk(EADGAR)
        assertEquals(STAGE_PARROT_WANTED, f.stage())
        assertTrue(f.said("Have you talked to the troll cook yet?"))
        assertTrue(f.said("First of all, I will need a parrot!"))
    }

    @Test fun `meeting the cook first still leads Eadgar to his plan`() {
        val f = Fixture(STAGE_STARTED)
        f.talk(BURNTMEAT)
        assertEquals(STAGE_COOK_FIRST, f.stage())
        f.talk(BURNTMEAT)
        assertTrue(f.said("Erm, not yet, but I'm working on it..."))
        f.choose(3)
        f.talk(EADGAR)
        assertEquals(STAGE_PARROT_WANTED, f.stage())
        assertTrue(f.said("Sanfew, you say?"))
    }

    @Test fun `alco-chunks need both of Pete's answers and catch one parrot`() {
        val f = Fixture(STAGE_PARROT_WANTED)
        f.give(VODKA)
        f.give(PINEAPPLE_CHUNKS, 2)
        f.use(VODKA, PINEAPPLE_CHUNKS)
        assertTrue(f.said("Why would you want to do that?"))
        assertEquals(0, f.count(ALCO_CHUNKS))
        f.choose(2)
        f.talk(PETE)
        f.choose(3)
        f.talk(PETE)
        f.locU(HATCH, HATCH_TILE, PINEAPPLE_CHUNKS)
        assertEquals(1, f.count(PINEAPPLE_CHUNKS), "plain chunks are eaten and the parrot flies off")
        assertTrue(f.said("I need some way to slow it down..."))
        f.use(PINEAPPLE_CHUNKS, VODKA)
        assertEquals(1, f.count(ALCO_CHUNKS))
        assertEquals(0, f.count(VODKA) + f.count(PINEAPPLE_CHUNKS))
        f.locU(HATCH, HATCH_TILE, ALCO_CHUNKS)
        assertEquals(1, f.count(PARROT))
        assertTrue(f.said("Sqwaawk...*hic*"))
        f.choose(3)
        f.talk(EADGAR)
        assertEquals(STAGE_PLAN, f.stage())
        assertEquals(1, f.count(PARROT), "the player keeps the parrot to hide it")
    }

    @Test fun `a lost parrot flies back to Eadgar and the hatch catches no second one`() {
        val f = Fixture(STAGE_PLAN)
        f.give(PARROT)
        f.held5(PARROT)
        assertEquals(0, f.count(PARROT))
        assertTrue(f.said("The parrot flies away."))
        f.give(ALCO_CHUNKS)
        f.locU(HATCH, HATCH_TILE, ALCO_CHUNKS)
        assertEquals(0, f.count(PARROT))
        assertTrue(f.said("you need to find it again"))
        f.choose(5, 3)
        f.talk(EADGAR)
        assertEquals(1, f.count(PARROT))
        assertTrue(f.said("One flew over here and I managed to capture it."))
    }

    @Test fun `the parrot hides under the rack and is not ready until the potion is made`() {
        val f = Fixture(STAGE_PLAN)
        f.give(PARROT)
        f.locU(RACK, RACK_TILE, PARROT)
        assertEquals(STAGE_PARROT_HIDDEN, f.stage())
        assertEquals(0, f.count(PARROT))
        f.locOp(RACK, RACK_TILE)
        assertTrue(f.said("I don't think it's done yet."))
        assertEquals(0, f.count(PARROT))
        f.jump(STAGE_FETCH_PARROT)
        f.locOp(RACK, RACK_TILE)
        assertEquals(1, f.count(PARROT))
        assertEquals(STAGE_PARROT_TRAINED, f.stage())
        f.locOp(RACK, RACK_TILE)
        assertEquals(1, f.count(PARROT), "only one parrot is under there")
    }

    @Test fun `Eadgar takes the ingredients as they come and counts what is left`() {
        val f = Fixture(STAGE_PARROT_HIDDEN)
        f.give(LOGS, 2)
        f.give(RAW_CHICKEN, 3)
        f.talk(EADGAR)
        assertEquals(STAGE_HANDING_IN, f.stage())
        assertTrue(f.said("I have the logs, three chickens, no bundles of grain and no dirty clothes."))
        assertTrue(f.said("You now need no logs, two chickens, ten bundles of grain and the dirty clothes."))
        assertEquals(1, f.count(LOGS), "only one log is wanted")
        assertEquals(0, f.count(RAW_CHICKEN))
        f.choose(2)
        f.talk(SANFEW)
        assertTrue(f.said("I think Tegid is doing his laundry outside."))
        f.choose(2)
        f.talk(TEGID)
        assertEquals(1, f.count(ROBE))
        f.choose(2)
        f.talk(TEGID)
        assertEquals(1, f.count(ROBE), "one robe is enough")
        f.give(RAW_CHICKEN, 4)
        f.give(GRAIN, 10)
        f.talk(EADGAR)
        assertEquals(STAGE_MAKE_POTION, f.stage())
        assertTrue(f.said("That's everything!"))
        assertEquals(2, f.count(RAW_CHICKEN), "the spare chickens are left")
        assertEquals(0, f.count(GRAIN) + f.count(ROBE))
    }

    @Test fun `Tegid has nothing for a player who is not after a robe`() {
        val f = Fixture()
        f.talk(TEGID)
        assertTrue(f.said("I suppose it is."))
        assertFalse(f.said("dirty robes by any chance"))
        assertEquals(0, f.count(ROBE))
    }

    @Test fun `the truth potion is picked, dried, ground and mixed in order`() {
        val f = Fixture(STAGE_PARROT_TRAINED)
        f.talkThistle()
        assertEquals(0, f.count(THISTLE), "not before Eadgar asks for it")
        f.jump(STAGE_MAKE_POTION)
        f.talkThistle()
        assertEquals(1, f.count(THISTLE))
        f.talkThistle()
        assertEquals(1, f.count(THISTLE), "one at a time")
        f.give(RANARR_UNF)
        f.use(THISTLE, RANARR_UNF)
        assertTrue(f.said("I need to dry it over a fire first."))
        f.locU("loc.fire", CoordGrid(2891, 3677, 0), THISTLE)
        assertEquals(1, f.count(DRIED_THISTLE))
        f.use(DRIED_THISTLE, RANARR_UNF)
        assertTrue(f.said("It's too big to fit in the vial."))
        f.give(PESTLE_AND_MORTAR)
        f.use(DRIED_THISTLE, PESTLE_AND_MORTAR)
        assertEquals(1, f.count(GROUND_THISTLE))
        assertEquals(1, f.count(PESTLE_AND_MORTAR))
        f.setStat("stat.herblore", base = 31, current = 30)
        f.use(RANARR_UNF, GROUND_THISTLE)
        assertEquals(0, f.count(TROLL_POTION), "Herblore 31 is needed again here")
        f.setStat("stat.herblore", base = 31, current = 31)
        f.use(GROUND_THISTLE, RANARR_UNF)
        assertEquals(1, f.count(TROLL_POTION))
        assertEquals(0, f.count(RANARR_UNF) + f.count(GROUND_THISTLE))
        f.talk(EADGAR)
        assertEquals(STAGE_FETCH_PARROT, f.stage())
        assertEquals(0, f.count(TROLL_POTION))
    }

    @Test fun `Eadgar turns the parrot into a fake man and replaces a lost one`() {
        val f = Fixture(STAGE_PARROT_TRAINED)
        f.give(PARROT)
        f.talk(EADGAR)
        assertEquals(STAGE_FAKE_MAN, f.stage())
        assertEquals(1, f.count(FAKE_MAN))
        assertEquals(0, f.count(PARROT))
        assertTrue(f.said("Where's the rest of the Guard?"))
        f.drop(FAKE_MAN)
        f.talk(EADGAR)
        assertEquals(1, f.count(FAKE_MAN))
        assertTrue(f.said("You bumbling imbecile!"))
    }

    @Test fun `Burntmeat takes the fake man, pays in burnt meat and tells of the key`() {
        val f = Fixture(STAGE_FAKE_MAN)
        f.talk(BURNTMEAT)
        assertEquals(STAGE_FAKE_MAN, f.stage(), "no fake man, no deal")
        f.give(FAKE_MAN)
        f.choose(1)
        f.talk(BURNTMEAT)
        assertEquals(STAGE_COOK_FOOLED, f.stage())
        assertEquals(1, f.count(BURNT_MEAT))
        assertEquals(0, f.count(FAKE_MAN))
        assertTrue(f.said("fake bottom of kitchen drawer"))
    }

    @Test fun `the drawer key opens the storeroom, which only locks from outside`() {
        val f = Fixture(STAGE_FAKE_MAN)
        f.locOp(DRAWERS, DRAWERS_TILE)
        assertEquals(0, f.count(STOREROOM_KEY), "nothing to find before Burntmeat lets slip")
        f.jump(STAGE_COOK_FOOLED)
        f.locOp(DRAWERS, DRAWERS_TILE)
        assertEquals(1, f.count(STOREROOM_KEY))
        f.locOp(DRAWERS, DRAWERS_TILE)
        assertEquals(1, f.count(STOREROOM_KEY))
        assertTrue(f.said("You don't find anything."))
        val door = f.bound(STOREROOM_DOOR, DOOR_TILE)
        f.player.coords = CoordGrid(2869, 10084, 0)
        f.drop(STOREROOM_KEY)
        assertFalse(f.unlock(door))
        assertTrue(f.said("This door is locked."))
        f.give(STOREROOM_KEY)
        assertTrue(f.unlock(door))
        assertEquals(STAGE_STOREROOM, f.stage())
        assertEquals(1, f.count(STOREROOM_KEY), "the key is kept")
        f.drop(STOREROOM_KEY)
        f.player.coords = CoordGrid(2869, 10086, 0)
        assertTrue(f.unlock(door), "the way out is never locked")
    }

    @Test fun `searching the goutweed crate risks the guards and pays out goutweed`() {
        val f = Fixture(STAGE_STOREROOM)
        var caught = 0
        var found = 0
        repeat(20) {
            f.player.coords = CRATE_SIDE
            val before = f.count(GOUTWEED)
            f.locOp(CRATE, CRATE_TILE)
            if (f.count(GOUTWEED) > before) {
                found++
                assertEquals(CRATE_SIDE, f.player.coords)
            } else {
                caught++
                assertEquals(CoordGrid(2869, 10084, 0), f.player.coords, "thrown out of the storeroom")
            }
        }
        assertTrue(found > 0 && caught > 0, "found=$found caught=$caught")
        f.jump(STAGE_COOK_FOOLED)
        val before = f.count(GOUTWEED)
        f.player.coords = CRATE_SIDE
        f.locOp(CRATE, CRATE_TILE)
        assertEquals(before, f.count(GOUTWEED), "no goutweed before the storeroom is unlocked")
    }

    @Test fun `Sanfew completes the quest for goutweed and then trades it for herbs`() {
        val f = Fixture(STAGE_STOREROOM)
        f.talk(SANFEW)
        assertEquals(STAGE_STOREROOM, f.stage(), "no goutweed yet")
        f.give(GOUTWEED, 3)
        val xp = f.player.statMap.getXP("stat.herblore")
        val qp = f.player.vars["varp.qp"]
        f.talk(SANFEW)
        assertEquals(STAGE_COMPLETE, f.stage())
        assertEquals(qp + 1, f.player.vars["varp.qp"])
        assertEquals(11_000, f.player.statMap.getXP("stat.herblore") - xp)
        assertEquals(2, f.count(GOUTWEED), "one goutweed for the ritual")
        f.choose(1)
        f.talk(SANFEW)
        assertEquals(0, f.count(GOUTWEED))
        val herbs = SanfewEadgarsRuse.HERB_TABLE.sumOf { f.count(it.first) }
        assertEquals(2, herbs)
        assertEquals(qp + 1, f.player.vars["varp.qp"], "rewards are given once")
        assertEquals(128, SanfewEadgarsRuse.HERB_TABLE.sumOf { it.second })
    }

    @Test fun `Eadgar trades stew for logs`() {
        val f = Fixture()
        f.choose(2, 1)
        f.talk(EADGAR)
        assertTrue(f.said("You don't have any logs!"))
        f.give(LOGS)
        f.choose(2, 1)
        f.talk(EADGAR)
        assertEquals(0, f.count(LOGS))
        assertTrue(f.said("You eat the mountain goat stew."))
    }

    @Test fun `the cave mouth leads to Eadgar and back`() {
        val f = Fixture()
        f.player.coords = CoordGrid(2893, 3671, 0)
        f.locOp(EadgarsCave.ENTRANCE, CoordGrid(2892, 3672, 0))
        assertEquals(EadgarsCave.INSIDE, f.player.coords)
        f.locOp(EadgarsCave.EXIT, CoordGrid(2892, 10072, 2))
        assertEquals(EadgarsCave.OUTSIDE, f.player.coords)
    }

    @Test fun `progress survives logging out and a reset clears the quest's own flags`() {
        val f = Fixture(STAGE_HANDING_IN)
        f.player.erLogsGiven = true
        f.player.erChickensGiven = 3
        f.player.erGrainGiven = 7
        f.player.erAskedPeteFood = true
        val loaded = f.saveAndReload()
        assertEquals(STAGE_HANDING_IN, f.eadgarsRuse.stage(loaded))
        assertTrue(loaded.erLogsGiven)
        assertEquals(3, loaded.erChickensGiven)
        assertEquals(7, loaded.erGrainGiven)
        assertTrue(loaded.erAskedPeteFood)
        f.eadgarsRuse.quest.jumpToStage(loaded, 0)
        assertFalse(loaded.erLogsGiven || loaded.erAskedPeteFood)
        assertEquals(0, loaded.erChickensGiven + loaded.erGrainGiven)
    }

    private class Fixture(stage: Int = 0) {
        val events = EventBus()
        private val client = RecordingClient()
        private val collision = CollisionFlagMap()
        private val npcs = NpcList()
        private val players = PlayerList()
        private val coroutine = GameCoroutine("eadgar-test")
        private var result: Result<Unit>? = null
        private val validator = PlayerTeleportValidator(emptySet())
        private val rng = DefaultGameRandom(Random(7))
        private val context = ProtectedAccessContextFactory.empty().copy(
            getEventBus = { events }, getAlignment = { TextAlignment() },
            getCollision = { collision }, getNpcList = { npcs },
            getTeleportValidator = { validator },
            getAreaChecker = { unused<AreaChecker>() },
            getNpcInteractions = { NpcInteractions(events) },
            getRandom = { rng },
            getHitModifier = { NoopPlayerHitModifier },
            getInstantHitProcessor = { DamageOnlyPlayerHitProcessor(events, npcs, players) },
        )
        val clock = MapClock(100)
        private val objRepo = ObjRepository(clock, ObjRegistry(ZoneUpdateMap()))
        private val npcRepo: NpcRepository
        private val picks = ArrayDeque<Int>()
        private val locU = LocUInteractions::class.java.getDeclaredConstructor(EventBus::class.java)
            .apply { isAccessible = true }.newInstance(events)
        private val heldU = HeldUInteractions(events)

        val player = newPlayer(7171L, 1)

        val eadgarsRuse = EadgarsRuseQuest()
        val troll = TrollStrongholdQuest()
        val ritual = DruidicRitualQuest()
        private val mourning = MourningsEndQuest()
        val storeroom: TrollStoreroom

        init {
            for ((x0, z0, x1, z1, level) in AREAS) {
                for (x in x0..x1 step 8) for (z in z0..z1 step 8) collision.allocateIfAbsent(x, z, level)
            }
            players[player.slotId] = player
            npcRepo = NpcRepository(clock, NpcRegistry(npcs, collision, events), npcs)
            storeroom = TrollStoreroom(eadgarsRuse, unused<GenericPassageScript>(), npcRepo, objRepo)
            val scripts = ScriptContext(events, CheatCommandMap(), EngineQueueCache())
            for (script in listOf(
                eadgarsRuse, troll, ritual, mourning, storeroom,
                Eadgar(eadgarsRuse, objRepo), Burntmeat(eadgarsRuse, objRepo),
                ArdougneAviary(eadgarsRuse), TrollThistle(eadgarsRuse), EadgarsCave(),
                Sanfew(ritual, SanfewEadgarsRuse(eadgarsRuse)), MournerClothing(mourning),
            )) {
                with(script) { scripts.startup() }
            }
            ritual.quest.jumpToStage(player, DruidicRitualQuest.STAGE_COMPLETE)
            troll.quest.jumpToStage(player, TrollStrongholdQuest.STAGE_COMPLETE)
            if (stage > 0) eadgarsRuse.quest.jumpToStage(player, stage)
        }

        private fun newPlayer(id: Long, slot: Int) = Player().apply {
            this.client = this@Fixture.client
            uuid = id
            observerUUID = id
            slotId = slot
            assignUid()
            coords = CoordGrid(2890, 10084, 2)
            currentMapClock = 100
            processedMapClock = 100
            pendingSequence = EntitySeq.NULL
            pendingFaceAngle = EntityFaceAngle.NULL
            inv = Inventory(InventoryServerType(size = 28, flags = 0), arrayOfNulls(28))
            worn = Inventory(InventoryServerType(size = 14, flags = 0), arrayOfNulls(14))
            for ((stat, level) in listOf("stat.hitpoints" to 70, "stat.herblore" to 40)) {
                statMap.setBaseLevel(stat, level.toByte())
                statMap.setCurrentLevel(stat, level.toByte())
            }
        }

        fun access(p: Player = player) = ProtectedAccess(p, coroutine, context)

        fun stage(): Int = eadgarsRuse.stage(player)

        fun jump(stage: Int) = eadgarsRuse.quest.jumpToStage(player, stage)

        fun journal(): String = eadgarsRuse.questLog(access())

        fun choose(vararg options: Int) {
            picks += options.toList()
        }

        fun setStat(stat: String, base: Int, current: Int) {
            player.statMap.setBaseLevel(stat, base.toByte())
            player.statMap.setCurrentLevel(stat, current.toByte())
        }

        fun give(obj: String, count: Int = 1) {
            repeat(count) { player.inv[player.inv.indexOfFirst { it == null }] = InvObj(obj, 1) }
        }

        fun drop(obj: String) {
            val slot = player.inv.indexOfFirst { it?.id == obj.asRSCM() }
            if (slot >= 0) player.inv[slot] = null
        }

        fun count(obj: String): Int = player.inv.count(obj)

        fun said(text: String): Boolean = output().contains(text)

        fun talk(type: String, at: CoordGrid = player.coords.translateX(1)) {
            val npc = Npc(type, at)
            npcRepo.add(npc, Int.MAX_VALUE)
            publish(NpcEvents.Op1(npc))
            if (npc.isSlotAssigned) npcRepo.del(npc, Int.MAX_VALUE)
        }

        fun talkThistle() = talk(THISTLE_NPC)

        fun held5(obj: String) {
            val slot = player.inv.indexOfFirst { it?.id == obj.asRSCM() }
            val type = checkNotNull(ServerCacheManager.getItem(obj.asRSCM()))
            dispatch { assertTrue(events.publish(this, HeldObjEvents.Op5(slot, checkNotNull(inv[slot]), type, inv))) }
        }

        fun use(first: String, second: String) {
            val a = player.inv.indexOfFirst { it?.id == first.asRSCM() }
            val b = player.inv.indexOfLast { it?.id == second.asRSCM() }
            check(a >= 0 && b >= 0) { "missing $first or $second" }
            val typeA = checkNotNull(ServerCacheManager.getItem(first.asRSCM()))
            val typeB = checkNotNull(ServerCacheManager.getItem(second.asRSCM()))
            dispatch { heldU.interact(this, inv, typeA, a, typeB, b) }
        }

        fun locOp(symbol: String, coords: CoordGrid, op: InteractionOp = InteractionOp.Op1) {
            val loc = bound(symbol, coords)
            val event = LocInteractions(BoundValidator(collision), events).opTrigger(player, loc, op)
            publish(checkNotNull(event) { "No $op handler for $symbol" })
        }

        fun locU(symbol: String, coords: CoordGrid, obj: String) {
            val loc = bound(symbol, coords)
            val locType = checkNotNull(ServerCacheManager.getObject(symbol.asRSCM()))
            val objType = checkNotNull(ServerCacheManager.getItem(obj.asRSCM()))
            val slot = player.inv.indexOfFirst { it?.id == obj.asRSCM() }
            val event = with(locU) { access().opTrigger(loc, loc, locType, objType, slot) }
            publish(checkNotNull(event) { "No $obj handler for $symbol" })
        }

        fun unlock(door: BoundLocInfo): Boolean {
            var opened = false
            dispatch { opened = with(storeroom) { unlockStoreroom(door) } }
            return opened
        }

        fun saveAndReload(): Player {
            val loaded = newPlayer(player.uuid ?: 0L, 3)
            for ((varp, value) in player.vars.backing) {
                val scope = ServerCacheManager.getVarp(varp)?.scope
                if (scope != VarpLifetime.Temp) loaded.vars.backing[varp] = value
            }
            player.attr[QUEST_STAGE_MAP_ATTR]?.let { loaded.attr[QUEST_STAGE_MAP_ATTR] = it.toMutableMap() }
            eadgarsRuse.quest.syncState(loaded)
            return loaded
        }

        fun bound(symbol: String, coords: CoordGrid): BoundLocInfo {
            val type = checkNotNull(ServerCacheManager.getObject(symbol.asRSCM()))
            return BoundLocInfo(LocInfo(0, coords, LocEntity(type.id, 10, 0)), type)
        }

        private fun publish(event: SuspendEvent<ProtectedAccess>) {
            dispatch { assertTrue(events.publish(this, event)) }
        }

        fun dispatch(block: suspend ProtectedAccess.() -> Unit) {
            val p = player
            p.clearPendingAction(events)
            result = null
            p.activeCoroutine = coroutine
            val access = access(p)
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
                step(p)
                result?.getOrThrow()
            }
            fail<Unit>("Interaction did not finish: ${output()}")
        }

        private fun step(p: Player) {
            if (coroutine.isAwaiting(ResumePauseButtonInput::class)) {
                val parent = listOf("chat_left", "chat_right", "messagebox", "chatmenu", "objectbox", "objectbox_double")
                    .firstOrNull { p.ui.containsModal("interface.$it") }
                    ?: error("Unknown dialogue: ${output()}")
                val input = when (parent) {
                    "chatmenu" -> ResumePauseButtonInput("component.chatmenu:options", picks.removeFirstOrNull() ?: 1)
                    "objectbox" -> ResumePauseButtonInput("component.objectbox:universe", -1)
                    "objectbox_double" -> ResumePauseButtonInput("component.objectbox_double:pausebutton", -1)
                    else -> ResumePauseButtonInput("component.$parent:continue", -1)
                }
                coroutine.resumeWith(input)
            } else {
                p.currentMapClock++
                p.processedMapClock = p.currentMapClock
                p.pendingSequence = EntitySeq.NULL
                p.pendingFaceAngle = EntityFaceAngle.NULL
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
        const val HATCH = "loc.eadgar_aviary_wall_hatch"
        const val RACK = "loc.eadgar_rack"
        const val DRAWERS = "loc.eadgar_kitchen_drawers"
        const val STOREROOM_DOOR = "loc.eadgar_storeroomdoor"
        const val CRATE = "loc.eadgar_crate_goutweed"
        val HATCH_TILE = CoordGrid(2611, 3287, 0)
        val RACK_TILE = CoordGrid(2828, 10096, 0)
        val DRAWERS_TILE = CoordGrid(2852, 10049, 1)
        val DOOR_TILE = CoordGrid(2869, 10085, 0)
        val CRATE_TILE = CoordGrid(2857, 10074, 0)
        val CRATE_SIDE = CoordGrid(2858, 10075, 0)

        /** x0, z0, x1, z1 and level of every area a test stands the player in. */
        val AREAS =
            listOf(
                intArrayOf(2880, 10064, 2905, 10095, 2),
                intArrayOf(2880, 3660, 2905, 3685, 0),
                intArrayOf(2816, 10048, 2879, 10111, 0),
                intArrayOf(2816, 10048, 2879, 10111, 1),
                intArrayOf(2600, 3275, 2620, 3295, 0),
                intArrayOf(2900, 3405, 2925, 3430, 0),
            )

        private fun respectingProgress(block: () -> Unit) {
            val previous = QuestRequirements.activePolicy()
            QuestRequirements.install(QuestRequirementPolicy(QuestRequirementMode.RespectProgress))
            try {
                block()
            } finally {
                QuestRequirements.install(previous)
            }
        }

        private inline fun <reified T> unused(): T {
            val field = Unsafe::class.java.getDeclaredField("theUnsafe").apply { isAccessible = true }
            return (field.get(null) as Unsafe).allocateInstance(T::class.java) as T
        }

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
