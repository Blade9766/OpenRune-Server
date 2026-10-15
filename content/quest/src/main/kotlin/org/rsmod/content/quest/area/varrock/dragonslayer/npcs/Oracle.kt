package org.rsmod.content.quest.area.varrock.dragonslayer.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.varrock.dragonslayer.DragonSlayerQuest
import org.rsmod.content.quest.area.varrock.dragonslayer.DragonSlayerQuest.Companion.STAGE_BRIEFED
import org.rsmod.content.quest.area.varrock.dragonslayer.DragonSlayerQuest.Companion.STAGE_SHIP_REPAIRED
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** The Oracle at the top of Ice Mountain, who knows where Thalzar hid his piece of the map. */
class Oracle @Inject constructor(private val dragonSlayer: DragonSlayerQuest) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(ORACLE) { startDialogue(it.npc) { oracle() } }
    }

    private suspend fun Dialogue.oracle() {
        val seeking =
            dragonSlayer.stage(player) in STAGE_BRIEFED..STAGE_SHIP_REPAIRED &&
                !dragonSlayer.hasMapPiece(player, DragonSlayerQuest.MAP_PART_THALZAR)
        if (!seeking) {
            wisdom()
            return
        }
        when (
            choice2(
                "I seek a piece of the map to the island of Crandor.", 1,
                "Can you impart your wise knowledge to me, O Oracle?", 2,
            )
        ) {
            1 -> {
                chatPlayer(quiz, "I seek a piece of the map to the island of Crandor.")
                chatNpc(neutral, "The map lies behind a door beneath this mountain, but the way in is not easy. Listen well, for this is what you must offer the door.")
                chatNpc(neutral, "First, a drink that mages favour. Next, worm-string that has been changed to sheet. Then, a small cage for crustaceans. Last, a bowl that has never known heat.")
                dragonSlayer.oracleAsked.set(player, true)
            }
            2 -> wisdom()
        }
    }

    private suspend fun Dialogue.wisdom() {
        chatPlayer(quiz, "Can you impart your wise knowledge to me, O Oracle?")
        chatNpc(neutral, "The mountain remembers every footstep, yet the snow forgets them all by morning. Seek, and the seeking will shape you more than the finding.")
    }

    private companion object {
        const val ORACLE = "npc.oracle"
    }
}
