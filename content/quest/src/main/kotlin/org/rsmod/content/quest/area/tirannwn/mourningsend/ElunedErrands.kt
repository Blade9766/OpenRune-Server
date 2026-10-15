package org.rsmod.content.quest.area.tirannwn.mourningsend

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.content.quest.area.tirannwn.mourningsend.MourningsEndQuest.Companion.COINS
import org.rsmod.content.quest.area.tirannwn.mourningsend.MourningsEndQuest.Companion.CRYSTALS
import org.rsmod.content.quest.area.tirannwn.mourningsend.MourningsEndQuest.Companion.CRYSTAL_3
import org.rsmod.content.quest.area.tirannwn.mourningsend.MourningsEndQuest.Companion.CRYSTAL_4
import org.rsmod.content.quest.area.tirannwn.mourningsend.MourningsEndQuest.Companion.CRYSTAL_SEED
import org.rsmod.content.quest.area.tirannwn.mourningsend.MourningsEndQuest.Companion.LLETYA_ARRIVAL
import org.rsmod.content.quest.area.tirannwn.mourningsend.MourningsEndQuest.Companion.STAGE_STARTED
import org.rsmod.content.quest.manager.startQuestPrompt
import org.rsmod.game.entity.Player

/**
 * Eluned's part in Mourning's End Part I, called from her Roving Elves script once that quest is
 * done: she takes the player to Lletya to start the quest, and afterwards re-enchants spent
 * teleport crystals.
 *
 * Starting is a single step: the crystal is added and the stage set in the same tick, before the
 * player is moved, so neither a repeated conversation nor a full pack can hand out a second
 * crystal or start the quest without one. Arianwyn's briefing follows straight on; if it is cut
 * short he gives it when next spoken to.
 *
 * Re-enchanting turns a crystal teleport seed into a three-charge crystal. The price falls by 150
 * coins with each enchantment, from 750 to 150, counted on `varbit.mourning_eluned_chant`.
 */
@Singleton
class ElunedErrands @Inject constructor(private val mourning: MourningsEndQuest, private val arianwyn: ArianwynBriefing) {

    fun offersQuest(player: Player): Boolean = !mourning.isStarted(player) && !mourning.isComplete(player)

    fun offersEnchant(player: Player): Boolean = mourning.isStarted(player) || mourning.isComplete(player)

    suspend fun Dialogue.offerQuest() {
        chatPlayer(neutral, "Hello Eluned.")
        chatNpc(neutral, "Ah, I was hoping to see you. Arianwyn, the leader of our people's resistance, asked me to bring you to him in Lletya when you were ready.")
        val ready = choice2("I'm ready.", true, "Not just now.", false)
        if (!ready) {
            chatPlayer(neutral, "Not just now.")
            chatNpc(neutral, "Not a problem. Just let us know when you're ready.")
            return
        }
        chatPlayer(neutral, "I'm ready.")
        val problem = mourning.startProblem(player)
        if (problem != null) {
            chatNpc(sad, "Forgive me, but I don't think you are ready yet.")
            mesbox(problem)
            return
        }
        if (!startQuestPrompt(mourning.quest)) {
            chatPlayer(neutral, "Actually, I don't want to go just yet.")
            chatNpc(neutral, "Not a problem. Just let us know when you're ready.")
            return
        }
        if (player.inv.freeSpace() < 1) {
            chatNpc(neutral, "Arianwyn asked me to give you something for the journey. Make some room in your pack first.")
            return
        }
        if (!start()) {
            return
        }
        mesbox("Eluned takes you to Lletya.")
        access.telejump(LLETYA_ARRIVAL)
        objbox(CRYSTAL_4, 400, "Eluned hands you a tiny crystal seed.")
        chatNpc(neutral, "If you ever need to come back to Lletya, use this. It only holds a few charges, so use it with care. I can sing more into it, though not as well as the one who made it.")
        chatPlayer(happy, "Thanks a lot Eluned!")
        chatNpc(neutral, "I should get back before Islwyn starts to worry. Arianwyn is waiting for you.")
        with(arianwyn) { brief() }
    }

    private fun Dialogue.start(): Boolean {
        if (mourning.isStarted(player)) {
            return false
        }
        if (access.invAdd(access.inv, CRYSTAL_4).failure) {
            return false
        }
        mourning.advanceTo(access, STAGE_STARTED)
        return true
    }

    suspend fun Dialogue.enchant() {
        val seeds = player.inv.count(CRYSTAL_SEED)
        if (seeds == 0) {
            if (CRYSTALS.any { player.inv.contains(it) }) {
                chatNpc(neutral, "Your crystal still has some song left in it. Come back when it has crumbled to a seed.")
            } else {
                chatNpc(neutral, "Bring me a crystal teleport seed and I can sing it back into a teleport crystal.")
            }
            return
        }
        val price = price(player)
        chatNpc(quiz, "Would you like me to re-enchant your teleport crystal? It will cost you $price coins to cover my time.")
        if (!choice2("Yes please.", true, "No thanks.", false)) {
            chatPlayer(neutral, "No thanks.")
            return
        }
        chatPlayer(happy, "Yes please.")
        if (access.invTotal(access.inv, COINS) < price) {
            chatNpc(sad, "I'm afraid you don't have enough coins.")
            return
        }
        if (!access.swap(listOf(CRYSTAL_SEED to 1, COINS to price), listOf(CRYSTAL_3 to 1))) {
            return
        }
        MourningsEndQuest.setVarBit(player, "varbit.mourning_eluned_chant", (player.elunedChants + 1).coerceAtMost(MAX_CHANTS))
        objbox(CRYSTAL_3, 400, "Although you can't hear anything, your crystal has been chanted.")
    }

    companion object {
        const val FIRST_PRICE = 750
        const val PRICE_STEP = 150
        const val LOWEST_PRICE = 150
        const val MAX_CHANTS = 7

        fun price(player: Player): Int = (FIRST_PRICE - PRICE_STEP * player.elunedChants).coerceAtLeast(LOWEST_PRICE)
    }
}
