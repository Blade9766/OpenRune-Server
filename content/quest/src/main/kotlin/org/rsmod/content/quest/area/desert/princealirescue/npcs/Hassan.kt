package org.rsmod.content.quest.area.desert.princealirescue.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.desert.princealirescue.PrinceAliRescueQuest
import org.rsmod.content.quest.area.desert.princealirescue.PrinceAliRescueQuest.Companion.HASSAN
import org.rsmod.content.quest.area.desert.princealirescue.PrinceAliRescueQuest.Companion.JUG_OF_WATER
import org.rsmod.content.quest.area.desert.princealirescue.PrinceAliRescueQuest.Companion.STAGE_ALI_ESCAPED
import org.rsmod.content.quest.area.desert.princealirescue.PrinceAliRescueQuest.Companion.STAGE_COMPLETE
import org.rsmod.content.quest.area.desert.princealirescue.PrinceAliRescueQuest.Companion.STAGE_STARTED
import org.rsmod.content.quest.manager.startQuestPrompt
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** Chancellor Hassan starts the quest in the Al Kharid palace and pays out once the Prince is home. */
class Hassan @Inject constructor(private val princeAli: PrinceAliRescueQuest) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(HASSAN) { startDialogue(it.npc) { hassan() } }
    }

    private suspend fun Dialogue.hassan() {
        when (val stage = princeAli.stage(player)) {
            0 -> greeting()
            STAGE_STARTED -> {
                chatNpc(quiz, "Hello again. Have you spoken to Osman yet? He should be just outside the palace.")
                chatPlayer(neutral, "Not yet. I'll go and see him.")
            }
            in STAGE_STARTED until STAGE_ALI_ESCAPED ->
                chatNpc(
                    neutral,
                    "Hello again. I hear you have agreed to help rescue Prince Ali. On behalf of the " +
                        "Emir, I will have a reward ready for you upon your success.",
                )
            STAGE_ALI_ESCAPED -> reward()
            else -> {
                check(stage == STAGE_COMPLETE)
                chatNpc(happy, "Thank you for being a friend to Al Kharid. You are always welcome here.")
            }
        }
    }

    private suspend fun Dialogue.greeting() {
        chatNpc(happy, "Greetings! I am Hassan, Chancellor to the Emir of Al Kharid.")
        while (true) {
            when (
                choice4(
                    "Is there anything I can help you with?", HELP,
                    "It's just too hot here. How can you stand it?", HOT,
                    "Do you mind if I just kill your warriors?", WARRIORS,
                    "I'd better be off.", LEAVE,
                )
            ) {
                HELP -> {
                    offerQuest()
                    return
                }
                HOT -> {
                    chatPlayer(quiz, "It's just too hot here. How can you stand it?")
                    chatNpc(
                        neutral,
                        "We manage, in our humble way. We are a wealthy town and we have water. It " +
                            "cures many thirsts.",
                    )
                    if (access.invAdd(access.inv, JUG_OF_WATER).success) {
                        objbox(JUG_OF_WATER, "The chancellor hands you some water.")
                    }
                }
                WARRIORS -> {
                    chatPlayer(quiz, "Do you mind if I just kill your warriors?")
                    chatNpc(confused, "Kill our warriors? I assume this is some sort of joke?")
                    chatPlayer(neutral, "I'll take that as a no. Forget I asked.")
                }
                else -> {
                    chatPlayer(neutral, "I'd better be off.")
                    return
                }
            }
        }
    }

    private suspend fun Dialogue.offerQuest() {
        chatPlayer(quiz, "Is there anything I can help you with?")
        chatNpc(
            worried,
            "Well... we do currently have a very urgent issue we need to resolve, and I suppose you " +
                "look like someone who knows how to get a job done. Are you definitely interested " +
                "in helping?",
        )
        if (!startQuestPrompt(princeAli.quest)) {
            chatPlayer(neutral, "Actually, I've changed my mind.")
            chatNpc(neutral, "I see. Well you know where to find me if you do wish to help us.")
            return
        }
        chatPlayer(happy, "Of course.")
        princeAli.setStage(access, STAGE_STARTED)
        chatNpc(
            neutral,
            "You'll find our Spymaster, Osman, just outside the palace. Go to him and tell him I " +
                "sent you. He will fill you in on the details of our problem.",
        )
        chatPlayer(neutral, "Alright, I'll get to it.")
    }

    private suspend fun Dialogue.reward() {
        chatNpc(
            happy,
            "Prince Ali is home safe. You have the eternal gratitude of the Emir for rescuing his " +
                "son. Please, take this payment as a thank you.",
        )
        princeAli.setStage(access, STAGE_COMPLETE)
    }

    private companion object {
        const val HELP = 1
        const val HOT = 2
        const val WARRIORS = 3
        const val LEAVE = 4
    }
}
