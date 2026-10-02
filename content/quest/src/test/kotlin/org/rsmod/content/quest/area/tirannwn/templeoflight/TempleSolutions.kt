package org.rsmod.content.quest.area.tirannwn.templeoflight

import org.rsmod.content.quest.area.tirannwn.templeoflight.Facing.DOWN
import org.rsmod.content.quest.area.tirannwn.templeoflight.Facing.EAST
import org.rsmod.content.quest.area.tirannwn.templeoflight.Facing.NORTH
import org.rsmod.content.quest.area.tirannwn.templeoflight.Facing.SOUTH
import org.rsmod.content.quest.area.tirannwn.templeoflight.Facing.UP
import org.rsmod.content.quest.area.tirannwn.templeoflight.Facing.WEST

/**
 * The reference solutions for the six sections of the puzzle, as pillar placements. They are the
 * OSRS wiki's walkthrough (Mourning's End Part II, "The light puzzles") translated onto this
 * server's pillar ids: the walkthrough's "Mirror #n" is whichever pillar its map marks, and the
 * pillars are named after the beam varbits around them (floor_column, floor 1 being the ground).
 *
 * Each section lists the doors it is meant to open and the chest it reaches, and builds on the
 * previous one exactly as the walkthrough does (it only resets at the dispenser where the guide
 * says so).
 */
object TempleSolutions {
    data class Section(
        val name: String,
        val placements: Map<String, PillarContent>,
        val opens: Set<String>,
        val chest: String?,
        /** Pieces the walkthrough has the player holding by now (dispenser plus chests). */
        val inventory: Map<TempleItem, Int>,
    ) {
        val state: PuzzleState
            get() = PuzzleState(placements)
    }

    private fun m(facing: Facing) = PillarContent.Mirror(facing)

    private fun c(item: TempleItem) = PillarContent.Crystal(item)

    /** Chest #1 (cyan crystal, two mirrors): yellow light opens the blue door past the wall supports. */
    val chest1 =
        Section(
            "chest 1",
            mapOf(
                "2_9" to m(NORTH),
                "2_7" to m(WEST),
                "2_6" to m(SOUTH),
                "2_11" to c(TempleItem.YELLOW),
                "2_15" to m(EAST),
            ),
            opens = setOf("2_16_west"),
            chest = "loc.mourning_temple_light_parts_2_closed",
            inventory = mapOf(TempleItem.MIRROR to 4, TempleItem.YELLOW to 1),
        )

    /** Chest #2 (two mirrors): cyan then yellow makes green for the magenta door. */
    val chest2 =
        Section(
            "chest 2",
            mapOf(
                "2_9" to m(NORTH),
                "2_7" to m(WEST),
                "2_6" to c(TempleItem.CYAN),
                "2_5" to m(NORTH),
                "2_2" to m(EAST),
                "2_3" to c(TempleItem.YELLOW),
            ),
            opens = setOf("2_4_west"),
            chest = "loc.mourning_temple_light_parts_3_closed",
            inventory = mapOf(TempleItem.MIRROR to 6, TempleItem.YELLOW to 1, TempleItem.CYAN to 1),
        )

    /** Chest #3 (fractured crystal, two mirrors): cyan through the fixed magenta turns blue for the yellow doors. */
    val chest3 =
        Section(
            "chest 3",
            chest2.placements - "2_3" +
                mapOf(
                    "2_3" to m(UP),
                    "3_3" to m(WEST),
                    "3_1" to m(DOWN),
                    "1_1" to m(SOUTH),
                ),
            opens = setOf("1_1_south"),
            chest = "loc.mourning_temple_light_parts_5_closed",
            inventory = mapOf(TempleItem.MIRROR to 8, TempleItem.YELLOW to 1, TempleItem.CYAN to 1),
        )

    /** Chest #4 (blue crystal): yellow through the fixed magenta turns red for the south-west cyan doors. */
    val chest4 =
        Section(
            "chest 4",
            chest3.placements +
                mapOf(
                    "2_6" to c(TempleItem.YELLOW),
                    "3_1" to m(SOUTH),
                    "3_13" to m(DOWN),
                ),
            opens = setOf("1_13_north"),
            chest = "loc.mourning_temple_light_parts_4_closed",
            inventory =
                mapOf(
                    TempleItem.MIRROR to 10,
                    TempleItem.YELLOW to 1,
                    TempleItem.CYAN to 1,
                    TempleItem.FRACTURED_HORIZONTAL to 1,
                ),
        )

    /** Chest #5, first part: back to the blue door to leave the blue crystal in its pillar. */
    val chest5a =
        Section(
            "chest 5 (part 1)",
            chest1.placements + ("2_16" to c(TempleItem.BLUE)),
            opens = setOf("2_16_west"),
            chest = null,
            inventory =
                mapOf(
                    TempleItem.MIRROR to 10,
                    TempleItem.YELLOW to 1,
                    TempleItem.CYAN to 1,
                    TempleItem.BLUE to 1,
                    TempleItem.FRACTURED_HORIZONTAL to 1,
                ),
        )

    /**
     * Chest #5, second part (fractured crystal, three mirrors): the fractured crystal splits white
     * light, the fixed green turns one branch green for the magenta door and the blue crystal the
     * other blue for the yellow door behind it.
     */
    val chest5 =
        Section(
            "chest 5",
            chest5a.placements - "2_15" +
                mapOf(
                    "2_6" to m(UP),
                    "3_6" to m(SOUTH),
                    "3_11" to c(TempleItem.FRACTURED_HORIZONTAL),
                    "3_10" to m(DOWN),
                    "3_15" to m(EAST),
                    "3_16" to m(DOWN),
                    "1_10" to m(SOUTH),
                    "1_14" to m(EAST),
                    "1_16" to m(NORTH),
                ),
            opens = setOf("1_16_west", "1_16_north"),
            chest = "loc.mourning_temple_light_parts_6_closed",
            inventory = chest5a.inventory,
        )

    /**
     * The Death Altar: both fractured crystals split the light three ways; red (yellow through the
     * fixed magenta), green (the fixed green) and blue (the blue crystal) reach the Final Pillar's
     * east, south and north sides and merge into white for the black door. Mirror 14 is the preset
     * one in front of the cyan door; it first lets the player in, then turns west.
     */
    val deathAltar =
        Section(
            "death altar",
            mapOf(
                "2_9" to m(NORTH),
                "2_7" to m(DOWN),
                "1_7" to m(WEST),
                "1_6" to c(TempleItem.FRACTURED_VERTICAL),
                "1_3" to m(UP),
                "1_11" to c(TempleItem.FRACTURED_HORIZONTAL),
                "1_10" to m(UP),
                "1_12" to m(UP),
                "2_3" to c(TempleItem.YELLOW),
                "3_3" to m(WEST),
                "3_10" to m(WEST),
                "3_12" to m(WEST),
                "3_11" to m(NORTH),
                "3_6" to m(WEST),
                "3_5" to c(TempleItem.BLUE),
                "3_1" to m(SOUTH),
                "3_8" to m(EAST),
                "1_b" to m(WEST),
            ),
            opens = setOf("1_c"),
            chest = null,
            inventory =
                mapOf(
                    TempleItem.MIRROR to 13,
                    TempleItem.YELLOW to 1,
                    TempleItem.CYAN to 1,
                    TempleItem.BLUE to 1,
                    TempleItem.FRACTURED_HORIZONTAL to 1,
                    TempleItem.FRACTURED_VERTICAL to 1,
                ),
        )

    /** The same arrangement with mirror 14 still facing the cyan door, as the player first finds it. */
    val deathAltarEntrance =
        deathAltar.copy(name = "death altar (entering)", placements = deathAltar.placements - "1_b", opens = setOf("1_b"))

    val sections = listOf(chest1, chest2, chest3, chest4, chest5a, chest5, deathAltar)
}
