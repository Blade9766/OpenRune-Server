package org.rsmod.content.skills.construction.house

import jakarta.inject.Inject
import org.rsmod.api.death.PlayerDeathCleanupHook
import org.rsmod.api.death.PlayerDeathContext
import org.rsmod.api.death.PlayerDeathHandling
import org.rsmod.api.death.PlayerDeathHook
import org.rsmod.api.death.PlayerRespawnHook
import org.rsmod.api.death.UntradeableHandling
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid

/**
 * Dying in a house is safe, as the wiki says: nothing is lost, and the player wakes up outside the
 * portal of the house they died in. An owner who dies at home takes the house down behind them. A
 * fighter who loses in a combat ring is only put out of the ring, and stays in the house.
 */
class HouseDeathHooks
@Inject
constructor(private val registry: HouseRegistry, private val houses: HouseAccess) :
    PlayerDeathHook, PlayerRespawnHook, PlayerDeathCleanupHook {
    private val diedInHouse = HashSet<Player>()

    override val priority: Int
        get() = PlayerDeathHook.PRIORITY_SAFE_ACTIVITY

    override fun handleDeath(context: PlayerDeathContext): PlayerDeathHandling? {
        registry.houseAt(context.coords) ?: return null
        return PlayerDeathHandling(
            keepCount = Int.MAX_VALUE,
            dropReceiver = null,
            dropDuration = 0,
            revealDelay = 0,
            supplyPile = false,
            untradeableHandling = UntradeableHandling.KEEP,
        )
    }

    override fun respawn(player: Player): CoordGrid? {
        val house = registry.houseAt(player.coords) ?: return null
        val ring = registry.ringAt(house, player.coords)
        if (ring != null) {
            return ring.exitFrom(player.coords)
        }
        diedInHouse += player
        return house.state.location.arrive
    }

    override fun cleanup(player: Player) {
        if (diedInHouse.remove(player)) {
            houses.afterDeath(player)
        }
    }
}
