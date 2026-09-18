package org.rsmod.content.skills.fletching.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.baseFletchingLvl
import org.rsmod.api.player.stat.statBase
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.api.shops.Shops
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class HicktonScript @Inject constructor(private val shops: Shops) : PluginScript() {
    override fun ScriptContext.startup() {
        onOpNpc1("npc.hickton") { talk(it.npc) }
        onOpNpc3("npc.hickton") { player.openArcheryShop(it.npc) }
    }

    private suspend fun ProtectedAccess.talk(npc: Npc) = startDialogue(npc) { greeting(npc) }

    private suspend fun Dialogue.greeting(npc: Npc) {
        chatNpc(happy, "Welcome to Hickton's Archery Store. Do you want to see my wares?")
        val choice =
            choice3(
                "Can you tell me about your cape?",
                1,
                "Yes, please.",
                2,
                "No, I prefer to bash things close up.",
                3,
            )
        when (choice) {
            1 -> aboutCape(npc)
            2 -> player.openArcheryShop(npc)
            3 -> chatPlayer(neutral, "No, I prefer to bash things close up.")
        }
    }

    private suspend fun Dialogue.aboutCape(npc: Npc) {
        chatPlayer(quiz, "Can you tell me about your cape?")
        chatNpc(
            happy,
            "Certainly! Skillcapes are a symbol of achievement. Only people who have mastered a " +
                "skill and reached level 99 can get their hands on them and gain the benefits they " +
                "carry.",
        )
        chatNpc(
            happy,
            "The Cape of Fletching can be searched for a mithril grapple and crossbow three times " +
                "every day. Is there anything else I can help you with?",
        )
        when (choice2("I'd like to view your store please.", 1, "No thank you.", 2)) {
            1 -> player.openArcheryShop(npc)
            2 -> chatPlayer(neutral, "No thank you.")
        }
    }

    private fun Player.openArcheryShop(npc: Npc) {
        val inv =
            when {
                baseFletchingLvl < MAX_LEVEL -> SHOP
                maxedStats() > 1 -> SHOP_TRIMMED_CAPE
                else -> SHOP_CAPE
            }
        shops.open(this, npc, SHOP_NAME, inv)
    }

    private fun Player.maxedStats(): Int = STATS.count { statBase(it) >= MAX_LEVEL }

    private companion object {
        private const val SHOP_NAME = "Hickton's Archery Emporium"
        private const val SHOP = "inv.archeryshop2"
        private const val SHOP_CAPE = "inv.archeryshop2_skillcape"
        private const val SHOP_TRIMMED_CAPE = "inv.archeryshop2_skillcape_trimmed"
        private const val MAX_LEVEL = 99

        private val STATS =
            listOf(
                "stat.attack", "stat.defence", "stat.strength", "stat.hitpoints", "stat.ranged",
                "stat.prayer", "stat.magic", "stat.cooking", "stat.woodcutting", "stat.fletching",
                "stat.fishing", "stat.firemaking", "stat.crafting", "stat.smithing", "stat.mining",
                "stat.herblore", "stat.agility", "stat.thieving", "stat.slayer", "stat.farming",
                "stat.runecrafting", "stat.hunter", "stat.construction",
            )
    }
}
