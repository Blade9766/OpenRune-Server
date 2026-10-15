package org.rsmod.content.quest.area.camelot.holygrail

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.InventoryServerType
import dev.openrune.util.Wearpos
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
import org.rsmod.api.combat.weapon.WeaponSpeeds
import org.rsmod.api.combat.weapon.styles.AttackStyles
import org.rsmod.api.death.NpcDeath
import org.rsmod.api.death.PlayerDeathContext
import org.rsmod.api.inv.storage.PlayerItemStorage
import org.rsmod.api.invtx.InvTransactionsScript
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.player.bonus.WornBonuses
import org.rsmod.api.player.dialogue.align.TextAlignment
import org.rsmod.api.player.events.interact.HeldObjEvents
import org.rsmod.api.player.events.interact.NpcEvents
import org.rsmod.api.player.events.interact.ObjEvents
import org.rsmod.api.player.hit.modifier.NoopPlayerHitModifier
import org.rsmod.api.player.hook.PlayerTeleportValidator
import org.rsmod.api.player.input.ResumePauseButtonInput
import org.rsmod.api.player.interact.LocInteractions
import org.rsmod.api.player.interact.NpcInteractions
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.api.player.protect.ProtectedAccessLauncher
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
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.route.BoundValidator
import org.rsmod.content.generic.locs.passages.GenericPassageScript
import org.rsmod.content.generic.locs.passages.StairNavigator
import org.rsmod.content.interfaces.bank.bankCapacity
import org.rsmod.content.interfaces.bank.scripts.BankInvScript
import org.rsmod.content.quest.area.burthorpe.heroesquest.HeroesQuest
import org.rsmod.content.quest.area.burthorpe.heroesquest.npcs.EntranaMonks
import org.rsmod.content.quest.area.camelot.holygrail.HolyGrailQuest.Companion.BELL
import org.rsmod.content.quest.area.camelot.holygrail.HolyGrailQuest.Companion.DEAD_ARRIVAL
import org.rsmod.content.quest.area.camelot.holygrail.HolyGrailQuest.Companion.EXCALIBUR
import org.rsmod.content.quest.area.camelot.holygrail.HolyGrailQuest.Companion.FEATHER
import org.rsmod.content.quest.area.camelot.holygrail.HolyGrailQuest.Companion.FISHER_KING
import org.rsmod.content.quest.area.camelot.holygrail.HolyGrailQuest.Companion.GALAHAD
import org.rsmod.content.quest.area.camelot.holygrail.HolyGrailQuest.Companion.HIGH_PRIEST
import org.rsmod.content.quest.area.camelot.holygrail.HolyGrailQuest.Companion.HOLY_GRAIL
import org.rsmod.content.quest.area.camelot.holygrail.HolyGrailQuest.Companion.KING_ARTHUR
import org.rsmod.content.quest.area.camelot.holygrail.HolyGrailQuest.Companion.KING_PERCIVAL
import org.rsmod.content.quest.area.camelot.holygrail.HolyGrailQuest.Companion.MERLIN
import org.rsmod.content.quest.area.camelot.holygrail.HolyGrailQuest.Companion.NAPKIN
import org.rsmod.content.quest.area.camelot.holygrail.HolyGrailQuest.Companion.PERCIVAL_SACKS
import org.rsmod.content.quest.area.camelot.holygrail.HolyGrailQuest.Companion.RESTORED_ARRIVAL
import org.rsmod.content.quest.area.camelot.holygrail.HolyGrailQuest.Companion.SIR_PERCIVAL
import org.rsmod.content.quest.area.camelot.holygrail.HolyGrailQuest.Companion.STAGE_COMPLETE
import org.rsmod.content.quest.area.camelot.holygrail.HolyGrailQuest.Companion.STAGE_ENTRANA
import org.rsmod.content.quest.area.camelot.holygrail.HolyGrailQuest.Companion.STAGE_FEATHER
import org.rsmod.content.quest.area.camelot.holygrail.HolyGrailQuest.Companion.STAGE_GRAIL_TAKEN
import org.rsmod.content.quest.area.camelot.holygrail.HolyGrailQuest.Companion.STAGE_HEIR_NEEDED
import org.rsmod.content.quest.area.camelot.holygrail.HolyGrailQuest.Companion.STAGE_MERLIN
import org.rsmod.content.quest.area.camelot.holygrail.HolyGrailQuest.Companion.STAGE_PERCIVAL_SENT
import org.rsmod.content.quest.area.camelot.holygrail.HolyGrailQuest.Companion.STAGE_REALM_ENTERED
import org.rsmod.content.quest.area.camelot.holygrail.HolyGrailQuest.Companion.STAGE_REALM_RESTORED
import org.rsmod.content.quest.area.camelot.holygrail.HolyGrailQuest.Companion.STAGE_STARTED
import org.rsmod.content.quest.area.camelot.holygrail.HolyGrailQuest.Companion.TITAN
import org.rsmod.content.quest.area.camelot.holygrail.HolyGrailQuest.Companion.TOWER
import org.rsmod.content.quest.area.camelot.holygrail.HolyGrailQuest.Companion.WHISTLE
import org.rsmod.content.quest.area.camelot.holygrail.npcs.EntranaGrailLore
import org.rsmod.content.quest.area.camelot.holygrail.npcs.FisherRealmFolk
import org.rsmod.content.quest.area.camelot.holygrail.npcs.Galahad
import org.rsmod.content.quest.area.camelot.holygrail.npcs.GrailArthur
import org.rsmod.content.quest.area.camelot.holygrail.npcs.Merlin
import org.rsmod.content.quest.area.camelot.holygrail.npcs.SirPercival
import org.rsmod.content.quest.area.camelot.merlinscrystal.MerlinsCrystalQuest
import org.rsmod.content.quest.area.camelot.merlinscrystal.npcs.KingArthur
import org.rsmod.content.quest.area.lumbridge.lostcity.EntranaBoat
import org.rsmod.content.quest.area.lumbridge.lostcity.LostCityQuest
import org.rsmod.content.quest.area.varrock.shieldofarrav.ShieldOfArravQuest
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
 * Drives Holy Grail's real scripts through the event bus: the complete path from King Arthur to
 * the Grail's delivery; the requirements; Entrana's edict and the monks' storage; the whistles
 * with and without the napkin and their recovery; the whistle at the tower, elsewhere and inside
 * the realm; the Titan's finishing blow and a death in the realm; the Fisher King heard out in
 * pieces; the feather, the sacks and the whistle handed to Percival; the Grail refused too early
 * and never duplicated; the restoration shown once; save and reload; and rewards granted once.
 */
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class HolyGrailInteractionTest {

    @Test fun `the complete quest path delivers the Grail and grants the rewards once`() {
        val f = Fixture()
        f.startQuest()
        assertEquals(STAGE_STARTED, f.stage())
        assertTrue(f.journal().contains("Merlin"))

        f.talk(MERLIN)
        assertEquals(STAGE_MERLIN, f.stage())
        assertTrue(f.journal().contains("Entrana"))
        assertTrue(f.said("true king's blade"))

        f.talk(HIGH_PRIEST)
        assertEquals(STAGE_ENTRANA, f.stage())
        assertTrue(f.said("Draynor Manor"))
        assertTrue(f.said("sword of a true king"))
        assertEquals(1, f.live(HolyGrailQuest.CRONE))

        f.choose(1)
        f.talk(GALAHAD)
        assertEquals(1, f.count(NAPKIN))
        assertTrue(f.player.napkinGiven)

        f.enterWhistleRoom()
        assertEquals(2, f.onTable())
        f.takeWhistles()
        assertEquals(2, f.count(WHISTLE))
        assertTrue(f.player.whistlesFound)

        f.player.coords = TOWER
        f.blow()
        assertEquals(DEAD_ARRIVAL, f.player.coords)
        assertEquals(STAGE_REALM_ENTERED, f.stage())
        assertTrue(f.said("Grey light"))

        f.wield(EXCALIBUR)
        assertTrue(f.slayTitan())
        assertTrue(f.player.titanDefeated)

        f.player.coords = FisherRealm.BELL_SPOT.translateX(1)
        f.give(BELL)
        f.held(BELL)
        assertEquals(FisherRealm.CASTLE_ENTRY, f.player.coords)
        assertTrue(f.player.castleEntered)

        f.choose(1, 2)
        f.talk(FISHER_KING)
        assertEquals(STAGE_HEIR_NEEDED, f.stage())

        f.talk(KING_ARTHUR)
        assertEquals(STAGE_FEATHER, f.stage())
        assertEquals(1, f.count(FEATHER))
        assertTrue(f.said("Percival"))

        f.player.coords = SirPercival.SACKS.translateZ(-1)
        f.choose(1)
        f.openSacks()
        assertEquals(STAGE_PERCIVAL_SENT, f.stage())
        assertEquals(1, f.count(WHISTLE))
        assertEquals(0, f.live(SIR_PERCIVAL))

        f.player.coords = TOWER
        f.blow()
        assertEquals(RESTORED_ARRIVAL, f.player.coords)
        assertEquals(STAGE_REALM_RESTORED, f.stage())
        assertTrue(f.said("The realm is healed"))

        f.talk(KING_PERCIVAL)
        assertTrue(f.said("east tower"))
        f.takeGrail()
        assertEquals(STAGE_GRAIL_TAKEN, f.stage())
        assertEquals(1, f.count(HOLY_GRAIL))

        val prayer = f.xp("stat.prayer")
        val defence = f.xp("stat.defence")
        val qp = f.qp()
        f.talk(KING_ARTHUR)
        assertEquals(STAGE_COMPLETE, f.stage())
        assertEquals(0, f.count(HOLY_GRAIL))
        assertEquals(11_000, f.xp("stat.prayer") - prayer)
        assertEquals(15_300, f.xp("stat.defence") - defence)
        assertEquals(qp + 2, f.qp())
        assertTrue(f.player.ui.containsModal("interface.questscroll"))

        f.talk(KING_ARTHUR)
        assertEquals(qp + 2, f.qp())
        assertEquals(11_000, f.xp("stat.prayer") - prayer)
    }

    @Test fun `arthur holds the quest back until merlin is free and the player has 20 attack`() {
        respectingProgress {
            val f = Fixture(merlinDone = false)
            f.choose(2)
            f.talk(KING_ARTHUR)
            assertEquals(0, f.stage())
            assertTrue(f.said("Welcome to my court"))
            assertTrue(f.hg.hint(f.player).contains("Merlin's Crystal"))
        }

        val weak = Fixture(attack = 10)
        weak.choose(1)
        weak.talk(KING_ARTHUR)
        assertEquals(0, weak.stage())
        assertTrue(weak.said("level 20 Attack"))
    }

    @Test fun `when merlin's crystal is only assumed, arthur offers both quests`() {
        val grail = Fixture(merlinDone = false)
        grail.choose(2, 1, 1)
        grail.talk(KING_ARTHUR)
        assertEquals(STAGE_STARTED, grail.stage())

        val knight = Fixture(merlinDone = false)
        knight.choose(1)
        knight.talk(KING_ARTHUR)
        assertEquals(0, knight.stage())
        assertTrue(knight.said("rescue Merlin"))
    }

    @Test fun `nobody moves the quest on before its turn`() {
        val f = Fixture()
        f.talk(MERLIN)
        f.talk(HIGH_PRIEST)
        f.talk(GALAHAD)
        assertEquals(0, f.stage())
        assertEquals(0, f.count(NAPKIN))
        f.jump(STAGE_STARTED)
        f.talk(HIGH_PRIEST)
        assertEquals(STAGE_STARTED, f.stage())
        f.talk(FISHER_KING)
        assertEquals(STAGE_STARTED, f.stage())
        f.player.coords = SirPercival.SACKS.translateZ(-1)
        f.openSacks()
        assertEquals(0, f.live(SIR_PERCIVAL))
    }

    @Test fun `the port sarim monks refuse weapons, list them and can send them to the bank`() {
        val f = Fixture(STAGE_MERLIN)
        f.give(EXCALIBUR)
        f.wield("obj.rune_full_helm")
        f.player.coords = PORT_SARIM
        f.choose(2, 2)
        f.talk(PORT_SARIM_MONK)
        assertTrue(f.said("NO WEAPONS OR ARMOUR"))
        assertTrue(f.said("Excalibur"))
        assertEquals(PORT_SARIM, f.player.coords)
        assertEquals(1, f.count(EXCALIBUR))

        f.choose(2, 1)
        f.talk(PORT_SARIM_MONK)
        assertEquals(0, f.count(EXCALIBUR))
        assertNull(f.player.worn[Wearpos.Hat.slot])
        assertEquals(1, f.bank().count(EXCALIBUR))
        assertEquals(1, f.bank().count("obj.rune_full_helm"))
        assertTrue(f.said("sends to your bank"))
        assertEquals(ENTRANA_DECK, f.player.coords)
    }

    @Test fun `the whistles only show to a napkin bearer who knows to look`() {
        val f = Fixture(STAGE_MERLIN)
        f.enterWhistleRoom()
        assertEquals(0, f.onTable())
        assertTrue(f.said("watching you"))

        f.give(NAPKIN)
        f.enterWhistleRoom()
        assertEquals(0, f.onTable())
        assertTrue(f.said("don't know what you are looking for"))

        f.jump(STAGE_ENTRANA)
        f.enterWhistleRoom()
        assertEquals(2, f.onTable())
        assertTrue(f.whistlesArePrivate())
        f.enterWhistleRoom()
        assertEquals(2, f.onTable(), "re-entering must not add more")

        f.takeWhistles()
        f.enterWhistleRoom()
        assertEquals(0, f.onTable(), "two carried is enough")
    }

    @Test fun `lost whistles and a lost napkin can always be replaced`() {
        val f = Fixture(STAGE_ENTRANA)
        f.player.napkinGiven = true
        f.talk(GALAHAD)
        assertEquals(1, f.count(NAPKIN))
        assertTrue(f.said("way back to my drawer"))

        f.give(WHISTLE)
        f.enterWhistleRoom()
        assertEquals(1, f.onTable(), "one carried, one more needed")

        val after = Fixture(STAGE_PERCIVAL_SENT)
        after.give(NAPKIN)
        after.enterWhistleRoom()
        assertEquals(1, after.onTable(), "after Percival only one is needed")

        val done = Fixture(STAGE_COMPLETE)
        done.give(NAPKIN)
        done.enterWhistleRoom()
        assertEquals(1, done.onTable(), "the realm stays open after the quest")
    }

    @Test fun `the whistle works beneath the tower and nowhere else in the ordinary world`() {
        val f = Fixture(STAGE_MERLIN)
        f.give(WHISTLE)
        val falador = CoordGrid(2965, 3380, 0)
        f.player.coords = falador
        f.blow()
        assertEquals(falador, f.player.coords)
        assertTrue(f.said("nothing else happens"))

        f.player.coords = TOWER
        f.blow()
        assertEquals(TOWER, f.player.coords)
        assertTrue(f.said("You are not expected"))

        f.jump(STAGE_ENTRANA)
        f.player.coords = falador
        f.blow()
        assertTrue(f.said("beneath the tower north-west of Brimhaven"))

        f.player.coords = TOWER.translateX(2)
        f.blow()
        assertEquals(DEAD_ARRIVAL, f.player.coords)

        f.blow()
        assertEquals(TOWER, f.player.coords)
        assertTrue(f.said("beneath the watchtower"))
    }

    @Test fun `only excalibur may deal the titan's finishing blow`() {
        val f = Fixture(STAGE_REALM_ENTERED)
        f.wield("obj.rune_longsword")
        assertFalse(f.slayTitan())
        assertFalse(f.player.titanDefeated)
        assertTrue(f.said("special sword"))
        assertEquals(f.lastTitan!!.baseHitpointsLvl, f.lastTitan!!.hitpoints)

        f.wield(EXCALIBUR)
        assertFalse(f.slayTitan(type = HitType.Magic), "a spell cast with Excalibur in hand is not its blow")
        assertTrue(f.slayTitan())
        assertTrue(f.player.titanDefeated)
        assertEquals(1, f.player.worn.count(EXCALIBUR), "Excalibur is never used up")
    }

    @Test fun `a beaten titan lets the player cross both ways without another fight`() {
        val f = Fixture(STAGE_REALM_ENTERED)
        f.player.coords = FisherRealm.BRIDGE_EAST
        f.choose(2)
        f.talk(TITAN, at = FisherRealm.BRIDGE_TILE)
        assertEquals(FisherRealm.BRIDGE_EAST, f.player.coords)
        assertTrue(f.said("sword of a true king"))

        f.player.titanDefeated = true
        f.choose(1)
        f.talk(TITAN, at = FisherRealm.BRIDGE_TILE)
        assertEquals(FisherRealm.BRIDGE_WEST, f.player.coords)
        f.choose(1)
        f.talk(TITAN, at = FisherRealm.BRIDGE_TILE)
        assertEquals(FisherRealm.BRIDGE_EAST, f.player.coords)
    }

    @Test fun `dying in the realm keeps everything and wakes the player at the tower for another try`() {
        val f = Fixture(STAGE_REALM_ENTERED)
        f.wield(EXCALIBUR)
        f.player.coords = FisherRealm.BRIDGE_EAST
        val hook = FisherRealmDeath()
        val handling = hook.handleDeath(f.deathAt(f.player.coords))
        assertEquals(Int.MAX_VALUE, handling!!.keepCount)
        assertEquals(TOWER, hook.respawn(f.player))
        f.player.coords = CoordGrid(2965, 3380, 0)
        assertNull(hook.handleDeath(f.deathAt(f.player.coords)))
        assertNull(hook.respawn(f.player))

        assertFalse(f.slayTitan(blowWith = null))
        assertTrue(f.slayTitan())
        assertEquals(STAGE_REALM_ENTERED, f.stage(), "the fight never moves the stage on its own")
    }

    @Test fun `the bell only opens the castle near its walls`() {
        val f = Fixture(STAGE_REALM_ENTERED)
        f.give(BELL)
        f.player.coords = CoordGrid(2965, 3380, 0)
        f.held(BELL)
        assertTrue(f.said("Nothing happens"))
        f.player.coords = DEAD_ARRIVAL
        f.held(BELL)
        assertEquals(DEAD_ARRIVAL, f.player.coords)
        assertTrue(f.said("too far from the castle"))
        assertFalse(f.player.castleEntered)
    }

    @Test fun `the fisher king must be heard out on both his health and his son`() {
        val f = Fixture(STAGE_REALM_ENTERED)
        f.choose(1, 4)
        f.talk(FISHER_KING)
        assertTrue(f.player.heardHealth)
        assertEquals(STAGE_REALM_ENTERED, f.stage())
        assertTrue(f.journal().contains("more to tell me"))

        f.choose(3, 4)
        f.talk(FISHER_KING)
        assertTrue(f.said("table bare"))
        assertEquals(STAGE_REALM_ENTERED, f.stage())

        f.choose(2)
        f.talk(FISHER_KING)
        assertTrue(f.player.heardSon)
        assertEquals(STAGE_HEIR_NEEDED, f.stage())
        assertTrue(f.said("bring my son home"))
    }

    @Test fun `arthur replaces a lost feather and waits for room in a full pack`() {
        val f = Fixture(STAGE_HEIR_NEEDED)
        f.fill()
        f.talk(KING_ARTHUR)
        assertEquals(STAGE_FEATHER, f.stage())
        assertEquals(0, f.count(FEATHER))
        assertTrue(f.said("Your pack is full"))
        f.drop("obj.bronze_dagger")
        f.talk(KING_ARTHUR)
        assertEquals(1, f.count(FEATHER))
        f.talk(KING_ARTHUR)
        assertEquals(1, f.count(FEATHER), "no second feather while one is carried")
    }

    @Test fun `the feather points the way to goblin village`() {
        assertTrue(SirPercival.featherPointing(CoordGrid(2965, 3380, 0)).contains("north"))
        assertTrue(SirPercival.featherPointing(CoordGrid(3222, 3218, 0)).contains("north-west"))
        assertTrue(SirPercival.featherPointing(CoordGrid(2950, 3500, 0)).contains("close"))
        assertTrue(SirPercival.featherPointing(SirPercival.PERCIVAL_TILE).contains("straight down"))
        assertTrue(SirPercival.featherPointing(DEAD_ARRIVAL).contains("not in this world"))
        val f = Fixture(STAGE_FEATHER)
        f.give(FEATHER)
        f.player.coords = CoordGrid(2965, 3380, 0)
        f.held(FEATHER)
        assertTrue(f.said("turns to point north"))
    }

    @Test fun `only open with the feather finds percival, and prod never does`() {
        val f = Fixture(STAGE_FEATHER)
        f.player.coords = SirPercival.SACKS.translateZ(-1)
        f.openSacks()
        assertEquals(0, f.live(SIR_PERCIVAL))
        assertTrue(f.said("Without Arthur's feather"))

        f.give(FEATHER)
        f.prodSacks()
        assertEquals(0, f.live(SIR_PERCIVAL))
        assertTrue(f.said("\"Ow!\""))
        assertFalse(f.player.percivalFound)
    }

    @Test fun `percival never takes the player's only whistle and never appears twice`() {
        val f = Fixture(STAGE_FEATHER)
        f.give(FEATHER)
        f.give(WHISTLE)
        f.player.coords = SirPercival.SACKS.translateZ(-1)
        f.choose(1)
        f.openSacks()
        assertTrue(f.player.percivalFound)
        assertEquals(STAGE_FEATHER, f.stage())
        assertEquals(1, f.count(WHISTLE))
        assertTrue(f.said("I have only one"))
        assertEquals(1, f.live(SIR_PERCIVAL))

        f.choose(1)
        f.openSacks()
        assertEquals(1, f.live(SIR_PERCIVAL), "a second Percival climbed out")

        f.give(WHISTLE)
        f.choose(1)
        f.openSacks()
        assertEquals(STAGE_PERCIVAL_SENT, f.stage())
        assertEquals(1, f.count(WHISTLE))
        assertEquals(0, f.live(SIR_PERCIVAL))

        f.openSacks()
        assertEquals(0, f.live(SIR_PERCIVAL))
        assertTrue(f.said("empty now"))
    }

    @Test fun `the grail cannot be taken early and is never duplicated`() {
        val f = Fixture(STAGE_HEIR_NEEDED)
        f.takeGrail()
        assertEquals(0, f.count(HOLY_GRAIL))
        assertTrue(f.said("realm is not whole"))

        f.player.coords = CoordGrid(2778, 4684, 2)
        f.movedTo(f.player.coords)
        assertTrue(f.said("shape of its absence"))

        f.jump(STAGE_REALM_RESTORED)
        f.takeGrail()
        assertEquals(1, f.count(HOLY_GRAIL))
        f.takeGrail()
        assertEquals(1, f.count(HOLY_GRAIL))
        assertTrue(f.said("already have the Holy Grail"))

        f.drop(HOLY_GRAIL)
        f.talk(KING_ARTHUR)
        assertEquals(STAGE_GRAIL_TAKEN, f.stage())
        assertTrue(f.said("Fetch it again"))
        f.takeGrail()
        assertEquals(1, f.count(HOLY_GRAIL))

        f.talk(KING_ARTHUR)
        assertEquals(STAGE_COMPLETE, f.stage())
        f.takeGrail()
        assertEquals(0, f.count(HOLY_GRAIL))
        assertTrue(f.said("safe there"))
    }

    @Test fun `the restoration is shown once and everyone notices it`() {
        val f = Fixture(STAGE_PERCIVAL_SENT)
        f.give(WHISTLE)
        f.player.coords = TOWER
        f.blow()
        assertEquals(STAGE_REALM_RESTORED, f.stage())
        val shown = f.occurrences("The realm is healed")
        f.blow()
        f.player.coords = TOWER
        f.blow()
        assertEquals(shown, f.occurrences("The realm is healed"))
        assertTrue(f.said("as King Percival keeps it"))

        f.talk(KING_PERCIVAL)
        assertTrue(f.said("such green"))
        f.talk(HolyGrailQuest.HAPPY_PEASANT)
        assertTrue(f.said("rain came with him"))
        f.talk(HolyGrailQuest.GRAIL_MAIDEN)
        assertTrue(f.said("forgotten the birds"))
    }

    @Test fun `the quest state survives a save and reload`() {
        val f = Fixture(STAGE_REALM_ENTERED)
        f.player.napkinGiven = true
        f.player.whistlesFound = true
        f.player.titanDefeated = true
        f.player.heardHealth = true
        val loaded = f.saveAndReload()
        assertEquals(STAGE_REALM_ENTERED, f.hg.stage(loaded))
        assertTrue(loaded.napkinGiven && loaded.whistlesFound && loaded.titanDefeated && loaded.heardHealth)
        assertFalse(loaded.heardSon)
    }

    @Test fun `resetting the quest clears every milestone`() {
        val f = Fixture(STAGE_REALM_ENTERED)
        f.player.napkinGiven = true
        f.player.titanDefeated = true
        f.hg.quest.resetQuest(f.player)
        assertEquals(0, f.stage())
        assertFalse(f.player.napkinGiven || f.player.titanDefeated)
    }

    private class Fixture(stage: Int = 0, merlinDone: Boolean = true, attack: Int = 40) {
        val events = EventBus()
        private val client = RecordingClient()
        private val collision = CollisionFlagMap()
        private val npcs = NpcList()
        private val players = PlayerList()
        private val coroutine = GameCoroutine("holy-grail-test")
        private var result: Result<Unit>? = null
        private lateinit var regions: RegionRegistry
        private val context = ProtectedAccessContextFactory.empty().copy(
            getEventBus = { events }, getAlignment = { TextAlignment() },
            getCollision = { collision }, getNpcList = { npcs },
            getTeleportValidator = { PlayerTeleportValidator(emptySet()) },
            getAreaChecker = { AreaChecker(regions, AreaIndex()) },
            getNpcInteractions = { NpcInteractions(events) },
            getRandom = { DefaultGameRandom(Random(7)) },
            getHitModifier = { NoopPlayerHitModifier },
        )
        private val clock = MapClock(100)
        private val objRegistry = ObjRegistry(ZoneUpdateMap())
        private val objRepo = ObjRepository(clock, objRegistry)
        val npcRepo: NpcRepository
        private val locRepo: LocRepository
        private val picks = ArrayDeque<Int>()
        var lastTitan: Npc? = null

        @OptIn(InternalApi::class)
        val player = Player().apply {
            this.client = this@Fixture.client
            uuid = 4242L
            observerUUID = 4242L
            slotId = 1
            assignUid()
            coords = CAMELOT
            currentMapClock = 100
            processedMapClock = 100
            pendingSequence = EntitySeq.NULL
            pendingFaceAngle = EntityFaceAngle.NULL
            inv = Inventory(InventoryServerType(size = 28, flags = 0), arrayOfNulls(28))
            worn = Inventory(InventoryServerType(size = 14, flags = 0), arrayOfNulls(14))
            bankCapacity = BANK_CAPACITY
            for (stat in listOf("stat.attack", "stat.hitpoints", "stat.defence", "stat.prayer")) {
                val level = (if (stat == "stat.attack") attack else 40).toByte()
                statMap.setBaseLevel(stat, level)
                statMap.setCurrentLevel(stat, level)
            }
        }

        val hg = HolyGrailQuest()
        private val merlin = MerlinsCrystalQuest()
        val realm: FisherRealm
        val room: WhistleRoom

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
            val world = WorldRepository(updates)
            val passages = GenericPassageScript(locRepo, LocInteractions(BoundValidator(collision), events),
                StairNavigator(locRepo, collision), WorldQueueList(), players)
            val ai = AiPlayerInteractions(events, players)
            realm = FisherRealm(hg, objRepo, players, unused<ProtectedAccessLauncher>(), ai,
                NpcDeath(npcRepo, players, objRepo, emptySet(), emptySet()))
            room = WhistleRoom(hg, objRepo, passages)
            val lore = EntranaGrailLore(hg, npcRepo, world)
            val bank = BankInvScript(events, WornBonuses(), WeaponSpeeds(AttackStyles()))
            val scripts = ScriptContext(events, CheatCommandMap(), EngineQueueCache())
            with(hg) { scripts.startup() }
            with(merlin) { scripts.startup() }
            with(KingArthur(merlin, GrailArthur(hg))) { scripts.startup() }
            with(Merlin(hg, passages)) { scripts.startup() }
            with(lore) { scripts.startup() }
            with(EntranaMonks(HeroesQuest(ShieldOfArravQuest()), lore)) { scripts.startup() }
            with(EntranaBoat(LostCityQuest(), bank, events)) { scripts.startup() }
            with(Galahad(hg)) { scripts.startup() }
            with(room) { scripts.startup() }
            with(MagicWhistle(hg)) { scripts.startup() }
            with(realm) { scripts.startup() }
            with(FisherRealmFolk(hg)) { scripts.startup() }
            with(SirPercival(hg, npcRepo, world)) { scripts.startup() }
            if (merlinDone) merlin.quest.jumpToStage(player, MERLIN_ENDSTATE)
            if (stage > 0) hg.quest.jumpToStage(player, stage)
        }

        fun access() = ProtectedAccess(player, coroutine, context)

        fun stage(): Int = hg.stage(player)

        fun jump(stage: Int) = hg.quest.jumpToStage(player, stage)

        fun choose(vararg options: Int) {
            picks += options.toList()
        }

        fun give(obj: String, count: Int = 1) {
            repeat(count) {
                val slot = player.inv.indexOfFirst { it == null }
                player.inv[slot] = InvObj(obj, 1)
            }
        }

        fun wield(obj: String) {
            val type = checkNotNull(ServerCacheManager.getItem(obj.asRSCM()))
            player.worn[type.wearpos1] = InvObj(obj, 1)
        }

        fun fill() {
            while (player.inv.freeSpace() > 0) give("obj.bronze_dagger")
        }

        fun drop(obj: String) {
            val slot = player.inv.indexOfFirst { it?.id == obj.asRSCM() }
            player.inv[slot] = null
        }

        fun count(obj: String): Int = player.inv.count(obj)

        fun occurrences(text: String): Int = output().windowed(text.length).count { it == text }

        fun bank(): Inventory = checkNotNull(player.invMap["inv.bank"])

        fun xp(stat: String): Int = player.statMap.getXP(stat)

        fun qp(): Int = player.vars["varp.qp"]

        fun said(text: String): Boolean = output().contains(text)

        fun journal(): String = hg.questLog(access())

        fun live(type: String): Int = npcs.count { it != null && it.isType(type) && it.isSlotAssigned }

        fun startQuest() {
            choose(1, 1)
            talk(KING_ARTHUR)
        }

        fun talk(type: String, at: CoordGrid = player.coords.translateX(1)) {
            val npc = Npc(type, at)
            npcRepo.add(npc, Int.MAX_VALUE)
            dispatch(NpcEvents.Op1(npc))
            if (npc.isSlotAssigned) npcRepo.del(npc, Int.MAX_VALUE)
        }

        fun held(obj: String) {
            val slot = player.inv.indexOfFirst { it?.id == obj.asRSCM() }
            val type = checkNotNull(ServerCacheManager.getItem(obj.asRSCM()))
            dispatch(HeldObjEvents.Op1(slot, checkNotNull(player.inv[slot]), type, player.inv))
        }

        fun blow() {
            if (WHISTLE !in player.inv) give(WHISTLE)
            held(WHISTLE)
        }

        fun enterWhistleRoom() {
            player.coords = WhistleRoom.TABLE.translateZ(1)
            room.enter(player, player.invMap["inv.bank"])
        }

        fun onTable(): Int = room.waitingOnTable(player)

        fun whistlesArePrivate(): Boolean =
            objRegistry.findAll(WhistleRoom.TABLE).all { it.receiverId == player.observerUUID }

        fun takeWhistles() {
            for (obj in objRegistry.findAll(WhistleRoom.TABLE).toList()) {
                objRepo.del(obj, Int.MAX_VALUE)
                give(WHISTLE)
            }
        }

        fun slayTitan(type: HitType = HitType.Melee, blowWith: Player? = player): Boolean {
            val titan = lastTitan ?: Npc(TITAN, FisherRealm.BRIDGE_TILE).also {
                npcRepo.add(it, Int.MAX_VALUE)
                lastTitan = it
            }
            titan.hitpoints = 0
            if (blowWith != null) realm.recordBlow(titan, blowWith, type)
            return realm.judge(titan, player)
        }

        fun openSacks() = locOp(PERCIVAL_SACKS, SirPercival.SACKS, InteractionOp.Op2)

        fun prodSacks() = locOp(PERCIVAL_SACKS, SirPercival.SACKS, InteractionOp.Op1)

        fun takeGrail() {
            player.coords = GRAIL_TABLE.translateZ(-1)
            val obj = objRepo.add(HOLY_GRAIL, GRAIL_TABLE, Int.MAX_VALUE)
            dispatch(ObjEvents.Op3(obj))
            if (objRegistry.findAll(GRAIL_TABLE).any { it === obj }) objRepo.del(obj, Int.MAX_VALUE)
        }

        fun movedTo(coords: CoordGrid) {
            player.coords = coords
            events.publish(org.rsmod.api.player.events.PlayerMovementEvent.CoordsMovedEvent(player, coords))
        }

        fun deathAt(coords: CoordGrid) =
            PlayerDeathContext(player, coords, false, 0, false, false, false, false, false, 0, null)

        fun saveAndReload(): Player {
            val loaded = Player()
            loaded.vars.backing.putAll(player.vars.backing)
            player.attr[QUEST_STAGE_MAP_ATTR]?.let { loaded.attr[QUEST_STAGE_MAP_ATTR] = it.toMutableMap() }
            hg.quest.syncState(loaded)
            return loaded
        }

        private fun locOp(symbol: String, coords: CoordGrid, op: InteractionOp) {
            val type = checkNotNull(ServerCacheManager.getObject(symbol.asRSCM()))
            val loc = BoundLocInfo(LocInfo(0, coords, LocEntity(type.id, 10, 0)), type)
            val event = LocInteractions(BoundValidator(collision), events).opTrigger(player, loc, op)
            dispatch(checkNotNull(event) { "No $op handler for $symbol" })
        }

        private fun dispatch(event: SuspendEvent<ProtectedAccess>) {
            player.clearPendingAction(events)
            result = null
            player.activeCoroutine = coroutine
            val access = access()
            val block: suspend () -> Unit = { assertTrue(events.publish(access, event)) }
            block.startCoroutine(object : Continuation<Unit> {
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
        const val MERLIN_ENDSTATE = 7
        const val BANK_CAPACITY = 800
        const val PORT_SARIM_MONK = "npc.shipmonk"

        val CAMELOT = CoordGrid(2763, 3513, 0)
        val PORT_SARIM = CoordGrid(3046, 3235, 0)
        val ENTRANA_DECK = CoordGrid(2834, 3331, 1)
        val GRAIL_TABLE = CoordGrid(2649, 4684, 2)

        val AREAS =
            listOf(
                intArrayOf(2744, 3488, 2784, 3528),
                intArrayOf(3032, 3224, 3064, 3248),
                intArrayOf(2824, 3320, 2864, 3360),
                intArrayOf(2600, 3464, 2624, 3488),
                intArrayOf(3096, 3352, 3120, 3368),
                intArrayOf(2728, 3224, 2752, 3248),
                intArrayOf(2624, 4672, 2815, 4735),
                intArrayOf(2944, 3496, 2976, 3520),
                intArrayOf(2952, 3368, 2976, 3392),
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
