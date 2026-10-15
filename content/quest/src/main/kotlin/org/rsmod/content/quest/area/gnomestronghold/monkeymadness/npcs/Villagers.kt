package org.rsmod.content.quest.area.gnomestronghold.monkeymadness.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.api.shops.Shops
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.Greegree
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.MonkeyMadnessQuest.Companion.AMULET
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.MonkeyMadnessQuest.Companion.DENADU
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.MonkeyMadnessQuest.Companion.HAFUBA
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.MonkeyMadnessQuest.Companion.LOFU
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.MonkeyMadnessQuest.Companion.MUROWOI
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.MonkeyMadnessQuest.Companion.PADULAH
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.MonkeyMadnessQuest.Companion.UWOGO
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The monkeys of Marim who are neither guards nor royalty: the five stall keepers with their shops
 * and the townsfolk with a line or two. None of them will deal with anything that isn't a monkey
 * wearing a M'speak amulet.
 */
class Villagers @Inject constructor(private val greegree: Greegree, private val shops: Shops) : PluginScript() {

    private class Shopkeeper(val npc: String, val title: String, val inv: String, val greeting: String)

    override fun ScriptContext.startup() {
        for (keeper in SHOPKEEPERS) {
            onOpNpc1(keeper.npc) { keeperTalk(it.npc, keeper) }
            onOpNpc3(keeper.npc) { openShop(it.npc, keeper) }
        }
        onOpNpc1(UWOGO) { villagerTalk(it.npc, "The King has been in a strange mood since the human ships came. Strange, and cruel.") }
        onOpNpc1(MUROWOI) { villagerTalk(it.npc, "Guarding the palace door is dull work. Nobody ever tries to get in except Kruk, and he's allowed.") }
        onOpNpc1(LOFU) { villagerTalk(it.npc, "Have you seen the view from up here? You can see all the way to the little island where the small folk crashed.") }
        onOpNpc1(DENADU) { villagerTalk(it.npc, "Oook. If the King wants the gnomes gone, he could just ask them to leave. Nobody asked me.") }
        onOpNpc1(HAFUBA) { villagerTalk(it.npc, "Marimbo watches over us all. Even the ones who don't pray.") }
        onOpNpc1(PADULAH) { villagerTalk(it.npc, "A monkey I don't know, in Marim? Kruk will want to hear about you.") }
    }

    private fun ProtectedAccess.understands(): Boolean = greegree.isMonkey(player) && player.worn.contains(AMULET)

    private suspend fun ProtectedAccess.keeperTalk(npc: Npc, keeper: Shopkeeper) {
        if (!understands()) {
            startDialogue(npc) { chatNpc(neutral, "Ook ook eek?") }
            mes("The stall keeper chatters at you and waves you away.")
            return
        }
        startDialogue(npc) {
            chatNpc(happy, keeper.greeting)
            when (choice2("Yes, show me what you have.", 1, "No thanks.", 2)) {
                1 -> {
                    chatPlayer(happy, "Yes, show me what you have.")
                    shops.open(player, npc, keeper.title, keeper.inv)
                }
                2 -> chatPlayer(neutral, "No thanks.")
            }
        }
    }

    private fun ProtectedAccess.openShop(npc: Npc, keeper: Shopkeeper) {
        if (!understands()) {
            mes("The stall keeper won't trade with something that can't even say 'Ook'.")
            return
        }
        shops.open(player, npc, keeper.title, keeper.inv)
    }

    private suspend fun ProtectedAccess.villagerTalk(npc: Npc, line: String) {
        if (!understands()) {
            startDialogue(npc) { chatNpc(neutral, "Ook. Ook ook.") }
            return
        }
        startDialogue(npc) { chatNpc(neutral, line) }
    }

    private companion object {
        val SHOPKEEPERS =
            listOf(
                Shopkeeper("npc.mm_solihib", "Solihib's Food Stall", "inv.mm_food_shop", "Bananas! Fresh bananas! And a few other things, if you must. Want to see?"),
                Shopkeeper("npc.mm_daga", "Daga's Scimitar Smithy", "inv.mm_scimitar_shop", "Finest scimitars on the island. Even a dragon one, for those who can pay. Want a look?"),
                Shopkeeper("npc.mm_tutab", "Tutab's Magical Market", "inv.mm_magic_shop", "Runes, talismans and things that glow. Want to see?"),
                Shopkeeper("npc.mm_ifaba", "Ifaba's General Store", "inv.mm_general_shop", "A little bit of everything at Ifaba's. Want to see?"),
                Shopkeeper("npc.mm_hamab", "Hamab's Crafting Emporium", "inv.mm_crafting_shop", "Needles, thread, moulds: everything a crafty monkey needs. Want to see?"),
            )
    }
}
