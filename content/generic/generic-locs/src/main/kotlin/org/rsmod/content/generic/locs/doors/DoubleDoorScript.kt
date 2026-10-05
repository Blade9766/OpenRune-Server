package org.rsmod.content.generic.locs.doors

import jakarta.inject.Inject
import org.rsmod.api.script.onOpContentLoc1
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class DoubleDoorScript @Inject constructor(private val doors: DoubleDoors) : PluginScript() {
    override fun ScriptContext.startup() {
        with(doors) {
            onOpContentLoc1("content.closed_left_door") { openLeftDoor(it.loc, it.type) }
            onOpContentLoc1("content.closed_right_door") { openRightDoor(it.loc, it.type) }
            onOpContentLoc1("content.opened_left_door") { closeLeftDoor(it.loc, it.type) }
            onOpContentLoc1("content.opened_right_door") { closeRightDoor(it.loc, it.type) }
        }
    }
}
