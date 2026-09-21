package org.rsmod.content.quest.area.ardougne.biohazard

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.ardougne.biohazard.BiohazardQuest.Companion.STAGE_TOLD_ELENA
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.RANGED_REQ
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.STAGE_COMPLETE
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.STAGE_IBAN_DEAD
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.STAGE_STARTED
import org.rsmod.content.quest.area.ardougne.undergroundpass.lathasMet
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * King Lathas in the throne room of East Ardougne castle, who admits the plague is a hoax and
 * explains the wall is there to keep his corrupted brother Tyras out.
 */
class KingLathas
@Inject
constructor(
    private val biohazard: BiohazardQuest,
    private val undergroundPass: UndergroundPassQuest,
) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(KING) { startDialogue(it.npc) { king() } }
    }

    private suspend fun Dialogue.king() {
        when {
            undergroundPass.stage(player) >= STAGE_IBAN_DEAD -> ibanIsDead()
            undergroundPass.isStarted(player) -> passInProgress()
            biohazard.quest.isQuestCompleted(player) -> afterQuest()
            biohazard.stage(player) == STAGE_TOLD_ELENA -> confrontation()
            else -> chatNpc(angry, "Leave me citizen. I'm far too busy to talk.")
        }
    }

    private suspend fun Dialogue.confrontation() {
        chatPlayer(neutral, "I assume that you are King Lathas of East Ardougne?")
        chatNpc(angry, "You assume correctly, but where do you get such impertinence.")
        chatPlayer(angry, "I get it from finding out that the plague is a hoax.")
        chatNpc(shocked, "A hoax? I've never heard such a ridiculous thing...")
        chatPlayer(neutral, "I have evidence, from Guidor of Varrock.")
        chatNpc(sad, "Ah... I see. Well then you are right about the plague. But I did it for the good of my people.")
        chatPlayer(angry, "When is it ever good to lie to people like that?")
        chatNpc(neutral, "When it protects them from a far greater danger, a fear too big to fathom.")
        chatPlayer(confused, "I don't understand...")
        chatNpc(neutral, "When my father was king, he ruled over a united Ardougne. But on his deathbed, he could not decide which of his sons should succeed him, so he had the city split into two.")
        chatNpc(neutral, "I became king of East Ardougne while my brother, Tyras, ruled over West Ardougne. My brother was a good man and was great at many things. Being a king was not one of them.")
        chatNpc(neutral, "Rather than looking after his people, he went on numerous expeditions to the lands west of here. On one of these expeditions, he was captured by the forces of the Dark Lord.")
        chatNpc(neutral, "The Dark Lord agreed to spare his life, but only on one condition... That he would drink from the Chalice of Eternity.")
        chatPlayer(quiz, "So what happened?")
        chatNpc(worried, "The chalice corrupted him. He joined forces with the Dark Lord, the embodiment of pure evil...")
        chatNpc(neutral, "And so I erected the wall, not just to protect my people, but to protect all the people of Gielinor. Tyras is king of West Ardougne, sealing off the city was the only way to stop him and the Dark Lord.")
        chatNpc(sad, "I knew people would never believe the truth, so I had the plague made up as an excuse for the cordon. I'm not proud of it, but it was the only way to keep people safe.")
        chatPlayer(neutral, "I see. Well at least I know now.")
        chatNpc(neutral, "While my brother is still at large, I must ask that you keep this secret. We can't bring the wall down until we know he is no longer a danger.")
        chatPlayer(quiz, "But how do we stop him?")
        chatNpc(happy, "You're willing to help? Gods be praised!")
        chatNpc(neutral, "My brother is currently gathering strength in the lands to the west. If we are to stop him, we must find a way through the mountains. There is an underground pass that should lead through but it is full of danger.")
        chatNpc(neutral, "Return to me when you are ready to face this peril. Until then, I give you permission to use my training area. It's located just to the north west of the city.")
        chatNpc(neutral, "I'll also let the mourners know that you are a friend to Ardougne. You will be allowed to pass through the gates to West Ardougne whenever you please. Now, you'd better be off. You have much to prepare for.")
        biohazard.advanceTo(access, biohazard.quest.maxSteps)
        chatNpc(quiz, "Do you need anything else adventurer?")
        when (
            choice2(
                "Why don't I get started right away?", 1,
                "I don't think so.", 2,
            )
        ) {
            1 -> {
                chatPlayer(happy, "Why don't I get started right away?")
                chatNpc(happy, "Your enthusiasm is to be commended. Very well, if you feel you are already prepared then who am I to stop you.")
                undergroundPass()
            }
            2 -> {
                chatPlayer(neutral, "I don't think so.")
                chatNpc(neutral, "Very well. Return to me once you are ready and we will discuss our next steps.")
            }
        }
    }

    private suspend fun Dialogue.afterQuest() {
        chatPlayer(happy, "Good day King Lathas.")
        chatNpc(neutral, "And to you adventurer. I assume you're here about King Tyras?")
        when (
            choice2(
                "I am. What's our plan?", 1,
                "I'm just passing through.", 2,
            )
        ) {
            1 -> {
                chatPlayer(neutral, "I am. What's our plan?")
                chatNpc(neutral, "Well, as we've previously discussed, my brother is currently gathering strength in the lands west of here. The only known way into those lands is through the Underground Pass.")
                undergroundPass()
            }
            2 -> {
                chatPlayer(neutral, "I'm just passing through.")
                chatNpc(neutral, "Fair enough. Return to me when you are ready and we will discuss our next steps.")
            }
        }
    }

    /**
     * The start of Underground Pass. Lathas will not send anyone down there who cannot shoot, so
     * the Ranged requirement is checked here rather than by the quest journal alone.
     */
    private suspend fun Dialogue.undergroundPass() {
        chatNpc(neutral, "As we've previously discussed, my brother is currently gathering strength in the lands west of here. The only known way into those lands is through the Underground Pass.")
        chatNpc(neutral, "Nobody who has gone down there has come back to tell of it. It runs under the mountains from a cave west of the city wall, and something has made a home of it.")
        chatNpc(quiz, "I need that road opened. Will you do it?")
        when (
            choice2(
                "I'll do it.", 1,
                "Not today.", 2,
            )
        ) {
            1 -> {
                chatPlayer(neutral, "I'll do it.")
                if (access.statBase("stat.ranged") < RANGED_REQ) {
                    chatNpc(worried, "Then you had better learn to use a bow first. There is work down there that wants shooting at from a distance, and you would not last the first hour.")
                    chatNpc(neutral, "Come back to me with a Ranged level of $RANGED_REQ.")
                    return
                }
                chatNpc(happy, "Good. My tracker Koftik is waiting for you outside the cave, in West Ardougne. He knows the first part of the road.")
                undergroundPass.advanceTo(access, STAGE_STARTED)
                player.lathasMet = 1
                chatNpc(neutral, "Take a rope, a spade, a tinderbox and a bow with you, and food. Koftik will tell you the rest.")
            }
            2 -> {
                chatPlayer(neutral, "Not today.")
                chatNpc(neutral, "Very well. Return to me when you are ready and we will discuss our next steps.")
            }
        }
    }

    private suspend fun Dialogue.passInProgress() {
        chatNpc(quiz, "How does it go down there?")
        chatPlayer(worried, "Badly. There is a great deal of it and none of it wants me alive.")
        chatNpc(neutral, "Then keep at it. Koftik is somewhere ahead of you, and he is a better tracker than he is a fighter. Look after him if you can.")
    }

    private suspend fun Dialogue.ibanIsDead() {
        if (undergroundPass.isComplete(player)) {
            chatNpc(happy, "The pass is open, and I have my brother to thank you for. My mages are still clearing the Well of Voyage.")
            return
        }
        chatPlayer(happy, "It's done. Iban is dead and his temple is down on top of him.")
        chatNpc(shocked, "Dead? You are certain?")
        chatPlayer(neutral, "I threw his own likeness into the well under his throne and the hill came down. I am certain.")
        chatNpc(happy, "Then the road west is open, and my brother's back is no longer to a mountain. You have done this kingdom a service it will not be able to repay.")
        chatNpc(neutral, "I will send mages down to clear the Well of Voyage the dwarves spoke of - it is the only way an army will pass. Until then, keep the staff. He will not be wanting it.")
        undergroundPass.advanceTo(access, STAGE_COMPLETE)
    }

    private companion object {
        /** The multi-npc on the throne; shows `npc.kinglathas_vis` in this era. */
        const val KING = "npc.kinglathas"
    }
}
