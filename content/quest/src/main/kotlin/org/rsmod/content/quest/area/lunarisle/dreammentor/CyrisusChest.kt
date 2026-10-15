package org.rsmod.content.quest.area.lunarisle.dreammentor

import dev.openrune.types.ItemServerType
import org.rsmod.api.enums.DreamMentorArmourEnums
import org.rsmod.api.enums.DreamMentorBankEnums
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.game.entity.Player

/**
 * Cyrisus's chest: the five pieces of armour the player has picked out of his bank for him. The
 * chest only ever holds a choice, never the items: each `dream_arma_itemN` varbit stores the bank
 * slot of the piece picked for chest slot N (weapon, helm, body, legs, boots), or 0 for none. Bank
 * slot 0 holds logs, which can never go in the chest.
 */
object CyrisusChest {
    const val SLOTS = 5
    const val BANK_SIZE = 36
    const val EMPTY = 0

    private val VARBITS =
        listOf(
            "varbit.dream_arma_item1",
            "varbit.dream_arma_item2",
            "varbit.dream_arma_item3",
            "varbit.dream_arma_item4",
            "varbit.dream_arma_item5",
        )

    val SLOT_NAMES = listOf("weapon", "headgear", "body armour", "leg armour", "footwear")

    enum class Fit {
        Missing,
        Right,
        Wrong,
    }

    sealed class Withdrawal {
        data object Nothing : Withdrawal()

        data object NotWearable : Withdrawal()

        /** The piece went into [chestSlot], putting back whatever was there ([replaced], or 0). */
        data class Packed(val chestSlot: Int, val replaced: Int) : Withdrawal()
    }

    fun bankObj(bankSlot: Int): ItemServerType? = DreamMentorBankEnums.dream_mentor_bank_objs[bankSlot]

    fun bankCount(bankSlot: Int): Int = DreamMentorBankEnums.dream_mentor_bank_counts[bankSlot] ?: 1

    fun chestSlotOf(bankSlot: Int): Int? = DreamMentorArmourEnums.dream_mentor_armour_slots[bankSlot]

    fun styleOf(bankSlot: Int): Int? = DreamMentorArmourEnums.dream_mentor_armour_styles[bankSlot]

    fun picked(player: Player, chestSlot: Int): Int = player.vars[VARBITS[chestSlot]]

    fun contents(player: Player): List<Int> = (0 until SLOTS).map { picked(player, it) }

    fun isPacked(player: Player, bankSlot: Int): Boolean = bankSlot != EMPTY && bankSlot in contents(player)

    fun isEmpty(player: Player): Boolean = contents(player).all { it == EMPTY }

    fun withdraw(player: Player, bankSlot: Int): Withdrawal {
        if (bankSlot !in 0 until BANK_SIZE || bankObj(bankSlot) == null || isPacked(player, bankSlot)) {
            return Withdrawal.Nothing
        }
        val chestSlot = chestSlotOf(bankSlot) ?: return Withdrawal.NotWearable
        val replaced = picked(player, chestSlot)
        set(player, chestSlot, bankSlot)
        return Withdrawal.Packed(chestSlot, replaced)
    }

    /** Puts the piece in [chestSlot] back in the bank; returns its bank slot, or [EMPTY]. */
    fun deposit(player: Player, chestSlot: Int): Int {
        if (chestSlot !in 0 until SLOTS) {
            return EMPTY
        }
        val bankSlot = picked(player, chestSlot)
        set(player, chestSlot, EMPTY)
        return bankSlot
    }

    fun fit(player: Player, style: CombatStyle): List<Fit> =
        contents(player).map { bankSlot ->
            when {
                bankSlot == EMPTY -> Fit.Missing
                styleOf(bankSlot) == style.value -> Fit.Right
                else -> Fit.Wrong
            }
        }

    fun armament(fit: List<Fit>): Int = fit.count { it == Fit.Right } * CyrisusRecovery.FULL / SLOTS

    fun clear(player: Player) {
        for (slot in 0 until SLOTS) {
            set(player, slot, EMPTY)
        }
    }

    private fun set(player: Player, chestSlot: Int, bankSlot: Int) {
        val varbit = VARBITS[chestSlot]
        if (player.vars[varbit] != bankSlot) {
            VarPlayerIntMapSetter.set(player, varbit, bankSlot)
        }
    }
}
