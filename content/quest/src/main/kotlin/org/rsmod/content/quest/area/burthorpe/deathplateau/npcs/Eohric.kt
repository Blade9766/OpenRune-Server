package org.rsmod.content.quest.area.burthorpe.deathplateau.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.burthorpe.deathplateau.DeathPlateauQuest
import org.rsmod.content.quest.area.burthorpe.deathplateau.DeathPlateauQuest.Companion.EOHRIC
import org.rsmod.content.quest.area.burthorpe.deathplateau.DeathPlateauQuest.Companion.STAGE_BUY_DRINK
import org.rsmod.content.quest.area.burthorpe.deathplateau.DeathPlateauQuest.Companion.STAGE_FIND_HAROLD
import org.rsmod.content.quest.area.burthorpe.deathplateau.DeathPlateauQuest.Companion.STAGE_HAROLD_REFUSED
import org.rsmod.content.quest.area.burthorpe.deathplateau.DeathPlateauQuest.Companion.STAGE_ROOM_OPEN
import org.rsmod.content.quest.area.burthorpe.deathplateau.DeathPlateauQuest.Companion.STAGE_STARTED
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** Eohric, head servant of Burthorpe Castle, who knows where last night's guard is staying. */
class Eohric @Inject constructor(private val quest: DeathPlateauQuest) : PluginScript() {
    override fun ScriptContext.startup() {
        onOpNpc1(EOHRIC) { startDialogue(it.npc) { eohric() } }
    }

    private suspend fun Dialogue.eohric() {
        val stage = quest.stage(player)
        when {
            stage == STAGE_HAROLD_REFUSED -> {
                chatPlayer(happy, "Hi!")
                chatNpc(neutral, "Hi, can I help?")
                chatPlayer(sad, "I found Harold but he won't talk to me!")
                chatNpc(
                    neutral,
                    "Hmm. Harold has got in trouble a few times over his drinking and " +
                        "gambling. Perhaps he'd open up after a drink?",
                )
                chatPlayer(happy, "Thanks, I'll try that!")
                quest.advanceTo(access, STAGE_BUY_DRINK)
            }
            stage == STAGE_BUY_DRINK -> {
                chatPlayer(happy, "Hi!")
                chatNpc(neutral, "Hi, can I help?")
                chatPlayer(quiz, "You said that Harold had a weakness?")
                chatNpc(
                    neutral,
                    "Yes, if you buy Harold a beer he might talk to you. I also know he has a " +
                        "weakness for gambling. Hope that helps!",
                )
                chatPlayer(happy, "Thanks for the help!")
            }
            stage in STAGE_STARTED until STAGE_ROOM_OPEN -> questOptions(stage)
            else -> castleChat()
        }
    }

    private suspend fun Dialogue.questOptions(stage: Int) {
        if (stage == STAGE_STARTED) {
            chatPlayer(happy, "Hi!")
        }
        chatNpc(neutral, "Hi, can I help?")
        val guardOption =
            if (stage == STAGE_STARTED) {
                "I'm looking for the guard that was on last night."
            } else {
                "Where is the guard that was on last night staying?"
            }
        when (
            choice3(
                guardOption,
                1,
                "Do you know of another way up Death Plateau?",
                2,
                "No, I'm just looking around.",
                3,
            )
        ) {
            1 ->
                if (stage == STAGE_STARTED) {
                    chatPlayer(quiz, "I'm looking for the guard that was on last night.")
                    chatNpc(
                        neutral,
                        "There was only one guard on last night. Harold. He's a nice lad, if a " +
                            "little dim.",
                    )
                    chatPlayer(quiz, "Do you know where he is staying?")
                    chatNpc(neutral, "Harold is staying at the Toad and Chicken.")
                    chatPlayer(happy, "Thanks!")
                    quest.advanceTo(access, STAGE_FIND_HAROLD)
                } else {
                    chatPlayer(quiz, "Where is the guard that was on last night staying?")
                    chatNpc(neutral, "Harold is staying at the local inn, the Toad and Chicken.")
                }
            2 -> {
                chatPlayer(quiz, "Do you know of another way up Death Plateau?")
                chatNpc(
                    neutral,
                    "No, sorry. I wouldn't want to go north-east from here, it's very rocky " +
                        "and barren.",
                )
            }
            else -> chatPlayer(neutral, "No, I'm just looking around.")
        }
    }

    private suspend fun Dialogue.castleChat() {
        chatPlayer(happy, "Hi!")
        chatNpc(neutral, "Hi, can I help?")
        while (true) {
            when (
                choice4(
                    "What is this castle?",
                    1,
                    "Where is the prince?",
                    2,
                    "Do you have any quests for me?",
                    3,
                    "No, I'm just looking around.",
                    4,
                )
            ) {
                1 -> {
                    chatPlayer(quiz, "What is this castle?")
                    chatNpc(
                        happy,
                        "Welcome to Burthorpe Castle! Home to his Royal Highness the Prince of " +
                            "Burthorpe, Heir to the Throne of Asgarnia!",
                    )
                    chatPlayer(happy, "Wow!")
                }
                2 -> {
                    chatPlayer(quiz, "Where is the prince?")
                    chatNpc(neutral, "We do not disclose his majesty's business.")
                }
                3 -> {
                    chatPlayer(quiz, "Do you have any quests for me?")
                    chatNpc(neutral, "Yes actually I do, we need another servant.")
                    chatPlayer(quiz, "Does this noble quest have a name?")
                    chatNpc(shifty, "Yes, it's the... er... Empty the Cesspit Quest!")
                    chatPlayer(laugh, "I'll give it a miss!")
                }
                else -> {
                    chatPlayer(neutral, "No, I'm just looking around.")
                    return
                }
            }
            chatNpc(neutral, "Anything else I can help you with?")
        }
    }
}
