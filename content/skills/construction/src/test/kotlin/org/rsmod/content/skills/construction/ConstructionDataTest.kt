package org.rsmod.content.skills.construction

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.repo.region.RegionRepository
import org.rsmod.content.skills.construction.data.Floor
import org.rsmod.content.skills.construction.data.HouseLocation
import org.rsmod.content.skills.construction.data.HouseStyle
import org.rsmod.content.skills.construction.data.PlankType
import org.rsmod.content.skills.construction.data.RoomType
import org.rsmod.content.skills.construction.data.Side
import org.rsmod.content.skills.construction.house.HouseState
import org.rsmod.content.skills.construction.house.Room

class ConstructionDataTest {
    @Test
    fun `every hotspot option names one built loc per hotspot loc`() {
        for (room in RoomType.entries) {
            for (group in room.hotspots) {
                for (option in group.options) {
                    assertEquals(
                        group.locs.size,
                        option.built.size,
                        "${room.label}/${group.key}/${option.label}",
                    )
                }
            }
        }
    }

    @Test
    fun `hotspot keys are unique within a room`() {
        for (room in RoomType.entries) {
            val keys = room.hotspots.map { it.key }
            assertEquals(keys.size, keys.toSet().size, room.label)
        }
    }

    @Test
    fun `a hotspot loc only ever belongs to one group`() {
        for (room in RoomType.entries) {
            val locs = room.hotspots.flatMap { it.locs }
            assertEquals(locs.size, locs.toSet().size, room.label)
        }
    }

    @Test
    fun `build options are listed in ascending level order`() {
        for (room in RoomType.entries) {
            for (group in room.hotspots) {
                val levels = group.options.map { it.level }
                assertEquals(levels.sorted(), levels, "${room.label}/${group.key}")
            }
        }
    }

    @Test
    fun `every option costs something and awards experience`() {
        for (room in RoomType.entries) {
            for (group in room.hotspots) {
                for (option in group.options) {
                    assertTrue(option.materials.isNotEmpty(), "${group.key}/${option.label}")
                    assertTrue(option.materials.all { it.count > 0 }, option.label)
                    assertTrue(option.xp > 0.0, "${group.key}/${option.label}")
                }
            }
        }
    }

    /**
     * A room can only be attached to a door, so a template with no doors on a side can never be
     * placed there. Every room must therefore be turnable to face any of the four sides.
     */
    @Test
    fun `every room can be turned to face any side`() {
        for (room in RoomType.entries) {
            for (side in Side.ALL) {
                assertTrue(
                    room.rotationsFacing(side).isNotEmpty(),
                    "${room.label} cannot face ${Side.label(side)}",
                )
            }
        }
    }

    @Test
    fun `turning a room moves its doors with it`() {
        val kitchen = RoomType.KITCHEN
        assertTrue(kitchen.hasDoor(Side.WEST, rotation = 0))
        assertTrue(kitchen.hasDoor(Side.SOUTH, rotation = 0))
        assertFalse(kitchen.hasDoor(Side.EAST, rotation = 0))

        assertTrue(kitchen.hasDoor(Side.NORTH, rotation = 1))
        assertTrue(kitchen.hasDoor(Side.WEST, rotation = 1))
        assertFalse(kitchen.hasDoor(Side.SOUTH, rotation = 1))
    }

    @Test
    fun `opposite sides pair up`() {
        assertEquals(Side.EAST, Side.opposite(Side.WEST))
        assertEquals(Side.SOUTH, Side.opposite(Side.NORTH))
        for (side in Side.ALL) {
            assertEquals(side, Side.opposite(Side.opposite(side)))
        }
    }

    @Test
    fun `side deltas point at the neighbouring cell`() {
        assertEquals(-1, Side.deltaX(Side.WEST))
        assertEquals(1, Side.deltaX(Side.EAST))
        assertEquals(1, Side.deltaZ(Side.NORTH))
        assertEquals(-1, Side.deltaZ(Side.SOUTH))
    }

    @Test
    fun `gardens are ground floor only`() {
        assertEquals(setOf(Floor.GROUND), RoomType.GARDEN.floors)
        assertTrue(Floor.UPPER in RoomType.PARLOUR.floors)
    }

    @Test
    fun `a starter house is one garden with a portal in it`() {
        val state = HouseState()
        state.createStarterHouse()
        assertTrue(state.owned)
        assertEquals(1, state.rooms.size)
        val garden = state[Floor.GROUND, Construction.STARTER_CELL, Construction.STARTER_CELL]
        assertEquals(RoomType.GARDEN, garden?.type)
        assertEquals(0, garden?.furniture?.get("centrepiece"))
    }

    @Test
    fun `rooms are only connected when both sides have a door`() {
        val state = HouseState()
        state[Floor.GROUND, 5, 5] = Room(RoomType.GARDEN, rotation = 0)
        assertFalse(state.connected(Floor.GROUND, 5, 5, Side.EAST))

        // The kitchen template only has west and south doors, so it must be turned to face back.
        val facing = RoomType.KITCHEN.rotationsFacing(Side.WEST).first()
        state[Floor.GROUND, 6, 5] = Room(RoomType.KITCHEN, facing)
        assertTrue(state.connected(Floor.GROUND, 5, 5, Side.EAST))
        assertTrue(state.connected(Floor.GROUND, 6, 5, Side.WEST))
    }

    @Test
    fun `house state survives the save encoding`() {
        val state = HouseState()
        state.createStarterHouse()
        state.style = HouseStyle.FANCY_STONE
        state.location = HouseLocation.YANILLE
        val parlour = Room(RoomType.PARLOUR, rotation = 2)
        parlour.furniture["chair_1"] = 3
        parlour.furniture["rug"] = 1
        state[Floor.UPPER, 4, 9] = parlour

        val decoded = HouseState.decode(state.encode())
        assertTrue(decoded.owned)
        assertEquals(HouseStyle.FANCY_STONE, decoded.style)
        assertEquals(HouseLocation.YANILLE, decoded.location)
        assertEquals(state.rooms.size, decoded.rooms.size)

        val restored = decoded[Floor.UPPER, 4, 9]
        assertEquals(RoomType.PARLOUR, restored?.type)
        assertEquals(2, restored?.rotation)
        assertEquals(3, restored?.furniture?.get("chair_1"))
        assertEquals(1, restored?.furniture?.get("rug"))
    }

    @Test
    fun `decoding drops rooms that fall outside the grid`() {
        val state = HouseState.decode("1,BASIC_WOOD,RIMMINGTON;GROUND,31,31,PARLOUR,0,")
        assertTrue(state.rooms.isEmpty())
    }

    @Test
    fun `decoding an empty save leaves an unowned house`() {
        val state = HouseState.decode("")
        assertFalse(state.owned)
        assertTrue(state.rooms.isEmpty())
        assertNull(state[Floor.GROUND, 0, 0])
    }

    @Test
    fun `grid cells pack and unpack without colliding`() {
        val keys = HashSet<Int>()
        for (floor in Floor.entries) {
            for (gx in 0 until Construction.GRID) {
                for (gz in 0 until Construction.GRID) {
                    assertTrue(keys.add(HouseState.key(floor, gx, gz)), "$floor $gx $gz")
                }
            }
        }
    }

    @Test
    fun `every style names a distinct template block and level`() {
        val slots = HouseStyle.entries.map { it.blockZoneX to it.templateLevel }
        assertEquals(slots.size, slots.toSet().size)
    }

    @Test
    fun `house locations and portals are unique`() {
        val portals = HouseLocation.entries.map { it.portal }
        assertEquals(portals.size, portals.toSet().size)
        for (location in HouseLocation.entries) {
            assertEquals(location, HouseLocation.forPortal(location.portal))
        }
    }

    /** The grid plus its one-zone margin has to fit inside a small runtime region. */
    @Test
    fun `the house grid fits in a small region`() {
        assertTrue(
            Construction.GRID + 2 <= RegionRepository.SMALL_REGION_ZONE_LENGTH,
            "grid of ${Construction.GRID} does not fit in ${RegionRepository.SMALL_REGION_ZONE_LENGTH}",
        )
    }

    @Test
    fun `plank prices rise with the log`() {
        val costs = PlankType.entries.map { it.cost }
        assertEquals(costs.sorted(), costs)
        for (plank in PlankType.entries) {
            assertEquals(plank, PlankType.forLogs(plank.logs))
        }
    }
}
