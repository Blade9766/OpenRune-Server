package org.rsmod.api.death

import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid

/**
 * Lets content choose where a player wakes up after dying. Hooks are asked from the highest
 * [respawnPriority] down and the first non-null coordinate wins; when none answers, the player respawns at
 * the default respawn point. Activities that own a death (minigames, quest arenas) keep the default
 * priority; a player's chosen respawn point answers at [PRIORITY_CHOSEN_POINT], after all of them.
 */
public fun interface PlayerRespawnHook {
    public val respawnPriority: Int
        get() = PRIORITY_DEFAULT

    public fun respawn(player: Player): CoordGrid?

    public companion object {
        public const val PRIORITY_DEFAULT: Int = 0
        public const val PRIORITY_CHOSEN_POINT: Int = -100
    }
}
