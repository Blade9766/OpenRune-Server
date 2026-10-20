package org.rsmod.content.other.bots.world

import org.rsmod.api.invtx.invTransaction
import org.rsmod.api.invtx.select
import org.rsmod.api.invtx.transfer
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.Inventory

/**
 * Moves every item count of [load] from [bank] into [player]'s inventory in one transaction: all of
 * it or nothing. A bank transfer moves up to its count, so each stack must hold the full count first.
 */
internal fun withdrawLoad(player: Player, bank: Inventory, load: Map<Int, Int>): Boolean {
    val slots =
        load.mapValues { (id, count) ->
            bank.indices.firstOrNull { bank[it]?.id == id && bank[it]!!.count >= count } ?: return false
        }
    val result =
        player.invTransaction(bank, player.inv) {
            val from = select(bank)
            val into = select(player.inv)
            for ((id, count) in load) {
                transfer(from = from, fromSlot = slots.getValue(id), count = count, into = into)
            }
        }
    return result.success
}
