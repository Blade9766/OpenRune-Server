package org.rsmod.content.skills.hunter.guild

import dev.openrune.ServerCacheManager
import dev.openrune.definition.type.widget.IfEvent
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.aconverted.interf.IfButtonOp
import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.statBase
import org.rsmod.api.script.onIfModalButton
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.api.script.onOpNpc4
import org.rsmod.api.shops.Shops
import org.rsmod.content.skills.hunter.traps.TrapManager
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Imia's Supplies and Pellem's Fur Store in the Hunter Guild, plus Pellem's Custom Fur Clothing
 * interface. The clothing grid is built client-side from `enum.1559` structs, so button comsubs
 * index [FurClothing.entries] in the same order.
 */
class GuildShopsScript @Inject constructor(private val shops: Shops) : PluginScript() {
    override fun ScriptContext.startup() {
        onOpNpc1(IMIA) { talkToImia(it.npc) }
        onOpNpc3(IMIA) { if (hasGuildLevel()) openImia() }
        onOpNpc1(PELLEM) { talkToPellem(it.npc) }
        onOpNpc3(PELLEM) { if (hasGuildLevel()) openPellem() }
        onOpNpc4(PELLEM) { if (hasGuildLevel()) openFurClothing() }
        onIfModalButton(FUR_ITEMS) { button ->
            val clothing = FurClothing.entries.getOrNull(button.comsub) ?: return@onIfModalButton
            when (button.op) {
                IfButtonOp.Op1 -> value(clothing)
                IfButtonOp.Op2 -> make(clothing, 1)
                IfButtonOp.Op3 -> make(clothing, 5)
                IfButtonOp.Op4 -> make(clothing, 10)
                IfButtonOp.Op5 -> examine(clothing)
                else -> Unit
            }
        }
    }

    private suspend fun ProtectedAccess.talkToImia(npc: Npc) =
        startDialogue(npc) {
            if (!player.hasGuildLevel()) {
                chatNpc(neutral, GUILD_ONLY)
                return@startDialogue
            }
            chatNpc(happy, "Need some hunting supplies? I've got everything you could want.")
            val trade = choice2("Let's see what you have.", true, "No thanks.", false)
            if (trade) {
                access.openImia()
            }
        }

    private suspend fun ProtectedAccess.talkToPellem(npc: Npc) =
        startDialogue(npc) {
            if (!player.hasGuildLevel()) {
                chatNpc(neutral, GUILD_ONLY)
                return@startDialogue
            }
            chatNpc(
                happy,
                "Furs bought and sold! Bring me the right pelts and I'll make you some fine " +
                    "hunting clothes too.",
            )
            val choice =
                choice3(
                    "Let's see your furs.",
                    1,
                    "Can you make me some clothing?",
                    2,
                    "No thanks.",
                    3,
                )
            when (choice) {
                1 -> access.openPellem()
                2 -> access.openFurClothing()
            }
        }

    private fun ProtectedAccess.hasGuildLevel(): Boolean {
        if (player.hasGuildLevel()) {
            return true
        }
        mes("You need a Hunter level of $GUILD_LEVEL to use the Hunter Guild's shops.")
        return false
    }

    private fun Player.hasGuildLevel(): Boolean = statBase(TrapManager.STAT) >= GUILD_LEVEL

    private fun ProtectedAccess.openImia() {
        shops.open(player, "Imia's Supplies", IMIA_INV, BUY_PERCENT, SELL_PERCENT, CHANGE_PERCENT)
    }

    private fun ProtectedAccess.openPellem() {
        shops.open(player, "Pellem's Fur Store", PELLEM_INV, BUY_PERCENT, SELL_PERCENT, CHANGE_PERCENT)
    }

    private fun ProtectedAccess.openFurClothing() {
        ifOpenMainModal(FUR_INTERFACE)
        ifSetEvents(
            FUR_ITEMS,
            0 until FurClothing.entries.size,
            IfEvent.Op1,
            IfEvent.Op2,
            IfEvent.Op3,
            IfEvent.Op4,
            IfEvent.Op5,
        )
    }

    private fun ProtectedAccess.value(clothing: FurClothing) {
        val fur = objName(clothing.furs.first())
        mes(
            "${objName(clothing.product)}: ${clothing.furCount} x $fur and ${clothing.fee} coins."
        )
    }

    private fun ProtectedAccess.examine(clothing: FurClothing) {
        val type = ServerCacheManager.getItem(clothing.product.asRSCM(RSCMType.OBJ)) ?: return
        mes(type.examine)
    }

    private fun ProtectedAccess.make(clothing: FurClothing, amount: Int) {
        var made = 0
        while (made < amount) {
            val fur = clothing.furs.firstOrNull { inv.count(it) >= clothing.furCount }
            if (fur == null) {
                if (made == 0) {
                    mes("You don't have enough ${objName(clothing.furs.first())} to make that.")
                }
                break
            }
            if (inv.count(COINS) < clothing.fee) {
                if (made == 0) {
                    mes("You need ${clothing.fee} coins to have that made.")
                }
                break
            }
            invDel(inv, fur, clothing.furCount)
            invDel(inv, COINS, clothing.fee)
            invAdd(inv, clothing.product)
            made++
        }
    }

    private fun objName(obj: String): String =
        ServerCacheManager.getItem(obj.asRSCM(RSCMType.OBJ))?.name ?: obj

    private companion object {
        const val IMIA = "npc.hg_mixedhide_seller"
        const val PELLEM = "npc.hg_fur_trader"
        const val IMIA_INV = "inv.hunting_shop_guild"
        const val PELLEM_INV = "inv.fur_shop_guild"
        const val FUR_INTERFACE = "interface.hunting_customfurs"
        const val FUR_ITEMS = "component.hunting_customfurs:choices_items"
        const val COINS = "obj.coins"
        const val GUILD_LEVEL = 46
        const val SELL_PERCENT = 130.0
        const val BUY_PERCENT = 40.0
        const val CHANGE_PERCENT = 2.0
        const val GUILD_ONLY =
            "Sorry, I only serve members of the Hunter Guild. You'll need a Hunter level of " +
                "$GUILD_LEVEL."
    }
}
