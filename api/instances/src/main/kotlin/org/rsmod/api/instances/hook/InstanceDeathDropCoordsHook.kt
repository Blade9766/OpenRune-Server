package org.rsmod.api.instances.hook

import jakarta.inject.Inject
import org.rsmod.api.death.PlayerDeathDropCoordsHook
import org.rsmod.api.instances.InstanceManager
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid

internal class InstanceDeathDropCoordsHook @Inject constructor(private val manager: InstanceManager) :
    PlayerDeathDropCoordsHook {
    override fun dropCoords(player: Player): CoordGrid? {
        val exit = manager.sessionForPlayer(player)?.placement?.exitCoord ?: return null
        return exit.takeUnless { it == CoordGrid.ZERO }
    }
}
