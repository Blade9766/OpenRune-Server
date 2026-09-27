package org.rsmod.content.other.castlewars

import kotlin.math.sign
import org.rsmod.api.config.constants
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.map.CoordGrid

private const val CLIENT_CYCLES_PER_TICK = 30

/** Slides the player onto [dest] over [ticks] while [seq] plays, and waits for them to land. */
internal suspend fun ProtectedAccess.glide(dest: CoordGrid, seq: String, ticks: Int) {
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

private fun facing(from: CoordGrid, to: CoordGrid): Int =
    when ((to.x - from.x).sign to (to.z - from.z).sign) {
        0 to 1 -> constants.em_face_north
        1 to 1 -> constants.em_face_northeast
        1 to 0 -> constants.em_face_east
        1 to -1 -> constants.em_face_southeast
        0 to -1 -> constants.em_face_south
        -1 to -1 -> constants.em_face_southwest
        -1 to 0 -> constants.em_face_west
        else -> constants.em_face_northwest
    }
