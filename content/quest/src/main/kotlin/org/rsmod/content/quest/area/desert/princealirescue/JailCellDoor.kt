package org.rsmod.content.quest.area.desert.princealirescue

import dev.openrune.types.ObjectServerType
import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLocU
import org.rsmod.content.generic.locs.passages.GenericPassageScript
import org.rsmod.content.quest.area.desert.princealirescue.PrinceAliRescueQuest.Companion.CELL_DOOR
import org.rsmod.content.quest.area.desert.princealirescue.PrinceAliRescueQuest.Companion.KEY
import org.rsmod.content.quest.area.desert.princealirescue.PrinceAliRescueQuest.Companion.STAGE_KELI_TIED
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The gate of the Prince's cell in the Draynor jail. Only the copied key opens it from outside,
 * and only once Keli is out of the way; anyone inside can always let themselves out.
 */
class JailCellDoor
@Inject
constructor(
    private val princeAli: PrinceAliRescueQuest,
    private val passages: GenericPassageScript,
) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpLoc1(CELL_DOOR) { open(it.loc, it.type) }
        onOpLocU(CELL_DOOR, KEY) { unlock(it.loc, it.type) }
    }

    private suspend fun ProtectedAccess.open(door: BoundLocInfo, type: ObjectServerType) {
        if (insideCell(door)) {
            with(passages) { walkThrough(door, type) }
            return
        }
        arriveDelay()
        mes("The door is locked.")
    }

    private suspend fun ProtectedAccess.unlock(door: BoundLocInfo, type: ObjectServerType) {
        if (!insideCell(door) && princeAli.stage(player) < STAGE_KELI_TIED) {
            arriveDelay()
            mesbox("You'll need to deal with Lady Keli before freeing the Prince.")
            return
        }
        mes("You unlock the door.")
        with(passages) { walkThrough(door, type) }
    }

    private fun ProtectedAccess.insideCell(door: BoundLocInfo): Boolean = coords.z <= door.coords.z
}
