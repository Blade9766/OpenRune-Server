package org.rsmod.content.quest.area.lunarisle.dreammentor

import dev.openrune.ServerCacheManager
import dev.openrune.types.InventoryServerType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.content.quest.area.lunarisle.dreammentor.CyrisusRecovery.Feeding
import org.rsmod.content.quest.area.lunarisle.dreammentor.DreamMentorQuest.Companion.STAGE_ARMED
import org.rsmod.content.quest.area.lunarisle.dreammentor.DreamMentorQuest.Companion.STAGE_AWAKE
import org.rsmod.content.quest.area.lunarisle.dreammentor.DreamMentorQuest.Companion.STAGE_FED_MORE
import org.rsmod.content.quest.area.lunarisle.dreammentor.DreamMentorQuest.Companion.STAGE_FED_WELL
import org.rsmod.content.quest.area.lunarisle.dreammentor.DreamMentorQuest.Companion.STAGE_GOT_CHEST
import org.rsmod.content.quest.area.lunarisle.dreammentor.DreamMentorQuest.Companion.STAGE_SITTING
import org.rsmod.content.quest.area.lunarisle.dreammentor.DreamMentorQuest.Companion.STAGE_SPIRIT_LIFTED
import org.rsmod.content.quest.area.lunarisle.dreammentor.DreamMentorQuest.Companion.STAGE_STANDING
import org.rsmod.content.quest.area.lunarisle.dreammentor.DreamMentorQuest.Companion.STAGE_STARTED
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.Inventory
import org.rsmod.map.CoordGrid

/**
 * The arithmetic and rules behind Dream Mentor, checked against the real cache: how far each
 * meal and kind word carries Cyrisus and when he moves on, which foods he takes, the armour that
 * suits each fighting style, the order of his fears, and where The Inadequacy can reach to melee.
 */
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class DreamMentorLogicTest {

    @Test fun `twenty meals and the spirit checkpoints take him from the floor to standing`() {
        val player = player()
        var stage = STAGE_STARTED
        var meals = 0
        val kinds = listOf(SHARK, LOBSTER, BREAD)
        while (stage < STAGE_STANDING) {
            if (CyrisusRecovery.wantsFood(player, stage)) {
                val meal = CyrisusRecovery.feeding(player, stage, kinds[meals % kinds.size])
                assertTrue(meal is Feeding.Eats, "meal $meals at stage $stage: $meal")
                CyrisusRecovery.eat(player, stage, meal as Feeding.Eats)
                meals++
            } else {
                assertTrue(CyrisusRecovery.wantsCompany(player, stage), "stuck at stage $stage")
                CyrisusRecovery.cheer(player, stage)
            }
            stage = CyrisusRecovery.nextStage(player, stage) ?: stage
        }
        assertEquals(14, meals, "70% health is fourteen meals")
        assertEquals(70, player.dreamHealth)
        assertEquals(72, player.dreamSpirit)
    }

    @Test fun `each bar stops at its marker until the other one catches up`() {
        val player = player()
        player.dreamHealth = 20
        assertFalse(CyrisusRecovery.wantsFood(player, STAGE_AWAKE))
        assertEquals(Feeding.NotHungry, CyrisusRecovery.feeding(player, STAGE_AWAKE, SHARK))
        player.dreamSpirit = 30
        assertEquals(32, CyrisusRecovery.cheer(player, STAGE_AWAKE))
        assertEquals(32, CyrisusRecovery.cheer(player, STAGE_AWAKE), "spirit stays at its marker")
        assertEquals(STAGE_SPIRIT_LIFTED, CyrisusRecovery.nextStage(player, STAGE_AWAKE))
        assertEquals(40, CyrisusRecovery.healthCap(STAGE_SPIRIT_LIFTED))
        assertEquals(CyrisusRecovery.SIT_UP_SPIRIT, CyrisusRecovery.spiritCap(STAGE_FED_MORE))
        assertEquals(STAGE_SITTING, run { player.dreamHealth = 40; player.dreamSpirit = 52; CyrisusRecovery.nextStage(player, STAGE_FED_MORE) })
        assertEquals(70, CyrisusRecovery.healthCap(STAGE_SITTING))
        assertEquals(72, CyrisusRecovery.spiritCap(STAGE_FED_WELL))
        assertEquals(72, CyrisusRecovery.spiritCap(STAGE_GOT_CHEST), "no more spirit until he is armed")
        assertEquals(100, CyrisusRecovery.healthCap(STAGE_ARMED))
        assertEquals(100, CyrisusRecovery.spiritCap(STAGE_ARMED))
        assertNull(CyrisusRecovery.nextStage(player, STAGE_ARMED), "armed recovery ends in conversation")
    }

    @Test fun `he refuses either of his last two kinds of food, and forgets them on logout`() {
        val player = player()
        eat(player, SHARK)
        assertEquals(Feeding.Recent, CyrisusRecovery.feeding(player, STAGE_STARTED, SHARK))
        eat(player, LOBSTER)
        assertEquals(Feeding.Recent, CyrisusRecovery.feeding(player, STAGE_STARTED, SHARK), "still too recent")
        assertEquals(Feeding.Recent, CyrisusRecovery.feeding(player, STAGE_STARTED, LOBSTER))
        eat(player, BREAD)
        assertTrue(CyrisusRecovery.feeding(player, STAGE_STARTED, SHARK) is Feeding.Eats, "three kinds take turns")
        CyrisusRecovery.forgetMeals(player)
        assertTrue(CyrisusRecovery.feeding(player, STAGE_STARTED, BREAD) is Feeding.Eats)
    }

    @Test fun `only food counts, wine and guthix rest included, other drinks and dishes not`() {
        val player = player()
        assertEquals(Feeding.Sweets, CyrisusRecovery.feeding(player, STAGE_STARTED, CyrisusRecovery.SWEETS))
        for (notFood in listOf("obj.beer", "obj.keg_of_beer", "obj.beer_glass", "obj.bronze_sword", "obj.coins", "obj.cup_empty")) {
            assertEquals(Feeding.NotFood, CyrisusRecovery.feeding(player, STAGE_STARTED, notFood), notFood)
        }
        val wine = CyrisusRecovery.feeding(player, STAGE_STARTED, "obj.jug_wine")
        assertTrue(wine is Feeding.Eats, "wine: $wine")
        val rest = CyrisusRecovery.feeding(player, STAGE_STARTED, "obj.cup_guthix_rest_4") as Feeding.Eats
        assertEquals("obj.cup_guthix_rest_3", rest.leftover, "a dose at a time")
        val lastDose = CyrisusRecovery.feeding(player, STAGE_STARTED, "obj.cup_guthix_rest_1") as Feeding.Eats
        assertEquals("obj.cup_empty", lastDose.leftover)
    }

    @Test fun `each fighting style has exactly one right piece for every chest slot`() {
        for (style in CombatStyle.entries) {
            val pieces = (0 until CyrisusChest.BANK_SIZE).filter { CyrisusChest.styleOf(it) == style.value }
            assertEquals(CyrisusChest.SLOTS, pieces.size, "$style pieces")
            assertEquals((0 until CyrisusChest.SLOTS).toSet(), pieces.map { CyrisusChest.chestSlotOf(it) }.toSet(), "$style slots")
        }
        val melee = (0 until CyrisusChest.BANK_SIZE).filter { CyrisusChest.styleOf(it) == CombatStyle.Melee.value }
        assertEquals(
            setOf("Dragon med helm", "Ahrim's robetop", "Ahrim's robeskirt", "Ranger boots", "Abyssal whip"),
            melee.mapNotNull { CyrisusChest.bankObj(it)?.name }.toSet(),
        )
        assertTrue(CyrisusChest.chestSlotOf(0) == null, "the logs in slot 0 never go in the chest")
    }

    @Test fun `the chest holds a choice, swaps a slot's piece and judges it by style`() {
        val player = player()
        val whip = slotOf("Abyssal whip")
        val staff = slotOf("Ancient staff")
        assertEquals(CyrisusChest.Withdrawal.Packed(0, CyrisusChest.EMPTY), CyrisusChest.withdraw(player, whip))
        assertEquals(CyrisusChest.Withdrawal.Nothing, CyrisusChest.withdraw(player, whip), "already packed")
        assertEquals(CyrisusChest.Withdrawal.Packed(0, whip), CyrisusChest.withdraw(player, staff), "the staff replaces the whip")
        assertFalse(CyrisusChest.isPacked(player, whip))
        assertEquals(CyrisusChest.Withdrawal.NotWearable, CyrisusChest.withdraw(player, slotOf("Lobster")))
        assertEquals(CyrisusChest.Withdrawal.Nothing, CyrisusChest.withdraw(player, CyrisusChest.BANK_SIZE))
        for (name in listOf("Dragon med helm", "Ahrim's robetop", "Ahrim's robeskirt", "Ranger boots")) {
            CyrisusChest.withdraw(player, slotOf(name))
        }
        val fit = CyrisusChest.fit(player, CombatStyle.Melee)
        assertEquals(CyrisusChest.Fit.Wrong, fit[0])
        assertEquals(80, CyrisusChest.armament(fit))
        assertEquals(staff, CyrisusChest.deposit(player, 0))
        assertEquals(CyrisusChest.Fit.Missing, CyrisusChest.fit(player, CombatStyle.Melee)[0])
        CyrisusChest.withdraw(player, whip)
        assertEquals(100, CyrisusChest.armament(CyrisusChest.fit(player, CombatStyle.Melee)))
        assertEquals(0, CyrisusChest.armament(CyrisusChest.fit(player, CombatStyle.Ranged)))
    }

    @Test fun `cyrisus copies whichever style sets the player's combat level`() {
        assertEquals(CombatStyle.Melee, CombatStyle.dominant(attack = 80, strength = 80, ranged = 99, magic = 99))
        assertEquals(CombatStyle.Ranged, CombatStyle.dominant(attack = 60, strength = 60, ranged = 99, magic = 70))
        assertEquals(CombatStyle.Magic, CombatStyle.dominant(attack = 60, strength = 60, ranged = 70, magic = 99))
        assertEquals(CombatStyle.Melee, CombatStyle.dominant(attack = 75, strength = 75, ranged = 100, magic = 1), "melee wins ties")
    }

    @Test fun `the fears come in their order and the last has nothing after it`() {
        assertEquals(listOf(Fear.Inadequacy, Fear.Everlasting, Fear.Untouchable, Fear.Illusive), generateSequence(Fear.Inadequacy) { it.next }.toList())
        assertNull(Fear.Illusive.next)
    }

    @Test fun `the inadequacy melees only from beside its footprint`() {
        val type = checkNotNull(ServerCacheManager.getNpc(Fear.Inadequacy.type.asRSCMId()))
        val npc = Npc(type, CoordGrid(1822, 5147, 2))
        fun reach(x: Int, z: Int) = DreamFightScript.inMeleeReach(npc, player().apply { coords = CoordGrid(x, z, 2) })
        assertTrue(reach(1821, 5149), "west side")
        assertTrue(reach(1827, 5151), "east side")
        assertTrue(reach(1824, 5152), "north side")
        assertFalse(reach(1821, 5146), "diagonal corner")
        assertFalse(reach(1820, 5149), "two tiles away")
    }

    private fun eat(player: Player, obj: String) {
        val meal = CyrisusRecovery.feeding(player, STAGE_STARTED, obj) as Feeding.Eats
        CyrisusRecovery.eat(player, STAGE_STARTED, meal)
    }

    private fun slotOf(name: String): Int =
        (0 until CyrisusChest.BANK_SIZE).first { CyrisusChest.bankObj(it)?.name == name }

    private fun player() = Player().apply {
        inv = Inventory(InventoryServerType(size = 28, flags = 0), arrayOfNulls(28))
    }

    private fun String.asRSCMId(): Int = dev.openrune.rscm.RSCM.run { asRSCM(dev.openrune.rscm.RSCMType.NPC) }

    companion object {
        const val SHARK = "obj.shark"
        const val LOBSTER = "obj.lobster"
        const val BREAD = "obj.bread"

        @JvmStatic @BeforeAll fun cache() {
            ServerCacheManager.init(241).close()
        }
    }
}
