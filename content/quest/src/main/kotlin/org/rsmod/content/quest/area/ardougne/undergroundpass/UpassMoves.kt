package org.rsmod.content.quest.area.ardougne.undergroundpass

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.BasType
import kotlin.math.sign
import org.rsmod.api.config.constants
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid

/** Client cycles (20ms) in one server tick; `exactmove` delays are expressed in client cycles. */
private const val CLIENT_CYCLES_PER_TICK = 30

/**
 * Glides the player from where they stand to [dest] over [ticks] while [seq] plays, then leaves
 * them standing on [dest]. The obstacles of the pass all sit on tiles the routefinder refuses, so
 * every one of them has to be crossed this way rather than walked.
 */
internal suspend fun ProtectedAccess.climbOver(
    dest: CoordGrid,
    seq: String,
    ticks: Int = 2,
    startDelay: Int = 0,
) {
    val start = coords
    anim(seq)
    exactMove(
        start = start,
        end = dest,
        delay1 = startDelay,
        delay2 = ticks * CLIENT_CYCLES_PER_TICK,
        dir = faceTowards(start, dest),
        teleportType = TeleportType.Exempt,
    )
    delay(ticks)
    resetAnim()
}

/**
 * Replaces the player's stand and walk animations with [walk] (and [ready] when stood still) until
 * [clearWalkStyle] is called: the sidestep along the ledge and the drowning in the swamp.
 */
internal fun ProtectedAccess.setWalkStyle(walk: String, ready: String = walk) {
    val readyId = ready.asRSCM(RSCMType.SEQ)
    val walkId = walk.asRSCM(RSCMType.SEQ)
    player.bas =
        BasType(
            id = -1,
            readyAnim = readyId,
            turnOnSpot = readyId,
            walkForward = walkId,
            walkBack = walkId,
            walkLeft = walkId,
            walkRight = walkId,
            running = walkId,
        )
    rebuildAppearance()
}

internal fun ProtectedAccess.clearWalkStyle() {
    if (player.bas != null) {
        player.bas = null
        rebuildAppearance()
    }
}

/**
 * Steps the player through [tiles] one a tick. Each step is a teleport move, which the client draws
 * as a walk but which ignores collision, so the player cannot be pushed off a ledge or out of a
 * forced walk by their own clicks.
 */
internal suspend fun ProtectedAccess.stepThrough(tiles: List<CoordGrid>) {
    for (tile in tiles) {
        if (coords == tile) {
            continue
        }
        teleport(tile, TeleportType.Exempt)
        delay(1)
    }
}

/** Every tile in a straight line from the player to [dest], [dest] included. */
internal fun ProtectedAccess.lineTo(dest: CoordGrid): List<CoordGrid> {
    val tiles = mutableListOf<CoordGrid>()
    var x = coords.x
    var z = coords.z
    while (x != dest.x || z != dest.z) {
        x += (dest.x - x).sign
        z += (dest.z - z).sign
        tiles += CoordGrid(x, z, dest.level)
    }
    return tiles
}

/**
 * The tile directly across [loc] from where the player is standing. Used by every blocking
 * obstacle that is crossed perpendicular to its own footprint: the rockslides, the pipes and the
 * picklocked cell gates.
 */
internal fun ProtectedAccess.acrossFrom(loc: BoundLocInfo): CoordGrid {
    val minX = loc.coords.x
    val maxX = minX + loc.adjustedWidth - 1
    val minZ = loc.coords.z
    val maxZ = minZ + loc.adjustedLength - 1
    val px = coords.x
    val pz = coords.z
    return when {
        px < minX -> CoordGrid(maxX + 1, pz.coerceIn(minZ, maxZ), coords.level)
        px > maxX -> CoordGrid(minX - 1, pz.coerceIn(minZ, maxZ), coords.level)
        pz < minZ -> CoordGrid(px.coerceIn(minX, maxX), maxZ + 1, coords.level)
        pz > maxZ -> CoordGrid(px.coerceIn(minX, maxX), minZ - 1, coords.level)
        // Standing inside the footprint: leave by the nearest edge.
        else -> CoordGrid(px, if (pz - minZ <= maxZ - pz) minZ - 1 else maxZ + 1, coords.level)
    }
}

internal fun faceTowards(from: CoordGrid, to: CoordGrid): Int {
    val dx = (to.x - from.x).sign
    val dz = (to.z - from.z).sign
    return when {
        dx == 0 && dz > 0 -> constants.em_face_north
        dx == 0 && dz < 0 -> constants.em_face_south
        dx > 0 && dz == 0 -> constants.em_face_east
        dx < 0 && dz == 0 -> constants.em_face_west
        dx > 0 && dz > 0 -> constants.em_face_northeast
        dx < 0 && dz > 0 -> constants.em_face_northwest
        dx > 0 && dz < 0 -> constants.em_face_southeast
        dx < 0 && dz < 0 -> constants.em_face_southwest
        else -> constants.em_face_south
    }
}
