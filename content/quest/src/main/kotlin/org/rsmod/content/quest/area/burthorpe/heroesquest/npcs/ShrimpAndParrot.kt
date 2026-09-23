package org.rsmod.content.quest.area.burthorpe.heroesquest.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.api.shops.Shops
import org.rsmod.content.quest.area.burthorpe.heroesquest.HeroesQuest
import org.rsmod.content.quest.area.burthorpe.heroesquest.HeroesQuest.Companion.PHOENIX_ALFONSE
import org.rsmod.content.quest.area.burthorpe.heroesquest.HeroesQuest.Companion.PHOENIX_BRIEFED
import org.rsmod.content.quest.area.burthorpe.heroesquest.HeroesQuest.Companion.PHOENIX_CHARLIE
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

internal const val ALFONSE = "npc.alfonse_the_waiter"
private const val CHARLIE = "npc.charlie_the_cook"

/**
 * The Shrimp and Parrot, the Brimhaven restaurant the Phoenix Gang set up as a front for the
 * candlestick heist. Alfonse waits tables and answers the gang's password; Charlie the cook shows
 * members the secret panel into Mr Olbors' garden.
 */
class ShrimpAndParrot @Inject constructor(private val heroes: HeroesQuest, private val shops: Shops) :
    PluginScript() {
    override fun ScriptContext.startup() {
        onOpNpc1(ALFONSE) { startDialogue(it.npc) { alfonse() } }
        onOpNpc3(ALFONSE) { openShop(player) }
        onOpNpc1(CHARLIE) { startDialogue(it.npc) { charlie() } }
    }

    private fun openShop(player: Player) {
        shops.open(
            player = player,
            title = SHOP_TITLE,
            shopInv = SHOP_INV,
            buyPercentage = BUY_PERCENTAGE,
            sellPercentage = SELL_PERCENTAGE,
            changePercentage = CHANGE_PERCENTAGE,
        )
    }

    private suspend fun Dialogue.alfonse() {
        val salutation = if (access.isBodyTypeA()) "sir" else "madam"
        chatNpc(neutral, "Welcome to the Shrimp and Parrot. Would you like to order, $salutation?")
        val gherkin = heroes.isPhoenix(player) && heroes.stage(player) == PHOENIX_BRIEFED
        val topic =
            if (gherkin) {
                choice4(
                    "Yes please.",
                    1,
                    "No thank you.",
                    2,
                    "Do you sell Gherkins?",
                    3,
                    "Where do you get your Karambwan from?",
                    4,
                )
            } else {
                choice3("Yes please.", 1, "No thank you.", 2, "Where do you get your Karambwan from?", 4)
            }
        when (topic) {
            1 -> {
                chatPlayer(happy, "Yes please.")
                openShop(player)
            }
            2 -> chatPlayer(neutral, "No thank you.")
            3 -> {
                chatPlayer(quiz, "Do you sell Gherkins?")
                chatNpc(
                    happy,
                    "Hmmmm Gherkins eh? Ask Charlie the cook, round the back. He may have some " +
                        "'gherkins' for you!",
                )
                heroes.setStage(access, PHOENIX_ALFONSE)
                mesbox("Alfonse winks at you.")
            }
            else -> karambwan()
        }
    }

    private suspend fun Dialogue.karambwan() {
        chatPlayer(quiz, "Where do you get your Karambwan from?")
        chatNpc(
            neutral,
            "We buy directly off Lubufu, a local fisherman. He seems to have a monopoly over " +
                "Karambwan sale.",
        )
        if (choice2("Where can I find Lubufu?", true, "How does he manage a monopoly?", false)) {
            chatPlayer(quiz, "Where can I find Lubufu?")
            chatNpc(neutral, "He is usually working just to the south of the town.")
        } else {
            chatPlayer(quiz, "How does he manage a monopoly?")
            chatNpc(
                neutral,
                "Simple - nobody else knows how to catch Karambwan. It is a closely guarded " +
                    "secret.",
            )
        }
        chatPlayer(neutral, "Thanks.")
    }

    private suspend fun Dialogue.charlie() {
        chatNpc(angry, "Hey! What are you doing back here?")
        val topic =
            choice3(
                "I'm looking for a gherkin...",
                1,
                "I'm a fellow member of the Phoenix gang.",
                2,
                "Just exploring...",
                3,
            )
        when (topic) {
            1 -> chatPlayer(neutral, "I'm looking for a gherkin...")
            2 -> chatPlayer(neutral, "I'm a fellow member of the Phoenix gang.")
            else -> {
                chatPlayer(neutral, "Just exploring...")
                chatNpc(
                    angry,
                    "Well get out! This kitchen isn't for exploring! It's a private " +
                        "establishment! It's out of bounds to customers!",
                )
                return
            }
        }
        if (!heroes.isPhoenix(player)) {
            chatNpc(
                angry,
                "I don't know what you're on about. This kitchen is out of bounds to customers!",
            )
            return
        }
        chatNpc(
            happy,
            "Aaaaaah... a fellow Phoenix! So, tell me compadre... what brings you to sunny " +
                "Brimhaven?",
        )
        val heist =
            choice2(
                "Sun, sand and the fresh sea air!",
                false,
                "I want to steal Scarface Pete's candlesticks.",
                true,
            )
        if (!heist) {
            chatPlayer(happy, "Sun, sand and the fresh sea air!")
            chatNpc(
                happy,
                "Well, can't say I blame you compadre. I used to be a city boy myself, but have " +
                    "to admit it's a lot nicer living here nowadays. Brimhaven's certainly good " +
                    "for it.",
            )
            return
        }
        chatPlayer(neutral, "I want to steal Scarface Pete's candlesticks.")
        chatNpc(
            neutral,
            "Ah yes, of course. The candlesticks. Well, I have to be honest with you compadre, we " +
                "haven't made much progress in that task ourselves so far. We can however offer",
        )
        chatNpc(
            neutral,
            "a little assistance. The setting up of this restaurant was the start of things; we " +
                "have a secret door out the back of here that leads through the back of Mr " +
                "Olbors' garden.",
        )
        if (heroes.stage(player) == PHOENIX_ALFONSE) {
            heroes.setStage(access, PHOENIX_CHARLIE)
        }
        chatNpc(
            neutral,
            "Now, at the other side of Mr Olbors' garden, is an old side entrance to Scarface " +
                "Pete's mansion. It seems to have been blocked off from the rest of the mansion " +
                "some years ago",
        )
        chatNpc(
            neutral,
            "and we can't seem to find a way through. We're positive this is the key to entering " +
                "the house undetected however, and I promise to let you know if we find anything " +
                "there.",
        )
        chatPlayer(quiz, "Mind if I check it out for myself?")
        chatNpc(
            happy,
            "Not at all! The more minds we have working on the problem, the quicker we get that " +
                "loot!",
        )
    }

    private companion object {
        const val SHOP_TITLE = "The Shrimp and Parrot"
        const val SHOP_INV = "inv.karamja_fishrestaurant"
        const val SELL_PERCENTAGE = 130.0
        const val BUY_PERCENTAGE = 75.0
        const val CHANGE_PERCENTAGE = 3.0
    }
}
