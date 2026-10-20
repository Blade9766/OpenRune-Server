package org.rsmod.content.areas.misc.waterbirth

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.rsmod.map.CoordGrid

/** The three paths east of the main cavern on sublevel 1, each behind its own pressure door. */
enum class WaterbirthPath(
    val door: String,
    val doorOrigin: CoordGrid,
    val support: String,
    val supportCoords: CoordGrid,
    val supportFalling: String,
    val supportFallen: String,
) {
    North(
        door = "loc.dagannoth_pressure_door",
        doorOrigin = CoordGrid(2491, 10162, 0),
        support = "npc.dagannoth_weak_door_north",
        supportCoords = CoordGrid(2545, 10145, 0),
        supportFalling = "npc.dagannoth_weak_door_falling_north",
        supportFallen = "npc.dagannoth_weak_door_fallen_north",
    ),
    Middle(
        door = "loc.dagannoth_pressure_door2",
        doorOrigin = CoordGrid(2491, 10146, 0),
        support = "npc.dagannoth_weak_door_west",
        supportCoords = CoordGrid(2543, 10143, 0),
        supportFalling = "npc.dagannoth_weak_door_falling_west",
        supportFallen = "npc.dagannoth_weak_door_fallen_west",
    ),
    South(
        door = "loc.dagannoth_pressure_door3",
        doorOrigin = CoordGrid(2491, 10130, 0),
        support = "npc.dagannoth_weak_door_south",
        supportCoords = CoordGrid(2545, 10141, 0),
        supportFalling = "npc.dagannoth_weak_door_falling_south",
        supportFallen = "npc.dagannoth_weak_door_fallen_south",
    );

    val supportId: Int by lazy { support.asRSCM(RSCMType.NPC) }

    /** The two floor plates on the cavern side of the door, at its north and south ends. */
    val pads: List<CoordGrid>
        get() = listOf(doorOrigin.translate(-1, 0), doorOrigin.translate(-1, DOOR_LENGTH - 1))

    /** The tile the player lands on after walking through the door from [from]. */
    fun throughDoor(from: CoordGrid): CoordGrid {
        val z = from.z.coerceIn(doorOrigin.z, doorOrigin.z + DOOR_LENGTH - 1)
        val x = if (from.x < doorOrigin.x) doorOrigin.x + 1 else doorOrigin.x - 1
        return CoordGrid(x, z, doorOrigin.level)
    }

    companion object {
        private const val DOOR_LENGTH = 3

        fun ofDoor(door: String): WaterbirthPath? = entries.firstOrNull { it.door == door }

        fun ofSupportId(id: Int): WaterbirthPath? = entries.firstOrNull { it.supportId == id }

        fun ofPad(coords: CoordGrid): WaterbirthPath? = entries.firstOrNull { coords in it.pads }
    }
}

/**
 * Sublevel 1's door rules. A pressure door opens from the cavern only while both of its floor
 * plates are weighed down, by players or a dropped pet rock; from the path side it always opens.
 * The props holding the ladder room's doors shut can only be knocked down from the room itself or
 * from another path, never from the path they block.
 */
object WaterbirthDoors {
    private const val CAVERN_EDGE_X = 2491
    private const val ROOM_MIN_X = 2543
    private const val NORTH_PATH_MIN_Z = 10155
    private const val SOUTH_PATH_MAX_Z = 10136
    private const val ROOM_NORTH_Z = 10145
    private const val ROOM_SOUTH_Z = 10141

    private val ROOM =
        setOf(
            CoordGrid(2544, 10145, 0),
            CoordGrid(2543, 10144, 0),
            CoordGrid(2544, 10144, 0),
            CoordGrid(2545, 10144, 0),
            CoordGrid(2544, 10143, 0),
            CoordGrid(2545, 10143, 0),
            CoordGrid(2543, 10142, 0),
            CoordGrid(2544, 10142, 0),
            CoordGrid(2545, 10142, 0),
            CoordGrid(2544, 10141, 0),
            CoordGrid(2544, 10140, 0),
        )

    fun inLadderRoom(coords: CoordGrid): Boolean = coords in ROOM

    /** The path [coords] stands in, or `null` in the cavern, the ladder room or off sublevel 1. */
    fun pathOf(coords: CoordGrid): WaterbirthPath? =
        when {
            coords.level != 0 || coords.x <= CAVERN_EDGE_X || coords in ROOM -> null
            coords.x >= ROOM_MIN_X && coords.z >= ROOM_NORTH_Z -> WaterbirthPath.North
            coords.x >= ROOM_MIN_X && coords.z <= ROOM_SOUTH_Z -> WaterbirthPath.South
            coords.z >= NORTH_PATH_MIN_Z -> WaterbirthPath.North
            coords.z <= SOUTH_PATH_MAX_Z -> WaterbirthPath.South
            else -> WaterbirthPath.Middle
        }

    fun fromCavernSide(path: WaterbirthPath, coords: CoordGrid): Boolean =
        coords.x < path.doorOrigin.x

    /**
     * Whether [path]'s door opens for someone at [opener]: always from the path side, and from the
     * cavern only when every plate is in [pressed].
     */
    fun opens(path: WaterbirthPath, opener: CoordGrid, pressed: Collection<CoordGrid>): Boolean =
        !fromCavernSide(path, opener) || path.pads.all { it in pressed }

    /** Whether someone at [attacker] may strike the prop blocking [support]'s path. */
    fun canStrike(support: WaterbirthPath, attacker: CoordGrid): Boolean = pathOf(attacker) != support
}
