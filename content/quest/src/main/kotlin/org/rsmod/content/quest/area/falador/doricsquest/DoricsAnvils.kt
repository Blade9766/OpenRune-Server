package org.rsmod.content.quest.area.falador.doricsquest

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ObjectServerType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.content.quest.area.falador.doricsquest.npcs.DoricDialogue

/**
 * The gate on Doric's two anvils, called by anvil smithing before it opens. Until the quest is done
 * Doric objects, and the objection can start or finish the quest.
 */
@Singleton
class DoricsAnvils
@Inject
constructor(private val dorics: DoricsQuest, private val dialogue: DoricDialogue) {

    private val anvil by lazy { DORICS_ANVIL.asRSCM(RSCMType.LOC) }

    suspend fun ProtectedAccess.mayUse(type: ObjectServerType): Boolean {
        if (type.id != anvil || dorics.anvilsUnlocked(player)) {
            return true
        }
        startDialogue { with(dialogue) { anvilRefusal() } }
        return false
    }

    companion object {
        const val DORICS_ANVIL = "loc.dorics_anvil"
    }
}
