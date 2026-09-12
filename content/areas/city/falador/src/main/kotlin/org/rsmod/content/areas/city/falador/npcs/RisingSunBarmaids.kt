package org.rsmod.content.areas.city.falador.npcs

import jakarta.inject.Inject
import org.rsmod.api.invtx.invAddOrDrop
import org.rsmod.api.invtx.invTakeFee
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.output.spam
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onApNpc1
import org.rsmod.api.script.onOpNpc1
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Emily, Kaylee and Tina, the barmaids of the Rising Sun Inn in Falador. Emily and Kaylee serve
 * from behind the bar counter, which the route finder cannot cross, so the conversation also
 * starts from a couple of tiles away (the counter does not block line of sight).
 */
class RisingSunBarmaids @Inject constructor(private val objRepo: ObjRepository) : PluginScript() {

    private class Drink(val obj: String, val name: String, val price: Int)

    private val drinks =
        listOf(
            Drink("obj.asgarnian_ale", "Asgarnian ale", DRINK_PRICE),
            Drink("obj.wizards_mind_bomb", "Wizard's mind bomb", DRINK_PRICE),
            Drink("obj.dwarven_stout", "Dwarven stout", DRINK_PRICE),
        )

    override fun ScriptContext.startup() {
        for (barmaid in BARMAIDS) {
            onOpNpc1(barmaid) { startDialogue(it.npc) { barmaid(it.npc) } }
            onApNpc1(barmaid) {
                if (isWithinApRange(it.npc, TALK_RANGE)) {
                    startDialogue(it.npc) { barmaid(it.npc) }
                }
            }
        }
    }

    private suspend fun Dialogue.barmaid(npc: Npc) {
        chatNpc(happy, "Heya! What can I get you?")
        when (
            choice3(
                "What ales are you serving?", 1,
                "Heard any rumours recently?", 2,
                "Nothing, thanks.", 3,
            )
        ) {
            1 -> ales()
            2 -> {
                chatPlayer(quiz, "Heard any rumours recently?")
                chatNpc(neutral, "Only that the White Knights are hiring again. Half the regulars in here reckon they'll be knighted by the end of the month.")
                chatNpc(laugh, "The other half reckon they'll fall off the castle walls first.")
            }
            3 -> chatPlayer(neutral, "Nothing, thanks.")
        }
    }

    private suspend fun Dialogue.ales() {
        chatPlayer(quiz, "What ales are you serving?")
        chatNpc(happy, "Well, we've got ${drinks[0].name}, ${drinks[1].name} and ${drinks[2].name}, all for only $DRINK_PRICE coins each.")
        val pick =
            choice4(
                "One ${drinks[0].name}, please.", 0,
                "One ${drinks[1].name}, please.", 1,
                "One ${drinks[2].name}, please.", 2,
                "I'll not have anything, thanks.", 3,
            )
        if (pick !in drinks.indices) {
            chatPlayer(neutral, "I'll not have anything, thanks.")
            return
        }
        val drink = drinks[pick]
        chatPlayer(happy, "One ${drink.name}, please.")
        chatNpc(happy, "That'll be ${drink.price} coins, please.")
        if (!player.invTakeFee(fee = drink.price)) {
            chatPlayer(sad, "Oh dear, I don't seem to have enough money.")
            return
        }
        player.spam("You buy a ${drink.name.lowercase()}.")
        player.invAddOrDrop(objRepo, drink.obj)
    }

    private companion object {
        /** Emily and Kaylee downstairs, Tina upstairs. */
        val BARMAIDS = listOf("npc.risingsun_barmaid", "npc.risingsun_barmaid2", "npc.risingsun_barmaid3")

        const val DRINK_PRICE = 3

        /** Across the bar counter and one tile of slack. */
        const val TALK_RANGE = 2
    }
}
