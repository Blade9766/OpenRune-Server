package org.rsmod.content.interfaces.omnishop

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ItemServerType
import dev.openrune.types.aconverted.interf.IfButtonOp
import org.rsmod.api.invtx.add
import org.rsmod.api.invtx.delete
import org.rsmod.api.invtx.invTransaction
import org.rsmod.api.invtx.select
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.ui.IfScriptArgs
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.script.onIfModalButton
import org.rsmod.api.script.onIfScriptTrigger
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class OmnishopScript : PluginScript() {
    override fun ScriptContext.startup() {
        onIfScriptTrigger<InfoArgs>("component.omnishop_main:trigger_request_info") {
            sendInfo(it.shop, it.index)
        }
        onIfScriptTrigger<BuyArgs>("component.omnishop_main:trigger_buy") {
            buy(it.shop, it.index, it.option)
        }
        onIfScriptTrigger<ExamineArgs>("component.omnishop_main:trigger_examine") {
            val obj = ServerCacheManager.getItem(it.obj) ?: return@onIfScriptTrigger
            mes(obj.examine)
        }
        onIfModalButton("component.omnishop_side:items") { button ->
            val obj = button.obj ?: return@onIfModalButton
            when (button.op) {
                IfButtonOp.Op2 -> sell(obj, 1)
                IfButtonOp.Op3 -> sell(obj, 5)
                IfButtonOp.Op4 -> sell(obj, 10)
                IfButtonOp.Op5 -> sell(obj, 50)
                IfButtonOp.Op10 -> mes(obj.examine)
                else -> Unit
            }
        }
    }

    private fun ProtectedAccess.sendInfo(shop: Int, index: Int) {
        val stock = OmnishopStock.find(shop, index) ?: return
        player.omnishopSelectedId = index
        runClientScript(INFO_UPDATE.asRSCM(RSCMType.CLIENTSCRIPT), shop, -1, index, stock.description(), 1, 0, 1)
    }

    private fun ProtectedAccess.buy(shop: Int, index: Int, option: Int) {
        if (shop != player.omnishopLastShop) return
        val stock = OmnishopStock.find(shop, index) ?: return
        if (!stock.buyable) return
        var quantity = BUY_QUANTITIES.getOrNull(option - 1) ?: return
        val costs = stock.buyCosts().filter { it.second > 0 }
        for ((currency, price) in costs) {
            val affordable = currencyCount(currency) / price
            if (affordable == 0) {
                mes("You don't have enough ${currency.pluralName}.")
                return
            }
            quantity = minOf(quantity, affordable)
        }
        if (!stock.obj.stackable) {
            quantity = minOf(quantity, inv.freeSpace() / stock.multiplier)
        } else if (inv.physicalCount(stock.obj.internalName) == 0 && inv.isFull()) {
            quantity = 0
        }
        if (quantity <= 0) {
            mes("You don't have enough inventory space.")
            return
        }
        val count = quantity.toLong() * stock.multiplier
        if (count > Int.MAX_VALUE) {
            mes("You don't have enough inventory space.")
            return
        }
        val objDebits = ArrayList<Pair<Int, Int>>()
        for ((currency, price) in costs) {
            if (currency.varp != null) continue
            var remaining = price * quantity
            for (obj in currency.objs) {
                val take = minOf(remaining, inv.physicalCount(obj.internalName))
                if (take > 0) {
                    objDebits += obj.id to take
                    remaining -= take
                }
            }
            if (remaining > 0) {
                mes("You don't have enough ${currency.pluralName}.")
                return
            }
        }
        val result =
            player.invTransaction(inv) {
                val image = select(inv)
                for ((obj, take) in objDebits) {
                    delete(image, obj, take)
                }
                add(image, stock.obj.id, count.toInt())
            }
        if (result.failure) {
            mes("You don't have enough inventory space.")
            return
        }
        for ((currency, price) in costs) {
            val varp = currency.varp ?: continue
            VarPlayerIntMapSetter.set(player, varp, player.vars[varp] - price * quantity)
        }
    }

    private fun ProtectedAccess.sell(obj: ItemServerType, requested: Int) {
        val shop = player.omnishopLastShop
        val stock = OmnishopStock.all(shop).firstOrNull { it.obj.id == obj.id }
        if (stock == null || !stock.sellable) {
            mes("You can't sell this item to this shop.")
            return
        }
        var quantity = minOf(requested, inv.physicalCount(obj.internalName))
        if (quantity == 0) return
        val prices = stock.sellCosts().filter { it.second > 0 }
        for ((currency, price) in prices) {
            val room = Int.MAX_VALUE.toLong() - heldPayoutCount(currency)
            val fits = (room / price).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
            if (fits == 0) {
                mes("You can't hold any more ${currency.pluralName}.")
                return
            }
            quantity = minOf(quantity, fits)
        }
        val payouts = prices.map { (currency, price) -> currency to price * quantity }
        val objCredits =
            payouts.mapNotNull { (currency, amount) ->
                if (currency.varp != null) return@mapNotNull null
                currency.objs.firstOrNull()?.let { it.id to amount }
            }
        val result =
            player.invTransaction(inv) {
                val image = select(inv)
                delete(image, obj.id, quantity)
                for ((currencyObj, amount) in objCredits) {
                    add(image, currencyObj, amount)
                }
            }
        if (result.failure) {
            mes("You don't have enough inventory space.")
            return
        }
        for ((currency, amount) in payouts) {
            val varp = currency.varp ?: continue
            VarPlayerIntMapSetter.set(player, varp, player.vars[varp] + amount)
        }
    }

    private fun ProtectedAccess.heldPayoutCount(currency: OmnishopCurrency): Long {
        val varp = currency.varp
        if (varp != null) return player.vars[varp].toLong()
        val obj = currency.objs.firstOrNull() ?: return 0L
        return inv.physicalCount(obj.internalName).toLong()
    }

    private fun ProtectedAccess.currencyCount(currency: OmnishopCurrency): Int {
        val varp = currency.varp
        if (varp != null) return player.vars[varp]
        val total = currency.objs.sumOf { inv.physicalCount(it.internalName).toLong() }
        return total.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    internal data class InfoArgs(val shop: Int, val index: Int) : IfScriptArgs

    internal data class BuyArgs(val shop: Int, val index: Int, val option: Int) : IfScriptArgs

    internal data class ExamineArgs(val obj: Int) : IfScriptArgs

    private companion object {
        const val INFO_UPDATE = "clientscript.omnishop_info_update"
        val BUY_QUANTITIES = listOf(1, 5, 10, 50)
    }
}
