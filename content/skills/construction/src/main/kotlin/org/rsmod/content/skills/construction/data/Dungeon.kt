package org.rsmod.content.skills.construction.data

/**
 * A dungeon's defences: its locked doors and its traps. Success charts, damage and experience
 * come from the Old School wiki.
 */
object Dungeon {
    /**
     * A tier of dungeon door. [pick] and [force] are the wiki's success charts for picking the lock
     * (Thieving) and forcing the door (Strength); a lockpick raises picking to [pickWithLockpick].
     */
    enum class Door(
        val wood: String,
        val pick: IntRange,
        val pickWithLockpick: IntRange,
        val force: IntRange,
        val xp: Double,
    ) {
        OAK("oak", 10..250, 60..250, 10..250, 10.0),
        STEEL("steel", 5..125, 55..125, 5..125, 15.0),
        MARBLE("marble", 2..50, 52..50, 2..50, 20.0);

        val left: String
            get() = "loc.poh_dungeon_ldoor_$wood"

        val right: String
            get() = "loc.poh_dungeon_rdoor_$wood"

        fun opened(closed: String): String = closed + "_open"

        companion object {
            fun of(loc: String): Door? = entries.firstOrNull { it.left == loc || it.right == loc }

            fun ofOpen(loc: String): Door? =
                entries.firstOrNull { it.opened(it.left) == loc || it.opened(it.right) == loc }
        }
    }

    /** What a trap does to whoever fails to dodge it. */
    enum class Effect {
        SPIKES,
        CRUSH,
        TANGLE,
        DRAIN,
        TELEPORT,
    }

    /** A trap, by the loc its build option places; [dodgeXp] is the Agility a dodge earns. */
    enum class Trap(val index: Int, val label: String, val dodgeXp: Double, val effect: Effect) {
        SPIKE(1, "spike trap", 5.0, Effect.SPIKES),
        MAN(2, "man trap", 7.5, Effect.CRUSH),
        VINE(3, "tangle vine", 10.0, Effect.TANGLE),
        MARBLE(4, "marble trap", 15.0, Effect.DRAIN),
        TELEPORT(5, "teleport trap", 20.0, Effect.TELEPORT);

        val built: String
            get() = "loc.poh_trap_$index"

        /** The trap as a finished house shows it: there, but not to be seen. */
        val hidden: String
            get() = "loc.poh_trap_${index}_hidden"

        companion object {
            fun of(built: String): Trap? = entries.firstOrNull { it.built == built }
        }
    }

    /** Carried by a guest in a house: each cycle it checks the tile underfoot for a trap. */
    const val TRAP_TIMER: String = "timer.poh_dungeon_traps"

    /**
     * The guards, by the name both their statue loc and their npc share: built as the statue, the
     * guard itself stands there once the house is out of building mode.
     */
    val GUARDS: List<String> =
        listOf("poh_skeleton", "poh_guarddog", "poh_hobgoblin", "poh_babyreddragon", "poh_giantspider", "poh_troll", "poh_hellhound") +
            Treasure.GUARDIANS

    fun guardStatue(guard: String): String = "loc.$guard"

    fun guardNpc(statue: String): String? = statue.removePrefix("loc.").takeIf { it in GUARDS }?.let { "npc.$it" }

    /** Every trap is dodged on the same Agility chart: 12% at level 1, 82% at 99. */
    val DODGE: IntRange = 30..210

    val SPIKE_DAMAGE: IntRange = 3..5
    val CRUSH_DAMAGE: IntRange = 9..11

    /**
     * Ticks a tangle vine holds its victim. The wiki only says "several seconds, less with higher
     * Agility", so this runs from 8 ticks at level 1 down to 3 at 99.
     */
    fun tangleTicks(agility: Int): Int = (8 - agility / 20).coerceAtLeast(3)

    /** Agility a marble trap takes away: a twentieth of the level, plus one. */
    fun marbleDrain(agility: Int): Int = agility / 20 + 1
}
