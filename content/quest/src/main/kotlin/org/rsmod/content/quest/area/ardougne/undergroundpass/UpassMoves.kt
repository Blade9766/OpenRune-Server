package org.rsmod.content.quest.area.ardougne.undergroundpass

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
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
internal suspend fun ProtectedAccess.climbOver(dest: CoordGrid, seq: String, ticks: Int = 2) {
    val start = coords
    anim(seq)
    exactMove(
        start = start,
        end = dest,
        delay1 = 0,
        delay2 = ticks * CLIENT_CYCLES_PER_TICK,
        dir = faceTowards(start, dest),
        teleportType = TeleportType.Exempt,
    )
    delay(ticks)
    resetAnim()
}

/** Number of server ticks [seq] plays for, or [fallback] when the cache holds no duration. */
internal fun upassSeqTicks(seq: String, fallback: Int): Int {
    val type = ServerCacheManager.getAnim(seq.asRSCM(RSCMType.SEQ))
    val ticks = type?.tickDuration ?: 0
    return if (ticks > 0) ticks else fallback
}

/**
 * The tile directly across [loc] from where the player is standing. Used by every blocking
 * obstacle that is crossed perpendicular to its own footprint: the rockslides, the pipes, the
 * tunnels through the unicorn doors and the picklocked cell gates.
 */
internal fun ProtectedAccess.acrossFrom(loc: BoundLocInfo): CoordGrid {
    val minX = loc.coords.x
    val maxX = minX + loc.adjustedWidth - 1
    val minZ = loc.coords.z
    val maxZ = minZ + loc.adjustedLength - 1
    val px = coords.x
    val pz = coords.z
    return when {
        px < minX -> CoordGrid(maxX + 1, pz.coerceIn(minZ, maxZ), loc.level)
        px > maxX -> CoordGrid(minX - 1, pz.coerceIn(minZ, maxZ), loc.level)
        pz < minZ -> CoordGrid(px.coerceIn(minX, maxX), maxZ + 1, loc.level)
        pz > maxZ -> CoordGrid(px.coerceIn(minX, maxX), minZ - 1, loc.level)
        // Standing inside the footprint: leave by the nearest edge.
        else -> CoordGrid(px, if (pz - minZ <= maxZ - pz) minZ - 1 else maxZ + 1, loc.level)
    }
}

/** Of [first] and [second], the one the player is not already standing next to. */
internal fun ProtectedAccess.farEndOf(first: CoordGrid, second: CoordGrid): CoordGrid =
    if (coords.chebyshevDistance(first) <= coords.chebyshevDistance(second)) second else first

private fun faceTowards(from: CoordGrid, to: CoordGrid): Int {
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
