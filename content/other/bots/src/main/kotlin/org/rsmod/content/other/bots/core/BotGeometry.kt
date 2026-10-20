package org.rsmod.content.other.bots.core

import kotlin.math.abs
import kotlin.math.max

object BotGeometry {
    /** Chebyshev distance from tile ([x], [z]) to the tiles on the straight line from a to b. */
    fun distanceToSegment(x: Int, z: Int, ax: Int, az: Int, bx: Int, bz: Int): Int {
        val steps = max(abs(bx - ax), abs(bz - az))
        if (steps == 0) {
            return max(abs(x - ax), abs(z - az))
        }
        var best = Int.MAX_VALUE
        for (i in 0..steps) {
            val px = ax + (bx - ax) * i / steps
            val pz = az + (bz - az) * i / steps
            best = minOf(best, max(abs(x - px), abs(z - pz)))
        }
        return best
    }
}
