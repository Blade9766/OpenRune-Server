package org.rsmod.content.slayer.rewards

import dev.openrune.types.ItemServerType
import dev.openrune.types.aconverted.interf.IfButtonOp
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.table.slayer.SlayerUnlockRow

object SlayerRewardsHandler {

    fun onConfirmButton(access: ProtectedAccess, comsub: Int) {
        if (SlayerRewardTasks.tryHandleUnblockConfirm(access, comsub)) {
            return
        }

        SlayerUnlockRow.all().find { it.bit == comsub }?.let { unlockRow ->
            SlayerRewardUnlocks.handleUnlockSlot(access, unlockRow)
            return
        }

        SlayerRewardTasks.handleComsub(access, comsub)
    }

    fun onBuyItem(access: ProtectedAccess, shopIndex: Int, op: IfButtonOp, item: ItemServerType?) {
        if (op == IfButtonOp.Op10) {
            SlayerRewardShop.handleBuyReward(access, shopIndex, item, requestedSets = 0, examine = true)
            return
        }

        val sets =
            when (op) {
                IfButtonOp.Op2 -> 1
                IfButtonOp.Op3 -> 5
                IfButtonOp.Op4 -> 10
                IfButtonOp.Op5 -> 50
                else -> return
            }

        SlayerRewardShop.handleBuyReward(access, shopIndex, item, sets, examine = false)
    }
}
