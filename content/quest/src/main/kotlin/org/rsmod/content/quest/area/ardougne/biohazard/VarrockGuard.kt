package org.rsmod.content.quest.area.ardougne.biohazard

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onArea
import org.rsmod.api.script.onAreaExit
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.ardougne.biohazard.BiohazardQuest.Companion.PLAGUE_SAMPLE
import org.rsmod.content.quest.area.ardougne.biohazard.BiohazardQuest.Companion.STAGE_GOT_SAMPLES
import org.rsmod.content.quest.area.ardougne.biohazard.BiohazardQuest.Companion.STAGE_GUIDOR_TESTED
import org.rsmod.content.quest.area.ardougne.biohazard.BiohazardQuest.Companion.VIALS
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The guard watching the south-east corner of Varrock around Guidor's house and the Dancing
 * Donkey Inn. Anyone walking into that district carrying Elena's chemicals gets searched and
 * the vials confiscated; the plague sample itself means nothing to him.
 */
class VarrockGuard @Inject constructor(private val biohazard: BiohazardQuest) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(GUARD) {
            startDialogue(it.npc) {
                chatNpc(neutral, "Please don't disturb me, I've got to keep any eye out for suspicious individuals.")
            }
        }
        onArea(WATCH_AREA) { enterDistrict() }
        onAreaExit(WATCH_AREA) { leaveDistrict() }
    }

    private suspend fun ProtectedAccess.enterDistrict() {
        if (biohazard.stage(player) !in STAGE_GOT_SAMPLES..STAGE_GUIDOR_TESTED) {
            return
        }
        val carried = VIALS.filter { inv.contains(it) }
        if (carried.isEmpty() && !inv.contains(PLAGUE_SAMPLE)) {
            return
        }
        stopAction()
        startDialogue {
            chatNpcSpecific("Guard", GUARD, neutral, "Halt. I need to conduct a search on you. There have been reports of someone bringing a virus into this area of Varrock.")
            mesbox("The guard searches you.")
            for (vial in carried) {
                access.invDel(player.inv, vial)
                objbox(vial, "He takes the vial of ${vialName(vial)} from you.")
            }
            chatNpcSpecific("Guard", GUARD, neutral, "You may now pass.")
        }
    }

    private suspend fun ProtectedAccess.leaveDistrict() {
        if (biohazard.stage(player) !in STAGE_GOT_SAMPLES..STAGE_GUIDOR_TESTED) {
            return
        }
        if (VIALS.any { inv.contains(it) }) {
            mes("I should have talked to Guidor before leaving the area. If I go back in with these vials the guard will take them.")
        }
    }

    private fun vialName(vial: String): String =
        when (vial) {
            "obj.ethenea" -> "ethenea"
            "obj.liquid_honey" -> "liquid honey"
            else -> "sulphuric broline"
        }

    private companion object {
        const val GUARD = "npc.bioguard1"

        /** Custom area packed from `.data/raw-cache/map/area/biohazard_varrock_watch.toml`. */
        const val WATCH_AREA = "area.biohazard_varrock_watch"
    }
}
