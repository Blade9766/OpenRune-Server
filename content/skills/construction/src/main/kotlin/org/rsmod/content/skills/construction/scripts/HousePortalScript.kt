package org.rsmod.content.skills.construction.scripts

import jakarta.inject.Inject
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc3
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.api.script.onPlayerLogout
import org.rsmod.api.script.onPlayerSoftQueue
import org.rsmod.content.skills.construction.data.HouseLocation
import org.rsmod.content.skills.construction.house.HouseAccess
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The town portals and the portal standing in the garden.
 *
 * Houses only exist while their owner is in them, so a player standing in one when they disconnect
 * would be saved at coordinates that belong to nothing. Logout moves them out before the save; the
 * login check is for saves that predate that, and it waits a cycle because the login scene is
 * composed before any script runs and the engine sends no rebuild for the first build area.
 */
class HousePortalScript
@Inject
constructor(private val access: HouseAccess) : PluginScript() {
    override fun ScriptContext.startup() {
        for (location in HouseLocation.entries) {
            onOpLoc1(location.portal) { access.enter(this, buildMode = false) }
            onOpLoc3(location.portal) { access.enter(this, buildMode = true) }
        }
        onOpLoc1(EXIT_PORTAL) { access.leave(this) }

        onPlayerLogout { access.evict(player) }
        onPlayerLogin { player.softQueue(EVICT_QUEUE, 1) }
        onPlayerSoftQueue(EVICT_QUEUE) { access.evict(player) }
    }

    private companion object {
        const val EXIT_PORTAL = "loc.poh_exit_portal"

        const val EVICT_QUEUE = "queue.poh_evict"
    }
}
