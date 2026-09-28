package org.rsmod.content.skills.hunter.netting

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.map.CoordGrid

/**
 * Overworld impling spawns. Every [CYCLE_TICKS] all wandering implings despawn and a new batch of
 * invisible precursors is placed: four high-tier, N mid-tier and 2N low-tier (N in 5..10) on random
 * variable spawn points, plus one low-tier on each fixed low point and one crystal precursor in
 * Prifddinas. Precursors wander invisibly for [REVEAL_TICKS] before turning into an impling rolled
 * from their tier; a caught impling immediately restarts its spawn point with a new precursor.
 */
@Singleton
class ImplingSpawner
@Inject
constructor(
    private val npcRepo: NpcRepository,
    private val mapClock: MapClock,
    private val random: GameRandom,
) {
    private class Spawn(val tier: ImplingTier, val point: CoordGrid) {
        var npc: Npc? = null
        var revealCycle: Int = 0
    }

    private val spawns = mutableListOf<Spawn>()
    private var nextCycle = 0

    fun tick() {
        if (mapClock >= nextCycle) {
            startCycle()
            return
        }
        for (spawn in spawns) {
            if (spawn.revealCycle != 0 && mapClock >= spawn.revealCycle) {
                reveal(spawn)
            }
        }
    }

    fun onCaught(npc: Npc): Boolean {
        val spawn = spawns.firstOrNull { it.npc === npc } ?: return false
        remove(npc)
        spawnPrecursor(spawn)
        return true
    }

    private fun startCycle() {
        nextCycle = mapClock + CYCLE_TICKS
        spawns.forEach { spawn -> spawn.npc?.let(::remove) }
        spawns.clear()

        val mid = random.of(5, 10)
        repeat(HIGH_TIER_COUNT) { spawns += Spawn(ImplingTier.High, variablePoint()) }
        repeat(mid) { spawns += Spawn(ImplingTier.Mid, variablePoint()) }
        repeat(mid * 2) { spawns += Spawn(ImplingTier.Low, variablePoint()) }
        ImplingSpawns.lowTier.forEach { spawns += Spawn(ImplingTier.Low, it) }
        spawns += Spawn(ImplingTier.Crystal, ImplingSpawns.crystal.random(random))

        spawns.forEach(::spawnPrecursor)
    }

    private fun spawnPrecursor(spawn: Spawn) {
        val npc = Npc(spawn.tier.precursor, spawn.point)
        npcRepo.add(npc, Int.MAX_VALUE)
        spawn.npc = npc
        spawn.revealCycle = mapClock + REVEAL_TICKS
    }

    private fun reveal(spawn: Spawn) {
        val precursor = spawn.npc ?: return
        val coords = precursor.coords
        remove(precursor)
        val impling = rollImpling(spawn.tier)
        val npc = Npc(impling.npcs.random(random), coords)
        npcRepo.add(npc, Int.MAX_VALUE)
        spawn.npc = npc
        spawn.revealCycle = 0
    }

    private fun rollImpling(tier: ImplingTier): Impling {
        var roll = random.of(tier.weights.sumOf { it.second })
        for ((impling, weight) in tier.weights) {
            if (roll < weight) {
                return impling
            }
            roll -= weight
        }
        return tier.weights.last().first
    }

    private fun variablePoint(): CoordGrid = ImplingSpawns.variable.random(random)

    private fun remove(npc: Npc) {
        if (npc.isSlotAssigned) {
            npcRepo.del(npc, Int.MAX_VALUE)
        }
    }

    private fun <T> List<T>.random(random: GameRandom): T = this[random.of(size)]

    private companion object {
        const val CYCLE_TICKS = 3000
        const val REVEAL_TICKS = 200
        const val HIGH_TIER_COUNT = 4
    }
}
