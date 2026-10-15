package org.rsmod.api.droptable

import org.rsmod.game.entity.Player

private val RINGS_OF_WEALTH: List<String> =
    listOf("obj.ring_of_wealth", "obj.ring_of_wealth_i") +
        (1..5).flatMap { listOf("obj.ring_of_wealth_$it", "obj.ring_of_wealth_i$it") }

public fun Player.wearingRingOfWealth(): Boolean = RINGS_OF_WEALTH.any { it in worn }
