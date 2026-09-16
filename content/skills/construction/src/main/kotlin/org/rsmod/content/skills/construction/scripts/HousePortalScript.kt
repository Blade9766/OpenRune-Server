package org.rsmod.content.skills.construction.scripts

import jakarta.inject.Inject
import org.rsmod.api.registry.region.RegionRegistry
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc3
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.api.script.onPlayerLogout
import org.rsmod.content.skills.construction.data.HouseLocation
import org.rsmod.content.skills.construction.house.HouseAccess
import org.rsmod.content.skills.construction.house.HouseStore
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The town portals and the portal standing in the garden.
 *
 * Houses only exist while their owner is in them, so a player standing in one when they disconnect
 * would be saved at coordinates that belong to nothing. Logout moves them out before the save, and
 * login repeats the check for anyone whose save predates that.
 */
class HousePortalScript
@Inject
constructor(private val access: HouseAccess, private val store: HouseStore) : PluginScript() {
    override fun ScriptContext.startup() {
        for (location in HouseLocation.entries) {
            onOpLoc1(location.portal) { access.enter(this, buildMode = false) }
            onOpLoc3(location.portal) { access.enter(this, buildMode = true) }
        }
        onOpLoc1(EXIT_PORTAL) { access.leave(this) }

        onPlayerLogout {
            if (RegionRegistry.inWorkingArea(player.coords)) {
                player.coords = store.state(player).location.arrive
            }
            access.evict(player)
        }
        onPlayerLogin {
            if (RegionRegistry.inWorkingArea(player.coords)) {
                player.coords = store.state(player).location.arrive
            }
            access.evict(player)
        }
    }

    private companion object {
        const val EXIT_PORTAL = "loc.poh_exit_portal"
    }
}
