package org.rsmod.content.quest.area.camelot.holygrail

import jakarta.inject.Inject
import org.rsmod.api.death.PlayerDeathContext
import org.rsmod.api.death.PlayerDeathHandling
import org.rsmod.api.death.PlayerDeathHook
import org.rsmod.api.death.PlayerRespawnHook
import org.rsmod.api.death.UntradeableHandling
import org.rsmod.content.quest.area.camelot.holygrail.HolyGrailQuest.Companion.TOWER
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid

/**
 * Dying in the Fisher Realm costs nothing but the walk: everything carried and worn is kept
 * (Excalibur above all), and the player wakes beneath the Brimhaven watchtower, a whistle's blow
 * from another try at the Titan.
 */
class FisherRealmDeath @Inject constructor() : PlayerDeathHook, PlayerRespawnHook {
    override val priority: Int
        get() = PlayerDeathHook.PRIORITY_SAFE_ACTIVITY

    override fun handleDeath(context: PlayerDeathContext): PlayerDeathHandling? {
        if (!FisherRealm.isInRealm(context.coords)) {
            return null
        }
        return PlayerDeathHandling(
            keepCount = Int.MAX_VALUE,
            dropReceiver = null,
            dropDuration = 0,
            revealDelay = 0,
            supplyPile = false,
            untradeableHandling = UntradeableHandling.KEEP,
        )
    }

    override fun respawn(player: Player): CoordGrid? = if (FisherRealm.isInRealm(player.coords)) TOWER else null
}
