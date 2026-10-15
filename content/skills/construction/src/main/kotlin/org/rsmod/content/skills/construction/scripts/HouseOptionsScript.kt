package org.rsmod.content.skills.construction.scripts

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.ui.ifSetText
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.api.player.vars.resyncVar
import org.rsmod.api.script.onIfOpen
import org.rsmod.api.script.onIfOverlayButton
import org.rsmod.content.skills.construction.house.HouseAccess
import org.rsmod.content.skills.construction.house.HouseRegistry
import org.rsmod.content.skills.construction.house.HouseStore
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The house options side panel (`poh_options`), opened from the settings tab.
 *
 * Its radio buttons set their varbits on the client as they are clicked, so the server only has to
 * mirror each click into the same varbit - or, for building mode, rebuild the house.
 */
class HouseOptionsScript
@Inject
constructor(
    private val registry: HouseRegistry,
    private val houses: HouseAccess,
    private val store: HouseStore,
    private val servants: ServantDialogues,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onIfOpen(INTERFACE) { player.showRoomCount() }

        onIfOverlayButton(component("build_mode_on")) { setBuildingMode(true) }
        onIfOverlayButton(component("build_mode_off")) { setBuildingMode(false) }
        onIfOverlayButton(component("tele_on")) { player.teleportOutside = 0 }
        onIfOverlayButton(component("tele_off")) { player.teleportOutside = 1 }
        onIfOverlayButton(component("default_build_mode_on")) { player.teleportBuilding = 1 }
        onIfOverlayButton(component("default_build_mode_off")) { player.teleportBuilding = 0 }
        for ((option, buttons) in DOORS) {
            for (button in buttons) {
                onIfOverlayButton(component(button)) { setDoors(option) }
            }
        }
        onIfOverlayButton(component("expel_guests")) { expelGuests() }
        onIfOverlayButton(component("leave_house")) { leaveHouse() }
        onIfOverlayButton(component("call_servant")) { with(servants) { call(fromBell = false) } }
    }

    private fun Player.showRoomCount() {
        val rooms = store.state(this).rooms.size
        ifSetText(component("roomcount"), "Number of rooms: $rooms")
    }

    private fun ProtectedAccess.setBuildingMode(on: Boolean) {
        val house = registry.active(player)
        if (house == null || !registry.isInside(player)) {
            player.resyncVar(BUILDING_MODE)
            mes("You can only do this in your own house.")
            return
        }
        if (house.buildMode == on) {
            return
        }
        houses.rebuild(this, buildMode = on)
    }

    /** Doors are hung as the house is built, so a change shows once the owner's house is redone. */
    private fun ProtectedAccess.setDoors(option: Int) {
        if (player.doorsOption == option) {
            return
        }
        player.doorsOption = option
        val house = registry.active(player)
        if (house != null && registry.isInside(player) && !house.buildMode) {
            houses.rebuild(this, buildMode = false)
        }
    }

    private fun ProtectedAccess.expelGuests() {
        if (!houses.expelGuests(this)) {
            mes("You can only do this in your own house.")
        }
    }

    private fun ProtectedAccess.leaveHouse() {
        if (registry.houseAt(player.coords) == null) {
            mes("You are not in a house.")
            return
        }
        houses.leave(this)
    }

    private companion object {
        const val INTERFACE = "interface.poh_options"
        const val BUILDING_MODE = "varbit.poh_building_mode"

        /** `poh_doors_option`: 0 closed, 1 open, 2 no doors - the indices its clientscript sets. */
        val DOORS: Map<Int, List<String>> =
            mapOf(
                0 to listOf("doors_closed", "icon_doors_closed"),
                1 to listOf("doors_open", "icon_doors_open"),
                2 to listOf("doors_none", "icon_doors_none"),
            )

        fun component(name: String): String = "component.poh_options:$name"

        var Player.teleportOutside by intVarBit("varbit.poh_tele_toggle")
        var Player.teleportBuilding by intVarBit("varbit.poh_teleport_building_mode")
        var Player.doorsOption by intVarBit("varbit.poh_doors_option")
    }
}
