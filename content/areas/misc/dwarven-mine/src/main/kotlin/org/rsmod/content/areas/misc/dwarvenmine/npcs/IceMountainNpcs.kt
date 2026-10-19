package org.rsmod.content.areas.misc.dwarvenmine.npcs

import org.rsmod.api.script.onOpNpc1
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** Rolad studying outside the mine's north entrance. */
class IceMountainNpcs : PluginScript() {
    override fun ScriptContext.startup() {
        onOpNpc1("npc.dwarfrock_rolad") {
            startDialogue(it.npc) {
                chatNpc(neutral, "Can you leave me alone please? I'm trying to study.")
            }
        }
    }
}
