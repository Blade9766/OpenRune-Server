package org.rsmod.content.quest.area.varrock.dragonslayer

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpHeldU
import org.rsmod.content.quest.area.varrock.dragonslayer.DragonSlayerQuest.Companion.CRANDOR_MAP
import org.rsmod.content.quest.area.varrock.dragonslayer.DragonSlayerQuest.Companion.MAP_PARTS
import org.rsmod.content.quest.area.varrock.dragonslayer.DragonSlayerQuest.Companion.MAP_PART_LOZAR
import org.rsmod.content.quest.area.varrock.dragonslayer.DragonSlayerQuest.Companion.MAP_PART_MELZAR
import org.rsmod.content.quest.area.varrock.dragonslayer.DragonSlayerQuest.Companion.MAP_PART_THALZAR
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** Studying the three map pieces and joining them into the map of the route to Crandor. */
class MapPieces @Inject constructor(private val dragonSlayer: DragonSlayerQuest) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpHeld1(MAP_PART_MELZAR) {
            mesbox(
                "This is a piece of map that you found in Melzar's Maze. You will need to join " +
                    "it to the other two map pieces before you can see the route to Crandor.",
            )
        }
        onOpHeld1(MAP_PART_THALZAR) {
            mesbox(
                "This is a piece of map that you found in a secret chest in the Dwarven Mine. " +
                    "You will need to join it to the other two map pieces before you can see " +
                    "the route to Crandor.",
            )
        }
        onOpHeld1(MAP_PART_LOZAR) {
            mesbox(
                "This is a piece of map that you got from Wormbrain, the goblin thief. You " +
                    "will need to join it to the other two map pieces before you can see the " +
                    "route to Crandor.",
            )
        }
        onOpHeld1(CRANDOR_MAP) {
            mesbox(
                "This map shows the route through the reefs to Crandor. A captain who knows " +
                    "the sea could follow it.",
            )
        }
        for (i in MAP_PARTS.indices) {
            for (j in i + 1 until MAP_PARTS.size) {
                onOpHeldU(MAP_PARTS[i], MAP_PARTS[j]) { join() }
            }
        }
    }

    private suspend fun ProtectedAccess.join() {
        if (!dragonSlayer.hasAllMapParts(player)) {
            mes("You still need one more piece of map.")
            return
        }
        for (part in MAP_PARTS) {
            invDel(inv, part, 1)
        }
        invAdd(inv, CRANDOR_MAP)
        objbox(
            CRANDOR_MAP,
            "You put the three pieces together and assemble a map that shows the route " +
                "through the reefs to Crandor.",
        )
    }
}
