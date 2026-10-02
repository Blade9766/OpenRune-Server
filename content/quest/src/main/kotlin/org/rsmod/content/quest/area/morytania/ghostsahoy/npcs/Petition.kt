package org.rsmod.content.quest.area.morytania.ghostsahoy.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.ECTOTOKEN
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.GRAVINGAS
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.PETITION
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.SIGNATURES_NEEDED
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.STAGE_GATHER
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.VILLAGER
import org.rsmod.content.quest.area.morytania.ghostsahoy.hasGhostspeak
import org.rsmod.content.quest.area.morytania.ghostsahoy.isDisguised
import org.rsmod.content.quest.area.morytania.ghostsahoy.owns
import org.rsmod.content.quest.manager.menu
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Gravingas's petition and the ghost villagers who sign it.
 *
 * A villager only signs for someone who can speak to them and looks like one of them (the
 * slimed bedsheet). Each asks at random: most sign, some want 1-3 ecto-tokens first (which can be
 * refused in favour of asking someone else) and some side with Necrovarus. The same ghost can't
 * be asked twice in a row, but two ghosts asked alternately are enough. The count lives on the
 * petition, so a replacement form from Gravingas starts again from nothing, and is re-checked
 * at the moment it is written so a repeated click can never add a second signature.
 */
class Petition @Inject constructor(private val ahoy: GhostsAhoyQuest) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(GRAVINGAS) { gravingas(it.npc) }
        onOpNpc1(VILLAGER) { villager(it.npc) }
        onOpHeld1(PETITION) { mes("There are ${ahoy.signatures(player)} signatures on the petition.") }
    }

    private suspend fun ProtectedAccess.gravingas(npc: Npc) {
        if (!player.hasGhostspeak()) {
            startDialogue(npc) { chatNpc(angry, "Wooo! Woooo woo wooooooo!") }
            mes("You can't understand what the ghost is saying.")
            return
        }
        startDialogue(npc) {
            chatNpc(angry, "Down with Necrovarus! Let the people choose for themselves!")
            if (ahoy.stage(player) != STAGE_GATHER || ahoy.isPetitionPresented(player)) {
                return@startDialogue
            }
            when {
                player.inv.contains(PETITION) && ahoy.signatures(player) >= SIGNATURES_NEEDED -> approve()
                player.owns(PETITION) -> {
                    chatNpc(neutral, "How is the petition coming along? We need $SIGNATURES_NEEDED signatures.")
                    chatNpc(neutral, "The townsfolk won't sign for a mortal, mind. You'll have to look the part.")
                }
                else -> offerPetition()
            }
        }
    }

    private suspend fun Dialogue.offerPetition() {
        val help = menu("Velorina told me her story. I'd be glad to help." to true, "Good luck with that." to false)
        if (!help) {
            chatPlayer(neutral, "Good luck with that.")
            return
        }
        chatPlayer(happy, "Velorina told me her story. I'd be glad to help.")
        if (access.invAdd(access.inv, PETITION).failure) {
            chatNpc(neutral, "You haven't room for the petition form. Come back when you do.")
            return
        }
        ahoy.setSignatures(player, 0)
        ahoy.setPetitionApproved(player, false)
        objbox(PETITION, "Gravingas hands you a petition form.")
        chatNpc(happy, "Take this to the townsfolk and get $SIGNATURES_NEEDED signatures. Then we'll see if Necrovarus can ignore his own people!")
        chatNpc(neutral, "Mind you, they won't sign anything for a mortal. You'll need to look a bit more... ghostly.")
    }

    private suspend fun Dialogue.approve() {
        chatPlayer(happy, "I've got all $SIGNATURES_NEEDED signatures!")
        ahoy.setPetitionApproved(player, true)
        chatNpc(happy, "Ten names! Take it to Necrovarus. Let's see him ignore his own people now!")
    }

    private suspend fun ProtectedAccess.villager(npc: Npc) {
        if (!player.hasGhostspeak()) {
            startDialogue(npc) { chatNpc(neutral, "Woooo wooo wooooo.") }
            mes("You can't understand what the ghost is saying.")
            return
        }
        startDialogue(npc) {
            if (!canCollect()) {
                chatNpc(neutral, "Greetings, traveller.")
                return@startDialogue
            }
            ask(npc)
        }
    }

    private fun Dialogue.canCollect(): Boolean =
        ahoy.stage(player) == STAGE_GATHER &&
            player.inv.contains(PETITION) &&
            !ahoy.isPetitionApproved(player) &&
            ahoy.signatures(player) < SIGNATURES_NEEDED

    private suspend fun Dialogue.ask(npc: Npc) {
        chatPlayer(neutral, "Would you sign this petition asking Necrovarus to let the ghosts pass on?")
        if (!player.isDisguised()) {
            chatNpc(angry, "Sign a petition for a mortal? Certainly not. Go away.")
            return
        }
        val identity = npc.spawnCoords.packed
        if (ahoy.lastSigner(player) == identity) {
            chatNpc(angry, "You asked me that a moment ago! Go and pester someone else.")
            return
        }
        ahoy.setLastSigner(player, identity)
        when (access.random.of(ANSWERS)) {
            0, 1 -> sign(npc)
            2 -> bribe(npc)
            else -> refuse()
        }
    }

    private suspend fun Dialogue.refuse() {
        val line =
            when (access.random.of(3)) {
                0 -> "I am a loyal follower of Necrovarus. How dare you!"
                1 -> "Necrovarus keeps us safe. I'll sign no such thing."
                else -> "Get lost."
            }
        chatNpc(angry, line)
    }

    private suspend fun Dialogue.bribe(npc: Npc) {
        val price = access.random.of(1, MAX_BRIBE)
        chatNpc(shifty, "It'll cost you. $price ecto-token${if (price == 1) "" else "s"}, and I'll sign.")
        if (player.inv.count(ECTOTOKEN) < price) {
            chatPlayer(sad, "I don't have that many ecto-tokens.")
            chatNpc(neutral, "Then no signature. Try someone else.")
            return
        }
        if (!menu("Pay $price ecto-token${if (price == 1) "" else "s"}." to true, "No thanks, I'll ask someone else." to false)) {
            chatPlayer(neutral, "No thanks, I'll ask someone else.")
            return
        }
        if (!canCollect() || access.invDel(access.inv, ECTOTOKEN, price).failure) {
            return
        }
        sign(npc)
    }

    private suspend fun Dialogue.sign(npc: Npc) {
        if (!canCollect()) {
            return
        }
        val count = ahoy.signatures(player) + 1
        ahoy.setSignatures(player, count)
        chatNpc(happy, "Yes, it's about time somebody stood up to him! There you go.")
        if (count >= SIGNATURES_NEEDED) {
            mesbox("That's $SIGNATURES_NEEDED signatures! You should take the petition back to Gravingas.")
        } else {
            mesbox("The ghost signs the petition. It now has $count signature${if (count == 1) "" else "s"}.")
        }
    }

    private companion object {
        /** Two in four sign, one in four asks for tokens, one in four refuses. */
        const val ANSWERS = 4
        const val MAX_BRIBE = 3
    }
}
