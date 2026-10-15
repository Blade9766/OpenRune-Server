package org.rsmod.content.quest.area.feldip.bigchompy.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.feldip.bigchompy.BigChompyBirdHuntingQuest
import org.rsmod.content.quest.area.feldip.bigchompy.BigChompyBirdHuntingQuest.Companion.BUGS
import org.rsmod.content.quest.area.feldip.bigchompy.BigChompyBirdHuntingQuest.Companion.CHISEL
import org.rsmod.content.quest.area.feldip.bigchompy.BigChompyBirdHuntingQuest.Companion.COINS
import org.rsmod.content.quest.area.feldip.bigchompy.BigChompyBirdHuntingQuest.Companion.FLAVOUR_CABBAGE
import org.rsmod.content.quest.area.feldip.bigchompy.BigChompyBirdHuntingQuest.Companion.FLAVOUR_EQUA
import org.rsmod.content.quest.area.feldip.bigchompy.BigChompyBirdHuntingQuest.Companion.FLAVOUR_UNSET
import org.rsmod.content.quest.area.feldip.bigchompy.BigChompyBirdHuntingQuest.Companion.KNIFE
import org.rsmod.content.quest.area.feldip.bigchompy.BigChompyBirdHuntingQuest.Companion.STAGE_ASKED_ABOUT_TOADS
import org.rsmod.content.quest.area.feldip.bigchompy.BigChompyBirdHuntingQuest.Companion.STAGE_OPENED_CHEST
import org.rsmod.content.quest.area.feldip.bigchompy.BigChompyBirdHuntingQuest.Companion.STAGE_SHOWN_TOAD
import org.rsmod.content.quest.area.feldip.bigchompy.BigChompyBirdHuntingQuest.Companion.STAGE_STARTED
import org.rsmod.content.quest.area.feldip.bigchompy.BigChompyBirdHuntingQuest.Companion.STAGE_TOLD_TO_COOK
import org.rsmod.content.quest.area.feldip.bigchompy.BigChompyBirdHuntingQuest.Companion.TOOL_PRICE
import org.rsmod.content.quest.area.feldip.bigchompy.bugsFlavour
import org.rsmod.content.quest.area.feldip.bigchompy.chompyBoughtTools
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Bugs, Rantz's son. He sells the knife and chisel the arrows need to anyone who turned up without
 * them, and later names the seasoning he wants with his share of the chompy.
 */
class Bugs
@Inject
constructor(
    private val quest: BigChompyBirdHuntingQuest,
    private val objRepo: ObjRepository,
    private val random: GameRandom,
) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(BUGS) { startDialogue(it.npc) { bugs() } }
    }

    private suspend fun Dialogue.bugs() {
        when (quest.stage(player)) {
            0 -> chatNpc(neutral, "You's better talk to Dad, him chasey sneaky da chompy.")
            STAGE_STARTED -> sellTools()
            STAGE_ASKED_ABOUT_TOADS, STAGE_OPENED_CHEST -> aboutToads()
            STAGE_SHOWN_TOAD ->
                chatNpc(neutral, "You's better talk to Dad, he might have something for you to do.")
            STAGE_TOLD_TO_COOK -> nameSeasoning()
            else ->
                if (quest.isComplete(player)) {
                    chatNpc(neutral, "Thanks for da chompy, it was deloverly!")
                } else {
                    chatNpc(neutral, "Have you talked to Dad, he might have something for you to do.")
                }
        }
    }

    private suspend fun Dialogue.sellTools() {
        val carriesBoth =
            access.invTotal(access.inv, CHISEL) > 0 && access.invTotal(access.inv, KNIFE) > 0
        if (carriesBoth || player.chompyBoughtTools) {
            if (player.chompyBoughtTools) {
                chatNpc(
                    neutral,
                    "I's sorry creature, I don't got no more scratchers! But I got lots of bright " +
                        "pretties! Hope you's get da stabbers for Dad soonly!",
                )
            } else {
                chatNpc(neutral, "You's better talk to Dad, him chasey sneaky da chompy.")
            }
            return
        }
        chatNpc(neutral, "Hey you Creature, Dad says you's is gonna get da stabbers!")
        chatPlayer(happy, "That's right... I'm making some 'stabbers' for Rantz.")
        chatNpc(
            neutral,
            "Dat's great...Dad want's to hunt da chompy... Da chompy is our bestest yumms! Yous " +
                "needies da scratchers for makin' dem huh? I's wants some bright pretties for em!",
        )
        doubleobjbox(CHISEL, KNIFE, "Bugs shows you a chisel and a knife.")
        chatPlayer(neutral, "How many 'bright pretties' do you want?")
        chatNpc(
            neutral,
            "Bugs wants lots of bright pretties, this many! <col=0000ff>~ Bugs quickly opens and closes " +
                "his hands in front of you to indicate a number of bright pretties. ~</col> " +
                "<col=0000ff>~ It looks like he wants $TOOL_PRICE gold coins.~</col>",
        )
        val buy =
            choice2(
                "Ok, I'll give you $TOOL_PRICE bright pretties.",
                true,
                "Er, sorry, I can't give you that many...",
                false,
            )
        if (!buy) {
            chatPlayer(neutral, "Er, sorry, I can't give you that many...")
            chatNpc(angry, "Well, you not have da scratchers den!")
            return
        }
        chatPlayer(sad, "Ok, I'll give you $TOOL_PRICE bright pretties.")
        if (access.invTotal(access.inv, COINS) < TOOL_PRICE) {
            chatNpc(
                sad,
                "You's not got da bright pretties... I wants da bright pretties..you not get no " +
                    "scratchers wid'out da bright pretties.",
            )
            return
        }
        if (access.invDel(access.inv, COINS, TOOL_PRICE).failure) {
            return
        }
        player.chompyBoughtTools = true
        access.invAddOrDrop(objRepo, CHISEL)
        access.invAddOrDrop(objRepo, KNIFE)
        doubleobjbox(CHISEL, COINS, "You offer the $TOOL_PRICE coins for the tools.")
        chatNpc(happy, "Ok, dat's a good 'un, I got da bright pretties and you got da scratchers!")
    }

    private suspend fun Dialogue.aboutToads() {
        chatPlayer(neutral, "Rantz said that you play with the 'fatsy toadies', what are they?")
        chatNpc(
            neutral,
            "Oh we sometimes use da blower on da toadies but Dad don't let us get in da locked " +
                "box no more. He, he, it was good fun making da toadies fat on da swamp gas.",
        )
    }

    private suspend fun Dialogue.nameSeasoning() {
        if (player.bugsFlavour == FLAVOUR_UNSET) {
            player.bugsFlavour = if (random.randomBoolean()) FLAVOUR_EQUA else FLAVOUR_CABBAGE
        }
        val seasoning = if (player.bugsFlavour == FLAVOUR_CABBAGE) "cabbage" else "equa leaves"
        chatNpc(
            neutral,
            "Dad say's you's making da chompy for us! Slurp! Me's has to have $seasoning wiv mine! " +
                "Chompy is our favourite yummms!",
        )
    }
}
