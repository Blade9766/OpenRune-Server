package org.rsmod.content.quest.area.taverley.witchshouse.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.taverley.witchshouse.WitchsHouseQuest
import org.rsmod.content.quest.area.taverley.witchshouse.WitchsHouseQuest.Companion.BALL
import org.rsmod.content.quest.area.taverley.witchshouse.WitchsHouseQuest.Companion.BOY
import org.rsmod.content.quest.area.taverley.witchshouse.WitchsHouseQuest.Companion.RECOMMENDED_COMBAT
import org.rsmod.content.quest.area.taverley.witchshouse.WitchsHouseQuest.Companion.STAGE_STARTED
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** The boy crying by the hedges in north Taverley, whose ball is locked in the witch's shed. */
class Boy @Inject constructor(private val witchsHouse: WitchsHouseQuest) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(BOY) { startDialogue(it.npc) { boy() } }
    }

    private suspend fun Dialogue.boy() {
        when {
            witchsHouse.isComplete(player) -> chatNpc(happy, "Thank you for getting my ball back!")
            witchsHouse.isStarted(player) -> ballHunt()
            else -> notStarted()
        }
    }

    private suspend fun Dialogue.notStarted() {
        chatPlayer(happy, "Hello young man.")
        mesbox("The boy sobs.")
        when (choice2("What's the matter?", 1, "Well if you're not going to answer, I'll go.", 2)) {
            1 -> {
                chatPlayer(quiz, "What's the matter?")
                chatNpc(
                    sad,
                    "I've kicked my ball over that hedge, into that garden! The old lady who " +
                        "lives there is scary... She's locked the ball in her wooden shed! Can " +
                        "you get my ball back for me please?",
                )
                if (player.combatLevel < RECOMMENDED_COMBAT) {
                    mesbox(
                        "Before starting this quest, be aware that your combat level is lower " +
                            "than the recommended level of $RECOMMENDED_COMBAT.",
                    )
                }
                when (choice2("Yes.", 1, "No.", 2, title = "Start the Witch's House quest?")) {
                    1 -> {
                        chatPlayer(neutral, "Ok, I'll see what I can do.")
                        witchsHouse.advanceTo(access, STAGE_STARTED)
                        val title = if (player.appearance.bodyType == 1) "lady" else "mister"
                        chatNpc(happy, "Thanks $title!")
                    }
                    2 -> {
                        chatPlayer(angry, "Get it back yourself.")
                        chatNpc(angry, "You're a meany!")
                        mesbox("The boy starts crying again.")
                    }
                }
            }
            2 -> {
                chatPlayer(angry, "Well if you're not going to answer, I'll go.")
                mesbox("The boy sniffs slightly.")
            }
        }
    }

    private suspend fun Dialogue.ballHunt() {
        if (BALL !in player.inv) {
            chatNpc(quiz, "Have you got my ball back yet?")
            chatPlayer(sad, "Not yet.")
            chatNpc(angry, "Well, it's in the shed in that garden.")
            return
        }
        chatPlayer(
            neutral,
            "Hi, I have got your ball back. It was MUCH harder than I thought it would be.",
        )
        if (access.invDel(access.inv, BALL).failure) {
            return
        }
        mesbox("You give the ball back.")
        chatNpc(happy, "Thank you so much!")
        witchsHouse.quest.completeQuest(access)
    }
}
