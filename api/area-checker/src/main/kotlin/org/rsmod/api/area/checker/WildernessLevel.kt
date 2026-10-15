package org.rsmod.api.area.checker

import org.rsmod.map.CoordGrid

public fun CoordGrid.wildernessLevel(areaChecker: AreaChecker): Int {
    if (!isInWilderness(areaChecker)) {
        return -1
    }
    return wildernessLevelAt(x, z).takeIf { it > 0 } ?: 1
}

/** Mirrors the client's `wilderness_level` proc, which ignores the plane. */
internal fun wildernessLevelAt(x: Int, z: Int): Int {
    val mx = x shr 6
    val mz = z shr 6
    val lx = x and 63
    val lz = z and 63
    return when {
        mx in 52..54 && mz in 62..64 && !(mx == 53 && mz == 63 && lx in 21..42 && lz in 21..42) -> 5
        mx in 46..52 && mz in 55..67 -> (z - 55 * 64) / 8 + 1
        mx == 47 && mz == 158 -> (z - 155 * 64) / 8 - 1
        mx == 51 && mz == 159 -> 35
        mx == 53 && mz == 159 -> 35
        mx == 52 && mz == 161 -> 40
        mx == 27 && mz == 180 -> 21
        mx == 29 && mz == 180 -> 21
        mx == 25 && mz == 180 -> 29
        mx == 52 && mz == 160 -> 33 + (lz - 6) * 7 / 50
        mx in 46..53 && mz in 155..169 -> (z - 155 * 64) / 8 + 1
        else -> 0
    }
}
