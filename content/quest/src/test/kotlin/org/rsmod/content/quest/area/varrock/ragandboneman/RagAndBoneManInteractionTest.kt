package org.rsmod.content.quest.area.varrock.ragandboneman

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
import org.rsmod.api.player.dialogue.align.TextAlignment
import org.rsmod.api.player.events.interact.HeldObjEvents
import org.rsmod.api.player.events.interact.NpcEvents
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
import org.rsmod.api.registry.zone.ZoneUpdateMap
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.route.BoundValidator
import org.rsmod.api.shops.Shops
import org.rsmod.content.quest.area.varrock.ragandboneman.RagAndBoneManQuest.Companion.BOILER_BOILED
import org.rsmod.content.quest.area.varrock.ragandboneman.RagAndBoneManQuest.Companion.BOILER_BOILING
import org.rsmod.content.quest.area.varrock.ragandboneman.RagAndBoneManQuest.Companion.BOILER_EMPTY
import org.rsmod.content.quest.area.varrock.ragandboneman.RagAndBoneManQuest.Companion.BOILER_LOADED
import org.rsmod.content.quest.area.varrock.ragandboneman.RagAndBoneManQuest.Companion.BOILER_LOGS
import org.rsmod.content.quest.area.varrock.ragandboneman.RagAndBoneManQuest.Companion.COINS
import org.rsmod.content.quest.area.varrock.ragandboneman.RagAndBoneManQuest.Companion.FORTUNATO
import org.rsmod.content.quest.area.varrock.ragandboneman.RagAndBoneManQuest.Companion.JUG
import org.rsmod.content.quest.area.varrock.ragandboneman.RagAndBoneManQuest.Companion.LOGS
import org.rsmod.content.quest.area.varrock.ragandboneman.RagAndBoneManQuest.Companion.ODD_OLD_MAN
import org.rsmod.content.quest.area.varrock.ragandboneman.RagAndBoneManQuest.Companion.POT
import org.rsmod.content.quest.area.varrock.ragandboneman.RagAndBoneManQuest.Companion.POT_OF_VINEGAR
import org.rsmod.content.quest.area.varrock.ragandboneman.RagAndBoneManQuest.Companion.STAGE_BOILED
import org.rsmod.content.quest.area.varrock.ragandboneman.RagAndBoneManQuest.Companion.STAGE_COMPLETE
import org.rsmod.content.quest.area.varrock.ragandboneman.RagAndBoneManQuest.Companion.STAGE_STARTED
import org.rsmod.content.quest.area.varrock.ragandboneman.RagAndBoneManQuest.Companion.STAGE_VINEGAR
import org.rsmod.content.quest.area.varrock.ragandboneman.RagAndBoneManQuest.Companion.TINDERBOX
import org.rsmod.content.quest.area.varrock.ragandboneman.RagAndBoneManQuest.Companion.VINEGAR
import org.rsmod.content.quest.area.varrock.ragandboneman.npcs.Fortunato
import org.rsmod.content.quest.area.varrock.ragandboneman.npcs.OddOldMan
import org.rsmod.content.quest.manager.QUEST_STAGE_MAP_ATTR
import org.rsmod.content.quest.manager.QuestItemDrops
import org.rsmod.coroutine.GameCoroutine
import org.rsmod.events.EventBus
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.client.Client
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
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.game.seq.EntitySeq
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

/**
 * Drives Rag and Bone Man I's real scripts through the event bus: the whole quest, specimens
 * cleaned in different orders, quest drops before, during and after the quest, Fortunato's vinegar
 * and every missing coin, pot, log, tinderbox and slot, ordinary and polished bones turned away,
 * the boiler's order and an occupied boiler, full packs, logging out mid-boil, a thrown-away
 * specimen coming back, unfinished collections refused, and rewards given once.
 */
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class RagAndBoneManInteractionTest {

    @Test fun `the whole quest, from the wish-list to the reward scroll`() {
        val f = Fixture()
        f.start()
        assertEquals(STAGE_STARTED, f.stage())
        assertTrue(f.said("wish-list has been added"))
        assertTrue(f.journal().contains("Fortunato"))
        for (specimen in Specimen.entries) assertTrue(f.journal().contains(specimen.label), specimen.label)

        f.give(COINS, 8)
        f.unlockVinegar(buy = 8)
        assertEquals(STAGE_VINEGAR, f.stage())
        assertEquals(8, f.count(VINEGAR))
        assertEquals(0, f.count(COINS))

        f.give(TINDERBOX)
        for (specimen in Specimen.entries) {
            f.give(specimen.raw)
            f.give(POT)
            f.give(LOGS)
            f.clean(specimen)
            assertEquals(1, f.count(specimen.polished), specimen.label)
        }
        assertEquals(STAGE_BOILED, f.stage())
        assertEquals(8, f.count(POT), "every pot comes back")
        assertEquals(8, f.count(JUG))
        assertEquals(0, f.count(LOGS))
        assertEquals(1, f.count(TINDERBOX), "the tinderbox is reusable")
        assertEquals(0, f.xp("stat.firemaking"), "lighting the boiler is no Firemaking")
        assertTrue(f.journal().contains("all eight polished"))

        f.talk(ODD_OLD_MAN)
        assertEquals(STAGE_COMPLETE, f.stage())
        for (specimen in Specimen.entries) assertEquals(0, f.count(specimen.polished))
        assertEquals(1, f.player.vars["varp.qp"])
        assertEquals(500, f.xp("stat.cooking"))
        assertEquals(500, f.xp("stat.prayer"))
        assertTrue(f.player.ui.containsModal("interface.questscroll"))
        assertTrue(f.completedJournal().contains("QUEST COMPLETE"))
    }

    @Test fun `specimens can be cleaned in any order and interleaved`() {
        val f = Fixture(STAGE_VINEGAR)
        f.give(TINDERBOX)
        val order = Specimen.entries.reversed()
        for (specimen in order) f.give(specimen.raw)
        repeat(8) { f.give(POT); f.give(VINEGAR) }
        for (specimen in listOf(Specimen.GIANT_BAT, Specimen.GOBLIN, Specimen.UNICORN)) {
            f.use(VINEGAR, POT)
            f.use(specimen.raw, POT_OF_VINEGAR)
        }
        assertEquals(1, f.count(Specimen.GOBLIN.inVinegar))
        assertEquals(SpecimenState.IN_VINEGAR, f.state(Specimen.GOBLIN))
        assertEquals(SpecimenState.RAW, f.state(Specimen.RAM))
        for (specimen in listOf(Specimen.GOBLIN, Specimen.GIANT_BAT, Specimen.UNICORN)) {
            f.give(LOGS)
            f.onBoiler(LOGS)
            f.onBoiler(specimen.inVinegar)
            f.onBoiler(TINDERBOX)
            assertEquals(SpecimenState.BOILING, f.state(specimen))
            f.boil()
            assertEquals(SpecimenState.BOILED, f.state(specimen))
            f.boilerOp()
            assertEquals(SpecimenState.POLISHED, f.state(specimen))
        }
        assertEquals(listOf(Specimen.GIANT_RAT, Specimen.BEAR, Specimen.RAM, Specimen.BIG_FROG, Specimen.MONKEY),
            f.rb.unfinished(f.player))
    }

    @Test fun `the pot of vinegar takes a specimen whichever way round it is used`() {
        val f = Fixture(STAGE_VINEGAR)
        f.give(Specimen.MONKEY.raw)
        f.give(POT_OF_VINEGAR)
        f.use(POT_OF_VINEGAR, Specimen.MONKEY.raw)
        assertEquals(1, f.count(Specimen.MONKEY.inVinegar))
        f.give(Specimen.BEAR.raw)
        f.give(POT_OF_VINEGAR)
        f.use(Specimen.BEAR.raw, POT_OF_VINEGAR)
        assertEquals(1, f.count(Specimen.BEAR.inVinegar), "bear ribs keep their own identity")
        assertEquals(0, f.count(POT_OF_VINEGAR))
        f.give(POT)
        f.give(VINEGAR)
        f.use(POT, VINEGAR)
        assertEquals(1, f.count(POT_OF_VINEGAR))
        assertEquals(1, f.count(JUG))
    }

    @Test fun `quest drops only during the quest and only while the specimen is needed`() {
        val f = Fixture()
        val rat = Specimen.GIANT_RAT
        assertFalse(f.rb.isNeeded(f.player, rat), "no drops before the quest is accepted")
        assertFalse(f.needsDrop(rat))
        f.start()
        for (specimen in Specimen.entries) assertTrue(f.needsDrop(specimen), specimen.label)

        f.give(rat.raw)
        assertFalse(f.needsDrop(rat), "a held specimen isn't dropped again")
        f.drop(rat.raw)
        f.bank(rat.raw)
        assertFalse(f.needsDrop(rat), "nor a banked one")
        f.unbank()
        assertTrue(f.needsDrop(rat), "a lost specimen drops again")

        f.give(rat.inVinegar)
        assertFalse(f.needsDrop(rat))
        f.drop(rat.inVinegar)
        f.give(rat.polished)
        assertFalse(f.needsDrop(rat))
        assertTrue(f.needsDrop(Specimen.UNICORN), "each specimen is tracked on its own")

        f.jump(STAGE_COMPLETE)
        assertFalse(f.needsDrop(Specimen.UNICORN), "no drops after the quest")
    }

    @Test fun `a specimen loaded in the boiler isn't dropped again`() {
        val f = Fixture(STAGE_VINEGAR)
        f.give(Specimen.RAM.inVinegar)
        f.give(LOGS)
        f.onBoiler(LOGS)
        f.onBoiler(Specimen.RAM.inVinegar)
        assertEquals(SpecimenState.BOILING, f.state(Specimen.RAM))
        assertFalse(f.needsDrop(Specimen.RAM))
    }

    @Test fun `Fortunato keeps his vinegar back until the Odd Old Man is mentioned`() {
        val f = Fixture()
        f.give(COINS, 10)
        f.choose(2)
        f.talk(FORTUNATO)
        assertTrue(f.said("purveyor of fine wines"))
        assertEquals(0, f.count(VINEGAR), "no vinegar before the quest")
        assertFalse(f.rb.isVinegarUnlocked(f.player))

        f.jump(STAGE_STARTED)
        f.choose(3)
        f.talk(FORTUNATO)
        assertFalse(f.rb.isVinegarUnlocked(f.player), "walking away unlocks nothing")
        f.unlockVinegar(buy = 1)
        assertTrue(f.said("will now sell you jugs of vinegar"))
        assertEquals(1, f.count(VINEGAR))
        assertEquals(9, f.count(COINS))
        f.choose(2, 2)
        f.talk(FORTUNATO)
        assertEquals(6, f.count(VINEGAR), "five more at a coin each")
        assertEquals(4, f.count(COINS))
    }

    @Test fun `vinegar is never sold without the coins or the room`() {
        val f = Fixture(STAGE_VINEGAR)
        f.give(COINS, 3)
        f.choose(2, 3)
        f.talk(FORTUNATO)
        assertTrue(f.said("you've only 3"))
        assertEquals(0, f.count(VINEGAR))
        assertEquals(3, f.count(COINS))

        f.give(COINS, 5)
        f.fill(leave = 4)
        f.choose(2, 3)
        f.talk(FORTUNATO)
        assertTrue(f.said("haven't room for 8"))
        assertEquals(0, f.count(VINEGAR))
        assertEquals(8, f.count(COINS))

        f.choose(2, 1)
        f.talk(FORTUNATO)
        assertEquals(1, f.count(VINEGAR))
    }

    @Test fun `missing pots, vinegar, logs or tinderbox stop the cleaning without using anything`() {
        val f = Fixture(STAGE_VINEGAR)
        val goblin = Specimen.GOBLIN
        f.give(goblin.raw)
        f.give(VINEGAR)
        f.use(goblin.raw, VINEGAR)
        assertTrue(f.said("Pour the vinegar into an empty pot first"))
        f.give(POT)
        f.use(goblin.raw, POT)
        assertTrue(f.said("fill the pot with vinegar"))
        assertEquals(1, f.count(goblin.raw))
        assertEquals(1, f.count(VINEGAR))

        f.use(VINEGAR, POT)
        f.use(POT_OF_VINEGAR, goblin.raw)
        f.onBoiler(goblin.inVinegar)
        assertTrue(f.said("put a log beneath"))
        assertEquals(1, f.count(goblin.inVinegar))
        assertEquals(BOILER_EMPTY, f.boilerState())

        f.give(LOGS)
        f.give(TINDERBOX)
        f.onBoiler(TINDERBOX)
        assertTrue(f.said("nothing beneath the pot-boiler to light"))
        f.onBoiler(LOGS)
        f.onBoiler(TINDERBOX)
        assertTrue(f.said("Put a pot on the boiler before you light"))
        assertEquals(BOILER_LOGS, f.boilerState(), "lighting too early wastes nothing")

        f.onBoiler(goblin.inVinegar)
        assertEquals(BOILER_LOADED, f.boilerState())
        assertEquals(0, f.count(goblin.inVinegar))
        f.drop(TINDERBOX)
        f.boil()
        assertEquals(BOILER_LOADED, f.boilerState(), "nothing boils until it's lit")
    }

    @Test fun `ordinary bones and polished specimens are turned away`() {
        val f = Fixture(STAGE_VINEGAR)
        f.give("obj.bones")
        f.give(POT_OF_VINEGAR)
        f.use("obj.bones", POT_OF_VINEGAR)
        assertTrue(f.said("not just any old bones"))
        assertEquals(1, f.count(POT_OF_VINEGAR))
        assertEquals(1, f.count("obj.bones"))
        f.use(POT_OF_VINEGAR, "obj.bones")
        assertEquals(1, f.count(POT_OF_VINEGAR))

        f.give(Specimen.RAM.polished)
        f.use(Specimen.RAM.polished, POT_OF_VINEGAR)
        assertTrue(f.said("already polished"))
        assertEquals(1, f.count(Specimen.RAM.polished))

        f.give(LOGS)
        f.onBoiler(LOGS)
        f.onBoiler("obj.bones")
        f.onBoiler(Specimen.RAM.polished)
        f.onBoiler(POT_OF_VINEGAR)
        f.give(Specimen.RAM.raw)
        f.onBoiler(Specimen.RAM.raw)
        assertTrue(f.said("needs to soak in a pot of vinegar"))
        assertEquals(BOILER_LOGS, f.boilerState())
        assertEquals(1, f.count("obj.bones"))
        assertEquals(1, f.count(POT_OF_VINEGAR))

        val boiler = Fixture(STAGE_VINEGAR)
        boiler.give("obj.oak_logs")
        boiler.onBoiler("obj.oak_logs")
        assertTrue(boiler.said("Plain ordinary logs will do"))
        assertEquals(BOILER_EMPTY, boiler.boilerState())
    }

    @Test fun `an occupied boiler takes no second log or pot, and repeated clicks use nothing twice`() {
        val f = Fixture(STAGE_VINEGAR)
        f.give(LOGS, 2)
        f.onBoiler(LOGS)
        f.onBoiler(LOGS)
        assertTrue(f.said("already a log"))
        assertEquals(1, f.count(LOGS))

        f.give(Specimen.UNICORN.inVinegar)
        f.give(Specimen.BEAR.inVinegar)
        f.onBoiler(Specimen.UNICORN.inVinegar)
        f.onBoiler(Specimen.BEAR.inVinegar)
        assertTrue(f.said("already a pot on the pot-boiler"))
        assertEquals(1, f.count(Specimen.BEAR.inVinegar))
        assertEquals(Specimen.UNICORN, f.rb.boilerSpecimen(f.player))

        f.give(TINDERBOX)
        f.onBoiler(TINDERBOX)
        f.onBoiler(TINDERBOX)
        assertTrue(f.said("already burning"))
        assertEquals(PotBoiler.BOIL_STEPS, f.rb.boilStepsLeft(f.player), "a second light doesn't restart the boil")
        f.boilerOp()
        assertEquals(BOILER_BOILING, f.boilerState(), "an unfinished pot can't be taken as polished")
        assertEquals(0, f.count(Specimen.UNICORN.polished))
        f.onBoiler(LOGS)
        f.onBoiler(Specimen.BEAR.inVinegar)
        assertEquals(1, f.count(LOGS))
        assertEquals(1, f.count(Specimen.BEAR.inVinegar))

        f.boil()
        f.boilerOp()
        f.boilerOp()
        assertEquals(1, f.count(Specimen.UNICORN.polished), "one boil, one polished specimen")
        assertEquals(1, f.count(POT))
        assertEquals(BOILER_EMPTY, f.boilerState())
    }

    @Test fun `a loaded pot can be lifted back off before lighting`() {
        val f = Fixture(STAGE_VINEGAR)
        f.give(LOGS)
        f.give(Specimen.MONKEY.inVinegar)
        f.onBoiler(LOGS)
        f.onBoiler(Specimen.MONKEY.inVinegar)
        f.fill()
        f.boilerOp()
        assertTrue(f.said("don't have room"))
        assertEquals(BOILER_LOADED, f.boilerState())
        f.player.inv[0] = null
        f.boilerOp()
        assertEquals(1, f.count(Specimen.MONKEY.inVinegar))
        assertEquals(BOILER_LOGS, f.boilerState(), "the log stays put")
    }

    @Test fun `a full pack leaves the finished specimen waiting in the boiler`() {
        val f = Fixture(STAGE_VINEGAR)
        f.give(TINDERBOX)
        f.give(LOGS)
        f.give(Specimen.BIG_FROG.inVinegar)
        f.onBoiler(LOGS)
        f.onBoiler(Specimen.BIG_FROG.inVinegar)
        f.onBoiler(TINDERBOX)
        f.boil()
        f.fill(leave = 1)
        f.boilerOp()
        assertTrue(f.said("two free inventory spaces"))
        assertEquals(BOILER_BOILED, f.boilerState())
        assertEquals(SpecimenState.BOILED, f.state(Specimen.BIG_FROG))
        assertEquals(0, f.count(Specimen.BIG_FROG.polished))
        f.player.inv[0] = null
        f.boilerOp()
        assertEquals(1, f.count(Specimen.BIG_FROG.polished))
        assertEquals(1, f.count(POT))
    }

    @Test fun `logging out mid-boil keeps the pot and the steps left`() {
        val f = Fixture(STAGE_VINEGAR)
        f.give(TINDERBOX)
        f.give(LOGS)
        f.give(Specimen.GIANT_BAT.inVinegar)
        f.onBoiler(LOGS)
        f.onBoiler(Specimen.GIANT_BAT.inVinegar)
        f.onBoiler(TINDERBOX)
        f.boiler.boilStep(f.player)
        assertEquals(PotBoiler.BOIL_STEPS - 1, f.rb.boilStepsLeft(f.player))

        val loaded = f.saveAndReload()
        assertEquals(BOILER_BOILING, f.rb.boilerState(loaded))
        assertEquals(Specimen.GIANT_BAT, f.rb.boilerSpecimen(loaded))
        assertEquals(PotBoiler.BOIL_STEPS - 1, f.rb.boilStepsLeft(loaded))
        f.adopt(loaded)
        f.boiler.resume(f.player)
        repeat(PotBoiler.BOIL_STEPS - 1) { f.boiler.boilStep(f.player) }
        assertEquals(BOILER_BOILED, f.boilerState())

        val again = f.saveAndReload()
        assertEquals(BOILER_BOILED, f.rb.boilerState(again), "a finished result waits across a relog")
        f.adopt(again)
        f.boilerOp()
        assertEquals(1, f.count(Specimen.GIANT_BAT.polished))
    }

    @Test fun `emptying a soaking pot asks first and lets the specimen be found again`() {
        val f = Fixture(STAGE_VINEGAR)
        f.give(Specimen.UNICORN.inVinegar)
        f.choose(2)
        f.op4(Specimen.UNICORN.inVinegar)
        assertEquals(1, f.count(Specimen.UNICORN.inVinegar), "declining keeps it")
        assertFalse(f.needsDrop(Specimen.UNICORN))
        f.choose(1)
        f.op4(Specimen.UNICORN.inVinegar)
        assertEquals(0, f.count(Specimen.UNICORN.inVinegar))
        assertEquals(1, f.count(POT))
        assertTrue(f.said("You'll need to find another"))
        assertTrue(f.needsDrop(Specimen.UNICORN), "the unicorn drops another")

        f.give(VINEGAR)
        f.op4(VINEGAR)
        assertEquals(1, f.count(JUG))
        f.give(POT_OF_VINEGAR)
        f.op4(POT_OF_VINEGAR)
        assertEquals(2, f.count(POT))
    }

    @Test fun `the Odd Old Man refuses an incomplete or unpolished collection and lists what's missing`() {
        val f = Fixture(STAGE_VINEGAR)
        for (specimen in Specimen.entries.dropLast(2)) f.give(specimen.polished)
        f.give(Specimen.MONKEY.raw)
        f.give(Specimen.GIANT_BAT.inVinegar)
        f.choose(1)
        f.talk(ODD_OLD_MAN)
        assertEquals(STAGE_VINEGAR, f.stage())
        assertTrue(f.said("6 polished"))
        assertTrue(f.said("Monkey paw: found, needs cleaning"))
        assertTrue(f.said("Giant bat wing: soaking in a pot of vinegar"))
        assertTrue(f.said("raw or half-soaked"))
        for (specimen in Specimen.entries.dropLast(2)) assertEquals(1, f.count(specimen.polished), "nothing is taken")

        f.drop(Specimen.GIANT_BAT.inVinegar)
        f.give(Specimen.GIANT_BAT.polished)
        f.drop(Specimen.MONKEY.raw)
        f.bank(Specimen.MONKEY.polished)
        f.choose(1)
        f.talk(ODD_OLD_MAN)
        assertTrue(f.said("Monkey paw: polished, but not on you"))
        assertEquals(STAGE_VINEGAR, f.stage())
        assertTrue(f.rb.hint(f.player).contains("bank"))
    }

    @Test fun `the collection is handed over once and rewarded once`() {
        val f = Fixture(STAGE_BOILED)
        for (specimen in Specimen.entries) f.give(specimen.polished)
        f.talk(ODD_OLD_MAN)
        assertEquals(STAGE_COMPLETE, f.stage())
        for (specimen in Specimen.entries) f.give(specimen.polished)
        f.talk(ODD_OLD_MAN)
        assertTrue(f.said("favourite supplier"))
        assertEquals(1, f.player.vars["varp.qp"])
        assertEquals(500, f.xp("stat.cooking"))
        assertEquals(500, f.xp("stat.prayer"))
        for (specimen in Specimen.entries) assertEquals(1, f.count(specimen.polished), "a second set isn't taken")
        f.rb.quest.completeQuest(f.access())
        assertEquals(1, f.player.vars["varp.qp"])
    }

    @Test fun `declining the Odd Old Man leaves the quest unstarted`() {
        val f = Fixture()
        f.choose(1, 2)
        f.talk(ODD_OLD_MAN)
        assertEquals(0, f.stage())
        f.choose(2)
        f.talk(ODD_OLD_MAN)
        assertTrue(f.said("There is no wind"))
        assertEquals(0, f.stage())
        f.give(LOGS)
        f.onBoiler(LOGS)
        assertEquals(BOILER_EMPTY, f.boilerState(), "the boiler is his until the quest starts")
    }

    @Test fun `the journal checklist and hints follow each specimen`() {
        val f = Fixture(STAGE_STARTED)
        assertTrue(f.journal().contains("Giant rat bone</col>: not yet found"))
        assertTrue(f.journal().contains("Lumbridge Swamp"))
        assertTrue(f.rb.hint(f.player).contains("giant rat"))
        f.give(Specimen.GIANT_RAT.raw)
        assertTrue(f.journal().contains("Giant rat bone</col>: found, needs cleaning"))
        assertTrue(f.rb.hint(f.player).contains("Fortunato"))
        f.jump(STAGE_VINEGAR)
        assertTrue(f.rb.hint(f.player).contains("out of vinegar"))
        f.give(VINEGAR)
        assertTrue(f.rb.hint(f.player).contains("Pour vinegar"))
        f.drop(VINEGAR)
        f.drop(Specimen.GIANT_RAT.raw)
        f.give(Specimen.GIANT_RAT.inVinegar)
        assertTrue(f.journal().contains("soaking in a pot of vinegar"))
        assertTrue(f.rb.hint(f.player).contains("ordinary log"))
        f.give(LOGS)
        f.onBoiler(LOGS)
        f.onBoiler(Specimen.GIANT_RAT.inVinegar)
        assertTrue(f.journal().contains("I need to light the log"))
        assertTrue(f.rb.hint(f.player).contains("tinderbox"))
    }

    @Test fun `resetting the quest clears the boiler`() {
        val f = Fixture(STAGE_VINEGAR)
        f.give(LOGS)
        f.give(Specimen.RAM.inVinegar)
        f.onBoiler(LOGS)
        f.onBoiler(Specimen.RAM.inVinegar)
        f.rb.quest.resetQuest(f.player)
        assertEquals(0, f.stage())
        assertEquals(BOILER_EMPTY, f.boilerState())
        assertNull(f.rb.boilerSpecimen(f.player))
    }

    private class Fixture(stage: Int = 0) {
        val events = EventBus()
        private val client = RecordingClient()
        private val collision = CollisionFlagMap()
        private val npcs = NpcList()
        private val coroutine = GameCoroutine("rag-and-bone-test")
        private var result: Result<Unit>? = null
        private val random = DefaultGameRandom(Random(7))
        private val context = ProtectedAccessContextFactory.empty().copy(
            getEventBus = { events }, getAlignment = { TextAlignment() },
            getCollision = { collision }, getNpcList = { npcs },
            getNpcInteractions = { NpcInteractions(events) },
            getRandom = { random },
        )
        private val npcRepo: NpcRepository
        private val picks = ArrayDeque<Int>()
        private val locU = LocUInteractions::class.java.getDeclaredConstructor(EventBus::class.java)
            .apply { isAccessible = true }.newInstance(events)
        private val heldU = HeldUInteractions(events)

        @OptIn(InternalApi::class)
        var player = newPlayer()

        val rb = RagAndBoneManQuest()
        val boiler: PotBoiler

        init {
            val updates = ZoneUpdateMap()
            npcRepo = NpcRepository(org.rsmod.game.MapClock(100), NpcRegistry(npcs, collision, events), npcs)
            for (x in 3352 until 3376 step 8) for (z in 3496 until 3520 step 8) collision.allocateIfAbsent(x, z, 0)
            for (x in 3080 until 3096 step 8) for (z in 3240 until 3256 step 8) collision.allocateIfAbsent(x, z, 0)
            boiler = PotBoiler(rb, WorldRepository(updates))
            val scripts = ScriptContext(events, CheatCommandMap(), EngineQueueCache())
            with(rb) { scripts.startup() }
            with(boiler) { scripts.startup() }
            with(VinegarPreparation()) { scripts.startup() }
            with(OddOldMan(rb)) { scripts.startup() }
            with(Fortunato(rb, Shops(events))) { scripts.startup() }
            if (stage > 0) rb.quest.jumpToStage(player, stage)
        }

        @OptIn(InternalApi::class)
        private fun newPlayer() = Player().apply {
            this.client = this@Fixture.client
            uuid = 4343L
            observerUUID = 4343L
            slotId = 1
            assignUid()
            coords = CAMP
            currentMapClock = 100
            processedMapClock = 100
            pendingSequence = EntitySeq.NULL
            pendingFaceAngle = EntityFaceAngle.NULL
            inv = Inventory(InventoryServerType(size = 28, flags = 0), arrayOfNulls(28))
            worn = Inventory(InventoryServerType(size = 14, flags = 0), arrayOfNulls(14))
        }

        fun access() = ProtectedAccess(player, coroutine, context)

        fun stage(): Int = rb.stage(player)

        fun jump(stage: Int) = rb.quest.jumpToStage(player, stage)

        fun state(specimen: Specimen): SpecimenState = rb.specimenState(player, specimen)

        fun boilerState(): Int = rb.boilerState(player)

        fun needsDrop(specimen: Specimen): Boolean =
            org.rsmod.content.quest.manager.QuestRequirements.isOnQuest(player, RagAndBoneManQuest.QUEST_KEY) &&
                QuestItemDrops.isNeeded(player, specimen.raw)

        fun xp(stat: String): Int = player.statMap.getXP(stat)

        fun choose(vararg options: Int) {
            picks += options.toList()
        }

        fun give(obj: String, count: Int = 1) {
            val type = checkNotNull(ServerCacheManager.getItem(obj.asRSCM()))
            if (type.stackable) {
                val slot = player.inv.indexOfFirst { it?.id == obj.asRSCM() }
                if (slot >= 0) {
                    player.inv[slot] = InvObj(obj, checkNotNull(player.inv[slot]).count + count)
                    return
                }
                player.inv[player.inv.indexOfFirst { it == null }] = InvObj(obj, count)
                return
            }
            repeat(count) { player.inv[player.inv.indexOfFirst { it == null }] = InvObj(obj, 1) }
        }

        fun fill(leave: Int = 0) {
            while (player.inv.freeSpace() > leave) give("obj.bronze_dagger")
        }

        fun drop(obj: String) {
            val slot = player.inv.indexOfFirst { it?.id == obj.asRSCM() }
            player.inv[slot] = null
        }

        fun bank(obj: String) {
            val bank = player.invMap.getOrPut("inv.bank")
            bank[bank.indexOfFirst { it == null }] = InvObj(obj, 1)
        }

        fun unbank() {
            val bank = player.invMap.getOrPut("inv.bank")
            for (slot in 0 until bank.size) bank[slot] = null
        }

        fun count(obj: String): Int = player.inv.count(obj)

        fun said(text: String): Boolean = output().contains(text)

        fun journal(): String = rb.questLog(access())

        fun completedJournal(): String = rb.completedLog(access())

        fun start() {
            choose(1, 1)
            talk(ODD_OLD_MAN)
        }

        /** Mentions the Odd Old Man to Fortunato, then buys [buy] jugs (1, 5 or 8). */
        fun unlockVinegar(buy: Int) {
            choose(2, listOf(1, 5, 8).indexOf(buy) + 1)
            talk(FORTUNATO)
        }

        /** Soaks [specimen] and runs it through the boiler, from a raw specimen to a polished one. */
        fun clean(specimen: Specimen) {
            use(VINEGAR, POT)
            use(specimen.raw, POT_OF_VINEGAR)
            onBoiler(LOGS)
            onBoiler(specimen.inVinegar)
            onBoiler(TINDERBOX)
            boil()
            boilerOp()
        }

        fun boil() {
            repeat(PotBoiler.BOIL_STEPS) { boiler.boilStep(player) }
        }

        fun use(first: String, second: String) {
            val a = player.inv.indexOfFirst { it?.id == first.asRSCM() }
            val b = player.inv.indexOfLast { it?.id == second.asRSCM() }
            check(a >= 0 && b >= 0) { "missing $first or $second" }
            val typeA = checkNotNull(ServerCacheManager.getItem(first.asRSCM()))
            val typeB = checkNotNull(ServerCacheManager.getItem(second.asRSCM()))
            dispatch { heldU.interact(this, inv, typeA, a, typeB, b) }
        }

        fun op4(obj: String) {
            val slot = player.inv.indexOfFirst { it?.id == obj.asRSCM() }
            val type = checkNotNull(ServerCacheManager.getItem(obj.asRSCM()))
            dispatch { assertTrue(events.publish(this, HeldObjEvents.Op4(slot, checkNotNull(inv[slot]), type, inv))) }
        }

        fun talk(type: String) {
            val npc = Npc(type, player.coords.translateX(1))
            npcRepo.add(npc, Int.MAX_VALUE)
            dispatch { assertTrue(events.publish(this, NpcEvents.Op1(npc))) }
        }

        fun onBoiler(obj: String) {
            val loc = bound()
            val objType = checkNotNull(ServerCacheManager.getItem(obj.asRSCM()))
            val slot = player.inv.indexOfFirst { it?.id == obj.asRSCM() }
            if (slot < 0) return
            val locType = checkNotNull(ServerCacheManager.getObject(PotBoiler.BOILER.asRSCM()))
            val event = with(locU) { access().opTrigger(loc, loc, locType, objType, slot) }
            val trigger = checkNotNull(event) { "No $obj handler for the boiler" }
            dispatch { assertTrue(events.publish(this, trigger)) }
        }

        fun boilerOp() {
            val event = LocInteractions(BoundValidator(collision), events).opTrigger(player, bound(), InteractionOp.Op1)
                ?: return
            dispatch { assertTrue(events.publish(this, event)) }
        }

        @OptIn(InternalApi::class)
        fun saveAndReload(): Player {
            val loaded = newPlayer()
            for ((varp, value) in player.vars.backing) {
                val scope = ServerCacheManager.getVarp(varp)?.scope
                if (scope != VarpLifetime.Temp) loaded.vars.backing[varp] = value
            }
            player.attr[QUEST_STAGE_MAP_ATTR]?.let { loaded.attr[QUEST_STAGE_MAP_ATTR] = it.toMutableMap() }
            for (slot in 0 until player.inv.size) loaded.inv[slot] = player.inv[slot]
            rb.quest.syncState(loaded)
            return loaded
        }

        fun adopt(loaded: Player) {
            player = loaded
        }

        private fun bound(): BoundLocInfo {
            val type = checkNotNull(ServerCacheManager.getObject(PotBoiler.BOILER.asRSCM()))
            return BoundLocInfo(LocInfo(0, PotBoiler.BOILER_TILE, LocEntity(type.id, 10, 0)), type)
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
        val CAMP = CoordGrid(3360, 3503, 0)

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
