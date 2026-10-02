package org.rsmod.content.quest.area.ardougne.regicide

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.content.quest.area.ardougne.regicide.RegicideQuest.Companion.CRAFTING_REQ
import org.rsmod.content.quest.area.ardougne.regicide.RegicideQuest.Companion.IORWERTH_MESSAGE
import org.rsmod.content.quest.area.ardougne.regicide.RegicideQuest.Companion.KINGS_MESSAGE
import org.rsmod.content.quest.area.ardougne.regicide.RegicideQuest.Companion.STAGE_LETTER
import org.rsmod.content.quest.area.ardougne.regicide.RegicideQuest.Companion.STAGE_LETTER_UNSEALED
import org.rsmod.content.quest.area.ardougne.regicide.RegicideQuest.Companion.STAGE_STARTED
import org.rsmod.content.quest.manager.menu
import org.rsmod.content.quest.manager.startQuestPrompt

/**
 * King Lathas's side of Regicide, spoken from his throne in Ardougne Castle once the Underground
 * Pass is open. The quest can be accepted here whether or not the King's Messenger ever found the
 * player; the summons is only a reminder.
 */
@Singleton
class RegicideLathas
@Inject
constructor(private val regicide: RegicideQuest, private val pendant: CrystalPendant) {

    suspend fun Dialogue.talk() {
        val stage = regicide.stage(player)
        when {
            regicide.isComplete(player) -> {
                chatPlayer(neutral, "Good day my lord.")
                chatNpc(neutral, "Hello ${player.displayName}. I've got nothing for you right now.")
            }
            stage == 0 -> mission()
            stage < STAGE_LETTER -> {
                chatPlayer(quiz, "My liege. What must I do again?")
                orders()
            }
            stage == STAGE_LETTER -> chatNpc(angry, "Leave me citizen. I'm far too busy to talk.")
            else -> report()
        }
    }

    private suspend fun Dialogue.mission() {
        if (player.messengerSeen == 1 || player.inv.contains(KINGS_MESSAGE)) {
            chatPlayer(neutral, "I received your message my liege. How may I serve the kingdom?")
        } else {
            chatPlayer(neutral, "Good day my liege. How may I serve the kingdom?")
        }
        chatNpc(neutral, "Ahh... adventurer. The repair work on the Well of Voyage has been completed. We're finally ready to move against my brother.")
        when (
            menu(
                "I assume you have a plan?" to 1,
                "What's the deal with your brother again?" to 2,
                "Sounds good. See you later." to 3,
            )
        ) {
            1 -> {
                chatPlayer(quiz, "I assume you have a plan?")
                plan()
            }
            2 -> brother()
            else -> {
                chatPlayer(neutral, "Sounds good. See you later.")
                chatNpc(neutral, "Farewell adventurer. Return to me once you are ready to begin.")
            }
        }
    }

    private suspend fun Dialogue.brother() {
        chatPlayer(quiz, "What's the deal with your brother again?")
        chatNpc(neutral, "My brother, King Tyras, was always a bit of an explorer. He went on numerous expeditions to the lands west of here. On one of these expeditions, he was captured by the forces of the Dark Lord.")
        chatNpc(neutral, "The Dark Lord agreed to spare his life, but only on one condition... That he would drink from the Chalice of Eternity. The chalice corrupted him. He joined forces with the Dark Lord, the embodiment of pure evil...")
        chatNpc(neutral, "As a result, I had a wall built around West Ardougne to act as a barrier between him and the rest of the world. Tyras is king of West Ardougne, sealing off the city was the only way to stop him and the Dark Lord.")
        chatNpc(sad, "I knew people would never believe the truth, so I had the plague made up as an excuse for the cordon. I'm not proud of it, but it was the only way to keep people safe.")
        chatPlayer(neutral, "But now we're ready to stop him.")
        chatNpc(neutral, "Indeed we are. My brother has been gathering strength in the west, preparing to move against us. But now that we have a way through, we can move against him first.")
        when (menu("Excellent. Do you have a plan in place?" to 1, "Sounds good. See you later." to 2)) {
            1 -> {
                chatPlayer(quiz, "Excellent. Do you have a plan in place?")
                plan()
            }
            else -> {
                chatPlayer(neutral, "Sounds good. See you later.")
                chatNpc(neutral, "Farewell adventurer. Return to me once you are ready to begin.")
            }
        }
    }

    private suspend fun Dialogue.plan() {
        chatNpc(neutral, "I do indeed. I recently managed to make contact with the elves that inhabit the western lands.")
        chatPlayer(shocked, "Elves? I've never seen an elf before.")
        chatNpc(neutral, "Well that may well change soon. Apparently, my brother has been causing them quite a bit of trouble. As such, they've agreed to help us get rid of him once and for all.")
        chatPlayer(quiz, "So what do I need to do?")
        orders()
        if (!regicide.canStart(access)) {
            mesbox("You need a Crafting level of at least $CRAFTING_REQ and to have completed the Underground Pass to start this quest.")
            return
        }
        if (!startQuestPrompt(regicide.quest)) {
            chatPlayer(neutral, "Actually, I don't think I'm ready for this yet.")
            chatNpc(neutral, "As you wish. Return to me when you are ready and we will begin.")
            return
        }
        chatPlayer(quiz, "Very well. Anything else I should know?")
        regicide.advanceTo(access, STAGE_STARTED)
        chatNpc(worried, "My brother may not be the only threat you face. I hear the elves are currently dealing with a rebellion. The rebels likely won't take kindly to you working with their enemy so be on the look out.")
        chatPlayer(neutral, "I see. I'll be careful.")
        chatNpc(neutral, "Good luck adventurer.")
    }

    private suspend fun Dialogue.orders() {
        chatNpc(neutral, "You are to head into the Underground Pass and through the Well of Voyage. Lord Iorwerth, the leader of the elves, will send some representatives to meet you on the other side.")
        chatNpc(neutral, "With their help, you are to find my brother and kill him. Once that's done, return to me.")
    }

    /**
     * Handing over Iorwerth's letter completes the quest. The letter is taken and the stage set to
     * complete in the same cycle, so the reward is paid exactly once and the slot the letter frees
     * is there for the coins.
     */
    private suspend fun Dialogue.report() {
        chatPlayer(happy, "My lord, Tyras is dead!")
        chatNpc(quiz, "This is grand news indeed! You'll forgive me if I'm a little apprehensive about this, but I take it you do have proof?")
        if (!player.inv.contains(IORWERTH_MESSAGE)) {
            chatPlayer(sad, "No, I have nothing to corroborate this.")
            chatNpc(neutral, "I am sure you have done as you say, but on this matter I can afford no doubts. You must find something to confirm your story.")
            chatPlayer(neutral, "I'll see what I can do.")
            return
        }
        chatPlayer(neutral, "Yes, I have a letter sent by Lord Iorwerth.")
        objbox(IORWERTH_MESSAGE, "You hand the king the message. He looks at the seal then opens it.")
        chatNpc(happy, "Yes... Good... This will do nicely. Well done, you've done an excellent job.")
        chatPlayer(quiz, "Does this mean we can reveal the truth about the plague?")
        chatNpc(neutral, "Not yet I'm afraid. Even with my brother gone, the Dark Lord remains a threat. For now, the plague must remain.")
        chatNpc(neutral, "Anyway, I'll send word when I next require your services. Oh, and I guess you'll be wanting your reward.")
        if (regicide.stage(player) != STAGE_LETTER_UNSEALED || access.invDel(player.inv, IORWERTH_MESSAGE).failure) {
            return
        }
        with(pendant) { access.confiscate() }
        regicide.quest.completeQuest(access)
    }
}
