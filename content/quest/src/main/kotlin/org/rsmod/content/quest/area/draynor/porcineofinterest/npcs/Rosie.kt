package org.rsmod.content.quest.area.draynor.porcineofinterest.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.draynor.porcineofinterest.PorcineOfInterestQuest.Companion.ROSIE
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Sarah's sheepdog, who spooked the sourhog long enough for her to get away.
 *
 * She is a nameless "Sheepdog" with no options until Sarah introduces her, at which point
 * `varbit.porcine_rosie` turns the multinpc into Rosie and gives her the "Pet" option. The op
 * arrives under the base type, so it is registered on the multinpc rather than on Rosie herself.
 */
class Rosie @Inject constructor() : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(ROSIE) { pet(it.npc.coords) }
    }

    private suspend fun ProtectedAccess.pet(coords: CoordGrid) {
        arriveDelay()
        faceSquare(coords)
        anim(PET_SEQ)
        delay(PET_TICKS)
        mes("Rosie licks your hand and thumps her tail against the floor.")
    }

    private companion object {
        const val PET_SEQ = "seq.human_pickuptable"
        const val PET_TICKS = 2
    }
}
