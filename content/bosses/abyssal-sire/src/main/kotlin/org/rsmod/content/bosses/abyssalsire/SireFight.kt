package org.rsmod.content.bosses.abyssalsire

import java.util.IdentityHashMap
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid

enum class SireStage {
    Waking,
    Lungs,
    WalkingToMelee,
    Melee,
    WalkingToCentre,
    Panic,
    Apocalypse,
    Dying,
}

/** One player's fight against one Sire. Cycle fields are map clock cycles. */
class SireFight(val sire: Npc, val chamber: SireChamber, var hero: Player, now: Int) {
    var stage: SireStage = SireStage.Waking
    var stageStartedAt: Int = now

    /** Ranged and magic damage dealt since the last disorientation; 75 disorients the Sire. */
    var stunDamage: Int = 0
    var pendingStun: Boolean = false
    var sireStunnedUntil: Int = -1
    var tentaclesStunnedUntil: Int = -1

    var nextAttack: Int = now
    var heroLastActive: Int = now
    var heroLastHitSire: Int = now
    var poolsSinceExplosion: Int = 0
    var lungXpGiven: Int = 0
    var walkDeadline: Int = -1
    var walkLastCoords: CoordGrid? = null
    var walkStillTicks: Int = 0
    var onArrival: (() -> Unit)? = null

    val lungs: MutableList<Npc> = mutableListOf()
    val tentacles: MutableList<Npc> = mutableListOf()

    /** Living spawns and scions, with the cycle each spawn appeared (scions keep theirs). */
    val minions: MutableMap<Npc, SireMinion> = IdentityHashMap()

    /** Miasma pools: centre tile to the last cycle it still burns. */
    val pools: MutableMap<CoordGrid, Int> = HashMap()

    fun sireStunned(now: Int): Boolean = now < sireStunnedUntil

    fun tentaclesStunned(now: Int): Boolean = now < tentaclesStunnedUntil

    fun tentaclesActive(now: Int): Boolean =
        when (stage) {
            SireStage.Waking,
            SireStage.Lungs -> !tentaclesStunned(now)
            SireStage.Panic,
            SireStage.Apocalypse -> true
            else -> false
        }

    fun inTransition(): Boolean =
        stage == SireStage.WalkingToMelee || stage == SireStage.WalkingToCentre

    fun aliveMinions(): Int = minions.keys.count { it.isSlotAssigned && it.hitpoints > 0 }
}

class SireMinion(val spawnedAt: Int) {
    var lastInReach: Int = spawnedAt
}
