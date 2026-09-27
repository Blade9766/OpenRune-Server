package org.rsmod.content.other.barrows

import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.random.GameRandom
import org.rsmod.game.entity.Player

/**
 * One player's progress through a Barrows run. Everything lives in vars so a run survives
 * logging out: `varp.barrows_kills` (brothers slain, reward potential, chest opened) and the door
 * bits of `varp.barrows` are the cache's own and drive the overlay and door multilocs; the
 * tunnel brother, puzzle and looted flag sit in the custom `varp.barrows_run`.
 */
object BarrowsRun {
    const val KILLED_MONSTER = "varbit.barrows_killed_monster"
    const val CHEST_OPEN = "varbit.barrows_chest_open"
    const val TUNNEL_BROTHER = "varbit.barrows_tunnel_brother"
    const val PUZZLE_TYPE = "varbit.barrows_puzzle_type"
    const val PUZZLE_ANSWER = "varbit.barrows_puzzle_answer"
    const val PUZZLE_SOLVED = "varbit.barrows_puzzle_solved"
    const val CHEST_LOOTED = "varbit.barrows_chest_looted"
    const val LADDER_VISIBLE = "varbit.barrows_ladder_visible"
    const val CHEST_COUNT = "varp.total_barrows_chests"

    const val MAX_KILL_POTENTIAL = 1000
    const val POTENTIAL_PER_BROTHER = 2

    fun killed(player: Player): List<Brother> =
        Brother.entries.filter { player.vars[it.killedVarbit] == 1 }

    fun isKilled(player: Player, brother: Brother): Boolean = player.vars[brother.killedVarbit] == 1

    fun tunnelBrother(player: Player): Brother? = Brother.ofCode(player.vars[TUNNEL_BROTHER])

    fun killPotential(player: Player): Int = player.vars[KILLED_MONSTER]

    fun rewardPotential(player: Player): Int =
        killPotential(player) + killed(player).size * POTENTIAL_PER_BROTHER

    fun isChestOpen(player: Player): Boolean = player.vars[CHEST_OPEN] == 1

    fun isLooted(player: Player): Boolean = player.vars[CHEST_LOOTED] == 1

    fun isPuzzleSolved(player: Player): Boolean = player.vars[PUZZLE_SOLVED] == 1

    fun isLocked(player: Player, corridor: TunnelCorridor): Boolean =
        player.vars[corridor.varbit] == 1

    fun lockedCorridors(player: Player): Set<TunnelCorridor> =
        TunnelCorridor.entries.filterTo(mutableSetOf()) { isLocked(player, it) }

    /** Picks the tunnel crypt, door layout and puzzle the first time a run needs them. */
    fun ensureStarted(player: Player, random: GameRandom) {
        if (tunnelBrother(player) != null) {
            return
        }
        val tunnel = Brother.entries[random.of(Brother.entries.size)]
        set(player, TUNNEL_BROTHER, tunnel.code)
        shuffleTunnels(player, random)
    }

    /** All four corner ladders share one visibility bit, so every corner has a way out. */
    fun showLadders(player: Player) = set(player, LADDER_VISIBLE, 1)

    /** Rebuilds the door layout and deals a new puzzle, as a failed puzzle or a loot does. */
    fun shuffleTunnels(player: Player, random: GameRandom) {
        val locked = TunnelLayout.generate(random)
        for (corridor in TunnelCorridor.entries) {
            set(player, corridor.varbit, if (corridor in locked) 1 else 0)
        }
        set(player, PUZZLE_TYPE, random.of(BarrowsPuzzle.TYPES))
        set(player, PUZZLE_ANSWER, random.of(BarrowsPuzzle.ANSWER_SLOTS))
        set(player, PUZZLE_SOLVED, 0)
    }

    fun creditKill(player: Player, brother: Brother?, combatLevel: Int) {
        if (isLooted(player)) {
            return
        }
        if (brother != null) {
            if (isKilled(player, brother)) {
                return
            }
            set(player, brother.killedVarbit, 1)
        }
        val potential = (killPotential(player) + combatLevel).coerceAtMost(MAX_KILL_POTENTIAL)
        set(player, KILLED_MONSTER, potential)
    }

    fun markChestOpen(player: Player) = set(player, CHEST_OPEN, 1)

    fun markLooted(player: Player) {
        set(player, CHEST_LOOTED, 1)
        set(player, CHEST_COUNT, player.vars[CHEST_COUNT] + 1)
    }

    fun markPuzzleSolved(player: Player) = set(player, PUZZLE_SOLVED, 1)

    /** Ends a looted run: the brothers rise again and the next run picks a new tunnel crypt. */
    fun reset(player: Player) {
        for (brother in Brother.entries) {
            set(player, brother.killedVarbit, 0)
        }
        set(player, KILLED_MONSTER, 0)
        set(player, CHEST_OPEN, 0)
        set(player, TUNNEL_BROTHER, 0)
        set(player, PUZZLE_SOLVED, 0)
        set(player, CHEST_LOOTED, 0)
    }

    private fun set(player: Player, varbit: String, value: Int) {
        if (player.vars[varbit] != value) {
            VarPlayerIntMapSetter.set(player, varbit, value)
        }
    }
}

/**
 * Picks which corridors are locked. Every room other than the chest room stays reachable from
 * every other through open doors (the outer ring counts as a room), and exactly one corridor
 * into the chest room is open, so wherever the hidden tunnel drops the player there is always a
 * route to the puzzle door and back to a ladder.
 */
object TunnelLayout {
    private const val OPEN_CHANCE_PERCENT = 55

    fun generate(random: GameRandom): Set<TunnelCorridor> {
        val centreDoors = TunnelCorridor.entries.filter { it.entersCentre }
        val outerDoors = TunnelCorridor.entries.filterNot { it.entersCentre }
        while (true) {
            val open = outerDoors.filterTo(mutableSetOf()) { random.of(100) < OPEN_CHANCE_PERCENT }
            if (!outerRoomsConnected(open)) {
                continue
            }
            open += centreDoors[random.of(centreDoors.size)]
            return TunnelCorridor.entries.toSet() - open
        }
    }

    fun outerRoomsConnected(open: Set<TunnelCorridor>): Boolean {
        val outer = TunnelRoom.entries - TunnelRoom.Centre
        val edges = open.flatMap { it.links }.filter { TunnelRoom.Centre !in it.toList() }
        val seen = mutableSetOf(outer.first())
        val queue = ArrayDeque(seen)
        while (queue.isNotEmpty()) {
            val room = queue.removeFirst()
            for ((a, b) in edges) {
                val next =
                    when (room) {
                        a -> b
                        b -> a
                        else -> continue
                    }
                if (seen.add(next)) {
                    queue.addLast(next)
                }
            }
        }
        return seen.containsAll(outer)
    }
}

/**
 * The four pattern puzzles on the chest room door. Each uses six consecutive models: the right
 * answer, two wrong answers, then the three shapes of the sequence (RuneLite finds the answer as
 * the first sequence model minus three).
 */
object BarrowsPuzzle {
    const val TYPES = 4
    const val ANSWER_SLOTS = 3

    private val BASE_MODELS = intArrayOf(6713, 6719, 6725, 6731)

    fun sequence(type: Int): List<Int> = (3..5).map { BASE_MODELS[type] + it }

    /** The three answer models in slot order a, b, c with the right one in [answerSlot]. */
    fun answers(type: Int, answerSlot: Int): List<Int> {
        val base = BASE_MODELS[type]
        val wrong = mutableListOf(base + 1, base + 2)
        return List(ANSWER_SLOTS) { slot -> if (slot == answerSlot) base else wrong.removeFirst() }
    }
}

data class BarrowsReward(val obj: String, val count: Int)

/**
 * The chest's loot rules from the OSRS wiki: one roll plus one per brother slain; each roll first
 * tries for an equipment piece of a slain brother (1 in 450 - 58 per brother, never the same
 * piece twice), then falls back to the reward potential table. At most one elite clue per chest.
 */
object BarrowsLoot {
    const val ELITE_CLUE = "obj.trail_elite_emote_exp1"

    private data class Band(
        val obj: String,
        val unlock: Int,
        val full: Int,
        val min: Int,
        val max: Int,
    )

    private val BANDS =
        listOf(
            Band("obj.coins", 1, 380, 2, 774),
            Band("obj.mindrune", 381, 505, 253, 336),
            Band("obj.chaosrune", 506, 630, 112, 139),
            Band("obj.deathrune", 631, 755, 70, 83),
            Band("obj.bloodrune", 756, 880, 37, 43),
            Band("obj.barrows_karil_ammo", 881, 1005, 35, 40),
        )

    private const val KEY_HALF_UNLOCK = 1006
    private const val DRAGON_MED_HELM_UNLOCK = 1012

    fun uniqueChanceDenominator(brothersKilled: Int): Int = 450 - 58 * brothersKilled

    fun clueChanceDenominator(brothersKilled: Int): Int = 200 - (171 * brothersKilled) / 6

    fun roll(killed: List<Brother>, potential: Int, random: GameRandom): List<BarrowsReward> {
        if (potential <= 0) {
            return emptyList()
        }
        val rewards = mutableListOf<BarrowsReward>()
        val uniques = killed.flatMap { it.items }.toMutableList()
        var clueGiven = false
        repeat(1 + killed.size) {
            if (!clueGiven && random.of(clueChanceDenominator(killed.size)) == 0) {
                clueGiven = true
                rewards += BarrowsReward(ELITE_CLUE, 1)
            }
            if (uniques.isNotEmpty() && random.of(uniqueChanceDenominator(killed.size)) == 0) {
                rewards += BarrowsReward(uniques.removeAt(random.of(uniques.size)), 1)
                return@repeat
            }
            rewards += rollPotential(potential, random)
        }
        return merge(rewards)
    }

    private fun rollPotential(potential: Int, random: GameRandom): BarrowsReward {
        val roll = random.of(1, potential)
        if (roll >= DRAGON_MED_HELM_UNLOCK) {
            return BarrowsReward("obj.dragon_med_helm", 1)
        }
        if (roll >= KEY_HALF_UNLOCK) {
            val half = if (random.randomBoolean()) "obj.keyhalf1" else "obj.keyhalf2"
            return BarrowsReward(half, 1)
        }
        val band = BANDS.last { roll >= it.unlock }
        val scale = ((potential - band.unlock + 1).toDouble() / (band.full - band.unlock + 1))
        val fraction = scale.coerceIn(0.0, 1.0)
        val max = (band.max * fraction).toInt().coerceAtLeast(1)
        val min = (band.min * fraction).toInt().coerceIn(1, max)
        return BarrowsReward(band.obj, random.of(min, max))
    }

    private fun merge(rewards: List<BarrowsReward>): List<BarrowsReward> =
        rewards
            .groupBy { it.obj }
            .map { (obj, same) -> BarrowsReward(obj, same.sumOf { it.count }) }
}
