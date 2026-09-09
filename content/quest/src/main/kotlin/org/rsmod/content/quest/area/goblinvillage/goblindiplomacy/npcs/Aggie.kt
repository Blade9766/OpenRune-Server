package org.rsmod.content.quest.area.goblinvillage.goblindiplomacy.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.content.quest.area.goblinvillage.goblindiplomacy.GoblinDiplomacyQuest
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Aggie, the witch of Draynor Village, who makes red, yellow and blue dye for a few ingredients
 * and five coins. She is a multi-npc: after her first conversation `varbit.gobdip_met_aggie`
 * gives her a `Dyes` option that skips straight to business.
 */
class Aggie @Inject constructor(private val goblinDiplomacy: GoblinDiplomacyQuest) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(AGGIE) { startDialogue(it.npc) { aggie() } }
        onOpNpc3(AGGIE) { startDialogue(it.npc) { dyesMenu() } }
    }

    private suspend fun Dialogue.aggie() {
        if (!goblinDiplomacy.metAggie.get(player)) {
            goblinDiplomacy.metAggie.set(player, true)
            goblinDiplomacy.syncVars(player)
        }
        chatNpc(quiz, "What can I help you with?")
        when (
            choice4(
                "What could you make for me?", 1,
                "Cool, do you turn people into frogs?", 2,
                "You mad old witch, you can't help me.", 3,
                "Can you make dyes for me please?", 4,
            )
        ) {
            1 -> {
                chatPlayer(quiz, "What could you make for me?")
                chatNpc(happy, "I mostly just make what I find pretty. I sometimes make dye for the women's clothes to brighten the place up. I can make red, yellow and blue dyes. If you'd like some, just bring me the appropriate ingredients.")
                colourMenu(withDecline = true)
            }
            2 -> {
                chatPlayer(quiz, "Cool, do you turn people into frogs?")
                chatNpc(neutral, "Oh, not for years, but if you meet a talking chicken, you have probably met the professor in the manor north of here. A few years ago it was flying fish. That machine is a menace.")
            }
            3 -> insult()
            4 -> dyesMenu()
        }
    }

    private suspend fun Dialogue.dyesMenu() {
        chatPlayer(quiz, "Can you make dyes for me please?")
        chatNpc(quiz, "What sort of dye would you like? Red, yellow or blue?")
        colourMenu(withDecline = false)
    }

    private suspend fun Dialogue.colourMenu(withDecline: Boolean) {
        val choice =
            if (withDecline) {
                choice4(
                    "What do you need to make red dye?", 1,
                    "What do you need to make yellow dye?", 2,
                    "What do you need to make blue dye?", 3,
                    "No thanks, I am happy the colour I am.", 4,
                )
            } else {
                choice3(
                    "What do you need to make red dye?", 1,
                    "What do you need to make yellow dye?", 2,
                    "What do you need to make blue dye?", 3,
                )
            }
        when (choice) {
            1 -> dye(RED)
            2 -> dye(YELLOW)
            3 -> dye(BLUE)
            4 -> {
                chatPlayer(neutral, "No thanks, I am happy the colour I am.")
                chatNpc(neutral, "You are easily pleased with yourself then. When you need dyes, come to me.")
            }
        }
    }

    private suspend fun Dialogue.dye(recipe: Recipe) {
        chatPlayer(quiz, "What do you need to make ${recipe.colour} dye?")
        chatNpc(neutral, recipe.price)
        when (
            choice4(
                "Okay, make me some ${recipe.colour} dye please.", 1,
                "I don't think I have all the ingredients yet.", 2,
                "I can do without dye at that price.", 3,
                "Where do I get ${recipe.ingredientName}?", 4,
            )
        ) {
            1 -> {
                chatPlayer(happy, "Okay, make me some ${recipe.colour} dye please.")
                make(recipe)
            }
            2 -> {
                chatPlayer(neutral, "I don't think I have all the ingredients yet.")
                chatNpc(neutral, "You know what you need to get, now come back when you have them. Goodbye for now.")
            }
            3 -> {
                chatPlayer(neutral, "I can do without dye at that price.")
                chatNpc(shifty, "That's your choice, but I would think you have killed for less. I can see it in your eyes.")
            }
            4 -> {
                chatPlayer(quiz, "Where do I get ${recipe.ingredientName}?")
                chatNpc(neutral, recipe.source)
                when (
                    choice2(
                        "What other colours can you make?", 1,
                        "Thanks", 2,
                    )
                ) {
                    1 -> {
                        chatPlayer(quiz, "What other colours can you make?")
                        chatNpc(neutral, "Red, yellow and blue. Which one would you like?")
                        colourMenu(withDecline = false)
                    }
                    2 -> {
                        chatPlayer(happy, "Thanks.")
                        chatNpc(happy, "You're welcome!")
                    }
                }
            }
        }
    }

    private suspend fun Dialogue.make(recipe: Recipe) {
        if (player.inv.count(recipe.ingredient) < recipe.count) {
            mesbox(recipe.lacking)
            return
        }
        if (player.inv.count(COINS) < DYE_PRICE) {
            mesbox("You don't have enough coins to pay for the dye.")
            return
        }
        if (access.invDel(access.inv, recipe.ingredient, recipe.count).failure) {
            return
        }
        access.invDel(access.inv, COINS, DYE_PRICE)
        access.invAdd(access.inv, recipe.dye)
        objbox(recipe.dye, "Aggie hands you a bottle of ${recipe.colour} dye.")
    }

    /** Calling a witch names has a price. */
    private suspend fun Dialogue.insult() {
        chatPlayer(angry, "You mad old witch, you can't help me.")
        val coins = player.inv.count(COINS)
        when {
            coins in 21..100 -> fine(COINS, 5, "Aggie waves her hands about, and you seem to be five coins poorer.")
            coins > 100 -> fine(COINS, 20, "Aggie waves her hands about, and you seem to be 20 coins poorer.")
            player.inv.count(FLOUR) > 0 -> {
                chatNpc(angry, "Oh, you like to call a witch names do you?")
                access.invDel(access.inv, FLOUR)
                objbox(FLOUR, "Aggie waves her hands about, and you seem to have a pot of flour less.")
                chatNpc(happy, "Thank you for your kind present of some flour. I am sure you never meant to insult me.")
            }
            else -> chatNpc(angry, "You should be careful about insulting a witch. You never know what shape you could wake up in.")
        }
    }

    private suspend fun Dialogue.fine(obj: String, amount: Int, text: String) {
        chatNpc(angry, "Oh, you like to call a witch names do you?")
        access.invDel(access.inv, obj, amount)
        objbox(obj, text)
        chatNpc(angry, "That's a fine for insulting a witch. You should learn some respect.")
    }

    private class Recipe(
        val colour: String,
        val dye: String,
        val ingredient: String,
        val ingredientName: String,
        val count: Int,
        val price: String,
        val lacking: String,
        val source: String,
    )

    private companion object {
        /** The multi-npc in the map; her forms are `aggie_1op` and `aggie_2ops`. */
        const val AGGIE = "npc.aggie"

        const val COINS = "obj.coins"
        const val FLOUR = "obj.flour"
        const val DYE_PRICE = 5

        val RED =
            Recipe(
                colour = "red",
                dye = "obj.reddye",
                ingredient = "obj.redberries",
                ingredientName = "redberries",
                count = 3,
                price = "3 lots of redberries and 5 coins to you.",
                lacking = "You don't have enough berries to make the red dye.",
                source = "I pick mine from the woods south of Varrock. The food shop in Port Sarim sometimes has some as well.",
            )
        val YELLOW =
            Recipe(
                colour = "yellow",
                dye = "obj.yellowdye",
                ingredient = "obj.onion",
                ingredientName = "onions",
                count = 2,
                price = "Yellow is a strange colour to get, comes from onion skins. I need 2 onions and 5 coins to make yellow dye.",
                lacking = "You don't have enough onions to make the yellow dye.",
                source = "There are some onions growing on a farm to the East of here, next to the sheep field.",
            )
        val BLUE =
            Recipe(
                colour = "blue",
                dye = "obj.bluedye",
                ingredient = "obj.woadleaf",
                ingredientName = "woad leaves",
                count = 2,
                price = "2 woad leaves and 5 coins to you.",
                lacking = "You don't have enough woad leaves to make the blue dye.",
                source = "Woad leaves are fairly hard to find. My other customers tell me the chief gardener in Falador grows them.",
            )
    }
}
