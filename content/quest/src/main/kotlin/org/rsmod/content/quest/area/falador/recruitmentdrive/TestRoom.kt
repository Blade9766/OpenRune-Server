package org.rsmod.content.quest.area.falador.recruitmentdrive

import org.rsmod.map.CoordGrid

/**
 * The seven testing rooms of the Temple Knight training grounds, numbered as the cache numbers them:
 * room N is observed by `npc.rd_observer_room_N`, finished on `varbit.rd_roomN_complete`, entered
 * through `loc.rd_portal_roomN_entrance` and left through `loc.rd_roomN_exitdoor` and
 * `loc.rd_portal_roomN_exit`. Every tile is the world tile of the cache map (map square 38_77); the
 * scripts translate them into the player's private copy.
 *
 * [jingle] is the room's js5 archive 11 group (the "Cache ID" on each jingle's OSRS wiki page).
 */
enum class TestRoom(
    val id: Int,
    val observer: String,
    val observerTile: CoordGrid,
    val jingle: Int,
    val minX: Int,
    val minZ: Int,
    val maxX: Int,
    val maxZ: Int,
    val entranceTile: CoordGrid,
    val arrival: CoordGrid,
    val exitDoorTile: CoordGrid,
    val exitPortalTile: CoordGrid,
) {
    CROSSING(
        id = 1,
        observer = "npc.rd_observer_room_1",
        observerTile = CoordGrid(2488, 4973, 0),
        jingle = 160,
        minX = 2472, minZ = 4968, maxX = 2490, maxZ = 4976,
        entranceTile = CoordGrid(2490, 4972, 0),
        arrival = CoordGrid(2489, 4972, 0),
        exitDoorTile = CoordGrid(2472, 4972, 0),
        exitPortalTile = CoordGrid(2471, 4972, 0),
    ),
    STATUES(
        id = 2,
        observer = "npc.rd_observer_room_2",
        observerTile = CoordGrid(2458, 4981, 0),
        jingle = 156,
        minX = 2447, minZ = 4975, maxX = 2460, maxZ = 4983,
        entranceTile = CoordGrid(2460, 4979, 0),
        arrival = CoordGrid(2459, 4979, 0),
        exitDoorTile = CoordGrid(2447, 4979, 0),
        exitPortalTile = CoordGrid(2446, 4979, 0),
    ),
    COMBAT(
        id = 3,
        observer = "npc.rd_observer_room_3",
        observerTile = CoordGrid(2457, 4966, 0),
        jingle = 162,
        minX = 2455, minZ = 4960, maxX = 2464, maxZ = 4967,
        entranceTile = CoordGrid(2455, 4964, 0),
        arrival = CoordGrid(2456, 4964, 0),
        exitDoorTile = CoordGrid(2463, 4963, 0),
        exitPortalTile = CoordGrid(2464, 4963, 0),
    ),
    PATIENCE(
        id = 4,
        observer = "npc.rd_observer_room_4",
        observerTile = CoordGrid(2476, 4958, 0),
        jingle = 161,
        minX = 2471, minZ = 4953, maxX = 2481, maxZ = 4959,
        entranceTile = CoordGrid(2471, 4956, 0),
        arrival = CoordGrid(2472, 4956, 0),
        exitDoorTile = CoordGrid(2480, 4956, 0),
        exitPortalTile = CoordGrid(2481, 4956, 0),
    ),
    ACROSTIC(
        id = 5,
        observer = "npc.rd_observer_room_5",
        observerTile = CoordGrid(2443, 4956, 0),
        jingle = 159,
        minX = 2439, minZ = 4953, maxX = 2447, maxZ = 4959,
        entranceTile = CoordGrid(2439, 4956, 0),
        arrival = CoordGrid(2440, 4956, 0),
        exitDoorTile = CoordGrid(2446, 4956, 0),
        exitPortalTile = CoordGrid(2447, 4956, 0),
    ),
    IMPROVISATION(
        id = 6,
        observer = "npc.rd_observer_room_6",
        observerTile = CoordGrid(2469, 4941, 0),
        jingle = 131,
        minX = 2467, minZ = 4936, maxX = 2479, maxZ = 4944,
        entranceTile = CoordGrid(2467, 4940, 0),
        arrival = CoordGrid(2468, 4940, 0),
        exitDoorTile = CoordGrid(2478, 4940, 0),
        exitPortalTile = CoordGrid(2479, 4940, 0),
    ),
    LOGIC(
        id = 7,
        observer = "npc.rd_observer_room_7",
        observerTile = CoordGrid(2451, 4939, 0),
        jingle = 155,
        minX = 2448, minZ = 4935, maxX = 2456, maxZ = 4944,
        entranceTile = CoordGrid(2451, 4935, 0),
        arrival = CoordGrid(2451, 4936, 0),
        exitDoorTile = CoordGrid(2452, 4943, 0),
        exitPortalTile = CoordGrid(2452, 4944, 0),
    );

    val completeVarbit: String = "varbit.rd_room${id}_complete"
    val entrancePortal: String = "loc.rd_portal_room${id}_entrance"
    val exitPortal: String = "loc.rd_portal_room${id}_exit"
    val exitDoor: String = "loc.rd_room${id}_exitdoor"

    operator fun contains(world: CoordGrid): Boolean =
        world.level == 0 && world.x in minX..maxX && world.z in minZ..maxZ

    companion object {
        fun byId(id: Int): TestRoom? = entries.firstOrNull { it.id == id }

        fun at(world: CoordGrid): TestRoom? = entries.firstOrNull { world in it }
    }
}
