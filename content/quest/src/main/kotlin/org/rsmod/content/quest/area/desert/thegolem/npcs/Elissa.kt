package org.rsmod.content.quest.area.desert.thegolem.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.desert.thegolem.TheGolemQuest
import org.rsmod.content.quest.area.desert.thegolem.TheGolemQuest.Companion.ELISSA
import org.rsmod.content.quest.area.desert.thegolem.TheGolemQuest.Companion.SIDE_ASKED_ELISSA
import org.rsmod.content.quest.area.desert.thegolem.TheGolemQuest.Companion.SIDE_READ_LETTER
import org.rsmod.content.quest.area.desert.thegolem.TheGolemQuest.Companion.STAGE_REPAIRED
import org.rsmod.content.quest.area.desert.thegolem.golemSide
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Elissa, the Third Age architecture expert at the north-east corner of the Digsite. The letter
 * found in Uzer was hers, written to her late husband Varmen.
 */
class Elissa @Inject constructor(private val golem: TheGolemQuest) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(ELISSA) { startDialogue(it.npc) { elissa() } }
    }

    private suspend fun Dialogue.elissa() {
        chatNpc(happy, "Hello there.")
        val topic = questTopic()
        val choice =
            if (topic == null) {
                choice2("What do you do here?", Topic.Work, "What is this place?", Topic.Place)
            } else {
                choice3(
                    "What do you do here?",
                    Topic.Work,
                    "What is this place?",
                    Topic.Place,
                    topic.option,
                    topic,
                )
            }
        when (choice) {
            Topic.Work -> {
                chatPlayer(quiz, "What do you do here?")
                chatNpc(happy, "I'm helping with the dig. I'm an expert on Third Age architecture.")
            }
            Topic.Place -> aboutTheDig()
            Topic.Letter -> letter()
            Topic.Notes -> {
                chatPlayer(quiz, Topic.Notes.option)
                chatNpc(neutral, "They're on a bookcase in the Exam Centre.")
            }
            Topic.Statuette -> statuette()
        }
    }

    private fun Dialogue.questTopic(): Topic? {
        if (golem.stage(player) < STAGE_REPAIRED || golem.isComplete(player)) {
            return null
        }
        return when (player.golemSide) {
            0 -> null
            SIDE_READ_LETTER -> Topic.Letter
            SIDE_ASKED_ELISSA -> Topic.Notes
            else -> Topic.Statuette
        }
    }

    private suspend fun Dialogue.aboutTheDig() {
        chatPlayer(quiz, "What is this place?")
        chatNpc(
            happy,
            "In the Third Age, this was a great city. Look at these giant walls! They put " +
                "Varrock to shame!",
        )
        val impressed =
            choice2(
                "I don't know, Varrock is pretty impressive.",
                true,
                "What happened to the city?",
                false,
            )
        if (impressed) {
            chatPlayer(neutral, "I don't know, Varrock palace is impressive.")
            chatNpc(
                angry,
                "Hmph. I don't think it will look this good when it's been buried in the ground " +
                    "for three thousand years!",
            )
            return
        }
        chatPlayer(quiz, "What happened to the city?")
        chatNpc(neutral, "No one knows for sure.")
        chatNpc(
            neutral,
            "But the Third Age was a time of destruction, when the gods were violently at war.",
        )
        chatNpc(sad, "Many great civilizations were destroyed then.")
    }

    private suspend fun Dialogue.letter() {
        chatPlayer(neutral, "I found a letter in the desert with your name on.")
        chatNpc(happy, "Ah, so you've found the ruins of Uzer.")
        chatNpc(sad, "I wrote that letter to my late husband when he was exploring there.")
        chatNpc(
            neutral,
            "That was a great city as well, but the museum could only fund one excavation and " +
                "this one was closer to home.",
        )
        if (player.golemSide < SIDE_ASKED_ELISSA) {
            player.golemSide = SIDE_ASKED_ELISSA
        }
        chatNpc(
            neutral,
            "If you're interested in his expedition, the notes he made are in the library in the " +
                "Exam Centre.",
        )
    }

    private suspend fun Dialogue.statuette() {
        chatPlayer(quiz, "Where is the statuette that Varmen took back from Uzer?")
        chatNpc(neutral, "The statuette? Oh, yes...")
        chatNpc(sad, "That statuette was the only thing we had to show from that expedition.")
        chatNpc(
            neutral,
            "It was very worn, but you can still make out a lot of detail. The Uzerians were " +
                "expert sculptors. It's a pity we only have that small example.",
        )
        chatNpc(neutral, "Now it's on display in the Varrock museum.")
    }

    private enum class Topic(val option: String) {
        Work("What do you do here?"),
        Place("What is this place?"),
        Letter("I found a letter in the desert with your name on."),
        Notes("Where did you say the notes were?"),
        Statuette("Where is the statuette that Varmen took back from Uzer?"),
    }
}
