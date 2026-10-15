package org.rsmod.api.player.hook

import org.rsmod.game.entity.Player

/**
 * Called on login right after the client's varps have been reset and resent. Client-only values
 * written earlier in login are wiped by that reset, so this is where they belong.
 */
public fun interface PlayerLoginVarsHook {
    public fun onLoginVarsSent(player: Player)
}
