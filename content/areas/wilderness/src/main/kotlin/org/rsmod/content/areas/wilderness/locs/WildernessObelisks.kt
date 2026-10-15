package org.rsmod.content.areas.wilderness.locs

import jakarta.inject.Inject
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLoc3
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.util.PathingEntityCommon
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

/**
 * The Wilderness obelisks.
 *
 * Each site is four obelisks round a 3x3 square. Touching any of them charges the site; a few ticks
 * later everyone standing in the square who is not teleblocked is carried to another site, keeping
 * their place in the square. Where to is random, unless the player who charged it has finished the
 * hard Wilderness diary and used Teleport to Destination or Set Destination to pick a site. A site
 * that is already charged cannot be charged again until it has gone off.
 */
class WildernessObelisks
@Inject
constructor(
    private val players: PlayerList,
    private val collision: CollisionFlagMap,
) : PluginScript() {
    private val charged = HashSet<Site>()

    override fun ScriptContext.startup() {
        for (stone in STONES) {
            onOpLoc1(stone) { activate(it.loc, destination = null) }
            onOpLoc2(stone) { chooseAndActivate(it.loc) }
            onOpLoc3(stone) { chooseAndActivate(it.loc) }
        }
    }

    private suspend fun ProtectedAccess.chooseAndActivate(loc: BoundLocInfo) {
        if (!canChoose(player)) {
            mes("You need to complete the hard Wilderness diary to choose where the obelisk sends you.")
            return
        }
        val from = Site.nearest(loc.coords)
        val destination = choose(this, exclude = from) ?: return
        activate(loc, destination)
    }

    private suspend fun ProtectedAccess.activate(loc: BoundLocInfo, destination: Site?) {
        val site = Site.nearest(loc.coords)
        if (!charged.add(site)) {
            mes("The obelisk is already active.")
            return
        }
        anim(TOUCH_SEQ)
        mes("You activate the ancient obelisk...")
        try {
            delay(CHARGE_TICKS)
        } finally {
            charged.remove(site)
        }
        val target = destination ?: Site.entries.filter { it != site }.random()
        val passengers = players.filter { it.coords.level == site.centre.level && inSquare(site, it.coords) }
        for (passenger in passengers) {
            if (passenger.vars[TELEBLOCK] > 0) {
                passenger.mes("A teleport block has been cast on you.")
                continue
            }
            val dx = passenger.coords.x - site.centre.x
            val dz = passenger.coords.z - site.centre.z
            PathingEntityCommon.telejump(passenger, collision, target.centre.translate(dx, dz, 0))
            passenger.mes("Ancient magic teleports you somewhere in the Wilderness.")
        }
    }

    private fun inSquare(site: Site, coords: CoordGrid): Boolean =
        coords.chebyshevDistance(site.centre) <= SQUARE_RADIUS

    /** The six sites, by the centre of each one's square and its Wilderness level. */
    enum class Site(val label: String, val level: Int, val centre: CoordGrid) {
        FEROX("Level 13 (south-east of Ferox Enclave)", 13, CoordGrid(3156, 3620, 0)),
        GRAVEYARD("Level 19 (east of the Graveyard of Shadows)", 19, CoordGrid(3227, 3667, 0)),
        GOD_WARS("Level 27 (south of the God Wars Dungeon)", 27, CoordGrid(3035, 3732, 0)),
        LAVA_MAZE_SOUTH("Level 35 (south of the Lava Maze)", 35, CoordGrid(3106, 3794, 0)),
        LAVA_MAZE_WEST("Level 44 (west of the Lava Maze)", 44, CoordGrid(2980, 3867, 0)),
        ROGUES_CASTLE("Level 50 (near Rogues' Castle)", 50, CoordGrid(3307, 3916, 0));

        companion object {
            fun nearest(coords: CoordGrid): Site = entries.minBy { it.centre.chebyshevDistance(coords) }
        }
    }

    companion object {
        private const val TELEBLOCK = "varbit.teleblock_cycles"
        private const val HARD_DIARY = "varbit.wilderness_diary_hard_complete"
        private const val TOUCH_SEQ = "seq.human_pickuptable"
        private const val CHARGE_TICKS = 6
        private const val SQUARE_RADIUS = 1

        private val STONES = (0..5).map { "loc.wilderness_portal_stone_$it" }

        /** Whether [player] may pick an obelisk's destination rather than be sent at random. */
        fun canChoose(player: Player): Boolean = player.vars[HARD_DIARY] == 1

        /** Asks [access]'s player which site to go to, leaving out [exclude]; null if they back out. */
        suspend fun choose(access: ProtectedAccess, exclude: Site? = null): Site? {
            val sites = Site.entries.filter { it != exclude }
            val chosen = access.menu("Obelisk Destinations", hotkeys = true, choices = sites.map { it.label })
            return sites.getOrNull(chosen)
        }
    }
}
