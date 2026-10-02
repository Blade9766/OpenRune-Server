@file:OptIn(InternalApi::class)

package org.rsmod.content.quest.area.tirannwn.templeoflight

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.InventoryServerType
import dev.openrune.types.varp.VarpLifetime
import dev.openrune.types.varp.baseVar
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
import org.rsmod.api.player.events.interact.HeldObjEvents
import org.rsmod.api.player.events.interact.NpcEvents
import org.rsmod.api.player.hit.modifier.NoopPlayerHitModifier
import org.rsmod.api.player.hit.processor.DamageOnlyPlayerHitProcessor
import org.rsmod.api.player.hook.PlayerTeleportValidateHook
import org.rsmod.api.player.hook.PlayerTeleportValidator
import org.rsmod.api.player.input.ResumePauseButtonInput
import org.rsmod.api.player.interact.LocInteractions
import org.rsmod.api.player.interact.LocUInteractions
import org.rsmod.api.player.interact.NpcInteractions
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.protect.clearPendingAction
import org.rsmod.api.player.stat.stat
import org.rsmod.api.random.DefaultGameRandom
import org.rsmod.api.registry.npc.NpcRegistry
import org.rsmod.api.registry.zone.ZoneUpdateMap
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.route.BoundValidator
import org.rsmod.content.quest.area.ardougne.QuestDoors
import org.rsmod.content.quest.area.tirannwn.mourningsend.ArianwynBriefing
import org.rsmod.content.quest.area.tirannwn.mourningsend.MournerHideout
import org.rsmod.content.quest.area.tirannwn.mourningsend.MourningsEndQuest
import org.rsmod.content.quest.area.tirannwn.mourningsend.npcs.Essyllt
import org.rsmod.content.quest.area.tirannwn.mourningsend.npcs.LletyaArianwyn
import org.rsmod.content.quest.area.tirannwn.templeoflight.MourningsEndPart2Quest.Companion.CHARGED_CRYSTAL
import org.rsmod.content.quest.area.tirannwn.templeoflight.MourningsEndPart2Quest.Companion.CHISEL
import org.rsmod.content.quest.area.tirannwn.templeoflight.MourningsEndPart2Quest.Companion.COLOUR_WHEEL
import org.rsmod.content.quest.area.tirannwn.templeoflight.MourningsEndPart2Quest.Companion.DEATH_TALISMAN
import org.rsmod.content.quest.area.tirannwn.templeoflight.MourningsEndPart2Quest.Companion.ITEM_LIST
import org.rsmod.content.quest.area.tirannwn.templeoflight.MourningsEndPart2Quest.Companion.JOURNAL
import org.rsmod.content.quest.area.tirannwn.templeoflight.MourningsEndPart2Quest.Companion.NEW_CRYSTAL
import org.rsmod.content.quest.area.tirannwn.templeoflight.MourningsEndPart2Quest.Companion.NEW_KEY
import org.rsmod.content.quest.area.tirannwn.templeoflight.MourningsEndPart2Quest.Companion.NOTES
import org.rsmod.content.quest.area.tirannwn.templeoflight.MourningsEndPart2Quest.Companion.ROPE
import org.rsmod.content.quest.area.tirannwn.templeoflight.MourningsEndPart2Quest.Companion.SAMPLE
import org.rsmod.content.quest.area.tirannwn.templeoflight.MourningsEndPart2Quest.Companion.STAGE_COMPLETE
import org.rsmod.content.quest.area.tirannwn.templeoflight.MourningsEndPart2Quest.Companion.STAGE_CRYSTAL
import org.rsmod.content.quest.area.tirannwn.templeoflight.MourningsEndPart2Quest.Companion.STAGE_FOUND
import org.rsmod.content.quest.area.tirannwn.templeoflight.MourningsEndPart2Quest.Companion.STAGE_KEY
import org.rsmod.content.quest.area.tirannwn.templeoflight.MourningsEndPart2Quest.Companion.STAGE_RESTORED
import org.rsmod.content.quest.area.tirannwn.templeoflight.MourningsEndPart2Quest.Companion.STAGE_SAMPLE
import org.rsmod.content.quest.area.tirannwn.templeoflight.MourningsEndPart2Quest.Companion.STAGE_STARTED
import org.rsmod.content.quest.area.tirannwn.templeoflight.MourningsEndPart2Quest.Companion.TRINKET
import org.rsmod.content.quest.area.tirannwn.templeoflight.npcs.ArianwynTempleTalk
import org.rsmod.content.quest.area.tirannwn.templeoflight.npcs.EssylltTempleTalk
import org.rsmod.content.quest.area.tirannwn.templeoflight.npcs.Thorgel
import org.rsmod.content.quest.manager.QUEST_STAGE_MAP_ATTR
import org.rsmod.content.quest.manager.QuestRequirementMode
import org.rsmod.content.quest.manager.QuestRequirementPolicy
import org.rsmod.content.quest.manager.QuestRequirements
import org.rsmod.content.travel.jewellery.JewelleryDestination
import org.rsmod.content.travel.jewellery.JewelleryRequirements
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
 * Drives Mourning's End Part II's real scripts through the event bus: the start and its
 * requirement, Essyllt's key and the mines door, the dig team, the sample and Eluned's crystal,
 * the pillars (every action, every check, repeated clicks), the dispenser and the chests, the
 * doors, Thorgel's list, the altar and the restoration, the rewards, logging out and back in,
 * two players with different arrangements, and what completion unlocks.
 */
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class MourningsEndPart2InteractionTest {

    @Test fun `Arianwyn only offers the quest once Part I is really done, and asks first`() {
        respectingProgress {
            val f = Fixture()
            f.part1.quest.jumpToStage(f.player, MourningsEndQuest.STAGE_REVEALED)
            f.talk(ARIANWYN)
            assertEquals(0, f.stage(), "Part I's debrief comes first")
            f.part1.quest.jumpToStage(f.player, MourningsEndQuest.STAGE_COMPLETE)
            f.choose(2)
            f.talk(ARIANWYN)
            assertEquals(0, f.stage(), "declining leaves it unstarted")
            assertTrue(f.said("Every moment we waste is a risk"))
            f.choose(1)
            f.talk(ARIANWYN)
            assertEquals(STAGE_STARTED, f.stage())
            assertTrue(f.journal().contains("Headquarters"))
        }
    }

    @Test fun `Essyllt hands over the new key once, and replaces it or the desk does`() {
        val f = Fixture(STAGE_STARTED)
        f.fill()
        f.talk(ESSYLLT)
        assertEquals(STAGE_STARTED, f.stage(), "no room, no key")
        f.empty()
        f.talk(ESSYLLT)
        assertEquals(STAGE_KEY, f.stage())
        assertEquals(1, f.count(NEW_KEY))
        f.talk(ESSYLLT)
        assertEquals(1, f.count(NEW_KEY), "owning one, he gives no other")
        f.drop(NEW_KEY)
        f.talk(ESSYLLT)
        assertEquals(1, f.count(NEW_KEY), "a lost key is replaced")
        f.drop(NEW_KEY)
        f.locOp("loc.mourning_office_table", CoordGrid(2043, 4629, 0))
        assertEquals(1, f.count(NEW_KEY), "or found on his desk")
        f.locOp("loc.mourning_office_table", CoordGrid(2043, 4629, 0))
        assertEquals(1, f.count(NEW_KEY), "only one")
    }

    @Test fun `the door into the mines wants the new key from the basement side`() {
        val f = Fixture(STAGE_KEY)
        f.player.coords = CoordGrid(2035, 4636, 0)
        f.locOp(MournerHideout.BACK_ROOM_DOOR, CoordGrid(2034, 4636, 0))
        assertTrue(f.said("The door is locked."))
    }

    @Test fun `finding the dig team, Edern's journal and the guard's colour wheel`() {
        val f = Fixture(STAGE_KEY)
        f.player.coords = CoordGrid(1925, 4640, 0)
        assertTrue(TempleDiscoveries.atDigSite(f.player.coords))
        f.dispatch { with(f.discoveries) { findDigTeam() } }
        assertEquals(STAGE_FOUND, f.stage())
        f.dispatch { with(f.discoveries) { findDigTeam() } }
        assertEquals(STAGE_FOUND, f.stage(), "only once")
        f.locOp(TempleDiscoveries.EDERN, CoordGrid(1925, 4642, 0))
        assertEquals(1, f.count(JOURNAL))
        f.locOp(TempleDiscoveries.EDERN, CoordGrid(1925, 4642, 0))
        assertEquals(1, f.count(JOURNAL), "one journal")
        f.fill()
        f.locOp(TempleDiscoveries.DISPENSER_GUARD, CoordGrid(1910, 4635, 1))
        assertTrue(f.said("you don't have enough room"))
        f.empty()
        f.locOp(TempleDiscoveries.DISPENSER_GUARD, CoordGrid(1910, 4635, 1))
        assertEquals(1, f.count(COLOUR_WHEEL))
        assertEquals(1, f.count(NOTES))
    }

    @Test fun `the sample comes off the black crystal with a chisel, once and only while needed`() {
        val f = Fixture(STAGE_FOUND)
        f.locU(TempleDiscoveries.BLACK_CRYSTAL, BLACK_CRYSTAL_TILE, CHISEL_FOR_TEST)
        assertEquals(0, f.count(SAMPLE), "no chisel, no sample")
        f.give(CHISEL)
        f.locU(TempleDiscoveries.BLACK_CRYSTAL, BLACK_CRYSTAL_TILE, CHISEL)
        assertEquals(1, f.count(SAMPLE))
        f.locU(TempleDiscoveries.BLACK_CRYSTAL, BLACK_CRYSTAL_TILE, CHISEL)
        assertEquals(1, f.count(SAMPLE), "one sample")
        f.drop(SAMPLE)
        f.locU(TempleDiscoveries.BLACK_CRYSTAL, BLACK_CRYSTAL_TILE, CHISEL)
        assertEquals(1, f.count(SAMPLE), "a lost sample is chipped again")
    }

    @Test fun `Arianwyn has Eluned turn the sample into the new crystal, in one swap`() {
        val f = Fixture(STAGE_FOUND)
        f.talk(ARIANWYN)
        assertEquals(STAGE_SAMPLE, f.stage(), "he asks for a sample")
        f.talk(ARIANWYN)
        assertEquals(STAGE_SAMPLE, f.stage(), "and waits for one")
        f.give(SAMPLE)
        f.talk(ARIANWYN)
        assertEquals(STAGE_CRYSTAL, f.stage())
        assertEquals(0, f.count(SAMPLE))
        assertEquals(1, f.count(NEW_CRYSTAL))
        f.talk(ARIANWYN)
        assertEquals(1, f.count(NEW_CRYSTAL), "talking again makes no second crystal")
        f.drop(NEW_CRYSTAL)
        f.talk(ARIANWYN)
        assertEquals(1, f.count(NEW_CRYSTAL), "a lost crystal is replaced")
        f.drop(NEW_CRYSTAL)
        f.give(CHARGED_CRYSTAL)
        f.talk(ARIANWYN)
        assertEquals(0, f.count(NEW_CRYSTAL), "not while the charged one is kept")
        val sampleFirst = Fixture(STAGE_FOUND)
        sampleFirst.give(SAMPLE)
        sampleFirst.talk(ARIANWYN)
        assertEquals(STAGE_CRYSTAL, sampleFirst.stage(), "a sample brought along goes straight to Eluned")
    }

    @Test fun `a mirror goes in facing the chosen way, turns, and comes back out`() {
        val f = Fixture(STAGE_CRYSTAL)
        f.give(MIRROR, 2)
        f.beside("2_9")
        f.choose(1)
        f.useOnPillar("2_9", MIRROR)
        assertEquals(PillarContent.Mirror(Facing.NORTH), f.state()["2_9"])
        assertEquals(1, f.count(MIRROR))
        assertEquals(7, f.vars("varbit.mourning_light_temple_2_7_9"), "the beam is drawn white to the next pillar")
        f.useOnPillar("2_9", MIRROR)
        assertEquals(1, f.count(MIRROR), "an occupied pillar takes nothing")
        assertTrue(f.said("There is already something in this pillar."))
        f.choose(1, 5, 1)
        f.searchPillar("2_9")
        assertEquals(PillarContent.Mirror(Facing.UP), f.state()["2_9"], "turned up through the second menu")
        f.fill()
        f.choose(2)
        f.searchPillar("2_9")
        assertEquals(PillarContent.Mirror(Facing.UP), f.state()["2_9"], "no room to take it back")
        f.empty()
        f.choose(2)
        f.searchPillar("2_9")
        assertEquals(PillarContent.Empty, f.state()["2_9"])
        assertEquals(2, f.count(MIRROR), "nothing gained or lost")
        assertEquals(0, f.vars("varbit.mourning_light_temple_2_7_9"))
    }

    @Test fun `a pillar refuses what does not belong in it`() {
        val f = Fixture(STAGE_CRYSTAL)
        f.give(TempleItem.FRACTURED_HORIZONTAL.obj)
        f.give(TempleItem.YELLOW.obj)
        f.beside("2_9")
        f.useOnPillar("2_9", TempleItem.FRACTURED_HORIZONTAL.obj)
        assertTrue(f.said("doesn't seem to fit right"))
        assertEquals(1, f.count(TempleItem.FRACTURED_HORIZONTAL.obj))
        f.beside("2_11")
        f.useOnPillar("2_11", TempleItem.FRACTURED_HORIZONTAL.obj)
        assertEquals(PillarContent.Crystal(TempleItem.FRACTURED_HORIZONTAL), f.state()["2_11"])
        assertTrue(f.said("clear side facing north"))
        f.beside("2_10")
        f.useOnPillar("2_10", TempleItem.YELLOW.obj)
        assertTrue(f.said("fused into this pillar"))
        f.searchPillar("2_10")
        assertTrue(f.said("There is a green crystal fused into the pillar."))
        f.beside("1_b")
        f.useOnPillar("1_b", TempleItem.YELLOW.obj)
        assertTrue(f.said("already a mirror fixed"))
        f.choose(1, 4)
        f.searchPillar("1_b")
        assertEquals(PillarContent.Mirror(Facing.WEST), f.state()["1_b"], "the preset mirror turns")
        f.choose(2)
        f.searchPillar("1_b")
        assertEquals(PillarContent.Mirror(Facing.WEST), f.state()["1_b"], "but offers no way to take it")
        f.player.coords = CoordGrid(1890, 4639, 0)
        f.choose(1, 2)
        f.searchPillar("1_b")
        assertEquals(PillarContent.Mirror(Facing.WEST), f.state()["1_b"], "nothing changes from across the room")
    }

    @Test fun `nobody may change the light before the quest or once the safeguards are restored`() {
        val f = Fixture(0)
        f.give(MIRROR)
        f.beside("2_9")
        f.useOnPillar("2_9", MIRROR)
        assertEquals(PillarContent.Empty, f.state()["2_9"])
        f.jump(STAGE_RESTORED)
        f.player.safeguardsRestored = 1
        f.useOnPillar("2_9", MIRROR)
        assertTrue(f.said("safeguards are working fine"))
        assertEquals(1, f.count(MIRROR))
    }

    @Test fun `the dispenser resets the puzzle, collects, and never makes more than the player is owed`() {
        val f = Fixture(STAGE_CRYSTAL)
        f.beside(CrystalDispenser.LEVER, CoordGrid(1913, 4639, 1))
        f.choose(1)
        f.locOp(CrystalDispenser.LEVER, LEVER_TILE)
        assertEquals(4, f.puzzle.tray(f.player)[TempleItem.MIRROR], "the starting set")
        assertEquals(0, f.count(MIRROR), "a reset only fills the tray")
        f.locOp(CrystalDispenser.LEVER, LEVER_TILE)
        assertEquals(4, f.count(MIRROR))
        assertEquals(1, f.count(TempleItem.YELLOW.obj))
        assertTrue(f.puzzle.tray(f.player).values.all { it == 0 })
        f.place("2_9", PillarContent.Mirror(Facing.NORTH), MIRROR)
        f.place("2_11", PillarContent.Crystal(TempleItem.YELLOW), TempleItem.YELLOW.obj)
        f.drop(MIRROR)
        assertEquals(2, f.count(MIRROR), "two held, one placed, one destroyed")
        f.choose(1)
        f.locOp(CrystalDispenser.LEVER, LEVER_TILE)
        assertEquals(PuzzleState(), f.state())
        assertEquals(2, f.puzzle.tray(f.player)[TempleItem.MIRROR], "the placed one and the lost one")
        assertEquals(1, f.puzzle.tray(f.player)[TempleItem.YELLOW])
        f.fill(26)
        f.locOp(CrystalDispenser.LEVER, LEVER_TILE)
        assertEquals(4, f.count(MIRROR), "as much as fits")
        assertEquals(1, f.puzzle.tray(f.player)[TempleItem.YELLOW], "the rest waits")
        f.empty()
        f.locOp(CrystalDispenser.LEVER, LEVER_TILE)
        assertEquals(1, f.count(TempleItem.YELLOW.obj))
        f.choose(1)
        f.locOp(CrystalDispenser.LEVER, LEVER_TILE)
        assertTrue(f.puzzle.tray(f.player).values.all { it == 0 }, "a reset owes nothing to a player who holds everything")
        assertEquals(4, f.count(MIRROR))
        f.bank(TempleItem.YELLOW.obj)
        f.choose(1)
        f.locOp(CrystalDispenser.LEVER, LEVER_TILE)
        assertEquals(0, f.puzzle.tray(f.player)[TempleItem.YELLOW], "a banked crystal still counts as held")
    }

    @Test fun `each chest gives its contents once, and only when they fit`() {
        val f = Fixture(STAGE_CRYSTAL)
        val chest = TempleChest.BLUE_DOOR
        f.fill(27)
        f.locOp(chest.open, CoordGrid(1917, 4613, 1))
        assertEquals(0, f.count(MIRROR), "three slots needed")
        assertTrue(f.said("You need 3 free inventory spaces"))
        f.empty()
        f.locOp(chest.open, CoordGrid(1917, 4613, 1))
        assertEquals(2, f.count(MIRROR))
        assertEquals(1, f.count(TempleItem.CYAN.obj))
        f.locOp(chest.open, CoordGrid(1917, 4613, 1))
        assertEquals(2, f.count(MIRROR), "only once")
        assertTrue(f.said("The chest is empty."))
        assertEquals(6, f.puzzle.owed(f.player)[TempleItem.MIRROR])
    }

    @Test fun `the reference solutions, built through the pillars, open their doors and the door lets the player through`() {
        val f = Fixture(STAGE_CRYSTAL)
        for ((pillar, content) in TempleSolutions.chest1.placements) {
            val item = if (content is PillarContent.Crystal) content.item.obj else MIRROR
            f.place(pillar, content, item)
        }
        assertEquals(1, f.vars("varbit.mourning_door_2_16_west"))
        f.player.coords = CoordGrid(1911, 4613, 1)
        f.locOp("loc.mourning_door_2_16_west", CoordGrid(1912, 4613, 1))
        assertEquals(CoordGrid(1913, 4613, 1), f.player.coords, "through the blue door")
        f.player.coords = CoordGrid(1915, 4615, 1)
        f.locOp("loc.mourning_door_2_16_north", CoordGrid(1915, 4616, 1))
        assertEquals(CoordGrid(1915, 4615, 1), f.player.coords, "the unlit door stays shut")
        for (section in TempleSolutions.sections) {
            val g = Fixture(STAGE_CRYSTAL)
            g.commit(section.state)
            for (door in TempleGeometry.doors) {
                assertEquals(if (door.id in section.opens) 1 else 0, g.vars(door.varbit), "${section.name}: ${door.id}")
            }
        }
    }

    @Test fun `stepping through the black door the first time meets Thorgel and writes a stable list`() {
        val f = Fixture(STAGE_CRYSTAL)
        f.commit(TempleSolutions.deathAltar.state)
        f.player.coords = CoordGrid(1866, 4639, 0)
        f.locOp("loc.mourning_door_1_c", CoordGrid(1865, 4639, 0))
        assertEquals(CoordGrid(1864, 4639, 0), f.player.coords)
        assertEquals(1, f.player.metThorgel)
        assertEquals(1, f.player.thorgelVisible)
        assertEquals(1, f.count(ITEM_LIST))
        assertEquals(ThorgelList.TASK_OPEN, f.player.thorgelTask)
        val list = ThorgelList.entries(f.player)!!.map { it.label }
        assertEquals(50, list.size)
        val reloaded = f.saveAndReload()
        assertEquals(list, ThorgelList.entries(reloaded)!!.map { it.label }, "logging out never rerolls the list")
        f.held(1, ITEM_LIST)
        assertTrue(f.said(ThorgelList.GUARANTEED.first().second))
    }

    @Test fun `Thorgel takes only what is still on the list, a part at a time, and pays once`() {
        val f = Fixture(STAGE_CRYSTAL)
        f.meetThorgel()
        val entries = ThorgelList.entries(f.player)!!
        f.give(entries[0].objs[0])
        f.give(entries[1].objs[0])
        f.give("obj.coins", 5)
        f.choose(1, 1)
        f.talk(Thorgel.THORGEL)
        assertTrue(ThorgelList.delivered(f.player, 0) && ThorgelList.delivered(f.player, 1))
        assertEquals(0, f.count(entries[0].objs[0]))
        assertEquals(5, f.stack("obj.coins"), "nothing off the list is taken")
        f.give(entries[0].objs[0])
        f.choose(1, 1)
        f.talk(Thorgel.THORGEL)
        assertEquals(1, f.count(entries[0].objs[0]), "a delivered item is not taken twice")
        f.drop(entries[0].objs[0])
        assertFalse(ThorgelList.text(f.player).contains(entries[0].label), "the list shows only what is left")
        f.held(1, ITEM_LIST)
        assertTrue(f.said(entries[2].label), "the paper shows what is left")
        f.give(entries[2].objs[0])
        f.give(entries[3].objs[0])
        f.choose(1, 2, 1, 2)
        f.talk(Thorgel.THORGEL)
        assertTrue(ThorgelList.delivered(f.player, 2), "one at a time: the first given")
        assertFalse(ThorgelList.delivered(f.player, 3), "the second kept")
        val rest = entries.drop(3)
        for (batch in rest.dropLast(1).chunked(20)) {
            for (entry in batch) if (!f.player.inv.contains(entry.objs[0])) f.give(entry.objs[0])
            f.choose(1, 1)
            f.talk(Thorgel.THORGEL)
        }
        assertEquals(1, ThorgelList.outstanding(f.player).size)
        assertEquals(0, f.count(DEATH_TALISMAN), "one item still missing")
        f.give(rest.last().objs[0])
        f.fillUp()
        f.choose(1, 1)
        f.talk(Thorgel.THORGEL)
        assertEquals(ThorgelList.TASK_BETWEEN, f.player.thorgelTask)
        assertEquals(1, f.count(DEATH_TALISMAN), "the talisman with the last item")
        f.talk(Thorgel.THORGEL)
        assertEquals(1, f.count(DEATH_TALISMAN), "and once")
    }

    @Test fun `a player who can already get into the altar is not given a list`() {
        val ways =
            listOf<(Fixture) -> Unit>(
                { it.give(DEATH_TALISMAN) },
                { it.give("obj.catalytic_talisman") },
                { it.wear("obj.tiara_death") },
                { it.wear("obj.tiara_catalytic") },
                { it.wear("obj.skillcape_runecrafting") },
            )
        for (way in ways) {
            val f = Fixture(STAGE_CRYSTAL)
            way(f)
            f.meetThorgel()
            assertEquals(ThorgelList.TASK_NONE, f.player.thorgelTask)
            assertEquals(0, f.count(ITEM_LIST))
        }
    }

    @Test fun `the altar charges the new crystal without any Runecraft level, and the black crystal takes it`() {
        val f = Fixture(STAGE_CRYSTAL)
        f.setStat("stat.runecrafting", 1)
        f.give(NEW_CRYSTAL)
        f.locU(TempleDiscoveries.DEATH_ALTAR, DEATH_ALTAR_TILE, NEW_CRYSTAL)
        assertEquals(1, f.count(CHARGED_CRYSTAL))
        assertEquals(0, f.count(NEW_CRYSTAL))
        f.locU(TempleDiscoveries.BLACK_CRYSTAL, BLACK_CRYSTAL_TILE, CHARGED_CRYSTAL)
        assertEquals(STAGE_RESTORED, f.stage())
        assertEquals(1, f.player.safeguardsRestored)
        assertEquals(0, f.count(CHARGED_CRYSTAL), "the crystal stays with the others")
        assertTrue(f.part2.guarded(f.player))
        val other = Fixture(STAGE_CRYSTAL)
        assertEquals(0, other.player.safeguardsRestored, "another player's black crystal is still dead")
        val early = Fixture(STAGE_SAMPLE)
        early.give(CHARGED_CRYSTAL)
        early.locU(TempleDiscoveries.BLACK_CRYSTAL, BLACK_CRYSTAL_TILE, CHARGED_CRYSTAL)
        assertEquals(STAGE_SAMPLE, early.stage())
        assertEquals(1, early.count(CHARGED_CRYSTAL))
    }

    @Test fun `Arianwyn completes the quest once, with 60,000 Agility experience and the trinket`() {
        val f = Fixture(STAGE_RESTORED)
        f.fill()
        f.talk(ARIANWYN)
        assertEquals(STAGE_RESTORED, f.stage(), "no room for the trinket")
        f.empty()
        val before = f.player.statMap.getXP("stat.agility")
        val points = f.player.vars["varp.qp"]
        f.talk(ARIANWYN)
        assertEquals(STAGE_COMPLETE, f.stage())
        assertEquals(1, f.count(TRINKET))
        assertEquals(points + 2, f.player.vars["varp.qp"])
        val gained = f.player.statMap.getXP("stat.agility") - before
        assertEquals((MourningsEndPart2Quest.AGILITY_XP * f.player.xpRate * f.player.globalXpRate).toInt(), gained, "60,000 at the player's rate")
        f.talk(ARIANWYN)
        assertEquals(points + 2, f.player.vars["varp.qp"], "once")
        assertEquals(1, f.count(TRINKET))
        f.drop(TRINKET)
        f.talk(ARIANWYN)
        assertEquals(1, f.count(TRINKET), "a lost trinket is replaced")
    }

    @Test fun `after the quest the trinket lets the player into the temple and calms the shadows`() {
        val f = Fixture(STAGE_COMPLETE)
        f.player.safeguardsRestored = 1
        f.player.coords = CoordGrid(1917, 4639, 0)
        f.dispatch { with(f.obstacles) { enterTemple() } }
        assertEquals(CoordGrid(1917, 4639, 0), f.player.coords)
        assertTrue(f.said("A strange force blocks your path."))
        f.give(TRINKET)
        f.dispatch { with(f.obstacles) { enterTemple() } }
        assertEquals(CoordGrid(1916, 4639, 0), f.player.coords)
        TempleShadows.syncPeace(f.player, f.part2)
        assertEquals(1, f.player.vars[TempleShadows.PEACE_VARP])
        f.drop(TRINKET)
        TempleShadows.syncPeace(f.player, f.part2)
        assertEquals(0, f.player.vars[TempleShadows.PEACE_VARP])
        val during = Fixture(STAGE_KEY)
        during.player.coords = CoordGrid(1917, 4639, 0)
        during.dispatch { with(during.obstacles) { enterTemple() } }
        assertEquals(CoordGrid(1916, 4639, 0), during.player.coords, "open to everyone until the safeguards return")
    }

    @Test fun `the slayer ring's Dark Beasts teleport waits for the quest`() {
        val f = Fixture(STAGE_CRYSTAL)
        val darkBeasts = JewelleryDestination(MourningsEndPart2Quest.DARK_BEASTS_TELEPORT, CoordGrid(2028, 4636, 0))
        respectingProgress {
            assertTrue(JewelleryRequirements.denial(f.player, darkBeasts)!!.contains("Mourning's End Part II"))
            f.jump(STAGE_COMPLETE)
            assertNull(JewelleryRequirements.denial(f.player, darkBeasts))
        }
    }

    @Test fun `the wall supports and the blade traps follow the Agility rolls`() {
        val f = Fixture(STAGE_CRYSTAL)
        f.setStat("stat.agility", 99)
        f.player.coords = TempleObstacles.SUPPORTS_WEST
        f.locOp(TempleObstacles.WALL_SUPPORT, CoordGrid(1902, 4612, 1))
        assertEquals(TempleObstacles.SUPPORTS_EAST, f.player.coords, "at 99 the supports never fail")
        val slip = Fixture(STAGE_CRYSTAL)
        slip.setStat("stat.agility", 1)
        slip.setStat("stat.hitpoints", 50)
        slip.player.coords = TempleObstacles.SUPPORTS_WEST
        slip.locOp(TempleObstacles.WALL_SUPPORT, CoordGrid(1902, 4612, 1))
        assertEquals(0, slip.player.coords.level, "a slip lands on the ground floor")
        assertEquals(45, slip.player.statMap.getCurrentLevel("stat.hitpoints").toInt())
        val trap = TempleObstacles.Trap.TOP_4
        val g = Fixture(STAGE_CRYSTAL)
        g.setStat("stat.agility", 99)
        g.player.coords = trap.wall
        val xp = g.player.statMap.getXP("stat.agility")
        g.dispatch { with(g.obstacles) { dodge(trap, trap.wall, trap.beyond, trap.wall.translateZ(-1)) } }
        assertEquals(trap.beyond, g.player.coords)
        assertEquals(5, g.player.statMap.getXP("stat.agility") - xp, "5 Agility experience")
        assertEquals(trap, TempleObstacles.Trap.at(trap.beyond))
        assertEquals(trap.wall, trap.other(trap.beyond))
    }

    @Test fun `the rope stays tied and the ladders land on the right floors`() {
        val f = Fixture(STAGE_CRYSTAL)
        f.player.coords = TempleObstacles.ROPE_TOP
        f.locOp(TempleObstacles.ROCK, CoordGrid(1876, 4620, 1))
        assertEquals(TempleObstacles.ROPE_TOP, f.player.coords, "no rope, no way down")
        f.give(ROPE)
        f.locU(TempleObstacles.ROCK, CoordGrid(1876, 4620, 1), ROPE)
        assertEquals(1, f.player.templeRope)
        assertEquals(0, f.count(ROPE))
        f.locOp(TempleObstacles.ROCK, CoordGrid(1876, 4620, 1))
        assertEquals(TempleObstacles.ROPE_BOTTOM, f.player.coords)
        f.locOp(TempleObstacles.ROPE_MULTI, CoordGrid(1877, 4620, 0))
        assertEquals(TempleObstacles.ROPE_TOP, f.player.coords)
        assertEquals(1, f.saveAndReload().templeRope, "the rope survives logging out")
        for (climb in TempleObstacles.CLIMBS) {
            f.player.coords = climb.coords.translateX(-1)
            f.locOp(climb.loc, climb.coords)
            assertEquals(climb.dest, f.player.coords, "${climb.loc} at ${climb.coords}")
        }
    }

    @Test fun `the arrangement survives logging out and the light is redrawn from it`() {
        val f = Fixture(STAGE_CRYSTAL)
        f.commit(TempleSolutions.chest5.state)
        val reloaded = f.saveAndReload()
        assertEquals(TempleSolutions.chest5.state.contents, TemplePuzzleStore.load(reloaded).contents)
        for (link in TempleGeometry.links) reloaded.vars.backing.remove(ServerCacheManager.getVarbit(link.varbit.asRSCM())!!.baseVar.id)
        f.part2.quest.syncState(reloaded)
        assertEquals(1, reloaded.vars["varbit.mourning_door_1_16_west"], "doors are recomputed, not trusted")
        assertEquals(3, reloaded.vars["varbit.mourning_light_temple_1_10_14"], "green")
        assertEquals(TemplePuzzleStore.SCHEMA_VERSION, reloaded.vars[TemplePuzzleStore.VERSION_VARBIT])
    }

    @Test fun `logging in draws nothing for a player who never started, and a reset clears the temple`() {
        val fresh = Fixture(0)
        fresh.part2.quest.syncState(fresh.player)
        assertEquals(0, fresh.vars("varbit.mourning_light_temple_2_9_up"), "no light written for a player who never started")
        val f = Fixture(STAGE_CRYSTAL)
        f.part2.quest.syncState(f.player)
        assertEquals(7, f.vars("varbit.mourning_light_temple_2_9_up"), "the emitter shines for a player on the quest")
        f.meetThorgel()
        f.player.templeRope = 1
        f.part2.quest.resetQuest(f.player)
        assertEquals(PuzzleState(), f.state())
        assertEquals(0, f.player.metThorgel)
        assertEquals(0, f.player.thorgelTask)
        assertEquals(0, f.player.templeRope)
        assertEquals(0, f.vars("varbit.mourning_door_1_c"))
        assertEquals(0, f.vars("varbit.mourning_light_temple_2_9_up"))
        assertEquals(0, f.vars(TemplePuzzleStore.VERSION_VARBIT))
        assertTrue(ThorgelList.DELIVERED_VARPS.all { f.player.vars[it] == 0 })
    }

    @Test fun `two players in the temple keep their own light`() {
        val f = Fixture(STAGE_CRYSTAL)
        f.commit(TempleSolutions.chest1.state)
        f.asSecond { f.commit(TempleSolutions.chest2.state) }
        assertEquals(1, f.player.vars["varbit.mourning_door_2_16_west"])
        assertEquals(0, f.player.vars["varbit.mourning_door_2_4_west"])
        assertEquals(0, f.second.vars["varbit.mourning_door_2_16_west"])
        assertEquals(1, f.second.vars["varbit.mourning_door_2_4_west"])
        f.player.coords = CoordGrid(1911, 4613, 1)
        f.locOp("loc.mourning_door_2_16_west", CoordGrid(1912, 4613, 1))
        assertEquals(CoordGrid(1913, 4613, 1), f.player.coords)
        f.asSecond {
            f.second.coords = CoordGrid(1911, 4613, 1)
            f.locOp("loc.mourning_door_2_16_west", CoordGrid(1912, 4613, 1))
        }
        assertEquals(CoordGrid(1911, 4613, 1), f.second.coords, "the second player's own light does not open it")
    }

    private class Fixture(stage: Int = 0) {
        val events = EventBus()
        private val client = RecordingClient()
        private val collision = CollisionFlagMap()
        private val npcs = NpcList()
        private val players = PlayerList()
        private val coroutine = GameCoroutine("temple-test")
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
        private val locU =
            LocUInteractions::class.java.getDeclaredConstructor(EventBus::class.java).apply { isAccessible = true }.newInstance(events)

        val player = newPlayer(7171L, 1)
        val second = newPlayer(7272L, 2)
        private var active = player

        val part1 = MourningsEndQuest()
        val part2 = MourningsEndPart2Quest(part1)
        val puzzle = TemplePuzzle(part2)
        val obstacles: TempleObstacles
        val discoveries: TempleDiscoveries

        init {
            for ((x0, z0, x1, z1) in AREAS) {
                for (level in 0..2) for (x in x0..x1 step 8) for (z in z0..z1 step 8) collision.allocateIfAbsent(x, z, level)
            }
            players[player.slotId] = player
            players[second.slotId] = second
            npcRepo = NpcRepository(clock, NpcRegistry(npcs, collision, events), npcs)
            val launcher = ProtectedAccessLauncher(unused<ProtectedAccessContextFactory>())
            obstacles = TempleObstacles(part2, puzzle, launcher)
            discoveries = TempleDiscoveries(part2, launcher)
            val thorgel = Thorgel()
            val scripts = ScriptContext(events, CheatCommandMap(), EngineQueueCache())
            for (script in listOf(
                part1, part2, thorgel, obstacles, discoveries,
                TemplePillars(puzzle), CrystalDispenser(puzzle, WorldRepository(ZoneUpdateMap()), unused<LocRepository>()),
                LightDoors(puzzle, thorgel),
                LletyaArianwyn(part1, ArianwynBriefing(part1), ArianwynTempleTalk(part2)),
                Essyllt(part1, EssylltTempleTalk(part2)),
                MournerHideout(part1, unused<QuestDoors>(), unused<LocRepository>(), part2),
            )) {
                with(script) { scripts.startup() }
            }
            for (p in listOf(player, second)) {
                part1.quest.jumpToStage(p, MourningsEndQuest.STAGE_COMPLETE)
                if (stage > 0) part2.quest.jumpToStage(p, stage)
            }
        }

        private fun newPlayer(id: Long, slot: Int) =
            Player().apply {
                this.client = this@Fixture.client
                uuid = id
                observerUUID = id
                slotId = slot
                assignUid()
                coords = CoordGrid(2353, 3171, 0)
                currentMapClock = 100
                processedMapClock = 100
                pendingSequence = EntitySeq.NULL
                pendingFaceAngle = EntityFaceAngle.NULL
                inv = Inventory(InventoryServerType(size = 28, flags = 0), arrayOfNulls(28))
                worn = Inventory(InventoryServerType(size = 14, flags = 0), arrayOfNulls(14))
                for ((stat, level) in listOf("stat.hitpoints" to 70, "stat.agility" to 60, "stat.runecrafting" to 1)) {
                    statMap.setBaseLevel(stat, level.toByte())
                    statMap.setCurrentLevel(stat, level.toByte())
                }
            }

        fun access(p: Player = active) = ProtectedAccess(p, coroutine, context)

        fun stage(): Int = part2.stage(player)

        fun jump(stage: Int) = part2.quest.jumpToStage(player, stage)

        fun journal(): String = part2.questLog(access())

        fun state(): PuzzleState = TemplePuzzleStore.load(active)

        fun commit(state: PuzzleState) {
            puzzle.commit(active, state)
        }

        fun vars(varbit: String): Int = active.vars[varbit]

        fun choose(vararg options: Int) {
            picks += options.toList()
        }

        fun setStat(stat: String, level: Int) {
            player.statMap.setBaseLevel(stat, level.toByte())
            player.statMap.setCurrentLevel(stat, level.toByte())
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

        fun wear(obj: String) {
            val type = checkNotNull(ServerCacheManager.getItem(obj.asRSCM()))
            player.worn[type.wearpos1] = InvObj(obj, 1)
        }

        private val bankInv by lazy { access().bank }

        fun bank(obj: String) {
            drop(obj)
            bankInv[bankInv.indexOfFirst { it == null }] = InvObj(obj, 1)
        }

        fun fill(upTo: Int = 28) {
            while (28 - player.inv.freeSpace() < upTo && player.inv.freeSpace() > 0) give("obj.bronze_dagger")
        }

        /** Fills every slot but leaves what is there. */
        fun fillUp() = fill()

        fun empty() {
            for (i in player.inv.indices) {
                if (player.inv[i]?.id == "obj.bronze_dagger".asRSCM()) player.inv[i] = null
            }
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

        fun held(op: Int, obj: String) {
            val slot = active.inv.indexOfFirst { it?.id == obj.asRSCM() }
            val type = checkNotNull(ServerCacheManager.getItem(obj.asRSCM()))
            dispatch {
                val held = checkNotNull(inv[slot])
                val event =
                    when (op) {
                        1 -> HeldObjEvents.Op1(slot, held, type, inv)
                        else -> HeldObjEvents.Op2(slot, held, type, inv)
                    }
                assertTrue(events.publish(this, event))
            }
        }

        fun beside(pillar: String) {
            active.coords = TempleGeometry.pillarsById.getValue(pillar).coords.translateX(-1)
        }

        fun beside(loc: String, coords: CoordGrid) {
            active.coords = coords.translateX(-1)
        }

        fun searchPillar(pillar: String) {
            val p = TempleGeometry.pillarsById.getValue(pillar)
            locOp(p.loc, p.coords)
        }

        fun useOnPillar(pillar: String, obj: String) {
            val p = TempleGeometry.pillarsById.getValue(pillar)
            locU(p.loc, p.coords, obj)
        }

        /** Puts [content] in [pillar] the way a player would, giving them [obj] first. */
        fun place(pillar: String, content: PillarContent, obj: String) {
            if (!active.inv.contains(obj)) give(obj)
            beside(pillar)
            if (content is PillarContent.Mirror) {
                val facing = content.facing
                when (facing) {
                    Facing.UP -> choose(5, 1)
                    Facing.DOWN -> choose(5, 2)
                    else -> choose(listOf(Facing.NORTH, Facing.EAST, Facing.SOUTH, Facing.WEST).indexOf(facing) + 1)
                }
                if (TempleGeometry.pillarsById.getValue(pillar).kind == PillarKind.PRESET_MIRROR) {
                    picks.addFirst(1)
                    searchPillar(pillar)
                    return
                }
            }
            useOnPillar(pillar, obj)
            assertEquals(content, state()[pillar], "placed $content in $pillar")
        }

        fun meetThorgel() {
            commit(TempleSolutions.deathAltar.state)
            player.coords = CoordGrid(1866, 4639, 0)
            locOp("loc.mourning_door_1_c", CoordGrid(1865, 4639, 0))
        }

        fun locOp(symbol: String, coords: CoordGrid, op: InteractionOp = InteractionOp.Op1) {
            val loc = bound(symbol, coords)
            val event = LocInteractions(BoundValidator(collision), events).opTrigger(active, loc, op)
            publish(checkNotNull(event) { "No $op handler for $symbol" })
        }

        fun locU(symbol: String, coords: CoordGrid, obj: String) {
            val loc = bound(symbol, coords)
            val locType = checkNotNull(ServerCacheManager.getObject(symbol.asRSCM()))
            val objType = checkNotNull(ServerCacheManager.getItem(obj.asRSCM()))
            val slot = active.inv.indexOfFirst { it?.id == obj.asRSCM() }
            if (slot < 0) return
            val event = with(locU) { access().opTrigger(loc, loc, locType, objType, slot) }
            publish(checkNotNull(event) { "No $obj handler for $symbol" })
        }

        fun asSecond(block: () -> Unit) {
            active = second
            try {
                block()
            } finally {
                active = player
            }
        }

        fun saveAndReload(): Player {
            val loaded = newPlayer(player.uuid ?: 0L, 3)
            for ((varp, value) in player.vars.backing) {
                val scope = ServerCacheManager.getVarp(varp)?.scope
                if (scope != VarpLifetime.Temp) loaded.vars.backing[varp] = value
            }
            player.attr[QUEST_STAGE_MAP_ATTR]?.let { loaded.attr[QUEST_STAGE_MAP_ATTR] = it.toMutableMap() }
            part2.quest.syncState(loaded)
            return loaded
        }

        private fun bound(symbol: String, coords: CoordGrid): BoundLocInfo {
            val type = checkNotNull(ServerCacheManager.getObject(symbol.asRSCM()))
            return BoundLocInfo(LocInfo(0, coords, LocEntity(type.id, 10, 0)), type)
        }

        private fun publish(event: SuspendEvent<ProtectedAccess>) {
            dispatch { assertTrue(events.publish(this, event)) }
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
                    return
                }
                step(p)
                result?.getOrThrow()
            }
            fail<Unit>("Interaction did not finish: ${output()}")
        }

        private fun step(p: Player) {
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
        const val ARIANWYN = "npc.mourning_arianwyn"
        const val ESSYLLT = "npc.mourner_hideout_head_mourner"
        const val MIRROR = "obj.mourning_mirror"
        const val CHISEL_FOR_TEST = SAMPLE
        val BLACK_CRYSTAL_TILE = CoordGrid(1908, 4638, 2)
        val DEATH_ALTAR_TILE = CoordGrid(2204, 4835, 0)
        val LEVER_TILE = CoordGrid(1913, 4639, 1)

        val AREAS =
            listOf(
                intArrayOf(1850, 4600, 1990, 4680),
                intArrayOf(2020, 4620, 2050, 4656),
                intArrayOf(2340, 3160, 2365, 3185),
                intArrayOf(2300, 9780, 2320, 9800),
                intArrayOf(2195, 4825, 2215, 4845),
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
