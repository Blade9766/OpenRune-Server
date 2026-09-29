package org.rsmod.content.bosses.barrows

import jakarta.inject.Inject
import org.rsmod.api.death.PlayerDeathCleanupHook
import org.rsmod.game.entity.Player

internal class BarrowsDeathCleanupHook @Inject constructor(private val spawner: BarrowsNpcSpawner) :
    PlayerDeathCleanupHook {
    override fun cleanup(player: Player) {
        spawner.despawnAll(player)
    }
}
