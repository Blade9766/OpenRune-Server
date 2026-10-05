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
 * [roomTypeId] is the room's `poh_room` type, the id the room creation menu answers with.
 *
 * [zoneOffsetX] and [zoneZ] locate the room's 8x8 template relative to a style's block (see
 * [HouseStyle]); [doors] is a bitmask of the edges the template has a door hotspot on, before the
 * room is rotated, with one bit per [Side].
 *
 * A hall is authored twice. [stairsTopZoneOffsetX] is the second template, the one used when a
 * staircase reaches up into the room: it is the same room with the floor cut away over the
 * stairwell, so you can see down into the room below, and with no rug authored across the hole.
 *
 * A [dungeon] room can only be built in the basement, and a [groundOnly] one only on the ground
 * floor. A house may hold only one of each [unique] room.
 */
enum class RoomType(
    val label: String,
    val roomTypeId: Int,
    val level: Int,
    val cost: Int,
    val zoneOffsetX: Int,
    val zoneZ: Int,
    val doors: Int,
    val outdoors: Boolean,
    val hotspots: List<HotspotGroup>,
    val stairsTopZoneOffsetX: Int? = null,
    val dungeon: Boolean = false,
    val groundOnly: Boolean = false,
    val unique: Boolean = false,
) {
    GARDEN("Garden", 2, 1, 1_000, 0, 881, 0b1111, true, Furniture.GARDEN),
    PARLOUR("Parlour", 1, 1, 1_000, 0, 887, 0b1101, false, Furniture.PARLOUR),
    KITCHEN("Kitchen", 3, 5, 5_000, 2, 887, 0b1001, false, Furniture.KITCHEN),
    DINING_ROOM("Dining room", 4, 10, 5_000, 4, 887, 0b1101, false, Furniture.DINING_ROOM),
    WORKSHOP("Workshop", 12, 15, 10_000, 0, 885, 0b1010, false, Furniture.WORKSHOP),
    BEDROOM("Bedroom", 5, 20, 10_000, 6, 887, 0b1001, false, Furniture.BEDROOM),
    SKILL_HALL("Skill hall", 7, 25, 15_000, 1, 886, 0b1111, false, Furniture.SKILL_HALL, 3),
    QUEST_HALL("Quest hall", 9, 35, 25_000, 5, 886, 0b1111, false, Furniture.QUEST_HALL, 7),
    STUDY("Study", 13, 40, 50_000, 4, 885, 0b1101, false, Furniture.STUDY),
    CHAPEL("Chapel", 11, 45, 50_000, 2, 885, 0b1001, false, Furniture.CHAPEL),
    PORTAL_CHAMBER("Portal chamber", 14, 50, 100_000, 1, 884, 0b1000, false, Furniture.PORTAL_CHAMBER),
    FORMAL_GARDEN("Formal garden", 21, 55, 75_000, 2, 881, 0b1111, true, Furniture.FORMAL_GARDEN),
    SUPERIOR_GARDEN("Superior garden", 26, 65, 75_000, 5, 880, 0b1111, true, Furniture.SUPERIOR_GARDEN),
    MENAGERIE_INDOOR("Menagerie", 24, 37, 30_000, 7, 882, 0b1111, false, Furniture.MENAGERIE_INDOOR),
    MENAGERIE_OUTDOOR("Menagerie", 25, 37, 30_000, 7, 880, 0b1111, true, Furniture.MENAGERIE_OUTDOOR),
    LEAGUE_HALL("League hall", 29, 27, 15_000, 5, 888, 0b1101, false, Furniture.LEAGUE_HALL, unique = true),
    PORTAL_NEXUS("Portal nexus", 28, 72, 200_000, 3, 888, 0b1111, false, Furniture.PORTAL_NEXUS, unique = true),
    COMBAT_ROOM("Combat room", 22, 32, 25_000, 3, 884, 0b1101, false, Furniture.COMBAT_ROOM),
    GAMES_ROOM("Games room", 6, 30, 25_000, 5, 884, 0b1101, false, Furniture.GAMES_ROOM, unique = true),
    COSTUME_ROOM("Costume room", 23, 42, 50_000, 6, 881, 0b1000, false, Furniture.COSTUME_ROOM, unique = true),
    ACHIEVEMENT_GALLERY("Achievement gallery", 27, 80, 200_000, 1, 888, 0b1010, false, Furniture.ACHIEVEMENT_GALLERY, unique = true),
    THRONE_ROOM("Throne room", 15, 60, 150_000, 6, 885, 0b1000, false, Furniture.THRONE_ROOM, groundOnly = true),
    DUNGEON_CROSS("Dungeon cross", 18, 70, 7_500, 0, 883, 0b1111, false, Furniture.DUNGEON_ROOM, dungeon = true),
    DUNGEON_STAIRS("Dungeon stairs", 19, 70, 7_500, 2, 883, 0b1111, false, Furniture.DUNGEON_STAIRS, dungeon = true),
    OUBLIETTE("Oubliette", 16, 65, 150_000, 6, 883, 0b1111, false, Furniture.OUBLIETTE, dungeon = true),
    DUNGEON_CORRIDOR("Dungeon corridor", 17, 70, 7_500, 4, 883, 0b1010, false, Furniture.DUNGEON_ROOM, dungeon = true),
    TREASURE_ROOM("Treasure room", 20, 75, 250_000, 7, 884, 0b1000, false, Furniture.TREASURE_ROOM, dungeon = true);

    /** Gardens are open to the sky; the cache keeps throne rooms to the ground floor; dungeons go below. */
    val floors: Set<Floor>
        get() =
            when {
                dungeon -> setOf(Floor.DUNGEON)
                outdoors || groundOnly -> setOf(Floor.GROUND)
                else -> setOf(Floor.GROUND, Floor.UPPER)
            }

    val isHall: Boolean
        get() = this == SKILL_HALL || this == QUEST_HALL

    companion object {
        fun byRoomTypeId(id: Int): RoomType? = entries.firstOrNull { it.roomTypeId == id }
    }

    fun hotspot(key: String): HotspotGroup? = hotspots.firstOrNull { it.key == key }

    /** True when the template has a door on [side] once the room is turned by [rotation]. */
    fun hasDoor(side: Int, rotation: Int): Boolean {
        val template = (side - rotation) and 3
        return doors and (1 shl template) != 0
    }

    /** The rotations that put a door on [side]; a room can only be placed if one of them fits. */
    fun rotationsFacing(side: Int): List<Int> = (0..3).filter { hasDoor(side, it) }
}
