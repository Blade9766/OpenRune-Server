package org.rsmod.content.quest.area.taverley.druidicritual.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.taverley.druidicritual.DruidicRitualQuest
import org.rsmod.content.quest.area.taverley.druidicritual.DruidicRitualQuest.Companion.ENCHANTED_MEATS
import org.rsmod.content.quest.area.taverley.druidicritual.DruidicRitualQuest.Companion.SANFEW
import org.rsmod.content.quest.area.taverley.druidicritual.DruidicRitualQuest.Companion.STAGE_SPOKEN_SANFEW
import org.rsmod.content.quest.area.taverley.druidicritual.DruidicRitualQuest.Companion.STAGE_STARTED
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Sanfew, upstairs in Taverley's herblore shop. He asks for the four meats, each dipped in the
 * Cauldron of Thunder, and takes them off the player's hands.
 */
class Sanfew @Inject constructor(private val druidicRitual: DruidicRitualQuest) : PluginScript() {

    private val quest
        get() = druidicRitual.quest

    override fun ScriptContext.startup() {
        onOpNpc1(SANFEW) { startDialogue(it.npc) { sanfew() } }
    }

    private suspend fun Dialogue.sanfew() {
        chatNpc(quiz, "What can I do for you young 'un?")
        when (druidicRitual.stage(player)) {
            STAGE_STARTED ->
                when (
                    choice2(
                        "I've been sent to help purify the Varrock stone circle.", 1,
                        "Actually I don't need to speak to you.", 2,
                    )
                ) {
                    1 -> information()
                    2 -> dontNeed()
                }
            STAGE_SPOKEN_SANFEW -> giveIngredients()
            0 ->
                when (
                    choice2(
                        "I've heard you druids might be able to teach me herblore.", 1,
                        "Actually I don't need to speak to you.", 2,
                    )
                ) {
                    1 -> teachHerblore()
                    2 -> dontNeed()
                }
            else ->
                when (
                    choice2(
                        "Have you any more work for me, to help reclaim the circle?", 1,
                        "Actually I don't need to speak to you.", 2,
                    )
                ) {
                    1 -> moreWork()
                    2 -> dontNeed()
                }
        }
    }

    private suspend fun Dialogue.information() {
        chatPlayer(neutral, "I've been sent to assist you with the ritual to purify the Varrockian stone circle.")
        chatNpc(neutral, "Well, what I'm struggling with right now is the meats needed for the potion to honour Guthix. I need the raw meat of four different animals for it, but not just any old meats will do.")
        chatNpc(neutral, "Each meat has to be dipped individually into the Cauldron of Thunder for it to work correctly.")
        quest.advanceQuestStage(access)
        questions()
    }

    private suspend fun Dialogue.questions() {
        when (
            choice2(
                "Where can I find this cauldron?", 1,
                "Ok, I'll do that then.", 2,
            )
        ) {
            1 -> {
                chatPlayer(quiz, "Where can I find this cauldron?")
                chatNpc(neutral, "It is located somewhere in the mysterious underground halls which are located somewhere in the woods just South of here. They are too dangerous for me to go myself however.")
            }
            2 -> {
                chatPlayer(neutral, "Ok, I'll do that then.")
                chatNpc(happy, "Well thank you very much!")
            }
        }
    }

    private suspend fun Dialogue.giveIngredients() {
        chatNpc(quiz, "Did you bring me the required ingredients for the potion?")
        if (druidicRitual.hasAllMeats(player)) {
            handOver()
            return
        }
        chatPlayer(sad, "No, not yet...")
        chatNpc(neutral, "Well let me know when you do young 'un.")
        when (
            choice2(
                "What was I meant to be doing again?", 1,
                "I'll get on with it.", 2,
            )
        ) {
            1 -> {
                chatPlayer(neutral, "What was I meant to be doing again?")
                chatNpc(neutral, "Trouble with your memory eh young'un? I need the raw meats of four different animals that have been dipped into the Cauldron of Thunder so I can make my potion to honour Guthix.")
                chatPlayer(neutral, "Ooooh yeah, I remember.")
                questions()
            }
            2 -> {
                chatPlayer(neutral, "I'll get on with it.")
                chatNpc(happy, "Good, good.")
            }
        }
    }

    private suspend fun Dialogue.handOver() {
        chatPlayer(happy, "Yes, I have all four now!")
        chatNpc(happy, "Well hand 'em over then!")
        for (meat in ENCHANTED_MEATS) {
            access.invDel(access.inv, meat)
        }
        chatNpc(happy, "Thank you so much adventurer! These meats will allow our potion to honour Guthix to be completed, and bring one step closer to reclaiming our stone circle!")
        quest.advanceQuestStage(access)
        chatNpc(neutral, "Now go and talk to Kaqemeex and he will introduce you to the wonderful world of herblore and potion making!")
    }

    private suspend fun Dialogue.teachHerblore() {
        chatPlayer(quiz, "So... I've heard you druids might be able to teach me herblore...")
        chatNpc(neutral, "Herblore eh? You're probably best off talking to Kaqemeex about that; he's the best herblore teacher we currently have. I believe at the moment he's at our stone circle just North of here.")
        chatPlayer(happy, "Thanks.")
    }

    private suspend fun Dialogue.moreWork() {
        chatPlayer(quiz, "Have you any more work for me to help reclaim the stone circle?")
        chatNpc(neutral, "Well, not right now I don't think young 'un. In fact, I need to make some more preparations myself for the ritual. Rest assured, if I need any more help I will ask you again.")
    }

    private suspend fun Dialogue.dontNeed() {
        chatPlayer(confused, "Actually, I don't need to speak to you.")
        chatNpc(neutral, "Well, we all make mistakes sometimes.")
        access.mes("Sanfew grunts.")
    }
}
