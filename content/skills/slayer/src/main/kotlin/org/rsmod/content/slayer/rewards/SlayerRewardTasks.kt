package org.rsmod.content.slayer.rewards

import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.content.slayer.core.MortimerAssignment
import org.rsmod.content.slayer.core.SlayerTaskManager

internal object SlayerRewardTasks {

    fun handleComsub(access: ProtectedAccess, comsub: Int) {
        when (comsub) {
            CANCEL_COMSUB -> confirmCancel(access)
            BLOCK_COMSUB -> confirmBlock(access)
            EXTEND_ALL_COMSUB -> SlayerRewardUnlocks.confirmFullExtensionUnlock(access)
        }
    }

    private fun confirmCancel(access: ProtectedAccess) {
        if (access.vars["varp.slayer_target"] == 0) {
            access.mes("You do not have a Slayer assignment right now.")
            return
        }
        val master = SlayerTaskManager.getCurrentAssignedMaster(access.player)
        val cancelCost =
            if (MortimerAssignment.isMortimer(master)) MortimerAssignment.CANCEL_COST else CANCEL_TASK_COST
        if (SlayerRewardsPoints.getPoints(access.player) < cancelCost) {
            access.mes(
                "You do not have enough Slayer Points to cancel your task. You need $cancelCost Slayer Points.",
            )
            return
        }

        SlayerRewardsPoints.spendPoints(access.player, cancelCost)
        SlayerTaskManager.resetTask(access)
        SlayerRewardsPoints.syncPoints(access)
        access.mes("Your Slayer assignment has been cancelled.")
    }

    private fun confirmBlock(access: ProtectedAccess) {
        if (access.vars["varp.slayer_target"] == 0) {
            access.mes("You do not have a Slayer assignment right now.")
            return
        }

        val master = SlayerTaskManager.getCurrentAssignedMaster(access.player) ?: return
        val blockCost = master.blockCost
        if (SlayerRewardsPoints.getPoints(access.player) < blockCost) {
            access.mes(
                "You do not have enough Slayer Points to block your task. You need $blockCost Slayer Points.",
            )
            return
        }

        val slot = SlayerBlockSlots.firstEmptySlot(access.player, master)
        if (slot == null) {
            access.mes("You don't have any empty slots to block this task!")
            return
        }

        val varbit = RSCM.getReverseMapping(RSCMType.VARBIT, master.blockVarbits[slot])
        if (!SlayerRewardsPoints.spendPoints(access.player, blockCost)) return

        VarPlayerIntMapSetter.set(access.player, varbit, access.vars["varp.slayer_target"])
        SlayerTaskManager.resetTask(access)
        SlayerRewardsPoints.syncPoints(access)
    }

    private val unblockConfirmComsubToSlot =
        mapOf(69 to 0, 70 to 1, 71 to 2, 72 to 3, 73 to 4, 78 to 5, 74 to 6)

    fun tryHandleUnblockConfirm(access: ProtectedAccess, comsub: Int): Boolean {
        val slotIndex = unblockConfirmComsubToSlot[comsub] ?: return false
        confirmUnblock(access, slotIndex)
        return true
    }

    private fun confirmUnblock(access: ProtectedAccess, slotIndex: Int) {
        val master = SlayerTaskManager.getFocusedMaster(access.player) ?: return
        val varbit = RSCM.getReverseMapping(RSCMType.VARBIT, master.blockVarbits[slotIndex])
        if (access.vars[varbit] == 0) {
            access.mes("You don't have a Slayer task blocked in that slot.")
            return
        }

        VarPlayerIntMapSetter.set(access.player, varbit, 0)
        SlayerRewardsPoints.update(access.player)
    }

    private const val CONFIRM_COMSUB_BASE = 66
    private const val CANCEL_COMSUB = CONFIRM_COMSUB_BASE + 1
    private const val BLOCK_COMSUB = CONFIRM_COMSUB_BASE + 2
    private const val EXTEND_ALL_COMSUB = CONFIRM_COMSUB_BASE + 9
    private const val CANCEL_TASK_COST = 30
}
