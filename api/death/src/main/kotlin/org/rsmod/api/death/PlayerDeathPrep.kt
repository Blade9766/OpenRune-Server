package org.rsmod.api.death

import org.rsmod.api.player.death.DeathCause
import org.rsmod.api.player.death.recordDeathCause
import org.rsmod.game.entity.Player

public fun Player.preparePvpDeath(killer: Player) {
    attr[LAST_PVP_HIT_TICK_ATTR] = currentMapClock
    recordDeathCause(DeathCause.ByPlayer(killer))
}
