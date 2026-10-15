package org.rsmod.api.shops.currency

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ItemServerType
import dev.openrune.types.varp.bits
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.Inventory

public class VarBitShopCurrency(
    private val varbit: String,
    override val singularName: String,
    override val pluralName: String = "${singularName}s",
    private val sync: (Player) -> Unit = {},
) : ShopCurrency {
    override val invObj: ItemServerType? = null

    override fun balance(player: Player, sideInv: Inventory): Int = player.vars[varbit]

    private val maxValue: Int by lazy {
        val bits = ServerCacheManager.getVarbit(varbit.asRSCM(RSCMType.VARBIT))!!.bits
        val mask = (1L shl (bits.last - bits.first + 1)) - 1
        minOf(mask, Int.MAX_VALUE.toLong()).toInt()
    }

    override fun receiveCap(player: Player, sideInv: Inventory): Int =
        (maxValue - balance(player, sideInv)).coerceAtLeast(0)

    override fun deduct(player: Player, amount: Int) {
        if (amount <= 0) {
            return
        }
        val next = (player.vars[varbit] - amount).coerceAtLeast(0)
        VarPlayerIntMapSetter.set(player, varbit, next)
        sync(player)
    }

    override fun credit(player: Player, amount: Int) {
        if (amount <= 0) {
            return
        }
        VarPlayerIntMapSetter.set(player, varbit, player.vars[varbit] + amount)
        sync(player)
    }
}
