package org.rsmod.content.skills.construction.data

/**
 * The oubliette under a throne room, and the throne room floors that drop or hold intruders.
 * Levels, materials and experience come from the Old School wiki; what the wiki leaves out - the
 * prison's escape charts and how often the pit hurts - is marked where it is chosen.
 */
object Oubliette {
    /** The floor hotspots across the pit: every tile a victim can land on. */
    val PIT_HOTSPOTS: List<String> =
        listOf("loc.poh_oubliette_1", "loc.poh_oubliette_1_side", "loc.poh_oubliette_1_corner")

    /** The pit's object-layer hotspots, which the flames and the rocnar stand on. */
    val PIT_OBJECT_HOTSPOTS: List<String> =
        listOf("loc.poh_oubliette_1_type8", "loc.poh_oubliette_1_type8_ogre")

    /**
     * What lies at the bottom of the pit, in build order. [damage] is rolled every [interval]
     * cycles while challenge mode is on; spikes only hurt as a victim lands on them.
     *
     * The wiki gives the tentacles' 0-4, the flames' 1-3 and the rocnar's max hit of 12 every six
     * ticks; the spikes borrow the dungeon spike trap's 3-5, and how often the tentacles and flames
     * strike is a choice made here.
     */
    enum class Hazard(val damage: IntRange, val interval: Int, val message: String) {
        SPIKES(3..5, 0, "You land on the spikes!"),
        TENTACLES(0..4, 5, "A tentacle lashes out of the water!"),
        FLAMES(1..3, 3, "The flames burn you!"),
        ROCNAR(0..12, 6, "The rocnar attacks you!"),
    }

    /**
     * A prison cage, by the name its locs share, with the experience an escape gives. The wiki has
     * no success charts for picking or forcing the gate; these run from the oak dungeon door's
     * chart down towards the marble door's, as the cages get stronger.
     */
    enum class Cage(private val key: String, val chart: IntRange, val xp: Double) {
        OAK("oak", 10..250, 10.0),
        OAK_STEEL("oak+steel", 8..190, 12.5),
        STEEL("steel", 5..125, 17.5),
        SPIKED("steel+spikes", 5..125, 17.5),
        BONES("bones", 2..50, 20.0);

        val wall: String
            get() = "loc.poh_cage_dungeon_$key"

        val door: String
            get() = "loc.poh_cage_dungeon_${key}_door"

        val openDoor: String
            get() = "loc.poh_cage_dungeon_${key}_door_open"

        companion object {
            fun ofDoor(loc: String): Cage? = entries.firstOrNull { it.door == loc }

            fun ofOpenDoor(loc: String): Cage? = entries.firstOrNull { it.openDoor == loc }
        }
    }

    /** What a throne room floor does when its lever is pulled, in build order. */
    enum class ThroneFloor {
        DECORATION,
        STEEL_CAGE,
        TRAPDOOR,
        LESSER_MAGIC_CAGE,
        GREATER_MAGIC_CAGE;

        /** The loc dropped over the mat to hold its victims, if this floor holds anyone. */
        val cage: String?
            get() =
                when (this) {
                    STEEL_CAGE -> "loc.poh_cage_throneroom"
                    LESSER_MAGIC_CAGE -> "loc.poh_magic_cage_lesser"
                    GREATER_MAGIC_CAGE -> "loc.poh_magic_cage_greater"
                    else -> null
                }
    }

    val LADDERS: List<String> =
        listOf("loc.poh_dungeon_ladder_oak", "loc.poh_dungeon_ladder_teak", "loc.poh_dungeon_ladder_mag")

    const val LADDER_HOTSPOT: String = "loc.poh_oubliette_5"

    /** The throne room's trapdoors, closed, in build order; each has an `_open_7` twin. */
    val TRAPDOORS: List<String> =
        listOf("loc.poh_trapdoor_oak_7", "loc.poh_trapdoor_teak_7", "loc.poh_trapdoor_mag_7")

    fun openTrapdoor(closed: String): String = closed.removeSuffix("_7") + "_open_7"

    const val TRAPDOOR_HOTSPOT: String = "loc.poh_throne_room_7"

    /** Carries a victim down from a throne room trap to the oubliette below, a cycle after it opens. */
    const val DROP_QUEUE: String = "queue.poh_oubliette_drop"
}
