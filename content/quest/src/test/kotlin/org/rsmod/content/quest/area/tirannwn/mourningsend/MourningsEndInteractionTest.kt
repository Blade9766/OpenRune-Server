@file:OptIn(InternalApi::class)

package org.rsmod.content.quest.area.tirannwn.mourningsend

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.InventoryServerType
import dev.openrune.types.varp.VarpLifetime
import dev.openrune.util.Wearpos
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.random.Random
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
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
import org.rsmod.api.inv.storage.PlayerItemStorage
import org.rsmod.api.invtx.InvTransactionsScript
import org.rsmod.api.player.dialogue.align.TextAlignment
import org.rsmod.api.player.events.interact.HeldObjEvents
import org.rsmod.api.player.events.interact.NpcEvents
import org.rsmod.api.player.hit.modifier.NoopPlayerHitModifier
import org.rsmod.api.player.hit.processor.DamageOnlyPlayerHitProcessor
import org.rsmod.api.player.hook.PlayerTeleportValidateHook
import org.rsmod.api.player.hook.PlayerTeleportValidator
import org.rsmod.api.player.hook.RestrictedAction
import org.rsmod.api.player.input.ResumePauseButtonInput
import org.rsmod.api.player.interact.HeldUInteractions
import org.rsmod.api.player.interact.LocInteractions
import org.rsmod.api.player.interact.LocUInteractions
import org.rsmod.api.player.interact.NpcInteractions
import org.rsmod.api.player.interact.NpcUInteractions
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.protect.clearPendingAction
import org.rsmod.api.random.DefaultGameRandom
import org.rsmod.api.registry.npc.NpcRegistry
import org.rsmod.api.registry.obj.ObjRegistry
import org.rsmod.api.registry.zone.ZoneUpdateMap
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.route.BoundValidator
import org.rsmod.api.shops.Shops
import org.rsmod.content.quest.area.ardougne.QuestDoors
import org.rsmod.content.quest.area.ardougne.biohazard.BiohazardQuest
import org.rsmod.content.quest.area.ardougne.biohazard.npcs.Elena
import org.rsmod.content.quest.area.ardougne.plaguecity.PlagueCityQuest
import org.rsmod.content.quest.area.ardougne.regicide.RegicideChemistry
import org.rsmod.content.quest.area.ardougne.regicide.RegicideQuest
import org.rsmod.content.quest.area.ardougne.sheepherder.SheepHerderQuest
import org.rsmod.content.quest.area.feldip.bigchompy.BigChompyBirdHuntingQuest
import org.rsmod.content.quest.area.feldip.bigchompy.BloatedToads
import org.rsmod.content.quest.area.feldip.bigchompy.ChompyBirds
import org.rsmod.content.quest.area.feldip.bigchompy.ChompyHunt
import org.rsmod.content.quest.area.tirannwn.mourningsend.FixedDevice.Shot
import org.rsmod.content.quest.area.tirannwn.mourningsend.MourningsEndQuest.Companion.APPLE_BARREL
import org.rsmod.content.quest.area.tirannwn.mourningsend.MourningsEndQuest.Companion.BARREL
import org.rsmod.content.quest.area.tirannwn.mourningsend.MourningsEndQuest.Companion.BARREL_OF_NAPHTHA
import org.rsmod.content.quest.area.tirannwn.mourningsend.MourningsEndQuest.Companion.BARREL_OF_ROTTEN_APPLES
import org.rsmod.content.quest.area.tirannwn.mourningsend.MourningsEndQuest.Companion.BEAR_FUR
import org.rsmod.content.quest.area.tirannwn.mourningsend.MourningsEndQuest.Companion.BELLOWS
import org.rsmod.content.quest.area.tirannwn.mourningsend.MourningsEndQuest.Companion.BLOODY_TOP
import org.rsmod.content.quest.area.tirannwn.mourningsend.MourningsEndQuest.Companion.BROKEN_DEVICE
import org.rsmod.content.quest.area.tirannwn.mourningsend.MourningsEndQuest.Companion.BUCKET
import org.rsmod.content.quest.area.tirannwn.mourningsend.MourningsEndQuest.Companion.BUCKET_OF_WATER
import org.rsmod.content.quest.area.tirannwn.mourningsend.MourningsEndQuest.Companion.CLOAK
import org.rsmod.content.quest.area.tirannwn.mourningsend.MourningsEndQuest.Companion.COINS
import org.rsmod.content.quest.area.tirannwn.mourningsend.MourningsEndQuest.Companion.CRYSTAL_1
import org.rsmod.content.quest.area.tirannwn.mourningsend.MourningsEndQuest.Companion.CRYSTAL_3
import org.rsmod.content.quest.area.tirannwn.mourningsend.MourningsEndQuest.Companion.CRYSTAL_4
import org.rsmod.content.quest.area.tirannwn.mourningsend.MourningsEndQuest.Companion.CRYSTAL_SEED
import org.rsmod.content.quest.area.tirannwn.mourningsend.MourningsEndQuest.Companion.FEATHER
import org.rsmod.content.quest.area.tirannwn.mourningsend.MourningsEndQuest.Companion.FIXED_DEVICE
import org.rsmod.content.quest.area.tirannwn.mourningsend.MourningsEndQuest.Companion.GNOME_AGREED
import org.rsmod.content.quest.area.tirannwn.mourningsend.MourningsEndQuest.Companion.GNOME_FREED
import org.rsmod.content.quest.area.tirannwn.mourningsend.MourningsEndQuest.Companion.GNOME_KEY
import org.rsmod.content.quest.area.tirannwn.mourningsend.MourningsEndQuest.Companion.GNOME_SLIPPED
import org.rsmod.content.quest.area.tirannwn.mourningsend.MourningsEndQuest.Companion.LEATHER
import org.rsmod.content.quest.area.tirannwn.mourningsend.MourningsEndQuest.Companion.LETTER
import org.rsmod.content.quest.area.tirannwn.mourningsend.MourningsEndQuest.Companion.LLETYA_ARRIVAL
import org.rsmod.content.quest.area.tirannwn.mourningsend.MourningsEndQuest.Companion.LLETYA_TELEPORT
import org.rsmod.content.quest.area.tirannwn.mourningsend.MourningsEndQuest.Companion.MAGIC_LOGS
import org.rsmod.content.quest.area.tirannwn.mourningsend.MourningsEndQuest.Companion.MOURNER_LEGS
import org.rsmod.content.quest.area.tirannwn.mourningsend.MourningsEndQuest.Companion.MOURNER_TOP
import org.rsmod.content.quest.area.tirannwn.mourningsend.MourningsEndQuest.Companion.NAPHTHA_APPLE_MIX
import org.rsmod.content.quest.area.tirannwn.mourningsend.MourningsEndQuest.Companion.PREMADE_TOAD_CRUNCHIES
import org.rsmod.content.quest.area.tirannwn.mourningsend.MourningsEndQuest.Companion.RIPPED_LEGS
import org.rsmod.content.quest.area.tirannwn.mourningsend.MourningsEndQuest.Companion.ROTTEN_APPLE
import org.rsmod.content.quest.area.tirannwn.mourningsend.MourningsEndQuest.Companion.SIEVE
import org.rsmod.content.quest.area.tirannwn.mourningsend.MourningsEndQuest.Companion.SILK
import org.rsmod.content.quest.area.tirannwn.mourningsend.MourningsEndQuest.Companion.SOAP
import org.rsmod.content.quest.area.tirannwn.mourningsend.MourningsEndQuest.Companion.STAGE_ADMITTED
import org.rsmod.content.quest.area.tirannwn.mourningsend.MourningsEndQuest.Companion.STAGE_BRIEFED
import org.rsmod.content.quest.area.tirannwn.mourningsend.MourningsEndQuest.Companion.STAGE_COMPLETE
import org.rsmod.content.quest.area.tirannwn.mourningsend.MourningsEndQuest.Companion.STAGE_DEVICE_FIXED
import org.rsmod.content.quest.area.tirannwn.mourningsend.MourningsEndQuest.Companion.STAGE_FOOD_TASK
import org.rsmod.content.quest.area.tirannwn.mourningsend.MourningsEndQuest.Companion.STAGE_REVEALED
import org.rsmod.content.quest.area.tirannwn.mourningsend.MourningsEndQuest.Companion.STAGE_SIEVE
import org.rsmod.content.quest.area.tirannwn.mourningsend.MourningsEndQuest.Companion.STAGE_STARTED
import org.rsmod.content.quest.area.tirannwn.mourningsend.MourningsEndQuest.Companion.STAGE_STORES_DONE
import org.rsmod.content.quest.area.tirannwn.mourningsend.MourningsEndQuest.Companion.TOAD_CRUNCHIES
import org.rsmod.content.quest.area.tirannwn.mourningsend.MourningsEndQuest.Companion.TOXIC_NAPHTHA
import org.rsmod.content.quest.area.tirannwn.mourningsend.MourningsEndQuest.Companion.TOXIC_POWDER
import org.rsmod.content.quest.area.tirannwn.mourningsend.npcs.Essyllt
import org.rsmod.content.quest.area.tirannwn.mourningsend.npcs.HideoutGnome
import org.rsmod.content.quest.area.tirannwn.mourningsend.npcs.LletyaArianwyn
import org.rsmod.content.quest.area.tirannwn.mourningsend.npcs.Oronwen
import org.rsmod.content.quest.area.tirannwn.rovingelves.RovingElvesQuest
import org.rsmod.content.quest.area.tirannwn.rovingelves.npcs.Eluned
import org.rsmod.content.quest.area.tirannwn.templeoflight.MourningsEndPart2Quest
import org.rsmod.content.quest.area.tirannwn.templeoflight.npcs.ArianwynTempleTalk
import org.rsmod.content.quest.area.tirannwn.templeoflight.npcs.EssylltTempleTalk
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
import org.rsmod.game.obj.Obj
import org.rsmod.game.obj.ObjEntity
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.game.queue.WorldQueueList
import org.rsmod.game.seq.EntitySeq
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap
import sun.misc.Unsafe

/**
 * Drives Mourning's End Part I's real scripts through the event bus: the start and its
 * requirements, Lletya and the crystal, the mourner's drain and the quest drops, the clothing,
 * the disguise, Essyllt and the gnome, the toads and the device, the four flocks, Elena, the
 * toxin and the food stores, the debrief and its rewards, logging out and back in, and two
 * players on the quest at once.
 */
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class MourningsEndInteractionTest {

    @Test fun `the start checks the three quests and unboosted Ranged and Thieving`() {
        respectingProgress {
            val f = Fixture()
            f.completePrerequisites(chompy = false)
            f.choose(1)
            f.talk(ELUNED)
            assertEquals(0, f.stage(), "Big Chompy Bird Hunting is missing")
            assertTrue(f.said("You do not meet all of the requirements"))
            f.completePrerequisites()
            f.setStat("stat.ranged", base = 59, current = 65)
            f.choose(1)
            f.talk(ELUNED)
            assertEquals(0, f.stage(), "a boosted Ranged level doesn't count")
            f.setStat("stat.ranged", base = 60, current = 60)
            f.setStat("stat.thieving", base = 49, current = 55)
            f.choose(1)
            f.talk(ELUNED)
            assertEquals(0, f.stage(), "nor a boosted Thieving level")
            f.setStat("stat.thieving", base = 50, current = 50)
            f.choose(1, 1, 2)
            f.talk(ELUNED)
            assertEquals(STAGE_BRIEFED, f.stage(), "Eluned takes the player to Lletya and Arianwyn briefs them")
            assertEquals(1, f.count(CRYSTAL_4))
            assertEquals(LLETYA_ARRIVAL, f.player.coords)
            assertTrue(f.journal().contains("Arandar"))
        }
    }

    @Test fun `the crystal is handed over once and never into a full pack`() {
        val f = Fixture()
        f.fill()
        f.choose(1, 1)
        f.talk(ELUNED)
        assertEquals(0, f.stage(), "no room, no start")
        assertEquals(0, f.count(CRYSTAL_4))
        f.empty()
        f.choose(1, 1, 2)
        f.talk(ELUNED)
        assertEquals(STAGE_BRIEFED, f.stage())
        val handovers = f.output().split("tiny crystal seed").size
        f.choose(1)
        f.talk(ELUNED)
        assertEquals(1, f.count(CRYSTAL_4), "talking again gives no second crystal")
        assertEquals(handovers, f.output().split("tiny crystal seed").size, "and no second hand-over")
    }

    @Test fun `a briefing cut short is given by Arianwyn, and the stage waits for it`() {
        val f = Fixture()
        f.jump(STAGE_STARTED)
        f.talk(ARIANWYN)
        assertEquals(STAGE_BRIEFED, f.stage())
        f.talk(ARIANWYN)
        assertEquals(STAGE_BRIEFED, f.stage(), "a second talk is only advice")
    }

    @Test fun `the trees into Lletya open only once the quest has started`() {
        respectingProgress {
            val f = Fixture()
            f.player.coords = CoordGrid(2304, 3193, 0)
            f.locOp(TREE_GATE, CoordGrid(2305, 3191, 0))
            assertEquals(CoordGrid(2304, 3193, 0), f.player.coords)
            assertTrue(f.said("too dense"))
            f.jump(STAGE_STARTED)
            f.locOp(TREE_GATE, CoordGrid(2305, 3191, 0))
            assertEquals(CoordGrid(2306, 3193, 0), f.player.coords)
            f.jump(0)
            f.locOp(TREE_GATE, CoordGrid(2305, 3191, 0))
            assertEquals(CoordGrid(2304, 3193, 0), f.player.coords, "leaving is always allowed")
        }
    }

    @Test fun `the crystal loses a charge per teleport and crumbles into a seed`() {
        val f = Fixture(STAGE_BRIEFED)
        f.give(CRYSTAL_4)
        f.held(1, CRYSTAL_4)
        assertEquals(LLETYA_TELEPORT, f.player.coords)
        assertEquals(1, f.count(CRYSTAL_3))
        f.drop(CRYSTAL_3)
        f.give(CRYSTAL_1)
        f.held(1, CRYSTAL_1)
        assertEquals(1, f.count(CRYSTAL_SEED))
        assertTrue(f.said("run out of charges"))
        f.drop(CRYSTAL_SEED)
        f.give(CRYSTAL_3)
        f.player.coords = CoordGrid(2350, 3170, 0)
        f.teleportDenial = "A magical force stops you from teleporting."
        f.held(1, CRYSTAL_3)
        assertEquals(1, f.count(CRYSTAL_3), "a refused teleport costs no charge")
        f.teleportDenial = null
        f.held(3, CRYSTAL_3)
        assertEquals(1, f.player.crystalToggle)
        f.held(1, CRYSTAL_3)
        assertTrue(f.said("Song of the Elves") || f.said("path to Prifddinas"), "Prifddinas is now the left click")
        assertEquals(1, f.count(CRYSTAL_3))
    }

    @Test fun `Eluned re-enchants a seed for a falling price`() {
        val f = Fixture(STAGE_BRIEFED)
        f.give(CRYSTAL_SEED)
        f.give(COINS, 500)
        f.choose(1)
        f.op3(ELUNED_ENCHANT)
        assertEquals(1, f.count(CRYSTAL_SEED), "750 coins are needed the first time")
        f.give(COINS, 1000)
        f.choose(1)
        f.op3(ELUNED_ENCHANT)
        assertEquals(1, f.count(CRYSTAL_3))
        assertEquals(750, f.stack(COINS))
        assertEquals(600, ElunedErrands.price(f.player))
        repeat(7) { MourningsEndQuest.setVarBit(f.player, "varbit.mourning_eluned_chant", it + 1) }
        assertEquals(150, ElunedErrands.price(f.player), "it never drops below 150")
    }

    @Test fun `the mourner's vial lowers only what is above 20, and never a base level`() {
        val f = Fixture(STAGE_BRIEFED)
        f.second.statMap.setCurrentLevel("stat.attack", 85.toByte())
        for (stat in ArandarMourner.DRAINED_STATS) f.setStat(stat, base = 80, current = 85)
        f.setStat("stat.magic", base = 15, current = 12)
        f.setStat("stat.woodcutting", base = 70, current = 70)
        ArandarMourner.drain(f.player)
        for (stat in ArandarMourner.DRAINED_STATS - "stat.magic") {
            assertEquals(20, f.player.statMap.getCurrentLevel(stat).toInt(), stat)
            assertEquals(80, f.player.statMap.getBaseLevel(stat).toInt(), stat)
        }
        assertEquals(12, f.player.statMap.getCurrentLevel("stat.magic").toInt(), "a low stat isn't raised")
        assertEquals(70, f.player.statMap.getCurrentLevel("stat.woodcutting").toInt())
        assertEquals(85, f.second.statMap.getCurrentLevel("stat.attack").toInt(), "only the target is touched")
        assertTrue(f.said("throws a vial at you"))
    }

    @Test fun `the quest drops come only while wanted and never double up`() {
        val f = Fixture(STAGE_STARTED)
        val quest = f.mourning
        assertFalse(quest.needsLetterDrop(f.player), "not before the briefing")
        f.jump(STAGE_BRIEFED)
        assertTrue(quest.needsTopDrop(f.player) && quest.needsLetterDrop(f.player))
        f.give(LETTER)
        assertFalse(quest.needsLetterDrop(f.player))
        f.bank(BLOODY_TOP)
        assertFalse(quest.needsTopDrop(f.player), "a banked top counts")
        f.unbank(BLOODY_TOP)
        f.drop(BLOODY_TOP)
        f.give(MOURNER_TOP)
        assertFalse(quest.needsTopDrop(f.player), "nor once it has been cleaned")
        f.drop(MOURNER_TOP)
        f.jump(STAGE_ADMITTED)
        assertFalse(quest.needsTopDrop(f.player) || quest.needsLetterDrop(f.player))
    }

    @Test fun `Tegid's soap is stolen once and scrubs the top with a bucket of water`() {
        val f = Fixture(STAGE_BRIEFED)
        f.give(BLOODY_TOP)
        f.locOp(BASKET, BASKET_TILE)
        assertTrue(f.said("It's full of dirty robes."))
        assertEquals(0, f.count(SOAP), "nothing to see before talking to Tegid")
        f.talk(TEGID)
        assertEquals(1, f.player.tegidChat)
        f.choose(2)
        f.locOp(BASKET, BASKET_TILE)
        assertEquals(0, f.count(SOAP), "left where it is")
        f.choose(1)
        f.locOp(BASKET, BASKET_TILE)
        assertEquals(1, f.count(SOAP))
        f.choose(1)
        f.locOp(BASKET, BASKET_TILE)
        assertEquals(1, f.count(SOAP), "one bar at a time")
        f.use(SOAP, BLOODY_TOP)
        assertTrue(f.said("The dry soap has no effect."))
        assertEquals(1, f.count(BLOODY_TOP))
        f.give(BUCKET_OF_WATER)
        f.use(SOAP, BLOODY_TOP)
        assertEquals(1, f.count(MOURNER_TOP))
        assertEquals(1, f.count(BUCKET), "the bucket comes back empty")
        assertEquals(1, f.count(SOAP), "and the soap is kept")
        assertEquals(0, f.count(BLOODY_TOP) + f.count(BUCKET_OF_WATER))
    }

    @Test fun `Oronwen keeps materials handed over early and mends the trousers once`() {
        val f = Fixture(STAGE_BRIEFED)
        f.give(RIPPED_LEGS)
        f.give(SILK)
        f.talk(ORONWEN)
        assertEquals(1, f.player.trousersShown)
        assertEquals(1, f.count(RIPPED_LEGS), "she can't mend them without the fur")
        f.npcU(ORONWEN, SILK)
        assertEquals(1, f.player.firstSilk)
        assertTrue(f.said("I need bear fur and one piece of silk."))
        val reloaded = f.saveAndReload()
        assertEquals(1, reloaded.firstSilk, "a half-paid repair survives logging out")
        f.give(SILK)
        f.give(BEAR_FUR)
        f.give(SILK)
        f.choose(3)
        f.talk(ORONWEN)
        assertEquals(1, f.count(MOURNER_LEGS))
        assertEquals(1, f.count(SILK), "only the one silk still owed was taken")
        assertEquals(0, f.count(BEAR_FUR) + f.count(RIPPED_LEGS))
        assertEquals(1, f.player.trousersFixed)
        assertEquals(0, f.player.firstSilk + f.player.secondSilk + f.player.furGiven)
        f.choose(2)
        f.talk(ORONWEN)
        assertTrue(f.said("No thanks."), "with nothing left to mend she only offers her shop")
        assertEquals(1, f.count(MOURNER_LEGS), "no second pair")
    }

    @Test fun `the disguise is the six pieces in their slots, whatever else is worn`() {
        val f = Fixture(STAGE_BRIEFED)
        f.wearDisguise()
        f.wear("obj.bronze_dagger")
        f.wear("obj.ring_of_recoil")
        assertTrue(f.player.wearsDisguise())
        assertTrue(f.hideout.admits(f.player))
        f.player.worn[Wearpos.Back.slot] = null
        assertFalse(f.player.wearsDisguise(), "the cloak is part of it")
        assertFalse(f.hideout.admits(f.player))
        f.wear(CLOAK)
        f.jump(STAGE_STARTED)
        assertFalse(f.hideout.admits(f.player), "not before Arianwyn's briefing")
        f.jump(STAGE_BRIEFED)
        f.player.coords = CoordGrid(2542, 3328, 0)
        f.dispatch { with(f.hideout) { trapdoor() } }
        assertEquals(MournerHideout.BASEMENT_ARRIVAL, f.player.coords)
        f.player.worn[Wearpos.Hat.slot] = null
        f.player.coords = CoordGrid(2542, 3328, 0)
        f.dispatch { with(f.hideout) { trapdoor() } }
        assertTrue(f.said("bolted on the other side"))
        assertEquals(CoordGrid(2542, 3328, 0), f.player.coords)
    }

    @Test fun `Essyllt takes the letter once and replaces a lost device or key`() {
        val f = Fixture(STAGE_BRIEFED)
        f.talk(ESSYLLT)
        assertEquals(STAGE_BRIEFED, f.stage(), "no letter, no assignment")
        f.give(LETTER)
        f.fill()
        f.talk(ESSYLLT)
        assertEquals(STAGE_BRIEFED, f.stage(), "a full pack keeps the letter")
        assertEquals(1, f.count(LETTER))
        f.empty()
        f.talk(ESSYLLT)
        assertEquals(STAGE_ADMITTED, f.stage())
        assertEquals(0, f.count(LETTER))
        assertEquals(1, f.count(BROKEN_DEVICE))
        assertEquals(1, f.count(GNOME_KEY))
        f.talk(ESSYLLT)
        assertEquals(1, f.count(BROKEN_DEVICE), "nothing lost, nothing given")
        f.drop(BROKEN_DEVICE)
        f.drop(GNOME_KEY)
        f.talk(ESSYLLT)
        assertEquals(1, f.count(BROKEN_DEVICE))
        assertEquals(1, f.count(GNOME_KEY))
    }

    @Test fun `the gnome gives in to a feather and crunchies, and fixes the device on release`() {
        val f = Fixture(STAGE_ADMITTED)
        f.give(BROKEN_DEVICE)
        f.give(FEATHER, 5)
        f.locU(RACK, RACK_TILE, FEATHER)
        assertEquals(0, f.player.gnomeState, "nothing to tickle out of him yet")
        f.choose(1)
        f.locOp(RACK, RACK_TILE)
        assertEquals(0, f.player.gnomeState, "a wrong answer")
        f.choose(3)
        f.locOp(RACK, RACK_TILE)
        assertEquals(GNOME_SLIPPED, f.player.gnomeState)
        f.locU(RACK, RACK_TILE, FEATHER)
        assertEquals(GNOME_SLIPPED, f.player.gnomeState, "no crunchies to dangle")
        f.give(TOAD_CRUNCHIES)
        f.locU(RACK, RACK_TILE, TOAD_CRUNCHIES)
        assertEquals(0, f.count(TOAD_CRUNCHIES), "fed too early, he just eats them")
        f.give(PREMADE_TOAD_CRUNCHIES)
        f.locU(RACK, RACK_TILE, FEATHER)
        assertEquals(GNOME_AGREED, f.player.gnomeState, "the premade kind works too")
        f.locOp(RACK, RACK_TILE, InteractionOp.Op2)
        assertEquals(1, f.count(BROKEN_DEVICE), "not without leather and logs")
        f.give(LEATHER)
        f.give(MAGIC_LOGS)
        f.locOp(RACK, RACK_TILE, InteractionOp.Op2)
        assertEquals(GNOME_FREED, f.player.gnomeState)
        assertEquals(STAGE_DEVICE_FIXED, f.stage())
        assertEquals(1, f.count(FIXED_DEVICE))
        assertEquals(0, f.count(BROKEN_DEVICE) + f.count(LEATHER) + f.count(MAGIC_LOGS) + f.count(PREMADE_TOAD_CRUNCHIES))
        f.talk(GNOME)
        assertEquals(MourningsEndQuest.GNOME_ASKED_AMMO, f.player.gnomeState)
        assertEquals(1, f.count(FIXED_DEVICE), "talking again never hands out another")
    }

    @Test fun `dye fills empty bellows, which inflate one coloured toad`() {
        val f = Fixture(STAGE_DEVICE_FIXED)
        f.give(BELLOWS)
        f.give("obj.reddye")
        f.use("obj.reddye", BELLOWS)
        assertEquals(1, f.count(Flock.RED.bellows))
        assertEquals(0, f.count("obj.reddye") + f.count(BELLOWS))
        f.npcU(SWAMP_TOAD, Flock.RED.bellows)
        assertEquals(1, f.count(Flock.RED.toad))
        assertEquals(1, f.count(BELLOWS), "the bellows are empty again")
        f.npcU(SWAMP_TOAD, BELLOWS)
        assertEquals(1, f.count(Flock.RED.toad), "empty bellows only blow air")
    }

    @Test fun `the device loads one toad, empties it back, and needs one and 60 Ranged to wield`() {
        val f = Fixture(STAGE_DEVICE_FIXED)
        f.give(FIXED_DEVICE)
        assertNotNull(f.wearRestriction(), "an empty device can't be wielded")
        f.give(Flock.BLUE.toad, 2)
        f.use(Flock.BLUE.toad, FIXED_DEVICE)
        assertEquals(Flock.BLUE.ammo, f.player.gunAmmo)
        f.use(Flock.BLUE.toad, FIXED_DEVICE)
        assertEquals(1, f.count(Flock.BLUE.toad), "one toad at a time")
        f.setStat("stat.ranged", base = 59, current = 59)
        assertNotNull(f.wearRestriction())
        f.setStat("stat.ranged", base = 60, current = 60)
        assertNull(f.wearRestriction())
        f.held(3, FIXED_DEVICE)
        assertEquals(0, f.player.gunAmmo)
        assertEquals(2, f.count(Flock.BLUE.toad))
    }

    @Test fun `firing hits what stands on the aimed tile, once per toad`() {
        val f = Fixture(STAGE_DEVICE_FIXED)
        f.armDevice(Flock.RED)
        val sheep = f.spawn(Flock.RED.field, f.player.coords.translate(0, FixedDevice.START_RANGE))
        assertEquals(Shot.REDYED, f.fire())
        assertEquals(1, f.player.vars[Flock.RED.varbit])
        assertEquals(0, f.player.gunAmmo, "the toad is used up")
        assertNull(f.fire(), "the interface closed with the shot, so a second fire packet does nothing")
        f.armDevice(Flock.RED)
        f.aimMove(0, 1)
        assertEquals(Shot.MISS, f.fire(), "one tile past the sheep")
        assertEquals(0, f.player.gunAmmo, "a miss still uses the toad")
        f.armDevice(Flock.RED)
        assertEquals(Shot.ALREADY_DYED, f.fire())
        f.despawn(sheep)
        f.armDevice(Flock.GREEN)
        f.spawn(Flock.BLUE.field, f.player.coords.translate(0, FixedDevice.START_RANGE))
        assertEquals(Shot.WRONG_COLOUR, f.fire())
        assertEquals(0, f.player.vars[Flock.BLUE.varbit])
        assertEquals(0, f.player.vars[Flock.GREEN.varbit])
    }

    @Test fun `the aim stays in range and the device must stay wielded`() {
        val f = Fixture(STAGE_DEVICE_FIXED)
        f.armDevice(Flock.YELLOW)
        repeat(20) { f.aimMove(1, 1) }
        assertEquals(FixedDevice.Aim(FixedDevice.MAX_RANGE, FixedDevice.MAX_RANGE), f.device.aimOf(f.player))
        f.player.worn[Wearpos.RightHand.slot] = null
        assertNull(f.fire(), "taking the device off ends the aim")
        assertEquals(Flock.YELLOW.ammo, f.player.gunAmmo, "and keeps the toad")
        assertNull(f.device.aimOf(f.player))
        assertNull(f.fireWithoutAim(), "a fire with no aim open is ignored")
    }

    @Test fun `the four flocks are separate flags and only all four end the task`() {
        val f = Fixture(STAGE_DEVICE_FIXED)
        for ((i, flock) in Flock.entries.withIndex()) {
            assertFalse(f.mourning.allSheepDone(f.player))
            repeat(2) {
                f.armDevice(flock)
                val sheep = f.spawn(flock.field, f.player.coords.translate(0, FixedDevice.START_RANGE))
                f.fire()
                f.despawn(sheep)
            }
            assertEquals(i + 1, Flock.entries.count { f.mourning.sheepDone(f.player, it) }, "hitting ${flock.label} twice counts once")
        }
        assertTrue(f.mourning.allSheepDone(f.player))
        f.talk(ESSYLLT)
        assertEquals(STAGE_FOOD_TASK, f.stage())
    }

    @Test fun `Essyllt waits for all four flocks before the food task`() {
        val f = Fixture(STAGE_DEVICE_FIXED)
        f.give(FIXED_DEVICE)
        f.give(GNOME_KEY)
        for (flock in Flock.entries.drop(1)) MourningsEndQuest.setVarBit(f.player, flock.varbit, 1)
        f.talk(ESSYLLT)
        assertEquals(STAGE_DEVICE_FIXED, f.stage())
        MourningsEndQuest.setVarBit(f.player, Flock.BLUE.varbit, 1)
        f.talk(ESSYLLT)
        assertEquals(STAGE_FOOD_TASK, f.stage())
    }

    @Test fun `Elena only takes the apple from behind the Headquarters and hands over a sieve`() {
        val f = Fixture(STAGE_FOOD_TASK)
        f.talk(ELENA)
        assertEquals(MourningsEndQuest.ELENA_AGREED, f.player.elenaState)
        f.give(ROTTEN_APPLE)
        f.talk(ELENA)
        assertEquals(STAGE_FOOD_TASK, f.stage(), "any old rotten apple won't do")
        assertEquals(1, f.count(ROTTEN_APPLE))
        f.drop(ROTTEN_APPLE)
        val hook = HeadquartersAppleHook(f.mourning)
        val behindHq = f.groundApple(CoordGrid(2535, 3333, 0))
        val elsewhere = f.groundApple(CoordGrid(2600, 3300, 0))
        val type = checkNotNull(ServerCacheManager.getItem(ROTTEN_APPLE.asRSCM()))
        assertFalse(hook.redirects(f.player, elsewhere, type))
        assertTrue(hook.redirects(f.player, behindHq, type))
        assertTrue(hook.take(f.player, behindHq, type))
        assertEquals(1, f.player.hqApple)
        f.talk(ELENA)
        assertEquals(STAGE_SIEVE, f.stage())
        assertEquals(1, f.count(SIEVE))
        assertEquals(0, f.count(ROTTEN_APPLE))
        assertEquals(0, f.player.hqApple)
        f.drop(SIEVE)
        f.talk(ELENA)
        assertEquals(1, f.count(SIEVE), "a lost sieve is replaced")
        f.talk(ELENA)
        assertEquals(1, f.count(SIEVE), "but only when lost")
    }

    @Test fun `the toxin is made in order, on a range not a fire, with room for both piles`() {
        val f = Fixture(STAGE_SIEVE)
        f.give(BARREL)
        f.locU(APPLE_PILE, ORCHARD_PILE, BARREL)
        assertEquals(1, f.count(BARREL_OF_ROTTEN_APPLES))
        f.locU(PRESS, ORCHARD_PRESS, BARREL_OF_ROTTEN_APPLES)
        assertEquals(1, f.count(APPLE_BARREL))
        f.give(BARREL_OF_NAPHTHA)
        f.use(BARREL_OF_NAPHTHA, APPLE_BARREL)
        assertEquals(1, f.count(NAPHTHA_APPLE_MIX))
        assertEquals(1, f.count(BARREL))
        f.give(SIEVE)
        f.use(SIEVE, NAPHTHA_APPLE_MIX)
        assertEquals(1, f.count(TOXIC_NAPHTHA))
        assertEquals(1, f.count(SIEVE), "the sieve is a tool")
        f.fill()
        f.empty(1)
        f.locU(HQ_RANGE, HQ_RANGE_TILE, TOXIC_NAPHTHA)
        assertEquals(1, f.count(TOXIC_NAPHTHA), "two free spaces are needed")
        assertTrue(f.said("two free spaces"))
        f.empty()
        f.locU(HQ_RANGE, HQ_RANGE_TILE, TOXIC_NAPHTHA)
        assertEquals(2, f.count(TOXIC_POWDER))
        assertEquals(2, f.count(BARREL), "one barrel back from the mixing and one from the drying")
        f.give(TOXIC_NAPHTHA)
        val hp = f.player.statMap.getCurrentLevel("stat.hitpoints").toInt()
        f.locU("loc.fire", CoordGrid(2500, 3300, 0), TOXIC_NAPHTHA)
        assertEquals(0, f.count(TOXIC_NAPHTHA), "a fire destroys it")
        assertEquals(2, f.count(TOXIC_POWDER), "and makes no powder")
        assertTrue(f.said("explodes"))
        assertTrue(hp > 0)
    }

    @Test fun `two different food stores are needed, and none is credited twice`() {
        val f = Fixture(STAGE_DEVICE_FIXED)
        f.give(TOXIC_POWDER, 2)
        f.locU(FoodStore.CHURCH.locs[0], CoordGrid(2524, 3285, 0), TOXIC_POWDER)
        assertEquals(2, f.count(TOXIC_POWDER), "not before the food task")
        f.jump(STAGE_SIEVE)
        f.locU(FoodStore.CHURCH.locs[0], CoordGrid(2524, 3285, 0), TOXIC_POWDER)
        assertEquals(1, f.count(TOXIC_POWDER))
        f.locU(FoodStore.CHURCH.locs[1], CoordGrid(2524, 3287, 0), TOXIC_POWDER)
        assertEquals(1, f.count(TOXIC_POWDER), "another sack of the same store is the same store")
        assertTrue(f.said("already poisoned this store"))
        assertEquals(STAGE_SIEVE, f.stage())
        f.locU(FoodStore.GENERAL_STORE.locs[0], CoordGrid(2467, 3287, 0), TOXIC_POWDER)
        assertEquals(0, f.count(TOXIC_POWDER))
        assertEquals(STAGE_STORES_DONE, f.stage())
        val reloaded = f.saveAndReload()
        assertTrue(f.mourning.storePoisoned(reloaded, FoodStore.CHURCH))
        assertTrue(f.mourning.storePoisoned(reloaded, FoodStore.GENERAL_STORE))
        assertFalse(f.mourning.storePoisoned(reloaded, FoodStore.CIVIC_OFFICE))
    }

    @Test fun `only Arianwyn completes the quest, after Essyllt's report, and rewards once`() {
        val f = Fixture(STAGE_STORES_DONE)
        f.talk(ARIANWYN)
        assertEquals(STAGE_STORES_DONE, f.stage(), "Essyllt must be heard first")
        f.talk(ESSYLLT)
        assertEquals(STAGE_REVEALED, f.stage())
        val thieving = f.player.statMap.getXP("stat.thieving")
        val hitpoints = f.player.statMap.getXP("stat.hitpoints")
        f.talk(ARIANWYN)
        assertEquals(STAGE_COMPLETE, f.stage())
        assertEquals(2, f.player.vars["varp.qp"])
        assertEquals(40_000, f.player.statMap.getXP("stat.thieving") - thieving)
        assertEquals(25_000, f.player.statMap.getXP("stat.hitpoints") - hitpoints)
        f.talk(ARIANWYN)
        assertEquals(2, f.player.vars["varp.qp"], "rewards are given once")
        assertEquals(40_000, f.player.statMap.getXP("stat.thieving") - thieving)
    }

    @Test fun `progress survives logging out and a reset clears the quest's own flags`() {
        val f = Fixture(STAGE_DEVICE_FIXED)
        MourningsEndQuest.setVarBit(f.player, Flock.RED.varbit, 1)
        MourningsEndQuest.setVarBit(f.player, "varbit.mourning_gun_ammo", Flock.GREEN.ammo)
        MourningsEndQuest.setVarBit(f.player, "varbit.mourning_gnome", GNOME_FREED)
        MourningsEndQuest.setVarBit(f.player, "varbit.mourning_hq_apple", 1)
        val loaded = f.saveAndReload()
        assertEquals(STAGE_DEVICE_FIXED, f.mourning.stage(loaded))
        assertEquals(1, loaded.vars[Flock.RED.varbit])
        assertEquals(Flock.GREEN.ammo, loaded.gunAmmo)
        assertEquals(GNOME_FREED, loaded.gnomeState)
        assertEquals(1, loaded.hqApple)
        f.mourning.quest.jumpToStage(loaded, 0)
        assertEquals(0, loaded.vars[Flock.RED.varbit])
        assertEquals(0, loaded.gunAmmo + loaded.gnomeState + loaded.hqApple)
    }

    @Test fun `two players dye sheep and spoil stores independently`() {
        val f = Fixture(STAGE_DEVICE_FIXED)
        f.mourning.quest.jumpToStage(f.second, STAGE_DEVICE_FIXED)
        f.armDevice(Flock.RED)
        f.spawn(Flock.RED.field, f.player.coords.translate(0, FixedDevice.START_RANGE))
        assertEquals(Shot.REDYED, f.fire())
        assertEquals(1, f.player.vars[Flock.RED.varbit])
        assertEquals(0, f.second.vars[Flock.RED.varbit], "the other player's flock is still undyed")
        f.asSecond {
            f.armDevice(Flock.RED)
            assertEquals(Shot.REDYED, f.fire())
        }
        assertEquals(1, f.second.vars[Flock.RED.varbit])
        f.mourning.quest.jumpToStage(f.player, STAGE_SIEVE)
        f.give(TOXIC_POWDER)
        f.locU(FoodStore.CIVIC_OFFICE.locs[0], CoordGrid(2517, 3312, 0), TOXIC_POWDER)
        assertTrue(f.mourning.storePoisoned(f.player, FoodStore.CIVIC_OFFICE))
        assertFalse(f.mourning.storePoisoned(f.second, FoodStore.CIVIC_OFFICE))
    }

    private class Fixture(stage: Int = 0) {
        val events = EventBus()
        private val client = RecordingClient()
        private val collision = CollisionFlagMap()
        private val npcs = NpcList()
        private val players = PlayerList()
        private val coroutine = GameCoroutine("mourning-test")
        private var result: Result<Unit>? = null
        var teleportDenial: String? = null
        private val validator = PlayerTeleportValidator(setOf(PlayerTeleportValidateHook { _, _, _ -> teleportDenial }))
        private val context = ProtectedAccessContextFactory.empty().copy(
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
        private val objRegistry = ObjRegistry(ZoneUpdateMap())
        private val objRepo = ObjRepository(clock, objRegistry)
        private val npcRepo: NpcRepository
        private val picks = ArrayDeque<Int>()
        private val locU = LocUInteractions::class.java.getDeclaredConstructor(EventBus::class.java)
            .apply { isAccessible = true }.newInstance(events)
        private val heldU = HeldUInteractions(events)
        private val npcUInteractions = NpcUInteractions::class.java.getDeclaredConstructor(EventBus::class.java)
            .apply { isAccessible = true }.newInstance(events)

        val player = newPlayer(6161L, 1)
        val second = newPlayer(6262L, 2)
        private var active = player

        val mourning = MourningsEndQuest()
        val roving = RovingElvesQuest()
        val chompy = BigChompyBirdHuntingQuest()
        val herder = SheepHerderQuest()
        val hideout: MournerHideout
        val device: FixedDevice
        private val wearHook = FixedDeviceWearHook()

        init {
            for ((x0, z0, x1, z1) in AREAS) {
                for (level in 0..1) for (x in x0..x1 step 8) for (z in z0..z1 step 8) collision.allocateIfAbsent(x, z, level)
            }
            players[player.slotId] = player
            players[second.slotId] = second
            npcRepo = NpcRepository(clock, NpcRegistry(npcs, collision, events), npcs)
            val launcher = ProtectedAccessLauncher(unused<ProtectedAccessContextFactory>())
            val biohazard = BiohazardQuest()
            val plague = PlagueCityQuest()
            val regicide = RegicideQuest()
            val briefing = ArianwynBriefing(mourning)
            val errands = ElunedErrands(mourning, briefing)
            val part2 = MourningsEndPart2Quest(mourning)
            hideout = MournerHideout(mourning, unused<QuestDoors>(), unused<LocRepository>(), part2)
            device = FixedDevice(mourning, WorldRepository(ZoneUpdateMap()), npcRepo)
            val hunt = ChompyHunt(npcRepo, collision, clock, players, launcher, WorldQueueList())
            val scripts = ScriptContext(events, CheatCommandMap(), EngineQueueCache())
            for (script in listOf(
                mourning, roving, chompy, herder, hideout, device,
                Eluned(roving, errands), LletyaArianwyn(mourning, briefing, ArianwynTempleTalk(part2)), Lletya(mourning, validator, unused()),
                MournerClothing(mourning), Oronwen(mourning, unused<Shops>()), Essyllt(mourning, EssylltTempleTalk(part2)), HideoutGnome(mourning),
                FoodSupply(mourning, unused<QuestDoors>()),
                Elena(biohazard, plague, objRepo, unused<QuestDoors>(), RegicideChemistry(regicide), ElenaResearch(mourning)),
                BloatedToads(chompy, hunt, unused<ChompyBirds>(), players, DefaultGameRandom(Random(5))),
            )) {
                with(script) { scripts.startup() }
            }
            for (p in listOf(player, second)) {
                roving.quest.jumpToStage(p, RovingElvesQuest.STAGE_COMPLETE)
                if (stage > 0) mourning.quest.jumpToStage(p, stage)
            }
        }

        @OptIn(InternalApi::class)
        private fun newPlayer(id: Long, slot: Int) = Player().apply {
            this.client = this@Fixture.client
            uuid = id
            observerUUID = id
            slotId = slot
            assignUid()
            coords = CoordGrid(2290, 3150, 0)
            currentMapClock = 100
            processedMapClock = 100
            pendingSequence = EntitySeq.NULL
            pendingFaceAngle = EntityFaceAngle.NULL
            inv = Inventory(InventoryServerType(size = 28, flags = 0), arrayOfNulls(28))
            worn = Inventory(InventoryServerType(size = 14, flags = 0), arrayOfNulls(14))
            for ((stat, level) in listOf("stat.hitpoints" to 70, "stat.ranged" to 70, "stat.thieving" to 60)) {
                statMap.setBaseLevel(stat, level.toByte())
                statMap.setCurrentLevel(stat, level.toByte())
            }
        }

        fun access(p: Player = active) = ProtectedAccess(p, coroutine, context)

        fun stage(): Int = mourning.stage(player)

        fun jump(stage: Int) = mourning.quest.jumpToStage(player, stage)

        fun journal(): String = mourning.questLog(access())

        fun completePrerequisites(chompy: Boolean = true) {
            roving.quest.jumpToStage(player, RovingElvesQuest.STAGE_COMPLETE)
            this.chompy.quest.jumpToStage(player, if (chompy) BigChompyBirdHuntingQuest.STAGE_COMPLETE else 0)
            herder.quest.jumpToStage(player, SheepHerderQuest.STAGE_COMPLETE)
        }

        fun choose(vararg options: Int) {
            picks += options.toList()
        }

        fun setStat(stat: String, base: Int, current: Int) {
            player.statMap.setBaseLevel(stat, base.toByte())
            player.statMap.setCurrentLevel(stat, current.toByte())
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

        fun wear(obj: String) {
            val type = checkNotNull(ServerCacheManager.getItem(obj.asRSCM()))
            player.worn[type.wearpos1] = InvObj(obj, 1)
        }

        fun wearDisguise() {
            for ((pos, obj) in MourningsEndQuest.DISGUISE) player.worn[pos.slot] = InvObj(obj, 1)
        }

        private val bankInv by lazy { access().bank }

        fun bank(obj: String) {
            drop(obj)
            bankInv[bankInv.indexOfFirst { it == null }] = InvObj(obj, 1)
        }

        fun unbank(obj: String) {
            bankInv[bankInv.indexOfFirst { it?.id == obj.asRSCM() }] = null
            give(obj)
        }

        fun fill() {
            while (player.inv.freeSpace() > 0) give("obj.bronze_dagger")
        }

        fun empty(slots: Int = 28) {
            var freed = 0
            for (i in player.inv.indices) {
                if (freed >= slots) return
                if (player.inv[i]?.id == "obj.bronze_dagger".asRSCM()) {
                    player.inv[i] = null
                    freed++
                }
            }
        }

        fun drop(obj: String) {
            val slot = player.inv.indexOfFirst { it?.id == obj.asRSCM() }
            if (slot >= 0) player.inv[slot] = null
        }

        fun count(obj: String): Int = player.inv.count(obj)

        fun stack(obj: String): Int = player.inv.filter { it?.id == obj.asRSCM() }.sumOf { it?.count ?: 0 }

        fun said(text: String): Boolean = output().contains(text)

        fun spawn(type: String, at: CoordGrid): Npc {
            val npc = Npc(type, at)
            npcRepo.add(npc, Int.MAX_VALUE)
            return npc
        }

        fun despawn(npc: Npc) = npcRepo.del(npc, Int.MAX_VALUE)

        fun groundApple(at: CoordGrid): Obj = Obj(at, ObjEntity(ROTTEN_APPLE.asRSCM(), 1, 0), 100, player.observerUUID ?: 0L)

        fun talk(type: String, at: CoordGrid = active.coords.translateX(1)) {
            val npc = Npc(type, at)
            npcRepo.add(npc, Int.MAX_VALUE)
            publish(NpcEvents.Op1(npc))
            if (npc.isSlotAssigned) npcRepo.del(npc, Int.MAX_VALUE)
        }

        fun op3(type: String) {
            val npc = Npc(type, active.coords.translateX(1))
            npcRepo.add(npc, Int.MAX_VALUE)
            publish(NpcEvents.Op3(npc))
            if (npc.isSlotAssigned) npcRepo.del(npc, Int.MAX_VALUE)
        }

        fun npcU(type: String, obj: String) {
            val npc = Npc(type, player.coords.translateX(1))
            npcRepo.add(npc, Int.MAX_VALUE)
            val slot = player.inv.indexOfFirst { it?.id == obj.asRSCM() }
            val objType = checkNotNull(ServerCacheManager.getItem(obj.asRSCM()))
            val npcType = checkNotNull(ServerCacheManager.getNpc(type.asRSCM()))
            dispatch { npcUInteractions.interactOp(this, npc, inv, slot, npcType, objType) }
            if (npc.isSlotAssigned) npcRepo.del(npc, Int.MAX_VALUE)
        }

        fun held(op: Int, obj: String) {
            val slot = player.inv.indexOfFirst { it?.id == obj.asRSCM() }
            val type = checkNotNull(ServerCacheManager.getItem(obj.asRSCM()))
            dispatch {
                val held = checkNotNull(inv[slot])
                val event = when (op) {
                    1 -> HeldObjEvents.Op1(slot, held, type, inv)
                    2 -> HeldObjEvents.Op2(slot, held, type, inv)
                    else -> HeldObjEvents.Op3(slot, held, type, inv)
                }
                assertTrue(events.publish(this, event))
            }
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
            val event = LocInteractions(BoundValidator(collision), events).opTrigger(active, loc, op)
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

        fun wearRestriction(): String? {
            val type = checkNotNull(ServerCacheManager.getItem(FIXED_DEVICE.asRSCM()))
            return wearHook.restriction(active, RestrictedAction.Equip(type))
        }

        /** Loads a [flock] toad into a wielded device and opens the aim. */
        fun armDevice(flock: Flock) {
            val p = active
            p.worn[Wearpos.RightHand.slot] = InvObj(FIXED_DEVICE, 1)
            MourningsEndQuest.setVarBit(p, "varbit.mourning_gun_ammo", flock.ammo)
            dispatch { with(device) { openAim() } }
        }

        fun aimMove(dx: Int, dz: Int) = dispatch(closeModals = false) { with(device) { move(dx, dz) } }

        fun fire(): Shot? {
            var shot: Shot? = null
            dispatch(closeModals = false) { shot = with(device) { fire() } }
            return shot
        }

        fun fireWithoutAim(): Shot? = fire()

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
            mourning.quest.syncState(loaded)
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
        const val ELUNED = "npc.roving_female_woodelf"
        const val ELUNED_ENCHANT = "npc.roving_female_woodelf_2op"
        const val ARIANWYN = "npc.mourning_arianwyn"
        const val TEGID = "npc.eadgar_druid_washing"
        const val ORONWEN = "npc.mourning_seamstress"
        const val ESSYLLT = "npc.mourner_hideout_head_mourner"
        const val GNOME = "npc.mourner_hideout_gnome"
        const val ELENA = "npc.elena2"
        const val SWAMP_TOAD = "npc.toad"
        const val TREE_GATE = "loc.elf_village_treegate"
        const val BASKET = "loc.eadgar_laundry_basket"
        const val RACK = "loc.mourning_gnome_rack"
        const val APPLE_PILE = "loc.mourning_orchard_applepile"
        const val PRESS = "loc.mourning_orchard_applebarrel_empty"
        const val HQ_RANGE = "loc.range"
        val BASKET_TILE = CoordGrid(2912, 3418, 0)
        val RACK_TILE = CoordGrid(2035, 4629, 0)
        val ORCHARD_PILE = CoordGrid(2487, 3374, 0)
        val ORCHARD_PRESS = CoordGrid(2484, 3374, 0)
        val HQ_RANGE_TILE = CoordGrid(2547, 3322, 0)

        val AREAS =
            listOf(
                intArrayOf(2280, 3140, 2360, 3200),
                intArrayOf(2900, 3405, 2925, 3430),
                intArrayOf(2460, 3264, 2560, 3340),
                intArrayOf(2470, 3355, 2500, 3390),
                intArrayOf(2580, 3320, 2600, 3345),
                intArrayOf(2024, 4620, 2050, 4656),
                intArrayOf(2330, 3055, 2345, 3070),
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
