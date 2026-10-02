package org.rsmod.content.quest.area.hemenster.fishingcontest

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.InventoryServerType
import dev.openrune.types.NpcMode
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
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.inv.storage.PlayerItemStorage
import org.rsmod.api.invtx.InvTransactionsScript
import org.rsmod.api.player.dialogue.align.TextAlignment
import org.rsmod.api.player.events.interact.NpcEvents
import org.rsmod.api.player.hook.PlayerTeleportValidator
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
import org.rsmod.api.registry.region.RegionRegistry
import org.rsmod.api.registry.zone.ZonePlayerActivityBitSet
import org.rsmod.api.registry.zone.ZoneUpdateMap
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.route.BoundValidator
import org.rsmod.content.quest.area.hemenster.fishingcontest.FishingCompetition.Spot
import org.rsmod.content.quest.area.hemenster.fishingcontest.FishingContestQuest.Companion.AUSTRI
import org.rsmod.content.quest.area.hemenster.fishingcontest.FishingContestQuest.Companion.BONZO
import org.rsmod.content.quest.area.hemenster.fishingcontest.FishingContestQuest.Companion.CARP
import org.rsmod.content.quest.area.hemenster.fishingcontest.FishingContestQuest.Companion.COINS
import org.rsmod.content.quest.area.hemenster.fishingcontest.FishingContestQuest.Companion.FLY_ROD
import org.rsmod.content.quest.area.hemenster.fishingcontest.FishingContestQuest.Companion.GARLIC
import org.rsmod.content.quest.area.hemenster.fishingcontest.FishingContestQuest.Companion.GRANDPA_JACK
import org.rsmod.content.quest.area.hemenster.fishingcontest.FishingContestQuest.Companion.MORRIS
import org.rsmod.content.quest.area.hemenster.fishingcontest.FishingContestQuest.Companion.OILY_ROD
import org.rsmod.content.quest.area.hemenster.fishingcontest.FishingContestQuest.Companion.PASS
import org.rsmod.content.quest.area.hemenster.fishingcontest.FishingContestQuest.Companion.ROD
import org.rsmod.content.quest.area.hemenster.fishingcontest.FishingContestQuest.Companion.ROUND_STEPS
import org.rsmod.content.quest.area.hemenster.fishingcontest.FishingContestQuest.Companion.SPADE
import org.rsmod.content.quest.area.hemenster.fishingcontest.FishingContestQuest.Companion.STAGE_COMPLETE
import org.rsmod.content.quest.area.hemenster.fishingcontest.FishingContestQuest.Companion.STAGE_ENTERED
import org.rsmod.content.quest.area.hemenster.fishingcontest.FishingContestQuest.Companion.STAGE_GROUNDS
import org.rsmod.content.quest.area.hemenster.fishingcontest.FishingContestQuest.Companion.STAGE_STARTED
import org.rsmod.content.quest.area.hemenster.fishingcontest.FishingContestQuest.Companion.STAGE_TROPHY
import org.rsmod.content.quest.area.hemenster.fishingcontest.FishingContestQuest.Companion.STRANGER
import org.rsmod.content.quest.area.hemenster.fishingcontest.FishingContestQuest.Companion.TROPHY
import org.rsmod.content.quest.area.hemenster.fishingcontest.FishingContestQuest.Companion.VESTRI
import org.rsmod.content.quest.area.hemenster.fishingcontest.FishingContestQuest.Companion.WORMS
import org.rsmod.content.quest.area.hemenster.fishingcontest.npcs.Competitors
import org.rsmod.content.quest.area.hemenster.fishingcontest.npcs.GrandpaJack
import org.rsmod.content.quest.area.hemenster.fishingcontest.npcs.TunnelDwarves
import org.rsmod.content.quest.manager.QUEST_STAGE_MAP_ATTR
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
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.game.region.RegionListLarge
import org.rsmod.game.region.RegionListSmall
import org.rsmod.game.region.RegionListWorldEntity
import org.rsmod.game.seq.EntitySeq
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

/**
 * Drives Fishing Contest's real scripts through the event bus: the whole path started and finished
 * at either dwarf, every missing pass, rod, bait, garlic, coin and level refusal, the wood and the
 * railing, the garlic and the Stranger, fishing the right and wrong spots, losing and retrying,
 * full packs, duplicate payments and rewards, both tunnel ends, and save and reload.
 */
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class FishingContestInteractionTest {

    @Test fun `the whole quest started at Austri and finished at Vestri`() {
        val f = Fixture()
        f.startAt(AUSTRI)
        assertEquals(STAGE_STARTED, f.stage())
        assertEquals(1, f.count(PASS))
        assertTrue(f.journal().contains("fishing pass"))

        f.gate()
        assertEquals(STAGE_GROUNDS, f.stage())
        assertEquals(1, f.count(PASS), "the pass is shown, not taken")
        assertEquals(HemensterGate.INSIDE_X, f.player.routeDestination.lastOrNull()?.x)

        f.give(COINS, 10)
        f.choose(2, 1)
        f.talk(GRANDPA_JACK)
        assertEquals(1, f.count(ROD))
        assertEquals(5, f.count(COINS))

        f.give(SPADE)
        repeat(3) { f.checkVine() }
        assertEquals(3, f.count(WORMS))

        f.give(GARLIC)
        f.garlicOnPipe()
        assertEquals(0, f.count(GARLIC))
        assertTrue(f.fc.isGarlicPlaced(f.player))

        val stranger = f.spawnStranger()
        f.enter()
        assertEquals(STAGE_ENTERED, f.stage())
        assertEquals(0, f.count(COINS))
        assertTrue(f.fc.isRoundActive(f.player))
        assertTrue(f.fc.isStrangerMoved(f.player))
        assertEquals(NpcMode.None, stranger.mode)
        assertTrue(f.said("Garlic!") || f.said("whiff of garlic"))

        f.fishUntil(Spot.PIPES) { f.count(CARP) == 1 }
        assertTrue(f.count(WORMS) < 3)
        f.endRound()
        f.talk(BONZO)
        assertEquals(STAGE_TROPHY, f.stage())
        assertEquals(1, f.count(TROPHY))
        assertEquals(0, f.count(CARP))

        val xp = f.fishingXp()
        f.talk(VESTRI)
        assertEquals(STAGE_COMPLETE, f.stage())
        assertEquals(0, f.count(TROPHY))
        assertEquals(1, f.player.vars["varp.qp"])
        assertEquals(2_437, f.fishingXp() - xp)
        assertTrue(f.player.ui.containsModal("interface.questscroll"))
    }

    @Test fun `either dwarf starts the same quest, and the other one knows`() {
        val f = Fixture()
        f.startAt(VESTRI)
        assertEquals(STAGE_STARTED, f.stage())
        f.talk(AUSTRI)
        assertEquals(STAGE_STARTED, f.stage())
        assertEquals(1, f.count(PASS), "a second dwarf hands out no second pass")

        val g = Fixture(STAGE_TROPHY)
        g.give(TROPHY)
        g.talk(AUSTRI)
        assertEquals(STAGE_COMPLETE, g.stage())
    }

    @Test fun `declining a dwarf leaves the quest unstarted`() {
        val f = Fixture()
        f.choose(2)
        f.talk(AUSTRI)
        assertEquals(0, f.stage())
        f.choose(1, 2)
        f.talk(VESTRI)
        assertEquals(0, f.stage())
        assertEquals(0, f.count(PASS))
    }

    @Test fun `too low a Fishing level stops the start, the entry and the cast`() {
        val f = Fixture()
        f.setFishing(9)
        f.choose(1, 1)
        f.talk(AUSTRI)
        assertEquals(0, f.stage())
        assertTrue(f.said("Fishing level of 10"))

        val g = Fixture(STAGE_GROUNDS)
        g.kit()
        g.setFishing(9)
        g.enter()
        assertEquals(5, g.count(COINS))
        assertFalse(g.fc.isRoundActive(g.player))
        assertTrue(g.said("level 10"))
    }

    @Test fun `a full pack at the start keeps the quest going and the pass at the dwarf`() {
        val f = Fixture()
        f.fill()
        f.startAt(AUSTRI)
        assertEquals(STAGE_STARTED, f.stage())
        assertEquals(0, f.count(PASS))
        assertTrue(f.said("no room"))
        f.player.inv[0] = null
        f.talk(AUSTRI)
        assertEquals(1, f.count(PASS))
    }

    @Test fun `Morris refuses without a pass and a lost pass is replaced`() {
        val f = Fixture(STAGE_STARTED)
        f.gate()
        assertFalse(f.fc.hasShownPass(f.player))
        assertNull(f.player.routeDestination.lastOrNull())
        assertTrue(f.said("No pass, no fishing"))
        assertTrue(f.journal().contains("lost"))

        f.talk(VESTRI)
        assertEquals(1, f.count(PASS))
        f.gate()
        assertTrue(f.fc.hasShownPass(f.player))
        assertEquals(STAGE_GROUNDS, f.stage())

        f.drop(PASS)
        f.gate()
        assertEquals(HemensterGate.INSIDE_X, f.player.routeDestination.lastOrNull()?.x, "Morris remembers the face")

        val g = Fixture()
        g.give(PASS)
        g.talk(MORRIS)
        assertFalse(g.fc.hasShownPass(g.player), "a found pass doesn't start the quest")
    }

    @Test fun `leaving the grounds never needs a pass`() {
        val f = Fixture(STAGE_STARTED)
        f.gate(from = CoordGrid(HemensterGate.INSIDE_X, 3441, 0))
        assertEquals(HemensterGate.OUTSIDE_X, f.player.routeDestination.lastOrNull()?.x)
    }

    @Test fun `Bonzo takes no fee without a plain rod, worms or five coins`() {
        val f = Fixture(STAGE_GROUNDS)
        f.give(COINS, 5)
        f.give(WORMS, 3)
        f.enter()
        assertTrue(f.said("fishing rod"))
        f.give(FLY_ROD)
        f.give(OILY_ROD)
        f.enter()
        assertTrue(f.said("Fly fishing and oily rods aren't allowed"))
        assertEquals(5, f.count(COINS))

        f.give(ROD)
        f.drop(WORMS)
        f.enter()
        assertTrue(f.said("bait"))

        f.give(WORMS, 3)
        f.drop(COINS)
        f.give(COINS, 4)
        f.enter()
        assertTrue(f.said("a bit short"))
        assertEquals(4, f.count(COINS))
        assertFalse(f.fc.isFeePaid(f.player))
        assertEquals(STAGE_GROUNDS, f.stage())
    }

    @Test fun `a fly or oily rod can't fish a contest spot`() {
        val f = Fixture(STAGE_GROUNDS)
        f.kit()
        f.enter()
        f.drop(ROD)
        f.give(FLY_ROD)
        f.give(OILY_ROD)
        f.spot(Spot.OPEN)
        assertTrue(f.said("Fly fishing and oily rods aren't allowed"))
        f.cast(Spot.OPEN)
        assertEquals(0, f.count("obj.raw_sardine"))
    }

    @Test fun `nobody fishes the contest outside a paid round`() {
        val f = Fixture(STAGE_GROUNDS)
        f.kit()
        f.spot(Spot.OPEN)
        assertTrue(f.said("enter the competition first"))
        f.cast(Spot.OPEN)
        assertEquals(3, f.count(WORMS))
    }

    @Test fun `the Stranger guards his spot until the garlic drives him off`() {
        val f = Fixture(STAGE_GROUNDS)
        f.kit()
        f.enter()
        f.spot(Spot.PIPES)
        assertTrue(f.said("This spot is mine"))
        f.cast(Spot.PIPES)
        assertEquals(0, f.count(CARP))

        f.give(GARLIC)
        f.garlicOnPipe()
        assertTrue(f.fc.isStrangerMoved(f.player), "garlic during a round works at once")
        f.fishUntil(Spot.PIPES) { f.count(CARP) == 1 }
    }

    @Test fun `garlic is only used up when it goes in the pipe`() {
        val f = Fixture()
        f.give(GARLIC)
        f.garlicOnPipe()
        assertEquals(1, f.count(GARLIC))
        assertTrue(f.said("Why would you"))

        f.jump(STAGE_GROUNDS)
        f.give(GARLIC)
        f.garlicOnPipe()
        assertEquals(1, f.count(GARLIC))
        f.garlicOnPipe()
        assertEquals(1, f.count(GARLIC))
        assertTrue(f.said("already garlic"))
        f.searchPipe()
        assertTrue(f.said("reeks of garlic"))
    }

    @Test fun `the open spot only ever gives sardines and the champions' spots are taken`() {
        val f = Fixture(STAGE_GROUNDS)
        f.kit()
        f.give(WORMS, 20)
        f.enter()
        repeat(40) { f.cast(Spot.OPEN) }
        assertEquals(0, f.count(CARP))
        assertTrue(f.count("obj.raw_sardine") > 0)
        f.npcOp(FishingCompetition.BIG_DAVE_SPOT)
        assertTrue(f.said("This is my spot"))
    }

    @Test fun `running out of worms mid-round says so and more can be dug`() {
        val f = Fixture(STAGE_GROUNDS)
        f.kit()
        f.drop(WORMS)
        f.give(WORMS, 1)
        f.enter()
        f.fishUntil(Spot.OPEN) { f.count(WORMS) == 0 }
        f.cast(Spot.OPEN)
        assertTrue(f.said("run out of red vine worms"))
        f.give(SPADE)
        f.checkVine()
        assertEquals(1, f.count(WORMS))
    }

    @Test fun `a lost round is judged, costs nothing more, and can be retried`() {
        val f = Fixture(STAGE_GROUNDS)
        f.kit()
        f.enter()
        assertEquals(0, f.count(COINS))
        f.fishUntil(Spot.OPEN) { f.count("obj.raw_sardine") > 0 }
        f.endRound()
        assertTrue(f.said("Time's up"))
        assertTrue(f.fc.isAwaitingJudging(f.player))
        f.spot(Spot.OPEN)
        assertTrue(f.said("The round is over"))
        f.talk(BONZO)
        assertTrue(f.said("Big Dave takes it again"))
        assertFalse(f.fc.isFeePaid(f.player))
        assertEquals(STAGE_ENTERED, f.stage())
        assertTrue(f.journal().contains("5 coins"))

        f.give(GARLIC)
        f.garlicOnPipe()
        f.give(COINS, 5)
        f.enter()
        assertEquals(0, f.count(COINS))
        assertTrue(f.fc.isStrangerMoved(f.player))
        f.fishUntil(Spot.PIPES) { f.count(CARP) == 1 }
        f.choose(1)
        f.talk(BONZO)
        assertEquals(STAGE_TROPHY, f.stage())
        assertFalse(f.fc.isRoundActive(f.player))
    }

    @Test fun `the garlic stays put for a retry after a loss`() {
        val f = Fixture(STAGE_GROUNDS)
        f.kit()
        f.give(GARLIC)
        f.garlicOnPipe()
        f.enter()
        f.endRound()
        f.talk(BONZO)
        assertFalse(f.fc.isStrangerMoved(f.player))
        assertTrue(f.fc.isGarlicPlaced(f.player))
        f.give(COINS, 5)
        f.enter()
        assertTrue(f.fc.isStrangerMoved(f.player))
    }

    @Test fun `paying twice or pressing Pay mid-round charges once`() {
        val f = Fixture(STAGE_GROUNDS)
        f.kit()
        f.give(COINS, 5)
        f.enter()
        assertEquals(5, f.count(COINS))
        f.enter()
        f.pay()
        assertEquals(5, f.count(COINS))
        assertTrue(f.fc.isRoundActive(f.player))
        assertTrue(f.said("Get fishing"))
    }

    @Test fun `declining at the payment prompt takes nothing`() {
        val f = Fixture(STAGE_GROUNDS)
        f.kit()
        f.choose(1, 2)
        f.talk(BONZO)
        assertEquals(5, f.count(COINS))
        assertFalse(f.fc.isFeePaid(f.player))
    }

    @Test fun `a round cut short by logging out restarts free`() {
        val f = Fixture(STAGE_GROUNDS)
        f.kit()
        f.enter()
        val loaded = f.saveAndReload()
        assertTrue(f.fc.isFeePaid(loaded))
        assertFalse(f.fc.isRoundActive(loaded), "the round clock is not saved")
        f.adopt(loaded)
        assertTrue(f.journal().contains("cut short"))
        f.choose(1)
        f.pay()
        assertTrue(f.said("No need to pay twice"))
        assertTrue(f.fc.isRoundActive(f.player))
        assertEquals(0, f.count(COINS))
    }

    @Test fun `the round clock counts down and ends the round`() {
        val f = Fixture(STAGE_GROUNDS)
        f.kit()
        f.enter()
        repeat(ROUND_STEPS - 1) { f.comp.tick(f.player) }
        assertTrue(f.fc.isRoundActive(f.player))
        assertTrue(f.said("One minute left") && f.said("Thirty seconds left"))
        f.comp.tick(f.player)
        assertFalse(f.fc.isRoundActive(f.player))
        assertTrue(f.fc.isAwaitingJudging(f.player))
        f.comp.tick(f.player)
        assertTrue(f.fc.isAwaitingJudging(f.player))
    }

    @Test fun `a full pack keeps the worm when the fish won't fit, and swaps the carp for the trophy`() {
        val f = Fixture(STAGE_GROUNDS)
        f.kit()
        f.give(GARLIC)
        f.garlicOnPipe()
        f.enter()
        f.fill()
        repeat(10) { f.cast(Spot.PIPES) }
        assertEquals(0, f.count(CARP))
        assertEquals(3, f.count(WORMS))
        assertTrue(f.said("enough inventory space"))

        f.drop("obj.bronze_dagger")
        f.fishUntil(Spot.PIPES) { f.count(CARP) == 1 }
        assertEquals(0, f.player.inv.freeSpace())
        f.endRound()
        f.talk(BONZO)
        assertEquals(1, f.count(TROPHY))
        assertEquals(STAGE_TROPHY, f.stage())
    }

    @Test fun `the trophy can't be won twice and is only replaced when lost`() {
        val f = Fixture(STAGE_TROPHY)
        f.give(TROPHY)
        f.talk(BONZO)
        f.pay()
        assertEquals(1, f.count(TROPHY))
        f.drop(TROPHY)
        f.talk(BONZO)
        assertEquals(1, f.count(TROPHY))
        f.talk(BONZO)
        assertEquals(1, f.count(TROPHY))
        assertTrue(f.journal().contains("Austri or Vestri"))
    }

    @Test fun `the dwarves ask for the trophy before finishing`() {
        val f = Fixture(STAGE_TROPHY)
        f.talk(VESTRI)
        assertEquals(STAGE_TROPHY, f.stage())
        assertTrue(f.said("left it somewhere"))
    }

    @Test fun `rewards are granted once`() {
        val f = Fixture(STAGE_TROPHY)
        f.give(TROPHY)
        f.talk(AUSTRI)
        val xp = f.fishingXp()
        f.access().ifCloseSub("interface.questscroll")
        f.give(TROPHY)
        f.talk(VESTRI)
        f.talk(AUSTRI)
        assertEquals(1, f.count(TROPHY))
        assertEquals(xp, f.fishingXp())
        assertEquals(1, f.player.vars["varp.qp"])
        assertFalse(f.player.ui.containsModal("interface.questscroll"))
    }

    @Test fun `both ends of the tunnel stay shut until the quest is done, then both open`() {
        val f = Fixture(STAGE_TROPHY)
        f.stairs(WhiteWolfTunnel.WEST_DOWN, CoordGrid(2820, 3484, 0))
        assertTrue(f.said("friends of the dwarves only"))
        assertEquals(TOWN, f.player.coords)
        f.stairs(WhiteWolfTunnel.EAST_DOWN, CoordGrid(2876, 3480, 0))
        assertEquals(TOWN, f.player.coords)

        f.give(TROPHY)
        f.talk(AUSTRI)
        f.stairs(WhiteWolfTunnel.WEST_DOWN, CoordGrid(2820, 3484, 0))
        assertEquals(WhiteWolfTunnel.WEST_BOTTOM, f.player.coords)
        f.stairs(WhiteWolfTunnel.EAST_UP, CoordGrid(2876, 9880, 0))
        assertEquals(WhiteWolfTunnel.EAST_TOP, f.player.coords)
        f.stairs(WhiteWolfTunnel.EAST_DOWN, CoordGrid(2876, 3480, 0))
        assertEquals(WhiteWolfTunnel.EAST_BOTTOM, f.player.coords)
        f.stairs(WhiteWolfTunnel.WEST_UP, CoordGrid(2820, 9883, 0))
        assertEquals(WhiteWolfTunnel.WEST_TOP, f.player.coords)
    }

    @Test fun `the stairs up never trap anyone below`() {
        val f = Fixture()
        f.stairs(WhiteWolfTunnel.WEST_UP, CoordGrid(2820, 9883, 0))
        assertEquals(WhiteWolfTunnel.WEST_TOP, f.player.coords)
    }

    @Test fun `the wood has a locked gate, a two-way loose railing and vines that need a spade`() {
        val f = Fixture()
        f.loc(McGruborsWood.GATE_LEFT, CoordGrid(2650, 3470, 0))
        assertTrue(f.said("The gate is locked"))

        f.player.coords = McGruborsWood.OUTSIDE
        f.loc(McGruborsWood.LOOSE_RAILING, McGruborsWood.INSIDE)
        assertEquals(McGruborsWood.INSIDE, f.player.coords)
        f.loc(McGruborsWood.LOOSE_RAILING, McGruborsWood.INSIDE)
        assertEquals(McGruborsWood.OUTSIDE, f.player.coords)

        f.checkVine()
        assertEquals(0, f.count(WORMS))
        assertTrue(f.said("need a spade"))
        f.give(SPADE)
        f.fill()
        f.checkVine()
        assertEquals(0, f.count(WORMS))
        assertTrue(f.said("room to carry"))
        f.drop("obj.bronze_dagger")
        repeat(5) { f.checkVine() }
        assertEquals(5, f.count(WORMS))
        assertEquals(1, f.player.inv.count { it?.id == WORMS.asRSCM() }, "worms stack in one slot")
    }

    @Test fun `Grandpa Jack sells a rod for five coins, refunds on a full pack and lends a spade`() {
        val f = Fixture(STAGE_GROUNDS)
        f.give(COINS, 4)
        f.choose(2, 1)
        f.talk(GRANDPA_JACK)
        assertEquals(0, f.count(ROD))
        assertEquals(4, f.count(COINS))

        f.give(COINS, 2)
        f.fill()
        f.choose(2, 1)
        f.talk(GRANDPA_JACK)
        assertEquals(0, f.count(ROD))
        assertEquals(6, f.count(COINS), "the coins are refunded when the rod won't fit")

        f.drop("obj.bronze_dagger")
        f.choose(3)
        f.talk(GRANDPA_JACK)
        assertEquals(1, f.count(SPADE))

        f.choose(1)
        f.talk(GRANDPA_JACK)
        assertTrue(f.said("red vine worms") && f.said("McGrubor's Wood") && f.said("wall pipes"))
        assertFalse(f.said("Use the garlic"), "Jack hints; he doesn't solve it")
    }

    @Test fun `save and reload keeps every objective`() {
        val f = Fixture()
        f.startAt(AUSTRI)
        f.gate()
        f.give(GARLIC)
        f.garlicOnPipe()
        val loaded = f.saveAndReload()
        assertEquals(STAGE_GROUNDS, f.fc.stage(loaded))
        assertTrue(f.fc.hasShownPass(loaded))
        assertTrue(f.fc.isGarlicPlaced(loaded))

        f.adopt(loaded)
        f.kit()
        f.enter()
        f.fishUntil(Spot.PIPES) { f.count(CARP) == 1 }
        f.endRound()
        var again = f.saveAndReload()
        assertTrue(f.fc.isAwaitingJudging(again), "a finished round waits for judging across a relog")
        f.adopt(again)
        f.talk(BONZO)
        f.talk(VESTRI)
        again = f.saveAndReload()
        assertTrue(f.fc.isComplete(again))
    }

    @Test fun `resetting the quest clears the contest state`() {
        val f = Fixture(STAGE_GROUNDS)
        f.kit()
        f.give(GARLIC)
        f.garlicOnPipe()
        f.enter()
        f.fc.quest.resetQuest(f.player)
        assertEquals(0, f.stage())
        assertFalse(f.fc.isGarlicPlaced(f.player))
        assertFalse(f.fc.hasShownPass(f.player))
        assertFalse(f.fc.isFeePaid(f.player))
        assertFalse(f.fc.isRoundActive(f.player))
    }

    @Test fun `hints only ever name the current obstacle`() {
        val f = Fixture()
        assertTrue(f.fc.hint(f.player).contains("Austri"))
        f.jump(STAGE_STARTED)
        f.give(PASS)
        assertTrue(f.fc.hint(f.player).contains("Morris"))
        f.jump(STAGE_GROUNDS)
        assertTrue(f.fc.hint(f.player).contains("Grandpa Jack"))
        assertFalse(f.fc.hint(f.player).contains("garlic"))
        f.give(ROD)
        assertTrue(f.fc.hint(f.player).contains("red vine worms"))
        f.give(WORMS)
        assertTrue(f.fc.hint(f.player).contains("pale"))
        f.give(GARLIC)
        assertTrue(f.fc.hint(f.player).contains("pipe"))
        f.garlicOnPipe()
        assertTrue(f.fc.hint(f.player).contains("5 coins"))
        f.jump(STAGE_TROPHY)
        assertTrue(f.fc.hint(f.player).contains("Bonzo"))
        f.give(TROPHY)
        assertTrue(f.fc.hint(f.player).contains("Austri or Vestri"))
    }

    private class Fixture(stage: Int = 0) {
        val events = EventBus()
        private val client = RecordingClient()
        private val collision = CollisionFlagMap()
        private val npcs = NpcList()
        private val coroutine = GameCoroutine("fishing-contest-test")
        private var result: Result<Unit>? = null
        private val context = ProtectedAccessContextFactory.empty().copy(
            getEventBus = { events }, getAlignment = { TextAlignment() },
            getCollision = { collision }, getNpcList = { npcs },
            getNpcInteractions = { NpcInteractions(events) },
            getRandom = { random },
            getTeleportValidator = { PlayerTeleportValidator(emptySet()) },
            getAreaChecker = { AreaChecker(regions, AreaIndex()) },
        )
        private val random = DefaultGameRandom(Random(7))
        private val clock = MapClock(100)
        val npcRepo: NpcRepository
        private val locRepo: LocRepository
        private lateinit var regions: RegionRegistry
        private val picks = ArrayDeque<Int>()
        private val locU = LocUInteractions::class.java.getDeclaredConstructor(EventBus::class.java)
            .apply { isAccessible = true }.newInstance(events)

        @OptIn(InternalApi::class)
        var player = newPlayer()

        val fc = FishingContestQuest()
        val comp: FishingCompetition

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
            for ((x0, z0) in listOf(2624 to 3392, 2624 to 3456, 2816 to 3456, 2816 to 9856)) {
                for (x in x0 until x0 + 64 step 8) for (z in z0 until z0 + 64 step 8) collision.allocateIfAbsent(x, z, 0)
            }
            comp = FishingCompetition(fc, npcs, WorldRepository(updates))
            val scripts = ScriptContext(events, CheatCommandMap(), EngineQueueCache())
            with(fc) { scripts.startup() }
            with(comp) { scripts.startup() }
            with(TunnelDwarves(fc)) { scripts.startup() }
            with(GrandpaJack(fc)) { scripts.startup() }
            with(Competitors(fc)) { scripts.startup() }
            with(HemensterGate(fc, locRepo)) { scripts.startup() }
            with(McGruborsWood(fc)) { scripts.startup() }
            with(WhiteWolfTunnel(fc)) { scripts.startup() }
            if (stage > 0) {
                fc.quest.jumpToStage(player, stage)
                if (stage >= STAGE_GROUNDS) fc.markPassShown(player)
            }
        }

        @OptIn(InternalApi::class)
        private fun newPlayer() = Player().apply {
            this.client = this@Fixture.client
            uuid = 4242L
            observerUUID = 4242L
            slotId = 1
            assignUid()
            coords = TOWN
            currentMapClock = 100
            processedMapClock = 100
            pendingSequence = EntitySeq.NULL
            pendingFaceAngle = EntityFaceAngle.NULL
            inv = Inventory(InventoryServerType(size = 28, flags = 0), arrayOfNulls(28))
            worn = Inventory(InventoryServerType(size = 14, flags = 0), arrayOfNulls(14))
            statMap.setBaseLevel("stat.fishing", 10)
            statMap.setCurrentLevel("stat.fishing", 10)
        }

        fun access() = ProtectedAccess(player, coroutine, context)

        fun stage(): Int = fc.stage(player)

        fun jump(stage: Int) = fc.quest.jumpToStage(player, stage)

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

        fun kit() {
            give(ROD)
            give(WORMS, 3)
            give(COINS, 5)
        }

        fun fill() {
            while (player.inv.freeSpace() > 0) give("obj.bronze_dagger")
        }

        fun drop(obj: String) {
            val slot = player.inv.indexOfFirst { it?.id == obj.asRSCM() }
            player.inv[slot] = null
        }

        fun count(obj: String): Int = player.inv.count(obj)

        fun said(text: String): Boolean = output().contains(text)

        fun fishingXp(): Int = player.statMap.getXP("stat.fishing")

        @OptIn(InternalApi::class)
        fun setFishing(level: Int) {
            player.statMap.setBaseLevel("stat.fishing", level.toByte())
            player.statMap.setCurrentLevel("stat.fishing", level.toByte())
        }

        fun journal(): String = fc.questLog(access())

        fun startAt(dwarf: String) {
            choose(1, 1)
            talk(dwarf)
        }

        fun enter() {
            choose(1, 1)
            talk(BONZO)
        }

        fun pay() = npcOp(BONZO, InteractionOp.Op3)

        fun endRound() {
            repeat(ROUND_STEPS) { comp.tick(player) }
        }

        fun spawnStranger(): Npc {
            val npc = Npc(STRANGER, FishingCompetition.STRANGER_POST)
            npcRepo.add(npc, Int.MAX_VALUE)
            return npc
        }

        fun gate(from: CoordGrid = OUTSIDE_GATE) {
            player.coords = from
            player.routeDestination.clear()
            loc(HemensterGate.GATE_RIGHT, CoordGrid(HemensterGate.INSIDE_X, 3441, 0), wall = true)
        }

        fun checkVine() = loc("loc.red_worm_vine", CoordGrid(2631, 3498, 0))

        fun searchPipe() = loc(FishingCompetition.PIPE, CoordGrid(2637, 3446, 0))

        fun garlicOnPipe() = locU(FishingCompetition.PIPE, CoordGrid(2637, 3446, 0), GARLIC)

        fun spot(spot: Spot) = npcOp(if (spot == Spot.PIPES) FishingCompetition.PIPE_SPOT else FishingCompetition.OPEN_SPOT)

        fun cast(spot: Spot) = dispatch { with(comp) { cast(spot) } }

        fun fishUntil(spot: Spot, done: () -> Boolean) {
            repeat(60) {
                if (done()) return
                cast(spot)
            }
            assertTrue(done(), "never caught anything: ${output()}")
        }

        fun stairs(symbol: String, at: CoordGrid) = loc(symbol, at)

        fun talk(type: String, at: CoordGrid = player.coords.translateX(1)) {
            val npc = Npc(type, at)
            npcRepo.add(npc, Int.MAX_VALUE)
            dispatch { assertTrue(events.publish(this, NpcEvents.Op1(npc))) }
        }

        fun npcOp(type: String, op: InteractionOp = InteractionOp.Op1) {
            val npc = Npc(type, player.coords.translateX(1))
            npcRepo.add(npc, Int.MAX_VALUE)
            val event = if (op == InteractionOp.Op3) NpcEvents.Op3(npc) else NpcEvents.Op1(npc)
            dispatch { assertTrue(events.publish(this, event)) }
        }

        fun loc(symbol: String, coords: CoordGrid, wall: Boolean = false) {
            val loc = bound(symbol, coords, wall)
            if (wall) locRepo.add(coords, symbol, Int.MAX_VALUE, LocAngle.East, LocShape.WallStraight)
            val event = LocInteractions(BoundValidator(collision), events).opTrigger(player, loc, InteractionOp.Op1)
            val trigger = checkNotNull(event) { "No handler for $symbol" }
            dispatch { assertTrue(events.publish(this, trigger)) }
        }

        fun locU(symbol: String, coords: CoordGrid, obj: String) {
            val loc = bound(symbol, coords, wall = false)
            val objType = checkNotNull(ServerCacheManager.getItem(obj.asRSCM()))
            val slot = player.inv.indexOfFirst { it?.id == obj.asRSCM() }
            if (slot < 0) return
            val locType = checkNotNull(ServerCacheManager.getObject(symbol.asRSCM()))
            val event = with(locU) { access().opTrigger(loc, loc, locType, objType, slot) }
            val trigger = checkNotNull(event) { "No $obj handler for $symbol" }
            dispatch { assertTrue(events.publish(this, trigger)) }
        }

        /** What the account save keeps: the permanent varps and the persistent quest-stage attribute. */
        @OptIn(InternalApi::class)
        fun saveAndReload(): Player {
            val loaded = newPlayer()
            for ((varp, value) in player.vars.backing) {
                val scope = ServerCacheManager.getVarp(varp)?.scope
                if (scope != VarpLifetime.Temp) loaded.vars.backing[varp] = value
            }
            player.attr[QUEST_STAGE_MAP_ATTR]?.let { loaded.attr[QUEST_STAGE_MAP_ATTR] = it.toMutableMap() }
            for (slot in 0 until player.inv.size) loaded.inv[slot] = player.inv[slot]
            loaded.statMap.setBaseLevel("stat.fishing", player.statMap.getBaseLevel("stat.fishing"))
            loaded.statMap.setCurrentLevel("stat.fishing", player.statMap.getCurrentLevel("stat.fishing"))
            fc.quest.syncState(loaded)
            return loaded
        }

        fun adopt(loaded: Player) {
            player = loaded
        }

        private fun bound(symbol: String, coords: CoordGrid, wall: Boolean): BoundLocInfo {
            val type = checkNotNull(ServerCacheManager.getObject(symbol.asRSCM()))
            val entity = if (wall) LocEntity(type.id, 0, 2) else LocEntity(type.id, 10, 1)
            return BoundLocInfo(LocInfo(coords.level, coords, entity), type)
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
        val TOWN = CoordGrid(2640, 3440, 0)
        val OUTSIDE_GATE = CoordGrid(HemensterGate.OUTSIDE_X, 3441, 0)

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
