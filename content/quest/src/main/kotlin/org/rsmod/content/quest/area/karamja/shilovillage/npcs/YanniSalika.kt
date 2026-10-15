package org.rsmod.content.quest.area.karamja.shilovillage.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpcU
import org.rsmod.content.quest.area.karamja.shilovillage.ShiloVillageQuest.Companion.BEADS_OF_THE_DEAD
import org.rsmod.content.quest.area.karamja.shilovillage.ShiloVillageQuest.Companion.BERVIRIUS_NOTES
import org.rsmod.content.quest.area.karamja.shilovillage.ShiloVillageQuest.Companion.BONE_KEY
import org.rsmod.content.quest.area.karamja.shilovillage.ShiloVillageQuest.Companion.COINS
import org.rsmod.content.quest.area.karamja.shilovillage.ShiloVillageQuest.Companion.CRUMPLED_SCROLL
import org.rsmod.content.quest.area.karamja.shilovillage.ShiloVillageQuest.Companion.LOCATING_CRYSTAL
import org.rsmod.content.quest.area.karamja.shilovillage.ShiloVillageQuest.Companion.STONE_PLAQUE
import org.rsmod.content.quest.area.karamja.shilovillage.ShiloVillageQuest.Companion.TATTERED_SCROLL
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** Yanni Salika, Shilo Village's antiques dealer, who buys the artefacts left over from the quest. */
class YanniSalika @Inject constructor() : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(YANNI) { startDialogue(it.npc) { talk() } }
        onOpNpcU(YANNI) { sell(it.npc, it.objType.internalName) }
    }

    private suspend fun Dialogue.talk() {
        chatPlayer(neutral, "Hello there!")
        chatNpc(
            happy,
            "Greetings Bwana! My name is Yanni and I buy and sell antiques and other interesting items. If you have " +
                "any interesting items that you might want to sell me, please let me see them and I'll offer you a fair price.",
        )
        chatNpc(quiz, "Would you like me to have a look at your items and give you a quote?")
        if (!choice2("Yes please!", true, "Maybe some other time?", false)) {
            chatPlayer(neutral, "Maybe some other time?")
            chatNpc(neutral, "Sure thing. Have a nice day Bwana.")
            return
        }
        chatPlayer(happy, "Yes please!")
        mesbox("Yanni looks through your items...")
        val offers = ARTEFACTS.filter { player.inv.contains(it.obj) }
        for (artefact in offers) {
            chatNpc(neutral, "I'll give you ${artefact.price} Gold for ${artefact.quoteName}.")
        }
        when (offers.size) {
            0 -> chatNpc(sad, "Sorry Bwana, you have nothing I am interested in.")
            1 -> chatNpc(neutral, "And that's the only item I am interested in. If you want to sell me that item, simply show it to me.")
            else -> chatNpc(neutral, "Those are the items I am interested in Bwana. If you want to sell me those items, simply show them to me.")
        }
    }

    private suspend fun ProtectedAccess.sell(yanni: Npc, obj: String) {
        val artefact = ARTEFACTS.firstOrNull { it.obj == obj }
        if (artefact == null) {
            startDialogue(yanni) { chatNpc(neutral, "I'm sorry Bwana but I just don't have a use for that!") }
            return
        }
        startDialogue(yanni) { chatNpc(happy, artefact.thanks) }
        val count = player.inv.count(obj)
        if (count <= 0) {
            return
        }
        invDel(inv, obj, count)
        invAdd(inv, COINS, artefact.price * count)
        mes("You sell ${artefact.soldName} for ${artefact.price * count} gold.")
    }

    private data class Artefact(
        val obj: String,
        val price: Int,
        val quoteName: String,
        val soldName: String,
        val thanks: String,
    )

    private companion object {
        const val YANNI = "npc.shiloantiques"

        val ARTEFACTS =
            listOf(
                Artefact(BONE_KEY, 100, "the Bone Key", "the bone key", "That's a great bone key. Here's 100 Gold for it."),
                Artefact(STONE_PLAQUE, 100, "the Stone Plaque", "the stone-plaque", "That's a great stone-plaque. Here's 100 Gold for it."),
                Artefact(TATTERED_SCROLL, 100, "the tattered scroll", "the tattered scroll", "That's a great tattered scroll. Here's 100 Gold for it."),
                Artefact(CRUMPLED_SCROLL, 100, "the crumpled scroll", "the crumpled scroll", "That's a great crumpled scroll. Here's 100 Gold for it."),
                Artefact(BERVIRIUS_NOTES, 100, "the Bervirius notes", "the Bervirius notes", "That's a great copy of Bervirius notes. Here's 100 Gold for it."),
                Artefact(LOCATING_CRYSTAL, 500, "your locating crystal", "the locating crystal", "That's a great Locating Crystal. Here's 500 Gold for it."),
                Artefact(BEADS_OF_THE_DEAD, 1000, "your 'Beads of the Dead'", "the 'Beads of the Dead'", "Impressive necklace there, here's 1000 Gold for it."),
            )
    }
}
