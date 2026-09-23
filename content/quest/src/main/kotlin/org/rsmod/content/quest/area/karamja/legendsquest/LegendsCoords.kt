package org.rsmod.content.quest.area.karamja.legendsquest

import org.rsmod.map.CoordGrid

/** Every place Legends' Quest needs to know about, checked against the cache loc spawns. */
internal object LegendsCoords {
    /** The mithril gate into the guild grounds, the tile beside it inside and the one outside. */
    val GATE_WEST = CoordGrid(2728, 3349, 0)
    val GATE_INSIDE = CoordGrid(2728, 3350, 0)
    val GATE_OUTSIDE = CoordGrid(2729, 3348, 0)

    fun inGuildHall(coords: CoordGrid): Boolean =
        coords.level == 0 && coords.x in 2722..2733 && coords.z in 3373..3384

    fun inJungleWest(coords: CoordGrid): Boolean = inKharazi(coords) && coords.x in 2757..2815

    fun inJungleMiddle(coords: CoordGrid): Boolean = inKharazi(coords) && coords.x in 2816..2879

    fun inJungleEast(coords: CoordGrid): Boolean = inKharazi(coords) && coords.x in 2880..2974

    fun inKharazi(coords: CoordGrid): Boolean =
        coords.level == 0 && coords.x in 2757..2974 && coords.z in 2882..2935

    /**
     * The strips of jungle along the northern edge where a player can get hemmed in by trees, and
     * the tile each one lets a lost player scrabble out to.
     */
    private val DENSE_JUNGLE =
        listOf(
            Rect(2930, 2937, 2941, 2941) to CoordGrid(2937, 2943, 0),
            Rect(2858, 2934, 2873, 2939) to CoordGrid(2866, 2941, 0),
            Rect(2789, 2935, 2802, 2941) to CoordGrid(2795, 2942, 0),
            Rect(2759, 2934, 2765, 2941) to CoordGrid(2820, 2941, 0),
            Rect(2816, 2935, 2823, 2938) to CoordGrid(2820, 2941, 0),
        )

    fun denseJungleExit(coords: CoordGrid): CoordGrid? =
        DENSE_JUNGLE.firstOrNull { it.first.contains(coords) }?.second

    /** Where Gujuo leads a lost player out of the jungle. */
    val JUNGLE_EXIT = CoordGrid(2865, 2941, 0)

    /** The three mossy rocks over the shaman's cave, and the cave's way back out. */
    val SHAMAN_CAVE_LANDING = CoordGrid(2773, 9341, 0)
    val SHAMAN_CAVE_EXIT = CoordGrid(2781, 2934, 0)

    fun inShamanCaves(coords: CoordGrid): Boolean =
        coords.level == 0 && coords.x in 2752..2815 && coords.z in 9280..9343

    /**
     * The flaming octagram round Ungadulu. The Jagex zone also takes in a column of tiles west of
     * the flames that is really outside them; [inOctagram] uses the true shape and
     * [inOctagramZone] the one the game checks when a player attacks him.
     */
    private val OCTAGRAM =
        listOf(Rect(2788, 9328, 2797, 9329), Rect(2792, 9324, 2793, 9333), Rect(2789, 9325, 2796, 9332))

    fun inOctagram(coords: CoordGrid): Boolean =
        coords.level == 0 && OCTAGRAM.any { it.contains(coords) }

    fun inOctagramZone(coords: CoordGrid): Boolean =
        inOctagram(coords) || (coords.level == 0 && Rect(2788, 9317, 2788, 9326).contains(coords))

    val OCTAGRAM_CENTRE = CoordGrid(2792, 9328, 0)

    /** Where Ungadulu's bolt throws a player who tries to fight him inside the octagram. */
    val THROW_SPOTS =
        listOf(
            CoordGrid(2785, 9325, 0),
            CoordGrid(2787, 9322, 0),
            CoordGrid(2792, 9319, 0),
            CoordGrid(2795, 9322, 0),
            CoordGrid(2798, 9323, 0),
            CoordGrid(2801, 9328, 0),
            CoordGrid(2800, 9332, 0),
            CoordGrid(2796, 9335, 0),
            CoordGrid(2792, 9337, 0),
            CoordGrid(2787, 9316, 0),
            CoordGrid(2784, 9328, 0),
        )

    val OCTAGRAM_JUMP_FROM = CoordGrid(2791, 9332, 0)
    val OCTAGRAM_JUMP_TO = CoordGrid(2791, 9334, 0)

    /** The bookcase crevice behind Ungadulu's desk and the tunnel it leads to. */
    val BOOKCASE_FRONT = CoordGrid(2794, 9339, 0)
    val BOOKCASE_CREVICE = CoordGrid(2795, 9340, 0)
    val CREVICE_TUNNEL = CoordGrid(2799, 9341, 0)
    val CREVICE_TUNNEL_STEP = CoordGrid(2800, 9340, 0)
    val CREVICE_BACK = CoordGrid(2795, 9338, 0)

    /** The trials east of the octagram: the outer gate, three boulders and the strength gate. */
    val BOULDER_TRIAL_ESCAPE = CoordGrid(2810, 9330, 0)
    val STRENGTH_GATE_THROUGH = CoordGrid(2810, 9314, 0)

    val JAGGED_WALL_SOUTH = CoordGrid(2790, 9295, 0)
    const val JAGGED_WALL_SPLIT_Z = 9295

    /** The two marked walls; the south-west one lets out into the small walled cavern. */
    val MARKED_WALL_WEST = CoordGrid(2776, 9303, 0)
    val MARKED_WALL_WEST_EXIT = CoordGrid(2780, 9306, 0)
    val MARKED_WALL_EAST_EXIT = CoordGrid(2774, 9301, 0)

    /** The cavern of pools and its seven carved rocks, each crowned by its own gem. */
    fun inGemRoom(coords: CoordGrid): Boolean =
        coords.level == 0 && coords.x in 2755..2782 && coords.z in 9280..9311

    val GEM_ROOM_CENTRE = CoordGrid(2764, 9296, 0)
    val BOOK_SPOT = CoordGrid(2765, 9297, 0)

    /** The magic trial's gate: the tile it is cast from, and the far side it carries a player. */
    val MAGIC_GATE_CAST_FROM = CoordGrid(2763, 9311, 0)
    val MAGIC_GATE_NORTH = CoordGrid(2763, 9320, 0)
    val MAGIC_GATE_SOUTH = CoordGrid(2763, 9312, 0)

    /** The winch down to the Viyeldi caves, and the climbing rope back up. */
    val VIYELDI_LANDING = CoordGrid(2377, 4712, 0)
    val WINCH_TOP = CoordGrid(2760, 9328, 0)

    fun inViyeldiCaves(coords: CoordGrid): Boolean =
        coords.level == 0 && coords.x in 2368..2431 && coords.z in 4672..4735

    val DRAGONS_EYE = CoordGrid(2410, 4715, 0)
    val BARRIER_SOUTH = CoordGrid(2421, 4690, 0)
    val BARRIER_NORTH = CoordGrid(2421, 4691, 0)

    data class Rect(val x0: Int, val z0: Int, val x1: Int, val z1: Int) {
        fun contains(coords: CoordGrid): Boolean = coords.x in x0..x1 && coords.z in z0..z1
    }
}
