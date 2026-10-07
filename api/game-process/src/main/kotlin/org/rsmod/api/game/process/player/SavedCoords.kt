package org.rsmod.api.game.process.player

import org.rsmod.api.attr.AttributeKey
import org.rsmod.api.registry.region.RegionRegistry
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid

private val LOGIN_EXIT_COORD: AttributeKey<Int> = AttributeKey(persistenceKey = "instance_exit_coord")

/**
 * Places a returning character for a login or a world-type switch: at the exit an instance left them,
 * or at [spawn] when their save points into an instance that no longer exists.
 */
public fun Player.restoreSavedCoords(spawn: CoordGrid) {
    val exit = attr[LOGIN_EXIT_COORD]
    if (exit != null) {
        coords = CoordGrid(exit)
        attr.remove(LOGIN_EXIT_COORD)
    }
    recoverAbandonedInstance(spawn)
}

internal fun Player.recoverAbandonedInstance(spawn: CoordGrid) {
    if (coords in RegionRegistry.workingAreaSmall || coords in RegionRegistry.workingAreaLarge) {
        coords = spawn
    }
}
