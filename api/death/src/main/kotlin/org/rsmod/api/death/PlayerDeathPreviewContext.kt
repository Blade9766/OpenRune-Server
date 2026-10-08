package org.rsmod.api.death

import org.rsmod.api.area.checker.isInWildernessBasic
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid

public object PlayerDeathPreviewContext {
    public fun create(
        player: Player,
        protectItem: Boolean,
        skulled: Boolean,
        playerKill: Boolean,
        wildernessLevel: Int,
        inInstance: Boolean,
        inRevenantCaves: Boolean,
        gamemode: Int,
    ): PlayerDeathContext {
        val inWilderness = wildernessLevel > 0
        return PlayerDeathContext(
            player = player,
            coords = player.coords,
            inWilderness = inWilderness,
            wildernessLevel = if (inWilderness) wildernessLevel else -1,
            inRevenantCaves = inRevenantCaves && inWilderness,
            inInstance = inInstance,
            isSkulled = skulled,
            hasProtectItem = protectItem,
            recentPvpDamage = playerKill,
            gamemode = gamemode,
            killer = if (playerKill) player else null,
        )
    }
}

/** The Wilderness level used by the death rules, or `-1` outside the Wilderness. */
public fun CoordGrid.deathWildernessLevel(): Int {
    if (!isInWildernessBasic()) return -1
    val y = z
    return when {
        level == 0 && x in 2944..3392 && y in 3520..4351 -> ((y - 3520) shr 3) + 1
        level == 0 && x in 3008..3071 && y in 10112..10175 -> ((y - 9920) shr 3) - 1
        level == 0 && x in 2944..3455 && y in 9920..10879 -> ((y - 9920) shr 3) + 1
        else -> 1
    }
}
