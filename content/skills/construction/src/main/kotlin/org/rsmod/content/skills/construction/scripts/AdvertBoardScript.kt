package org.rsmod.content.skills.construction.scripts

import dev.openrune.definition.type.widget.IfEvent
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.player.input.ResumePNameDialogInput
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.script.onIfModalButton
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLoc3
import org.rsmod.api.script.onPlayerLogout
import org.rsmod.content.skills.construction.data.HouseLocation
import org.rsmod.content.skills.construction.house.HouseAccess
import org.rsmod.content.skills.construction.house.HouseAdverts
import org.rsmod.content.skills.construction.house.HouseRegistry
import org.rsmod.game.entity.PlayerList
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The house advertisement boards beside the house portals, and the exit portal's Remove board
 * advert.
 *
 * View opens the real `poh_board`. Its clientscripts draw everything from one line per listing
 * (see [HouseAdverts.line]) and the board's town in `varbit.poh_board_last_loc`. A listing from
 * this town gets an Enter House arrow, which answers a name dialog with the owner's name on its
 * own, so the board waits for one and visits that house the way the portal's Friend's house does -
 * privacy and all. The Add/Remove House and Refresh Data buttons cancel that wait, do their work and
 * show the board again; the location filter is the client's own.
 */
class AdvertBoardScript
@Inject
constructor(
    private val adverts: HouseAdverts,
    private val houses: HouseAccess,
    private val registry: HouseRegistry,
    private val players: PlayerList,
) : PluginScript() {
    override fun ScriptContext.startup() {
        for (location in HouseLocation.entries) {
            onOpLoc1(location.board) { view(location) }
            onOpLoc2(location.board) { addHouse() }
            onOpLoc3(location.board) { visitLast(location) }
        }
        onIfModalButton(ADD_REMOVE) { boardLocation()?.let { toggle(it) } }
        onIfModalButton(REFRESH) { boardLocation()?.let { view(it) } }
        onOpLoc3(EXIT_PORTAL) { removeFromPortal() }
        onPlayerLogout { adverts.remove(player) }
    }

    private suspend fun ProtectedAccess.view(location: HouseLocation) {
        ifOpenMainModal(INTERFACE)
        VarPlayerIntMapSetter.set(player, LAST_LOC_VARBIT, location.boardId)
        ifSetEvents(ADD_REMOVE, -1..-1, IfEvent.Op1)
        ifSetEvents(REFRESH, -1..-1, IfEvent.Op1)
        val listed = adverts.current().take(ROWS)
        for (row in 0..ROWS) {
            val line = listed.getOrNull(row)?.let(adverts::line).orEmpty()
            runClientScript(ADD_LINE, row, line, location.boardId)
        }
        val input = nameDialogInput()
        val owner = (input.result as? ResumePNameDialogInput.Result.SameWorld)?.uid?.resolve(players)
        if (owner == null) {
            mes("They do not seem to be at home.")
            return
        }
        ifClose()
        houses.visit(this, owner.displayName, location)
    }

    private fun ProtectedAccess.addHouse() {
        val problem = adverts.add(player)
        mes(problem ?: "Your house is now being advertised.")
    }

    private suspend fun ProtectedAccess.toggle(location: HouseLocation) {
        if (adverts.remove(player)) {
            mes("You take your house off the board.")
        } else {
            addHouse()
        }
        view(location)
    }

    private suspend fun ProtectedAccess.visitLast(location: HouseLocation) {
        val name = houses.lastVisited(player)
        if (name == null) {
            mes("You haven't visited anyone's house yet.")
            return
        }
        houses.visit(this, name, location)
    }

    private fun ProtectedAccess.removeFromPortal() {
        val house = registry.houseAt(player.coords)
        if (house == null || house.owner !== player) {
            mes("Only the owner of this house can take it off the board.")
            return
        }
        mes(if (adverts.remove(player)) "You take your house off the board." else "Your house isn't being advertised.")
    }

    private fun ProtectedAccess.boardLocation(): HouseLocation? = HouseLocation.forBoardId(player.vars[LAST_LOC_VARBIT])

    private companion object {
        const val INTERFACE = "interface.poh_board"
        const val ADD_REMOVE = "component.poh_board:addremove"
        const val REFRESH = "component.poh_board:refresh_button"
        const val LAST_LOC_VARBIT = "varbit.poh_board_last_loc"
        const val EXIT_PORTAL = "loc.poh_exit_portal"

        /** The rows the board's clientscript builds; the call for the row after them draws it. */
        const val ROWS = 200

        val ADD_LINE by lazy { "clientscript.[clientscript,poh_board_addline]".asRSCM(RSCMType.CLIENTSCRIPT) }
    }
}
