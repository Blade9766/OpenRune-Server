package org.rsmod.content.quest.area.wilderness.magearena

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.random.GameRandom
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid

/**
 * Where the three god followers hide. Each player gets their own draw of three distinct places
 * from [SPAWN_POINTS], redrawn every [ROTATION_TICKS]; the real game only counts time spent in
 * the Wilderness towards that, this counts server time.
 */
@Singleton
class FollowerSpawns
@Inject
constructor(
    private val mageArena2: MageArena2Quest,
    private val mapClock: MapClock,
    private val random: GameRandom,
) {
    /**
     * Makes sure the player has a current draw. Returns true when an existing draw was replaced,
     * which is when the symbol should mention that the creatures have moved.
     */
    fun ensureRotation(player: Player): Boolean {
        val last = mageArena2.spawnRotation.get(player)
        val unset = last < 0 || God.entries.any { mageArena2.spawnIndex.getValue(it).get(player) < 0 }
        // A last-rotation cycle in the future means the server restarted since; draw again.
        val stale = last > mapClock.cycle || mapClock.cycle - last >= ROTATION_TICKS
        if (!unset && !stale) {
            return false
        }
        rotate(player)
        return !unset
    }

    fun rotate(player: Player) {
        val picks = mutableListOf<Int>()
        while (picks.size < God.entries.size) {
            val pick = random.of(SPAWN_POINTS.size)
            if (pick !in picks) {
                picks += pick
            }
        }
        for ((index, god) in God.entries.withIndex()) {
            mageArena2.spawnIndex.getValue(god).set(player, picks[index])
        }
        mageArena2.spawnRotation.set(player, mapClock.cycle)
    }

    fun spawnTile(player: Player, god: God): CoordGrid {
        val index = mageArena2.spawnIndex.getValue(god).get(player).coerceIn(0, SPAWN_POINTS.lastIndex)
        return SPAWN_POINTS[index]
    }

    companion object {
        /** Forty-five minutes. */
        const val ROTATION_TICKS = 4500

        /** The circles on the OSRS wiki's "God followers spawn locations" map, deep Wilderness. */
        val SPAWN_POINTS =
            listOf(
                CoordGrid(3172, 3898, 0),
                CoordGrid(3304, 3942, 0),
                CoordGrid(3296, 3876, 0),
                CoordGrid(3260, 3886, 0),
                CoordGrid(3233, 3909, 0),
                CoordGrid(3215, 3883, 0),
                CoordGrid(3231, 3873, 0),
                CoordGrid(3276, 3842, 0),
                CoordGrid(3248, 3831, 0),
                CoordGrid(3261, 3800, 0),
                CoordGrid(3168, 3796, 0),
                CoordGrid(3158, 3842, 0),
                CoordGrid(3022, 3831, 0),
                CoordGrid(3150, 3878, 0),
                CoordGrid(3334, 3903, 0),
                CoordGrid(3172, 3866, 0),
                CoordGrid(3282, 3821, 0),
            )
    }
}
