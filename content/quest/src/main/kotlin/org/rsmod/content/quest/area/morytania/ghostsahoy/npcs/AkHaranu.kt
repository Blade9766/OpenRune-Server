package org.rsmod.content.quest.area.morytania.ghostsahoy.npcs

import dev.openrune.rscm.RSCM.asRSCM
import jakarta.inject.Inject
import org.rsmod.api.invtx.invTransaction
import org.rsmod.api.invtx.select
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.AK_HARANU
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.BOW_ASKED
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.BOW_NONE
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.BOW_SIGNED
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.BOW_TRADED
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.MANUAL
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.SIGNED_OAK_LONGBOW
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.STAGE_GATHER
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Ak-Haranu, the eastern trader at the docks. He owns the translation manual and will part with
 * it only for an oak longbow signed by Robin, whom he worships as a bowman. The swap is one
 * transaction, and once traded he hands over another manual if the first is lost.
 */
class AkHaranu @Inject constructor(private val ahoy: GhostsAhoyQuest) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(AK_HARANU) { startDialogue(it.npc) { trader() } }
    }

    private suspend fun Dialogue.trader() {
        chatNpc(happy, "Ak-Haranu is trader from far across sea. Have many fine thing for sale!")
        if (ahoy.stage(player) != STAGE_GATHER || ahoy.givenManual(player)) {
            return
        }
        val bow = ahoy.bowState(player)
        when {
            bow == BOW_SIGNED && player.inv.contains(SIGNED_OAK_LONGBOW) -> trade()
            bow == BOW_TRADED && ahoy.needsManual(player) -> replace()
            bow == BOW_TRADED || player.inv.contains(MANUAL) -> {
                chatNpc(happy, "Ak-Haranu hope book is good help to you, friend.")
            }
            else -> ask(bow)
        }
    }

    private suspend fun Dialogue.ask(bow: Int) {
        chatPlayer(quiz, "I'm looking for a manual to translate an old book of spells. Do you have one?")
        chatNpc(neutral, "Ak-Haranu have such book, yes. But Ak-Haranu not want money for it.")
        chatNpc(happy, "In my country, there is great hero: the bowman Robin, staying at inn in this town! Bring Ak-Haranu oak longbow signed by him, and book is yours.")
        if (bow == BOW_NONE) {
            ahoy.setBowState(player, BOW_ASKED)
        }
        if (bow == BOW_SIGNED) {
            chatNpc(confused, "But where is bow? Bring it here!")
        }
    }

    private suspend fun Dialogue.trade() {
        chatNpc(shocked, "Can it be? A bow signed by Master Bowman himself!")
        val traded =
            player.invTransaction(player.inv) {
                val pack = select(player.inv)
                delete {
                    from = pack
                    obj = SIGNED_OAK_LONGBOW.asRSCM()
                    strictCount = 1
                }
                insert {
                    into = pack
                    obj = MANUAL.asRSCM()
                    strictCount = 1
                }
            }
        if (traded.failure) {
            return
        }
        ahoy.setBowState(player, BOW_TRADED)
        objbox(MANUAL, "You give Ak-Haranu the signed bow, and he gives you a translation manual in return.")
        chatNpc(happy, "Ak-Haranu will treasure it always!")
    }

    private suspend fun Dialogue.replace() {
        chatPlayer(sad, "I've lost the translation manual you gave me.")
        if (player.inv.isFull()) {
            chatNpc(neutral, "Make space in pack and Ak-Haranu give you another.")
            return
        }
        access.invAdd(access.inv, MANUAL)
        objbox(MANUAL, "Ak-Haranu gives you another translation manual.")
        chatNpc(happy, "For gift of signed bow, Ak-Haranu would give you a thousand books!")
    }
}
