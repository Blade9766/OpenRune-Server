package org.rsmod.content.areas.misc.waterbirth

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.content.areas.misc.waterbirth.WaterbirthPath.Middle
import org.rsmod.content.areas.misc.waterbirth.WaterbirthPath.North
import org.rsmod.content.areas.misc.waterbirth.WaterbirthPath.South
import org.rsmod.map.CoordGrid

class WaterbirthDoorsTest {
    @Test
    fun `each door has a plate at either end on the cavern side`() {
        assertEquals(listOf(tile(2490, 10162), tile(2490, 10164)), North.pads)
        assertEquals(listOf(tile(2490, 10146), tile(2490, 10148)), Middle.pads)
        assertEquals(listOf(tile(2490, 10130), tile(2490, 10132)), South.pads)
        for (path in WaterbirthPath.entries) {
            for (pad in path.pads) assertEquals(path, WaterbirthPath.ofPad(pad))
        }
        assertNull(WaterbirthPath.ofPad(tile(2490, 10147)))
    }

    @Test
    fun `a door from the cavern needs both plates weighed down`() {
        val plates = Middle.pads
        val opener = plates[0]
        assertFalse(WaterbirthDoors.opens(Middle, opener, emptySet()))
        assertFalse(WaterbirthDoors.opens(Middle, opener, setOf(plates[0])))
        assertFalse(WaterbirthDoors.opens(Middle, opener, setOf(plates[1])))
        assertFalse(WaterbirthDoors.opens(Middle, opener, North.pads + plates[0]))
        assertTrue(WaterbirthDoors.opens(Middle, opener, plates))
        assertTrue(WaterbirthDoors.opens(Middle, tile(2490, 10147), plates))
    }

    @Test
    fun `a door always opens from the path side`() {
        for (path in WaterbirthPath.entries) {
            val inside = path.doorOrigin.translate(1, 1)
            assertTrue(WaterbirthDoors.opens(path, inside, emptySet()), "$path")
        }
    }

    @Test
    fun `walking through a door crosses it and keeps to its span`() {
        assertEquals(tile(2492, 10162), North.throughDoor(tile(2490, 10162)))
        assertEquals(tile(2492, 10164), North.throughDoor(tile(2490, 10164)))
        assertEquals(tile(2492, 10164), North.throughDoor(tile(2490, 10165)))
        assertEquals(tile(2490, 10147), Middle.throughDoor(tile(2492, 10147)))
    }

    @Test
    fun `tiles are sorted into their path`() {
        assertNull(WaterbirthDoors.pathOf(tile(2480, 10147)))
        assertNull(WaterbirthDoors.pathOf(tile(2491, 10147)))
        assertEquals(North, WaterbirthDoors.pathOf(tile(2492, 10163)))
        assertEquals(Middle, WaterbirthDoors.pathOf(tile(2492, 10147)))
        assertEquals(South, WaterbirthDoors.pathOf(tile(2492, 10131)))
        assertEquals(North, WaterbirthDoors.pathOf(tile(2547, 10150)))
        assertEquals(North, WaterbirthDoors.pathOf(North.supportCoords))
        assertEquals(South, WaterbirthDoors.pathOf(tile(2547, 10135)))
        assertEquals(South, WaterbirthDoors.pathOf(South.supportCoords))
        assertEquals(Middle, WaterbirthDoors.pathOf(tile(2542, 10143)))
        assertNull(WaterbirthDoors.pathOf(WaterbirthCoords.LADDER_ROOM))
        assertNull(WaterbirthDoors.pathOf(tile(2492, 10147, level = 1)))
    }

    @Test
    fun `a prop cannot be struck from the path it blocks`() {
        assertFalse(WaterbirthDoors.canStrike(North, tile(2545, 10148)))
        assertFalse(WaterbirthDoors.canStrike(Middle, tile(2540, 10143)))
        assertFalse(WaterbirthDoors.canStrike(South, tile(2545, 10138)))
    }

    @Test
    fun `a prop can be struck from another path or the ladder room`() {
        val north = tile(2545, 10148)
        val middle = tile(2540, 10143)
        val south = tile(2545, 10138)
        assertTrue(WaterbirthDoors.canStrike(Middle, north))
        assertTrue(WaterbirthDoors.canStrike(Middle, south))
        assertTrue(WaterbirthDoors.canStrike(North, middle))
        assertTrue(WaterbirthDoors.canStrike(South, middle))
        for (path in WaterbirthPath.entries) {
            assertTrue(WaterbirthDoors.canStrike(path, WaterbirthCoords.LADDER_ROOM), "$path")
        }
    }

    private fun tile(x: Int, z: Int, level: Int = 0) = CoordGrid(x, z, level)
}
