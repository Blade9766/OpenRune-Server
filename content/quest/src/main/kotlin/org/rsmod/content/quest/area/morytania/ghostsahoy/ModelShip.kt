package org.rsmod.content.quest.area.morytania.ghostsahoy

import dev.openrune.rscm.RSCM.asRSCM
import jakarta.inject.Inject
import org.rsmod.api.invtx.invTransaction
import org.rsmod.api.invtx.select
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpHeldU
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.KNIFE
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.NEEDLE
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.SILK
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.THREAD
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.TOY_BOAT
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.TOY_BOAT_REPAIRED
import org.rsmod.content.quest.manager.menu
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The Old Crone's model ship. Its torn flag is replaced with a piece of silk, cut with a knife and
 * stitched on with a needle and thread (the silk and one thread are used up). The new white flag
 * has three parts - top half, bottom half and skull emblem - each dyed separately with any of the
 * six dyes, and any part can be dyed again over a wrong colour. The colours live on the player
 * in `varp.ahoy_state`, beside the wreck flag's solution they have to match.
 *
 * Dyes are mixed by the existing dye-mixing handlers (red and yellow make orange, red and blue
 * purple, blue and yellow green).
 */
class ModelShip @Inject constructor(private val ahoy: GhostsAhoyQuest) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpHeld1(TOY_BOAT) { repair() }
        onOpHeldU(NEEDLE, TOY_BOAT) { repair() }
        onOpHeldU(SILK, TOY_BOAT) { repair() }
        onOpHeld1(TOY_BOAT_REPAIRED) { inspect() }
        for (colour in FlagColour.entries) {
            onOpHeldU(colour.dye, TOY_BOAT_REPAIRED) { dye(colour) }
        }
    }

    private fun ProtectedAccess.repair() {
        val missing = listOf(SILK to "some silk", NEEDLE to "a needle", THREAD to "some thread", KNIFE to "a knife")
            .filterNot { inv.contains(it.first) }
        if (missing.isNotEmpty()) {
            mes("To make a new flag you need ${missing.joinToString(", ") { it.second }}.")
            return
        }
        val mended =
            player.invTransaction(inv) {
                val pack = select(inv)
                delete {
                    from = pack
                    obj = SILK.asRSCM()
                    strictCount = 1
                }
                delete {
                    from = pack
                    obj = THREAD.asRSCM()
                    strictCount = 1
                }
                delete {
                    from = pack
                    obj = TOY_BOAT.asRSCM()
                    strictCount = 1
                }
                insert {
                    into = pack
                    obj = TOY_BOAT_REPAIRED.asRSCM()
                    strictCount = 1
                }
            }
        if (mended.failure) {
            return
        }
        ahoy.clearApplied(player)
        mes("You cut a flag from the silk and stitch it onto the model ship's mast.")
    }

    private fun ProtectedAccess.inspect() {
        val parts = FlagPart.entries.joinToString("; ") { "the ${it.label} is ${ahoy.applied(player, it)?.label ?: "white"}" }
        mes("The model ship's flag: $parts.")
    }

    private suspend fun ProtectedAccess.dye(colour: FlagColour) {
        var chosen: FlagPart? = null
        startDialogue {
            chosen =
                menu(
                    "Dye the top half of the flag." to FlagPart.TOP,
                    "Dye the bottom half of the flag." to FlagPart.BOTTOM,
                    "Dye the skull emblem." to FlagPart.SKULL,
                    title = "Which part of the flag?",
                )
        }
        val part = chosen ?: return
        if (!inv.contains(TOY_BOAT_REPAIRED) || invDel(inv, colour.dye).failure) {
            return
        }
        ahoy.setApplied(player, part, colour)
        mes("You dye the ${part.label} of the flag ${colour.label}.")
    }
}
