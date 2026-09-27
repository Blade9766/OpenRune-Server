package org.rsmod.content.other.combatachievements

import jakarta.inject.Singleton
import org.rsmod.api.player.stat.prayerLvl
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.Hit
import org.rsmod.game.hit.HitType
import org.rsmod.map.CoordGrid

/**
 * What each player has done against the npc they are currently fighting. A fight starts on the
 * player's first hit on an npc and is replaced when they hit a different one, so a task condition
 * sees only the fight that ended in the kill.
 */
@Singleton
class CombatFights {
    private val fights = HashMap<Long, Fight>()
    private val streaks = HashMap<Long, Streak>()
    private val recentKills = HashMap<Long, ArrayDeque<Kill>>()

    fun of(player: Player): Fight? = fights[player.accountHash]

    fun onPlayerHitNpc(player: Player, npc: Npc, hit: Hit, clock: Int) {
        val current = fights[player.accountHash]
        val fight =
            if (current != null && current.npcSlot == npc.slotId && current.npcType == npc.id) {
                current
            } else {
                Fight(npc.slotId, npc.id, clock, player.prayerLvl).also {
                    fights[player.accountHash] = it
                }
            }
        fight.styles += hit.type
        hit.righthandType()?.let { fight.weapons += it.id }
        if (hit.damage > 0) {
            fight.damagingHits++
        }
        fight.notePrayer(player)
    }

    fun onNpcHitPlayer(player: Player, npc: Npc, hit: Hit) {
        val fight = fights[player.accountHash] ?: return
        if (fight.npcSlot != npc.slotId || fight.npcType != npc.id) {
            return
        }
        fight.damageTaken += hit.damage
        fight.notePrayer(player)
    }

    fun end(player: Player) {
        fights.remove(player.accountHash)
    }

    /**
     * Records a kill and returns how many consecutive kills of this npc type the player has made
     * without moving away from where they killed the previous one, standing in for "without
     * leaving" the lair.
     */
    fun recordKill(player: Player, npc: Npc, clock: Int): Int {
        val kills = recentKills.getOrPut(player.accountHash) { ArrayDeque() }
        kills.addLast(Kill(npc.name.lowercase(), clock))
        while (kills.isNotEmpty() && clock - kills.first().clock > RECENT_WINDOW) {
            kills.removeFirst()
        }
        val key = player.accountHash
        val previous = streaks[key]
        val stayed =
            previous != null &&
                previous.npcType == npc.id &&
                previous.coords.level == player.coords.level &&
                previous.coords.chebyshevDistance(player.coords) <= STREAK_RADIUS
        val count = if (stayed) previous!!.count + 1 else 1
        streaks[key] = Streak(npc.id, player.coords, count)
        return count
    }

    /** How many npcs named [name] the player has killed in the last [ticks] ticks. */
    fun recentKills(player: Player, name: String, clock: Int, ticks: Int): Int =
        recentKills[player.accountHash]?.count { it.name == name && clock - it.clock <= ticks } ?: 0

    fun forget(player: Player) {
        fights.remove(player.accountHash)
        streaks.remove(player.accountHash)
        recentKills.remove(player.accountHash)
    }

    class Fight(val npcSlot: Int, val npcType: Int, val startClock: Int, startPrayer: Int) {
        val styles = mutableSetOf<HitType>()
        val weapons = mutableSetOf<Int>()
        var damageTaken = 0
        var damagingHits = 0
        private val prayerAtStart = startPrayer
        var prayerLost = false
            private set

        fun notePrayer(player: Player) {
            if (player.prayerLvl < prayerAtStart) {
                prayerLost = true
            }
        }
    }

    private data class Streak(val npcType: Int, val coords: CoordGrid, val count: Int)

    private data class Kill(val name: String, val clock: Int)

    private companion object {
        const val STREAK_RADIUS = 40
        const val RECENT_WINDOW = 20
    }
}
