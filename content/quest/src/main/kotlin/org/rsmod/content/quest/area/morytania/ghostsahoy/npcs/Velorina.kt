package org.rsmod.content.quest.area.morytania.ghostsahoy.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.ECTOPHIAL
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.ECTOPHIAL_EMPTY
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.PRIEST_IN_PERIL
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.RESTLESS_GHOST
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.STAGE_COMPLETE
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.STAGE_FIND_CRONE
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.STAGE_REFUSED
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.STAGE_RELEASED
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.STAGE_STARTED
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.VELORINA
import org.rsmod.content.quest.area.morytania.ghostsahoy.hasGhostspeak
import org.rsmod.content.quest.area.morytania.ghostsahoy.owns
import org.rsmod.content.quest.manager.QuestRequirements
import org.rsmod.content.quest.manager.menu
import org.rsmod.content.quest.manager.startQuestPrompt
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Velorina, the ghost who starts the quest from her house east of the town gates. She sends the
 * player to Necrovarus, then to the Old Crone, and finishes the quest once he has been commanded
 * to release the ghosts. Afterwards she replaces a lost ectophial.
 */
class Velorina @Inject constructor(private val ahoy: GhostsAhoyQuest) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(VELORINA) { talk(it.npc) }
    }

    private suspend fun ProtectedAccess.talk(npc: Npc) {
        if (!player.hasGhostspeak()) {
            startDialogue(npc) { chatNpc(sad, "Woooo wooo wooooo woooo.") }
            mes("You can't understand what the ghost is saying.")
            return
        }
        startDialogue(npc) {
            when (ahoy.stage(player)) {
                0 -> introduction()
                STAGE_STARTED -> {
                    chatNpc(sad, "Have you spoken to Necrovarus yet? He is in the temple of the Ectofuntus, north of the town.")
                }
                STAGE_REFUSED -> refused()
                STAGE_FIND_CRONE -> crone()
                STAGE_RELEASED -> complete()
                STAGE_COMPLETE -> afterwards()
                else -> progress()
            }
        }
    }

    private suspend fun Dialogue.introduction() {
        chatNpc(sad, "Please, have you a moment for a lost soul? I have waited so very long.")
        when (menu("Why, what is the matter?" to true, "Sorry, I'm scared of ghosts." to false)) {
            false -> {
                chatPlayer(shocked, "Sorry, I'm scared of ghosts.")
                return
            }
            true -> chatPlayer(quiz, "Why, what is the matter?")
        }
        chatNpc(sad, "Do you know the history of Port Phasmatys?")
        chatPlayer(neutral, "No, I don't.")
        chatNpc(sad, "Long ago our priest Necrovarus built the Ectofuntus to save us from death. It worked, after a fashion.")
        chatNpc(sad, "We do not die. But neither may we pass on. He binds every ghost in this town to it, whether they wish it or not.")
        chatNpc(sad, "Most of us are weary beyond words. We only want to rest, but Necrovarus will not let anyone leave.")
        chatNpc(quiz, "Will you help us? Plead with Necrovarus on our behalf?")
        if (!QuestRequirements.hasCompleted(player, PRIEST_IN_PERIL) || !QuestRequirements.hasCompleted(player, RESTLESS_GHOST)) {
            chatNpc(sad, "Forgive me, you are not yet ready to face Necrovarus. Return when you have proven yourself against the undead.")
            mesbox("You must complete Priest in Peril and The Restless Ghost to start Ghosts Ahoy.")
            return
        }
        if (!startQuestPrompt(ahoy.quest)) {
            chatPlayer(neutral, "I can't right now, sorry.")
            chatNpc(sad, "Then we shall go on waiting. We are good at it.")
            return
        }
        chatPlayer(happy, "Yes, I'll help you.")
        ahoy.advanceTo(access, STAGE_STARTED)
        chatNpc(happy, "Bless you! Necrovarus is in the temple of the Ectofuntus, just north of the town. Ask him to let us go.")
    }

    private suspend fun Dialogue.refused() {
        chatPlayer(sad, "Necrovarus wouldn't listen. He threatened to burn the flesh off my bones.")
        chatNpc(sad, "I feared as much. Perhaps there is another way.")
        chatNpc(neutral, "There was a woman who knew Necrovarus in life, before he ever built the Ectofuntus. If anyone knows his weaknesses, she does.")
        crone()
    }

    private suspend fun Dialogue.crone() {
        chatPlayer(quiz, "Where can I find her?")
        chatNpc(neutral, "She is still mortal, and lives alone in a hut by the water, west of the farm outside our walls.")
        chatNpc(neutral, "She is very old now. Be patient with her.")
        ahoy.advanceTo(access, STAGE_FIND_CRONE)
    }

    private suspend fun Dialogue.progress() {
        chatNpc(quiz, "Have you found a way to make Necrovarus listen?")
        chatPlayer(neutral, "Not yet, but I'm working on it.")
        chatNpc(sad, "Please hurry. Every day here is a hundred years.")
    }

    private suspend fun Dialogue.complete() {
        chatNpc(happy, "I felt it! The barrier Necrovarus held around our souls is gone!")
        chatPlayer(happy, "He has agreed to let any ghost who wishes it pass on.")
        if (player.inv.freeSpace() < 1) {
            chatNpc(happy, "I have a gift for you, but your arms are full. Make some room and speak to me again.")
            return
        }
        chatNpc(happy, "Then I will go at last. Please, take this before I do.")
        chatNpc(neutral, "It is an ectophial. Empty it on the ground and you will be carried to the Ectofuntus at once. Refill it there afterwards.")
        ahoy.quest.completeQuest(access)
    }

    private suspend fun Dialogue.afterwards() {
        val holdsPhial = player.owns(ECTOPHIAL) || player.owns(ECTOPHIAL_EMPTY)
        if (holdsPhial) {
            chatNpc(happy, "Thank you again. I shall pass on soon, now that I am free to choose.")
            return
        }
        chatPlayer(sad, "I've lost the ectophial you gave me.")
        if (player.inv.isFull()) {
            chatNpc(neutral, "I have another, but you have no room to carry it.")
            return
        }
        access.invAdd(access.inv, ECTOPHIAL)
        chatNpc(happy, "I kept another, just in case. Take better care of this one.")
    }
}
