package org.rsmod.content.quest.area.falador.doricsquest.npcs

import jakarta.inject.Inject
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.falador.doricsquest.DoricsQuest
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** Doric, in his smithy north of Falador. Starts and ends Doric's Quest; guards his whetstone. */
class Doric
@Inject
constructor(private val dorics: DoricsQuest, private val dialogue: DoricDialogue) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(DoricDialogue.DORIC) { startDialogue(it.npc) { with(dialogue) { talk() } } }
        onOpLoc1(WHETSTONE) {
            if (dorics.anvilsUnlocked(player)) {
                mes("Nothing interesting happens.")
            } else {
                mesbox("You should probably ask before using that.")
            }
        }
    }

    private companion object {
        const val WHETSTONE = "loc.devious_whetstone"
    }
}
