package org.rsmod.content.quest.area.camelot.merlinscrystal

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLoc2
import org.rsmod.content.quest.area.camelot.merlinscrystal.MerlinsCrystalQuest.Companion.CHAOS_ALTAR
import org.rsmod.content.quest.area.camelot.merlinscrystal.MerlinsCrystalQuest.Companion.STAGE_SPOKEN_MORGAN
import org.rsmod.content.quest.area.camelot.merlinscrystal.MerlinsCrystalQuest.Companion.WORDS_OF_BINDING
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The chaos altar in the Zamorak chapel in south-east Varrock. Morgan Le Faye remembers only that
 * the words of binding are cut into the base of one of the chaos altars; this is the one, and
 * reading them is what makes the summoning survivable.
 */
class ChaosAltar @Inject constructor(private val quest: MerlinsCrystalQuest) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpLoc2(CHAOS_ALTAR) { checkAltar() }
    }

    private suspend fun ProtectedAccess.checkAltar() {
        arriveDelay()
        if (quest.stage(player) != STAGE_SPOKEN_MORGAN) {
            mes("An altar to the evil god Zamorak.")
            return
        }
        player.merlinKnowsWords = true
        mesbox(
            "You find a small inscription at the bottom of the altar.<br>" +
                "It reads: '$WORDS_OF_BINDING'.",
        )
    }
}
