package org.rsmod.content.quest.area.taverley.druidicritual

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLocU
import org.rsmod.content.quest.area.taverley.druidicritual.DruidicRitualQuest.Companion.CAULDRON_MEATS
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The Cauldron of Thunder in Taverley Dungeon. Raw meat dipped in it comes out enchanted. The
 * cauldron works whether or not the quest is under way, and on as many meats as the player brings.
 */
class CauldronOfThunder : PluginScript() {
    override fun ScriptContext.startup() {
        for ((raw, enchanted) in CAULDRON_MEATS) {
            onOpLocU(CAULDRON, raw) { dip(it.loc, raw, enchanted) }
        }
    }

    private suspend fun ProtectedAccess.dip(loc: BoundLocInfo, raw: String, enchanted: String) {
        arriveDelay()
        faceLoc(loc)
        val replaced = invReplace(inv, raw, 1, enchanted)
        if (replaced.failure) {
            return
        }
        soundSynth(DIP_SOUND)
        mes("You dip the ${displayName(raw)} in the cauldron.")
    }

    private fun displayName(obj: String): String =
        ServerCacheManager.getItem(obj.asRSCM(RSCMType.OBJ))?.name?.lowercase() ?: "meat"

    private companion object {
        const val CAULDRON = "loc.cauldron_of_thunder"
        const val DIP_SOUND = "synth.cauldron_bubbling"
    }
}
