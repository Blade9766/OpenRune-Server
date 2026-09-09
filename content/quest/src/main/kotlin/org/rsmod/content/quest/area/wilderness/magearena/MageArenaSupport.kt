package org.rsmod.content.quest.area.wilderness.magearena

import org.rsmod.api.player.back
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.righthand
import org.rsmod.game.entity.Player
import org.rsmod.game.map.collision.isWalkBlocked
import org.rsmod.game.map.collision.isZoneValid
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.collision.CollisionFlagMap

/* Places and helpers shared by the Mage Arena scripts. */

object MageArenaCoords {
    /**
     * The whole arena at ground level: the raised octagon the battle mages patrol plus the ring
     * corridor around it. God spell casts only count as training inside this box.
     */
    fun isInArena(coords: CoordGrid): Boolean =
        coords.level == 0 && coords.x in ARENA_MIN_X..ARENA_MAX_X && coords.z in ARENA_MIN_Z..ARENA_MAX_Z

    private const val ARENA_MIN_X = 3082
    private const val ARENA_MAX_X = 3129
    private const val ARENA_MIN_Z = 3912
    private const val ARENA_MAX_Z = 3955

    /** Where the duel with Kolodion starts: the player south of him, in the middle of the arena. */
    val FIGHT_PLAYER = CoordGrid(3105, 3928, 0)
    val FIGHT_KOLODION = CoordGrid(3105, 3932, 0)

    /** The lever by the arena wall (3104,3956) drops the player into the ring corridor. */
    val ARENA_INSIDE = CoordGrid(3105, 3951, 0)

    /** The lever in the ring corridor (3105,3952) puts the player back outside by the wall. */
    val ARENA_OUTSIDE = CoordGrid(3105, 3956, 0)

    /** Beside the lever in the west room of the ruined house (3090,3956). */
    val HOUSE_LEVER_ARRIVAL = CoordGrid(3091, 3956, 0)

    /** Beside the lever on the south wall of the bank cave (2539,4712). */
    val CAVE_LEVER_ARRIVAL = CoordGrid(2539, 4713, 0)

    /** In front of Kolodion in the bank cave, where the winner of the duel lands. */
    val CAVE_KOLODION_ARRIVAL = CoordGrid(2540, 4716, 0)

    /** North of the sparkling pool in the god statue chamber (pool at 2508-2510, 4686-4688). */
    val POOL_TO_CHAMBER = CoordGrid(2509, 4690, 0)

    /** South of the sparkling pool in the bank cave (pool at 2541-2543, 4719-4721). */
    val POOL_TO_BANK = CoordGrid(2542, 4718, 0)

    const val KOLODION = "npc.arenamage1"
}

/** The walkable tile closest to [center] within [radius], or null if there is none. */
fun CollisionFlagMap.nearestFree(center: CoordGrid, radius: Int = 2): CoordGrid? {
    val tiles = mutableListOf<CoordGrid>()
    for (dz in -radius..radius) {
        for (dx in -radius..radius) {
            tiles += center.translate(dx, dz)
        }
    }
    return tiles
        .sortedWith(compareBy({ it.chebyshevDistance(center) }, { it.z }, { it.x }))
        .firstOrNull(::walkable)
}

/**
 * The south-west tile of a [size]x[size] footprint of walkable tiles closest to [center], or null
 * if nothing that large fits within [radius].
 */
fun CollisionFlagMap.freeFootprint(center: CoordGrid, size: Int, radius: Int = 4): CoordGrid? {
    val candidates = mutableListOf<CoordGrid>()
    for (dz in -radius..radius) {
        for (dx in -radius..radius) {
            candidates += center.translate(dx - size / 2, dz - size / 2)
        }
    }
    return candidates
        .sortedWith(compareBy({ it.chebyshevDistance(center) }, { it.z }, { it.x }))
        .firstOrNull { southWest ->
            (0 until size).all { dx -> (0 until size).all { dz -> walkable(southWest.translate(dx, dz)) } }
        }
}

/**
 * The walkable tiles touching a [size]x[size] footprint whose south-west corner is [southWest],
 * closest to [near] first.
 */
fun CollisionFlagMap.tilesAround(southWest: CoordGrid, size: Int, near: CoordGrid): List<CoordGrid> {
    val tiles = mutableListOf<CoordGrid>()
    for (dx in -1..size) {
        for (dz in -1..size) {
            val inside = dx in 0 until size && dz in 0 until size
            if (!inside) {
                tiles += southWest.translate(dx, dz)
            }
        }
    }
    return tiles.filter(::walkable).sortedBy { it.chebyshevDistance(near) }
}

fun CollisionFlagMap.walkable(coords: CoordGrid): Boolean = isZoneValid(coords) && !isWalkBlocked(coords)

/** The god whose cape the player is wearing, if any. */
fun Player.wornCapeGod(): God? = back?.let { God.byCape(it.id) }

/** The god whose cape the player is wearing or carrying, if any. */
fun Player.carriedCapeGod(): God? = wornCapeGod() ?: God.entries.firstOrNull { inv.contains(it.cape) }

/** The god whose staff (or an equivalent weapon) the player is wielding, if any. */
fun Player.wieldedStaffGod(): God? = righthand?.let { God.byStaff(it.id) }

fun Player.isProtectingFromMagic(): Boolean = vars["varbit.prayer_protectfrommagic"] > 0

/** Compass direction from [from] to [to], as used by the enchanted symbol's hints. */
fun compassDirection(from: CoordGrid, to: CoordGrid): String {
    val dx = to.x - from.x
    val dz = to.z - from.z
    val ns = when {
        dz > 0 -> "north"
        dz < 0 -> "south"
        else -> ""
    }
    val ew = when {
        dx > 0 -> "east"
        dx < 0 -> "west"
        else -> ""
    }
    // Ignore the weaker axis when the target is nearly straight along the other one.
    val diagonal = ns.isNotEmpty() && ew.isNotEmpty() && minOf(kotlin.math.abs(dx), kotlin.math.abs(dz)) * 2 >= maxOf(kotlin.math.abs(dx), kotlin.math.abs(dz))
    return when {
        diagonal -> "$ns-$ew"
        kotlin.math.abs(dz) >= kotlin.math.abs(dx) && ns.isNotEmpty() -> ns
        ew.isNotEmpty() -> ew
        else -> ns
    }
}

/**
 * The standard teleport: the casting animation and graphic, then the jump. Scripted moves are
 * exempt from the teleport validators; levers and pools apply their own tele-block check.
 */
suspend fun ProtectedAccess.magicTeleport(dest: CoordGrid) {
    anim("seq.human_castteleport")
    spotanim("spotanim.teleport_casting", height = TELEPORT_GFX_HEIGHT)
    soundSynth("synth.teleport_all")
    delay(TELEPORT_DELAY)
    telejump(dest, TeleportType.Exempt)
    resetAnim()
}

private const val TELEPORT_GFX_HEIGHT = 92
private const val TELEPORT_DELAY = 2
