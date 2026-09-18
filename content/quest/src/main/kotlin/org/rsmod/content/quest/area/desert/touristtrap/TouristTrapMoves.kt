package org.rsmod.content.quest.area.desert.touristtrap

import kotlin.math.sign
import org.rsmod.api.config.constants
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.map.CoordGrid

/** Client cycles in one server tick; `exactmove` delays are counted in client cycles. */
private const val CLIENT_CYCLES_PER_TICK = 30

/**
 * Carries the player from their tile to [dest] over [ticks] while [seq] plays: squeezing through
 * the cell window, scrambling over the rock and up and down the cliff, none of which the
 * routefinder can walk.
 */
internal suspend fun ProtectedAccess.glideTo(dest: CoordGrid, seq: String, ticks: Int) {
    val start = coords
    anim(seq)
    exactMove(
        start = start,
        end = dest,
        delay1 = 0,
        delay2 = ticks * CLIENT_CYCLES_PER_TICK,
        dir = facing(start, dest),
        teleportType = TeleportType.Exempt,
    )
    delay(ticks)
}

private fun facing(from: CoordGrid, to: CoordGrid): Int {
    val dx = (to.x - from.x).sign
    val dz = (to.z - from.z).sign
    return when {
        dx == 0 && dz > 0 -> constants.em_face_north
        dx == 0 && dz < 0 -> constants.em_face_south
        dx > 0 && dz == 0 -> constants.em_face_east
        dx < 0 && dz == 0 -> constants.em_face_west
        dx > 0 && dz > 0 -> constants.em_face_northeast
        dx < 0 && dz > 0 -> constants.em_face_northwest
        dx > 0 -> constants.em_face_southeast
        else -> constants.em_face_southwest
    }
}
