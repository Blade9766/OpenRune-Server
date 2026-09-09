package org.rsmod.content.generic.locs.passages

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocEntity
import org.rsmod.game.loc.LocShape
import org.rsmod.game.map.Direction
import org.rsmod.map.CoordGrid

class PassagesTest {
    @Test
    fun `doors ladders caves and stiles are told apart by name and op`() {
        val ground = LocShape.CentrepieceStraight
        assertEquals(PassageAction.OpenDoor, Passages.classify("Door", "Open", LocShape.WallStraight))
        assertEquals(PassageAction.CloseDoor, Passages.classify("Gate", "Close", LocShape.WallStraight))
        assertEquals(PassageAction.OpenTrapdoor, Passages.classify("Trapdoor", "Open", ground))
        assertEquals(PassageAction.ClimbUp, Passages.classify("Ladder", "Climb-up", ground))
        assertEquals(PassageAction.ClimbDown, Passages.classify("Stairs", "Climb-down", ground))
        assertEquals(PassageAction.ClimbEither, Passages.classify("Staircase", "Climb", ground))
        assertEquals(PassageAction.Enter, Passages.classify("Cave entrance", "Enter", ground))
        assertEquals(PassageAction.ClimbOver, Passages.classify("Stile", "Climb-over", ground))
        assertNull(Passages.classify("Crate", "Search", ground))
        assertNull(Passages.classify("Tree", "Climb-up", ground))
    }

    @Test
    fun `climbing moves one level or between the surface and its dungeon`() {
        val ground = CoordGrid(3222, 3218, 0)
        assertEquals(CoordGrid(3222, 3218, 1), Passages.climbDestination(ground, up = true))
        assertEquals(CoordGrid(3222, 9618, 0), Passages.climbDestination(ground, up = false))
        val upstairs = CoordGrid(3222, 3218, 2)
        assertEquals(CoordGrid(3222, 3218, 1), Passages.climbDestination(upstairs, up = false))
        val dungeon = CoordGrid(3222, 9618, 0)
        assertEquals(ground, Passages.climbDestination(dungeon, up = true))
        assertNull(Passages.climbDestination(dungeon, up = false))
    }

    @Test
    fun `stiles and ditches are crossed to the tile past the far edge`() {
        // A 1x2 loc lying across z = 3521..3522, like the Wilderness ditch.
        val ditch =
            BoundLocInfo(
                coords = CoordGrid(3087, 3521),
                entity = LocEntity(23271, LocShape.CentrepieceStraight.id, LocAngle.West.id),
                layer = 2,
                width = 1,
                length = 2,
                forceApproachFlags = 0,
            )
        assertEquals(CoordGrid(3087, 3523), Passages.farSide(ditch, CoordGrid(3087, 3520)))
        assertEquals(CoordGrid(3087, 3520), Passages.farSide(ditch, CoordGrid(3087, 3523)))
        assertNull(Passages.farSide(ditch, CoordGrid(3087, 3521)))

        // The same loc turned a quarter, so it is 2 wide and 1 long.
        val turned =
            ditch.copy(entity = LocEntity(23271, LocShape.CentrepieceStraight.id, LocAngle.North.id))
        assertEquals(CoordGrid(3089, 3521), Passages.farSide(turned, CoordGrid(3086, 3521)))
    }

    @Test
    fun `landing candidates start at the destination and move outwards`() {
        val dest = CoordGrid(3200, 3200)
        val candidates = Passages.landingCandidates(dest, radius = 1)
        assertEquals(dest, candidates.first())
        assertEquals(9, candidates.size)
    }

    /** A 2x3 flight of stairs with its foot to the south when unturned, like Varrock's. */
    private fun stairs(coords: CoordGrid, angle: LocAngle, width: Int = 2, length: Int = 3) =
        BoundLocInfo(
            coords = coords,
            entity = LocEntity(15645, LocShape.CentrepieceStraight.id, angle.id),
            layer = 2,
            width = width,
            length = length,
            forceApproachFlags = FOOT_SOUTH,
        )

    @Test
    fun `the open side of a staircase turns with it`() {
        assertEquals(Direction.South, Passages.openSide(FOOT_SOUTH, LocAngle.West))
        assertEquals(Direction.West, Passages.openSide(FOOT_SOUTH, LocAngle.North))
        assertEquals(Direction.North, Passages.openSide(FOOT_SOUTH, LocAngle.East))
        assertEquals(Direction.East, Passages.openSide(FOOT_SOUTH, LocAngle.South))
        assertEquals(Direction.North, Passages.openSide(FOOT_NORTH, LocAngle.West))
        assertNull(Passages.openSide(0, LocAngle.West))
    }

    @Test
    fun `climbing comes out past the far end on the lane the player climbed in`() {
        // loc.stairs at Varrock, foot to the south; the player stands on its east lane.
        val bottom = stairs(CoordGrid(2590, 3089, 0), LocAngle.West)
        val plane = CoordGrid(2590, 3089, 1)
        val landed = Passages.exitLanding(bottom, CoordGrid(2591, 3088, 0), plane) { true }
        assertEquals(CoordGrid(2591, 3092, 1), landed)
        // A player off to the side is clamped onto the stairs.
        val clamped = Passages.exitLanding(bottom, CoordGrid(2595, 3088, 0), plane) { true }
        assertEquals(CoordGrid(2591, 3092, 1), clamped)
    }

    @Test
    fun `climbing down walks on past the lower flight when it reaches further`() {
        // Yanille: the 2x2 top loc shares the foot end of the 2x3 bottom loc, so the tile past
        // the top loc is inside the bottom loc on the lower level.
        val top =
            stairs(CoordGrid(2537, 3085, 1), LocAngle.East, length = 2)
                .copy(forceApproachFlags = FOOT_NORTH)
        val plane = CoordGrid(2537, 3085, 0)
        val bottomTiles = setOf(CoordGrid(2537, 3087, 0), CoordGrid(2538, 3087, 0))
        val landed =
            Passages.exitLanding(top, CoordGrid(2537, 3084, 1), plane) { it !in bottomTiles }
        assertEquals(CoordGrid(2537, 3088, 0), landed)
    }

    @Test
    fun `the far end of a pair puts the player at its foot`() {
        // loc.stairstop above Varrock's loc.stairs, foot to the north.
        val top =
            stairs(CoordGrid(2590, 3090, 1), LocAngle.West, length = 2)
                .copy(forceApproachFlags = FOOT_NORTH)
        val landed = Passages.counterpartLanding(top, CoordGrid(2591, 3088, 1)) { true }
        assertEquals(CoordGrid(2591, 3092, 1), landed)
        // With the foot blocked the player is put further out, never to the side.
        val blocked = setOf(CoordGrid(2590, 3092, 1), CoordGrid(2591, 3092, 1))
        val further = Passages.counterpartLanding(top, CoordGrid(2591, 3088, 1)) { it !in blocked }
        assertEquals(CoordGrid(2591, 3093, 1), further)
        assertNull(Passages.counterpartLanding(top.copy(forceApproachFlags = 0), CoordGrid(2591, 3088, 1)) { true })
    }

    @Test
    fun `a spiral staircase top is a counterpart of the flight below it`() {
        assertTrue(Passages.isClimbCounterpart("Staircase", listOf("Climb-down"), up = true))
        assertTrue(Passages.isClimbCounterpart("Stairs", listOf("Climb"), up = false))
        assertFalse(Passages.isClimbCounterpart("Staircase", listOf("Climb-up"), up = true))
        assertFalse(Passages.isClimbCounterpart("Bookcase", listOf("Climb-down"), up = true))
    }

    private companion object {
        /** Blocks north, east and west (and the fifth approach): the foot is to the south. */
        const val FOOT_SOUTH = 27

        /** Blocks east, south and west: the foot is to the north. */
        const val FOOT_NORTH = 30
    }
}
