package org.rsmod.content.interfaces.bank

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.game.entity.Player

/**
 * Objs added straight into the bank inv land past the tracked tab sizes, where withdrawing them
 * finds no tab; the main tab is grown over them so the bank stays consistent.
 */
fun Player.coverStrayBankObjs() {
    val bank = invMap.getOrPut("inv.bank")
    val lastOccupied = bank.indexOfLast { it != null }
    val tracked = BankTab.entries.sumOf { vars[it.sizeVarBit] }
    if (lastOccupied < tracked) {
        return
    }
    val main = checkNotNull(ServerCacheManager.getVarbit(BankTab.Main.sizeVarBit.asRSCM(RSCMType.VARBIT)))
    VarPlayerIntMapSetter.set(this, main, vars[BankTab.Main.sizeVarBit] + lastOccupied + 1 - tracked)
}

/** Call after adding to or taking from the bank inv outside the bank interface's own ops. */
fun Player.syncBankAfterDirectWrite() {
    coverStrayBankObjs()
    queue("queue.bank_compress", 1)
}
