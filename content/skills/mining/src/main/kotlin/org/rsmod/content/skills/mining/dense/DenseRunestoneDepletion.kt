package org.rsmod.content.skills.mining.dense

import kotlin.math.max
import kotlin.math.min

object DenseRunestoneDepletion {
    const val ROLL_RANGE = 1000
    private const val MIN_LEVEL = 38
    private const val MAX_LEVEL = 99
    private const val SURVIVE_AT_MIN = 855
    private const val SURVIVE_AT_MAX = 902

    /** Out of [ROLL_RANGE]: the chance a runestone survives one mined block, 85.5% to 90.2%. */
    fun surviveChance(miningLevel: Int): Int {
        val level = min(MAX_LEVEL, max(MIN_LEVEL, miningLevel))
        val span = SURVIVE_AT_MAX - SURVIVE_AT_MIN
        return SURVIVE_AT_MIN + (level - MIN_LEVEL) * span / (MAX_LEVEL - MIN_LEVEL)
    }

    fun depletes(miningLevel: Int, roll: Int): Boolean = roll >= surviveChance(miningLevel)
}
