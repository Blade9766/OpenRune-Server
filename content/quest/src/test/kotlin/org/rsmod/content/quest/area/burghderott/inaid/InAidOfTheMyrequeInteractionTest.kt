package org.rsmod.content.quest.area.burghderott.inaid

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.InventoryServerType
import dev.openrune.types.ItemServerType
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.startCoroutine
import kotlin.random.Random
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.annotations.InternalApi
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.death.NpcAttackValidateResult
import org.rsmod.api.death.NpcDeath
import org.rsmod.api.inv.storage.PlayerItemStorage
import org.rsmod.api.invtx.InvTransactionsScript
import org.rsmod.api.npc.hit.modifier.NpcHitModifier
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.player.dialogue.align.TextAlignment
import org.rsmod.api.player.events.interact.HeldObjEvents
import org.rsmod.api.player.events.interact.HeldUDefaultEvents
import org.rsmod.api.player.events.interact.LocUDefaultEvents
import org.rsmod.api.player.events.interact.LocUEvents
import org.rsmod.api.player.events.interact.NpcEvents
import org.rsmod.api.player.events.interact.NpcUDefaultEvents
import org.rsmod.api.player.hit.modifier.NoopPlayerHitModifier
import org.rsmod.api.player.hook.PlayerTeleportValidateHook
import org.rsmod.api.player.hook.PlayerTeleportValidator
import org.rsmod.api.player.hook.RestrictedAction
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.input.ResumePauseButtonInput
import org.rsmod.api.player.interact.LocInteractions
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
import org.rsmod.api.registry.obj.ObjRegistry
import org.rsmod.api.registry.region.RegionRegistry
import org.rsmod.api.registry.zone.ZonePlayerActivityBitSet
import org.rsmod.api.registry.zone.ZoneUpdateMap
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.route.BoundValidator
import org.rsmod.content.other.pets.PetFollowers
import org.rsmod.content.quest.area.burghderott.inaid.InAidOfTheMyrequeQuest.Companion.ROD_FULL
import org.rsmod.content.quest.area.burghderott.inaid.InAidOfTheMyrequeQuest.Companion.STAGE_ADMITTED
import org.rsmod.content.quest.area.burghderott.inaid.InAidOfTheMyrequeQuest.Companion.STAGE_BANK_OPEN
import org.rsmod.content.quest.area.burghderott.inaid.InAidOfTheMyrequeQuest.Companion.STAGE_BOOK_READ
import org.rsmod.content.quest.area.burghderott.inaid.InAidOfTheMyrequeQuest.Companion.STAGE_CELLAR_CLEARED
import org.rsmod.content.quest.area.burghderott.inaid.InAidOfTheMyrequeQuest.Companion.STAGE_CELLAR_SUGGESTED
import org.rsmod.content.quest.area.burghderott.inaid.InAidOfTheMyrequeQuest.Companion.STAGE_COMPLETE
import org.rsmod.content.quest.area.burghderott.inaid.InAidOfTheMyrequeQuest.Companion.STAGE_CRATE_GIVEN
import org.rsmod.content.quest.area.burghderott.inaid.InAidOfTheMyrequeQuest.Companion.STAGE_FURNACE_LIT
import org.rsmod.content.quest.area.burghderott.inaid.InAidOfTheMyrequeQuest.Companion.STAGE_GADDERANKS_DEAD
import org.rsmod.content.quest.area.burghderott.inaid.InAidOfTheMyrequeQuest.Companion.STAGE_GADDERANKS_DEFEATED
import org.rsmod.content.quest.area.burghderott.inaid.InAidOfTheMyrequeQuest.Companion.STAGE_HELP_OFFERED
import org.rsmod.content.quest.area.burghderott.inaid.InAidOfTheMyrequeQuest.Companion.STAGE_IVAN_DELIVERED
import org.rsmod.content.quest.area.burghderott.inaid.InAidOfTheMyrequeQuest.Companion.STAGE_LIBRARY_KEY
import org.rsmod.content.quest.area.burghderott.inaid.InAidOfTheMyrequeQuest.Companion.STAGE_MOULD_MADE
import org.rsmod.content.quest.area.burghderott.inaid.InAidOfTheMyrequeQuest.Companion.STAGE_PARTY_TOLD
import org.rsmod.content.quest.area.burghderott.inaid.InAidOfTheMyrequeQuest.Companion.STAGE_RELOCATION_BRIEFED
import org.rsmod.content.quest.area.burghderott.inaid.InAidOfTheMyrequeQuest.Companion.STAGE_RETURN_TO_HOLLOWS
import org.rsmod.content.quest.area.burghderott.inaid.InAidOfTheMyrequeQuest.Companion.STAGE_STARTED
import org.rsmod.content.quest.area.burghderott.inaid.InAidOfTheMyrequeQuest.Companion.STAGE_STORE_REPAIRS
import org.rsmod.content.quest.area.burghderott.inaid.InAidOfTheMyrequeQuest.Companion.STAGE_STORE_STOCKED
import org.rsmod.content.quest.area.burghderott.inaid.InAidOfTheMyrequeQuest.Companion.STAGE_TITHE_FIGHT
import org.rsmod.content.quest.area.burghderott.inaid.InAidOfTheMyrequeQuest.Companion.STAGE_TOMB_FOUND
import org.rsmod.content.quest.area.mortton.myreque.Betrayal
import org.rsmod.content.quest.area.mortton.myreque.InSearchOfTheMyrequeQuest
import org.rsmod.content.quest.area.mortton.myreque.npcs.MyrequeMembers
import org.rsmod.content.quest.area.paterdomus.priestinperil.PaterdomusDoors
import org.rsmod.content.quest.manager.QUEST_STAGE_MAP_ATTR
import org.rsmod.content.quest.manager.QuestRequirementMode
import org.rsmod.content.quest.manager.QuestRequirementPolicy
import org.rsmod.content.quest.manager.QuestRequirements
import org.rsmod.coroutine.GameCoroutine
import org.rsmod.events.EventBus
import org.rsmod.events.SuspendEvent
import org.rsmod.game.MapClock
import org.rsmod.game.area.AreaIndex
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.client.Client
import org.rsmod.game.entity.ControllerList
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.util.EntityFaceAngle
import org.rsmod.game.hit.HitType
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
import org.rsmod.game.queue.WorldQueueList
import org.rsmod.game.region.RegionListLarge
import org.rsmod.game.region.RegionListSmall
import org.rsmod.game.region.RegionListWorldEntity
import org.rsmod.game.seq.EntitySeq
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap
import sun.misc.Unsafe

/**
 * Drives In Aid of the Myreque's real scripts through the event bus: the whole quest from Veliaf's
 * offer to the reward scroll, and then each system on its own - the gate and the food chest, the
 * cellar rubble, the crate, the repairs and their costs, the services each stage opens, the tier 2
 * vampyre rules, the blood tithe fight (Veliaf's finishing blow, walking out and retrying), the
 * relocation conversations, both escort routes with Ivan's food, armour and escape, the library,
 * the rod, the final handover, a save and reload, and two players progressing apart.
 *
 * Private map copies run in place ([InPlaceCopies]); a player is "inside" between entering and
 * leaving one.
 */
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class InAidOfTheMyrequeInteractionTest {

    @Test fun `the full quest runs from veliaf's offer to the rod and rewards once`() {
        val f = Fixture()
        f.finishInSearch()
        f.choose(1)
        f.npcOp(InAidOfTheMyrequeQuest.VELIAF_HOLLOWS)
        assertEquals(STAGE_STARTED, f.stage())

        f.give("obj.salmon", 2)
        f.admit()
        assertEquals(STAGE_ADMITTED, f.stage())
        assertEquals(1, f.count("obj.salmon"), "exactly one piece of food goes into the chest")

        f.askForHideaway()
        assertEquals(STAGE_CELLAR_SUGGESTED, f.stage())
        f.clearCellar()
        assertEquals(STAGE_CELLAR_CLEARED, f.stage())
        assertTrue(f.said("dusty looking wall plaque"))
        assertEquals(10, f.count("obj.nails_bronze"))
        assertEquals(5, f.count("obj.nails_iron"))
        assertEquals(1, f.count(InAidOfTheMyrequeQuest.DUSTY_SCROLL))
        assertEquals(1, f.count(InAidOfTheMyrequeQuest.PLASTER_FRAGMENT))

        f.choose(3)
        f.npcOp("npc.burgh_vilager_2")
        assertEquals(STAGE_HELP_OFFERED, f.stage())
        f.choose(3)
        f.npcOp(GeneralStore.AUREL)
        assertEquals(STAGE_STORE_REPAIRS, f.stage())

        f.bankCellarGear()
        f.give(InAidOfTheMyrequeQuest.HAMMER)
        f.give(InAidOfTheMyrequeQuest.PLANK, 6)
        f.give("obj.nails", 23)
        f.repairStore()
        assertEquals(0, f.count(InAidOfTheMyrequeQuest.PLANK), "roof and wall take three planks each")
        assertEquals(20, nailCount(f.player.inv), "and twelve nails each; the rubble's 21 nails make up the 44")

        f.takeCrate()
        assertEquals(STAGE_CRATE_GIVEN, f.stage())
        f.stockCrate()
        f.npcOp(GeneralStore.AUREL)
        assertEquals(STAGE_STORE_STOCKED, f.stage())
        assertEquals(0, f.count(InAidOfTheMyrequeQuest.CRATE))
        assertEquals(1, f.player.vars[InAidOfTheMyrequeQuest.STORE_STOCKED])

        f.give(InAidOfTheMyrequeQuest.SWAMP_PASTE)
        f.give(InAidOfTheMyrequeQuest.PLANK, 5)
        f.repairBank()
        assertEquals(0, f.count(InAidOfTheMyrequeQuest.PLANK), "the bank takes the other five of the eleven planks")
        assertEquals(0, f.count(InAidOfTheMyrequeQuest.SWAMP_PASTE))
        f.choose(3)
        f.npcOp(BurghCitizens.CORNELIUS)
        assertEquals(STAGE_BANK_OPEN, f.stage())
        assertEquals(1, f.player.vars[InAidOfTheMyrequeQuest.BANK_TELLER])

        f.give(InAidOfTheMyrequeQuest.STEEL_BAR, 2)
        f.give(InAidOfTheMyrequeQuest.COAL)
        f.give(InAidOfTheMyrequeQuest.TINDERBOX)
        f.lightFurnace()
        assertEquals(STAGE_FURNACE_LIT, f.stage())
        assertEquals(InAidOfTheMyrequeQuest.FURNACE_LIT, f.player.vars[InAidOfTheMyrequeQuest.FURNACE])
        assertEquals(InAidOfTheMyrequeQuest.TITHE_COLLECTING, f.player.vars[InAidOfTheMyrequeQuest.TITHE_VISIBLE])
        assertTrue(f.said("He's found us!"))

        f.provokeTithe()
        assertEquals(STAGE_TITHE_FIGHT, f.stage())
        f.winTitheFight(veliafFinishes = true)
        assertEquals(STAGE_GADDERANKS_DEAD, f.stage())
        assertEquals(1, f.count(InAidOfTheMyrequeQuest.GADDERHAMMER))
        f.npcOp(BloodTithe.VELIAF_TALK, BurghCoords.GADDERANKS)
        assertEquals(STAGE_RETURN_TO_HOLLOWS, f.stage())
        assertEquals(InAidOfTheMyrequeQuest.TITHE_OVER, f.player.vars[InAidOfTheMyrequeQuest.TITHE_VISIBLE])

        f.npcOp(InAidOfTheMyrequeQuest.VELIAF_HOLLOWS)
        assertEquals(STAGE_RELOCATION_BRIEFED, f.stage())
        f.npcOp(InAidOfTheMyrequeQuest.POLMAFI_HOLLOWS)
        assertEquals(STAGE_PARTY_TOLD, f.stage())
        f.escortIvan(IvanEscort.Route.Short)
        assertEquals(STAGE_IVAN_DELIVERED, f.stage())
        assertEquals(BurghCoords.PATERDOMUS_ARRIVAL, f.player.coords)
        assertEquals(1, f.player.vars[InAidOfTheMyrequeQuest.HIDEOUT_NPCS], "the old hideout empties")

        f.askDrezel()
        assertEquals(STAGE_LIBRARY_KEY, f.stage())
        f.readTheSleepingSeven()
        assertEquals(STAGE_BOOK_READ, f.stage())
        f.makeMould()
        assertEquals(STAGE_MOULD_MADE, f.stage())
        f.makeRod()
        assertEquals(1, f.count(ROD_FULL))

        val before = f.xp()
        f.npcOp(InAidOfTheMyrequeQuest.VELIAF_BURGH, BurghCoords.CELLAR_FOOT.translateX(1))
        assertEquals(STAGE_COMPLETE, f.stage())
        assertEquals(0, f.count(ROD_FULL), "Veliaf keeps the rod")
        assertTrue(f.player.ui.containsModal("interface.questscroll"))
        assertEquals(2, f.player.vars["varp.qp"])
        for ((stat, xp) in f.xp()) assertEquals(2000, xp - before.getValue(stat), stat)
        f.access().ifCloseSub("interface.questscroll")

        f.give(ROD_FULL)
        f.npcOp(InAidOfTheMyrequeQuest.VELIAF_BURGH, BurghCoords.CELLAR_FOOT.translateX(1))
        assertEquals(1, f.count(ROD_FULL), "nothing is taken twice")
        assertEquals(2, f.player.vars["varp.qp"])
        for ((stat, xp) in f.xp()) assertEquals(2000, xp - before.getValue(stat), stat)
        assertTrue(f.iaom.templeTrekkingUnlocked(f.player))
        assertEquals(1, f.count(InAidOfTheMyrequeQuest.GADDERHAMMER), "no second Gadderhammer at completion")
    }

    @Test fun `florin's gate only opens for a player who has left food after being turned away`() {
        val f = Fixture(STAGE_STARTED)
        f.give("obj.salmon")
        f.give("obj.coins", 10)
        f.useOnLoc(BurghGate.CHEST, BurghCoords.FOOD_CHEST, "obj.salmon")
        assertTrue(f.said("find out who's guarding the gate"))
        assertEquals(1, f.count("obj.salmon"))
        f.gate()
        assertTrue(f.said("The gate has been locked"))
        assertFalse(f.iaom.isAdmitted(f.player))

        f.choose(4)
        f.npcOp(BurghGate.FLORIN, BurghCoords.GATE_INSIDE)
        assertTrue(f.iaom.isFlorinRefused(f.player))
        f.useOnLoc(BurghGate.CHEST, BurghCoords.FOOD_CHEST, "obj.coins")
        assertEquals(10, f.count("obj.coins"), "only food is accepted")
        f.useOnLoc(BurghGate.TABLE, BurghCoords.FOOD_TABLE, "obj.salmon")
        assertTrue(f.said("it would just get ruined"))
        assertEquals(1, f.count("obj.salmon"))
        f.useOnLoc(BurghGate.CHEST, BurghCoords.FOOD_CHEST, "obj.salmon")
        assertEquals(STAGE_ADMITTED, f.stage())
        assertEquals(0, f.count("obj.salmon"))

        f.give("obj.salmon")
        f.useOnLoc(BurghGate.CHEST, BurghCoords.FOOD_CHEST, "obj.salmon")
        assertEquals(1, f.count("obj.salmon"), "a second piece is never taken")
        val messages = f.output()
        f.gate()
        assertEquals(messages.split("gate has been locked").size, f.output().split("gate has been locked").size, "the gate opens now")

        val other = Fixture(STAGE_STARTED)
        other.gate()
        assertTrue(other.said("The gate has been locked"), "admission is per player")
    }

    @Test fun `mined rubble that is not carried away comes back and each pile counts once`() {
        val f = Fixture(STAGE_CELLAR_SUGGESTED)
        f.kitForCellar(buckets = 1)
        f.openTrapdoor()
        f.climbDown()
        assertEquals(15, f.liveRubble())
        val pile = BurghCoords.RUBBLE_PILES[0]
        repeat(3) { f.locOp(InnCellar.RUBBLE_STAGES[it], pile) }
        assertTrue(f.said("Though you'll need something to carry it in"))
        f.locOp(InnCellar.CELLAR_LADDER, BurghCoords.CELLAR_LADDER.copy(level = 1))
        assertFalse(f.copies.inside(f.player))
        assertEquals(0, f.iaom.rubbleRemoved(f.player), "mining alone removes nothing")

        f.climbDown()
        assertEquals(15, f.liveRubble(), "the mined pile is whole again")
        assertTrue(f.loc(InnCellar.RUBBLE_STAGES[0], pile))
        f.clearPile(0)
        assertEquals(1, f.iaom.rubbleRemoved(f.player))
        f.locOp(InnCellar.RUBBLE_STAGES.last(), pile)
        assertEquals(1, f.iaom.rubbleRemoved(f.player), "a gone pile cannot be removed again")
        f.clearPile(1)
        f.clearPile(2)
        assertEquals(1, f.count(InAidOfTheMyrequeQuest.RUBBLE_BUCKETS[2]), "three loads fill a bucket")
        f.mineFully(3)
        f.locOp(InnCellar.RUBBLE_STAGES.last(), BurghCoords.RUBBLE_PILES[3])
        assertTrue(f.said("You need a bucket with some room"))
        assertEquals(3, f.iaom.rubbleRemoved(f.player))

        f.heldOp4(InAidOfTheMyrequeQuest.RUBBLE_BUCKETS[2])
        assertTrue(f.said("fill the room up with rubble again"))
        f.locOp(InnCellar.CELLAR_LADDER, BurghCoords.CELLAR_LADDER.copy(level = 1))
        f.player.coords = BurghCoords.GATE_INSIDE
        f.heldOp4(InAidOfTheMyrequeQuest.RUBBLE_BUCKETS[2])
        assertTrue(f.said("would not be happy"))
        f.useOnLoc(InnCellar.RUBBLE_DUMP, BurghCoords.RUBBLE_DUMP, InAidOfTheMyrequeQuest.RUBBLE_BUCKETS[2], typed = true)
        assertEquals(1, f.count(InAidOfTheMyrequeQuest.BUCKET))

        f.climbDown()
        assertEquals(12, f.liveRubble(), "removed piles stay removed")
        assertEquals(3, f.saveAndReload().let { f.iaom.rubbleRemoved(it) })
    }

    @Test fun `a full inventory throws the lore finds into the bucket and the rubble pile gives them back`() {
        val f = Fixture(STAGE_CELLAR_SUGGESTED)
        f.kitForCellar(buckets = 5)
        f.openTrapdoor()
        f.climbDown()
        for (pile in 0 until 7) f.clearPile(pile)
        f.fillInventory()
        f.clearPile(7)
        assertTrue(f.said("throw it into the bucket"))
        assertEquals(0, f.count(InAidOfTheMyrequeQuest.DUSTY_SCROLL))
        f.locOp(InnCellar.CELLAR_LADDER, BurghCoords.CELLAR_LADDER.copy(level = 1))
        f.emptyFiller(2)
        f.locOp(InnCellar.RUBBLE_DUMP, BurghCoords.RUBBLE_DUMP, InteractionOp.Op2)
        assertEquals(1, f.count(InAidOfTheMyrequeQuest.DUSTY_SCROLL))
        f.locOp(InnCellar.RUBBLE_DUMP, BurghCoords.RUBBLE_DUMP, InteractionOp.Op2)
        assertEquals(1, f.count(InAidOfTheMyrequeQuest.DUSTY_SCROLL), "only one copy")
    }

    @Test fun `repairs take exactly their materials and nothing when anything is missing`() {
        val f = Fixture(STAGE_STORE_REPAIRS)
        f.give(InAidOfTheMyrequeQuest.PLANK, 3)
        f.give("obj.nails", 12)
        f.choose(1)
        f.climbToRoof()
        f.locOp(GeneralStore.ROOF_HOLE, BurghCoords.STORE_ROOF_HOLE)
        assertTrue(f.said("You need a hammer, 3 basic planks and 12 nails"))
        assertEquals(3, f.count(InAidOfTheMyrequeQuest.PLANK))
        assertEquals(0, f.player.vars[InAidOfTheMyrequeQuest.STORE_ROOF])

        f.give(InAidOfTheMyrequeQuest.HAMMER)
        f.take("obj.nails", 4)
        f.give("obj.nails_bronze", 2)
        f.give("obj.nails_mithril", 2)
        f.choose(1)
        f.locOp(GeneralStore.ROOF_HOLE, BurghCoords.STORE_ROOF_HOLE)
        assertEquals(1, f.player.vars[InAidOfTheMyrequeQuest.STORE_ROOF])
        assertEquals(0, f.count(InAidOfTheMyrequeQuest.PLANK))
        assertEquals(0, nailCount(f.player.inv), "a mix of nails counts")

        val g = Fixture(InAidOfTheMyrequeQuest.STAGE_STORE_STOCKED)
        g.give(InAidOfTheMyrequeQuest.HAMMER)
        g.give(InAidOfTheMyrequeQuest.PLANK, 2)
        g.give("obj.nails", 8)
        g.choose(1)
        g.locOp("loc.burgh_bank_booth_multiloc", BurghCoords.BANK_BOOTH)
        assertTrue(g.said("1 swamp paste, 2 basic planks and 8 nails"))
        g.give(InAidOfTheMyrequeQuest.SWAMP_PASTE)
        g.choose(1)
        g.locOp("loc.burgh_bank_booth_multiloc", BurghCoords.BANK_BOOTH)
        assertEquals(1, g.player.vars[InAidOfTheMyrequeQuest.BANK_BOOTH])
        assertEquals(0, g.count(InAidOfTheMyrequeQuest.PLANK) + nailCount(g.player.inv) + g.count(InAidOfTheMyrequeQuest.SWAMP_PASTE))
    }

    @Test fun `the crate keeps its request and contents and refuses notes, extras and strangers`() {
        val f = Fixture(STAGE_STORE_REPAIRS)
        VarPlayerIntMapSetter.set(f.player, InAidOfTheMyrequeQuest.STORE_ROOF, 1)
        VarPlayerIntMapSetter.set(f.player, InAidOfTheMyrequeQuest.STORE_WALL, 1)
        f.takeCrate()
        val request = checkNotNull(f.iaom.crateRequest(f.player))
        val loaded = f.saveAndReload()
        assertEquals(request, f.iaom.crateRequest(loaded), "the request survives a relog")

        f.give(InAidOfTheMyrequeQuest.BRONZE_AXE, 4)
        f.choose(1)
        f.useOnCrate(InAidOfTheMyrequeQuest.BRONZE_AXE)
        assertEquals(4, f.player.vars[InAidOfTheMyrequeQuest.CRATE_AXES_VAR])
        f.give(InAidOfTheMyrequeQuest.BRONZE_AXE, 8)
        f.choose(1)
        f.useOnCrate(InAidOfTheMyrequeQuest.BRONZE_AXE)
        assertEquals(10, f.player.vars[InAidOfTheMyrequeQuest.CRATE_AXES_VAR])
        assertEquals(2, f.count(InAidOfTheMyrequeQuest.BRONZE_AXE), "only what was asked for is taken")
        f.useOnCrate(InAidOfTheMyrequeQuest.BRONZE_AXE)
        assertTrue(f.said("You've already filled the crate with bronze axes"))
        assertEquals(2, f.count(InAidOfTheMyrequeQuest.BRONZE_AXE))

        f.give("obj.coins", 5)
        f.useOnCrate("obj.coins")
        assertTrue(f.said("not something Aurel asked for"))
        val wrong = if (request == InAidOfTheMyrequeQuest.CrateFood.Mackerel) "obj.snail_corpse1" else "obj.raw_mackerel"
        f.give(wrong)
        f.useOnCrate(wrong)
        assertEquals(0, f.player.vars[InAidOfTheMyrequeQuest.CRATE_FOOD_VAR], "the other food is refused")

        f.take(InAidOfTheMyrequeQuest.CRATE, 1)
        f.npcOp(GeneralStore.AUREL)
        assertEquals(1, f.count(InAidOfTheMyrequeQuest.CRATE), "a lost crate is replaced")
        assertEquals(10, f.player.vars[InAidOfTheMyrequeQuest.CRATE_AXES_VAR], "with its contents")
        f.npcOp(GeneralStore.AUREL)
        assertEquals(1, f.count(InAidOfTheMyrequeQuest.CRATE), "but never doubled")
        assertEquals(STAGE_CRATE_GIVEN, f.stage(), "a part-filled crate is not accepted")
    }

    @Test fun `the shop, bank and furnace open at their own stages`() {
        val f = Fixture(STAGE_CRATE_GIVEN)
        f.npcOp(GeneralStore.AUREL, op = InteractionOp.Op3)
        assertTrue(f.said("can't open a store which has no stock"))
        f.locOp(BurghRepairs.BOOTH_REPAIRED, BurghCoords.BANK_BOOTH)
        assertTrue(f.said("there still isn't anyone working behind the booth"))
        assertFalse(f.player.ui.containsModal("interface.bankmain"))

        val g = Fixture(STAGE_BANK_OPEN)
        g.locOp(BurghRepairs.BOOTH_REPAIRED, BurghCoords.BANK_BOOTH)
        assertTrue(g.player.ui.containsModal("interface.bankmain"), "the bank opens once Cornelius is recruited")
        assertTrue(g.iaom.isStoreOpen(g.player))
        assertFalse(g.iaom.isFurnaceLit(g.player))
        g.give(InAidOfTheMyrequeQuest.HAMMER)
        g.give(InAidOfTheMyrequeQuest.STEEL_BAR, 1)
        g.choose(1)
        g.locOp("loc.burgh_furnace_multiloc", BurghCoords.FURNACE)
        assertTrue(g.said("You need a hammer and 2 steel bars"))
        assertEquals(0, g.player.vars[InAidOfTheMyrequeQuest.FURNACE])
        g.give(InAidOfTheMyrequeQuest.STEEL_BAR, 1)
        g.choose(1)
        g.locOp("loc.burgh_furnace_multiloc", BurghCoords.FURNACE)
        assertEquals(InAidOfTheMyrequeQuest.FURNACE_REPAIRED, g.player.vars[InAidOfTheMyrequeQuest.FURNACE])
        g.choose(1)
        g.locOp("loc.burgh_furnace_multiloc", BurghCoords.FURNACE)
        assertTrue(g.said("You need some coal"))
        g.give(InAidOfTheMyrequeQuest.COAL)
        g.choose(1)
        g.locOp("loc.burgh_furnace_multiloc", BurghCoords.FURNACE)
        assertEquals(InAidOfTheMyrequeQuest.FURNACE_FUELLED, g.player.vars[InAidOfTheMyrequeQuest.FURNACE])
        g.choose(1)
        g.locOp("loc.burgh_furnace_multiloc", BurghCoords.FURNACE)
        assertTrue(g.said("You need a tinderbox"))
        assertEquals(InAidOfTheMyrequeQuest.FURNACE_FUELLED, g.player.vars[InAidOfTheMyrequeQuest.FURNACE], "no fuel is lost")
        assertTrue(g.iaom.isFurnaceLit(Fixture(STAGE_FURNACE_LIT).player))

        val unstarted = Fixture(0)
        assertFalse(unstarted.iaom.isBankOpen(unstarted.player), "respect-progress keeps an unstarted player out")
    }

    @Test fun `tier 2 vampyres take silver in melee, half of anything with efaritay's aid, and nothing else`() {
        val silver = "obj.dagger_wolfbane".asRSCM(RSCMType.OBJ)
        val rod = ROD_FULL.asRSCM(RSCMType.OBJ)
        val steel = "obj.steel_longsword".asRSCM(RSCMType.OBJ)
        assertEquals(10, VampyreFights.tierTwoDamage(10, HitType.Melee, silver, efaritay = false))
        assertEquals(11, VampyreFights.tierTwoDamage(10, HitType.Melee, silver, efaritay = true))
        assertEquals(10, VampyreFights.tierTwoDamage(10, HitType.Melee, rod, efaritay = false))
        assertEquals(0, VampyreFights.tierTwoDamage(10, HitType.Melee, steel, efaritay = false))
        assertEquals(5, VampyreFights.tierTwoDamage(10, HitType.Melee, steel, efaritay = true))
        assertEquals(0, VampyreFights.tierTwoDamage(10, HitType.Magic, silver, efaritay = false), "silver only bites in melee")
        assertEquals(5, VampyreFights.tierTwoDamage(10, HitType.Ranged, null, efaritay = true))
    }

    @Test fun `veliaf's finishing blow counts and walking out of the store resets the fight`() {
        val f = Fixture(STAGE_FURNACE_LIT)
        f.provokeTithe()
        assertEquals(STAGE_TITHE_FIGHT, f.stage())
        assertEquals(3, f.tithe.fighters(f.player).size)
        val stranger = Player()
        assertTrue(VampyreFightAttackHook(f.fights).validate(stranger, f.tithe.fighters(f.player).first()) is NpcAttackValidateResult.Deny)
        assertTrue(VampyreFightAttackHook(f.fights).validate(f.player, f.tithe.fighters(f.player).first()) is NpcAttackValidateResult.Pass)

        f.player.coords = BurghCoords.STORE_EXIT
        f.tithe.tick(f.player)
        assertTrue(f.tithe.fighters(f.player).isEmpty(), "leaving the store ends the fight")
        assertEquals(0, f.liveNpcs(BloodTithe.JUVINATE_ONE, BloodTithe.JUVINATE_TWO, BloodTithe.GADDERANKS_FIGHTING))
        assertEquals(STAGE_TITHE_FIGHT, f.stage())

        f.player.coords = BurghCoords.STORE_FIGHT_ENTRY
        f.copies.leaveAll()
        f.npcOp(BloodTithe.GADDERANKS, BurghCoords.GADDERANKS)
        assertEquals(3, f.tithe.fighters(f.player).size, "speaking to the party again starts it afresh")
        f.winTitheFight(veliafFinishes = true, fullInventory = true)
        assertEquals(STAGE_GADDERANKS_DEAD, f.stage())
        assertEquals(0, f.count(InAidOfTheMyrequeQuest.GADDERHAMMER))
        assertEquals(0, f.player.vars[InAidOfTheMyrequeQuest.HAMMER_GIVEN])
        f.emptySlots(2)
        f.choose(3)
        f.npcOp(GeneralStore.AUREL)
        assertEquals(1, f.count(InAidOfTheMyrequeQuest.GADDERHAMMER), "Aurel kept the hammer")
        f.choose(3)
        f.npcOp(GeneralStore.AUREL)
        assertEquals(1, f.count(InAidOfTheMyrequeQuest.GADDERHAMMER), "and only one")
    }

    @Test fun `a fight cut short after gadderanks falls is finished by veliaf`() {
        val f = Fixture(STAGE_GADDERANKS_DEFEATED)
        f.npcOp(BloodTithe.VELIAF_TALK, BurghCoords.GADDERANKS)
        assertTrue(f.said("Silver dust and garlic"))
        assertEquals(STAGE_RETURN_TO_HOLLOWS, f.stage())
        assertEquals(1, f.count(InAidOfTheMyrequeQuest.GADDERHAMMER))
        assertTrue(GadderhammerWearHook(f.iaom).restriction(f.player, equip(InAidOfTheMyrequeQuest.GADDERHAMMER)) == null)
        val early = Fixture(STAGE_TITHE_FIGHT)
        assertNotNull(GadderhammerWearHook(early.iaom).restriction(early.player, equip(InAidOfTheMyrequeQuest.GADDERHAMMER)))
    }

    @Test fun `relocation follows veliaf, polmafi and radigad in order and ivan waits for them`() {
        val f = Fixture(STAGE_RETURN_TO_HOLLOWS)
        f.npcOp(InAidOfTheMyrequeQuest.POLMAFI_HOLLOWS)
        assertEquals(STAGE_RETURN_TO_HOLLOWS, f.stage(), "Polmafi has nothing to pass on yet")
        f.npcOp(InAidOfTheMyrequeQuest.VELIAF_HOLLOWS)
        assertEquals(STAGE_RELOCATION_BRIEFED, f.stage())
        f.npcOp(InAidOfTheMyrequeQuest.IVAN_HOLLOWS)
        assertTrue(f.said("I just need to speak with Polmafi and Radigad"))
        assertNull(f.escort.escortOf(f.player))
        f.npcOp(InAidOfTheMyrequeQuest.RADIGAD_HOLLOWS)
        assertEquals(STAGE_PARTY_TOLD, f.stage())
        f.npcOp(InAidOfTheMyrequeQuest.POLMAFI_HOLLOWS)
        assertTrue(f.said("I'm just getting my things together"))
    }

    @Test fun `ivan takes steel armour, the sickle and up to fifteen of his foods, none of it twice`() {
        val f = Fixture(STAGE_RELOCATION_BRIEFED)
        f.give("obj.steel_med_helm", 2)
        f.useOnIvan("obj.steel_med_helm")
        f.useOnIvan("obj.steel_med_helm")
        assertEquals(1, f.count("obj.steel_med_helm"), "a second helm is refused")
        assertEquals(1, f.player.vars[InAidOfTheMyrequeQuest.IVAN_HELM])
        f.give("obj.rune_full_helm")
        f.useOnIvan("obj.rune_full_helm")
        assertTrue(f.said("I can only wear a steel medium helm"))
        f.give(InAidHollows.SILVER_SICKLE)
        f.useOnIvan(InAidHollows.SILVER_SICKLE)
        assertEquals(1, f.player.vars[InAidOfTheMyrequeQuest.IVAN_SICKLE])
        f.give("obj.salmon", 10)
        f.choose(1)
        f.useOnIvan("obj.salmon")
        assertEquals(10, f.player.vars[InAidOfTheMyrequeQuest.IVAN_FOOD])
        f.give("obj.stew", 8)
        f.choose(1)
        f.useOnIvan("obj.stew")
        assertEquals(15, f.player.vars[InAidOfTheMyrequeQuest.IVAN_FOOD])
        assertEquals(3, f.count("obj.stew"), "only five fit")
        assertEquals(9, f.player.vars[InAidOfTheMyrequeQuest.IVAN_FOOD_HEAL], "he heals by his weakest food")
        f.give("obj.bread")
        f.useOnIvan("obj.bread")
        assertTrue(f.said("my favourites are"))
        assertEquals(1, f.count("obj.bread"))
    }

    @Test fun `both routes bring their own juvinates and only a living ivan finishes the escort`() {
        val short = Fixture(STAGE_PARTY_TOLD)
        short.startEscort(IvanEscort.Route.Short)
        val escort = checkNotNull(short.escort.escortOf(short.player))
        assertEquals(2, escort.juvinates.size)
        assertTrue(escort.juvinates.all { it.isType(IvanEscort.Route.Short.juvinate) })
        assertEquals(0, short.player.vars[InAidOfTheMyrequeQuest.AMBUSH_ROUTE])

        val long = Fixture(STAGE_PARTY_TOLD)
        long.startEscort(IvanEscort.Route.Long)
        val longEscort = checkNotNull(long.escort.escortOf(long.player))
        assertEquals(4, longEscort.juvinates.size)
        assertTrue(longEscort.juvinates.all { it.isType(IvanEscort.Route.Long.juvinate) })
        assertEquals(1, long.player.vars[InAidOfTheMyrequeQuest.AMBUSH_ROUTE])

        longEscort.ivan.hitpoints = 5
        long.escort.tick(long.player)
        assertTrue(longEscort.escaped, "with no food and little health Ivan flees")
        for (juvinate in longEscort.juvinates.toList()) long.fights.dispatch(juvinate)
        assertEquals(STAGE_PARTY_TOLD, long.stage(), "killing the juvinates without Ivan achieves nothing")
        long.locOp(IvanEscort.ESCAPE_PATH, CoordGrid(1999, 5028, 0))
        assertEquals(IvanEscort.ESCAPE_EXIT, long.player.coords)
        assertNull(long.escort.escortOf(long.player))
        long.startEscort(IvanEscort.Route.Long)
        assertNotNull(long.escort.escortOf(long.player), "the escort can be retried")
    }

    @Test fun `ivan eats his food when hurt and keeps what is left after a failed attempt`() {
        val f = Fixture(STAGE_PARTY_TOLD)
        VarPlayerIntMapSetter.set(f.player, InAidOfTheMyrequeQuest.IVAN_FOOD, 3)
        VarPlayerIntMapSetter.set(f.player, InAidOfTheMyrequeQuest.IVAN_FOOD_HEAL, 9)
        VarPlayerIntMapSetter.set(f.player, InAidOfTheMyrequeQuest.IVAN_BODY, 1)
        f.startEscort(IvanEscort.Route.Short)
        val escort = checkNotNull(f.escort.escortOf(f.player))
        escort.ivan.hitpoints = 15
        f.escort.tick(f.player)
        assertEquals(24, escort.ivan.hitpoints)
        assertEquals(2, f.player.vars[InAidOfTheMyrequeQuest.IVAN_FOOD])
        assertTrue(f.said("I only have 2 left"))
        f.escort.endAttempt(f.player)
        assertEquals(2, f.player.vars[InAidOfTheMyrequeQuest.IVAN_FOOD], "logging out keeps the rest of his food")
        assertEquals(1, f.player.vars[InAidOfTheMyrequeQuest.IVAN_BODY], "and his armour")
        assertEquals(0, f.liveNpcs(IvanEscort.IVAN_PLAIN, IvanEscort.Route.Short.juvinate))
    }

    @Test fun `the library opens with drezel's key and the tomb and mould need the sleeping seven`() {
        val f = Fixture(STAGE_IVAN_DELIVERED)
        f.give(InAidOfTheMyrequeQuest.HAMMER)
        f.useOnLoc(RodOfIvandis.BOARDS, BurghCoords.TOMB_BOARDS, InAidOfTheMyrequeQuest.HAMMER, base = "loc.burgh_ivandis_tombdoor_board_multiloc")
        assertEquals(0, f.player.vars[InAidOfTheMyrequeQuest.TOMB_BOARDS], "the boards stay until the book is read")
        f.give(InAidOfTheMyrequeQuest.SOFT_CLAY)
        f.useOnLoc(RodOfIvandis.COFFIN, BurghCoords.IVANDIS_COFFIN, InAidOfTheMyrequeQuest.SOFT_CLAY, typed = true)
        assertEquals(0, f.count(InAidOfTheMyrequeQuest.ROD_MOULD))

        f.askDrezel()
        assertEquals(1, f.count(InAidOfTheMyrequeQuest.LIBRARY_KEY))
        assertEquals(0, f.player.vars[InAidOfTheMyrequeQuest.LIBRARY_TRAPDOOR], "no trapdoor before the keyhole is used")
        f.readTheSleepingSeven()
        assertEquals(STAGE_BOOK_READ, f.stage())
        f.makeMould()
        assertEquals(STAGE_MOULD_MADE, f.stage())
        assertEquals(1, f.count(InAidOfTheMyrequeQuest.ROD_MOULD))
    }

    @Test fun `the rod is enchanted by the spell and blessed only with a rope`() {
        val f = Fixture(STAGE_MOULD_MADE)
        f.give(InAidOfTheMyrequeQuest.SILVTHRILL)
        f.player.coords = BurghCoords.SALVE_WELL.translateX(1)
        f.useOnLoc(PaterdomusLibrary.WELL, BurghCoords.SALVE_WELL, InAidOfTheMyrequeQuest.SILVTHRILL, typed = true)
        assertTrue(f.said("You'd lose the rod"))
        f.give(InAidOfTheMyrequeQuest.ROPE)
        f.useOnLoc(PaterdomusLibrary.WELL, BurghCoords.SALVE_WELL, InAidOfTheMyrequeQuest.SILVTHRILL, typed = true)
        assertTrue(f.said("otherwise nothing has changed"))
        assertEquals(1, f.count(InAidOfTheMyrequeQuest.SILVTHRILL))

        f.rod.canCast = false
        f.enchant()
        assertEquals(1, f.count(InAidOfTheMyrequeQuest.SILVTHRILL), "no runes, no enchantment")
        f.rod.canCast = true
        val magic = f.player.statMap.getXP("stat.magic")
        f.enchant()
        assertEquals(1, f.count(InAidOfTheMyrequeQuest.SILVTHRILL_ENCHANTED))
        assertTrue(f.player.statMap.getXP("stat.magic") > magic)
        f.useOnLoc(PaterdomusLibrary.WELL, BurghCoords.SALVE_WELL, InAidOfTheMyrequeQuest.SILVTHRILL_ENCHANTED, typed = true)
        assertEquals(1, f.count(ROD_FULL), "the blessed rod has ten charges")
        assertEquals(1, f.count(InAidOfTheMyrequeQuest.ROPE), "the rope is kept")
    }

    @Test fun `veliaf won't take a rod that is wielded or unfinished`() {
        val f = Fixture(STAGE_MOULD_MADE)
        f.give(InAidOfTheMyrequeQuest.SILVTHRILL_ENCHANTED)
        f.npcOp(InAidOfTheMyrequeQuest.VELIAF_BURGH)
        assertTrue(f.said("no divine blessing"))
        f.take(InAidOfTheMyrequeQuest.SILVTHRILL_ENCHANTED, 1)
        f.player.worn[3] = InvObj(ROD_FULL, 1)
        f.npcOp(InAidOfTheMyrequeQuest.VELIAF_BURGH)
        assertTrue(f.said("while you're still using it"))
        assertEquals(STAGE_MOULD_MADE, f.stage())
    }

    @Test fun `progress survives a save and two players move through the town apart`() {
        val a = Fixture(STAGE_STORE_REPAIRS)
        VarPlayerIntMapSetter.set(a.player, InAidOfTheMyrequeQuest.STORE_ROOF, 1)
        a.markPile(4)
        val loaded = a.saveAndReload()
        assertEquals(STAGE_STORE_REPAIRS, a.iaom.stage(loaded))
        assertEquals(1, loaded.vars[InAidOfTheMyrequeQuest.STORE_ROOF])
        assertEquals(1, loaded.vars[InAidOfTheMyrequeQuest.INN_WALL], "earlier stages are filled in")
        assertEquals(InAidOfTheMyrequeQuest.ALL_PILES, loaded.vars[InAidOfTheMyrequeQuest.RUBBLE_REMOVED])

        val b = Fixture(STAGE_STARTED)
        assertEquals(0, b.player.vars[InAidOfTheMyrequeQuest.STORE_ROOF])
        assertFalse(b.iaom.isAdmitted(b.player))
        assertTrue(a.iaom.isAdmitted(a.player))

        val reset = Fixture(STAGE_BANK_OPEN)
        reset.iaom.quest.resetQuest(reset.player)
        assertEquals(0, reset.player.vars[InAidOfTheMyrequeQuest.BANK_TELLER])
        assertEquals(0, reset.player.vars[InAidOfTheMyrequeQuest.STORE_STOCKED])
    }

    @Test fun `under assume-completed an unstarted player gets the town but veliaf still offers the quest`() {
        val previous = QuestRequirements.activePolicy()
        try {
            QuestRequirements.install(QuestRequirementPolicy(QuestRequirementMode.AssumeCompleted))
            val f = Fixture(0)
            f.iaom.quest.syncState(f.player)
            assertTrue(f.iaom.isAdmitted(f.player))
            assertTrue(f.iaom.isBankOpen(f.player))
            assertEquals(1, f.player.vars[InAidOfTheMyrequeQuest.BANK_TELLER])
            assertEquals(InAidOfTheMyrequeQuest.FURNACE_LIT, f.player.vars[InAidOfTheMyrequeQuest.FURNACE])
            assertEquals(0, f.player.vars[InAidOfTheMyrequeQuest.HIDEOUT_NPCS], "Veliaf is still in the old hideout to start it")
            f.finishInSearch()
            f.choose(1)
            f.npcOp(InAidOfTheMyrequeQuest.VELIAF_HOLLOWS)
            assertEquals(STAGE_STARTED, f.stage())
            assertFalse(f.iaom.isAdmitted(f.player), "a real start follows real progress")
        } finally {
            QuestRequirements.install(previous)
        }
    }

    /* Fixture */

    private fun equip(obj: String): RestrictedAction.Equip =
        RestrictedAction.Equip(checkNotNull(ServerCacheManager.getItem(obj.asRSCM(RSCMType.OBJ))))

    /** Copies run in place: the player stays on world tiles and fighters are plain world npcs. */
    class InPlaceCopies(private val npcRepo: NpcRepository) : BurghCopies(unused()) {
        private val insidePlayers = HashSet<Player>()

        fun inside(player: Player): Boolean = player in insidePlayers

        fun leaveAll() = insidePlayers.clear()

        override fun ProtectedAccess.enter(key: String, world: CoordGrid, exit: CoordGrid): Copy {
            insidePlayers += player
            telejump(world, TeleportType.Exempt)
            return Copy(null, 0, 0, 0)
        }

        override fun ProtectedAccess.leave() {
            insidePlayers -= player
        }

        override fun ProtectedAccess.isInside(): Boolean = player in insidePlayers

        override fun spawn(copy: Copy, type: String, world: CoordGrid, face: Direction): Npc {
            val npc = Npc(type, world)
            npcRepo.add(npc, Int.MAX_VALUE)
            npc.respawns = false
            return npc
        }

        override fun remove(npc: Npc) {
            if (npc.isSlotAssigned) npcRepo.del(npc, Int.MAX_VALUE)
        }
    }

    class TestRod(iaom: InAidOfTheMyrequeQuest) : RodOfIvandis(iaom, unused(), unused(), unused()) {
        var canCast = true

        override fun castEnchant(player: Player): Double? = if (canCast) ENCHANT_XP else null
    }

    private class Fixture(stage: Int = 0) {
        val events = EventBus()
        private val client = RecordingClient()
        private val collision = CollisionFlagMap()
        private val npcs = NpcList()
        private val players = PlayerList()
        private val coroutine = GameCoroutine("iaom-test")
        private var result: Result<Unit>? = null
        private lateinit var regions: RegionRegistry
        private val random = DefaultGameRandom(Random(SEED))
        private val clock = MapClock(100)
        private val context = ProtectedAccessContextFactory.empty().copy(
            getEventBus = { events }, getAlignment = { TextAlignment() },
            getCollision = { collision }, getNpcList = { npcs },
            getTeleportValidator = { PlayerTeleportValidator(setOf(PlayerTeleportValidateHook { _, _, _ -> null })) },
            getAreaChecker = { AreaChecker(regions, AreaIndex()) },
            getNpcInteractions = { NpcInteractions(events) },
            getRandom = { random },
            getHitModifier = { NoopPlayerHitModifier },
        )
        private val objRegistry = ObjRegistry(ZoneUpdateMap())
        val npcRepo: NpcRepository
        private val objRepo = ObjRepository(clock, objRegistry)
        private val locRepo: LocRepository
        private val picks = ArrayDeque<Int>()

        @OptIn(InternalApi::class)
        val player = Player().apply {
            this.client = this@Fixture.client
            uuid = 5151L
            observerUUID = 5151L
            slotId = 2
            assignUid()
            coords = CoordGrid(3505, 3230, 0)
            currentMapClock = 100
            processedMapClock = 100
            pendingSequence = EntitySeq.NULL
            pendingFaceAngle = EntityFaceAngle.NULL
            inv = Inventory(InventoryServerType(size = 28, flags = 0), arrayOfNulls(28))
            worn = Inventory(InventoryServerType(size = 14, flags = 0), arrayOfNulls(14))
            for ((stat, level) in listOf("stat.agility" to 25, "stat.crafting" to 25, "stat.mining" to 15, "stat.magic" to 7, "stat.hitpoints" to 40)) {
                statMap.setBaseLevel(stat, level.toByte())
                statMap.setCurrentLevel(stat, level.toByte())
            }
        }

        val myq = InSearchOfTheMyrequeQuest()
        val iaom = InAidOfTheMyrequeQuest()
        val copies: InPlaceCopies
        val fights: VampyreFights
        val tithe: BloodTithe
        val escort: IvanEscort
        val rod: TestRod

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
            for ((x0, z0, x1, z1) in AREAS) {
                for (level in 0..2) for (x in x0..x1 step 8) for (z in z0..z1 step 8) collision.allocateIfAbsent(x, z, level)
            }
            players[player.slotId] = player
            val worldQueues = WorldQueueList()
            val ai = AiPlayerInteractions(events, players)
            val doors = PaterdomusDoors(locRepo, worldQueues, players, unused())
            val noHits = NpcHitModifier {}
            copies = InPlaceCopies(npcRepo)
            fights = VampyreFights(npcRepo, players)
            tithe = BloodTithe(iaom, copies, fights, doors, ai, noHits, random, players, worldQueues)
            escort = IvanEscort(iaom, copies, fights, doors, ai, noHits, random, players, clock)
            rod = TestRod(iaom)
            val citizens = BurghCitizens(iaom)
            val repairs = Repairs()
            val hollows = InAidHollows(iaom, escort, PetFollowers(npcRepo, npcs, clock, collision))
            val betrayal = Betrayal(myq, npcRepo, objRepo, NpcDeath(npcRepo, players, objRepo, emptySet(), emptySet()),
                players, worldQueues, ai)
            val scripts = ScriptContext(events, CheatCommandMap(), EngineQueueCache())
            for (script in listOf(
                myq, iaom, betrayal, citizens, BurghGate(iaom, citizens, doors, npcRepo), InnCellar(iaom, copies, locRepo, objRepo),
                GeneralStore(iaom, citizens, repairs, unused()), BurghRepairs(iaom, repairs), fights, tithe, hollows, escort,
                MyrequeMembers(myq, betrayal, hollows), PaterdomusLibrary(iaom), rod, BurghHideout(iaom),
            )) {
                with(script) { scripts.startup() }
            }
            myq.quest.jumpToStage(player, InSearchOfTheMyrequeQuest.STAGE_COMPLETE)
            if (stage > 0) iaom.quest.jumpToStage(player, stage)
        }

        fun access() = ProtectedAccess(player, coroutine, context)

        fun stage(): Int = iaom.stage(player)

        fun choose(vararg options: Int) {
            picks.addAll(options.toList())
        }

        fun give(obj: String, count: Int = 1) {
            val type = item(obj)
            if (type.stackable) {
                val existing = player.inv.indexOfFirst { it?.id == type.id }
                if (existing >= 0) {
                    player.inv[existing] = InvObj(obj, player.inv[existing]!!.count + count)
                    return
                }
                player.inv[player.inv.indexOfFirst { it == null }] = InvObj(obj, count)
                return
            }
            repeat(count) { player.inv[player.inv.indexOfFirst { it == null }] = InvObj(obj, 1) }
        }

        fun take(obj: String, count: Int) {
            val id = obj.asRSCM(RSCMType.OBJ)
            var left = count
            for (slot in 0 until player.inv.size) {
                val held = player.inv[slot] ?: continue
                if (held.id != id || left == 0) continue
                val removed = minOf(held.count, left)
                left -= removed
                player.inv[slot] = if (held.count == removed) null else InvObj(obj, held.count - removed)
            }
        }

        fun fillInventory() {
            while (player.inv.any { it == null }) give("obj.bread")
        }

        fun emptySlots(count: Int) {
            repeat(count) { take("obj.bread", 1) }
        }

        fun emptyFiller(count: Int) = emptySlots(count)

        fun count(obj: String): Int = player.inv.count(obj)

        fun said(text: String): Boolean = output().contains(text)

        fun xp(): Map<String, Int> = REWARD_STATS.associateWith { player.statMap.getXP(it) }

        fun liveNpcs(vararg types: String): Int = npcs.count { npc -> npc != null && types.any { npc.isType(it) } }

        fun liveRubble(): Int = BurghCoords.RUBBLE_PILES.count { tile -> InnCellar.RUBBLE_STAGES.any { loc(it, tile) } }

        fun loc(symbol: String, at: CoordGrid): Boolean = locRepo.findLoc(at, symbol)

        fun markPile(pile: Int) = iaom.markPileRemoved(player, pile)

        fun finishInSearch() {
            myq.quest.jumpToStage(player, InSearchOfTheMyrequeQuest.STAGE_COMPLETE)
            player.coords = CoordGrid(3505, 9836, 0)
        }

        fun gate() {
            player.coords = BurghCoords.GATE_OUTSIDE
            locOp(BurghGate.GATE_LEFT, BurghCoords.GATE_LEFT)
        }

        fun admit() {
            choose(4)
            npcOp(BurghGate.FLORIN, BurghCoords.GATE_INSIDE)
            useOnLoc(BurghGate.CHEST, BurghCoords.FOOD_CHEST, "obj.salmon")
        }

        fun askForHideaway() {
            choose(4)
            npcOp("npc.burgh_vilager_1")
        }

        fun bankCellarGear() {
            for (obj in listOf("obj.bronze_pickaxe", InAidOfTheMyrequeQuest.SPADE, InAidOfTheMyrequeQuest.BUCKET, "obj.limestone",
                "obj.broken_glass", InAidOfTheMyrequeQuest.DUSTY_SCROLL, InAidOfTheMyrequeQuest.PLASTER_FRAGMENT, "obj.salmon")) {
                take(obj, 28)
            }
        }

        fun kitForCellar(buckets: Int) {
            give("obj.bronze_pickaxe")
            give(InAidOfTheMyrequeQuest.SPADE)
            give(InAidOfTheMyrequeQuest.BUCKET, buckets)
        }

        fun openTrapdoor() {
            useOnLoc(InnCellar.RUBBLE_BLOCKED, BurghCoords.INN_RUBBLE, "obj.bronze_pickaxe", base = "loc.burgh_inn_colapsed_wall_multiloc")
            assertEquals(1, player.vars[InAidOfTheMyrequeQuest.INN_WALL], output())
            player.coords = BurghCoords.INN_WALL_INSIDE
            locOp("loc.burgh_inn_trapdoor_multiloc", BurghCoords.INN_TRAPDOOR)
            assertEquals(1, player.vars[InAidOfTheMyrequeQuest.INN_TRAPDOOR])
        }

        fun climbDown() {
            player.coords = BurghCoords.INN_WALL_INSIDE
            locOp("loc.burgh_inn_trapdoor_multiloc", BurghCoords.INN_TRAPDOOR)
        }

        fun mineFully(pile: Int) {
            val tile = BurghCoords.RUBBLE_PILES[pile]
            for (stage in 0 until 3) locOp(InnCellar.RUBBLE_STAGES[stage], tile)
        }

        fun clearPile(pile: Int) {
            mineFully(pile)
            locOp(InnCellar.RUBBLE_STAGES.last(), BurghCoords.RUBBLE_PILES[pile])
        }

        fun clearCellar() {
            kitForCellar(buckets = 5)
            openTrapdoor()
            climbDown()
            for (pile in 0 until InAidOfTheMyrequeQuest.RUBBLE_PILES) clearPile(pile)
            locOp(InnCellar.CELLAR_LADDER, BurghCoords.CELLAR_LADDER.copy(level = 1))
            for (bucket in 0 until 5) {
                useOnLoc(InnCellar.RUBBLE_DUMP, BurghCoords.RUBBLE_DUMP, InAidOfTheMyrequeQuest.RUBBLE_BUCKETS.last(), typed = true)
            }
            assertEquals(5, count(InAidOfTheMyrequeQuest.BUCKET))
        }

        fun climbToRoof() {
            player.coords = BurghCoords.STORE_LADDER_FOOT
            locOp(GeneralStore.LADDER_UP, BurghCoords.STORE_LADDER)
            assertEquals(BurghCoords.STORE_ROOF_ARRIVAL, player.coords)
        }

        fun repairStore() {
            climbToRoof()
            choose(1)
            locOp(GeneralStore.ROOF_HOLE, BurghCoords.STORE_ROOF_HOLE)
            locOp(GeneralStore.LADDER_DOWN, BurghCoords.STORE_ROOF_LADDER)
            choose(1)
            locOp("loc.burgh_general_store_wall_multiloc", BurghCoords.STORE_WALL)
            assertTrue(iaom.isStoreRepaired(player), output())
        }

        fun takeCrate() {
            choose(3)
            npcOp(GeneralStore.AUREL)
            assertEquals(1, count(InAidOfTheMyrequeQuest.CRATE), output())
        }

        fun stockCrate() {
            val food = checkNotNull(iaom.crateRequest(player))
            give(InAidOfTheMyrequeQuest.BRONZE_AXE, 10)
            give(InAidOfTheMyrequeQuest.TINDERBOX, 3)
            give(food.objs.first(), 10)
            for (obj in listOf(InAidOfTheMyrequeQuest.BRONZE_AXE, InAidOfTheMyrequeQuest.TINDERBOX, food.objs.first())) {
                choose(1)
                useOnCrate(obj)
            }
            assertTrue(iaom.isCrateFull(player), output())
        }

        fun repairBank() {
            choose(1)
            locOp("loc.burgh_bank_booth_multiloc", BurghCoords.BANK_BOOTH)
            choose(1)
            locOp("loc.burgh_bank_wall_multiloc", BurghCoords.BANK_WALL)
            assertEquals(1, player.vars[InAidOfTheMyrequeQuest.BANK_WALL], output())
        }

        fun lightFurnace() {
            repeat(3) {
                choose(1)
                locOp("loc.burgh_furnace_multiloc", BurghCoords.FURNACE)
            }
        }

        fun provokeTithe() {
            player.coords = BurghCoords.STORE_FIGHT_ENTRY
            npcOp(BloodTithe.GADDERANKS, BurghCoords.GADDERANKS)
            npcOp(BloodTithe.WISKIT, BurghCoords.GADDERANKS.translateX(1))
            npcOp(BloodTithe.TITHE_JUVINATES[0], BurghCoords.JUVINATE_ONE)
        }

        fun winTitheFight(veliafFinishes: Boolean, fullInventory: Boolean = false) {
            val fighters = tithe.fighters(player)
            assertEquals(3, fighters.size, output())
            val juvinates = fighters.filter { !it.isType(BloodTithe.GADDERANKS_FIGHTING) }
            fights.mist(juvinates[0])
            assertNotNull(tithe.veliafOf(player), "Veliaf arrives when the first juvinate turns to mist")
                fights.mist(juvinates[1])
            val gadderanks = fighters.single { it.isType(BloodTithe.GADDERANKS_FIGHTING) }
            if (veliafFinishes) {
                gadderanks.hitpoints = 0
            }
            fights.dispatch(gadderanks)
            assertEquals(STAGE_GADDERANKS_DEFEATED, stage())
            if (fullInventory) fillInventory()
            run { with(tithe) { finishFight() } }
            assertTrue(tithe.fighters(player).isEmpty())
        }

        fun escortIvan(route: IvanEscort.Route) {
            give("obj.steel_chainbody")
            useOnIvan("obj.steel_chainbody")
            startEscort(route)
            val escort = checkNotNull(this.escort.escortOf(player))
            for (juvinate in escort.juvinates.toList()) fights.mist(juvinate)
            assertEquals(STAGE_IVAN_DELIVERED, stage())
            run { with(this@Fixture.escort) { arrive(escort) } }
        }

        fun startEscort(route: IvanEscort.Route) {
            player.coords = CoordGrid(3505, 9836, 0)
            choose(if (route == IvanEscort.Route.Short) 1 else 2)
            npcOp(InAidOfTheMyrequeQuest.IVAN_HOLLOWS)
            assertNotNull(escort.escortOf(player), output())
        }

        fun askDrezel() {
            choose(1, 1, 2, 3, 4, 3)
            run {
                startDialogue {
                    with(PaterdomusLibrary(iaom)) { talk { mood, text -> chatNpcSpecific("Drezel", "npc.burgh_farmer", mood, text) } }
                }
            }
        }

        fun readTheSleepingSeven() {
            player.coords = BurghCoords.LIBRARY_KEYHOLE.translateX(-1)
            choose(1)
            useOnLoc(PaterdomusLibrary.KEYHOLE, BurghCoords.LIBRARY_KEYHOLE, InAidOfTheMyrequeQuest.LIBRARY_KEY, typed = true)
            assertEquals(1, player.vars[InAidOfTheMyrequeQuest.LIBRARY_TRAPDOOR], output())
            locOp("loc.burgh_temple_trapdoor_multiloc", BurghCoords.LIBRARY_TRAPDOOR)
            assertEquals(BurghCoords.LIBRARY_ARRIVAL, player.coords)
            locOp(PaterdomusLibrary.IVANDIS_BOOKCASE, BurghCoords.IVANDIS_BOOKCASE)
            assertEquals(1, count(InAidOfTheMyrequeQuest.SLEEPING_SEVEN))
            val slot = player.inv.indexOfFirst { it?.id == InAidOfTheMyrequeQuest.SLEEPING_SEVEN.asRSCM(RSCMType.OBJ) }
            dispatchUntilCancelled(HeldObjEvents.Op1(slot, player.inv[slot]!!, item(InAidOfTheMyrequeQuest.SLEEPING_SEVEN), player.inv)) {
                player.ui.containsModal(PaterdomusLibrary.BOOK_INTERFACE)
            }
        }

        fun makeMould() {
            give(InAidOfTheMyrequeQuest.HAMMER)
            give(InAidOfTheMyrequeQuest.SOFT_CLAY)
            choose(1)
            useOnLoc(RodOfIvandis.BOARDS, BurghCoords.TOMB_BOARDS, InAidOfTheMyrequeQuest.HAMMER, base = "loc.burgh_ivandis_tombdoor_board_multiloc")
            assertEquals(1, player.vars[InAidOfTheMyrequeQuest.TOMB_BOARDS], output())
            player.coords = BurghCoords.TOMB_OUTSIDE
            locOp(RodOfIvandis.TOMB_ENTRANCE, BurghCoords.TOMB_ENTRANCE)
            assertEquals(BurghCoords.TOMB_ARRIVAL, player.coords)
            locOp(RodOfIvandis.COFFIN, BurghCoords.IVANDIS_COFFIN)
            assertEquals(STAGE_TOMB_FOUND, stage())
            useOnLoc(RodOfIvandis.COFFIN, BurghCoords.IVANDIS_COFFIN, InAidOfTheMyrequeQuest.SOFT_CLAY, typed = true)
            assertEquals(1, count(InAidOfTheMyrequeQuest.ROD_MOULD))
        }

        fun makeRod() {
            give(InAidOfTheMyrequeQuest.SILVTHRILL)
            give(InAidOfTheMyrequeQuest.ROPE)
            enchant()
            useOnLoc(PaterdomusLibrary.WELL, BurghCoords.SALVE_WELL, InAidOfTheMyrequeQuest.SILVTHRILL_ENCHANTED, typed = true)
        }

        fun enchant() {
            val slot = player.inv.indexOfFirst { it?.id == InAidOfTheMyrequeQuest.SILVTHRILL.asRSCM(RSCMType.OBJ) }
            run { with(rod) { enchant(InAidOfTheMyrequeQuest.SILVTHRILL.asRSCM(RSCMType.OBJ), slot) } }
        }

        fun useOnIvan(obj: String) {
            val slot = player.inv.indexOfFirst { it?.id == obj.asRSCM(RSCMType.OBJ) }
            val npc = Npc(InAidOfTheMyrequeQuest.IVAN_HOLLOWS, player.coords.translateX(1))
            npcRepo.add(npc, Int.MAX_VALUE)
            dispatch(NpcUDefaultEvents.OpType(npc, slot, item(obj), npcType(InAidOfTheMyrequeQuest.IVAN_HOLLOWS)))
        }

        fun useOnCrate(obj: String) {
            val crate = player.inv.indexOfFirst { it?.id == InAidOfTheMyrequeQuest.CRATE.asRSCM(RSCMType.OBJ) }
            val slot = player.inv.indexOfFirst { it?.id == obj.asRSCM(RSCMType.OBJ) }
            dispatch(HeldUDefaultEvents.Type(item(InAidOfTheMyrequeQuest.CRATE), crate, item(obj), slot))
        }

        fun heldOp4(obj: String) {
            val slot = player.inv.indexOfFirst { it?.id == obj.asRSCM(RSCMType.OBJ) }
            dispatch(HeldObjEvents.Op4(slot, player.inv[slot]!!, item(obj), player.inv))
        }

        /** Uses [obj] on a loc: the type's own handler when [typed], else the loc's default one. */
        fun useOnLoc(symbol: String, coords: CoordGrid, obj: String, typed: Boolean = false, base: String = symbol) {
            val slot = player.inv.indexOfFirst { it?.id == obj.asRSCM(RSCMType.OBJ) }
            val visType = checkNotNull(ServerCacheManager.getObject(symbol.asRSCM(RSCMType.LOC)))
            val baseType = checkNotNull(ServerCacheManager.getObject(base.asRSCM(RSCMType.LOC)))
            val bound = BoundLocInfo(placedOrNew(coords, baseType.id), baseType)
            val vis = BoundLocInfo(placedOrNew(coords, visType.id), visType)
            val event = if (typed) LocUEvents.Op(bound, vis, visType, item(obj), slot) else LocUDefaultEvents.OpType(bound, vis, visType, item(obj), slot)
            dispatch(event)
        }

        /** The loc the scripts spawned at [coords], so its layer and angle match; else a stand-in. */
        private fun placedOrNew(coords: CoordGrid, id: Int): LocInfo =
            locRepo.findAll(coords).firstOrNull { it.id == id } ?: LocInfo(0, coords, LocEntity(id, 10, 0))

        fun saveAndReload(): Player {
            val loaded = Player()
            loaded.vars.backing.putAll(player.vars.backing)
            player.attr[QUEST_STAGE_MAP_ATTR]?.let { loaded.attr[QUEST_STAGE_MAP_ATTR] = it.toMutableMap() }
            iaom.quest.syncState(loaded)
            return loaded
        }

        fun npcOp(type: String, at: CoordGrid = player.coords.translateX(1), op: InteractionOp = InteractionOp.Op1) {
            val npc = Npc(type, at)
            npcRepo.add(npc, Int.MAX_VALUE)
            dispatch(if (op == InteractionOp.Op3) NpcEvents.Op3(npc) else NpcEvents.Op1(npc))
        }

        fun locOp(symbol: String, coords: CoordGrid, op: InteractionOp = InteractionOp.Op1) {
            val type = checkNotNull(ServerCacheManager.getObject(symbol.asRSCM(RSCMType.LOC))) { symbol }
            val loc = BoundLocInfo(placedOrNew(coords, type.id), type)
            val event = LocInteractions(BoundValidator(collision), events).opTrigger(player, loc, op)
            dispatch(checkNotNull(event) { "No $op handler for $symbol" })
        }

        fun run(block: suspend ProtectedAccess.() -> Unit) {
            start { block(access()) }
            finish()
        }

        fun dispatchUntilCancelled(event: SuspendEvent<ProtectedAccess>, until: () -> Boolean) {
            start { assertTrue(events.publish(access(), event)) }
            repeat(400) {
                if (until()) {
                    coroutine.cancel()
                    assertInstanceOf(CancellationException::class.java, result?.exceptionOrNull())
                    result = null
                    player.activeCoroutine = null
                    picks.clear()
                    return
                }
                step()
            }
            fail<Unit>("Never reached the cut: ${output()}")
        }

        private fun item(obj: String): ItemServerType = checkNotNull(ServerCacheManager.getItem(obj.asRSCM(RSCMType.OBJ))) { obj }

        private fun npcType(npc: String) = checkNotNull(ServerCacheManager.getNpc(npc.asRSCM(RSCMType.NPC)))

        private fun start(block: suspend () -> Unit) {
            player.clearPendingAction(events)
            result = null
            player.activeCoroutine = coroutine
            block.startCoroutine(object : Continuation<Unit> {
                override val context = EmptyCoroutineContext
                override fun resumeWith(result: Result<Unit>) { this@Fixture.result = result }
            })
            result?.getOrThrow()
        }

        private fun dispatch(event: SuspendEvent<ProtectedAccess>) {
            start { assertTrue(events.publish(access(), event), "nothing handles $event") }
            finish()
        }

        private fun finish() {
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
        const val SEED = 11L
        const val ENCHANT_XP = 17.5

        val REWARD_STATS = listOf("stat.attack", "stat.strength", "stat.crafting", "stat.defence")

        /** Burgh de Rott, the inn cellar, Canifis, the tunnels and tomb, Paterdomus and the clearing. */
        val AREAS =
            listOf(
                listOf(3456, 3184, 3583, 3263),
                listOf(3456, 9600, 3519, 9663),
                listOf(3456, 3456, 3519, 3519),
                listOf(3456, 9792, 3519, 9919),
                listOf(3328, 9856, 3455, 9919),
                listOf(3392, 3456, 3455, 3519),
                listOf(1984, 4992, 2047, 5055),
            )

        inline fun <reified T> unused(): T {
            val field = Unsafe::class.java.getDeclaredField("theUnsafe").apply { isAccessible = true }
            return (field.get(null) as Unsafe).allocateInstance(T::class.java) as T
        }

        private val restored = mutableListOf<() -> Unit>()

        @OptIn(InternalApi::class)
        @JvmStatic @BeforeAll fun cache() {
            ServerCacheManager.init(240).close()
            val previous = QuestRequirements.activePolicy()
            restored += { QuestRequirements.install(previous) }
            QuestRequirements.install(QuestRequirementPolicy(QuestRequirementMode.RespectProgress))
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
