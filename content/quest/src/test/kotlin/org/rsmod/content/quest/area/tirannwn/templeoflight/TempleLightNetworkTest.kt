package org.rsmod.content.quest.area.tirannwn.templeoflight

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.content.quest.area.tirannwn.templeoflight.Facing.DOWN
import org.rsmod.content.quest.area.tirannwn.templeoflight.Facing.EAST
import org.rsmod.content.quest.area.tirannwn.templeoflight.Facing.NORTH
import org.rsmod.content.quest.area.tirannwn.templeoflight.Facing.SOUTH
import org.rsmod.content.quest.area.tirannwn.templeoflight.Facing.UP
import org.rsmod.content.quest.area.tirannwn.templeoflight.Facing.WEST
import org.rsmod.content.quest.area.tirannwn.templeoflight.LightColour.BLUE
import org.rsmod.content.quest.area.tirannwn.templeoflight.LightColour.CYAN
import org.rsmod.content.quest.area.tirannwn.templeoflight.LightColour.GREEN
import org.rsmod.content.quest.area.tirannwn.templeoflight.LightColour.MAGENTA
import org.rsmod.content.quest.area.tirannwn.templeoflight.LightColour.RED
import org.rsmod.content.quest.area.tirannwn.templeoflight.LightColour.WHITE
import org.rsmod.content.quest.area.tirannwn.templeoflight.LightColour.YELLOW

/**
 * The light puzzle on its own, without a server: the colour wheel, every way a mirror can face,
 * light running up and down the shafts, fractured crystals and the Final Pillar, loops, the six
 * reference solutions and arrangements that look close but must not open anything.
 */
class TempleLightNetworkTest {

    private fun m(facing: Facing) = PillarContent.Mirror(facing)

    private fun c(item: TempleItem) = PillarContent.Crystal(item)

    private fun trace(vararg placements: Pair<String, PillarContent>) = LightNetwork.trace(PuzzleState(placements.toMap()))

    private fun LightResult.colour(varbit: String): LightColour? =
        LightColour.only(linkColours("varbit.mourning_light_temple_$varbit"))

    @Test fun `crystals work round the colour wheel`() {
        for (crystal in LightColour.entries - WHITE) {
            assertEquals(crystal, WHITE.through(crystal), "white light takes a $crystal crystal's colour")
            assertEquals(crystal, crystal.through(crystal), "light passes a crystal of its own colour")
            assertNull(crystal.through(checkNotNull(crystal.complement)), "the opposite colour stops it")
        }
        assertEquals(GREEN, CYAN.through(YELLOW), "the walkthrough's cyan then yellow crystals make green")
        assertEquals(BLUE, CYAN.through(MAGENTA), "cyan through the fixed magenta is blue")
        assertEquals(RED, YELLOW.through(MAGENTA), "yellow through the fixed magenta is red")
        assertEquals(CYAN, GREEN.through(BLUE), "green through blue is cyan, not black")
        assertEquals(listOf(MAGENTA, RED, BLUE, null, null, null), listOf(WHITE, YELLOW, CYAN, RED, BLUE, GREEN).map { it.through(MAGENTA) }, "the magenta crystal takes only white, yellow and cyan")
        assertEquals(listOf(GREEN, YELLOW, CYAN, null, null, null), listOf(WHITE, RED, BLUE, YELLOW, CYAN, MAGENTA).map { it.through(GREEN) }, "the green crystal takes only white, red and blue")
        assertEquals(mapOf(RED to CYAN, GREEN to MAGENTA, BLUE to YELLOW), listOf(RED, GREEN, BLUE).associateWith { it.complement })
    }

    @Test fun `each door wants a beam of only its opposite colour`() {
        val wanted = TempleGeometry.doors.associate { it.id to it.required }
        assertEquals(BLUE, wanted["1_1_east"])
        assertEquals(RED, wanted["1_13_north"])
        assertEquals(GREEN, wanted["2_4_west"])
        assertEquals(YELLOW, wanted["2_16_west"])
        assertEquals(WHITE, wanted["1_c"], "the black door wants white")
        val door = TempleGeometry.doorsById.getValue("2_16_west")
        fun hit(vararg colours: Int) = LightResult(emptyMap(), emptyMap(), mapOf(door.id to colours.toList()), emptyMap(), false, 0)
        assertTrue(hit(YELLOW.bit).isOpen(door))
        assertFalse(hit(YELLOW.bit or RED.bit).isOpen(door), "yellow sharing its path with red is not yellow alone")
        assertTrue(hit(RED.bit, YELLOW.bit).isOpen(door), "a yellow beam from one side opens it whatever reaches the other")
        assertFalse(hit(WHITE.bit).isOpen(door))
    }

    @Test fun `the emitter lights the middle floor and nothing opens on its own`() {
        val result = trace()
        assertEquals(WHITE.bit, result.gaps[2 to 9], "white light rises straight through the empty emitter pillar")
        assertTrue(result.openDoors().isEmpty())
        assertTrue(result.links.isEmpty())
    }

    @Test fun `a mirror sends the light whichever way it faces`() {
        assertEquals(WHITE, trace("2_9" to m(NORTH)).colour("2_7_9"))
        assertEquals(WHITE, trace("2_9" to m(SOUTH)).colour("2_9_south"))
        for (blind in listOf(EAST, WEST)) {
            val result = trace("2_9" to m(blind))
            assertTrue(result.links.isEmpty() && result.gaps.isEmpty(), "the emitter pillar has no $blind opening")
        }
        assertEquals(WHITE.bit, trace("2_9" to m(UP)).gaps[2 to 9], "up runs into the black crystal")
        assertEquals(WHITE.bit, trace("2_9" to m(DOWN)).gaps[1 to 9], "down runs back into the great crystal")
        val base = arrayOf("2_9" to m(NORTH), "2_7" to m(WEST))
        val expectations =
            mapOf(
                NORTH to "2_6_north",
                EAST to "2_6_7",
                SOUTH to "2_6_11",
                WEST to "2_5_6",
            )
        for ((facing, varbit) in expectations) {
            assertEquals(WHITE, trace(*base, "2_6" to m(facing)).colour(varbit), "2_6 facing $facing")
        }
        assertEquals(WHITE.bit, trace(*base, "2_6" to m(UP)).gaps[2 to 6], "2_6 facing up lights the shaft above")
        assertEquals(WHITE.bit, trace(*base, "2_6" to m(DOWN)).gaps[1 to 6], "2_6 facing down lights the shaft below")
        assertEquals(WHITE, trace(*base).colour("2_5_6"), "an empty pillar lets light straight through")
    }

    @Test fun `vertical beams pass floors without a pillar and stop at the floor and ceiling`() {
        val up = trace("2_9" to m(NORTH), "2_7" to m(WEST), "2_6" to m(UP), "3_6" to m(WEST))
        assertEquals(WHITE.bit, up.gaps[2 to 6])
        assertEquals(WHITE.bit, up.lightAt("3_6"), "light rises into the top floor's pillar above")
        val shaft = trace("2_9" to m(NORTH), "2_7" to m(WEST), "2_6" to m(UP))
        assertEquals(WHITE.bit, shaft.gaps[3 to 6], "with nothing to stop it at the top it shines into the ceiling")
        val through = trace(*TempleSolutions.chest3.placements.toList().toTypedArray())
        assertEquals(BLUE.bit, through.gaps[2 to 1], "from the top floor it passes the middle floor's open shaft")
        assertEquals(BLUE.bit, through.gaps[1 to 1])
        assertEquals(BLUE.bit, through.lightAt("1_1"), "and lands in the ground floor's pillar")
        val floor = trace("2_9" to m(NORTH), "2_7" to m(DOWN), "1_7" to m(DOWN))
        assertEquals(WHITE.bit, floor.gaps[1 to 7])
        assertTrue(floor.links.keys.none { it.varbit.contains("1_6_7") }, "light sent into the floor goes nowhere")
    }

    @Test fun `a fractured crystal splits light only from its clear edge`() {
        val horizontal = trace("2_9" to m(NORTH), "2_7" to m(WEST), "2_6" to m(SOUTH), "2_11" to c(TempleItem.FRACTURED_HORIZONTAL))
        assertEquals(WHITE, horizontal.colour("2_10_11"), "west")
        assertEquals(WHITE, horizontal.colour("2_11_15"), "south")
        assertEquals(WHITE, horizontal.colour("2_11_east"), "east")
        val wrongEdge = trace("2_9" to m(NORTH), "2_7" to m(WEST), "2_6" to c(TempleItem.FRACTURED_HORIZONTAL))
        assertTrue(wrongEdge.links.keys.none { it.varbit.endsWith("2_5_6") || it.varbit.endsWith("2_6_11") }, "light from the east cannot enter its north edge")
        val vertical = trace("2_9" to m(NORTH), "2_7" to m(WEST), "2_6" to c(TempleItem.FRACTURED_VERTICAL))
        assertEquals(WHITE, vertical.colour("2_6_north"))
        assertEquals(WHITE, vertical.colour("2_5_6"))
        assertEquals(WHITE, vertical.colour("2_6_11"))
        assertEquals(WHITE.bit, vertical.lightAt("2_6"))
        assertTrue(TempleGeometry.pillars.filter { it.crossShaped }.map { it.id }.containsAll(listOf("1_6", "1_11", "2_6", "2_11", "3_6", "3_11")))
    }

    @Test fun `mirrors facing each other settle instead of looping forever`() {
        val loop = trace("2_9" to m(NORTH), "2_7" to m(WEST), "2_6" to m(SOUTH), "2_11" to m(NORTH))
        assertTrue(loop.steps < LightNetwork.MAX_STEPS)
        assertEquals(WHITE, loop.colour("2_6_11"))
        val ring =
            trace(
                "2_9" to m(NORTH), "2_7" to m(WEST), "2_6" to m(SOUTH), "2_11" to m(WEST), "2_10" to PillarContent.Empty,
                "3_10" to m(EAST), "3_11" to m(NORTH), "3_6" to m(DOWN),
            )
        assertTrue(ring.steps < LightNetwork.MAX_STEPS)
    }

    @Test fun `every reference solution opens its doors with the pieces the walkthrough has given by then`() {
        for (section in TempleSolutions.sections) {
            val result = LightNetwork.trace(section.state)
            assertEquals(section.opens, result.openDoors(), section.name)
            val placed = section.state.placed()
            for ((item, count) in placed) {
                assertTrue(count <= (section.inventory[item] ?: 0), "${section.name} places $count $item")
            }
        }
        assertEquals(setOf("1_b"), LightNetwork.trace(TempleSolutions.deathAltarEntrance.state).openDoors(), "mirror 14 first lets the player in")
    }

    @Test fun `the reference solutions colour the light the way the walkthrough describes`() {
        val chest1 = LightNetwork.trace(TempleSolutions.chest1.state)
        assertEquals(WHITE, chest1.colour("2_6_11"))
        assertEquals(YELLOW, chest1.colour("2_11_15"))
        assertEquals(YELLOW, chest1.colour("2_15_east"))
        val chest2 = LightNetwork.trace(TempleSolutions.chest2.state)
        assertEquals(CYAN, chest2.colour("2_5_6"))
        assertEquals(GREEN, chest2.colour("2_3_east"))
        val chest3 = LightNetwork.trace(TempleSolutions.chest3.state)
        assertEquals(BLUE, chest3.colour("3_1_2"), "the light turns blue as it passes the pillar to the west")
        val chest4 = LightNetwork.trace(TempleSolutions.chest4.state)
        assertEquals(RED, chest4.colour("3_1_8"), "this light should be red")
        val chest5 = LightNetwork.trace(TempleSolutions.chest5.state)
        assertEquals(GREEN, chest5.colour("1_10_14"), "green light comes in from the ceiling")
        assertEquals(BLUE, chest5.colour("1_16_north"), "blue light comes in from the ceiling")
        val altar = LightNetwork.trace(TempleSolutions.deathAltar.state)
        assertEquals(RED, altar.colour("3_1_8"))
        assertEquals(RED, altar.colour("1_b_west"))
        assertEquals(GREEN, altar.colour("3_10_west"))
        assertEquals(BLUE, altar.colour("3_5_west"))
        assertTrue(altar.finalLit)
        assertEquals(WHITE, altar.colour("1_d_west"))
    }

    @Test fun `near misses open nothing`() {
        val noYellow = TempleSolutions.chest1.placements - "2_11"
        assertEquals(emptySet<String>(), LightNetwork.trace(PuzzleState(noYellow)).openDoors(), "white light does not open the blue door")
        val cyanOnly = TempleSolutions.chest2.placements - "2_3"
        assertEquals(emptySet<String>(), LightNetwork.trace(PuzzleState(cyanOnly)).openDoors(), "cyan alone does not open the magenta door")
        val wrongTurn = TempleSolutions.chest4.placements + ("1_13" to m(EAST))
        assertEquals(setOf("1_13_east"), LightNetwork.trace(PuzzleState(wrongTurn)).openDoors(), "a preset mirror only opens the door it faces")
        val noBlue = TempleSolutions.deathAltar.placements - "3_5"
        val white = LightNetwork.trace(PuzzleState(noBlue))
        assertEquals(WHITE, white.colour("3_5_west"), "white light reaches the Final Pillar's north side")
        assertFalse(white.finalLit, "white is not the blue the north side wants")
        assertFalse("1_c" in white.openDoors())
        val oneBeam =
            PuzzleState(mapOf("2_9" to m(NORTH), "2_7" to m(WEST), "2_6" to m(UP), "3_6" to m(WEST)))
        assertEquals(emptySet<String>(), LightNetwork.trace(oneBeam).openDoors(), "one white beam into the Final Pillar is not enough")
        val noRed = TempleSolutions.deathAltar.placements + ("1_b" to m(EAST))
        assertEquals(setOf("1_b"), LightNetwork.trace(PuzzleState(noRed)).openDoors(), "without red on its east side the black door stays shut")
        val swapped = TempleSolutions.deathAltar.placements + ("3_5" to m(WEST)) + ("2_3" to PillarContent.Empty)
        assertFalse(LightNetwork.trace(PuzzleState(swapped)).finalLit)
    }

    @Test fun `saved arrangements read back exactly`() {
        val states = TempleSolutions.sections.map { it.state } + PuzzleState()
        for (state in states) {
            val decoded = TemplePuzzleStore.decode(TemplePuzzleStore.encode(state))
            for (pillar in TempleGeometry.adjustable) {
                assertEquals(state.contentOf(pillar), decoded.contentOf(pillar), pillar.id)
            }
        }
        assertTrue(TemplePuzzleStore.encode(PuzzleState()).all { it == 0 }, "an untouched temple saves as zeros")
        val preset = PuzzleState(mapOf("1_b" to m(EAST)))
        assertTrue(TemplePuzzleStore.encode(preset).all { it == 0 }, "a preset mirror at its default saves as zero")
        val garbage = TemplePuzzleStore.decode(IntArray(5) { -1 })
        for (pillar in TempleGeometry.adjustable) {
            val content = garbage.contentOf(pillar)
            if (pillar.kind == PillarKind.PRESET_MIRROR) assertTrue(content is PillarContent.Mirror, pillar.id)
            else assertEquals(PillarContent.Empty, content, "${pillar.id}: an unknown code reads as empty")
        }
    }

    @Test fun `the client display follows the light`() {
        val values = TempleLights.displayValues(LightNetwork.trace(TempleSolutions.chest1.state))
        assertEquals(7, values["varbit.mourning_light_temple_2_7_9"])
        assertEquals(2, values["varbit.mourning_light_temple_2_11_15"], "yellow")
        assertEquals(1, values["varbit.mourning_door_2_16_west"])
        assertEquals(0, values["varbit.mourning_door_2_16_north"])
        val altar = TempleLights.displayValues(LightNetwork.trace(TempleSolutions.deathAltar.state))
        assertEquals(1, altar["varbit.mourning_door_1_c"])
        assertEquals(7, altar["varbit.mourning_light_temple_1_d_west"])
        assertEquals(1, altar["varbit.mourning_light_temple_3_8_east"], "red into the pipe to mirror 14")
        val chest5 = TempleLights.displayValues(LightNetwork.trace(TempleSolutions.chest5.state))
        assertEquals(7 + 3, chest5["varbit.mourning_pillar_light_cross_1_15"], "the green beam to the magenta door crosses column 15 east to west")
    }
}
