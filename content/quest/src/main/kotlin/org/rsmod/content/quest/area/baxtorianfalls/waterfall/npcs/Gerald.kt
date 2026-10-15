package org.rsmod.content.quest.area.baxtorianfalls.waterfall.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.baxtorianfalls.waterfall.WaterfallQuest
import org.rsmod.content.quest.area.baxtorianfalls.waterfall.WaterfallQuest.Companion.GERALD
import org.rsmod.content.quest.area.baxtorianfalls.waterfall.WaterfallQuest.Companion.STAGE_MET_HUDON
import org.rsmod.content.quest.area.baxtorianfalls.waterfall.WaterfallQuest.Companion.STAGE_STARTED
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** Gerald fishes on the bank where the river washes treasure hunters ashore. */
class Gerald @Inject constructor(private val waterfall: WaterfallQuest) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(GERALD) { startDialogue(it.npc) { gerald() } }
    }

    private suspend fun Dialogue.gerald() {
        val stage = waterfall.stage(player)
        when {
            stage == 0 -> {
                chatPlayer(happy, "Hello there.")
                chatNpc(happy, "Good day, traveller. Come to fish, or just taking in the view? I've landed some real monsters down here.")
                chatPlayer(quiz, "Is that so?")
                chatNpc(laugh, "The last one was THIS big!")
            }
            stage == STAGE_STARTED -> {
                chatPlayer(happy, "Hello.")
                chatNpc(happy, "Hello there.")
                chatPlayer(quiz, "Have you seen a young boy around here?")
                chatNpc(laugh, "Can't say I have. Plenty of young fish, mind.")
            }
            waterfall.heardOfTreasure.get(player) -> {
                chatNpc(happy, "Hello there.")
                chatNpc(quiz, "Back again, traveller? Fishing, or treasure hunting?")
                chatPlayer(quiz, "What makes you say that?")
                chatNpc(neutral, "Adventurers come through here every week. None of them ever find a thing.")
            }
            stage >= STAGE_MET_HUDON -> {
                chatPlayer(happy, "Hello.")
                chatNpc(quiz, "Hello traveller. Here to fish, or to go looking for treasure?")
                chatPlayer(quiz, "What makes you say that?")
                chatNpc(neutral, "Adventurers come through here every week. None of them ever find a thing.")
                chatPlayer(quiz, "What is it they're looking for?")
                treasureRumour(waterfall)
            }
        }
    }
}

private const val GERALD_NAME = "Gerald"

private suspend fun Dialogue.treasureRumour(waterfall: WaterfallQuest) {
    chatNpc(neutral, "Legend has it the old elf king left a treasure hidden inside the waterfall. Not that anybody has ever found it.")
    chatPlayer(quiz, "Interesting. Where could I find out more?")
    chatNpc(happy, "Try Hadley, the tourist guide. He's in the building right here.")
    waterfall.heardOfTreasure.set(player, true)
}

/**
 * Gerald's greeting when the river dumps the player at his feet, once they have met Hudon and
 * before they have heard the legend.
 */
internal suspend fun ProtectedAccess.geraldGreetsWashedUp(waterfall: WaterfallQuest) {
    if (waterfall.stage(player) < STAGE_MET_HUDON || waterfall.heardOfTreasure.get(player)) {
        return
    }
    startDialogue {
        chatNpcSpecific(GERALD_NAME, GERALD, shocked, "Good grief! Where did you spring from? You're not another of those treasure hunters, are you?")
        chatPlayer(quiz, "Treasure hunters?")
        chatNpcSpecific(GERALD_NAME, GERALD, neutral, "Legend has it the old elf king left a treasure hidden inside the waterfall. Not that anybody has ever found it.")
        chatPlayer(quiz, "Interesting. Where could I find out more?")
        chatNpcSpecific(GERALD_NAME, GERALD, happy, "Try Hadley, the tourist guide. He's in the building right here.")
        waterfall.heardOfTreasure.set(player, true)
    }
}
