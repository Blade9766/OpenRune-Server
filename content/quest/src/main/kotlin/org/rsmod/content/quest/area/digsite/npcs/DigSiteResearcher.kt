package org.rsmod.content.quest.area.digsite.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.digsite.TheDigSiteQuest
import org.rsmod.content.quest.area.digsite.TheDigSiteQuest.Companion.RESEARCHER
import org.rsmod.content.quest.area.digsite.TheDigSiteQuest.Companion.STAGE_INVITED
import org.rsmod.content.quest.area.digsite.TheDigSiteQuest.Companion.STAGE_LEVEL1
import org.rsmod.content.quest.area.digsite.TheDigSiteQuest.Companion.STAGE_LEVEL2
import org.rsmod.content.quest.area.digsite.TheDigSiteQuest.Companion.STAGE_LEVEL3
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** The researcher in the Exam Centre, who explains where each archaeologist's tool comes from. */
class DigSiteResearcher @Inject constructor(private val quest: TheDigSiteQuest) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(RESEARCHER) { startDialogue(it.npc) { researcher() } }
    }

    private suspend fun Dialogue.researcher() {
        when (quest.stage(player)) {
            in 0 until STAGE_LEVEL1 -> beforeFirstExam()
            STAGE_LEVEL1 -> afterFirstExam()
            STAGE_LEVEL2 -> afterSecondExam()
            STAGE_LEVEL3 -> afterThirdExam()
            else -> afterTalisman()
        }
        topics()
    }

    private suspend fun Dialogue.beforeFirstExam() {
        chatNpc(quiz, "Hello there. What are you doing here?")
        chatPlayer(neutral, "I'm going to pass the Earth Sciences exams so I can dig at the site north of here.")
        chatNpc(
            happy,
            "A good goal to work towards, and it should prove rewarding for you. So where are you " +
                "starting your studies?",
        )
        chatPlayer(confused, "I'm not quite sure.")
        chatNpc(
            neutral,
            "Well, you could try looking in the Earth Sciences section of the shelves over there, " +
                "although the other students may have beaten you to it.",
        )
        chatNpc(
            neutral,
            "You could even ask the students themselves. I'm sure I saw a group of them on their " +
                "way to the digsite earlier today.",
        )
    }

    private suspend fun Dialogue.afterFirstExam() {
        chatPlayer(happy, "Hello!")
        chatNpc(
            happy,
            "Oh, hello there. Congratulations on passing your first exam, I saw your performance, " +
                "well done. Are you going to go for the next level?",
        )
        chatPlayer(happy, "Of course! I should probably talk to those students again; they were very helpful.")
        chatNpc(happy, "Glad to hear it!")
    }

    private suspend fun Dialogue.afterSecondExam() {
        chatPlayer(happy, "Hi there! I passed my second Earth Sciences exam.")
        chatNpc(happy, "Well done! Are you planning on going for level 3?")
        chatPlayer(happy, "Yes! Someday I'll know as much as you! Now, where did I leave those students....")
        chatNpc(happy, "Have fun and good luck!")
    }

    private suspend fun Dialogue.afterThirdExam() {
        chatPlayer(happy, "I did it! Level 3 Earth Sciences!")
        chatNpc(happy, "Bravo! Well done indeed! What are your plans now?")
        chatPlayer(neutral, "Well, I'm not really sure; I guess I should see what I can find around the site.")
        chatNpc(
            happy,
            "I hear that the third level digs yield the best artefacts for display; Terry, the " +
                "archaeological expert, has found many good things there that he's excited about.",
        )
    }

    private suspend fun Dialogue.afterTalisman() {
        if (quest.stage(player) < STAGE_INVITED) {
            afterThirdExam()
            return
        }
        chatNpc(happy, "I hear you've been given access to the private mining shafts. You're very lucky.")
        chatPlayer(happy, "I managed to find something... interesting.")
        chatNpc(quiz, "Oh? What was that?")
        chatPlayer(neutral, "A talisman linked to Zaros, so Terry says.")
        chatNpc(shocked, "Wow! That's a serious find! Good luck in the lower digs.")
    }

    private suspend fun Dialogue.topics() {
        val tools =
            choice2(
                "Can you tell me more about the tools an archaeologist uses?",
                true,
                "Thank you!",
                false,
            )
        if (!tools) {
            chatPlayer(happy, "Thank you!")
            return
        }
        chatPlayer(quiz, "Can you tell me more about the tools an archaeologist uses?")
        chatNpc(
            neutral,
            "Of course! Let's see now... Rock picks are for splitting rocks or scraping away " +
                "soil; you can get one from a cupboard in the Education Centre.",
        )
        chatPlayer(quiz, "What about sample jars?")
        chatNpc(
            neutral,
            "I think you'll find them scattered about pretty much everywhere, but I know you can " +
                "get one from a cupboard somewhere in the Education Centre, just like the rock pick!",
        )
        chatPlayer(quiz, "Okay, what about a specimen brush?")
        chatNpc(
            neutral,
            "We have a bit of a shortage of those at the moment. You could try borrowing one from " +
                "a workman on the site... but I don't think they'd give it willingly.",
        )
        chatPlayer(neutral, "Sounds like I'll need to be sneaky to get one of those, then... Okay - trowel?")
        chatNpc(neutral, "Ahh... that you must earn by passing your exams! The examiner holds those.")
        chatPlayer(quiz, "Anything else?")
        chatNpc(
            neutral,
            "If you need something identified or are not sure about something, give it to Terry; " +
                "he's the archaeological expert in the next room.",
        )
        chatPlayer(happy, "Ahh, ok thanks.")
    }
}
