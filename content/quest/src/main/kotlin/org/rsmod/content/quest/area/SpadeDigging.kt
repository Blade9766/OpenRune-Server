package org.rsmod.content.quest.area

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.hook.SpadeDigHook
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid

/**
 * Quest dig sites for the spade's "Dig" option. Quests that have something buried register the
 * tile here from their own `startup`; on those tiles the quest's handler runs the whole dig, and
 * anywhere else the generic spade script turns over the soil.
 */
@Singleton
class SpadeDigging @Inject constructor() : SpadeDigHook {
    private val sites = mutableListOf<DigSite>()

    fun register(tile: CoordGrid, radius: Int = 1, handler: suspend ProtectedAccess.() -> Unit) {
        sites += DigSite(tile, radius, handler)
    }

    override fun claims(player: Player): Boolean = siteAt(player) != null

    override suspend fun ProtectedAccess.beforeDig(): Boolean {
        val site = siteAt(player) ?: return true
        site.handler(this)
        return false
    }

    override suspend fun ProtectedAccess.dig() {}

    private fun siteAt(player: Player): DigSite? =
        sites.firstOrNull {
            player.coords.level == it.tile.level &&
                player.coords.chebyshevDistance(it.tile) <= it.radius
        }

    private class DigSite(
        val tile: CoordGrid,
        val radius: Int,
        val handler: suspend ProtectedAccess.() -> Unit,
    )
}
