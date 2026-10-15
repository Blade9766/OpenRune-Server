package org.rsmod.content.quest.area.varrock.shieldofarrav.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.varrock.shieldofarrav.ShieldOfArravQuest
import org.rsmod.content.quest.area.varrock.shieldofarrav.ShieldOfArravQuest.Companion.COINS
import org.rsmod.content.quest.area.varrock.shieldofarrav.ShieldOfArravQuest.Companion.PHOENIX_TOLD_BY_BARAEK
import org.rsmod.content.quest.area.varrock.shieldofarrav.ShieldOfArravQuest.Companion.PHOENIX_TOLD_BY_RELDO
import org.rsmod.content.quest.area.varrock.shieldofarrav.phoenixGang
import org.rsmod.content.quest.area.varrock.shieldofarrav.phoenixSource
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Baraek, the fur trader in Varrock Square. He sells bear furs, buys furs back, and for 20 coins
 * tells Shield of Arrav players where the Phoenix Gang hide.
 */
class Baraek
@Inject
constructor(private val arrav: ShieldOfArravQuest, private val objRepo: ObjRepository) :
    PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(BARAEK) { startDialogue(it.npc) { baraek() } }
    }

    private suspend fun Dialogue.baraek() {
        val options = mutableListOf<Pair<String, Topic>>()
        if (player.phoenixGang == PHOENIX_TOLD_BY_RELDO && !arrav.isBlackArm(player)) {
            options += "Can you tell me where I can find the Phoenix Gang?" to Topic.PhoenixGang
        }
        options += "Can you sell me some furs?" to Topic.SellFurs
        options += "Hello. I am in search of a quest." to Topic.Quest
        when {
            access.inv.count(GREY_WOLF_FUR) > 0 ->
                options += "Would you like to buy my grey wolf fur?" to Topic.GreyWolfFur
            access.inv.count(BEAR_FUR) > 0 || access.inv.count(FUR) > 0 ->
                options += "Would you like to buy my fur?" to Topic.BuyFur
        }
        when (choose(options)) {
            Topic.PhoenixGang -> phoenixGang()
            Topic.SellFurs -> sellFurs()
            Topic.Quest -> {
                chatPlayer(neutral, "Hello! I am in search of a quest.")
                chatNpc(neutral, "Sorry, kiddo. I sell furs; I'm not some damsel in distress.")
            }
            Topic.GreyWolfFur -> buyGreyWolfFur()
            Topic.BuyFur -> buyFur()
        }
    }

    private suspend fun Dialogue.phoenixGang() {
        chatPlayer(quiz, "Can you tell me where I can find the Phoenix Gang?")
        chatNpc(worried, "Shh! Keep your voice down! Do you want to get me into trouble?")
        chatPlayer(shifty, "So you do know where they are.")
        chatNpc(
            shifty,
            "Maybe I do... but giving away their hideout could land me in hot water. Mind you, " +
                "if I were about 20 coins richer, I might be willing to take the risk...",
        )
        val choice =
            choice3(
                "Okay. Have 20 gold coins.",
                1,
                "No, I don't like things like bribery.",
                2,
                "Yes. I'd like to be 20 coins richer too.",
                3,
            )
        when (choice) {
            1 -> {
                chatPlayer(neutral, "Okay. Have 20 gold coins.")
                if (access.inv.count(COINS) < BRIBE) {
                    chatPlayer(sad, "Oh... hang on. I don't actually have 20 coins. Silly me.")
                    return
                }
                access.invDel(access.inv, COINS, BRIBE)
                access.soundSynth(COINS_SOUND)
                player.phoenixGang = PHOENIX_TOLD_BY_BARAEK
                player.phoenixSource = true
                chatNpc(
                    neutral,
                    "Right. Head to the south gate of the city, then follow the alley past the " +
                        "Blue Moon Inn.",
                )
                chatNpc(
                    neutral,
                    "Down there is a building belonging to the VTAM Corporation. That's the " +
                        "Phoenix Gang. Watch yourself, though - they're not ones to be trifled with.",
                )
                chatPlayer(happy, "Thanks!")
            }
            2 -> {
                chatPlayer(angry, "No, I don't do bribery.")
                chatNpc(
                    laugh,
                    "Heh. If you want to deal with the Phoenix Gang, a little bribery is the " +
                        "least of what they get up to.",
                )
            }
            else -> chatPlayer(happy, "Yes, I'd like to be 20 coins richer too.")
        }
    }

    private suspend fun Dialogue.sellFurs() {
        chatPlayer(quiz, "Can you sell me some furs?")
        chatNpc(neutral, "Sure. 20 gold coins apiece.")
        if (!choice2("Yeah, okay, here you go.", true, "20 gold coins? That's an outrage!", false)) {
            chatPlayer(angry, "20 gold coins? That's an outrage!")
            chatNpc(sad, "Sorry mate, that's as low as I go. I've a family to feed.")
            return
        }
        chatPlayer(neutral, "Yeah, okay, here you go.")
        if (access.inv.count(COINS) >= FUR_PRICE) {
            sellFur(FUR_PRICE)
            return
        }
        chatPlayer(sad, "Oh dear, I haven't got enough money!")
        chatNpc(neutral, "Well, the best I can do is 18 coins.")
        if (!choice2("Okay, here you go.", true, "No thanks, I'll leave it.", false)) {
            chatPlayer(neutral, "No thanks, I'll leave it.")
            chatNpc(neutral, "Your loss, mate.")
            return
        }
        chatPlayer(neutral, "Okay, here you go.")
        if (access.inv.count(COINS) < FUR_BEST_PRICE) {
            chatPlayer(sad, "Oh dear, I haven't got that either.")
            chatNpc(sad, "Sorry mate, that's as low as I go. I've a family to feed.")
            chatPlayer(neutral, "Oh well, never mind.")
            return
        }
        sellFur(FUR_BEST_PRICE)
    }

    private suspend fun Dialogue.sellFur(price: Int) {
        access.invDel(access.inv, COINS, price)
        access.invAddOrDrop(objRepo, BEAR_FUR)
        access.soundSynth(COINS_SOUND)
        objbox(BEAR_FUR, "Baraek sells you a fur.")
    }

    private suspend fun Dialogue.buyFur() {
        chatPlayer(quiz, "Would you like to buy my fur?")
        chatNpc(neutral, "Let's have a look at it.")
        val shown = if (access.inv.count(BEAR_FUR) > 0) BEAR_FUR else FUR
        objbox(shown, "You hand Baraek your fur to look at.")
        val count = access.inv.count(BEAR_FUR) + access.inv.count(FUR)
        val each = if (count == 1) "for it" else "for each one"
        chatNpc(neutral, "It's seen better days. I suppose I could give you 12 coins $each.")
        if (!choice2("Yeah, that'll do.", true, "I think I'll keep hold of it actually.", false)) {
            chatPlayer(neutral, "I think I'll keep hold of it, actually!")
            chatNpc(sad, "Suit yourself. I didn't want it anyway!")
            return
        }
        chatPlayer(happy, "Yeah, that'll do.")
        sellAll(listOf(BEAR_FUR, FUR), FUR_BUYBACK)
        chatPlayer(happy, "Thanks!")
    }

    private suspend fun Dialogue.buyGreyWolfFur() {
        chatPlayer(quiz, "Would you like to buy my grey wolf fur?")
        chatNpc(happy, "GREY WOLF FUR? Now you're talking!")
        chatNpc(happy, "I can always use grey wolf fur.")
        chatNpc(happy, "I'll give you 120 gold pieces for every one you've got. Fair?")
        if (!choice2("Yep, sounds fine.", true, "No! I almost got my throat torn out by a wolf to get this!", false)) {
            chatPlayer(angry, "No! A wolf nearly tore my throat out for this!")
            return
        }
        chatPlayer(happy, "Yep, that sounds fine.")
        sellAll(listOf(GREY_WOLF_FUR), GREY_WOLF_PRICE)
        chatPlayer(happy, "Thanks!")
    }

    private suspend fun Dialogue.sellAll(furs: List<String>, price: Int) {
        access.ifClose()
        for (fur in furs) {
            while (access.inv.count(fur) > 0) {
                access.invDel(access.inv, fur)
                access.invAdd(access.inv, COINS, price)
                access.soundSynth(COINS_SOUND)
                delay(1)
            }
        }
    }

    private suspend fun Dialogue.choose(options: List<Pair<String, Topic>>): Topic =
        when (options.size) {
            2 -> choice2(options[0].first, options[0].second, options[1].first, options[1].second)
            3 ->
                choice3(
                    options[0].first,
                    options[0].second,
                    options[1].first,
                    options[1].second,
                    options[2].first,
                    options[2].second,
                )
            else ->
                choice4(
                    options[0].first,
                    options[0].second,
                    options[1].first,
                    options[1].second,
                    options[2].first,
                    options[2].second,
                    options[3].first,
                    options[3].second,
                )
        }

    private enum class Topic {
        PhoenixGang,
        SellFurs,
        Quest,
        GreyWolfFur,
        BuyFur,
    }

    private companion object {
        const val BARAEK = "npc.baraek"
        const val BEAR_FUR = "obj.fur"
        const val FUR = "obj.werewolve_fur"
        const val GREY_WOLF_FUR = "obj.grey_wolf_fur"
        const val COINS_SOUND = "synth.coins_jingle_1"

        const val BRIBE = 20
        const val FUR_PRICE = 20
        const val FUR_BEST_PRICE = 18
        const val FUR_BUYBACK = 12
        const val GREY_WOLF_PRICE = 120
    }
}
