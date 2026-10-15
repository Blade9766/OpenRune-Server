package org.rsmod.content.quest.area.morytania.ghostsahoy

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
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNotSame
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
import org.rsmod.api.death.NpcDeath
import org.rsmod.api.inv.storage.PlayerItemStorage
import org.rsmod.api.invtx.InvTransactionsScript
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.player.dialogue.align.TextAlignment
import org.rsmod.api.player.events.interact.HeldObjEvents
import org.rsmod.api.player.events.interact.NpcEvents
import org.rsmod.api.player.hit.modifier.NoopPlayerHitModifier
import org.rsmod.api.player.hook.PlayerTeleportValidateHook
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
import org.rsmod.content.generic.locs.spade.SpadeScript
import org.rsmod.content.quest.area.SpadeDigging
import org.rsmod.content.quest.area.goblinvillage.goblindiplomacy.GoblinDyes
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.AK_HARANU
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.BEDSHEET
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.BEDSHEET_SLIMED
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.BOAT_GIVEN
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.BOAT_RETURNED
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.BONE_KEY
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.BOOK
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.BOWL
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.BOWL_OF_NETTLE_WATER
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.BOWL_OF_TEA
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.BOWL_OF_WATER
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.BOW_ASKED
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.BOW_SIGNED
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.BOW_TRADED
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.BUCKET
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.BUCKET_OF_MILK
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.BUCKET_OF_SLIME
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.CAPTAIN
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.CHARMED_RING
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.CHEST_KEY
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.COINS
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.CRONE
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.CUP_OF_MILKY_TEA
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.CUP_OF_TEA
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.ECTOPHIAL
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.ECTOPHIAL_EMPTY
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.ECTOTOKEN
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.GHOSTSPEAK
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.GHOSTSPEAK_ENCHANTED
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.GRAVINGAS
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.INNKEEPER
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.KNIFE
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.MANUAL
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.MAP
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.NECROVARUS
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.NEEDLE
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.NETTLES
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.OAK_LONGBOW
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.OLD_MAN
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.PETITION
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.PORCELAIN_CUP
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.ROBES
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.ROBIN
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.SCRAP_1
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.SCRAP_2
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.SCRAP_3
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.SIGNATURES_NEEDED
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.SIGNED_OAK_LONGBOW
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.SILK
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.SPADE
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.STAGE_COMPLETE
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.STAGE_ENCHANTED
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.STAGE_FIND_CRONE
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.STAGE_GATHER
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.STAGE_REFUSED
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.STAGE_RELEASED
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.STAGE_STARTED
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.TEA_ASKED
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.TEA_DRUNK
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.THREAD
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.TOY_BOAT
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.TOY_BOAT_REPAIRED
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.VELORINA
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.VILLAGER
import org.rsmod.content.quest.area.morytania.ghostsahoy.RuneDrawGame.Rune
import org.rsmod.content.quest.area.morytania.ghostsahoy.npcs.AkHaranu
import org.rsmod.content.quest.area.morytania.ghostsahoy.npcs.GhostCaptain
import org.rsmod.content.quest.area.morytania.ghostsahoy.npcs.GhostInnkeeper
import org.rsmod.content.quest.area.morytania.ghostsahoy.npcs.Necrovarus
import org.rsmod.content.quest.area.morytania.ghostsahoy.npcs.OldCrone
import org.rsmod.content.quest.area.morytania.ghostsahoy.npcs.OldMan
import org.rsmod.content.quest.area.morytania.ghostsahoy.npcs.Petition
import org.rsmod.content.quest.area.morytania.ghostsahoy.npcs.Robin
import org.rsmod.content.quest.area.morytania.ghostsahoy.npcs.Velorina
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
 * Drives Ghosts Ahoy's real scripts through the event bus: starting and the early stages, the
 * tea, the model ship and its flag, the three distinct map scraps and the lobster's owner, the
 * island fare and the dig, Rune-Draw settlement, the petition, the robes, the enchantment and
 * the command, the toll barrier, the ectophial, full packs, logging out and back in, a reward
 * that is given once, and two players on the quest at the same time.
 */
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class GhostsAhoyInteractionTest {

    @Test fun `ghosts can't be understood without a ghostspeak amulet`() {
        val f = Fixture()
        f.talk(VELORINA)
        assertEquals(0, f.stage())
        assertTrue(f.said("can't understand"))
    }

    @Test fun `the prerequisites gate starting the quest, not the skills`() {
        val f = Fixture()
        f.wear(GHOSTSPEAK)
        respectingProgress {
            f.choose(1)
            f.talk(VELORINA)
        }
        assertEquals(0, f.stage(), "Priest in Peril and The Restless Ghost aren't done")
        assertTrue(f.said("Priest in Peril"))
        f.choose(1, 1)
        f.talk(VELORINA)
        assertEquals(STAGE_STARTED, f.stage(), "no Agility or Cooking level is needed to accept")
    }

    @Test fun `Velorina, Necrovarus and Velorina again lead to the Old Crone`() {
        val f = Fixture()
        f.wear(GHOSTSPEAK)
        f.choose(1, 1)
        f.talk(VELORINA)
        f.talk(NECROVARUS)
        assertEquals(STAGE_REFUSED, f.stage())
        f.talk(VELORINA)
        assertEquals(STAGE_FIND_CRONE, f.stage())
        assertTrue(f.journal().contains("old crone"))
    }

    @Test fun `the crone only drinks milky nettle tea from her own porcelain cup`() {
        val f = Fixture(STAGE_FIND_CRONE)
        f.give(NETTLES)
        f.give(BOWL_OF_WATER)
        f.use(NETTLES, BOWL_OF_WATER)
        assertEquals(1, f.count(BOWL_OF_NETTLE_WATER))
        f.drop(BOWL_OF_NETTLE_WATER)
        f.talk(CRONE)
        assertEquals(TEA_ASKED, f.ahoy.teaState(f.player))
        f.give(BOWL_OF_TEA)
        f.talk(CRONE)
        assertEquals(1, f.count(PORCELAIN_CUP), "she hands over her cup for a bowl of tea")
        f.use(BOWL_OF_TEA, PORCELAIN_CUP)
        assertEquals(1, f.count(CUP_OF_TEA))
        assertEquals(1, f.count(BOWL))
        f.talk(CRONE)
        assertEquals(STAGE_FIND_CRONE, f.stage(), "no milk, no memory")
        f.give(BUCKET_OF_MILK)
        f.use(BUCKET_OF_MILK, CUP_OF_TEA)
        assertEquals(1, f.count(BUCKET))
        f.talk(CRONE)
        assertEquals(STAGE_GATHER, f.stage())
        assertEquals(TEA_DRUNK, f.ahoy.teaState(f.player))
        assertEquals(0, f.count(CUP_OF_MILKY_TEA), "she keeps the cup")
    }

    @Test fun `the model ship is mended, dyed part by part, repainted and recognised`() {
        val f = Fixture(STAGE_GATHER)
        f.choose(2)
        f.talk(CRONE)
        assertEquals(BOAT_GIVEN, f.ahoy.toyBoat(f.player))
        assertEquals(1, f.count(TOY_BOAT))
        f.held1(TOY_BOAT)
        assertEquals(1, f.count(TOY_BOAT), "nothing to mend it with")
        for (obj in listOf(SILK, NEEDLE, THREAD, KNIFE)) f.give(obj)
        f.held1(TOY_BOAT)
        assertEquals(1, f.count(TOY_BOAT_REPAIRED))
        assertEquals(0, f.count(SILK) + f.count(THREAD))
        assertEquals(1, f.count(NEEDLE), "the needle is kept")
        assertEquals(1, f.count(KNIFE), "the knife is kept")
        f.setTargets(FlagColour.RED, FlagColour.ORANGE, FlagColour.GREEN)

        f.give("obj.reddye")
        f.give("obj.yellowdye")
        f.use("obj.reddye", "obj.yellowdye")
        assertEquals(1, f.count("obj.orangedye"), "red and yellow make orange")

        f.dye("obj.orangedye", FlagPart.TOP)
        f.dye("obj.reddye", FlagPart.BOTTOM)
        f.dye("obj.greendye", FlagPart.SKULL)
        assertEquals(FlagColour.ORANGE, f.ahoy.applied(f.player, FlagPart.TOP))
        f.choose(1)
        f.talk(OLD_MAN)
        assertEquals(BOAT_GIVEN, f.ahoy.toyBoat(f.player), "wrong colours")
        assertTrue(f.said("colours on mine were different") || f.output().contains("colours"))

        f.dye("obj.reddye", FlagPart.TOP)
        f.dye("obj.orangedye", FlagPart.BOTTOM)
        assertTrue(f.ahoy.isFlagCorrect(f.player))
        f.choose(1)
        f.talk(OLD_MAN)
        assertEquals(BOAT_RETURNED, f.ahoy.toyBoat(f.player))
        assertEquals(0, f.count(TOY_BOAT_REPAIRED), "the old man keeps the ship")
        assertEquals(1, f.count(CHEST_KEY))
    }

    @Test fun `the flag's solution is rolled once and survives logging out`() {
        val f = Fixture(STAGE_GATHER)
        var calls = 0
        f.ahoy.ensureTargets(f.player) { calls++; 2 }
        val first = FlagPart.entries.map { f.ahoy.target(f.player, it) }
        f.ahoy.ensureTargets(f.player) { 0 }
        assertEquals(first, FlagPart.entries.map { f.ahoy.target(f.player, it) })
        assertEquals(3, calls)
        f.ahoy.setApplied(f.player, FlagPart.SKULL, FlagColour.PURPLE)
        val loaded = f.saveAndReload()
        assertEquals(first, FlagPart.entries.map { f.ahoy.target(loaded, it) })
        assertEquals(FlagColour.PURPLE, f.ahoy.applied(loaded, FlagPart.SKULL))
    }

    @Test fun `the mast shows the flag only when the wind drops`() {
        val f = Fixture(STAGE_GATHER)
        f.player.coords = Shipwreck.MAST_TILE.translate(-1, 0)
        f.ahoy.setWindHigh(f.player, true)
        f.player.softTimerMap.schedule(Shipwreck.WIND_TIMER, 100, 5)
        f.locOp(Shipwreck.MAST, Shipwreck.MAST_TILE)
        assertTrue(FlagPart.entries.none { f.ahoy.isSeen(f.player, it) })
        f.ahoy.setWindHigh(f.player, false)
        repeat(3) { f.locOp(Shipwreck.MAST, Shipwreck.MAST_TILE) }
        assertTrue(FlagPart.entries.all { f.ahoy.isSeen(f.player, it) })
        val top = checkNotNull(f.ahoy.target(f.player, FlagPart.TOP))
        assertTrue(f.said("top half is ${top.label}"))
    }

    @Test fun `the wind changes without touching an open dialogue and stops off the quarterdeck`() {
        val f = Fixture(STAGE_GATHER)
        f.player.coords = Shipwreck.MAST_TILE.translate(-1, 0)
        f.player.softTimerMap.schedule(Shipwreck.WIND_TIMER, 100, 5)
        f.ahoy.setWindHigh(f.player, true)
        var changed = false
        repeat(50) {
            f.shipwreck.windTick(f.player)
            if (!f.ahoy.isWindHigh(f.player)) changed = true
        }
        assertTrue(changed, "the wind drops now and then")
        assertTrue(Shipwreck.WIND_TIMER in f.player.softTimerMap)
        f.player.coords = Shipwreck.DECK_LANDING
        f.shipwreck.windTick(f.player)
        assertFalse(Shipwreck.WIND_TIMER in f.player.softTimerMap, "the wind stops once off the quarterdeck")
    }

    @Test fun `each map scrap comes from its own chest and only distinct scraps make the map`() {
        val f = Fixture(STAGE_GATHER)
        f.player.coords = Shipwreck.CAPTAINS_CHEST.translate(-1, -1)
        f.locU(Shipwreck.LOCKED_CHEST, Shipwreck.CAPTAINS_CHEST, CHEST_KEY, give = true)
        assertEquals(1, f.count(SCRAP_1))
        assertEquals(0, f.count(CHEST_KEY))
        f.locU(Shipwreck.LOCKED_CHEST, Shipwreck.CAPTAINS_CHEST, CHEST_KEY, give = true)
        assertEquals(1, f.count(SCRAP_1), "no second copy")
        assertEquals(1, f.count(CHEST_KEY), "a key that wasn't needed isn't used up")

        f.player.coords = Shipwreck.ROCK_CHEST.translate(-1, 0)
        f.locOp(Shipwreck.CLOSED_CHEST, Shipwreck.ROCK_CHEST)
        f.locOp(Shipwreck.CLOSED_CHEST, Shipwreck.ROCK_CHEST)
        assertEquals(1, f.count(SCRAP_3))

        f.give(SCRAP_1)
        f.use(SCRAP_1, SCRAP_3)
        assertEquals(0, f.count(MAP), "two first scraps don't stand in for the second")
        assertEquals(2, f.count(SCRAP_1))
        f.give(SCRAP_2)
        f.use(SCRAP_2, SCRAP_3)
        assertEquals(1, f.count(MAP))
        assertEquals(1, f.count(SCRAP_1), "only one of each scrap is used")
        assertEquals(0, f.count(SCRAP_2) + f.count(SCRAP_3))
        f.drop(SCRAP_1)
        f.player.coords = Shipwreck.ROCK_CHEST.translate(-1, 0)
        f.locOp(Shipwreck.CLOSED_CHEST, Shipwreck.ROCK_CHEST)
        assertEquals(0, f.count(SCRAP_3), "the chests stay empty once the map is made")
    }

    @Test fun `each player gets their own lobster and only its owner is credited`() {
        val f = Fixture(STAGE_GATHER)
        val (one, two) = f.player to f.second
        f.ahoy.quest.jumpToStage(two, STAGE_GATHER)
        for (p in listOf(one, two)) p.coords = GiantLobster.CHEST_TILE.translate(-1, 1)
        f.searchLobsterChest()
        f.searchLobsterChest()
        assertEquals(1, f.liveLobsters(), "searching again doesn't spawn a second lobster")
        val first = checkNotNull(f.lobster.lobsterOf(one.uid))
        f.searchLobsterChest(two)
        assertEquals(2, f.liveLobsters())
        val second = checkNotNull(f.lobster.lobsterOf(two.uid))
        assertNotSame(first, second)

        assertEquals(one, f.lobster.creditKill(first))
        assertTrue(f.ahoy.isLobsterKilled(one))
        assertFalse(f.ahoy.isLobsterKilled(two), "someone else's kill doesn't count")
        f.searchLobsterChest()
        assertEquals(1, f.count(SCRAP_2))

        two.coords = GiantLobster.CHEST_TILE.translate(-30, 0)
        f.lobster.tick(second)
        assertNull(f.lobster.lobsterOf(two.uid), "the lobster retreats when its owner flees")
        two.coords = GiantLobster.CHEST_TILE.translate(-1, 1)
        f.searchLobsterChest(two)
        val third = checkNotNull(f.lobster.lobsterOf(two.uid)) { "and can be summoned again" }
        f.clock.cycle += GiantLobster.LIFETIME
        f.lobster.tick(third)
        assertNull(f.lobster.lobsterOf(two.uid), "it gives up after a while")
    }

    @Test fun `the island boat charges once for the round trip and the ring charms it down`() {
        val f = Fixture(STAGE_GATHER)
        f.wear(GHOSTSPEAK)
        f.player.coords = PORT
        f.give(ECTOTOKEN, 24)
        f.talk(CAPTAIN)
        assertEquals(PORT, f.player.coords, "24 tokens isn't enough")
        f.give(ECTOTOKEN, 1)
        f.choose(1)
        f.talk(CAPTAIN)
        assertTrue(f.player.coords.x > 3750, "on Dragontooth Island")
        assertEquals(0, f.count(ECTOTOKEN))
        f.choose(1)
        f.talk(CAPTAIN)
        assertEquals(PORT_LANDING, f.player.coords, "the way back is paid for")

        f.wear(CHARMED_RING)
        f.give(ECTOTOKEN, 10)
        f.choose(1, 1)
        f.npcOp3(CAPTAIN)
        assertTrue(f.player.coords.x > 3750)
        assertEquals(0, f.count(ECTOTOKEN), "charmed down to ten")
    }

    @Test fun `the book is dug up only on the map's tile with the map in hand`() {
        val f = Fixture(STAGE_GATHER)
        f.give(SPADE)
        f.player.coords = TreasureMap.DIG_TILE
        f.held1(SPADE)
        assertEquals(0, f.count(BOOK), "no map, no book")
        f.give(MAP)
        f.player.coords = TreasureMap.DIG_TILE.translate(1, 0)
        f.held1(SPADE)
        assertEquals(0, f.count(BOOK), "one tile off")
        f.player.coords = TreasureMap.DIG_TILE
        f.held1(SPADE)
        assertEquals(1, f.count(BOOK))
        f.held1(SPADE)
        assertEquals(1, f.count(BOOK), "only one book")
    }

    @Test fun `rune-draw follows the bag`() {
        val death = RuneDrawGame(listOf(Rune.DEATH) + Rune.entries.filter { it != Rune.DEATH })
        death.playerDraw()
        assertEquals(RuneDrawGame.Result.LOST, death.result)

        val robinDies = RuneDrawGame(listOf(Rune.AIR, Rune.DEATH) + others(Rune.AIR))
        robinDies.playerDraw()
        assertEquals(RuneDrawGame.Result.WON, robinDies.result)

        val chase = RuneDrawGame(listOf(Rune.NATURE, Rune.AIR, Rune.FIRE, Rune.EARTH, Rune.DEATH) + others(Rune.NATURE, Rune.AIR, Rune.FIRE, Rune.EARTH))
        chase.playerDraw()
        chase.playerHold()
        assertEquals(9, chase.playerScore)
        assertEquals(10, chase.robinScore, "Robin draws until he beats a held score")
        assertEquals(RuneDrawGame.Result.LOST, chase.result)

        val tie = RuneDrawGame(listOf(Rune.EARTH, Rune.AIR, Rune.WATER, Rune.DEATH) + others(Rune.EARTH, Rune.AIR, Rune.WATER))
        tie.playerDraw()
        tie.playerHold()
        assertEquals(4, tie.robinScore, "Robin drew level")
        assertEquals(RuneDrawGame.Result.WON, tie.result, "a tie isn't a win, so Robin drew again and hit death")

        val bag = listOf(Rune.AIR, Rune.NATURE, Rune.MIND, Rune.CHAOS, Rune.EARTH, Rune.WATER, Rune.FIRE, Rune.BODY, Rune.COSMIC, Rune.DEATH)
        val held = RuneDrawGame(bag)
        repeat(3) { held.playerDraw() }
        assertTrue(held.robinHeld, "Robin holds on 17 against 7")
        held.playerDraw()
        held.playerDraw()
        assertNull(held.result, "15 doesn't beat 17")
        held.playerDraw()
        assertEquals(21, held.playerScore)
        assertEquals(RuneDrawGame.Result.WON, held.result)
        held.playerDraw()
        assertEquals(21, held.playerScore, "no drawing after the game is over")
        assertEquals(RuneDrawGame.BAG_SIZE, RuneDrawGame.shuffledBag { 0 }.toSet().size)
    }

    @Test fun `robin settles every game once and signs at a hundred coins of debt`() {
        val f = Fixture(STAGE_GATHER)
        f.talk(AK_HARANU)
        assertEquals(BOW_ASKED, f.ahoy.bowState(f.player))
        f.give(COINS, 10_000)
        var wins = 0
        var games = 0
        while (f.ahoy.robinDebt(f.player) < 100) {
            check(games++ < 400) { "never won four games" }
            val before = f.count(COINS)
            val debt = f.ahoy.robinDebt(f.player)
            f.choose(2)
            f.talk(ROBIN)
            val game = checkNotNull(f.robin.gameOf(f.player))
            assertEquals(before - 25, f.count(COINS), "the stake is taken up front")
            repeat(RuneDrawGame.BAG_SIZE) { if (!game.isOver) f.robinDraw() }
            assertTrue(game.isOver)
            assertNull(f.robin.gameOf(f.player))
            f.robinDraw()
            f.robinHold()
            if (game.result == RuneDrawGame.Result.WON) {
                wins++
                assertEquals(before, f.count(COINS), "a win returns the stake")
                assertEquals(debt + 25, f.ahoy.robinDebt(f.player))
            } else {
                assertEquals(before - 25, f.count(COINS), "a loss keeps it")
                assertEquals(debt, f.ahoy.robinDebt(f.player), "a loss doesn't cut Robin's debt")
            }
        }
        assertEquals(4, wins)
        val coins = f.count(COINS)
        f.choose(2)
        f.talk(ROBIN)
        assertNull(f.robin.gameOf(f.player), "he won't play while he owes 100")
        assertEquals(coins, f.count(COINS))

        f.choose(1)
        f.talk(ROBIN)
        assertEquals(0, f.count(SIGNED_OAK_LONGBOW), "no bow to sign")
        f.give(OAK_LONGBOW)
        f.choose(1)
        f.talk(ROBIN)
        assertEquals(BOW_SIGNED, f.ahoy.bowState(f.player))
        assertEquals(1, f.count(SIGNED_OAK_LONGBOW))
        f.fill()
        f.talk(AK_HARANU)
        assertEquals(BOW_TRADED, f.ahoy.bowState(f.player), "the swap fits even in a full pack")
        assertEquals(1, f.count(MANUAL))
        assertEquals(0, f.count(SIGNED_OAK_LONGBOW))
    }

    @Test fun `walking away from a rune-draw game forfeits the stake`() {
        val f = Fixture(STAGE_GATHER)
        f.give(COINS, 100)
        f.choose(1)
        f.talk(ROBIN)
        assertNotNull(f.robin.gameOf(f.player))
        f.dispatch { ifClose() }
        assertNull(f.robin.gameOf(f.player))
        assertEquals(75, f.count(COINS))
        f.robinDraw()
        assertEquals(75, f.count(COINS))
    }

    @Test fun `ten disguised signatures, never twice in a row from the same ghost`() {
        val f = Fixture(STAGE_GATHER)
        f.wear(GHOSTSPEAK)
        f.choose(1)
        f.talk(GRAVINGAS)
        assertEquals(1, f.count(PETITION))
        f.talk(VILLAGER, VILLAGER_A)
        assertEquals(0, f.ahoy.signatures(f.player), "a mortal gets no signatures")
        f.give(BEDSHEET)
        f.give(BUCKET_OF_SLIME)
        f.use(BUCKET_OF_SLIME, BEDSHEET)
        assertEquals(1, f.count(BEDSHEET_SLIMED))
        f.wearFromInv(BEDSHEET_SLIMED)

        f.ahoy.setLastSigner(f.player, 0)
        f.talk(VILLAGER, VILLAGER_A)
        val afterFirst = f.ahoy.signatures(f.player)
        f.talk(VILLAGER, VILLAGER_A)
        assertEquals(afterFirst, f.ahoy.signatures(f.player), "the same ghost twice in a row")
        assertTrue(f.said("a moment ago"))

        var asks = 0
        while (f.ahoy.signatures(f.player) < SIGNATURES_NEEDED) {
            check(asks++ < 200) { "never collected the signatures" }
            f.choose(2)
            f.talk(VILLAGER, if (asks % 2 == 0) VILLAGER_A else VILLAGER_B)
        }
        repeat(4) { f.talk(VILLAGER, if (it % 2 == 0) VILLAGER_A else VILLAGER_B) }
        assertEquals(SIGNATURES_NEEDED, f.ahoy.signatures(f.player), "capped at ten")

        f.talk(GRAVINGAS)
        assertTrue(f.ahoy.isPetitionApproved(f.player))
        f.talk(NECROVARUS)
        assertEquals(0, f.count(PETITION), "Necrovarus burns it")
        assertTrue(f.ahoy.isPetitionPresented(f.player))
        val keys = f.groundKeys()
        assertEquals(1, keys.size)
        assertEquals(f.player.observerUUID, keys.single().receiverId, "only its finder can see the key")
    }

    @Test fun `the bone key opens the robing room for its finder alone`() {
        val f = Fixture(STAGE_GATHER)
        val door = TempleRobes.TEMPLE_DOOR
        f.player.coords = door.translate(-1, 0)
        f.locOp(TempleRobes.DOOR, door)
        assertTrue(f.said("The door is locked."))
        f.locU(TempleRobes.DOOR, door, BONE_KEY, give = true)
        assertFalse(f.ahoy.isTempleUnlocked(f.player), "a key picked up by someone who never presented the petition")
        f.ahoy.setPetitionPresented(f.player)
        f.locU(TempleRobes.DOOR, door, BONE_KEY)
        assertTrue(f.ahoy.isTempleUnlocked(f.player))
        assertFalse(f.ahoy.isTempleUnlocked(f.second))
        f.player.coords = CoordGrid(3658, 3512, 1)
        f.locOp(TempleRobes.COFFIN, CoordGrid(3659, 3513, 1))
        f.locOp(TempleRobes.COFFIN, CoordGrid(3659, 3513, 1))
        assertEquals(1, f.count(ROBES))
    }

    @Test fun `the crone enchants the amulet only with all three items and the amulet to hand`() {
        val f = Fixture(STAGE_GATHER)
        for (obj in listOf(BOOK, MANUAL, ROBES)) f.give(obj)
        f.choose(3)
        f.talk(CRONE)
        assertTrue(f.ahoy.hasGivenAll(f.player))
        assertEquals(0, f.count(BOOK) + f.count(MANUAL) + f.count(ROBES))
        assertEquals(STAGE_GATHER, f.stage(), "no amulet, no enchantment")
        f.wear(GHOSTSPEAK)
        f.talk(CRONE)
        assertEquals(STAGE_ENCHANTED, f.stage())
        assertTrue(f.player.worn.contains(GHOSTSPEAK_ENCHANTED))
        assertFalse(f.player.worn.contains(GHOSTSPEAK))
    }

    @Test fun `jokes don't spend the amulet but the release does`() {
        val f = Fixture(STAGE_ENCHANTED)
        f.wear(GHOSTSPEAK_ENCHANTED)
        f.choose(2)
        f.talk(NECROVARUS)
        f.choose(3)
        f.talk(NECROVARUS)
        assertEquals(STAGE_ENCHANTED, f.stage())
        assertTrue(f.player.worn.contains(GHOSTSPEAK_ENCHANTED))
        f.choose(1)
        f.talk(NECROVARUS)
        assertEquals(STAGE_RELEASED, f.stage())
        assertTrue(f.player.worn.contains(GHOSTSPEAK), "the amulet is ordinary again")
        assertFalse(f.player.worn.contains(GHOSTSPEAK_ENCHANTED))
        assertTrue(f.player.hasGhostspeak())
    }

    @Test fun `Velorina completes the quest once, with room for the ectophial`() {
        val f = Fixture(STAGE_RELEASED)
        f.wear(GHOSTSPEAK)
        f.fill()
        f.talk(VELORINA)
        assertEquals(STAGE_RELEASED, f.stage(), "not with a full pack")
        f.drop("obj.bronze_dagger")
        f.talk(VELORINA)
        assertEquals(STAGE_COMPLETE, f.stage())
        assertEquals(2, f.player.vars["varp.qp"])
        assertEquals(2400, f.player.statMap.getXP("stat.prayer"))
        assertEquals(1, f.count(ECTOPHIAL))
        f.talk(VELORINA)
        assertEquals(2, f.player.vars["varp.qp"], "rewards are given once")
        assertEquals(2400, f.player.statMap.getXP("stat.prayer"))
        assertEquals(1, f.count(ECTOPHIAL), "she doesn't hand out another while you have one")
        f.drop(ECTOPHIAL)
        f.talk(VELORINA)
        assertEquals(1, f.count(ECTOPHIAL), "a lost ectophial is replaced")
        assertTrue(f.ahoy.quest.isQuestCompleted(f.saveAndReload()))
    }

    @Test fun `the toll is paid on the way in only, and waived after the release`() {
        val f = Fixture(STAGE_GATHER)
        val gate = PhasmatysBarrier.Gate.SOUTH
        val outside = CoordGrid(3669, 3452, 0)
        f.player.coords = outside
        f.give(ECTOTOKEN, 1)
        f.locOp(PhasmatysBarrier.BARRIER, gate.origin)
        assertEquals(outside, f.player.coords)
        f.give(ECTOTOKEN, 1)
        f.choose(1)
        f.locOp(PhasmatysBarrier.BARRIER, gate.origin)
        assertTrue(gate.isInside(f.player.coords))
        assertEquals(0, f.count(ECTOTOKEN))
        f.locOp(PhasmatysBarrier.BARRIER, gate.origin)
        assertEquals(outside, f.player.coords, "leaving is free")
        f.give(ECTOTOKEN, 2)
        f.locOp(PhasmatysBarrier.BARRIER, gate.origin, InteractionOp.Op4)
        assertTrue(gate.isInside(f.player.coords), "pay-toll pays and passes")
        assertEquals(0, f.count(ECTOTOKEN))
        f.locOp(PhasmatysBarrier.BARRIER, gate.origin)
        f.jump(STAGE_RELEASED)
        f.locOp(PhasmatysBarrier.BARRIER, gate.origin)
        assertTrue(gate.isInside(f.player.coords), "free after the release")
    }

    @Test fun `the ectophial teleports, refills and can't be emptied twice`() {
        val f = Fixture(STAGE_COMPLETE)
        f.player.coords = CoordGrid(3688, 3470, 0)
        f.give(ECTOPHIAL)
        f.teleportDenial = "A magical force stops you from teleporting."
        f.held1(ECTOPHIAL)
        assertEquals(CoordGrid(3688, 3470, 0), f.player.coords)
        assertEquals(1, f.count(ECTOPHIAL), "a blocked teleport keeps the phial full")
        f.teleportDenial = null
        f.held1(ECTOPHIAL)
        assertEquals(Ectophial.ARRIVAL, f.player.coords)
        assertEquals(1, f.count(ECTOPHIAL), "refilled on arrival")
        assertEquals(0, f.count(ECTOPHIAL_EMPTY))
        f.drop(ECTOPHIAL)
        f.give(ECTOPHIAL_EMPTY)
        assertFalse(f.held1Handled(ECTOPHIAL_EMPTY), "an empty phial can't be emptied")
        f.locU(Ectophial.ECTOFUNTUS, Ectophial.ECTOFUNTUS_TILE, ECTOPHIAL_EMPTY)
        assertEquals(1, f.count(ECTOPHIAL), "an interrupted refill can be finished by hand")
    }

    @Test fun `the innkeeper's bedsheet comes again if lost, and dips green in the slime pool`() {
        val f = Fixture(STAGE_GATHER)
        f.wear(GHOSTSPEAK)
        f.choose(1, 1)
        f.talk(INNKEEPER)
        assertEquals(1, f.count(BEDSHEET))
        assertTrue(f.ahoy.isSheetRequested(f.player))
        f.choose(1)
        f.talk(INNKEEPER)
        assertEquals(1, f.count(BEDSHEET), "only one while you have it")
        f.drop(BEDSHEET)
        f.choose(1)
        f.talk(INNKEEPER)
        assertEquals(1, f.count(BEDSHEET))
        f.player.coords = CoordGrid(3683, 9888, 0)
        f.locU("loc.ahoy_new_green_floor", CoordGrid(3684, 9888, 0), BEDSHEET)
        assertEquals(1, f.count(BEDSHEET_SLIMED))
    }

    @Test fun `a full pack never loses a quest item`() {
        val f = Fixture(STAGE_GATHER)
        f.fill()
        f.choose(2)
        f.talk(CRONE)
        assertEquals(0, f.count(TOY_BOAT))
        assertEquals(0, f.ahoy.toyBoat(f.player), "no ship handed over into a full pack")
        f.drop("obj.bronze_dagger")
        f.give(CHEST_KEY)
        f.player.coords = Shipwreck.CAPTAINS_CHEST.translate(-1, -1)
        f.locU(Shipwreck.LOCKED_CHEST, Shipwreck.CAPTAINS_CHEST, CHEST_KEY)
        assertEquals(1, f.count(SCRAP_1), "the key's slot holds the scrap")
        f.player.coords = TreasureMap.DIG_TILE
        f.drop("obj.bronze_dagger")
        f.drop("obj.bronze_dagger")
        f.give(MAP)
        f.give(SPADE)
        f.held1(SPADE)
        assertEquals(0, f.count(BOOK), "no room for the book")
        assertTrue(f.ahoy.needsBook(f.player), "so it can be dug up again")
    }

    @Test fun `two players progress side by side without touching each other's state`() {
        val f = Fixture(STAGE_GATHER)
        val (one, two) = f.player to f.second
        f.ahoy.quest.jumpToStage(two, STAGE_STARTED)
        f.ahoy.ensureTargets(one) { 0 }
        f.ahoy.ensureTargets(two) { 5 }
        f.ahoy.setSignatures(one, 7)
        f.ahoy.setBowState(one, BOW_ASKED + 2)
        f.ahoy.setLobsterKilled(one)
        assertEquals(FlagColour.RED, f.ahoy.target(one, FlagPart.TOP))
        assertEquals(FlagColour.PURPLE, f.ahoy.target(two, FlagPart.TOP))
        assertEquals(0, f.ahoy.signatures(two))
        assertEquals(0, f.ahoy.robinDebt(two))
        assertFalse(f.ahoy.isLobsterKilled(two))
        assertEquals(STAGE_GATHER, f.stage())
        assertEquals(STAGE_STARTED, f.ahoy.stage(two))
        assertFalse(f.ahoy.hasFreePassage(two))
    }

    @Test fun `logging out and back in keeps every objective`() {
        val f = Fixture(STAGE_GATHER)
        f.ahoy.setTeaState(f.player, TEA_DRUNK)
        f.ahoy.setToyBoat(f.player, BOAT_RETURNED)
        f.ahoy.setBowState(f.player, BOW_ASKED + 3)
        f.ahoy.setSignatures(f.player, 6)
        f.ahoy.setLastSigner(f.player, VILLAGER_A.packed)
        f.ahoy.setLobsterKilled(f.player)
        f.ahoy.setTempleUnlocked(f.player)
        f.ahoy.setGiven(f.player, BOOK)
        f.ahoy.setPetitionApproved(f.player, true)
        val p = f.saveAndReload()
        assertEquals(STAGE_GATHER, f.ahoy.stage(p))
        assertEquals(TEA_DRUNK, f.ahoy.teaState(p))
        assertEquals(BOAT_RETURNED, f.ahoy.toyBoat(p))
        assertEquals(75, f.ahoy.robinDebt(p))
        assertEquals(6, f.ahoy.signatures(p))
        assertEquals(VILLAGER_A.packed, f.ahoy.lastSigner(p))
        assertTrue(f.ahoy.isLobsterKilled(p) && f.ahoy.isTempleUnlocked(p) && f.ahoy.givenBook(p) && f.ahoy.isPetitionApproved(p))
        assertFalse(f.ahoy.givenManual(p))
    }

    @Test fun `resetting the quest clears every objective`() {
        val f = Fixture(STAGE_GATHER)
        f.ahoy.ensureTargets(f.player) { 1 }
        f.ahoy.setSignatures(f.player, 4)
        f.ahoy.setToyBoat(f.player, BOAT_GIVEN)
        f.ahoy.quest.resetQuest(f.player)
        assertEquals(0, f.stage())
        assertNull(f.ahoy.target(f.player, FlagPart.TOP))
        assertEquals(0, f.ahoy.signatures(f.player))
        assertEquals(0, f.ahoy.toyBoat(f.player))
    }

    private fun others(vararg drawn: Rune): List<Rune> = Rune.entries.filter { it != Rune.DEATH && it !in drawn }

    private class Fixture(stage: Int = 0) {
        val events = EventBus()
        private val client = RecordingClient()
        private val collision = CollisionFlagMap()
        private val npcs = NpcList()
        private val players = PlayerList()
        private val coroutine = GameCoroutine("ghosts-ahoy-test")
        private var result: Result<Unit>? = null
        var teleportDenial: String? = null
        private val validator = PlayerTeleportValidator(setOf(PlayerTeleportValidateHook { _, _, _ -> teleportDenial }))
        private val context = ProtectedAccessContextFactory.empty().copy(
            getEventBus = { events }, getAlignment = { TextAlignment() },
            getCollision = { collision }, getNpcList = { npcs },
            getTeleportValidator = { validator },
            getAreaChecker = { unused<AreaChecker>() },
            getNpcInteractions = { NpcInteractions(events) },
            getRandom = { DefaultGameRandom(Random(11)) },
            getHitModifier = { NoopPlayerHitModifier },
        )
        val clock = MapClock(100)
        private val objRegistry = ObjRegistry(ZoneUpdateMap())
        private val objRepo = ObjRepository(clock, objRegistry)
        private val npcRepo: NpcRepository
        private val picks = ArrayDeque<Int>()
        private val locU = LocUInteractions::class.java.getDeclaredConstructor(EventBus::class.java)
            .apply { isAccessible = true }.newInstance(events)
        private val heldU = HeldUInteractions(events)

        val player = newPlayer(4141L, 1)
        val second = newPlayer(4242L, 2)
        private var active = player

        val ahoy = GhostsAhoyQuest()
        val robin: Robin
        val lobster: GiantLobster
        val shipwreck: Shipwreck

        init {
            for ((x0, z0, x1, z1) in AREAS) {
                for (level in 0..2) for (x in x0..x1 step 8) for (z in z0..z1 step 8) collision.allocateIfAbsent(x, z, level)
            }
            players[player.slotId] = player
            players[second.slotId] = second
            npcRepo = NpcRepository(clock, NpcRegistry(npcs, collision, events), npcs)
            robin = Robin(ahoy, objRepo)
            lobster = GiantLobster(ahoy, npcRepo, players, AiPlayerInteractions(events, players), unused<NpcDeath>(), clock)
            val spade = SpadeDigging()
            shipwreck = Shipwreck(ahoy, unused(), DefaultGameRandom(Random(3)), events)
            val passages = unused<GenericPassageScript>()
            val scripts = ScriptContext(events, CheatCommandMap(), EngineQueueCache())
            for (script in listOf(
                ahoy, robin, lobster, SpadeScript(setOf(spade)), TreasureMap(ahoy, spade), Velorina(ahoy), Necrovarus(ahoy, objRepo),
                OldCrone(ahoy), GhostInnkeeper(ahoy), Petition(ahoy), AkHaranu(ahoy), OldMan(ahoy), GhostCaptain(ahoy),
                NettleTea(), ModelShip(ahoy), shipwreck, TempleRobes(ahoy, passages),
                Ectophial(validator, unused()), PhasmatysBarrier(ahoy), GoblinDyes(),
            )) {
                with(script) { scripts.startup() }
            }
            if (stage > 0) ahoy.quest.jumpToStage(player, stage)
        }

        @OptIn(InternalApi::class)
        private fun newPlayer(id: Long, slot: Int) = Player().apply {
            this.client = this@Fixture.client
            uuid = id
            observerUUID = id
            slotId = slot
            assignUid()
            coords = CoordGrid(3680, 3490, 0)
            currentMapClock = 100
            processedMapClock = 100
            pendingSequence = EntitySeq.NULL
            pendingFaceAngle = EntityFaceAngle.NULL
            inv = Inventory(InventoryServerType(size = 28, flags = 0), arrayOfNulls(28))
            worn = Inventory(InventoryServerType(size = 14, flags = 0), arrayOfNulls(14))
            for (stat in listOf("stat.hitpoints", "stat.agility", "stat.cooking")) {
                statMap.setBaseLevel(stat, 40)
                statMap.setCurrentLevel(stat, 40)
            }
        }

        fun access(p: Player = active) = ProtectedAccess(p, coroutine, context)

        fun stage(): Int = ahoy.stage(player)

        fun jump(stage: Int) = ahoy.quest.jumpToStage(player, stage)

        fun journal(): String = ahoy.questLog(access())

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

        fun wear(obj: String) {
            val type = checkNotNull(ServerCacheManager.getItem(obj.asRSCM()))
            player.worn[type.wearpos1] = InvObj(obj, 1)
        }

        fun wearFromInv(obj: String) {
            drop(obj)
            wear(obj)
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

        fun setTargets(top: FlagColour, bottom: FlagColour, skull: FlagColour) {
            val picksFor = ArrayDeque(listOf(top, bottom, skull).map { FlagColour.entries.indexOf(it) })
            ahoy.ensureTargets(player) { picksFor.removeFirst() }
        }

        fun dye(dye: String, part: FlagPart) {
            if (count(dye) == 0) give(dye)
            choose(part.ordinal + 1)
            use(dye, TOY_BOAT_REPAIRED)
        }

        fun liveLobsters(): Int = npcs.count { it != null && it.isSlotAssigned && it.id == GhostsAhoyQuest.GIANT_LOBSTER.asRSCM() }

        fun searchLobsterChest(p: Player = player) {
            active = p
            try {
                locOp(GiantLobster.CHEST, GiantLobster.CHEST_TILE)
            } finally {
                active = player
            }
        }

        fun groundKeys() = objRegistry.findAll(player.coords.translateX(1)).filter { it.type == BONE_KEY.asRSCM() }.toList()

        fun talk(type: String, at: CoordGrid = player.coords.translateX(1)) {
            val npc = Npc(type, at)
            npcRepo.add(npc, Int.MAX_VALUE)
            publish(NpcEvents.Op1(npc))
            if (npc.isSlotAssigned) npcRepo.del(npc, Int.MAX_VALUE)
        }

        fun npcOp3(type: String) {
            val npc = Npc(type, player.coords.translateX(1))
            npcRepo.add(npc, Int.MAX_VALUE)
            publish(NpcEvents.Op3(npc))
            if (npc.isSlotAssigned) npcRepo.del(npc, Int.MAX_VALUE)
        }

        fun held1(obj: String) {
            check(held1Handled(obj)) { "No op1 handler for $obj" }
        }

        fun held1Handled(obj: String): Boolean {
            val slot = player.inv.indexOfFirst { it?.id == obj.asRSCM() }
            val type = checkNotNull(ServerCacheManager.getItem(obj.asRSCM()))
            var handled = false
            dispatch { handled = events.publish(this, HeldObjEvents.Op1(slot, checkNotNull(inv[slot]), type, inv)) }
            return handled
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

        fun locU(symbol: String, coords: CoordGrid, obj: String, give: Boolean = false) {
            if (give) give(obj)
            val loc = bound(symbol, coords)
            val locType = checkNotNull(ServerCacheManager.getObject(symbol.asRSCM()))
            val objType = checkNotNull(ServerCacheManager.getItem(obj.asRSCM()))
            val slot = player.inv.indexOfFirst { it?.id == obj.asRSCM() }
            val event = with(locU) { access().opTrigger(loc, loc, locType, objType, slot) }
            publish(checkNotNull(event) { "No $obj handler for $symbol" })
        }

        /** A click on the open board, which (unlike walking off or a new action) keeps it open. */
        fun robinDraw() = dispatch(closeModals = false) { with(robin) { draw() } }

        fun robinHold() = dispatch(closeModals = false) { with(robin) { hold() } }

        fun saveAndReload(): Player {
            val loaded = newPlayer(player.uuid ?: 0L, 3)
            for ((varp, value) in player.vars.backing) {
                val scope = ServerCacheManager.getVarp(varp)?.scope
                if (scope != VarpLifetime.Temp) loaded.vars.backing[varp] = value
            }
            player.attr[QUEST_STAGE_MAP_ATTR]?.let { loaded.attr[QUEST_STAGE_MAP_ATTR] = it.toMutableMap() }
            ahoy.quest.syncState(loaded)
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
        val PORT = CoordGrid(3702, 3489, 0)
        val PORT_LANDING = CoordGrid(3701, 3487, 0)
        val VILLAGER_A = CoordGrid(3670, 3480, 0)
        val VILLAGER_B = CoordGrid(3672, 3480, 0)

        val AREAS =
            listOf(
                intArrayOf(3640, 3440, 3720, 3530),
                intArrayOf(3584, 3528, 3632, 3576),
                intArrayOf(3776, 3520, 3816, 3568),
                intArrayOf(3672, 9880, 3696, 9896),
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
