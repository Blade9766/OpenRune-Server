package org.rsmod.content.quest.area.burthorpe.eadgarsruse

import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLoc1
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** The way in and out of Mad Eadgar's cave at the top of Trollheim. */
class EadgarsCave : PluginScript() {

    override fun ScriptContext.startup() {
        onOpLoc1(ENTRANCE) { pass(INSIDE) }
        onOpLoc1(EXIT) { pass(OUTSIDE) }
    }

    private suspend fun ProtectedAccess.pass(dest: CoordGrid) {
        arriveDelay()
        delay(1)
        telejump(dest, TeleportType.Exempt)
    }

    internal companion object {
        const val ENTRANCE = "loc.troll_mad_eadgar_entrance"
        const val EXIT = "loc.troll_mad_eadgar_exit"

        val INSIDE = CoordGrid(2893, 10074, 2)
        val OUTSIDE = CoordGrid(2893, 3671, 0)
    }
}
