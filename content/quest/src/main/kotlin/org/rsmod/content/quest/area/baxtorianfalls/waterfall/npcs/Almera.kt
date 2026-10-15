package org.rsmod.content.quest.area.baxtorianfalls.waterfall.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.baxtorianfalls.waterfall.WaterfallQuest
import org.rsmod.content.quest.area.baxtorianfalls.waterfall.WaterfallQuest.Companion.ALMERA
import org.rsmod.content.quest.area.baxtorianfalls.waterfall.WaterfallQuest.Companion.RECOMMENDED_COMBAT
import org.rsmod.content.quest.area.baxtorianfalls.waterfall.WaterfallQuest.Companion.STAGE_ENTERED_TOMB
import org.rsmod.content.quest.area.baxtorianfalls.waterfall.WaterfallQuest.Companion.STAGE_MET_HUDON
import org.rsmod.content.quest.area.baxtorianfalls.waterfall.WaterfallQuest.Companion.STAGE_READ_BOOK
import org.rsmod.content.quest.area.baxtorianfalls.waterfall.WaterfallQuest.Companion.STAGE_STARTED
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** Almera, in her house at the top of the falls. She starts the quest. */
class Almera @Inject constructor(private val waterfall: WaterfallQuest) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(ALMERA) { startDialogue(it.npc) { almera() } }
    }

    private suspend fun Dialogue.almera() {
        when (waterfall.stage(player)) {
            0 -> notStarted()
            STAGE_STARTED -> {
                chatPlayer(happy, "Hello Almera.")
                chatNpc(worried, "Hello again. Any sign of my boy?")
                chatPlayer(neutral, "Not yet, but I doubt he's gone far.")
                chatNpc(worried, "I hope you're right. These are dangerous times.")
            }
            STAGE_MET_HUDON -> {
                chatPlayer(happy, "Hello again.")
                chatNpc(happy, "Oh, you're still here.")
                chatPlayer(sad, "I found Hudon on the river, but he wouldn't come back with me.")
                chatNpc(angry, "I know. The silly boy came home soaked through after going over the falls. He's lucky to be alive, and he won't be leaving his room for the rest of the summer.")
                chatPlayer(quiz, "Is there anything else around here worth seeing?")
                chatNpc(neutral, "You could try the tourist centre south of the waterfall.")
            }
            STAGE_READ_BOOK -> {
                chatPlayer(happy, "Hello again Almera.")
                chatNpc(happy, "Hello again. Enjoying the peace and quiet out here?")
                chatPlayer(happy, "Very much so.")
                chatNpc(happy, "Some officials once wanted to turn this whole valley into a mine. We locals refused to move, and in the end they gave up.")
                chatPlayer(happy, "Good for you.")
                chatNpc(laugh, "Good for all of us!")
            }
            in STAGE_ENTERED_TOMB..Int.MAX_VALUE -> {
                chatPlayer(happy, "Hello Almera.")
                chatNpc(quiz, "Hello there. How is the treasure hunting going?")
                chatPlayer(neutral, "I'm only here for the scenery.")
                chatNpc(laugh, "Nobody stays this long just for the scenery. Still, that's your business. Use the raft whenever you like, just try not to wreck it again!")
                chatPlayer(happy, "Thanks Almera.")
            }
        }
    }

    private suspend fun Dialogue.notStarted() {
        chatPlayer(happy, "Hello.")
        chatNpc(happy, "Oh, hello. It's nice to see a new face around here. I don't suppose you have a moment? I could use some help.")
        if (player.combatLevel < RECOMMENDED_COMBAT) {
            mesbox(
                "Before starting this quest, be aware that your combat level is lower than the " +
                    "recommended level of $RECOMMENDED_COMBAT.",
            )
        }
        when (choice2("Yes.", true, "No.", false, title = "Start the Waterfall Quest?")) {
            true -> {
                chatPlayer(quiz, "What's the problem?")
                chatNpc(worried, "My son Hudon is forever getting into mischief. He's got it into his head that there's treasure hidden in the river, and I'm worried sick. The poor boy can't even swim.")
                chatPlayer(happy, "I could go and look for him, if you like.")
                waterfall.quest.advanceQuestStage(access)
                chatNpc(happy, "Would you? That's very kind of you. Take the little raft out the back, but please be careful. The current downstream is fierce.")
            }
            false -> {
                chatPlayer(neutral, "Sorry, I'm in a hurry.")
                chatNpc(sad, "Oh. Never mind then.")
            }
        }
    }
}
