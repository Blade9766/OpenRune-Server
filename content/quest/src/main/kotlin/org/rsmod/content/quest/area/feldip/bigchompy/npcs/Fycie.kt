package org.rsmod.content.quest.area.feldip.bigchompy.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.feldip.bigchompy.BigChompyBirdHuntingQuest
import org.rsmod.content.quest.area.feldip.bigchompy.BigChompyBirdHuntingQuest.Companion.COINS
import org.rsmod.content.quest.area.feldip.bigchompy.BigChompyBirdHuntingQuest.Companion.FEATHER
import org.rsmod.content.quest.area.feldip.bigchompy.BigChompyBirdHuntingQuest.Companion.FEATHERS_SOLD
import org.rsmod.content.quest.area.feldip.bigchompy.BigChompyBirdHuntingQuest.Companion.FEATHER_PRICE
import org.rsmod.content.quest.area.feldip.bigchompy.BigChompyBirdHuntingQuest.Companion.FLAVOUR_DOOGLE
import org.rsmod.content.quest.area.feldip.bigchompy.BigChompyBirdHuntingQuest.Companion.FLAVOUR_TOMATO
import org.rsmod.content.quest.area.feldip.bigchompy.BigChompyBirdHuntingQuest.Companion.FLAVOUR_UNSET
import org.rsmod.content.quest.area.feldip.bigchompy.BigChompyBirdHuntingQuest.Companion.FYCIE
import org.rsmod.content.quest.area.feldip.bigchompy.BigChompyBirdHuntingQuest.Companion.STAGE_ASKED_ABOUT_TOADS
import org.rsmod.content.quest.area.feldip.bigchompy.BigChompyBirdHuntingQuest.Companion.STAGE_OPENED_CHEST
import org.rsmod.content.quest.area.feldip.bigchompy.BigChompyBirdHuntingQuest.Companion.STAGE_STARTED
import org.rsmod.content.quest.area.feldip.bigchompy.BigChompyBirdHuntingQuest.Companion.STAGE_TOLD_TO_COOK
import org.rsmod.content.quest.area.feldip.bigchompy.chompyBoughtFeathers
import org.rsmod.content.quest.area.feldip.bigchompy.fycieFlavour
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Fycie, Rantz's daughter. She sells the feathers a first-time fletcher needs for the arrows, and
 * later names the seasoning she wants with her share of the chompy.
 */
class Fycie
@Inject
constructor(
    private val quest: BigChompyBirdHuntingQuest,
    private val objRepo: ObjRepository,
    private val random: GameRandom,
) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(FYCIE) { startDialogue(it.npc) { fycie() } }
    }

    private suspend fun Dialogue.fycie() {
        when (quest.stage(player)) {
            0 -> chatNpc(neutral, "You's better talk to Dad, We not talk to wierdly|'umans.")
            STAGE_STARTED -> sellFeathers()
            STAGE_ASKED_ABOUT_TOADS, STAGE_OPENED_CHEST -> aboutToads()
            STAGE_TOLD_TO_COOK -> nameSeasoning()
            else ->
                if (quest.isComplete(player)) {
                    chatNpc(neutral, "Thanks for da chompy, it was deloverly!")
                } else {
                    chatNpc(neutral, "You's better talk to Dad, him chasey sneaky chompy.")
                }
        }
    }

    private suspend fun Dialogue.sellFeathers() {
        if (player.chompyBoughtFeathers) {
            chatNpc(
                neutral,
                "I's sorry creature, I don't got no more flufsies! But I got lots of bright " +
                    "pretties! Hope you's get da stabbers for Dad soonly!",
            )
            return
        }
        chatNpc(neutral, "Hey you Creature, I know's what you is You's a|'uman!")
        chatPlayer(happy, "That's right... I'm making some 'stabbers' for Rantz.")
        chatNpc(
            neutral,
            "Dat's great...Dad want's to hunt da chompy... Da chompy is our bestest yumms! Yous " +
                "needies da scratchers for makin' dem huh? I's wants some bright pretties for em!",
        )
        objbox(FEATHER, OBJBOX_ZOOM, "Fycie shows you the flufsies...you count $FEATHERS_SOLD of them.")
        chatPlayer(neutral, "How many 'bright pretties' do you want?")
        chatNpc(
            neutral,
            "Mee's wants lots of bright pretties, this many!|@blu@~ Fycie quickly opens and closes " +
                "her hands in front ~|@blu@~ of you to indicate a number of bright pretties. ~|" +
                "@blu@~ It looks like she wants $FEATHER_PRICE gold coins.~",
        )
        val buy =
            choice2(
                "Ok, I'll give you $FEATHER_PRICE bright pretties.",
                true,
                "Er, sorry, I can't give you that many...",
                false,
            )
        if (!buy) {
            chatPlayer(neutral, "Er, sorry, I can't give you that many...")
            chatNpc(angry, "Well, you not have da flufsies den!")
            return
        }
        chatPlayer(sad, "Ok, I'll give you $FEATHER_PRICE bright pretties.")
        if (access.invTotal(access.inv, COINS) < FEATHER_PRICE) {
            chatNpc(
                sad,
                "You's not got da bright pretties... I wants da bright pretties..you not get no " +
                    "flufsies wid'out da bright pretties.",
            )
            return
        }
        if (access.invDel(access.inv, COINS, FEATHER_PRICE).failure) {
            return
        }
        player.chompyBoughtFeathers = true
        access.invAddOrDrop(objRepo, FEATHER, FEATHERS_SOLD)
        doubleobjbox(FEATHER, COINS, "You offer the $FEATHER_PRICE coins for the $FEATHERS_SOLD flufsies.")
        chatNpc(happy, "Ok, dat's a good 'un, I got da bright pretties and you got da flufsies!")
    }

    private suspend fun Dialogue.aboutToads() {
        chatPlayer(neutral, "Rantz said that you play with the 'fatsy toadies', what are they?")
        chatNpc(
            neutral,
            "Oh we sometimes is using blower on da toadies but Dad don't let us get in da locksy " +
                "bocksy no more. He, he, big chuklees when make da toadies fat on da swampy gas.",
        )
    }

    private suspend fun Dialogue.nameSeasoning() {
        if (player.fycieFlavour == FLAVOUR_UNSET) {
            player.fycieFlavour = if (random.randomBoolean()) FLAVOUR_TOMATO else FLAVOUR_DOOGLE
        }
        val seasoning = if (player.fycieFlavour == FLAVOUR_DOOGLE) "doogle leaves" else "tomato"
        chatNpc(
            neutral,
            "Dad say's you's roastling da chompy for us! Slurp!|Me's wants $seasoning wiv mine! " +
                "Yummy, can't wait|to eats it.",
        )
    }

    private companion object {
        const val OBJBOX_ZOOM = 250
    }
}
