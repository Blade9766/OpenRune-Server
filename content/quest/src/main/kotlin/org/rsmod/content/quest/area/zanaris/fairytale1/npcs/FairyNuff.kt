package org.rsmod.content.quest.area.zanaris.fairytale1.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.zanaris.fairytale1.Fairytale1Quest
import org.rsmod.content.quest.area.zanaris.fairytale1.Fairytale1Quest.Companion.FAIRY_NUFF
import org.rsmod.content.quest.area.zanaris.fairytale1.Fairytale1Quest.Companion.STAGE_HAS_SYMPTOMS
import org.rsmod.content.quest.area.zanaris.fairytale1.Fairytale1Quest.Companion.STAGE_SEEN_GODFATHER
import org.rsmod.content.quest.area.zanaris.fairytale1.Fairytale1Quest.Companion.STAGE_SEEN_ZANDAR
import org.rsmod.content.quest.area.zanaris.fairytale1.Fairytale1Quest.Companion.SYMPTOMS_LIST
import org.rsmod.content.quest.area.zanaris.fairytale1.carries
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Fairy Nuff, the healer whose grotto is north of the Zanaris bank. She is the only fairy willing
 * to admit the Queen is ill, and she writes out the list of symptoms that sends the player to
 * Zandar Horfyre.
 */
class FairyNuff @Inject constructor(private val fairytale: Fairytale1Quest) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(FAIRY_NUFF) { startDialogue(it.npc) { nuff() } }
    }

    private suspend fun Dialogue.nuff() {
        val stage = fairytale.stage(player)
        when {
            fairytale.isComplete(player) -> afterQuest()
            stage >= STAGE_SEEN_ZANDAR -> alreadyDiagnosed()
            stage == STAGE_HAS_SYMPTOMS -> remindOrReplace()
            stage == STAGE_SEEN_GODFATHER -> theQueenIsIll()
            else -> smallTalk()
        }
    }

    private suspend fun Dialogue.smallTalk() {
        chatPlayer(neutral, "Hello there.")
        chatNpc(happy, "Hello! Fairy Nuff, healer. Mind the shelves; some of those really do bite.")
    }

    private suspend fun Dialogue.theQueenIsIll() {
        chatPlayer(quiz, "Everyone here tells me the Fairy Queen is on holiday. I don't believe them.")
        chatNpc(worried, "Nor should you. Close the door behind you, would you?")
        chatNpc(
            sad,
            "The Queen is not on holiday. The Queen is lying in the back of my grotto and she has " +
                "not woken for a fortnight. I have tried everything I know.",
        )
        chatPlayer(quiz, "What's wrong with her?")
        chatNpc(
            worried,
            "That is exactly the problem. Fairies do not sicken. There is no such thing as a sick " +
                "fairy, and so there is nobody in Zanaris who knows how to cure one.",
        )
        chatPlayer(quiz, "Has anyone outside Zanaris looked at her?")
        chatNpc(
            neutral,
            "The Godfather would have my wings for suggesting it. But yes - there is a human " +
                "wizard, Zandar Horfyre, who studies things no wizard should. He might recognise " +
                "the signs.",
        )
        giveList(replacement = false)
    }

    private suspend fun Dialogue.giveList(replacement: Boolean) {
        if (player.inv.isFull()) {
            chatNpc(neutral, "I would write it all down for you, but you have nowhere to put it. Come back with a free hand.")
            return
        }
        chatNpc(
            neutral,
            if (replacement) {
                "Then I shall write it out again. Do try to hang on to this one."
            } else {
                "I have written down everything I have seen: the sleep, the pallor, the cold. " +
                    "Take it to Horfyre and see what he makes of it."
            },
        )
        access.invAdd(access.inv, SYMPTOMS_LIST)
        objbox(SYMPTOMS_LIST, "Fairy Nuff hands you a list of the Fairy Queen's symptoms.")
        if (!replacement) {
            fairytale.advanceTo(access, STAGE_HAS_SYMPTOMS)
            chatNpc(
                worried,
                "You will find him at the top of the Dark Wizards' Tower, west of Falador. And " +
                    "please - not a word of this to the Godfather.",
            )
        }
    }

    private suspend fun Dialogue.remindOrReplace() {
        chatNpc(quiz, "Have you shown my list to the wizard yet?")
        if (access.carries(SYMPTOMS_LIST)) {
            chatPlayer(neutral, "Not yet. Zandar Horfyre, top of the Dark Wizards' Tower.")
            chatNpc(neutral, "That is the one. Hurry, please.")
            return
        }
        chatPlayer(sad, "I'm afraid I've lost the list.")
        giveList(replacement = true)
    }

    private suspend fun Dialogue.alreadyDiagnosed() {
        chatPlayer(neutral, "The wizard says a Tanglefoot has the Queen's secateurs.")
        chatNpc(
            worried,
            "A Tanglefoot. Of course. Without her secateurs the Queen cannot draw on the world's " +
                "growing, and neither can anything else. Find it, and bring them back.",
        )
    }

    private suspend fun Dialogue.afterQuest() {
        chatPlayer(neutral, "How is the Queen?")
        chatNpc(
            sad,
            "Sleeping still, but the colour is coming back to her. Her secateurs are safe, and " +
                "that is the half of it. The rest will take longer.",
        )
        chatPlayer(neutral, "Send for me if I can help again.")
        chatNpc(happy, "Oh, I shall.")
    }
}
