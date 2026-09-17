package org.rsmod.content.skills.construction.data

/** Which floor of the house a room sits on. Region levels are allocated in the same order. */
enum class Floor(val label: String) {
    DUNGEON("dungeon"),
    GROUND("ground floor"),
    UPPER("first floor");

    val regionLevel: Int
        get() = ordinal
}

/** Side indices match loc wall angles: 0 west, 1 north, 2 east, 3 south. */
object Side {
    const val WEST: Int = 0
    const val NORTH: Int = 1
    const val EAST: Int = 2
    const val SOUTH: Int = 3

    val ALL: IntArray = intArrayOf(WEST, NORTH, EAST, SOUTH)

    fun opposite(side: Int): Int = (side + 2) and 3

    fun deltaX(side: Int): Int =
        when (side) {
            WEST -> -1
            EAST -> 1
            else -> 0
        }

    fun deltaZ(side: Int): Int =
        when (side) {
            SOUTH -> -1
            NORTH -> 1
            else -> 0
        }

    fun label(side: Int): String =
        when (side) {
            WEST -> "west"
            NORTH -> "north"
            EAST -> "east"
            else -> "south"
        }
}

/**
 * A buildable room.
 *
 * [zoneOffsetX] and [zoneZ] locate the room's 8x8 template relative to a style's block (see
 * [HouseStyle]); [doors] is a bitmask of the edges the template has a door hotspot on, before the
 * room is rotated, with one bit per [Side].
 *
 * A hall is authored twice. [stairsTopZoneOffsetX] is the second template, the one used when a
 * staircase reaches up into the room: it is the same room with the floor cut away over the
 * stairwell, so you can see down into the room below, and with no rug authored across the hole.
 */
enum class RoomType(
    val label: String,
    val level: Int,
    val cost: Int,
    val zoneOffsetX: Int,
    val zoneZ: Int,
    val doors: Int,
    val outdoors: Boolean,
    val hotspots: List<HotspotGroup>,
    val stairsTopZoneOffsetX: Int? = null,
) {
    GARDEN("Garden", 1, 1_000, 0, 881, 0b1111, true, Furniture.GARDEN),
    PARLOUR("Parlour", 1, 1_000, 0, 887, 0b1101, false, Furniture.PARLOUR),
    KITCHEN("Kitchen", 5, 5_000, 2, 887, 0b1001, false, Furniture.KITCHEN),
    DINING_ROOM("Dining room", 10, 5_000, 4, 887, 0b1101, false, Furniture.DINING_ROOM),
    WORKSHOP("Workshop", 15, 10_000, 0, 885, 0b1010, false, Furniture.WORKSHOP),
    BEDROOM("Bedroom", 20, 10_000, 6, 887, 0b1001, false, Furniture.BEDROOM),
    SKILL_HALL("Skill hall", 25, 15_000, 1, 886, 0b1111, false, Furniture.SKILL_HALL, 3),
    QUEST_HALL("Quest hall", 35, 25_000, 5, 886, 0b1111, false, Furniture.QUEST_HALL, 7),
    STUDY("Study", 40, 50_000, 4, 885, 0b1101, false, Furniture.STUDY),
    CHAPEL("Chapel", 45, 50_000, 2, 885, 0b1001, false, Furniture.CHAPEL),
    PORTAL_CHAMBER("Portal chamber", 50, 100_000, 1, 884, 0b1000, false, Furniture.PORTAL_CHAMBER);

    /** Gardens are open to the sky, so they can only sit on the ground floor. */
    val floors: Set<Floor>
        get() = if (outdoors) setOf(Floor.GROUND) else setOf(Floor.GROUND, Floor.UPPER)

    fun hotspot(key: String): HotspotGroup? = hotspots.firstOrNull { it.key == key }

    /** True when the template has a door on [side] once the room is turned by [rotation]. */
    fun hasDoor(side: Int, rotation: Int): Boolean {
        val template = (side - rotation) and 3
        return doors and (1 shl template) != 0
    }

    /** The rotations that put a door on [side]; a room can only be placed if one of them fits. */
    fun rotationsFacing(side: Int): List<Int> = (0..3).filter { hasDoor(side, it) }
}
