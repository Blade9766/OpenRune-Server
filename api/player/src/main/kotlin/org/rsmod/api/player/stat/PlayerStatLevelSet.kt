package org.rsmod.api.player.stat

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.rsmod.annotations.InternalApi
import org.rsmod.api.player.ui.PlayerInterfaceUpdates
import org.rsmod.game.entity.Player
import org.rsmod.game.stat.PlayerSkillXPTable

/**
 * Admin tooling: puts [stat] on exactly the xp of [level] (clamped to the stat's level range),
 * raising or lowering it, with the current level reset to the new base level.
 *
 * Raising ignores the player's personal and global xp rates; [source] decides whether it queues
 * level-ups (none by default). Lowering writes the xp and base level directly, since xp
 * reduction is not a standard gameplay operation.
 */
@OptIn(InternalApi::class)
public fun Player.statSetLevel(stat: String, level: Int, source: XpSource = XpSource.Manual) {
    val type =
        ServerCacheManager.getStats(stat.asRSCM(RSCMType.STAT)) ?: error("No stat found for $stat")
    val target = level.coerceIn(type.minLevel, type.maxLevel)
    val targetXp = PlayerSkillXPTable.getXPFromLevel(target)
    if (statBase(stat) > target) {
        statMap.setCurrentLevel(stat, statMap.getBaseLevel(stat))
        val levelDelta = stat(stat) - target
        statMap.setXP(stat, targetXp)
        statMap.setBaseLevel(stat, target.toByte())
        statSub(stat, constant = levelDelta, percent = 0)
        appearance.combatLevel = PlayerSkillXP.calculateCombatLevel(this)
        PlayerInterfaceUpdates.updateCombatLevel(this)
        return
    }
    val xpDelta = targetXp - statMap.getXP(stat)
    statMap.setCurrentLevel(stat, target.toByte())
    statAdvance(stat, xpDelta.toDouble(), rate = 1.0, globalRate = 1.0, source)
}
