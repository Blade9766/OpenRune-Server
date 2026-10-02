@file:OptIn(InternalApi::class)

package org.rsmod.content.quest.area.falador.recruitmentdrive

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
import org.junit.jupiter.api.Assertions.assertNotEquals
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
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.player.dialogue.align.TextAlignment
import org.rsmod.api.player.events.interact.HeldUDefaultEvents
import org.rsmod.api.player.events.interact.NpcEvents
import org.rsmod.api.player.events.interact.WornObjEvents
import org.rsmod.api.player.hit.modifier.NoopPlayerHitModifier
import org.rsmod.api.player.hit.processor.DamageOnlyPlayerHitProcessor
import org.rsmod.api.player.hook.PlayerTeleportValidateHook
import org.rsmod.api.player.hook.PlayerTeleportValidator
import org.rsmod.api.player.hook.RestrictedAction
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.input.ResumePCountDialogInput
import org.rsmod.api.player.input.ResumePauseButtonInput
import org.rsmod.api.player.interact.LocInteractions
import org.rsmod.api.player.interact.LocUInteractions
import org.rsmod.api.player.interact.NpcInteractions
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.protect.clearPendingAction
import org.rsmod.api.random.DefaultGameRandom
import org.rsmod.api.registry.npc.NpcRegistry
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.route.BoundValidator
import org.rsmod.api.shops.Shops
import org.rsmod.content.quest.area.falador.blackknightsfortress.BlackKnightsFortressQuest
import org.rsmod.content.quest.area.falador.blackknightsfortress.npcs.SirAmikVarze
import org.rsmod.content.quest.area.falador.recruitmentdrive.npcs.SirAmikRecruitment
import org.rsmod.content.quest.area.falador.recruitmentdrive.npcs.SirTiffyCashien
import org.rsmod.content.quest.area.falador.recruitmentdrive.rooms.AcrosticRoom
import org.rsmod.content.quest.area.falador.recruitmentdrive.rooms.Alchemy
import org.rsmod.content.quest.area.falador.recruitmentdrive.rooms.Bank
import org.rsmod.content.quest.area.falador.recruitmentdrive.rooms.Cargo
import org.rsmod.content.quest.area.falador.recruitmentdrive.rooms.CombatRoom
import org.rsmod.content.quest.area.falador.recruitmentdrive.rooms.CrossingRoom
import org.rsmod.content.quest.area.falador.recruitmentdrive.rooms.ImprovisationRoom
import org.rsmod.content.quest.area.falador.recruitmentdrive.rooms.LogicRoom
import org.rsmod.content.quest.area.falador.recruitmentdrive.rooms.PatienceRoom
import org.rsmod.content.quest.area.falador.recruitmentdrive.rooms.RiverCrossing
import org.rsmod.content.quest.area.falador.recruitmentdrive.rooms.SirLeyeAttackHook
import org.rsmod.content.quest.area.falador.recruitmentdrive.rooms.StatueRoom
import org.rsmod.content.quest.area.taverley.druidicritual.DruidicRitualQuest
import org.rsmod.content.quest.manager.QuestInstances
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
import org.rsmod.game.interact.InteractionObj
import org.rsmod.game.interact.InteractionOp
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.InvVirtualStorageHolder
import org.rsmod.game.inv.Inventory
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocEntity
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.obj.Obj
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.game.queue.WorldQueueList
import org.rsmod.game.seq.EntitySeq
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap
import sun.misc.Unsafe

/**
 * Drives Recruitment Drive's real scripts through the event bus, with the training grounds mapped
 * onto the world map instead of an instance: starting the quest, entering the grounds, every room's
 * solution and its representative wrong answers, failing, quitting, logging out and dying, stale
 * actions, two players at once, the rewards, the armour and the respawn point.
 */
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class RecruitmentDriveInteractionTest {

    /* Starting */

    @Test fun `Sir Amik only puts the player forward with both requirements, and acceptance is apart from any attempt`() {
        respectingProgress {
            val f = Fixture(stage = 0)
            f.bkf.quest.jumpToStage(f.player, BlackKnightsFortressQuest.STAGE_COMPLETE)
            f.talk(RecruitmentDriveQuest.SIR_AMIK)
            assertEquals(0, f.stage())
            assertTrue(f.said("You do not meet all of the requirements"))
            f.druidic.quest.jumpToStage(f.player, f.druidic.quest.maxSteps)
            f.choose(2)
            f.talk(RecruitmentDriveQuest.SIR_AMIK)
            assertEquals(0, f.stage(), "declining leaves it unstarted")
            f.choose(1)
            f.talk(RecruitmentDriveQuest.SIR_AMIK)
            assertEquals(RecruitmentDriveQuest.STAGE_STARTED, f.stage())
            assertNull(RecruitmentState.order(f.player), "accepting the quest rolls no attempt")
            assertTrue(f.journal().contains("Sir Tiffy Cashien"))
            f.talk(RecruitmentDriveQuest.SIR_AMIK)
            assertTrue(f.said("he will be expecting you"))
        }
    }

    @Test fun `where the policy already counts the quest done, it can still be started and played`() {
        val previous = QuestRequirements.activePolicy()
        QuestRequirements.install(QuestRequirementPolicy(QuestRequirementMode.AssumeCompleted))
        try {
            val f = Fixture(stage = 0)
            f.choose(1)
            f.talk(RecruitmentDriveQuest.SIR_AMIK)
            assertEquals(RecruitmentDriveQuest.STAGE_STARTED, f.stage())
            f.choose(3)
            f.talk(RecruitmentDriveQuest.SIR_TIFFY)
            assertTrue(f.grounds.inside(f.player))
        } finally {
            QuestRequirements.install(previous)
        }
    }

    @Test fun `Sir Tiffy refuses anyone carrying or wearing anything, and takes nothing from them`() {
        val f = Fixture()
        f.give("obj.bronze_dagger")
        f.choose(3)
        f.talk(RecruitmentDriveQuest.SIR_TIFFY)
        assertFalse(f.grounds.inside(f.player))
        assertEquals(1, f.count("obj.bronze_dagger"), "nothing confiscated")
        assertTrue(f.said("completely empty inventory"))
        f.drop("obj.bronze_dagger")
        f.player.worn[0] = InvObj("obj.bronze_full_helm", 1)
        f.choose(3)
        f.talk(RecruitmentDriveQuest.SIR_TIFFY)
        assertFalse(f.grounds.inside(f.player))
        assertNotNull(f.player.worn[0])
        f.player.worn[0] = null
        f.choose(3)
        f.talk(RecruitmentDriveQuest.SIR_TIFFY)
        assertTrue(f.grounds.inside(f.player))
        assertEquals(1, f.player.rdSpokeToTiffy)
        assertNotNull(RecruitmentState.order(f.player))
    }

    @Test fun `every attempt is five different rooms with the combat room, in any order`() {
        val orders = RoomOrder.all
        assertEquals(1800, orders.size)
        assertEquals(orders.size, orders.toSet().size)
        for (order in orders) {
            assertEquals(5, order.toSet().size)
            assertTrue(TestRoom.COMBAT in order)
            assertEquals(order, RoomOrder.decode(RoomOrder.encode(order)))
        }
        val random = DefaultGameRandom(Random(11))
        val rolled = (1..2000).map { RoomOrder.roll(random) }
        assertEquals(TestRoom.entries.toSet(), rolled.flatten().toSet(), "every room turns up")
        assertEquals((0 until 5).toSet(), rolled.map { it.indexOf(TestRoom.COMBAT) }.toSet(), "combat in any place")
        assertTrue(rolled.toSet().size > 1000)
    }

    @Test fun `the attempt's order and room stay put through the visit, and the first room greets the player`() {
        val f = Fixture()
        val order = listOf(TestRoom.ACROSTIC, TestRoom.COMBAT, TestRoom.STATUES, TestRoom.PATIENCE, TestRoom.LOGIC)
        f.enter(order)
        assertEquals(TestRoom.ACROSTIC, f.grounds.roomOf(f.player))
        assertTrue(f.said("Greetings friend, and welcome here,"))
        assertEquals(order, RecruitmentState.order(f.player))
        assertNotEquals(0, f.player.rdRiddle)
        val riddle = f.player.rdRiddle
        f.choose(2)
        f.talkObserver(TestRoom.ACROSTIC)
        assertEquals(riddle, f.player.rdRiddle, "asking again keeps the riddle")
        f.testing.abandonVisit(f.player)
        f.enter(order)
        assertEquals(riddle, f.player.rdRiddle, "coming back keeps the riddle")
        assertEquals(order, RecruitmentState.order(f.player))
    }

    /* Sir Spishyus */

    @Test fun `the fox, chicken and grain cross in seven moves, judged from where they are`() {
        val f = Fixture()
        f.enter(listOf(TestRoom.CROSSING, TestRoom.COMBAT, TestRoom.STATUES, TestRoom.PATIENCE, TestRoom.LOGIC))
        f.carry(Cargo.CHICKEN)
        f.crossBridge()
        f.putDown(Cargo.CHICKEN)
        f.crossBridge()
        f.carry(Cargo.FOX)
        f.crossBridge()
        f.putDown(Cargo.FOX)
        f.carry(Cargo.CHICKEN)
        f.crossBridge()
        f.putDown(Cargo.CHICKEN)
        f.carry(Cargo.GRAIN)
        f.crossBridge()
        f.putDown(Cargo.GRAIN)
        assertFalse(RecruitmentState.passed(f.player, TestRoom.CROSSING))
        f.crossBridge()
        f.carry(Cargo.CHICKEN)
        f.crossBridge()
        f.putDown(Cargo.CHICKEN)
        assertTrue(RecruitmentState.passed(f.player, TestRoom.CROSSING))
        assertTrue(f.said("Congratulations! You have solved this room's puzzle!"))
        assertTrue(f.player.worn.none { it != null }, "nothing still carried")
    }

    @Test fun `the bridge takes one piece, and leaving the fox with the chicken or the chicken with the grain fails`() {
        val f = Fixture()
        f.enter(listOf(TestRoom.CROSSING, TestRoom.COMBAT, TestRoom.STATUES, TestRoom.PATIENCE, TestRoom.LOGIC))
        f.carry(Cargo.FOX)
        f.carry(Cargo.GRAIN)
        f.crossBridge()
        assertTrue(f.said("more than 5 Kg"))
        assertEquals(Bank.EAST, f.crossing.load(f.player)!!.player, "did not cross")
        f.putDown(Cargo.GRAIN)
        f.crossBridge()
        assertFalse(f.grounds.inside(f.player), "the chicken ate the grain")
        assertNull(RecruitmentState.order(f.player), "attempt cleared")
        assertEquals(TestingGrounds.FALADOR_PARK, f.player.coords)
        assertTrue(f.said("jolly bad luck"))
        assertTrue(f.player.worn.none { it != null } && f.player.inv.isEmpty(), "the fox went with the room")
        assertEquals(RecruitmentDriveQuest.STAGE_STARTED, f.stage(), "the quest stays accepted")
    }

    @Test fun `every legal river crossing state is judged by the rules, not by a sequence`() {
        val seen = mutableSetOf(RiverCrossing.START)
        val queue = ArrayDeque(listOf(RiverCrossing.START))
        var solved = 0
        while (queue.isNotEmpty()) {
            val state = queue.removeFirst()
            if (state.solved) solved++
            val next = mutableListOf<RiverCrossing>()
            for (cargo in Cargo.entries) {
                state.pickUp(cargo)?.let(next::add)
                state.putDown(cargo)?.let(next::add)
            }
            when (val outcome = state.cross()) {
                is RiverCrossing.Outcome.Crossed -> {
                    val left = state.on(state.player)
                    assertFalse(Cargo.FOX in left && Cargo.CHICKEN in left)
                    assertFalse(Cargo.CHICKEN in left && Cargo.GRAIN in left)
                    next += outcome.state
                }
                is RiverCrossing.Outcome.Eaten -> {
                    val left = state.on(state.player)
                    assertTrue((Cargo.FOX in left && Cargo.CHICKEN in left) || (Cargo.CHICKEN in left && Cargo.GRAIN in left))
                }
                RiverCrossing.Outcome.Overloaded -> assertTrue(state.carried.size > 1)
            }
            for (n in next) if (seen.add(n)) queue += n
        }
        assertTrue(solved > 0, "the puzzle can be solved")
        val bothSolutions =
            listOf(
                listOf(Cargo.CHICKEN, null, Cargo.FOX, Cargo.CHICKEN, Cargo.GRAIN, null, Cargo.CHICKEN),
                listOf(Cargo.CHICKEN, null, Cargo.GRAIN, Cargo.CHICKEN, Cargo.FOX, null, Cargo.CHICKEN),
            )
        for (moves in bothSolutions) {
            var state = RiverCrossing.START
            var bankBefore = state.player
            for (move in moves) {
                state = state.carried.fold(state) { s, c -> s.putDown(c)!! }
                if (move != null) {
                    state = state.pickUp(move) ?: error("can't take $move from ${state.player}: $state")
                }
                val outcome = state.cross()
                assertTrue(outcome is RiverCrossing.Outcome.Crossed, "$moves at $move: $outcome")
                state = (outcome as RiverCrossing.Outcome.Crossed).state
                assertNotEquals(bankBefore, state.player)
                bankBefore = state.player
            }
            state = state.carried.fold(state) { s, c -> s.putDown(c)!! }
            assertTrue(state.solved, "$moves")
        }
    }

    /* Lady Table */

    @Test fun `the missing statue is kept for the visit, can't be touched early, and the right one passes`() {
        val f = Fixture()
        f.enter(listOf(TestRoom.STATUES, TestRoom.COMBAT, TestRoom.CROSSING, TestRoom.PATIENCE, TestRoom.LOGIC))
        val answer = f.player.rdStatueAnswer
        assertTrue(answer in 1..12)
        assertEquals(answer, f.player.rdStatueLayout, "shows the layout with one missing")
        val missing = checkNotNull(StatueRoom.missingStatue(answer))
        f.touchStatue(StatueRoom.STATUES.map { it.asRSCM(RSCMType.LOC) }.first { it != missing })
        assertTrue(f.said("Wait until all statues have been placed"))
        assertTrue(f.grounds.inside(f.player), "an early touch is no answer")
        f.talkObserver(TestRoom.STATUES)
        assertEquals(answer, f.player.rdStatueAnswer, "talking does not reroll")
        f.player.delay = f.player.currentMapClock + 5
        f.statues.memoriseOver(f.player)
        assertEquals(RecruitmentState.STATUE_MEMORISING, f.player.rdStatuePhase, "the swap waits while the player is busy")
        f.player.delay = Int.MIN_VALUE
        f.statues.memoriseOver(f.player)
        f.runLaunched()
        assertEquals(RecruitmentState.STATUE_READY, f.player.rdStatuePhase)
        assertEquals(StatueRoom.FULL_SET, f.player.rdStatueLayout)
        assertTrue(f.said("bring the missing statue back in"))
        f.touchStatue(missing)
        assertTrue(RecruitmentState.passed(f.player, TestRoom.STATUES))
    }

    @Test fun `touching the wrong statue fails the test`() {
        val f = Fixture()
        f.enter(listOf(TestRoom.STATUES, TestRoom.COMBAT, TestRoom.CROSSING, TestRoom.PATIENCE, TestRoom.LOGIC))
        f.statues.memoriseOver(f.player)
        f.runLaunched()
        val missing = checkNotNull(StatueRoom.missingStatue(f.player.rdStatueAnswer))
        val wrong = StatueRoom.STATUES.map { it.asRSCM(RSCMType.LOC) }.first { it != missing }
        f.touchStatue(wrong)
        assertFalse(f.grounds.inside(f.player))
        assertNull(RecruitmentState.order(f.player))
    }

    /* Sir Ren Itchood */

    @Test fun `every clue spells its riddle's answer down its first letters, one line each`() {
        assertEquals(listOf("BITE", "TIME", "FISH", "MEAT", "LAST", "RAIN"), AcrosticRoom.RIDDLES.map { it.answer })
        for (riddle in AcrosticRoom.RIDDLES) {
            for (clue in riddle.clues) {
                val lines = clue.split("<br>")
                assertEquals(4, lines.size, clue)
                assertEquals(riddle.answer, lines.joinToString("") { it.trimStart().first().uppercase() }, clue)
            }
        }
    }

    @Test fun `the lock opens for this player's own riddle only, and a wrong word fails`() {
        val f = Fixture()
        f.enter(listOf(TestRoom.ACROSTIC, TestRoom.COMBAT, TestRoom.CROSSING, TestRoom.PATIENCE, TestRoom.LOGIC))
        val riddle = checkNotNull(f.acrostic.riddle(f.player))
        f.choose(2)
        f.talkObserver(TestRoom.ACROSTIC)
        assertEquals(1, f.player.rdRiddleClue)
        assertTrue(f.said(riddle.clues[1].replace("<br>", " ")))
        f.choose(2)
        f.talkObserver(TestRoom.ACROSTIC)
        assertTrue(f.said(riddle.clues[2].replace("<br>", " ")))
        f.dial(riddle.answer)
        assertEquals(riddle.answer, f.acrostic.combination(f.player))
        f.dispatch(closeModals = false) { with(f.acrostic) { tryCombination() } }
        assertTrue(RecruitmentState.passed(f.player, TestRoom.ACROSTIC))
        assertTrue(f.said("Your wit is sharp"))

        val g = Fixture()
        g.enter(listOf(TestRoom.ACROSTIC, TestRoom.COMBAT, TestRoom.CROSSING, TestRoom.PATIENCE, TestRoom.LOGIC))
        val other = AcrosticRoom.RIDDLES.first { it != g.acrostic.riddle(g.player) }
        g.dial(other.answer)
        g.dispatch(closeModals = false) { with(g.acrostic) { tryCombination() } }
        assertTrue(g.said("You have failed to solve the riddle."))
        assertFalse(g.grounds.inside(g.player))
    }

    /* Ms. Hynn Terprett */

    @Test fun `each logic riddle passes on its answer and fails on any other`() {
        for (riddle in LogicRoom.LogicRiddle.entries) {
            val right = Fixture()
            right.player.rdLogicRiddle = riddle.ordinal + 1
            right.answer(riddle, LogicRoom.ANSWERS.getValue(riddle))
            right.enter(listOf(TestRoom.LOGIC, TestRoom.COMBAT, TestRoom.CROSSING, TestRoom.PATIENCE, TestRoom.STATUES))
            assertTrue(RecruitmentState.passed(right.player, TestRoom.LOGIC), riddle.name)
            val wrong = Fixture()
            wrong.player.rdLogicRiddle = riddle.ordinal + 1
            wrong.answer(riddle, if (LogicRoom.ANSWERS.getValue(riddle) == 2) 1 else 2)
            wrong.enter(listOf(TestRoom.LOGIC, TestRoom.COMBAT, TestRoom.CROSSING, TestRoom.PATIENCE, TestRoom.STATUES))
            assertFalse(wrong.grounds.inside(wrong.player), riddle.name)
            assertNull(RecruitmentState.order(wrong.player))
        }
        assertEquals(mapOf(LogicRoom.LogicRiddle.FINGERS to 0, LogicRoom.LogicRiddle.DAUGHTER to 10, LogicRoom.LogicRiddle.FALSE_STATEMENTS to 3, LogicRoom.LogicRiddle.FATE to 3, LogicRoom.LogicRiddle.BUCKETS to 1), LogicRoom.ANSWERS)
    }

    /* Sir Tinley */

    @Test fun `waiting nine seconds untouched passes, and harmless client traffic doesn't count`() {
        val f = Fixture()
        f.enter(listOf(TestRoom.PATIENCE, TestRoom.COMBAT, TestRoom.CROSSING, TestRoom.STATUES, TestRoom.LOGIC))
        assertTrue(f.grounds.objs.contains(PatienceRoom.HOURGLASS to PatienceRoom.HOURGLASS_TILE))
        repeat(20) { f.patience.tick(f.player) }
        assertFalse(RecruitmentState.passed(f.player, TestRoom.PATIENCE), "taking no clue is no test")
        f.talkObserver(TestRoom.PATIENCE)
        assertEquals(RecruitmentState.PATIENCE_WAITING, f.player.rdPatiencePhase)
        repeat(PatienceRoom.WAIT_TICKS) {
            f.player.currentMapClock++
            f.patience.tick(f.player)
        }
        assertFalse(RecruitmentState.passed(f.player, TestRoom.PATIENCE), "not before he speaks")
        f.patience.tick(f.player)
        f.runLaunched()
        assertTrue(RecruitmentState.passed(f.player, TestRoom.PATIENCE))
        assertFalse(PatienceRoom.TIMER in f.player.softTimerMap, "the timer is cleaned up")
    }

    @Test fun `moving, interacting or using an item during the wait fails`() {
        for (action in listOf<(Fixture) -> Unit>(
            { it.player.coords = it.player.coords.translateX(1) },
            { it.player.clearPendingAction(it.events) },
            { it.player.interaction = InteractionObj(unused<Obj>(), InteractionOp.Op3, hasOpTrigger = true, hasApTrigger = false) },
            { it.player.routeDestination.add(it.player.coords.translateZ(2)) },
        )) {
            val f = Fixture()
            f.enter(listOf(TestRoom.PATIENCE, TestRoom.COMBAT, TestRoom.CROSSING, TestRoom.STATUES, TestRoom.LOGIC))
            f.talkObserver(TestRoom.PATIENCE)
            repeat(3) { f.patience.tick(f.player) }
            action(f)
            f.patience.tick(f.player)
            f.runLaunched()
            assertFalse(f.grounds.inside(f.player))
            assertNull(RecruitmentState.order(f.player))
        }
    }

    /* Sir Kuam Ferentse */

    @Test fun `Sir Leye falls only to the warhammer or bare hands, whatever the player's body type`() {
        for (bodyType in listOf(0, 1)) {
            for ((weapon, passes) in listOf(null to true, "obj.steel_warhammer" to true, "obj.steel_sword" to false, "obj.steel_claws" to false, "obj.steel_battleaxe" to false)) {
                val f = Fixture()
                f.player.appearance.bodyType = bodyType
                f.enter(listOf(TestRoom.COMBAT, TestRoom.LOGIC, TestRoom.CROSSING, TestRoom.STATUES, TestRoom.PATIENCE))
                val leye = checkNotNull(f.grounds.space(f.player)?.leye) { "Sir Leye was summoned" }
                assertTrue(leye.isSlotAssigned)
                assertTrue(f.grounds.objs.map { it.first }.containsAll(CombatRoom.WEAPONS.map { it.first }))
                if (weapon != null) f.player.worn[3] = InvObj(weapon, 1)
                f.combat.judge(f.player, f.combat.permitted(weapon?.asRSCM(RSCMType.OBJ)))
                f.runLaunched()
                assertEquals(passes, RecruitmentState.passed(f.player, TestRoom.COMBAT), "$weapon, body type $bodyType")
                assertEquals(passes, f.grounds.inside(f.player))
            }
        }
    }

    @Test fun `a blade's finishing blow fails even if the warhammer is held by the time he dies, and others can't fight him`() {
        val f = Fixture()
        f.enter(listOf(TestRoom.COMBAT, TestRoom.LOGIC, TestRoom.CROSSING, TestRoom.STATUES, TestRoom.PATIENCE))
        val leye = checkNotNull(f.grounds.space(f.player)?.leye)
        assertEquals(NpcAttackValidateResult.Pass, SirLeyeAttackHook().validate(f.player, leye))
        assertTrue(SirLeyeAttackHook().validate(f.second, leye) is NpcAttackValidateResult.Deny)
        f.player.worn[3] = InvObj("obj.steel_warhammer", 1)
        f.combat.judge(f.player, blow = false)
        f.runLaunched()
        assertFalse(RecruitmentState.passed(f.player, TestRoom.COMBAT))
        assertFalse(f.grounds.inside(f.player))
        assertFalse(leye.isSlotAssigned, "removed with the visit")
    }

    /* Miss Cheevers */

    @Test fun `both doors open the documented way, through every intermediate item`() {
        val f = Fixture()
        f.enter(listOf(TestRoom.IMPROVISATION, TestRoom.STATUES, TestRoom.COMBAT, TestRoom.LOGIC, TestRoom.PATIENCE))
        f.give(ImprovisationRoom.SPADE)
        f.locU(ImprovisationRoom.BUNSEN_BURNER, CoordGrid(2472, 4940, 0), ImprovisationRoom.SPADE)
        assertEquals(1, f.count(ImprovisationRoom.SPADE_HEAD))
        assertEquals(1, f.count("obj.ashes"))
        f.choose(1)
        f.loc(ImprovisationRoom.Shelf.NORTH_2.loc, CoordGrid(2472, 4944, 0))
        assertEquals(1, f.count(Alchemy.CUPRIC_SULFATE))
        f.choose(3)
        f.loc(ImprovisationRoom.Shelf.SOUTH_1.loc, CoordGrid(2474, 4936, 0))
        assertEquals(3, f.count(Alchemy.LIQUID))
        f.choose(1)
        f.loc(ImprovisationRoom.Shelf.SOUTH_1.loc, CoordGrid(2474, 4936, 0))
        assertTrue(f.said("There is nothing of interest on these shelves."))

        f.locU("loc.rd_stone_door", CoordGrid(2477, 4940, 0), ImprovisationRoom.SPADE_HEAD)
        assertEquals(ImprovisationRoom.DOOR_SPADE, f.player.vars[ImprovisationRoom.STONE_DOOR_VARBIT])
        f.locU("loc.rd_stone_door", CoordGrid(2477, 4940, 0), Alchemy.CUPRIC_SULFATE)
        f.locU("loc.rd_stone_door", CoordGrid(2477, 4940, 0), Alchemy.LIQUID)
        assertEquals(ImprovisationRoom.DOOR_EXPANDED, f.player.vars[ImprovisationRoom.STONE_DOOR_VARBIT])
        f.loc("loc.rd_stone_door", CoordGrid(2477, 4940, 0))
        assertEquals(ImprovisationRoom.DOOR_OPEN, f.player.vars[ImprovisationRoom.STONE_DOOR_VARBIT])

        f.loc("loc.rd_large_crate", CoordGrid(2476, 4943, 0))
        assertEquals(1, f.count(Alchemy.TIN))
        f.loc("loc.rd_large_crate", CoordGrid(2476, 4943, 0))
        assertEquals(1, f.count(Alchemy.TIN), "one tin while one is carried")
        f.choose(1)
        f.loc(ImprovisationRoom.Shelf.NORTH_3.loc, CoordGrid(2473, 4944, 0))
        f.use(Alchemy.LIQUID, Alchemy.TIN)
        assertEquals(1, f.count(Alchemy.TIN_LAYERED))
        f.use(Alchemy.GYPSUM, Alchemy.TIN_LAYERED)
        assertEquals(1, f.count(Alchemy.TIN_HARDENING))
        f.locU(ImprovisationRoom.CHAINED_KEY, CoordGrid(2468, 4938, 0), Alchemy.TIN_HARDENING)
        assertEquals(1, f.count(Alchemy.TIN_IMPRESSION))
        f.choose(1)
        f.loc(ImprovisationRoom.Shelf.SOUTH_3.loc, CoordGrid(2472, 4936, 0))
        f.choose(1)
        f.loc(ImprovisationRoom.Shelf.SOUTH_2.loc, CoordGrid(2473, 4936, 0))
        f.use(Alchemy.COPPER_POWDER, Alchemy.TIN_IMPRESSION)
        assertEquals(1, f.count(Alchemy.TIN_WITH_COPPER))
        f.use(Alchemy.TIN_POWDER, Alchemy.TIN_WITH_COPPER)
        assertEquals(1, f.count(Alchemy.TIN_UNHEATED))
        f.locU(ImprovisationRoom.BUNSEN_BURNER, CoordGrid(2472, 4940, 0), Alchemy.TIN_UNHEATED)
        assertEquals(1, f.count(Alchemy.TIN_KEY))
        f.loc("loc.rd_bookshelf_old_tall3", CoordGrid(2468, 4937, 0))
        f.use(Alchemy.KNIFE, Alchemy.TIN_KEY)
        assertEquals(1, f.count(Alchemy.BRONZE_KEY))
        assertEquals(1, f.count(Alchemy.TIN_IMPRESSION))

        f.player.coords = CoordGrid(2476, 4940, 0)
        f.loc(room = TestRoom.IMPROVISATION.exitDoor, at = TestRoom.IMPROVISATION.exitDoorTile)
        assertFalse(RecruitmentState.passed(f.player, TestRoom.IMPROVISATION), "the stone door is still in the way")
        f.loc(ImprovisationRoom.STONE_DOOR_OPEN, CoordGrid(2477, 4940, 0))
        assertEquals(ImprovisationRoom.BETWEEN_DOORS, f.player.coords)
        f.loc(room = TestRoom.IMPROVISATION.exitDoor, at = TestRoom.IMPROVISATION.exitDoorTile)
        assertTrue(RecruitmentState.passed(f.player, TestRoom.IMPROVISATION))
        f.loc(room = TestRoom.IMPROVISATION.exitDoor, at = TestRoom.IMPROVISATION.exitDoorTile)
        assertEquals(TestRoom.STATUES, f.grounds.roomOf(f.player), "on to the next room")
        assertEquals(0, f.count(Alchemy.BRONZE_KEY), "the key stays behind")
        assertEquals(0, f.player.vars[ImprovisationRoom.STONE_DOOR_VARBIT])
    }

    @Test fun `wrong mixtures follow the wiki and nothing is used up by an action that does nothing`() {
        assertEquals(Alchemy.HOT_MIXTURE, (Alchemy.combine(Alchemy.CUPRIC_SULFATE, Alchemy.LIQUID, Alchemy.Layer.NONE) as Alchemy.Result.Change).into[Alchemy.CUPRIC_SULFATE])
        assertEquals(Alchemy.WARM_MIXTURE, (Alchemy.combine(Alchemy.LIQUID, Alchemy.GYPSUM, Alchemy.Layer.NONE) as Alchemy.Result.Change).into[Alchemy.GYPSUM])
        for (other in listOf(Alchemy.SALT, Alchemy.GYPSUM, Alchemy.ACETIC_ACID, Alchemy.COPPER_POWDER, Alchemy.TIN_POWDER)) {
            assertEquals(Alchemy.HORRIBLE_MIXTURE, (Alchemy.combine(Alchemy.CUPRIC_SULFATE, other, Alchemy.Layer.NONE) as Alchemy.Result.Change).into[Alchemy.CUPRIC_SULFATE], other)
        }
        assertEquals(Alchemy.HORRIBLE_MIXTURE, (Alchemy.combine(Alchemy.ACETIC_ACID, Alchemy.HOT_MIXTURE, Alchemy.Layer.NONE) as Alchemy.Result.Change).into[Alchemy.HOT_MIXTURE])
        assertEquals(Alchemy.Result.Laugh, Alchemy.combine(Alchemy.NITROUS_OXIDE, Alchemy.GYPSUM, Alchemy.Layer.NONE))
        assertEquals(Alchemy.Result.Say("I have no time for brine!"), Alchemy.combine(Alchemy.SALT, Alchemy.LIQUID, Alchemy.Layer.NONE))
        assertEquals(Alchemy.TIN_STRANGE, (Alchemy.combine(Alchemy.TIN, Alchemy.ACETIC_ACID, Alchemy.Layer.NONE) as Alchemy.Result.Change).into[Alchemy.TIN])
        assertEquals(Alchemy.TIN_HARDENING, (Alchemy.combine(Alchemy.GYPSUM, Alchemy.TIN_LAYERED, Alchemy.Layer.LIQUID) as Alchemy.Result.Change).into[Alchemy.TIN_LAYERED], "either order")
        assertEquals(Alchemy.TIN_UNHEATED, (Alchemy.combine(Alchemy.TIN_POWDER, Alchemy.TIN_WITH_COPPER, Alchemy.Layer.NONE) as Alchemy.Result.Change).into[Alchemy.TIN_WITH_COPPER])
        assertEquals(Alchemy.TIN_UNHEATED, (Alchemy.combine(Alchemy.COPPER_POWDER, Alchemy.TIN_WITH_TIN, Alchemy.Layer.NONE) as Alchemy.Result.Change).into[Alchemy.TIN_WITH_TIN])
        for (tool in Alchemy.KEY_TOOLS) assertEquals(Alchemy.BRONZE_KEY, (Alchemy.combine(tool, Alchemy.TIN_KEY, Alchemy.Layer.NONE) as Alchemy.Result.Change).adds, tool)
        assertEquals(Alchemy.Result.Nothing, Alchemy.combine(Alchemy.KNIFE, Alchemy.TIN_UNHEATED, Alchemy.Layer.NONE), "no key before heating")

        val f = Fixture()
        f.enter(listOf(TestRoom.IMPROVISATION, TestRoom.LOGIC, TestRoom.COMBAT, TestRoom.STATUES, TestRoom.PATIENCE))
        f.give(ImprovisationRoom.SPADE)
        f.locU("loc.rd_stone_door", CoordGrid(2477, 4940, 0), ImprovisationRoom.SPADE)
        assertEquals(1, f.count(ImprovisationRoom.SPADE), "a whole spade doesn't go in")
        assertTrue(f.said("smash my way through the stone"))
        f.give(Alchemy.TIN)
        f.give(Alchemy.ACETIC_ACID)
        f.use(Alchemy.ACETIC_ACID, Alchemy.TIN)
        assertEquals(1, f.count(Alchemy.TIN_STRANGE))
        f.drop(Alchemy.TIN_STRANGE)
        f.loc("loc.rd_large_crate", CoordGrid(2476, 4943, 0))
        assertEquals(1, f.count(Alchemy.TIN), "a dropped tin is replaced from the crate")
        f.choose(1)
        f.loc(ImprovisationRoom.Shelf.NORTH_3.loc, CoordGrid(2473, 4944, 0))
        f.choose(1)
        f.loc(ImprovisationRoom.Shelf.NORTH_3.loc, CoordGrid(2473, 4944, 0))
        assertEquals(1, f.count(Alchemy.GYPSUM), "the gypsum is there once per visit")
    }

    /* Failing, quitting, logging out, dying */

    @Test fun `quitting through the entrance portal clears the attempt but keeps the quest`() {
        val f = Fixture()
        f.enter(listOf(TestRoom.STATUES, TestRoom.COMBAT, TestRoom.CROSSING, TestRoom.PATIENCE, TestRoom.LOGIC))
        RecruitmentState.markPassed(f.player, TestRoom.STATUES)
        f.dispatch { with(f.testing) { proceed(TestRoom.STATUES) } }
        assertEquals(TestRoom.COMBAT, f.grounds.roomOf(f.player))
        f.loc(TestRoom.COMBAT.entrancePortal, TestRoom.COMBAT.entranceTile)
        assertFalse(f.grounds.inside(f.player))
        assertNull(RecruitmentState.order(f.player))
        assertEquals(0, RecruitmentState.passedCount(f.player))
        assertEquals(RecruitmentDriveQuest.STAGE_STARTED, f.stage())
    }

    @Test fun `logging out or dying ends the visit and its items but keeps the passed rooms, and only room items go`() {
        for (exit in listOf("logout", "death", "login")) {
            val f = Fixture()
            val order = listOf(TestRoom.LOGIC, TestRoom.COMBAT, TestRoom.STATUES, TestRoom.PATIENCE, TestRoom.ACROSTIC)
            f.player.rdLogicRiddle = LogicRoom.LogicRiddle.FATE.ordinal + 1
            f.answer(LogicRoom.LogicRiddle.FATE, 3)
            f.enter(order)
            assertTrue(RecruitmentState.passed(f.player, TestRoom.LOGIC))
            f.dispatch { with(f.testing) { proceed(TestRoom.LOGIC) } }
            assertEquals(TestRoom.COMBAT, f.grounds.roomOf(f.player))
            f.player.worn[3] = InvObj("obj.steel_warhammer", 1)
            f.give("obj.coins", 5)
            val leye = checkNotNull(f.grounds.space(f.player)?.leye)
            when (exit) {
                "logout" -> f.testing.abandonVisit(f.player)
                "death" -> TrainingGroundsHooks { f.testing }.cleanup(f.player)
                else -> {
                    f.grounds.forget(f.player)
                    f.testing.abandonVisit(f.player)
                }
            }
            assertFalse(f.grounds.inside(f.player), exit)
            assertFalse(leye.isSlotAssigned, "$exit: Sir Leye goes")
            assertNull(f.player.worn[3], "$exit: the warhammer goes")
            assertEquals(5, f.stack("obj.coins"), "$exit: anything else stays")
            assertEquals(order, RecruitmentState.order(f.player), "$exit: the attempt is kept")
            assertTrue(RecruitmentState.passed(f.player, TestRoom.LOGIC))
            assertEquals(0, f.player.rdRoomLogout)
            assertEquals(TestRoom.COMBAT, RecruitmentState.currentRoom(f.player), "$exit: resumes at Sir Leye")
        }
    }

    @Test fun `teleports are refused inside the grounds but not elsewhere`() {
        val f = Fixture()
        val hooks = TrainingGroundsHooks { f.testing }
        assertNull(hooks.validate(f.player, TeleportType.Standard, unused<AreaChecker>()))
        f.enter(listOf(TestRoom.STATUES, TestRoom.COMBAT, TestRoom.CROSSING, TestRoom.PATIENCE, TestRoom.LOGIC))
        assertNotNull(hooks.validate(f.player, TeleportType.Standard, unused<AreaChecker>()))
        assertNull(hooks.validate(f.player, TeleportType.Exempt, unused<AreaChecker>()))
    }

    /* Stale actions and two players */

    @Test fun `actions from an earlier room or attempt do nothing`() {
        val f = Fixture()
        f.enter(listOf(TestRoom.STATUES, TestRoom.COMBAT, TestRoom.CROSSING, TestRoom.PATIENCE, TestRoom.LOGIC))
        val stale = f.testing.attempt(f.player)
        RecruitmentState.markPassed(f.player, TestRoom.STATUES)
        f.dispatch { with(f.testing) { proceed(TestRoom.STATUES) } }
        f.dispatch { assertFalse(with(f.testing) { passRoom(TestRoom.COMBAT, stale) }) }
        f.dispatch { assertFalse(with(f.testing) { failRoom(TestRoom.COMBAT, stale) }) }
        assertTrue(f.grounds.inside(f.player))
        assertFalse(RecruitmentState.passed(f.player, TestRoom.COMBAT))
        f.touchStatue(checkNotNull(StatueRoom.missingStatue(1)))
        assertTrue(f.grounds.inside(f.player), "the statue room's statues are out of reach")
        f.dispatch { with(f.testing) { proceed(TestRoom.STATUES) } }
        assertEquals(TestRoom.COMBAT, f.grounds.roomOf(f.player), "a repeated exit does nothing")
        f.dispatch { with(f.testing) { proceed(TestRoom.COMBAT) } }
        assertEquals(TestRoom.COMBAT, f.grounds.roomOf(f.player), "no leaving an unpassed room")
    }

    @Test fun `two players in different rooms keep separate puzzles`() {
        val f = Fixture()
        f.enter(listOf(TestRoom.CROSSING, TestRoom.COMBAT, TestRoom.STATUES, TestRoom.PATIENCE, TestRoom.LOGIC))
        f.asSecond { f.enter(listOf(TestRoom.STATUES, TestRoom.COMBAT, TestRoom.CROSSING, TestRoom.PATIENCE, TestRoom.LOGIC)) }
        f.carry(Cargo.CHICKEN)
        assertEquals(CrossingRoom.EAST_ABSENT, f.player.vars["varbit.rd_chickleft"])
        assertEquals(CrossingRoom.EAST_PRESENT, f.second.vars["varbit.rd_chickleft"])
        assertEquals(0, f.player.rdStatueAnswer)
        assertNotEquals(0, f.second.rdStatueAnswer)
        f.asSecond { f.carry(Cargo.FOX) }
        assertNull(f.second.worn[3], "no fox outside the crossing room")
        assertNotNull(f.player.worn[5], "the first player still carries the chicken")
        assertNotEquals(f.testing.attempt(f.player), -1)
    }

    /* Finishing */

    @Test fun `five passes bring the player back to Sir Tiffy, who rewards them once`() {
        val f = Fixture()
        val order = listOf(TestRoom.STATUES, TestRoom.COMBAT, TestRoom.CROSSING, TestRoom.PATIENCE, TestRoom.ACROSTIC)
        f.enter(order)
        for (room in order) {
            assertEquals(room, RecruitmentState.currentRoom(f.player))
            f.dispatch {
                with(f.grounds) { moveTo(room.arrival) }
                with(f.testing) { passRoom(room, f.testing.attempt(player)) }
            }
            if (room != order.last()) f.dispatch { with(f.testing) { proceed(room) } }
        }
        assertTrue(f.said("You have passed all five of the required tests!"))
        assertFalse(f.grounds.inside(f.player))
        assertEquals(TestingGrounds.FALADOR_PARK, f.player.coords)
        assertTrue(RecruitmentState.allPassed(f.player))
        val qp = f.player.vars["varp.qp"]
        f.talk(RecruitmentDriveQuest.SIR_TIFFY)
        assertEquals(RecruitmentDriveQuest.STAGE_COMPLETE, f.stage())
        assertEquals(qp + 1, f.player.vars["varp.qp"])
        assertEquals(RecruitmentDriveQuest.REWARD_COINS, f.stack("obj.coins"))
        assertEquals(1, f.count(RecruitmentDriveQuest.SALLET))
        for (stat in listOf("stat.prayer", "stat.herblore", "stat.agility")) {
            assertEquals(10005, f.player.statMap.getFineXP(stat), "$stat: 1,000.5 xp kept to the tenth")
        }
        assertTrue(f.said("Your respawn point has been left alone"))
        assertEquals(GazeOfSaradomin.LUMBRIDGE, f.player.respawnPoint)
        f.choose(5)
        f.talk(RecruitmentDriveQuest.SIR_TIFFY)
        assertEquals(RecruitmentDriveQuest.REWARD_COINS, f.stack("obj.coins"), "no second reward")
        assertEquals(1, f.count(RecruitmentDriveQuest.SALLET))
        assertEquals(qp + 1, f.player.vars["varp.qp"])
    }

    @Test fun `initiate armour needs the quest, and Sir Tiffy moves the respawn point both ways`() {
        respectingProgress {
            val f = Fixture()
            val hook = InitiateArmourWearHook { f.quest }
            val sallet = checkNotNull(ServerCacheManager.getItem(RecruitmentDriveQuest.SALLET.asRSCM(RSCMType.OBJ)))
            assertNotNull(hook.restriction(f.player, RestrictedAction.Equip(sallet)))
            val gaze = GazeOfSaradomin { f.quest }
            f.player.respawnPoint = GazeOfSaradomin.FALADOR
            assertNull(gaze.respawn(f.player), "no Falador respawn before the quest")
            f.player.respawnPoint = GazeOfSaradomin.LUMBRIDGE
            f.quest.quest.jumpToStage(f.player, RecruitmentDriveQuest.STAGE_COMPLETE)
            assertNull(hook.restriction(f.player, RestrictedAction.Equip(sallet)))
            assertNull(gaze.respawn(f.player), "the quest leaves the respawn point alone")
            f.choose(4, 1)
            f.talk(RecruitmentDriveQuest.SIR_TIFFY)
            assertEquals(GazeOfSaradomin.FALADOR, f.player.respawnPoint)
            assertEquals(GazeOfSaradomin.FALADOR_RESPAWN, gaze.respawn(f.player))
            f.choose(4, 2)
            f.talk(RecruitmentDriveQuest.SIR_TIFFY)
            assertEquals(GazeOfSaradomin.FALADOR, f.player.respawnPoint, "declining keeps it")
            f.choose(4, 1)
            f.talk(RecruitmentDriveQuest.SIR_TIFFY)
            assertEquals(GazeOfSaradomin.LUMBRIDGE, f.player.respawnPoint)
            assertNull(gaze.respawn(f.player))
            assertTrue(GazeOfSaradomin { f.quest }.respawnPriority < 0, "minigame respawns come first")
        }
    }

    /* Fixture */

    private class TestGrounds(private val npcRepo: NpcRepository) : TestingGrounds(unused<QuestInstances>(), npcRepo, unused<ObjRepository>()) {
        val objs = mutableListOf<Pair<String, CoordGrid>>()

        override fun ProtectedAccess.open(arrival: CoordGrid): Boolean {
            val space = adoptWorld(player)
            for (room in TestRoom.entries) {
                val npc = Npc(room.observer, room.observerTile)
                npcRepo.add(npc, Int.MAX_VALUE)
                space.observers[room] = npc
            }
            return true
        }

        override fun ProtectedAccess.close(destination: CoordGrid) {
            forget(player)
            telejump(destination, TeleportType.Exempt)
        }

        override fun spawnNpc(player: Player, type: String, world: CoordGrid): Npc? {
            space(player) ?: return null
            return Npc(type, world).also { npcRepo.add(it, Int.MAX_VALUE) }
        }

        override fun spawnObj(player: Player, type: String, world: CoordGrid) {
            objs += type to world
        }
    }

    private class Fixture(stage: Int = RecruitmentDriveQuest.STAGE_STARTED) {
        val events = EventBus()
        private val client = RecordingClient()
        private val collision = CollisionFlagMap()
        private val npcs = NpcList()
        private val players = PlayerList()
        private val coroutine = GameCoroutine("rd-test")
        private var result: Result<Unit>? = null
        private val validator = PlayerTeleportValidator(setOf(PlayerTeleportValidateHook { _, _, _ -> null }))
        private val context =
            ProtectedAccessContextFactory.empty().copy(
                getEventBus = { events }, getAlignment = { TextAlignment() },
                getCollision = { collision }, getNpcList = { npcs },
                getTeleportValidator = { validator },
                getAreaChecker = { unused<AreaChecker>() },
                getNpcInteractions = { NpcInteractions(events) },
                getRandom = { DefaultGameRandom(Random(7)) },
                getHitModifier = { NoopPlayerHitModifier },
                getInstantHitProcessor = { DamageOnlyPlayerHitProcessor(events, npcs, players) },
            )
        val clock = MapClock(100)
        private val npcRepo: NpcRepository
        private val picks = ArrayDeque<Int>()
        private val counts = ArrayDeque<Int>()
        private val launched = ArrayDeque<Pair<Player, suspend ProtectedAccess.() -> Unit>>()
        private val locU =
            LocUInteractions::class.java.getDeclaredConstructor(EventBus::class.java).apply { isAccessible = true }.newInstance(events)

        val player = newPlayer(8181L, 1)
        val second = newPlayer(8282L, 2)
        private var active = player

        val quest = RecruitmentDriveQuest()
        val bkf = BlackKnightsFortressQuest()
        val druidic = DruidicRitualQuest()
        val grounds: TestGrounds
        val testing: RecruitmentTesting
        val crossing: CrossingRoom
        val statues: StatueRoom
        val combat: CombatRoom
        val patience: PatienceRoom
        val acrostic: AcrosticRoom

        init {
            for ((x0, z0, x1, z1) in AREAS) {
                for (level in 0..1) for (x in x0..x1 step 8) for (z in z0..z1 step 8) collision.allocateIfAbsent(x, z, level)
            }
            players[player.slotId] = player
            players[second.slotId] = second
            npcRepo = NpcRepository(clock, NpcRegistry(npcs, collision, events), npcs)
            grounds = TestGrounds(npcRepo)
            testing = RecruitmentTesting(quest, grounds, WorldQueueList(), players, unused<ProtectedAccessLauncher>())
            testing.launchHook = { p, block -> launched += p to block }
            crossing = CrossingRoom(testing)
            statues = StatueRoom(testing)
            combat = CombatRoom(testing, unused<NpcDeath>(), players, AiPlayerInteractions(events, players), clock)
            patience = PatienceRoom(testing)
            acrostic = AcrosticRoom(testing)
            val scripts = ScriptContext(events, CheatCommandMap(), EngineQueueCache())
            for (script in listOf(
                quest, bkf, druidic, crossing, statues, combat, patience, acrostic,
                ImprovisationRoom(testing, unused<LocRepository>(), unused<ObjRepository>()),
                LogicRoom(testing),
                TrainingGroundsScript(testing),
                SirTiffyCashien(quest, testing, unused<Shops>(), unused<ObjRepository>()),
                SirAmikVarze(bkf, SirAmikRecruitment(quest)),
            )) {
                with(script) { scripts.startup() }
            }
            for (p in listOf(player, second)) {
                bkf.quest.jumpToStage(p, BlackKnightsFortressQuest.STAGE_COMPLETE)
                if (stage > 0) quest.quest.jumpToStage(p, stage)
            }
        }

        private fun newPlayer(id: Long, slot: Int) =
            Player().apply {
                this.client = this@Fixture.client
                uuid = id
                observerUUID = id
                slotId = slot
                assignUid()
                coords = CoordGrid(2996, 3374, 0)
                currentMapClock = 100
                processedMapClock = 100
                pendingSequence = EntitySeq.NULL
                pendingFaceAngle = EntityFaceAngle.NULL
                inv = Inventory(InventoryServerType(size = 28, flags = 0), arrayOfNulls(28))
                worn = Inventory(InventoryServerType(size = 14, flags = 0), arrayOfNulls(14))
                statMap.setBaseLevel("stat.hitpoints", 30.toByte())
                statMap.setCurrentLevel("stat.hitpoints", 30.toByte())
            }

        fun access(p: Player = active) = ProtectedAccess(p, coroutine, context)

        fun stage(): Int = quest.stage(player)

        fun journal(): String = quest.questLog(access())

        fun choose(vararg options: Int) {
            picks += options.toList()
        }

        /** Answers Ms. Hynn Terprett's riddle the next time she asks. */
        fun answer(riddle: LogicRoom.LogicRiddle, value: Int) {
            if (riddle == LogicRoom.LogicRiddle.FINGERS || riddle == LogicRoom.LogicRiddle.DAUGHTER) counts += value else picks += value
        }

        fun enter(order: List<TestRoom>) {
            RecruitmentState.setOrder(active, order)
            dispatch { with(testing) { beginTesting() } }
        }

        fun give(obj: String, count: Int = 1) {
            val p = active
            val type = checkNotNull(ServerCacheManager.getItem(obj.asRSCM()))
            if (type.stackable) {
                val slot = p.inv.indexOfFirst { it?.id == obj.asRSCM() }
                if (slot >= 0) {
                    p.inv[slot] = InvObj(obj, checkNotNull(p.inv[slot]).count + count)
                    return
                }
                p.inv[p.inv.indexOfFirst { it == null }] = InvObj(obj, count)
                return
            }
            repeat(count) { p.inv[p.inv.indexOfFirst { it == null }] = InvObj(obj, 1) }
        }

        fun drop(obj: String) {
            val slot = active.inv.indexOfFirst { it?.id == obj.asRSCM() }
            if (slot >= 0) active.inv[slot] = null
        }

        fun count(obj: String): Int = active.inv.count(obj)

        fun stack(obj: String): Int = active.inv.filter { it?.id == obj.asRSCM() }.sumOf { it?.count ?: 0 }

        fun said(text: String): Boolean = output().contains(text)

        fun talk(type: String, at: CoordGrid = active.coords.translateX(1)) {
            val npc = Npc(type, at)
            npcRepo.add(npc, Int.MAX_VALUE)
            publish(NpcEvents.Op1(npc))
            if (npc.isSlotAssigned) npcRepo.del(npc, Int.MAX_VALUE)
        }

        fun talkObserver(room: TestRoom) {
            publish(NpcEvents.Op1(checkNotNull(grounds.observer(active, room))))
        }

        fun carry(cargo: Cargo) {
            val state = crossing.load(active) ?: return
            val east = state.player == Bank.EAST
            val world =
                when (cargo) {
                    Cargo.FOX -> if (east) CoordGrid(2485, 4974, 0) else CoordGrid(2475, 4970, 0)
                    Cargo.CHICKEN -> if (east) CoordGrid(2487, 4974, 0) else CoordGrid(2473, 4970, 0)
                    Cargo.GRAIN -> if (east) CoordGrid(2486, 4974, 0) else CoordGrid(2474, 4970, 0)
                }
            val base =
                when (cargo) {
                    Cargo.FOX -> if (east) "loc.rd_room2_fox_multi" else "loc.rd_room2_fox_multi_right"
                    Cargo.CHICKEN -> if (east) "loc.rd_room2_chicken_multi" else "loc.rd_room2_chicken_multi_right"
                    Cargo.GRAIN -> if (east) "loc.rd_room2_grain_multi" else "loc.rd_room2_grain_multi_right"
                }
            val event = LocInteractions(BoundValidator(collision), events).opTrigger(active, bound(base, world), InteractionOp.Op1) ?: return
            publish(event)
        }

        fun putDown(cargo: Cargo) {
            val slot = CrossingRoom.wearSlot(cargo)
            val obj = active.worn[slot] ?: return
            publish(WornObjEvents.Op1(slot, obj))
        }

        fun crossBridge() {
            val east = crossing.load(active)?.player == Bank.EAST
            loc(if (east) CrossingRoom.BRIDGE_EAST else CrossingRoom.BRIDGE_WEST, CoordGrid(if (east) 2483 else 2477, 4972, 0))
        }

        fun touchStatue(statue: Int) {
            val multiloc = StatueRoom.MULTILOCS.first { name ->
                val transforms = checkNotNull(ServerCacheManager.getObject(name.asRSCM(RSCMType.LOC))).transforms!!
                transforms[active.rdStatueLayout] == statue
            }
            val index = StatueRoom.MULTILOCS.indexOf(multiloc)
            val tile = STATUE_TILES[index]
            val event = LocInteractions(BoundValidator(collision), events).opTrigger(active, bound(multiloc, tile), InteractionOp.Op1) ?: return
            publish(event)
        }

        fun dial(word: String) {
            for ((wheel, letter) in word.withIndex()) {
                repeat(letter - 'A') { dispatch(closeModals = false) { with(acrostic) { turnWheel(wheel, 1) } } }
            }
        }

        fun loc(symbol: String = "", at: CoordGrid = CoordGrid.ZERO, room: String? = null) {
            val name = room ?: symbol
            val event = LocInteractions(BoundValidator(collision), events).opTrigger(active, bound(name, at), InteractionOp.Op1)
            publish(checkNotNull(event) { "No op1 handler for $name" })
        }

        fun locU(symbol: String, at: CoordGrid, obj: String) {
            val loc = bound(symbol, at)
            val locType = checkNotNull(ServerCacheManager.getObject(symbol.asRSCM()))
            val objType = checkNotNull(ServerCacheManager.getItem(obj.asRSCM()))
            val slot = active.inv.indexOfFirst { it?.id == obj.asRSCM() }
            if (slot < 0) fail<Unit>("no $obj to use")
            val event = with(locU) { access().opTrigger(loc, loc, locType, objType, slot) }
            publish(checkNotNull(event) { "No $obj handler for $symbol" })
        }

        fun use(first: String, second: String) {
            val a = active.inv.indexOfFirst { it?.id == first.asRSCM() }
            val b = active.inv.indexOfFirst { it?.id == second.asRSCM() }
            assertTrue(a >= 0 && b >= 0, "holding $first and $second")
            val typeA = checkNotNull(ServerCacheManager.getItem(first.asRSCM()))
            val typeB = checkNotNull(ServerCacheManager.getItem(second.asRSCM()))
            publish(HeldUDefaultEvents.Type(typeA, a, typeB, b))
        }

        fun asSecond(block: () -> Unit) {
            active = second
            try {
                block()
            } finally {
                active = player
            }
        }

        fun runLaunched() {
            while (launched.isNotEmpty()) {
                val (p, block) = launched.removeFirst()
                val previous = active
                active = p
                try {
                    dispatch(closeModals = false, block = block)
                } finally {
                    active = previous
                }
            }
        }

        private fun bound(symbol: String, coords: CoordGrid): BoundLocInfo {
            val type = checkNotNull(ServerCacheManager.getObject(symbol.asRSCM()))
            return BoundLocInfo(LocInfo(0, coords, LocEntity(type.id, 10, 0)), type)
        }

        private fun publish(event: SuspendEvent<ProtectedAccess>) {
            dispatch { events.publish(this, event) }
        }

        fun dispatch(closeModals: Boolean = true, block: suspend ProtectedAccess.() -> Unit) {
            val p = active
            if (closeModals) p.clearPendingAction(events)
            result = null
            p.activeCoroutine = coroutine
            val access = access(p)
            val start: suspend () -> Unit = { access.block() }
            start.startCoroutine(
                object : Continuation<Unit> {
                    override val context = EmptyCoroutineContext

                    override fun resumeWith(result: Result<Unit>) {
                        this@Fixture.result = result
                    }
                },
            )
            result?.getOrThrow()
            repeat(600) {
                if (coroutine.isIdle) {
                    picks.clear()
                    counts.clear()
                    p.clearPendingAction(events)
                    return
                }
                step(p)
                result?.getOrThrow()
            }
            fail<Unit>("Interaction did not finish: ${output()}")
        }

        private fun step(p: Player) {
            if (coroutine.isAwaiting(ResumePCountDialogInput::class)) {
                coroutine.resumeWith(ResumePCountDialogInput(counts.removeFirstOrNull() ?: 0))
                return
            }
            if (coroutine.isAwaiting(ResumePauseButtonInput::class)) {
                val parent =
                    listOf("chat_left", "chat_right", "messagebox", "chatmenu", "objectbox", "objectbox_double")
                        .firstOrNull { p.ui.containsModal("interface.$it") }
                        ?: error("Unknown dialogue: ${output()}")
                val input =
                    when (parent) {
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
        val AREAS =
            listOf(
                intArrayOf(2430, 4928, 2495, 4991),
                intArrayOf(2960, 3330, 3010, 3385),
            )

        /** The statue multilocs' tiles, in `loc.rd_statue_multi_N` order. */
        val STATUE_TILES =
            listOf(
                CoordGrid(2450, 4982, 0), CoordGrid(2450, 4979, 0), CoordGrid(2450, 4976, 0),
                CoordGrid(2452, 4982, 0), CoordGrid(2452, 4979, 0), CoordGrid(2452, 4976, 0),
                CoordGrid(2454, 4982, 0), CoordGrid(2454, 4979, 0), CoordGrid(2454, 4976, 0),
                CoordGrid(2456, 4982, 0), CoordGrid(2456, 4979, 0), CoordGrid(2456, 4976, 0),
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

        private inline fun <reified T> unused(): T {
            val field = Unsafe::class.java.getDeclaredField("theUnsafe").apply { isAccessible = true }
            return (field.get(null) as Unsafe).allocateInstance(T::class.java) as T
        }
    }
}
