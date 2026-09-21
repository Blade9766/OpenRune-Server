package org.rsmod.content.quest.area.camelot.merlinscrystal.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.camelot.merlinscrystal.MerlinsCrystalQuest
import org.rsmod.content.quest.area.camelot.merlinscrystal.MerlinsCrystalQuest.Companion.SIR_GAWAIN
import org.rsmod.content.quest.area.camelot.merlinscrystal.MerlinsCrystalQuest.Companion.SIR_LANCELOT
import org.rsmod.content.quest.area.camelot.merlinscrystal.MerlinsCrystalQuest.Companion.STAGE_SPOKEN_GAWAIN
import org.rsmod.content.quest.area.camelot.merlinscrystal.MerlinsCrystalQuest.Companion.STAGE_SPOKEN_LANCELOT
import org.rsmod.content.quest.area.camelot.merlinscrystal.MerlinsCrystalQuest.Companion.STAGE_SPOKEN_MORGAN
import org.rsmod.content.quest.area.camelot.merlinscrystal.MerlinsCrystalQuest.Companion.STAGE_STARTED
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The two knights who carry the trail: Gawain names Morgan Le Faye and her stronghold, and
 * Lancelot - who has heard of nothing he could not have done better himself - lets slip that its
 * only other way in is the sea entrance the Catherby deliveries come through.
 */
class RoundTableKnights @Inject constructor(private val quest: MerlinsCrystalQuest) :
    PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(SIR_GAWAIN) { startDialogue(it.npc) { gawain() } }
        onOpNpc1(SIR_LANCELOT) { startDialogue(it.npc) { lancelot() } }
    }

    private suspend fun Dialogue.gawain() {
        chatNpc(happy, "Good day to you!")
        val stage = quest.stage(player)
        when {
            stage == STAGE_STARTED -> {
                when (
                    choice3(
                        "Good day.",
                        Gawain.Greet,
                        "Any ideas on how to get Merlin out of that crystal?",
                        Gawain.Crystal,
                        "Do you know how Merlin got trapped?",
                        Gawain.Trapped,
                    )
                ) {
                    Gawain.Greet -> chatPlayer(neutral, "Good day.")
                    Gawain.Crystal -> {
                        chatPlayer(quiz, "Any ideas on how to get Merlin out of that crystal?")
                        chatNpc(
                            confused,
                            "I'm a little stumped myself. We've tried opening it with anything " +
                                "and everything!",
                        )
                    }
                    Gawain.Trapped -> howMerlinWasTrapped()
                    else -> Unit
                }
            }
            stage in STAGE_SPOKEN_GAWAIN..STAGE_SPOKEN_LANCELOT -> {
                when (
                    choice2(
                        "Any idea how to get into Morgan Le Faye's stronghold?",
                        Gawain.Stronghold,
                        "Hello again.",
                        Gawain.Greet,
                    )
                ) {
                    Gawain.Stronghold -> stronghold()
                    else -> chatPlayer(neutral, "Hello again.")
                }
            }
            stage >= STAGE_SPOKEN_MORGAN && !quest.isComplete(player) -> {
                chatPlayer(quiz, "Any ideas on finding Excalibur?")
                chatNpc(sad, "Unfortunately not, adventurer.")
            }
            else -> {
                when (
                    choice2(
                        "Good day.",
                        Gawain.Greet,
                        "Know you of any quests, sir knight?",
                        Gawain.Quests,
                    )
                ) {
                    Gawain.Quests -> {
                        chatPlayer(quiz, "Know you of any quests, sir knight?")
                        if (quest.isComplete(player)) {
                            chatNpc(confused, "I think you've done the main quest we were on right now...")
                        } else {
                            chatNpc(neutral, "The king is the man to talk to if you want a quest.")
                        }
                    }
                    else -> chatPlayer(neutral, "Good day.")
                }
            }
        }
    }

    private suspend fun Dialogue.howMerlinWasTrapped() {
        chatPlayer(quiz, "Do you know how Merlin got trapped?")
        chatNpc(angry, "I would guess this is the work of the evil Morgan Le Faye!")
        chatPlayer(quiz, "And where could I find her?")
        chatNpc(
            angry,
            "She lives in her stronghold to the south of here, guarded by some renegade knights " +
                "led by Sir Mordred.",
        )
        quest.advanceTo(access, STAGE_SPOKEN_GAWAIN)
        val follow =
            choice2(
                "Any idea how to get into Morgan Le Faye's stronghold?",
                Gawain.Stronghold,
                "Thank you for the information.",
                Gawain.Greet,
            )
        if (follow == Gawain.Stronghold) {
            stronghold()
            return
        }
        chatPlayer(neutral, "Thank you for the information.")
        chatNpc(happy, "It is the least I can do.")
    }

    private suspend fun Dialogue.stronghold() {
        chatPlayer(quiz, "Any idea how to get into Morgan Le Faye's stronghold?")
        chatNpc(confused, "No, you've got me stumped there...")
        chatNpc(neutral, "Sir Lancelot travels more than I do. He may know more.")
    }

    private suspend fun Dialogue.lancelot() {
        chatNpc(
            happy,
            "Greetings! I am Sir Lancelot, the greatest Knight in the land! What do you want?",
        )
        val stage = quest.stage(player)
        if (quest.isComplete(player)) {
            chatNpc(
                neutral,
                "Hmmm. I heard you freed Merlin. Either you're better than you look or you got " +
                    "lucky. I think the latter.",
            )
            return
        }
        val choice =
            when (stage) {
                0 ->
                    choice2(
                        "You're a little full of yourself, aren't you?",
                        Lancelot.Vanity,
                        "I seek a quest!",
                        Lancelot.Quest,
                    )
                STAGE_SPOKEN_GAWAIN ->
                    choice3(
                        "I want to get Merlin out of the crystal.",
                        Lancelot.Crystal,
                        "You're a little full of yourself, aren't you?",
                        Lancelot.Vanity,
                        "Any ideas on how to get into Morgan Le Faye's stronghold?",
                        Lancelot.Stronghold,
                    )
                else ->
                    choice2(
                        "I want to get Merlin out of the crystal.",
                        Lancelot.Crystal,
                        "You're a little full of yourself, aren't you?",
                        Lancelot.Vanity,
                    )
            }
        when (choice) {
            Lancelot.Vanity -> {
                chatPlayer(quiz, "You're a little full of yourself, aren't you?")
                chatNpc(happy, "I have every right to be proud of myself.")
                chatNpc(happy, "My prowess in battle is world renowned!")
            }
            Lancelot.Quest -> {
                chatPlayer(happy, "I seek a quest!")
                chatNpc(neutral, "Leave questing to the professionals.")
                chatNpc(happy, "Such as myself.")
            }
            Lancelot.Crystal -> {
                chatPlayer(neutral, "I want to get Merlin out of the crystal.")
                chatNpc(
                    angry,
                    "Well, if the Knights of the Round Table can't manage it, I can't see how a " +
                        "commoner like you could succeed where we have failed.",
                )
            }
            Lancelot.Stronghold -> seaEntrance()
        }
    }

    private suspend fun Dialogue.seaEntrance() {
        chatPlayer(quiz, "Any ideas on how to get into Morgan Le Faye's stronghold?")
        chatNpc(happy, "That stronghold is built in a strong defensive position.")
        chatNpc(happy, "It's on a big rock sticking out into the sea.")
        quest.advanceTo(access, STAGE_SPOKEN_LANCELOT)
        chatNpc(
            neutral,
            "There are two ways in that I know of: the large heavy front doors, and the sea " +
                "entrance, only penetrable by boat.",
        )
        chatNpc(neutral, "They get all their deliveries by boat from Catherby.")
    }

    private enum class Gawain {
        Greet,
        Crystal,
        Trapped,
        Stronghold,
        Quests,
    }

    private enum class Lancelot {
        Vanity,
        Quest,
        Crystal,
        Stronghold,
    }
}
