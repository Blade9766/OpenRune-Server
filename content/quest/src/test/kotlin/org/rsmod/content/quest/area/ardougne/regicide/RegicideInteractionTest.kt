package org.rsmod.content.quest.area.ardougne.regicide

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.InventoryServerType
import dev.openrune.types.varp.VarpLifetime
import jakarta.inject.Provider
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
import org.rsmod.api.death.NpcAttackValidateResult
import org.rsmod.api.death.NpcDeath
import org.rsmod.api.death.NpcDeathKillContext
import org.rsmod.api.inv.storage.PlayerItemStorage
import org.rsmod.api.invtx.InvTransactionsScript
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.player.dialogue.align.TextAlignment
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
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.protect.clearPendingAction
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.random.DefaultGameRandom
import org.rsmod.api.registry.npc.NpcRegistry
import org.rsmod.api.registry.obj.ObjRegistry
import org.rsmod.api.registry.zone.ZoneUpdateMap
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.repo.region.RegionRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.route.BoundValidator
import org.rsmod.content.quest.area.ardougne.biohazard.BiohazardQuest
import org.rsmod.content.quest.area.ardougne.biohazard.KingLathas
import org.rsmod.content.quest.area.ardougne.biohazard.Smuggling
import org.rsmod.content.quest.area.ardougne.regicide.RegicideQuest.Companion.BARREL
import org.rsmod.content.quest.area.ardougne.regicide.RegicideQuest.Companion.BARREL_BOMB
import org.rsmod.content.quest.area.ardougne.regicide.RegicideQuest.Companion.BARREL_OF_COAL_TAR
import org.rsmod.content.quest.area.ardougne.regicide.RegicideQuest.Companion.BARREL_OF_NAPHTHA
import org.rsmod.content.quest.area.ardougne.regicide.RegicideQuest.Companion.BOOK
import org.rsmod.content.quest.area.ardougne.regicide.RegicideQuest.Companion.CAMP_GUARD
import org.rsmod.content.quest.area.ardougne.regicide.RegicideQuest.Companion.CATAPULT_GUARD
import org.rsmod.content.quest.area.ardougne.regicide.RegicideQuest.Companion.CHEMIST
import org.rsmod.content.quest.area.ardougne.regicide.RegicideQuest.Companion.COAL
import org.rsmod.content.quest.area.ardougne.regicide.RegicideQuest.Companion.COINS
import org.rsmod.content.quest.area.ardougne.regicide.RegicideQuest.Companion.COOKED_RABBIT
import org.rsmod.content.quest.area.ardougne.regicide.RegicideQuest.Companion.ENCOUNTER_GUARD
import org.rsmod.content.quest.area.ardougne.regicide.RegicideQuest.Companion.GROUND_SULPHUR
import org.rsmod.content.quest.area.ardougne.regicide.RegicideQuest.Companion.HINING
import org.rsmod.content.quest.area.ardougne.regicide.RegicideQuest.Companion.IORWERTH
import org.rsmod.content.quest.area.ardougne.regicide.RegicideQuest.Companion.IORWERTH_MESSAGE
import org.rsmod.content.quest.area.ardougne.regicide.RegicideQuest.Companion.KINGS_MESSAGE
import org.rsmod.content.quest.area.ardougne.regicide.RegicideQuest.Companion.KING_LATHAS
import org.rsmod.content.quest.area.ardougne.regicide.RegicideQuest.Companion.LIMESTONE
import org.rsmod.content.quest.area.ardougne.regicide.RegicideQuest.Companion.NAPHTHA_QUICKLIME_MIX
import org.rsmod.content.quest.area.ardougne.regicide.RegicideQuest.Companion.NAPHTHA_SULPHUR_MIX
import org.rsmod.content.quest.area.ardougne.regicide.RegicideQuest.Companion.PENDANT
import org.rsmod.content.quest.area.ardougne.regicide.RegicideQuest.Companion.PESTLE_AND_MORTAR
import org.rsmod.content.quest.area.ardougne.regicide.RegicideQuest.Companion.POT
import org.rsmod.content.quest.area.ardougne.regicide.RegicideQuest.Companion.POT_OF_QUICKLIME
import org.rsmod.content.quest.area.ardougne.regicide.RegicideQuest.Companion.QUICKLIME
import org.rsmod.content.quest.area.ardougne.regicide.RegicideQuest.Companion.ROAST_RABBIT
import org.rsmod.content.quest.area.ardougne.regicide.RegicideQuest.Companion.STAGE_COMPLETE
import org.rsmod.content.quest.area.ardougne.regicide.RegicideQuest.Companion.STAGE_DENSE_FOREST
import org.rsmod.content.quest.area.ardougne.regicide.RegicideQuest.Companion.STAGE_FOUND_CAMP
import org.rsmod.content.quest.area.ardougne.regicide.RegicideQuest.Companion.STAGE_FOUND_TRACKS
import org.rsmod.content.quest.area.ardougne.regicide.RegicideQuest.Companion.STAGE_GOT_PENDANT
import org.rsmod.content.quest.area.ardougne.regicide.RegicideQuest.Companion.STAGE_GUARD_KILLED
import org.rsmod.content.quest.area.ardougne.regicide.RegicideQuest.Companion.STAGE_LETTER
import org.rsmod.content.quest.area.ardougne.regicide.RegicideQuest.Companion.STAGE_LETTER_UNSEALED
import org.rsmod.content.quest.area.ardougne.regicide.RegicideQuest.Companion.STAGE_MAKE_BOMB
import org.rsmod.content.quest.area.ardougne.regicide.RegicideQuest.Companion.STAGE_MET_ELVES
import org.rsmod.content.quest.area.ardougne.regicide.RegicideQuest.Companion.STAGE_MET_IORWERTH
import org.rsmod.content.quest.area.ardougne.regicide.RegicideQuest.Companion.STAGE_STARTED
import org.rsmod.content.quest.area.ardougne.regicide.RegicideQuest.Companion.STAGE_TRACKER_HELPING
import org.rsmod.content.quest.area.ardougne.regicide.RegicideQuest.Companion.STAGE_TRACKER_REFUSED
import org.rsmod.content.quest.area.ardougne.regicide.RegicideQuest.Companion.STAGE_TYRAS_DEAD
import org.rsmod.content.quest.area.ardougne.regicide.RegicideQuest.Companion.STRIP_OF_CLOTH
import org.rsmod.content.quest.area.ardougne.regicide.RegicideQuest.Companion.SULPHUR
import org.rsmod.content.quest.area.ardougne.regicide.RegicideQuest.Companion.TINDERBOX
import org.rsmod.content.quest.area.ardougne.regicide.RegicideQuest.Companion.TRACKER
import org.rsmod.content.quest.area.ardougne.regicide.RegicideQuest.Companion.UNFUSED_BOMB
import org.rsmod.content.quest.area.ardougne.regicide.RegicideQuest.Ingredient
import org.rsmod.content.quest.area.ardougne.regicide.npcs.ElfTracker
import org.rsmod.content.quest.area.ardougne.regicide.npcs.LordIorwerth
import org.rsmod.content.quest.area.ardougne.regicide.npcs.TyrasCamp
import org.rsmod.content.quest.area.ardougne.undergroundpass.IbanTemple
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest
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
 * Drives Regicide's real scripts through the event bus: the prerequisites and the start, the
 * Well of Voyage, Lord Iorwerth and the tracker, the dense forest in each direction, the guard's
 * owner and credit, the materials and both orders of the bomb, the still and its settlement, the
 * catapult's commit, Arianwyn, King Lathas's reward given once, the unlocks and when they come,
 * logging out and back in, and two players on the quest at the same time.
 */
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class RegicideInteractionTest {

    @Test fun `the Underground Pass and an unboosted Crafting level gate the start, not Agility`() {
        val f = Fixture()
        respectingProgress {
            f.talk(KING_LATHAS)
            assertEquals(0, f.stage(), "Lathas still has the pass to talk about")
            f.completePass()
            f.player.statMap.setBaseLevel("stat.crafting", 9)
            f.player.statMap.setCurrentLevel("stat.crafting", 12)
            f.choose(1, 1)
            f.talk(KING_LATHAS)
            assertEquals(0, f.stage(), "a boosted Crafting level doesn't count")
            assertTrue(f.said("Crafting level of at least 10"))
            f.player.statMap.setBaseLevel("stat.crafting", 10)
            f.player.statMap.setCurrentLevel("stat.crafting", 10)
            f.player.statMap.setBaseLevel("stat.agility", 1)
            f.player.statMap.setCurrentLevel("stat.agility", 1)
            f.choose(1, 1)
            f.talk(KING_LATHAS)
        }
        assertEquals(STAGE_STARTED, f.stage(), "no Agility level is needed to accept")
        assertTrue(f.journal().contains("King Lathas"))
    }

    @Test fun `the messenger's summons is optional`() {
        val f = Fixture()
        f.completePass()
        f.choose(1, 1)
        f.talk(KING_LATHAS)
        assertEquals(STAGE_STARTED, f.stage())
        assertFalse(f.said("I received your message"))
        val other = Fixture()
        other.completePass()
        other.give(KINGS_MESSAGE)
        other.choose(1, 1)
        other.talk(KING_LATHAS)
        assertTrue(other.said("I received your message"))
        assertEquals(1, other.count(KINGS_MESSAGE), "the summons isn't taken")
    }

    @Test fun `the ruined temple opens on the Well of Voyage once Regicide has started`() {
        val f = Fixture()
        f.completePass()
        f.player.coords = CoordGrid(2145, 4647, 1)
        f.locOp("loc.upass_templedoor_closed_left", CoordGrid(2143, 4647, 1))
        assertTrue(f.said("The temple is in ruins"))
        assertEquals(CoordGrid(2145, 4647, 1), f.player.coords)
        f.jump(STAGE_STARTED)
        f.locOp("loc.upass_templedoor_closed_left", CoordGrid(2143, 4647, 1))
        assertEquals(CoordGrid(2014, 4711, 1), f.player.coords, "inside the restored temple")
        f.locOp(WellOfVoyage.TEMPLE_WELL, RestoredTemple.WELL_TILE)
        assertEquals(WellOfVoyage.TIRANNWN_WELL_LANDING, f.player.coords)
        assertEquals(1, f.player.downWell)
        f.jump(STAGE_MET_ELVES)
        f.locOp(WellOfVoyage.CAVE_EXIT, WellOfVoyage.CAVE_EXIT_TILE)
        assertEquals(WellOfVoyage.ISAFDAR_ARRIVAL, f.player.coords)
        f.locOp(WellOfVoyage.CAVE_ENTRANCE, WellOfVoyage.CAVE_ENTRANCE_TILE)
        assertEquals(WellOfVoyage.TIRANNWN_CAVE_LANDING, f.player.coords, "and the way back")
        f.player.coords = WellOfVoyage.TIRANNWN_WELL_LANDING
        f.locOp(WellOfVoyage.TIRANNWN_WELL, WellOfVoyage.TIRANNWN_WELL_TILE)
        assertEquals(RestoredTemple.WELL_LANDING, f.player.coords)
        f.player.coords = CoordGrid(2014, 4711, 1)
        f.locOp("loc.upass_templedoor_closed_left", CoordGrid(2015, 4711, 1))
        assertEquals(CoordGrid(2145, 4647, 1), f.player.coords, "out into the ruins")
        assertEquals(UndergroundPassQuest.STAGE_COMPLETE, f.upass.stage(f.player), "the pass is never reset")
    }

    @Test fun `Iorwerth, the tracker, the pendant and the footprints, in order`() {
        val f = Fixture(STAGE_STARTED)
        f.talk(TRACKER)
        assertEquals(STAGE_STARTED, f.stage(), "the tracker won't talk to strangers")
        f.talk(IORWERTH)
        assertEquals(STAGE_MET_IORWERTH, f.stage(), "Iorwerth sees a player the elves never met")
        f.talk(TRACKER)
        assertEquals(STAGE_TRACKER_REFUSED, f.stage())
        f.fill()
        f.talk(IORWERTH)
        assertEquals(STAGE_TRACKER_REFUSED, f.stage(), "no pendant without room for it")
        assertEquals(0, f.count(PENDANT))
        f.drop("obj.bronze_dagger")
        f.talk(IORWERTH)
        assertEquals(STAGE_GOT_PENDANT, f.stage())
        f.player.coords = CoordGrid(2241, 3151, 0)
        f.locOp(FOOTPRINTS, CoordGrid(2240, 3150, 0))
        assertEquals(STAGE_GOT_PENDANT, f.stage(), "the footprints mean nothing yet")
        f.bank(PENDANT)
        f.talk(TRACKER)
        assertEquals(STAGE_GOT_PENDANT, f.stage(), "a banked pendant proves nothing")
        f.unbank(PENDANT)
        f.talk(TRACKER)
        assertEquals(STAGE_TRACKER_HELPING, f.stage())
        f.locOp(FOOTPRINTS, CoordGrid(2240, 3150, 0))
        assertEquals(STAGE_FOUND_TRACKS, f.stage())
        assertTrue(f.said("impassable woodland"))
        f.talk(TRACKER)
        assertEquals(STAGE_DENSE_FOREST, f.stage())
        assertFalse(f.journal().contains("Dark Lord"), "the deception isn't given away early")
    }

    @Test fun `dense forest needs the lesson and 56 Agility heading away from the pass only`() {
        val f = Fixture(STAGE_FOUND_TRACKS)
        f.setAgility(base = 99, current = 99)
        f.player.coords = EAST_OF_BAND
        f.forest(BAND_EAST)
        assertTrue(f.said("too dense"))
        assertEquals(EAST_OF_BAND, f.player.coords)
        f.jump(STAGE_DENSE_FOREST)
        f.setAgility(base = 55, current = 55)
        f.forest(BAND_EAST)
        assertTrue(f.said("Agility level of 56"))
        assertEquals(EAST_OF_BAND, f.player.coords)
        f.setAgility(base = 50, current = 56)
        f.forest(BAND_EAST)
        assertEquals(CoordGrid(2237, 3149, 0), f.player.coords, "a boosted level is enough")
        f.setAgility(base = 1, current = 1)
        f.forest(BAND_EAST)
        assertEquals(EAST_OF_BAND, f.player.coords, "the way back towards the pass is always open")
        val other = Fixture(STAGE_STARTED)
        other.setAgility(base = 1, current = 1)
        other.player.coords = CoordGrid(2237, 3149, 0)
        other.forest(BAND_EAST)
        assertEquals(EAST_OF_BAND, other.player.coords, "even before the lesson")
    }

    @Test fun `the log balances want 45 Agility once the quest has started`() {
        val f = Fixture(STAGE_STARTED)
        f.setAgility(base = 44, current = 44)
        f.player.coords = CoordGrid(2196, 3237, 0)
        f.locOp("loc.regicide_logbalance1_start", CoordGrid(2197, 3237, 0))
        assertEquals(CoordGrid(2196, 3237, 0), f.player.coords)
        f.setAgility(base = 45, current = 45)
        f.locOp("loc.regicide_logbalance1_start", CoordGrid(2197, 3237, 0))
        assertEquals(CoordGrid(2202, 3237, 0), f.player.coords)
        f.locOp("loc.regicide_logbalance1_start", CoordGrid(2201, 3237, 0))
        assertEquals(CoordGrid(2196, 3237, 0), f.player.coords, "and back")
    }

    @Test fun `each player gets their own guard, only the owner is credited, and camp guards count too`() {
        val f = Fixture(STAGE_DENSE_FOREST)
        val (one, two) = f.player to f.second
        f.regicide.quest.jumpToStage(two, STAGE_DENSE_FOREST)
        for (p in listOf(one, two)) {
            f.setAgility(p, base = 70, current = 70)
            p.coords = CoordGrid(2234, 3149, 0)
        }
        f.forest(GUARD_FOREST)
        assertEquals(CoordGrid(2231, 3149, 0), one.coords)
        assertEquals(1, f.liveGuards())
        val first = checkNotNull(f.encounter.guardOf(one.uid))
        assertEquals(1, one.seenGuard)
        one.coords = CoordGrid(2234, 3149, 0)
        f.forest(GUARD_FOREST)
        assertEquals(1, f.liveGuards(), "going through again doesn't bring a second guard")
        f.forest(GUARD_FOREST, two)
        assertEquals(2, f.liveGuards())
        val second = checkNotNull(f.encounter.guardOf(two.uid))
        assertNotSame(first, second)

        val hook = TyrasGuardAttackHook()
        assertTrue(hook.validate(two, first) is NpcAttackValidateResult.Deny, "nobody else may fight someone's guard")
        assertEquals(NpcAttackValidateResult.Pass, hook.validate(one, first))

        assertEquals(one, f.encounter.creditKill(first))
        assertEquals(STAGE_GUARD_KILLED, f.regicide.stage(one))
        assertEquals(STAGE_DENSE_FOREST, f.regicide.stage(two), "someone else's kill doesn't count")
        assertNull(f.encounter.creditKill(first), "and a guard is only credited once")

        two.coords = CoordGrid(2200, 3200, 0)
        f.encounter.tick(second)
        assertNull(f.encounter.guardOf(two.uid), "the guard goes when his owner leaves")
        two.coords = CoordGrid(2234, 3149, 0)
        f.forest(GUARD_FOREST, two)
        val third = checkNotNull(f.encounter.guardOf(two.uid)) { "and comes back" }
        f.clock.cycle += TyrasGuardEncounter.LIFETIME
        f.encounter.tick(third)
        assertNull(f.encounter.guardOf(two.uid), "and gives up after a while")

        val camp = Npc(CAMP_GUARD, CoordGrid(2187, 3150, 0))
        val killHook = TyrasGuardKillHook(f.regicide, f.launcher)
        killHook.onKill(NpcDeathKillContext(two, camp, 0))
        assertEquals(STAGE_GUARD_KILLED, f.regicide.stage(two), "a camp guard is the other way past")
        killHook.onKill(NpcDeathKillContext(one, camp, 0))
        assertEquals(STAGE_GUARD_KILLED, f.regicide.stage(one))
    }

    @Test fun `Hining, then Iorwerth's book, which takes the pendant from everywhere`() {
        val f = Fixture(STAGE_GUARD_KILLED)
        f.talk(HINING)
        assertEquals(STAGE_FOUND_CAMP, f.stage())
        f.give(PENDANT)
        f.bank(PENDANT)
        f.give(PENDANT)
        f.wear(PENDANT)
        f.fill()
        f.talk(IORWERTH)
        assertEquals(STAGE_FOUND_CAMP, f.stage(), "no book without room")
        assertFalse(f.player.worn.contains(PENDANT) || f.bankCount(PENDANT) > 0, "the pendant goes all the same")
        f.drop("obj.bronze_dagger")
        f.talk(IORWERTH)
        assertEquals(STAGE_MAKE_BOMB, f.stage())
        assertEquals(1, f.count(BOOK))
        f.drop(BOOK)
        f.talk(IORWERTH)
        assertEquals(1, f.count(BOOK), "a lost book is replaced")
        assertEquals(STAGE_MAKE_BOMB, f.stage())
        f.choose(1, 5, 1, 5, 2)
        f.talk(IORWERTH)
        assertTrue(f.regicide.askedAbout(f.player, Ingredient.QUICKLIME))
        assertTrue(f.regicide.askedAbout(f.player, Ingredient.FUSE))
        assertFalse(f.regicide.askedAbout(f.player, Ingredient.SULPHUR))
    }

    @Test fun `quicklime, its pot and the gloves`() {
        val f = Fixture(STAGE_MAKE_BOMB)
        f.give(LIMESTONE, 2)
        f.give(PESTLE_AND_MORTAR)
        val hp = f.player.hitpoints
        f.locU("loc.furnace", CoordGrid(2000, 3000, 0), LIMESTONE)
        assertEquals(1, f.count(QUICKLIME))
        assertEquals(hp - BarrelBomb.BURN_DAMAGE, f.player.hitpoints, "bare hands are burnt")
        f.wear("obj.leather_gloves")
        f.locU(BarrelBomb.SMALL_FURNACE, CoordGrid(2193, 3146, 0), LIMESTONE)
        assertEquals(2, f.count(QUICKLIME))
        assertEquals(hp - BarrelBomb.BURN_DAMAGE, f.player.hitpoints, "gloves keep the hands safe")
        f.use(PESTLE_AND_MORTAR, QUICKLIME)
        assertEquals(1, f.count(QUICKLIME), "with no pot the quicklime is spilt")
        assertEquals(0, f.count(POT_OF_QUICKLIME))
        f.give(POT)
        f.use(PESTLE_AND_MORTAR, QUICKLIME)
        assertEquals(1, f.count(POT_OF_QUICKLIME))
        assertEquals(0, f.count(POT))
        assertEquals(1, f.count(PESTLE_AND_MORTAR), "the tools are kept")
        f.give("obj.limestonebrick")
        assertFalse(f.locUHandled("loc.furnace", CoordGrid(2000, 3000, 0), "obj.limestonebrick"), "limestone bricks are not limestone")
        f.give(SULPHUR)
        f.use(PESTLE_AND_MORTAR, SULPHUR)
        assertEquals(1, f.count(GROUND_SULPHUR))
        f.give(BARREL, 2)
        f.locU(BarrelBomb.COAL_TAR, CoordGrid(2263, 3127, 0), BARREL)
        assertEquals(1, f.count(BARREL_OF_COAL_TAR))
        assertEquals(1, f.count(BARREL), "one barrel at a time")
    }

    @Test fun `the bomb goes together in either order, never twice and only with a strip of cloth`() {
        for (quicklimeFirst in listOf(true, false)) {
            val f = Fixture(STAGE_MAKE_BOMB)
            f.give(BARREL_OF_NAPHTHA)
            f.give(POT_OF_QUICKLIME, 2)
            f.give(GROUND_SULPHUR, 2)
            f.give(STRIP_OF_CLOTH)
            f.use(BARREL_OF_NAPHTHA, STRIP_OF_CLOTH)
            assertEquals(1, f.count(STRIP_OF_CLOTH), "no fuse before the mixture")
            if (quicklimeFirst) {
                f.use(BARREL_OF_NAPHTHA, POT_OF_QUICKLIME)
                assertEquals(1, f.count(NAPHTHA_QUICKLIME_MIX))
                f.use(NAPHTHA_QUICKLIME_MIX, POT_OF_QUICKLIME)
                assertEquals(1, f.count(POT_OF_QUICKLIME), "quicklime isn't added twice")
                f.use(NAPHTHA_QUICKLIME_MIX, GROUND_SULPHUR)
            } else {
                f.use(BARREL_OF_NAPHTHA, GROUND_SULPHUR)
                assertEquals(1, f.count(NAPHTHA_SULPHUR_MIX))
                f.use(NAPHTHA_SULPHUR_MIX, GROUND_SULPHUR)
                assertEquals(1, f.count(GROUND_SULPHUR), "sulphur isn't added twice")
                f.use(NAPHTHA_SULPHUR_MIX, POT_OF_QUICKLIME)
            }
            assertEquals(1, f.count(UNFUSED_BOMB))
            assertEquals(1, f.count(POT), "the quicklime's pot comes back")
            f.use(UNFUSED_BOMB, GROUND_SULPHUR)
            assertEquals(1, f.count(UNFUSED_BOMB))
            f.use(UNFUSED_BOMB, STRIP_OF_CLOTH)
            assertEquals(1, f.count(BARREL_BOMB))
            assertEquals(0, f.count(STRIP_OF_CLOTH))
            assertEquals(1, f.count(POT_OF_QUICKLIME), "only one pot of quicklime went in")
            assertEquals(1, f.count(GROUND_SULPHUR), "only one lot of sulphur went in")
        }
    }

    @Test fun `the still's controls, burning out and settling`() {
        val sim = StillSimulation()
        repeat(10) { sim.step() }
        assertEquals(0, sim.pressure, "no tar, no pressure")
        sim.turnTarRegulator(1)
        sim.turnTarRegulator(1)
        assertEquals(StillSimulation.TarRegulator.AFTERBURNER, sim.tar)
        sim.turnTarRegulator(1)
        assertEquals(StillSimulation.TarRegulator.AFTERBURNER, sim.tar, "it stops at its last mark")
        while (StillSimulation.needle(sim.pressure) < 8) sim.step()
        sim.turnPressureValve(1)
        val held = sim.pressure
        repeat(5) { sim.step() }
        assertEquals(held, sim.pressure, "the middle position holds the pressure")
        repeat(4) { sim.addCoal().let { if (it == StillSimulation.Outcome.BURNT_OUT) return@repeat } }
        assertEquals(0, sim.heat, "four coal at once burns it out")
        assertEquals(StillSimulation.TarRegulator.CLOSED, sim.tar, "and the valves reset")

        val run = StillSimulation()
        run.turnTarRegulator(2)
        while (StillSimulation.needle(run.pressure) < 8) run.step()
        run.turnPressureValve(1)
        var coal = 0
        var ticks = 0
        repeat(2) { run.addCoal(); coal++ }
        while (!run.isFinished && ticks < 500) {
            if (StillSimulation.needle(run.heat) < 7) {
                run.addCoal()
                coal++
            }
            run.step()
            ticks++
        }
        assertTrue(run.isFinished, "following the guide fills the bar")
        assertTrue(coal in 4..9, "with a guide's worth of coal: $coal")

        val blown = StillSimulation(tar = StillSimulation.TarRegulator.AFTERBURNER)
        var outcome = StillSimulation.Outcome.NONE
        repeat(100) { if (outcome == StillSimulation.Outcome.NONE) outcome = blown.step() }
        assertEquals(StillSimulation.Outcome.OVER_PRESSURE, outcome, "a closed valve lets the pressure run away")
        assertEquals(1 or (1 shl 13) or (1 shl 26) or (1 shl 29), StillSimulation().settings(), "cold gauges and closed valves")
    }

    @Test fun `the Chemist's permission, the still session and one barrel of naphtha`() {
        val f = Fixture(STAGE_MAKE_BOMB)
        f.give(BARREL_OF_COAL_TAR)
        f.player.coords = CoordGrid(2926, 3212, 0)
        f.locU(FractionalStill.STILL, STILL_TILE, BARREL_OF_COAL_TAR)
        assertTrue(f.said("ask the Chemist"))
        assertEquals(1, f.count(BARREL_OF_COAL_TAR))
        f.give(BOOK)
        f.choose(2)
        f.talk(CHEMIST)
        assertEquals(1, f.player.chemistChat)
        f.locU(FractionalStill.STILL, STILL_TILE, BARREL_OF_COAL_TAR)
        assertEquals(1, f.player.tarInStill)
        assertEquals(0, f.count(BARREL_OF_COAL_TAR))
        val session = checkNotNull(f.still.session(f.player))
        f.give("obj.cert_coal", 5)
        f.addCoal()
        assertTrue(f.said("no coal"), "noted coal isn't fuel")
        assertEquals(5, f.stack("obj.cert_coal"))
        f.give(COAL, 9)
        session.turnTarRegulator(2)
        while (StillSimulation.needle(session.pressure) < 8) f.still.tick(f.player)
        session.turnPressureValve(1)
        f.addCoal()
        f.addCoal()
        assertEquals(7, f.count(COAL), "one coal a click")
        repeat(6) { f.still.tick(f.player) }
        val saved = f.player.stillTotal
        assertTrue(saved > 0)
        f.still.stop(f.player)
        assertNull(f.still.session(f.player), "closing ends the session")
        assertEquals(saved, f.player.stillTotal, "but keeps the bar")
        f.locOp(FractionalStill.STILL, STILL_TILE)
        val resumed = checkNotNull(f.still.session(f.player))
        assertEquals(saved, resumed.total)
        resumed.turnTarRegulator(2)
        var guard = 0
        while (f.player.tarInStill == 1 && guard++ < 600) {
            if (StillSimulation.needle(resumed.pressure) >= 8) resumed.turnPressureValve(1 - resumed.pressureValve.ordinal)
            if (StillSimulation.needle(resumed.heat) < 7 && f.count(COAL) > 0) f.addCoal()
            f.still.tick(f.player)
        }
        assertEquals(1, f.count(BARREL_OF_NAPHTHA))
        assertEquals(0, f.player.tarInStill)
        repeat(5) { f.still.tick(f.player) }
        f.locOp(FractionalStill.STILL, STILL_TILE)
        assertEquals(1, f.count(BARREL_OF_NAPHTHA), "the still pays out once")
        assertTrue(f.said("The still is empty"))
    }

    @Test fun `a still that burns out keeps its tar`() {
        val f = Fixture(STAGE_MAKE_BOMB)
        f.player.chemistChat = 1
        f.give(BARREL_OF_COAL_TAR)
        f.give(COAL, 4)
        f.player.coords = CoordGrid(2926, 3212, 0)
        f.locU(FractionalStill.STILL, STILL_TILE, BARREL_OF_COAL_TAR)
        repeat(4) { f.addCoal() }
        assertTrue(f.said("burns out"))
        assertEquals(1, f.player.tarInStill, "the coal tar isn't lost")
        assertEquals(0, f.player.stillTotal)
    }

    @Test fun `the catapult guard, the tinderbox and a single commit`() {
        val f = Fixture(STAGE_MAKE_BOMB)
        f.give(BARREL_BOMB)
        assertFalse(f.commit(), "the guard is watching")
        assertTrue(f.said("Don't mess with that"))
        f.talk(CATAPULT_GUARD)
        assertEquals(0, f.player.givenRabbit, "talking without a rabbit does nothing")
        f.give(COOKED_RABBIT)
        f.talk(CATAPULT_GUARD)
        assertEquals(1, f.player.givenRabbit)
        assertEquals(0, f.count(COOKED_RABBIT))
        assertFalse(f.commit(), "the fuse needs a tinderbox")
        assertEquals(1, f.count(BARREL_BOMB))
        f.give(TINDERBOX)
        assertTrue(f.commit())
        assertEquals(STAGE_TYRAS_DEAD, f.stage())
        assertEquals(0, f.count(BARREL_BOMB), "the bomb is spent once, before the launch is shown")
        assertEquals(1, f.count(TINDERBOX))
        assertEquals(0, f.player.givenRabbit, "the guard is back from his meal")
        assertFalse(f.commit())
        val roast = Fixture(STAGE_MAKE_BOMB)
        roast.give(ROAST_RABBIT)
        roast.npcU(CATAPULT_GUARD, ROAST_RABBIT)
        assertEquals(1, roast.player.givenRabbit, "a roast rabbit does as well")
    }

    @Test fun `Iorwerth's letter opens Arandar, and Arianwyn breaks its seal`() = respectingProgress {
        val f = Fixture(STAGE_TYRAS_DEAD)
        f.player.coords = CoordGrid(2385, 3335, 0)
        f.locOp("loc.overpass_gate_left", CoordGrid(2384, 3334, 0))
        assertEquals(CoordGrid(2385, 3335, 0), f.player.coords, "not into Tirannwn yet")
        f.player.coords = CoordGrid(2385, 3333, 0)
        f.locOp("loc.overpass_gate_left", CoordGrid(2384, 3334, 0))
        assertEquals(CoordGrid(2385, 3335, 0), f.player.coords, "the gate always lets you out")
        f.fill()
        f.talk(IORWERTH)
        assertEquals(STAGE_TYRAS_DEAD, f.stage(), "no letter, and no Arandar, without room")
        f.drop("obj.bronze_dagger")
        f.talk(IORWERTH)
        assertEquals(STAGE_LETTER, f.stage())
        assertEquals(1, f.count(IORWERTH_MESSAGE))
        f.locOp("loc.overpass_gate_left", CoordGrid(2384, 3334, 0))
        assertEquals(CoordGrid(2385, 3333, 0), f.player.coords, "Arandar is open")
        assertFalse(f.regicide.isComplete(f.player))

        f.held1(IORWERTH_MESSAGE)
        assertTrue(f.said("sealed"))
        assertEquals(0, f.player.readMessage)
        f.drop(IORWERTH_MESSAGE)
        f.dispatch { with(f.arianwyn) { intercept() } }
        assertEquals(STAGE_LETTER, f.stage(), "Arianwyn only stops someone carrying the letter")
        f.talk(IORWERTH)
        assertEquals(1, f.count(IORWERTH_MESSAGE), "a lost letter is replaced")
        f.talk(KING_LATHAS)
        assertEquals(STAGE_LETTER_UNSEALED, f.stage(), "going to the king brings Arianwyn")
        assertEquals(1, f.player.readMessage)
        assertTrue(f.said("Dark Lord"))
        assertEquals(0, f.liveNpcs(RegicideQuest.ARIANWYN), "and he leaves afterwards")
    }

    @Test fun `King Lathas completes the quest once`() {
        val f = Fixture(STAGE_LETTER_UNSEALED)
        f.talk(KING_LATHAS)
        assertEquals(STAGE_LETTER_UNSEALED, f.stage(), "not without proof")
        f.give(IORWERTH_MESSAGE)
        f.give(PENDANT)
        f.bank(PENDANT)
        f.talk(KING_LATHAS)
        assertEquals(STAGE_COMPLETE, f.stage())
        assertEquals(3, f.player.vars["varp.qp"])
        assertEquals(13_750, f.player.statMap.getXP("stat.agility") - f.startingAgilityXp)
        assertEquals(15_000, f.count(COINS))
        assertEquals(0, f.count(IORWERTH_MESSAGE))
        assertEquals(0, f.bankCount(PENDANT), "the king takes the pendant too")
        f.give(IORWERTH_MESSAGE)
        f.talk(KING_LATHAS)
        assertEquals(3, f.player.vars["varp.qp"], "rewards are given once")
        assertEquals(15_000, f.count(COINS))
        assertTrue(f.regicide.isComplete(f.saveAndReload()))
    }

    @Test fun `the unlocks wait for the right moment`() {
        val f = Fixture(STAGE_LETTER_UNSEALED)
        val hook = DragonHalberdWearHook()
        val halberd = checkNotNull(ServerCacheManager.getItem("obj.dragon_halberd".asRSCM()))
        respectingProgress {
            f.give(RegicideUnlocks.IORWERTH_CAMP_SCROLL)
            f.held1(RegicideUnlocks.IORWERTH_CAMP_SCROLL)
            assertTrue(f.said("complete the Regicide quest before you can teleport to Tirannwn"))
            assertEquals(1, f.count(RegicideUnlocks.IORWERTH_CAMP_SCROLL), "the scroll isn't used up")
            assertNotNull(hook.restriction(f.player, RestrictedAction.Equip(halberd)))
            f.regicide.quest.jumpToStage(f.player, STAGE_COMPLETE)
            assertNull(hook.restriction(f.player, RestrictedAction.Equip(halberd)))
            f.teleportDenial = "You can't teleport from here."
            f.held1(RegicideUnlocks.IORWERTH_CAMP_SCROLL)
            assertEquals(1, f.count(RegicideUnlocks.IORWERTH_CAMP_SCROLL), "a refused teleport keeps the scroll")
            f.teleportDenial = null
            f.held1(RegicideUnlocks.IORWERTH_CAMP_SCROLL)
            assertEquals(0, f.count(RegicideUnlocks.IORWERTH_CAMP_SCROLL))
            assertEquals(RegicideUnlocks.IORWERTH_CAMP, f.player.coords)
        }
    }

    @Test fun `progress survives logging out, and so does a scene's return tile`() {
        val f = Fixture(STAGE_MAKE_BOMB)
        f.regicide.markAsked(f.player, Ingredient.NAPHTHA)
        f.player.chemistChat = 1
        f.player.tarInStill = 1
        f.player.stillTotal = 12
        f.player.sceneReturn = CoordGrid(2185, 3181, 0).packed
        val loaded = f.saveAndReload()
        assertEquals(STAGE_MAKE_BOMB, f.regicide.stage(loaded))
        assertTrue(f.regicide.askedAbout(loaded, Ingredient.NAPHTHA))
        assertEquals(1, loaded.chemistChat)
        assertEquals(1, loaded.tarInStill)
        assertEquals(12, loaded.stillTotal)
        f.scenes.restoreLogin(loaded)
        assertEquals(CoordGrid(2185, 3181, 0), loaded.coords, "back in the world, not in a vanished copy")
        assertEquals(0, loaded.sceneReturn)
        f.regicide.quest.jumpToStage(loaded, 0)
        assertEquals(0, loaded.tarInStill, "a reset clears the quest's own flags")
        assertEquals(0, loaded.stillTotal)
        assertEquals(0, f.player.messengerSeen)
    }

    private class Fixture(stage: Int = 0) {
        val events = EventBus()
        private val client = RecordingClient()
        private val collision = CollisionFlagMap()
        private val npcs = NpcList()
        private val players = PlayerList()
        private val coroutine = GameCoroutine("regicide-test")
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

        val player = newPlayer(5151L, 1)
        val second = newPlayer(5252L, 2)
        private var active = player
        val startingAgilityXp: Int

        val regicide = RegicideQuest()
        val upass = UndergroundPassQuest()
        val launcher = ProtectedAccessLauncher(unused<ProtectedAccessContextFactory>())
        val scenes: RegicideScenes
        val encounter: TyrasGuardEncounter
        val arianwyn: Arianwyn
        val still: FractionalStill
        val catapult: Catapult

        init {
            for ((x0, z0, x1, z1) in AREAS) {
                for (level in 0..2) for (x in x0..x1 step 8) for (z in z0..z1 step 8) collision.allocateIfAbsent(x, z, level)
            }
            players[player.slotId] = player
            players[second.slotId] = second
            npcRepo = NpcRepository(clock, NpcRegistry(npcs, collision, events), npcs)
            scenes = RegicideScenes(unused<RegionRepository>(), npcRepo)
            val pendant = CrystalPendant()
            encounter = TyrasGuardEncounter(regicide, npcRepo, players, AiPlayerInteractions(events, players), unused<NpcDeath>(), clock, collision, launcher)
            arianwyn = Arianwyn(regicide, scenes, collision, clock)
            still = FractionalStill(objRepo)
            catapult = Catapult(regicide, scenes, unused<LocRepository>(), unused<WorldRepository>())
            val idris = IdrisScene(regicide, scenes)
            val biohazard = BiohazardQuest()
            val chemistry = RegicideChemistry(regicide)
            val temple = IbanTemple(upass, npcRepo, unused(), objRepo, launcher, DefaultGameRandom(Random(3)), collision, AiPlayerInteractions(events, players), Provider { regicide })
            val scripts = ScriptContext(events, CheatCommandMap(), EngineQueueCache())
            for (script in listOf(
                regicide, encounter, arianwyn, still, catapult, idris, temple,
                KingLathas(biohazard, upass, regicide, RegicideLathas(regicide, pendant), arianwyn),
                LordIorwerth(regicide, pendant), ElfTracker(regicide, pendant), TyrasCamp(regicide),
                IsafdarObstacles(regicide, encounter), WellOfVoyage(regicide, idris), BarrelBomb(), RegicideReading(regicide),
                Smuggling(biohazard, objRepo, chemistry), RegicideUnlocks(regicide, validator, unused()),
            )) {
                with(script) { scripts.startup() }
            }
            for (p in listOf(player, second)) {
                if (stage > 0) {
                    regicide.quest.jumpToStage(p, stage)
                    upass.quest.jumpToStage(p, UndergroundPassQuest.STAGE_COMPLETE)
                }
            }
            startingAgilityXp = player.statMap.getXP("stat.agility")
        }

        @OptIn(InternalApi::class)
        private fun newPlayer(id: Long, slot: Int) = Player().apply {
            this.client = this@Fixture.client
            uuid = id
            observerUUID = id
            slotId = slot
            assignUid()
            coords = CoordGrid(2578, 3292, 1)
            currentMapClock = 100
            processedMapClock = 100
            pendingSequence = EntitySeq.NULL
            pendingFaceAngle = EntityFaceAngle.NULL
            inv = Inventory(InventoryServerType(size = 28, flags = 0), arrayOfNulls(28))
            worn = Inventory(InventoryServerType(size = 14, flags = 0), arrayOfNulls(14))
            for ((stat, level) in listOf("stat.hitpoints" to 60, "stat.agility" to 60, "stat.crafting" to 20)) {
                statMap.setBaseLevel(stat, level.toByte())
                statMap.setCurrentLevel(stat, level.toByte())
            }
        }

        fun access(p: Player = active) = ProtectedAccess(p, coroutine, context)

        fun stage(): Int = regicide.stage(player)

        fun jump(stage: Int) = regicide.quest.jumpToStage(player, stage)

        fun completePass() = upass.quest.jumpToStage(player, UndergroundPassQuest.STAGE_COMPLETE)

        fun journal(): String = regicide.questLog(access())

        fun choose(vararg options: Int) {
            picks += options.toList()
        }

        fun setAgility(p: Player = player, base: Int, current: Int) {
            p.statMap.setBaseLevel("stat.agility", base.toByte())
            p.statMap.setCurrentLevel("stat.agility", current.toByte())
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
            val slot = player.inv.indexOfFirst { it?.id == obj.asRSCM() }
            if (slot >= 0) player.inv[slot] = null
            player.worn[type.wearpos1] = InvObj(obj, 1)
        }

        private val bankInv by lazy { access().bank }

        fun bank(obj: String) {
            drop(obj)
            val slot = bankInv.indexOfFirst { it == null }
            bankInv[slot] = InvObj(obj, 1)
        }

        fun unbank(obj: String) {
            val slot = bankInv.indexOfFirst { it?.id == obj.asRSCM() }
            bankInv[slot] = null
            give(obj)
        }

        fun bankCount(obj: String): Int = bankInv.count(obj)

        fun fill() {
            while (player.inv.freeSpace() > 0) give("obj.bronze_dagger")
        }

        fun drop(obj: String) {
            val slot = player.inv.indexOfFirst { it?.id == obj.asRSCM() }
            if (slot >= 0) player.inv[slot] = null
        }

        fun count(obj: String): Int = player.inv.count(obj)

        fun stack(obj: String): Int = player.inv.filter { it?.id == obj.asRSCM() }.sumOf { it?.count ?: 0 }

        fun said(text: String): Boolean = output().contains(text)

        fun liveGuards(): Int = liveNpcs(ENCOUNTER_GUARD)

        fun liveNpcs(type: String): Int = npcs.count { it != null && it.isSlotAssigned && it.id == type.asRSCM() }

        fun talk(type: String, at: CoordGrid = player.coords.translateX(1)) {
            val npc = Npc(type, at)
            npcRepo.add(npc, Int.MAX_VALUE)
            publish(NpcEvents.Op1(npc))
            if (npc.isSlotAssigned) npcRepo.del(npc, Int.MAX_VALUE)
        }

        fun npcU(type: String, obj: String) {
            val npc = Npc(type, player.coords.translateX(1))
            npcRepo.add(npc, Int.MAX_VALUE)
            val slot = player.inv.indexOfFirst { it?.id == obj.asRSCM() }
            val objType = checkNotNull(ServerCacheManager.getItem(obj.asRSCM()))
            val npcType = checkNotNull(ServerCacheManager.getNpc(type.asRSCM()))
            dispatch { assertTrue(events.publish(this, org.rsmod.api.player.events.interact.NpcUEvents.Op(npc, slot, objType, npcType))) }
            if (npc.isSlotAssigned) npcRepo.del(npc, Int.MAX_VALUE)
        }

        fun held1(obj: String) {
            val slot = player.inv.indexOfFirst { it?.id == obj.asRSCM() }
            val type = checkNotNull(ServerCacheManager.getItem(obj.asRSCM()))
            dispatch {
                assertTrue(events.publish(this, org.rsmod.api.player.events.interact.HeldObjEvents.Op1(slot, checkNotNull(inv[slot]), type, inv)))
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

        fun forest(at: CoordGrid, p: Player = player) {
            active = p
            try {
                locOp("loc.regicide_cross_over1", at, angle = 3)
            } finally {
                active = player
            }
        }

        fun locOp(symbol: String, coords: CoordGrid, op: InteractionOp = InteractionOp.Op1, angle: Int = 0) {
            val loc = bound(symbol, coords, angle)
            val event = LocInteractions(BoundValidator(collision), events).opTrigger(active, loc, op)
            publish(checkNotNull(event) { "No $op handler for $symbol" })
        }

        fun locUHandled(symbol: String, coords: CoordGrid, obj: String): Boolean {
            val loc = bound(symbol, coords)
            val locType = checkNotNull(ServerCacheManager.getObject(symbol.asRSCM()))
            val objType = checkNotNull(ServerCacheManager.getItem(obj.asRSCM()))
            val slot = player.inv.indexOfFirst { it?.id == obj.asRSCM() }
            return with(locU) { access().opTrigger(loc, loc, locType, objType, slot) } != null
        }

        fun locU(symbol: String, coords: CoordGrid, obj: String) {
            val loc = bound(symbol, coords)
            val locType = checkNotNull(ServerCacheManager.getObject(symbol.asRSCM()))
            val objType = checkNotNull(ServerCacheManager.getItem(obj.asRSCM()))
            val slot = player.inv.indexOfFirst { it?.id == obj.asRSCM() }
            val event = with(locU) { access().opTrigger(loc, loc, locType, objType, slot) }
            publish(checkNotNull(event) { "No $obj handler for $symbol" })
        }

        fun addCoal() = dispatch(closeModals = false) { with(still) { addCoal() } }

        fun commit(): Boolean {
            var committed = false
            dispatch { committed = with(catapult) { commitLaunch() } }
            return committed
        }

        fun saveAndReload(): Player {
            val loaded = newPlayer(player.uuid ?: 0L, 3)
            for ((varp, value) in player.vars.backing) {
                val scope = ServerCacheManager.getVarp(varp)?.scope
                if (scope != VarpLifetime.Temp) loaded.vars.backing[varp] = value
            }
            player.attr[QUEST_STAGE_MAP_ATTR]?.let { loaded.attr[QUEST_STAGE_MAP_ATTR] = it.toMutableMap() }
            regicide.quest.syncState(loaded)
            return loaded
        }

        private fun bound(symbol: String, coords: CoordGrid, angle: Int = 0): BoundLocInfo {
            val type = checkNotNull(ServerCacheManager.getObject(symbol.asRSCM()))
            return BoundLocInfo(LocInfo(0, coords, LocEntity(type.id, 10, angle)), type)
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
        const val FOOTPRINTS = "loc.regicide_old_camp_footprints"
        val STILL_TILE = CoordGrid(2927, 3212, 0)
        val BAND_EAST = CoordGrid(2238, 3148, 0)
        val EAST_OF_BAND = CoordGrid(2240, 3149, 0)
        val GUARD_FOREST = DenseForest.GUARD_TRIGGER

        val AREAS =
            listOf(
                intArrayOf(2140, 3040, 2400, 3340),
                intArrayOf(2296, 9600, 2360, 9640),
                intArrayOf(1984, 4672, 2047, 4735),
                intArrayOf(2112, 4608, 2175, 4671),
                intArrayOf(2560, 3270, 2600, 3320),
                intArrayOf(2900, 3190, 2950, 3230),
                intArrayOf(1990, 2990, 2010, 3010),
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
