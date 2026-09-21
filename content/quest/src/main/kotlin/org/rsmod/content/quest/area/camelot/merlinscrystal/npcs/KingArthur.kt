package org.rsmod.content.quest.area.camelot.merlinscrystal.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.camelot.merlinscrystal.MerlinsCrystalQuest
import org.rsmod.content.quest.area.camelot.merlinscrystal.MerlinsCrystalQuest.Companion.KING_ARTHUR
import org.rsmod.content.quest.area.camelot.merlinscrystal.MerlinsCrystalQuest.Companion.STAGE_FREED_MERLIN
import org.rsmod.content.quest.area.camelot.merlinscrystal.MerlinsCrystalQuest.Companion.STAGE_STARTED
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** King Arthur, at the head of the Round Table in Camelot. He starts and ends the quest. */
class KingArthur @Inject constructor(private val quest: MerlinsCrystalQuest) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(KING_ARTHUR) { startDialogue(it.npc) { arthur() } }
    }

    private suspend fun Dialogue.arthur() {
        when {
            quest.isComplete(player) -> alreadyKnighted()
            quest.stage(player) == STAGE_FREED_MERLIN -> merlinFreed()
            else -> firstMeeting()
        }
    }

    private suspend fun Dialogue.merlinFreed() {
        chatPlayer(happy, "I have freed Merlin from his crystal!")
        chatNpc(
            happy,
            "Ah. A good job, well done. I dub thee a Knight Of The Round Table. You are now an " +
                "honorary knight.",
        )
        quest.complete(access)
    }

    private suspend fun Dialogue.alreadyKnighted() {
        chatNpc(happy, "Greetings, Sir Knight. Camelot is in your debt.")
        chatPlayer(quiz, "How is Merlin?")
        chatNpc(
            laugh,
            "Shut away in his workshop again, muttering about crystals. Some things never change.",
        )
    }

    private suspend fun Dialogue.firstMeeting() {
        chatNpc(neutral, "Welcome to my court. I am King Arthur.")
        when (
            choice3(
                "I want to become a Knight of the Round Table!",
                Topic.Knight,
                "So what are you doing in RuneScape?",
                Topic.Here,
                "Thank you very much.",
                Topic.Thanks,
            )
        ) {
            Topic.Knight -> knighthood()
            Topic.Here -> {
                chatPlayer(quiz, "So what are you doing in RuneScape?")
                chatNpc(
                    neutral,
                    "Well, legend says we will return to Britain in its time of greatest need. " +
                        "But that's not for quite a while yet.",
                )
                chatNpc(neutral, "So we've moved the whole outfit here for now.")
                chatNpc(happy, "We're passing the time in RuneScape!")
            }
            Topic.Thanks -> chatPlayer(happy, "Thank you very much.")
        }
    }

    private suspend fun Dialogue.knighthood() {
        chatPlayer(neutral, "I want to become a Knight of the Round Table!")
        if (quest.isStarted(player)) {
            chatNpc(
                neutral,
                "Well then you must complete your quest to rescue Merlin. Talk to my knights if " +
                    "you need any help.",
            )
            return
        }
        chatNpc(
            neutral,
            "Really? Well then you will need to go on a quest to prove yourself worthy.",
        )
        chatNpc(neutral, "My knights all appreciate a good quest.")
        chatNpc(sad, "Unfortunately, our current quest is to rescue Merlin.")
        chatNpc(
            confused,
            "Back in England, he got himself trapped in some sort of magical crystal. We've " +
                "moved him from the cave we found him in and now he's upstairs in his tower.",
        )
        quest.advanceTo(access, STAGE_STARTED)
        chatPlayer(neutral, "I will see what I can do then.")
        chatNpc(neutral, "Talk to my knights if you need any help.")
    }

    private enum class Topic {
        Knight,
        Here,
        Thanks,
    }
}
