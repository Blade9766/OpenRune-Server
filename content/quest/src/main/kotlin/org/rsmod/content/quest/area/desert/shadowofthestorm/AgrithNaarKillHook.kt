package org.rsmod.content.quest.area.desert.shadowofthestorm

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.util.Wearpos
import jakarta.inject.Inject
import org.rsmod.api.death.NpcDeathKillContext
import org.rsmod.api.death.NpcDeathKillHook
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.content.quest.area.desert.shadowofthestorm.ShadowOfTheStormQuest.Companion.AGRITH_NAAR
import org.rsmod.content.quest.area.desert.shadowofthestorm.ShadowOfTheStormQuest.Companion.DARKLIGHT
import org.rsmod.content.quest.area.desert.shadowofthestorm.ShadowOfTheStormQuest.Companion.DYED_SILVERLIGHT
import org.rsmod.content.quest.area.desert.shadowofthestorm.ShadowOfTheStormQuest.Companion.SILVERLIGHT
import org.rsmod.content.quest.area.desert.shadowofthestorm.ShadowOfTheStormQuest.Companion.SOUND_APPEAR
import org.rsmod.content.quest.area.desert.shadowofthestorm.ShadowOfTheStormQuest.Companion.STAGE_FIGHT
import org.rsmod.content.quest.area.desert.shadowofthestorm.ShadowOfTheStormQuest.Companion.STAGE_SLAIN
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.isType

/**
 * Agrith-Naar's blood is the last ingredient Silverlight was ever going to take. The sword the
 * player is holding when he falls comes away black and far heavier, and that is Darklight.
 */
class AgrithNaarKillHook
@Inject
constructor(
    private val sots: ShadowOfTheStormQuest,
    private val launcher: ProtectedAccessLauncher,
) : NpcDeathKillHook {

    private val demonId: Int by lazy { AGRITH_NAAR.asRSCM(RSCMType.NPC) }

    override fun onKill(context: NpcDeathKillContext) {
        if (context.npc.id != demonId) {
            return
        }
        val hero = context.hero
        if (sots.stage(hero) != STAGE_FIGHT) {
            return
        }
        launcher.launch(hero) { claimDarklight() }
    }

    private suspend fun ProtectedAccess.claimDarklight() {
        sots.advanceTo(this, STAGE_SLAIN)
        soundSynth(SOUND_APPEAR)
        mesbox(
            "Agrith-Naar folds in on himself, and what is left of him runs down the blade in " +
                "your hand.",
        )
        val wielded = player.worn[Wearpos.RightHand.slot]
        if (wielded.isType(DYED_SILVERLIGHT) || wielded.isType(SILVERLIGHT)) {
            worn[Wearpos.RightHand.slot] = InvObj(DARKLIGHT, wielded.count, wielded.vars)
        } else if (invDel(inv, DYED_SILVERLIGHT).success || invDel(inv, SILVERLIGHT).success) {
            invAdd(inv, DARKLIGHT)
        } else {
            // Nothing to soak it up. The demon is still dead; Father Reen can hand the sword back.
            mes("The blood hisses away on the stone. Father Reen will want to hear about this.")
            return
        }
        objbox(
            DARKLIGHT,
            "Silverlight has drunk it up. The blade is black to the hilt now, and it is not " +
                "Silverlight any more.",
        )
        mes("Father Reen and Father Badden are waiting for you.")
    }
}
