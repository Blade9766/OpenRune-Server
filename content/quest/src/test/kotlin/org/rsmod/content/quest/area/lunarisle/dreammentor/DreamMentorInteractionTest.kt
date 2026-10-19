@file:OptIn(InternalApi::class)

package org.rsmod.content.quest.area.lunarisle.dreammentor

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.InventoryServerType
import dev.openrune.types.varp.VarpLifetime
import io.mockk.every
import io.mockk.mockk
import jakarta.inject.Provider
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
import org.rsmod.api.config.refs.BaseParams
import org.rsmod.api.death.NpcAttackValidateResult
import org.rsmod.api.death.PlayerDeathContext
import org.rsmod.api.death.PlayerDeathHandling
import org.rsmod.api.death.UntradeableHandling
import org.rsmod.api.instances.InstanceManager
import org.rsmod.api.inv.storage.PlayerItemStorage
import org.rsmod.api.invtx.InvTransactionsScript
import org.rsmod.api.npc.owner.assignSpawnOwner
import org.rsmod.api.player.dialogue.align.TextAlignment
import org.rsmod.api.player.events.interact.HeldObjEvents
import org.rsmod.api.player.events.interact.NpcEvents
import org.rsmod.api.player.events.interact.NpcUDefaultEvents
import org.rsmod.api.player.hit.modifier.NoopPlayerHitModifier
import org.rsmod.api.player.hit.processor.DamageOnlyPlayerHitProcessor
import org.rsmod.api.player.hook.PlayerTeleportValidator
import org.rsmod.api.player.hook.RestrictedAction
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.input.ResumePauseButtonInput
import org.rsmod.api.player.interact.HeldUInteractions
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
import org.rsmod.content.quest.area.burthorpe.eadgarsruse.EadgarsRuseQuest
import org.rsmod.content.quest.area.burthorpe.heroesquest.questPoints
import org.rsmod.content.quest.area.lunarisle.dreammentor.DreamMentorQuest.Companion.DREAMY_LAMP
import org.rsmod.content.quest.area.lunarisle.dreammentor.DreamMentorQuest.Companion.SEAL_OF_PASSAGE
import org.rsmod.content.quest.area.lunarisle.dreammentor.DreamMentorQuest.Companion.STAGE_ARMED
import org.rsmod.content.quest.area.lunarisle.dreammentor.DreamMentorQuest.Companion.STAGE_AWAKE
import org.rsmod.content.quest.area.lunarisle.dreammentor.DreamMentorQuest.Companion.STAGE_COMPLETE
import org.rsmod.content.quest.area.lunarisle.dreammentor.DreamMentorQuest.Companion.STAGE_CONQUERED
import org.rsmod.content.quest.area.lunarisle.dreammentor.DreamMentorQuest.Companion.STAGE_GOT_CHEST
import org.rsmod.content.quest.area.lunarisle.dreammentor.DreamMentorQuest.Companion.STAGE_NEEDS_ARMOUR
import org.rsmod.content.quest.area.lunarisle.dreammentor.DreamMentorQuest.Companion.STAGE_POTION
import org.rsmod.content.quest.area.lunarisle.dreammentor.DreamMentorQuest.Companion.STAGE_RESTORED
import org.rsmod.content.quest.area.lunarisle.dreammentor.DreamMentorQuest.Companion.STAGE_STANDING
import org.rsmod.content.quest.area.lunarisle.dreammentor.DreamMentorQuest.Companion.STAGE_STARTED
import org.rsmod.content.quest.area.lunarisle.lunardiplomacy.LunarDiplomacyQuest
import org.rsmod.content.quest.area.lunarisle.lunardiplomacy.LunarTravel
import org.rsmod.content.quest.area.lunarisle.lunardiplomacy.dream.DreamWorldScript
import org.rsmod.content.quest.area.lunarisle.lunardiplomacy.dream.MeFightScript
import org.rsmod.content.quest.area.lunarisle.lunardiplomacy.npcs.Oneiromancer
import org.rsmod.content.quest.manager.PolicySpellQuestRequirement
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
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.InvVirtualStorageHolder
import org.rsmod.game.inv.Inventory
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.game.seq.EntitySeq
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap
import sun.misc.Unsafe

/**
 * Drives Dream Mentor's real scripts through the event bus: the start and its requirements,
 * nursing Cyrisus from unconscious to standing with food and encouragement, everything he refuses,
 * 'Birds-Eye' Jack and the armour chest, the dream potion, the Oneiromancer's part, completion and
 * the dreamy lamp, logging out and back in, the dream's restrictions, the order and reset of the
 * fight, and the spells and banking that completion unlocks.
 */
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class DreamMentorInteractionTest {

    @Test fun `the quest needs 85 combat, Lunar Diplomacy and Eadgar's Ruse`() {
        respectingProgress {
            val f = Fixture()
            f.lunar.quest.jumpToStage(f.player, 0)
            f.combat(90)
            f.choose(1)
            f.talkToCyrisus()
            assertEquals(0, f.stage())
            assertTrue(f.said("Lunar Diplomacy") && f.said("Eadgar's Ruse"))
            f.lunar.quest.jumpToStage(f.player, f.lunar.quest.maxSteps)
            f.completeEadgarsRuse()
            f.combat(84)
            f.choose(1)
            f.talkToCyrisus()
            assertEquals(0, f.stage(), "84 combat is not enough")
            assertTrue(f.said("a combat level of 85"))
            f.combat(85)
            f.choose(2)
            f.talkToCyrisus()
            assertEquals(0, f.stage(), "declined")
            f.choose(1)
            f.talkToCyrisus()
            assertEquals(STAGE_STARTED, f.stage())
            assertEquals(CombatStyle.Melee.value, f.player.dreamCombatType)
            assertTrue(f.journal().contains("three different kinds"), f.journal())
        }
    }

    @Test fun `food and encouragement take him from unconscious to standing`() {
        val f = Fixture(STAGE_STARTED)
        val foods = listOf(SHARK, LOBSTER, BREAD)
        var meal = 0
        repeat(40) {
            val stage = f.stage()
            if (stage >= STAGE_STANDING) return@repeat
            if (CyrisusRecovery.wantsFood(f.player, stage)) {
                f.give(foods[meal++ % foods.size])
                f.feed(foods[(meal - 1) % foods.size])
            } else {
                f.encourage()
            }
        }
        assertEquals(STAGE_NEEDS_ARMOUR, f.stage(), "standing up leads straight into the armour request")
        assertEquals(14, meal)
        assertEquals(0, f.count(SHARK) + f.count(LOBSTER) + f.count(BREAD), "every meal was eaten")
        assertTrue(f.cave.cyrisusOf(f.player)!!.isType(CyrisusCave.STANDING), "the cave shows him standing")
        assertTrue(f.said("I'm Cyrisus"))
        assertTrue(f.journal().contains("Lunar Isle bank"))
    }

    @Test fun `nothing he refuses is used up or counts`() {
        val f = Fixture(STAGE_STARTED)
        f.give(COINS, 50)
        f.feed(COINS)
        assertEquals(50, f.count(COINS))
        assertTrue(f.said("You can only give him something to eat."))
        f.give(SHARK, 2)
        f.feed(SHARK)
        f.feed(SHARK)
        assertEquals(1, f.count(SHARK), "the second shark was too soon after the first")
        assertEquals(5, f.player.dreamHealth)
        f.give(CyrisusRecovery.SWEETS)
        f.feed(CyrisusRecovery.SWEETS)
        assertEquals(1, f.count(CyrisusRecovery.SWEETS))
        f.player.dreamHealth = 20
        f.jump(STAGE_AWAKE)
        f.give(BREAD)
        f.feed(BREAD)
        assertEquals(1, f.count(BREAD), "not hungry while his spirit needs lifting")
        assertEquals(20, f.player.dreamHealth)
    }

    @Test fun `another player's cyrisus ignores you`() {
        val f = Fixture(STAGE_STARTED)
        val other = f.secondPlayer()
        f.give(SHARK)
        f.feedNpc(f.cave.cyrisusOf(other)!!, SHARK)
        assertEquals(1, f.count(SHARK))
        assertEquals(0, f.player.dreamHealth)
        assertTrue(f.said("He doesn't seem to notice you."))
    }

    @Test fun `jack hands over one chest and needs space for it`() {
        val f = Fixture(STAGE_NEEDS_ARMOUR)
        f.fillInventory()
        f.choose(2)
        f.talk(BirdsEyeJack.JACK)
        assertEquals(STAGE_NEEDS_ARMOUR, f.stage())
        assertEquals(0, f.count(CyrisusBank.CHEST))
        f.clearInventory()
        f.choose(2)
        f.talk(BirdsEyeJack.JACK)
        assertEquals(STAGE_GOT_CHEST, f.stage())
        assertEquals(1, f.count(CyrisusBank.CHEST))
        f.choose(2)
        f.talk(BirdsEyeJack.JACK)
        assertEquals(1, f.count(CyrisusBank.CHEST), "talking again with the chest only reopens the bank")
        f.drop(CyrisusBank.CHEST)
        f.bank()[0] = InvObj(CyrisusBank.CHEST, 1)
        f.choose(2)
        f.talk(BirdsEyeJack.JACK)
        assertEquals(0, f.count(CyrisusBank.CHEST), "no spare while one is in the bank")
        f.bank()[0] = null
        f.choose(2)
        f.talk(BirdsEyeJack.JACK)
        assertEquals(1, f.count(CyrisusBank.CHEST), "a lost chest is replaced")
    }

    @Test fun `the wrong armour is handed back and the right set dresses him`() {
        val f = Fixture(STAGE_GOT_CHEST)
        f.player.dreamCombatType = CombatStyle.Melee.value
        f.player.dreamHealth = 70
        f.player.dreamSpirit = 72
        f.give(CyrisusBank.CHEST)
        f.talkToCyrisus()
        assertEquals(STAGE_GOT_CHEST, f.stage())
        assertTrue(f.said("there's nothing in it"))
        for (name in listOf("Ancient staff", "Dragon med helm", "Ahrim's robetop", "Ahrim's robeskirt", "Ranger boots")) {
            CyrisusChest.withdraw(f.player, f.bankSlot(name))
        }
        f.talkToCyrisus()
        assertEquals(STAGE_GOT_CHEST, f.stage())
        assertEquals(80, f.player.dreamArmament)
        assertEquals(1, f.count(CyrisusBank.CHEST))
        assertTrue(f.said("The weapon has to go."))
        CyrisusChest.withdraw(f.player, f.bankSlot("Abyssal whip"))
        f.talkToCyrisus()
        assertEquals(STAGE_ARMED, f.stage())
        assertEquals(100, f.player.dreamArmament)
        assertEquals(0, f.count(CyrisusBank.CHEST), "he keeps the chest")
        assertEquals(0, f.count("obj.abyssal_whip"), "none of his armour reaches the player")
        assertTrue(f.cave.cyrisusOf(f.player)!!.isType(CombatStyle.Melee.cyrisusType))
    }

    @Test fun `full health and spirit after the armour bring out his fear`() {
        val f = Fixture(STAGE_ARMED)
        f.player.dreamArmament = 100
        f.player.dreamHealth = 95
        f.player.dreamSpirit = 100
        f.give(SHARK)
        f.feed(SHARK)
        assertEquals(STAGE_RESTORED, f.stage())
        assertTrue(f.said("I'm afraid of combat."))
        assertEquals(CyrisusSpot.oneiromancer(CombatStyle.of(f.player)), f.player.dreamCyrisusSpot)
    }

    @Test fun `the dream potion takes each ingredient in turn and wastes nothing`() {
        val f = Fixture(STAGE_POTION)
        f.give(DreamPotion.VIAL_WATER)
        f.give(DreamPotion.ASTRAL_RUNE, 3)
        f.give("obj.hammer")
        f.give(DreamPotion.PESTLE_AND_MORTAR)
        f.use(DreamPotion.GROUND_ASTRAL, DreamPotion.VIAL_WATER, expectHandler = false)
        f.give(DreamPotion.GOUTWEED)
        f.use(DreamPotion.GOUTWEED, DreamPotion.VIAL_WATER)
        assertEquals(1, f.count(DreamPotion.VIAL_WEED))
        assertEquals(0, f.count(DreamPotion.GOUTWEED))
        f.use("obj.hammer", DreamPotion.ASTRAL_RUNE)
        assertEquals(2, f.count(DreamPotion.ASTRAL_RUNE), "one rune broken")
        f.use(DreamPotion.PESTLE_AND_MORTAR, DreamPotion.ASTRAL_SHARDS)
        assertEquals(1, f.count(DreamPotion.GROUND_ASTRAL))
        f.use(DreamPotion.GROUND_ASTRAL, DreamPotion.VIAL_WEED)
        assertEquals(1, f.count(DreamPotion.VIAL_FULL))
        assertEquals(0, f.count(DreamPotion.GROUND_ASTRAL) + f.count(DreamPotion.VIAL_WEED))
    }

    @Test fun `the oneiromancer gives the vial once there is room and spares a lost one`() {
        val f = Fixture(STAGE_RESTORED)
        f.player.dreamCyrisusSpot = CyrisusSpot.oneiromancer(CombatStyle.Melee)
        f.fillInventory()
        f.choose(2)
        f.talk(ONEIROMANCER)
        assertEquals(STAGE_RESTORED, f.stage())
        f.clearInventory()
        f.choose(2)
        f.talk(ONEIROMANCER)
        assertEquals(STAGE_POTION, f.stage())
        assertEquals(1, f.count(DreamPotion.VIAL_EMPTY))
        assertEquals(CyrisusSpot.brazier(CombatStyle.Melee), f.player.dreamCyrisusSpot)
        f.choose(2)
        f.talk(ONEIROMANCER)
        assertEquals(1, f.count(DreamPotion.VIAL_EMPTY), "no spare while the player has one")
        f.drop(DreamPotion.VIAL_EMPTY)
        f.choose(2)
        f.talk(ONEIROMANCER)
        assertEquals(1, f.count(DreamPotion.VIAL_EMPTY), "a lost vial is replaced")
    }

    @Test fun `completion pays out once and the lamp grants one wish`() {
        val f = Fixture(STAGE_CONQUERED)
        f.fillInventory()
        f.choose(2)
        f.talk(ONEIROMANCER)
        assertEquals(STAGE_CONQUERED, f.stage(), "needs room for the lamp")
        f.clearInventory()
        val points = f.player.questPoints
        val hitpointsXp = f.xp("stat.hitpoints")
        f.choose(2)
        f.talk(ONEIROMANCER)
        assertEquals(STAGE_COMPLETE, f.stage())
        assertEquals(points + 2, f.player.questPoints)
        assertTrue(f.xp("stat.hitpoints") > hitpointsXp)
        assertEquals(1, f.count(DREAMY_LAMP))
        assertEquals(CyrisusSpot.CAVE, f.player.dreamCyrisusSpot)
        f.choose(2)
        f.talk(ONEIROMANCER)
        assertEquals(points + 2, f.player.questPoints, "no second reward")
        assertEquals(1, f.count(DREAMY_LAMP), "no spare lamp while one is held")

        val strengthXp = f.xp("stat.strength")
        f.lampPick = "stat.strength"
        f.heldOp1(DREAMY_LAMP)
        assertEquals(0, f.count(DREAMY_LAMP))
        assertTrue(f.player.dreamLampUsed)
        assertTrue(f.xp("stat.strength") > strengthXp)
        val afterWish = f.xp("stat.strength")
        f.give(DREAMY_LAMP)
        f.heldOp1(DREAMY_LAMP)
        assertEquals(afterWish, f.xp("stat.strength"), "a used wish can't be granted again")
        assertEquals(0, f.count(DREAMY_LAMP))
        f.choose(2)
        f.talk(ONEIROMANCER)
        assertEquals(0, f.count(DREAMY_LAMP), "no replacement once the wish is used")
    }

    @Test fun `the lamp refuses attack and prayer`() {
        val f = Fixture(STAGE_COMPLETE)
        f.give(DREAMY_LAMP)
        val attackXp = f.xp("stat.attack")
        f.lampPick = "stat.attack"
        f.heldOp1(DREAMY_LAMP)
        assertEquals(attackXp, f.xp("stat.attack"))
        assertEquals(1, f.count(DREAMY_LAMP), "the lamp is kept when the pick is refused")
        assertFalse(f.player.dreamLampUsed)
    }

    @Test fun `progress survives logging out but his last meals do not`() {
        val f = Fixture(STAGE_GOT_CHEST)
        f.player.dreamHealth = 70
        f.player.dreamSpirit = 72
        f.player.dreamCombatType = CombatStyle.Ranged.value
        f.player.dreamRecentFoodA = 123
        CyrisusChest.withdraw(f.player, f.bankSlot("Magic shortbow"))
        val loaded = f.saveAndReload()
        assertEquals(STAGE_GOT_CHEST, f.dreamMentor.stage(loaded))
        assertEquals(70, loaded.dreamHealth)
        assertEquals(72, loaded.dreamSpirit)
        assertEquals(CombatStyle.Ranged, CombatStyle.of(loaded))
        assertEquals(f.bankSlot("Magic shortbow"), CyrisusChest.picked(loaded, 0))
        assertEquals(0, loaded.dreamRecentFoodA, "what he last ate is forgotten")
        f.dreamMentor.quest.jumpToStage(loaded, 0)
        assertEquals(0, loaded.dreamHealth + loaded.dreamSpirit + CyrisusChest.picked(loaded, 0))
    }

    @Test fun `the dream blocks prayer and teleports, keeps fears private and takes the seal`() {
        val f = Fixture(STAGE_POTION)
        val arena = mockk<DreamArena>(relaxed = true)
        every { arena.isDreaming(any()) } returns true
        val hooks = DreamMentorHooks(Provider { arena }, Provider { mockk(relaxed = true) })
        assertNotNull(hooks.restriction(f.player, RestrictedAction.Prayer))
        assertNull(hooks.restriction(f.player, RestrictedAction.Food))
        val areas = mockk<AreaChecker>(relaxed = true)
        assertNotNull(hooks.validate(f.player, TeleportType.Standard, areas))
        assertNull(hooks.validate(f.player, TeleportType.Exempt, areas), "the lectern's own wake-up is exempt")
        val other = f.secondPlayer()
        val boss = Npc(Fear.Inadequacy.type, CoordGrid(1822, 5147, 2)).apply { assignSpawnOwner(other, 0) }
        assertTrue(hooks.validate(f.player, boss) is NpcAttackValidateResult.Deny)
        assertEquals(NpcAttackValidateResult.Pass, hooks.validate(other, boss))
        f.give(SEAL_OF_PASSAGE)
        f.player.worn[0] = InvObj(SEAL_OF_PASSAGE, 1)
        hooks.beforeDrops(f.deathContext(), DEATH_HANDLING)
        assertEquals(0, f.count(SEAL_OF_PASSAGE))
        assertNull(f.player.worn[0], "even a worn seal is lost")
        every { arena.isDreaming(any()) } returns false
        assertNull(hooks.restriction(f.player, RestrictedAction.Prayer), "prayer works again once awake")
        assertNull(hooks.validate(f.player, TeleportType.Standard, areas))
    }

    @Test fun `the fears come in order, ignore other kills and start over after a reset`() {
        val f = Fixture(STAGE_POTION)
        val fight = f.fight()
        val encounter = fight.start(f.player)
        assertEquals(Fear.Inadequacy, encounter.fear)
        val stranger = Npc(Fear.Inadequacy.type, CoordGrid(1820, 5150, 2))
        assertNull(fight.onFearDeath(f.player, stranger), "a fear that isn't this encounter's boss changes nothing")
        assertEquals(Fear.Inadequacy, encounter.fear)
        for (expected in listOf(Fear.Everlasting, Fear.Untouchable, Fear.Illusive)) {
            val boss = encounter.boss!!
            assertEquals(expected, fight.onFearDeath(f.player, boss))
            assertTrue(encounter.boss!!.isType(expected.type))
            assertEquals(boss.coords, encounter.boss!!.coords, "each fear rises where the last fell")
        }
        assertNull(fight.onFearDeath(f.player, encounter.boss!!), "nothing after The Illusive")
        fight.onHit(f.player, encounter.boss!!, 30)
        assertEquals(30, f.player.dreamDamageDealt)
        assertEquals(1, fight.courage(f.player))
        fight.cleanup(f.player)
        assertNull(fight.encounterOf(f.player))
        assertFalse(encounter.boss!!.isSlotAssigned, "cleanup removes the fear")
        assertFalse(encounter.cyrisus!!.isSlotAssigned, "and Cyrisus")
        val retry = fight.start(f.player)
        assertEquals(Fear.Inadequacy, retry.fear, "a new attempt starts from the beginning")
        assertEquals(0, f.player.dreamDamageDealt)
        assertEquals(STAGE_POTION, f.stage(), "beating fears in a reset encounter didn't advance the quest")
        fight.cleanup(f.player)
    }

    @Test fun `the illusive burrows after every two hits`() {
        val f = Fixture(STAGE_POTION)
        val fight = f.fight()
        val encounter = fight.start(f.player)
        repeat(3) { fight.onFearDeath(f.player, encounter.boss!!) }
        val illusive = encounter.boss!!
        val before = illusive.coords
        fight.onHit(f.player, illusive, 5)
        assertEquals(before, illusive.coords)
        fight.onHit(f.player, illusive, 0)
        assertEquals(before, illusive.coords, "a miss doesn't count")
        fight.onHit(f.player, illusive, 5)
        assertTrue(illusive.coords != before, "it surfaced elsewhere")
        fight.cleanup(f.player)
    }

    @Test fun `jack banks only for seal holders until the quest is complete`() {
        respectingProgress {
            val f = Fixture(STAGE_NEEDS_ARMOUR)
            val jack = BirdsEyeJack(f.dreamMentor, CyrisusBank(f.dreamMentor))
            assertFalse(jack.mayBank(f.player))
            f.give(SEAL_OF_PASSAGE)
            assertTrue(jack.mayBank(f.player))
            f.drop(SEAL_OF_PASSAGE)
            f.jump(STAGE_COMPLETE)
            assertTrue(jack.mayBank(f.player), "no seal needed once Dream Mentor is done")
        }
    }

    @Test fun `the seven spells are locked behind the quest`() {
        respectingProgress {
            val f = Fixture()
            val requirement = PolicySpellQuestRequirement()
            for (spell in SPELLS) {
                val type = checkNotNull(ServerCacheManager.getItem(spell.asRSCM(RSCMType.OBJ)))
                val row = checkNotNull(type.paramOrNull(BaseParams.spell_questreq)) { "$spell has no quest requirement" }
                assertEquals("dbrow.${DreamMentorQuest.QUEST_KEY}".asRSCM(RSCMType.DBROW), row.id, spell)
            }
            assertFalse(requirement.hasCompleted(f.player, DreamMentorQuest.QUEST_KEY))
            f.jump(STAGE_CONQUERED)
            assertFalse(requirement.hasCompleted(f.player, DreamMentorQuest.QUEST_KEY), "not until it's finished")
            f.jump(STAGE_COMPLETE)
            assertTrue(requirement.hasCompleted(f.player, DreamMentorQuest.QUEST_KEY))
        }
    }

    @Test fun `every dream mentor script starts beside lunar diplomacy's dream scripts`() {
        val events = EventBus()
        val scripts = ScriptContext(events, CheatCommandMap(), EngineQueueCache())
        val dreamMentor = DreamMentorQuest()
        val town = DreamTown(dreamMentor, mockk(relaxed = true), mockk(relaxed = true))
        val bank = CyrisusBank(dreamMentor)
        for (script in listOf(
            dreamMentor, bank, CyrisusStatus(dreamMentor),
            CyrisusScript(dreamMentor, mockk(relaxed = true), CyrisusStatus(dreamMentor), town),
            BirdsEyeJack(dreamMentor, bank), DreamPotion(town), DreamyLamp(),
            DreamFightScript(
                mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true),
                mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true),
                mockk(relaxed = true), mockk(relaxed = true),
            ),
            DreamWorldScript(LunarDiplomacyQuest(), mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true)),
            MeFightScript(LunarDiplomacyQuest(), mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true)),
        )) {
            with(script) { scripts.startup() }
        }
    }

    private class Fixture(stage: Int = 0) {
        val events = EventBus()
        private val client = RecordingClient()
        private val collision = CollisionFlagMap()
        private val npcs = NpcList()
        private val players = PlayerList()
        private val coroutine = GameCoroutine("dream-mentor-test")
        private var result: Result<Unit>? = null
        private val validator = PlayerTeleportValidator(emptySet())
        private val rng = DefaultGameRandom(Random(7))
        private val context = ProtectedAccessContextFactory.empty().copy(
            getEventBus = { events }, getAlignment = { TextAlignment() },
            getCollision = { collision }, getNpcList = { npcs },
            getTeleportValidator = { validator },
            getAreaChecker = { unused<AreaChecker>() },
            getNpcInteractions = { NpcInteractions(events) },
            getRandom = { rng },
            getHitModifier = { NoopPlayerHitModifier },
            getInstantHitProcessor = { DamageOnlyPlayerHitProcessor(events, npcs, players) },
        )
        val clock = MapClock(100)
        private val objRepo = ObjRepository(clock, ObjRegistry(ZoneUpdateMap()))
        val npcRepo: NpcRepository
        private val picks = ArrayDeque<Int>()
        private val heldU = HeldUInteractions(events)
        var lampPick: String? = null

        val player = newPlayer(8181L, 1)

        val dreamMentor = DreamMentorQuest()
        val lunar = LunarDiplomacyQuest()
        val eadgarsRuse = EadgarsRuseQuest()
        val cave: CyrisusCave

        init {
            for ((x0, z0, x1, z1, level) in AREAS) {
                for (x in x0..x1 step 8) for (z in z0..z1 step 8) collision.allocateIfAbsent(x, z, level)
            }
            players[player.slotId] = player
            npcRepo = NpcRepository(clock, NpcRegistry(npcs, collision, events), npcs)
            cave = CyrisusCave(dreamMentor, npcRepo, clock)
            val town = DreamTown(dreamMentor, mockk(relaxed = true), mockk(relaxed = true))
            val bank = CyrisusBank(dreamMentor)
            val scripts = ScriptContext(events, CheatCommandMap(), EngineQueueCache())
            for (script in listOf(
                dreamMentor, lunar, bank, CyrisusStatus(dreamMentor),
                CyrisusScript(dreamMentor, cave, CyrisusStatus(dreamMentor), town),
                BirdsEyeJack(dreamMentor, bank), DreamPotion(town), DreamyLamp(),
                Oneiromancer(lunar, LunarTravel(), objRepo, DreamMentorOneiromancer(dreamMentor)),
            )) {
                with(script) { scripts.startup() }
            }
            lunar.quest.jumpToStage(player, lunar.quest.maxSteps)
            player.coords = CyrisusCave.CAVE_SIDE
            if (stage > 0) dreamMentor.quest.jumpToStage(player, stage)
            cave.refresh(player)
        }

        private fun newPlayer(id: Long, slot: Int) = Player().apply {
            this.client = this@Fixture.client
            uuid = id
            observerUUID = id
            slotId = slot
            assignUid()
            coords = CyrisusCave.CAVE_SIDE
            currentMapClock = 100
            processedMapClock = 100
            pendingSequence = EntitySeq.NULL
            pendingFaceAngle = EntityFaceAngle.NULL
            inv = Inventory(InventoryServerType(size = 28, flags = 0), arrayOfNulls(28))
            worn = Inventory(InventoryServerType(size = 14, flags = 0), arrayOfNulls(14))
            val bankType = checkNotNull(ServerCacheManager.getInventory("inv.bank".asRSCM(RSCMType.INV)))
            invMap["inv.bank"] = Inventory(bankType, arrayOfNulls(bankType.size))
            for ((stat, level) in listOf("stat.hitpoints" to 90, "stat.attack" to 90, "stat.strength" to 90, "stat.defence" to 90)) {
                statMap.setBaseLevel(stat, level.toByte())
                statMap.setCurrentLevel(stat, level.toByte())
            }
            appearance.combatLevel = 100
        }

        fun secondPlayer(): Player {
            val other = newPlayer(9191L, 2)
            players[other.slotId] = other
            dreamMentor.quest.jumpToStage(other, STAGE_STARTED)
            cave.refresh(other)
            return other
        }

        fun fight(): DreamFight {
            val arena = DreamArena(unused<InstanceManager>())
            return DreamFight(dreamMentor, arena, npcRepo, clock, mockk(relaxed = true), rng, collision, mockk(relaxed = true), events)
        }

        fun access(p: Player = player) = ProtectedAccess(p, coroutine, context)

        fun stage(): Int = dreamMentor.stage(player)

        fun jump(stage: Int) = dreamMentor.quest.jumpToStage(player, stage)

        fun journal(): String = dreamMentor.questLog(access())

        fun combat(level: Int) {
            player.appearance.combatLevel = level
        }

        fun completeEadgarsRuse() = eadgarsRuse.quest.jumpToStage(player, eadgarsRuse.quest.maxSteps)

        fun choose(vararg options: Int) {
            picks += options.toList()
        }

        fun xp(stat: String): Int = player.statMap.getXP(stat)

        fun give(obj: String, count: Int = 1) {
            val type = checkNotNull(ServerCacheManager.getItem(obj.asRSCM(RSCMType.OBJ)))
            if (type.stackable) {
                player.inv[player.inv.indexOfFirst { it == null }] = InvObj(obj, count)
            } else {
                repeat(count) { player.inv[player.inv.indexOfFirst { it == null }] = InvObj(obj, 1) }
            }
        }

        fun drop(obj: String) {
            for (slot in player.inv.objs.indices) {
                if (player.inv[slot]?.id == obj.asRSCM(RSCMType.OBJ)) player.inv[slot] = null
            }
        }

        fun fillInventory() {
            for (slot in player.inv.objs.indices) {
                if (player.inv[slot] == null) player.inv[slot] = InvObj("obj.bronze_sword", 1)
            }
        }

        fun clearInventory() = drop("obj.bronze_sword")

        fun count(obj: String): Int = player.inv.count(obj)

        fun said(text: String): Boolean = output().contains(text)

        fun bankSlot(name: String): Int = (0 until CyrisusChest.BANK_SIZE).first { CyrisusChest.bankObj(it)?.name == name }

        fun bank(): Inventory = checkNotNull(player.invMap["inv.bank"])

        fun talkToCyrisus() {
            val npc = checkNotNull(cave.cyrisusOf(player)) { "no Cyrisus in the cave at stage ${stage()}" }
            publish(NpcEvents.Op1(npc))
        }

        fun encourage() {
            player.dreamSpiritQuestion = 0
            choose(1, 1)
            talkToCyrisus()
        }

        fun feed(obj: String) = feedNpc(checkNotNull(cave.cyrisusOf(player)), obj)

        fun feedNpc(npc: Npc, obj: String) {
            val slot = player.inv.indexOfFirst { it?.id == obj.asRSCM(RSCMType.OBJ) }
            check(slot >= 0) { "no $obj to feed" }
            val type = checkNotNull(ServerCacheManager.getItem(obj.asRSCM(RSCMType.OBJ)))
            publish(NpcUDefaultEvents.OpType(npc, slot, type, npc.type))
        }

        fun talk(type: String, at: CoordGrid = player.coords.translateX(1)) {
            val npc = Npc(type, at)
            npcRepo.add(npc, Int.MAX_VALUE)
            publish(NpcEvents.Op1(npc))
            if (npc.isSlotAssigned) npcRepo.del(npc, Int.MAX_VALUE)
        }

        fun heldOp1(obj: String) {
            val slot = player.inv.indexOfFirst { it?.id == obj.asRSCM(RSCMType.OBJ) }
            val type = checkNotNull(ServerCacheManager.getItem(obj.asRSCM(RSCMType.OBJ)))
            dispatch { assertTrue(events.publish(this, HeldObjEvents.Op1(slot, checkNotNull(inv[slot]), type, inv))) }
        }

        fun use(first: String, second: String, expectHandler: Boolean = true) {
            val a = player.inv.indexOfFirst { it?.id == first.asRSCM(RSCMType.OBJ) }
            val b = player.inv.indexOfLast { it?.id == second.asRSCM(RSCMType.OBJ) }
            if (a < 0 || b < 0) {
                check(!expectHandler) { "missing $first or $second" }
                return
            }
            val typeA = checkNotNull(ServerCacheManager.getItem(first.asRSCM(RSCMType.OBJ)))
            val typeB = checkNotNull(ServerCacheManager.getItem(second.asRSCM(RSCMType.OBJ)))
            dispatch { heldU.interact(this, inv, typeA, a, typeB, b) }
        }

        fun deathContext() =
            PlayerDeathContext(
                player = player, coords = player.coords, inWilderness = false, wildernessLevel = 0,
                inRevenantCaves = false, inInstance = true, isSkulled = false, hasProtectItem = false,
                recentPvpDamage = false, gamemode = 0, killer = null,
            )

        fun saveAndReload(): Player {
            val loaded = newPlayer(player.uuid ?: 0L, 3)
            for ((varp, value) in player.vars.backing) {
                val scope = ServerCacheManager.getVarp(varp)?.scope
                if (scope != VarpLifetime.Temp) loaded.vars.backing[varp] = value
            }
            player.attr[QUEST_STAGE_MAP_ATTR]?.let { loaded.attr[QUEST_STAGE_MAP_ATTR] = it.toMutableMap() }
            dreamMentor.quest.syncState(loaded)
            return loaded
        }

        private fun publish(event: SuspendEvent<ProtectedAccess>) {
            dispatch { assertTrue(events.publish(this, event)) }
        }

        fun dispatch(block: suspend ProtectedAccess.() -> Unit) {
            val p = player
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
                if (p.ui.containsModal("interface.xpreward")) {
                    val index = LAMP_STATS.indexOf(lampPick)
                    coroutine.resumeWith(ResumePauseButtonInput("component.xpreward:universe", index))
                    return
                }
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
        const val SHARK = "obj.shark"
        const val LOBSTER = "obj.lobster"
        const val BREAD = "obj.bread"
        const val COINS = "obj.coins"
        const val ONEIROMANCER = "npc.lunar_oneiromancer"

        val SPELLS =
            listOf(
                "obj.66_monster_examine", "obj.68_humidify", "obj.71_hunter_kit", "obj.75_stat_spy",
                "obj.79_dream", "obj.86_plank_make", "obj.96_spellbook_swap",
            )

        /** The xpreward interface's stat order, from the quest manager's lamp helper. */
        val LAMP_STATS =
            listOf(
                "stat.attack", "stat.strength", "stat.ranged", "stat.magic", "stat.defence", "stat.hitpoints",
                "stat.prayer",
            )

        val DEATH_HANDLING =
            PlayerDeathHandling(
                keepCount = 3, dropReceiver = null, dropDuration = 0, revealDelay = 0,
                supplyPile = false, untradeableHandling = UntradeableHandling.DROP,
            )

        /** x0, z0, x1, z1 and level of every area a test stands the player or an npc in. */
        val AREAS =
            listOf(
                intArrayOf(2328, 10336, 2360, 10376, 2),
                intArrayOf(1800, 5128, 1848, 5176, 2),
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
            ServerCacheManager.init(241).close()
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
