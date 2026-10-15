package org.rsmod.content.quest.area.tirannwn.templeoflight

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLocU
import org.rsmod.api.script.onPlayerCoordsChanged
import org.rsmod.content.quest.area.ardougne.undergroundpass.climbOver
import org.rsmod.content.quest.area.ardougne.undergroundpass.faceTowards
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitType
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Getting about the Temple of Light.
 *
 * - Stairs and ladders join the three floors; each lands on the tile beside the matching loc
 *   above or below, checked against the map in `MourningsEndPart2CacheTest`.
 * - The low walls into the two shadow-free alcoves on the middle floor never fail.
 * - A rope tied to the rock in the southern alcove makes a shortcut to the ground floor and stays
 *   tied (`varbit.mourning_temple_rope`).
 * - The wall supports across the gap on the middle floor take eight jumps, each passed with the
 *   wiki's 64/256 to 272/256 Agility roll; a slip drops the player to the ground floor below for
 *   5 damage. They give no experience.
 * - Each blade trap guards a server-side wall: stepping onto the tile on either side makes the
 *   player dodge past (32/256 to 352/256, 5 Agility experience) or be knocked back a tile for 5
 *   damage.
 * - The doorway from the mines is open until the player restores the safeguards; after that only
 *   a player carrying the crystal trinket gets through ("A strange force blocks your path.").
 * - The hole behind the Death Altar and the dwarves' tunnel in the Underground Pass join once the
 *   player has met Thorgel.
 */
class TempleObstacles
@Inject
constructor(
    private val quest: MourningsEndPart2Quest,
    private val puzzle: TemplePuzzle,
    private val launcher: ProtectedAccessLauncher,
) : PluginScript() {

    override fun ScriptContext.startup() {
        for (loc in CLIMBS.map { it.loc }.distinct()) {
            onOpLoc1(loc) { climb(it.loc) }
        }
        onOpLoc1(LOW_WALL) { climbOver(acrossLowWall(it.loc) ?: return@onOpLoc1, LOW_WALL_SEQ, ticks = 2) }
        onOpLocU(ROCK, ROPE) { tieRope() }
        onOpLoc1(ROCK) { climbDownRope() }
        onOpLoc1(ROPE_MULTI) { climbUpRope() }
        onOpLoc1(WALL_SUPPORT) { crossSupports() }
        onOpLoc1(WALL_HOLE) { enterTunnel(UPASS_LANDING) }
        onOpLoc1(UPASS_TUNNEL) { descendTunnel() }
        onPlayerCoordsChanged { moved(player, lastKnownCoords) }
    }

    private suspend fun ProtectedAccess.climb(loc: BoundLocInfo) {
        arriveDelay()
        val climb = CLIMBS.firstOrNull { it.matches(loc) } ?: return
        anim(if (climb.up) CLIMB_UP_SEQ else CLIMB_DOWN_SEQ)
        delay(1)
        telejump(climb.dest)
    }

    private fun ProtectedAccess.acrossLowWall(wall: BoundLocInfo): CoordGrid? {
        val dx = wall.coords.x - coords.x
        if (wall.coords.z != coords.z || (dx != 1 && dx != -1)) return null
        return CoordGrid(wall.coords.x + dx, wall.coords.z, wall.coords.level)
    }

    private suspend fun ProtectedAccess.tieRope() {
        arriveDelay()
        if (player.templeRope == 1) {
            mes("There is already a rope tied to the rock.")
            return
        }
        if (invDel(inv, ROPE, 1).failure) {
            return
        }
        player.templeRope = 1
        anim(TIE_SEQ)
        mes("You tie the rope to the rock and let it fall down the hole.")
    }

    private suspend fun ProtectedAccess.climbDownRope() {
        arriveDelay()
        if (player.templeRope == 0) {
            mes("It's a long way down. You'll need to tie a rope to the rock first.")
            return
        }
        anim(CLIMB_DOWN_SEQ)
        delay(1)
        telejump(ROPE_BOTTOM)
    }

    private suspend fun ProtectedAccess.climbUpRope() {
        arriveDelay()
        if (player.templeRope == 0) {
            return
        }
        anim(CLIMB_UP_SEQ)
        delay(1)
        telejump(ROPE_TOP)
    }

    /**
     * Eight jumps along the supports, one roll each. The player hangs on the supports throughout,
     * moved a tile per jump, so their own clicks cannot pull them off halfway.
     */
    private suspend fun ProtectedAccess.crossSupports() {
        arriveDelay()
        val eastward =
            when (coords) {
                SUPPORTS_WEST -> true
                SUPPORTS_EAST -> false
                else -> return
            }
        val step = if (eastward) 1 else -1
        val face = faceTowards(coords, if (eastward) SUPPORTS_EAST else SUPPORTS_WEST)
        anim(if (eastward) HOLD_FIRST_SEQ else HOLD_FIRST_REVERSE_SEQ)
        delay(1)
        for (jump in 1..SUPPORT_JUMPS) {
            val next = coords.translateX(step)
            if (!statRandom(AGILITY, SUPPORT_LOW, SUPPORT_HIGH, 0)) {
                anim(if (eastward) HOLD_FALL_SEQ else HOLD_FALL_REVERSE_SEQ)
                delay(1)
                mes("You lose your grip and fall!")
                telejump(CoordGrid(coords.x, coords.z, 0))
                takeInstantHit(HitType.Typeless, FALL_DAMAGE)
                return
            }
            anim(if (eastward) HOLD_MIDDLE_SEQ else HOLD_MIDDLE_REVERSE_SEQ)
            exactMove(coords, next, 0, CYCLES_PER_TICK, face)
            delay(1)
        }
        val end = if (eastward) SUPPORTS_EAST else SUPPORTS_WEST
        anim(if (eastward) HOLD_LAST_SEQ else HOLD_LAST_REVERSE_SEQ)
        exactMove(coords, end, 0, CYCLES_PER_TICK, face)
        delay(1)
        resetAnim()
        mes("You make it safely across.")
    }

    private suspend fun ProtectedAccess.enterTunnel(dest: CoordGrid) {
        arriveDelay()
        anim(CRAWL_SEQ)
        delay(2)
        telejump(dest)
        mes("You crawl through the tunnel.")
    }

    private suspend fun ProtectedAccess.descendTunnel() {
        arriveDelay()
        if (player.metThorgel == 0) {
            return
        }
        enterTunnel(TEMPLE_LANDING)
    }

    private fun moved(player: Player, from: CoordGrid) {
        val to = player.coords
        if (to == from || to.level != from.level) {
            return
        }
        when (doorwayCrossing(from, to)) {
            Crossing.ENTER -> {
                launcher.launch(player) {
                    stopAction()
                    enterTemple()
                }
                return
            }
            Crossing.LEAVE -> {
                launcher.launch(player) {
                    stopAction()
                    leaveTemple()
                }
                return
            }
            null -> Unit
        }
        val trap = Trap.at(to) ?: return
        val other = trap.other(to) ?: return
        if (from == other) {
            return
        }
        launcher.launch(player) {
            stopAction()
            dodge(trap, to, other, from)
        }
    }

    internal suspend fun ProtectedAccess.enterTemple() {
        if (quest.guarded(player) && !inv.contains(MourningsEndPart2Quest.TRINKET)) {
            mes("A strange force blocks your path.")
            return
        }
        val dest = CoordGrid(DOORWAY_INSIDE_X - 1, coords.z, 0)
        exactMove(coords, dest, 0, CYCLES_PER_TICK, faceTowards(coords, dest))
        delay(1)
        puzzle.refresh(player)
    }

    internal suspend fun ProtectedAccess.leaveTemple() {
        val dest = CoordGrid(DOORWAY_OUTSIDE_X + 1, coords.z, 0)
        exactMove(coords, dest, 0, CYCLES_PER_TICK, faceTowards(coords, dest))
        delay(1)
    }

    internal suspend fun ProtectedAccess.dodge(trap: Trap, at: CoordGrid, past: CoordGrid, from: CoordGrid) {
        if (coords != at) {
            return
        }
        if (!statRandom(AGILITY, TRAP_LOW, TRAP_HIGH, 0)) {
            anim(TRAP_HIT_SEQ)
            mes("The blade catches you as you try to dodge past it!")
            takeInstantHit(HitType.Typeless, TRAP_DAMAGE)
            val back = if (from.chebyshevDistance(at) == 1) from else trap.backFrom(at)
            climbOver(back, STUMBLE_SEQ, ticks = 1)
            return
        }
        climbOver(past, DODGE_SEQ, ticks = 1)
        statAdvance(AGILITY, TRAP_XP)
    }

    /**
     * A blade trap: the server-side wall on [wall]'s [edge] side that only a dodge gets past, with
     * the tile on each side of it.
     */
    enum class Trap(val wall: CoordGrid, val edge: Edge) {
        GROUND_NORTH_1(CoordGrid(1870, 4659, 0), Edge.WEST),
        GROUND_NORTH_2(CoordGrid(1873, 4658, 0), Edge.WEST),
        MIDDLE_1(CoordGrid(1859, 4649, 1), Edge.NORTH),
        MIDDLE_2(CoordGrid(1861, 4632, 1), Edge.NORTH),
        MIDDLE_3(CoordGrid(1863, 4614, 1), Edge.EAST),
        MIDDLE_4(CoordGrid(1864, 4625, 1), Edge.NORTH),
        MIDDLE_5(CoordGrid(1864, 4664, 1), Edge.EAST),
        MIDDLE_6(CoordGrid(1869, 4622, 1), Edge.WEST),
        MIDDLE_7(CoordGrid(1869, 4656, 1), Edge.EAST),
        MIDDLE_8(CoordGrid(1870, 4612, 1), Edge.EAST),
        MIDDLE_9(CoordGrid(1874, 4653, 1), Edge.NORTH),
        MIDDLE_10(CoordGrid(1882, 4614, 1), Edge.EAST),
        MIDDLE_11(CoordGrid(1882, 4664, 1), Edge.EAST),
        MIDDLE_12(CoordGrid(1888, 4624, 1), Edge.SOUTH),
        MIDDLE_13(CoordGrid(1899, 4654, 1), Edge.NORTH),
        TOP_1(CoordGrid(1866, 4621, 2), Edge.EAST),
        TOP_2(CoordGrid(1866, 4657, 2), Edge.EAST),
        TOP_3(CoordGrid(1869, 4623, 2), Edge.NORTH),
        TOP_4(CoordGrid(1869, 4654, 2), Edge.NORTH),
        TOP_5(CoordGrid(1874, 4627, 2), Edge.EAST),
        TOP_6(CoordGrid(1879, 4651, 2), Edge.EAST),
        TOP_7(CoordGrid(1888, 4623, 2), Edge.NORTH),
        ;

        val beyond: CoordGrid
            get() = CoordGrid(wall.x + edge.dx, wall.z + edge.dz, wall.level)

        fun other(tile: CoordGrid): CoordGrid? =
            when (tile) {
                wall -> beyond
                beyond -> wall
                else -> null
            }

        /** The tile a knocked-back player lands on: one further from the wall. */
        fun backFrom(tile: CoordGrid): CoordGrid {
            val sign = if (tile == wall) -1 else 1
            return CoordGrid(tile.x + edge.dx * sign, tile.z + edge.dz * sign, tile.level)
        }

        companion object {
            private val byTile = entries.flatMap { listOf(it.wall to it, it.beyond to it) }.toMap()

            fun at(tile: CoordGrid): Trap? = byTile[tile]
        }
    }

    enum class Edge(val dx: Int, val dz: Int) {
        NORTH(0, 1),
        EAST(1, 0),
        SOUTH(0, -1),
        WEST(-1, 0),
    }

    /** A staircase or ladder at [coords] (its south-west tile) and where it takes the player. */
    class Climb(val loc: String, val coords: CoordGrid, val dest: CoordGrid) {
        val up: Boolean
            get() = dest.level > coords.level

        fun matches(bound: BoundLocInfo): Boolean = bound.coords == coords
    }

    enum class Crossing { ENTER, LEAVE }

    companion object {
        const val AGILITY = "stat.agility"
        const val LOW_WALL = "loc.mourning_temple_wall_jump"
        const val ROCK = "loc.mourning_temple_way_down"
        const val ROPE_MULTI = "loc.mourning_temple_way_ropemulti"
        const val ROPE = "obj.rope"
        const val WALL_SUPPORT = "loc.mourning_temple_agility_hanging"
        const val WALL_HOLE = "loc.mourning_temple_light_wall_with_hole"
        const val UPASS_TUNNEL = "loc.cavewalltunnel_to_temple"

        private const val CIRCLE_BASE = "loc.mourning_temple_circle_stairs_base"
        private const val CIRCLE_TOP = "loc.mourning_temple_circle_stairs_top"
        private const val STAIRS_BASE = "loc.mourning_temple_stairs_base"
        private const val STAIRS_TOP = "loc.mourning_temple_stairs_top"
        private const val LADDER = "loc.mourning_temple_ladder_wall"
        private const val LADDER_TOP = "loc.mourning_temple_ladder_wall_top"

        val CLIMBS =
            listOf(
                Climb(CIRCLE_BASE, CoordGrid(1902, 4638, 0), CoordGrid(1901, 4639, 1)),
                Climb(CIRCLE_TOP, CoordGrid(1902, 4638, 1), CoordGrid(1905, 4639, 0)),
                Climb(CIRCLE_BASE, CoordGrid(1887, 4638, 0), CoordGrid(1890, 4639, 1)),
                Climb(CIRCLE_TOP, CoordGrid(1887, 4638, 1), CoordGrid(1886, 4639, 0)),
                Climb(CIRCLE_BASE, CoordGrid(1890, 4641, 1), CoordGrid(1891, 4644, 2)),
                Climb(CIRCLE_TOP, CoordGrid(1890, 4641, 2), CoordGrid(1891, 4640, 1)),
                Climb(CIRCLE_BASE, CoordGrid(1890, 4635, 1), CoordGrid(1891, 4634, 2)),
                Climb(CIRCLE_TOP, CoordGrid(1890, 4635, 2), CoordGrid(1891, 4638, 1)),
                Climb(STAIRS_BASE, CoordGrid(1893, 4658, 1), CoordGrid(1892, 4658, 2)),
                Climb(STAIRS_TOP, CoordGrid(1893, 4658, 2), CoordGrid(1896, 4658, 1)),
                Climb(STAIRS_BASE, CoordGrid(1893, 4620, 1), CoordGrid(1892, 4620, 2)),
                Climb(STAIRS_TOP, CoordGrid(1893, 4620, 2), CoordGrid(1896, 4620, 1)),
                Climb(LADDER, CoordGrid(1898, 4668, 1), CoordGrid(1898, 4666, 2)),
                Climb(LADDER_TOP, CoordGrid(1898, 4667, 2), CoordGrid(1898, 4667, 1)),
                Climb(LADDER, CoordGrid(1898, 4610, 1), CoordGrid(1898, 4612, 2)),
                Climb(LADDER_TOP, CoordGrid(1898, 4610, 2), CoordGrid(1898, 4611, 1)),
            )

        val ROPE_TOP = CoordGrid(1876, 4619, 1)
        val ROPE_BOTTOM = CoordGrid(1876, 4620, 0)
        val SUPPORTS_WEST = CoordGrid(1901, 4612, 1)
        val SUPPORTS_EAST = CoordGrid(1911, 4612, 1)
        const val SUPPORT_JUMPS = 8
        const val SUPPORT_LOW = 64
        const val SUPPORT_HIGH = 272
        const val FALL_DAMAGE = 5

        const val TRAP_LOW = 32
        const val TRAP_HIGH = 352
        const val TRAP_DAMAGE = 5
        const val TRAP_XP = 5.0

        /**
         * The doorway from the mines: the tiles either side of the temple's invisible threshold. A
         * glide lands a tile past the far one, off its trigger, so the player can turn straight back.
         */
        const val DOORWAY_OUTSIDE_X = 1917
        const val DOORWAY_INSIDE_X = 1916
        val DOORWAY_Z = 4638..4640
        const val RUN_STEP = 2

        /**
         * Whether a move from [from] to [to] walks up to the doorway from the mines or from inside.
         * A running step covers two tiles, so the threshold is reached from up to two tiles away.
         */
        fun doorwayCrossing(from: CoordGrid, to: CoordGrid): Crossing? {
            if (to.level != 0 || from.level != 0 || to.z !in DOORWAY_Z || from.chebyshevDistance(to) > RUN_STEP) {
                return null
            }
            return when {
                to.x == DOORWAY_OUTSIDE_X && from.x > DOORWAY_OUTSIDE_X -> Crossing.ENTER
                to.x == DOORWAY_INSIDE_X && from.x < DOORWAY_INSIDE_X -> Crossing.LEAVE
                else -> null
            }
        }

        val UPASS_LANDING = CoordGrid(2311, 9793, 0)
        val TEMPLE_LANDING = CoordGrid(1857, 4639, 0)

        const val CYCLES_PER_TICK = 30
        const val CLIMB_UP_SEQ = "seq.human_reachforladder"
        const val CLIMB_DOWN_SEQ = "seq.human_reachforladdertop"
        const val LOW_WALL_SEQ = "seq.human_lowwall"
        const val TIE_SEQ = "seq.human_pickupfloor"
        const val CRAWL_SEQ = "seq.human_crawling"
        const val HOLD_FIRST_SEQ = "seq.agilityarena_handholds_first"
        const val HOLD_MIDDLE_SEQ = "seq.agilityarena_handholds_middle"
        const val HOLD_LAST_SEQ = "seq.agilityarena_handholds_last"
        const val HOLD_FALL_SEQ = "seq.agilityarena_handholds_middlefall"
        const val HOLD_FIRST_REVERSE_SEQ = "seq.agilityarena_handholds_firstreverse"
        const val HOLD_MIDDLE_REVERSE_SEQ = "seq.agilityarena_handholds_middlereverse"
        const val HOLD_LAST_REVERSE_SEQ = "seq.agilityarena_handholds_lastreverse"
        const val HOLD_FALL_REVERSE_SEQ = "seq.agilityarena_handholds_middlefallreverse"
        const val DODGE_SEQ = "seq.human_dodge_rf_a"
        const val STUMBLE_SEQ = "seq.human_dodge_rf_b"
        const val TRAP_HIT_SEQ = "seq.human_dodge_rf_c"
    }
}
