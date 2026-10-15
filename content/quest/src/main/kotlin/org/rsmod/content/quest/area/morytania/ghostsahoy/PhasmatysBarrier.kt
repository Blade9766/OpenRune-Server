package org.rsmod.content.quest.area.morytania.ghostsahoy

import jakarta.inject.Inject
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc4
import org.rsmod.content.quest.area.ardougne.undergroundpass.faceTowards
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.ECTOTOKEN
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.TOLL
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The energy barriers in Port Phasmatys's walls. Each is a cache multiloc on `varbit.ahoy_questvar`
 * that shows the toll barrier until the quest is complete and the free post-quest barrier after.
 *
 * Only entering is charged: two ecto-tokens, taken in the same step that moves the player
 * through. Leaving is always free, and so is entering once Necrovarus has been commanded, even
 * while the client still draws the toll barrier because Velorina has not yet been told.
 */
class PhasmatysBarrier @Inject constructor(private val ahoy: GhostsAhoyQuest) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpLoc1(BARRIER) { pass(it.loc, payNow = false) }
        onOpLoc4(BARRIER) { pass(it.loc, payNow = true) }
        onOpLoc4(BARRIER_FREE) { pass(it.loc, payNow = false) }
    }

    private suspend fun ProtectedAccess.pass(loc: BoundLocInfo, payNow: Boolean) {
        arriveDelay()
        val gate = Gate.at(loc.coords) ?: return
        if (gate.isInside(coords) || ahoy.hasFreePassage(player)) {
            cross(gate)
            return
        }
        if (inv.count(ECTOTOKEN) < TOLL) {
            mesbox("The barrier won't let you pass without paying the toll of $TOLL ecto-tokens.")
            return
        }
        if (!payNow) {
            var pay = false
            startDialogue {
                pay = choice2("Pay $TOLL ecto-tokens.", true, "Stay outside.", false, title = "Pass through the barrier?")
            }
            if (!pay) {
                return
            }
        }
        if (invDel(inv, ECTOTOKEN, TOLL).failure) {
            mes("You don't have enough ecto-tokens.")
            return
        }
        mes("You pay the toll of $TOLL ecto-tokens.")
        cross(gate)
    }

    private suspend fun ProtectedAccess.cross(gate: Gate) {
        val dest = gate.across(coords)
        exactMove(coords, dest, delay1 = 0, delay2 = CROSS_CYCLES, dir = faceTowards(coords, dest), teleportType = TeleportType.Exempt)
        delay(CROSS_TICKS)
        mes("You pass through the energy barrier.")
    }

    /**
     * One barrier: the tiles it stands on and the axis it is crossed along. [inside] is the town
     * side of that axis, one tile past the barrier; the outside is one tile before it.
     */
    enum class Gate(
        val origin: CoordGrid,
        val width: Int,
        val length: Int,
        val alongX: Boolean,
        val inside: Int,
        val outside: Int,
    ) {
        WEST(CoordGrid(3659, 3508, 0), width = 2, length = 1, alongX = false, inside = 3507, outside = 3509),
        SOUTH_WEST(CoordGrid(3652, 3485, 0), width = 1, length = 2, alongX = true, inside = 3653, outside = 3651),
        SOUTH(CoordGrid(3669, 3453, 0), width = 2, length = 1, alongX = false, inside = 3454, outside = 3452);

        fun isInside(coords: CoordGrid): Boolean {
            val axis = if (alongX) coords.x else coords.z
            return if (inside > outside) axis >= inside else axis <= inside
        }

        /** The tile straight through the barrier from [coords], kept within the barrier's span. */
        fun across(coords: CoordGrid): CoordGrid {
            val target = if (isInside(coords)) outside else inside
            return if (alongX) {
                CoordGrid(target, coords.z.coerceIn(origin.z, origin.z + length - 1), origin.level)
            } else {
                CoordGrid(coords.x.coerceIn(origin.x, origin.x + width - 1), target, origin.level)
            }
        }

        companion object {
            fun at(coords: CoordGrid): Gate? = entries.firstOrNull { it.origin == coords }
        }
    }

    companion object {
        const val BARRIER = "loc.ahoy_town_barrier"
        const val BARRIER_FREE = "loc.ahoy_town_barrier_post_quest"
        private const val CROSS_TICKS = 2
        private const val CROSS_CYCLES = CROSS_TICKS * 30
    }
}
