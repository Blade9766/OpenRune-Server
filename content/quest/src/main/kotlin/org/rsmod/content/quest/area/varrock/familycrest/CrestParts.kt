package org.rsmod.content.quest.area.varrock.familycrest

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpHeldU
import org.rsmod.content.quest.area.varrock.familycrest.FamilyCrestQuest.Companion.ASSEMBLE_SOUND
import org.rsmod.content.quest.area.varrock.familycrest.FamilyCrestQuest.Companion.AVAN_CREST
import org.rsmod.content.quest.area.varrock.familycrest.FamilyCrestQuest.Companion.CALEB_CREST
import org.rsmod.content.quest.area.varrock.familycrest.FamilyCrestQuest.Companion.FAMILY_CREST
import org.rsmod.content.quest.area.varrock.familycrest.FamilyCrestQuest.Companion.JOHNATHON_CREST
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** Fitting the three brothers' pieces back into one crest. */
class CrestParts @Inject constructor() : PluginScript() {

    override fun ScriptContext.startup() {
        for (i in PARTS.indices) {
            for (j in i + 1 until PARTS.size) {
                onOpHeldU(PARTS[i], PARTS[j]) { assemble() }
            }
        }
    }

    private suspend fun ProtectedAccess.assemble() {
        val missing = PARTS.filterNot(inv::contains)
        if (missing.isNotEmpty()) {
            mes("The two pieces fit together, but a third is still missing.")
            return
        }
        for (part in PARTS) {
            if (invDel(inv, part).failure) {
                return
            }
        }
        invAdd(inv, FAMILY_CREST)
        soundSynth(ASSEMBLE_SOUND)
        objbox(FAMILY_CREST, "The three pieces lock together into the Fitzharmon family crest.")
    }

    private companion object {
        val PARTS = listOf(CALEB_CREST, AVAN_CREST, JOHNATHON_CREST)
    }
}
