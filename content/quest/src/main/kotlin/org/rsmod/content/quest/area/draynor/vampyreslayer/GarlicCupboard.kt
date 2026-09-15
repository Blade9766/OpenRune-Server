package org.rsmod.content.quest.area.draynor.vampyreslayer

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.content.quest.area.draynor.vampyreslayer.VampyreSlayerQuest.Companion.GARLIC
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** The cupboard upstairs in Morgan's house, which never runs out of garlic. */
class GarlicCupboard @Inject constructor(private val locRepo: LocRepository) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpLoc1(SHUT) { open(it.loc) }
        onOpLoc1(OPEN) { search() }
        onOpLoc2(OPEN) { close(it.loc) }
    }

    private suspend fun ProtectedAccess.open(cupboard: BoundLocInfo) {
        arriveDelay()
        anim("seq.human_openchest")
        soundSynth("synth.cupboard_open")
        mes("You open the cupboard.")
        locRepo.change(cupboard, OPEN, OPEN_DURATION)
    }

    private suspend fun ProtectedAccess.search() {
        arriveDelay()
        if (inv.isFull()) {
            mesbox("The cupboard contains garlic, but you don't have room to hold it at the moment.")
            return
        }
        if (inv.count(GARLIC) == 0) {
            mes("The cupboard contains garlic. You take a clove.")
        } else {
            mes("You take a clove of garlic.")
        }
        invAdd(inv, GARLIC)
    }

    private suspend fun ProtectedAccess.close(cupboard: BoundLocInfo) {
        arriveDelay()
        anim("seq.human_closechest")
        soundSynth("synth.cupboard_close")
        mes("You close the cupboard.")
        locRepo.change(cupboard, SHUT, OPEN_DURATION)
    }

    private companion object {
        const val SHUT = "loc.garliccupboardshut"
        const val OPEN = "loc.garliccupboardopen"
        const val OPEN_DURATION = 300
    }
}
