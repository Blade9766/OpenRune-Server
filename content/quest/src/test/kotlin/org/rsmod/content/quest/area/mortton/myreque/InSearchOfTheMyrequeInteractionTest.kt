package org.rsmod.content.quest.area.mortton.myreque

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.InventoryServerType
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.startCoroutine
import kotlin.random.Random
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.annotations.InternalApi
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.death.NpcDeath
import org.rsmod.api.inv.storage.PlayerItemStorage
import org.rsmod.api.invtx.InvTransactionsScript
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.player.dialogue.align.TextAlignment
import org.rsmod.api.player.events.interact.NpcEvents
import org.rsmod.api.player.hit.modifier.NoopPlayerHitModifier
import org.rsmod.api.player.hook.PlayerTeleportValidator
import org.rsmod.api.player.input.ResumePauseButtonInput
import org.rsmod.api.player.interact.LocInteractions
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
import org.rsmod.content.quest.area.burghderott.inaid.InAidHollows
import org.rsmod.content.quest.area.burghderott.inaid.InAidOfTheMyrequeQuest
import org.rsmod.content.quest.area.mortton.myreque.InSearchOfTheMyrequeQuest.Companion.BIG_BONES
import org.rsmod.content.quest.area.mortton.myreque.InSearchOfTheMyrequeQuest.Companion.COINS
import org.rsmod.content.quest.area.mortton.myreque.InSearchOfTheMyrequeQuest.Companion.CURPILE
import org.rsmod.content.quest.area.mortton.myreque.InSearchOfTheMyrequeQuest.Companion.CYREG
import org.rsmod.content.quest.area.mortton.myreque.InSearchOfTheMyrequeQuest.Companion.HAMMER
import org.rsmod.content.quest.area.mortton.myreque.InSearchOfTheMyrequeQuest.Companion.HELLHOUND
import org.rsmod.content.quest.area.mortton.myreque.InSearchOfTheMyrequeQuest.Companion.NAILS
import org.rsmod.content.quest.area.mortton.myreque.InSearchOfTheMyrequeQuest.Companion.PLANK
import org.rsmod.content.quest.area.mortton.myreque.InSearchOfTheMyrequeQuest.Companion.POUCH
import org.rsmod.content.quest.area.mortton.myreque.InSearchOfTheMyrequeQuest.Companion.STAGE_BETRAYED
import org.rsmod.content.quest.area.mortton.myreque.InSearchOfTheMyrequeQuest.Companion.STAGE_BOATMAN_CONVINCED
import org.rsmod.content.quest.area.mortton.myreque.InSearchOfTheMyrequeQuest.Companion.STAGE_BRIDGE_REPAIRED
import org.rsmod.content.quest.area.mortton.myreque.InSearchOfTheMyrequeQuest.Companion.STAGE_COMPLETE
import org.rsmod.content.quest.area.mortton.myreque.InSearchOfTheMyrequeQuest.Companion.STAGE_GUARD_PASSED
import org.rsmod.content.quest.area.mortton.myreque.InSearchOfTheMyrequeQuest.Companion.STAGE_HOUND_SLAIN
import org.rsmod.content.quest.area.mortton.myreque.InSearchOfTheMyrequeQuest.Companion.STAGE_MET_MEMBERS
import org.rsmod.content.quest.area.mortton.myreque.InSearchOfTheMyrequeQuest.Companion.STAGE_MET_VELIAF
import org.rsmod.content.quest.area.mortton.myreque.InSearchOfTheMyrequeQuest.Companion.STAGE_REACHED_HOLLOWS
import org.rsmod.content.quest.area.mortton.myreque.InSearchOfTheMyrequeQuest.Companion.STAGE_ROUTE_REVEALED
import org.rsmod.content.quest.area.mortton.myreque.InSearchOfTheMyrequeQuest.Companion.STAGE_SHORTCUT_OPENED
import org.rsmod.content.quest.area.mortton.myreque.InSearchOfTheMyrequeQuest.Companion.STAGE_STARTED
import org.rsmod.content.quest.area.mortton.myreque.InSearchOfTheMyrequeQuest.Companion.STAGE_WEAPONS_DELIVERED
import org.rsmod.content.quest.area.mortton.myreque.InSearchOfTheMyrequeQuest.Companion.STRANGER_NPC
import org.rsmod.content.quest.area.mortton.myreque.InSearchOfTheMyrequeQuest.Companion.UNCUT_RUBY
import org.rsmod.content.quest.area.mortton.myreque.InSearchOfTheMyrequeQuest.Companion.VANSTROM_SITTING
import org.rsmod.content.quest.area.mortton.myreque.InSearchOfTheMyrequeQuest.Companion.VELIAF
import org.rsmod.content.quest.area.mortton.myreque.InSearchOfTheMyrequeQuest.Companion.WEAPONS
import org.rsmod.content.quest.area.mortton.myreque.InSearchOfTheMyrequeQuest.Member
import org.rsmod.content.quest.area.mortton.myreque.npcs.CanifisTavern
import org.rsmod.content.quest.area.mortton.myreque.npcs.CurpileFyod
import org.rsmod.content.quest.area.mortton.myreque.npcs.CyregPaddlehorn
import org.rsmod.content.quest.area.mortton.myreque.npcs.MyrequeMembers
import org.rsmod.content.quest.manager.QUEST_STAGE_MAP_ATTR
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
 * Drives the quest's real scripts through the event bus, from the tavern to the stranger: every
 * supply refusal, the guard's wrong answers and retries, the bridge keeping its repairs, all
 * five introductions, an interrupted betrayal, the hound's defeat and retries, the shortcut, a
 * save and reload part way, and that no reward, loot or cutscene can be had twice.
 */
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class InSearchOfTheMyrequeInteractionTest {

    @Test fun `the full quest runs from the tavern to the stranger and rewards once`() {
        val f = Fixture()
        f.startQuest()
        assertEquals(STAGE_STARTED, f.stage())

        f.kit()
        f.convinceCyregAndDepart()
        assertEquals(STAGE_REACHED_HOLLOWS, f.stage())
        assertEquals(MyrequeCoords.HOLLOWS_LANDING, f.player.coords)
        assertEquals(3, f.count(PLANK))
        assertEquals(90, f.count(COINS))
        assertEquals(9, f.count(POUCH), "the ghast at the boat costs one charge")

        f.repairBridge()
        assertEquals(STAGE_BRIDGE_REPAIRED, f.stage())
        assertEquals(0, f.count(PLANK))
        assertEquals(0, f.count(NAILS))
        assertEquals(1, f.count(HAMMER))
        assertEquals(7, f.player.vars["varbit.route_bridgecomplete"])

        f.locOp("loc.route_treebase_2ops", MyrequeCoords.SOUTH_TREE, at = MyrequeCoords.BRIDGE_SOUTH_END)
        assertEquals(MyrequeCoords.BRIDGE_NORTH_END, f.player.coords)

        f.passGuard()
        assertEquals(STAGE_GUARD_PASSED, f.stage())
        f.locOp("loc.freedomfighterentrancel", CoordGrid(3510, 3447, 0))
        assertEquals(MyrequeCoords.TUNNEL_DOORS_INSIDE, f.player.coords)

        f.squeezeIn()
        f.locOp(MyrequeTunnels.CAVE_ENTRANCE, MyrequeCoords.POCKET_CAVE, at = MyrequeCoords.POCKET_INSIDE)
        assertEquals(MyrequeCoords.HIDEOUT_ARRIVAL, f.player.coords)

        f.npcOp(VELIAF)
        assertEquals(STAGE_MET_VELIAF, f.stage())
        f.meetEveryone()
        assertEquals(STAGE_MET_MEMBERS, f.stage())

        f.npcOp(VELIAF)
        assertEquals(STAGE_BETRAYED, f.stage())
        for ((weapon, _) in WEAPONS) assertEquals(0, f.count(weapon), weapon)
        assertEquals(1, f.player.vars["varbit.thsfm_vanstrom_hide"])
        assertTrue(f.said("Sani Piliu and Harold Evans fall"))
        assertTrue(f.said("north-east bed"), "the hint is not cut off by the hound")
        assertEquals(1, f.liveHounds())

        f.killHound()
        assertEquals(STAGE_HOUND_SLAIN, f.stage())
        assertEquals(2, f.ground(UNCUT_RUBY))
        assertEquals(4, f.ground(BIG_BONES))

        f.npcOp(VELIAF)
        assertEquals(STAGE_ROUTE_REVEALED, f.stage())

        f.walkOutToCellar()
        assertEquals(STAGE_SHORTCUT_OPENED, f.stage())
        assertTrue(f.said("unlocked a shortcut"))
        f.locOp(MyrequeTunnels.BASEMENT_LADDER, MyrequeCoords.BASEMENT_LADDER, at = MyrequeCoords.BASEMENT_FOOT)
        assertEquals(MyrequeCoords.CANIFIS_EXIT, f.player.coords)

        assertFalse(f.myq.isComplete(f.player))
        f.npcOp(STRANGER_NPC, CoordGrid(3503, 3477, 0))
        assertEquals(STAGE_COMPLETE, f.stage())
        assertTrue(f.player.ui.containsModal("interface.questscroll"))
        assertEquals(2, f.player.vars["varp.qp"])
        for (stat in REWARD_STATS) assertEquals(600, f.player.statMap.getXP(stat) - f.startXp.getValue(stat), stat)
        f.access().ifCloseSub("interface.questscroll")

        f.npcOp(STRANGER_NPC, CoordGrid(3503, 3477, 0))
        f.npcOp(VELIAF)
        assertEquals(2, f.player.vars["varp.qp"])
        for (stat in REWARD_STATS) assertEquals(600, f.player.statMap.getXP(stat) - f.startXp.getValue(stat), stat)
        assertFalse(f.player.ui.containsModal("interface.questscroll"))
    }

    @Test fun `the boss loot and completion rewards are kept apart and granted once`() {
        val f = Fixture(STAGE_BETRAYED)
        f.player.coords = MyrequeCoords.HIDEOUT_ARRIVAL
        f.npcOp(VELIAF)
        val hound = checkNotNull(f.betrayal.houndOf(f.player))
        val owner = f.betrayal.ownerOf(hound)
        val at = hound.coords
        f.npcRepo.del(hound, Int.MAX_VALUE)
        f.betrayal.rewardKill(owner, f.player, at)
        f.betrayal.rewardKill(owner, f.player, at)
        assertEquals(2, f.ground(UNCUT_RUBY, at))
        assertEquals(4, f.ground(BIG_BONES, at))
        assertEquals(STAGE_HOUND_SLAIN, f.stage())
        assertEquals(0, f.player.vars["varp.qp"])
        for (stat in REWARD_STATS) assertEquals(0, f.player.statMap.getXP(stat) - f.startXp.getValue(stat))
    }

    @Test fun `vanstrom refuses a player without the requirements`() {
        val f = Fixture()
        f.player.statMap.setBaseLevel("stat.agility", 24)
        f.choose(2)
        f.npcOp(VANSTROM_SITTING, CoordGrid(3503, 3477, 0))
        assertTrue(f.said("level 25 Agility"))
        assertEquals(0, f.stage())
    }

    @Test fun `cyreg names every missing supply and takes nothing until all are carried`() {
        val f = Fixture(STAGE_STARTED)
        f.player.coords = MyrequeCoords.MORTTON_LANDING
        f.choose(2, 1, 5, 1)
        f.npcOp(CYREG)
        assertEquals(STAGE_BOATMAN_CONVINCED, f.stage())
        assertEquals(MyrequeCoords.MORTTON_LANDING, f.player.coords)
        for (missing in listOf("Steel longsword", "druid pouch", "6 planks", "225 steel nails", "No hammer", "10 coins")) {
            assertTrue(f.said(missing), missing)
        }

        f.kit()
        f.player.inv[f.slotOf("obj.steel_sword")] = null
        f.player.worn[3] = InvObj("obj.steel_sword", 1)
        f.choose(1)
        f.npcOp(CYREG)
        assertTrue(f.said("1 x Steel sword"))
        assertTrue(f.said("not in your hands"))
        assertEquals(6, f.count(PLANK))
        assertEquals(100, f.count(COINS))
        assertEquals(STAGE_BOATMAN_CONVINCED, f.stage())

        f.give("obj.steel_sword")
        f.player.inv[f.slotOf(POUCH)] = InvObj(POUCH, 4)
        f.choose(1)
        f.npcOp(CYREG)
        assertTrue(f.said("at least 5 charges"))
        assertEquals(6, f.count(PLANK))
        f.player.inv[f.slotOf(POUCH)] = InvObj(POUCH, 5)
        f.choose(1)
        f.npcOp(CYREG)
        assertEquals(STAGE_REACHED_HOLLOWS, f.stage())
        assertEquals(3, f.count(PLANK))
        assertEquals(90, f.count(COINS))
    }

    @Test fun `a bridge already mended on foot only leaves cyreg's own planks to bring`() {
        val f = Fixture(STAGE_BOATMAN_CONVINCED)
        f.mendBridge()
        f.player.coords = MyrequeCoords.MORTTON_LANDING
        for ((weapon, n) in WEAPONS) f.give(weapon, n)
        f.give(PLANK, 3)
        f.give(COINS, 10)
        f.give(POUCH, 5)
        f.choose(1)
        f.npcOp(CYREG)
        assertEquals(STAGE_REACHED_HOLLOWS, f.stage())
        assertEquals(0, f.count(PLANK))
        assertEquals(0, f.count(COINS))
    }

    @Test fun `later crossings are free and need no pouch`() {
        val f = Fixture(STAGE_REACHED_HOLLOWS)
        f.player.coords = MyrequeCoords.HOLLOWS_LANDING
        f.give("obj.shark")
        f.locOp("loc.route_rowboat_hollows", CoordGrid(3498, 3377, 0))
        assertEquals(MyrequeCoords.MORTTON_LANDING, f.player.coords)
        assertEquals(1, f.count("obj.rotten_food"), "without a pouch the ghast spoils food")
        f.locOp("loc.route_rowboat_mortton", CoordGrid(3523, 3284, 0))
        assertEquals(MyrequeCoords.HOLLOWS_LANDING, f.player.coords)
        assertTrue(f.said("charged druid pouch would have kept"))
    }

    @Test fun `bridge repairs are kept section by section when supplies run out`() {
        val f = Fixture(STAGE_REACHED_HOLLOWS)
        f.player.coords = MyrequeCoords.BRIDGE_SOUTH_END
        f.give(PLANK)
        f.give(NAILS, 150)
        f.locOp("loc.route_treebase_1op", MyrequeCoords.SOUTH_TREE, op = InteractionOp.Op2)
        assertTrue(f.said("You'll need a hammer"))
        assertEquals(0, f.myq.sectionsRepaired(f.player))

        f.give(HAMMER)
        f.locOp("loc.route_treebase_1op", MyrequeCoords.SOUTH_TREE, op = InteractionOp.Op2)
        assertEquals(1, f.myq.sectionsRepaired(f.player))
        assertEquals(75, f.count(NAILS))
        assertTrue(f.said("You need a plank and 75 steel nails"))
        assertEquals(STAGE_REACHED_HOLLOWS, f.stage())

        val saved = f.saveAndReload()
        assertEquals(1, f.myq.sectionsRepaired(saved))

        f.give(PLANK)
        f.locOp("loc.route_treebase_1op", MyrequeCoords.SOUTH_TREE, op = InteractionOp.Op2)
        assertEquals(2, f.myq.sectionsRepaired(f.player))
        assertEquals(0, f.count(NAILS))
        f.give(PLANK)
        f.give(NAILS, 75)
        f.locOp("loc.route_treebase_1op", MyrequeCoords.SOUTH_TREE, op = InteractionOp.Op2)
        assertTrue(f.myq.isBridgeRepaired(f.player))
        assertEquals(STAGE_BRIDGE_REPAIRED, f.stage())
        assertEquals(1, f.count(HAMMER))
    }

    @Test fun `the rope bridge itself can be crossed once mended`() {
        val f = Fixture(STAGE_REACHED_HOLLOWS)
        f.locAp("loc.swamp_bridge1", CoordGrid(3502, 3428, 0), at = MyrequeCoords.BRIDGE_SOUTH_END)
        assertTrue(f.said("Several planks are missing"))
        assertEquals(MyrequeCoords.BRIDGE_SOUTH_END, f.player.coords)
        f.mendBridge()
        f.locAp("loc.swamp_bridge1", CoordGrid(3502, 3428, 0), at = MyrequeCoords.BRIDGE_SOUTH_END)
        assertEquals(MyrequeCoords.BRIDGE_NORTH_END, f.player.coords)
        f.locAp("loc.swamp_bridge1", CoordGrid(3502, 3429, 0))
        assertEquals(MyrequeCoords.BRIDGE_SOUTH_END, f.player.coords)
    }

    @Test fun `a wrong answer knocks the player back to the boat and the guard can be tried again`() {
        val f = Fixture(STAGE_BRIDGE_REPAIRED)
        f.mendBridge()
        f.kit()
        f.player.coords = MyrequeCoords.BRIDGE_NORTH_END
        val before = f.player.inv.objs.toList()
        f.answer(wrong = true)
        f.npcOp(CURPILE)
        assertEquals(MyrequeCoords.HOLLOWS_LANDING, f.player.coords)
        assertTrue(f.said("Spy!"))
        assertTrue(f.said("Your belongings are untouched"))
        assertEquals(STAGE_BRIDGE_REPAIRED, f.stage())
        assertTrue(f.myq.isBridgeRepaired(f.player))
        assertEquals(before, f.player.inv.objs.toList())

        f.locOp("loc.freedomfighterentrancel", CoordGrid(3510, 3447, 0))
        assertTrue(f.said("barred from below"))
        assertEquals(MyrequeCoords.HOLLOWS_LANDING, f.player.coords)

        f.player.coords = MyrequeCoords.BRIDGE_NORTH_END
        f.passGuard()
        assertEquals(STAGE_GUARD_PASSED, f.stage())
        f.npcOp(CURPILE)
        assertTrue(f.said("Go on through"))
    }

    @Test fun `the stalagmites need 25 agility`() {
        val f = Fixture(STAGE_GUARD_PASSED)
        f.player.coords = MyrequeCoords.POCKET_OUTSIDE
        f.player.statMap.setCurrentLevel("stat.agility", 24)
        f.locOp(MyrequeTunnels.STALAGMITE, MyrequeCoords.STALAGMITE, op = InteractionOp.Op2, at = MyrequeCoords.POCKET_OUTSIDE)
        assertTrue(f.said("Agility level of 25"))
        assertEquals(MyrequeCoords.POCKET_OUTSIDE, f.player.coords)
        f.player.statMap.setCurrentLevel("stat.agility", 25)
        f.squeezeIn()
        f.locOp(MyrequeTunnels.STALAGMITE, MyrequeCoords.STALAGMITE, op = InteractionOp.Op2, at = MyrequeCoords.POCKET_INSIDE)
        assertEquals(MyrequeCoords.POCKET_OUTSIDE, f.player.coords)
    }

    @Test fun `every member must be met before veliaf takes the weapons, and only all of them`() {
        val f = Fixture(STAGE_GUARD_PASSED)
        f.kit()
        f.player.coords = MyrequeCoords.HIDEOUT_ARRIVAL
        f.npcOp(Member.Sani.npc)
        assertTrue(f.said("Talk to Veliaf first"))
        assertFalse(f.myq.hasMet(f.player, Member.Sani))

        f.npcOp(VELIAF)
        for (member in Member.entries.dropLast(1)) {
            f.npcOp(member.npc)
            assertTrue(f.myq.hasMet(f.player, member), member.name)
            assertEquals(STAGE_MET_VELIAF, f.stage())
        }
        f.npcOp(VELIAF)
        assertTrue(f.said("Radigad Ponfit"))
        assertEquals(STAGE_MET_VELIAF, f.stage())
        f.npcOp(Member.Radigad.npc)
        assertEquals(STAGE_MET_MEMBERS, f.stage())
        assertTrue(f.said("You have now met all of the Myreque"))

        f.player.inv[f.slotOf("obj.steel_dagger")] = null
        f.npcOp(VELIAF)
        assertTrue(f.said("1 x Steel dagger"))
        assertEquals(STAGE_MET_MEMBERS, f.stage())
        assertEquals(2, f.count("obj.steel_sword"))
        assertEquals(0, f.liveHounds())
    }

    @Test fun `a betrayal cut short replays and only ever leaves one hound`() {
        val f = Fixture(STAGE_WEAPONS_DELIVERED)
        f.player.coords = MyrequeCoords.HIDEOUT_ARRIVAL
        f.dispatchUntilCancelled(f.npcEvent(VELIAF)) { f.said("Where is that mist coming from") }
        assertEquals(STAGE_WEAPONS_DELIVERED, f.stage())
        assertEquals(0, f.liveActors(), "the scene's actors are removed when it is cut short")
        assertEquals(0, f.liveHounds())
        assertFalse(f.betrayal.isInScene(f.player))

        f.locOp(MyrequeTunnels.CAVE_ENTRANCE, MyrequeCoords.POCKET_CAVE, at = MyrequeCoords.POCKET_INSIDE)
        assertEquals(STAGE_BETRAYED, f.stage())
        assertEquals(0, f.liveActors())
        assertEquals(1, f.liveHounds())
        val hound = f.betrayal.houndOf(f.player)

        f.npcOp(VELIAF)
        f.locOp(MyrequeTunnels.CAVE_ENTRANCE, MyrequeCoords.POCKET_CAVE, at = MyrequeCoords.POCKET_INSIDE)
        assertEquals(1, f.liveHounds())
        assertSame(hound, f.betrayal.houndOf(f.player))
        assertEquals(1, f.output().split("Sani Piliu and Harold Evans fall").size - 1, "the deaths play once")
    }

    @Test fun `losing or fleeing the fight brings a fresh hound and nobody else may attack it`() {
        val f = Fixture(STAGE_BETRAYED)
        f.player.coords = MyrequeCoords.HIDEOUT_ARRIVAL
        f.npcOp(VELIAF)
        val first = checkNotNull(f.betrayal.houndOf(f.player))
        val stranger = Player()
        assertFalse(HoundAttackHook(f.betrayal).validate(stranger, first) is org.rsmod.api.death.NpcAttackValidateResult.Pass)
        assertTrue(HoundAttackHook(f.betrayal).validate(f.player, first) is org.rsmod.api.death.NpcAttackValidateResult.Pass)

        f.locOp(MyrequeTunnels.CAVE_ENTRANCE, MyrequeCoords.HIDEOUT_CAVE, at = MyrequeCoords.HIDEOUT_ARRIVAL)
        assertEquals(MyrequeCoords.POCKET_INSIDE, f.player.coords)
        assertFalse(first.isSlotAssigned)
        assertEquals(0, f.liveHounds())

        f.player.coords = CoordGrid(3222, 3218, 0)
        f.locOp(MyrequeTunnels.CAVE_ENTRANCE, MyrequeCoords.POCKET_CAVE, at = MyrequeCoords.POCKET_INSIDE)
        val second = checkNotNull(f.betrayal.houndOf(f.player))
        assertNotSame(first, second)
        assertEquals(1, f.liveHounds())

        f.npcRepo.del(second, Int.MAX_VALUE)
        f.npcOp(VELIAF)
        assertEquals(1, f.liveHounds())
        assertEquals(STAGE_BETRAYED, f.stage())
    }

    @Test fun `the shortcut stays shut until veliaf reveals it and then works both ways`() {
        val f = Fixture(STAGE_HOUND_SLAIN)
        f.player.coords = MyrequeCoords.CANIFIS_EXIT
        f.locOp(MyrequeTunnels.CANIFIS_TRAPDOOR, MyrequeCoords.CANIFIS_TRAPDOOR)
        assertTrue(f.said("bolted from below"))
        f.player.coords = MyrequeCoords.FALSE_WALL_SOUTH
        f.locOp(MyrequeTunnels.FALSE_WALL, MyrequeCoords.FALSE_WALL, at = MyrequeCoords.FALSE_WALL_SOUTH)
        assertTrue(f.said("find nothing unusual"))
        assertEquals(MyrequeCoords.FALSE_WALL_SOUTH, f.player.coords)

        f.player.coords = MyrequeCoords.HIDEOUT_ARRIVAL
        f.npcOp(VELIAF)
        assertEquals(STAGE_ROUTE_REVEALED, f.stage())
        f.walkOutToCellar()
        assertEquals(MyrequeCoords.FALSE_WALL_NORTH, f.player.coords)
        assertTrue(f.myq.isShortcutUnlocked(f.player))

        f.locOp(MyrequeTunnels.BASEMENT_LADDER, MyrequeCoords.BASEMENT_LADDER, at = MyrequeCoords.BASEMENT_FOOT)
        f.locOp(MyrequeTunnels.CANIFIS_TRAPDOOR, MyrequeCoords.CANIFIS_TRAPDOOR, at = MyrequeCoords.CANIFIS_EXIT)
        assertEquals(MyrequeCoords.BASEMENT_FOOT, f.player.coords)
        f.player.coords = MyrequeCoords.FALSE_WALL_NORTH
        f.locOp(MyrequeTunnels.FALSE_WALL, MyrequeCoords.FALSE_WALL, at = MyrequeCoords.FALSE_WALL_NORTH)
        assertEquals(MyrequeCoords.FALSE_WALL_SOUTH, f.player.coords)
        assertEquals(1, f.output().split("unlocked a shortcut").size - 1, "the unlock is announced once")
    }

    @Test fun `the stranger only finishes the quest once the shortcut is found`() {
        val f = Fixture(STAGE_ROUTE_REVEALED)
        f.npcOp(STRANGER_NPC, CoordGrid(3503, 3477, 0))
        assertTrue(f.said("Never heard of him"))
        assertEquals(STAGE_ROUTE_REVEALED, f.stage())
        assertEquals(0, f.player.vars["varp.qp"])
    }

    @Test fun `a save part way through restores every flag`() {
        val f = Fixture(STAGE_MET_VELIAF)
        f.mendBridge()
        f.myq.markMet(f.player, Member.Ivan)
        f.myq.markMet(f.player, Member.Sani)
        val loaded = f.saveAndReload()
        assertEquals(STAGE_MET_VELIAF, f.myq.stage(loaded))
        assertEquals(STAGE_MET_VELIAF, loaded.vars["varp.routequest"])
        assertTrue(f.myq.isBridgeRepaired(loaded))
        assertTrue(f.myq.hasMet(loaded, Member.Ivan))
        assertTrue(f.myq.hasMet(loaded, Member.Sani))
        assertFalse(f.myq.hasMet(loaded, Member.Harold))
        assertEquals(0, loaded.vars["varbit.thsfm_vanstrom_hide"])

        val later = Fixture(STAGE_SHORTCUT_OPENED).saveAndReload()
        assertEquals(1, later.vars["varbit.thsfm_vanstrom_hide"])
    }

    @Test fun `resetting the quest clears the bridge and introductions`() {
        val f = Fixture(STAGE_MET_MEMBERS)
        f.mendBridge()
        for (member in Member.entries) f.myq.markMet(f.player, member)
        f.myq.quest.resetQuest(f.player)
        assertEquals(0, f.stage())
        assertEquals(0, f.player.vars["varp.routequestmulti"])
    }

    @Test fun `the journal and hints never name vanstrom's nature early`() {
        val f = Fixture(STAGE_STARTED)
        for (stage in listOf(STAGE_STARTED, STAGE_BOATMAN_CONVINCED, STAGE_REACHED_HOLLOWS, STAGE_BRIDGE_REPAIRED,
            STAGE_GUARD_PASSED, STAGE_MET_VELIAF, STAGE_MET_MEMBERS, STAGE_WEAPONS_DELIVERED)) {
            f.myq.quest.jumpToStage(f.player, stage)
            val log = f.myq.questLog(f.access()).lowercase() + f.myq.hint(f.player).lowercase()
            assertFalse(log.contains("vampyre lord") || log.contains("vanstrom is") || log.contains("betray"), "stage $stage: $log")
        }
        f.myq.quest.jumpToStage(f.player, STAGE_BETRAYED)
        assertTrue(f.myq.questLog(f.access()).contains("He was a vampyre all along"))
        f.myq.quest.jumpToStage(f.player, STAGE_REACHED_HOLLOWS)
        assertTrue(f.myq.hint(f.player).contains("Climb the tree"))
        assertFalse(f.myq.hint(f.player).contains("Curpile"))
    }

    private class Fixture(stage: Int = 0) {
        val events = EventBus()
        private val client = RecordingClient()
        private val collision = CollisionFlagMap()
        private val npcs = NpcList()
        private val players = PlayerList()
        private val coroutine = GameCoroutine("myreque-test")
        private var result: Result<Unit>? = null
        private lateinit var regions: RegionRegistry
        private val context = ProtectedAccessContextFactory.empty().copy(
            getEventBus = { events }, getAlignment = { TextAlignment() },
            getCollision = { collision }, getNpcList = { npcs },
            getTeleportValidator = { PlayerTeleportValidator(emptySet()) },
            getAreaChecker = { AreaChecker(regions, AreaIndex()) },
            getNpcInteractions = { NpcInteractions(events) },
            getRandom = { random },
            getHitModifier = { NoopPlayerHitModifier },
        )
        private val random = DefaultGameRandom(Random(SEED))
        private val clock = MapClock(100)
        private val objRegistry = ObjRegistry(ZoneUpdateMap())
        val npcRepo: NpcRepository
        private val objRepo = ObjRepository(clock, objRegistry)
        private val locRepo: LocRepository
        private val picks = ArrayDeque<(String) -> Int>()

        @OptIn(InternalApi::class)
        val player = Player().apply {
            this.client = this@Fixture.client
            uuid = 4242L
            observerUUID = 4242L
            slotId = 1
            assignUid()
            coords = CoordGrid(3502, 3478, 0)
            currentMapClock = 100
            processedMapClock = 100
            pendingSequence = EntitySeq.NULL
            pendingFaceAngle = EntityFaceAngle.NULL
            inv = Inventory(InventoryServerType(size = 28, flags = 0), arrayOfNulls(28))
            worn = Inventory(InventoryServerType(size = 14, flags = 0), arrayOfNulls(14))
            statMap.setBaseLevel("stat.agility", 25)
            statMap.setCurrentLevel("stat.agility", 25)
            statMap.setBaseLevel("stat.hitpoints", 40)
            statMap.setCurrentLevel("stat.hitpoints", 40)
        }

        val startXp = REWARD_STATS.associateWith { player.statMap.getXP(it) }

        val myq = InSearchOfTheMyrequeQuest()
        val betrayal: Betrayal

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
            betrayal = Betrayal(myq, npcRepo, objRepo, NpcDeath(npcRepo, players, objRepo, emptySet(), emptySet()),
                players, WorldQueueList(), AiPlayerInteractions(events, players))
            for ((x0, z0, x1, z1) in AREAS) {
                for (x in x0..x1 step 8) for (z in z0..z1 step 8) collision.allocateIfAbsent(x, z, 0)
            }
            players[player.slotId] = player
            val scripts = ScriptContext(events, CheatCommandMap(), EngineQueueCache())
            with(myq) { scripts.startup() }
            with(betrayal) { scripts.startup() }
            with(CanifisTavern(myq)) { scripts.startup() }
            with(CyregPaddlehorn(myq, SwampBoat())) { scripts.startup() }
            with(HollowsBridge(myq)) { scripts.startup() }
            with(CurpileFyod(myq)) { scripts.startup() }
            with(MyrequeTunnels(myq, locRepo, betrayal)) { scripts.startup() }
            with(MyrequeMembers(myq, betrayal, InAidHollows(InAidOfTheMyrequeQuest(), unused(), unused()))) { scripts.startup() }
            if (stage > 0) myq.quest.jumpToStage(player, stage)
        }

        fun access() = ProtectedAccess(player, coroutine, context)

        fun stage(): Int = myq.stage(player)

        fun choose(vararg options: Int) {
            for (option in options) picks += { _ -> option }
        }

        /** Curpile's quiz: "Ask away", then each question answered from what the menu asks. */
        fun answer(wrong: Boolean = false) {
            picks += { _ -> 1 }
            repeat(CurpileFyod.QUESTIONS_ASKED) {
                picks += { out ->
                    val question = CurpileFyod.QUESTIONS.maxBy { out.lastIndexOf(it.text) }
                    val right = question.options.indexOf(question.answer)
                    1 + if (wrong) (right + 1) % question.options.size else right
                }
            }
        }

        fun give(obj: String, count: Int = 1) {
            val perSlot = if (item(obj).stackable) count else 1
            repeat(count / perSlot) {
                val slot = player.inv.indexOfFirst { it == null }
                player.inv[slot] = InvObj(obj, perSlot)
            }
        }

        fun kit() {
            for ((weapon, n) in WEAPONS) give(weapon, n)
            give(PLANK, 6)
            give(NAILS, 225)
            give(HAMMER)
            give(COINS, 100)
            give(POUCH, 10)
        }

        fun count(obj: String): Int = player.inv.count(obj)

        fun slotOf(obj: String): Int = player.inv.indexOfFirst { it?.id == obj.asRSCM() }

        fun said(text: String): Boolean = output().contains(text)

        fun mendBridge() {
            for (section in 1..3) myq.repairSection(player, section)
        }

        fun ground(obj: String, at: CoordGrid = MyrequeCoords.HOUND_SPAWN): Int =
            objRegistry.findAll(at).count { it.type == obj.asRSCM() }

        fun liveHounds(): Int = npcs.count { it != null && it.isType(HELLHOUND) }

        fun liveActors(): Int =
            npcs.count { npc -> npc != null && ACTORS.any { npc.isType(it) } }

        fun startQuest() {
            choose(2, 1)
            npcOp(VANSTROM_SITTING, CoordGrid(3503, 3477, 0))
        }

        fun convinceCyregAndDepart() {
            player.coords = MyrequeCoords.MORTTON_LANDING
            choose(2, 1, 5, 1)
            npcOp(CYREG)
        }

        fun repairBridge() {
            player.coords = MyrequeCoords.BRIDGE_SOUTH_END
            locOp("loc.route_treebase_1op", MyrequeCoords.SOUTH_TREE, op = InteractionOp.Op2)
        }

        fun passGuard() {
            answer()
            npcOp(CURPILE)
        }

        fun squeezeIn() {
            player.coords = MyrequeCoords.POCKET_OUTSIDE
            locOp(MyrequeTunnels.STALAGMITE, MyrequeCoords.STALAGMITE, op = InteractionOp.Op2, at = MyrequeCoords.POCKET_OUTSIDE)
            assertEquals(MyrequeCoords.POCKET_INSIDE, player.coords, output())
        }

        fun meetEveryone() {
            for (member in Member.entries) npcOp(member.npc)
        }

        fun killHound() {
            val hound = checkNotNull(betrayal.houndOf(player)) { "no hound: ${output()}" }
            val owner = betrayal.ownerOf(hound)
            val at = hound.coords
            npcRepo.del(hound, Int.MAX_VALUE)
            assertNull(betrayal.ownerOf(hound))
            betrayal.rewardKill(owner, player, at)
        }

        fun walkOutToCellar() {
            locOp(MyrequeTunnels.CAVE_ENTRANCE, MyrequeCoords.HIDEOUT_CAVE, at = MyrequeCoords.HIDEOUT_ARRIVAL)
            locOp(MyrequeTunnels.STALAGMITE, MyrequeCoords.STALAGMITE, op = InteractionOp.Op2, at = MyrequeCoords.POCKET_INSIDE)
            locOp(MyrequeTunnels.FALSE_WALL, MyrequeCoords.FALSE_WALL, at = MyrequeCoords.FALSE_WALL_SOUTH)
        }

        /** What the account save keeps: the varps and the persistent quest-stage attribute. */
        fun saveAndReload(): Player {
            val loaded = Player()
            loaded.vars.backing.putAll(player.vars.backing)
            player.attr[QUEST_STAGE_MAP_ATTR]?.let { loaded.attr[QUEST_STAGE_MAP_ATTR] = it.toMutableMap() }
            myq.quest.syncState(loaded)
            return loaded
        }

        fun npcEvent(type: String, at: CoordGrid = player.coords.translateX(1)): NpcEvents.Op1 {
            val npc = Npc(type, at)
            npcRepo.add(npc, Int.MAX_VALUE)
            return NpcEvents.Op1(npc)
        }

        fun npcOp(type: String, at: CoordGrid = player.coords.translateX(1)) = dispatch(npcEvent(type, at))

        fun locOp(symbol: String, coords: CoordGrid, op: InteractionOp = InteractionOp.Op1, at: CoordGrid? = null) {
            if (at != null) player.coords = at
            val type = checkNotNull(ServerCacheManager.getObject(symbol.asRSCM()))
            val loc = BoundLocInfo(LocInfo(0, coords, LocEntity(type.id, 10, 0)), type)
            val event = LocInteractions(BoundValidator(collision), events).opTrigger(player, loc, op)
            dispatch(checkNotNull(event) { "No $op handler for $symbol" })
        }

        fun locAp(symbol: String, coords: CoordGrid, at: CoordGrid? = null) {
            if (at != null) player.coords = at
            val type = checkNotNull(ServerCacheManager.getObject(symbol.asRSCM()))
            val loc = BoundLocInfo(LocInfo(0, coords, LocEntity(type.id, 22, 0)), type)
            val event = LocInteractions(BoundValidator(collision), events).apTrigger(player, loc, InteractionOp.Op1)
            dispatch(checkNotNull(event) { "No ap handler for $symbol" })
        }

        fun dispatchUntilCancelled(event: SuspendEvent<ProtectedAccess>, until: () -> Boolean) {
            start(event)
            repeat(400) {
                if (until()) {
                    coroutine.cancel()
                    assertInstanceOf(CancellationException::class.java, result?.exceptionOrNull())
                    result = null
                    player.activeCoroutine = null
                    return
                }
                step()
            }
            fail<Unit>("Never reached the cut: ${output()}")
        }

        private fun item(obj: String) = checkNotNull(ServerCacheManager.getItem(obj.asRSCM()))

        private fun start(event: SuspendEvent<ProtectedAccess>) {
            player.clearPendingAction(events)
            result = null
            player.activeCoroutine = coroutine
            val block: suspend () -> Unit = { assertTrue(events.publish(access(), event)) }
            block.startCoroutine(object : Continuation<Unit> {
                override val context = EmptyCoroutineContext
                override fun resumeWith(result: Result<Unit>) { this@Fixture.result = result }
            })
            result?.getOrThrow()
        }

        private fun dispatch(event: SuspendEvent<ProtectedAccess>) {
            start(event)
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
                    "chatmenu" -> ResumePauseButtonInput("component.chatmenu:options", picks.removeFirstOrNull()?.invoke(output()) ?: 1)
                    "objectbox" -> ResumePauseButtonInput("component.objectbox:universe", -1)
                    "objectbox_double" -> ResumePauseButtonInput("component.objectbox_double:pausebutton", -1)
                    else -> ResumePauseButtonInput("component.$parent:continue", -1)
                }
                coroutine.resumeWith(input)
            } else {
                tick()
            }
        }

        private fun tick() {
            player.currentMapClock++
            player.processedMapClock = player.currentMapClock
            player.pendingSequence = EntitySeq.NULL
            player.pendingFaceAngle = EntityFaceAngle.NULL
            coroutine.advance()
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
        const val SEED = 7L

        /** A stand-in for collaborators the In Search of the Myreque paths never reach. */
        inline fun <reified T> unused(): T {
            val field = sun.misc.Unsafe::class.java.getDeclaredField("theUnsafe").apply { isAccessible = true }
            return (field.get(null) as sun.misc.Unsafe).allocateInstance(T::class.java) as T
        }

        val REWARD_STATS = listOf("stat.attack", "stat.defence", "stat.strength", "stat.hitpoints", "stat.crafting")

        val ACTORS = listOf(Betrayal.SANI_ACTOR, Betrayal.HAROLD_ACTOR, Betrayal.MIST_ACTOR, Betrayal.VANSTROM_ACTOR)

        /** Map squares the quest walks through: Canifis, Mort'ton, the Hollows, the tunnels, Lumbridge. */
        val AREAS =
            listOf(
                listOf(3456, 3456, 3519, 3519),
                listOf(3512, 3264, 3535, 3299),
                listOf(3456, 3328, 3519, 3455),
                listOf(3456, 9792, 3519, 9855),
                listOf(3216, 3208, 3231, 3223),
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
