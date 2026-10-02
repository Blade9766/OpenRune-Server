package org.rsmod.content.quest.area.tirannwn.mourningsend.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.api.script.onOpNpcU
import org.rsmod.api.shops.Shops
import org.rsmod.content.quest.area.tirannwn.mourningsend.MourningsEndQuest
import org.rsmod.content.quest.area.tirannwn.mourningsend.MourningsEndQuest.Companion.BEAR_FUR
import org.rsmod.content.quest.area.tirannwn.mourningsend.MourningsEndQuest.Companion.MOURNER_LEGS
import org.rsmod.content.quest.area.tirannwn.mourningsend.MourningsEndQuest.Companion.RIPPED_LEGS
import org.rsmod.content.quest.area.tirannwn.mourningsend.MourningsEndQuest.Companion.SILK
import org.rsmod.content.quest.area.tirannwn.mourningsend.MourningsEndQuest.Companion.STAGE_BRIEFED
import org.rsmod.content.quest.area.tirannwn.mourningsend.firstSilk
import org.rsmod.content.quest.area.tirannwn.mourningsend.furGiven
import org.rsmod.content.quest.area.tirannwn.mourningsend.secondSilk
import org.rsmod.content.quest.area.tirannwn.mourningsend.swap
import org.rsmod.content.quest.area.tirannwn.mourningsend.trousersShown
import org.rsmod.content.quest.manager.menu
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Oronwen, Lletya's seamstress. Besides her shop she mends the ripped mourner trousers for two
 * pieces of silk and a bear fur. The materials can be handed over one at a time by using them on
 * her; each one she holds is a cache varbit (`mourning_silk_1`, `mourning_silk_2`,
 * `mourning_fur`), so a half-paid repair survives logging out. The mending itself is immediate and
 * one transaction: the ripped trousers and whatever materials she still needs go, the mended pair
 * comes back, and only then are her held materials cleared, so nothing is taken twice and a
 * repair is never handed out without its trousers.
 */
class Oronwen
@Inject
constructor(private val mourning: MourningsEndQuest, private val shops: Shops) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(ORONWEN) { startDialogue(it.npc) { oronwen(it.npc) } }
        onOpNpc3(ORONWEN) { openShop(it.npc) }
        onOpNpcU(ORONWEN) { useOn(it.npc, it.objType.internalName) }
    }

    private fun ProtectedAccess.openShop(npc: Npc) {
        shops.open(player, npc, SHOP_TITLE, SHOP_INV)
    }

    private fun mends(player: Player): Boolean =
        mourning.stage(player) >= STAGE_BRIEFED || mourning.isComplete(player)

    private suspend fun Dialogue.oronwen(npc: Npc) {
        chatNpc(happy, "Hello, can I help?")
        val ripped = player.inv.contains(RIPPED_LEGS)
        if (mends(player) && ripped && player.trousersShown == 0) {
            firstLook()
            return
        }
        val options = mutableListOf("Yes please. What are you selling?" to SHOP, "No thanks." to NOTHING)
        if (mends(player) && player.trousersShown == 1 && ripped) {
            if (hasEverything(player)) {
                options += "I have all I need to mend my trousers." to MEND
            } else {
                options += "What do you need to mend my trousers?" to ASK
            }
        }
        when (menu(options)) {
            SHOP -> {
                chatPlayer(neutral, "Yes please. What are you selling?")
                access.openShop(npc)
            }
            NOTHING -> chatPlayer(neutral, "No thanks.")
            ASK -> {
                chatPlayer(quiz, "What do you need to mend my trousers?")
                chatNpc(neutral, stillNeeded(player))
                chatPlayer(neutral, "Alright.")
            }
            MEND -> {
                chatPlayer(happy, "I have all I need to mend my trousers.")
                chatNpc(neutral, "Pass it all here then, and I will get started.")
                mend()
            }
        }
    }

    private suspend fun Dialogue.firstLook() {
        chatPlayer(quiz, "Do you mend clothes?")
        chatNpc(neutral, "I do, but human clothes are hard work. They lack the finesse of elven garments.")
        chatPlayer(neutral, "As it happens, it's elven clothing I need repaired.")
        chatNpc(neutral, "Let me take a look then.")
        objbox(RIPPED_LEGS, 400, "You show the seamstress the mourner trousers.")
        chatNpc(worried, "There is something uncomfortably familiar about the cut of these. They were made for an elf.")
        chatPlayer(quiz, "But can you fix them?")
        MourningsEndQuest.setVarBit(player, "varbit.mourning_trousers_chat", 1)
        chatNpc(neutral, "Of course I can, but I will need two pieces of silk and some bear fur.")
        if (!hasEverything(player)) {
            return
        }
        chatPlayer(happy, "Great, I have those here.")
        chatNpc(laugh, "Huh, it's almost as if you knew what I needed.")
        mend()
    }

    private suspend fun Dialogue.mend() {
        val take = mutableListOf(RIPPED_LEGS to 1)
        silkNeeded(player).takeIf { it > 0 }?.let { take += SILK to it }
        if (player.furGiven == 0) {
            take += BEAR_FUR to 1
        }
        if (!access.swap(take, listOf(MOURNER_LEGS to 1))) {
            return
        }
        for (varbit in HELD_MATERIALS) {
            MourningsEndQuest.setVarBit(player, varbit, 0)
        }
        MourningsEndQuest.setVarBit(player, "varbit.mourning_trousers_fixed", 1)
        objbox(MOURNER_LEGS, 400, "Oronwen hands you the mourner trousers. They look as good as new.")
        chatPlayer(happy, "Thanks a lot.")
        chatNpc(happy, "Any time.")
    }

    private suspend fun ProtectedAccess.useOn(npc: Npc, obj: String) {
        val accepting = mends(player) && player.trousersShown == 1 && ownsRipped(player)
        when {
            obj == SILK && accepting && silkNeeded(player) > 0 -> {
                if (invDel(inv, SILK, 1).failure) {
                    return
                }
                val bit = if (player.firstSilk == 0) "varbit.mourning_silk_1" else "varbit.mourning_silk_2"
                MourningsEndQuest.setVarBit(player, bit, 1)
                startDialogue(npc) {
                    objbox(SILK, "You give the silk to Oronwen.")
                    chatNpc(neutral, stillNeeded(player))
                }
            }
            obj == BEAR_FUR && accepting && player.furGiven == 0 -> {
                if (invDel(inv, BEAR_FUR, 1).failure) {
                    return
                }
                MourningsEndQuest.setVarBit(player, "varbit.mourning_fur", 1)
                startDialogue(npc) {
                    objbox(BEAR_FUR, "You give the bear fur to Oronwen.")
                    chatNpc(neutral, stillNeeded(player))
                }
            }
            obj == SILK || obj == BEAR_FUR -> mes("Oronwen doesn't need that from you right now.")
            else -> mes("Nothing interesting happens.")
        }
    }

    private fun ownsRipped(player: Player): Boolean = player.inv.contains(RIPPED_LEGS)

    companion object {
        const val ORONWEN = "npc.mourning_seamstress"
        const val SHOP_TITLE = "Lletya Seamstress"
        const val SHOP_INV = "inv.lletyaseamstressshop1"

        private const val SHOP = 1
        private const val NOTHING = 2
        private const val ASK = 3
        private const val MEND = 4

        val HELD_MATERIALS = listOf("varbit.mourning_silk_1", "varbit.mourning_silk_2", "varbit.mourning_fur")

        fun silkNeeded(player: Player): Int = 2 - player.firstSilk - player.secondSilk

        fun hasEverything(player: Player): Boolean =
            player.inv.count(SILK) >= silkNeeded(player) && (player.furGiven == 1 || player.inv.contains(BEAR_FUR))

        fun stillNeeded(player: Player): String {
            val silk = silkNeeded(player)
            val fur = player.furGiven == 0
            return when {
                silk == 0 && !fur -> "That's everything. Talk to me and I'll mend them."
                silk == 0 -> "I only need bear fur now."
                !fur && silk == 2 -> "I need two pieces of silk."
                !fur -> "I only need one piece of silk."
                silk == 2 -> "I need bear fur and two pieces of silk."
                else -> "I need bear fur and one piece of silk."
            }
        }
    }
}
