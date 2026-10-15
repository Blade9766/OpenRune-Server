package org.rsmod.content.quest.area.falador.recruitmentdrive

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import jakarta.inject.Provider
import org.rsmod.api.player.hook.PlayerRestrictionHook
import org.rsmod.api.player.hook.RestrictedAction
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpHeld1
import org.rsmod.content.quest.area.falador.recruitmentdrive.RecruitmentDriveQuest.Companion.CUISSE
import org.rsmod.content.quest.area.falador.recruitmentdrive.RecruitmentDriveQuest.Companion.HARNESS
import org.rsmod.content.quest.area.falador.recruitmentdrive.RecruitmentDriveQuest.Companion.HAUBERK
import org.rsmod.content.quest.area.falador.recruitmentdrive.RecruitmentDriveQuest.Companion.SALLET
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Initiate armour needs Recruitment Drive to wear, on top of the 20 Defence and 10 Prayer the cache's
 * own stat requirement params already enforce. Having it is not enough: the requirement is checked
 * on wearing, whoever sold or gave it.
 */
class InitiateArmourWearHook @Inject constructor(private val quest: Provider<RecruitmentDriveQuest>) : PlayerRestrictionHook {
    private val pieces by lazy { listOf(SALLET, HAUBERK, CUISSE).map { it.asRSCM(RSCMType.OBJ) }.toSet() }

    override fun restriction(player: Player, action: RestrictedAction): String? {
        if (action !is RestrictedAction.Equip || action.obj.id !in pieces) return null
        if (quest.get().unlocked(player)) return null
        return "You need to have completed the Recruitment Drive quest to wear this."
    }
}

/** The initiate harness unpacks into the sallet, hauberk and cuisse. */
class InitiateHarness : PluginScript() {
    override fun ScriptContext.startup() {
        onOpHeld1(HARNESS) { unpack(it.slot) }
    }

    private fun ProtectedAccess.unpack(slot: Int) {
        if (inv[slot]?.id != HARNESS.asRSCM(RSCMType.OBJ)) return
        if (inv.freeSpace() < PIECES - 1) {
            mes("You don't have enough inventory space.")
            return
        }
        if (invDel(inv, HARNESS, slot = slot).failure) return
        invAdd(inv, SALLET)
        invAdd(inv, HAUBERK)
        invAdd(inv, CUISSE)
    }

    private companion object {
        const val PIECES = 3
    }
}
