package org.rsmod.content.quest.area.tirannwn.rovingelves

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
import org.rsmod.api.death.NpcDeathKillContext
import org.rsmod.api.hunt.NpcSearch
import org.rsmod.api.inv.storage.PlayerItemStorage
import org.rsmod.api.invtx.InvTransactionsScript
import org.rsmod.api.obj.charges.ObjChargeManager
import org.rsmod.api.obj.charges.ObjChargeManager.Companion.isFailure
import org.rsmod.api.player.dialogue.align.TextAlignment
import org.rsmod.api.player.events.interact.HeldObjEvents
import org.rsmod.api.player.events.interact.NpcEvents
import org.rsmod.api.player.hit.modifier.NoopPlayerHitModifier
import org.rsmod.api.player.hit.modifier.StandardPlayerHitModifier
import org.rsmod.api.player.hit.processor.DamageOnlyPlayerHitProcessor
import org.rsmod.api.player.hook.PlayerTeleportValidateHook
import org.rsmod.api.player.hook.PlayerTeleportValidator
import org.rsmod.api.player.hook.RestrictedAction
import org.rsmod.api.player.input.ResumePauseButtonInput
import org.rsmod.api.player.interact.LocInteractions
import org.rsmod.api.player.interact.LocUInteractions
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
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.route.BoundValidator
import org.rsmod.content.quest.area.ardougne.regicide.IsafdarObstacles
import org.rsmod.content.quest.area.ardougne.regicide.RegicideQuest
import org.rsmod.content.quest.area.ardougne.regicide.TyrasGuardEncounter
import org.rsmod.content.quest.area.baxtorianfalls.waterfall.BaxtorianFalls
import org.rsmod.content.quest.area.baxtorianfalls.waterfall.GlarialsTomb
import org.rsmod.content.quest.area.baxtorianfalls.waterfall.WaterfallDungeon
import org.rsmod.content.quest.area.baxtorianfalls.waterfall.WaterfallQuest
import org.rsmod.content.quest.area.baxtorianfalls.waterfall.npcs.Golrie
import org.rsmod.content.quest.area.tirannwn.mourningsend.ArianwynBriefing
import org.rsmod.content.quest.area.tirannwn.mourningsend.ElunedErrands
import org.rsmod.content.quest.area.tirannwn.mourningsend.MourningsEndQuest
import org.rsmod.content.quest.area.tirannwn.rovingelves.RovingElvesQuest.Companion.COINS
import org.rsmod.content.quest.area.tirannwn.rovingelves.RovingElvesQuest.Companion.CRYSTAL_BOW
import org.rsmod.content.quest.area.tirannwn.rovingelves.RovingElvesQuest.Companion.CRYSTAL_CHARGES
import org.rsmod.content.quest.area.tirannwn.rovingelves.RovingElvesQuest.Companion.CRYSTAL_SHIELD
import org.rsmod.content.quest.area.tirannwn.rovingelves.RovingElvesQuest.Companion.ELUNED
import org.rsmod.content.quest.area.tirannwn.rovingelves.RovingElvesQuest.Companion.FULL_CHARGES
import org.rsmod.content.quest.area.tirannwn.rovingelves.RovingElvesQuest.Companion.ILFEEN
import org.rsmod.content.quest.area.tirannwn.rovingelves.RovingElvesQuest.Companion.ILFEEN_VARBIT
import org.rsmod.content.quest.area.tirannwn.rovingelves.RovingElvesQuest.Companion.ISLWYN
import org.rsmod.content.quest.area.tirannwn.rovingelves.RovingElvesQuest.Companion.ISLWYN_TRADE
import org.rsmod.content.quest.area.tirannwn.rovingelves.RovingElvesQuest.Companion.ISLWYN_VARBIT
import org.rsmod.content.quest.area.tirannwn.rovingelves.RovingElvesQuest.Companion.MOSS_GUARDIAN
import org.rsmod.content.quest.area.tirannwn.rovingelves.RovingElvesQuest.Companion.NEW_SEED
import org.rsmod.content.quest.area.tirannwn.rovingelves.RovingElvesQuest.Companion.OLD_SEED
import org.rsmod.content.quest.area.tirannwn.rovingelves.RovingElvesQuest.Companion.REWARD_BOW
import org.rsmod.content.quest.area.tirannwn.rovingelves.RovingElvesQuest.Companion.REWARD_SHIELD
import org.rsmod.content.quest.area.tirannwn.rovingelves.RovingElvesQuest.Companion.SOTE_SCOUT
import org.rsmod.content.quest.area.tirannwn.rovingelves.RovingElvesQuest.Companion.SPADE
import org.rsmod.content.quest.area.tirannwn.rovingelves.RovingElvesQuest.Companion.STAGE_ACCEPTED
import org.rsmod.content.quest.area.tirannwn.rovingelves.RovingElvesQuest.Companion.STAGE_COMPLETE
import org.rsmod.content.quest.area.tirannwn.rovingelves.RovingElvesQuest.Companion.STAGE_GET_SEED
import org.rsmod.content.quest.area.tirannwn.rovingelves.RovingElvesQuest.Companion.STAGE_LIED
import org.rsmod.content.quest.area.tirannwn.rovingelves.RovingElvesQuest.Companion.STAGE_PLANTED
import org.rsmod.content.quest.area.tirannwn.rovingelves.RovingElvesQuest.Companion.STAGE_PLANT_SEED
import org.rsmod.content.quest.area.tirannwn.rovingelves.RovingElvesQuest.Companion.WEAPON_SEED
import org.rsmod.content.quest.area.tirannwn.rovingelves.npcs.Eluned
import org.rsmod.content.quest.area.tirannwn.rovingelves.npcs.Ilfeen
import org.rsmod.content.quest.area.tirannwn.rovingelves.npcs.Islwyn
import org.rsmod.content.quest.area.varrock.dragonslayer.DoorPassage
import org.rsmod.content.quest.manager.QUEST_STAGE_MAP_ATTR
import org.rsmod.content.quest.manager.QuestItemDrops
import org.rsmod.content.quest.manager.QuestRequirementMode
import org.rsmod.content.quest.manager.QuestRequirementPolicy
import org.rsmod.content.quest.manager.QuestRequirements
import org.rsmod.coroutine.GameCoroutine
import org.rsmod.events.EventBus
import org.rsmod.events.SuspendEvent
import org.rsmod.game.MapClock
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.client.Client
import org.rsmod.game.entity.ControllerList
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.util.EntityFaceAngle
import org.rsmod.game.hit.HitBuilder
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
import org.rsmod.game.region.RegionListLarge
import org.rsmod.game.region.RegionListSmall
import org.rsmod.game.region.RegionListWorldEntity
import org.rsmod.game.seq.EntitySeq
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap
import sun.misc.Unsafe

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class RovingElvesInteractionTest {

    @Test fun `Regicide and Waterfall Quest gate the start through the requirement policy`() {
        val f = Fixture()
        respectingProgress {
            f.talk(ISLWYN)
            assertEquals(0, f.stage())
            assertTrue(f.said("no mood for company"))
            f.regicide.quest.jumpToStage(f.player, RegicideQuest.STAGE_COMPLETE)
            f.talk(ISLWYN)
            assertEquals(0, f.stage(), "Waterfall Quest is still missing")
            f.completeWaterfall()
            f.choose(1, 1)
            f.talk(ISLWYN)
        }
        assertEquals(STAGE_ACCEPTED, f.stage())
        assertTrue(f.said("Speak with her"))

        val assumed = Fixture()
        assumed.choose(1, 1)
        assumed.talk(ISLWYN)
        assertEquals(STAGE_ACCEPTED, assumed.stage(), "the default policy counts both as done")
        assertEquals(0, assumed.waterfall.stage(assumed.player), "without touching the real quests")
        assertEquals(0, assumed.regicide.stage(assumed.player))
    }

    @Test fun `a lie sends the player away until they tell the truth`() {
        val f = Fixture()
        f.choose(2)
        f.talk(ISLWYN)
        assertEquals(STAGE_LIED, f.stage())
        assertEquals(1, f.player.toldLie)
        assertTrue(f.journal().contains("tell him the truth"))
        f.talk(ELUNED)
        assertEquals(STAGE_LIED, f.stage(), "Eluned sends them back to Islwyn")
        f.choose(1, 2)
        f.talk(ISLWYN)
        assertEquals(STAGE_LIED, f.stage(), "owning up isn't the same as offering help")
        f.choose(1, 1)
        f.talk(ISLWYN)
        assertEquals(STAGE_ACCEPTED, f.stage())
        assertTrue(f.said("I took Glarial's ashes"))
    }

    @Test fun `Eluned's instructions come before the guardian will drop a seed`() {
        val f = Fixture(STAGE_ACCEPTED)
        assertFalse(f.seedDrops())
        f.talk(ELUNED)
        assertEquals(STAGE_GET_SEED, f.stage())
        assertTrue(f.said("bare hands"))
        assertTrue(f.journal().contains("Moss Guardian") && f.journal().contains("no weapons, armour or runes"))
        assertTrue(f.seedDrops())
        f.give(OLD_SEED)
        assertFalse(f.seedDrops(), "not while one is held")
        f.bank(OLD_SEED)
        assertFalse(f.seedDrops(), "or banked")
        f.unbank(OLD_SEED)
        f.drop(OLD_SEED)
        assertTrue(f.seedDrops(), "a lost seed drops again")
        f.jump(STAGE_PLANT_SEED)
        assertFalse(f.seedDrops(), "and never once the seed is enchanted")
    }

    @Test fun `only the killer of the real guardian is credited, and only while they need it`() {
        val f = Fixture(STAGE_GET_SEED)
        val hook = MossGuardianKillHook(f.roving)
        val tomb = CoordGrid(2541, 9845, 0)
        hook.onKill(NpcDeathKillContext(f.player, Npc(MOSS_GUARDIAN, tomb), 0))
        assertEquals(1, f.player.guardianSlain)
        assertEquals(0, f.second.guardianSlain, "the other player in the tomb gets nothing")
        for (other in listOf("npc.mossgiant", "npc.nzone_roving_mossgiant_normal", "npc.nzone_roving_mossgiant_hard")) {
            hook.onKill(NpcDeathKillContext(f.second, Npc(other, tomb), 0))
        }
        assertEquals(0, f.second.guardianSlain, "ordinary and Nightmare Zone moss giants never count")
        val early = Fixture(STAGE_ACCEPTED)
        MossGuardianKillHook(early.roving).onKill(NpcDeathKillContext(early.player, Npc(MOSS_GUARDIAN, tomb), 0))
        assertEquals(0, early.player.guardianSlain, "not before Eluned has explained")
    }

    @Test fun `Eluned enchants only a seed the player won, in one swap`() {
        val f = Fixture(STAGE_GET_SEED)
        f.give(OLD_SEED)
        f.talk(ELUNED)
        assertEquals(STAGE_GET_SEED, f.stage(), "someone else's seed")
        assertTrue(f.said("not won by your own hand"))
        f.player.guardianSlain = 1
        f.bank(OLD_SEED)
        f.talk(ELUNED)
        assertEquals(STAGE_GET_SEED, f.stage())
        assertTrue(f.said("Bring the seed with you"))
        f.unbank(OLD_SEED)
        f.talk(ELUNED)
        assertEquals(STAGE_PLANT_SEED, f.stage())
        assertEquals(0, f.count(OLD_SEED))
        assertEquals(1, f.count(NEW_SEED))
        f.talk(ELUNED)
        assertEquals(1, f.count(NEW_SEED), "talking again doesn't add another")
    }

    @Test fun `a lost enchanted seed is replaced by Eluned without another kill`() {
        val f = Fixture(STAGE_PLANT_SEED)
        f.fill()
        f.talk(ELUNED)
        assertEquals(0, f.count(NEW_SEED), "no room")
        f.drop("obj.bronze_dagger")
        f.talk(ELUNED)
        assertEquals(1, f.count(NEW_SEED))
        f.talk(ELUNED)
        assertEquals(1, f.count(NEW_SEED), "one at a time")
        f.bank(NEW_SEED)
        f.talk(ELUNED)
        assertEquals(0, f.count(NEW_SEED), "a banked seed isn't lost")
        assertEquals(STAGE_PLANT_SEED, f.stage())
    }

    @Test fun `planting needs the enchanted seed, a spade and the chalice`() {
        val f = Fixture(STAGE_PLANT_SEED)
        f.give(NEW_SEED)
        f.player.coords = CoordGrid(2589, 9888, 0)
        f.give(SPADE)
        f.held1(NEW_SEED)
        assertEquals(STAGE_PLANT_SEED, f.stage())
        assertTrue(f.said("isn't the right place"))
        f.drop(SPADE)
        f.player.coords = CoordGrid(2602, 9910, 0)
        f.held1(NEW_SEED)
        assertTrue(f.said("need a spade"))
        assertEquals(1, f.count(NEW_SEED))
        f.give(SPADE)
        f.held1(NEW_SEED)
        assertEquals(STAGE_PLANTED, f.stage())
        assertEquals(0, f.count(NEW_SEED))
        assertEquals(1, f.count(SPADE), "the spade is kept")
        assertTrue(f.growthAt(CoordGrid(2602, 9911, 0)), "crystal grows on the free tile beside the player")
        assertTrue(f.journal().contains("tell Islwyn"))

        val real = Fixture(STAGE_PLANT_SEED)
        real.give(NEW_SEED)
        real.give(SPADE)
        real.player.coords = CoordGrid(2564, 9911, 0)
        real.held1(NEW_SEED)
        assertEquals(STAGE_PLANTED, real.stage(), "beneath the chalice in the room the key door opens on")

        val early = Fixture(STAGE_GET_SEED)
        early.give(OLD_SEED)
        early.give(SPADE)
        early.player.coords = CoordGrid(2602, 9910, 0)
        assertFalse(early.heldOpHandled(OLD_SEED), "the dead seed cannot be planted")
        early.give(NEW_SEED)
        early.held1(NEW_SEED)
        assertEquals(STAGE_GET_SEED, early.stage())
        assertEquals(1, early.count(NEW_SEED))
    }

    @Test fun `the planting commits the seed and the stage together`() {
        val f = Fixture(STAGE_PLANT_SEED)
        f.player.coords = CoordGrid(2602, 9910, 0)
        f.give(SPADE)
        var committed = true
        f.dispatch { committed = with(f.consecration) { commitPlanting() } }
        assertFalse(committed)
        assertEquals(STAGE_PLANT_SEED, f.stage(), "no seed, no planting")
        f.give(NEW_SEED)
        f.dispatch { committed = with(f.consecration) { commitPlanting() } }
        assertTrue(committed)
        assertEquals(STAGE_PLANTED, f.stage())
        assertEquals(0, f.count(NEW_SEED))
        f.give(NEW_SEED)
        f.held1(NEW_SEED)
        assertTrue(f.said("already been consecrated"))
        assertEquals(1, f.count(NEW_SEED))
    }

    @Test fun `both rewards hold 500 charges and the quest completes once`() {
        val f = Fixture(STAGE_PLANTED)
        f.player.statMap.setBaseLevel("stat.agility", 1)
        f.player.statMap.setBaseLevel("stat.ranged", 1)
        val strength = f.player.statMap.getXP("stat.strength")
        f.choose(1)
        f.talk(ISLWYN)
        assertEquals(STAGE_COMPLETE, f.stage())
        assertEquals(1, f.count(CRYSTAL_BOW), "the reward isn't gated on wielding it")
        assertEquals(500, f.charges(CRYSTAL_BOW))
        assertEquals(REWARD_BOW, f.player.rewardChoice)
        assertEquals(1, f.player.vars["varp.qp"])
        assertEquals(10_000, f.player.statMap.getXP("stat.strength") - strength)
        assertTrue(f.said("Which would you like?"))
        f.choose(2)
        f.talk(ISLWYN)
        assertEquals(1, f.count(CRYSTAL_BOW))
        assertEquals(0, f.count(CRYSTAL_SHIELD), "one reward only")
        assertEquals(1, f.player.vars["varp.qp"])
        assertTrue(f.roving.completedLog(f.access()).contains("crystal bow"))

        val shield = Fixture(STAGE_PLANTED)
        shield.player.xpRate = 2.0
        val before = shield.player.statMap.getXP("stat.strength")
        shield.choose(2)
        shield.talk(ISLWYN)
        assertEquals(500, shield.charges(CRYSTAL_SHIELD))
        assertEquals(0, shield.count(CRYSTAL_BOW))
        assertEquals(REWARD_SHIELD, shield.player.rewardChoice)
        assertEquals(20_000, shield.player.statMap.getXP("stat.strength") - before, "quest xp follows the player's xp rate")
    }

    @Test fun `a full inventory or second thoughts leave the reward waiting`() {
        val f = Fixture(STAGE_PLANTED)
        f.choose(3)
        f.talk(ISLWYN)
        assertEquals(STAGE_PLANTED, f.stage())
        f.fill()
        f.choose(1)
        f.talk(ISLWYN)
        assertEquals(STAGE_PLANTED, f.stage())
        assertEquals(0, f.player.rewardChoice)
        assertEquals(0, f.player.vars["varp.qp"])
        assertTrue(f.said("no room to carry it"))
        f.drop("obj.bronze_dagger")
        f.choose(2)
        f.talk(ISLWYN)
        assertEquals(STAGE_COMPLETE, f.stage())
        assertEquals(500, f.charges(CRYSTAL_SHIELD))
    }

    @Test fun `crystal charges live on the item and run down into the inactive forms`() {
        val manager = ObjChargeManager()
        val f = Fixture(STAGE_COMPLETE)
        f.player.worn[Wearpos.RightHand.slot] = InvObj(CRYSTAL_BOW, vars = CrystalSinging.varsFor(500))
        f.player.worn[Wearpos.LeftHand.slot] = InvObj(CRYSTAL_SHIELD, vars = CrystalSinging.varsFor(500))
        assertEquals(500, manager.getCharges(f.player.worn[Wearpos.RightHand.slot], CRYSTAL_CHARGES))
        val bow = manager.reduceWornCharges(f.player, Wearpos.RightHand, CRYSTAL_CHARGES, 499)
        assertFalse(bow.isFailure())
        assertEquals(CRYSTAL_BOW.asRSCM(), f.player.worn[Wearpos.RightHand.slot]?.id)
        assertTrue((manager.reduceWornCharges(f.player, Wearpos.RightHand, CRYSTAL_CHARGES, 1) as ObjChargeManager.Uncharge.Success).fullyUncharged)
        assertEquals("obj.crystal_bow_inactive".asRSCM(), f.player.worn[Wearpos.RightHand.slot]?.id)
        manager.reduceWornCharges(f.player, Wearpos.LeftHand, CRYSTAL_CHARGES, 500)
        assertEquals("obj.crystal_shield_inactive".asRSCM(), f.player.worn[Wearpos.LeftHand.slot]?.id)
    }

    @Test fun `wielding crystal is gated on Roving Elves, receiving it is not`() {
        val f = Fixture(STAGE_PLANTED)
        val hook = CrystalEquipmentWearHook()
        respectingProgress {
            for (obj in CrystalEquipmentWearHook.CRYSTAL_ITEMS) {
                val type = checkNotNull(ServerCacheManager.getItem(obj.asRSCM()))
                assertTrue(hook.restriction(f.player, RestrictedAction.Equip(type))!!.contains("Roving Elves"), obj)
            }
            f.jump(STAGE_COMPLETE)
            val bow = checkNotNull(ServerCacheManager.getItem(CRYSTAL_BOW.asRSCM()))
            assertNull(hook.restriction(f.player, RestrictedAction.Equip(bow)))
            val dagger = checkNotNull(ServerCacheManager.getItem("obj.bronze_dagger".asRSCM()))
            assertNull(hook.restriction(Fixture().player, RestrictedAction.Equip(dagger)))
        }
    }

    @Test fun `Protect from Melee stops the guardian's melee but not its magic`() {
        val f = Fixture()
        val modifier = StandardPlayerHitModifier(EventBus())
        VarPlayerIntMapSetter.set(f.player, "varbit.prayer_protectfrommelee", 1)
        assertEquals(0, modify(modifier, f.player, HitType.Melee, 14, penetration = 0))
        assertEquals(14, modify(modifier, f.player, HitType.Magic, 14, MossGuardian.PIERCES_PRAYER))
        VarPlayerIntMapSetter.set(f.player, "varbit.prayer_protectfrommelee", 0)
        VarPlayerIntMapSetter.set(f.player, "varbit.prayer_protectfrommagic", 1)
        assertEquals(0, modify(modifier, f.player, HitType.Magic, 14, penetration = 0), "an ordinary spell would be stopped")
        assertEquals(14, modify(modifier, f.player, HitType.Magic, 14, MossGuardian.PIERCES_PRAYER), "the guardian's is not")
    }

    @Test fun `progress, the kill and the reward survive a relog`() {
        val f = Fixture(STAGE_GET_SEED)
        f.player.guardianSlain = 1
        val loaded = f.saveAndReload()
        assertEquals(STAGE_GET_SEED, f.roving.stage(loaded))
        assertEquals(1, loaded.guardianSlain)

        val done = Fixture(STAGE_PLANTED)
        done.choose(2)
        done.talk(ISLWYN)
        val back = done.saveAndReload()
        assertTrue(done.roving.isComplete(back))
        assertEquals(REWARD_SHIELD, back.rewardChoice)
        assertEquals(1, back.vars[ISLWYN_VARBIT], "Islwyn offers Trade again after the relog")
        VarPlayerIntMapSetter.set(back, ISLWYN_VARBIT, SOTE_SCOUT)
        done.roving.syncVars(back)
        assertEquals(SOTE_SCOUT, back.vars[ISLWYN_VARBIT], "a later quest's scout form is left alone")
    }

    @Test fun `two players progress side by side without sharing anything`() {
        val f = Fixture(STAGE_GET_SEED)
        MossGuardianKillHook(f.roving).onKill(NpcDeathKillContext(f.player, Npc(MOSS_GUARDIAN, CoordGrid(2528, 9843, 0)), 0))
        f.give(OLD_SEED)
        f.talk(ELUNED)
        assertEquals(STAGE_PLANT_SEED, f.stage())
        assertTrue(f.seedDropsFor(f.second), "the second player still needs their own")
        f.give(OLD_SEED, p = f.second)
        f.talk(ELUNED, p = f.second)
        assertEquals(STAGE_GET_SEED, f.roving.stage(f.second), "and must win it themselves")
        f.player.coords = CoordGrid(2602, 9910, 0)
        f.give(SPADE)
        f.held1(NEW_SEED)
        assertEquals(STAGE_PLANTED, f.stage())
        assertEquals(STAGE_GET_SEED, f.roving.stage(f.second), "one ritual advances one player")
    }

    @Test fun `Islwyn sells and Ilfeen sings crystal at full charge once the quest is done`() {
        val f = Fixture(STAGE_PLANTED)
        f.give(COINS, 900_000)
        f.op3(ISLWYN_TRADE)
        assertEquals(900_000, f.stack(COINS), "nothing for sale before the reward")
        f.jump(STAGE_COMPLETE)
        f.choose(1)
        f.op3(ISLWYN_TRADE)
        assertEquals(0, f.stack(COINS))
        assertEquals(FULL_CHARGES, f.charges(CRYSTAL_BOW))
        f.give(COINS, 100)
        f.choose(2)
        f.op3(ISLWYN_TRADE)
        assertEquals(0, f.count(CRYSTAL_SHIELD), "not without 750,000 coins")
        assertEquals(100, f.stack(COINS))

        val g = Fixture()
        for (round in 0 until 5) {
            g.give(WEAPON_SEED)
            g.give(COINS, Ilfeen.shieldPrice(round))
            g.choose(2)
            g.talk(ILFEEN)
            assertEquals(round + 1, g.count(CRYSTAL_SHIELD))
            assertEquals(0, g.stack(COINS), "round $round cost ${Ilfeen.shieldPrice(round)}")
        }
        assertEquals(listOf(750_000, 600_000, 450_000, 300_000, 150_000, 150_000), (0..5).map(Ilfeen::shieldPrice))
        assertEquals(listOf(900_000, 720_000, 540_000, 360_000, 180_000), (0..4).map(Ilfeen::bowPrice))
        assertEquals(0, g.count(WEAPON_SEED))
        assertEquals(1, g.player.vars[ILFEEN_VARBIT], "the Enchant option opens at the lowest price")
        g.give(WEAPON_SEED)
        g.choose(1)
        g.talk(ILFEEN)
        assertEquals(1, g.count(WEAPON_SEED), "no coins, no bow")
    }

    @Test fun `Glarial's tomb checks everything carried and worn and keeps the pebble`() {
        val f = Fixture()
        f.completeWaterfall()
        val outside = CoordGrid(2557, 3444, 0)
        f.player.coords = outside
        f.give(PEBBLE)
        for (forbidden in listOf("obj.bronze_dagger", "obj.airrune")) {
            f.give(forbidden)
            f.locU(TOMBSTONE, TOMBSTONE_TILE, PEBBLE)
            assertEquals(outside, f.player.coords, forbidden)
            assertTrue(f.said("come in peace"))
            f.drop(forbidden)
        }
        for (forbidden in listOf("obj.bronze_full_helm", "obj.bronze_arrow")) {
            f.give(forbidden)
            f.wear(forbidden)
            f.locU(TOMBSTONE, TOMBSTONE_TILE, PEBBLE)
            assertEquals(outside, f.player.coords, "worn $forbidden")
            f.player.worn[checkNotNull(ServerCacheManager.getItem(forbidden.asRSCM())).wearpos1] = null
        }
        f.give("obj.shark", 3)
        f.give("obj.4dose2attack")
        f.give(SPADE)
        f.give("obj.ring_of_recoil")
        f.wear("obj.ring_of_recoil")
        f.give("obj.amulet_of_strength")
        f.wear("obj.amulet_of_strength")
        f.locU(TOMBSTONE, TOMBSTONE_TILE, PEBBLE)
        assertEquals(CoordGrid(2555, 9844, 0), f.player.coords, "food, potions and jewellery are welcome")
        assertEquals(1, f.count(PEBBLE), "the pebble is never used up")
        assertTrue(f.waterfall.isComplete(f.player), "and Waterfall Quest is untouched")
    }

    @Test fun `a lost pebble and amulet come back once each, without replaying Waterfall Quest`() {
        val f = Fixture()
        f.completeWaterfall()
        f.waterfall.metGolrie.set(f.player, true)
        f.talk(GOLRIE)
        assertEquals(1, f.count(PEBBLE))
        f.talk(GOLRIE)
        assertEquals(1, f.count(PEBBLE))
        f.bank(PEBBLE)
        f.talk(GOLRIE)
        assertEquals(0, f.count(PEBBLE), "a banked pebble isn't lost")

        f.locOp(CHEST, CoordGrid(2530, 9844, 0))
        assertEquals(1, f.count(AMULET))
        f.bank(AMULET)
        f.locOp(CHEST, CoordGrid(2530, 9844, 0))
        assertEquals(0, f.count(AMULET), "nor a banked amulet")

        val assumed = Fixture()
        assumed.talk(GOLRIE)
        assertEquals(1, assumed.count(PEBBLE), "Waterfall Quest counted as done by the policy")
        assertEquals(0, assumed.waterfall.stage(assumed.player))
        respectingProgress {
            val fresh = Fixture()
            fresh.talk(GOLRIE)
            assertEquals(0, fresh.count(PEBBLE), "but not for someone who must still do it")
        }
    }

    @Test fun `the falls open without the amulet after Waterfall Quest, and the key is in the crate`() {
        val f = Fixture()
        f.player.coords = CoordGrid(2509, 3493, 0)
        f.locOp("loc.lograft_waterfall_quest", CoordGrid(2509, 3494, 0))
        assertEquals(CoordGrid(2512, 3481, 0), f.player.coords, "the policy counts Waterfall Quest done")
        f.player.coords = CoordGrid(2511, 3463, 0)
        f.locOp("loc.waterfall_ledge_door", CoordGrid(2511, 3464, 0))
        assertEquals(CoordGrid(2575, 9862, 0), f.player.coords)
        assertEquals(0, f.waterfall.stage(f.player), "entering never starts Waterfall Quest")
        f.player.coords = CoordGrid(2589, 9887, 0)
        f.locOp(KEY_CRATE, CoordGrid(2589, 9888, 0))
        assertEquals(1, f.count(BAXTORIAN_KEY))
        f.locOp(KEY_CRATE, CoordGrid(2589, 9888, 0))
        assertEquals(1, f.count(BAXTORIAN_KEY), "one key at a time")

        respectingProgress {
            val fresh = Fixture()
            fresh.player.coords = CoordGrid(2509, 3493, 0)
            fresh.locOp("loc.lograft_waterfall_quest", CoordGrid(2509, 3494, 0))
            assertEquals(CoordGrid(2509, 3493, 0), fresh.player.coords, "the raft still waits for Waterfall Quest")
        }
    }

    @Test fun `Isafdar's dense forest wants 56 Agility, boosts included, separate from the quest`() {
        val f = Fixture()
        f.regicide.quest.jumpToStage(f.player, RegicideQuest.STAGE_COMPLETE)
        f.setAgility(base = 55, current = 55)
        f.player.coords = EAST_OF_BAND
        f.forest(BAND_EAST)
        assertTrue(f.said("Agility level of 56"))
        assertEquals(EAST_OF_BAND, f.player.coords)
        f.setAgility(base = 50, current = 56)
        f.forest(BAND_EAST)
        assertEquals(CoordGrid(2237, 3149, 0), f.player.coords, "a boosted level is enough")

        val assumed = Fixture()
        assumed.setAgility(base = 56, current = 56)
        assumed.player.coords = EAST_OF_BAND
        assumed.forest(BAND_EAST)
        assertEquals(CoordGrid(2237, 3149, 0), assumed.player.coords, "Regicide counted as done by the policy")
        respectingProgress {
            val fresh = Fixture()
            fresh.setAgility(base = 99, current = 99)
            fresh.player.coords = EAST_OF_BAND
            fresh.forest(BAND_EAST)
            assertEquals(EAST_OF_BAND, fresh.player.coords, "a player who never did Regicide doesn't know the way")
        }
    }

    private fun modify(modifier: StandardPlayerHitModifier, target: Player, type: HitType, damage: Int, penetration: Int): Int {
        val builder = HitBuilder(type, damage, null, null, true, false, 0, null, null, 0, 0, null, null, null, null, 0, 0)
        builder.penetration = penetration
        with(modifier) { builder.modify(target) }
        return builder.damage
    }

    private class Fixture(stage: Int = 0) {
        val events = EventBus()
        private val client = RecordingClient()
        private val collision = CollisionFlagMap()
        private val npcs = NpcList()
        private val players = PlayerList()
        private val coroutine = GameCoroutine("roving-test")
        private var result: Result<Unit>? = null
        private val validator = PlayerTeleportValidator(setOf(PlayerTeleportValidateHook { _, _, _ -> null }))
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
        private val clock = MapClock(100)
        private val objRepo = ObjRepository(clock, ObjRegistry(ZoneUpdateMap()))
        private val npcRepo: NpcRepository
        val locReg: LocRegistry
        private val picks = ArrayDeque<Int>()
        private val locU = LocUInteractions::class.java.getDeclaredConstructor(EventBus::class.java)
            .apply { isAccessible = true }.newInstance(events)

        val player = newPlayer(6161L, 1)
        val second = newPlayer(6262L, 2)
        private var active = player

        val roving = RovingElvesQuest()
        val regicide = RegicideQuest()
        val waterfall = WaterfallQuest()
        val consecration: Consecration

        init {
            for ((x0, z0, x1, z1) in AREAS) {
                for (level in 0..1) for (x in x0..x1 step 8) for (z in z0..z1 step 8) collision.allocateIfAbsent(x, z, level)
            }
            players[player.slotId] = player
            players[second.slotId] = second
            val updates = ZoneUpdateMap()
            val storage = LocZoneStorage()
            val normal = LocRegistryNormal(updates, collision, storage)
            val npcRegistry = NpcRegistry(npcs, collision, events)
            val regions = RegionRegistry(RegionListSmall(), RegionListLarge(), RegionListWorldEntity(),
                normal, collision, storage, npcRegistry,
                ControllerRegistry(clock, ControllerList()), ZonePlayerActivityBitSet())
            locReg = LocRegistry(storage, normal, LocRegistryRegion(updates, collision, storage, regions))
            npcRepo = NpcRepository(clock, npcRegistry, npcs)
            val singing = CrystalSinging()
            consecration = Consecration(roving, LocRepository(clock, locReg, regions), WorldRepository(updates), collision)
            val scripts = ScriptContext(events, CheatCommandMap(), EngineQueueCache())
            for (script in listOf(
                roving, regicide, waterfall, consecration,
                Islwyn(roving, singing), Eluned(roving, ElunedErrands(MourningsEndQuest(), ArianwynBriefing(MourningsEndQuest()))), Ilfeen(roving, singing),
                GlarialsTomb(waterfall, unused<LocRepository>(), objRepo),
                BaxtorianFalls(waterfall, unused<NpcSearch>()),
                WaterfallDungeon(waterfall, objRepo, unused<WorldRepository>(), unused<DoorPassage>()),
                Golrie(waterfall, objRepo),
                IsafdarObstacles(regicide, unused<TyrasGuardEncounter>()),
            )) {
                with(script) { scripts.startup() }
            }
            for (p in listOf(player, second)) {
                if (stage > 0) roving.quest.jumpToStage(p, stage)
            }
        }

        @OptIn(InternalApi::class)
        private fun newPlayer(id: Long, slot: Int) = Player().apply {
            this.client = this@Fixture.client
            uuid = id
            observerUUID = id
            slotId = slot
            assignUid()
            coords = CoordGrid(2290, 3148, 0)
            currentMapClock = 100
            processedMapClock = 100
            pendingSequence = EntitySeq.NULL
            pendingFaceAngle = EntityFaceAngle.NULL
            inv = Inventory(InventoryServerType(size = 28, flags = 0), arrayOfNulls(28))
            worn = Inventory(InventoryServerType(size = 14, flags = 0), arrayOfNulls(14))
            for ((stat, level) in listOf("stat.hitpoints" to 60, "stat.agility" to 60, "stat.strength" to 40)) {
                statMap.setBaseLevel(stat, level.toByte())
                statMap.setCurrentLevel(stat, level.toByte())
            }
        }

        fun access(p: Player = active) = ProtectedAccess(p, coroutine, context)

        fun stage(): Int = roving.stage(player)

        fun jump(stage: Int) = roving.quest.jumpToStage(player, stage)

        fun completeWaterfall() = waterfall.quest.jumpToStage(player, waterfall.quest.maxSteps)

        fun journal(): String = roving.questLog(access())

        fun growthAt(tile: CoordGrid): Boolean = locReg.findType(tile, Consecration.GROWTH_LOC.asRSCM()) != null

        fun seedDrops(): Boolean = seedDropsFor(player)

        fun seedDropsFor(p: Player): Boolean =
            QuestRequirements.isOnQuest(p, RovingElvesQuest.QUEST_KEY) && QuestItemDrops.isNeeded(p, OLD_SEED)

        fun charges(obj: String): Int = ObjChargeManager().getCharges(player.inv.first { it?.id == obj.asRSCM() }, CRYSTAL_CHARGES)

        fun choose(vararg options: Int) {
            picks += options.toList()
        }

        fun setAgility(base: Int, current: Int) {
            player.statMap.setBaseLevel("stat.agility", base.toByte())
            player.statMap.setCurrentLevel("stat.agility", current.toByte())
        }

        fun give(obj: String, count: Int = 1, p: Player = player) {
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
            drop(obj)
            player.worn[type.wearpos1] = InvObj(obj, 1)
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

        fun drop(obj: String) {
            val slot = player.inv.indexOfFirst { it?.id == obj.asRSCM() }
            if (slot >= 0) player.inv[slot] = null
        }

        fun count(obj: String): Int = player.inv.count(obj)

        fun stack(obj: String): Int = player.inv.filter { it?.id == obj.asRSCM() }.sumOf { it?.count ?: 0 }

        fun said(text: String): Boolean = output().contains(text)

        fun talk(type: String, p: Player = player) = npcOp(type, p) { NpcEvents.Op1(it) }

        fun op3(type: String) = npcOp(type, player) { NpcEvents.Op3(it) }

        private fun npcOp(type: String, p: Player, event: (Npc) -> SuspendEvent<ProtectedAccess>) {
            val npc = Npc(type, p.coords.translateX(1))
            npcRepo.add(npc, Int.MAX_VALUE)
            active = p
            try {
                publish(event(npc))
            } finally {
                active = player
                if (npc.isSlotAssigned) npcRepo.del(npc, Int.MAX_VALUE)
            }
        }

        fun held1(obj: String) {
            dispatch { assertTrue(events.publish(this, heldOp1(obj))) }
        }

        fun heldOpHandled(obj: String): Boolean {
            var handled = true
            dispatch { handled = events.publish(this, heldOp1(obj)) }
            return handled
        }

        private fun heldOp1(obj: String): HeldObjEvents.Op1 {
            val slot = player.inv.indexOfFirst { it?.id == obj.asRSCM() }
            val type = checkNotNull(ServerCacheManager.getItem(obj.asRSCM()))
            return HeldObjEvents.Op1(slot, checkNotNull(player.inv[slot]), type, player.inv)
        }

        fun forest(at: CoordGrid) = locOp("loc.regicide_cross_over1", at, angle = 3)

        fun locOp(symbol: String, coords: CoordGrid, op: InteractionOp = InteractionOp.Op1, angle: Int = 0) {
            val loc = bound(symbol, coords, angle)
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

        fun saveAndReload(): Player {
            val loaded = newPlayer(player.uuid ?: 0L, 3)
            for ((varp, value) in player.vars.backing) {
                val scope = ServerCacheManager.getVarp(varp)?.scope
                if (scope != VarpLifetime.Temp) loaded.vars.backing[varp] = value
            }
            player.attr[QUEST_STAGE_MAP_ATTR]?.let { loaded.attr[QUEST_STAGE_MAP_ATTR] = it.toMutableMap() }
            roving.quest.syncState(loaded)
            return loaded
        }

        private fun bound(symbol: String, coords: CoordGrid, angle: Int = 0): BoundLocInfo {
            val type = checkNotNull(ServerCacheManager.getObject(symbol.asRSCM()))
            return BoundLocInfo(LocInfo(0, coords, LocEntity(type.id, 10, angle)), type)
        }

        private fun publish(event: SuspendEvent<ProtectedAccess>) {
            dispatch { assertTrue(events.publish(this, event)) }
        }

        fun dispatch(block: suspend ProtectedAccess.() -> Unit) {
            val p = active
            p.clearPendingAction(events)
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
        const val PEBBLE = WaterfallQuest.PEBBLE
        const val AMULET = WaterfallQuest.AMULET
        const val BAXTORIAN_KEY = WaterfallQuest.BAXTORIAN_KEY
        const val GOLRIE = WaterfallQuest.GOLRIE
        const val TOMBSTONE = "loc.glarials_tombstone_waterfall_quest"
        const val CHEST = "loc.glarials_chest_open_waterfall_quest"
        const val KEY_CRATE = "loc.baxtorian_crate_waterfall_quest"
        val TOMBSTONE_TILE = CoordGrid(2558, 3444, 0)
        val BAND_EAST = CoordGrid(2238, 3148, 0)
        val EAST_OF_BAND = CoordGrid(2240, 3149, 0)

        val AREAS =
            listOf(
                intArrayOf(2140, 3040, 2400, 3340),
                intArrayOf(2490, 3400, 2620, 3500),
                intArrayOf(2496, 9792, 2623, 9920),
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
