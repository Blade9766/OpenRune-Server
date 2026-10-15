package org.rsmod.content.skills.construction.scripts

import dev.openrune.definition.type.widget.IfEvent
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.player.hook.PlayerTeleportValidator
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.output.ChatType
import org.rsmod.api.player.output.ClientScripts
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.script.onIfClose
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLoc3
import org.rsmod.api.script.onOpLoc4
import org.rsmod.content.skills.construction.data.JewelleryBox
import org.rsmod.content.skills.construction.house.HouseAccess
import org.rsmod.content.travel.jewellery.JewelleryDestination
import org.rsmod.content.travel.jewellery.JewelleryRequirements
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The jewellery box and the mounted amulet of glory: free, unlimited jewellery teleports that leave
 * the house as its exit portal does.
 *
 * The box's Teleport Menu opens the real `poh_jewellery_box` interface; its init script lays out
 * the buttons for the box's tier and greys out none, and a click comes back as a pause button on
 * the universe layer carrying the button's number. Its third op, which the client only shows once
 * the box has been used, goes straight back to the last place it sent the player. The mounted
 * glory's four ops are its four places.
 */
class JewelleryBoxScript
@Inject
constructor(
    private val houses: HouseAccess,
    private val teleportValidator: PlayerTeleportValidator,
    private val areaChecker: AreaChecker,
) : PluginScript() {
    override fun ScriptContext.startup() {
        for ((box, families) in JewelleryBox.BOXES) {
            onOpLoc2(box) { openMenu(families) }
            onOpLoc3(box) { teleportToLast(families) }
        }
        onOpLoc1(JewelleryBox.MOUNTED_GLORY) { teleport(JewelleryBox.GLORY[0]) }
        onOpLoc2(JewelleryBox.MOUNTED_GLORY) { teleport(JewelleryBox.GLORY[1]) }
        onOpLoc3(JewelleryBox.MOUNTED_GLORY) { teleport(JewelleryBox.GLORY[2]) }
        onOpLoc4(JewelleryBox.MOUNTED_GLORY) { teleport(JewelleryBox.GLORY[3]) }
        onIfClose(INTERFACE) { ClientScripts.chatDefaultRestoreInput(player) }
    }

    private suspend fun ProtectedAccess.openMenu(families: Int) {
        val buttons = JewelleryBox.buttons(families)
        ifOpenMainModal(INTERFACE)
        runClientScript(INIT_SCRIPT.asRSCM(RSCMType.CLIENTSCRIPT), TIERS.getValue(families), TITLES.getValue(families), ALL_UNLOCKED)
        ifSetEvents(UNIVERSE, 0 until buttons, IfEvent.PauseButton)
        val input = pauseButton()
        ifClose()
        if (!input.isComponentType(UNIVERSE)) {
            return
        }
        if (input.subcomponent !in 0 until buttons) {
            return
        }
        val destination = JewelleryBox.DESTINATIONS[input.subcomponent]
        if (teleport(destination)) {
            VarPlayerIntMapSetter.set(player, JewelleryBox.LAST_DESTINATION_VARBIT, input.subcomponent + 1)
        }
    }

    private suspend fun ProtectedAccess.teleportToLast(families: Int) {
        val index = player.vars[JewelleryBox.LAST_DESTINATION_VARBIT] - 1
        if (index !in 0 until JewelleryBox.buttons(families)) {
            return
        }
        teleport(JewelleryBox.DESTINATIONS[index])
    }

    /** Returns whether the player was sent on their way. */
    private suspend fun ProtectedAccess.teleport(destination: JewelleryDestination): Boolean {
        val coords = destination.coords
        if (coords == null) {
            mes("${destination.name} has not been added to this server yet.")
            return false
        }
        if (actionDelay > mapClock) {
            return false
        }
        val requirement = JewelleryRequirements.denial(player, destination)
        if (requirement != null) {
            mes(requirement)
            return false
        }
        val denial = teleportValidator.validate(player, TeleportType.Standard, areaChecker)
        if (denial != null) {
            mes(denial, ChatType.Engine)
            return false
        }
        actionDelay = mapClock + ACTION_DELAY
        anim(TELEPORT_ANIM)
        spotanim(TELEPORT_SPOTANIM, height = TELEPORT_SPOTANIM_HEIGHT)
        soundSynth(TELEPORT_SOUND)
        delay(TELEPORT_DELAY)
        houses.leave(this, sound = false, to = coords)
        anim(TELEPORT_END_ANIM)
        return true
    }

    private companion object {
        const val INTERFACE = "interface.poh_jewellery_box"
        const val UNIVERSE = "component.poh_jewellery_box:universe"
        const val INIT_SCRIPT = "clientscript.[clientscript,poh_jewellery_box_init]"

        /** The init script's tier argument, by how many families the box holds. */
        val TIERS = mapOf(2 to 1, 4 to 2, 6 to 3)
        val TITLES = mapOf(2 to "Basic Jewellery Box", 4 to "Fancy Jewellery Box", 6 to "Ornate Jewellery Box")

        /** The init script greys out destinations whose bit is clear; every destination is open here. */
        const val ALL_UNLOCKED = 0x1F

        const val TELEPORT_ANIM = "seq.human_castteleport"
        const val TELEPORT_END_ANIM = "seq.human_castteleport_reverse"
        const val TELEPORT_SPOTANIM = "spotanim.teleport_casting"
        const val TELEPORT_SPOTANIM_HEIGHT = 92
        const val TELEPORT_SOUND = "synth.teleport_all"
        const val TELEPORT_DELAY = 3
        const val ACTION_DELAY = 4
    }
}
