package org.rsmod.content.quest.area.camelot.merlinscrystal

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.NpcServerType
import dev.openrune.types.hunt.HuntVis
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.hunt.NpcSearch
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onArea
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.camelot.merlinscrystal.MerlinsCrystalQuest.Companion.BEGGAR
import org.rsmod.content.quest.area.camelot.merlinscrystal.MerlinsCrystalQuest.Companion.BREAD
import org.rsmod.content.quest.area.camelot.merlinscrystal.MerlinsCrystalQuest.Companion.EXCALIBUR
import org.rsmod.content.quest.area.camelot.merlinscrystal.MerlinsCrystalQuest.Companion.LADY_OF_THE_LAKE
import org.rsmod.content.quest.area.camelot.merlinscrystal.MerlinsCrystalQuest.Companion.STAGE_SPOKEN_MORGAN
import org.rsmod.content.quest.area.camelot.merlinscrystal.MerlinsCrystalQuest.Companion.TEST_MET_BEGGAR
import org.rsmod.content.quest.area.camelot.merlinscrystal.MerlinsCrystalQuest.Companion.TEST_REWARDED
import org.rsmod.content.quest.area.camelot.merlinscrystal.MerlinsCrystalQuest.Companion.TEST_SET
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Excalibur, and the test that earns it.
 *
 * The Lady of the Lake will not hand the sword to anyone who has not shown they are "above
 * material goods", and her test is a beggar outside Grum's Gold Exchange in Port Sarim asking for
 * a loaf of bread. The beggar is the Lady; giving him the bread ends the test on the spot. A
 * knight who later loses Excalibur can buy it back from her for 500 coins.
 */
@Singleton
class ExcaliburTest
@Inject
constructor(private val quest: MerlinsCrystalQuest, private val search: NpcSearch) :
    PluginScript() {

    private val ladyType: NpcServerType by lazy { npcType(LADY_OF_THE_LAKE) }

    override fun ScriptContext.startup() {
        onOpNpc1(LADY_OF_THE_LAKE) { startDialogue(it.npc) { lady() } }
        onOpNpc1(BEGGAR) { startDialogue(it.npc) { beggar(it.npc) } }
        onArea(JEWELLER_AREA) { approachedByBeggar() }
    }

    /* The Lady of the Lake, on the Taverley lake shore */

    private suspend fun Dialogue.lady() {
        chatNpc(neutral, "Good day to you.")
        val canAsk =
            quest.stage(player) >= STAGE_SPOKEN_MORGAN && !access.playerContainsObj(EXCALIBUR)
        val topic =
            if (canAsk) {
                choice3(
                    "Who are you?",
                    Topic.Who,
                    "Good day.",
                    Topic.Greet,
                    "I seek the sword Excalibur.",
                    Topic.Excalibur,
                )
            } else {
                choice2("Who are you?", Topic.Who, "Good day.", Topic.Greet)
            }
        when (topic) {
            Topic.Who -> {
                chatPlayer(quiz, "Who are you?")
                chatNpc(neutral, "I am the Lady of the Lake.")
            }
            Topic.Greet -> chatPlayer(neutral, "Good day.")
            Topic.Excalibur -> askForExcalibur()
        }
    }

    private suspend fun Dialogue.askForExcalibur() {
        chatPlayer(confused, "I seek the sword Excalibur.")
        chatNpc(neutral, "Aye, I have that artefact in my possession.")
        chatNpc(neutral, "'Tis very valuable, and not an artefact to be given away lightly.")
        chatNpc(neutral, "I would want to give it away only to one who is worthy and good.")
        if (quest.isComplete(player)) {
            replaceExcalibur()
            return
        }
        chatPlayer(quiz, "And how am I meant to prove that?")
        chatNpc(neutral, "I shall set a test for you.")
        chatNpc(
            neutral,
            "First I need you to travel to Port Sarim. Then go to the jeweller's shop there.",
        )
        chatPlayer(happy, "Ok. That seems easy enough.")
        player.merlinExcaliburTest = TEST_SET
    }

    private suspend fun Dialogue.replaceExcalibur() {
        chatNpc(
            neutral,
            "...But you have already proved thyself worthy of wielding it once. I shall return " +
                "it to you if you can prove yourself to still be worthy.",
        )
        chatPlayer(quiz, "...And how can I do that?")
        chatNpc(neutral, "Why, by proving yourself to be above material goods.")
        chatPlayer(quiz, "...And how can I do that?")
        chatNpc(happy, "500 gold coins ought to do it.")
        if (access.invCoinTotal() < REPLACEMENT_COST) {
            chatPlayer(sad, "I don't have that kind of money...")
            chatNpc(neutral, "Well, come back when you do.")
            return
        }
        chatPlayer(happy, "Ok, here you go.")
        if (access.invDel(access.inv, COINS, REPLACEMENT_COST).failure) {
            return
        }
        access.invAdd(access.inv, EXCALIBUR)
        chatNpc(
            happy,
            "You are still worthy to wield Excalibur! And thanks for the cash - I felt like " +
                "getting a new haircut!",
        )
    }

    /* The beggar in Port Sarim */

    /** The beggar makes his own approach the first time the player walks into the shop. */
    private suspend fun ProtectedAccess.approachedByBeggar() {
        if (player.merlinExcaliburTest != TEST_SET) {
            return
        }
        val beggar = nearestBeggar() ?: return
        stopAction()
        beggar.facePlayer(player)
        startDialogue(beggar) { begForBread(beggar) }
    }

    private suspend fun Dialogue.beggar(beggar: Npc) {
        when (player.merlinExcaliburTest) {
            TEST_SET -> begForBread(beggar)
            TEST_MET_BEGGAR -> {
                chatNpc(quiz, "Have you got any bread for me yet?")
                val give =
                    choice2("Yes, here you go.", true, "No, I still have none.", false)
                if (!give) {
                    chatPlayer(neutral, "Sorry, no, I still have none.")
                    return
                }
                chatPlayer(neutral, "Yes, here you go.")
                if (!access.invContainsBread()) {
                    chatPlayer(neutral, "Actually, I'm wrong. I still don't have any bread. Sorry.")
                    return
                }
                giveBread(beggar)
            }
            else -> mesbox("The beggar isn't interested in talking.")
        }
    }

    private suspend fun Dialogue.begForBread(beggar: Npc) {
        chatNpc(sad, "Please, kind stranger... my family and I are starving...")
        chatNpc(sad, "Could you find it in your heart to spare me a simple loaf of bread?")
        val give =
            choice2(
                "Yes, certainly.",
                true,
                "No, I don't have any bread with me.",
                false,
            )
        if (!give) {
            chatPlayer(neutral, "No, I don't have any bread with me.")
            refused()
            return
        }
        chatPlayer(neutral, "Yes, certainly.")
        if (!access.invContainsBread()) {
            chatPlayer(sad, "... except I don't have any bread on me at the moment...")
            refused()
            return
        }
        giveBread(beggar)
    }

    private suspend fun Dialogue.refused() {
        chatNpc(neutral, "Well, if you get some, you know where to come.")
        player.merlinExcaliburTest = TEST_MET_BEGGAR
    }

    private suspend fun Dialogue.giveBread(beggar: Npc) {
        if (access.invDel(access.inv, BREAD).failure) {
            return
        }
        mesbox("You give bread to the beggar.")
        chatNpc(happy, "Thank you very much!")
        access.npcChangeType(beggar, ladyType, TRANSFORM_TICKS)
        mesbox("The beggar has turned into the Lady of the Lake!")
        chatNpc(neutral, "Well done. You have passed my test.")
        chatNpc(neutral, "Here is Excalibur. Guard it well.")
        player.merlinExcaliburTest = TEST_REWARDED
        access.invAdd(access.inv, EXCALIBUR)
        access.objbox(EXCALIBUR, "The Lady of the Lake hands you Excalibur.")
    }

    private fun ProtectedAccess.nearestBeggar(): Npc? =
        npcFind(player.coords, BEGGAR, BEGGAR_SEARCH_RANGE, HuntVis.LineOfSight, search)

    private fun ProtectedAccess.invContainsBread(): Boolean = inv.contains(BREAD)

    private fun npcType(name: String): NpcServerType =
        ServerCacheManager.getNpc(name.asRSCM(RSCMType.NPC)) ?: error("Missing npc: $name")

    private enum class Topic {
        Who,
        Greet,
        Excalibur,
    }

    private companion object {
        const val JEWELLER_AREA = "area.merlin_sarim_jeweller"
        const val COINS = "obj.coins"
        const val REPLACEMENT_COST = 500
        const val TRANSFORM_TICKS = 100
        const val BEGGAR_SEARCH_RANGE = 10
    }
}
