package org.rsmod.api.death

import org.rsmod.api.attr.AttributeKey
import org.rsmod.api.player.ironman.PlayerGamemode
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid

public val DEATH_KILLER_ATTR: AttributeKey<Player> = AttributeKey()

public val LAST_PVP_HIT_TICK_ATTR: AttributeKey<Int> = AttributeKey()

public data class PlayerDeathContext(
    val player: Player,
    val coords: CoordGrid,
    val inWilderness: Boolean,
    val wildernessLevel: Int,
    val inRevenantCaves: Boolean,
    val inInstance: Boolean,
    val isSkulled: Boolean,
    val hasProtectItem: Boolean,
    val recentPvpDamage: Boolean,
    val gamemode: Int,
    val killer: Player?,
) {
    val isUIM: Boolean get() = gamemode == PlayerGamemode.ULTIMATE_IRONMAN
    val isHardcoreIronman: Boolean get() = gamemode == PlayerGamemode.HARDCORE_IRONMAN
    val isIronman: Boolean get() = gamemode != PlayerGamemode.NORMAL
    val isPvpDeath: Boolean get() = killer != null || recentPvpDamage

    /** Wilderness PvP deaths follow the regular PvP rules even for an Ultimate Ironman. */
    val usesUimRules: Boolean get() = isUIM && !(inWilderness && isPvpDeath)
}

public data class PlayerDeathHandling(
    val keepCount: Int,
    val dropReceiver: Player?,
    val dropDuration: Int,
    val revealDelay: Int,
    val supplyPile: Boolean,
    val untradeableHandling: UntradeableHandling,
) {
    /** A safe death (POH, Castle Wars, duels...): nothing is lost and it does not count as a real death. */
    val keepsEverything: Boolean get() = keepCount == Int.MAX_VALUE
}

public enum class UntradeableHandling {
    DROP,
    COINS,
    DESTROY,
    KEEP,
}

public const val RECENT_PVP_HIT_TICKS: Int = 600

/** Where a death's drops land when the death tile itself is about to disappear (an instance). */
public fun interface PlayerDeathDropCoordsHook {
    public fun dropCoords(player: Player): CoordGrid?
}

public interface PlayerDeathHook {
    /**
     * Hooks are asked in descending priority and the first non-null handling wins. Activities
     * with their own death rules (safe minigames) sit above [PRIORITY_DEFAULT]; the catch-all
     * standard handling sits at [PRIORITY_FALLBACK] so it can never shadow them.
     *
     * [handleDeath] must be free of side effects: it also builds the Items Kept on Death preview.
     * Work that changes the player belongs in [PlayerDeathItemHook].
     */
    public val priority: Int
        get() = PRIORITY_DEFAULT

    public fun handleDeath(context: PlayerDeathContext): PlayerDeathHandling?

    public companion object {
        public const val PRIORITY_SAFE_ACTIVITY: Int = 100
        public const val PRIORITY_GAMEMODE: Int = 50
        public const val PRIORITY_DEFAULT: Int = 0
        public const val PRIORITY_FALLBACK: Int = -100
    }
}
