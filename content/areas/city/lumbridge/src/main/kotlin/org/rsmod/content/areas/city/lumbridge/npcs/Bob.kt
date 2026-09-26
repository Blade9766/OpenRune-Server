package org.rsmod.content.areas.city.lumbridge.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.dialogue.mesanims
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.advanced.onUnimplementedOpNpc4
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.api.shops.Shops
import org.rsmod.content.quest.area.lumbridge.losttribe.LostTribeQuest
import org.rsmod.content.quest.area.lumbridge.losttribe.LostTribeQuest.Witness
import org.rsmod.content.quest.manager.menu
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class Bob @Inject constructor(private val shops: Shops, private val lostTribe: LostTribeQuest) :
    PluginScript() {
    override fun ScriptContext.startup() {
        onOpNpc1("npc.bob") { startDialogue(it.npc) }
        onOpNpc3("npc.bob") { player.openShop(it.npc) }
        onUnimplementedOpNpc4("npc.bob") { repairOp(it.npc) }
    }

    private fun Player.openShop(npc: Npc) {
        shops.open(this, npc, "Bob's Brilliant Axes", "inv.axeshop")
    }

    private suspend fun ProtectedAccess.startDialogue(npc: Npc) {
        startDialogue(npc) { bobDialogue(npc) }
    }

    private suspend fun Dialogue.bobDialogue(npc: Npc) {
        val options = buildList {
            lostTribe.cellarQuestion(player, Witness.Bob)?.let { add(it to 4) }
            add("Give me a quest!" to 1)
            add("Have you anything to sell?" to 2)
            add("Can you repair my items for me?" to 3)
        }
        val choice = menu(options)
        when (choice) {
            1 -> requestQuest()
            2 -> openShop(npc)
            3 -> repairItems()
            4 -> with(lostTribe) { askAboutCellar(Witness.Bob) }
        }
    }

    private suspend fun Dialogue.requestQuest() {
        chatPlayer(happy, "Give me a quest!")
        chatNpc(angry, "Get yer own!")
    }

    private suspend fun Dialogue.openShop(npc: Npc) {
        chatPlayer(quiz, "Have you anything to sell?")
        chatNpc(happy, "Yes! I buy and sell axes! Take your pick (or axe)!")
        player.openShop(npc)
    }

    private suspend fun Dialogue.repairItems() {
        chatPlayer(sad, "Can you repair my items for me?")
        chatNpc(
            shocked,
            "Of course I'll repair it, though the materials may cost " +
                "you. Just hand me the item and I'll have a look.",
        )
    }

    private suspend fun ProtectedAccess.repairOp(npc: Npc) {
        chatNpc(npc, mesanims.confused, "You don't have anything I can repair.")
    }
}
