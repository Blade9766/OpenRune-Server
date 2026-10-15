package org.rsmod.content.skills.construction.scripts

import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.basePrayerLvl
import org.rsmod.api.player.stat.constructionLvl
import org.rsmod.api.player.vars.intVarp
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.manager.QuestRequirements
import org.rsmod.content.quest.manager.menu
import org.rsmod.content.skills.construction.data.Heraldry
import org.rsmod.content.skills.construction.data.Heraldry.Crest
import org.rsmod.content.skills.construction.data.Heraldry.Requirement
import org.rsmod.content.skills.construction.data.Paintings
import org.rsmod.content.skills.construction.data.Paintings.Painting
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Sir Renitee, the herald of Falador, who keeps every family's crest and paints pictures for the
 * quest hall. Dialogue follows the wiki transcript.
 */
class SirReniteeScript : PluginScript() {
    override fun ScriptContext.startup() {
        onOpNpc1(HERALD) { startDialogue(it.npc) { talk() } }
    }

    private suspend fun Dialogue.talk() {
        val young = if (access.isBodyTypeB()) "woman" else "man"
        chatNpc(quiz, "Hmm? What's that, young $young? What can I do for you?")
        val stay = choice2("I don't know, what can you do for me?", true, "Nothing, thanks", false)
        if (!stay) {
            chatPlayer(neutral, "Nothing thanks.")
            chatNpc(neutral, "Mmm, well, see you some other time maybe.")
            return
        }
        chatPlayer(neutral, "I don't know, what can you do for me?")
        chatNpc(
            neutral,
            "Hmm, well, mmm, do you have a family crest? I keep track of every Gielinor family, " +
                "you know, so I might be able to find yours.",
        )
        chatNpc(
            neutral,
            "I'm also something of an, mmm, a painter. If you've met any important persons or " +
                "visited any nice places I could paint them for you.",
        )
        val crest = choice2("Can you see if I have a family crest?", true, "Can I buy a painting?", false)
        if (crest) {
            readCrest(young)
        } else {
            buyPainting()
        }
    }

    private suspend fun Dialogue.readCrest(young: String) {
        chatPlayer(quiz, "Can you see if I have a family crest?")
        chatNpc(quiz, "What is your name?")
        chatPlayer(neutral, "${player.displayName}.")
        chatNpc(neutral, "Mmm, ${player.displayName}, let me see...")
        if (player.constructionLvl < Heraldry.CONSTRUCTION_LEVEL) {
            chatNpc(
                neutral,
                "First things first, young $young! There is not much point in having a family " +
                    "crest if you cannot display it. You should train construction until you can " +
                    "build a wall decoration in your dining room.",
            )
            return
        }
        val crest = Crest.of(player.familyCrest) ?: assignFirstCrest()
        chatNpc(neutral, "According to my records, your crest is ${crest.record}.")
        val change =
            choice2("I don't like that crest. Can I have a different one?", true, "Thanks!", false)
        if (!change) {
            chatPlayer(happy, "Thanks!")
            chatNpc(happy, "You're welcome, my ${if (access.isBodyTypeB()) "girl" else "boy"}.")
            return
        }
        changeCrest()
    }

    private suspend fun Dialogue.buyPainting() {
        chatPlayer(quiz, "Can I buy a painting?")
        chatNpc(quiz, "Would you like a portrait or an, mmm, a landscape? Or a map, maybe?")
        when (choice3("A portrait", 0, "A landscape", 1, "A map", 2)) {
            0 -> {
                chatPlayer(neutral, "A portrait please.")
                chatNpc(
                    neutral,
                    "Mmm, well, there are a few portraits I can paint. I can only let you have one " +
                        "if you've got some connection with that person though. Who would you like?",
                )
                pickPainting(Paintings.PORTRAITS)?.let { sell(it) }
            }
            1 -> {
                chatPlayer(neutral, "A landscape please.")
                chatNpc(
                    neutral,
                    "Mmm, well, I can paint a few places. Where have you had your adventures?",
                )
                pickPainting(Paintings.LANDSCAPES)?.let { sell(it) }
            }
            else -> {
                chatPlayer(neutral, "A map please.")
                chatNpc(
                    neutral,
                    "Mmm, yes, ah, I have painted maps of the known world on several different " +
                        "sizes of parchment. Which size would you like?",
                )
                val map = menu(Paintings.MAPS.map { it.option to it })
                if (player.questPoints < map.questPoints) {
                    chatNpc(
                        neutral,
                        "Mmm, a map that size shows a great deal of the world. Come back when you " +
                            "have seen more of it - say, ${map.questPoints} quest points' worth.",
                    )
                    return
                }
                sell(map)
            }
        }
    }

    /** The paintings the player has earned, or null after saying so when there are none. */
    private suspend fun Dialogue.pickPainting(paintings: List<Painting>): Painting? {
        val earned =
            paintings.filter { painting ->
                painting.quests.all { QuestRequirements.hasCompleted(player, it) }
            }
        if (earned.isEmpty()) {
            chatNpc(
                neutral,
                "Mmm, but I don't think you have had any adventures there yet. Come back when you " +
                    "have.",
            )
            return null
        }
        return menu(earned.map { it.option to it })
    }

    private suspend fun Dialogue.sell(painting: Painting) {
        chatNpc(neutral, "That will be, mmm, ${painting.cost} coins please.")
        if (access.invCoinTotal() < painting.cost) {
            chatPlayer(sad, "I don't have that much money on me.")
            return
        }
        if (!choice2("All right", true, "No thanks", false)) {
            chatPlayer(neutral, "No thanks.")
            chatNpc(neutral, "Well, mmm, maybe some other time, mmm?")
            return
        }
        chatPlayer(neutral, "All right.")
        if (access.inv.isFull()) {
            access.mes("You don't have enough inventory space.")
            return
        }
        if (access.invTakeFee(painting.cost)) {
            access.invAdd(access.inv, painting.obj)
            chatNpc(happy, "There you go.")
        }
    }

    private fun Dialogue.assignFirstCrest(): Crest {
        val crest = Heraldry.FREE_CRESTS[access.random.of(0, Heraldry.FREE_CRESTS.size - 1)]
        player.familyCrest = crest.id
        return crest
    }

    private suspend fun Dialogue.changeCrest() {
        chatPlayer(quiz, "I don't like that crest. Can I have a different one?")
        chatNpc(neutral, "Mmm, very well. Changing your crest will cost 5,000 coins.")
        if (access.invCoinTotal() < Heraldry.CHANGE_COST) {
            chatPlayer(sad, "I'll have to go get some.")
            return
        }
        chatNpc(neutral, "There are sixteen different symbols; which one would you like?")
        val crest = pickCrest()
        if (crest == Crest.MONEY) {
            buyMoneyCrest()
            return
        }
        if (!earned(crest)) {
            return
        }
        if (access.invTakeFee(Heraldry.CHANGE_COST)) {
            player.familyCrest = crest.id
        }
    }

    /** Sixteen crests, four to a page, the fifth option turning the page. */
    private suspend fun Dialogue.pickCrest(): Crest {
        var page = 0
        val pages = Crest.entries.chunked(CRESTS_PER_PAGE)
        while (true) {
            val crests = pages[page]
            val picked =
                choice5(
                    crests[0].option,
                    crests[0],
                    crests[1].option,
                    crests[1],
                    crests[2].option,
                    crests[2],
                    crests[3].option,
                    crests[3],
                    "More...",
                    null,
                )
            if (picked != null) {
                return picked
            }
            page = (page + 1) % pages.size
        }
    }

    private suspend fun Dialogue.earned(crest: Crest): Boolean {
        val (allowed, line) =
            when (crest.requirement) {
                Requirement.NONE -> true to approval(crest)
                Requirement.QUEST ->
                    QuestRequirements.hasCompleted(player, crest.quest!!).let { done ->
                        done to if (done) approval(crest) else refusal(crest)
                    }
                Requirement.PRAYER ->
                    (player.basePrayerLvl >= Heraldry.PRAYER_LEVEL).let { devoted ->
                        devoted to
                            if (devoted) approval(crest)
                            else
                                "You do not seem to be very devoted to any god. I will not let " +
                                    "you have a divine symbol unless you have level 70 prayer."
                    }
                Requirement.TOY_HORSEY ->
                    Heraldry.TOY_HORSEYS.any { it in access.inv }.let { brought ->
                        brought to if (brought) approval(crest) else refusal(crest)
                    }
                Requirement.SKULLED ->
                    (player.skullIcon != null).let { skulled ->
                        skulled to if (skulled) approval(crest) else refusal(crest)
                    }
            }
        chatNpc(if (allowed) happy else neutral, line)
        return allowed
    }

    private fun Dialogue.approval(crest: Crest): String =
        when (crest) {
            Crest.ARRAV ->
                "Ah yes, the shield that you helped to retrieve. You have certainly earned the " +
                    "right to wear its symbol."
            Crest.ASGARNIA ->
                "Ah, splendid, splendid. There is no better symbol than that of our fair land!"
            Crest.DORGESHUUN ->
                "Ah yes, our new neighbours under Lumbridge. I hear you were the one who made " +
                    "contact with them, jolly good."
            Crest.DRAGON ->
                "I see you are a mighty dragon-slayer! You have certainly earned the right to wear " +
                    "a dragon symbol."
            Crest.FAIRY -> "Hmm, mmm, yes, everyone likes pretty fairies."
            Crest.GUTHIX ->
                "Guthix, god of balance! I'm a Saradominist myself, you know, but we all find " +
                    "meaning in our way, what?"
            Crest.HAM ->
                "Hmm, I'm not sure I like that HAM group, their beliefs are a little extreme for " +
                    "me. But if that's what you want."
            Crest.HORSE ->
                "Ah, I see you've brought a toy horse for me to see. An interesting beast. " +
                    "Certainly you can use that as your crest if you like, although it seems a bit " +
                    "strange to me."
            Crest.JOGRE -> "A Jungle Ogre, eh? Odd beast, very odd."
            Crest.KANDARIN -> "Our neighbours in the west? Very good, very good."
            Crest.MISTHALIN -> "Ah, the fair land of Lumbridge and Varrock."
            Crest.MONEY -> "Thank you very much! You may now use a money-bag as your symbol."
            Crest.SARADOMIN ->
                "Ah, the great god Saradomin! May he smile on your house as you adorn it with his " +
                    "symbol!"
            Crest.SKULL ->
                "Of... of course you can have a skull symbol, " +
                    "${if (access.isBodyTypeB()) "madam" else "sir"}!"
            Crest.VARROCK -> "Ah, Varrock, a fine city!"
            Crest.ZAMORAK ->
                "The god of Chaos? It is a terrible thing to worship that evil being. But if that " +
                    "is what you wish..."
        }

    private fun refusal(crest: Crest): String =
        when (crest) {
            Crest.ARRAV ->
                "But that legendary shield is still lost! I don't think it would be proper for you " +
                    "to wear its symbol."
            Crest.DORGESHUUN ->
                "Hmm, have you ever even met the Dorgeshuun? I don't think you should wear their " +
                    "symbol until you have made contact with that lost tribe."
            Crest.DRAGON ->
                "When the dragon on Crandor Isle remains undefeated? I think you should prove " +
                    "yourself a dragon-slayer before you can wear a dragon symbol!"
            Crest.FAIRY ->
                "A fairy? Fairies are rumoured to exist in a lost city somewhere. I don't think I " +
                    "should let you use them as a symbol until you have met them in person."
            Crest.HORSE ->
                "A horse? I know people talk about them, but I'm not at all sure they ever " +
                    "existed. I don't think I could let you use one as your symbol unless you can " +
                    "fetch me some kind of model of one."
            Crest.SKULL ->
                "A symbol of death? You do not seem like a killer to me; perhaps some other symbol " +
                    "would suit you better."
            else -> error("$crest has no requirement to refuse.")
        }

    private suspend fun Dialogue.buyMoneyCrest() {
        val offer =
            "You wish to represent yourself by a moneybag? I think to make that meaningful I " +
                "should increase the price to 500,000 coins. Do you agree?"
        chatNpc(neutral, offer)
        if (!choice2("All right", true, "No way", false)) {
            chatPlayer(angry, "No way!")
            chatNpc(
                neutral,
                "Well we can't have just any pauper using a money-bag as a symbol, can we? You'll " +
                    "have to choose a different symbol.",
            )
            return
        }
        chatPlayer(neutral, "All right.")
        if (access.invCoinTotal() < Heraldry.MONEY_COST) {
            chatPlayer(sad, "But I'll have to go and fetch the money.")
            return
        }
        if (access.invTakeFee(Heraldry.MONEY_COST)) {
            player.familyCrest = Crest.MONEY.id
            chatNpc(happy, approval(Crest.MONEY))
        }
    }

    private companion object {
        const val HERALD = "npc.poh_herald_of_falador"
        const val CRESTS_PER_PAGE = 4
    }
}

private val Player.questPoints by intVarp("varp.qp")

/** The player's family crest as a [Crest.id], or zero before Sir Renitee has assigned one. */
internal var Player.familyCrest by intVarp("varp.poh_family_crest")

internal fun ProtectedAccess.familyCrest(): Crest? = Crest.of(player.familyCrest)
